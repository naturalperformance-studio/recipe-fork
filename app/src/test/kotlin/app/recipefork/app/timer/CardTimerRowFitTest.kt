package app.recipefork.app.timer

import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.R
import app.recipefork.app.card.assertCardTarget
import app.recipefork.app.card.assertWhole
import app.recipefork.app.card.textLayoutsWithin
import app.recipefork.app.editor.PATIENCE_MS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.time.Duration.Companion.minutes

/**
 * A11Y-004 with A11Y-003 (2): on a 320 dp-wide phone at twice the font scale, a finished timer that rings shows its
 * name, its status, Stop and Clear each whole and readable — the actions wrap under the name and status rather than
 * squeezing them — in English and in Russian.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CardTimerRowFitTest : CardTimersKit() {
    override val fontScale = 2f

    @Test
    @Config(qualifiers = "en-w320dp-h640dp")
    fun aRingingTimerReadsWholeInEnglish() = ringingReadsWhole()

    @Test
    @Config(qualifiers = "ru-w320dp-h640dp")
    fun aRingingTimerReadsWholeInRussian() = ringingReadsWhole()

    private fun ringingReadsWhole() {
        show(soup)
        start(0)
        awaitHeld { it.size == 1 }
        pass(10.minutes)
        awaitStatus(0, context.getString(R.string.timer_finished))
        compose.runOnIdle { timers.ringing.ring(held().single()) }
        compose.waitUntil(PATIENCE_MS) { shown(TimerTags.stop(0)) }
        compose.onNodeWithTag(TimerTags.stop(0)).performScrollTo()
        compose.waitForIdle()
        listOf(TimerTags.start(0), TimerTags.stop(0), TimerTags.cancel(0)).forEach {
            compose.assertWhole(it)
            compose.assertCardTarget(it)
        }
        // The status on one line, the actions' labels on one line each.
        listOf(TimerTags.status(0), TimerTags.stop(0), TimerTags.cancel(0)).forEach { tag ->
            compose.textLayoutsWithin(tag).forEach { assertEquals(tag, 1, it.lineCount) }
        }
        val name = compose.textLayoutsWithin(TimerTags.start(0)).first { it.layoutInput.text.text == "Bring the water to the boil" }
        assertTrue("the name has room for a word a line", name.lineCount <= 4)
    }
}
