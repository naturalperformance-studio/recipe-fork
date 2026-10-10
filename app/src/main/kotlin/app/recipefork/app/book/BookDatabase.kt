package app.recipefork.app.book

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.TypeConverters
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import app.recipefork.app.data.db.Converters
import app.recipefork.app.dictionary.DictionaryEntry
import app.recipefork.app.dictionary.DictionarySnapshot
import app.recipefork.app.dictionary.EquipmentEntryEntity
import app.recipefork.app.recipe.EquipmentNames
import app.recipefork.core.recipe.SentStatus
import kotlinx.coroutines.flow.Flow

/** One recipe book's database: an account's, or the pool's (decisions/20260928-one-database-per-recipe-book.md). */
@Database(
    entities = [
        RecipeEntity::class, IngredientEntity::class, StepEntity::class, StepReferenceEntity::class,
        MediaEntity::class, RecipeTagEntity::class, FavouriteEntity::class, IngredientGroupEntity::class,
        StepTimerEntity::class, EquipmentEntity::class,
    ],
    version = 8,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class BookDatabase : RoomDatabase() {
    abstract fun recipes(): BookRecipeDao
    abstract fun favourites(): FavouriteDao

    companion object {
        /**
         * Opens the book [book]. An older book file is brought to this version; [equipment] is the equipment dictionary
         * its items written as text find their entries in, read only where a book of version 6 lists some — the one the
         * application ships, which the device's own copy is seeded from.
         */
        fun open(
            context: Context,
            book: BookName,
            equipment: () -> List<DictionaryEntry> = { shippedEquipment(context) },
        ): BookDatabase =
            Room.databaseBuilder(context, BookDatabase::class.java, book.fileName).addMigrations(*migrations(equipment)).build()

        /** The equipment dictionary the application ships (decisions/20260928-dictionaries-ship-with-the-application.md). */
        fun shippedEquipment(context: Context): List<DictionaryEntry> =
            DictionarySnapshot.read(context).equipment.map(EquipmentEntryEntity::of)

        /**
         * Every step from an older book file to this version; a book already on a phone keeps every recipe. [equipment]
         * is read by the step to version 7 alone, and only where there is an item to find an entry for.
         */
        fun migrations(equipment: () -> List<DictionaryEntry>): Array<Migration> = arrayOf(
            // 1 → 2: an ingredient row keeps a quantity the author typed that reads as no value (FLOW-024 (6)).
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("alter table ingredient add column entered_quantity text")
                }
            },
            // 2 → 3: a step's reference may read a part of its ingredient.
            object : Migration(2, 3) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("alter table step_reference add column share text")
                    db.execSQL("alter table step_reference add column share_unit text")
                }
            },
            // 3 → 4: an ingredient may carry a note (DOM-023), and the ingredient list may fall into groups (DOM-022).
            object : Migration(3, 4) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("alter table ingredient add column note text")
                    db.execSQL(
                        "create table if not exists `ingredient_group` (`recipe_id` TEXT NOT NULL, " +
                            "`position` INTEGER NOT NULL, `name` TEXT NOT NULL, `first_ingredient` INTEGER NOT NULL, " +
                            "PRIMARY KEY(`recipe_id`, `position`), FOREIGN KEY(`recipe_id`) REFERENCES `recipe`(`recipe_id`) " +
                            "ON UPDATE NO ACTION ON DELETE CASCADE )",
                    )
                }
            },
            // 4 → 5: a step carries up to five timers (DOM-024); its one timer moves from its row to the first of them.
            object : Migration(4, 5) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "create table if not exists `step_timer` (`recipe_id` TEXT NOT NULL, `step_position` INTEGER NOT NULL, " +
                            "`position` INTEGER NOT NULL, `minutes` INTEGER NOT NULL, `label` TEXT, " +
                            "PRIMARY KEY(`recipe_id`, `step_position`, `position`), FOREIGN KEY(`recipe_id`) " +
                            "REFERENCES `recipe`(`recipe_id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                    )
                    db.execSQL(
                        "insert into step_timer (recipe_id, step_position, position, minutes, label) " +
                            "select recipe_id, position, 0, timer_minutes, timer_label from step where timer_minutes is not null",
                    )
                    db.execSQL(
                        "create table `step_new` (`recipe_id` TEXT NOT NULL, `position` INTEGER NOT NULL, `text` TEXT NOT NULL, " +
                            "PRIMARY KEY(`recipe_id`, `position`), FOREIGN KEY(`recipe_id`) REFERENCES `recipe`(`recipe_id`) " +
                            "ON UPDATE NO ACTION ON DELETE CASCADE )",
                    )
                    db.execSQL("insert into step_new (recipe_id, position, text) select recipe_id, position, text from step")
                    db.execSQL("drop table step")
                    db.execSQL("alter table step_new rename to step")
                }
            },
            // 5 → 6: a recipe lists the equipment it is cooked with; every recipe already in the book lists none.
            object : Migration(5, 6) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "create table if not exists `recipe_equipment` (`recipe_id` TEXT NOT NULL, `position` INTEGER NOT NULL, " +
                            "`name` TEXT NOT NULL, PRIMARY KEY(`recipe_id`, `position`), FOREIGN KEY(`recipe_id`) " +
                            "REFERENCES `recipe`(`recipe_id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                    )
                }
            },
            EquipmentByEntry(equipment),
            // 7 → 8: a step may carry the author's note; every step already in the book has none.
            object : Migration(7, 8) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("alter table step add column note text")
                }
            },
        )
    }
}

/**
 * 6 → 7: an item of equipment is named as an ingredient row is — a dictionary entry and a qualifier, or free text. An
 * item whose whole text is an entry's name ([EquipmentNames]) becomes that entry with no qualifier; every other stays,
 * as free text, exactly as written. The dictionary is read once, and only where the book lists any equipment; one that
 * cannot be read leaves every item free text.
 */
private class EquipmentByEntry(private val equipment: () -> List<DictionaryEntry>) : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val items = buildList {
            db.query("select recipe_id, position, name from recipe_equipment").use { rows ->
                while (rows.moveToNext()) add(Triple(rows.getString(0), rows.getInt(1), rows.getString(2)))
            }
        }
        db.execSQL("drop table recipe_equipment")
        db.execSQL(
            "create table if not exists `recipe_equipment` (`recipe_id` TEXT NOT NULL, `position` INTEGER NOT NULL, " +
                "`dictionary_entry` INTEGER, `qualifier` TEXT, `free_text_name` TEXT, PRIMARY KEY(`recipe_id`, `position`), " +
                "FOREIGN KEY(`recipe_id`) REFERENCES `recipe`(`recipe_id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        if (items.isEmpty()) return
        // A dictionary that cannot be read finds no entry: every recipe opens intact, its items as free text.
        val names = EquipmentNames(
            try {
                equipment()
            } catch (e: Exception) {
                emptyList()
            },
        )
        items.forEach { (recipe, position, name) ->
            val entry = names.entryOf(name)
            db.execSQL(
                "insert into recipe_equipment (recipe_id, position, dictionary_entry, qualifier, free_text_name) values (?, ?, ?, null, ?)",
                arrayOf<Any?>(recipe, position, entry, name.takeIf { entry == null }),
            )
        }
    }
}

/** Which book a database file holds. Books are named by the account's identity, never by a key. */
sealed class BookName(val fileName: String) {
    /** The directory of the book's media, named like its database (decisions/20260928-one-database-per-recipe-book.md). */
    val mediaDirectoryName: String get() = fileName.removeSuffix(".db") + "-media"

    /** What was written and marked with no account (AUTH-026). */
    data object Pool : BookName("book-pool.db")

    data class OfAccount(val accountId: String) : BookName("book-$accountId.db")
}

/**
 * The media identity of the cover of the recipe a query names `r`: its one item attached to no step (MEDIA-001), null
 * for none.
 */
private const val COVER_OF_R =
    "(select m.media_id from media m where m.recipe_id = r.recipe_id and m.step_position is null " +
        "order by m.position limit 1)"

/** Everything of one recipe as its rows. */
data class RecipeRows(
    val recipe: RecipeEntity,
    val ingredients: List<IngredientEntity>,
    val steps: List<StepEntity>,
    val references: List<StepReferenceEntity>,
    val media: List<MediaEntity>,
    val tags: List<RecipeTagEntity>,
    /** The headings of its ingredient list, in order (DOM-022). */
    val groups: List<IngredientGroupEntity> = emptyList(),
    /** Its steps' timers, by step and then in order (DOM-024). */
    val timers: List<StepTimerEntity> = emptyList(),
    /** Its equipment, in order. */
    val equipment: List<EquipmentEntity> = emptyList(),
)

/** A recipe taken out of the book ([BookRecipeDao.take]): its rows, and its mark when it was a found one. */
data class TakenRows(val rows: RecipeRows, val favourite: FavouriteEntity?)

/** SQLite's limit that limits nothing. */
private const val NO_LIMIT = -1

@Dao
abstract class BookRecipeDao {
    /**
     * The own-recipes view under `newest`: the last change of content first (FLOW-039). A found recipe — one whose
     * content the book holds because it is marked (FLOW-009, FLOW-064) — is not written here and is not listed.
     */
    @Query(
        "select r.recipe_id as recipe_id, r.title as title, r.state as state, r.sent as sent, " +
            "$COVER_OF_R as cover_id, r.language as language, r.description as description from recipe r " +
            "where r.recipe_id not in (select recipe_id from favourite) " +
            "order by r.content_changed_at desc, r.recipe_id limit :limit",
    )
    abstract fun newest(limit: Int): Flow<List<OwnRecipeRow>>

    /** Every own recipe, in the order of [newest]: what the own-recipes view searches and orders (FLOW-035). */
    fun own(): Flow<List<OwnRecipeRow>> = newest(NO_LIMIT)

    /**
     * Every ingredient row of the book as the views are searched and filtered by it (FLOW-035): what names it, its
     * quantity and its recipe's serving count, in the order of the rows.
     */
    @Query(
        "select i.recipe_id as recipe_id, i.dictionary_entry as dictionary_entry, i.qualifier as qualifier, " +
            "i.free_text_name as free_text_name, i.unit_type as unit_type, i.unit as unit, i.quantity as quantity, " +
            "r.servings as servings from ingredient i join recipe r on r.recipe_id = i.recipe_id " +
            "order by i.recipe_id, i.position",
    )
    abstract fun ingredientLines(): Flow<List<IngredientLineRow>>

    /**
     * Every dictionary entry of equipment the book's recipes name, which the views are filtered by when the kitchen is
     * to have enough of it; items of free text are left out.
     */
    @Query("select recipe_id, dictionary_entry from recipe_equipment where dictionary_entry is not null order by recipe_id, position")
    abstract fun equipmentEntries(): Flow<List<RecipeEquipmentRow>>

    /** Every tag the book's recipes carry, which the views are filtered by (FLOW-035). */
    @Query("select * from recipe_tag order by recipe_id, tag_id")
    abstract fun allTags(): Flow<List<RecipeTagEntity>>

    @Query("select count(*) from recipe")
    abstract suspend fun count(): Int

    /** Writes the whole recipe, replacing whatever the book held of it, atomically. */
    @Transaction
    open suspend fun put(rows: RecipeRows) {
        upsert(rows.recipe)
        val id = rows.recipe.recipeId
        deleteIngredients(id)
        deleteSteps(id)
        deleteReferences(id)
        deleteMedia(id)
        deleteTags(id)
        deleteGroups(id)
        deleteTimers(id)
        deleteEquipment(id)
        insertIngredients(rows.ingredients)
        insertSteps(rows.steps)
        insertReferences(rows.references)
        insertMedia(rows.media)
        insertTags(rows.tags)
        insertGroups(rows.groups)
        insertTimers(rows.timers)
        insertEquipment(rows.equipment)
    }

    /**
     * Deletes the recipe and everything it holds — its equipment, ingredients and their groups, steps and their timers,
     * references, media and tags — atomically (ST-010).
     * Its parts cascade from its row; they are deleted here as well, so that nothing of it is left whatever the
     * connection's foreign-key setting. Returns whether the book held it.
     */
    @Transaction
    open suspend fun delete(recipeId: String): Boolean {
        deleteIngredients(recipeId)
        deleteSteps(recipeId)
        deleteReferences(recipeId)
        deleteMedia(recipeId)
        deleteTags(recipeId)
        deleteGroups(recipeId)
        deleteTimers(recipeId)
        deleteEquipment(recipeId)
        return deleteRecipe(recipeId) > 0
    }

    /**
     * Deletes the recipe [recipeId] as [delete] does, atomically, and returns the identities of the media items it
     * held, whose files are then the caller's to delete; null when the book held no such recipe.
     */
    @Transaction
    open suspend fun deleteHolding(recipeId: String): List<String>? {
        val media = media(recipeId).map { it.mediaId }
        return if (delete(recipeId)) media else null
    }

    /** Of the media identities [ids], those some recipe of the book still holds. */
    @Query("select media_id from media where media_id in (:ids)")
    abstract suspend fun held(ids: List<String>): List<String>

    /** Every media identity a recipe of the book holds. */
    @Query("select media_id from media")
    abstract suspend fun allMedia(): List<String>

    /**
     * The media identities whose files the device may clear (FLOW-060): those of every found recipe — one the book
     * marks — and of every own recipe of the status [sent]. A media item belongs to one recipe alone. The rows
     * themselves are not touched.
     */
    @Query(
        """select m.media_id from media m join recipe r on r.recipe_id = m.recipe_id
           where r.recipe_id in (select recipe_id from favourite) or r.sent = :sent
           order by m.media_id""",
    )
    abstract suspend fun clearableMedia(sent: SentStatus): List<String>

    /**
     * Writes a found recipe and marks it, atomically (FLOW-064): its rows and its favourite. Returns false, writing
     * nothing, when the book already holds a recipe of that identity — found before, or its own — or marks one.
     */
    @Transaction
    open suspend fun putFound(rows: RecipeRows, favourite: FavouriteEntity): Boolean {
        val id = rows.recipe.recipeId
        if (recipe(id) != null || isMarked(id)) return false
        put(rows)
        insertFavourite(favourite)
        return true
    }

    /**
     * Removes the marked recipe [recipeId] from the book with the content it holds of it (FLOW-009 (4), FLOW-064 (6)),
     * atomically. Returns the identities of the media items it held, whose files are then the caller's to delete, or
     * null when nothing of that identity was marked.
     */
    @Transaction
    open suspend fun removeMarked(recipeId: String): List<String>? {
        if (!isMarked(recipeId)) return null
        val media = media(recipeId).map { it.mediaId }
        deleteFavourite(recipeId)
        delete(recipeId)
        return media
    }

    /**
     * Takes the recipe [recipeId] out of the book, atomically: everything it holds, and its mark when the book holds it
     * as a found one. Returns what was taken, which [putBack] puts back as it was; null when the book held no such
     * recipe. The files of its media are the caller's.
     */
    @Transaction
    open suspend fun take(recipeId: String): TakenRows? {
        val rows = get(recipeId) ?: return null
        val favourite = favourite(recipeId)
        if (favourite != null) deleteFavourite(recipeId)
        delete(recipeId)
        return TakenRows(rows, favourite)
    }

    /**
     * Puts back what [take] took, atomically, as it was: the recipe with everything it held, and its mark, under the
     * same times, so that it is listed where it was. Returns false, writing nothing, when the book holds or marks a
     * recipe of that identity again.
     */
    @Transaction
    open suspend fun putBack(taken: TakenRows): Boolean {
        val id = taken.rows.recipe.recipeId
        if (recipe(id) != null || isMarked(id)) return false
        put(taken.rows)
        taken.favourite?.let { insertFavourite(it) }
        return true
    }

    @Query("select * from favourite where recipe_id = :id")
    protected abstract suspend fun favourite(id: String): FavouriteEntity?

    @Query("select exists(select 1 from favourite where recipe_id = :id)")
    abstract suspend fun isMarked(id: String): Boolean

    /**
     * A fork of [parent] the book holds whose content has not changed since it was made — its content time is still
     * its creation time (FLOW-015) — the newest such one first; null when every fork of it was changed, or none is held.
     */
    @Query(
        """select recipe_id from recipe where parent = :parent and content_changed_at = created_at
           and recipe_id not in (select recipe_id from favourite) order by created_at desc, recipe_id limit 1""",
    )
    abstract suspend fun unchangedForkOf(parent: String): String?

    @Insert
    protected abstract suspend fun insertFavourite(favourite: FavouriteEntity)

    @Query("delete from favourite where recipe_id = :id")
    protected abstract suspend fun deleteFavourite(id: String)

    @Transaction
    open suspend fun get(recipeId: String): RecipeRows? {
        val recipe = recipe(recipeId) ?: return null
        return RecipeRows(
            recipe, ingredients(recipeId), steps(recipeId), references(recipeId), media(recipeId), tags(recipeId),
            groups(recipeId), timers(recipeId), equipment(recipeId),
        )
    }

    @Query("select * from recipe where recipe_id = :id")
    protected abstract suspend fun recipe(id: String): RecipeEntity?

    @Query("select * from ingredient where recipe_id = :id order by position")
    protected abstract suspend fun ingredients(id: String): List<IngredientEntity>

    @Query("select * from step where recipe_id = :id order by position")
    protected abstract suspend fun steps(id: String): List<StepEntity>

    @Query("select * from step_reference where recipe_id = :id order by step_position, text_offset")
    protected abstract suspend fun references(id: String): List<StepReferenceEntity>

    @Query("select * from media where recipe_id = :id order by position")
    protected abstract suspend fun media(id: String): List<MediaEntity>

    @Query("select * from recipe_tag where recipe_id = :id order by tag_id")
    protected abstract suspend fun tags(id: String): List<RecipeTagEntity>

    @Query("select * from ingredient_group where recipe_id = :id order by position")
    protected abstract suspend fun groups(id: String): List<IngredientGroupEntity>

    @Query("select * from step_timer where recipe_id = :id order by step_position, position")
    protected abstract suspend fun timers(id: String): List<StepTimerEntity>

    @Query("select * from recipe_equipment where recipe_id = :id order by position")
    protected abstract suspend fun equipment(id: String): List<EquipmentEntity>

    @Upsert
    protected abstract suspend fun upsert(recipe: RecipeEntity)

    @Query("delete from recipe where recipe_id = :id")
    protected abstract suspend fun deleteRecipe(id: String): Int

    @Query("delete from ingredient where recipe_id = :id")
    protected abstract suspend fun deleteIngredients(id: String)

    @Query("delete from step where recipe_id = :id")
    protected abstract suspend fun deleteSteps(id: String)

    @Query("delete from step_reference where recipe_id = :id")
    protected abstract suspend fun deleteReferences(id: String)

    @Query("delete from media where recipe_id = :id")
    protected abstract suspend fun deleteMedia(id: String)

    @Query("delete from recipe_tag where recipe_id = :id")
    protected abstract suspend fun deleteTags(id: String)

    @Query("delete from ingredient_group where recipe_id = :id")
    protected abstract suspend fun deleteGroups(id: String)

    @Query("delete from step_timer where recipe_id = :id")
    protected abstract suspend fun deleteTimers(id: String)

    @Query("delete from recipe_equipment where recipe_id = :id")
    protected abstract suspend fun deleteEquipment(id: String)

    @Insert
    protected abstract suspend fun insertEquipment(rows: List<EquipmentEntity>)

    @Insert
    protected abstract suspend fun insertTimers(rows: List<StepTimerEntity>)

    @Insert
    protected abstract suspend fun insertGroups(rows: List<IngredientGroupEntity>)

    @Insert
    protected abstract suspend fun insertIngredients(rows: List<IngredientEntity>)

    @Insert
    protected abstract suspend fun insertSteps(rows: List<StepEntity>)

    @Insert
    protected abstract suspend fun insertReferences(rows: List<StepReferenceEntity>)

    @Insert
    protected abstract suspend fun insertMedia(rows: List<MediaEntity>)

    @Insert
    protected abstract suspend fun insertTags(rows: List<RecipeTagEntity>)
}

@Dao
interface FavouriteDao {
    @Query("select * from favourite order by added_at desc, recipe_id")
    fun all(): Flow<List<FavouriteEntity>>

    /**
     * The view of marked recipes, the last marked first: each with the title and the stated author of the content
     * the book holds of it, where it holds any (FLOW-009, FLOW-064 (3)).
     */
    @Query(
        "select f.recipe_id as recipe_id, coalesce(r.title, f.title) as title, r.stated_author as stated_author, " +
            "r.recipe_id is not null as held, $COVER_OF_R as cover_id, r.language as language, " +
            "coalesce(r.description, '') as description " +
            "from favourite f left join recipe r on r.recipe_id = f.recipe_id " +
            "order by f.added_at desc, f.recipe_id",
    )
    fun marked(): Flow<List<MarkedRecipeRow>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(favourite: FavouriteEntity)
}
