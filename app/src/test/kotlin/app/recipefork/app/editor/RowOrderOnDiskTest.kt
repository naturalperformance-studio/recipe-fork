package app.recipefork.app.editor

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookDatabase
import app.recipefork.app.book.BookName
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.IngredientReference
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.Timer
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.Rational
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import kotlin.time.Duration.Companion.minutes

/**
 * FLOW-025 across the book file: the order the editor set by inserting and moving steps and rows — with the timers
 * and references that moved with them — is read back once the book's database is closed and opened again from disk.
 */
@RunWith(AndroidJUnit4::class)
class RowOrderOnDiskTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = BookName.OfAccount("row-order-on-disk")
    private val now = Instant.parse("2026-10-02T12:00:00Z")

    @After
    fun delete() {
        context.deleteDatabase(book.fileName)
    }

    private fun row(position: Int, name: String) =
        Ingredient(position, null, null, name, UnitType.EXACT, QuantityUnit.GRAM, Rational.of(position + 1L))

    @Test
    fun theNewOrderIsReadBackFromTheReopenedBook() = runBlocking {
        val database = BookDatabase.open(context, book)
        val recipes = RecipeRepository(ActiveBook(book, database))
        val recipe = Recipe.create(now, null).copy(
            ingredients = listOf(row(0, "salt"), row(1, "flour"), row(2, "water")),
            steps = listOf(
                Step(0, "Mix salt and flour.", listOf(IngredientReference(0, 4), IngredientReference(1, 13))),
                Step(1, "Add water.", listOf(IngredientReference(2, 4)), Timer(5.minutes, "Water")),
                Step(2, "Bake.", timer = Timer(40.minutes)),
            ),
        )
        recipes.save(recipe)
        val editor = RecipeEditor(recipes, recipe.id, { now }, CoroutineScope(Dispatchers.Unconfined))
        editor.load()
        editor.moveStep(2, 0)
        editor.insertStep(2)
        editor.setStepText(2, "Rest.")
        editor.moveIngredient(2, 0)
        editor.insertIngredient(1)
        editor.setIngredientName(1, "yeast")
        val written = editor.recipe.value!!
        // A second editor reads only once every write of the first is in the book.
        RecipeEditor(recipes, recipe.id, { now }, CoroutineScope(Dispatchers.Unconfined)).load()
        database.close()

        val reopened = BookDatabase.open(context, book)
        try {
            val read = RecipeRepository(ActiveBook(book, reopened)).get(recipe.id)!!
            assertEquals(written, read)
            assertEquals(listOf("Bake.", "Mix salt and flour.", "Rest.", "Add water."), read.steps.map { it.text })
            assertEquals(listOf(0, 1, 2, 3), read.steps.map { it.position })
            assertEquals(listOf(Timer(40.minutes), null, null, Timer(5.minutes, "Water")), read.steps.map { it.timer })
            assertEquals(listOf("water", "yeast", "salt", "flour"), read.ingredients.map { it.freeTextName })
            assertEquals(listOf(0, 1, 2, 3), read.ingredients.map { it.position })
            val names = read.steps.map { step -> step.ingredientReferences.map { read.ingredients[it.ingredientPosition].freeTextName } }
            assertEquals(listOf(emptyList(), listOf("salt", "flour"), emptyList(), listOf("water")), names)
            assertEquals(listOf(IngredientReference(2, 4), IngredientReference(3, 13)), read.steps[1].ingredientReferences)
        } finally {
            reopened.close()
        }
    }
}
