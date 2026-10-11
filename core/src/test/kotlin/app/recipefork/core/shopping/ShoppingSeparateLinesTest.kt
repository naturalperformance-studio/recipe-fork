package app.recipefork.core.shopping

import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.QuantityUnit.CUP
import app.recipefork.core.recipe.QuantityUnit.GRAM
import app.recipefork.core.recipe.QuantityUnit.PIECE
import app.recipefork.core.recipe.QuantityUnit.PINCH
import app.recipefork.core.recipe.UnitType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Criterion 4: rows to taste, free text, rows that cannot be scaled, and qualifiers. Criterion 5: each contribution is
 * taken as given.
 */
class ShoppingSeparateLinesTest {

    @Test
    fun `salt to taste from two recipes is one line to taste`() {
        val line = list(recipe(A, toTaste(SALT)), recipe(B, toTaste(SALT))).single()
        assertEquals(ShoppingName.Entry(SALT), line.name)
        assertEquals(ShoppingQuantity.ToTaste, line.quantity)
        assertEquals(listOf(A, B), line.recipes)
    }

    @Test
    fun `a measured salt line gains + to taste instead of a line to taste`() {
        val lines = list(
            recipe(A, toTaste(SALT)),
            recipe(B, row(FLOUR, r(100), GRAM), row(SALT, r(5), GRAM)),
        )
        assertEquals(listOf(SALT, FLOUR).map(ShoppingName::Entry), lines.map { it.name })
        assertEquals(ShoppingQuantity.Measured(amount(GRAM, r(5)), plusToTaste = true), lines[0].quantity)
        assertEquals(listOf(A, B), lines[0].recipes)
        assertEquals(ShoppingQuantity.Measured(amount(GRAM, r(100))), lines[1].quantity)
    }

    @Test
    fun `+ to taste marks only the entry's first measured line`() {
        // Flour has no measure for a pinch, so its pinches are a line of their own.
        val lines = list(
            recipe(A, row(FLOUR, r(1), PINCH)),
            recipe(B, row(FLOUR, r(200), GRAM), toTaste(FLOUR)),
        )
        assertEquals(2, lines.size)
        assertEquals(ShoppingQuantity.Measured(amount(PINCH, r(1)), plusToTaste = true), lines[0].quantity)
        assertEquals(listOf(A, B), lines[0].recipes)
        assertEquals(ShoppingQuantity.Measured(amount(GRAM, r(200))), lines[1].quantity)
        assertEquals(listOf(B), lines[1].recipes)
    }

    @Test
    fun `a merged line gains + to taste and stays approximate`() {
        val line = list(
            recipe(A, row(FLOUR, r(300), GRAM), toTaste(FLOUR)),
            recipe(B, row(FLOUR, r(2), CUP)),
        ).single()
        assertEquals(ShoppingQuantity.Measured(amount(GRAM, r(560)), plusToTaste = true), line.quantity)
        assertTrue(line.approximate)
    }

    @Test
    fun `free-text rows are each their own line, never merged`() {
        val lines = list(
            recipe(A, free("Onion", r(1), PIECE), free("Salt", null, null)),
            recipe(B, free("Onion", r(1), PIECE), free("Salt", null, null)),
        )
        assertEquals(
            listOf("Onion", "Salt", "Onion", "Salt").map(ShoppingName::FreeText),
            lines.map { it.name },
        )
        assertMeasured(PIECE, r(1), lines[0])
        assertEquals(ShoppingQuantity.ToTaste, lines[1].quantity)
        assertMeasured(PIECE, r(1), lines[2])
        assertEquals(ShoppingQuantity.ToTaste, lines[3].quantity)
        assertEquals(listOf(listOf(A), listOf(A), listOf(B), listOf(B)), lines.map { it.recipes })
    }

    @Test
    fun `a free-text row is not merged into an entry`() {
        val lines = list(recipe(A, row(FLOUR, r(100), GRAM)), recipe(B, free("Flour", r(100), GRAM)))
        assertEquals(listOf(ShoppingName.Entry(FLOUR), ShoppingName.FreeText("Flour")), lines.map { it.name })
        lines.forEach { assertMeasured(GRAM, r(100), it) }
    }

    @Test
    fun `rows that cannot be scaled are their own lines, as written, and add to nothing`() {
        val incomplete = row(FLOUR, null, GRAM)
        val offGrid = row(FLOUR, r(1, 3), GRAM)
        val lines = list(
            recipe(A, row(FLOUR, r(300), GRAM), incomplete),
            recipe(B, offGrid, row(FLOUR, r(200), GRAM)),
        )
        assertEquals(3, lines.size)
        assertMeasured(GRAM, r(500), lines[0])
        assertEquals(listOf(A, B), lines[0].recipes)
        assertEquals(ShoppingQuantity.AsWritten(incomplete), lines[1].quantity)
        assertEquals(ShoppingName.Entry(FLOUR), lines[1].name)
        assertEquals(listOf(A), lines[1].recipes)
        assertEquals(ShoppingQuantity.AsWritten(offGrid), lines[2].quantity)
        assertEquals(listOf(B), lines[2].recipes)
        lines.forEach { assertFalse(it.approximate) }
    }

    @Test
    fun `a free-text row that cannot be scaled is as written too`() {
        val piece = free("Lemon", r(1, 2), PIECE, divisible = false)
        val line = list(recipe(A, piece, requested = r(8))).single()
        assertEquals(ShoppingName.FreeText("Lemon"), line.name)
        assertEquals(ShoppingQuantity.AsWritten(piece), line.quantity)
    }

    @Test
    fun `an unscaled row to taste of an entry does not take + to taste`() {
        // A measured row that cannot be scaled is no measured line: the taste row stays a line of its own.
        val lines = list(recipe(A, row(SALT, null, GRAM)), recipe(B, toTaste(SALT)))
        assertIs<ShoppingQuantity.AsWritten>(lines[0].quantity)
        assertEquals(ShoppingQuantity.ToTaste, lines[1].quantity)
    }

    @Test
    fun `qualifiers do not split an entry's line`() {
        val line = list(
            recipe(A, row(FLOUR, r(100), GRAM, qualifier = "wholegrain")),
            recipe(B, row(FLOUR, r(200), GRAM)),
            recipe(C, row(FLOUR, r(50), GRAM, qualifier = "rye")),
        ).single()
        assertMeasured(GRAM, r(350), line)
        assertEquals(listOf(A, B, C), line.recipes)
    }

    @Test
    fun `a row naming nothing is left out`() {
        val nameless = Ingredient(0, null, unitType = UnitType.EXACT, unit = GRAM, quantity = r(100))
        val blank = free("  ", r(100), GRAM)
        assertEquals(listOf(ShoppingName.Entry(FLOUR)), list(recipe(A, nameless, blank, row(FLOUR, r(1), GRAM))).map { it.name })
    }

    @Test
    fun `the same recipe given twice counts twice`() {
        val twice = list(recipe(A, row(FLOUR, r(300), GRAM), toTaste(SALT)), recipe(A, row(FLOUR, r(300), GRAM), toTaste(SALT)))
        assertEquals(2, twice.size)
        assertMeasured(GRAM, r(600), twice[0])
        assertEquals(listOf(A), twice[0].recipes)
        assertEquals(ShoppingQuantity.ToTaste, twice[1].quantity)
        assertEquals(listOf(A), twice[1].recipes)
    }
}
