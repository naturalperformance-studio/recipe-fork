package app.recipefork.core.scaling

import app.recipefork.core.recipe.QuantityUnit.GRAM
import app.recipefork.core.recipe.QuantityUnit.PIECE
import app.recipefork.core.recipe.QuantityUnit.TEASPOON
import app.recipefork.core.recipe.QuantityUnit.TO_TASTE
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private fun r(n: Long, d: Long = 1) = Rational.of(n, d)
private fun factor(n: Long, d: Long = 1) = ScaleFactor.of(r(n, d))

/** SCALE-005: a quantity the factor would take below its floor is shown at the floor, rounded up, and held there. */
class Scale005HeldAtFloorTest {

    @Test
    fun `3 indivisible pieces at 0_1 show 1 piece, rounded up and held at the floor`() {
        val shown = Scaling.display(AuthoredQuantity(PIECE, r(3), divisible = false), factor(1, 10))
        assertEquals(r(1), shown.value)
        assertEquals(Rounding.UP, shown.rounding)
        assertTrue(shown.heldAtFloor)
    }

    @Test
    fun `1 teaspoon at 0_05 shows a quarter teaspoon, rounded up and held at the floor`() {
        val shown = Scaling.display(AuthoredQuantity(TEASPOON, r(1)), factor(1, 20))
        assertEquals(r(1, 4), shown.value)
        assertEquals(Rounding.UP, shown.rounding)
        assertTrue(shown.heldAtFloor)
    }

    @Test
    fun `a value just below the floor whose nearest grid value is the floor anyway is held there`() {
        // 0.7 g is below the 1 g floor; 1 g would be its nearest value even on an unbounded grid.
        val shown = Scaling.display(AuthoredQuantity(GRAM, r(7)), factor(1, 10))
        assertEquals(r(1), shown.value)
        assertTrue(shown.heldAtFloor)
    }

    @Test
    fun `a value at or above the floor is not held, even when it is shown at the floor`() {
        val atFloor = Scaling.display(AuthoredQuantity(PIECE, r(2), divisible = false), factor(1, 2))
        assertEquals(r(1), atFloor.value)
        assertEquals(Rounding.NONE, atFloor.rounding)
        assertFalse(atFloor.heldAtFloor)
        // 1.2 pieces round down to the floor value of 1: rounded, but not held.
        val roundedDownOntoIt = Scaling.display(AuthoredQuantity(PIECE, r(2), divisible = false), factor(3, 5))
        assertEquals(r(1), roundedDownOntoIt.value)
        assertEquals(Rounding.DOWN, roundedDownOntoIt.rounding)
        assertFalse(roundedDownOntoIt.heldAtFloor)
    }

    @Test
    fun `nothing is held at the authored scale or on a line without a quantity`() {
        assertFalse(Scaling.display(AuthoredQuantity(TEASPOON, r(1, 4)), ScaleFactor.ONE).heldAtFloor)
        assertFalse(Scaling.display(AuthoredQuantity(TO_TASTE, null), factor(1, 20)).heldAtFloor)
    }
}

/** SCALE-009: two or more lines held at their floors and the recipe no longer holds its proportions. */
class Scale009ProportionsTest {
    /** Authored for 4: 3 indivisible eggs, 1 teaspoon of salt, 400 g of flour, salt to taste. */
    private val recipe = ScaledRecipe.of(
        authoredServings = 4,
        ingredients = listOf(
            AuthoredQuantity(PIECE, r(3), divisible = false),
            AuthoredQuantity(TEASPOON, r(1)),
            AuthoredQuantity(GRAM, r(400)),
            AuthoredQuantity(TO_TASTE, null),
        ),
    )

    private fun held(scaled: ScaledRecipe) = scaled.lines.map { it.quantity.heldAtFloor }

    @Test
    fun `with exactly one line held at its floor the proportions hold`() {
        // 1 serving: eggs 0.75 → 1 (held), salt ¼ tsp (exact, at the floor but not below it), flour 100 g.
        val one = recipe.requestServings(r(1))
        assertEquals(listOf(true, false, false, false), held(one))
        assertTrue(one.holdsProportions)
    }

    @Test
    fun `with two lines held at their floors the proportions no longer hold`() {
        // ½ serving: eggs 0.375 → 1 (held), salt ⅛ tsp → ¼ (held), flour 50 g.
        val half = recipe.requestServings(r(1, 2))
        assertEquals(listOf(true, true, false, false), held(half))
        assertFalse(half.holdsProportions)
        // Every held line still reads as rounded up.
        half.lines.take(2).forEach { assertEquals(Rounding.UP, it.quantity.rounding) }
    }

    @Test
    fun `back at the authored count the proportions hold again`() {
        val back = recipe.requestServings(r(1, 2)).requestServings(r(4))
        assertEquals(listOf(false, false, false, false), held(back))
        assertTrue(back.holdsProportions)
    }

    @Test
    fun `a fixed ingredient that takes two lines to their floors is counted the same way`() {
        // 4 g of flour out of 400 g: a factor of 1/100.
        val fixed = recipe.fix(2, r(4), GRAM)
        assertEquals(listOf(true, true, false, false), held(fixed))
        assertFalse(fixed.holdsProportions)
    }
}
