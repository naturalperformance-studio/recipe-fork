package app.recipefork.app.card

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.Media
import app.recipefork.core.recipe.MediaAttachment
import app.recipefork.core.recipe.MediaKind
import app.recipefork.core.recipe.MediaVisibility
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeState
import app.recipefork.core.recipe.SentStatus
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.recipe.fork
import app.recipefork.core.scaling.Rational
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * How the book knows a fork nothing has changed since it was made (FLOW-015): an edit of any part moves the fork's
 * content time past its creation, so an unchanged fork is one whose two times are still equal. Several forks offer an
 * unchanged one; only changed ones, or none, make a fork; a fork never sent counts as one that was.
 */
@RunWith(AndroidJUnit4::class)
class HeldForkTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val active = ActiveBook(BookName.Pool, book.database)
    private val recipes = RecipeRepository(active)
    private val created = Instant.parse("2026-09-20T09:00:00Z")
    private val forked = Instant.parse("2026-10-02T12:00:00Z")
    private val later = Instant.parse("2026-10-02T12:05:00Z")
    private val again = Instant.parse("2026-10-02T12:10:00Z")

    @After
    fun close() = book.close()

    private val parent = Recipe.create(created, null).copy(
        title = "Borscht",
        description = "My grandmother's way.",
        servings = 4,
        ingredients = listOf(
            Ingredient(0, null, freeTextName = "Beet", unitType = UnitType.PIECE, unit = QuantityUnit.PIECE, quantity = Rational.of(2), divisible = false),
        ),
        steps = listOf(Step(0, "Boil everything.")),
    ).inState(RecipeState.LOCAL)

    private fun save(recipe: Recipe) = runBlocking { recipes.save(recipe) }

    private fun held() = runBlocking { recipes.unchangedForkOf(parent.id) }

    private fun fork(at: Instant = forked) = parent.fork(at, null).also(::save)

    @Test
    fun noForkHeldMakesAForkSilently() {
        save(parent)
        assertNull(held())
        val asked = runBlocking { forkUnlessHeld(recipes, active, parent.id, forked, store = null) }
        assertTrue(asked is ForkAsked.Made)
        assertEquals(2, runBlocking { recipes.count() })
    }

    @Test
    fun anUnchangedForkIsOfferedAndNoneIsMade() {
        save(parent)
        val first = fork()
        assertEquals(first.id, held())
        val asked = runBlocking { forkUnlessHeld(recipes, active, parent.id, again, store = null) }
        assertEquals(ForkAsked.Held(first.id), asked)
        assertEquals(2, runBlocking { recipes.count() })
        // Asking for another makes one regardless.
        val another = runBlocking { forkRecipe(recipes, active, parent.id, again, store = null) }
        assertNotEquals(first.id, another)
        assertEquals(parent.id, runBlocking { recipes.get(another!!) }?.parent)
        assertEquals(3, runBlocking { recipes.count() })
    }

    // FLOW-015 (3): a change of any part — title, tags, servings, ingredients, steps, description or media.
    @Test
    fun aForkChangedInAnyPartIsNotOffered() {
        save(parent)
        val changes: List<(Recipe) -> Recipe> = listOf(
            { it.copy(title = "Green borscht") },
            { it.copy(tags = setOf(10L)) },
            { it.copy(servings = 6) },
            { it.copy(ingredients = it.ingredients.map { line -> line.copy(quantity = Rational.of(3)) }) },
            { it.copy(steps = listOf(Step(0, "Boil it all."))) },
            { it.copy(description = "") },
            { it.copy(media = listOf(Media("cover-1", null, MediaKind.PHOTO, MediaAttachment.Cover, MediaVisibility.PRIVATE))) },
        )
        changes.forEach { change ->
            val changed = fork().edited(later, change).also(::save)
            assertNull("changed: $changed", held())
            val asked = runBlocking { forkUnlessHeld(recipes, active, parent.id, again, store = null) }
            assertTrue(asked is ForkAsked.Made)
            // The fork just made is itself unchanged; it is taken out so the next change is tried alone.
            runBlocking { recipes.delete((asked as ForkAsked.Made).fork) }
            runBlocking { recipes.delete(changed.id) }
        }
    }

    // FLOW-015 (4): of several forks, the unchanged one is offered.
    @Test
    fun ofSeveralForksTheUnchangedOneIsOffered() {
        save(parent)
        fork().edited(later) { it.copy(title = "Green borscht") }.also(::save)
        val untouched = fork(Instant.parse("2026-10-02T12:01:00Z"))
        fork(Instant.parse("2026-10-02T12:02:00Z")).edited(later) { it.copy(servings = 2) }.also(::save)
        assertEquals(untouched.id, held())
    }

    // FLOW-015 (6): an unchanged fork never sent counts exactly as one the server holds.
    @Test
    fun anUnchangedForkCountsWhetherOrNotItWasSent() {
        save(parent)
        val never = fork()
        assertEquals(SentStatus.NEVER, never.sent)
        assertEquals(never.id, held())
        save(never.copy(sent = SentStatus.SENT))
        assertEquals(never.id, held())
    }

    // FLOW-015 (4): of several unchanged forks, the newest is offered.
    @Test
    fun ofSeveralUnchangedForksTheNewestIsOffered() {
        save(parent)
        fork(Instant.parse("2026-10-02T12:01:00Z"))
        val newest = fork(Instant.parse("2026-10-02T12:03:00Z"))
        fork(Instant.parse("2026-10-02T12:02:00Z"))
        assertEquals(newest.id, held())
    }

    // A found recipe — imported from a file — that is someone's unchanged fork of this recipe is not the reader's fork.
    @Test
    fun aFoundRecipeIsNotOfferedAsTheReadersFork() {
        save(parent)
        val found = parent.fork(forked, null, byAuthor = false)
        assertEquals(found.createdAt, found.contentChangedAt)
        assertTrue(runBlocking { recipes.addFound(found, forked) })
        assertNull(held())
        val asked = runBlocking { forkUnlessHeld(recipes, active, parent.id, again, store = null) }
        assertTrue(asked is ForkAsked.Made)
        assertEquals(3, runBlocking { recipes.count() })
    }

    // A fork of another recipe is no fork of this one.
    @Test
    fun aForkOfAnotherRecipeIsNotOffered() {
        save(parent)
        val other = Recipe.create(created, null).copy(title = "Soup").inState(RecipeState.LOCAL).also(::save)
        save(other.fork(forked, null))
        assertNull(held())
    }
}
