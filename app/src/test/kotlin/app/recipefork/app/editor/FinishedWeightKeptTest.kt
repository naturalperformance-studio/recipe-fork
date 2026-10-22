package app.recipefork.app.editor

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.Step
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * v0.44, T-318: the editor shows no finished weight yet, and a change of anything else — servings among it — keeps the
 * weight the recipe holds, in the editor and in the book.
 */
@RunWith(AndroidJUnit4::class)
class FinishedWeightKeptTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val recipes = RecipeRepository(ActiveBook(BookName.Pool, book.database))
    private val now = Instant.parse("2026-10-22T12:00:00Z")

    private val weighed = Recipe.create(now, null).copy(title = "Плов", servings = 2, steps = listOf(Step(0, "Томить.")), finishedWeightGrams = 560)

    @After
    fun close() = book.close()

    private fun open(recipe: Recipe): RecipeEditor {
        runBlocking { recipes.save(recipe) }
        return RecipeEditor(recipes, recipe.id, { now.plusSeconds(60) }, CoroutineScope(Dispatchers.Unconfined))
            .also { runBlocking { it.load() } }
    }

    @Test
    fun editingOtherFieldsKeepsTheFinishedWeight() = runBlocking {
        val editor = open(weighed)
        assertEquals(560, editor.recipe.value!!.finishedWeightGrams)
        editor.setTitle("Плов с курицей")
        editor.setServings("4")
        editor.setDescription("По-узбекски")
        editor.setStepText(0, "Томить час.")
        assertTrue(editor.written())
        assertEquals(560, editor.recipe.value!!.finishedWeightGrams)
        val stored = recipes.get(weighed.id)!!
        assertEquals("Плов с курицей", stored.title)
        assertEquals(4, stored.servings)
        assertEquals(560, stored.finishedWeightGrams)
    }

    @Test
    fun aRecipeWithNoFinishedWeightStaysWithNone() = runBlocking {
        val editor = open(weighed.copy(finishedWeightGrams = null))
        editor.setTitle("Плов")
        assertTrue(editor.written())
        assertNull(recipes.get(weighed.id)!!.finishedWeightGrams)
    }
}
