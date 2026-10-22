package app.recipefork.core.nutrition

import app.recipefork.core.recipe.QuantityUnit.CUBIC_METRE
import app.recipefork.core.recipe.QuantityUnit.CUP
import app.recipefork.core.recipe.QuantityUnit.FLUID_OUNCE
import app.recipefork.core.recipe.QuantityUnit.GRAM
import app.recipefork.core.recipe.QuantityUnit.KILOGRAM
import app.recipefork.core.recipe.QuantityUnit.LITRE
import app.recipefork.core.recipe.QuantityUnit.MILLILITRE
import app.recipefork.core.recipe.QuantityUnit.OUNCE
import app.recipefork.core.recipe.QuantityUnit.PIECE
import app.recipefork.core.recipe.QuantityUnit.PINCH
import app.recipefork.core.recipe.QuantityUnit.POUND
import app.recipefork.core.recipe.QuantityUnit.TABLESPOON
import app.recipefork.core.recipe.QuantityUnit.TEASPOON
import app.recipefork.core.recipe.QuantityUnit.TONNE
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Criterion 2: each row's grams — a mass exactly, a volume by the `ml` measure, pieces by the row's piece weight before
 * the `pc` measure, spoons, cups and pinches by their own measure. [PURE] holds 1 g of protein per gram.
 */
class NutritionGramsTest {

    @Test
    fun `a mass counts exactly, by its multiple of a gram`() {
        assertEquals(300, proteinOf(row(PURE, r(300), GRAM)))
        assertEquals(2_000, proteinOf(row(PURE, r(2), KILOGRAM)))
        assertEquals(1_000_000, proteinOf(row(PURE, r(1), TONNE)))
        // 28.349523125 g and 907.18474 g.
        assertEquals(28, proteinOf(row(PURE, r(1), OUNCE)))
        assertEquals(907, proteinOf(row(PURE, r(2), POUND)))
    }

    @Test
    fun `a mass ignores the entry's own gram factor`() {
        assertEquals(100, proteinOf(row(HEAVY_GRAM, r(100), GRAM)))
        assertEquals(1_000, proteinOf(row(HEAVY_GRAM, r(1), KILOGRAM)))
    }

    @Test
    fun `a volume counts by the entry's ml measure`() {
        // 0.5 g per ml.
        assertEquals(50, proteinOf(row(PURE, r(100), MILLILITRE)))
        assertEquals(1_000, proteinOf(row(PURE, r(2), LITRE)))
        assertEquals(500_000, proteinOf(row(PURE, r(1), CUBIC_METRE)))
        // 2 fl oz is 59.147059125 ml, 29.57 g.
        assertEquals(30, proteinOf(row(PURE, r(2), FLUID_OUNCE)))
    }

    @Test
    fun `pieces count by the entry's pc measure`() {
        assertEquals(120, proteinOf(row(PURE, r(3), PIECE)))
    }

    @Test
    fun `pieces count by the row's own piece weight before the entry's pc measure`() {
        assertEquals(165, proteinOf(row(PURE, r(3), PIECE, pieceWeight = r(55))))
    }

    @Test
    fun `a piece weight counts a row whose entry has no pc measure`() {
        val result = assertNotNull(dish(row(TENTH, r(2), PIECE, pieceWeight = r(150))))
        assertEquals(300, result.whole.kcal)
    }

    @Test
    fun `spoons, cups and pinches count by the entry's measure for that unit`() {
        assertEquals(10, proteinOf(row(PURE, r(2), TEASPOON)))
        assertEquals(30, proteinOf(row(PURE, r(2), TABLESPOON)))
        assertEquals(300, proteinOf(row(PURE, r(3, 2), CUP)))
        assertEquals(2, proteinOf(row(PURE, r(4), PINCH)))
    }

    @Test
    fun `two eggs and a tablespoon of oil count through their piece and spoon measures`() {
        val result = assertNotNull(dish(row(EGG, r(2), PIECE), row(OIL, r(1), TABLESPOON)))
        // 100 g of egg: 143 kcal, 9.5 g fat; 13.6 g of oil: 120.224 kcal, 13.6 g fat.
        assertEquals(NutritionValues(kcal = 260, protein = 13, fat = 23, carbs = 1), result.whole)
        assertEquals(listOf(CountedRow(0, 140), CountedRow(1, 120)), result.counted)
    }
}
