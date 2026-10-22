package app.recipefork.core.nutrition

import app.recipefork.core.api.NutritionDto
import app.recipefork.core.recipe.Dimension
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.Rational
import app.recipefork.core.scaling.ScaleFactor
import app.recipefork.core.scaling.measureOf
import app.recipefork.core.scaling.rationalOf
import java.math.BigInteger

/**
 * The rule that counts roughly what a dish holds (v0.40, T-288), from the per-100 g values of its dictionary entries.
 *
 * - A row's grams: a mass (g, kg, t, oz, lb) exactly; a volume (ml, l, m³, fl oz) by its entry's `ml` measure; pieces
 *   by the row's own piece weight, else by its entry's `pc` measure; tsp, tbsp, cup and pinch by its entry's measure for
 *   that unit. Every row is scaled by the factor in force, exactly.
 * - A row is left out for the first [LeftOutReason] that holds, in their order: free text, no entry in the dictionary,
 *   to taste, no quantity, no measure for its unit, no values on its entry or its parent.
 * - An entry without values of its own takes its parent's, unless it is marked as having none (`noNutrition`); its
 *   measures stay its own.
 * - Totals are summed from exact values and rounded once, half up: kcal to the nearest 10, grams to whole grams. One
 *   serving is the exact whole divided by the serving count shown, rounded once the same way, so it stays the same as
 *   the count changes. Each counted row's kcal is rounded on its own, and the rows may not add up to the total.
 * - 100 g of the finished dish (v0.44, T-320), where the recipe gives what it weighs at its own servings: the exact whole
 *   × 100 ÷ (that weight × the factor), rounded once the same way, so it stays the same as the scale changes. The
 *   weight at the scale is that weight × the factor, to the nearest whole gram, half up.
 */
object Nutrition {

    /**
     * The nutrition of a dish of [ingredients] at [factor], reading entries from [entries]; one serving of [servings]
     * where a count is shown; 100 g of the finished dish where the recipe gives its [finishedWeight] in grams, for the
     * dish at its own servings. Null where no row can be counted.
     */
    fun of(
        ingredients: List<Ingredient>,
        factor: ScaleFactor,
        entries: NutritionEntries,
        servings: Rational? = null,
        finishedWeight: Int? = null,
    ): DishNutrition? {
        require(servings == null || servings.signum() > 0) { "a serving count is above zero: $servings" }
        require(finishedWeight == null || finishedWeight > 0) { "a finished weight is above zero: $finishedWeight" }
        val counted = mutableListOf<Pair<Int, Exact>>()
        val leftOut = mutableListOf<LeftOutRow>()
        for (row in ingredients) {
            when (val count = count(row, entries)) {
                is Count.Left -> leftOut += LeftOutRow(row.position, count.reason)
                is Count.Counted -> counted += row.position to count.values * factor.value
            }
        }
        if (counted.isEmpty()) return null
        val whole = counted.map { it.second }.reduce(Exact::plus)
        val weight = finishedWeight?.let { Rational.of(it.toLong()) * factor.value }
        return DishNutrition(
            whole = whole.rounded(),
            perServing = servings?.let { (whole / it).rounded() },
            counted = counted.map { (position, values) -> CountedRow(position, kcalOf(values.kcal)) },
            leftOut = leftOut,
            per100g = weight?.let { (whole * HUNDRED / it).rounded() },
            finishedWeight = weight?.let(::gramsOf),
        )
    }

    /** What [row] holds as authored, or why it is left out. */
    private fun count(row: Ingredient, entries: NutritionEntries): Count {
        val id = row.dictionaryEntry ?: return Count.Left(LeftOutReason.FREE_TEXT)
        val entry = entries.entry(id) ?: return Count.Left(LeftOutReason.NOT_IN_DICTIONARY)
        if (row.unitType == UnitType.TASTE_UNMEASURED || row.unit == QuantityUnit.TO_TASTE) return Count.Left(LeftOutReason.TO_TASTE)
        val quantity = row.quantity?.takeIf { it.signum() > 0 } ?: return Count.Left(LeftOutReason.NO_QUANTITY)
        val perUnit = row.unit?.let { gramsPerUnit(it, row, entry.factors) } ?: return Count.Left(LeftOutReason.NO_MEASURE)
        val nutrition = entry.nutrition
            ?: entry.parentId?.takeUnless { entry.noNutrition }?.let(entries::entry)?.nutrition
            ?: return Count.Left(LeftOutReason.NO_DATA)
        return Count.Counted(Exact.of(nutrition) * (quantity * perUnit / HUNDRED))
    }

    /** How many grams one [unit] of [row] weighs; null where nothing gives a measure. */
    private fun gramsPerUnit(unit: QuantityUnit, row: Ingredient, factors: Map<String, Double>): Rational? = when {
        unit.dimension == Dimension.MASS -> unit.baseMultiple
        unit == QuantityUnit.PIECE -> row.pieceWeight?.takeIf { it.signum() > 0 } ?: measureOf(factors, unit)
        else -> measureOf(factors, unit)
    }

    private sealed interface Count {
        data class Left(val reason: LeftOutReason) : Count
        data class Counted(val values: Exact) : Count
    }

    /** Values held exactly, in kcal and grams. */
    private data class Exact(val kcal: Rational, val protein: Rational, val fat: Rational, val carbs: Rational) {
        operator fun plus(other: Exact) = Exact(kcal + other.kcal, protein + other.protein, fat + other.fat, carbs + other.carbs)
        operator fun times(by: Rational) = Exact(kcal * by, protein * by, fat * by, carbs * by)
        operator fun div(by: Rational) = Exact(kcal / by, protein / by, fat / by, carbs / by)

        fun rounded() = NutritionValues(kcalOf(kcal), gramsOf(protein), gramsOf(fat), gramsOf(carbs))

        companion object {
            fun of(values: NutritionDto) =
                Exact(rationalOf(values.kcal), rationalOf(values.protein), rationalOf(values.fat), rationalOf(values.carbs))
        }
    }

    /** [kcal] to the nearest 10, half up. */
    private fun kcalOf(kcal: Rational): BigInteger = halfUp(kcal / TEN) * BigInteger.TEN

    /** [grams] to the nearest whole gram, half up. */
    private fun gramsOf(grams: Rational): BigInteger = halfUp(grams)

    /** [value] to the nearest whole number, half up, however large (SCALE-010 sets the factor no ceiling). */
    private fun halfUp(value: Rational): BigInteger = (value + HALF).floor()

    private val HALF = Rational.of(1, 2)
    private val TEN = Rational.of(10)
    private val HUNDRED = Rational.of(100)
}
