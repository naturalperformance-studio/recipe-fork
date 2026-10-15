package app.recipefork.app.file

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
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
 * v0.39, T-279: the recipe file carries the text a recipe was made from, exactly, to whoever imports it; a recipe with
 * none exports and imports with none.
 */
@RunWith(AndroidJUnit4::class)
class SourceTextFileTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val friendsBook = TestBook(context)
    private val recipes = RecipeRepository(ActiveBook(BookName.Pool, book.database), context.filesDir)
    private val friend = BookName.OfAccount("source-text-friend")
    private val friendsRecipes = RecipeRepository(ActiveBook(friend, friendsBook.database), context.filesDir)
    private val now = Instant.parse("2026-10-15T12:00:00Z")
    private val pasted = "Блины\n\n  Мука — 200 г\r\nМолоко 500 мл\t\"тёплое\"\n\nСмешать. Жарить 2 мин.\n"

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
    fun theTextARecipeWasMadeFromTravelsInItsFileExactly() {
        val recipe = Recipe.create(now, null).copy(title = "Блины", steps = listOf(Step(0, "Жарить."))).withSourceText(pasted)
        val (document, arrived) = travel(recipe)
        assertTrue("\"format_version\": 8" in document)
        assertEquals(pasted, RecipeFile.decode(document).sourceText)
        assertEquals(pasted, arrived.sourceText)
    }

    @Test
    fun aRecipeWithNoSourceTextTravelsWithNone() {
        val (_, arrived) = travel(Recipe.create(now, null).copy(title = "Plain", steps = listOf(Step(0, "Boil."))))
        assertNull(arrived.sourceText)
    }
}
