package app.recipefork.app.recipe

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.file.RecipeImport
import app.recipefork.app.media.MediaSweep
import app.recipefork.app.media.PhotoStore
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.IngredientGroup
import app.recipefork.core.recipe.Media
import app.recipefork.core.recipe.MediaAttachment
import app.recipefork.core.recipe.MediaKind
import app.recipefork.core.recipe.MediaVisibility
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.Step
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
 * FLOW-053 in the book: a recipe taken out of it leaves at once with everything it holds — a found one with its mark —
 * while the files of its media stay; put back, it is whole and where it was; released, the files no other recipe holds
 * are deleted. Should the process end before either, the media sweep deletes those files at the book's next opening.
 */
@RunWith(AndroidJUnit4::class)
class RecipeTakeTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val active = ActiveBook(BookName.Pool, book.database)
    private val recipes = RecipeRepository(active, context.filesDir)
    private val store = PhotoStore(context.filesDir)
    private val now = Instant.parse("2026-10-09T12:00:00Z")

    @After
    fun close() {
        book.close()
        store.directory(BookName.Pool).deleteRecursively()
    }

    private fun media(mediaId: String, kind: MediaKind, attachment: MediaAttachment): Media {
        store.file(BookName.Pool, mediaId).apply { parentFile!!.mkdirs() }.writeBytes(byteArrayOf(-1, -40, -1, 1, 2, 3))
        return Media(mediaId, null, kind, attachment, MediaVisibility.PRIVATE)
    }

    private fun recipe(title: String, minutesAgo: Long = 0): Recipe {
        val key = title.lowercase()
        return Recipe.create(now.minusSeconds(minutesAgo * 60), null).copy(
            title = title,
            ingredients = listOf(
                Ingredient(0, null, null, "flour", UnitType.EXACT, QuantityUnit.GRAM, Rational.of(200), null, null, note = "sifted"),
                Ingredient(1, null, null, "apples", UnitType.EXACT, QuantityUnit.GRAM, Rational.of(300), null, null),
            ),
            ingredientGroups = listOf(IngredientGroup("Dough", 0), IngredientGroup("Filling", 1)),
            steps = listOf(Step(0, "Mix."), Step(1, "Bake.")),
            media = listOf(
                media("$key-cover", MediaKind.PHOTO, MediaAttachment.Cover),
                media("$key-clip", MediaKind.VIDEO, MediaAttachment.ToStep(1)),
            ),
        )
    }

    private fun files() = store.directory(BookName.Pool).list().orEmpty().sorted()

    private fun filesOf(vararg recipes: Recipe) = recipes.flatMap { r -> r.media.map { it.mediaId } }.sorted()

    private fun rows(recipe: Recipe): Map<String, Int> =
        listOf("recipe", "ingredient", "ingredient_group", "step", "media", "favourite").associateWith { table ->
            book.database.openHelper.readableDatabase
                .query("select count(*) from $table where recipe_id = ?", arrayOf(recipe.id.value))
                .use { it.moveToFirst(); it.getInt(0) }
        }

    @Test
    fun aTakenRecipeLeavesTheBookAtOnceAndItsFilesStay() = runBlocking {
        val pie = recipe("Pie").also { recipes.save(it) }
        val bread = recipe("Bread", minutesAgo = 1).also { recipes.save(it) }

        val taken = recipes.take(pie.id)!!

        assertFalse(taken.found)
        assertNull(recipes.get(pie.id))
        assertEquals(rows(pie).keys.associateWith { 0 }, rows(pie))
        assertEquals(filesOf(pie, bread), files())
        assertNull(recipes.take(pie.id))
    }

    @Test
    fun putBackAnOwnRecipeIsWholeAndWhereItWas() = runBlocking {
        val older = recipe("Bread", minutesAgo = 2).also { recipes.save(it) }
        val pie = recipe("Pie", minutesAgo = 1).also { recipes.save(it) }
        val newer = recipe("Soup").also { recipes.save(it) }
        val order = recipes.ownNewest(10).first()

        val taken = recipes.take(pie.id)!!
        assertTrue(taken.putBack())

        assertEquals(pie, recipes.get(pie.id))
        assertEquals(order, recipes.ownNewest(10).first())
        assertFalse(recipes.isMarked(pie.id))
        assertEquals(filesOf(older, pie, newer), files())
    }

    @Test
    fun putBackAFoundRecipeIsMarkedAgainWhereItWas() = runBlocking {
        val older = recipe("Bread").inState(RecipeImport.FOUND_STATE).also { recipes.addFound(it, now.minusSeconds(120)) }
        val pie = recipe("Pie").inState(RecipeImport.FOUND_STATE).also { recipes.addFound(it, now.minusSeconds(60)) }
        val newer = recipe("Soup").inState(RecipeImport.FOUND_STATE).also { recipes.addFound(it, now) }
        val marked = book.database.favourites().all().first()

        val taken = recipes.take(pie.id)!!
        assertTrue(taken.found)
        assertFalse(recipes.isMarked(pie.id))
        assertEquals(listOf(newer, older).map { it.id.value }, recipes.marked().first().map { it.recipeId })
        assertTrue(taken.putBack())

        assertEquals(pie, recipes.get(pie.id))
        assertTrue(recipes.isMarked(pie.id))
        assertEquals(marked, book.database.favourites().all().first())
        assertEquals(listOf(newer, pie, older).map { it.id.value }, recipes.marked().first().map { it.recipeId })
        assertEquals(filesOf(older, pie, newer), files())
    }

    @Test
    fun putBackWritesNothingOverARecipeOfTheSameIdentity() = runBlocking {
        val pie = recipe("Pie").also { recipes.save(it) }
        val taken = recipes.take(pie.id)!!
        val again = pie.copy(title = "Pie again")
        recipes.save(again)

        assertFalse(taken.putBack())

        assertEquals(again, recipes.get(pie.id))
    }

    @Test
    fun releasedTheFilesOfItsMediaNoOtherRecipeHoldsAreDeleted() = runBlocking {
        val pie = recipe("Pie").also { recipes.save(it) }
        val bread = recipe("Bread", minutesAgo = 1).also { recipes.save(it) }

        recipes.take(pie.id)!!.release()

        assertEquals(filesOf(bread), files())
        assertNull(recipes.get(pie.id))
    }

    @Test
    fun releasedAFoundRecipesFilesAreDeletedToo() = runBlocking {
        val pie = recipe("Pie").inState(RecipeImport.FOUND_STATE).also { recipes.addFound(it, now) }
        val bread = recipe("Bread", minutesAgo = 1).also { recipes.save(it) }

        RecipeImport(recipes, context.filesDir).take(pie.id)!!.release()

        assertEquals(filesOf(bread), files())
        assertTrue(recipes.marked().first().isEmpty())
    }

    // The process ends while Undo is offered: neither put back nor released, the recipe is gone, and the media sweep
    // at the book's next opening deletes the files of its media, which no recipe holds; another recipe's stay.
    @Test
    fun filesLeftByAProcessEndingBeforeTheNoticeWentAreSwept() = runBlocking {
        val pie = recipe("Pie").also { recipes.save(it) }
        val bread = recipe("Bread", minutesAgo = 1).also { recipes.save(it) }
        val written = System.currentTimeMillis()
        store.directory(BookName.Pool).listFiles()!!.forEach { assertTrue(it.setLastModified(written)) }

        recipes.take(pie.id)!!

        val sweep = MediaSweep(context.filesDir, now = { written + 11.minutes.inWholeMilliseconds })
        val swept = sweep.sweep(ActiveBook.Open(BookName.Pool, book.database)).map { it.name }.sorted()
        assertEquals(filesOf(pie), swept)
        assertEquals(filesOf(bread), files())
        assertNull(recipes.get(pie.id))
    }
}
