package app.recipefork.app.card

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.data.HeldLastCardTab
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.timer.LocalCardTimers
import app.recipefork.app.timer.RunningTimer
import app.recipefork.app.timer.TestTimers
import app.recipefork.app.timer.TimerAnnouncer
import app.recipefork.app.timer.TimerKey
import app.recipefork.app.timer.TimerNotices
import app.recipefork.app.timer.TimerStep
import app.recipefork.app.timer.TimerTags
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.Timer
import app.recipefork.core.timer.TimerName
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import kotlin.time.Duration.Companion.minutes

/**
 * FLOW-013 (4) with the stepper: opened from the notification of a timer that rings on a step marked done, the card
 * shows that step unfolded — its timers are read before it is drawn folded — and brings it into view with its timer
 * and the Stop beside it, not a folded line whose timer then unfolds below the screen.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w360dp-h640dp")
class CardStepOpenedAtItsTimerTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val timers = TestTimers(context)
    private val recipes = RecipeRepository(ActiveBook(BookName.Pool, book.database))
    private val marks = DoneMarks.inMemory()

    private val long = "stir the pot slowly with a wooden spoon, scraping the bottom so that nothing catches or burns."

    /** Six steps of several lines each; the fifth carries the timer. */
    private val stew = Recipe.create(Instant.parse("2026-10-12T12:00:00Z"), null).copy(
        title = "Stew",
        servings = 4,
        steps = (0 until 6).map { Step(it, "Step ${it + 1}: $long", timers = if (it == 4) listOf(Timer(10.minutes)) else emptyList()) },
    )

    @Before
    fun setUp() {
        compose.registerIdlingResource(book.idling)
        compose.registerIdlingResource(timers.idling)
    }

    @After
    fun close() {
        compose.unregisterIdlingResource(book.idling)
        compose.unregisterIdlingResource(timers.idling)
        book.close()
        timers.close()
    }

    // The step's timers are read before its fold animates: it stands as they make it at once, never seen unfolding.
    @Test
    fun onOpeningTheStepStandsAsItsTimerMakesItWithoutUnfolding() {
        compose.mainClock.autoAdvance = false
        openAtTheRingingTimer()
        val heights = mutableListOf<Int>()
        fun frame() {
            compose.mainClock.advanceTimeByFrame()
            compose.onAllNodes(hasTestTag(StepRowTags.entry(4)), useUnmergedTree = true).fetchSemanticsNodes().singleOrNull()
                ?.let { heights += it.size.height }
        }
        val deadline = System.currentTimeMillis() + PATIENCE_MS
        while (compose.onAllNodes(hasTestTag(TimerTags.stop(4))).fetchSemanticsNodes().isEmpty()) {
            check(System.currentTimeMillis() < deadline) { "the ringing timer never showed: $heights" }
            frame()
        }
        repeat(FRAMES) { frame() }
        compose.mainClock.autoAdvance = true
        val sizes = heights.distinct()
        assertTrue("the step went straight to its height, in no more than two: $sizes", sizes.size <= 2 && sizes.last() == sizes.max())
    }

    private companion object {
        /** More frames than any fold takes. */
        const val FRAMES = 60
    }

    /** Notices that are always shown. */
    private object Shown : TimerNotices {
        override fun announce(timer: RunningTimer) = true
        override fun withdraw(key: TimerKey) = Unit
    }

    /** The recipe saved, its fifth step marked done, its timer ringing, and the card opened at it from its notification. */
    private fun openAtTheRingingTimer(): () -> Boolean {
        runBlocking {
            recipes.save(stew)
            val step = stew.steps[4]
            timers.store.start(TimerKey(BookName.Pool, stew.id, 4), TimerName.of(step, 0)!!, stew.title, 10.minutes, TimerName.source(step, 0))
        }
        timers.clock.advance(11.minutes)
        runBlocking { TimerAnnouncer(Shown, timers.ringing).announceEnded(timers.store) }
        marks.hold(BookName.Pool, stew.id, setOf(DoneMark.of(stew.steps[4])))
        var shown = false
        val toShow = StepToShow(MutableStateFlow(TimerStep(BookName.Pool.fileName, stew.id.value, 4))) { shown = true }
        val environment = CardEnvironment(marks = marks, lastTab = HeldLastCardTab(CardMode.COOKING), ingredients = IngredientEntries { emptyList() })
        compose.setContent {
            CompositionLocalProvider(LocalCardTimers provides timers.card, LocalStepToShow provides toShow) {
                MaterialTheme { CardScreen(stew.id, recipes, environment, onBack = {}, onEdit = {}) }
            }
        }
        return { shown }
    }

    @Test
    fun openedAtADoneStepWhoseTimerRingsTheStepShowsUnfoldedWithItsStop() {
        val shown = openAtTheRingingTimer()
        compose.awaitCard()
        compose.waitUntil(PATIENCE_MS) { shown() && compose.onAllNodes(hasTestTag(TimerTags.stop(4))).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()

        val pager = compose.onNodeWithTag(CardTags.PAGER).fetchSemanticsNode().boundsInRoot
        val stop = compose.onNodeWithTag(TimerTags.stop(4)).fetchSemanticsNode()
        val top = stop.positionInRoot.y
        assertTrue("the Stop ($top to ${top + stop.size.height}) is in view (${pager.top} to ${pager.bottom})",
            top >= pager.top - 0.5f && top + stop.size.height <= pager.bottom + 0.5f)
        val step = compose.onNodeWithTag(CardTags.step(4)).fetchSemanticsNode()
        assertTrue("the step is in view", step.positionInRoot.y >= pager.top - 0.5f)
        assertTrue("its text unfolded", compose.textLayoutsWithin(StepTags.text(4)).single().lineCount > 1)
    }
}
