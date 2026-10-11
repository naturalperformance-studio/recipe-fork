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
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextDecoration
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.data.HeldLastCardTab
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.timer.TimerTags
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.Timer
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
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import kotlin.math.abs
import kotlin.time.Duration.Companion.minutes

/**
 * Scenario 3 of v0.31: on Cooking the steps are a stepper — each numbered circle joined to the next by a vertical line —
 * and a step marked done shows a check in its circle, is struck through and folds to its first line, ellipsized; a tap
 * on it unmarks it and it unfolds, the current step staying in view. View is as it was.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w360dp-h640dp")
class CardStepperTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val recipes = RecipeRepository(ActiveBook(BookName.Pool, book.database))
    private val marks = DoneMarks.inMemory()
    private val scheme = lightColorScheme()
    private val now = Instant.parse("2026-10-11T12:00:00Z")

    private val long = "stir the pot slowly with a wooden spoon, scraping the bottom so that nothing catches or burns."

    /** Three steps of several lines each, the first with the author's note and a timer. */
    private val stew = Recipe.create(now, null).copy(
        title = "Stew",
        servings = 4,
        steps = listOf(
            Step(0, "Brown the beef: $long", timers = listOf(Timer(5.minutes)), note = "Grandma used lard."),
            Step(1, "Add the onions: $long"),
            Step(2, "Add the carrots: $long"),
        ),
    )

    /** Eight steps of several lines each: the later ones lie well below the first. */
    private val pot = Recipe.create(now, null).copy(
        title = "Pot",
        servings = 4,
        steps = (0 until 8).map { Step(it, "Step ${it + 1}: $long") },
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

    private fun open(recipe: Recipe, tab: CardMode = CardMode.COOKING) {
        runBlocking { recipes.save(recipe) }
        val environment = CardEnvironment(marks = marks, lastTab = HeldLastCardTab(tab), ingredients = IngredientEntries { emptyList() })
        compose.setContent {
            MaterialTheme(colorScheme = scheme) { CardScreen(recipe.id, recipes, environment, onBack = {}, onEdit = {}) }
        }
        compose.awaitCard()
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(hasTestTag(CardTags.step(0))).fetchSemanticsNodes().size == 1 }
        compose.waitForIdle()
    }

    private fun tap(position: Int) {
        compose.onNodeWithTag(CardTags.step(position)).performScrollTo().performClick()
        compose.waitForIdle()
    }

    private fun layout(position: Int): TextLayoutResult = compose.textLayoutsWithin(StepTags.text(position)).single()

    private fun showsCheck(position: Int) =
        compose.onNodeWithTag(StepRowTags.number(position), useUnmergedTree = true).fetchSemanticsNode().config[ShowsCheck]

    private fun stateOf(position: Int) =
        compose.onNodeWithTag(CardTags.step(position)).fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription)

    private fun shown(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun same(a: Color, b: Color) = abs(a.red - b.red) < 3f / 255 && abs(a.green - b.green) < 3f / 255 && abs(a.blue - b.blue) < 3f / 255

    /** The colour drawn in step [position]'s entry under the middle of its circle, halfway from the circle to the entry's end. */
    private fun underTheCircle(position: Int): Color {
        compose.onNodeWithTag(StepRowTags.entry(position), useUnmergedTree = true).performScrollTo()
        compose.waitForIdle()
        val circle = compose.onNodeWithTag(StepRowTags.number(position), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val entry = compose.onNodeWithTag(StepRowTags.entry(position), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val pixels = compose.onNodeWithTag(StepRowTags.entry(position), useUnmergedTree = true).captureToImage().toPixelMap()
        val x = (circle.center.x - entry.left - 0.5f).toInt()
        val y = ((circle.bottom + entry.bottom) / 2f - entry.top).toInt()
        return pixels[x, y]
    }

    @Test
    fun eachStepsCircleIsJoinedToTheNextByALineAndTheLastToNone() {
        open(stew)
        assertTrue("the current step's line, over its container", same(scheme.onSurfaceVariant, underTheCircle(0)))
        assertTrue("a step's line", same(scheme.onSurfaceVariant, underTheCircle(1)))
        assertTrue("no line after the last step", same(scheme.surface, underTheCircle(2)))
    }

    @Test
    fun aStepMarkedDoneShowsACheckInItsCircleAndIsReadAsDone() {
        open(stew)
        assertEquals(listOf(false, false, false), (0..2).map(::showsCheck))
        tap(1)
        assertEquals(listOf(false, true, false), (0..2).map(::showsCheck))
        assertEquals("Done", stateOf(1))
        // The check is drawn in the circle's own colour for its number, on its primary fill.
        val circle = compose.onNodeWithTag(StepRowTags.number(1), useUnmergedTree = true).captureToImage().toPixelMap()
        val tick = circle[(circle.width * 0.36f).toInt(), (circle.height * 0.595f).toInt()]
        assertTrue("the check is drawn: $tick", same(scheme.onPrimary, tick))
        // Unmarked, the number is back.
        tap(1)
        assertEquals(listOf(false, false, false), (0..2).map(::showsCheck))
        assertEquals("Not done", stateOf(1))
    }

    @Test
    fun aStepMarkedDoneFoldsToItsFirstLineStruckThroughAndATapUnfoldsIt() {
        open(stew)
        val whole = layout(0)
        assertTrue("the step runs over several lines", whole.lineCount > 1)
        assertTrue(shown(StepRowTags.note(0)) && shown(TimerTags.start(0)))
        val unfolded = compose.onNodeWithTag(StepRowTags.entry(0), useUnmergedTree = true).fetchSemanticsNode().size.height

        tap(0)
        val folded = layout(0)
        assertEquals("folded to one line", 1, folded.lineCount)
        assertTrue("its first line, ellipsized", folded.isLineEllipsized(0))
        assertEquals(TextDecoration.LineThrough, folded.layoutInput.style.textDecoration)
        assertFalse("its note folds away", shown(StepRowTags.note(0)))
        assertFalse("its timer folds away", shown(TimerTags.start(0)))
        assertTrue(compose.onNodeWithTag(StepRowTags.entry(0), useUnmergedTree = true).fetchSemanticsNode().size.height < unfolded)
        // Still read whole, and still a card's target to tap.
        val spoken = compose.onNodeWithTag(StepTags.text(0), useUnmergedTree = true).fetchSemanticsNode().config[SemanticsProperties.Text]
        assertEquals(stew.steps[0].text, spoken.joinToString("") { it.text })
        compose.assertCardTarget(CardTags.step(0))

        // A tap on the folded step unmarks it, and it unfolds.
        tap(0)
        assertEquals("Current step", stateOf(0))
        assertEquals(whole.lineCount, layout(0).lineCount)
        assertTrue("no longer struck through", layout(0).layoutInput.style.textDecoration != TextDecoration.LineThrough)
        assertTrue(shown(StepRowTags.note(0)) && shown(TimerTags.start(0)))
        assertFalse(showsCheck(0))
    }

    @Test
    fun everyStepLineKeepsTheCardsTarget() {
        marks.hold(BookName.Pool, stew.id, setOf(DoneMark.of(stew.steps[2])))
        open(stew)
        (0..2).forEach { compose.assertCardTarget(CardTags.step(it)) }
    }

    @Test
    fun viewIsUnchangedNothingFoldsNoCheckNoLineAndTheMarkIsNotChangeable() {
        marks.hold(BookName.Pool, stew.id, setOf(DoneMark.of(stew.steps[0]), DoneMark.of(stew.steps[1])))
        open(stew, CardMode.VIEW)
        assertTrue("a done step stays unfolded", layout(0).lineCount > 1 && layout(1).lineCount > 1)
        assertEquals(TextDecoration.LineThrough, layout(0).layoutInput.style.textDecoration)
        assertTrue(shown(StepRowTags.note(0)))
        assertEquals("the mark is shown", "Done", stateOf(0))
        assertEquals(listOf(false, false, false), (0..2).map(::showsCheck))
        assertNull("and not changeable", compose.onNodeWithTag(CardTags.step(0)).fetchSemanticsNode().config.getOrNull(SemanticsActions.OnClick))
        assertTrue("no stepper line on View", same(scheme.surface, underTheCircle(0)))
    }

    /** Whether the step at [position]'s line lies wholly within the tab's viewport. */
    private fun inView(position: Int): Boolean {
        val pager = compose.onNodeWithTag(CardTags.PAGER).fetchSemanticsNode().boundsInRoot
        val node = compose.onNodeWithTag(CardTags.step(position)).fetchSemanticsNode()
        val top = node.positionInRoot.y
        return top >= pager.top - 0.5f && top + node.size.height <= pager.bottom + 0.5f
    }

    private fun scrolled(): Float =
        compose.onNodeWithTag(CardTags.CONTENT).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()

    @Test
    fun markingAStepFoldsItAndBringsTheNextIntoViewFrameByFrame() {
        open(pot)
        compose.onNodeWithTag(CardTags.step(3)).performScrollTo()
        compose.waitForIdle()
        // The fourth step is the current one once the first three are done; marking it folds it and brings the fifth.
        (0..2).forEach { tap(it) }
        compose.onNodeWithTag(CardTags.step(3)).performScrollTo()
        compose.waitForIdle()
        assertEquals("Current step", stateOf(3))
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag(CardTags.step(3)).performClick()
        repeat(FRAMES) {
            compose.mainClock.advanceTimeByFrame()
            assertTrue("the step being folded never leaves the view", inView(3) || inView(4))
        }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertEquals(1, layout(3).lineCount)
        assertEquals("Current step", stateOf(4))
        assertTrue("the next step is brought into view", inView(4))
    }

    @Test
    fun unmarkingAFoldedStepUnfoldsItWhereItIsAndKeepsItInView() {
        marks.hold(BookName.Pool, pot.id, (0..4).map { DoneMark.of(pot.steps[it]) }.toSet())
        open(pot)
        compose.onNodeWithTag(CardTags.step(2)).performScrollTo()
        compose.waitForIdle()
        val before = scrolled()
        val top = compose.onNodeWithTag(CardTags.step(2)).fetchSemanticsNode().positionInRoot.y
        tap(2)
        assertEquals("the card does not scroll", before, scrolled(), 0.5f)
        assertEquals("the step unfolds where it is", top, compose.onNodeWithTag(CardTags.step(2)).fetchSemanticsNode().positionInRoot.y, 0.5f)
        assertTrue(layout(2).lineCount > 1)
        assertEquals("unmarked, the first not done is the current one", "Current step", stateOf(2))
        assertTrue(inView(2))
    }

    private companion object {
        /** More frames than any folding or bringing into view takes. */
        const val FRAMES = 60
    }
}
