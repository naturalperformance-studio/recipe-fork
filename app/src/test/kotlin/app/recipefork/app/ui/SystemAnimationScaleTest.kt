package app.recipefork.app.ui

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.editor.RowsColumn
import app.recipefork.app.editor.rememberRowMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.roundToInt

/**
 * With the system's animator duration scale at 0 — animations turned off in the phone's settings — nothing the app
 * shows moves. On the phone Compose hands the system's scale to every animation as its [MotionDurationScale] (the
 * window's recomposer watches the setting); here the test hands it the same way. At a scale of 0 the app's motions —
 * a block opening (the fixing control, the proportions warning, on [Motion.open]), the timers' bar rising
 * ([Motion.rise]) and an editor row added ([rememberRowMotion]) — stand at their end in the frame they come in; at a
 * scale of 1 that frame finds them on their way.
 */
@RunWith(AndroidJUnit4::class)
class SystemAnimationScaleTest {
    /** The scale the composition's animations run at, as a real window hands on the system's. */
    private var scale = 1f

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>(
        object : MotionDurationScale {
            override val scaleFactor: Float get() = scale
        },
    )

    private val context = ApplicationProvider.getApplicationContext<Context>()

    /** Where each motion stands a frame after it starts: the block's height, the bar's offset, the row's height. */
    private data class Stand(val block: Int, val bar: Int, val row: Int)

    private fun aFrameIn(): Stand {
        var shown by mutableStateOf(false)
        var block = -1
        var barTop = -1f
        var barHome = 0f
        var row = -1
        compose.setContent {
            Column {
                AnimatedVisibility(shown, Modifier.onSizeChanged { block = it.height }, enter = Motion.open, exit = Motion.close) {
                    Box(Modifier.size(FULL.dp))
                }
                AnimatedVisibility(shown, Modifier.onGloballyPositioned { barHome = it.positionInRoot().y }, enter = Motion.rise, exit = Motion.sink) {
                    Box(Modifier.size(FULL.dp).onGloballyPositioned { barTop = it.positionInRoot().y })
                }
                val motion = rememberRowMotion()
                val keys = if (shown) listOf(1L) else emptyList()
                motion.update(keys)
                RowsColumn(0.dp) {
                    keys.forEach { k -> key(k) { Box(Modifier.onSizeChanged { row = it.height }.then(motion.row(k)).size(FULL.dp)) } }
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread {
            shown = true
            Snapshot.sendApplyNotifications()
        }
        // The frame they are composed in, and the first frames of their animations.
        repeat(3) { compose.mainClock.advanceTimeByFrame() }
        val stand = Stand(block, if (barTop < 0) -1 else (barTop - barHome).roundToInt(), row)
        compose.mainClock.autoAdvance = true
        return stand
    }

    private val full get() = (FULL * context.resources.displayMetrics.density).roundToInt()

    @Test
    fun atAScaleOfZeroWhatAppearsStandsAtItsEndInItsFirstFrame() {
        scale = 0f
        assertEquals(Stand(block = full, bar = 0, row = full), aFrameIn())
    }

    @Test
    fun atAScaleOfOneTheSameFrameFindsThemOnTheirWay() {
        scale = 1f
        val stand = aFrameIn()
        assertTrue("$stand of $full", stand.block in 1 until full && stand.bar in 1 until full && stand.row in 1 until full)
    }

    private companion object {
        const val FULL = 100
    }
}
