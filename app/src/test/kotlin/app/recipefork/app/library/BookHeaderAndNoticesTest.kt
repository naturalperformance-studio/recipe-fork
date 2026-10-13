package app.recipefork.app.library

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Dp
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
import app.recipefork.app.book.TestBook
import app.recipefork.app.card.CardEnvironment
import app.recipefork.app.card.DoneMarks
import app.recipefork.app.collections.CollectionRepository
import app.recipefork.app.editor.EditorEnvironment
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.editor.SAMPLE_DICTIONARIES
import app.recipefork.app.editor.TestDevice
import app.recipefork.app.file.RecipeImport
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.timer.CardTimers
import app.recipefork.app.timer.LocalCardTimers
import app.recipefork.app.timer.TestTimers
import app.recipefork.app.ui.AppNavHost
import app.recipefork.app.ui.AppTheme
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeLanguage
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
import java.time.Instant

/**
 * The book behaves as a Material 3 list does (v0.38, T-267): each view's search field and filters scroll away with its
 * list and come back on a scroll up, the tabs staying, so that at twice the font scale on a narrow phone a recipe's
 * title is read on the first screen; a notice ends when the reader shows the other view, and at large text puts its
 * action on a line of its own; the view's floating action stays shrunk to its icon at large text; Undo of a deleted
 * recipe — or of several — brings the first one put back into view.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w320dp-h640dp")
class BookHeaderAndNoticesTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val device = TestDevice(context)
    private val active = ActiveBook(BookName.Pool, book.database)
    private val recipes = RecipeRepository(active, context.filesDir)
    private val collections = CollectionRepository(active)
    private val sessions = ViewModelStore()
    private val session = ViewModelProvider(
        sessions,
        viewModelFactory { initializer { LibrarySession(SavedStateHandle()) } },
    )[LibrarySession::class.java]
    private val marks = DoneMarks.inMemory()
    private val now = Instant.parse("2026-10-13T12:00:00Z")

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

    private fun own(title: String, minutesAgo: Long = 0): Recipe =
        Recipe.create(now.minusSeconds(minutesAgo * 60), owner = null).copy(title = title, language = RecipeLanguage.EN)
            .also { runBlocking { recipes.save(it) } }

    private fun found(title: String): Recipe =
        Recipe.create(now, owner = null).copy(title = title).inState(RecipeImport.FOUND_STATE)
            .also { assertTrue(runBlocking { recipes.addFound(it, now) }) }

    /** [count] own recipes, the newest — listed first — first. */
    private fun many(count: Int): List<Recipe> = (0 until count).map { own("Recipe $it", minutesAgo = it.toLong()) }

    private fun show(view: LibraryView) {
        session.choose(view)
        val editor = EditorEnvironment(active, device.catalog(context)) { now }
        val card = CardEnvironment(marks = marks) { device.database.dictionaryDao().ingredients() }
        compose.setContent {
            CompositionLocalProvider(LocalCardTimers provides cardTimers) {
                AppTheme { AppNavHost(session, recipes, Mode.Standard, editor = editor, card = card, collections = collections) }
            }
        }
        compose.waitForIdle()
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun await(tag: String) = compose.waitUntil(PATIENCE_MS) { exists(tag) }

    private fun shown(tag: String): Rect = compose.onNodeWithTag(tag, useUnmergedTree = true).getBoundsInRoot().let {
        Rect(it.left.value, it.top.value, it.right.value, it.bottom.value)
    }

    private fun whole(tag: String): Rect = compose.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot().let {
        Rect(it.left.value, it.top.value, it.right.value, it.bottom.value)
    }

    /** How tall the header stands above the list, in dp: its whole height less what has scrolled away. */
    private fun headerHeight(): Float = shown(LibraryTags.HEADER_AREA).height

    private fun displayed(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() &&
        compose.onNodeWithTag(tag, useUnmergedTree = true).isDisplayed()

    // --- The header scrolls away -----------------------------------------------------------------------------------

    // Going down the list takes the search field and the filters away, the tabs staying; going up, wherever the list
    // stands, brings them back before the list moves.
    @Test
    fun theSearchAndTheFiltersScrollAwayAndComeBackTheTabsStay() {
        val all = many(20)
        show(LibraryView.OWN)
        await(LibraryTags.entry(all.first().id.value))
        compose.waitForIdle()
        val full = headerHeight()
        assertTrue("the header is shown, $full dp tall", full > 48f)
        val tabs = shown(LibraryTags.SWITCH)

        repeat(3) { compose.onNodeWithTag(LibraryTags.OWN_LIST).performTouchInput { swipeUp(durationMillis = 1_000) } }
        compose.waitUntil(PATIENCE_MS) { headerHeight() < 0.5f }
        assertFalse("the search field shows", displayed(LibraryTags.SEARCH))
        // The tabs stay, whole, under where the top bar was.
        val tabsNow = shown(LibraryTags.SWITCH)
        assertEquals("the tabs' height", tabs.height, tabsNow.height, 0.5f)
        assertTrue("the tabs stand on screen", tabsNow.top >= 0f)
        assertFalse("the list still stands at its top", displayed(LibraryTags.entry(all.first().id.value)))

        // A short scroll up brings the header back while the list is still down.
        compose.onNodeWithTag(LibraryTags.OWN_LIST).performTouchInput {
            swipeDown(startY = centerY, endY = centerY + 260f, durationMillis = 1_000)
        }
        compose.waitUntil(PATIENCE_MS) { headerHeight() > full - 0.5f }
        assertTrue("the search field shows", displayed(LibraryTags.SEARCH))
        assertFalse("the list came back to its top", displayed(LibraryTags.entry(all.first().id.value)))
        compose.onNodeWithTag(LibraryTags.tab(LibraryView.OWN)).assertExists()
    }

    // The imported view's header does the same.
    @Test
    fun theImportedViewsHeaderScrollsAwayToo() {
        repeat(20) { found("Found $it") }
        show(LibraryView.MARKED)
        await(LibraryTags.MARKED_LIST)
        compose.waitForIdle()
        assertTrue(headerHeight() > 48f)
        compose.onNodeWithTag(LibraryTags.MARKED_LIST).performTouchInput { swipeUp(durationMillis = 1_000) }
        compose.waitUntil(PATIENCE_MS) { headerHeight() < 0.5f }
    }

    /**
     * At twice the font scale on a 320 dp phone, the first screen of "My recipes" holding three recipes shows a recipe's
     * title whole: below the header, inside the window, and not under the view's floating action.
     */
    private fun assertATitleIsReadOnTheFirstScreen() {
        val three = listOf(own("Plum cake", 2), own("Chicken soup", 1), own("Pancakes"))
        show(LibraryView.OWN)
        three.forEach { await(LibraryTags.entryTitle(it.id.value)) }
        await(LibraryTags.NEW)
        compose.waitForIdle()
        val header = shown(LibraryTags.HEADER_AREA)
        val window = compose.onNodeWithTag(LibraryTags.PAGER).getBoundsInRoot()
        val action = shown(LibraryTags.NEW)
        val read = three.filter { recipe ->
            val tag = LibraryTags.entryTitle(recipe.id.value)
            val title = shown(tag)
            val unclipped = whole(tag)
            title.height > 0f && title.height >= unclipped.height - 0.5f && title.top >= header.bottom - 0.5f &&
                title.bottom <= window.bottom.value + 0.5f && !title.overlaps(action)
        }
        assertTrue("no title whole on the first screen (header $header, action $action)", read.isNotEmpty())
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp", fontScale = 2f)
    fun atTwiceTheFontScaleARecipesTitleIsReadOnTheFirstScreen() = assertATitleIsReadOnTheFirstScreen()

    @Test
    @Config(qualifiers = "ru-w320dp-h640dp", fontScale = 2f)
    fun inRussianAtTwiceTheFontScaleARecipesTitleIsReadOnTheFirstScreen() = assertATitleIsReadOnTheFirstScreen()

    // --- The view's floating action at large text -------------------------------------------------------------------

    /** How wide the view's floating action is drawn. */
    private fun actionWidth(): Dp = compose.onNodeWithTag(LibraryTags.NEW).getBoundsInRoot().let { it.right - it.left }

    // At large text "New recipe" starts, and stays, shrunk to its icon: its words would cover the first entry. The
    // screen reader still hears them.
    @Test
    @Config(qualifiers = "w320dp-h640dp", fontScale = 2f)
    fun atLargeTextTheFloatingActionStartsShrunkToItsIcon() {
        many(3)
        show(LibraryView.OWN)
        await(LibraryTags.NEW)
        compose.waitForIdle()
        assertTrue("New recipe is ${actionWidth()} wide", actionWidth() <= 64.dp)
        compose.onNodeWithTag(LibraryTags.NEW).assertTextContains("New recipe")
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp", fontScale = 1.5f)
    fun atOneAndAHalfTheFloatingActionStartsShrunkToItsIcon() {
        many(3)
        show(LibraryView.OWN)
        await(LibraryTags.NEW)
        compose.waitForIdle()
        assertTrue("New recipe is ${actionWidth()} wide", actionWidth() <= 64.dp)
    }

    // At the usual font scale it shows its words at the top of the list, as before.
    @Test
    fun atTheUsualFontScaleTheFloatingActionShowsItsWords() {
        many(3)
        show(LibraryView.OWN)
        await(LibraryTags.NEW)
        compose.waitForIdle()
        assertTrue("New recipe is ${actionWidth()} wide", actionWidth() > 80.dp)
    }

    // --- A notice ends when the view changes ------------------------------------------------------------------------

    private fun deleteFromItsMenu(recipe: Recipe) {
        compose.chooseEntryAction(recipe.id.value, LibraryTags.delete(recipe.id.value))
        compose.awaitRemovalNotice("“${recipe.title}” deleted", "Undo")
    }

    private fun stored(recipe: Recipe) = runBlocking { recipes.get(recipe.id) }

    // A tap on the other tab ends the notice: the deletion is final, and nothing offers to undo it.
    @Test
    fun theOtherTabEndsTheNotice() {
        val soup = own("Soup")
        found("Borscht")
        show(LibraryView.OWN)
        await(LibraryTags.entry(soup.id.value))
        deleteFromItsMenu(soup)

        compose.onNodeWithTag(LibraryTags.tab(LibraryView.MARKED)).performClick()

        compose.waitUntil(PATIENCE_MS) { !compose.noticeShown() }
        assertEquals(LibraryView.MARKED, session.view.value)
        assertNull("still offered to undo", session.removal.value)
        compose.waitForIdle()
        assertNull(stored(soup))
    }

    // So does a swipe to the other view.
    @Test
    fun aSwipeToTheOtherViewEndsTheNotice() {
        val soup = own("Soup")
        found("Borscht")
        show(LibraryView.OWN)
        await(LibraryTags.entry(soup.id.value))
        deleteFromItsMenu(soup)

        compose.onNodeWithTag(LibraryTags.PAGER).performTouchInput { swipeRight() }

        compose.waitUntil(PATIENCE_MS) { session.view.value == LibraryView.MARKED }
        compose.waitUntil(PATIENCE_MS) { !compose.noticeShown() }
        assertNull("still offered to undo", session.removal.value)
    }

    // --- The notice's action on its own line at large text ----------------------------------------------------------

    /** At large text the notice's Undo stands below its words, on a line of its own; at the usual scale, beside them. */
    private fun assertUndoOnItsOwnLine(said: String, undo: String, ownLine: Boolean) {
        val soup = own("Chicken with potatoes in the oven")
        show(LibraryView.OWN)
        await(LibraryTags.entry(soup.id.value))
        compose.chooseEntryAction(soup.id.value, LibraryTags.delete(soup.id.value))
        compose.awaitRemovalNotice(said, undo)
        compose.waitForIdle()
        val inNotice = hasAnyAncestor(hasTestTag(LibraryTags.NOTICE))
        val words = compose.onNode(hasText(said) and inNotice, useUnmergedTree = true).getBoundsInRoot()
        val action = compose.onNode(hasText(undo) and inNotice, useUnmergedTree = true).getBoundsInRoot()
        if (ownLine) {
            assertTrue("Undo ($action) stands below the words ($words)", action.top >= words.bottom)
        } else {
            assertTrue("Undo ($action) stands beside the words ($words)", action.left >= words.right && action.top < words.bottom)
        }
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp", fontScale = 2f)
    fun atTwiceTheFontScaleTheNoticesUndoStandsOnItsOwnLine() =
        assertUndoOnItsOwnLine("“Chicken with potatoes in the oven” deleted", "Undo", ownLine = true)

    @Test
    @Config(qualifiers = "ru-w320dp-h640dp", fontScale = 2f)
    fun inRussianAtTwiceTheFontScaleTheNoticesUndoStandsOnItsOwnLine() =
        assertUndoOnItsOwnLine("Рецепт «Chicken with potatoes in the oven» удалён", "Вернуть", ownLine = true)

    @Test
    @Config(qualifiers = "w320dp-h640dp", fontScale = 1.5f)
    fun atOneAndAHalfTheNoticesUndoStandsOnItsOwnLine() =
        assertUndoOnItsOwnLine("“Chicken with potatoes in the oven” deleted", "Undo", ownLine = true)

    @Test
    fun atTheUsualFontScaleTheNoticesUndoStandsBesideItsWords() =
        assertUndoOnItsOwnLine("“Chicken with potatoes in the oven” deleted", "Undo", ownLine = false)

    // --- Undo brings the recipe into view ---------------------------------------------------------------------------

    // The recipe at the top of what the list shows is deleted; Undo puts it back above the entry now at the top, and the
    // list is brought to it.
    @Test
    fun undoOfTheTopRecipeShowsItBackInView() {
        val all = many(12)
        show(LibraryView.OWN)
        await(LibraryTags.entry(all.first().id.value))
        compose.onNodeWithTag(LibraryTags.OWN_LIST).performScrollToIndex(4)
        compose.waitForIdle()
        val top = all[4]
        assertTrue(displayed(LibraryTags.entry(top.id.value)))
        deleteFromItsMenu(top)
        compose.waitUntil(PATIENCE_MS) { !exists(LibraryTags.entry(top.id.value)) }

        compose.takeNoticeAction("Undo")

        compose.waitUntil(PATIENCE_MS) { displayed(LibraryTags.entryTitle(top.id.value)) }
        compose.waitUntil(PATIENCE_MS) { session.restored.value == null }
    }

    // Several deleted together from the selection: Undo brings the first of them into view.
    @Test
    fun undoOfSeveralDeletedShowsTheFirstBackInView() {
        val all = many(12)
        show(LibraryView.OWN)
        await(LibraryTags.entry(all.first().id.value))
        compose.onNodeWithTag(LibraryTags.OWN_LIST).performScrollToIndex(4)
        compose.waitForIdle()
        val (first, second) = all[4] to all[5]
        compose.onNodeWithTag(LibraryTags.entry(first.id.value)).performTouchInput { longClick() }
        compose.waitUntil(PATIENCE_MS) { exists(LibraryTags.SELECTION_BAR) }
        compose.onNodeWithTag(LibraryTags.entry(second.id.value)).performClick()
        compose.waitUntil(PATIENCE_MS) { session.selection.value?.count == 2 }
        compose.onNodeWithTag(LibraryTags.SELECTION_MENU).performClick()
        await(LibraryTags.SELECTION_DELETE)
        compose.onNodeWithTag(LibraryTags.SELECTION_DELETE).performClick()
        compose.awaitRemovalNotice("2 recipes deleted", "Undo")
        compose.waitUntil(PATIENCE_MS) { !exists(LibraryTags.entry(first.id.value)) }

        compose.takeNoticeAction("Undo")

        compose.waitUntil(PATIENCE_MS) { displayed(LibraryTags.entryTitle(first.id.value)) }
    }
}
