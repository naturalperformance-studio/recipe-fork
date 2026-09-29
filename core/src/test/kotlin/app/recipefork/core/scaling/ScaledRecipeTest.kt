package app.recipefork.core.scaling

import app.recipefork.core.recipe.QuantityUnit.GRAM
import app.recipefork.core.recipe.QuantityUnit.KILOGRAM
import app.recipefork.core.recipe.QuantityUnit.PIECE
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun q(n: Long, d: Long = 1) = Rational.of(n, d)

/** A recipe authored for 4 servings whose ingredient 0 is [grams] g, with a 350 g ingredient 1. */
private fun fourServingsWith(grams: Long) = ScaledRecipe.of(
    authoredServings = 4,
    ingredients = listOf(AuthoredQuantity(GRAM, q(grams)), AuthoredQuantity(GRAM, q(350))),
)

/** The serving count shown when [fixed] g of ingredient 0, authored at [authored] g for 4 servings, is fixed. */
private fun countWhenFixing(fixed: Long, authored: Long): ServingCount =
    fourServingsWith(authored).fix(0, q(fixed), GRAM).servingCount

private fun assertApproximately(expected: Rational, count: ServingCount) {
    assertEquals(expected, count.value)
    assertTrue(count.approximate, "a count computed from a fixed ingredient is approximate")
}

class Scale002FixedIngredientTest {
    private val fixed = fourServingsWith(500).fix(0, q(300), GRAM)

    @Test
    fun `fixing 300 g of chicken authored at 500 g shows exactly 300 g on the chicken line`() {
        val chicken = fixed.lines[0]
        assertEquals(GRAM, chicken.quantity.unit)
        assertEquals(q(300), chicken.quantity.value)
        assertEquals(Rounding.NONE, chicken.quantity.rounding)
        assertTrue(chicken.fixed)
        assertNull(chicken.shortfall)
        assertEquals(ScaleFactor.of(q(3, 5)), fixed.factor)
    }

    @Test
    fun `fixing 0_3 kg is the same fixing as 300 g`() {
        assertEquals(fixed.lines, fourServingsWith(500).fix(0, q(3, 10), KILOGRAM).lines)
    }

    @Test
    fun `another 350 g ingredient shows 210 g, from 0_6 and not from the rounded count of 2`() {
        assertEquals(q(2), fixed.servingCount.value) // the rounded count: 2.4 → 2
        val other = fixed.lines[1]
        assertEquals(q(210), other.quantity.value)
        assertTrue(other.quantity.derived)
        assertFalse(other.fixed)
        // From the rounded count, 350 g × 2/4 would have been 175 g.
        assertEquals(q(175), Scaling.display(AuthoredQuantity(GRAM, q(350)), ScaleFactor.forServings(q(2), 4)).value)
    }
}

class Scale006MostRecentFixingSetsTheScaleTest {
    private val chickenAndEggs = ScaledRecipe.of(
        authoredServings = 4,
        ingredients = listOf(AuthoredQuantity(GRAM, q(500)), AuthoredQuantity(PIECE, q(3), divisible = false)),
    )
    private val chickenOnly = chickenAndEggs.fix(0, q(300), GRAM)
    private val both = chickenOnly.fix(1, q(3), PIECE)

    @Test
    fun `fixing 3 eggs after 300 g of chicken puts 1_0 in force and shows 500 g required against 300 g`() {
        assertEquals(ScaleFactor.ONE, both.factor)
        val chicken = both.lines[0]
        assertEquals(q(500), chicken.quantity.value)
        assertTrue(chicken.fixed)
        assertEquals(Shortfall(chicken.quantity, q(300)), chicken.shortfall)
        assertEquals(q(500), chicken.shortfall!!.required.value)
        assertEquals(GRAM, chicken.shortfall!!.required.unit)
        val eggs = both.lines[1]
        assertEquals(q(3), eggs.quantity.value)
        assertTrue(eggs.fixed)
        assertNull(eggs.shortfall, "the fixing in force has no shortfall")
    }

    @Test
    fun `releasing the egg fixing returns the factor to the chicken's and the shortfall disappears`() {
        val released = both.release(1)
        assertEquals(ScaleFactor.of(q(3, 5)), released.factor)
        assertEquals(chickenOnly.factor, released.factor)
        released.lines.forEach { assertNull(it.shortfall, "$it") }
        assertTrue(released.lines[0].fixed)
        assertEquals(q(300), released.lines[0].quantity.value)
        assertFalse(released.lines[1].fixed)
        assertEquals(q(2), released.lines[1].quantity.value) // 3 × 0.6 = 1.8 → 2
    }
}

class Scale008ServingCountBelowOneTest {
    @Test
    fun `fixing 50 g of 500 g for 4 servings shows an approximate third of a serving`() {
        assertApproximately(q(1, 3), countWhenFixing(50, 500))
    }

    @Test
    fun `fixing 25 g of 500 g (a computed 0_2 servings) shows an approximate quarter serving`() {
        assertApproximately(q(1, 4), countWhenFixing(25, 500))
    }

    @Test
    fun `a computed count whose nearest value is one or more is displayed as a whole number`() {
        // Computed counts 0.875, 0.92, 1, 1.2, 1.3, 1.6, 1.68, 2.5, 3.4, 7.75: none shows as a fraction.
        val cases = listOf(
            Triple(70L, 320L, q(1)), Triple(115L, 500L, q(1)), Triple(125L, 500L, q(1)),
            Triple(150L, 500L, q(1)), Triple(130L, 400L, q(1)), Triple(200L, 500L, q(2)),
            Triple(210L, 500L, q(2)), Triple(250L, 400L, q(3)), Triple(425L, 500L, q(3)),
            Triple(775L, 400L, q(8)),
        )
        for ((fixed, authored, expected) in cases) {
            val count = countWhenFixing(fixed, authored)
            assertTrue(count.value.isWhole, "$fixed g of $authored g shows ${count.value}")
            assertApproximately(expected, count)
        }
    }

    @Test
    fun `no quantity is computed from the displayed serving count`() {
        // 115 g of 500 g: a factor of 0.23, a count shown as 1. From the count, 350 g would be 87.5 → 88 g.
        val view = fourServingsWith(500).fix(0, q(115), GRAM)
        assertEquals(q(1), view.servingCount.value)
        assertEquals(q(115), view.lines[0].quantity.value)
        assertEquals(q(81), view.lines[1].quantity.value) // 350 × 0.23 = 80.5 → 81 g
        // 50 g of 500 g: a factor of 0.1, a count shown as ⅓. From the count, 350 g would be 29.2 → 29 g.
        val third = fourServingsWith(500).fix(0, q(50), GRAM)
        assertEquals(q(1, 3), third.servingCount.value)
        assertEquals(q(35), third.lines[1].quantity.value)
    }

    @Test
    fun `fixing 115 g of 500 g (a computed 0_92 servings) shows an approximate 1 serving, not three quarters`() {
        assertApproximately(q(1), countWhenFixing(115, 500))
    }

    @Test
    fun `fixing 70 g of 320 g (a computed 0_875, between three quarters and 1) shows an approximate 1 serving`() {
        assertEquals(ScaleFactor.of(q(21875, 100000)), fourServingsWith(320).fix(0, q(70), GRAM).factor)
        assertApproximately(q(1), countWhenFixing(70, 320))
    }

    @Test
    fun `fixing 100 g of 500 g (a computed 0_8 servings) shows an approximate three quarters of a serving`() {
        assertApproximately(q(3, 4), countWhenFixing(100, 500))
    }
}

class Scale010FixedCountNotCappedTest {
    @Test
    fun `fixing 10 kg of an ingredient authored at 5 g for 4 servings shows the derived count, uncapped`() {
        val view = ScaledRecipe.of(4, listOf(AuthoredQuantity(GRAM, q(5)))).fix(0, q(10), KILOGRAM)
        assertEquals(ScaleFactor.of(q(2000)), view.factor)
        assertApproximately(q(8000), view.servingCount)
        assertEquals(q(10_000), view.lines[0].quantity.value) // 10 kg in grams
    }
}

class Scale015ServingCountToNearestTest {
    @Test
    fun `fixing 250 g of 400 g for 4 servings (a computed 2_5) shows an approximate 3 servings`() {
        assertApproximately(q(3), countWhenFixing(250, 400))
    }

    @Test
    fun `fixing 25 g of 240 g for 4 servings (a computed 5 twelfths) shows an approximate half serving`() {
        assertApproximately(q(1, 2), countWhenFixing(25, 240))
    }
}
