package app.recipefork.app.file

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.card.forkRecipe
import app.recipefork.app.media.PhotoStore
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.file.RecipeFile
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.Step
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.zip.ZipInputStream

/**
 * v0.44, T-318, scenario 5: the recipe file carries what the finished dish weighs to whoever imports it, and the version
 * they make of the found recipe keeps it; a recipe with none exports and imports with none.
 */
@RunWith(AndroidJUnit4::class)
class FinishedWeightFileTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val friendsBook = TestBook(context)
    private val recipes = RecipeRepository(ActiveBook(BookName.Pool, book.database), context.filesDir)
    private val friend = BookName.OfAccount("finished-weight-friend")
    private val friendsActive = ActiveBook(friend, friendsBook.database)
    private val friendsRecipes = RecipeRepository(friendsActive, context.filesDir)
    private val now = Instant.parse("2026-10-22T12:00:00Z")

    @Before
    fun forgetEarlierDirectories() = FileProviderCache.clear()

    @After
    fun close() {
        book.close()
        friendsBook.close()
        PhotoStore(context.filesDir).directory(BookName.Pool).deleteRecursively()
        PhotoStore(context.filesDir).directory(friend).deleteRecursively()
    }

    /** Exports [recipe] from the book and imports its file into the friend's; returns the document and what arrived. */
    private fun travel(recipe: Recipe): Pair<String, Recipe> = runBlocking {
        recipes.save(recipe)
        val uri = RecipeExport.write(context, recipes, recipe.id)!!
        val document = ZipInputStream(context.contentResolver.openInputStream(uri)!!).use { zip ->
            generateSequence { zip.nextEntry }.first { it.name == RecipeFile.DOCUMENT }
            ByteArrayOutputStream().also { zip.copyTo(it) }.toString(Charsets.UTF_8.name())
        }
        RecipeImport(friendsRecipes, context.filesDir, clock = { now }).import { context.contentResolver.openInputStream(uri)!! }
        document to friendsRecipes.get(recipe.id)!!
    }

    @Test
    fun theFinishedWeightTravelsInItsFileAndIntoTheReadersVersion() = runBlocking {
        val recipe = Recipe.create(now, null).copy(title = "Плов", servings = 2, steps = listOf(Step(0, "Томить."))).copy(finishedWeightGrams = 560)
        val (document, arrived) = travel(recipe)
        assertTrue("\"format_version\": 9" in document)
        assertEquals(560, RecipeFile.decode(document).finishedWeightGrams)
        assertEquals(560, arrived.finishedWeightGrams)
        assertEquals(2, arrived.servings)
        val version = forkRecipe(friendsRecipes, friendsActive, arrived.id, now.plusSeconds(60))!!
        val fork = friendsRecipes.get(version)!!
        assertEquals(arrived.id, fork.parent)
        assertEquals(2, fork.servings)
        assertEquals(560, fork.finishedWeightGrams)
    }

    @Test
    fun aRecipeWithNoFinishedWeightTravelsWithNone() = runBlocking {
        val (document, arrived) = travel(Recipe.create(now, null).copy(title = "Plain", steps = listOf(Step(0, "Boil."))))
        assertTrue("finished_weight_grams" !in document)
        assertNull(arrived.finishedWeightGrams)
        assertNull(friendsRecipes.get(forkRecipe(friendsRecipes, friendsActive, arrived.id, now.plusSeconds(60))!!)!!.finishedWeightGrams)
    }
}
