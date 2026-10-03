package app.recipefork.app.editor

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookDatabase
import app.recipefork.app.book.BookName
import app.recipefork.app.dictionary.IngredientEntryEntity
import app.recipefork.app.recipe.BookFilters
import app.recipefork.app.recipe.ListingParams
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.file.RecipeFile
import app.recipefork.core.file.RecipeFileContent
import app.recipefork.core.recipe.AccountId
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.fork
import app.recipefork.core.search.BookOrder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.util.Locale

/** The dictionary of the shipped demo: chicken divisible, wings below it indivisible, meat saying nothing. */
val FLAGGED_ENTRIES = listOf(
    IngredientEntryEntity(10, "курица", "chicken", null, divisible = true),
    IngredientEntryEntity(11, "крылышки", "chicken wings", 10, divisible = false),
    IngredientEntryEntity(30, "мясо", "meat", null),
)

/**
 * What the editor writes into a row naming an entry, read without the dictionary: the entry's divisibility held by the
 * row (FLOW-001 (2), FLOW-023 (11)) and the entry's qualifier (DOM-009), across the book, a recipe file and a fork; and
 * that the qualifier is searched but never filtered by (CAT-006).
 */
@RunWith(AndroidJUnit4::class)
class EntryRowsCarryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = BookName.OfAccount("entry-rows-carry")
    private val now = Instant.parse("2026-10-03T12:00:00Z")

    @After
    fun delete() {
        context.deleteDatabase(book.fileName)
    }

    private suspend fun written(recipes: RecipeRepository, edit: RecipeEditor.() -> Unit): Recipe {
        val recipe = Recipe.create(now, null).copy(title = "Ужин")
        recipes.save(recipe)
        val editor = RecipeEditor(recipes, recipe.id, { now }, CoroutineScope(Dispatchers.Unconfined))
        editor.load()
        editor.knowEntries(FLAGGED_ENTRIES)
        editor.edit()
        // A second editor reads only once every write of the first is in the book.
        RecipeEditor(recipes, recipe.id, { now }, CoroutineScope(Dispatchers.Unconfined)).load()
        return recipes.get(recipe.id)!!
    }

    // FLOW-001 (2), FLOW-023 (11): the row holds its entry's flag whichever comes first, the entry or the unit.
    @Test
    fun aPieceRowHoldsItsEntrysDivisibility() = runBlocking {
        val database = BookDatabase.open(context, book)
        try {
            val recipes = RecipeRepository(ActiveBook(book, database))
            val read = written(recipes) {
                repeat(4) { addIngredient() }
                setIngredientEntry(0, 11)
                setIngredientUnit(0, QuantityUnit.PIECE)
                setIngredientUnit(1, QuantityUnit.PIECE)
                setIngredientEntry(1, 10)
                setIngredientEntry(2, 30)
                setIngredientUnit(2, QuantityUnit.PIECE)
                setIngredientFreeText(3, "яйцо")
                setIngredientUnit(3, QuantityUnit.PIECE)
                // Asked only where the entry says nothing, or the name is the author's own.
                assertFalse(setIngredientDivisible(0, true))
                assertTrue(setIngredientDivisible(2, true))
                assertTrue(setIngredientDivisible(3, false))
                setIngredientQuantity(0, "1.5")
                setIngredientQuantity(1, "1.5")
            }
            assertEquals(listOf(false, true, true, false), read.ingredients.map { it.divisible })
            // Off the entry's grid: 1½ wings is no complete row; 1½ chickens is.
            assertEquals(listOf(false, true), read.ingredients.take(2).map { it.isComplete })
            // The divisibility goes with the entry: the row in the author's words is asked again.
            val again = written(recipes) {
                addIngredient()
                setIngredientEntry(0, 11)
                setIngredientUnit(0, QuantityUnit.PIECE)
                setIngredientFreeText(0, "крылыш")
            }
            assertNull(again.ingredients.single().divisible)
        } finally {
            database.close()
        }
    }

    // DOM-009, FLOW-023 (4): the qualifier lives beside an entry only, and goes with it.
    @Test
    fun theQualifierNarrowsAnEntryOnly() = runBlocking {
        val database = BookDatabase.open(context, book)
        try {
            val recipes = RecipeRepository(ActiveBook(book, database))
            val read = written(recipes) {
                repeat(3) { addIngredient() }
                setIngredientEntry(0, 11)
                setIngredientQualifier(0, "  острые ")
                assertFalse(setIngredientQualifier(1, "острые"))
                setIngredientEntry(2, 11)
                setIngredientQualifier(2, "острые")
                setIngredientFreeText(2, "крылыш острые")
            }
            assertEquals(listOf(11L, null, null), read.ingredients.map { it.dictionaryEntry })
            assertEquals(listOf("острые", null, null), read.ingredients.map { it.qualifier })
            assertEquals("крылыш острые", read.ingredients[2].freeTextName)
        } finally {
            database.close()
        }
    }

    // FLOW-001 (2), DOM-009: a recipe file and a fork carry the qualifier and the entry's divisibility, read without
    // the dictionary.
    @Test
    fun aRecipeFileAndAForkCarryTheQualifierAndTheDivisibility() = runBlocking {
        val database = BookDatabase.open(context, book)
        try {
            val recipes = RecipeRepository(ActiveBook(book, database))
            val read = written(recipes) {
                addIngredient()
                setIngredientEntry(0, 11)
                setIngredientQualifier(0, "острые")
                setIngredientUnit(0, QuantityUnit.PIECE)
                setIngredientQuantity(0, "6")
            }
            val file = RecipeFile.decode(RecipeFile.encode(RecipeFileContent.of(read, null, "cook")))
            val fromFile = file.ingredients.single()
            assertEquals(11L, fromFile.dictionaryEntry)
            assertEquals("острые", fromFile.qualifier)
            assertEquals(false, fromFile.divisible)
            assertTrue(fromFile.isComplete)
            val fork = read.fork(now.plusSeconds(60), AccountId("someone"))
            recipes.save(fork)
            val forked = recipes.get(fork.id)!!.ingredients.single()
            assertEquals(read.ingredients.single(), forked)
            assertEquals("острые", forked.qualifier)
            assertEquals(false, forked.divisible)
        } finally {
            database.close()
        }
    }

    // CAT-006, FLOW-035: the search finds a qualifier's word; the ingredient filter reads the entry alone.
    @Test
    fun theQualifierIsSearchedButNeverFiltered() = runBlocking {
        val database = BookDatabase.open(context, book)
        try {
            val recipes = RecipeRepository(ActiveBook(book, database))
            val read = written(recipes) {
                addIngredient()
                setIngredientEntry(0, 11)
                setIngredientQualifier(0, "острые")
            }
            val other = written(recipes) {
                addIngredient()
                setIngredientEntry(0, 30)
                setIngredientQualifier(0, "курица")
            }
            suspend fun listed(query: String, filters: BookFilters = BookFilters()) = recipes
                .ownListed(flowOf(ListingParams(query, BookOrder.NEWEST, Locale.forLanguageTag("ru"), filters))) { FLAGGED_ENTRIES }
                .first().rows.map { it.recipeId }
            assertEquals(listOf(read.id.value), listed("остр"))
            assertEquals(listOf(read.id.value), listed("", BookFilters(ingredient = 10)))
            assertEquals(listOf(read.id.value), listed("", BookFilters(ingredient = 11)))
            // A qualifier naming an entry's word holds none of it.
            assertEquals(emptyList<String>(), listed("остр", BookFilters(ingredient = 30)))
            assertEquals(listOf(other.id.value), listed("", BookFilters(ingredient = 30)))
        } finally {
            database.close()
        }
    }
}
