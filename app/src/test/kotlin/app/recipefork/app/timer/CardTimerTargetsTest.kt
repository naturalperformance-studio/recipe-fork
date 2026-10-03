package app.recipefork.app.timer

import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.card.CardTarget
import app.recipefork.app.card.assertCardTarget
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.ui.AccessibilityChecks
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration.Companion.minutes

/**
 * A11Y-003 (2), A11Y-007: a timer's controls on the card are at least 56 by 56 dp — its start, its cancel while it
 * runs, its stop while it rings and its clear once finished — and so are the buttons of the restart question and of
 * the explanation that a timer will not be heard.
 */
@RunWith(AndroidJUnit4::class)
class CardTimerTargetsTest : CardTimersKit() {
    @Test
    fun startCancelStopAndClearAreAtLeast56dp() {
        show(soup)
        compose.assertCardTarget(TimerTags.start(0))
        compose.assertCardTarget(TimerTags.start(2))
        start(0)
        awaitHeld { it.size == 1 }
        compose.waitUntil(PATIENCE_MS) { shown(TimerTags.cancel(0)) }
        compose.assertCardTarget(TimerTags.cancel(0))
        AccessibilityChecks.assertTouchTargets(compose, min = CardTarget, expectAtLeast = 4)

        // Started again while it runs: the restart question.
        start(0)
        compose.waitUntil(PATIENCE_MS) { shown(TimerTags.RESTART_QUESTION) }
        compose.assertCardTarget(TimerTags.RESTART_CONFIRM)
        compose.assertCardTarget(TimerTags.RESTART_DECLINE)
        AccessibilityChecks.assertTouchTargets(compose, min = CardTarget, expectAtLeast = 6)
        compose.onNodeWithTag(TimerTags.RESTART_DECLINE).performClick()
        compose.waitUntil(PATIENCE_MS) { !shown(TimerTags.RESTART_QUESTION) }

        // Finished and ringing: stop beside clear.
        pass(10.minutes)
        awaitStatus(0, "Finished")
        compose.runOnIdle { timers.ringing.ring(held().single()) }
        compose.waitUntil(PATIENCE_MS) { shown(TimerTags.stop(0)) }
        compose.assertCardTarget(TimerTags.stop(0))
        compose.assertCardTarget(TimerTags.cancel(0))
        AccessibilityChecks.assertTouchTargets(compose, min = CardTarget, expectAtLeast = 5)
    }

    @Test
    fun theUnheardExplanationsButtonsAreAtLeast56dp() {
        timers.access.state = NotificationState.OFF
        timers.access.hasRequest = false
        show(soup)
        start(0)
        compose.waitUntil(PATIENCE_MS) { shown(TimerTags.UNHEARD) }
        compose.assertCardTarget(TimerTags.UNHEARD_SETTINGS)
        compose.assertCardTarget(TimerTags.UNHEARD_DISMISS)
        AccessibilityChecks.assertTouchTargets(compose, min = CardTarget, expectAtLeast = 2)
    }
}
