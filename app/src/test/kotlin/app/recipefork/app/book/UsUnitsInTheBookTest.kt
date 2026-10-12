package app.recipefork.app.book

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.IngredientReference
import app.recipefork.core.recipe.IngredientShare
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.Rational
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/** v0.35 T-246: rows and parts in oz, lb and fl oz are stored in the book and read back once it is opened again. */
@RunWith(AndroidJUnit4::class)
class UsUnitsInTheBookTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = BookName.OfAccount("us-units-in-the-book")

    @After
    fun delete() {
        context.deleteDatabase(book.fileName)
    }

    @Test
    fun ouncesPoundsAndFluidOuncesAreReadBackFromTheReopenedBook() = runBlocking {
        val recipe = Recipe.create(Instant.parse("2026-10-12T12:00:00Z"), null).copy(
            title = "Pound cake",
            servings = 8,
            ingredients = listOf(
                Ingredient(0, null, freeTextName = "butter", unitType = UnitType.EXACT, unit = QuantityUnit.POUND, quantity = Rational.of(1)),
                Ingredient(1, null, freeTextName = "sugar", unitType = UnitType.EXACT, unit = QuantityUnit.OUNCE, quantity = Rational.of(29, 2)),
                Ingredient(2, null, freeTextName = "milk", unitType = UnitType.EXACT, unit = QuantityUnit.FLUID_OUNCE, quantity = Rational.of(3, 2)),
            ),
            steps = listOf(
                Step(0, "Cream the butter", listOf(IngredientReference(0, 10, IngredientShare(Rational.of(1, 2), QuantityUnit.POUND)))),
                Step(1, "Add the milk", listOf(IngredientReference(2, 8, IngredientShare(Rational.of(1, 2), QuantityUnit.FLUID_OUNCE)))),
            ),
        )
        val database = BookDatabase.open(context, book)
        RecipeRepository(ActiveBook(book, database)).save(recipe)
        database.close()

        val reopened = BookDatabase.open(context, book)
        try {
            val read = RecipeRepository(ActiveBook(book, reopened)).get(recipe.id)!!
            assertEquals(listOf(QuantityUnit.POUND, QuantityUnit.OUNCE, QuantityUnit.FLUID_OUNCE), read.ingredients.map { it.unit })
            assertEquals(listOf(Rational.of(1), Rational.of(29, 2), Rational.of(3, 2)), read.ingredients.map { it.quantity })
            assertEquals(
                listOf(QuantityUnit.POUND, QuantityUnit.FLUID_OUNCE),
                read.steps.map { it.ingredientReferences.single().share!!.unit },
            )
            assertEquals(recipe.ingredients, read.ingredients)
            assertEquals(recipe.steps, read.steps)
        } finally {
            reopened.close()
        }
    }
}
