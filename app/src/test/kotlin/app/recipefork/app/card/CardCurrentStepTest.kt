package app.recipefork.app.card

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.geometry.Offset
import app.recipefork.app.editor.textsOf
import app.recipefork.app.timer.TimerTags
import app.recipefork.app.ui.ReadingFamily
import app.recipefork.app.ui.appTypography
import app.recipefork.core.recipe.Timer
import kotlin.time.Duration.Companion.minutes
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.core.graphics.Insets
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.data.HeldLastCardTab
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.Rational
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
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import kotlin.math.abs

/**
 * Scenario 3 of v0.27: on Cooking the first step not marked done is the current one — drawn on the theme's primary
 * container and read by the screen reader as the current step — and marking a step brings the next one not marked into
 * view, smoothly, unless the reader is scrolling themselves. View shows no current step.
 *
 * v0.45, T-324: the current step stands on the olive plate with the caption "Current step · N of M" above its text,
 * visible and read once, its text in Literata at 23 sp; its note and its timer stand on the plate too.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w360dp-h640dp")
class CardCurrentStepTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val recipes = RecipeRepository(ActiveBook(BookName.Pool, book.database))
    private val marks = DoneMarks.inMemory()
    private val scheme = lightColorScheme()
    private val now = Instant.parse("2026-10-09T12:00:00Z")

    private fun grams(position: Int, name: String, grams: Long) =
        Ingredient(position, null, freeTextName = name, unitType = UnitType.EXACT, unit = QuantityUnit.GRAM, quantity = Rational.of(grams))

    /** Three short steps, each one line. */
    private val pilaf = Recipe.create(now, null).copy(
        title = "Pilaf",
        servings = 4,
        ingredients = listOf(grams(0, "Chicken", 500), grams(1, "Rice", 350)),
        steps = listOf(Step(0, "Fry the chicken."), Step(1, "Add the rice."), Step(2, "Season it.")),
    )

    /** Eight steps of several lines each: the later ones lie well below the first. */
    private val stew = Recipe.create(now, null).copy(
        title = "Stew",
        servings = 4,
        ingredients = listOf(grams(0, "Beef", 800), grams(1, "Onions", 300), grams(2, "Carrots", 300)),
        steps = (0 until 8).map {
            Step(it, "Step ${it + 1}: stir the pot slowly with a wooden spoon, scraping the bottom so that nothing catches or burns.")
        },
    )

    @Before
    fun setUp() {
        compose.registerIdlingResource(book.idling)
    }

    @After
    fun close() {
        compose.unregisterIdlingResource(book.idling)
        book.close()
    }

    /** Opens [recipe] on [tab]; in the app's own type — its faces and sizes — where [appType]. */
    private fun open(recipe: Recipe, tab: CardMode, appType: Boolean = false) {
        runBlocking { recipes.save(recipe) }
        val environment = CardEnvironment(marks = marks, lastTab = HeldLastCardTab(tab), ingredients = IngredientEntries { emptyList() })
        compose.setContent {
            val typography = if (appType) appTypography(context.resources) else MaterialTheme.typography
            MaterialTheme(colorScheme = scheme, typography = typography) { CardScreen(recipe.id, recipes, environment, onBack = {}, onEdit = {}) }
        }
        compose.awaitCard()
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(hasTestTag(CardTags.step(0))).fetchSemanticsNodes().size == 1 }
        compose.waitForIdle()
    }

    private fun step(position: Int) = CardTags.step(position)

    /** The state the step at [position] is read with. */
    private fun stateOf(position: Int): String? =
        compose.onNodeWithTag(step(position)).fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription)

    /** Whether the step at [position] is read as the current one, of the three. */
    private fun readAsCurrent(position: Int): Boolean = stateOf(position) == "Current step, ${position + 1} of 3"

    /** The caption drawn above each step's text, by position: null where there is none. */
    private fun captions(count: Int = 3): List<String?> = (0 until count).map { position ->
        compose.onAllNodes(hasTestTag(StepRowTags.caption(position)), useUnmergedTree = true).fetchSemanticsNodes()
            .singleOrNull()?.config?.get(ShownText)
    }

    /** The colour drawn in the step at [position], right of its line of text and below it. */
    private fun backgroundOf(position: Int): Color {
        compose.onNodeWithTag(step(position)).performScrollTo()
        compose.waitForIdle()
        val pixels = compose.onNodeWithTag(step(position)).captureToImage().toPixelMap()
        return pixels[(pixels.width * 0.92f).toInt(), (pixels.height * 0.9f).toInt()]
    }

    private fun same(a: Color, b: Color) = abs(a.red - b.red) < 2f / 255 && abs(a.green - b.green) < 2f / 255 && abs(a.blue - b.blue) < 2f / 255

    @Test
    fun theFirstStepNotMarkedIsHighlightedAndReadAsTheCurrentOne() {
        open(pilaf, CardMode.COOKING)
        assertEquals(listOf(true, false, false), (0..2).map(::readAsCurrent))
        assertTrue("the current step stands on the primary container", same(scheme.primaryContainer, backgroundOf(0)))
        assertFalse("another step does not", same(scheme.primaryContainer, backgroundOf(1)))

        // Marked, the step is done and no longer current; the next one is.
        compose.onNodeWithTag(step(0)).performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(listOf(false, true, false), (0..2).map(::readAsCurrent))
        assertEquals("Done", compose.onNodeWithTag(step(0)).fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription))
        assertTrue(same(scheme.primaryContainer, backgroundOf(1)))
        assertFalse(same(scheme.primaryContainer, backgroundOf(0)))

        // The first not marked, wherever the marks are: a later step marked leaves the current one where it was.
        compose.onNodeWithTag(step(2)).performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(listOf(false, true, false), (0..2).map(::readAsCurrent))
        // Every step marked: none is current.
        compose.onNodeWithTag(step(1)).performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(listOf(false, false, false), (0..2).map(::readAsCurrent))
        // Unmarked again, the step is current again.
        compose.onNodeWithTag(step(0)).performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(listOf(true, false, false), (0..2).map(::readAsCurrent))
    }

    @Test
    @Config(qualifiers = "ru-w360dp-h640dp")
    fun theCurrentStepIsCaptionedAndReadInRussian() {
        marks.hold(BookName.Pool, pilaf.id, setOf(DoneMark.of(pilaf.steps[0])))
        open(pilaf, CardMode.COOKING)
        assertEquals("Текущий шаг, 2 из 3", stateOf(1))
        assertEquals(listOf(null, "ТЕКУЩИЙ ШАГ · 2 ИЗ 3", null), captions())
    }

    // The caption stands above the step's text, inside the plate; the text is Literata at 23 sp in the plate's ink.
    @Test
    fun theCurrentStepIsCaptionedAboveItsTextInLargeLiterata() {
        open(pilaf, CardMode.COOKING, appType = true)
        assertEquals(listOf("CURRENT STEP · 1 OF 3", null, null), captions())
        val caption = compose.onNodeWithTag(StepRowTags.caption(0), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val text = compose.onNodeWithTag(StepTags.text(0), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val plate = compose.onNodeWithTag(StepRowTags.entry(0)).fetchSemanticsNode().boundsInRoot
        assertTrue("the caption ($caption) stands above the text ($text)", caption.bottom <= text.top + 0.5f)
        assertTrue("the caption is on the plate", plate.contains(caption.topLeft) && plate.contains(caption.bottomRight - Offset(1f, 1f)))
        assertTrue("the caption has room", caption.height > 0f && caption.width > 0f)

        val layout = compose.textLayoutsWithin(StepTags.text(0)).single()
        assertEquals(23f, layout.layoutInput.style.fontSize.value, 0.01f)
        assertEquals(ReadingFamily, layout.layoutInput.style.fontFamily)
        assertEquals(scheme.onPrimaryContainer, layout.layoutInput.style.color)
        assertEquals("the caption numbers the step; its text does not", "Fry the chicken.", layout.layoutInput.text.text)
        val captionLayout = compose.textLayoutsWithin(StepRowTags.caption(0)).single()
        assertEquals(scheme.onPrimaryContainer, captionLayout.layoutInput.style.color)

        // Another step reads as before: its number in its text, at the Cooking size.
        val next = compose.textLayoutsWithin(StepTags.text(1)).single()
        assertEquals("2.\u00A0Add the rice.", next.layoutInput.text.text)
        assertEquals(24f, next.layoutInput.style.fontSize.value, 0.01f)
    }

    // Within the target sizes — 150 % on a 360 dp phone — the caption and the step's text read whole, in Russian, the
    // longer of the two.
    @Test
    @Config(qualifiers = "ru-w360dp-h640dp", fontScale = 1.5f)
    fun atOneAndAHalfTimesTheFontScaleTheCaptionAndTextReadWhole() {
        marks.hold(BookName.Pool, stew.id, (0..6).map { DoneMark.of(stew.steps[it]) }.toSet())
        open(stew, CardMode.COOKING, appType = true)
        compose.onNodeWithTag(step(7)).performScrollTo()
        compose.waitForIdle()
        assertEquals(listOf("ТЕКУЩИЙ ШАГ · 8 ИЗ 8"), (0..7).mapNotNull { captions(8)[it] })
        compose.assertWhole(StepRowTags.caption(7))
        compose.assertWhole(StepTags.text(7))
        compose.assertCardTarget(step(7))
    }

    // Read once: as the step's state, with its place among the steps; neither the drawn caption nor a number is read
    // besides.
    @Test
    fun theCaptionIsReadOnceAsTheStepsState() {
        open(pilaf, CardMode.COOKING)
        assertEquals("Current step, 1 of 3", stateOf(0))
        assertEquals(listOf("Fry the chicken."), compose.textsOf(step(0)))
        val drawn = compose.onNodeWithTag(StepRowTags.caption(0), useUnmergedTree = true).fetchSemanticsNode().config
        assertTrue("the drawn caption has no words of its own", drawn.getOrNull(SemanticsProperties.Text).isNullOrEmpty())
        assertTrue("nor a description", drawn.getOrNull(SemanticsProperties.ContentDescription).isNullOrEmpty())
        // Another step keeps its number and its state in words.
        assertEquals(listOf("2.", "Add the rice."), compose.textsOf(step(1)))
        assertEquals("Not done", stateOf(1))
    }

    @Test
    fun thePlateMovesWithTheMarksAndGoesWithTheLast() {
        open(pilaf, CardMode.COOKING)
        compose.onNodeWithTag(step(0)).performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(listOf(null, "CURRENT STEP · 2 OF 3", null), captions())
        compose.onNodeWithTag(step(1)).performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(listOf(null, null, "CURRENT STEP · 3 OF 3"), captions())
        // An earlier step unmarked takes the plate back.
        compose.onNodeWithTag(step(0)).performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(listOf("CURRENT STEP · 1 OF 3", null, null), captions())
        // Every step marked: no plate anywhere.
        (0..2).forEach { if (stateOf(it) != "Done") compose.onNodeWithTag(step(it)).performScrollTo().performClick() }
        compose.waitForIdle()
        assertEquals(listOf<String?>(null, null, null), captions())
        (0..2).forEach { assertFalse("step $it, every step done", same(scheme.primaryContainer, backgroundOf(it))) }
    }

    // The step's note and its timer stand on the plate: within its bounds, drawn on its colour.
    @Test
    fun theCurrentStepsNoteAndTimerStandOnThePlate() {
        val noted = pilaf.copy(
            steps = listOf(
                Step(0, "Fry the chicken.", timers = listOf(Timer(8.minutes, "Chicken")), note = "Skin side down first."),
                Step(1, "Add the rice."),
            ),
        )
        open(noted, CardMode.COOKING)
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(hasTestTag(TimerTags.start(0))).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag(TimerTags.start(0)).performScrollTo()
        compose.waitForIdle()
        val plate = compose.onNodeWithTag(StepRowTags.entry(0)).fetchSemanticsNode().boundsInRoot
        listOf(StepRowTags.note(0), TimerTags.start(0)).forEach { tag ->
            val part = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue("$tag ($part) within the plate ($plate)", part.left >= plate.left && part.top >= plate.top && part.right <= plate.right + 0.5f && part.bottom <= plate.bottom + 0.5f)
        }
        // Right of the note's words and below them, the plate's colour.
        val note = compose.onNodeWithTag(StepRowTags.note(0), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        assertTrue("under the note, the plate", same(scheme.primaryContainer, pixels[(note.right - 4f).toInt(), (note.bottom + 2f).toInt()]))
    }

    @Test
    fun viewShowsNoCurrentStep() {
        marks.hold(BookName.Pool, pilaf.id, setOf(DoneMark.of(pilaf.steps[0])))
        open(pilaf, CardMode.VIEW)
        assertEquals(listOf(false, false, false), (0..2).map(::readAsCurrent))
        assertEquals("no caption on View", listOf<String?>(null, null, null), captions())
        (0..2).forEach { assertFalse("step $it on View", same(scheme.primaryContainer, backgroundOf(it))) }
        assertTrue(same(scheme.surface, backgroundOf(1)))
    }

    /** The card's scroll on the tab shown, in pixels. */
    private fun scrolled(): Float =
        compose.onNodeWithTag(CardTags.CONTENT).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()

    /** Whether the step at [position] lies wholly within the tab's viewport. */
    private fun inView(position: Int): Boolean {
        val pager = compose.onNodeWithTag(CardTags.PAGER).fetchSemanticsNode().boundsInRoot
        val node = compose.onNodeWithTag(step(position)).fetchSemanticsNode()
        val top = node.positionInRoot.y
        return top >= pager.top - 0.5f && top + node.size.height <= pager.bottom + 0.5f
    }

    @Test
    fun markingAStepBringsTheNextNotMarkedIntoViewSmoothly() {
        // Steps 2 to 5 are done already: the next after the first is the sixth, far below it.
        marks.hold(BookName.Pool, stew.id, (1..4).map { DoneMark.of(stew.steps[it]) }.toSet())
        open(stew, CardMode.COOKING)
        compose.onNodeWithTag(step(0)).performScrollTo()
        compose.waitForIdle()
        assertTrue(inView(0))
        assertFalse("the sixth step is out of view", inView(5))

        // Frame by frame: the card moves over several frames, not in one jump.
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag(step(0)).performSemanticsAction(SemanticsActions.OnClick)
        val seen = mutableListOf(scrolled())
        repeat(FRAMES) {
            compose.mainClock.advanceTimeByFrame()
            seen += scrolled()
        }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertTrue("the sixth step is brought into view", inView(5))
        val positions = seen.distinct()
        assertTrue("the card scrolls down, over several frames: $positions", positions.size >= 4 && positions.zipWithNext().all { (a, b) -> b > a })
        assertEquals(positions.last(), scrolled(), 0.5f)
    }

    // The content runs on under the navigation bar: the next step is brought wholly above it, not to the viewport's edge
    // behind it.
    @Test
    fun theNextStepIsBroughtAboveTheNavigationBar() {
        marks.hold(BookName.Pool, stew.id, (1..4).map { DoneMark.of(stew.steps[it]) }.toSet())
        compose.runOnUiThread { WindowCompat.setDecorFitsSystemWindows(compose.activity.window, false) }
        open(stew, CardMode.COOKING)
        navigationBar(NAVIGATION)
        compose.onNodeWithTag(step(0)).performScrollTo()
        compose.waitForIdle()
        assertFalse(inView(5))

        compose.onNodeWithTag(step(0)).performClick()
        compose.waitForIdle()
        val screen = compose.onNodeWithTag(CardTags.SCREEN).fetchSemanticsNode().boundsInRoot
        val next = compose.onNodeWithTag(step(5)).fetchSemanticsNode().boundsInRoot
        assertTrue("the sixth step ($next) ends above the navigation bar (${screen.bottom - NAVIGATION})", next.bottom <= screen.bottom - NAVIGATION + 0.5f)
        assertTrue("the sixth step is in view", inView(5))
    }

    private fun navigationBar(height: Int) {
        compose.runOnUiThread {
            val insets = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, height))
                .build()
            compose.activity.window.decorView.dispatchApplyWindowInsets(insets.toWindowInsets())
        }
        compose.waitForIdle()
    }

    @Test
    fun aStepMarkedWithNoneNotMarkedAfterItOrUnmarkedMovesNothing() {
        marks.hold(BookName.Pool, stew.id, (1..7).map { DoneMark.of(stew.steps[it]) }.toSet())
        open(stew, CardMode.COOKING)
        compose.onNodeWithTag(step(0)).performScrollTo()
        compose.waitForIdle()
        val before = scrolled()
        compose.onNodeWithTag(step(0)).performClick()
        compose.waitForIdle()
        assertEquals(before, scrolled(), 0.5f)
        compose.onNodeWithTag(step(0)).performClick()
        compose.waitForIdle()
        assertEquals(before, scrolled(), 0.5f)
    }

    @Test
    fun aStepMarkedWhileTheReaderScrollsLeavesTheScrollToThem() {
        marks.hold(BookName.Pool, stew.id, (1..4).map { DoneMark.of(stew.steps[it]) }.toSet())
        // Steps 2 to 5, done, fold to a line each: a long first step leaves the fling room to run on towards the top.
        val first = stew.steps[0]
        open(stew.copy(steps = listOf(first.copy(text = List(5) { first.text }.joinToString(" "))) + stew.steps.drop(1)), CardMode.COOKING)
        // Scrolled down until the fifth step shows, the sixth just below; then flung back up towards the top, away from it.
        compose.onNodeWithTag(step(4)).performScrollTo()
        compose.waitForIdle()
        assertFalse(inView(5))
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag(CardTags.CONTENT).performTouchInput { swipeDown(startY = centerY - 100f, endY = centerY + 200f, durationMillis = 80) }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        val flung = scrolled()
        // While the fling runs, the first step is marked.
        compose.onNodeWithTag(step(0)).performSemanticsAction(SemanticsActions.OnClick)
        val seen = mutableListOf(flung)
        repeat(FRAMES) {
            compose.mainClock.advanceTimeByFrame()
            seen += scrolled()
        }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertEquals("Done", compose.onNodeWithTag(step(0)).fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription))
        // The fling goes on towards the top as the reader threw it: the card never turns back down to the sixth step.
        assertTrue("the fling went on: $seen", seen.last() < flung)
        assertTrue("the card never turned back down: $seen", seen.zipWithNext().all { (a, b) -> b <= a + 0.5f })
    }

    private companion object {
        /** More frames than any bringing into view or fling takes. */
        const val FRAMES = 90
        /** The navigation bar's height, in pixels. */
        const val NAVIGATION = 90
    }
}
