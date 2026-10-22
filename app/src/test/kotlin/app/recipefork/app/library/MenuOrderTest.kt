package app.recipefork.app.library

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.SemanticsMatcher
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
import app.recipefork.app.card.IngredientEntries
import app.recipefork.app.card.awaitCard
import app.recipefork.app.card.backThroughTheCard
import app.recipefork.app.card.closeTheCardMenu
import app.recipefork.app.card.openTheCardMenu
import app.recipefork.app.card.tagsTopToBottom
import app.recipefork.app.collections.CollectionRepository
import app.recipefork.app.collections.CollectionScreenTags
import app.recipefork.app.collections.CollectionShareTags
import app.recipefork.app.data.HeldUsualServings
import app.recipefork.app.editor.EditorEnvironment
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.editor.SAMPLE_DICTIONARIES
import app.recipefork.app.editor.TestDevice
import app.recipefork.app.editor.textsOf
import app.recipefork.app.print.LocalPrinting
import app.recipefork.app.print.PrintDocument
import app.recipefork.app.print.Printing
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.shopping.ShoppingEnvironment
import app.recipefork.app.timer.CardTimers
import app.recipefork.app.timer.LocalCardTimers
import app.recipefork.app.timer.TestTimers
import app.recipefork.app.ui.AppNavHost
import app.recipefork.app.ui.AppTheme
import app.recipefork.core.recipe.CollectionId
import app.recipefork.core.recipe.Recipe
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant

/**
 * v0.38, T-269 (scenario 5): every ⋮ of a recipe or of recipes — an entry's, the card's, a collection's and the book's
 * selection's — lists its items in one order, an item a place lacks skipped: Add to collection, Add to shopping list,
 * Print, Share as PDF, Export, then what only that place has, then Delete / Remove. The file action is "Export" /
 * «Экспорт» on a recipe and a collection alike. The selection's ⋮ draws its items by their words alone, so that their
 * labels line up whichever actions went to the bar.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MenuOrderTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val device = TestDevice(context)
    private val active = ActiveBook(BookName.Pool, book.database)
    private val recipes = RecipeRepository(active, context.filesDir)
    private val now = Instant.parse("2026-10-14T12:00:00Z")
    private var ticks = 0L
    private val collections = CollectionRepository(active) { now.plusSeconds(ticks++) }
    private val sessions = ViewModelStore()
    private val session = ViewModelProvider(
        sessions,
        viewModelFactory { initializer { LibrarySession(SavedStateHandle()) } },
    )[LibrarySession::class.java]
    private val timers = TestTimers(context)
    private val cardTimers = CardTimers(timers.store, timers.scope, timers.access, timers.ringing) { BookName.Pool }
    private val printing = object : Printing {
        override fun print(context: Context, document: PrintDocument) = Unit

        override suspend fun share(context: Context, document: PrintDocument): Boolean = true
    }

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

    private fun own(title: String): Recipe =
        Recipe.create(now, owner = null).copy(title = title, servings = 2).also { runBlocking { recipes.save(it) } }

    private fun collection(name: String, vararg members: Recipe): CollectionId = runBlocking {
        collections.create(name)!!.also { id -> members.forEach { collections.addTo(it.id, listOf(id)) } }
    }

    private fun show() {
        session.choose(LibraryView.OWN)
        val editor = EditorEnvironment(active, device.catalog(context)) { now }
        val entries = IngredientEntries { device.database.dictionaryDao().ingredients() }
        val card = CardEnvironment(HeldUsualServings(null), marks = DoneMarks.inMemory(), ingredients = entries, collections = collections)
        val shopping = ShoppingEnvironment(card.shopping, ingredients = entries)
        compose.setContent {
            CompositionLocalProvider(LocalCardTimers provides cardTimers, LocalPrinting provides printing) {
                AppTheme { AppNavHost(session, recipes, Mode.Serverless, editor = editor, card = card, shopping = shopping, collections = collections) }
            }
        }
        compose.waitForIdle()
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun await(tag: String) = compose.waitUntil(PATIENCE_MS) { exists(tag) }

    private fun awaitGone(tag: String) = compose.waitUntil(PATIENCE_MS) { !exists(tag) }

    private fun awaitEntry(id: String) = compose.waitUntil(PATIENCE_MS) {
        try {
            compose.onNodeWithTag(LibraryTags.OWN_LIST).performScrollToNode(hasTestTag(LibraryTags.entry(id)))
            true
        } catch (e: AssertionError) {
            false
        }
    }

    /** The words of the items tagged [tags], in the order given. */
    private fun words(tags: List<String>) = tags.map { compose.textsOf(it).single() }

    /** The words each place's ⋮ names its items by, top to bottom. */
    private class Words(
        val addToCollection: String,
        val addToShopping: String,
        val addAllToShopping: String,
        val print: String,
        val sharePdf: String,
        val export: String,
        val rename: String,
        val delete: String,
        val deleteCollection: String,
    )

    private val english = Words(
        "Add to collection", "Add to shopping list", "Add all to shopping list", "Print", "Share as PDF", "Export", "Rename",
        "Delete", "Delete collection",
    )

    private val russian = Words(
        "Добавить в коллекцию", "Добавить в список покупок", "Добавить все в список покупок", "Печать", "Поделиться PDF",
        "Экспорт", "Переименовать", "Удалить", "Удалить коллекцию",
    )

    private fun assertEveryMenuInOneOrder(words: Words) {
        val soup = own("Soup")
        val menu = collection("Menu", soup)
        show()
        awaitEntry(soup.id.value)
        val id = soup.id.value

        // An entry's ⋮.
        val entry = listOf(
            LibraryTags.addToCollection(id), LibraryTags.addToShopping(id), LibraryTags.print(id), LibraryTags.sharePdf(id),
            LibraryTags.export(id), LibraryTags.delete(id),
        )
        compose.openEntryMenu(id)
        assertEquals(entry, compose.tagsTopToBottom(entry.toSet()))
        assertEquals(listOf(words.addToCollection, words.addToShopping, words.print, words.sharePdf, words.export, words.delete), words(entry))
        compose.closeEntryMenu(id)

        // The selection's: its actions in the bar, as far as there is room, stand above its ⋮'s, in the same order.
        compose.onNodeWithTag(LibraryTags.entry(id)).performTouchInput { longClick(centerLeft + Offset(24f, 0f)) }
        await(LibraryTags.SELECTION_BAR)
        compose.onNodeWithTag(LibraryTags.SELECTION_MENU).performClick()
        await(LibraryTags.SELECT_ALL)
        val selection = listOf(
            LibraryTags.SELECTION_ADD_TO_COLLECTION, LibraryTags.SELECTION_ADD_TO_SHOPPING, LibraryTags.SELECTION_PRINT,
            LibraryTags.SELECTION_SHARE_PDF, LibraryTags.SELECTION_DELETE,
        )
        assertEquals(selection, compose.tagsTopToBottom(selection.toSet()))
        assertEquals(listOf(words.print, words.sharePdf, words.delete), words(selection.drop(2)))
        compose.onNodeWithTag(LibraryTags.SELECTION_MENU).performClick()
        awaitGone(LibraryTags.SELECT_ALL)
        compose.onNodeWithTag(LibraryTags.SELECTION_CLOSE).performClick()
        awaitGone(LibraryTags.SELECTION_BAR)

        // The card's.
        compose.runOnIdle { session.openCard(id, LibraryView.OWN) }
        compose.awaitCard()
        compose.waitForIdle()
        val card = listOf(CardTags.COLLECTION, CardTags.SHOPPING, CardTags.PRINT, CardTags.SHARE_PDF, CardTags.DELETE)
        compose.openTheCardMenu()
        await(CardTags.DELETE)
        assertEquals(card, compose.tagsTopToBottom(card.toSet()))
        assertEquals(listOf(words.addToCollection, words.addToShopping, words.print, words.sharePdf, words.delete), words(card))
        compose.closeTheCardMenu()
        compose.backThroughTheCard()
        await(LibraryTags.PAGER)

        // A collection's: its own Rename after Export, before its removal.
        compose.runOnIdle { session.openCollection(menu.value) }
        await(CollectionScreenTags.LIST)
        compose.onNodeWithTag(CollectionScreenTags.MENU).performClick()
        await(CollectionScreenTags.DELETE)
        val collection = listOf(
            CollectionScreenTags.ADD_ALL_TO_SHOPPING, CollectionScreenTags.PRINT, CollectionScreenTags.SHARE_PDF,
            CollectionShareTags.SHARE, CollectionScreenTags.RENAME, CollectionScreenTags.DELETE,
        )
        assertEquals(collection, compose.tagsTopToBottom(collection.toSet()))
        assertEquals(
            listOf(words.addAllToShopping, words.print, words.sharePdf, words.export, words.rename, words.deleteCollection),
            words(collection),
        )
    }

    // On a phone of the usual height: under the book's title on its own row (v0.42), a 470 dp window leaves an entry's
    // menu no room beside its ⋮.
    @Test
    @Config(qualifiers = "w360dp-h640dp")
    fun everyMenuListsItsItemsInOneOrder() = assertEveryMenuInOneOrder(english)

    @Test
    @Config(qualifiers = "ru-w360dp-h640dp")
    fun inRussianEveryMenuListsItsItemsInOneOrder() = assertEveryMenuInOneOrder(russian)

    /**
     * On a narrow phone at large text the selection's actions go to its ⋮ — those with an icon in the bar among them —
     * and every item there is drawn by its words alone: their words start at one line, as the other ⋮s' do.
     */
    private fun assertSelectionMenuLinesUp() {
        val soup = own("Soup")
        show()
        awaitEntry(soup.id.value)
        compose.onNodeWithTag(LibraryTags.entry(soup.id.value)).performTouchInput { longClick(centerLeft + Offset(24f, 0f)) }
        await(LibraryTags.SELECTION_BAR)
        compose.onNodeWithTag(LibraryTags.SELECTION_MENU).performClick()
        await(LibraryTags.SELECT_ALL)
        val items = listOf(
            LibraryTags.SELECT_ALL, LibraryTags.SELECTION_ADD_TO_COLLECTION, LibraryTags.SELECTION_ADD_TO_SHOPPING,
            LibraryTags.SELECTION_PRINT, LibraryTags.SELECTION_SHARE_PDF, LibraryTags.SELECTION_DELETE,
        )
        val inBar = hasTestTag(LibraryTags.SELECTION_BAR)
        val inMenu = items.filter { tag -> compose.onAllNodes(hasTestTag(tag) and !hasAnyAncestor(inBar)).fetchSemanticsNodes().isNotEmpty() }
        assertTrue("no action with an icon went to the ⋮: $inMenu", LibraryTags.SELECTION_ADD_TO_SHOPPING in inMenu)
        val starts = inMenu.associateWith { tag ->
            val text = SemanticsMatcher.keyIsDefined(SemanticsProperties.Text) and hasAnyAncestor(hasTestTag(tag))
            compose.onNode(text, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.left
        }
        val first = starts.getValue(LibraryTags.SELECT_ALL)
        starts.forEach { (tag, left) -> assertEquals("the words of $tag start at $left, Select all's at $first", first, left, 0.5f) }
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp", fontScale = 2f)
    fun atTwiceTheFontScaleTheSelectionsMenuItemsLineUp() = assertSelectionMenuLinesUp()

    @Test
    @Config(qualifiers = "ru-w320dp-h640dp", fontScale = 2f)
    fun inRussianAtTwiceTheFontScaleTheSelectionsMenuItemsLineUp() = assertSelectionMenuLinesUp()
}
