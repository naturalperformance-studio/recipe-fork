package app.recipefork.app.timer

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.BookName
import app.recipefork.core.recipe.RecipeId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import kotlin.time.Duration.Companion.minutes

/**
 * The timers' database moves from version 1 to 2 with what a derived name was read from: a timer running on the
 * device across the update is kept, holding no source, and timers started since hold theirs. From version 2 to 3 it
 * holds each timer of a step apart (DOM-024): a timer running across the update, its step's only one then, is kept as
 * the step's timer at index 0, and another timer of its step can start beside it.
 */
@RunWith(AndroidJUnit4::class)
class TimerDatabaseMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val clock = TestTimerClock()
    private val key = TimerKey(BookName.Pool, RecipeId("soup"), 0)
    private var opened: TimerDatabase? = null

    @After
    fun close() {
        opened?.close()
        context.deleteDatabase(TimerDatabase.FILE_NAME)
    }

    /** A version 1 file, as the device held it before, with one timer running until [endsAt]. */
    private fun versionOne(endsAt: Instant) {
        context.deleteDatabase(TimerDatabase.FILE_NAME)
        context.openOrCreateDatabase(TimerDatabase.FILE_NAME, Context.MODE_PRIVATE, null).use { db ->
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `running_timer` (`book` TEXT NOT NULL, `recipe_id` TEXT NOT NULL, " +
                    "`step_position` INTEGER NOT NULL, `name` TEXT NOT NULL, `recipe_title` TEXT NOT NULL, " +
                    "`duration_ms` INTEGER NOT NULL, `ends_at_ms` INTEGER NOT NULL, `announced` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`book`, `recipe_id`, `step_position`))",
            )
            db.execSQL(
                "INSERT INTO running_timer VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any>(key.book, key.recipeId, key.stepPosition, "Bring the water to the boil", "Soup", 600_000L, endsAt.toEpochMilli(), 0),
            )
            db.version = 1
        }
    }

    /** A version 2 file, as the device held it before, with [rows] of timers: (step, ends at, announced, source). */
    private fun versionTwo(vararg rows: Array<Any?>) {
        context.deleteDatabase(TimerDatabase.FILE_NAME)
        context.openOrCreateDatabase(TimerDatabase.FILE_NAME, Context.MODE_PRIVATE, null).use { db ->
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `running_timer` (`book` TEXT NOT NULL, `recipe_id` TEXT NOT NULL, " +
                    "`step_position` INTEGER NOT NULL, `name` TEXT NOT NULL, `recipe_title` TEXT NOT NULL, " +
                    "`duration_ms` INTEGER NOT NULL, `ends_at_ms` INTEGER NOT NULL, `announced` INTEGER NOT NULL, " +
                    "`step_source` TEXT, PRIMARY KEY(`book`, `recipe_id`, `step_position`))",
            )
            rows.forEach { row ->
                db.execSQL("INSERT INTO running_timer VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", row)
            }
            db.version = 2
        }
    }

    @Test
    fun oneTimerTimersRunningAcrossTheUpdateAreKeptAsTheirStepsFirstTimer() = runBlocking {
        val endsAt = clock.now().plusSeconds(600)
        val ended = clock.now().minusSeconds(30)
        versionTwo(
            arrayOf(key.book, key.recipeId, 0, "Bring the water to the boil", "Soup", 600_000L, endsAt.toEpochMilli(), 0, "Bring the water to the boil"),
            arrayOf(key.book, key.recipeId, 2, "Beans", "Soup", 5_400_000L, ended.toEpochMilli(), 1, null),
        )
        val database = TimerDatabase.open(context).also { opened = it }
        val timers = RunningTimers(database.timers(), clock)
        val beans = TimerKey(BookName.Pool, RecipeId("soup"), 2, 0)
        assertEquals(
            RunningTimer(key, "Bring the water to the boil", "Soup", 10.minutes, endsAt, announced = false, source = "Bring the water to the boil"),
            timers.get(key),
        )
        assertEquals(RunningTimer(beans, "Beans", "Soup", 90.minutes, ended, announced = true), timers.get(beans))
        assertEquals(listOf(key), timers.unannounced().map { it.key })

        // Another timer of the same step starts beside the one kept, and each is cancelled on its own.
        val second = key.copy(index = 1)
        val started = timers.start(second, "Bring the water to the boil", "Soup", 3.minutes, "Bring the water to the boil", 2)
        assertEquals(started, timers.get(second))
        assertEquals(endsAt, timers.get(key)?.endsAt)
        timers.cancel(key)
        assertNull(timers.get(key))
        assertEquals(started, timers.get(second))
    }

    @Test
    fun aTimerRunningAcrossTheUpdateIsKeptWithNoSource() = runBlocking {
        val endsAt = clock.now().plusSeconds(600)
        versionOne(endsAt)
        val database = TimerDatabase.open(context).also { opened = it }
        val timers = RunningTimers(database.timers(), clock)
        assertEquals(
            RunningTimer(key, "Bring the water to the boil", "Soup", 10.minutes, endsAt, announced = false, source = null),
            timers.get(key),
        )
        val started = timers.start(key, "Add 400 g flour", "Bread", 5.minutes, "Add ￼ flour")
        assertEquals(started, timers.get(key))
        assertEquals("Add ￼ flour", timers.get(key)?.source)
    }
}
