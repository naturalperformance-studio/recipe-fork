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
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * FLOW-024 (6) across the book file: the steps — added, edited, one deleted, a timer and references kept — and the
 * description the editor wrote are read back once the book's database is closed and opened again from disk.
 */
@RunWith(AndroidJUnit4::class)
class StepsOnDiskTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = BookName.OfAccount("steps-on-disk")
    private val now = Instant.parse("2026-10-02T12:00:00Z")

    @After
    fun delete() {
        context.deleteDatabase(book.fileName)
    }

    @Test
    fun theStepsAndTheDescriptionAreReadBackFromTheReopenedBook() = runBlocking {
        val database = BookDatabase.open(context, book)
        val recipes = RecipeRepository(ActiveBook(book, database))
        val flour = Ingredient(0, null, null, "flour", UnitType.EXACT, QuantityUnit.GRAM, Rational.of(500))
        val recipe = Recipe.create(now, null).copy(
            ingredients = listOf(flour),
            steps = listOf(Step(0, "Sift the flour.", listOf(IngredientReference(0, 9)), Timer(5.minutes, "Rest"))),
        )
        recipes.save(recipe)
        val editor = RecipeEditor(recipes, recipe.id, { now }, CoroutineScope(Dispatchers.Unconfined))
        editor.load()
        repeat(3) { editor.addStep() }
        editor.setStepText(0, "Sift the flour twice.")
        editor.setStepText(1, "To be deleted.")
        editor.setStepText(2, "Knead.\nLet it rise.")
        editor.removeStep(1)
        editor.setDescription("Plain bread.\n\nNo sugar.")
        val written = editor.recipe.value!!
        // A second editor reads only once every write of the first is in the book.
        RecipeEditor(recipes, recipe.id, { now }, CoroutineScope(Dispatchers.Unconfined)).load()
        database.close()

        val reopened = BookDatabase.open(context, book)
        try {
            val read = RecipeRepository(ActiveBook(book, reopened)).get(recipe.id)!!
            assertEquals(written, read)
            assertEquals(listOf("Sift the flour twice.", "Knead.\nLet it rise.", ""), read.steps.map { it.text })
            assertEquals(listOf(0, 1, 2), read.steps.map { it.position })
            assertEquals(Timer(5.minutes, "Rest"), read.steps[0].timer)
            assertEquals(listOf(IngredientReference(0, 9)), read.steps[0].ingredientReferences)
            assertEquals("Plain bread.\n\nNo sugar.", read.description)
        } finally {
            reopened.close()
        }
    }

    // FLOW-027: timers attached and named in the editor, one with its step deleted, read back from the reopened book.
    @Test
    fun theTimersAndTheirNamesAreReadBackFromTheReopenedBook() = runBlocking {
        val database = BookDatabase.open(context, book)
        val recipes = RecipeRepository(ActiveBook(book, database))
        val recipe = Recipe.create(now, null)
        recipes.save(recipe)
        val editor = RecipeEditor(recipes, recipe.id, { now }, CoroutineScope(Dispatchers.Unconfined))
        editor.load()
        repeat(4) { editor.addStep() }
        editor.setStepText(0, "Marinate overnight.")
        editor.setStepText(1, "To be deleted, with its timer.")
        editor.setStepText(2, "Roast the chicken until golden.")
        editor.setStepText(3, "Rest.")
        editor.addTimer(0, 0, 12, 0)
        editor.setTimerLabel(0, 0, "Marinade")
        editor.addTimer(1, 0, 0, 5)
        editor.addTimer(2, 0, 1, 5)
        editor.addTimer(3, 0, 0, 0)
        editor.removeStep(1)
        val name = "A name the author wrote that runs well past sixty characters, kept whole"
        editor.setTimerLabel(1, 0, name)
        editor.setStepText(1, "Roast the chicken until golden and crisp.")
        val written = editor.recipe.value!!
        RecipeEditor(recipes, recipe.id, { now }, CoroutineScope(Dispatchers.Unconfined)).load()
        database.close()

        val reopened = BookDatabase.open(context, book)
        try {
            val read = RecipeRepository(ActiveBook(book, reopened)).get(recipe.id)!!
            assertEquals(written, read)
            assertEquals(
                listOf(Timer(12.hours, "Marinade"), Timer(1.hours + 5.minutes, name), null),
                read.steps.map { it.timer },
            )
            assertEquals(listOf("Marinate overnight.", "Roast the chicken until golden and crisp.", "Rest."), read.steps.map { it.text })
        } finally {
            reopened.close()
        }
    }
}
