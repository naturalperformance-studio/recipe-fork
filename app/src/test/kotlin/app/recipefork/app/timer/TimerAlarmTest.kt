package app.recipefork.app.timer

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Looper
import android.os.VibrationAttributes
import android.os.Vibrator
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.MainActivity
import app.recipefork.app.RecipeForkApplication
import app.recipefork.app.book.BookName
import app.recipefork.core.recipe.RecipeId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowMediaPlayer
import org.robolectric.shadows.util.DataSource
import java.time.Instant
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

/**
 * The device's side of an ended timer ringing like an alarm (FLOW-013): the alarm sound looping on the alarm stream
 * with a repeating vibration, and each way the reader stops it from the notification — its Stop action, dismissing it,
 * or tapping it — and that the service holds the ringing until it stops.
 */
@RunWith(AndroidJUnit4::class)
class TimerAlarmTest {
    private val application = ApplicationProvider.getApplicationContext<RecipeForkApplication>()
    private val container = application.container
    private val ringing = container.timerRinging
    private val manager = application.getSystemService(NotificationManager::class.java)
    private val vibrator = shadowOf(application.getSystemService(Vibrator::class.java))
    private val key = TimerKey(BookName.Pool, RecipeId("soup"), 2)
    private val timer = RunningTimer(key, "Beans", "Soup", 20.minutes, Instant.parse("2026-10-02T12:20:00Z"))
    private val notices = AndroidTimerNotices(application)

    @After
    fun close() {
        ringing.forget(key)
        runBlocking { container.timers.cancel(key) }
    }

    /** Waits for [condition], letting the main thread run between looks. */
    private fun await(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 30_000
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "the condition was never met" }
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
    }

    private fun announced() {
        notices.announce(timer)
        ringing.ring(timer)
    }

    private fun shownNotification() = shadowOf(manager).allNotifications.singleOrNull { it.channelId == TimerChannels.ENDED }

    // The alarm's sound, looping, on the alarm stream; a vibration that repeats; both gone once stopped.
    @Test
    fun theAlarmSoundLoopsOnTheAlarmStreamWithARepeatingVibration() {
        RingtoneManager.setActualDefaultRingtoneUri(application, RingtoneManager.TYPE_ALARM, Uri.parse("content://media/internal/audio/media/7"))
        ShadowMediaPlayer.setMediaInfoProvider { ShadowMediaPlayer.MediaInfo(3_000, 0) }
        val sound = AndroidRingingSound(application)
        sound.start()
        val player = sound.player()
        assertNotNull(player)
        assertTrue(player!!.isLooping)
        assertTrue(player.isPlaying)
        assertEquals(AudioAttributes.USAGE_ALARM, shadowOf(player).audioAttributes.usage)
        assertEquals(DataSource.toDataSource(application, Uri.parse("content://media/internal/audio/media/7")), shadowOf(player).dataSource)
        assertTrue(vibrator.isVibrating)
        assertEquals(0, vibrator.repeat)
        assertEquals(
            VibrationAttributes.USAGE_ALARM,
            (vibrator.vibrationAttributesFromLastVibration as VibrationAttributes).usage,
        )
        sound.stop()
        assertNull(sound.player())
        assertFalse(sound.isPlaying)
        assertFalse(vibrator.isVibrating)
    }

    // A device without an alarm sound rings with its default notification sound.
    @Test
    fun withoutAnAlarmSoundTheNotificationSoundRings() {
        RingtoneManager.setActualDefaultRingtoneUri(application, RingtoneManager.TYPE_ALARM, null)
        RingtoneManager.setActualDefaultRingtoneUri(application, RingtoneManager.TYPE_NOTIFICATION, Uri.parse("content://media/internal/audio/media/9"))
        ShadowMediaPlayer.setMediaInfoProvider { ShadowMediaPlayer.MediaInfo(3_000, 0) }
        val sound = AndroidRingingSound(application)
        sound.start()
        assertEquals(DataSource.toDataSource(application, Uri.parse("content://media/internal/audio/media/9")), shadowOf(sound.player()!!).dataSource)
        assertEquals(AudioAttributes.USAGE_ALARM, shadowOf(sound.player()!!).audioAttributes.usage)
        sound.stop()
    }

    // The notification's Stop action: the ringing stops, the notification stays, silent, with nothing to stop.
    @Test
    fun theStopActionStopsTheRingingAndLeavesTheNotification() {
        announced()
        assertTrue(ringing.isRinging)
        shownNotification()!!.actions.single().actionIntent.send()
        await { !ringing.isRinging }
        val left = shownNotification()
        assertNotNull(left)
        assertTrue(left!!.actions.isNullOrEmpty())
    }

    // Dismissing the notification stops the ringing.
    @Test
    fun dismissingTheNotificationStopsTheRinging() {
        announced()
        shownNotification()!!.deleteIntent.send()
        await { !ringing.isRinging }
    }

    // Tapping the notification opens the card at the step, and stops the ringing.
    @Test
    fun tappingTheNotificationStopsTheRinging() {
        announced()
        val intent = shadowOf(shownNotification()!!.contentIntent).savedIntent
        val activity = Robolectric.buildActivity(MainActivity::class.java, intent).setup()
        try {
            assertFalse(ringing.isRinging)
        } finally {
            activity.pause().stop().destroy()
        }
    }

    // Stopping another timer's ringing leaves this one's.
    @Test
    fun anotherTimersStopLeavesThisOneRinging() {
        announced()
        application.sendBroadcast(
            Intent(application, TimerRingReceiver::class.java)
                .setAction(TimerRingReceiver.STOP)
                .setData(TimerIntents.uriOf(key.copy(stepPosition = 0))),
        )
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(ringing.isRinging)
    }

    // The service stays in the foreground while an ended timer rings, and ends once it is stopped.
    @Test
    fun theServiceHoldsTheRingingUntilItIsStopped() {
        shadowOf(application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        runBlocking { container.timers.start(key, "Beans", "Soup", 1.milliseconds) }
        Thread.sleep(5)
        val service = Robolectric.buildService(TimerService::class.java).create().startCommand(0, 1).get()
        val shadow = shadowOf(service)
        await { ringing.isRinging }
        assertTrue(runBlocking { container.timers.get(key) }!!.announced)
        Thread.sleep(200)
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(shadow.isStoppedBySelf)
        assertFalse(shadow.isForegroundStopped)
        ringing.stop(key)
        await { shadow.isStoppedBySelf }
        assertTrue(shadow.isForegroundStopped)
    }
}
