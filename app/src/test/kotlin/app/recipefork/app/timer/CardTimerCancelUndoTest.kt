package app.recipefork.app.timer

import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.R
import app.recipefork.app.card.CardTags
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.Timer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.minutes

/**
 * v0.38, T-270: cancelling a running timer — on its step or in the bar — says "Timer cancelled" in the card's notice,
 * with Undo; Undo brings it back as it was, ending at the same moment: running on, or, where that moment passed while
 * the notice showed, finished and ringing. A timer started again meanwhile is the reader's latest, and Undo leaves it.
 * Clearing a finished timer says nothing.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "en-w411dp-h891dp")
class CardTimerCancelUndoTest : CardTimersKit() {
    private val inNotice = hasAnyAncestor(hasTestTag(CardTags.SNACKBAR))
    private val said get() = context.getString(R.string.card_timer_cancelled)
    private val undo get() = context.getString(R.string.card_undo)

    private fun noticeShown() = compose.onAllNodes(hasText(said) and inNotice).fetchSemanticsNodes().isNotEmpty()

    private fun awaitNotice() = compose.waitUntil(PATIENCE_MS) { noticeShown() }

    private fun takeUndo() {
        awaitNotice()
        compose.onNode(hasText(undo) and inNotice).performClick()
        compose.waitForIdle()
    }

    private fun cancel(position: Int) {
        compose.onNodeWithTag(TimerTags.cancel(position)).performScrollTo().performClick()
        compose.waitForIdle()
    }

    @Test
    fun cancellingOnItsStepOffersUndoThatBringsItBackWithTheSameEnd() {
        show(soup)
        start(0)
        awaitHeld { it.size == 1 }
        pass(2.minutes)
        awaitStatus(0, "8:00")
        val before = held().single()

        cancel(0)
        awaitHeld { it.isEmpty() }
        awaitNotice()
        assertTrue(timers.announcements.cancelled.contains(key(0)))
        pass(1.minutes)

        takeUndo()
        awaitHeld { it.size == 1 }
        val back = held().single()
        assertEquals(before.endsAt, back.endsAt)
        assertEquals(before.copy(announced = false), back)
        // Its announcement is set again, for the same end.
        assertEquals(before.endsAt, timers.announcements.started.last().endsAt)
        awaitStatus(0, "7:00")
        compose.waitUntil(PATIENCE_MS) { !noticeShown() }
    }

    // At the usual font scale the notice's Undo stands beside its words (at large text: CardAtLargeTextTest).
    @Test
    fun atTheUsualFontScaleTheNoticesUndoStandsBesideItsWords() {
        show(soup)
        start(0)
        awaitHeld { it.size == 1 }
        cancel(0)
        awaitNotice()
        compose.waitForIdle()
        val words = compose.onNode(hasText(said) and inNotice, useUnmergedTree = true).getBoundsInRoot()
        val action = compose.onNode(hasText(undo) and inNotice, useUnmergedTree = true).getBoundsInRoot()
        assertTrue("Undo ($action) stands beside the words ($words)", action.left >= words.right && action.top < words.bottom)
    }

    @Test
    fun cancellingInTheBarOffersUndoThatBringsItBackWithTheSameEnd() {
        val stir = "Keep stirring slowly over a low heat, scraping the bottom of the pan so that nothing catches or burns"
        val stew = Recipe.create(now, owner = null).copy(
            title = "Stew",
            steps = listOf(Step(0, "Boil the rice", timer = Timer(10.minutes, "Rice"))) + (1..25).map { Step(it, stir) },
        ).also { runBlocking { recipes.save(it) } }
        show(stew)
        start(0)
        awaitHeld { it.size == 1 }
        val before = held().single()
        compose.onNodeWithTag(CardTags.step(25)).performScrollTo()
        compose.waitUntil(PATIENCE_MS) { shown(TimerTags.barCancel(0)) }
        pass(3.minutes)

        compose.onNodeWithTag(TimerTags.barCancel(0)).performClick()
        awaitHeld { it.isEmpty() }
        compose.waitUntil(PATIENCE_MS) { !shown(TimerTags.barTimer(0)) }

        takeUndo()
        awaitHeld { it.size == 1 }
        assertEquals(before.endsAt, held().single().endsAt)
        compose.waitUntil(PATIENCE_MS) { shown(TimerTags.barTimer(0)) }
        compose.waitUntil(PATIENCE_MS) {
            compose.onAllNodes(hasTestTag(TimerTags.barStatus(0)) and hasText("7:00"), true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    // Its end passed while the notice showed: Undo brings it back finished, not yet announced, so that it is announced
    // and rings at once, as it would have.
    @Test
    fun undoAfterItsEndHasPassedBringsItBackRinging() {
        show(soup)
        start(0)
        awaitHeld { it.size == 1 }
        val before = held().single()
        pass(9.minutes)
        cancel(0)
        awaitHeld { it.isEmpty() }
        pass(5.minutes)

        takeUndo()
        awaitHeld { it.size == 1 }
        val back = held().single()
        assertEquals(before.endsAt, back.endsAt)
        assertFalse(back.announced)
        awaitStatus(0, context.getString(R.string.timer_finished))

        // What announces timers on the device finds it ended and unannounced: it rings, and the card offers Stop.
        runBlocking { TimerAnnouncer(RecordedNotices(), timers.ringing).announceEnded(timers.store) }
        assertTrue(timers.sound.ringing)
        compose.waitUntil(PATIENCE_MS) { shown(TimerTags.stop(0)) }
    }

    // A timer started again while the notice showed is the reader's latest: Undo does not put the old one back over it.
    @Test
    fun undoLeavesATimerStartedAgainMeanwhile() {
        show(soup)
        start(0)
        awaitHeld { it.size == 1 }
        cancel(0)
        awaitHeld { it.isEmpty() }
        awaitNotice()
        pass(4.minutes)
        start(0)
        awaitHeld { it.size == 1 }
        val again = held().single()

        takeUndo()
        compose.waitForIdle()
        awaitHeld { it.size == 1 }
        assertEquals(again, held().single())
        awaitStatus(0, "10:00")
    }

    // Clearing a finished timer is no cancelling: nothing is said, and nothing offers to put it back.
    @Test
    fun clearingAFinishedTimerSaysNothing() {
        show(soup)
        start(0)
        awaitHeld { it.size == 1 }
        pass(10.minutes)
        awaitStatus(0, context.getString(R.string.timer_finished))
        cancel(0)
        awaitHeld { it.isEmpty() }
        compose.waitForIdle()
        assertFalse(noticeShown())
    }
}
