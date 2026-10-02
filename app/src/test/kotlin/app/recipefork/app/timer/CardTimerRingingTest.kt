package app.recipefork.app.timer

import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.editor.PATIENCE_MS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration.Companion.minutes

/** A finished timer ringing is stopped on the card too: Stop beside it silences it, and it stays finished. */
@RunWith(AndroidJUnit4::class)
class CardTimerRingingTest : CardTimersKit() {
    private fun endedAndRinging() {
        show(soup)
        start(0)
        awaitHeld { it.size == 1 }
        pass(10.minutes)
        awaitStatus(0, "Finished")
        compose.runOnIdle { timers.ringing.ring(held().single()) }
    }

    @Test
    fun stopOnTheCardSilencesTheRingingTimer() {
        endedAndRinging()
        compose.waitUntil(PATIENCE_MS) { shown(TimerTags.stop(0)) }
        compose.onNodeWithTag(TimerTags.stop(0)).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { !shown(TimerTags.stop(0)) }
        assertFalse(timers.ringing.isRinging)
        assertFalse(timers.sound.ringing)
        // It stays finished, and can still be cleared.
        awaitStatus(0, "Finished")
        assertTrue(shown(TimerTags.cancel(0)))
        assertEquals(1, held().size)
    }

    @Test
    fun aFinishedTimerNotRingingOffersNoStop() {
        show(soup)
        start(0)
        awaitHeld { it.size == 1 }
        pass(10.minutes)
        awaitStatus(0, "Finished")
        assertFalse(shown(TimerTags.stop(0)))
        assertTrue(shown(TimerTags.cancel(0)))
    }
}
