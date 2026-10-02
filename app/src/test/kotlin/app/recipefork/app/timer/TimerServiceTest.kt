package app.recipefork.app.timer

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Intent
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.RecipeForkApplication
import app.recipefork.app.book.BookName
import app.recipefork.core.recipe.RecipeId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

/**
 * How the device keeps a timer to announce it with the application closed (FLOW-013 (1, 2)): a started timer brings up
 * the foreground service and sets an alarm at its end; a cancelled one takes its alarm back; the service holds the
 * timers until the last is announced; the alarm announces what has ended; a restart takes held timers up again.
 */
@RunWith(AndroidJUnit4::class)
class TimerServiceTest {
    private val application = ApplicationProvider.getApplicationContext<RecipeForkApplication>()
    private val container = application.container
    private val alarms = shadowOf(application.getSystemService(AlarmManager::class.java))
    private val notifications = shadowOf(application.getSystemService(NotificationManager::class.java))
    private val key = TimerKey(BookName.Pool, RecipeId("soup"), 2)

    @After
    fun close() = runBlocking { container.timers.cancel(key) }

    /** Waits for [condition], letting the main thread run between looks. */
    private fun await(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 30_000
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "the condition was never met" }
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
    }

    private fun startedService(): Intent? = shadowOf(application).nextStartedService

    @Test
    fun aStartedTimerBringsUpTheServiceAndAnAlarmAtItsEnd() {
        val timer = runBlocking { container.timers.start(key, "Beans", "Soup", 20.minutes) }
        assertEquals(TimerService::class.java.name, startedService()?.component?.className)
        val alarm = alarms.scheduledAlarms.single()
        assertEquals(AlarmManager.RTC_WAKEUP, alarm.type)
        assertEquals(timer.endsAt.toEpochMilli(), alarm.triggerAtTime)
        assertTrue(alarm.isAllowWhileIdle)
    }

    // FLOW-013 (7): a cancelled timer's alarm is taken back.
    @Test
    fun aCancelledTimerTakesItsAlarmBack() {
        runBlocking { container.timers.start(key, "Beans", "Soup", 20.minutes) }
        runBlocking { container.timers.cancel(key) }
        assertTrue(alarms.scheduledAlarms.isEmpty())
    }

    @Test
    fun theServiceHoldsTheTimersInTheForegroundUntilNoneIsLeft() {
        runBlocking { container.timers.start(key, "Beans", "Soup", 20.minutes) }
        val service = Robolectric.buildService(TimerService::class.java).create().startCommand(0, 1).get()
        val shadow = shadowOf(service)
        assertEquals(TimerChannels.RUNNING, shadow.lastForegroundNotification.channelId)
        // The note names the next timer to end, counting down to it.
        val note = { notifications.getNotification(AndroidTimerNotices.RUNNING_ID) }
        await { note()?.extras?.getCharSequence(NotificationCompat.EXTRA_TEXT) == "Beans" }
        assertEquals("Soup", note()!!.extras.getCharSequence(NotificationCompat.EXTRA_TITLE))
        assertEquals(false, shadow.isStoppedBySelf)
        runBlocking { container.timers.cancel(key) }
        await { shadow.isStoppedBySelf }
        assertTrue(shadow.isForegroundStopped)
    }

    // FLOW-013 (1): the alarm at a timer's end announces it, should the service be gone.
    @Test
    fun theAlarmAnnouncesWhatHasEnded() {
        runBlocking { container.timers.start(key, "Beans", "Soup", 1.milliseconds) }
        Thread.sleep(5)
        application.sendBroadcast(Intent(application, TimerEndReceiver::class.java))
        await { notifications.allNotifications.any { it.channelId == TimerChannels.ENDED } }
        val announced = notifications.allNotifications.single { it.channelId == TimerChannels.ENDED }
        assertEquals("Soup", announced.extras.getCharSequence(NotificationCompat.EXTRA_TITLE))
        assertEquals("Beans", announced.extras.getCharSequence(NotificationCompat.EXTRA_TEXT))
        assertTrue(runBlocking { container.timers.get(key) }!!.announced)
    }

    // After a restart of the device or an update, held timers are taken up again.
    @Test
    fun aRestartTakesHeldTimersUpAgain() {
        runBlocking { container.timers.start(key, "Beans", "Soup", 20.minutes) }
        // The device restarts: the service and the alarms are gone.
        while (startedService() != null) Unit
        TimerBackstop.clear(application, key)
        assertTrue(alarms.scheduledAlarms.isEmpty())
        for (action in listOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)) {
            application.sendBroadcast(Intent(action).setClass(application, TimerRestartReceiver::class.java))
            var started: Intent? = null
            await { started = startedService(); started != null }
            assertEquals(TimerService::class.java.name, started?.component?.className)
            assertTrue(alarms.scheduledAlarms.isNotEmpty())
        }
    }
}
