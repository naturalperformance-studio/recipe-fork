package app.recipefork.app.editor

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookDatabase
import app.recipefork.app.book.BookName
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.Rational
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * FLOW-024 (6) across the book file: the serving count and every ingredient row the editor wrote — incomplete,
 * off its grid, or unreadable — are read back once the book's database is closed and opened again from disk.
 */
@RunWith(AndroidJUnit4::class)
class IngredientsOnDiskTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = BookName.OfAccount("ingredients-on-disk")
    private val now = Instant.parse("2026-10-02T12:00:00Z")

    @After
    fun delete() {
        context.deleteDatabase(book.fileName)
    }

    @Test
    fun everythingEnteredIsReadBackFromTheReopenedBook() = runBlocking {
        val database = BookDatabase.open(context, book)
        val recipes = RecipeRepository(ActiveBook(book, database))
        val recipe = Recipe.create(now, null)
        recipes.save(recipe)
        val editor = RecipeEditor(recipes, recipe.id, { now }, CoroutineScope(Dispatchers.Unconfined))
        editor.load()
        editor.setServings("6")
        // A complete row; a piece with no divisibility; one off its grid; one with separators; one to taste; an empty one.
        repeat(6) { editor.addIngredient() }
        editor.setIngredientName(0, "flour")
        editor.setIngredientUnit(0, QuantityUnit.KILOGRAM)
        editor.setIngredientQuantity(0, "1,5")
        editor.setIngredientName(1, "eggs")
        editor.setIngredientUnit(1, QuantityUnit.PIECE)
        editor.setIngredientQuantity(1, "3")
        editor.setIngredientName(2, "yeast")
        editor.setIngredientQuantity(2, "7.5")
        editor.setIngredientName(3, "water")
        editor.setIngredientUnit(3, QuantityUnit.MILLILITRE)
        editor.setIngredientQuantity(3, "1 500")
        editor.setIngredientName(4, "salt")
        editor.setIngredientUnit(4, QuantityUnit.TO_TASTE)
        val written = editor.recipe.value!!
        // A second editor reads only once every write of the first is in the book.
        RecipeEditor(recipes, recipe.id, { now }, CoroutineScope(Dispatchers.Unconfined)).load()
        database.close()

        val reopened = BookDatabase.open(context, book)
        try {
            val read = RecipeRepository(ActiveBook(book, reopened)).get(recipe.id)!!
            assertEquals(written, read)
            assertEquals(6, read.servings)
            val rows = read.ingredients
            assertEquals(listOf("flour", "eggs", "yeast", "water", "salt", null), rows.map { it.freeTextName })
            assertEquals(listOf(Rational.of(3, 2), Rational.of(3), Rational.of(15, 2), null, null, null), rows.map { it.quantity })
            assertEquals("1 500", rows[3].enteredQuantity)
            assertNull(rows[1].divisible)
            assertEquals(UnitType.TASTE_UNMEASURED, rows[4].unitType)
            assertEquals(listOf(true, false, false, false, true, false), rows.map { it.isComplete })
        } finally {
            reopened.close()
        }
    }

    // DOM-009 across the book file: a row named by an entry and a row in the author's own words, each read back as
    // it was chosen once the book is closed and opened again — an entry replacing free text included (FLOW-022 (3)).
    @Test
    fun theChosenNameIsReadBackFromTheReopenedBook() = runBlocking {
        val database = BookDatabase.open(context, book)
        val recipes = RecipeRepository(ActiveBook(book, database))
        val recipe = Recipe.create(now, null)
        recipes.save(recipe)
        val editor = RecipeEditor(recipes, recipe.id, { now }, CoroutineScope(Dispatchers.Unconfined))
        editor.load()
        repeat(3) { editor.addIngredient() }
        editor.setIngredientFreeText(0, "chicken")
        editor.setIngredientEntry(0, 10)
        editor.setIngredientFreeText(1, "wild garlic")
        editor.setIngredientEntry(2, 11)
        editor.setIngredientUnit(2, QuantityUnit.PIECE)
        editor.setIngredientDivisible(2, false)
        val written = editor.recipe.value!!
        RecipeEditor(recipes, recipe.id, { now }, CoroutineScope(Dispatchers.Unconfined)).load()
        database.close()

        val reopened = BookDatabase.open(context, book)
        try {
            val read = RecipeRepository(ActiveBook(book, reopened)).get(recipe.id)!!
            assertEquals(written, read)
            assertEquals(listOf(10L, null, 11L), read.ingredients.map { it.dictionaryEntry })
            assertEquals(listOf(null, "wild garlic", null), read.ingredients.map { it.freeTextName })
            assertEquals(false, read.ingredients[2].divisible)
        } finally {
            reopened.close()
        }
    }
}
