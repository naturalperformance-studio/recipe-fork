package app.recipefork.core.scaling

import app.recipefork.core.recipe.QuantityUnit.TABLESPOON
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private fun r(n: Long, d: Long = 1) = Rational.of(n, d)

class Scale015NearestGridValueTieUpTest {
    @Test
    fun `a tablespoon quantity of 1 5_12 shows 1 and a half tablespoons`() {
        // 1 tablespoon at a factor of 17/12 computes exactly 1 5⁄12, midway between 1⅓ and 1½.
        val line = Scaling.display(AuthoredQuantity(TABLESPOON, r(1)), ScaleFactor.of(r(17, 12)))
        assertEquals(TABLESPOON, line.unit)
        assertEquals(r(3, 2), line.value)
        assertEquals(Rounding.UP, line.rounding)
        assertEquals(r(3, 2), RoundingGrid.FRACTIONAL.nearest(r(17, 12)))
    }

    @Test
    fun `a value whose nearest grid value is below the floor shows the floor`() {
        // Nearest to 0 on the unbounded grid, which is below the floor of each unit type.
        assertEquals(r(1), RoundingGrid.EXACT.nearest(r(2, 5)))
        assertEquals(r(1), RoundingGrid.PIECE_INDIVISIBLE.nearest(r(2, 5)))
        assertEquals(r(1, 2), RoundingGrid.PIECE_DIVISIBLE.nearest(r(1, 5)))
        assertEquals(r(1, 4), RoundingGrid.FRACTIONAL.nearest(r(1, 10)))
        // Exactly midway between 0 and the floor, and a hair above zero.
        assertEquals(r(1), RoundingGrid.EXACT.nearest(r(1, 2)))
        assertEquals(r(1, 4), RoundingGrid.FRACTIONAL.nearest(r(1, 1_000_000)))
    }

    /** Adjacent grid values across every kind of step, including the gram-to-kilogram precision change. */
    private val adjacentPairs: Map<RoundingGrid, List<Pair<Rational, Rational>>> = mapOf(
        RoundingGrid.EXACT to listOf(
            r(1) to r(2), r(4) to r(5), r(999) to r(1000), r(999_999) to r(1_000_000),
            r(1_000_000) to r(1_001_000), r(1_234_000) to r(1_235_000),
        ),
        RoundingGrid.PIECE_INDIVISIBLE to listOf(r(1) to r(2), r(4) to r(5), r(999) to r(1000)),
        RoundingGrid.PIECE_DIVISIBLE to listOf(
            r(1, 2) to r(1), r(1) to r(3, 2), r(3, 2) to r(2), r(21, 2) to r(11),
        ),
        RoundingGrid.FRACTIONAL to listOf(
            r(1, 4) to r(1, 3), r(1, 3) to r(1, 2), r(1, 2) to r(2, 3), r(2, 3) to r(3, 4), r(3, 4) to r(1),
            r(1) to r(5, 4), r(5, 4) to r(4, 3), r(4, 3) to r(3, 2), r(3, 2) to r(5, 3), r(5, 3) to r(7, 4),
            r(7, 4) to r(2), r(1_333_337, 4) to r(1_000_003, 3),
        ),
    )

    @Test
    fun `just below, at and just above every midpoint`() {
        for ((grid, pairs) in adjacentPairs) {
            for ((low, high) in pairs) {
                assertTrue(grid.holds(low) && grid.holds(high), "$grid holds $low and $high")
                val mid = (low + high) / r(2)
                val eps = (high - low) / r(1000)
                assertEquals(low, grid.nearest(mid - eps), "$grid just below $mid")
                assertEquals(high, grid.nearest(mid), "$grid at $mid")
                assertEquals(high, grid.nearest(mid + eps), "$grid just above $mid")
                assertEquals(low, grid.nearest(low))
                assertEquals(high, grid.nearest(high))
            }
        }
    }

    @Test
    fun `the grids hold only their own values`() {
        assertFalse(RoundingGrid.EXACT.holds(r(15, 2)))
        assertFalse(RoundingGrid.EXACT.holds(r(1_000_500)))
        assertFalse(RoundingGrid.PIECE_INDIVISIBLE.holds(r(1, 2)))
        assertFalse(RoundingGrid.PIECE_DIVISIBLE.holds(r(1, 4)))
        assertFalse(RoundingGrid.FRACTIONAL.holds(r(3, 10)))
        assertFalse(RoundingGrid.FRACTIONAL.holds(r(1, 5)))
    }
}

class RationalTest {
    @Test
    fun `arithmetic stays exact and in lowest terms`() {
        assertEquals(r(1, 2), r(2, 4))
        assertEquals(r(-1, 2), r(1, -2))
        assertEquals(r(5, 6), r(1, 2) + r(1, 3))
        assertEquals(r(1, 6), r(1, 2) - r(1, 3))
        assertEquals(r(3, 2), r(1, 2) * r(3))
        assertEquals(r(3, 2), r(1, 2) / r(1, 3))
        assertEquals(-2L, r(-3, 2).floor().toLong())
        assertEquals(1L, r(3, 2).floor().toLong())
        assertTrue(r(1, 3) < r(1, 2))
    }
}
