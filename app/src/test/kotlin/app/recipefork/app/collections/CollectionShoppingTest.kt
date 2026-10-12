package app.recipefork.app.collections

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
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
import app.recipefork.app.card.DoneMarks
import app.recipefork.app.card.HeldScale
import app.recipefork.app.card.IngredientEntries
import app.recipefork.app.data.HeldUsualServings
import app.recipefork.app.editor.EditorEnvironment
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.editor.SAMPLE_DICTIONARIES
import app.recipefork.app.editor.TestDevice
import app.recipefork.app.file.RecipeImport
import app.recipefork.app.library.LibrarySession
import app.recipefork.app.library.LibraryTags
import app.recipefork.app.library.LibraryView
import app.recipefork.app.library.awaitRemovalNotice
import app.recipefork.app.library.takeNoticeAction
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
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.scaling.Rational
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.IOException
import java.time.Instant

/**
 * A whole collection into the shopping list (v0.36, T-257, scenario 3): the collection's ⋮ — own or imported, while it
 * has recipes — puts every recipe of it on the list at the count its card would open with; one listed already keeps its
 * count. One notice says how many were added, with Open, which opens the list above the collection; back from the list
 * returns to it.
 */
@RunWith(AndroidJUnit4::class)
class CollectionShoppingTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val device = TestDevice(context)
    private val active = ActiveBook(BookName.Pool, book.database)
    /** Recipes whose reads fail — as a corrupt row or a refusing storage would. */
    private val unreadable = mutableSetOf<RecipeId>()

    /** The book's recipes, the ones [unreadable] holds failing to read. */
    private val recipes = object : RecipeRepository(active, context.filesDir) {
        override fun observe(id: RecipeId): Flow<Recipe?> =
            if (id in unreadable) flow { throw IOException("the row does not read back") } else super.observe(id)
    }
    private val now = Instant.parse("2026-10-12T12:00:00Z")
    private var ticks = 0L
    private val collections = CollectionRepository(active) { now.plusSeconds(ticks++) }
    private val sessions = ViewModelStore()
    private val session = ViewModelProvider(
        sessions,
        viewModelFactory { initializer { LibrarySession(SavedStateHandle()) } },
    )[LibrarySession::class.java]
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
    }

    private fun own(title: String, servings: Int?, minutesAgo: Long = 0): RecipeId = runBlocking {
        Recipe.create(now.minusSeconds(minutesAgo * 60), owner = null).copy(title = title, servings = servings).also { recipes.save(it) }.id
    }

    private fun found(title: String, servings: Int?): RecipeId = runBlocking {
        Recipe.create(now, owner = null).copy(title = title, servings = servings, statedAuthor = "anna").inState(RecipeImport.FOUND_STATE)
            .also { assertTrue(recipes.addFound(it, now)) }.id
    }

    private fun collection(name: String, members: List<RecipeId> = emptyList()): CollectionId = runBlocking {
        collections.create(name)!!.also { id -> members.forEach { collections.addTo(it, listOf(id)) } }
    }

    private fun imported(name: String, members: List<RecipeId>): CollectionId = runBlocking {
        CollectionId.mint().also { assertEquals(ImportedCollection.CREATED, collections.putImported(it, name, "anna", members)) }
    }

    private fun show(view: LibraryView, usual: Rational? = null) {
        session.choose(view)
        val editor = EditorEnvironment(active, device.catalog(context)) { now }
        val entries = IngredientEntries { device.database.dictionaryDao().ingredients() }
        val card = CardEnvironment(HeldUsualServings(usual), marks = DoneMarks.inMemory(), shopping = store, ingredients = entries)
        val shopping = ShoppingEnvironment(store, ingredients = entries)
        compose.setContent {
            CompositionLocalProvider(LocalCardTimers provides cardTimers) {
                AppTheme { AppNavHost(session, recipes, Mode.Serverless, editor = editor, card = card, shopping = shopping, collections = collections) }
            }
        }
        compose.waitForIdle()
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    private fun await(tag: String) = compose.waitUntil(PATIENCE_MS) { exists(tag) }

    private fun awaitGone(tag: String) = compose.waitUntil(PATIENCE_MS) { !exists(tag) }

    /** Opens the collection [id] from the view's row, and waits until its recipes, or its empty state, show. */
    private fun open(id: CollectionId, view: LibraryView = LibraryView.OWN) {
        val list = if (view == LibraryView.OWN) LibraryTags.OWN_LIST else LibraryTags.MARKED_LIST
        compose.waitUntil(PATIENCE_MS) {
            try {
                compose.onNodeWithTag(list).performScrollToNode(hasTestTag(CollectionScreenTags.card(id.value)))
                true
            } catch (e: AssertionError) {
                false
            }
        }
        compose.onNodeWithTag(CollectionScreenTags.card(id.value)).performSemanticsAction(SemanticsActions.OnClick)
        awaitScreen()
    }

    private fun awaitScreen() {
        await(CollectionScreenTags.SCREEN)
        compose.waitUntil(PATIENCE_MS) {
            (exists(CollectionScreenTags.LIST) || exists(CollectionScreenTags.EMPTY)) && !exists(LibraryTags.PAGER) && !exists(ShoppingTags.SCREEN)
        }
    }

    private fun openMenu() {
        compose.onNodeWithTag(CollectionScreenTags.MENU).performClick()
        await(CollectionScreenTags.DELETE)
    }

    private fun addAll() {
        openMenu()
        compose.onNodeWithTag(CollectionScreenTags.ADD_ALL_TO_SHOPPING).performClick()
        awaitGone(CollectionScreenTags.DELETE)
    }

    // Three recipes, one of them listed already at another count: the other two go on the list after it, each at the
    // count its card would open with — the authored one, or the one the reader left its card at this session — and the
    // listed one keeps its count. "Added 2 recipes", with Open: the list opens above the collection, and back from it
    // returns there.
    @Test
    fun everyRecipeGoesOnTheListAtItsCardsCountAndOpenShowsTheListAboveTheCollection() {
        val soup = own("Soup", servings = 4, minutesAgo = 2)
        val bread = own("Bread", servings = 2, minutesAgo = 1)
        val cake = own("Cake", servings = 3)
        val menu = collection("New Year's menu", listOf(soup, bread, cake))
        store = HeldShopping(ShoppingList(listOf(ListedRecipe(pool, bread, Rational.of(6)))))
        // The cake's card was left at five servings this session: it would open at five.
        session.cards.holdScale(cake, HeldScale(Rational.of(5)))
        show(LibraryView.OWN)
        open(menu)

        openMenu()
        compose.onNodeWithTag(CollectionScreenTags.ADD_ALL_TO_SHOPPING).assertTextEquals("Add all to shopping list")
        AccessibilityChecks.assertTouchTargets(compose)
        // Offered first, above Rename and Delete.
        val bounds = { tag: String -> compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot }
        assertTrue(bounds(CollectionScreenTags.ADD_ALL_TO_SHOPPING).bottom <= bounds(CollectionScreenTags.RENAME).top)
        compose.onNodeWithTag(CollectionScreenTags.ADD_ALL_TO_SHOPPING).performClick()

        compose.awaitRemovalNotice("Added 2 recipes", "Open")
        assertEquals(
            listOf(ListedRecipe(pool, bread, Rational.of(6)), ListedRecipe(pool, soup, Rational.of(4)), ListedRecipe(pool, cake, Rational.of(5))),
            store.list.value.recipes,
        )

        compose.takeNoticeAction("Open")
        compose.waitUntil(PATIENCE_MS) { exists(ShoppingTags.SCREEN) && !exists(CollectionScreenTags.SCREEN) && !exists(ShoppingTags.LOADING) }
        assertTrue(session.shopping.value)
        assertEquals(menu.value, session.collection.value)
        // Its back says where it returns: to the collection.
        assertEquals("Back to the collection", backLabel())

        compose.onNodeWithTag(ShoppingTags.BACK).performClick()
        awaitScreen()
        await(LibraryTags.entry(soup.value))
        assertFalse(session.shopping.value)
        assertEquals(menu.value, session.collection.value)

        // Opened from the book, the list's back still says it returns to the book.
        compose.onNodeWithTag(CollectionScreenTags.BACK).performClick()
        await(LibraryTags.PAGER)
        compose.runOnIdle { session.openShopping() }
        compose.waitUntil(PATIENCE_MS) { exists(ShoppingTags.SCREEN) && !exists(LibraryTags.PAGER) }
        assertEquals("Back to the book", backLabel())
    }

    private fun backLabel() = AccessibilityChecks.clickActionLabel(compose.onNodeWithTag(ShoppingTags.BACK).fetchSemanticsNode())

    // The newest statement replaces the one shown: a recipe taken out of the collection right after the notice of the
    // whole collection added says so at once, not once that notice has timed out.
    @Test
    fun aRemovalRightAfterTheNoticeReplacesItAtOnce() {
        val soup = own("Soup", servings = 4, minutesAgo = 1)
        val bread = own("Bread", servings = 2)
        val menu = collection("Menu", listOf(soup, bread))
        show(LibraryView.OWN)
        open(menu)
        addAll()
        compose.awaitRemovalNotice("Added 2 recipes", "Open")
        val shown = compose.mainClock.currentTime

        compose.onNodeWithTag(LibraryTags.actions(bread.value)).performSemanticsAction(SemanticsActions.OnClick)
        await(CollectionScreenTags.remove(bread.value))
        compose.onNodeWithTag(CollectionScreenTags.remove(bread.value)).performClick()

        compose.awaitRemovalNotice("“Bread” removed from the collection", "Undo")
        val waited = compose.mainClock.currentTime - shown
        assertTrue("the removal waited $waited ms behind the notice", waited < WELL_BEFORE_TIMEOUT_MS)
        compose.takeNoticeAction("Undo")
        compose.waitUntil(PATIENCE_MS) { runBlocking { collections.get(menu)!!.recipes } == listOf(soup, bread) }
    }

    // None added only because the recipes could not be read: the notice says so — not that they are all listed — and
    // offers nothing to open. A recipe listed already beside it does not make it "all listed".
    @Test
    fun recipesThatCannotBeReadAreNotSaidToBeListed() {
        val soup = own("Soup", servings = 4, minutesAgo = 1)
        val bread = own("Bread", servings = 2)
        val menu = collection("Menu", listOf(soup, bread))
        val listed = ShoppingList(listOf(ListedRecipe(pool, soup, Rational.of(8))))
        store = HeldShopping(listed)
        show(LibraryView.OWN)
        open(menu)
        unreadable += bread

        addAll()

        compose.waitUntil(PATIENCE_MS) {
            compose.onAllNodes(hasText("Could not read the recipes; nothing was added"), useUnmergedTree = true).fetchSemanticsNodes().size == 1
        }
        assertEquals(0, compose.onAllNodes(hasText("Open"), useUnmergedTree = true).fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodes(hasText("All these recipes are already in the shopping list"), useUnmergedTree = true).fetchSemanticsNodes().size)
        assertEquals(listed, store.list.value)

        // Readable again, it is added.
        unreadable.clear()
        addAll()
        compose.awaitRemovalNotice("Added 1 recipe", "Open")
        assertEquals(listOf(ListedRecipe(pool, soup, Rational.of(8)), ListedRecipe(pool, bread, Rational.of(2))), store.list.value.recipes)
    }

    // The reader's usual count sets where a card opens, so it sets the count added; a recipe with no count goes on with none.
    @Test
    fun theUsualCountIsTheOpeningCountAndARecipeWithNoneGoesOnWithNone() {
        val soup = own("Soup", servings = 4, minutesAgo = 1)
        val tea = own("Tea", servings = null)
        val menu = collection("Menu", listOf(soup, tea))
        show(LibraryView.OWN, usual = Rational.of(3))
        open(menu)

        addAll()

        compose.awaitRemovalNotice("Added 2 recipes", "Open")
        assertEquals(listOf(ListedRecipe(pool, soup, Rational.of(3)), ListedRecipe(pool, tea, null)), store.list.value.recipes)
    }

    // Every recipe on the list already: nothing changes, and the notice says so, still with Open.
    @Test
    fun aCollectionAllListedChangesNothingAndSaysSo() {
        val soup = own("Soup", servings = 4)
        val menu = collection("Menu", listOf(soup))
        val listed = ShoppingList(listOf(ListedRecipe(pool, soup, Rational.of(8))))
        store = HeldShopping(listed)
        show(LibraryView.OWN)
        open(menu)

        addAll()

        compose.awaitRemovalNotice("All these recipes are already in the shopping list", "Open")
        assertEquals(listed, store.list.value)
    }

    // An empty collection does not offer it; an imported one with recipes does, as an own one.
    @Test
    fun anEmptyCollectionDoesNotOfferItAndAnImportedOneDoes() {
        own("Soup", servings = 2)
        val salads = collection("Salads")
        show(LibraryView.OWN)
        open(salads)
        await(CollectionScreenTags.EMPTY)
        openMenu()
        assertFalse(exists(CollectionScreenTags.ADD_ALL_TO_SHOPPING))
        compose.onNodeWithTag(CollectionScreenTags.MENU).performClick()
        awaitGone(CollectionScreenTags.DELETE)
        compose.onNodeWithTag(CollectionScreenTags.BACK).performClick()
        await(LibraryTags.PAGER)

        val borscht = found("Borscht", servings = 6)
        val annas = imported("Anna's dinners", listOf(borscht))
        compose.runOnIdle { session.choose(LibraryView.MARKED) }
        open(annas, LibraryView.MARKED)
        addAll()
        compose.awaitRemovalNotice("Added 1 recipe", "Open")
        assertEquals(listOf(ListedRecipe(pool, borscht, Rational.of(6))), store.list.value.recipes)
    }

    @Test
    @Config(qualifiers = "ru")
    fun inRussian() {
        val soup = own("Суп", servings = 4, minutesAgo = 1)
        val bread = own("Хлеб", servings = 2)
        val menu = collection("Меню", listOf(soup, bread))
        show(LibraryView.OWN)
        open(menu)

        openMenu()
        compose.onNodeWithTag(CollectionScreenTags.ADD_ALL_TO_SHOPPING).assertTextEquals("Добавить все в список покупок")
        compose.onNodeWithTag(CollectionScreenTags.ADD_ALL_TO_SHOPPING).performClick()
        compose.awaitRemovalNotice("Добавлено 2 рецепта", "Открыть")
        compose.takeNoticeAction("Открыть")
        await(ShoppingTags.SCREEN)
        assertEquals("Вернуться к коллекции", backLabel())

        compose.onNodeWithTag(ShoppingTags.BACK).performClick()
        awaitScreen()
        addAll()
        compose.awaitRemovalNotice("Все эти рецепты уже в списке покупок", "Открыть")

        unreadable += listOf(soup, bread)
        compose.runOnIdle { store.update { ShoppingList() } }
        addAll()
        compose.waitUntil(PATIENCE_MS) {
            compose.onAllNodes(hasText("Не удалось прочитать рецепты, ничего не добавлено"), useUnmergedTree = true).fetchSemanticsNodes().size == 1
        }
    }

    private companion object {
        /** Well before a Long notice — ten seconds — times out. */
        const val WELL_BEFORE_TIMEOUT_MS = 4_000L
    }
}
