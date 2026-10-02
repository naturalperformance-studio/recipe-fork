package app.recipefork.app.timer

import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant

/** Where a timer's end is said: the device's notifications, or what a test records. */
interface TimerNotices {
    /** Says that [timer] has ended, apart from any other timer's notice (FLOW-016 (2)). */
    fun announce(timer: RunningTimer)

    /** Takes back whatever was said for [key]. */
    fun withdraw(key: TimerKey)
}

/**
 * Announces timers as they end (FLOW-013): each once, by what it was started as, never one cancelled or started again
 * meanwhile. Announcing and withdrawing take turns, so a timer cancelled as it ends is not left announced.
 */
class TimerAnnouncer(private val notices: TimerNotices) {
    private val lock = Mutex()

    /** Announces every timer of [store] that has ended by [now] and is not yet announced. */
    suspend fun announceEnded(store: RunningTimers, now: Instant = store.clock.now()) {
        store.unannounced().filter { it.hasEnded(now) }.forEach { announce(store, it) }
    }

    private suspend fun announce(store: RunningTimers, timer: RunningTimer) = lock.withLock {
        // Only the timer still held as it was read: one cancelled or restarted meanwhile is not announced.
        if (store.markAnnounced(timer)) notices.announce(timer)
    }

    /** Takes back what was said for [key]: the timer was cancelled or started again (FLOW-013 (7)). */
    suspend fun withdraw(key: TimerKey) = lock.withLock { notices.withdraw(key) }

    /**
     * Follows [store] by its clock, announcing each timer as it ends, until no timer awaits its announcement.
     * [onChange] is told the timers still running whenever they change.
     */
    suspend fun watch(store: RunningTimers, onChange: (List<RunningTimer>) -> Unit = {}) {
        combine(store.all, store.clock.ticks) { held, now -> held to now }
            .transformWhile { (held, now) ->
                val waiting = held.filter { !it.announced }
                if (waiting.any { it.hasEnded(now) }) announceEnded(store, now)
                emit(waiting.filter { !it.hasEnded(now) })
                waiting.isNotEmpty()
            }
            .distinctUntilChanged()
            .collect(onChange)
    }
}
