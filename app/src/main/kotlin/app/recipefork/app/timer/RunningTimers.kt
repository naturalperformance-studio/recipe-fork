package app.recipefork.app.timer

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import app.recipefork.app.book.BookName
import app.recipefork.core.recipe.RecipeId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.toJavaDuration

/**
 * Which timer: the one on a step of a recipe of a book. A step carries at most one timer (DOM-013), so the step names
 * it. The book is named as its file is, which does not change while the book is on the device.
 */
data class TimerKey(val book: String, val recipeId: String, val stepPosition: Int) {
    constructor(book: BookName, recipeId: RecipeId, stepPosition: Int) : this(book.fileName, recipeId.value, stepPosition)
}

/**
 * A timer started and not cancelled: running until [endsAt], and ended from then on. It carries what it was started
 * as — the [name] it announces itself by (given or derived, FLOW-016), the title of its [recipe] and its [duration] —
 * so that what announces it never depends on whatever step sits at its position later. [announced] is whether its
 * end has been announced. [source] is what a derived name was read from, apart from the scale (`TimerName.source`):
 * null for a given name, and for a timer started before it was held.
 */
data class RunningTimer(
    val key: TimerKey,
    val name: String,
    val recipe: String,
    val duration: Duration,
    val endsAt: Instant,
    val announced: Boolean = false,
    val source: String? = null,
) {
    /** The time left at [now]; zero once the timer has ended. */
    fun remaining(now: Instant): Duration =
        java.time.Duration.between(now, endsAt).toMillis().coerceAtLeast(0).milliseconds

    fun hasEnded(now: Instant): Boolean = !now.isBefore(endsAt)
}

/**
 * What else must know when a timer is started or cancelled: the announcement of its end (FLOW-013), scheduled from
 * here. It is told after the store holds the change.
 */
interface TimerAnnouncements {
    /** [timer] now ends at its [RunningTimer.endsAt]; whatever was scheduled for its key before is replaced. */
    suspend fun started(timer: RunningTimer)

    /** The timer of [key] is gone: nothing may announce it any more (FLOW-013 (7)). */
    suspend fun cancelled(key: TimerKey)

    /** Announces nothing: what the device does until announcements exist. */
    object None : TimerAnnouncements {
        override suspend fun started(timer: RunningTimer) = Unit
        override suspend fun cancelled(key: TimerKey) = Unit
    }
}

/**
 * The device's running timers, of every book and every recipe at once (FLOW-016 (1)). Each is held by the moment it
 * ends, on disk, so it runs by the clock whatever becomes of the card or the process (FLOW-016 (10)). A timer that has
 * ended stays held, ended, until it is started again or cancelled, so that it can be shown as finished.
 */
class RunningTimers(
    private val dao: RunningTimerDao,
    val clock: TimerClock,
    private val announcements: TimerAnnouncements = TimerAnnouncements.None,
) {
    /** Every timer held, as it changes. */
    val all: Flow<List<RunningTimer>> = dao.all().map { rows -> rows.map { it.toTimer() } }

    /** The timers held for [recipeId] in [book], by step position, as they change. */
    fun ofRecipe(book: BookName, recipeId: RecipeId): Flow<Map<Int, RunningTimer>> =
        dao.ofRecipe(book.fileName, recipeId.value).map { rows -> rows.associate { it.stepPosition to it.toTimer() } }

    /** The timer of [key], or null where none is held. */
    suspend fun get(key: TimerKey): RunningTimer? = dao.get(key.book, key.recipeId, key.stepPosition)?.toTimer()

    /**
     * Starts the timer of [key] for its full [duration] from now, named [name], read from [source], in the recipe
     * titled [recipe]. A timer
     * already held under [key], running or ended, is replaced: what it had counted is gone (FLOW-021 (2)). Asking
     * first is the caller's.
     */
    suspend fun start(key: TimerKey, name: String, recipe: String, duration: Duration, source: String? = null): RunningTimer {
        val timer = RunningTimer(key, name, recipe, duration, clock.now().plus(duration.toJavaDuration()), source = source)
        dao.put(RunningTimerEntity.of(timer))
        announcements.started(timer)
        return timer
    }

    /** The timers whose end is not yet announced, running or just ended. */
    suspend fun unannounced(): List<RunningTimer> = dao.unannounced().map { it.toTimer() }

    /**
     * Marks [timer] announced, if it is still the one held under its key — not cancelled meanwhile nor started again.
     * Returns whether it was.
     */
    suspend fun markAnnounced(timer: RunningTimer): Boolean =
        dao.markAnnounced(timer.key.book, timer.key.recipeId, timer.key.stepPosition, timer.endsAt.toEpochMilli()) > 0

    /** Cancels the timer of [key]: it never ends and never announces itself (FLOW-013 (7)). */
    suspend fun cancel(key: TimerKey) {
        dao.delete(key.book, key.recipeId, key.stepPosition)
        announcements.cancelled(key)
    }

    /**
     * Cancels every timer held for [recipeId] in [book], running or ended: the recipe is gone, so none of them may
     * announce itself or ring any more, and whatever was said for them is taken back.
     */
    suspend fun cancelRecipe(book: BookName, recipeId: RecipeId) {
        dao.keysOfRecipe(book.fileName, recipeId.value).forEach { cancel(TimerKey(book.fileName, recipeId.value, it)) }
    }
}

@Entity(tableName = "running_timer", primaryKeys = ["book", "recipe_id", "step_position"])
data class RunningTimerEntity(
    @ColumnInfo(name = "book") val book: String,
    @ColumnInfo(name = "recipe_id") val recipeId: String,
    @ColumnInfo(name = "step_position") val stepPosition: Int,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "recipe_title") val recipeTitle: String,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
    @ColumnInfo(name = "ends_at_ms") val endsAtMs: Long,
    @ColumnInfo(name = "announced") val announced: Boolean,
    @ColumnInfo(name = "step_source") val stepSource: String? = null,
) {
    fun toTimer() = RunningTimer(
        TimerKey(book, recipeId, stepPosition),
        name,
        recipeTitle,
        durationMs.milliseconds,
        Instant.ofEpochMilli(endsAtMs),
        announced,
        stepSource,
    )

    companion object {
        fun of(timer: RunningTimer) = RunningTimerEntity(
            timer.key.book,
            timer.key.recipeId,
            timer.key.stepPosition,
            timer.name,
            timer.recipe,
            timer.duration.inWholeMilliseconds,
            timer.endsAt.toEpochMilli(),
            timer.announced,
            timer.source,
        )
    }
}

@Dao
interface RunningTimerDao {
    @Query("select * from running_timer order by ends_at_ms, book, recipe_id, step_position")
    fun all(): Flow<List<RunningTimerEntity>>

    @Query("select * from running_timer where book = :book and recipe_id = :recipeId order by step_position")
    fun ofRecipe(book: String, recipeId: String): Flow<List<RunningTimerEntity>>

    @Query("select step_position from running_timer where book = :book and recipe_id = :recipeId order by step_position")
    suspend fun keysOfRecipe(book: String, recipeId: String): List<Int>

    @Query("select * from running_timer where book = :book and recipe_id = :recipeId and step_position = :step")
    suspend fun get(book: String, recipeId: String, step: Int): RunningTimerEntity?

    @Query("select * from running_timer where announced = 0 order by ends_at_ms")
    suspend fun unannounced(): List<RunningTimerEntity>

    @Query(
        "update running_timer set announced = 1 " +
            "where book = :book and recipe_id = :recipeId and step_position = :step and ends_at_ms = :endsAt " +
            "and announced = 0",
    )
    suspend fun markAnnounced(book: String, recipeId: String, step: Int, endsAt: Long): Int

    @Upsert
    suspend fun put(timer: RunningTimerEntity)

    @Query("delete from running_timer where book = :book and recipe_id = :recipeId and step_position = :step")
    suspend fun delete(book: String, recipeId: String, step: Int)
}

/**
 * The running timers' own small database, beside the device database and the books: timers belong to the device and
 * span its books, and none of them is ever sent anywhere.
 */
@Database(entities = [RunningTimerEntity::class], version = 2, exportSchema = false)
abstract class TimerDatabase : RoomDatabase() {
    abstract fun timers(): RunningTimerDao

    companion object {
        const val FILE_NAME = "timers.db"

        fun open(context: Context): TimerDatabase =
            Room.databaseBuilder(context, TimerDatabase::class.java, FILE_NAME).addMigrations(MIGRATION_1_2).build()

        /** Version 2 holds what a derived name was read from; a timer running across the update holds none. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("alter table running_timer add column step_source TEXT")
            }
        }
    }
}
