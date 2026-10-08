package app.recipefork.app.editor

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A removed row's picture folds where the row stood, before the row that followed it — and stays there when a row
 * above it is removed while it still folds.
 */
@RunWith(AndroidJUnit4::class)
class RowMotionTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var keys by mutableStateOf(listOf(0, 1, 2, 3, 4))

    private fun bounds(tag: String): Rect = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    /** The space between the bottom of row [above] and the top of row [below]: what folds between them. */
    private fun between(above: Int, below: Int): Float = bounds("row-$below").top - bounds("row-$above").bottom

    private val row get() = ROW * compose.density.density

    private fun show(next: List<Int>) = compose.runOnUiThread {
        keys = next
        Snapshot.sendApplyNotifications()
    }

    @Test
    fun aFoldingPictureStaysInItsPlaceWhenARowAboveItIsRemoved() {
        compose.setContent {
            val motion = rememberRowMotion()
            val shown = keys
            motion.update(shown)
            Column(Modifier.fillMaxWidth().testTag(LIST)) {
                shown.forEachIndexed { position, k ->
                    motion.LeavingAt(position)
                    key(k) { Box(Modifier.fillMaxWidth().then(motion.row(k)).height(ROW.dp).testTag("row-$k")) }
                }
                motion.LeavingAt(shown.size, last = true)
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        try {
            // Row 3 goes; its picture folds between rows 2 and 4.
            show(listOf(0, 1, 2, 4))
            compose.waitUntil(PATIENCE_MS) {
                compose.mainClock.advanceTimeByFrame()
                between(2, 4) in 1f..row - 1f
            }
            // Row 1, above it, goes too while it folds: the picture stays before row 4, and nothing follows row 4.
            show(listOf(0, 2, 4))
            repeat(2) { compose.mainClock.advanceTimeByFrame() }
            assertTrue("row 3's picture is no longer between rows 2 and 4: ${between(2, 4)}", between(2, 4) >= 1f)
            assertEquals("something folds after the last row", bounds(LIST).bottom, bounds("row-4").bottom, 0.5f)
            assertTrue("row 1's picture is not between rows 0 and 2: ${between(0, 2)}", between(0, 2) >= 1f)
        } finally {
            compose.mainClock.autoAdvance = true
        }
        compose.waitForIdle()
        assertEquals(0f, between(0, 2), 0.5f)
        assertEquals(0f, between(2, 4), 0.5f)
        assertEquals(3 * row, bounds(LIST).height, 0.5f)
    }

    private companion object {
        const val ROW = 40
        const val LIST = "rows"
    }
}
