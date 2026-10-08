package app.recipefork.app.book

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.RecipeForkApplication
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.recipe.AccountId
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.IngredientGroup
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
    // - The old draft edited today comes first although it was created first: ordering by creation fails.
    // - The published recipe was created before the older untouched draft and last edited after it, so ordering
    //   it by its creation, or putting published recipes last, changes the order; its publication yesterday is a
    //   change of state and moves nothing.
    // - Two drafts were never edited: one created yesterday, after the published recipe's last edit, and one
    //   created five days ago, before it. Only their creation places them — the recent one before the published
    //   recipe, the older one after it — so putting never-edited drafts last, or first, or ordering them by
    //   anything but their creation, changes the order.
    @Test
    fun theBookOrdersOwnRecipesByTheirLastChangeOfContent() = runBlocking {
        fun ago(days: Long) = now.minus(Duration.ofDays(days))
        val editedDraft = Recipe.create(ago(30), null).edited(now) { it.copy(title = "old draft edited today") }
        val published = Recipe.create(ago(10), null).edited(ago(2)) { it.copy(title = "published yesterday") }
            .inState(RecipeState.PRIVATE).inState(RecipeState.PENDING).inState(RecipeState.PUBLISHED)
        val recentUntouched = Recipe.create(ago(1), null).copy(title = "untouched draft created yesterday")
        val olderUntouched = Recipe.create(ago(5), null).copy(title = "untouched draft created 5 days ago")
        listOf(recentUntouched, olderUntouched).forEach { assertEquals(it.createdAt, it.contentChangedAt) }
        assertTrue(published.contentChangedAt < recentUntouched.createdAt && recentUntouched.createdAt < editedDraft.contentChangedAt)
        assertTrue(published.createdAt < olderUntouched.createdAt && olderUntouched.createdAt < published.contentChangedAt)
        // Saved in an order that is neither the expected one nor its reverse.
        listOf(olderUntouched, editedDraft, recentUntouched, published).forEach { repository.save(it) }
        assertEquals(
            listOf(editedDraft.title, recentUntouched.title, published.title, olderUntouched.title),
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

    // DOM-022, DOM-023: a recipe's ingredient groups and notes are saved and read back; saved again with others, it
    // holds those alone; deleted, it leaves none behind.
    @Test
    fun groupsAndNotesSurviveTheBook() = runBlocking {
        val recipe = Recipe.create(now, owner = null).copy(
            title = "Pie",
            ingredients = listOf(
                Ingredient(0, 1, null, null, UnitType.EXACT, QuantityUnit.GRAM, Rational.of(300), note = "sifted"),
                Ingredient(1, null, null, "butter", UnitType.EXACT, QuantityUnit.GRAM, Rational.of(150), note = "cold"),
                Ingredient(2, 5, null, null, UnitType.TASTE_UNMEASURED, null, null),
            ),
            ingredientGroups = listOf(IngredientGroup("Dough", 0), IngredientGroup("Glaze", 2), IngredientGroup("Later", 3)),
        )
        repository.save(recipe)
        val read = repository.get(recipe.id)!!
        assertEquals(recipe, read)
        assertEquals(listOf("sifted", "cold", null), read.ingredients.map { it.note })
        assertEquals(recipe.ingredientGroups, read.ingredientGroups)

        val regrouped = recipe.copy(
            ingredients = recipe.ingredients.map { it.copy(note = null) },
            ingredientGroups = listOf(IngredientGroup("All", 1)),
        )
        repository.save(regrouped)
        assertEquals(regrouped, repository.get(recipe.id))

        repository.save(regrouped.copy(ingredientGroups = emptyList()))
        assertEquals(emptyList<IngredientGroup>(), repository.get(recipe.id)!!.ingredientGroups)

        repository.save(recipe)
        assertTrue(repository.delete(recipe.id))
        repository.save(recipe.copy(ingredientGroups = emptyList()))
        assertEquals(emptyList<IngredientGroup>(), repository.get(recipe.id)!!.ingredientGroups)
    }
}
