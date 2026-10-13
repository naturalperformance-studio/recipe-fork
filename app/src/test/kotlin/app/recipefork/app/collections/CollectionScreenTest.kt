package app.recipefork.app.collections

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.Mode
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.card.CardEnvironment
import app.recipefork.app.card.CardTags
import app.recipefork.app.card.DoneMarks
import app.recipefork.app.card.awaitCard
import app.recipefork.app.editor.EditorEnvironment
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.editor.SAMPLE_DICTIONARIES
import app.recipefork.app.editor.TestDevice
import app.recipefork.app.file.RecipeImport
import app.recipefork.app.library.LibrarySession
import app.recipefork.app.library.LibraryTags
import app.recipefork.app.library.LibraryView
import app.recipefork.app.library.awaitRemovalNotice
import app.recipefork.app.library.chooseEntryAction
import app.recipefork.app.library.swipeAwayNotice
import app.recipefork.app.library.takeNoticeAction
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.timer.CardTimers
import app.recipefork.app.timer.LocalCardTimers
import app.recipefork.app.timer.TestTimers
import app.recipefork.app.ui.AccessibilityChecks
import app.recipefork.app.ui.AppNavHost
import app.recipefork.app.ui.AppTheme
import app.recipefork.core.recipe.CollectionId
import app.recipefork.core.recipe.CollectionOrigin
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * Collections in the book and a collection's screen (v0.36, T-254): the own view shows the reader's collections and the
 * Imported view the imported ones, each as a row of cards above the recipes; a collection opens on its recipes in
 * order, each opening its card, back from which returns to it. An own collection is renamed, deleted with Undo, has a
 * recipe removed with Undo and is reordered; an imported one is frozen and names its stated author. An empty own
 * collection says how recipes are added.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class CollectionScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val device = TestDevice(context)
    private val active = ActiveBook(BookName.Pool, book.database)
    private val recipes = RecipeRepository(active, context.filesDir)
    private val now = Instant.parse("2026-10-12T12:00:00Z")
    private var ticks = 0L
    private val collections = CollectionRepository(active) { now.plusSeconds(ticks++) }
    private val sessions = ViewModelStore()
    private val session = ViewModelProvider(
        sessions,
        viewModelFactory { initializer { LibrarySession(SavedStateHandle()) } },
    )[LibrarySession::class.java]
    private val marks = DoneMarks.inMemory()
    private val timers = TestTimers(context)
    private val cardTimers = CardTimers(timers.store, timers.scope, timers.access, timers.ringing) { BookName.Pool }

    @Before
    fun setUp() {
        listOf(book.idling, device.idling, timers.idling).forEach(compose::registerIdlingResource)
        runBlocking { device.database.dictionaryDao().replace(SAMPLE_DICTIONARIES) }
    }

    @After
    fun close() {
        listOf(book.idling, device.idling, timers.idling).forEach(compose::unregisterIdlingResource)
        sessions.clear()
        book.close()
        device.close()
        timers.close()
    }

    // The book.

    private fun own(title: String, minutesAgo: Long = 0): RecipeId = runBlocking {
        Recipe.create(now.minusSeconds(minutesAgo * 60), owner = null).copy(title = title, servings = 2).also { recipes.save(it) }.id
    }

    private fun found(title: String, minutesAgo: Long = 0): RecipeId = runBlocking {
        val at = now.minusSeconds(minutesAgo * 60)
        Recipe.create(at, owner = null).copy(title = title, servings = 2, statedAuthor = "anna").inState(RecipeImport.FOUND_STATE)
            .also { assertTrue(recipes.addFound(it, at)) }.id
    }

    private fun collection(name: String, vararg members: String): CollectionId = runBlocking {
        collections.create(name)!!.also { id -> members.forEach { collections.addTo(RecipeId(it), listOf(id)) } }
    }

    private fun imported(name: String, author: String?, vararg members: String): CollectionId = runBlocking {
        CollectionId.mint().also { assertEquals(ImportedCollection.CREATED, collections.putImported(it, name, author, members.map(::RecipeId))) }
    }

    private fun order(id: CollectionId): List<RecipeId> = runBlocking { collections.get(id)!!.recipes }

    private fun show(view: LibraryView, mode: Mode = Mode.Serverless) {
        session.choose(view)
        val editor = EditorEnvironment(active, device.catalog(context)) { now }
        val card = CardEnvironment(marks = marks) { device.database.dictionaryDao().ingredients() }
        compose.setContent {
            CompositionLocalProvider(LocalCardTimers provides cardTimers) {
                AppTheme { AppNavHost(session, recipes, mode, editor = editor, card = card, collections = collections) }
            }
        }
        compose.waitForIdle()
    }

    // What the screen shows.

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    private fun await(tag: String) = compose.waitUntil(PATIENCE_MS) { exists(tag) }

    private fun awaitGone(tag: String) = compose.waitUntil(PATIENCE_MS) { !exists(tag) }

    private fun bounds(tag: String, unmerged: Boolean = false) =
        compose.onNodeWithTag(tag, useUnmergedTree = unmerged).fetchSemanticsNode().boundsInRoot

    private fun text(tag: String): String =
        compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)
            ?.joinToString("") { it.text }.orEmpty()

    private fun list(view: LibraryView) = if (view == LibraryView.OWN) LibraryTags.OWN_LIST else LibraryTags.MARKED_LIST

    /** Waits until the view's row shows the card of [id], brought into view. */
    private fun awaitCollectionCard(id: CollectionId, view: LibraryView = LibraryView.OWN) {
        val card = CollectionScreenTags.card(id.value)
        compose.waitUntil(PATIENCE_MS) {
            try {
                compose.onNodeWithTag(list(view)).performScrollToNode(hasTestTag(card))
                true
            } catch (e: AssertionError) {
                false
            }
        }
    }

    /** Opens the collection [id] from the view's row, and waits until its recipes, or its empty state, show. */
    private fun open(id: CollectionId, view: LibraryView = LibraryView.OWN) {
        awaitCollectionCard(id, view)
        compose.onNodeWithTag(CollectionScreenTags.card(id.value)).performSemanticsAction(SemanticsActions.OnClick)
        awaitScreen()
    }

    private fun awaitScreen() {
        await(CollectionScreenTags.SCREEN)
        compose.waitUntil(PATIENCE_MS) {
            (exists(CollectionScreenTags.LIST) || exists(CollectionScreenTags.EMPTY)) && !exists(LibraryTags.PAGER)
        }
    }

    /** The recipes the collection's screen shows, top to bottom. */
    private fun shownOrder(vararg recipes: String): List<RecipeId> {
        compose.waitForIdle()
        return recipes.sortedBy { bounds(LibraryTags.entry(it)).top }.map(::RecipeId)
    }

    private fun chooseInMenu(item: String) {
        compose.onNodeWithTag(CollectionScreenTags.MENU).performClick()
        await(CollectionScreenTags.DELETE)
        compose.onNodeWithTag(item).performClick()
    }

    private fun chooseForRecipe(recipe: RecipeId, item: String) {
        compose.onNodeWithTag(LibraryTags.actions(recipe.value)).performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        await(item)
        compose.onNodeWithTag(item).performClick()
        awaitGone(item)
    }

    /** Opens the card of [recipe] from the collection's screen, deletes it from the card's ⋮, and waits for the screen. */
    private fun deleteFromItsCard(recipe: RecipeId) {
        compose.onNodeWithTag(LibraryTags.entry(recipe.value)).performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        compose.awaitCard()
        compose.onNodeWithTag(CardTags.MENU).performClick()
        await(CardTags.DELETE)
        compose.onNodeWithTag(CardTags.DELETE).performClick()
        awaitScreen()
    }

    private fun systemBack() =compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }

    // The tests.

    @Test
    fun eachViewShowsItsCollectionsAboveItsRecipesTheLastMadeFirstWithTheirCounts() {
        val soup = own("Soup")
        val borscht = found("Borscht")
        val salads = collection("Salads")
        val menu = collection("New Year's menu", soup.value, borscht.value)
        val annas = imported("Anna's pies", "anna", borscht.value)
        show(LibraryView.OWN)

        awaitCollectionCard(menu)
        await(CollectionScreenTags.card(salads.value))
        assertEquals("New Year's menu", text(CollectionScreenTags.cardName(menu.value)))
        assertEquals("2 recipes", text(CollectionScreenTags.cardCount(menu.value)))
        assertEquals("0 recipes", text(CollectionScreenTags.cardCount(salads.value)))
        // The last made first, in one row above the recipes.
        assertTrue(bounds(CollectionScreenTags.card(menu.value)).left < bounds(CollectionScreenTags.card(salads.value)).left)
        assertEquals(bounds(CollectionScreenTags.card(menu.value)).top, bounds(CollectionScreenTags.card(salads.value)).top)
        await(LibraryTags.entry(soup.value))
        assertTrue(bounds(CollectionScreenTags.ROW).bottom <= bounds(LibraryTags.entry(soup.value)).top)
        assertFalse(exists(CollectionScreenTags.card(annas.value)))

        compose.runOnIdle { session.choose(LibraryView.MARKED) }
        awaitCollectionCard(annas, LibraryView.MARKED)
        assertEquals("Anna's pies", text(CollectionScreenTags.cardName(annas.value)))
        assertEquals("1 recipe", text(CollectionScreenTags.cardCount(annas.value)))
        await(LibraryTags.entry(borscht.value))
        assertTrue(bounds(CollectionScreenTags.card(annas.value)).bottom <= bounds(LibraryTags.entry(borscht.value)).top)
        compose.waitUntil(PATIENCE_MS) { !exists(CollectionScreenTags.card(menu.value)) }
    }

    @Test
    fun theRowIsHiddenWithoutCollectionsAndWhileAViewIsSearched() {
        val soup = own("Soup")
        found("Borscht")
        show(LibraryView.OWN)
        await(LibraryTags.entry(soup.value))
        assertFalse(exists(CollectionScreenTags.ROW))

        val menu = collection("Menu", soup.value)
        awaitCollectionCard(menu)
        compose.runOnIdle { session.search(LibraryView.OWN, "soup") }
        awaitGone(CollectionScreenTags.ROW)
        await(LibraryTags.entry(soup.value))

        // The Imported view holds no imported collection: it shows no row.
        compose.runOnIdle { session.choose(LibraryView.MARKED) }
        await(LibraryTags.MARKED_LIST)
        compose.waitForIdle()
        assertFalse(exists(CollectionScreenTags.ROW))
    }

    @Test
    fun aViewWithNoRecipesStillShowsItsCollections() {
        val borscht = found("Borscht")
        val menu = collection("Found ones", borscht.value)
        show(LibraryView.OWN)
        await(LibraryTags.OWN_EMPTY)
        await(CollectionScreenTags.card(menu.value))
        compose.onNodeWithTag(CollectionScreenTags.card(menu.value)).performSemanticsAction(SemanticsActions.OnClick)
        awaitScreen()
        await(LibraryTags.entry(borscht.value))
    }

    @Test
    fun aCollectionOpensOnItsRecipesInOrderAndBackFromACardReturnsToIt() {
        val soup = own("Soup", minutesAgo = 2)
        val bread = own("Bread", minutesAgo = 1)
        val borscht = found("Borscht")
        val menu = collection("New Year's menu", borscht.value, soup.value, bread.value)
        show(LibraryView.OWN)

        open(menu)

        compose.onNodeWithTag(CollectionScreenTags.TITLE).assertTextEquals("New Year's menu")
        assertEquals(listOf(borscht, soup, bread), shownOrder(soup.value, bread.value, borscht.value))
        compose.onNodeWithTag(LibraryTags.entry(soup.value)).performSemanticsAction(SemanticsActions.OnClick)
        compose.awaitCard()
        assertEquals(soup.value, session.viewing.value)

        compose.onNodeWithTag(CardTags.BACK).performClick()
        awaitScreen()
        await(LibraryTags.entry(soup.value))
        assertEquals(menu.value, session.collection.value)
        // The system's back from a card opened here returns here as well.
        compose.onNodeWithTag(LibraryTags.entry(bread.value)).performSemanticsAction(SemanticsActions.OnClick)
        compose.awaitCard()
        systemBack()
        awaitScreen()

        // Back from the collection returns to the book, on the view it was opened from.
        compose.onNodeWithTag(CollectionScreenTags.BACK).performClick()
        await(LibraryTags.PAGER)
        awaitCollectionCard(menu)
        assertNull(session.collection.value)
        assertEquals(LibraryView.OWN, session.view.value)
        open(menu)
        systemBack()
        await(LibraryTags.PAGER)
        assertNull(session.collection.value)
    }

    @Test
    fun anOwnCollectionIsRenamed() {
        val soup = own("Soup")
        val menu = collection("Menu", soup.value)
        show(LibraryView.OWN)
        open(menu)

        chooseInMenu(CollectionScreenTags.RENAME)
        await(CollectionScreenTags.RENAME_DIALOG)
        compose.onNodeWithTag(CollectionScreenTags.RENAME_FIELD).assertTextEquals("Name", "Menu")
        compose.onNodeWithTag(CollectionScreenTags.RENAME_FIELD).performTextClearance()
        compose.onNodeWithTag(CollectionScreenTags.RENAME_FIELD).performTextInput("   ")
        // A name of spaces is no name.
        compose.onNodeWithTag(CollectionScreenTags.RENAME_CONFIRM).assertIsNotEnabled()
        compose.onNodeWithTag(CollectionScreenTags.RENAME_FIELD).performTextClearance()
        compose.onNodeWithTag(CollectionScreenTags.RENAME_FIELD).performTextInput("New Year's menu")
        compose.onNodeWithTag(CollectionScreenTags.RENAME_CONFIRM).assertIsEnabled().performClick()

        awaitGone(CollectionScreenTags.RENAME_DIALOG)
        compose.waitUntil(PATIENCE_MS) { text(CollectionScreenTags.TITLE) == "New Year's menu" }
        assertEquals("New Year's menu", runBlocking { collections.get(menu)!!.name })

        // Cancel changes nothing.
        chooseInMenu(CollectionScreenTags.RENAME)
        await(CollectionScreenTags.RENAME_FIELD)
        compose.onNodeWithTag(CollectionScreenTags.RENAME_FIELD).performTextInput("!")
        compose.onNodeWithTag(CollectionScreenTags.RENAME_CANCEL).performClick()
        awaitGone(CollectionScreenTags.RENAME_DIALOG)
        assertEquals("New Year's menu", runBlocking { collections.get(menu)!!.name })
    }

    @Test
    fun aDeletedCollectionLeavesItsRecipesAndTheBookOffersUndo() {
        val soup = own("Soup", minutesAgo = 1)
        val borscht = found("Borscht")
        val menu = collection("New Year's menu", soup.value, borscht.value)
        show(LibraryView.OWN)
        open(menu)

        chooseInMenu(CollectionScreenTags.DELETE)

        await(LibraryTags.PAGER)
        compose.awaitRemovalNotice("Collection “New Year's menu” deleted", "Undo")
        awaitGone(CollectionScreenTags.card(menu.value))
        assertNull(runBlocking { collections.get(menu) })
        assertNull(session.collection.value)
        assertNotNull(runBlocking { recipes.get(soup) })
        assertNotNull(runBlocking { recipes.get(borscht) })
        await(LibraryTags.entry(soup.value))

        compose.takeNoticeAction("Undo")

        awaitCollectionCard(menu)
        assertEquals(listOf(soup, borscht), order(menu))
        assertEquals("New Year's menu", runBlocking { collections.get(menu)!!.name })

        // The notice gone without Undo leaves it deleted.
        open(menu)
        chooseInMenu(CollectionScreenTags.DELETE)
        await(LibraryTags.PAGER)
        compose.awaitRemovalNotice("Collection “New Year's menu” deleted", "Undo")
        compose.swipeAwayNotice()
        compose.waitForIdle()
        assertNull(runBlocking { collections.get(menu) })
        assertNull(session.collectionDeletion.value)
        assertFalse(exists(CollectionScreenTags.card(menu.value)))
    }

    @Test
    fun aRecipeRemovedFromACollectionStaysInTheBookAndUndoPutsItBackAtItsPlace() {
        val a = own("Apple pie", minutesAgo = 2)
        val b = own("Bread", minutesAgo = 1)
        val c = own("Cake")
        val menu = collection("Menu", a.value, b.value, c.value)
        show(LibraryView.OWN)
        open(menu)

        chooseForRecipe(b, CollectionScreenTags.remove(b.value))

        compose.awaitRemovalNotice("“Bread” removed from the collection", "Undo")
        awaitGone(LibraryTags.entry(b.value))
        assertEquals(listOf(a, c), order(menu))
        assertNotNull(runBlocking { recipes.get(b) })

        compose.takeNoticeAction("Undo")

        await(LibraryTags.entry(b.value))
        assertEquals(listOf(a, b, c), order(menu))
        assertEquals(listOf(a, b, c), shownOrder(a.value, b.value, c.value))
    }

    @Test
    fun anOwnCollectionIsReorderedByDragAndByTheRecipesMenu() {
        val a = own("Apple pie", minutesAgo = 2)
        val b = own("Bread", minutesAgo = 1)
        val c = own("Cake")
        val menu = collection("Menu", a.value, b.value, c.value)
        show(LibraryView.OWN)
        open(menu)

        // Dragged by its column until it covers the last, the first goes last.
        val handle = CollectionScreenTags.handle(a.value)
        await(handle)
        val distance = bounds(LibraryTags.entry(c.value)).bottom - bounds(LibraryTags.entry(a.value)).bottom
        compose.onNodeWithTag(handle).performTouchInput {
            down(center)
            repeat(20) { moveBy(Offset(0f, distance / 20)) }
            up()
        }
        compose.waitUntil(PATIENCE_MS) { order(menu) == listOf(b, c, a) }
        compose.waitUntil(PATIENCE_MS) { shownOrder(a.value, b.value, c.value) == listOf(b, c, a) }

        // The recipe's menu moves it as the drag does: the first has no Move up, the last no Move down.
        compose.onNodeWithTag(LibraryTags.actions(b.value)).performSemanticsAction(SemanticsActions.OnClick)
        await(CollectionScreenTags.moveDown(b.value))
        assertFalse(exists(CollectionScreenTags.moveUp(b.value)))
        compose.onNodeWithTag(LibraryTags.actions(b.value)).performClick()
        awaitGone(CollectionScreenTags.moveDown(b.value))
        chooseForRecipe(a, CollectionScreenTags.moveUp(a.value))
        compose.waitUntil(PATIENCE_MS) { order(menu) == listOf(b, a, c) }
        compose.waitUntil(PATIENCE_MS) { shownOrder(a.value, b.value, c.value) == listOf(b, a, c) }
    }

    // T-254 review: a reorder the book refuses — the collection frozen between the tap and the reorder — shows the stored
    // order again, not the one just asked for.
    @Test
    fun aRefusedReorderShowsTheStoredOrderAgain() {
        val a = own("Apple pie", minutesAgo = 2)
        val b = own("Bread", minutesAgo = 1)
        val c = own("Cake")
        val menu = collection("Menu", a.value, b.value, c.value)
        show(LibraryView.OWN)
        open(menu)
        compose.onNodeWithTag(LibraryTags.actions(b.value)).performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        await(CollectionScreenTags.moveDown(b.value))
        val moveDown = compose.onNodeWithTag(CollectionScreenTags.moveDown(b.value)).fetchSemanticsNode()

        compose.runOnUiThread {
            // In one turn of the main thread, so that the screen still offers the move when it is taken.
            book.database.openHelper.writableDatabase.execSQL(
                "update collection set origin = ? where collection_id = ?",
                arrayOf(CollectionOrigin.IMPORTED.name, menu.value),
            )
            moveDown.config[SemanticsActions.OnClick].action!!.invoke()
        }

        compose.waitForIdle()
        assertEquals(CollectionOrigin.IMPORTED, runBlocking { collections.get(menu)!!.origin })
        assertEquals(listOf(a, b, c), order(menu))
        assertEquals(listOf(a, b, c), shownOrder(a.value, b.value, c.value))
    }

    // T-254 review: a recipe deleted from its card, opened from the collection, is said back on the collection's screen
    // with Undo, which puts it back at its place in the collection.
    @Test
    fun aRecipeDeletedFromACardOpenedHereIsSaidHereAndUndoPutsItBackAtItsPlace() {
        val a = own("Apple pie", minutesAgo = 2)
        val b = own("Bread", minutesAgo = 1)
        val c = own("Cake")
        val menu = collection("Menu", a.value, b.value, c.value)
        show(LibraryView.OWN)
        open(menu)

        deleteFromItsCard(b)

        compose.awaitRemovalNotice("“Bread” deleted", "Undo")
        awaitGone(LibraryTags.entry(b.value))
        assertEquals(menu.value, session.collection.value)
        assertEquals(listOf(a, c), order(menu))

        compose.takeNoticeAction("Undo")

        await(LibraryTags.entry(b.value))
        compose.waitUntil(PATIENCE_MS) { order(menu) == listOf(a, b, c) }
        assertEquals(listOf(a, b, c), shownOrder(a.value, b.value, c.value))
        assertNotNull(runBlocking { recipes.get(b) })
    }

    // T-254 review: Back to the book while the collection's screen still offers to undo a recipe's deletion makes it
    // final, as opening the collection does: the book does not say it a second time.
    @Test
    fun backToTheBookMakesADeletionSaidHereFinalAndTheBookDoesNotSayItAgain() {
        val soup = own("Soup", minutesAgo = 1)
        val bread = own("Bread")
        val menu = collection("Menu", soup.value, bread.value)
        show(LibraryView.OWN)
        open(menu)
        deleteFromItsCard(bread)
        compose.awaitRemovalNotice("“Bread” deleted", "Undo")

        compose.onNodeWithTag(CollectionScreenTags.BACK).performClick()

        await(LibraryTags.PAGER)
        await(LibraryTags.entry(soup.value))
        compose.waitForIdle()
        assertNull(session.removal.value)
        assertFalse(exists(LibraryTags.NOTICE))
        assertNull(runBlocking { recipes.get(bread) })
        assertEquals(listOf(soup), order(menu))
    }

    // T-254 review: a collection deleted elsewhere — not from its screen — while its screen is open closes the screen;
    // the book offers no Undo for a deletion the reader did not make there.
    @Test
    fun aCollectionDeletedElsewhereWhileItsScreenIsOpenClosesIt() {
        val soup = own("Soup")
        val menu = collection("Menu", soup.value)
        show(LibraryView.OWN)
        open(menu)

        assertTrue(runBlocking { collections.delete(menu) })

        await(LibraryTags.PAGER)
        awaitGone(CollectionScreenTags.SCREEN)
        assertNull(session.collection.value)
        assertNull(session.collectionDeletion.value)
        await(LibraryTags.entry(soup.value))
    }

    @Test
    fun anImportedCollectionIsFrozenNamesItsStatedAuthorAndIsDeletedWithUndo() {
        val borscht = found("Borscht", minutesAgo = 1)
        val pelmeni = found("Pelmeni")
        val annas = imported("Anna's dinners", "anna", pelmeni.value, borscht.value)
        show(LibraryView.MARKED)
        open(annas, LibraryView.MARKED)

        compose.onNodeWithTag(CollectionScreenTags.STATED_AUTHOR).assertTextEquals("Author: anna (stated in the file, not verified)")
        assertEquals(listOf(pelmeni, borscht), shownOrder(borscht.value, pelmeni.value))
        // No drag column, and no menu on its recipes.
        assertFalse(exists(CollectionScreenTags.handle(borscht.value)))
        assertFalse(exists(LibraryTags.actions(borscht.value)))
        assertFalse(exists(LibraryTags.actions(pelmeni.value)))
        // Its menu offers Delete alone.
        compose.onNodeWithTag(CollectionScreenTags.MENU).performClick()
        await(CollectionScreenTags.DELETE)
        assertFalse(exists(CollectionScreenTags.RENAME))
        // Its recipes open their cards.
        compose.onNodeWithTag(CollectionScreenTags.MENU).performClick()
        awaitGone(CollectionScreenTags.DELETE)
        compose.onNodeWithTag(LibraryTags.entry(borscht.value)).performSemanticsAction(SemanticsActions.OnClick)
        compose.awaitCard()
        compose.onNodeWithTag(CardTags.BACK).performClick()
        awaitScreen()

        chooseInMenu(CollectionScreenTags.DELETE)
        await(LibraryTags.PAGER)
        compose.awaitRemovalNotice("Collection “Anna's dinners” deleted", "Undo")
        assertNotNull(runBlocking { recipes.get(borscht) })
        compose.takeNoticeAction("Undo")
        awaitCollectionCard(annas, LibraryView.MARKED)
        val back = runBlocking { collections.get(annas)!! }
        assertEquals(CollectionOrigin.IMPORTED, back.origin)
        assertEquals("anna", back.statedAuthor)
        assertEquals(listOf(pelmeni, borscht), back.recipes)
    }

    @Test
    fun anEmptyOwnCollectionSaysHowToAddRecipes() {
        own("Soup")
        val empty = collection("Salads")
        val annas = imported("Anna's", null)
        show(LibraryView.OWN)
        open(empty)

        await(CollectionScreenTags.EMPTY)
        assertEquals("No recipes here yet", text(LibraryTags.emptyHeadline(CollectionScreenTags.EMPTY)))
        // The ways in, as they are now (v0.38): a recipe's ⋮ in the book or on its card, and the book's selection.
        assertEquals(
            "Choose “Add to collection” in a recipe's ⋮ menu — in the book or on its card — or long-press to select several recipes in the book.",
            text(LibraryTags.emptySentence(CollectionScreenTags.EMPTY)),
        )

        // An imported one says it holds none, and offers no way to add.
        compose.onNodeWithTag(CollectionScreenTags.BACK).performClick()
        await(LibraryTags.PAGER)
        compose.runOnIdle { session.choose(LibraryView.MARKED) }
        await(LibraryTags.MARKED_EMPTY)
        await(CollectionScreenTags.card(annas.value))
        compose.onNodeWithTag(CollectionScreenTags.card(annas.value)).performSemanticsAction(SemanticsActions.OnClick)
        awaitScreen()
        await(CollectionScreenTags.EMPTY)
        assertEquals("No recipes in this collection", text(LibraryTags.emptyHeadline(CollectionScreenTags.EMPTY)))
        assertFalse(exists(LibraryTags.emptySentence(CollectionScreenTags.EMPTY)))
        assertFalse(exists(CollectionScreenTags.STATED_AUTHOR))
    }

    @Test
    fun aRecipeDeletedFromTheBookLeavesItsCollections() {
        val soup = own("Soup", minutesAgo = 1)
        val bread = own("Bread")
        val menu = collection("Menu", soup.value, bread.value)
        val salads = collection("Salads", soup.value)
        show(LibraryView.OWN)
        awaitCollectionCard(menu)
        assertEquals("2 recipes", text(CollectionScreenTags.cardCount(menu.value)))

        compose.onNodeWithTag(LibraryTags.OWN_LIST).performScrollToNode(hasTestTag(LibraryTags.entry(soup.value)))
        compose.chooseEntryAction(soup.value, LibraryTags.delete(soup.value))
        compose.awaitRemovalNotice("“Soup” deleted", "Undo")

        compose.waitUntil(PATIENCE_MS) { text(CollectionScreenTags.cardCount(menu.value)) == "1 recipe" }
        assertEquals("0 recipes", text(CollectionScreenTags.cardCount(salads.value)))
        assertEquals(listOf(bread), order(menu))
    }

    @Test
    @Config(qualifiers = "ru")
    fun inRussian() {
        val soup = own("Суп", minutesAgo = 1)
        val bread = own("Хлеб")
        val menu = collection("Новогоднее меню", soup.value, bread.value)
        val empty = collection("Салаты")
        show(LibraryView.OWN)
        awaitCollectionCard(menu)
        compose.onNode(hasText("Коллекции") and hasAnyAncestor(hasTestTag(CollectionScreenTags.ROW)), useUnmergedTree = true).assertExists()
        assertEquals("2 рецепта", text(CollectionScreenTags.cardCount(menu.value)))
        assertEquals("0 рецептов", text(CollectionScreenTags.cardCount(empty.value)))

        open(menu)
        compose.onNodeWithTag(CollectionScreenTags.MENU).performClick()
        await(CollectionScreenTags.DELETE)
        compose.onNodeWithTag(CollectionScreenTags.RENAME).assertTextEquals("Переименовать")
        compose.onNodeWithTag(CollectionScreenTags.DELETE).assertTextEquals("Удалить коллекцию")
        compose.onNodeWithTag(CollectionScreenTags.MENU).performClick()
        awaitGone(CollectionScreenTags.DELETE)
        chooseForRecipe(soup, CollectionScreenTags.remove(soup.value))
        compose.awaitRemovalNotice("Рецепт «Суп» убран из коллекции", "Вернуть")
        compose.takeNoticeAction("Вернуть")
        compose.waitUntil(PATIENCE_MS) { order(menu) == listOf(soup, bread) }

        compose.onNodeWithTag(CollectionScreenTags.BACK).performClick()
        open(empty)
        await(CollectionScreenTags.EMPTY)
        assertEquals("Здесь пока нет рецептов", text(LibraryTags.emptyHeadline(CollectionScreenTags.EMPTY)))
        assertEquals(
            "Выберите «Добавить в коллекцию» в меню ⋮ рецепта — в книге или на его карточке — или выделите несколько рецептов в книге долгим нажатием.",
            text(LibraryTags.emptySentence(CollectionScreenTags.EMPTY)),
        )
        chooseInMenu(CollectionScreenTags.DELETE)
        await(LibraryTags.PAGER)
        compose.awaitRemovalNotice("Коллекция «Салаты» удалена", "Вернуть")
    }

    @Test
    @Config(qualifiers = "en-w320dp-h640dp", fontScale = 2f)
    fun atTwiceTheFontScaleOnANarrowPhoneEverythingIsInReachAndWhole() {
        val soup = own("Tomato soup with basil", minutesAgo = 1)
        val bread = own("Bread")
        val menu = collection("A long New Year's Eve dinner menu for the family", soup.value, bread.value)
        collection("Salads")
        show(LibraryView.OWN)
        awaitCollectionCard(menu)
        compose.waitForIdle()
        AccessibilityChecks.assertTouchTargets(compose)
        AccessibilityChecks.assertInteractiveElementsLabelled(compose, except = AccessibilityChecks.snackbarPane)
        // The card grows to its words: its name and count stand whole inside it.
        val card = bounds(CollectionScreenTags.card(menu.value))
        val count = bounds(CollectionScreenTags.cardCount(menu.value), unmerged = true)
        assertTrue("the count is cut off: $count in $card", count.bottom <= card.bottom && count.top >= card.top)

        open(menu)
        compose.waitForIdle()
        AccessibilityChecks.assertTouchTargets(compose)
        AccessibilityChecks.assertInteractiveElementsLabelled(compose, except = AccessibilityChecks.snackbarPane)
        chooseInMenu(CollectionScreenTags.RENAME)
        await(CollectionScreenTags.RENAME_CONFIRM)
        AccessibilityChecks.assertTouchTargets(compose)
        val confirm = bounds(CollectionScreenTags.RENAME_CONFIRM)
        assertTrue(confirm.width > 0f && confirm.height > 0f)
    }
}
