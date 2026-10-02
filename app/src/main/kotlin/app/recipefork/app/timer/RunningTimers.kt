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

/** A timer started and not cancelled: running until [endsAt], and ended from then on. */
data class RunningTimer(val key: TimerKey, val duration: Duration, val endsAt: Instant) {
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
     * Starts the timer of [key] for its full [duration] from now. A timer already held under [key], running or ended,
     * is replaced: what it had counted is gone (FLOW-021 (2)). Asking first is the caller's.
     */
    suspend fun start(key: TimerKey, duration: Duration): RunningTimer {
        val timer = RunningTimer(key, duration, clock.now().plus(duration.toJavaDuration()))
        dao.put(RunningTimerEntity.of(timer))
        announcements.started(timer)
        return timer
    }

    /** Cancels the timer of [key]: it never ends and never announces itself (FLOW-013 (7)). */
    suspend fun cancel(key: TimerKey) {
        dao.delete(key.book, key.recipeId, key.stepPosition)
        announcements.cancelled(key)
    }
}

@Entity(tableName = "running_timer", primaryKeys = ["book", "recipe_id", "step_position"])
data class RunningTimerEntity(
    @ColumnInfo(name = "book") val book: String,
    @ColumnInfo(name = "recipe_id") val recipeId: String,
    @ColumnInfo(name = "step_position") val stepPosition: Int,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
    @ColumnInfo(name = "ends_at_ms") val endsAtMs: Long,
) {
    fun toTimer() = RunningTimer(
        TimerKey(book, recipeId, stepPosition),
        durationMs.milliseconds,
        Instant.ofEpochMilli(endsAtMs),
    )

    companion object {
        fun of(timer: RunningTimer) = RunningTimerEntity(
            timer.key.book,
            timer.key.recipeId,
            timer.key.stepPosition,
            timer.duration.inWholeMilliseconds,
            timer.endsAt.toEpochMilli(),
        )
    }
}

@Dao
interface RunningTimerDao {
    @Query("select * from running_timer order by ends_at_ms, book, recipe_id, step_position")
    fun all(): Flow<List<RunningTimerEntity>>

    @Query("select * from running_timer where book = :book and recipe_id = :recipeId order by step_position")
    fun ofRecipe(book: String, recipeId: String): Flow<List<RunningTimerEntity>>

    @Query("select * from running_timer where book = :book and recipe_id = :recipeId and step_position = :step")
    suspend fun get(book: String, recipeId: String, step: Int): RunningTimerEntity?

    @Upsert
    suspend fun put(timer: RunningTimerEntity)

    @Query("delete from running_timer where book = :book and recipe_id = :recipeId and step_position = :step")
    suspend fun delete(book: String, recipeId: String, step: Int)
}

/**
 * The running timers' own small database, beside the device database and the books: timers belong to the device and
 * span its books, and none of them is ever sent anywhere.
 */
@Database(entities = [RunningTimerEntity::class], version = 1, exportSchema = false)
abstract class TimerDatabase : RoomDatabase() {
    abstract fun timers(): RunningTimerDao

    companion object {
        const val FILE_NAME = "timers.db"

        fun open(context: Context): TimerDatabase =
            Room.databaseBuilder(context, TimerDatabase::class.java, FILE_NAME).build()
    }
}
