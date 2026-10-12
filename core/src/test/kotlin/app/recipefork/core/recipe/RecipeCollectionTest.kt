package app.recipefork.core.recipe

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A collection (v0.36): named, at most 60 characters, trimmed; an own one is editable and an imported one frozen; its
 * order changes keep each recipe once.
 */
class RecipeCollectionTest {
    private val a = RecipeId("a")
    private val b = RecipeId("b")
    private val c = RecipeId("c")
    private val d = RecipeId("d")

    @Test
    fun aNameIsTrimmedAndRefusedWhenBlankOrTooLong() {
        assertEquals("New Year's menu", RecipeCollection.nameOf("  New Year's menu \n"))
        assertNull(RecipeCollection.nameOf(""))
        assertNull(RecipeCollection.nameOf(" \t "))
        assertEquals("я".repeat(60), RecipeCollection.nameOf(" " + "я".repeat(60) + " "))
        assertNull(RecipeCollection.nameOf("я".repeat(61)))
    }

    @Test
    fun anOwnCollectionIsEditableAndAnImportedOneFrozen() {
        val own = RecipeCollection(CollectionId("c1"), "Salads", CollectionOrigin.OWN, null, Instant.EPOCH, listOf(a))
        assertTrue(own.editable)
        assertFalse(own.copy(origin = CollectionOrigin.IMPORTED, statedAuthor = "anna").editable)
    }

    @Test
    fun mintedIdentitiesDiffer() {
        assertNotEquals(CollectionId.mint(), CollectionId.mint())
        assertEquals(36, CollectionId.mint().value.length)
    }

    @Test
    fun aReorderTakesTheAskedOrderOfWhatIsHeldAndKeepsTheRestAfter() {
        assertEquals(listOf(c, a, b), CollectionOrder.reordered(listOf(a, b, c), listOf(c, a, b)))
        // A recipe not held is not added; one asked twice is placed once; one left out keeps its place after the rest.
        assertEquals(listOf(c, a, b), CollectionOrder.reordered(listOf(a, b, c), listOf(d, c, c, a)))
        assertEquals(listOf(a, b), CollectionOrder.reordered(listOf(a, b), emptyList()))
    }

    @Test
    fun addingPutsWhatIsLackingAfterInItsOrder() {
        assertEquals(listOf(b, d, a, c), CollectionOrder.added(listOf(b, d), listOf(a, d, c, a)))
        assertEquals(listOf(a), CollectionOrder.added(listOf(a), listOf(a)))
    }

    @Test
    fun anImportTakesTheFilesOrderAndKeepsWhatTheFileLeavesOutAfter() {
        assertEquals(listOf(c, a, d, b), CollectionOrder.imported(listOf(a, b), listOf(c, a, d)))
        assertEquals(listOf(a, b), CollectionOrder.imported(emptyList(), listOf(a, b, a)))
    }
}
