package app.recipefork.app.timer

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.card.CardTags
import app.recipefork.app.card.assertCardTarget
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.Timer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.minutes

/**
 * A step with several timers on the card (DOM-024): each has its own control and state, starts, runs, ends and rings
 * on its own; a running one is asked about before it restarts, apart from the others; one whose step no longer
 * carries it is dropped alone; the running-timers bar lists each, the unnamed ones told apart by their numbers.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "en-w320dp-h640dp")
class CardStepTimersTest : CardTimersKit() {
    private val long = "Keep stirring slowly over a low heat, scraping the bottom of the pan so that nothing catches or burns"
    private val pasta = Recipe.create(now, owner = null).copy(
        title = "Pasta",
        steps = listOf(
            Step(0, "Salt the water"),
            Step(1, "Boil the pasta", timers = listOf(Timer(10.minutes), Timer(3.minutes))),
            Step(2, "Make the sauce", timers = listOf(Timer(20.minutes, "Sauce"), Timer(5.minutes, "Garlic"))),
            Step(3, "Rest the pasta", timers = listOf(Timer(2.minutes))),
            Step(4, "Drain the pasta", timers = listOf(Timer(5.minutes), Timer(10.minutes))),
        ) + (5..22).map { Step(it, long) },
    )
    private val last = 22

    private fun showPasta() {
        runBlocking { recipes.save(pasta) }
        show(pasta)
    }

    private fun pastaKey(position: Int, index: Int) = TimerKey(app.recipefork.app.book.BookName.Pool, pasta.id, position, index)

    private fun heldOf(position: Int, index: Int) = held().singleOrNull { it.key == pastaKey(position, index) }

    private fun texts(tag: String): List<String> = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().singleOrNull()
        ?.config?.getOrElseNullable(SemanticsProperties.Text) { null }.orEmpty().map { it.text }

    @Test
    fun eachTimerOfAStepHasItsOwnControlAndState() {
        showPasta()
        // Unnamed timers sharing their step's name are told apart by their numbers; named ones read by their names.
        assertTrue(texts(TimerTags.start(1, 0)).contains("Boil the pasta · timer 1"))
        assertTrue(texts(TimerTags.start(1, 1)).contains("Boil the pasta · timer 2"))
        assertTrue(texts(TimerTags.start(2, 0)).contains("Sauce"))
        assertTrue(texts(TimerTags.start(2, 1)).contains("Garlic"))
        assertEquals("10 min", statusOf(1, 0))
        assertEquals("3 min", statusOf(1, 1))

        start(1, 1)
        awaitHeld { it.size == 1 }
        val started = held().single()
        assertEquals(pastaKey(1, 1), started.key)
        assertEquals("Boil the pasta", started.name)
        assertEquals(2, started.number)
        assertEquals(3.minutes, started.duration)
        awaitStatus(1, 1, "3:00")
        assertEquals("10 min", statusOf(1, 0))
        assertTrue(shown(TimerTags.cancel(1, 1)))
        assertFalse(shown(TimerTags.cancel(1, 0)))
        // Each control is a card target of its own.
        compose.assertCardTarget(TimerTags.start(1, 0))
        compose.assertCardTarget(TimerTags.start(1, 1))
        compose.assertCardTarget(TimerTags.cancel(1, 1))

        // A named timer of a step with several is held under its name, with no number.
        start(2, 1)
        awaitHeld { it.size == 2 }
        assertEquals("Garlic", heldOf(2, 1)!!.name)
        assertNull(heldOf(2, 1)!!.number)
    }

    @Test
    fun twoTimersOfAStepRunEndAndRingOnTheirOwn() {
        showPasta()
        start(1, 0)
        start(1, 1)
        awaitHeld { it.size == 2 }
        pass(3.minutes)
        awaitStatus(1, 1, "Finished")
        awaitStatus(1, 0, "7:00")
        compose.runOnIdle { timers.ringing.ring(heldOf(1, 1)!!) }
        compose.waitUntil(PATIENCE_MS) { shown(TimerTags.stop(1, 1)) }
        assertFalse(shown(TimerTags.stop(1, 0)))

        pass(7.minutes)
        awaitStatus(1, 0, "Finished")
        compose.runOnIdle { timers.ringing.ring(heldOf(1, 0)!!) }
        compose.waitUntil(PATIENCE_MS) { shown(TimerTags.stop(1, 0)) }

        // Stopping one leaves the other ringing.
        compose.onNodeWithTag(TimerTags.stop(1, 1)).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { !shown(TimerTags.stop(1, 1)) }
        assertEquals(setOf(pastaKey(1, 0)), timers.ringing.ringing.value)
        assertTrue(timers.sound.ringing)
        assertTrue(shown(TimerTags.stop(1, 0)))

        // Clearing one leaves the other.
        compose.onNodeWithTag(TimerTags.cancel(1, 1)).performScrollTo().performClick()
        awaitHeld { timers -> timers.map { it.key } == listOf(pastaKey(1, 0)) }
        awaitStatus(1, 0, "Finished")
        awaitStatus(1, 1, "3 min")
    }

    // FLOW-021: a running timer is asked about before it restarts; another of its step starts with no question.
    @Test
    fun restartAsksFirstForEachTimerApart() {
        showPasta()
        start(1, 0)
        awaitHeld { it.size == 1 }
        start(1, 1)
        awaitHeld { it.size == 2 }
        assertFalse(shown(TimerTags.RESTART_QUESTION))
        pass(1.minutes)
        val second = heldOf(1, 1)!!.endsAt
        val first = heldOf(1, 0)!!.endsAt

        start(1, 0)
        compose.waitUntil(PATIENCE_MS) { shown(TimerTags.RESTART_QUESTION) }
        assertTrue(
            compose.onAllNodes(hasText("Boil the pasta · timer 1", substring = true), true).fetchSemanticsNodes().isNotEmpty(),
        )
        compose.onNodeWithTag(TimerTags.RESTART_CONFIRM).performClick()
        awaitHeld { heldOf(1, 0)?.endsAt != first }
        assertEquals(first.plusSeconds(60), heldOf(1, 0)!!.endsAt)
        assertEquals(second, heldOf(1, 1)!!.endsAt)
    }

    // A timer whose duration or name changed in the editor is dropped; the others of its step keep running.
    @Test
    fun aChangedTimerIsDroppedAndTheOthersOfItsStepRunOn() {
        showPasta()
        start(1, 0)
        start(2, 0)
        start(2, 1)
        awaitHeld { it.size == 3 }

        fun saveStep2(timers: List<Timer>) = runBlocking {
            recipes.save(pasta.copy(steps = pasta.steps.map { if (it.position == 2) it.copy(timers = timers) else it }))
        }
        saveStep2(listOf(Timer(20.minutes, "Sauce"), Timer(6.minutes, "Garlic")))
        awaitHeld { it.size == 2 }
        assertEquals(listOf(pastaKey(2, 1)), timers.announcements.cancelled)
        assertEquals(setOf(pastaKey(1, 0), pastaKey(2, 0)), held().map { it.key }.toSet())

        saveStep2(listOf(Timer(20.minutes, "Ragù"), Timer(6.minutes, "Garlic")))
        awaitHeld { it.size == 1 }
        assertEquals(listOf(pastaKey(2, 1), pastaKey(2, 0)), timers.announcements.cancelled)
        assertEquals(listOf(pastaKey(1, 0)), held().map { it.key })
    }

    // The bar lists each running timer of a step, the unnamed ones by their step's number and their own.
    @Test
    fun theBarListsEachTimerOfAStep() {
        showPasta()
        start(1, 0)
        start(1, 1)
        start(2, 0)
        awaitHeld { it.size == 3 }
        compose.onNodeWithTag(CardTags.step(last)).performScrollTo()
        compose.waitUntil(PATIENCE_MS) {
            shown(TimerTags.barTimer(1, 0)) && shown(TimerTags.barTimer(1, 1)) && shown(TimerTags.barTimer(2, 0))
        }
        // As the card and the notification read them: the name, then the number that tells it apart.
        assertTrue(texts(TimerTags.barTimer(1, 0)).contains("Boil the pasta · timer 1"))
        assertTrue(texts(TimerTags.barTimer(1, 1)).contains("Boil the pasta · timer 2"))
        assertTrue(texts(TimerTags.barTimer(2, 0)).contains("Sauce"))

        // One ends and rings: its line offers Stop, which stops it alone.
        pass(3.minutes)
        compose.runOnIdle { timers.ringing.ring(heldOf(1, 1)!!) }
        compose.waitUntil(PATIENCE_MS) { shown(TimerTags.barStop(1, 1)) }
        assertTrue(shown(TimerTags.barCancel(1, 0)))
        compose.onNodeWithTag(TimerTags.barStop(1, 1)).performClick()
        compose.waitUntil(PATIENCE_MS) { !shown(TimerTags.barTimer(1, 1)) }
        assertTrue(timers.ringing.ringing.value.isEmpty())

        // Cancelling one from the bar leaves the other of its step.
        compose.onNodeWithTag(TimerTags.barCancel(1, 0)).performClick()
        awaitHeld { timers -> timers.none { it.key == pastaKey(1, 0) } }
        assertEquals(setOf(pastaKey(1, 1), pastaKey(2, 0)), held().map { it.key }.toSet())
    }

    // A timer added to a step while one of its timers runs: the running one reads, on the card and in the bar, as it
    // started — as its notification does — while the new one is told apart by its number.
    private fun saveStep(position: Int, timers: List<Timer>) = runBlocking {
        recipes.save(pasta.copy(steps = pasta.steps.map { if (it.position == position) it.copy(timers = timers) else it }))
    }

    @Test
    fun aRunningTimerReadsAsItStartedAfterATimerIsAddedToItsStep() {
        showPasta()
        start(3, 0)
        awaitHeld { it.size == 1 }
        assertNull(heldOf(3, 0)!!.number)
        saveStep(3, listOf(Timer(2.minutes), Timer(4.minutes)))
        compose.waitUntil(PATIENCE_MS) { shown(TimerTags.start(3, 1)) }
        assertTrue(texts(TimerTags.start(3, 0)).contains("Rest the pasta"))
        assertTrue(texts(TimerTags.start(3, 1)).contains("Rest the pasta · timer 2"))
        assertEquals(listOf(pastaKey(3, 0)), held().map { it.key })

        compose.onNodeWithTag(CardTags.step(last)).performScrollTo()
        compose.waitUntil(PATIENCE_MS) { shown(TimerTags.barTimer(3, 0)) }
        assertTrue(texts(TimerTags.barTimer(3, 0)).contains("Rest the pasta"))
    }

    // A running timer is the step's timer at its index: an earlier timer removed, the one that slides into its place
    // does not take over its run — the run ends, and the timer now there stands unstarted.
    @Test
    fun removingAnEarlierTimerEndsTheRunOfTheNext() {
        showPasta()
        start(4, 1)
        awaitHeld { it.size == 1 }
        awaitStatus(4, 1, "10:00")
        saveStep(4, listOf(Timer(10.minutes)))
        awaitHeld { it.isEmpty() }
        assertEquals(listOf(pastaKey(4, 1)), timers.announcements.cancelled)
        awaitStatus(4, 0, "10 min")
        assertFalse(shown(TimerTags.start(4, 1)))
        assertFalse(shown(TimerTags.cancel(4, 0)))
    }
}
