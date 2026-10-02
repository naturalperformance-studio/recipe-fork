package app.recipefork.core.scaling

import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.UnitType

/**
 * An amount the reader fixed for one ingredient, in the unit of the displayed line: the base
 * unit (gram or millilitre) for `exact`, the ingredient's own unit otherwise.
 */
data class Fixing(val ingredient: Int, val amount: Rational)

/**
 * The serving count a view displays. [approximate] is true exactly when the count was computed
 * from a fixed ingredient (SCALE-002); [value] is then a value of [RoundingGrid.SERVING_COUNT].
 */
data class ServingCount(val value: Rational, val approximate: Boolean)

/** A fixed ingredient whose amount at the factor in force is more than the reader has (SCALE-006). */
data class Shortfall(
    /** The amount the recipe needs at the factor in force, as its line displays it. */
    val required: DisplayedQuantity,
    /** The amount the reader fixed, in the unit of [required]. */
    val available: Rational,
)

/** One displayed ingredient line: its quantity, whether it is fixed, and any shortfall. */
data class ScaledLine(
    val quantity: DisplayedQuantity,
    val fixed: Boolean,
    val shortfall: Shortfall?,
)

/**
 * A recipe viewed at a scale: authored for [authoredServings], at the serving count
 * [requestedServings] the reader asked for, with the ingredients the reader fixed since, in the
 * order they were fixed.
 *
 * The factor in force is the one implied by the most recently fixed ingredient (SCALE-002,
 * SCALE-006); with nothing fixed it is that of the requested count (SCALE-001). Every quantity
 * is computed from that exact factor, never from the displayed serving count (SCALE-003).
 */
class ScaledRecipe private constructor(
    val authoredServings: Int,
    val ingredients: List<AuthoredQuantity>,
    val requestedServings: Rational,
    /** Oldest first; the last one sets the scale. At most one per ingredient. */
    val fixings: List<Fixing>,
) {

    /** The exact factor every quantity is computed from. */
    val factor: ScaleFactor =
        fixings.lastOrNull()?.let { impliedFactor(it) }
            ?: ScaleFactor.forServings(requestedServings, authoredServings)

    /** Every line of the ingredient list, in order. */
    val lines: List<ScaledLine> by lazy {
        val latest = fixings.lastOrNull()
        ingredients.mapIndexed { i, ingredient ->
            val quantity = Scaling.display(ingredient, factor)
            val fixing = fixings.firstOrNull { it.ingredient == i }
            val shortfall = fixing
                ?.takeIf { it != latest }
                ?.takeIf { requiredInLineUnit(i) > it.amount }
                ?.let { Shortfall(quantity, it.amount) }
            ScaledLine(quantity, fixed = fixing != null, shortfall = shortfall)
        }
    }

    /**
     * False exactly when two or more lines are held at the floor of their unit type (SCALE-005), so the displayed
     * quantities no longer hold the authored proportions and a warning says so (SCALE-009). Lines are counted
     * whatever set the scale: a requested count or a fixed ingredient.
     */
    val holdsProportions: Boolean
        get() = lines.count { it.quantity.heldAtFloor } < 2

    /**
     * The serving count displayed: the one the reader asked for while nothing is fixed, and
     * otherwise the count the factor implies, taken to the nearest value of the serving-count
     * set and marked as approximate (SCALE-002, SCALE-008, SCALE-015). No quantity is computed
     * from it.
     */
    val servingCount: ServingCount
        get() = if (fixings.isEmpty()) {
            ServingCount(requestedServings, approximate = false)
        } else {
            val computed = factor.value * Rational.of(authoredServings.toLong())
            ServingCount(RoundingGrid.SERVING_COUNT.nearest(computed), approximate = true)
        }

    /**
     * Fixes [amount] in [unit] of the ingredient at [ingredient], which then sets the scale. A
     * previous fixing of the same ingredient is replaced.
     */
    fun fix(ingredient: Int, amount: Rational, unit: QuantityUnit): ScaledRecipe {
        val authored = ingredients[ingredient]
        require(authored.quantity != null) { "a ${authored.unit.type} ingredient offers no fixing" }
        require(amount.signum() > 0) { "a fixed amount is above zero: $amount" }
        val inLineUnit = when (authored.unit.type) {
            UnitType.EXACT -> {
                require(unit.dimension == authored.unit.dimension) { "$unit is not read as ${authored.unit}" }
                amount * Rational.of(checkNotNull(unit.baseMultiple))
            }
            else -> {
                require(unit == authored.unit) { "$unit is not read as ${authored.unit}" }
                amount
            }
        }
        val kept = fixings.filter { it.ingredient != ingredient }
        return ScaledRecipe(authoredServings, ingredients, requestedServings, kept + Fixing(ingredient, inLineUnit))
    }

    /**
     * Releases the fixing of the ingredient at [ingredient]; the latest remaining one sets the
     * scale. Releasing the last one displays the recipe at [requestedServings], the count in force
     * immediately before the first of the fixings (SCALE-017): a fixing never changes the
     * requested count, and a change of count releases every fixing ([requestServings]), so the
     * requested count is always the one in force before the first fixing. The approximate count
     * shown during a fixing is never taken as a requested count.
     */
    fun release(ingredient: Int): ScaledRecipe =
        ScaledRecipe(authoredServings, ingredients, requestedServings, fixings.filter { it.ingredient != ingredient })

    /**
     * The reader asks for [servings]: every fixing is released and the recipe is displayed at
     * that count by SCALE-001, which is shown as the reader asked for it and not as approximate
     * (SCALE-016).
     */
    fun requestServings(servings: Rational): ScaledRecipe {
        ScaleFactor.forServings(servings, authoredServings)
        return ScaledRecipe(authoredServings, ingredients, servings, emptyList())
    }

    private fun authoredInLineUnit(i: Int): Rational {
        val authored = ingredients[i]
        val quantity = checkNotNull(authored.quantity)
        return authored.unit.baseMultiple?.let { quantity * Rational.of(it) } ?: quantity
    }

    private fun impliedFactor(fixing: Fixing): ScaleFactor =
        ScaleFactor.of(fixing.amount / authoredInLineUnit(fixing.ingredient))

    /** The exact amount the recipe needs of ingredient [i] at the factor in force. */
    private fun requiredInLineUnit(i: Int): Rational = authoredInLineUnit(i) * factor.value

    companion object {
        /** A recipe at [requestedServings] (its authored count unless given), nothing fixed. */
        fun of(
            authoredServings: Int,
            ingredients: List<AuthoredQuantity>,
            requestedServings: Rational = Rational.of(authoredServings.toLong()),
        ): ScaledRecipe {
            ScaleFactor.forServings(requestedServings, authoredServings)
            return ScaledRecipe(authoredServings, ingredients, requestedServings, emptyList())
        }
    }
}
