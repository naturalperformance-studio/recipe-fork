package app.recipefork.app.book

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.IngredientReference
import app.recipefork.core.recipe.IngredientShare
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.RecipeLanguage
import app.recipefork.core.recipe.RecipeState
import app.recipefork.core.recipe.SentStatus
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.Rational
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/** A book file written by an earlier version of the application opens with every recipe in it. */
@RunWith(AndroidJUnit4::class)
class BookMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = BookName.OfAccount("migration-test")

    @After
    fun delete() {
        context.deleteDatabase(book.fileName)
    }

    /** A book file of version 1, exactly as that version created it, holding one recipe with two ingredients. */
    private fun writeVersion1() {
        val file = context.getDatabasePath(book.fileName).also { it.parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            VERSION_1.forEach(db::execSQL)
            db.execSQL(
                "insert into recipe values ('r1', null, null, 'SENT', null, null, null, 'Soup', 'Hot', 'RU', 4, 'PRIVATE', " +
                    "0, 0, 0, 1000, 2000)",
            )
            db.execSQL("insert into ingredient values ('r1', 0, null, null, 'salt', 'TASTE_UNMEASURED', null, null, null, null)")
            db.execSQL("insert into ingredient values ('r1', 1, 7, null, null, 'PIECE', 'PIECE', '3/2', '60', 1)")
            db.execSQL("insert into recipe_tag values ('r1', 10)")
            db.version = 1
        }
    }

    @Test
    fun aVersion1BookOpensWithEveryRecipeAndIngredientKept() = runBlocking {
        writeVersion1()
        val database = BookDatabase.open(context, book)
        try {
            val recipe = RecipeRepository(ActiveBook(book, database)).get(RecipeId("r1"))
            val expected = Recipe.create(Instant.ofEpochMilli(1000), null, id = RecipeId("r1")).copy(
                sent = SentStatus.SENT,
                title = "Soup",
                description = "Hot",
                tags = setOf(10L),
                language = RecipeLanguage.RU,
                servings = 4,
                ingredients = listOf(
                    Ingredient(0, null, null, "salt", UnitType.TASTE_UNMEASURED, null, null),
                    Ingredient(1, 7, null, null, UnitType.PIECE, QuantityUnit.PIECE, Rational.of(3, 2), Rational.of(60), true),
                ),
                state = RecipeState.PRIVATE,
                contentChangedAt = Instant.ofEpochMilli(2000),
            )
            assertEquals(expected, recipe)
        } finally {
            database.close()
        }
    }

    // FLOW-024 (6): what the author typed into a quantity field survives the book being closed and reopened.
    @Test
    fun aRowsTypedQuantityIsKeptAfterTheBookIsReopened() = runBlocking {
        writeVersion1()
        val recipe = Recipe.create(Instant.ofEpochMilli(5000), null).copy(
            ingredients = listOf(
                Ingredient(0, null, null, "water", UnitType.EXACT, QuantityUnit.MILLILITRE, null, enteredQuantity = "1 500"),
                Ingredient(1, null, null, null, UnitType.EXACT, QuantityUnit.GRAM, Rational.of(15, 2)),
            ),
        )
        BookDatabase.open(context, book).let { database ->
            RecipeRepository(ActiveBook(book, database)).save(recipe)
            database.close()
        }
        val reopened = BookDatabase.open(context, book)
        try {
            assertEquals(recipe, RecipeRepository(ActiveBook(book, reopened)).get(recipe.id))
        } finally {
            reopened.close()
        }
    }

    // A step's part of an ingredient survives the book being closed and reopened, in a book migrated from version 1.
    @Test
    fun aPartOfAnIngredientIsKeptAfterTheBookIsReopened() = runBlocking {
        writeVersion1()
        val recipe = Recipe.create(Instant.ofEpochMilli(5000), null).copy(
            ingredients = listOf(
                Ingredient(0, null, null, "garlic", UnitType.PIECE, QuantityUnit.PIECE, Rational.of(4), divisible = false),
                Ingredient(1, null, null, "flour", UnitType.EXACT, QuantityUnit.KILOGRAM, Rational.of(3, 2)),
            ),
            steps = listOf(
                Step(
                    0, "Marinade:  and ",
                    listOf(
                        IngredientReference(0, 10, IngredientShare(Rational.of(2), QuantityUnit.PIECE)),
                        IngredientReference(1, 15, IngredientShare(Rational.of(1, 2), QuantityUnit.KILOGRAM)),
                    ),
                ),
                Step(1, "Sauce: ", listOf(IngredientReference(0, 7))),
            ),
        )
        BookDatabase.open(context, book).let { database ->
            RecipeRepository(ActiveBook(book, database)).save(recipe)
            database.close()
        }
        val reopened = BookDatabase.open(context, book)
        try {
            assertEquals(recipe, RecipeRepository(ActiveBook(book, reopened)).get(recipe.id))
        } finally {
            reopened.close()
        }
    }

    private companion object {
        /** What Room generated for the book at version 1. */
        val VERSION_1 = listOf(
            "CREATE TABLE IF NOT EXISTS `recipe` (`recipe_id` TEXT NOT NULL, `owner` TEXT, `parent` TEXT, `sent` TEXT NOT NULL, `stated_author` TEXT, `stated_parent_title` TEXT, `stated_parent_nickname` TEXT, `title` TEXT NOT NULL, `description` TEXT NOT NULL, `language` TEXT, `servings` INTEGER, `state` TEXT NOT NULL, `replaces_parent` INTEGER NOT NULL, `was_published` INTEGER NOT NULL, `safety_flag` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, `content_changed_at` INTEGER NOT NULL, PRIMARY KEY(`recipe_id`))",
            "CREATE INDEX IF NOT EXISTS `index_recipe_content_changed_at` ON `recipe` (`content_changed_at`)",
            "CREATE TABLE IF NOT EXISTS `ingredient` (`recipe_id` TEXT NOT NULL, `position` INTEGER NOT NULL, `dictionary_entry` INTEGER, `qualifier` TEXT, `free_text_name` TEXT, `unit_type` TEXT NOT NULL, `unit` TEXT, `quantity` TEXT, `piece_weight` TEXT, `divisible` INTEGER, PRIMARY KEY(`recipe_id`, `position`), FOREIGN KEY(`recipe_id`) REFERENCES `recipe`(`recipe_id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE TABLE IF NOT EXISTS `step` (`recipe_id` TEXT NOT NULL, `position` INTEGER NOT NULL, `text` TEXT NOT NULL, `timer_minutes` INTEGER, `timer_label` TEXT, PRIMARY KEY(`recipe_id`, `position`), FOREIGN KEY(`recipe_id`) REFERENCES `recipe`(`recipe_id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE TABLE IF NOT EXISTS `step_reference` (`recipe_id` TEXT NOT NULL, `step_position` INTEGER NOT NULL, `text_offset` INTEGER NOT NULL, `ingredient_position` INTEGER NOT NULL, PRIMARY KEY(`recipe_id`, `step_position`, `text_offset`), FOREIGN KEY(`recipe_id`) REFERENCES `recipe`(`recipe_id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE TABLE IF NOT EXISTS `media` (`media_id` TEXT NOT NULL, `recipe_id` TEXT NOT NULL, `position` INTEGER NOT NULL, `owner` TEXT, `kind` TEXT NOT NULL, `step_position` INTEGER, `visibility` TEXT NOT NULL, PRIMARY KEY(`media_id`), FOREIGN KEY(`recipe_id`) REFERENCES `recipe`(`recipe_id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_media_recipe_id` ON `media` (`recipe_id`)",
            "CREATE TABLE IF NOT EXISTS `recipe_tag` (`recipe_id` TEXT NOT NULL, `tag_id` INTEGER NOT NULL, PRIMARY KEY(`recipe_id`, `tag_id`), FOREIGN KEY(`recipe_id`) REFERENCES `recipe`(`recipe_id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE TABLE IF NOT EXISTS `favourite` (`recipe_id` TEXT NOT NULL, `title` TEXT NOT NULL, `added_at` INTEGER NOT NULL, PRIMARY KEY(`recipe_id`))",
            "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)",
            "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'd717bd6e765bd6a3aece06579173a804')",
        )
    }
}
