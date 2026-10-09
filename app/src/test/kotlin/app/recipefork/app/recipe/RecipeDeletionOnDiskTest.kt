package app.recipefork.app.recipe

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookDatabase
import app.recipefork.app.book.BookName
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.IngredientReference
import app.recipefork.core.recipe.Media
import app.recipefork.core.recipe.MediaAttachment
import app.recipefork.core.recipe.MediaKind
import app.recipefork.core.recipe.MediaVisibility
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.Timer
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.Rational
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import kotlin.time.Duration.Companion.minutes

/**
 * FLOW-053 (6) across the book file: a deleted recipe, with its ingredients, steps, references, media and tags, is
 * not in the book once its database is closed and opened again from disk; the book's other recipes are.
 */
@RunWith(AndroidJUnit4::class)
class RecipeDeletionOnDiskTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = BookName.OfAccount("deletion-on-disk")
    private val now = Instant.parse("2026-10-02T12:00:00Z")

    @After
    fun delete() {
        context.deleteDatabase(book.fileName)
    }

    private fun full(title: String): Recipe = Recipe.create(now, null).copy(
        title = title,
        servings = 2,
        tags = setOf(3, 7),
        ingredients = listOf(
            Ingredient(0, null, null, "beans", UnitType.EXACT, QuantityUnit.GRAM, Rational.of(200), null, null),
            Ingredient(1, null, null, "salt", UnitType.TASTE_UNMEASURED, null, null),
        ),
        steps = listOf(
            Step(0, "Boil the beans", listOf(IngredientReference(0, 9)), listOf(Timer(10.minutes), Timer(3.minutes, "Rest"))),
            Step(1, "Salt them", listOf(IngredientReference(1, 0))),
        ),
        media = listOf(Media("m-$title", null, MediaKind.PHOTO, MediaAttachment.Cover, MediaVisibility.PRIVATE)),
    )

    private fun rows(database: BookDatabase, recipe: Recipe): Map<String, Int> =
        listOf("recipe", "ingredient", "step", "step_timer", "step_reference", "media", "recipe_tag").associateWith { table ->
            database.openHelper.readableDatabase
                .query("select count(*) from $table where recipe_id = ?", arrayOf(recipe.id.value))
                .use { it.moveToFirst(); it.getInt(0) }
        }

    @Test
    fun aDeletedRecipeAndEverythingItHoldsIsGoneFromTheReopenedBook() = runBlocking {
        val database = BookDatabase.open(context, book)
        val recipes = RecipeRepository(ActiveBook(book, database))
        val soup = full("Soup")
        val bread = full("Bread")
        recipes.save(soup)
        recipes.save(bread)
        assertTrue(rows(database, soup).values.all { it > 0 })

        assertTrue(recipes.delete(soup.id))
        // A second deletion finds nothing to delete.
        assertFalse(recipes.delete(soup.id))
        database.close()

        val reopened = BookDatabase.open(context, book)
        try {
            val read = RecipeRepository(ActiveBook(book, reopened))
            assertNull(read.get(soup.id))
            assertEquals(rows(reopened, soup).keys.associateWith { 0 }, rows(reopened, soup))
            assertEquals(bread, read.get(bread.id))
            assertEquals(listOf(bread.id.value), read.ownNewest(10).first().map { it.recipeId })
        } finally {
            reopened.close()
        }
    }
}
