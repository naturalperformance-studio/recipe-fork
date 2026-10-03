package app.recipefork.core.search

import kotlin.test.Test
import kotlin.test.assertEquals

/** CAT-006: a filter on an entry matches it and the entries below it, never the entries above it. */
class IngredientFamilyTest {
    // курица (1) ← крылышки (2), филе бедра (3) ← (a deeper level, should the dictionary grow one) 4; говядина (5).
    private val parents = mapOf(1L to null, 2L to 1L, 3L to 1L, 4L to 3L, 5L to null)

    @Test
    fun aParentMatchesItselfAndEveryEntryBelowIt() {
        assertEquals(setOf(1L, 2L, 3L, 4L), ingredientFamily(1, parents))
    }

    @Test
    fun aChildMatchesOnlyItselfAndWhatIsBelowIt() {
        assertEquals(setOf(2L), ingredientFamily(2, parents))
        assertEquals(setOf(3L, 4L), ingredientFamily(3, parents))
        assertEquals(setOf(5L), ingredientFamily(5, parents))
    }

    @Test
    fun anEntryTheDictionaryDoesNotHoldMatchesItselfOnly() {
        assertEquals(setOf(9L), ingredientFamily(9, parents))
        assertEquals(setOf(1L), ingredientFamily(1, emptyMap()))
    }
}
