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
 * Which timer: the one at [index] among the timers of a step of a recipe of a book (DOM-024); a step with one timer
 * holds it at index 0. The book is named as its file is, which does not change while the book is on the device.
 */
data class TimerKey(val book: String, val recipeId: String, val stepPosition: Int, val index: Int = 0) {
    constructor(book: BookName, recipeId: RecipeId, stepPosition: Int, index: Int = 0) :
        this(book.fileName, recipeId.value, stepPosition, index)
}

/**
 * A timer started and not cancelled: running until [endsAt], and ended from then on. It carries what it was started
 * as — the [name] it announces itself by (given or derived, FLOW-016), the title of its [recipe] and its [duration] —
 * so that what announces it never depends on whatever step sits at its position later. [announced] is whether its
 * end has been announced. [source] is what a derived name was read from, apart from the scale (`TimerName.source`):
 * null for a given name, and for a timer started before it was held. [number] is what told it apart from the other
 * timers of its step as it started (`TimerName.number`): its place among them, from 1, for an unnamed timer of a step
 * with several; null otherwise.
 */
data class RunningTimer(
    val key: TimerKey,
    val name: String,
    val recipe: String,
    val duration: Duration,
    val endsAt: Instant,
    val announced: Boolean = false,
    val source: String? = null,
    val number: Int? = null,
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

    /** The timers held for [recipeId] in [book], each by its key, as they change. */
    fun ofRecipe(book: BookName, recipeId: RecipeId): Flow<Map<TimerKey, RunningTimer>> =
        dao.ofRecipe(book.fileName, recipeId.value).map { rows -> rows.map { it.toTimer() }.associateBy { it.key } }

    /** The timer of [key], or null where none is held. */
    suspend fun get(key: TimerKey): RunningTimer? = dao.get(key.book, key.recipeId, key.stepPosition, key.index)?.toTimer()

    /**
     * Starts the timer of [key] for its full [duration] from now, named [name], read from [source], told apart from
     * the other timers of its step by [number], in the recipe titled [recipe]. A timer already held under [key],
     * running or ended, is replaced: what it had counted is gone (FLOW-021 (2)); the other timers of its step are
     * left as they are. Asking first is the caller's.
     */
    suspend fun start(
        key: TimerKey,
        name: String,
        recipe: String,
        duration: Duration,
        source: String? = null,
        number: Int? = null,
    ): RunningTimer {
        val timer = RunningTimer(key, name, recipe, duration, clock.now().plus(duration.toJavaDuration()), source = source, number = number)
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
        dao.markAnnounced(timer.key.book, timer.key.recipeId, timer.key.stepPosition, timer.key.index, timer.endsAt.toEpochMilli()) > 0

    /** Cancels the timer of [key]: it never ends and never announces itself (FLOW-013 (7)). */
    suspend fun cancel(key: TimerKey) {
        dao.delete(key.book, key.recipeId, key.stepPosition, key.index)
        announcements.cancelled(key)
    }

    /**
     * Cancels every timer held for [recipeId] in [book], running or ended: the recipe is gone, so none of them may
     * announce itself or ring any more, and whatever was said for them is taken back.
     */
    suspend fun cancelRecipe(book: BookName, recipeId: RecipeId) {
        dao.keysOfRecipe(book.fileName, recipeId.value).forEach {
            cancel(TimerKey(book.fileName, recipeId.value, it.stepPosition, it.index))
        }
    }
}

@Entity(tableName = "running_timer", primaryKeys = ["book", "recipe_id", "step_position", "timer_index"])
data class RunningTimerEntity(
    @ColumnInfo(name = "book") val book: String,
    @ColumnInfo(name = "recipe_id") val recipeId: String,
    @ColumnInfo(name = "step_position") val stepPosition: Int,
    @ColumnInfo(name = "timer_index") val index: Int,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "recipe_title") val recipeTitle: String,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
    @ColumnInfo(name = "ends_at_ms") val endsAtMs: Long,
    @ColumnInfo(name = "announced") val announced: Boolean,
    @ColumnInfo(name = "step_source") val stepSource: String? = null,
    @ColumnInfo(name = "timer_number") val number: Int? = null,
) {
    fun toTimer() = RunningTimer(
        TimerKey(book, recipeId, stepPosition, index),
        name,
        recipeTitle,
        durationMs.milliseconds,
        Instant.ofEpochMilli(endsAtMs),
        announced,
        stepSource,
        number,
    )

    companion object {
        fun of(timer: RunningTimer) = RunningTimerEntity(
            timer.key.book,
            timer.key.recipeId,
            timer.key.stepPosition,
            timer.key.index,
            timer.name,
            timer.recipe,
            timer.duration.inWholeMilliseconds,
            timer.endsAt.toEpochMilli(),
            timer.announced,
            timer.source,
            timer.number,
        )
    }
}

/** Where a timer stands in its recipe: its step and its place among the step's timers. */
data class TimerPlace(
    @ColumnInfo(name = "step_position") val stepPosition: Int,
    @ColumnInfo(name = "timer_index") val index: Int,
)

@Dao
interface RunningTimerDao {
    @Query("select * from running_timer order by ends_at_ms, book, recipe_id, step_position, timer_index")
    fun all(): Flow<List<RunningTimerEntity>>

    @Query("select * from running_timer where book = :book and recipe_id = :recipeId order by step_position, timer_index")
    fun ofRecipe(book: String, recipeId: String): Flow<List<RunningTimerEntity>>

    @Query(
        "select step_position, timer_index from running_timer where book = :book and recipe_id = :recipeId " +
            "order by step_position, timer_index",
    )
    suspend fun keysOfRecipe(book: String, recipeId: String): List<TimerPlace>

    @Query(
        "select * from running_timer " +
            "where book = :book and recipe_id = :recipeId and step_position = :step and timer_index = :index",
    )
    suspend fun get(book: String, recipeId: String, step: Int, index: Int): RunningTimerEntity?

    @Query("select * from running_timer where announced = 0 order by ends_at_ms")
    suspend fun unannounced(): List<RunningTimerEntity>

    @Query(
        "update running_timer set announced = 1 " +
            "where book = :book and recipe_id = :recipeId and step_position = :step and timer_index = :index " +
            "and ends_at_ms = :endsAt and announced = 0",
    )
    suspend fun markAnnounced(book: String, recipeId: String, step: Int, index: Int, endsAt: Long): Int

    @Upsert
    suspend fun put(timer: RunningTimerEntity)

    @Query(
        "delete from running_timer " +
            "where book = :book and recipe_id = :recipeId and step_position = :step and timer_index = :index",
    )
    suspend fun delete(book: String, recipeId: String, step: Int, index: Int)
}

/**
 * The running timers' own small database, beside the device database and the books: timers belong to the device and
 * span its books, and none of them is ever sent anywhere.
 */
@Database(entities = [RunningTimerEntity::class], version = 3, exportSchema = false)
abstract class TimerDatabase : RoomDatabase() {
    abstract fun timers(): RunningTimerDao

    companion object {
        const val FILE_NAME = "timers.db"

        fun open(context: Context): TimerDatabase =
            Room.databaseBuilder(context, TimerDatabase::class.java, FILE_NAME).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()

        /** Version 2 holds what a derived name was read from; a timer running across the update holds none. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("alter table running_timer add column step_source TEXT")
            }
        }

        /**
         * Version 3 holds each timer of a step apart, by its place among them (DOM-024), and what told an unnamed one
         * apart as it started. A timer running across the update was its step's only one: it is kept as the timer at
         * index 0, with no number. The key changes with it, so the table is built anew.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `running_timer_new` (`book` TEXT NOT NULL, `recipe_id` TEXT NOT NULL, " +
                        "`step_position` INTEGER NOT NULL, `timer_index` INTEGER NOT NULL, `name` TEXT NOT NULL, " +
                        "`recipe_title` TEXT NOT NULL, `duration_ms` INTEGER NOT NULL, `ends_at_ms` INTEGER NOT NULL, " +
                        "`announced` INTEGER NOT NULL, `step_source` TEXT, `timer_number` INTEGER, " +
                        "PRIMARY KEY(`book`, `recipe_id`, `step_position`, `timer_index`))",
                )
                db.execSQL(
                    "INSERT INTO running_timer_new (book, recipe_id, step_position, timer_index, name, recipe_title, " +
                        "duration_ms, ends_at_ms, announced, step_source, timer_number) " +
                        "SELECT book, recipe_id, step_position, 0, name, recipe_title, duration_ms, ends_at_ms, " +
                        "announced, step_source, NULL FROM running_timer",
                )
                db.execSQL("DROP TABLE running_timer")
                db.execSQL("ALTER TABLE running_timer_new RENAME TO running_timer")
            }
        }
    }
}
