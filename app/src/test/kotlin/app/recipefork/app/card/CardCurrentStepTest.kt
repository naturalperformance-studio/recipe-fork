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
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
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

    private fun open(recipe: Recipe, tab: CardMode) {
        runBlocking { recipes.save(recipe) }
        val environment = CardEnvironment(marks = marks, lastTab = HeldLastCardTab(tab), ingredients = IngredientEntries { emptyList() })
        compose.setContent {
            MaterialTheme(colorScheme = scheme) { CardScreen(recipe.id, recipes, environment, onBack = {}, onEdit = {}) }
        }
        compose.awaitCard()
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(hasTestTag(CardTags.step(0))).fetchSemanticsNodes().size == 1 }
        compose.waitForIdle()
    }

    private fun step(position: Int) = CardTags.step(position)

    /** Whether the step at [position] is read as the current one. */
    private fun readAsCurrent(position: Int): Boolean =
        compose.onNodeWithTag(step(position)).fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription) == "Current step"

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
    fun theCurrentStepIsReadInRussian() {
        open(pilaf, CardMode.COOKING)
        assertEquals("Текущий шаг", compose.onNodeWithTag(step(0)).fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription))
    }

    @Test
    fun viewShowsNoCurrentStep() {
        marks.hold(BookName.Pool, pilaf.id, setOf(DoneMark.of(pilaf.steps[0])))
        open(pilaf, CardMode.VIEW)
        assertEquals(listOf(false, false, false), (0..2).map(::readAsCurrent))
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
        open(stew, CardMode.COOKING)
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
    }
}
