package app.recipefork.app.book

import android.content.Context
import androidx.compose.ui.test.IdlingResource
import androidx.room.Room
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * A book database held in memory for a screen test, whose work the Compose test rule waits for.
 *
 * Room runs its queries, its transactions and the refresh of its observed queries on its executors. Both are
 * wrapped here so that every task handed to them is counted until it has finished; [idling] is idle only
 * when none is queued or running. Registered with the Compose rule, `waitForIdle` and `waitUntil` then wait
 * for the database as they wait for the main looper: a query's result is posted to the main looper before its
 * task ends, and a task that starts another hands it over before it ends, so there is no moment at which the
 * rule sees both idle while a result is still on its way.
 */
class TestBook(context: Context) {
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

    val database: BookDatabase = Room.inMemoryDatabaseBuilder(context, BookDatabase::class.java)
        .setQueryExecutor(counted)
        .setTransactionExecutor(counted)
        .build()

    val idling: IdlingResource = object : IdlingResource {
        override val isIdleNow: Boolean get() = running.get() == 0
        override fun getDiagnosticMessageIfBusy() = "${running.get()} database tasks queued or running"
    }

    fun close() {
        database.close()
        threads.shutdown()
    }
}
