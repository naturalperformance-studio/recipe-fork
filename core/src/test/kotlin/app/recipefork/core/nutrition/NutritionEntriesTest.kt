package app.recipefork.core.nutrition

import app.recipefork.core.api.IngredientEntryDto
import app.recipefork.core.recipe.QuantityUnit.GRAM
import app.recipefork.core.recipe.QuantityUnit.PIECE
import app.recipefork.core.scaling.ScaleFactor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Criterion 4: a child entry without its own values takes its parent's, unless it is marked as having none (T-294);
 * and the entries as the dictionary holds them.
 */
class NutritionEntriesTest {

    @Test
    fun `a child without its own values takes its parent's`() {
        // Two wings at the wing's own 90 g a piece, by chicken's values: 360 kcal, 36 g protein, 23.4 g fat.
        val result = assertNotNull(dish(row(WINGS, r(2), PIECE)))
        assertEquals(totals(kcal = 360, protein = 36, fat = 23, carbs = 0), result.whole)
    }

    @Test
    fun `a child with its own values keeps them`() {
        val result = assertNotNull(dish(row(THIGHS, r(100), GRAM)))
        assertEquals(totals(kcal = 180, protein = 18, fat = 10, carbs = 0), result.whole)
    }

    @Test
    fun `a child whose parent has no values, or is not in the dictionary, has no data`() {
        val result = assertNotNull(dish(row(TENTH, r(100), GRAM), row(NO_DATA_CHILD, r(100), GRAM), row(ORPHAN, r(100), GRAM)))
        assertEquals(listOf(LeftOutRow(1, LeftOutReason.NO_DATA), LeftOutRow(2, LeftOutReason.NO_DATA)), result.leftOut)
    }

    @Test
    fun `a child takes its parent's values, not its parent's measures`() {
        val result = assertNotNull(dish(row(TENTH, r(100), GRAM), row(PIECELESS_CHILD, r(1), PIECE)))
        assertEquals(listOf(LeftOutRow(1, LeftOutReason.NO_MEASURE)), result.leftOut)
    }

    // T-294: a child of another form, known to have no values of its own, does not take its parent's.
    @Test
    fun `a child marked as having no values of its own is left out as no data, not counted by its parent's`() {
        val result = assertNotNull(dish(row(TENTH, r(100), GRAM), row(NO_OWN_DATA, r(100), GRAM), row(WINGS, r(2), PIECE)))
        assertEquals(listOf(LeftOutRow(1, LeftOutReason.NO_DATA)), result.leftOut)
        assertEquals(listOf(counted(0, 100), counted(2, 360)), result.counted)
    }

    @Test
    fun `the entries are read from the dictionary's entries, a child's parent among them`() {
        val dictionary = listOf(
            IngredientEntryDto(1, "курица", "chicken", nutrition = values(200.0, 20.0, 13.0, 0.0)),
            IngredientEntryDto(2, "крылья", "wings", parentId = 1, divisible = false, factors = mapOf("pc" to 90.0)),
            IngredientEntryDto(3, "куриные кости", "chicken bones", parentId = 1, factors = mapOf("g" to 1.0), noNutrition = true),
        )
        val entries = NutritionEntries.of(dictionary)
        assertEquals(NutritionEntry(parentId = 1, factors = mapOf("pc" to 90.0)), entries.entry(2))
        assertEquals(NutritionEntry(parentId = 1, factors = mapOf("g" to 1.0), noNutrition = true), entries.entry(3))
        assertNull(entries.entry(4))
        val result = assertNotNull(Nutrition.of(listOf(row(2, r(2), PIECE), row(3, r(100), GRAM).copy(position = 1)), ScaleFactor.ONE, entries))
        assertEquals(big(360), result.whole.kcal)
        assertEquals(listOf(LeftOutRow(1, LeftOutReason.NO_DATA)), result.leftOut)
    }
}
