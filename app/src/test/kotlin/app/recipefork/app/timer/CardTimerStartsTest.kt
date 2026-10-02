package app.recipefork.app.timer

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.Timer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * What a timer is started as, and that its start and cancel are finished whatever becomes of the card: a held timer
 * carries its name, its recipe's title and its duration; one its step no longer carries is dropped; a start or a
 * cancel outlives the card (FLOW-013 (7)); and a start asks before restarting a running timer, whatever the card has
 * loaded so far (FLOW-021).
 */
@RunWith(AndroidJUnit4::class)
class CardTimerStartsTest : CardTimersKit() {
    // FLOW-013 (3), FLOW-016 (3, 5): the timer is held as it was started — the given or derived name, and the title.
    @Test
    fun aStartedTimerCarriesItsNameTitleAndDuration() {
        show(soup)
        start(0)
        start(2)
        awaitHeld { it.size == 2 }
        val held = held().associateBy { it.key.stepPosition }
        assertEquals("Bring the water to the boil", held.getValue(0).name)
        assertEquals("Beans", held.getValue(2).name)
        assertEquals("Soup", held.getValue(0).recipe)
        assertEquals(1.hours + 30.minutes, held.getValue(2).duration)
        assertEquals(held.getValue(2), timers.announcements.started.last())
    }

    // A held timer whose step no longer carries a timer is dropped, and its announcement withdrawn.
    @Test
    fun aTimerItsStepNoLongerCarriesIsDropped() {
        show(soup)
        start(0)
        start(2)
        awaitHeld { it.size == 2 }
        runBlocking { recipes.save(soup.copy(steps = soup.steps.map { if (it.position == 0) it.copy(timer = null) else it })) }
        awaitHeld { it.map { timer -> timer.key.stepPosition } == listOf(2) }
        assertEquals(listOf(key(0)), timers.announcements.cancelled)
    }

    // A held timer whose step now carries another — another duration, or another name — is dropped.
    @Test
    fun aTimerItsStepNowCarriesAnotherIsDropped() {
        show(soup)
        start(0)
        start(2)
        awaitHeld { it.size == 2 }
        val changed = soup.steps.map {
            when (it.position) {
                0 -> it.copy(timer = Timer(12.minutes))
                2 -> it.copy(timer = Timer(1.hours + 30.minutes, "Chickpeas"))
                else -> it
            }
        }
        runBlocking { recipes.save(soup.copy(steps = changed)) }
        awaitHeld { it.isEmpty() }
        assertEquals(setOf(key(0), key(2)), timers.announcements.cancelled.toSet())
        awaitStatus(0, "12 min")
    }

    // The steps reordered: the timer held at a position is not announced as whichever step sits there now.
    @Test
    fun aTimerIsNotAnnouncedAsTheStepThatNowSitsAtItsPosition() {
        show(soup)
        start(2)
        awaitHeld { it.size == 1 }
        val swapped = listOf(
            Step(0, beansText, timer = Timer(1.hours + 30.minutes, "Beans")),
            Step(1, "Stir"),
            Step(2, "Bring the water to the boil", timer = Timer(1.hours + 30.minutes)),
        )
        runBlocking { recipes.save(soup.copy(steps = swapped)) }
        awaitHeld { it.isEmpty() }
    }

    // A timer whose step still carries it is kept.
    @Test
    fun aTimerItsStepStillCarriesIsKept() {
        show(soup)
        start(0)
        awaitHeld { it.size == 1 }
        runBlocking { recipes.save(soup.copy(title = "Pea soup")) }
        compose.waitForIdle()
        pass(1.minutes)
        awaitStatus(0, "9:00")
        assertEquals(1, held().size)
        assertTrue(timers.announcements.cancelled.isEmpty())
    }

    // A start finishes its write and its announcement even when the card is left at once.
    @Test
    fun aStartFinishesWhenTheCardIsLeftAtOnce() {
        show(soup)
        control(0).performClick()
        leaveTheCard()
        compose.waitUntil(PATIENCE_MS) { shown(AWAY) }
        awaitHeld { it.size == 1 }
        compose.waitUntil(PATIENCE_MS) { timers.announcements.started.size == 1 }
    }

    // FLOW-013 (7): a cancel finishes, its announcement withdrawn, even when the card is left at once.
    @Test
    fun aCancelFinishesWhenTheCardIsLeftAtOnce() {
        show(soup)
        start(0)
        awaitStatus(0, "10:00")
        compose.onNodeWithTag(TimerTags.cancel(0)).performClick()
        leaveTheCard()
        compose.waitUntil(PATIENCE_MS) { shown(AWAY) }
        awaitHeld { it.isEmpty() }
        compose.waitUntil(PATIENCE_MS) { timers.announcements.cancelled == listOf(key(0)) }
    }

    // FLOW-021: a quick second tap after a start asks before restarting; the timer started once.
    @Test
    fun aQuickSecondTapAsksBeforeRestarting() {
        show(soup)
        val control = control(0)
        control.performClick()
        control.performClick()
        compose.waitUntil(PATIENCE_MS) { shown(TimerTags.RESTART_QUESTION) }
        assertEquals(1, timers.announcements.started.size)
    }

    // FLOW-021: a start tapped before the card has loaded the held timers still asks about the running one.
    @Test
    fun aStartBeforeTheHeldTimersLoadAsksAboutTheRunningOne() {
        runBlocking { timers.store.start(key(0), "Bring the water to the boil", "Soup", 10.minutes) }
        show(soup)
        control(0).performClick()
        compose.waitUntil(PATIENCE_MS) { shown(TimerTags.RESTART_QUESTION) }
        compose.onNodeWithTag(TimerTags.RESTART_DECLINE).performClick()
        compose.waitUntil(PATIENCE_MS) { !shown(TimerTags.RESTART_QUESTION) }
        assertEquals(1, timers.announcements.started.size)
        assertFalse(compose.onAllNodes(hasTestTag(TimerTags.RESTART_QUESTION)).fetchSemanticsNodes().isNotEmpty())
    }
}
