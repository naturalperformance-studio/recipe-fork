package app.recipefork.app.timer

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.BookName
import app.recipefork.core.recipe.RecipeId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * The running-timer store: timers held by the moment they end, on disk, so they run by the clock across the card,
 * the application and the process (FLOW-016 (10)); several at once (FLOW-016 (1)); cancelled for good (FLOW-013 (7)).
 */
@RunWith(AndroidJUnit4::class)
class RunningTimersTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val clock = TestTimerClock()
    private val announcements = RecordedAnnouncements()
    private val opened = mutableListOf<TimerDatabase>()
    private val soup = RecipeId("soup")
    private val bread = RecipeId("bread")

    /** The store over the timers' file on disk, as a new process would open it. */
    private fun open(): RunningTimers {
        val database = Room.databaseBuilder(context, TimerDatabase::class.java, TimerDatabase.FILE_NAME)
            .allowMainThreadQueries()
            .build()
        opened += database
        return RunningTimers(database.timers(), clock, announcements)
    }

    @After
    fun close() {
        opened.forEach { it.close() }
        context.deleteDatabase(TimerDatabase.FILE_NAME)
    }

    @Test
    fun aStartedTimerIsHeldByTheMomentItEnds() = runBlocking {
        val timers = open()
        val key = TimerKey(BookName.Pool, soup, 2)
        val started = timers.start(key, "Timer", "Soup", 25.minutes)
        assertEquals(clock.now().plusSeconds(25 * 60), started.endsAt)
        assertEquals(started, timers.get(key))
        assertEquals(listOf(started), announcements.started)
    }

    // FLOW-016 (10): the time remaining is read from the clock, not counted.
    @Test
    fun theTimeRemainingIsWhatTheClockSays() = runBlocking {
        val timers = open()
        val timer = timers.start(TimerKey(BookName.Pool, soup, 0), "Timer", "Soup", 10.minutes)
        clock.advance(3.minutes + 20.seconds)
        assertEquals(6.minutes + 40.seconds, timer.remaining(clock.now()))
        assertFalse(timer.hasEnded(clock.now()))
        clock.advance(6.minutes + 40.seconds)
        assertEquals(0.milliseconds, timer.remaining(clock.now()))
        assertTrue(timer.hasEnded(clock.now()))
    }

    // FLOW-016 (10): the process ends and a new one finds the timer still running by the clock.
    @Test
    fun aRunningTimerOutlivesTheProcess() = runBlocking {
        val key = TimerKey(BookName.Pool, soup, 1)
        open().start(key, "Timer", "Soup", 2.hours)
        opened.forEach { it.close() }
        opened.clear()

        clock.advance(45.minutes)
        val afterwards = open().get(key)!!
        assertEquals(1.hours + 15.minutes, afterwards.remaining(clock.now()))
    }

    // FLOW-016 (1), FLOW-013 (6): timers of one recipe and of different recipes and books are held side by side.
    @Test
    fun timersOfSeveralStepsRecipesAndBooksRunAtOnce() = runBlocking {
        val timers = open()
        timers.start(TimerKey(BookName.Pool, soup, 0), "Timer", "Soup", 5.minutes)
        timers.start(TimerKey(BookName.Pool, soup, 3), "Timer", "Soup", 40.minutes)
        timers.start(TimerKey(BookName.Pool, bread, 0), "Timer", "Soup", 1.hours)
        timers.start(TimerKey(BookName.OfAccount("a1"), soup, 0), "Timer", "Soup", 7.minutes)

        assertEquals(setOf(0, 3), timers.ofRecipe(BookName.Pool, soup).first().keys)
        assertEquals(setOf(0), timers.ofRecipe(BookName.Pool, bread).first().keys)
        assertEquals(7.minutes, timers.ofRecipe(BookName.OfAccount("a1"), soup).first().getValue(0).duration)
        assertEquals(4, timers.all.first().size)
    }

    // FLOW-013 (7): a cancelled timer is gone, and whatever would announce it is told so.
    @Test
    fun aCancelledTimerIsGoneAndNeverAnnounced() = runBlocking {
        val timers = open()
        val key = TimerKey(BookName.Pool, soup, 0)
        val other = TimerKey(BookName.Pool, soup, 1)
        timers.start(key, "Timer", "Soup", 5.minutes)
        timers.start(other, "Timer", "Soup", 5.minutes)
        timers.cancel(key)
        assertNull(timers.get(key))
        assertEquals(listOf(key), announcements.cancelled)
        assertEquals(setOf(1), timers.ofRecipe(BookName.Pool, soup).first().keys)
    }

    // FLOW-021 (2): starting it again replaces what it had counted.
    @Test
    fun startingAgainRunsFromTheFullDuration() = runBlocking {
        val timers = open()
        val key = TimerKey(BookName.Pool, soup, 0)
        timers.start(key, "Timer", "Soup", 10.minutes)
        clock.advance(4.minutes)
        timers.start(key, "Timer", "Soup", 10.minutes)
        assertEquals(10.minutes, timers.get(key)!!.remaining(clock.now()))
        assertEquals(1, timers.all.first().size)
    }

    @Test
    fun aCountdownReadsMinutesAndSecondsWithHoursFromAnHourUp() {
        assertEquals("25:00", remainingText(25.minutes))
        assertEquals("0:59", remainingText(59.seconds))
        assertEquals("0:01", remainingText(1.milliseconds))
        assertEquals("0:00", remainingText(0.milliseconds))
        assertEquals("1:00:00", remainingText(1.hours))
        assertEquals("99:59:00", remainingText(99.hours + 59.minutes))
    }

    @Test
    fun keysNameTheBookByItsFile() {
        assertEquals(TimerKey("book-pool.db", "soup", 4), TimerKey(BookName.Pool, soup, 4))
    }
}
