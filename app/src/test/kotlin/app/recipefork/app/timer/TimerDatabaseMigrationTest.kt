package app.recipefork.app.timer

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.BookName
import app.recipefork.core.recipe.RecipeId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import kotlin.time.Duration.Companion.minutes

/**
 * The timers' database moves from version 1 to 2 with what a derived name was read from: a timer running on the
 * device across the update is kept, holding no source, and timers started since hold theirs.
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
