package app.recipefork.core.nutrition

import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.QuantityUnit.GRAM
import app.recipefork.core.recipe.QuantityUnit.MILLILITRE
import app.recipefork.core.recipe.QuantityUnit.PIECE
import app.recipefork.core.recipe.QuantityUnit.TABLESPOON
import app.recipefork.core.recipe.QuantityUnit.TEASPOON
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Criterion 3: a row is left out for exactly one reason, the first of free text, not in the dictionary, to taste, no
 * quantity, no measure, no data; the left-out rows are listed in recipe order and the totals count the rest.
 */
class NutritionLeftOutTest {

    /** The reason [row] is left out, beside a row that counts so that there is a result. */
    private fun reasonOf(row: Ingredient): LeftOutReason {
        val result = assertNotNull(dish(row(TENTH, r(100), GRAM), row))
        return result.leftOut.single().reason
    }

    @Test
    fun `salt to taste, free text and a row without a quantity are listed in order, and the rest counts`() {
        val result = assertNotNull(
            dish(toTaste(SALT), row(FLOUR, r(200), GRAM), free("секретный соус", r(50), GRAM), row(EGG, null, PIECE)),
        )
        assertEquals(
            listOf(
                LeftOutRow(0, LeftOutReason.TO_TASTE),
                LeftOutRow(2, LeftOutReason.FREE_TEXT),
                LeftOutRow(3, LeftOutReason.NO_QUANTITY),
            ),
            result.leftOut,
        )
        assertEquals(NutritionValues(kcal = 730, protein = 20, fat = 2, carbs = 152), result.whole)
        assertEquals(listOf(CountedRow(1, 730)), result.counted)
    }

    @Test
    fun `free text is left out before anything else it lacks`() {
        assertEquals(LeftOutReason.FREE_TEXT, reasonOf(free("щепотка любви", null, null)))
        assertEquals(LeftOutReason.FREE_TEXT, reasonOf(free("мука", r(100), GRAM)))
    }

    @Test
    fun `an entry the dictionary does not hold is left out before to taste`() {
        assertEquals(LeftOutReason.NOT_IN_DICTIONARY, reasonOf(toTaste(UNKNOWN)))
        assertEquals(LeftOutReason.NOT_IN_DICTIONARY, reasonOf(row(UNKNOWN, r(100), GRAM)))
    }

    @Test
    fun `to taste is left out before no quantity`() {
        assertEquals(LeftOutReason.TO_TASTE, reasonOf(toTaste(BARE)))
        assertEquals(LeftOutReason.TO_TASTE, reasonOf(toTaste(SALT)))
    }

    @Test
    fun `no quantity is left out before no measure`() {
        assertEquals(LeftOutReason.NO_QUANTITY, reasonOf(row(BARE, null, TABLESPOON)))
        assertEquals(LeftOutReason.NO_QUANTITY, reasonOf(row(FLOUR, null, GRAM)))
    }

    @Test
    fun `no measure is left out before no data`() {
        assertEquals(LeftOutReason.NO_MEASURE, reasonOf(row(BARE, r(1), TABLESPOON)))
    }

    @Test
    fun `a unit the entry has no measure for is left out as no measure`() {
        assertEquals(LeftOutReason.NO_MEASURE, reasonOf(row(TENTH, r(2), PIECE)))
        assertEquals(LeftOutReason.NO_MEASURE, reasonOf(row(TENTH, r(100), MILLILITRE)))
        // Flour has a tablespoon measure and none for a teaspoon: nothing derives one from the other.
        assertEquals(LeftOutReason.NO_MEASURE, reasonOf(row(FLOUR, r(1), TEASPOON)))
    }

    @Test
    fun `an entry with no values is left out as no data`() {
        assertEquals(LeftOutReason.NO_DATA, reasonOf(row(NO_DATA, r(100), GRAM)))
        assertEquals(LeftOutReason.NO_DATA, reasonOf(row(NO_DATA, r(1), TABLESPOON)))
    }
}
