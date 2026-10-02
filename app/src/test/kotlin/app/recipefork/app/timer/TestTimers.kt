package app.recipefork.app.timer

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.IdlingResource
import androidx.room.Room
import app.recipefork.app.book.BookName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.Instant
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration
import kotlin.time.toJavaDuration

/** A clock that stands still until a test moves it; each move is a tick. */
class TestTimerClock(start: Instant = Instant.parse("2026-10-02T12:00:00Z")) : TimerClock {
    private val time = MutableStateFlow(start)

    override fun now(): Instant = time.value

    override val ticks: Flow<Instant> = time

    fun advance(by: Duration) {
        time.value = time.value.plus(by.toJavaDuration())
    }
}

/** Notification settings a test sets; the settings it leads to are the device's own. */
class TestNotificationAccess(
    private val context: Context,
    var state: NotificationState = NotificationState.PERMITTED,
    override var hasRequest: Boolean = true,
) : NotificationAccess {
    /** How many times the state was read. */
    var reads = 0
        private set

    override fun state(): NotificationState = state.also { reads++ }

    override fun settings(state: NotificationState): Intent = AndroidNotificationAccess(context).settings(state)
}

/** Remembers what was announced, quieted and withdrawn, in order; each announcement is [shown] or not. */
class RecordedNotices(var shown: Boolean = true) : TimerNotices {
    val announced = mutableListOf<RunningTimer>()
    val quieted = mutableListOf<TimerKey>()
    val withdrawn = mutableListOf<TimerKey>()

    override fun announce(timer: RunningTimer): Boolean {
        synchronized(this) { announced += timer }
        return shown
    }

    override fun quiet(timer: RunningTimer) {
        synchronized(this) { quieted += timer.key }
    }

    override fun withdraw(key: TimerKey) {
        synchronized(this) { withdrawn += key }
    }
}

/** Rings in memory: whether it rings now, and how many times it started and stopped. */
class RecordedSound : RingingSound {
    @Volatile var ringing = false
        private set
    @Volatile var starts = 0
        private set
    @Volatile var stops = 0
        private set

    override fun start() {
        ringing = true
        starts++
    }

    override fun stop() {
        ringing = false
        stops++
    }
}

/** Remembers what it was told, in order. */
class RecordedAnnouncements : TimerAnnouncements {
    val started = mutableListOf<RunningTimer>()
    val cancelled = mutableListOf<TimerKey>()

    override suspend fun started(timer: RunningTimer) {
        synchronized(this) { started += timer }
    }

    override suspend fun cancelled(key: TimerKey) {
        synchronized(this) { cancelled += key }
    }
}

/**
 * The running timers held in memory for a screen test, whose database work the Compose test rule waits for (as
 * `TestBook` does for a book), run by a [TestTimerClock].
 */
class TestTimers(context: Context, val clock: TestTimerClock = TestTimerClock()) {
    private val running = AtomicInteger(0)
    private val threads = Executors.newCachedThreadPool()

    private val counted = Executor { task ->
        running.incrementAndGet()
        try {
            threads.execute {
                try {
                    task.run()
                } finally {
                    running.decrementAndGet()
                }
            }
        } catch (e: RuntimeException) {
            running.decrementAndGet()
            throw e
        }
    }

    val database: TimerDatabase = Room.inMemoryDatabaseBuilder(context, TimerDatabase::class.java)
        .setQueryExecutor(counted)
        .setTransactionExecutor(counted)
        .build()

    val announcements = RecordedAnnouncements()
    val store = RunningTimers(database.timers(), clock, announcements)
    val access = TestNotificationAccess(context)

    /** Outlives the card, as the application's does; on the main thread, whose work the Compose test rule waits for. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val sound = RecordedSound()
    val ringing = TimerRinging(clock, sound)
    val card = CardTimers(store, scope, access, ringing) { BookName.Pool }

    val idling: IdlingResource = object : IdlingResource {
        override val isIdleNow: Boolean get() = running.get() == 0
        override fun getDiagnosticMessageIfBusy() = "${running.get()} timer database tasks queued or running"
    }

    fun close() {
        scope.cancel()
        database.close()
        threads.shutdown()
    }
}
