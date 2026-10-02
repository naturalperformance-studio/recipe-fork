package app.recipefork.app

import android.app.NotificationManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.recipefork.app.book.BookName
import app.recipefork.app.timer.AndroidRingingSound
import app.recipefork.app.timer.RunningTimer
import app.recipefork.app.timer.TimerChannels
import app.recipefork.app.timer.TimerIntents
import app.recipefork.app.timer.TimerKey
import app.recipefork.core.recipe.RecipeId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.FileInputStream
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * FLOW-013 (1, 2) and FLOW-016 (2) on a real device: a timer that ends with the application removed from the recent
 * applications or left, the screen locked and the device idle, announces itself on time with a notification naming
 * the recipe and the timer, and rings like an alarm until it is stopped; two timers announce separately while the other
 * keeps running.
 *
 * The test runs in the application's own process, so the process itself cannot be ended: its task is removed, its
 * screen is gone, and the device is put to sleep and forced idle, which is what a closed application meets.
 */
@RunWith(AndroidJUnit4::class)
class TimerAnnouncedWithTheAppClosedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val application = instrumentation.targetContext.applicationContext as RecipeForkApplication
    private val timers = application.container.timers
    private val manager = application.getSystemService(NotificationManager::class.java)
    private val audio = application.getSystemService(AudioManager::class.java)
    private val soup = RecipeId("device-test-soup")

    private fun shell(command: String) {
        instrumentation.uiAutomation.executeShellCommand(command).use { FileInputStream(it.fileDescriptor).readBytes() }
    }

    private fun clear() = runBlocking {
        timers.all.first().forEach { timers.cancel(it.key) }
        manager.cancelAll()
    }

    @Before
    fun setUp() {
        shell("input keyevent KEYCODE_WAKEUP")
        shell("wm dismiss-keyguard")
        clear()
    }

    @After
    fun tearDown() {
        shell("dumpsys deviceidle unforce")
        shell("dumpsys battery reset")
        shell("input keyevent KEYCODE_WAKEUP")
        clear()
    }

    private fun ended(): List<StatusBarNotification> =
        manager.activeNotifications.filter { it.notification.channelId == TimerChannels.ENDED }

    private fun StatusBarNotification.title() = notification.extras.getCharSequence(NotificationCompat.EXTRA_TITLE)?.toString()
    private fun StatusBarNotification.text() = notification.extras.getCharSequence(NotificationCompat.EXTRA_TEXT)?.toString()

    private fun awaitUntil(within: Duration, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + within.inWholeMilliseconds
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "not met within $within" }
            Thread.sleep(200)
        }
    }

    private fun start(position: Int, name: String, duration: Duration): RunningTimer =
        runBlocking { timers.start(TimerKey(BookName.Pool, soup, position), name, "Soup", duration) }

    /** Locks the screen and lets the device go idle, as on a counter while the cook is away. */
    private fun lockAndIdle() {
        shell("input keyevent KEYCODE_SLEEP")
        shell("dumpsys battery unplug")
        shell("dumpsys deviceidle force-idle")
    }

    /** The timer's notification, posted at its end and not before; announced within a few seconds of it. */
    private fun assertAnnouncedOnTime(timer: RunningTimer, notification: StatusBarNotification) {
        assertEquals("Soup", notification.title())
        assertEquals(timer.name, notification.text())
        assertEquals(TimerIntents.tagOf(timer.key), notification.tag)
        val late = notification.postTime - timer.endsAt.toEpochMilli()
        assertTrue("announced ${late} ms after its end", late in 0..LATENESS_MS)
    }

    // FLOW-013 (1, 2): removed from the recent applications, the screen locked, the device idle — it announces itself.
    @Test
    fun aTimerEndingWithTheApplicationRemovedAndTheScreenLockedAnnouncesItself() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        val timer = start(2, "Beans", 15.seconds)
        scenario.onActivity { it.finishAndRemoveTask() }
        lockAndIdle()
        assertTrue(ended().isEmpty())
        awaitUntil(60.seconds) { ended().isNotEmpty() }
        assertAnnouncedOnTime(timer, ended().single())
        assertRinging(setOf(timer))
        scenario.close()
    }

    // An ended timer rings like an alarm with the application closed — the alarm sound on the alarm stream, on and
    // on — until the reader stops it from its notification, which stays, silent.
    @Test
    fun anEndedTimerRingsWithTheApplicationClosedUntilItIsStopped() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        val timer = start(2, "Beans", 8.seconds)
        scenario.onActivity { it.finishAndRemoveTask() }
        lockAndIdle()
        awaitUntil(60.seconds) { ended().isNotEmpty() }
        assertRinging(setOf(timer))
        // Still ringing a while later: not a single beep.
        Thread.sleep(10_000)
        assertRinging(setOf(timer))
        ended().single().notification.actions.single().actionIntent.send()
        awaitUntil(10.seconds) { !ringing.isRinging }
        try {
            awaitUntil(10.seconds) { !alarmPlaying() }
        } catch (e: IllegalStateException) {
            throw AssertionError("still playing: ${audio.activePlaybackConfigurations}", e)
        }
        val left = ended().single()
        assertTrue(left.notification.actions.isNullOrEmpty())
        scenario.close()
    }

    // FLOW-016 (2): two timers ending announce separately, and the other keeps running meanwhile.
    @Test
    fun twoTimersAnnounceSeparatelyAndTheOtherKeepsRunning() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        val first = start(0, "Water", 8.seconds)
        val second = start(2, "Beans", 30.seconds)
        shell("input keyevent KEYCODE_HOME")
        lockAndIdle()
        awaitUntil(60.seconds) { ended().isNotEmpty() }
        assertAnnouncedOnTime(first, ended().single())
        val other = runBlocking { timers.get(second.key) }!!
        assertFalse(other.announced)
        assertTrue(System.currentTimeMillis() < other.endsAt.toEpochMilli())
        awaitUntil(60.seconds) { ended().size == 2 }
        assertAnnouncedOnTime(second, ended().single { it.text() == "Beans" })
        assertEquals(setOf("Water", "Beans"), ended().map { it.text() }.toSet())
        scenario.close()
    }

    private val ringing get() = application.container.timerRinging

    /** Whether something of this application plays on the alarm stream now. */
    private fun alarmPlaying(): Boolean = audio.activePlaybackConfigurations.any {
        it.audioAttributes.usage == AudioAttributes.USAGE_ALARM
    }

    /** [timers] ring: the phone rings, a sound playing on the alarm stream. */
    private fun assertRinging(timers: Set<RunningTimer>) {
        assertEquals(timers.map { it.key }.toSet(), ringing.ringing.value)
        awaitUntil(5.seconds) { (ringing.sound as AndroidRingingSound).isPlaying }
        awaitUntil(5.seconds) { alarmPlaying() }
    }

    private companion object {
        /** How late a notification may come after its timer's end: the clock is read each second. */
        const val LATENESS_MS = 5_000L
    }
}
