package app.recipefork.app.ui

import androidx.compose.ui.test.junit4.ComposeTestRule
import app.recipefork.app.editor.PATIENCE_MS
import org.junit.Assert.assertTrue
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Checks that what [measure] reads — a height, where something stands — changes gradually once [act] is done: with the
 * test clock held, frame by frame, the first value that differs from where it started lies strictly between that and
 * where it settles. Without motion it would stand at its end in the first frame that changes it.
 */
fun ComposeTestRule.assertChangesGradually(what: String, measure: () -> Float, act: () -> Unit) {
    waitForIdle()
    val from = measure()
    mainClock.autoAdvance = false
    try {
        act()
        waitUntil(PATIENCE_MS) {
            mainClock.advanceTimeByFrame()
            abs(measure() - from) > 1f
        }
        val between = measure()
        mainClock.autoAdvance = true
        waitForIdle()
        val to = measure()
        assertTrue("$what: it changes (from $from to $to)", abs(to - from) > 1f)
        assertTrue("$what: on its way from $from to $to it stood at $between", between > min(from, to) + 0.5f && between < max(from, to) - 0.5f)
    } finally {
        mainClock.autoAdvance = true
    }
}
