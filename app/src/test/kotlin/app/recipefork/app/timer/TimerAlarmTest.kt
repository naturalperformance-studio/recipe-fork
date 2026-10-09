package app.recipefork.app.timer

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
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
import java.io.IOException
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
    private val audio = shadowOf(application.getSystemService(AudioManager::class.java))
    private val key = TimerKey(BookName.Pool, RecipeId("soup"), 2)
    private val timer = RunningTimer(key, "Beans", "Soup", 20.minutes, Instant.parse("2026-10-02T12:20:00Z"))
    private val notices = AndroidTimerNotices(application)

    /** The second timer of the same step (DOM-024). */
    private val second = key.copy(index = 1)
    private val secondTimer = RunningTimer(second, "Beans", "Soup", 5.minutes, Instant.parse("2026-10-02T12:05:00Z"), number = 2)

    @After
    fun close() {
        ringing.forget(key)
        ringing.forget(second)
        runBlocking {
            container.timers.cancel(key)
            container.timers.cancel(second)
        }
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

    private val alarmTone = Uri.parse("content://media/internal/audio/media/7")
    private val notificationTone = Uri.parse("content://media/internal/audio/media/9")

    /** The device's sound, prepared at once on the calling thread. */
    private fun sound() = AndroidRingingSound(application) { it.run() }

    private fun source(uri: Uri) = DataSource.toDataSource(application, uri)

    private fun sounds(alarm: Uri?, notification: Uri?) {
        RingtoneManager.setActualDefaultRingtoneUri(application, RingtoneManager.TYPE_ALARM, alarm)
        RingtoneManager.setActualDefaultRingtoneUri(application, RingtoneManager.TYPE_NOTIFICATION, notification)
        ShadowMediaPlayer.setMediaInfoProvider { ShadowMediaPlayer.MediaInfo(3_000, 0) }
    }

    // The alarm's sound, looping, on the alarm stream, with the audio focus an alarm takes; a vibration that repeats;
    // all of it given up once stopped.
    @Test
    fun theAlarmSoundLoopsOnTheAlarmStreamWithARepeatingVibration() {
        sounds(alarmTone, notificationTone)
        val sound = sound()
        sound.start()
        val player = sound.player()
        assertNotNull(player)
        assertTrue(player!!.isLooping)
        assertTrue(player.isPlaying)
        assertEquals(AudioAttributes.USAGE_ALARM, shadowOf(player).audioAttributes.usage)
        assertEquals(source(alarmTone), shadowOf(player).dataSource)
        val focus = audio.lastAudioFocusRequest.audioFocusRequest
        assertEquals(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT, focus.focusGain)
        assertEquals(AudioAttributes.USAGE_ALARM, focus.audioAttributes.usage)
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
        assertEquals(focus, audio.lastAbandonedAudioFocusRequest)
    }

    // A device without an alarm sound rings with its default notification sound.
    @Test
    fun withoutAnAlarmSoundTheNotificationSoundRings() {
        sounds(null, notificationTone)
        val sound = sound()
        sound.start()
        assertEquals(source(notificationTone), shadowOf(sound.player()!!).dataSource)
        assertEquals(AudioAttributes.USAGE_ALARM, shadowOf(sound.player()!!).audioAttributes.usage)
        sound.stop()
    }

    // An alarm sound that cannot be played — a deleted tone — gives way to the notification sound.
    @Test
    fun anAlarmSoundThatCannotPlayGivesWayToTheNotificationSound() {
        sounds(alarmTone, notificationTone)
        ShadowMediaPlayer.addException(source(alarmTone), IOException("deleted"))
        val sound = sound()
        sound.start()
        assertEquals(source(notificationTone), shadowOf(sound.player()!!).dataSource)
        sound.stop()
    }

    // Neither of the device's sounds playable, the application's own tone rings, on the alarm stream.
    @Test
    fun neitherSoundPlayableTheApplicationsOwnToneRings() {
        sounds(alarmTone, notificationTone)
        ShadowMediaPlayer.addException(source(alarmTone), IOException("deleted"))
        ShadowMediaPlayer.addException(source(notificationTone), IOException("deleted"))
        val sound = sound()
        sound.start()
        assertEquals(source(TimerSound.ownTone(application)), shadowOf(sound.player()!!).dataSource)
        assertTrue(sound.player()!!.isLooping)
        assertEquals(AudioAttributes.USAGE_ALARM, shadowOf(sound.player()!!).audioAttributes.usage)
        sound.stop()
    }

    // A device with no sound set at all rings with the application's own tone.
    @Test
    fun noSoundSetTheApplicationsOwnToneRings() {
        sounds(null, null)
        val sound = sound()
        sound.start()
        assertEquals(source(TimerSound.ownTone(application)), shadowOf(sound.player()!!).dataSource)
        sound.stop()
    }

    // The application's own tone is a resource of the application.
    @Test
    fun theOwnToneIsBundled() {
        application.contentResolver.openInputStream(TimerSound.ownTone(application))!!.use { assertTrue(it.read() >= 0) }
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

    /** Both timers of the step ended, each announced and ringing. */
    private fun bothAnnounced() {
        announced()
        notices.announce(secondTimer)
        ringing.ring(secondTimer)
        assertEquals(setOf(key, second), ringing.ringing.value)
    }

    private fun notificationOf(timer: TimerKey) = shadowOf(manager).allNotifications.single {
        TimerIntents.keyOf(shadowOf(it.contentIntent).savedIntent.data) == timer
    }

    // DOM-024: two timers of one step ring each with a notification of its own; Stop on one stops it alone.
    @Test
    fun theStopOfOneTimerOfAStepLeavesTheOtherRinging() {
        bothAnnounced()
        assertEquals(2, shadowOf(manager).allNotifications.count { it.channelId == TimerChannels.ENDED })
        notificationOf(second).actions.single().actionIntent.send()
        await { second !in ringing.ringing.value }
        assertEquals(setOf(key), ringing.ringing.value)
        assertTrue(notificationOf(second).actions.isNullOrEmpty())
        assertEquals(1, notificationOf(key).actions.size)
    }

    // DOM-024: dismissing one timer's notification stops its ringing alone.
    @Test
    fun dismissingOneTimersNotificationLeavesTheOtherRinging() {
        bothAnnounced()
        notificationOf(key).deleteIntent.send()
        await { key !in ringing.ringing.value }
        assertEquals(setOf(second), ringing.ringing.value)
    }

    // FLOW-013 (4), DOM-024: tapping one timer's notification opens the card at its step and stops its ringing alone.
    @Test
    fun tappingOneTimersNotificationStopsThatTimerAlone() {
        bothAnnounced()
        val intent = shadowOf(notificationOf(second).contentIntent).savedIntent
        val activity = Robolectric.buildActivity(MainActivity::class.java, intent).setup()
        try {
            assertEquals(setOf(key), ringing.ringing.value)
        } finally {
            activity.pause().stop().destroy()
        }
    }

    /** The alarm set at the end of the timer of [timer], if any. */
    private fun backstop(timer: TimerKey) = alarms.scheduledAlarms.singleOrNull {
        val sent = shadowOf(it.operation).savedIntent
        sent.component?.className == TimerEndReceiver::class.java.name && TimerIntents.keyOf(sent.data) == timer
    }

    // DOM-024: each timer of a step has its own alarm at its end; cancelling one takes back its alarm alone.
    @Test
    fun eachTimerOfAStepHasItsOwnAlarm() {
        runBlocking {
            container.timers.start(key, "Beans", "Soup", 20.minutes)
            container.timers.start(second, "Beans", "Soup", 5.minutes, number = 2)
        }
        val first = backstop(key)
        val other = backstop(second)
        assertNotNull(first)
        assertNotNull(other)
        assertFalse(first!!.operation == other!!.operation)
        assertTrue(other.triggerAtTime < first.triggerAtTime)
        runBlocking { container.timers.cancel(second) }
        assertNull(backstop(second))
        assertNotNull(backstop(key))
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

    private val alarms = shadowOf(application.getSystemService(AlarmManager::class.java))

    /** The alarm set at [key]'s ringing cap, if any. */
    private fun expiry() = alarms.scheduledAlarms.singleOrNull {
        shadowOf(it.operation).savedIntent.action == TimerRingReceiver.EXPIRED &&
            TimerIntents.keyOf(shadowOf(it.operation).savedIntent.data) == key
    }

    /** A timer ended and announced by the alarm at its end, the service gone: as after the process was ended. */
    private fun endedByTheBackstop() {
        shadowOf(application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        runBlocking { container.timers.start(key, "Beans", "Soup", 1.milliseconds) }
        Thread.sleep(5)
        application.sendBroadcast(Intent(application, TimerEndReceiver::class.java))
        await { ringing.isRinging }
    }

    // The service gone at the timer's end: the alarm announces it and it rings all the same, its notification shown;
    // its two minutes are kept without the service, by an alarm at the cap, which silences it and quiets the notice.
    @Test
    fun withoutTheServiceTheCapStillHolds() {
        val before = System.currentTimeMillis()
        endedByTheBackstop()
        assertNotNull("notes: ${shadowOf(manager).allNotifications.map { it.channelId }}", shownNotification())
        val alarm = expiry()
        assertNotNull("alarms: ${alarms.scheduledAlarms.map { shadowOf(it.operation).savedIntent }}", alarm)
        assertEquals(AlarmManager.RTC_WAKEUP, alarm!!.type)
        assertTrue(alarm.isAllowWhileIdle)
        val cap = TimerRinging.CAP.inWholeMilliseconds
        assertTrue(alarm.triggerAtTime in before + cap..System.currentTimeMillis() + cap)
        alarm.operation!!.send()
        await { !ringing.isRinging }
        assertTrue(shownNotification()!!.actions.isNullOrEmpty())
    }

    // Stopped before its cap, its alarm at the cap is taken back.
    @Test
    fun aStoppedRingTakesItsCapAlarmBack() {
        endedByTheBackstop()
        assertNotNull(expiry())
        ringing.stop(key)
        assertNull(expiry())
    }

    // The process ended since it rang: Stop on the notification left behind still quiets it.
    @Test
    fun stopOnANotificationLeftBehindQuietsIt() {
        endedByTheBackstop()
        // The process is gone and comes back: no ringing held, the notification still offering to stop.
        ringing.forget(key)
        notices.announce(runBlocking { container.timers.get(key) }!!)
        assertEquals(1, shownNotification()!!.actions.size)
        shownNotification()!!.actions.single().actionIntent.send()
        await { shownNotification()!!.actions.isNullOrEmpty() }
    }

    // The process ended since it rang: the alarm at the cap still quiets the notification left behind.
    @Test
    fun theCapOnANotificationLeftBehindQuietsIt() {
        endedByTheBackstop()
        val alarm = expiry()!!
        ringing.forget(key)
        notices.announce(runBlocking { container.timers.get(key) }!!)
        alarm.operation!!.send()
        await { shownNotification()!!.actions.isNullOrEmpty() }
    }
}
