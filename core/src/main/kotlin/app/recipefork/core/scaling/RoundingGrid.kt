package app.recipefork.core.scaling

import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.UnitType

/**
 * The rounding grids of spec/80-scaling-rules/00-index.md, one per unit type that carries a
 * quantity (divisible and indivisible `piece` each have their own), and the set the serving
 * count is displayed on.
 *
 * [EXACT] is expressed in the base unit of its dimension — grams or millilitres: whole units
 * below 1 000 000 (1000 kg or 1000 l), whole thousands of them at and above. The unit ladder
 * (SCALE-013) only changes how such a value is written, not which values exist.
 */
enum class RoundingGrid(
    /** The smallest value of the grid, and the floor of SCALE-005. */
    val floor: Rational,
) {
    EXACT(Rational.ONE),
    PIECE_INDIVISIBLE(Rational.ONE),
    PIECE_DIVISIBLE(Rational.of(1, 2)),

    /** `household` and `taste_measured`: a whole number, alone or followed by ¼, ⅓, ½, ⅔ or ¾. */
    FRACTIONAL(Rational.of(1, 4)),

    /**
     * The serving count: below one, the fractions ¼, ⅓, ½, ⅔, ¾; at one and above, whole numbers
     * only. One set that holds 1, so a count from 0.875 up goes to 1 (SCALE-008, SCALE-015).
     */
    SERVING_COUNT(Rational.of(1, 4)),
    ;

    /** True when [value] is one of the values this grid displays. */
    fun holds(value: Rational): Boolean {
        if (value < floor) return false
        val (below, _) = neighbours(value)
        return below == value
    }

    /**
     * The grid value displayed for a computed [value] above zero: the nearest one, the larger of
     * two at an exact tie (SCALE-015), and never less than [floor] (SCALE-005).
     */
    fun nearest(value: Rational): Rational {
        require(value.signum() > 0) { "a computed quantity is above zero: $value" }
        val (below, above) = neighbours(value)
        if (below == value) return value
        val candidateBelow = below.takeIf { it >= floor } ?: return above
        return if (value - candidateBelow < above - value) candidateBelow else above
    }

    /**
     * The largest value of the unbounded grid (0 included) not above [value], and the smallest
     * not below it. The floor is applied by the caller.
     */
    private fun neighbours(value: Rational): Pair<Rational, Rational> = when (this) {
        EXACT -> {
            val step = if (value < MILLION) Rational.ONE else THOUSAND
            val below = Rational.of((value / step).floor()) * step
            below to (if (below == value) below else below + step)
        }
        PIECE_INDIVISIBLE -> steps(value, WHOLE)
        PIECE_DIVISIBLE -> steps(value, HALVES)
        FRACTIONAL -> steps(value, KITCHEN_FRACTIONS)
        SERVING_COUNT -> steps(value, if (value < Rational.ONE) KITCHEN_FRACTIONS else WHOLE)
    }

    private fun steps(value: Rational, fractions: List<Rational>): Pair<Rational, Rational> {
        val whole = Rational.of(value.floor())
        val candidates = fractions.map { whole + it } + (whole + Rational.ONE)
        return candidates.last { it <= value } to candidates.first { it >= value }
    }

    companion object {
        private val MILLION = Rational.of(1_000_000)
        private val THOUSAND = Rational.of(1_000)
        private val WHOLE = listOf(Rational.ZERO)
        private val HALVES = listOf(Rational.ZERO, Rational.of(1, 2))
        private val KITCHEN_FRACTIONS = listOf(
            Rational.ZERO,
            Rational.of(1, 4),
            Rational.of(1, 3),
            Rational.of(1, 2),
            Rational.of(2, 3),
            Rational.of(3, 4),
        )

        /**
         * The grid of a unit; [divisible] is read for `piece` only. Null for `taste_unmeasured`,
         * which carries no quantity.
         */
        fun of(unit: QuantityUnit, divisible: Boolean? = null): RoundingGrid? = when (unit.type) {
            UnitType.EXACT -> EXACT
            UnitType.PIECE -> {
                requireNotNull(divisible) { "a piece ingredient carries its divisibility" }
                if (divisible) PIECE_DIVISIBLE else PIECE_INDIVISIBLE
            }
            UnitType.HOUSEHOLD, UnitType.TASTE_MEASURED -> FRACTIONAL
            UnitType.TASTE_UNMEASURED -> null
        }
    }
}
