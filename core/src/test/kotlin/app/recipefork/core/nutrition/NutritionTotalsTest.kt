package app.recipefork.core.nutrition

import app.recipefork.core.recipe.QuantityUnit.GRAM
import app.recipefork.core.recipe.QuantityUnit.PIECE
import app.recipefork.core.recipe.QuantityUnit.TABLESPOON
import app.recipefork.core.scaling.Rational
import app.recipefork.core.scaling.ScaleFactor
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Criteria 1 and 5: the whole dish and one serving, the whole following the factor and one serving not; totals summed
 * exactly and rounded once, half up; each counted row's kcal rounded on its own; no result where nothing counts.
 */
class NutritionTotalsTest {

    /** 200 g flour and 2 eggs (100 g): 871 kcal, 32.6 g protein, 11.5 g fat, 152.7 g carbohydrates, authored for 4. */
    private val rows = arrayOf(row(FLOUR, r(200), GRAM), row(EGG, r(2), PIECE))

    @Test
    fun `a dish of dictionary entries in grams and pieces gives the whole dish and one serving`() {
        val result = assertNotNull(dish(*rows, servings = r(4)))
        assertEquals(totals(kcal = 870, protein = 33, fat = 12, carbs = 153), result.whole)
        assertEquals(totals(kcal = 220, protein = 8, fat = 3, carbs = 38), result.perServing)
    }

    @Test
    fun `the whole dish scales with the factor and one serving stays the same`() {
        val at4 = assertNotNull(dish(*rows, servings = r(4)))
        val at8 = assertNotNull(dish(*rows, factor = ScaleFactor.forServings(r(8), 4), servings = r(8)))
        val at6 = assertNotNull(dish(*rows, factor = ScaleFactor.forServings(r(6), 4), servings = r(6)))
        assertEquals(totals(kcal = 1740, protein = 65, fat = 23, carbs = 305), at8.whole)
        assertEquals(totals(kcal = 1310, protein = 49, fat = 17, carbs = 229), at6.whole)
        assertEquals(at4.perServing, at8.perServing)
        assertEquals(at4.perServing, at6.perServing)
    }

    @Test
    fun `no serving count gives the whole dish alone`() {
        val result = assertNotNull(dish(*rows))
        assertEquals(big(870), result.whole.kcal)
        assertNull(result.perServing)
    }

    @Test
    fun `one serving divides the exact whole, not the rounded one`() {
        // 260 g at 1 g protein per 100 g: 2.6 g whole, shown 3; one of 2 servings holds 1.3 g, shown 1, not 3 / 2.
        val result = assertNotNull(dish(row(TENTH, r(260), GRAM), servings = r(2)))
        assertEquals(big(3), result.whole.protein)
        assertEquals(big(1), result.perServing?.protein)
    }

    @Test
    fun `totals are summed exactly and rounded once, not row by row`() {
        // Three rows of 14 kcal each: 42 kcal reads 40; row by row they would read 30.
        val kcal = assertNotNull(dish(row(TENTH, r(14), GRAM), row(TENTH, r(14), GRAM), row(TENTH, r(14), GRAM)))
        assertEquals(big(40), kcal.whole.kcal)
        assertEquals(listOf(big(10), big(10), big(10)), kcal.counted.map { it.kcal })
        // Three rows of 0.4 g protein each: 1.2 g reads 1; row by row they would read 0.
        val grams = assertNotNull(dish(row(TENTH, r(40), GRAM), row(TENTH, r(40), GRAM), row(TENTH, r(40), GRAM)))
        assertEquals(big(1), grams.whole.protein)
        // Three rows of 0.6 g fat each: 1.8 g reads 2; row by row they would read 3.
        val more = assertNotNull(dish(row(TENTH, r(60), GRAM), row(TENTH, r(60), GRAM), row(TENTH, r(60), GRAM)))
        assertEquals(big(2), more.whole.fat)
    }

    @Test
    fun `kcal round to the nearest 10 and grams to whole grams, half up`() {
        val fifteen = assertNotNull(dish(row(TENTH, r(15), GRAM)))
        assertEquals(big(20), fifteen.whole.kcal)
        assertEquals(big(0), fifteen.whole.protein)
        val twentyFive = assertNotNull(dish(row(TENTH, r(25), GRAM)))
        assertEquals(big(30), twentyFive.whole.kcal)
        val fifty = assertNotNull(dish(row(TENTH, r(50), GRAM)))
        assertEquals(big(1), fifty.whole.protein)
        val twoFifty = assertNotNull(dish(row(TENTH, r(250), GRAM)))
        assertEquals(big(3), twoFifty.whole.protein)
        assertEquals(big(250), twoFifty.whole.kcal)
        val below = assertNotNull(dish(row(TENTH, r(149), GRAM)))
        assertEquals(big(1), below.whole.protein)
        assertEquals(big(150), below.whole.kcal)
    }

    @Test
    fun `the breakdown gives each counted row's kcal for the whole dish, in recipe order`() {
        val result = assertNotNull(dish(row(FLOUR, r(200), GRAM), toTaste(SALT), row(EGG, r(2), PIECE), factor = ScaleFactor.forServings(r(8), 4)))
        // 1456 kcal of flour and 286 kcal of eggs at twice the recipe.
        assertEquals(listOf(counted(0, 1460), counted(2, 290)), result.counted)
    }

    @Test
    fun `a breakdown row is rounded on its own, half up`() {
        val result = assertNotNull(dish(row(TENTH, r(15), GRAM), row(TENTH, r(14), GRAM)))
        assertEquals(listOf(counted(0, 20), counted(1, 10)), result.counted)
        assertEquals(big(30), result.whole.kcal)
    }

    @Test
    fun `a factor past any fixed-width number still counts exactly`() {
        // 1 kg at 100 kcal and 1 g protein per 100 g, authored for 4, asked for 4·10¹⁷ servings: a factor of 10¹⁷.
        val tenTo17 = BigInteger.TEN.pow(17)
        val factor = ScaleFactor.of(Rational.of(tenTo17))
        val result = assertNotNull(dish(row(TENTH, r(1_000), GRAM), factor = factor, servings = Rational.of(tenTo17 * BigInteger.valueOf(4))))
        assertEquals(BigInteger.TEN.pow(20), result.whole.kcal)
        assertEquals(BigInteger.TEN.pow(18), result.whole.protein)
        assertEquals(totals(kcal = 250, protein = 3, fat = 3, carbs = 3), result.perServing)
        assertEquals(listOf(CountedRow(0, BigInteger.TEN.pow(20))), result.counted)
    }

    @Test
    fun `a recipe where no row can be counted gives no result`() {
        assertNull(dish(toTaste(SALT), free("something", r(1), GRAM), row(NO_DATA, r(100), GRAM), row(BARE, r(1), TABLESPOON)))
        assertNull(dish())
    }

    @Test
    fun `a row that counts to nothing still counts`() {
        val result = assertNotNull(dish(row(SALT, r(5), GRAM)))
        assertEquals(totals(0, 0, 0, 0), result.whole)
        assertEquals(listOf(counted(0, 0)), result.counted)
    }
}
