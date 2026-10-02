package app.recipefork.app.media

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * How many photographs are being processed and attached now, on this device. A screen test waits for none to be, as
 * it waits for the database; nothing else reads it.
 */
object PhotoWork {
    private val running = AtomicInteger(0)

    val isIdle: Boolean get() = running.get() == 0

    /** Counts one photograph's work from now until [done] is called, once. */
    fun begin(): () -> Unit {
        running.incrementAndGet()
        val finished = AtomicBoolean(false)
        return { if (finished.compareAndSet(false, true)) running.decrementAndGet() }
    }
}
