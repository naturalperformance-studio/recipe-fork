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
import kotlinx.coroutines.flow.Flow

/** One recipe book's database: an account's, or the pool's (decisions/20260928-one-database-per-recipe-book.md). */
@Database(
    entities = [
        RecipeEntity::class, IngredientEntity::class, StepEntity::class, StepReferenceEntity::class,
        MediaEntity::class, RecipeTagEntity::class, FavouriteEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class BookDatabase : RoomDatabase() {
    abstract fun recipes(): BookRecipeDao
    abstract fun favourites(): FavouriteDao

    companion object {
        fun open(context: Context, book: BookName): BookDatabase =
            Room.databaseBuilder(context, BookDatabase::class.java, book.fileName).addMigrations(*MIGRATIONS).build()

        /** Every step from an older book file to this version; a book already on a phone keeps every recipe. */
        val MIGRATIONS: Array<Migration> = arrayOf(
            // 1 → 2: an ingredient row keeps a quantity the author typed that reads as no value (FLOW-024 (6)).
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("alter table ingredient add column entered_quantity text")
                }
            },
        )
    }
}

/** Which book a database file holds. Books are named by the account's identity, never by a key. */
sealed class BookName(val fileName: String) {
    /** What was written and marked with no account (AUTH-026). */
    data object Pool : BookName("book-pool.db")

    data class OfAccount(val accountId: String) : BookName("book-$accountId.db")
}

/** Everything of one recipe as its rows. */
data class RecipeRows(
    val recipe: RecipeEntity,
    val ingredients: List<IngredientEntity>,
    val steps: List<StepEntity>,
    val references: List<StepReferenceEntity>,
    val media: List<MediaEntity>,
    val tags: List<RecipeTagEntity>,
)

@Dao
abstract class BookRecipeDao {
    /** The own-recipes view under `newest`: the last change of content first (FLOW-039). */
    @Query(
        "select recipe_id, title, state, sent from recipe " +
            "order by content_changed_at desc, recipe_id limit :limit",
    )
    abstract fun newest(limit: Int): Flow<List<OwnRecipeRow>>

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
        insertIngredients(rows.ingredients)
        insertSteps(rows.steps)
        insertReferences(rows.references)
        insertMedia(rows.media)
        insertTags(rows.tags)
    }

    /**
     * Deletes the recipe and everything it holds — ingredients, steps, references, media and tags — atomically (ST-010).
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
        return deleteRecipe(recipeId) > 0
    }

    @Transaction
    open suspend fun get(recipeId: String): RecipeRows? {
        val recipe = recipe(recipeId) ?: return null
        return RecipeRows(
            recipe, ingredients(recipeId), steps(recipeId), references(recipeId), media(recipeId), tags(recipeId),
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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(favourite: FavouriteEntity)
}
