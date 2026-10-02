package app.recipefork.core.scaling

import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.UnitType

/**
 * The exact ratio every displayed quantity is computed from (SCALE-001, SCALE-003). It is never
 * rounded and has no upper bound (SCALE-010).
 */
class ScaleFactor private constructor(val value: Rational) {

    val isOne: Boolean get() = value == Rational.ONE

    override fun equals(other: Any?): Boolean = other is ScaleFactor && value == other.value
    override fun hashCode(): Int = value.hashCode()
    override fun toString(): String = "ScaleFactor($value)"

    companion object {
        val ONE = ScaleFactor(Rational.ONE)

        /** Any exact factor above zero. */
        fun of(value: Rational): ScaleFactor {
            require(value.signum() > 0) { "a scale factor is above zero: $value" }
            return ScaleFactor(value)
        }

        /**
         * The factor of a requested serving count against the authored one (SCALE-001): a
         * requested count of ¼ or more, and no ceiling (SCALE-010).
         */
        fun forServings(requested: Rational, authored: Int): ScaleFactor {
            require(authored >= 1) { "an authored serving count is a whole number of 1 or more: $authored" }
            require(requested >= MIN_REQUESTED_SERVINGS) { "a requested serving count is ¼ or more: $requested" }
            return ScaleFactor(requested / Rational.of(authored.toLong()))
        }

        val MIN_REQUESTED_SERVINGS: Rational = Rational.of(1, 4)
    }
}

/**
 * What scaling reads of an ingredient: its unit, its authored quantity in that unit (null
 * exactly for `taste_unmeasured`), and, for `piece`, its divisibility.
 */
data class AuthoredQuantity(
    val unit: QuantityUnit,
    val quantity: Rational?,
    val divisible: Boolean? = null,
) {
    init {
        if (unit.type.carriesQuantity) {
            requireNotNull(quantity) { "a $unit ingredient carries a quantity" }
            require(quantity.signum() > 0) { "an authored quantity is above zero: $quantity" }
        } else {
            require(quantity == null) { "a ${unit.type} ingredient carries no quantity" }
        }
        if (unit.type == UnitType.PIECE) requireNotNull(divisible) { "a piece ingredient carries its divisibility" }
    }
}

/** Which way rounding moved a displayed value away from the exact computed one. */
enum class Rounding {
    /** The grid represented the computed value exactly. */
    NONE,

    /** The displayed value is above the exact one: the exact value was lower. */
    UP,

    /** The displayed value is below the exact one: the exact value was higher. */
    DOWN,
}

/**
 * The quantity a line displays at a scale factor.
 *
 * [value] is the grid value in [unit]; for `exact` that unit is the base unit of the dimension
 * (gram or millilitre) and the ladder of SCALE-013 is the text form's concern. [value] is null
 * for `taste_unmeasured`. [derived] is true exactly when the factor is not 1 and the line
 * carries a quantity (SCALE-004); [rounding] says which way the grid moved it.
 */
data class DisplayedQuantity(
    val unit: QuantityUnit,
    val value: Rational?,
    val derived: Boolean,
    val rounding: Rounding,
)

object Scaling {

    /**
     * The quantity displayed for [ingredient] at [factor], computed from the authored quantity
     * and the exact factor alone (SCALE-003).
     *
     * At a factor of 1 the authored value is displayed as written: it lies on its grid
     * (DOM-017), or it is a value DOM-018 keeps as written.
     */
    fun display(ingredient: AuthoredQuantity, factor: ScaleFactor): DisplayedQuantity {
        val authored = ingredient.quantity
            ?: return DisplayedQuantity(ingredient.unit, null, derived = false, rounding = Rounding.NONE)

        val unit = ingredient.unit.baseUnit ?: ingredient.unit
        val authoredInUnit = ingredient.unit.baseMultiple
            ?.let { authored * Rational.of(it) }
            ?: authored

        if (factor.isOne) return DisplayedQuantity(unit, authoredInUnit, derived = false, rounding = Rounding.NONE)

        val grid = checkNotNull(RoundingGrid.of(ingredient.unit, ingredient.divisible))
        val exact = authoredInUnit * factor.value
        val shown = grid.nearest(exact)
        val rounding = when (shown.compareTo(exact)) {
            0 -> Rounding.NONE
            1 -> Rounding.UP
            else -> Rounding.DOWN
        }
        return DisplayedQuantity(unit, shown, derived = true, rounding = rounding)
    }

    /** Every line of an ingredient list at [factor], in order. */
    fun display(ingredients: List<AuthoredQuantity>, factor: ScaleFactor): List<DisplayedQuantity> =
        ingredients.map { display(it, factor) }
}
