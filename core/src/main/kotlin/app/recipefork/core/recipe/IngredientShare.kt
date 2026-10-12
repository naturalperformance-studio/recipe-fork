package app.recipefork.core.recipe

import app.recipefork.core.scaling.AuthoredQuantity
import app.recipefork.core.scaling.DisplayedQuantity
import app.recipefork.core.scaling.Rational
import app.recipefork.core.scaling.ScaleFactor
import app.recipefork.core.scaling.Scaling

/**
 * A part of an ingredient that a step's reference reads instead of its whole quantity: [amount] in [unit], the
 * ingredient's own unit when the part was entered. Parts are independent of each other: their sum is never checked
 * against the whole, as one ingredient may go into a step in several parts.
 */
data class IngredientShare(val amount: Rational, val unit: QuantityUnit)

/** What a typed part reads as. */
sealed interface ShareReading {
    /** Nothing typed yet. */
    data object Empty : ShareReading

    data class Accepted(val share: IngredientShare) : ShareReading

    /** No value the ingredient's quantity field would hold: separators, no number, zero or below, off the grid. */
    data class Refused(val problem: QuantityProblem) : ShareReading

    /** A value the field would hold, but more than the ingredient's whole quantity. */
    data object MoreThanWhole : ShareReading

    /**
     * The ingredient offers no part as it stands ([IngredientShares.offered]): nothing typed is read, and nothing is
     * refused for a grid it does not have.
     */
    data object NotOffered : ShareReading
}

/** How a part of an ingredient is entered, checked, and read at a scale. */
object IngredientShares {
    /**
     * Whether [ingredient] offers a part: it holds a quantity in a unit of its type; never "to taste", never empty. A
     * `piece` row offers one only once its divisibility is given, which sets the grid its part is read on (DOM-017).
     */
    fun offered(ingredient: Ingredient): Boolean =
        ingredient.unitType.carriesQuantity && ingredient.unit?.type == ingredient.unitType &&
            ingredient.quantity?.let { it.signum() > 0 } == true &&
            (ingredient.unitType != UnitType.PIECE || ingredient.divisible != null)

    /**
     * [text] typed as a part of [ingredient], in its own unit: read like its quantity field (DOM-019), held only on its
     * unit type's grid and above zero (DOM-017), and no more than the ingredient's quantity. A row that offers no part
     * reads nothing ([ShareReading.NotOffered]).
     */
    fun read(text: String, ingredient: Ingredient): ShareReading {
        val unit = ingredient.unit?.takeIf { offered(ingredient) } ?: return ShareReading.NotOffered
        val accepts = QuantityInput.accepts(unit, ingredient.divisible) ?: return ShareReading.NotOffered
        val value = when (val reading = QuantityInput.read(text)) {
            QuantityReading.Empty -> return ShareReading.Empty
            QuantityReading.Separators -> return ShareReading.Refused(QuantityProblem.Separators)
            QuantityReading.NotANumber -> return ShareReading.Refused(QuantityProblem.NotAccepted(accepts))
            is QuantityReading.Value -> reading.value
        }
        if (!QuantityInput.holds(value, unit, ingredient.divisible)) {
            return ShareReading.Refused(QuantityProblem.NotAccepted(accepts))
        }
        val share = IngredientShare(value, unit)
        return if (exceeds(share, ingredient)) ShareReading.MoreThanWhole else ShareReading.Accepted(share)
    }

    /**
     * Whether [share] still reads as a part of a row in [unit]: in that very unit, or in another `exact` unit of its
     * dimension (grams of a row now in kilograms). A row changed to any other unit has its parts made whole.
     */
    fun fits(share: IngredientShare, unit: QuantityUnit?): Boolean =
        unit != null && (share.unit == unit || (share.unit.dimension != null && share.unit.dimension == unit.dimension))

    /** Whether [share] is more than [ingredient]'s quantity as it stands: the editor marks it, and refuses nothing. */
    fun exceeds(share: IngredientShare, ingredient: Ingredient): Boolean {
        val whole = ingredient.quantity ?: return false
        val unit = ingredient.unit ?: return false
        if (!fits(share, unit)) return false
        return inBase(share.amount, share.unit) > inBase(whole, unit)
    }

    /**
     * [share] of [ingredient] at [factor], through the same scaling and rounding grid as the whole (SCALE-003): its
     * own value scaled and rounded on its own, never taken from the whole's line. Null where it cannot be read so: a
     * share not above zero, or a `piece` row whose divisibility is not given. A `piece` part is rounded on the grid of
     * [divisible], the row's own unless a reader gives the one it reads the row with ([effectiveDivisible]).
     */
    fun display(
        share: IngredientShare,
        ingredient: Ingredient,
        factor: ScaleFactor,
        divisible: Boolean? = ingredient.divisible,
    ): DisplayedQuantity? {
        if (!share.unit.type.carriesQuantity || share.amount.signum() <= 0) return null
        val pieceDivisible = if (share.unit.type == UnitType.PIECE) divisible ?: return null else null
        return runCatching { Scaling.display(AuthoredQuantity(share.unit, share.amount, pieceDivisible), factor) }.getOrNull()
    }

    private fun inBase(amount: Rational, unit: QuantityUnit): Rational = unit.baseMultiple?.let { amount * it } ?: amount
}
