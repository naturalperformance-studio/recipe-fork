package app.recipefork.app.timer

import android.app.Application
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.R
import app.recipefork.app.book.BookName
import app.recipefork.app.readVector
import app.recipefork.app.renderDrawable
import app.recipefork.core.recipe.RecipeId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import kotlin.time.Duration.Companion.minutes

/**
 * The timer notifications' small icon: the thick drawing of mark 2б, in one colour — white on transparent, as Android
 * requires of a small icon, which it tints itself.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TimerNotificationIconTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun theSmallIconIsOneColourWhiteOnTransparent() {
        val vector = readVector(context, R.drawable.ic_timer_notification)
        assertEquals(24f to 24f, vector.widthDp to vector.heightDp)
        assertTrue("no path", vector.paths.isNotEmpty())
        for (path in vector.paths) {
            assertEquals("fill", WHITE, path.fillColor)
            if (path.strokeWidth > 0) assertEquals("stroke", WHITE, path.strokeColor)
        }

        val icon = renderDrawable(context, R.drawable.ic_timer_notification, 96)
        var drawn = 0
        for (y in 0 until icon.height) for (x in 0 until icon.width) {
            val pixel = icon.getPixel(x, y)
            if (pixel ushr 24 == 0) continue
            drawn++
            assertEquals("pixel ($x, $y) is #%08X".format(pixel), 0xFFFFFF, pixel and 0xFFFFFF)
        }
        assertTrue("only $drawn pixels are drawn", drawn > 96 * 96 / 10)
    }

    @Test
    fun theTimerNotificationsShowIt() {
        val timer = RunningTimer(
            TimerKey(BookName.Pool, RecipeId("soup"), 0, 0),
            "Beans",
            "Soup",
            30.minutes,
            Instant.parse("2026-10-02T12:30:00Z"),
        )
        val running = AndroidTimerNotices.running(context, listOf(timer))
        assertEquals("running", R.drawable.ic_timer_notification, running.smallIcon.resId)

        AndroidTimerNotices(context).announce(timer)
        val ended = shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications.single()
        assertEquals("ended", R.drawable.ic_timer_notification, ended.smallIcon.resId)
    }

    private companion object {
        const val WHITE = 0xFFFFFFFF.toInt()
    }
}
