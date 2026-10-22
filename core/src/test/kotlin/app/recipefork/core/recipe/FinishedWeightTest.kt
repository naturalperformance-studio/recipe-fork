package app.recipefork.core.recipe

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * v0.44, T-318: a recipe may say what its finished dish weighs, in whole grams from 1 to 100 000, for the recipe at its
 * stated servings; it says nothing until given one. A fork carries it as it carries the servings, whoever makes it.
 */
class FinishedWeightTest {
    private val now = Instant.parse("2026-10-22T12:00:00Z")
    private val later = Instant.parse("2026-10-23T12:00:00Z")

    private val weighed = Recipe.create(now, null).copy(title = "Плов", servings = 2, finishedWeightGrams = 560)

    @Test
    fun `a recipe holds no finished weight unless it is given one`() {
        assertNull(Recipe.create(now, null).finishedWeightGrams)
        assertEquals(560, weighed.finishedWeightGrams)
    }

    @Test
    fun `a finished weight is whole grams from 1 to 100 000`() {
        assertEquals(1, RecipeBounds.MIN_FINISHED_WEIGHT_GRAMS)
        assertEquals(100_000, RecipeBounds.MAX_FINISHED_WEIGHT_GRAMS)
        listOf(1, 560, 100_000).forEach { grams ->
            assertTrue(RecipeBounds.holdsFinishedWeight(grams))
            assertEquals(grams, weighed.copy(finishedWeightGrams = grams).finishedWeightGrams)
        }
        listOf(0, -1, 100_001, Int.MAX_VALUE, Int.MIN_VALUE).forEach { grams ->
            assertFalse(RecipeBounds.holdsFinishedWeight(grams))
            assertFailsWith<IllegalArgumentException>("$grams g") { weighed.copy(finishedWeightGrams = grams) }
        }
    }

    @Test
    fun `an edit of anything else keeps the finished weight`() {
        val edited = weighed.edited(later) { it.copy(title = "Плов с курицей", servings = 4) }
        assertEquals(560, edited.finishedWeightGrams)
    }

    @Test
    fun `the owner's fork carries the finished weight with the servings`() {
        val fork = weighed.fork(later, null)
        assertEquals(2, fork.servings)
        assertEquals(560, fork.finishedWeightGrams)
    }

    @Test
    fun `a fork by anyone else carries the finished weight too`() {
        val found = weighed.copy(statedAuthor = "anna").inState(RecipeState.LINK_SHARED)
        val fork = found.fork(later, AccountId("reader"), byAuthor = false)
        assertEquals("", fork.description)
        assertEquals(2, fork.servings)
        assertEquals(560, fork.finishedWeightGrams)
    }

    @Test
    fun `a fork of a recipe with no finished weight has none`() {
        assertNull(Recipe.create(now, null).fork(later, null).finishedWeightGrams)
        assertNull(Recipe.create(now, null).fork(later, null, byAuthor = false).finishedWeightGrams)
    }
}
