package app.recipefork.app.library

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.Mode
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.OwnRecipeRow
import app.recipefork.app.book.TestBook
import app.recipefork.app.card.CardEnvironment
import app.recipefork.app.card.CardTags
import app.recipefork.app.card.IngredientEntries
import app.recipefork.app.card.awaitCard
import app.recipefork.app.data.HeldLastBookView
import app.recipefork.app.dictionary.IngredientEntryEntity
import app.recipefork.app.dictionary.TagEntity
import app.recipefork.app.dictionary.TagSource
import app.recipefork.app.editor.EditorEnvironment
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.editor.textsOf
import app.recipefork.app.file.RecipeImport
import app.recipefork.app.recipe.Listed
import app.recipefork.app.kitchen.Kitchen
import app.recipefork.app.recipe.ListingParams
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.ui.AppNavHost
import app.recipefork.core.recipe.Recipe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import kotlin.math.abs

/**
 * The book's header and its views (FLOW-034): a top bar with the book's title and menu that scrolls away as a list goes
 * down and returns as it goes up, the tabs of the two views pinned beneath it, the views swiped between, each keeping
 * its own place, the view's action floating at the bottom right, and an indicator while a view's list is read for long.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class BookHeaderTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val active = ActiveBook(BookName.Pool, book.database)
    private val recipes = RecipeRepository(active)
    private val lastView = HeldLastBookView()
    private val session = LibrarySession(SavedStateHandle(), lastView)
    private val now = Instant.parse("2026-10-04T12:00:00Z")
    private val card = CardEnvironment(ingredients = IngredientEntries { emptyList() })
    private val editor = EditorEnvironment(
        active,
        object : TagSource {
            override suspend fun tags() = emptyList<TagEntity>()
        },
    ) { now }

    @Before
    fun waitForTheBook() = compose.registerIdlingResource(book.idling)

    @After
    fun close() {
        compose.unregisterIdlingResource(book.idling)
        book.close()
    }

    /** [count] recipes of [view], the first the newest. */
    private fun recipesOf(view: LibraryView, count: Int): List<Recipe> = (0 until count).map { n ->
        val recipe = Recipe.create(now.minusSeconds(n * 60L), owner = null).copy(title = "${view.name} $n")
        runBlocking {
            if (view == LibraryView.OWN) {
                recipes.save(recipe)
            } else {
                recipes.addFound(recipe.inState(RecipeImport.FOUND_STATE), now.minusSeconds(n * 60L))
            }
        }
        recipe
    }

    private fun show(view: LibraryView, mode: Mode = Mode.Standard, repository: RecipeRepository = recipes): StateRestorationTester {
        session.choose(view)
        val tester = StateRestorationTester(compose)
        tester.setContent { MaterialTheme { AppNavHost(session, repository, mode, card = card, editor = editor) } }
        compose.waitForIdle()
        return tester
    }

    private fun list(view: LibraryView) = if (view == LibraryView.OWN) LibraryTags.OWN_LIST else LibraryTags.MARKED_LIST

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    private fun shown(recipe: Recipe) = exists(LibraryTags.entry(recipe.id.value))

    private fun awaitShown(recipe: Recipe) = compose.waitUntil(PATIENCE_MS) { shown(recipe) }

    private fun bounds(tag: String): Rect = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    /** How much of the list of [view] shows within the pager, in width: none when its page is not composed. */
    private fun shownWidth(view: LibraryView): Float {
        val pager = bounds(LibraryTags.PAGER)
        return compose.onAllNodes(hasTestTag(list(view))).fetchSemanticsNodes()
            .maxOfOrNull { it.boundsInRoot.intersect(pager).width.coerceAtLeast(0f) } ?: 0f
    }

    /** The pager stands on [view]: its list fills the pager, the other's shows not at all (a pixel's rounding aside). */
    private fun assertOn(view: LibraryView) {
        val other = if (view == LibraryView.OWN) LibraryView.MARKED else LibraryView.OWN
        compose.waitUntil(PATIENCE_MS) {
            session.view.value == view && shownWidth(view) >= bounds(LibraryTags.PAGER).width - 1f && shownWidth(other) <= 1f
        }
        compose.onNodeWithTag(LibraryTags.tab(view)).assertIsSelected()
        compose.onNodeWithTag(LibraryTags.tab(other)).assertIsNotSelected()
    }

    private fun swipePager(left: Boolean) {
        compose.onNodeWithTag(LibraryTags.PAGER).performTouchInput { if (left) swipeLeft() else swipeRight() }
        compose.waitForIdle()
    }

    // Scenario 1: swiping moves between the views, the tabs following; tapping a tab moves too.
    @Test
    fun swipingMovesBetweenTheViewsAndTheTabsFollow() {
        val own = recipesOf(LibraryView.OWN, 1).single()
        val marked = recipesOf(LibraryView.MARKED, 1).single()
        show(LibraryView.MARKED)
        assertOn(LibraryView.MARKED)
        awaitShown(marked)
        swipePager(left = true)
        assertOn(LibraryView.OWN)
        awaitShown(own)
        swipePager(left = false)
        assertOn(LibraryView.MARKED)
        // Past the first view and the last, a swipe goes nowhere.
        swipePager(left = false)
        assertOn(LibraryView.MARKED)
        compose.onNodeWithTag(LibraryTags.tab(LibraryView.OWN)).performClick()
        compose.waitForIdle()
        assertOn(LibraryView.OWN)
        compose.onNodeWithTag(LibraryTags.tab(LibraryView.MARKED)).performClick()
        compose.waitForIdle()
        assertOn(LibraryView.MARKED)
        // Every view shown is kept as the one last left.
        swipePager(left = true)
        assertOn(LibraryView.OWN)
        assertEquals(LibraryView.OWN, lastView.read())
    }

    // Each view keeps its own place: across a swipe to the other and back, a card opened and left, and a
    // configuration change.
    @Test
    fun eachViewKeepsItsOwnPlace() {
        val own = recipesOf(LibraryView.OWN, 40)
        val marked = recipesOf(LibraryView.MARKED, 40)
        val tester = show(LibraryView.MARKED)
        awaitShown(marked[0])
        compose.onNodeWithTag(LibraryTags.MARKED_LIST).performScrollToIndex(25)
        awaitShown(marked[25])
        swipePager(left = true)
        assertOn(LibraryView.OWN)
        awaitShown(own[0])
        compose.onNodeWithTag(LibraryTags.OWN_LIST).performScrollToIndex(12)
        awaitShown(own[12])
        assertFalse(shown(own[0]))

        swipePager(left = false)
        assertOn(LibraryView.MARKED)
        awaitShown(marked[25])
        assertFalse("the marked list lost its place", shown(marked[0]))

        // A card opened from the marked view and left returns to it, where it was.
        compose.onNodeWithTag(LibraryTags.entry(marked[25].id.value)).performClick()
        compose.awaitCard()
        compose.onNodeWithTag(CardTags.BACK).performClick()
        assertOn(LibraryView.MARKED)
        awaitShown(marked[25])
        assertFalse("the marked list lost its place across the card", shown(marked[0]))
        compose.onNodeWithTag(LibraryTags.tab(LibraryView.OWN)).performClick()
        assertOn(LibraryView.OWN)
        awaitShown(own[12])
        assertFalse("the own list lost its place across the card", shown(own[0]))

        // A configuration change keeps both places.
        tester.emulateSavedInstanceStateRestore()
        assertOn(LibraryView.OWN)
        awaitShown(own[12])
        assertFalse("the own list lost its place across the change", shown(own[0]))
        swipePager(left = false)
        assertOn(LibraryView.MARKED)
        awaitShown(marked[25])
        assertFalse("the marked list lost its place across the change", shown(marked[0]))
    }

    private fun scrollOwnList(down: Boolean, long: Boolean) {
        compose.onNodeWithTag(LibraryTags.OWN_LIST).performTouchInput {
            val span = if (long) height * 0.8f else height * 0.2f
            if (down) {
                swipeUp(startY = centerY + span / 2, endY = centerY - span / 2, durationMillis = 600)
            } else {
                swipeDown(startY = centerY - span / 2, endY = centerY + span / 2, durationMillis = 600)
            }
        }
        compose.waitForIdle()
    }

    /** Whether the action shows its words beside its icon: wider than tall. Shrunk to its icon it is as wide as tall. */
    private fun actionExpanded(tag: String): Boolean = bounds(tag).let { it.width > it.height + 8f }

    private fun actionCollapsed(tag: String): Boolean = bounds(tag).let { abs(it.width - it.height) < 1f }

    // Scenario 5: the title bar scrolls away as the list goes down and returns as it goes up; the tabs stay, and the
    // search scrolls away and returns with the bar (v0.38); the action floats at the bottom right, shrinking to its icon
    // going down and showing its words going up.
    @Test
    fun theTitleBarScrollsAwayAndReturnsAndTheActionShrinksAndGrows() {
        recipesOf(LibraryView.OWN, 40)
        show(LibraryView.OWN)
        compose.waitUntil(PATIENCE_MS) { exists(LibraryTags.NEW) }
        val root = bounds(LibraryTags.PAGER)
        val barHeight = bounds(LibraryTags.TOP_BAR).height
        assertTrue(barHeight > 0f)
        // At the bottom right, its words beside its icon, and still read as "New recipe" by the screen reader.
        val action = bounds(LibraryTags.NEW)
        assertTrue("$action is not at the right of $root", root.right - action.right in 0f..root.width * 0.1f)
        assertTrue("$action is not at the bottom of $root", root.bottom - action.bottom in 0f..root.height * 0.1f)
        assertTrue("${bounds(LibraryTags.NEW)} is not expanded", actionExpanded(LibraryTags.NEW))
        assertEquals(listOf("New recipe"), compose.textsOf(LibraryTags.NEW))

        scrollOwnList(down = true, long = true)
        scrollOwnList(down = true, long = true)
        compose.waitUntil(PATIENCE_MS) { bounds(LibraryTags.TOP_BAR).height < 1f }
        compose.waitUntil(PATIENCE_MS) { actionCollapsed(LibraryTags.NEW) }
        assertEquals(listOf("New recipe"), compose.textsOf(LibraryTags.NEW))
        // The tabs stay on screen; the search has gone with the bar (v0.38).
        compose.onNodeWithTag(LibraryTags.SWITCH).assertIsDisplayed()
        compose.onNodeWithTag(LibraryTags.SEARCH).assertIsNotDisplayed()
        assertTrue(bounds(LibraryTags.SWITCH).top < 1f)

        // A little way back up, not to the top: the bar returns and the action shows its words.
        scrollOwnList(down = false, long = false)
        compose.waitUntil(PATIENCE_MS) { abs(bounds(LibraryTags.TOP_BAR).height - barHeight) < 1f }
        compose.waitUntil(PATIENCE_MS) { actionExpanded(LibraryTags.NEW) }
        compose.onNodeWithTag(LibraryTags.TITLE).assertIsDisplayed()
        // The search field and the filters come back after the bar, as far as the scroll goes (v0.38).
        compose.waitUntil(PATIENCE_MS) { bounds(LibraryTags.HEADER_AREA).height > 0f }
        val range = compose.onNodeWithTag(LibraryTags.OWN_LIST).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        assertTrue("the list went back to its top", range.value() > 0f)

        // Down again, then all the way to the top: at the top the action shows its words.
        scrollOwnList(down = true, long = true)
        compose.waitUntil(PATIENCE_MS) { actionCollapsed(LibraryTags.NEW) }
        compose.onNodeWithTag(LibraryTags.OWN_LIST).performScrollToIndex(0)
        compose.waitUntil(PATIENCE_MS) { actionExpanded(LibraryTags.NEW) }
    }

    // The list's last entry, scrolled to, stands clear of the action floating over the list's end.
    @Test
    fun theLastEntryStandsClearOfTheAction() {
        val own = recipesOf(LibraryView.OWN, 12)
        show(LibraryView.OWN)
        awaitShown(own[0])
        compose.onNodeWithTag(LibraryTags.OWN_LIST).performScrollToIndex(own.lastIndex)
        compose.waitUntil(PATIENCE_MS) { shown(own.last()) }
        compose.onNodeWithTag(LibraryTags.OWN_LIST).performTouchInput { swipeUp() }
        compose.waitForIdle()
        val last = bounds(LibraryTags.entry(own.last().id.value))
        assertTrue("$last runs under the action", last.bottom <= bounds(LibraryTags.NEW).top)
    }

    // The full-width button above the list is gone: the own view offers a new recipe from its action alone, and an
    // empty book keeps its own offer in place of the action.
    @Test
    fun theOwnViewOffersANewRecipeFromItsActionAndTheEmptyBookFromItsOwnOffer() {
        show(LibraryView.OWN)
        compose.waitUntil(PATIENCE_MS) { exists(LibraryTags.OWN_EMPTY) }
        assertTrue(exists(LibraryTags.WRITE))
        assertFalse(exists(LibraryTags.NEW))
        recipesOf(LibraryView.OWN, 1)
        compose.waitUntil(PATIENCE_MS) { exists(LibraryTags.NEW) }
        assertFalse(exists(LibraryTags.WRITE))
        assertEquals(1, compose.onAllNodes(hasTestTag(LibraryTags.NEW)).fetchSemanticsNodes().size)
        // It stands over the list, not above it.
        assertTrue(bounds(LibraryTags.NEW).top > bounds(LibraryTags.HEADER).bottom)
    }

    // The marked view's import, in serverless mode, is its action at the bottom right once anything is marked; with
    // nothing marked the empty state offers it. In standard mode the marked view has no action.
    @Test
    fun theMarkedViewImportsFromItsActionInServerlessModeOnly() {
        show(LibraryView.MARKED, Mode.Serverless)
        compose.waitUntil(PATIENCE_MS) { exists(LibraryTags.MARKED_EMPTY) && exists(LibraryTags.IMPORT) }
        // The empty state's own offer stands under its statement, in the empty state, clear of the action's place at
        // the bottom.
        assertTrue(bounds(LibraryTags.IMPORT).top > bounds(LibraryTags.emptyHeadline(LibraryTags.MARKED_EMPTY)).bottom)
        assertEquals(1, compose.onAllNodes(hasTestTag(LibraryTags.IMPORT) and hasAnyAncestor(hasTestTag(LibraryTags.MARKED_EMPTY))).fetchSemanticsNodes().size)
        bounds(LibraryTags.PAGER).let { pager -> assertTrue(pager.bottom - bounds(LibraryTags.IMPORT).bottom > pager.height * 0.1f) }
        recipesOf(LibraryView.MARKED, 1)
        compose.waitUntil(PATIENCE_MS) { !exists(LibraryTags.MARKED_EMPTY) && exists(LibraryTags.IMPORT) }
        val pager = bounds(LibraryTags.PAGER)
        val action = bounds(LibraryTags.IMPORT)
        assertTrue("$action is not at the bottom of $pager", pager.bottom - action.bottom in 0f..pager.height * 0.1f)
        assertTrue(actionExpanded(LibraryTags.IMPORT))
        // Its words are short (T-223); the screen reader hears the whole action.
        assertEquals(listOf("Import"), compose.textsOf(LibraryTags.IMPORT))
        assertEquals("Import a recipe file", compose.onNodeWithTag(LibraryTags.IMPORT).fetchSemanticsNode().config[SemanticsActions.OnClick].label)
    }

    @Test
    fun inStandardModeTheMarkedViewHasNoAction() {
        recipesOf(LibraryView.MARKED, 1)
        show(LibraryView.MARKED, Mode.Standard)
        compose.waitUntil(PATIENCE_MS) { exists(LibraryTags.MARKED_LIST) }
        assertFalse(exists(LibraryTags.IMPORT))
        assertFalse(exists(LibraryTags.NEW))
    }

    /** A book whose own list is read only once [open] is set: what it reads then is an empty view. */
    private inner class HeldBack : RecipeRepository(active) {
        val open = MutableStateFlow(false)

        override fun ownListed(
            params: Flow<ListingParams>,
            kitchen: Flow<Kitchen?>,
            dictionary: suspend () -> List<IngredientEntryEntity>,
        ): Flow<Listed<OwnRecipeRow>> = flow {
            open.first { it }
            emitAll(params.map { Listed(emptyList<OwnRecipeRow>(), viewIsEmpty = true, params = it) })
        }
    }

    private fun loading() = exists(LibraryTags.LOADING)

    // Scenario 7: a list read for long shows an indicator, once a moment has passed, and not once it is read.
    @Test
    fun aListReadForLongShowsAnIndicator() {
        val held = HeldBack()
        compose.mainClock.autoAdvance = false
        show(LibraryView.OWN, repository = held)
        compose.mainClock.advanceTimeBy(LOADING_DELAY_MS - 100)
        assertFalse("the indicator shows at once", loading())
        compose.mainClock.advanceTimeBy(200)
        assertTrue("no indicator while the list is read", loading())
        compose.onNodeWithTag(LibraryTags.LOADING).assertIsDisplayed()
        assertEquals(
            listOf("Opening the recipes"),
            compose.onNodeWithTag(LibraryTags.LOADING).fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription),
        )
        held.open.value = true
        compose.mainClock.autoAdvance = true
        compose.waitUntil(PATIENCE_MS) { exists(LibraryTags.OWN_EMPTY) }
        assertFalse(loading())
    }

    // A list read quickly shows no flash of the indicator.
    @Test
    fun aListReadQuicklyShowsNoIndicator() {
        val held = HeldBack()
        compose.mainClock.autoAdvance = false
        show(LibraryView.OWN, repository = held)
        compose.mainClock.advanceTimeBy(LOADING_DELAY_MS / 3)
        held.open.value = true
        repeat(60) {
            compose.mainClock.advanceTimeByFrame()
            assertFalse("the indicator flashed", loading())
        }
        assertTrue(exists(LibraryTags.OWN_EMPTY))
        compose.mainClock.autoAdvance = true
    }
}
