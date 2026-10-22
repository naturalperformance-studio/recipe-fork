package app.recipefork.core.nutrition

import app.recipefork.core.recipe.QuantityUnit.GRAM
import app.recipefork.core.recipe.QuantityUnit.PIECE
import app.recipefork.core.scaling.Rational
import app.recipefork.core.scaling.ScaleFactor
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * v0.44, T-320: 100 g of the finished dish — the exact whole × 100 ÷ (the finished weight × the factor), rounded once
 * as the other totals, the same at any scale — and what the dish weighs at the scale, in whole grams.
 */
class NutritionPer100gTest {

    /** 200 g flour and 2 eggs (100 g): 871 kcal, 32.6 g protein, 11.5 g fat, 152.7 g carbohydrates, authored for 4. */
    private val rows = arrayOf(row(FLOUR, r(200), GRAM), row(EGG, r(2), PIECE))

    @Test
    fun `a finished weight gives 100 g of the finished dish and the weight it rests on`() {
        val result = assertNotNull(dish(*rows, servings = r(4), finishedWeight = 250))
        // 348.4 kcal, 13.04 g, 4.6 g, 61.08 g.
        assertEquals(totals(kcal = 350, protein = 13, fat = 5, carbs = 61), result.per100g)
        assertEquals(big(250), result.finishedWeight)
        assertEquals(totals(kcal = 870, protein = 33, fat = 12, carbs = 153), result.whole)
        assertEquals(totals(kcal = 220, protein = 8, fat = 3, carbs = 38), result.perServing)
    }

    @Test
    fun `100 g stays the same as the servings change, and the weight follows them`() {
        val at4 = assertNotNull(dish(*rows, servings = r(4), finishedWeight = 250))
        val at8 = assertNotNull(dish(*rows, factor = ScaleFactor.forServings(r(8), 4), servings = r(8), finishedWeight = 250))
        val at6 = assertNotNull(dish(*rows, factor = ScaleFactor.forServings(r(6), 4), servings = r(6), finishedWeight = 250))
        val atThird = assertNotNull(dish(*rows, factor = ScaleFactor.forServings(r(4, 3), 4), servings = r(4, 3), finishedWeight = 250))
        assertEquals(at4.per100g, at8.per100g)
        assertEquals(at4.per100g, at6.per100g)
        assertEquals(at4.per100g, atThird.per100g)
        assertEquals(big(500), at8.finishedWeight)
        assertEquals(big(375), at6.finishedWeight)
        // 250 / 3 = 83.3 g.
        assertEquals(big(83), atThird.finishedWeight)
    }

    @Test
    fun `100 g divides the exact whole, rounded once, not the rounded whole`() {
        // 15 g at 100 kcal per 100 g: 15 kcal, shown 20; 10 g of finished dish make 150 kcal per 100 g, not 200.
        val kcal = assertNotNull(dish(row(TENTH, r(15), GRAM), finishedWeight = 10))
        assertEquals(big(20), kcal.whole.kcal)
        assertEquals(big(150), kcal.per100g?.kcal)
        // 260 g at 1 g protein per 100 g: 2.6 g, shown 3; 200 g of finished dish make 1.3 g per 100 g, shown 1, not 1.5.
        val protein = assertNotNull(dish(row(TENTH, r(260), GRAM), finishedWeight = 200))
        assertEquals(big(3), protein.whole.protein)
        assertEquals(big(1), protein.per100g?.protein)
    }

    @Test
    fun `100 g rounds as the other totals, kcal to 10 and grams whole, half up`() {
        // 100 g at 100 kcal and 1 g per 100 g, the dish weighing 400 g: 25 kcal reads 30, 0.25 g reads 0.
        val quarter = assertNotNull(dish(row(TENTH, r(100), GRAM), finishedWeight = 400))
        assertEquals(totals(kcal = 30, protein = 0, fat = 0, carbs = 0), quarter.per100g)
        // Weighing 200 g: 50 kcal, 0.5 g reads 1.
        val half = assertNotNull(dish(row(TENTH, r(100), GRAM), finishedWeight = 200))
        assertEquals(totals(kcal = 50, protein = 1, fat = 1, carbs = 1), half.per100g)
    }

    @Test
    fun `the weight at the scale is rounded to the nearest whole gram, half up`() {
        val half = assertNotNull(dish(*rows, factor = ScaleFactor.of(r(1, 2)), finishedWeight = 5))
        assertEquals(big(3), half.finishedWeight)
        val below = assertNotNull(dish(*rows, factor = ScaleFactor.of(r(1, 3)), finishedWeight = 4))
        assertEquals(big(1), below.finishedWeight)
    }

    @Test
    fun `a recipe with no serving count gets 100 g from its weight too`() {
        val result = assertNotNull(dish(*rows, finishedWeight = 250))
        assertNull(result.perServing)
        assertEquals(totals(kcal = 350, protein = 13, fat = 5, carbs = 61), result.per100g)
    }

    @Test
    fun `no finished weight gives no 100 g and no weight`() {
        val result = assertNotNull(dish(*rows, servings = r(4)))
        assertNull(result.per100g)
        assertNull(result.finishedWeight)
    }

    @Test
    fun `a weight does not make a count where no row can be counted`() {
        assertNull(dish(toTaste(SALT), finishedWeight = 250))
    }

    @Test
    fun `a factor past any fixed-width number still counts 100 g exactly`() {
        val tenTo17 = BigInteger.TEN.pow(17)
        val result = assertNotNull(dish(row(TENTH, r(1_000), GRAM), factor = ScaleFactor.of(Rational.of(tenTo17)), finishedWeight = 800))
        // 1 000 kcal and 10 g in 800 g: 125 kcal, 1.25 g.
        assertEquals(totals(kcal = 130, protein = 1, fat = 1, carbs = 1), result.per100g)
        assertEquals(tenTo17 * BigInteger.valueOf(800), result.finishedWeight)
    }

    @Test
    fun `a finished weight is above zero`() {
        assertFailsWith<IllegalArgumentException> { dish(*rows, finishedWeight = 0) }
    }
}
