package app.recipefork.app.timer

import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant

/** Where a timer's end is said: the device's notifications, or what a test records. */
interface TimerNotices {
    /** Says that [timer] has ended, apart from any other timer's notice (FLOW-016 (2)); returns whether it is shown. */
    fun announce(timer: RunningTimer): Boolean

    /** What was said for [timer] stays, but no longer rings nor offers to stop ringing. */
    fun quiet(timer: RunningTimer) = Unit

    /** Takes back whatever was said for [key]. */
    fun withdraw(key: TimerKey)
}

/**
 * Announces timers as they end (FLOW-013): each once, by what it was started as, never one cancelled or started again
 * meanwhile, and rings it like an alarm until it is stopped — where its notice is shown, or else where the application
 * is open, since the reader would otherwise miss it. Announcing and withdrawing take turns, so a timer cancelled as it
 * ends is not left announced nor ringing.
 */
class TimerAnnouncer(
    private val notices: TimerNotices,
    val ringing: TimerRinging? = null,
    private val isOpen: () -> Boolean = { false },
) {
    private val lock = Mutex()

    /** Announces every timer of [store] that has ended by [now] and is not yet announced. */
    suspend fun announceEnded(store: RunningTimers, now: Instant = store.clock.now()) {
        store.unannounced().filter { it.hasEnded(now) }.forEach { announce(store, it) }
    }

    private suspend fun announce(store: RunningTimers, timer: RunningTimer) = lock.withLock {
        // Only the timer still held as it was read: one cancelled or restarted meanwhile is not announced.
        if (!store.markAnnounced(timer)) return@withLock
        val shown = notices.announce(timer)
        if (shown || isOpen()) ringing?.ring(timer)
    }

    /** Takes back what was said for [key], and its ringing: the timer was cancelled or started again (FLOW-013 (7)). */
    suspend fun withdraw(key: TimerKey) = lock.withLock {
        ringing?.forget(key)
        notices.withdraw(key)
    }

    /**
     * Follows [store] by its clock, announcing each timer as it ends and letting each ringing one fall silent once it
     * has rung its while, until no timer awaits its announcement and none rings. [onChange] is told the timers still
     * running whenever they change.
     */
    suspend fun watch(store: RunningTimers, onChange: (List<RunningTimer>) -> Unit = {}) {
        val rings = ringing?.ringing ?: flowOf(emptySet())
        combine(store.all, store.clock.ticks, rings) { held, now, _ -> held to now }
            .transformWhile { (held, now) ->
                val waiting = held.filter { !it.announced }
                if (waiting.any { it.hasEnded(now) }) announceEnded(store, now)
                ringing?.expire(now)
                emit(waiting.filter { !it.hasEnded(now) })
                waiting.isNotEmpty() || ringing?.isRinging == true
            }
            .distinctUntilChanged()
            .collect(onChange)
    }
}
