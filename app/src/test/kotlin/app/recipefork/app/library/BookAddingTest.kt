package app.recipefork.app.library

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
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
import app.recipefork.app.book.FavouriteEntity
import app.recipefork.app.book.TestBook
import app.recipefork.app.card.CardEnvironment
import app.recipefork.app.card.DoneMarks
import app.recipefork.app.card.HeldScale
import app.recipefork.app.card.IngredientEntries
import app.recipefork.app.collections.AddToCollectionTags
import app.recipefork.app.collections.CollectionRepository
import app.recipefork.app.collections.ImportedCollection
import app.recipefork.app.data.HeldUsualServings
import app.recipefork.app.editor.EditorEnvironment
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.editor.SAMPLE_DICTIONARIES
import app.recipefork.app.editor.TestDevice
import app.recipefork.app.editor.textsOf
import app.recipefork.app.file.RecipeImport
import app.recipefork.app.media.PhotoStore
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.shopping.HeldShopping
import app.recipefork.app.shopping.ListedRecipe
import app.recipefork.app.shopping.ShoppingEnvironment
import app.recipefork.app.shopping.ShoppingList
import app.recipefork.app.shopping.ShoppingTags
import app.recipefork.app.timer.CardTimers
import app.recipefork.app.timer.LocalCardTimers
import app.recipefork.app.timer.TestTimers
import app.recipefork.app.ui.AccessibilityChecks
import app.recipefork.app.ui.AppNavHost
import app.recipefork.app.ui.AppTheme
import app.recipefork.core.recipe.CollectionId
import app.recipefork.core.recipe.CollectionOrigin
import app.recipefork.core.recipe.Media
import app.recipefork.core.recipe.MediaAttachment
import app.recipefork.core.recipe.MediaKind
import app.recipefork.core.recipe.MediaVisibility
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.scaling.Rational
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.time.Instant

/**
 * Into a collection and the shopping list from the book (v0.37, T-263, scenarios 2, 3 and 6). The selection's bar offers
 * "Add to collection" — a sheet of the reader's own collections and "New collection…"; the one chosen takes every recipe
 * chosen it does not hold, in the order they were chosen, at its end, and says how many — and "Add to shopping list",
 * which puts each on the list at the count its card would open with, one listed keeping its count, and says so with
 * Open. Either ends the selection. An entry's ⋮ in either view offers both for its one recipe, as the card does, above
 * the export and the removal. Also: a bulk Undo that cannot bring several back says how many, and while selecting the
 * hidden title bar takes none of the list's drags.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class BookAddingTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val device = TestDevice(context)
    private val active = ActiveBook(BookName.Pool, book.database)
    private val recipes = RecipeRepository(active, context.filesDir)
    private val now = Instant.parse("2026-10-13T12:00:00Z")
    private var ticks = 0L
    private val collections = CollectionRepository(active) { now.plusSeconds(ticks++) }
    private val sessions = ViewModelStore()
    private val session = ViewModelProvider(
        sessions,
        viewModelFactory { initializer { LibrarySession(SavedStateHandle()) } },
    )[LibrarySession::class.java]
    private val photos = PhotoStore(context.filesDir)
    private val timers = TestTimers(context)
    private val cardTimers = CardTimers(timers.store, timers.scope, timers.access, timers.ringing) { BookName.Pool }
    private val pool = BookName.Pool.fileName
    private var store = HeldShopping()

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
        photos.directory(BookName.Pool).deleteRecursively()
    }

    private fun recipe(title: String, servings: Int?, minutesAgo: Long) =
        Recipe.create(now.minusSeconds(minutesAgo * 60), owner = null).copy(title = title, servings = servings)

    private fun own(title: String, servings: Int? = 2, minutesAgo: Long = 0): Recipe =
        recipe(title, servings, minutesAgo).also { runBlocking { recipes.save(it) } }

    private fun found(title: String, servings: Int? = 2, minutesAgo: Long = 0): Recipe =
        recipe(title, servings, minutesAgo).inState(RecipeImport.FOUND_STATE).also {
            assertTrue(runBlocking { recipes.addFound(it, now.minusSeconds(minutesAgo * 60)) })
        }

    /** A recipe marked in the book whose content the book does not hold. */
    private fun markedOnly(id: String, title: String) =
        runBlocking { book.database.favourites().put(FavouriteEntity(id, title, now)) }

    private fun collection(name: String, vararg members: Recipe): CollectionId = runBlocking {
        collections.create(name)!!.also { id -> members.forEach { collections.addTo(it.id, listOf(id)) } }
    }

    private fun members(id: CollectionId): List<RecipeId> = runBlocking { collections.get(id)!!.recipes }

    private fun show(view: LibraryView, mode: Mode = Mode.Standard, usual: Rational? = null) {
        session.choose(view)
        val editor = EditorEnvironment(active, device.catalog(context)) { now }
        val entries = IngredientEntries { device.database.dictionaryDao().ingredients() }
        val card = CardEnvironment(HeldUsualServings(usual), marks = DoneMarks.inMemory(), shopping = store, ingredients = entries)
        val shopping = ShoppingEnvironment(store, ingredients = entries)
        compose.setContent {
            CompositionLocalProvider(LocalCardTimers provides cardTimers) {
                AppTheme { AppNavHost(session, recipes, mode, editor = editor, card = card, shopping = shopping, collections = collections) }
            }
        }
        compose.waitForIdle()
    }

    private fun list(view: LibraryView) = if (view == LibraryView.OWN) LibraryTags.OWN_LIST else LibraryTags.MARKED_LIST

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun await(tag: String) = compose.waitUntil(PATIENCE_MS) { exists(tag) }

    private fun awaitEntry(id: String, view: LibraryView) = compose.waitUntil(PATIENCE_MS) {
        try {
            compose.onNodeWithTag(list(view)).performScrollToNode(hasTestTag(LibraryTags.entry(id)))
            true
        } catch (e: AssertionError) {
            false
        }
    }

    private fun longPress(recipe: Recipe, view: LibraryView = LibraryView.OWN) = longPress(recipe.id.value, view)

    private fun longPress(id: String, view: LibraryView) {
        awaitEntry(id, view)
        compose.onNodeWithTag(LibraryTags.entry(id)).performTouchInput { longClick() }
        compose.waitUntil(PATIENCE_MS) { exists(LibraryTags.SELECTION_BAR) }
    }

    private fun tap(recipe: Recipe, view: LibraryView = LibraryView.OWN) {
        awaitEntry(recipe.id.value, view)
        compose.onNodeWithTag(LibraryTags.entry(recipe.id.value)).performClick()
        compose.waitForIdle()
    }

    /** Chooses [recipes] in [view] in this order: the first by a long press, the others by taps. */
    private fun choose(vararg recipes: Recipe, view: LibraryView = LibraryView.OWN) {
        compose.waitUntil(PATIENCE_MS) { session.selection.value == null }
        // The last notice, at the bottom, would take the long press of an entry beneath it.
        if (compose.noticeShown()) compose.swipeAwayNotice()
        longPress(recipes.first(), view)
        recipes.drop(1).forEach { tap(it, view) }
        compose.waitUntil(PATIENCE_MS) { session.selection.value?.count == recipes.size }
    }

    private fun awaitNoSelection() = compose.waitUntil(PATIENCE_MS) {
        !exists(LibraryTags.SELECTION_BAR) && exists(LibraryTags.TITLE) && session.selection.value == null
    }

    /** Waits until the book's snackbar shows one notice reading [text], and no action. */
    private fun awaitNotice(text: String) = compose.waitUntil(PATIENCE_MS) {
        val inNotice = hasAnyAncestor(hasTestTag(LibraryTags.NOTICE))
        compose.onAllNodes(hasTestTag(LibraryTags.NOTICE), useUnmergedTree = true).fetchSemanticsNodes().size == 1 &&
            compose.onAllNodes(hasText(text) and inNotice, useUnmergedTree = true).fetchSemanticsNodes().size == 1
    }

    /** Opens the selection's sheet of collections from its bar and waits until its collections are listed. */
    private fun openChooser() {
        compose.onNodeWithTag(LibraryTags.SELECTION_ADD_TO_COLLECTION).performClick()
        compose.waitUntil(PATIENCE_MS) { exists(AddToCollectionTags.CHOOSE_SHEET) && exists(AddToCollectionTags.NEW) }
        compose.waitForIdle()
    }

    private fun stored(recipe: Recipe) = runBlocking { recipes.get(recipe.id) }

    private fun bounds(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    // --- The selection into a collection -----------------------------------------------------------------------------

    // Three chosen, one of them in the collection already: the sheet lists the reader's own collections — no imported
    // one — and "New collection…"; the one tapped takes the other two at its end, in the order they were chosen, and
    // the notice counts them. The selection ends. Chosen again, all of them in it: nothing changes, and it says so.
    @Test
    fun theChosenGoIntoTheCollectionTappedInTheOrderChosenAndNoneTwice() {
        val soup = own("Soup", minutesAgo = 3)
        val pie = own("Pie", minutesAgo = 2)
        val bread = own("Bread", minutesAgo = 1)
        val salad = own("Salad")
        val dinner = collection("Dinner", salad, pie)
        val baking = collection("Baking")
        val annas = CollectionId.mint().also {
            assertEquals(ImportedCollection.CREATED, runBlocking { collections.putImported(it, "Anna's pies", "anna", listOf(pie.id)) })
        }
        show(LibraryView.OWN)

        choose(bread, pie, soup)
        // Both adding actions stand first at the bottom (v0.42), named by their words, their clicks by what they do.
        assertTrue(exists(LibraryTags.SELECTION_ADD_TO_SHOPPING))
        assertTrue(bounds(LibraryTags.SELECTION_ADD_TO_COLLECTION).right <= bounds(LibraryTags.SELECTION_ADD_TO_SHOPPING).left)
        assertTrue(bounds(LibraryTags.SELECTION_ADD_TO_SHOPPING).right <= bounds(LibraryTags.SELECTION_DELETE).left)
        assertEquals("To collection", AccessibilityChecks.announcedLabel(compose.onNodeWithTag(LibraryTags.SELECTION_ADD_TO_COLLECTION).fetchSemanticsNode()))
        assertEquals("To shopping", AccessibilityChecks.announcedLabel(compose.onNodeWithTag(LibraryTags.SELECTION_ADD_TO_SHOPPING).fetchSemanticsNode()))
        assertEquals("Add to collection", AccessibilityChecks.clickActionLabel(compose.onNodeWithTag(LibraryTags.SELECTION_ADD_TO_COLLECTION).fetchSemanticsNode()))
        assertEquals("Add to shopping list", AccessibilityChecks.clickActionLabel(compose.onNodeWithTag(LibraryTags.SELECTION_ADD_TO_SHOPPING).fetchSemanticsNode()))
        AccessibilityChecks.assertTouchTargets(compose)

        openChooser()
        // Titled with how many it adds (v0.38).
        assertEquals(listOf("Add 3 recipes to…"), compose.textsOf(AddToCollectionTags.TITLE))
        assertTrue(exists(AddToCollectionTags.choice(dinner.value)))
        assertTrue(exists(AddToCollectionTags.choice(baking.value)))
        assertFalse("an imported collection is listed", exists(AddToCollectionTags.choice(annas.value)))
        // The last made first.
        assertTrue(bounds(AddToCollectionTags.choice(baking.value)).bottom <= bounds(AddToCollectionTags.choice(dinner.value)).top)
        AccessibilityChecks.assertTouchTargets(compose)
        compose.onNodeWithTag(AddToCollectionTags.choice(dinner.value)).performClick()

        awaitNotice("2 recipes added to “Dinner”")
        compose.waitUntil(PATIENCE_MS) { !exists(AddToCollectionTags.CHOOSE_SHEET) }
        awaitNoSelection()
        assertEquals(listOf(salad, pie, bread, soup).map { it.id }, members(dinner))
        assertEquals(emptyList<RecipeId>(), members(baking))

        choose(soup, pie)
        openChooser()
        compose.onNodeWithTag(AddToCollectionTags.choice(dinner.value)).performClick()
        awaitNotice("All these recipes are already in “Dinner”")
        awaitNoSelection()
        assertEquals(listOf(salad, pie, bread, soup).map { it.id }, members(dinner))

        choose(salad)
        openChooser()
        compose.onNodeWithTag(AddToCollectionTags.choice(dinner.value)).performClick()
        awaitNotice("The recipe is already in “Dinner”")

        // One alone, not in it: counted in the singular.
        choose(soup)
        openChooser()
        compose.onNodeWithTag(AddToCollectionTags.choice(baking.value)).performClick()
        awaitNotice("1 recipe added to “Baking”")
        assertEquals(listOf(soup.id), members(baking))
    }

    // "New collection…" names a collection; Create makes it with every chosen recipe in it, in the order chosen.
    @Test
    fun aNewCollectionFromTheSheetTakesTheChosen() {
        val soup = own("Soup", minutesAgo = 1)
        val pie = own("Pie")
        show(LibraryView.OWN)
        choose(pie, soup)
        openChooser()
        // With no collection yet, the sheet says so.
        assertTrue(exists(AddToCollectionTags.EMPTY))

        compose.onNodeWithTag(AddToCollectionTags.NEW).performClick()
        await(AddToCollectionTags.NAME)
        compose.onNodeWithTag(AddToCollectionTags.NAME).performTextInput("  Weekend ")
        compose.onNodeWithTag(AddToCollectionTags.CREATE).performClick()

        awaitNotice("2 recipes added to “Weekend”")
        awaitNoSelection()
        val made = runBlocking { collections.listed(CollectionOrigin.OWN).first() }.single()
        assertEquals("Weekend", made.name)
        assertEquals(listOf(pie.id, soup.id), members(CollectionId(made.collectionId)))
    }

    // Recipes chosen that left the book while the sheet was open cannot go in: the notice says nothing was added, not
    // that they are in the collection already.
    @Test
    fun recipesGoneMeanwhileAreNotSaidToBeInTheCollection() {
        val soup = own("Soup", minutesAgo = 1)
        val pie = own("Pie")
        show(LibraryView.OWN)
        choose(soup, pie)
        openChooser()
        runBlocking { listOf(soup, pie).forEach { recipes.take(it.id) } }

        compose.onNodeWithTag(AddToCollectionTags.NEW).performClick()
        await(AddToCollectionTags.NAME)
        compose.onNodeWithTag(AddToCollectionTags.NAME).performTextInput("Weekend")
        compose.onNodeWithTag(AddToCollectionTags.CREATE).performClick()

        awaitNotice("Nothing was added to “Weekend”")
        val made = runBlocking { collections.listed(CollectionOrigin.OWN).first() }.single()
        assertEquals(emptyList<RecipeId>(), members(CollectionId(made.collectionId)))
        assertEquals(0, compose.onAllNodes(hasText("already", substring = true), useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    // Closed without a choice, the sheet adds nothing and the selection stays.
    @Test
    fun theSheetClosedWithoutAChoiceKeepsTheSelection() {
        val soup = own("Soup", minutesAgo = 1)
        val pie = own("Pie")
        val dinner = collection("Dinner")
        show(LibraryView.OWN)
        choose(soup, pie)
        openChooser()
        compose.onNodeWithTag(AddToCollectionTags.CHOOSE_SHEET).performTouchInput { swipeDown() }
        compose.waitUntil(PATIENCE_MS) { !exists(AddToCollectionTags.CHOOSE_SHEET) }
        assertEquals(2, session.selection.value?.count)
        assertTrue(exists(LibraryTags.SELECTION_BAR))
        assertEquals(emptyList<RecipeId>(), members(dinner))
    }

    // Among the imported, the adding actions take the recipes whose content the book holds; one only marked is chosen
    // all the same and left out, and for a selection of none such neither responds.
    @Test
    fun amongTheImportedOnlyRecipesTheBookHoldsAreAdded() {
        val borscht = found("Borscht", minutesAgo = 1)
        val pelmeni = found("Pelmeni")
        markedOnly("server-1", "Shchi")
        val dinner = collection("Dinner")
        show(LibraryView.MARKED)

        longPress("server-1", LibraryView.MARKED)
        compose.onNodeWithTag(LibraryTags.SELECTION_ADD_TO_COLLECTION).assertIsNotEnabled()
        compose.onNodeWithTag(LibraryTags.SELECTION_ADD_TO_SHOPPING).assertIsNotEnabled()
        tap(pelmeni, LibraryView.MARKED)
        tap(borscht, LibraryView.MARKED)
        compose.waitUntil(PATIENCE_MS) { session.selection.value?.count == 3 }
        openChooser()
        compose.onNodeWithTag(AddToCollectionTags.choice(dinner.value)).performClick()
        awaitNotice("2 recipes added to “Dinner”")
        assertEquals(listOf(pelmeni.id, borscht.id), members(dinner))

        choose(borscht, view = LibraryView.MARKED)
        compose.awaitAndChoose(LibraryTags.SELECTION_ADD_TO_SHOPPING)
        compose.awaitRemovalNotice("Added 1 recipe", "Open")
        assertEquals(listOf(ListedRecipe(pool, borscht.id, Rational.of(2))), store.list.value.recipes)
    }

    // --- The selection onto the shopping list -------------------------------------------------------------------------

    // Three chosen, one listed already at another count: the other two go on after it, each at the count its card would
    // open with — the authored one, or the one the reader left its card at — and the listed one keeps its count. One
    // notice, with Open, which opens the list. The selection ends. Chosen again: they are all listed already.
    @Test
    fun theChosenGoOnTheShoppingListAtTheirCardsCountsAndOpenOpensIt() {
        val soup = own("Soup", servings = 4, minutesAgo = 2)
        val bread = own("Bread", servings = 2, minutesAgo = 1)
        val cake = own("Cake", servings = 3)
        store = HeldShopping(ShoppingList(listOf(ListedRecipe(pool, bread.id, Rational.of(6)))))
        session.cards.holdScale(cake.id, HeldScale(Rational.of(5)))
        show(LibraryView.OWN)

        choose(soup, bread, cake)
        compose.onNodeWithTag(LibraryTags.SELECTION_ADD_TO_SHOPPING).performClick()

        compose.awaitRemovalNotice("Added 2 recipes", "Open")
        awaitNoSelection()
        assertEquals(
            listOf(ListedRecipe(pool, bread.id, Rational.of(6)), ListedRecipe(pool, soup.id, Rational.of(4)), ListedRecipe(pool, cake.id, Rational.of(5))),
            store.list.value.recipes,
        )

        choose(cake, soup)
        compose.onNodeWithTag(LibraryTags.SELECTION_ADD_TO_SHOPPING).performClick()
        compose.awaitRemovalNotice("All these recipes are already in the shopping list", "Open")
        assertEquals(3, store.list.value.recipes.size)

        compose.takeNoticeAction("Open")
        compose.waitUntil(PATIENCE_MS) { exists(ShoppingTags.SCREEN) && !exists(LibraryTags.PAGER) }
        assertTrue(session.shopping.value)
    }

    // The reader's usual count sets where a card opens, so it sets the count added.
    @Test
    fun theUsualCountIsTheCountAdded() {
        val soup = own("Soup", servings = 4, minutesAgo = 1)
        val tea = own("Tea", servings = null)
        show(LibraryView.OWN, usual = Rational.of(3))
        choose(tea, soup)
        compose.onNodeWithTag(LibraryTags.SELECTION_ADD_TO_SHOPPING).performClick()
        compose.awaitRemovalNotice("Added 2 recipes", "Open")
        assertEquals(listOf(ListedRecipe(pool, tea.id, null), ListedRecipe(pool, soup.id, Rational.of(3))), store.list.value.recipes)
    }

    // --- An entry's ⋮ --------------------------------------------------------------------------------------------------

    // "Add to collection" in an own entry's ⋮ opens the card's sheet with checkboxes; what it ticks is written, and its
    // notice is said in the book. The same from an imported entry.
    @Test
    fun anEntrysAddToCollectionOpensTheCardsSheetInEitherView() {
        val soup = own("Soup")
        val borscht = found("Borscht")
        val dinner = collection("Dinner")
        show(LibraryView.OWN)

        awaitEntry(soup.id.value, LibraryView.OWN)
        compose.chooseEntryAction(soup.id.value, LibraryTags.addToCollection(soup.id.value))
        compose.waitUntil(PATIENCE_MS) { exists(AddToCollectionTags.SHEET) && exists(AddToCollectionTags.collection(dinner.value)) }
        compose.onNodeWithTag(AddToCollectionTags.collection(dinner.value)).performClick()
        compose.waitUntil(PATIENCE_MS) {
            compose.onNodeWithTag(AddToCollectionTags.collection(dinner.value)).fetchSemanticsNode().config
                .getOrNull(SemanticsProperties.ToggleableState) == ToggleableState.On
        }
        compose.onNodeWithTag(AddToCollectionTags.DONE).performClick()
        awaitNotice("In 1 collection")
        compose.waitUntil(PATIENCE_MS) { !exists(AddToCollectionTags.SHEET) }
        assertEquals(listOf(soup.id), members(dinner))

        compose.onNodeWithTag(LibraryTags.tab(LibraryView.MARKED)).performClick()
        awaitEntry(borscht.id.value, LibraryView.MARKED)
        compose.chooseEntryAction(borscht.id.value, LibraryTags.addToCollection(borscht.id.value))
        compose.waitUntil(PATIENCE_MS) { exists(AddToCollectionTags.SHEET) && exists(AddToCollectionTags.collection(dinner.value)) }
        compose.onNodeWithTag(AddToCollectionTags.collection(dinner.value)).performClick()
        compose.onNodeWithTag(AddToCollectionTags.DONE).performClick()
        awaitNotice("In 1 collection")
        compose.waitUntil(PATIENCE_MS) { members(dinner) == listOf(soup.id, borscht.id) }
    }

    // "Add to shopping list" in an entry's ⋮ puts the recipe on the list at the count its card would open with, as the
    // card's ⋮ does, with Open; listed, the item reads "Update in shopping list" and sets the listed count to that one.
    // The same from an imported entry.
    @Test
    fun anEntrysAddToShoppingListActsAsTheCardsInEitherView() {
        val soup = own("Soup", servings = 4)
        val borscht = found("Borscht", servings = 6)
        show(LibraryView.OWN, usual = Rational.of(3))

        awaitEntry(soup.id.value, LibraryView.OWN)
        compose.openEntryMenu(soup.id.value)
        assertEquals(listOf("Add to shopping list"), compose.textsOf(LibraryTags.addToShopping(soup.id.value)))
        compose.onNodeWithTag(LibraryTags.addToShopping(soup.id.value)).performClick()
        compose.awaitRemovalNotice("Added to shopping list", "Open")
        assertEquals(listOf(ListedRecipe(pool, soup.id, Rational.of(3))), store.list.value.recipes)

        // The reader leaves its card at seven: the item now updates the listed count to that.
        compose.runOnIdle { session.cards.holdScale(soup.id, HeldScale(Rational.of(7))) }
        compose.openEntryMenu(soup.id.value)
        assertEquals(listOf("Update in shopping list"), compose.textsOf(LibraryTags.addToShopping(soup.id.value)))
        compose.onNodeWithTag(LibraryTags.addToShopping(soup.id.value)).performClick()
        compose.awaitRemovalNotice("Shopping list updated", "Open")
        assertEquals(listOf(ListedRecipe(pool, soup.id, Rational.of(7))), store.list.value.recipes)

        compose.onNodeWithTag(LibraryTags.tab(LibraryView.MARKED)).performClick()
        awaitEntry(borscht.id.value, LibraryView.MARKED)
        compose.chooseEntryAction(borscht.id.value, LibraryTags.addToShopping(borscht.id.value))
        compose.awaitRemovalNotice("Added to shopping list", "Open")
        assertEquals(listOf(ListedRecipe(pool, soup.id, Rational.of(7)), ListedRecipe(pool, borscht.id, Rational.of(3))), store.list.value.recipes)
        compose.takeNoticeAction("Open")
        compose.waitUntil(PATIENCE_MS) { exists(ShoppingTags.SCREEN) && !exists(LibraryTags.PAGER) }
    }

    // --- From the review of T-261 ------------------------------------------------------------------------------------

    /** [recipe] with a cover photograph held on the device under [mediaId]. */
    private fun withCover(recipe: Recipe, mediaId: String): Recipe {
        photos.file(BookName.Pool, mediaId).apply { parentFile!!.mkdirs() }.writeBytes(byteArrayOf(-1, -40, -1, 1, 2, 3))
        return recipe.copy(media = listOf(Media(mediaId, null, MediaKind.PHOTO, MediaAttachment.Cover, MediaVisibility.PRIVATE)))
    }

    // An Undo of several that cannot bring two of them back says how many.
    @Test
    fun anUndoThatCannotBringSeveralBackCountsThem() = undoFailureCounted("3 recipes deleted", "Undo", "Couldn't bring 2 recipes back")

    @Test
    @Config(qualifiers = "ru")
    fun anUndoThatCannotBringSeveralBackCountsThemInRussian() =
        undoFailureCounted("3 рецепта удалены", "Вернуть", "Не удалось вернуть 2 рецепта")

    private fun undoFailureCounted(deleted: String, undo: String, failed: String) {
        val soup = own("Soup", minutesAgo = 2)
        val pie = withCover(recipe("Pie", 2, 1), "pie-cover").also { runBlocking { recipes.save(it) } }
        val cake = withCover(recipe("Cake", 2, 0), "cake-cover").also { runBlocking { recipes.save(it) } }
        show(LibraryView.OWN)
        choose(cake, pie, soup)
        compose.awaitAndChoose(LibraryTags.SELECTION_DELETE)
        compose.awaitRemovalNotice(deleted, undo)
        listOf(soup, pie, cake).forEach { compose.waitUntil(PATIENCE_MS) { stored(it) == null } }
        // Other recipes hold their covers by the time Undo runs: those two cannot come back.
        runBlocking {
            recipes.save(Recipe.create(now, owner = null).copy(title = "Pie again", media = pie.media))
            recipes.save(Recipe.create(now, owner = null).copy(title = "Cake again", media = cake.media))
        }

        compose.onNode(hasText(undo) and hasAnyAncestor(hasTestTag(LibraryTags.NOTICE)), useUnmergedTree = true).performClick()

        compose.waitUntil(PATIENCE_MS) {
            ShadowLooper.idleMainLooper()
            compose.onAllNodes(hasText(failed) and hasAnyAncestor(hasTestTag(LibraryTags.NOTICE)), useUnmergedTree = true)
                .fetchSemanticsNodes().size == 1
        }
        compose.waitUntil(PATIENCE_MS) { stored(soup) != null }
        assertNull(stored(pie))
        assertNull(stored(cake))
    }

    // While selecting, the title bar is not shown, and the list's drags are the list's: once the selection ends, the
    // title bar is back as it was — whole, as it stood when the selection began.
    @Test
    fun whileSelectingTheHiddenTitleBarTakesNoneOfTheListsDrags() {
        val dishes = (1..20).map { own("Dish $it", minutesAgo = it.toLong()) }
        show(LibraryView.OWN)
        await(LibraryTags.TOP_BAR)
        val whole = bounds(LibraryTags.TOP_BAR).height
        assertTrue("the bar shows: $whole", whole >= with(compose.density) { 56.dp.toPx() })

        longPress(dishes.first())
        compose.onNodeWithTag(LibraryTags.OWN_LIST).performTouchInput { swipeUp() }
        compose.waitForIdle()
        compose.onNodeWithTag(LibraryTags.SELECTION_CLOSE).performClick()
        awaitNoSelection()
        compose.waitForIdle()

        assertEquals("the title bar came back as it was", whole, bounds(LibraryTags.TOP_BAR).height, 1f)
    }

    // --- Russian -----------------------------------------------------------------------------------------------------

    // Twenty-one chosen, all in the collection already: said as several — Russian's "one" form also covers 21.
    @Test
    @Config(qualifiers = "ru")
    fun twentyOneAllThereAreSaidAsSeveralInRussian() {
        val dishes = (1..21).map { own("Блюдо $it", minutesAgo = it.toLong()) }
        val dinner = collection("Ужин", *dishes.toTypedArray())
        show(LibraryView.OWN)
        longPress(dishes.first())
        compose.awaitAndChoose(LibraryTags.SELECT_ALL)
        compose.waitUntil(PATIENCE_MS) { session.selection.value?.count == 21 }
        openChooser()
        compose.onNodeWithTag(AddToCollectionTags.choice(dinner.value)).performClick()
        awaitNotice("Все эти рецепты уже в «Ужин»")
        assertEquals(dishes.map { it.id }, members(dinner))
    }

    @Test
    @Config(qualifiers = "ru")
    fun inRussian() {
        val dishes = (1..5).map { own("Блюдо $it", servings = 2, minutesAgo = it.toLong()) }
        val dinner = collection("Ужин")
        show(LibraryView.OWN)

        choose(*dishes.take(2).toTypedArray())
        assertEquals("В коллекцию", AccessibilityChecks.announcedLabel(compose.onNodeWithTag(LibraryTags.SELECTION_ADD_TO_COLLECTION).fetchSemanticsNode()))
        assertEquals("В покупки", AccessibilityChecks.announcedLabel(compose.onNodeWithTag(LibraryTags.SELECTION_ADD_TO_SHOPPING).fetchSemanticsNode()))
        assertEquals("Добавить в коллекцию", AccessibilityChecks.clickActionLabel(compose.onNodeWithTag(LibraryTags.SELECTION_ADD_TO_COLLECTION).fetchSemanticsNode()))
        assertEquals("Добавить в список покупок", AccessibilityChecks.clickActionLabel(compose.onNodeWithTag(LibraryTags.SELECTION_ADD_TO_SHOPPING).fetchSemanticsNode()))
        openChooser()
        assertEquals(listOf("Добавить 2 рецепта в…"), compose.textsOf(AddToCollectionTags.TITLE))
        assertEquals(listOf("Новая коллекция…"), compose.textsOf(AddToCollectionTags.NEW))
        compose.onNodeWithTag(AddToCollectionTags.choice(dinner.value)).performClick()
        awaitNotice("2 рецепта добавлены в «Ужин»")

        choose(*dishes.toTypedArray())
        openChooser()
        assertEquals(listOf("Добавить 5 рецептов в…"), compose.textsOf(AddToCollectionTags.TITLE))
        compose.onNodeWithTag(AddToCollectionTags.choice(dinner.value)).performClick()
        awaitNotice("3 рецепта добавлены в «Ужин»")

        choose(dishes[0], dishes[1])
        openChooser()
        compose.onNodeWithTag(AddToCollectionTags.choice(dinner.value)).performClick()
        awaitNotice("Все эти рецепты уже в «Ужин»")

        choose(dishes[0])
        openChooser()
        assertEquals(listOf("Добавить 1 рецепт в…"), compose.textsOf(AddToCollectionTags.TITLE))
        compose.onNodeWithTag(AddToCollectionTags.choice(dinner.value)).performClick()
        awaitNotice("Рецепт уже в «Ужин»")

        choose(*dishes.toTypedArray())
        compose.onNodeWithTag(LibraryTags.SELECTION_ADD_TO_SHOPPING).performClick()
        compose.awaitRemovalNotice("Добавлено 5 рецептов", "Открыть")

        awaitEntry(dishes[0].id.value, LibraryView.OWN)
        compose.openEntryMenu(dishes[0].id.value)
        assertEquals(listOf("Обновить в списке покупок"), compose.textsOf(LibraryTags.addToShopping(dishes[0].id.value)))
        compose.onNodeWithTag(LibraryTags.addToShopping(dishes[0].id.value)).performClick()
        compose.awaitRemovalNotice("Список покупок обновлён", "Открыть")
    }
}

/** Waits until the node tagged [tag] shows, and taps it. */
private fun androidx.compose.ui.test.junit4.ComposeTestRule.awaitAndChoose(tag: String) {
    waitUntil(PATIENCE_MS) { onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }
    onNodeWithTag(tag).performClick()
}
