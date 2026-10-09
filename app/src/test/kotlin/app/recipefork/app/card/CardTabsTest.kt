package app.recipefork.app.card

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.data.HeldLastCardTab
import app.recipefork.app.data.LastCardTab
import app.recipefork.app.data.PreferencesLastBookView
import app.recipefork.app.data.PreferencesLastCardTab
import app.recipefork.app.editor.EditorEnvironment
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.editor.SAMPLE_DICTIONARIES
import app.recipefork.app.editor.TestDevice
import app.recipefork.app.editor.textsOf
import app.recipefork.app.library.LibrarySession
import app.recipefork.app.library.LibraryTags
import app.recipefork.app.library.LibraryView
import app.recipefork.app.media.PhotoStore
import app.recipefork.app.media.TestPhotos
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.timer.LocalCardTimers
import app.recipefork.app.timer.TestTimers
import app.recipefork.app.timer.TimerTags
import app.recipefork.app.ui.AccessibilityChecks
import app.recipefork.app.ui.AppNavHost
import app.recipefork.app.ui.AppTheme
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.Media
import app.recipefork.core.recipe.MediaAttachment
import app.recipefork.core.recipe.MediaKind
import app.recipefork.core.recipe.MediaVisibility
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.Timer
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.recipe.fork
import app.recipefork.core.scaling.Rational
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant
import kotlin.math.abs
import kotlin.time.Duration.Companion.minutes

/**
 * The card's two tabs (the two-tab increment, scenarios 1–3): View and Cooking, M3 primary tabs pinned under the bar,
 * switched by a tap or a swipe, each with its own scroll; View holds what decides — fork, edit, the removal, the cover,
 * the summary, the provenance, the description — and Cooking nothing that distracts from cooking: back and the title,
 * and a ⋮ holding only the clearing of done marks. The card opens on the tab last chosen on the device.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "en-w360dp-h640dp")
class CardTabsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val device = TestDevice(context)
    private val timers = TestTimers(context)
    private val active = ActiveBook(BookName.Pool, book.database)
    private val recipes = RecipeRepository(active)
    private val store = PhotoStore(context.filesDir)
    private val marks = DoneMarks.inMemory()
    private val now = Instant.parse("2026-10-09T12:00:00Z")
    private var lastTab: LastCardTab = HeldLastCardTab()
    private val environment get() = CardEnvironment(marks = marks, lastTab = lastTab) { device.database.dictionaryDao().ingredients() }
    private var forks = 0
    private var edits = 0

    /** The showing of the card: a change makes the card anew, as opening it again does. */
    private var showing by mutableIntStateOf(0)

    @Before
    fun setUp() {
        compose.registerIdlingResource(book.idling)
        compose.registerIdlingResource(device.idling)
        compose.registerIdlingResource(timers.idling)
        runBlocking { device.database.dictionaryDao().replace(SAMPLE_DICTIONARIES) }
    }

    @After
    fun close() {
        compose.unregisterIdlingResource(book.idling)
        compose.unregisterIdlingResource(device.idling)
        compose.unregisterIdlingResource(timers.idling)
        book.close()
        device.close()
        timers.close()
    }

    private fun photo(mediaId: String, attachment: MediaAttachment): Media {
        val file = store.file(BookName.Pool, mediaId).apply { parentFile!!.mkdirs() }
        TestPhotos.jpeg(file, 1600, 1200)
        return Media(mediaId, null, MediaKind.PHOTO, attachment, MediaVisibility.PRIVATE)
    }

    /**
     * A fork long enough to scroll on either tab: a cover, a description, twelve ingredients, twenty steps, a photo on
     * the second step and a timer on the last.
     */
    private fun save(): Recipe {
        val parent = Recipe.create(now, null).copy(title = "Roast chicken")
        val recipe = parent.fork(now, owner = null).copy(
            title = "Lemon roast chicken",
            description = "A weeknight dinner.",
            servings = 4,
            ingredients = (0 until 12).map {
                Ingredient(it, null, freeTextName = "Ingredient $it", unitType = UnitType.EXACT, unit = QuantityUnit.GRAM, quantity = Rational.of(100))
            },
            steps = (0 until 20).map { Step(it, "Step number ${it + 1} of the roast.", timer = if (it == 19) Timer(30.minutes, "Roast") else null) },
            media = listOf(photo("cover", MediaAttachment.Cover), photo("step", MediaAttachment.ToStep(1))),
        )
        runBlocking {
            recipes.save(parent)
            recipes.save(recipe)
        }
        return recipe
    }

    private fun show(recipe: Recipe) {
        compose.setContent {
            CompositionLocalProvider(LocalCardTimers provides timers.card) {
                AppTheme {
                    key(showing) {
                        CardScreen(recipe.id, recipes, environment, onBack = {}, onEdit = { edits++ }, onFork = { forks++ })
                    }
                }
            }
        }
        compose.awaitCard()
        settled()
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun selected(mode: CardMode) =
        compose.onNodeWithTag(CardTags.tab(mode)).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected) == true

    /** Waits until the pager has come to rest on one page. */
    private fun settled() =
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(hasTestTag(CardTags.CONTENT)).fetchSemanticsNodes().size == 1 }

    private fun shownTab(): CardMode {
        settled()
        return CardMode.entries.single { selected(it) }
    }

    private fun choose(mode: CardMode) {
        compose.onNodeWithTag(CardTags.tab(mode)).performClick()
        compose.waitUntil(PATIENCE_MS) { selected(mode) }
        settled()
        compose.waitForIdle()
    }

    /** How far the shown tab's content is scrolled, in pixels. */
    private fun scrolled(): Float =
        compose.onNodeWithTag(CardTags.CONTENT).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()

    private fun scrollBy(by: Float) {
        compose.onNodeWithTag(CardTags.CONTENT).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, by) }
        compose.waitForIdle()
    }

    private fun top(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top

    private fun bottom(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.bottom

    // Scenario 1: two M3 tabs under the bar, named, each announcing what choosing it does, in sight at any scroll.
    @Test
    fun twoTabsSitUnderTheBarAndStayThereAtAnyScroll() =
        tabsUnderTheBar("View", "Cooking", "Show the recipe for reading", "Show the recipe for cooking")

    @Test
    @Config(qualifiers = "ru-w360dp-h640dp")
    fun twoTabsSitUnderTheBarAndStayThereAtAnyScrollInRussian() =
        tabsUnderTheBar("Просмотр", "Готовка", "Показать рецепт для чтения", "Показать рецепт для готовки")

    private fun tabsUnderTheBar(view: String, cooking: String, showView: String, showCooking: String) {
        show(save())
        assertEquals(listOf(view), compose.textsOf(CardTags.tab(CardMode.VIEW)))
        assertEquals(listOf(cooking), compose.textsOf(CardTags.tab(CardMode.COOKING)))
        // Each tab announces what choosing it does — how the recipe is shown — not what it looks like (A11Y-005).
        mapOf(CardMode.VIEW to showView, CardMode.COOKING to showCooking).forEach { (mode, action) ->
            val node = compose.onNodeWithTag(CardTags.tab(mode)).fetchSemanticsNode()
            assertEquals(action, AccessibilityChecks.clickActionLabel(node))
            assertNotEquals(action, AccessibilityChecks.announcedLabel(node))
        }
        CardMode.entries.forEach { mode ->
            val tab = compose.onNodeWithTag(CardTags.tab(mode)).fetchSemanticsNode()
            assertEquals("${mode.name} is a tab", Role.Tab, tab.config.getOrNull(SemanticsProperties.Role))
            compose.assertCardTarget(CardTags.tab(mode))
        }
        compose.onNodeWithTag(CardTags.tab(CardMode.VIEW)).assertIsSelected()
        compose.onNodeWithTag(CardTags.tab(CardMode.COOKING)).assertIsNotSelected()
        val under = top(CardTags.TABS)
        assertTrue("the tabs under the bar", under >= bottom(CardTags.BAR) - 0.5f)
        assertTrue("the content under the tabs", top(CardTags.CONTENT) >= bottom(CardTags.TABS) - 0.5f)
        CardMode.entries.forEach { mode ->
            choose(mode)
            scrollBy(3000f)
            assertTrue("$mode scrolled", scrolled() > 0f)
            compose.onNodeWithTag(CardTags.TABS).assertIsDisplayed()
            assertEquals("the tabs pinned on $mode", under, top(CardTags.TABS), 0.5f)
        }
    }

    // Scenario 1: a tap on a tab switches to it, and so does a horizontal swipe across the card.
    @Test
    fun aTapOrASwipeSwitchesTheTab() {
        show(save())
        assertEquals(CardMode.VIEW, shownTab())
        choose(CardMode.COOKING)
        assertEquals(CardMode.COOKING, shownTab())
        choose(CardMode.VIEW)
        compose.onNodeWithTag(CardTags.PAGER).performTouchInput { swipeLeft() }
        compose.waitUntil(PATIENCE_MS) { selected(CardMode.COOKING) }
        assertEquals(CardMode.COOKING, shownTab())
        assertFalse("Cooking shows no cover", exists(CardTags.COVER))
        compose.onNodeWithTag(CardTags.PAGER).performTouchInput { swipeRight() }
        compose.waitUntil(PATIENCE_MS) { selected(CardMode.VIEW) }
        assertEquals(CardMode.VIEW, shownTab())
        compose.waitUntil(PATIENCE_MS) { exists(CardTags.COVER) }
    }

    // Scenario 1: each tab keeps its own scroll — the part first at the top of each, as the card keeps its place.
    @Test
    fun eachTabKeepsItsOwnScroll() {
        show(save())
        // The pictures drawn first, so that the place read is the one the reader leaves.
        compose.waitUntil(PATIENCE_MS) { exists(CardTags.COVER) && exists(CardTags.stepPhoto(1)) }
        compose.onNodeWithTag(CardTags.step(10)).performScrollTo()
        compose.waitForIdle()
        val view = compose.firstAtTheTop()
        choose(CardMode.COOKING)
        assertEquals("Cooking opens at its own top", 0f, scrolled(), 0.5f)
        compose.waitUntil(PATIENCE_MS) { exists(CardTags.stepPhoto(1)) }
        compose.onNodeWithTag(CardTags.step(4)).performScrollTo()
        compose.waitForIdle()
        val cooking = compose.firstAtTheTop()
        assertTrue("the tabs stand at different places: $view, $cooking", view != cooking)
        choose(CardMode.VIEW)
        compose.waitUntil(PATIENCE_MS) { compose.firstAtTheTop() == view }
        choose(CardMode.COOKING)
        compose.waitUntil(PATIENCE_MS) { compose.firstAtTheTop() == cooking }
    }

    // Scenario 1: nothing inside the card scrolls sideways, so a horizontal swipe always turns the tab — step photos
    // included.
    @Test
    fun nothingInsideTheCardScrollsSideways() {
        show(save())
        CardMode.entries.forEach { mode ->
            choose(mode)
            compose.waitUntil(PATIENCE_MS) { exists(CardTags.stepPhoto(1)) }
            compose.onNodeWithTag(CardTags.stepPhoto(1), useUnmergedTree = true).performScrollTo()
            compose.waitForIdle()
            val sideways = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange), useUnmergedTree = true)
                .fetchSemanticsNodes().map { it.config.getOrNull(SemanticsProperties.TestTag) }
            assertEquals("on $mode only the pager scrolls sideways", listOf(CardTags.PAGER), sideways)
        }
    }

    // Scenario 2: View holds what decides — fork, edit, the removal, the cover, the summary, the provenance, the
    // description — and its lists are View's: no line marks, no timer starts.
    @Test
    fun viewHoldsForkEditTheRemovalAndWhatDecides() {
        val recipe = save()
        marks.hold(BookName.Pool, recipe.id, setOf(DoneMark.of(recipe.ingredients[0])))
        show(recipe)
        assertEquals(CardMode.VIEW, shownTab())
        compose.waitUntil(PATIENCE_MS) { exists(CardTags.COVER) }
        listOf(CardTags.FORK, CardTags.EDIT, CardTags.COVER, CardTags.TITLE, CardTags.SUMMARY).forEach {
            assertTrue("View shows $it", exists(it))
        }
        compose.waitUntil(PATIENCE_MS) { exists(CardTags.PROVENANCE) }
        compose.onNodeWithTag(CardTags.DESCRIPTION).performScrollTo()
        compose.openTheCardMenu()
        assertTrue("View's ⋮ holds Delete", exists(CardTags.DELETE))
        assertFalse("View's ⋮ holds no Clear marks", exists(CardTags.CLEAR_MARKS))
        compose.closeTheCardMenu()
        // The lists in View: a line answers no tap, a timer reads as its duration with nothing to start it.
        val line = compose.onNodeWithTag(CardTags.ingredient(1)).fetchSemanticsNode()
        assertNull("a View line answers no tap", line.config.getOrNull(SemanticsActions.OnClick))
        assertTrue("the timer read as its duration", exists(ViewTimerTags.line(19)))
        assertFalse("no timer starts on View", exists(TimerTags.start(19)))
        compose.onNodeWithTag(CardTags.EDIT).performClick()
        compose.forkFromTheCard()
        assertEquals(1, edits)
        assertEquals(1, forks)
    }

    // Scenario 3: Cooking holds nothing that distracts: no fork, no edit, no cover, summary or description; back and
    // the title in the bar; its lines mark, its timers start.
    @Test
    fun cookingHoldsNothingThatDistractsFromCooking() {
        val recipe = save()
        show(recipe)
        choose(CardMode.COOKING)
        listOf(
            CardTags.FORK, CardTags.EDIT, CardTags.COVER, CardTags.TITLE, CardTags.SUMMARY, CardTags.PROVENANCE,
            CardTags.DESCRIPTION, CardTags.MENU,
        ).forEach { assertFalse("Cooking shows no $it", exists(it)) }
        assertTrue(exists(CardTags.BACK))
        assertEquals(listOf("Lemon roast chicken"), compose.textsOf(CardTags.BAR_TITLE))
        compose.onNodeWithTag(CardTags.step(19)).performScrollTo()
        assertTrue("timers start on Cooking", exists(TimerTags.start(19)))
        assertFalse(exists(ViewTimerTags.line(19)))
        // A line marked: the ⋮ shows, holding Clear marks alone.
        compose.onNodeWithTag(CardTags.ingredient(1)).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { exists(CardTags.MENU) }
        compose.openTheCardMenu()
        assertTrue(exists(CardTags.CLEAR_MARKS))
        assertFalse("Cooking's ⋮ holds no Delete", exists(CardTags.DELETE) || exists(CardTags.REMOVE))
        compose.onNodeWithTag(CardTags.CLEAR_MARKS).performClick()
        compose.waitUntil(PATIENCE_MS) { !exists(CardTags.MENU) }
        assertTrue(marks.held(BookName.Pool, recipe.id).isEmpty())
    }

    // Scenario 1/2/3: a running timer's bar shows on both tabs while its step is out of sight.
    @Test
    fun theRunningTimersBarShowsOnBothTabs() {
        show(save())
        choose(CardMode.COOKING)
        compose.onNodeWithTag(TimerTags.start(19)).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { exists(TimerTags.cancel(19)) }
        scrollBy(-100_000f)
        compose.waitUntil(PATIENCE_MS) { exists(TimerTags.barStatus(19)) }
        choose(CardMode.VIEW)
        compose.waitUntil(PATIENCE_MS) { exists(TimerTags.barStatus(19)) }
        assertTrue("fork above the bar", bottom(CardTags.FORK) <= top(TimerTags.BAR) + 0.5f)
    }

    // Scenario 1: the card opens on the tab last chosen — View the first time.
    @Test
    fun theCardOpensOnTheTabLastChosen() {
        val held = HeldLastCardTab()
        lastTab = held
        show(save())
        assertEquals(CardMode.VIEW, shownTab())
        choose(CardMode.COOKING)
        compose.waitUntil(PATIENCE_MS) { held.read() == CardMode.COOKING }
        compose.runOnIdle { showing++ }
        compose.awaitCard()
        assertEquals(CardMode.COOKING, shownTab())
        compose.onNodeWithTag(CardTags.PAGER).performTouchInput { swipeRight() }
        compose.waitUntil(PATIENCE_MS) { held.read() == CardMode.VIEW }
        compose.runOnIdle { showing++ }
        compose.awaitCard()
        assertEquals(CardMode.VIEW, shownTab())
    }

    // Scenario 1: the tab last chosen is kept on the device, across restarts; anything else read there is no tab.
    @Test
    fun theTabLastChosenIsKeptOnTheDevice() {
        assertNull(PreferencesLastCardTab(context).read())
        PreferencesLastCardTab(context).keep(CardMode.COOKING)
        assertEquals(CardMode.COOKING, PreferencesLastCardTab(context).read())
        PreferencesLastCardTab(context).keep(CardMode.VIEW)
        assertEquals(CardMode.VIEW, PreferencesLastCardTab(context).read())
        context.getSharedPreferences(PreferencesLastBookView.FILE, Context.MODE_PRIVATE).edit()
            .putString(PreferencesLastCardTab.KEY, "SIDEWAYS").commit()
        assertNull(PreferencesLastCardTab(context).read())
    }

    // Scenario 1: the tabs take nothing from the system's back: from Cooking it leaves the card for the book.
    @Test
    fun theSystemBackLeavesTheCardFromEitherTab() {
        val recipe = save()
        val session = LibrarySession(SavedStateHandle()).apply { choose(LibraryView.OWN) }
        val editor = EditorEnvironment(active, device.catalog(context)) { now }
        compose.setContent {
            CompositionLocalProvider(LocalCardTimers provides timers.card) {
                AppTheme { AppNavHost(session, recipes, editor = editor, card = environment) }
            }
        }
        CardMode.entries.reversed().forEach { mode ->
            compose.runOnIdle { session.openCard(recipe.id.value, LibraryView.OWN) }
            compose.awaitCard()
            settled()
            choose(mode)
            compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
            compose.waitUntil(PATIENCE_MS) { !exists(CardTags.SCREEN) && exists(LibraryTags.PAGER) }
        }
    }
}
