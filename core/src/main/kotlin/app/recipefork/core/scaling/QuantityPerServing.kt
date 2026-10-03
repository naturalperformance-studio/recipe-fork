package app.recipefork.core.scaling

import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.UnitType

/** One ingredient line's quantity as the filter by an ingredient compares it (CAT-002, CAT-007). */
data class MeasuredLine(val unitType: UnitType, val unit: QuantityUnit?, val quantity: Rational?)

/**
 * How much of an ingredient one serving of a recipe holds, for ordering a filter by that ingredient (CAT-002, CAT-007).
 *
 * The dictionary carries no comparison factors yet (CAT-009), so a quantity is compared only with quantities of the same
 * measure: a mass in grams, a volume in millilitres, and any other unit — a piece, a spoon, a cup, a pinch — in itself.
 * Nothing converts one measure to another, and a factor is never derived from what a recipe states.
 */
object QuantityPerServing {
    /** The unit [line] is compared in: a mass in grams, a volume in millilitres, any other unit in itself; null for none. */
    fun comparisonUnit(line: MeasuredLine): QuantityUnit? {
        val unit = line.unit ?: return null
        if (!line.unitType.carriesQuantity || !unit.type.carriesQuantity || line.quantity == null) return null
        return unit.baseUnit ?: unit
    }

    /**
     * The quantity per serving of a recipe authored for [servings] holding the matched [lines], each measure summed
     * (CAT-007): by the unit it is compared in. A line `to taste` or without a quantity contributes nothing; a recipe of
     * no stated serving count has no quantity per serving at all.
     */
    fun of(lines: List<MeasuredLine>, servings: Int?): Map<QuantityUnit, Rational> {
        if (servings == null || servings < 1) return emptyMap()
        val perServing = Rational.of(1, servings.toLong())
        val sums = LinkedHashMap<QuantityUnit, Rational>()
        lines.forEach { line ->
            val unit = comparisonUnit(line) ?: return@forEach
            val inUnit = line.quantity!! * Rational.of(line.unit!!.baseMultiple ?: 1) * perServing
            sums[unit] = (sums[unit] ?: Rational.ZERO) + inUnit
        }
        return sums
    }
}

/**
 * This list, given in the order chosen for it, with the most per serving first (CAT-002): each item's quantities per
 * serving are given by [amounts] ([QuantityPerServing.of]). They are compared in one measure, the one most of the items
 * hold — on a tie the first in the order of [QuantityUnit], so a mass before a volume before a piece; the items holding
 * none of it follow, in the order chosen for them (CAT-009). Items of equal quantity keep the chosen order.
 */
fun <T> List<T>.mostPerServingFirst(amounts: (T) -> Map<QuantityUnit, Rational>): List<T> {
    val held = map { it to amounts(it) }
    val unit = held.flatMap { it.second.keys }.groupingBy { it }.eachCount().entries
        .maxWithOrNull(compareBy<Map.Entry<QuantityUnit, Int>> { it.value }.thenByDescending { it.key.ordinal })
        ?.key ?: return this
    val (measured, rest) = held.partition { unit in it.second }
    return measured.sortedByDescending { it.second.getValue(unit) }.map { it.first } + rest.map { it.first }
}
