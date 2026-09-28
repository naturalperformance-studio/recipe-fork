package app.recipefork.app.book

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.RecipeForkApplication
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.recipe.AccountId
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.IngredientReference
import app.recipefork.core.recipe.Media
import app.recipefork.core.recipe.MediaAttachment
import app.recipefork.core.recipe.MediaKind
import app.recipefork.core.recipe.MediaVisibility
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.RecipeLanguage
import app.recipefork.core.recipe.RecipeState
import app.recipefork.core.recipe.SentStatus
import app.recipefork.core.recipe.StatedParent
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.Timer
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.Rational
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant
import kotlin.time.Duration.Companion.minutes

@RunWith(AndroidJUnit4::class)
class BookDatabaseTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val database = Room.inMemoryDatabaseBuilder(context, BookDatabase::class.java).build()
    private val repository = RecipeRepository(ActiveBook(BookName.Pool, database))
    private val now = Instant.parse("2026-09-29T12:00:00Z")

    @After
    fun close() = database.close()

    @Test
    fun everyFieldOfARecipeAndItsPartsSurvivesTheBook() = runBlocking {
        val recipe = Recipe.create(now.minusSeconds(60), owner = AccountId("acc-1"), parent = RecipeId.mint()).copy(
            sent = SentStatus.SENT_WITH_CHANGES,
            statedAuthor = "calm green river",
            statedParent = StatedParent("Borscht", "quiet red hill"),
            title = "Soup",
            description = "A soup",
            tags = setOf(10, 11),
            language = RecipeLanguage.RU,
            servings = 4,
            ingredients = listOf(
                Ingredient(0, 1, "thigh", null, UnitType.EXACT, QuantityUnit.GRAM, Rational.of(1, 3), null, null),
                Ingredient(1, null, null, "wild garlic", UnitType.PIECE, QuantityUnit.PIECE, Rational.of(2), Rational.of(5), true),
                Ingredient(2, 5, null, null, UnitType.TASTE_UNMEASURED, null, null),
            ),
            steps = listOf(
                Step(0, "Boil the thigh", listOf(IngredientReference(0, 9)), Timer(90.minutes, "boil")),
                Step(1, "Serve"),
            ),
            media = listOf(
                Media("m1", AccountId("acc-1"), MediaKind.PHOTO, MediaAttachment.Cover, MediaVisibility.PRIVATE),
                Media("m2", null, MediaKind.VIDEO, MediaAttachment.ToStep(1), MediaVisibility.PUBLISHED),
            ),
            state = RecipeState.REJECTED,
            replacesParent = true,
            wasPublished = true,
            safetyFlag = true,
            contentChangedAt = now,
        )
        repository.save(recipe)
        assertEquals(recipe, repository.get(recipe.id))
    }

    // FLOW-038 (5): the book holds every own recipe's text; nothing is evicted for another.
    @Test
    fun hundredsOfOwnRecipesAreAllHeldInFull() = runBlocking {
        val recipes = (1..600).map { i ->
            Recipe.create(now.minusSeconds(i.toLong()), owner = null).copy(title = "Recipe $i", description = "Text of $i")
        }
        recipes.forEach { repository.save(it) }
        assertEquals(600, repository.count())
        assertEquals(600, repository.ownNewest(1_000).first().size)
        recipes.forEach { assertEquals(it, repository.get(it.id)) }
    }

    // FLOW-039 (1, 2, 5) as the book orders them.
    @Test
    fun theBookOrdersOwnRecipesByTheirLastChangeOfContent() = runBlocking {
        fun ago(days: Long) = now.minus(Duration.ofDays(days))
        val editedDraft = Recipe.create(ago(30), null).edited(now) { it.copy(title = "old draft edited today") }
        val published = Recipe.create(ago(5), null).edited(ago(4)) { it.copy(title = "published yesterday") }
            .inState(RecipeState.PRIVATE).inState(RecipeState.PENDING).inState(RecipeState.PUBLISHED)
        val untouched = Recipe.create(ago(3), null).copy(title = "untouched draft created 3 days ago")
        listOf(published, untouched, editedDraft).forEach { repository.save(it) }
        assertEquals(
            listOf(editedDraft.title, untouched.title, published.title),
            repository.ownNewest(10).first().map { it.title },
        )
    }

    @Test
    fun thePoolIsTheActiveBookAndLivesInItsOwnFile() = runBlocking {
        val container = ApplicationProvider.getApplicationContext<RecipeForkApplication>().container
        assertEquals(BookName.Pool, container.activeBook.open.value.name)
        container.recipes.save(Recipe.create(now, owner = null))
        assertEquals("book-pool.db", BookName.Pool.fileName)
        assertTrue(context.getDatabasePath("book-pool.db").exists())
        container.activeBook.database.close()
    }
}
