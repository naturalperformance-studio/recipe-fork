package app.recipefork.app.timer

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Instant

/**
 * The time timers run by: the wall clock on a device, one the tests move by hand. Timers are held by the moment they
 * end, so the clock is read, never counted: whatever happened meanwhile — the card left, the application closed,
 * the process ended — the time remaining is what the clock says it is (FLOW-016 (10)).
 */
interface TimerClock {
    /** The present moment. */
    fun now(): Instant

    /** The present moment as it moves, often enough for a countdown in whole seconds to show every second. */
    val ticks: Flow<Instant>
}

/** The device's own clock, ticking on each whole second. */
object SystemTimerClock : TimerClock {
    override fun now(): Instant = Instant.now()

    override val ticks: Flow<Instant> = flow {
        while (true) {
            val now = now()
            emit(now)
            delay(1_000L - now.toEpochMilli() % 1_000L)
        }
    }
}
