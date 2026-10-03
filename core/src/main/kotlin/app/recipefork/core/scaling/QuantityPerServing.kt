package app.recipefork.core.scaling

import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.recipe.comparisonFactor

/**
 * One ingredient line as the filter by an ingredient compares it (CAT-002, CAT-007, CAT-009): its quantity in its unit,
 * and the comparison [factors] of the dictionary entry the line names (DOM-011), empty for a line naming none.
 */
data class MeasuredLine(
    val unitType: UnitType,
    val unit: QuantityUnit?,
    val quantity: Rational?,
    val factors: Map<String, Double> = emptyMap(),
)

/**
 * How much of an ingredient one serving of a recipe holds, for ordering a filter by that ingredient (CAT-002, CAT-007,
 * CAT-009): its comparable quantity, on the scale the filtered family's factors share.
 *
 * A factor orders and nothing else (DOM-011): it is never shown, and nothing here scales a recipe or changes one.
 */
object QuantityPerServing {
    /**
     * The comparable quantity of the whole of [line] (CAT-009): its quantity times its entry's factor for its unit; null
     * when it holds no quantity — `to taste`, or none stated — or its entry gives no factor for its unit.
     */
    fun of(line: MeasuredLine): Double? {
        val unit = line.unit ?: return null
        val quantity = line.quantity ?: return null
        if (!line.unitType.carriesQuantity || !unit.type.carriesQuantity) return null
        val factor = comparisonFactor(line.factors, unit) ?: return null
        return quantity.numerator.toDouble() / quantity.denominator.toDouble() * factor
    }

    /**
     * The comparable quantity per serving of a recipe authored for [servings] holding the matched [lines], summed
     * (CAT-007); null when nothing in it is comparable — no line contributes, or the recipe states no serving count.
     */
    fun of(lines: List<MeasuredLine>, servings: Int?): Double? {
        if (servings == null || servings < 1) return null
        val contributions = lines.mapNotNull(::of)
        if (contributions.isEmpty()) return null
        return contributions.sum() / servings
    }
}

/**
 * This list, given in the order chosen for it, ordered by each item's comparable quantity per serving given by [amount]
 * ([QuantityPerServing.of]): the most first (CAT-002), or the least first when not [mostFirst]. The items of no
 * comparable quantity follow every item that has one, whichever way, in the order chosen for them (CAT-009); items of
 * equal quantity keep that order too.
 */
fun <T> List<T>.byQuantityPerServing(mostFirst: Boolean = true, amount: (T) -> Double?): List<T> {
    val held = map { it to amount(it) }
    val (measured, rest) = held.partition { it.second != null }
    val ordered = if (mostFirst) measured.sortedByDescending { it.second!! } else measured.sortedBy { it.second!! }
    return ordered.map { it.first } + rest.map { it.first }
}
