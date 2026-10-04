package app.recipefork.app.timer

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.R
import app.recipefork.app.card.assertCardTarget
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.ui.AccessibilityChecks
import app.recipefork.app.ui.Haptic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration.Companion.minutes

/**
 * A timer's controls on the card are buttons with an icon and a label, at least 56 dp, each announcing what it does and
 * showing it in a tooltip on a long press; starting, confirming a restart, cancelling, stopping the ringing and clearing
 * each answer with one confirm haptic — a press that only asks whether to restart, declining it, and a long press for
 * the tooltip with none, nor does the long press start the timer.
 */
@RunWith(AndroidJUnit4::class)
class CardTimerButtonsTest : CardTimersKit() {
    private fun confirms(count: Int) = assertEquals(List(count) { Haptic.CONFIRM }, haptics.all)

    private fun press(tag: String) {
        compose.onNodeWithTag(tag).performScrollTo().performClick()
        compose.waitForIdle()
    }

    /** [tag] is a button announced as [action], at least 56 dp, whose long press shows [action] in a tooltip. */
    private fun assertTimerButton(tag: String, action: Int) {
        val node = compose.onNodeWithTag(tag).performScrollTo().fetchSemanticsNode()
        assertEquals(tag, Role.Button, node.config.getOrNull(SemanticsProperties.Role))
        assertEquals(tag, context.getString(action), AccessibilityChecks.clickActionLabel(node))
        compose.assertCardTarget(tag)
        compose.onNodeWithTag(tag).performTouchInput { longClick() }
        compose.waitUntil(PATIENCE_MS) { shown(TimerTags.tooltip(tag)) }
        fun texts(node: SemanticsNode): List<String> =
            node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } + node.children.flatMap(::texts)
        val tooltip = compose.onNodeWithTag(TimerTags.tooltip(tag), useUnmergedTree = true).fetchSemanticsNode()
        assertEquals(listOf(context.getString(action)), texts(tooltip))
        compose.waitUntil(PATIENCE_MS) { !shown(TimerTags.tooltip(tag)) }
    }

    @Test
    fun eachControlIsAButtonThatSaysWhatItDoes() {
        show(soup)
        assertTimerButton(TimerTags.start(0), R.string.timer_start_action)
        // The long press only showed the tooltip.
        assertTrue(held().isEmpty())
        confirms(0)

        start(0)
        awaitHeld { it.size == 1 }
        compose.waitUntil(PATIENCE_MS) { shown(TimerTags.cancel(0)) }
        assertTimerButton(TimerTags.cancel(0), R.string.timer_cancel_action)

        pass(10.minutes)
        awaitStatus(0, "Finished")
        compose.runOnIdle { timers.ringing.ring(held().single()) }
        compose.waitUntil(PATIENCE_MS) { shown(TimerTags.stop(0)) }
        assertTimerButton(TimerTags.stop(0), R.string.timer_stop_action)
        assertTimerButton(TimerTags.cancel(0), R.string.timer_clear_action)
        AccessibilityChecks.assertTouchTargets(compose, expectAtLeast = 5)
        AccessibilityChecks.assertInteractiveElementsLabelled(compose, expectAtLeast = 5)
    }

    @Test
    fun startRestartCancelStopAndClearEachConfirmOnce() {
        show(soup)
        confirms(0)

        start(0)
        awaitHeld { it.size == 1 }
        confirms(1)

        // Started again while it runs: the press only asks, and confirms nothing; declining confirms nothing either.
        start(0)
        compose.waitUntil(PATIENCE_MS) { shown(TimerTags.RESTART_QUESTION) }
        confirms(1)
        compose.onNodeWithTag(TimerTags.RESTART_DECLINE).performClick()
        compose.waitUntil(PATIENCE_MS) { !shown(TimerTags.RESTART_QUESTION) }
        confirms(1)
        // Asked again and confirmed: the restart confirms once.
        start(0)
        compose.waitUntil(PATIENCE_MS) { shown(TimerTags.RESTART_QUESTION) }
        confirms(1)
        compose.onNodeWithTag(TimerTags.RESTART_CONFIRM).performClick()
        compose.waitUntil(PATIENCE_MS) { !shown(TimerTags.RESTART_QUESTION) }
        confirms(2)

        press(TimerTags.cancel(0))
        awaitHeld { it.isEmpty() }
        confirms(3)

        start(0)
        awaitHeld { it.size == 1 }
        confirms(4)
        pass(10.minutes)
        awaitStatus(0, "Finished")
        compose.runOnIdle { timers.ringing.ring(held().single()) }
        compose.waitUntil(PATIENCE_MS) { shown(TimerTags.stop(0)) }
        confirms(4)

        press(TimerTags.stop(0))
        compose.waitUntil(PATIENCE_MS) { !shown(TimerTags.stop(0)) }
        confirms(5)

        press(TimerTags.cancel(0))
        awaitHeld { it.isEmpty() }
        confirms(6)
        assertTrue(compose.onAllNodes(hasTestTag(TimerTags.cancel(0))).fetchSemanticsNodes().isEmpty())
    }
}
