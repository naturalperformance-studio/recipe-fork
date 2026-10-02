package app.recipefork.app.timer

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.BookName
import app.recipefork.core.recipe.RecipeId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Announcing timers as they end (FLOW-013, FLOW-016 (2–5)): each apart from the others, once, by what it was started
 * as, and never once cancelled or started again (FLOW-013 (7)).
 */
@RunWith(AndroidJUnit4::class)
class TimerAnnouncerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val clock = TestTimerClock()
    private val notices = RecordedNotices()
    private val announcer = TimerAnnouncer(notices)
    private val database = Room.inMemoryDatabaseBuilder(context, TimerDatabase::class.java).build()
    private val store = RunningTimers(database.timers(), clock, object : TimerAnnouncements {
        override suspend fun started(timer: RunningTimer) = announcer.withdraw(timer.key)
        override suspend fun cancelled(key: TimerKey) = announcer.withdraw(key)
    })
    private val soup = RecipeId("soup")
    private val water = TimerKey(BookName.Pool, soup, 0)
    private val beans = TimerKey(BookName.Pool, soup, 2)

    @After
    fun close() = database.close()

    // FLOW-016 (2): two timers ending announce separately, and the other keeps running meanwhile.
    @Test
    fun twoTimersAnnounceSeparatelyAndTheOtherKeepsRunning() = runBlocking {
        store.start(water, "Bring the water to the boil", "Soup", 10.minutes)
        store.start(beans, "Beans", "Soup", 30.minutes)
        clock.advance(10.minutes)
        announcer.announceEnded(store)
        assertEquals(listOf(water), notices.announced.map { it.key })
        val stillRunning = store.get(beans)!!
        assertFalse(stillRunning.hasEnded(clock.now()))
        assertFalse(stillRunning.announced)

        clock.advance(20.minutes)
        announcer.announceEnded(store)
        assertEquals(listOf(water, beans), notices.announced.map { it.key })
    }

    // FLOW-013 (3), FLOW-016 (3–5): the announcement carries the recipe's title and the name it was started with.
    @Test
    fun anAnnouncementCarriesTheTitleAndTheNameTheTimerWasStartedWith() = runBlocking {
        store.start(beans, "Beans", "Soup", 30.minutes)
        clock.advance(30.minutes)
        announcer.announceEnded(store)
        val announced = notices.announced.single()
        assertEquals("Beans", announced.name)
        assertEquals("Soup", announced.recipe)
        assertEquals(30.minutes, announced.duration)
    }

    @Test
    fun aTimerIsAnnouncedOnce() = runBlocking {
        store.start(water, "Water", "Soup", 10.minutes)
        clock.advance(11.minutes)
        announcer.announceEnded(store)
        announcer.announceEnded(store)
        assertEquals(1, notices.announced.size)
        assertTrue(store.get(water)!!.announced)
    }

    @Test
    fun aRunningTimerIsNotAnnounced() = runBlocking {
        store.start(water, "Water", "Soup", 10.minutes)
        clock.advance(10.minutes - 1.seconds)
        announcer.announceEnded(store)
        assertTrue(notices.announced.isEmpty())
    }

    // FLOW-013 (7): a cancelled timer never announces itself, and what was said of it is taken back.
    @Test
    fun aCancelledTimerIsNeverAnnounced() = runBlocking {
        store.start(water, "Water", "Soup", 10.minutes)
        store.cancel(water)
        clock.advance(1.minutes + 10.minutes)
        announcer.announceEnded(store)
        assertTrue(notices.announced.isEmpty())
        assertEquals(water, notices.withdrawn.last())
    }

    // A timer started again ends at its new end, and the notice of the one it replaced is withdrawn.
    @Test
    fun aRestartedTimerIsAnnouncedAtItsNewEnd() = runBlocking {
        store.start(water, "Water", "Soup", 10.minutes)
        clock.advance(10.minutes)
        announcer.announceEnded(store)
        store.start(water, "Water", "Soup", 10.minutes)
        assertEquals(water, notices.withdrawn.last())
        assertFalse(store.get(water)!!.announced)
        clock.advance(5.minutes)
        announcer.announceEnded(store)
        assertEquals(1, notices.announced.size)
        clock.advance(5.minutes)
        announcer.announceEnded(store)
        assertEquals(2, notices.announced.size)
    }

    // What the service runs: it follows the clock, announces each timer as it ends, and returns once none awaits.
    @Test
    fun watchingAnnouncesEachAsItEndsAndReturnsWhenNoneAwaits() = runBlocking {
        store.start(water, "Water", "Soup", 10.minutes)
        store.start(beans, "Beans", "Soup", 30.minutes)
        val running = java.util.concurrent.CopyOnWriteArrayList<List<TimerKey>>()
        val watching = launch(Dispatchers.Default) { announcer.watch(store) { list -> running += list.map { it.key } } }
        awaitUntil { running.lastOrNull() == listOf(water, beans) }
        clock.advance(10.minutes)
        awaitUntil { notices.announced.map { it.key } == listOf(water) }
        awaitUntil { running.last() == listOf(beans) }
        assertTrue(watching.isActive)
        clock.advance(20.minutes)
        withTimeout(5_000) { watching.join() }
        assertEquals(listOf(water, beans), notices.announced.map { it.key })
    }

    private suspend fun awaitUntil(condition: () -> Boolean) = withTimeout(5_000) {
        while (!synchronized(notices) { condition() }) delay(10)
    }
}
