package app.recipefork.app.library

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
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
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
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
import androidx.test.platform.app.InstrumentationRegistry
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
import org.junit.rules.ExternalResource
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
    /**
     * The screen in touch mode, as a phone's is once touched, where focus moves only as the reader or the application
     * moves it; Robolectric starts a window outside it. Set before the activity's window is added, which takes the mode
     * from the window session.
     */
    @get:Rule(order = 0)
    val touchMode = object : ExternalResource() {
        override fun before() = InstrumentationRegistry.getInstrumentation().setInTouchMode(true)

        override fun after() = InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
    }

    @get:Rule(order = 1)
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
        // The newest, first in the list; the others may stand below the first screen under the book's title (v0.42).
        await(LibraryTags.entryTitle(three.last().id.value))
        await(LibraryTags.NEW)
        compose.waitForIdle()
        val header = shown(LibraryTags.HEADER_AREA)
        val window = compose.onNodeWithTag(LibraryTags.PAGER).getBoundsInRoot()
        val action = shown(LibraryTags.NEW)
        val read = three.filter { recipe ->
            val tag = LibraryTags.entryTitle(recipe.id.value)
            if (!exists(tag)) return@filter false
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

        compose.onNodeWithTag(LibraryTags.PAGER).performTouchInput { swipeLeft() }

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

    // A recipe put back among those shown moves nothing: the list stays where it stood, its first entry with it.
    @Test
    fun undoOfARecipeInViewMovesNothing() {
        val all = many(12)
        show(LibraryView.OWN)
        await(LibraryTags.entry(all.first().id.value))
        compose.waitForIdle()
        val first = LibraryTags.entry(all.first().id.value)
        val before = shown(first)
        val second = all[1]
        deleteFromItsMenu(second)
        compose.waitUntil(PATIENCE_MS) { !exists(LibraryTags.entry(second.id.value)) }

        compose.takeNoticeAction("Undo")

        compose.waitUntil(PATIENCE_MS) { displayed(LibraryTags.entryTitle(second.id.value)) }
        compose.waitUntil(PATIENCE_MS) { session.restored.value == null }
        compose.waitForIdle()
        assertTrue("the first entry left the screen", displayed(first))
        assertEquals("the first entry's top", before.top, shown(first).top, 0.5f)
    }

    // A recipe put back that a search hides is not sought: a search changed lets it go, and the list does not jump to it
    // once it is listed again.
    @Test
    fun aRecipePutBackThatASearchHidesIsLetGo() {
        val all = many(12)
        show(LibraryView.OWN)
        await(LibraryTags.entry(all.first().id.value))
        val gone = all[1]
        deleteFromItsMenu(gone)
        compose.onNodeWithTag(LibraryTags.SEARCH).performTextReplacement("Recipe 11")
        compose.waitUntil(PATIENCE_MS) { exists(LibraryTags.entry(all[11].id.value)) && !exists(LibraryTags.entry(all[0].id.value)) }

        compose.takeNoticeAction("Undo")
        compose.waitUntil(PATIENCE_MS) { stored(gone) != null && session.restored.value == gone.id.value }

        compose.onNodeWithTag(LibraryTags.SEARCH).performTextReplacement("")
        assertNull("still sought", session.restored.value)
        compose.waitUntil(PATIENCE_MS) { exists(LibraryTags.entry(gone.id.value)) }
        compose.waitForIdle()
        assertTrue("the list left its top", displayed(LibraryTags.entry(all.first().id.value)))
    }

    // Showing the other view lets go of a recipe put back that its own view has not shown yet.
    @Test
    fun theOtherViewLetsGoOfARecipePutBack() {
        val all = many(3)
        found("Borscht")
        show(LibraryView.OWN)
        await(LibraryTags.entry(all.first().id.value))
        val gone = all[1]
        deleteFromItsMenu(gone)
        compose.onNodeWithTag(LibraryTags.SEARCH).performTextReplacement("Recipe 0")
        compose.waitUntil(PATIENCE_MS) { !exists(LibraryTags.entry(all[2].id.value)) }
        compose.takeNoticeAction("Undo")
        compose.waitUntil(PATIENCE_MS) { session.restored.value == gone.id.value }

        compose.onNodeWithTag(LibraryTags.tab(LibraryView.MARKED)).performClick()

        compose.waitUntil(PATIENCE_MS) { session.view.value == LibraryView.MARKED }
        assertNull(session.restored.value)
    }

    // --- The header lets go of the focus, and settles ---------------------------------------------------------------

    // A search field focused, the header scrolled wholly away: the field is focused no more, so that what the keyboard
    // types never goes out of sight. Told while the finger is still on the list: once it lifts, the pager the views
    // stand in takes the fling's end as a scroll of its own and clears the focus by itself, header or none.
    @Test
    fun theHeaderScrolledAwayLetsGoOfTheFocus() {
        val all = many(20)
        show(LibraryView.OWN)
        await(LibraryTags.entry(all.first().id.value))
        compose.onNodeWithTag(LibraryTags.SEARCH).performClick()
        compose.waitUntil(PATIENCE_MS) { focused(LibraryTags.SEARCH) }
        assertTrue("the screen is in touch mode, as a phone's is", compose.activity.window.decorView.isInTouchMode)
        val full = headerHeight()

        // Dragged up, slowly and well past the header's height and the top bar's, the finger kept down.
        compose.onNodeWithTag(LibraryTags.OWN_LIST).performTouchInput {
            down(Offset(centerX, bottom - 10f))
            val step = with(compose.density) { 8.dp.toPx() }
            repeat((with(compose.density) { (full + 300f).dp.toPx() } / step).toInt()) { moveBy(Offset(0f, -step), delayMillis = 16) }
        }

        compose.waitUntil(PATIENCE_MS) { headerHeight() < 0.5f }
        compose.waitUntil(PATIENCE_MS) { !focused(LibraryTags.SEARCH) && !headerHoldsFocus() }
        compose.onNodeWithTag(LibraryTags.OWN_LIST).performTouchInput { up() }
    }

    /** Whether any node of the book's header — its search field, its filters — holds the focus. */
    private fun headerHoldsFocus() = compose.onAllNodes(hasAnyAncestor(hasTestTag(LibraryTags.HEADER_AREA)), useUnmergedTree = true)
        .fetchSemanticsNodes().any { it.config.getOrNull(SemanticsProperties.Focused) == true }

    private fun focused(tag: String) =
        compose.onNodeWithTag(tag).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Focused) == true

    // Let go partway, the header settles the way the list was going: wholly away going down, whole again going up.
    @Test
    fun theHeaderLetGoPartwaySettles() {
        val all = many(20)
        show(LibraryView.OWN)
        await(LibraryTags.entry(all.first().id.value))
        compose.waitForIdle()
        val full = headerHeight()
        // Past the top bar's height, into the header's, and no further.
        val bar = shown(LibraryTags.TOP_BAR).height
        val partway = with(compose.density) { (bar + full / 2).dp.toPx() }
        compose.onNodeWithTag(LibraryTags.OWN_LIST).performTouchInput {
            swipeUp(startY = centerY + partway / 2, endY = centerY - partway / 2, durationMillis = 1_000)
        }
        compose.waitForIdle()
        assertEquals("going down, the header settled away", 0f, headerHeight(), 0.5f)

        compose.onNodeWithTag(LibraryTags.OWN_LIST).performTouchInput {
            swipeDown(startY = centerY - partway / 2, endY = centerY + partway / 2, durationMillis = 1_000)
        }
        compose.waitForIdle()
        assertEquals("going up, the header settled whole", full, headerHeight(), 0.5f)
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
