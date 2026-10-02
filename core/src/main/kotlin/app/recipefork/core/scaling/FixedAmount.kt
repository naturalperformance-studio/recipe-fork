package app.recipefork.core.scaling

import app.recipefork.core.recipe.QuantityInput
import app.recipefork.core.recipe.QuantityProblem
import app.recipefork.core.recipe.QuantityReading
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.UnitType

/** What an amount typed into the fixing control reads as (SCALE-018). */
sealed interface FixedAmountReading {
    /** Nothing typed: the scale in force does not change, and nothing is released (SCALE-018 (8)). */
    data object Empty : FixedAmountReading

    /** An amount the control holds, in the unit it was typed in. */
    data class Accepted(val amount: Rational) : FixedAmountReading

    /** Anything else: the control does not hold it and says why (DOM-017 (6), DOM-019 (5)). */
    data class Refused(val problem: QuantityProblem) : FixedAmountReading
}

/**
 * The amounts a reader may fix for an ingredient (SCALE-018): read as the quantity field reads a number (DOM-019),
 * held only where that field would hold it on its unit type's grid (DOM-017), and stated in a unit the author's unit
 * reads in without conversion.
 */
object FixedAmount {

    /**
     * The units an ingredient authored in [authored] can be fixed in: for `exact` every unit of its dimension, for
     * any other unit type carrying a quantity its own unit alone; none for `taste_unmeasured`.
     */
    fun units(authored: QuantityUnit): List<QuantityUnit> = when {
        !authored.type.carriesQuantity -> emptyList()
        authored.type == UnitType.EXACT -> QuantityUnit.entries.filter { it.dimension == authored.dimension }
        else -> listOf(authored)
    }

    /** Reads [text] as an amount of [unit] for an ingredient of divisibility [divisible] (`piece` only). */
    fun read(text: String, unit: QuantityUnit, divisible: Boolean?): FixedAmountReading {
        val accepts = requireNotNull(QuantityInput.accepts(unit, divisible)) { "$unit holds no amount" }
        return when (val reading = QuantityInput.read(text)) {
            QuantityReading.Empty -> FixedAmountReading.Empty
            QuantityReading.Separators -> FixedAmountReading.Refused(QuantityProblem.Separators)
            QuantityReading.NotANumber -> FixedAmountReading.Refused(QuantityProblem.NotAccepted(accepts))
            is QuantityReading.Value ->
                if (reading.value.signum() > 0 && QuantityInput.holds(reading.value, unit, divisible)) {
                    FixedAmountReading.Accepted(reading.value)
                } else {
                    FixedAmountReading.Refused(QuantityProblem.NotAccepted(accepts))
                }
        }
    }
}
