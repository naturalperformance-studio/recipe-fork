package app.recipefork.core.recipe

import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecipeTest {
    private val now = Instant.parse("2026-09-29T12:00:00Z")
    private fun daysAgo(days: Long) = now.minus(Duration.ofDays(days))

    @Test
    fun aNewRecipeIsLocalNeverSentWithADeviceMintedIdAndNoOwnerInThePool() {
        val a = Recipe.create(now, owner = null)
        val b = Recipe.create(now, owner = null)
        assertEquals(RecipeState.LOCAL, a.state)
        assertEquals(RecipeState.NEW, a.state)
        assertEquals(SentStatus.NEVER, a.sent)
        assertNull(a.owner)
        assertNull(a.parent)
        assertNotEquals(a.id, b.id)
        // A UUID, so the device can mint it without the server.
        java.util.UUID.fromString(a.id.value)
    }

    @Test
    fun thereAreEightStatesAndExactlyLocalAndPrivateAreDrafts() {
        assertEquals(8, RecipeState.entries.size)
        assertEquals(setOf(RecipeState.LOCAL, RecipeState.PRIVATE), RecipeState.entries.filter { it.isDraft }.toSet())
    }

    // FLOW-039 (1)
    @Test
    fun aDraftCreatedAMonthAgoAndEditedTodayComesBeforeARecipePublishedYesterday() {
        val draft = Recipe.create(daysAgo(30), owner = null).edited(now) { it.copy(title = "draft") }
        val published = Recipe.create(daysAgo(3), owner = null)
            .edited(daysAgo(2)) { it.copy(title = "published") }
            .inState(RecipeState.PRIVATE).inState(RecipeState.PENDING).inState(RecipeState.PUBLISHED)
        assertEquals(listOf(draft, published), listOf(published, draft).sortedWith(NewestOwnFirst))
    }

    // FLOW-039 (2)
    @Test
    fun aPublishedRecipeIsOrderedByItsLastEditBeforeItWasFrozen() {
        val lastEdit = daysAgo(10)
        val recipe = Recipe.create(daysAgo(20), owner = null)
            .edited(lastEdit) { it.copy(title = "soup") }
            .inState(RecipeState.PRIVATE)
            .inState(RecipeState.PENDING)
            .inState(RecipeState.PUBLISHED)
        assertEquals(lastEdit, recipe.newestKey)
        assertTrue(recipe.wasPublished)
        assertFailsWith<IllegalStateException> { recipe.edited(now) { it.copy(title = "changed") } }
    }

    // FLOW-039 (5)
    @Test
    fun aDraftNeverEditedIsOrderedByItsCreation() {
        val created = daysAgo(5)
        val untouched = Recipe.create(created, owner = null)
        assertEquals(created, untouched.newestKey)
        val older = Recipe.create(daysAgo(6), owner = null)
        assertEquals(listOf(untouched, older), listOf(older, untouched).sortedWith(NewestOwnFirst))
    }

    @Test
    fun anEditOfASentRecipeHoldsChangesNotSent() {
        val sent = Recipe.create(daysAgo(2), owner = AccountId("a")).inState(RecipeState.PRIVATE).copy(sent = SentStatus.SENT)
        assertEquals(SentStatus.SENT_WITH_CHANGES, sent.edited(now) { it.copy(title = "x") }.sent)
        val never = Recipe.create(daysAgo(2), owner = null)
        assertEquals(SentStatus.NEVER, never.edited(now) { it.copy(title = "x") }.sent)
    }

    @Test
    fun servingsAreOneOrMoreOrEmpty() {
        assertFailsWith<IllegalArgumentException> { Recipe.create(now, null).copy(servings = 0) }
        assertEquals(4, Recipe.create(now, null).copy(servings = 4).servings)
    }
}
