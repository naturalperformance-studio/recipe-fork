package app.recipefork.core.shopping

import app.recipefork.core.api.IngredientEntryDto
import app.recipefork.core.recipe.QuantityUnit.GRAM
import app.recipefork.core.recipe.QuantityUnit.KILOGRAM
import app.recipefork.core.recipe.QuantityUnit.MILLILITRE
import app.recipefork.core.recipe.QuantityUnit.PIECE
import app.recipefork.core.recipe.RecipeId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Criterion 1: what goes in, and what comes out — lines in the order of first appearance, named, with their recipes. */
class ShoppingLineShapeTest {

    private val lines = list(
        recipe(A, row(ONION, r(1), PIECE), row(FLOUR, r(300), GRAM), row(EGG, r(2), PIECE)),
        recipe(B, row(MILK, r(200), MILLILITRE), row(FLOUR, r(6, 5), KILOGRAM), free("Vanilla pod", r(1), PIECE), row(ONION, r(2), PIECE)),
    )

    @Test
    fun `lines stand in the order of their first row, recipe by recipe, not by entry`() {
        assertEquals(
            listOf(
                ShoppingName.Entry(ONION),
                ShoppingName.Entry(FLOUR),
                ShoppingName.Entry(EGG),
                ShoppingName.Entry(MILK),
                ShoppingName.FreeText("Vanilla pod"),
            ),
            lines.map { it.name },
        )
    }

    @Test
    fun `each line names the recipes it comes from, in the order they were given`() {
        assertEquals(listOf(A, B), lines[0].recipes)
        assertEquals(listOf(A, B), lines[1].recipes)
        assertEquals(listOf(A), lines[2].recipes)
        assertEquals(listOf(B), lines[3].recipes)
        assertEquals(listOf(B), lines[4].recipes)
        val reversed = list(recipe(B, row(FLOUR, r(100), GRAM)), recipe(A, row(FLOUR, r(100), GRAM)))
        assertEquals(listOf(B, A), reversed.single().recipes)
    }

    @Test
    fun `each line holds its quantity and unit, and whether it is approximate`() {
        assertMeasured(PIECE, r(3), lines[0])
        assertMeasured(GRAM, r(1500), lines[1])
        assertMeasured(PIECE, r(2), lines[2])
        assertMeasured(MILLILITRE, r(200), lines[3])
        assertMeasured(PIECE, r(1), lines[4])
        lines.forEach {
            assertFalse(it.approximate)
            assertTrue(it.counted.isEmpty())
        }
    }

    @Test
    fun `a line order follows the recipes as given`() {
        val swapped = list(
            recipe(B, row(MILK, r(200), MILLILITRE), row(FLOUR, r(100), GRAM)),
            recipe(A, row(ONION, r(1), PIECE), row(FLOUR, r(300), GRAM)),
        )
        assertEquals(listOf(MILK, FLOUR, ONION).map(ShoppingName::Entry), swapped.map { it.name })
    }

    @Test
    fun `a piece line is rounded by the entry's divisibility from its measures`() {
        // 3 pieces for 4 servings, at 2: 1½ exactly.
        val half = recipe(A, row(42, r(3), PIECE, divisible = true), requested = r(2))
        val indivisible = ShoppingTotals.of(listOf(half), mapOf(42L to EntryMeasures(divisible = false)))
        val divisible = ShoppingTotals.of(listOf(half), mapOf(42L to EntryMeasures(divisible = true)))
        assertMeasured(PIECE, r(2), indivisible.single())
        assertMeasured(PIECE, r(3, 2), divisible.single())
    }

    @Test
    fun `an entry's measures are read from its dictionary entry`() {
        val dto = IngredientEntryDto(7, "мука", "flour", divisible = true, factors = mapOf("g" to 1.0, "cup" to 130.0))
        assertEquals(EntryMeasures(true, mapOf("g" to 1.0, "cup" to 130.0)), EntryMeasures.of(dto))
        assertEquals(mapOf(7L to EntryMeasures(true, mapOf("g" to 1.0, "cup" to 130.0))), EntryMeasures.of(listOf(dto)))
    }

    @Test
    fun `free text keeps its words, trimmed`() {
        val line = list(recipe(RecipeId("x"), free("  Grandma's spice mix ", r(10), GRAM))).single()
        assertEquals(ShoppingName.FreeText("Grandma's spice mix"), line.name)
    }
}
