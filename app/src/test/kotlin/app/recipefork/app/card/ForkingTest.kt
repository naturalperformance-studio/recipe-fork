package app.recipefork.app.card

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookDatabase
import app.recipefork.app.book.BookName
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.recipe.AccountId
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.Media
import app.recipefork.core.recipe.MediaAttachment
import app.recipefork.core.recipe.MediaKind
import app.recipefork.core.recipe.MediaVisibility
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.RecipeState
import app.recipefork.core.recipe.SentStatus
import app.recipefork.core.recipe.StatedParent
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.Timer
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.recipe.IngredientReference
import app.recipefork.core.scaling.Rational
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import kotlin.time.Duration.Companion.minutes

/**
 * Forking writes the fork to the active book beside its parent (FORK-001 (7)), and both are there, unchanged by
 * each other, after the book is reopened from its file.
 */
@RunWith(AndroidJUnit4::class)
class ForkingTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val file = "book-fork-test.db"
    private val created = Instant.parse("2026-09-20T09:00:00Z")
    private val now = Instant.parse("2026-10-02T12:00:00Z")
    private val later = Instant.parse("2026-10-02T12:05:00Z")
    private val open = mutableListOf<BookDatabase>()

    private fun openBook(): BookDatabase =
        Room.databaseBuilder(context, BookDatabase::class.java, file).allowMainThreadQueries().build().also { open += it }

    @After
    fun close() {
        open.forEach { it.close() }
        context.deleteDatabase(file)
    }

    private val parent = Recipe.create(created, null).copy(
        title = "Borscht",
        description = "My grandmother's way.",
        tags = setOf(3L),
        servings = 4,
        ingredients = listOf(
            Ingredient(0, 1, qualifier = "young", unitType = UnitType.EXACT, unit = QuantityUnit.GRAM, quantity = Rational.of(500)),
            Ingredient(1, null, freeTextName = "Beet", unitType = UnitType.PIECE, unit = QuantityUnit.PIECE, quantity = Rational.of(3, 2), pieceWeight = Rational.of(200), divisible = true),
        ),
        steps = listOf(
            Step(0, "Boil the beet", listOf(IngredientReference(1, 9)), Timer(40.minutes, "Beet")),
            Step(1, "Season", timer = Timer(5.minutes)),
        ),
        media = listOf(Media("cover", null, MediaKind.PHOTO, MediaAttachment.Cover, MediaVisibility.PRIVATE)),
    )

    @Test
    fun theForkAndItsParentAreBothInTheBookAfterItIsReopenedFromDisk() = runBlocking {
        val first = openBook()
        val recipes = RecipeRepository(ActiveBook(BookName.Pool, first))
        recipes.save(parent)
        val forkId = requireNotNull(forkRecipe(recipes, ActiveBook(BookName.Pool, first), parent.id, now))
        assertNotEquals(parent.id, forkId)
        val fork = requireNotNull(recipes.get(forkId))
        // An edit of the fork, as the editor makes one: a changed ingredient.
        val edited = fork.edited(later) { r -> r.copy(ingredients = r.ingredients.map { it.copy(quantity = Rational.of(750)) }.take(1) + r.ingredients.drop(1)) }
        recipes.save(edited)
        first.close()

        val reopened = RecipeRepository(ActiveBook(BookName.Pool, openBook()))
        assertEquals(parent, reopened.get(parent.id))
        val stored = requireNotNull(reopened.get(forkId))
        assertEquals(edited, stored)
        assertEquals(parent.id, stored.parent)
        assertEquals(Rational.of(750), stored.ingredients[0].quantity)
        assertEquals(parent.ingredients.drop(1), stored.ingredients.drop(1))
        assertEquals(parent.steps, stored.steps)
        assertEquals(parent.description, stored.description)
        assertEquals(parent.media.map { it.kind to it.attachment }, stored.media.map { it.kind to it.attachment })
        assertEquals(listOf(forkId.value, parent.id.value), reopened.ownNewest(10).first().map { it.recipeId })
        assertEquals(listOf("Borscht", "Borscht"), reopened.ownNewest(10).first().map { it.title })
    }

    // The fork is written to the active book with that book's owner: the pool's has none, an account's has it.
    @Test
    fun theForkBelongsToTheActiveBook() = runBlocking {
        val database = openBook()
        val pool = ActiveBook(BookName.Pool, database)
        val recipes = RecipeRepository(pool)
        recipes.save(parent.inState(RecipeState.PRIVATE).copy(sent = SentStatus.SENT))
        val inPool = requireNotNull(recipes.get(requireNotNull(forkRecipe(recipes, pool, parent.id, now))))
        assertNull(inPool.owner)
        assertEquals(RecipeState.LOCAL, inPool.state)
        assertEquals(SentStatus.NEVER, inPool.sent)
        val account = ActiveBook(BookName.OfAccount("acc-1"), database)
        val ofAccount = requireNotNull(recipes.get(requireNotNull(forkRecipe(RecipeRepository(account), account, parent.id, now))))
        assertEquals(AccountId("acc-1"), ofAccount.owner)
    }

    // A recipe the book no longer holds forks into nothing.
    @Test
    fun aRecipeTheBookDoesNotHoldForksIntoNothing() = runBlocking {
        val database = openBook()
        val book = ActiveBook(BookName.Pool, database)
        val recipes = RecipeRepository(book)
        assertNull(forkRecipe(recipes, book, RecipeId("gone"), now))
        assertEquals(0, recipes.count())
    }

    // FORK-002, FLOW-065 (1, 2): a found recipe forks into an editable recipe among the own ones, naming it as its parent,
    // with its ingredients, steps, references and timers and none of its description and media; the found recipe is
    // still found, its media untouched. The fork keeps its parent's title and stated author, after reopening too.
    @Test
    fun aFoundRecipeForksIntoAnOwnRecipeWithNoDescriptionAndNoMedia() = runBlocking {
        val first = openBook()
        val book = ActiveBook(BookName.Pool, first)
        val recipes = RecipeRepository(book)
        val found = parent.copy(id = RecipeId("found-1"), statedAuthor = "anna").inState(RecipeState.LINK_SHARED)
        recipes.addFound(found, created)
        val forkId = requireNotNull(forkRecipe(recipes, book, found.id, now))
        first.close()

        val reopened = RecipeRepository(ActiveBook(BookName.Pool, openBook()))
        val fork = requireNotNull(reopened.get(forkId))
        assertEquals(found.id, fork.parent)
        assertEquals(RecipeState.LOCAL, fork.state)
        assertEquals(true, fork.state.editable)
        assertNull(fork.owner)
        assertEquals("", fork.description)
        assertEquals(emptyList<Media>(), fork.media)
        assertEquals(found.ingredients, fork.ingredients)
        assertEquals(found.steps, fork.steps)
        assertEquals(Timer(40.minutes, "Beet"), fork.steps[0].timer)
        assertEquals(listOf(IngredientReference(1, 9)), fork.steps[0].ingredientReferences)
        assertNull(fork.statedAuthor)
        assertEquals(StatedParent("Borscht", "anna"), fork.statedParent)
        assertEquals(listOf(forkId.value), reopened.ownNewest(10).first().map { it.recipeId })
        assertEquals(found, reopened.get(found.id))
        assertEquals(listOf(found.id.value), reopened.marked().first().map { it.recipeId })
    }
}
