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
 * An ended timer rings like an alarm until it is stopped (FLOW-013): from its end, on and on, until the reader stops
 * it, the timer is cancelled or started again, or two minutes have passed; while any of two ended timers rings.
 */
@RunWith(AndroidJUnit4::class)
class TimerRingingTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val clock = TestTimerClock()
    private val notices = RecordedNotices()
    private val sound = RecordedSound()
    private val ringing = TimerRinging(clock, sound, notices)
    private var open = false
    private val announcer = TimerAnnouncer(notices, ringing) { open }
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

    /** Starts both timers, the water ending first, and lets both end and be announced. */
    private suspend fun bothEnded() {
        store.start(water, "Water", "Soup", 10.minutes)
        store.start(beans, "Beans", "Soup", 12.minutes)
        clock.advance(12.minutes)
        announcer.announceEnded(store)
    }

    @Test
    fun anEndedTimerRingsFromItsEndOnAndOn() = runBlocking {
        store.start(water, "Water", "Soup", 10.minutes)
        clock.advance(10.minutes - 1.seconds)
        announcer.announceEnded(store)
        assertFalse(sound.ringing)
        clock.advance(1.seconds)
        announcer.announceEnded(store)
        assertTrue(sound.ringing)
        assertEquals(setOf(water), ringing.ringing.value)
        // Not a single beep: it rings on until stopped.
        clock.advance(1.minutes + 59.seconds)
        ringing.expire()
        assertTrue(sound.ringing)
        assertEquals(1, sound.starts)
        assertEquals(0, sound.stops)
    }

    // The Stop action, or Stop on the card: the ringing stops, and the notification stays, silent.
    @Test
    fun stoppingItSilencesItAndLeavesTheNotificationQuiet() = runBlocking {
        store.start(water, "Water", "Soup", 10.minutes)
        clock.advance(10.minutes)
        announcer.announceEnded(store)
        val withdrawn = notices.withdrawn.size
        assertTrue(ringing.stop(water))
        assertFalse(sound.ringing)
        assertEquals(listOf(water), notices.quieted)
        assertEquals(withdrawn, notices.withdrawn.size)
        assertFalse(ringing.stop(water))
    }

    // The notification tapped or dismissed: the ringing goes with it, and nothing is posted again.
    @Test
    fun theNotificationGoneSilencesIt() = runBlocking {
        store.start(water, "Water", "Soup", 10.minutes)
        clock.advance(10.minutes)
        announcer.announceEnded(store)
        ringing.forget(water)
        assertFalse(sound.ringing)
        assertTrue(notices.quieted.isEmpty())
    }

    // Clear on the card cancels the timer: its ringing stops, and its notification is withdrawn.
    @Test
    fun clearingTheTimerSilencesIt() = runBlocking {
        store.start(water, "Water", "Soup", 10.minutes)
        clock.advance(10.minutes)
        announcer.announceEnded(store)
        store.cancel(water)
        assertFalse(sound.ringing)
        assertEquals(water, notices.withdrawn.last())
    }

    // Started again on the card: the ended one's ringing stops; the new one rings at its own end.
    @Test
    fun restartingTheTimerSilencesIt() = runBlocking {
        store.start(water, "Water", "Soup", 10.minutes)
        clock.advance(10.minutes)
        announcer.announceEnded(store)
        store.start(water, "Water", "Soup", 10.minutes)
        assertFalse(sound.ringing)
        clock.advance(10.minutes)
        announcer.announceEnded(store)
        assertTrue(sound.ringing)
    }

    // Unstopped, it falls silent by itself after two minutes, leaving its notification in place, silent.
    @Test
    fun itFallsSilentByItselfAfterTwoMinutes() = runBlocking {
        store.start(water, "Water", "Soup", 10.minutes)
        clock.advance(10.minutes)
        announcer.announceEnded(store)
        val withdrawn = notices.withdrawn.size
        clock.advance(2.minutes - 1.seconds)
        ringing.expire()
        assertTrue(sound.ringing)
        assertTrue(notices.quieted.isEmpty())
        clock.advance(1.seconds)
        ringing.expire()
        assertFalse(sound.ringing)
        assertEquals(listOf(water), notices.quieted)
        assertEquals(withdrawn, notices.withdrawn.size)
    }

    // FLOW-016 (2): two ended timers ring while either is unstopped; stopping one leaves the other ringing.
    @Test
    fun twoEndedTimersRingUntilBothAreStopped() = runBlocking {
        bothEnded()
        assertEquals(setOf(water, beans), ringing.ringing.value)
        ringing.stop(water)
        assertTrue(sound.ringing)
        assertEquals(setOf(beans), ringing.ringing.value)
        ringing.forget(beans)
        assertFalse(sound.ringing)
        assertEquals(1, sound.starts)
        assertEquals(1, sound.stops)
    }

    // Each falls silent two minutes after its own end: the later one rings on after the first falls silent.
    @Test
    fun twoEndedTimersFallSilentEachInItsTurn() = runBlocking {
        store.start(water, "Water", "Soup", 10.minutes)
        store.start(beans, "Beans", "Soup", 11.minutes)
        clock.advance(10.minutes)
        announcer.announceEnded(store)
        clock.advance(1.minutes)
        announcer.announceEnded(store)
        clock.advance(1.minutes)
        ringing.expire()
        assertEquals(setOf(beans), ringing.ringing.value)
        assertTrue(sound.ringing)
        clock.advance(1.minutes)
        ringing.expire()
        assertFalse(sound.ringing)
    }

    // Notifications not permitted: with the application closed nothing rings, as there is nothing to stop it from.
    @Test
    fun notShownAndClosedItDoesNotRing() = runBlocking {
        notices.shown = false
        store.start(water, "Water", "Soup", 10.minutes)
        clock.advance(10.minutes)
        announcer.announceEnded(store)
        assertFalse(sound.ringing)
        assertTrue(store.get(water)!!.announced)
    }

    // Notifications not permitted and the application open: it rings in the application until stopped.
    @Test
    fun notShownButOpenItRingsInTheApplication() = runBlocking {
        notices.shown = false
        open = true
        store.start(water, "Water", "Soup", 10.minutes)
        clock.advance(10.minutes)
        announcer.announceEnded(store)
        assertTrue(sound.ringing)
        ringing.stop(water)
        assertFalse(sound.ringing)
    }

    // What the service runs: it keeps watching while a timer rings, and lets it fall silent by the clock.
    @Test
    fun watchingHoldsTheRingingAndLetsItFallSilentAfterTwoMinutes() = runBlocking {
        store.start(water, "Water", "Soup", 10.minutes)
        val watching = launch(Dispatchers.Default) { announcer.watch(store) }
        awaitUntil { store.get(water) != null }
        clock.advance(10.minutes)
        awaitUntil { sound.ringing }
        clock.advance(1.minutes)
        delay(100)
        assertTrue(watching.isActive)
        assertTrue(sound.ringing)
        clock.advance(1.minutes)
        withTimeout(5_000) { watching.join() }
        assertFalse(sound.ringing)
        assertEquals(listOf(water), notices.quieted)
    }

    // The ringing stopped by the reader, the watch ends with it.
    @Test
    fun watchingEndsOnceTheRingingIsStopped() = runBlocking {
        store.start(water, "Water", "Soup", 10.minutes)
        val watching = launch(Dispatchers.Default) { announcer.watch(store) }
        clock.advance(10.minutes)
        awaitUntil { sound.ringing }
        assertTrue(watching.isActive)
        ringing.stop(water)
        withTimeout(5_000) { watching.join() }
    }

    private suspend fun awaitUntil(condition: suspend () -> Boolean) = withTimeout(5_000) {
        while (!condition()) delay(10)
    }

    // The cap is kept apart from the watch too: each ring has its timeout, which silences it when due.
    @Test
    fun eachRingHasItsTimeoutAtItsCap() {
        val timeouts = RecordedTimeouts()
        val ringing = TimerRinging(clock, sound, notices, timeouts = timeouts)
        val timer = RunningTimer(water, "Water", "Soup", 10.minutes, clock.now())
        ringing.ring(timer)
        assertEquals(clock.now().plusSeconds(120), timeouts.set[water]!!.first)
        timeouts.set.getValue(water).second()
        assertFalse(sound.ringing)
        assertEquals(listOf(water), notices.quieted)
        // Stopped or forgotten sooner, the timeout is cleared.
        ringing.ring(timer)
        ringing.stop(water)
        assertEquals(null, timeouts.set[water])
        ringing.ring(timer)
        ringing.forget(water)
        assertEquals(null, timeouts.set[water])
    }

    // A timeout of a ring since replaced — the timer ended again — does not silence the new ring.
    @Test
    fun aStaleTimeoutLeavesANewerRing() {
        val timeouts = RecordedTimeouts()
        val ringing = TimerRinging(clock, sound, notices, timeouts = timeouts)
        val timer = RunningTimer(water, "Water", "Soup", 10.minutes, clock.now())
        ringing.ring(timer)
        val stale = timeouts.set.getValue(water).second
        ringing.forget(water)
        ringing.ring(timer)
        stale()
        assertTrue(sound.ringing)
    }
}

/** Remembers each timeout set and not cleared, with what it calls when due. */
class RecordedTimeouts : RingTimeouts {
    val set = mutableMapOf<TimerKey, Pair<java.time.Instant, () -> Unit>>()

    override fun set(key: TimerKey, at: java.time.Instant, due: () -> Unit) {
        set[key] = at to due
    }

    override fun clear(key: TimerKey) {
        set.remove(key)
    }
}
