package app.recipefork.core.scaling

import app.recipefork.core.recipe.Dimension
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.UnitSystem
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.recipe.comparisonFactor
import java.math.BigDecimal
import java.math.BigInteger

/**
 * An amount as the reader's system shows it: [quantity] carries its unit, its value on that unit's grid and the
 * derived and rounding marks exactly as [Scaling.display] does; [approximate] is true where the amount was converted
 * through the entry's measures ("≈"). [writtenPerShown] is how many of the written unit's line unit
 * ([QuantityUnit.lineUnit], or the unit itself outside `exact`) one shown unit holds — 1 where nothing converted — so a
 * value typed in the shown unit reads back as `typed × writtenPerShown` in the written one, by the same ratio.
 */
data class ShownQuantity(
    val quantity: DisplayedQuantity,
    val approximate: Boolean,
    val writtenPerShown: Rational,
)

/**
 * The rule that shows an amount in the reader's [UnitSystem] (v0.35, "Rules decided at planning").
 *
 * - Shared units — piece, pinch, to taste, tsp, tbsp — pass through, and so does an amount already in the chosen system
 *   (a cup counts as US).
 * - Into Metric: oz, lb → grams and fl oz → millilitres, exactly; a cup → millilitres where the entry has an `ml`
 *   measure (a liquid), else grams, by the entry's cup measure, approximately; a cup the entry has no measure for stays.
 * - Into US, a volume (ml, l, m³) → cups from ¼ cup, else tbsp from 1 tbsp, else tsp, by the US cup (8 fl oz) exactly.
 * - Into US, a mass (g, kg, t) → the same household units by the entry's measures, approximately: cups where it has a
 *   cup measure and the amount is ¼ cup or more; below ¼ cup tbsp, below 1 tbsp tsp — each spoon by its own measure,
 *   else derived from the entry's other volume measures (1 cup = 16 tbsp, 1 tbsp = 3 tsp); an entry with spoon measures
 *   and no cup measure takes spoons only below 4 tbsp. Otherwise ounces exactly, on their grid (the oz → lb ladder is
 *   the text's).
 *
 * The exact scaled amount is converted, then rounded once on the shown unit's grid; the written grid is never applied
 * first (SCALE-003).
 */
object UnitConversion {

    /**
     * [ingredient] at [factor] in [system], by the measures of its entry ([factors], grams per unit under
     * IngredientEntryDto.FACTOR_UNITS codes; empty for free text). Where nothing converts it is [Scaling.display]
     * exactly, the authored value kept as written at a factor of 1.
     */
    fun display(ingredient: AuthoredQuantity, factor: ScaleFactor, factors: Map<String, Double>, system: UnitSystem): ShownQuantity {
        val quantity = ingredient.quantity
        val inLine = quantity?.let { inLine(ingredient.unit, it * factor.value) }
        val target = inLine?.let { target(ingredient.unit, it, factors, system) }
            ?: return ShownQuantity(Scaling.display(ingredient, factor), approximate = false, writtenPerShown = Rational.ONE)
        return shown(target, inLine, derived = !factor.isOne, divisible = null)
    }

    /**
     * An exact amount [exact] of [unit] — a sum, already scaled — in [system]; where nothing converts it is rounded on
     * the grid of [unit]'s line unit ([divisible] for `piece`). [derived] is passed through to the shown quantity.
     * "To taste" has no amount and no grid: it passes through as it is, with no value, whatever [exact] says.
     */
    fun convert(
        unit: QuantityUnit,
        exact: Rational,
        factors: Map<String, Double>,
        system: UnitSystem,
        derived: Boolean = false,
        divisible: Boolean? = null,
    ): ShownQuantity {
        if (unit == QuantityUnit.TO_TASTE) {
            return ShownQuantity(DisplayedQuantity(unit, null, derived, Rounding.NONE), approximate = false, writtenPerShown = Rational.ONE)
        }
        val inLine = inLine(unit, exact)
        val target = target(unit, inLine, factors, system) ?: Target(unit.lineUnit ?: unit, Rational.ONE, approximate = false)
        return shown(target, inLine, derived, divisible)
    }

    /** The unit to show, how many written line units one of it holds, and whether that ratio came from the dictionary. */
    private data class Target(val unit: QuantityUnit, val per: Rational, val approximate: Boolean)

    private fun inLine(unit: QuantityUnit, amount: Rational): Rational = unit.lineMultiple?.let { amount * it } ?: amount

    /** Where [unit] (its amount [inLine] in its line unit) goes in [system]; null where it stays as written. */
    private fun target(unit: QuantityUnit, inLine: Rational, factors: Map<String, Double>, system: UnitSystem): Target? {
        val written = unit.lineUnit ?: unit
        return when {
            unit.type == UnitType.EXACT && unit.system == system -> null
            unit.type == UnitType.EXACT && system == UnitSystem.METRIC -> {
                val base = checkNotNull(unit.baseUnit)
                Target(base, checkNotNull(base.baseMultiple) / checkNotNull(written.baseMultiple), approximate = false)
            }
            unit.type == UnitType.EXACT && unit.dimension == Dimension.VOLUME ->
                household(inLine, US_CUP_ML, US_CUP_ML / SIXTEEN, US_CUP_ML / FORTY_EIGHT, approximate = false)
            unit.type == UnitType.EXACT -> massIntoUs(inLine, factors)
            unit == QuantityUnit.CUP && system == UnitSystem.METRIC -> {
                val cup = measureOf(factors, QuantityUnit.CUP) ?: return null
                val into = if (measureOf(factors, QuantityUnit.MILLILITRE) != null) QuantityUnit.MILLILITRE else QuantityUnit.GRAM
                measureOf(factors, into)?.let { Target(into, it / cup, approximate = true) }
            }
            else -> null
        }
    }

    /** Grams ([inLine]) into household units by the entry's measures, else into ounces exactly. */
    private fun massIntoUs(inLine: Rational, factors: Map<String, Double>): Target {
        val ounces = Target(QuantityUnit.OUNCE, checkNotNull(QuantityUnit.OUNCE.baseMultiple), approximate = false)
        val gram = measureOf(factors, QuantityUnit.GRAM) ?: return ounces
        val cup = measureOf(factors, QuantityUnit.CUP)
        val tbsp = measureOf(factors, QuantityUnit.TABLESPOON)
        val tsp = measureOf(factors, QuantityUnit.TEASPOON)
        val spoon = tbsp ?: tsp?.times(THREE) ?: cup?.div(SIXTEEN) ?: return ounces
        // Below 4 tbsp is what ¼ cup means to an entry with no cup measure.
        if (cup == null && inLine * gram / spoon >= FOUR) return ounces
        return household(inLine, cup?.div(gram), spoon / gram, (tsp ?: spoon / THREE) / gram, approximate = true)
    }

    /** Cups from ¼ cup where [cup] is given, else tbsp from 1 tbsp, else tsp; each given in written line units. */
    private fun household(inLine: Rational, cup: Rational?, tbsp: Rational, tsp: Rational, approximate: Boolean): Target = when {
        cup != null && inLine / cup >= QUARTER -> Target(QuantityUnit.CUP, cup, approximate)
        inLine / tbsp >= Rational.ONE -> Target(QuantityUnit.TABLESPOON, tbsp, approximate)
        else -> Target(QuantityUnit.TEASPOON, tsp, approximate)
    }

    /** The exact amount converted to [target]'s unit, rounded once on its grid. */
    private fun shown(target: Target, inLine: Rational, derived: Boolean, divisible: Boolean?): ShownQuantity {
        val exact = inLine / target.per
        val grid = checkNotNull(RoundingGrid.of(target.unit, divisible))
        val value = grid.nearest(exact)
        val rounding = when (value.compareTo(exact)) {
            0 -> Rounding.NONE
            1 -> Rounding.UP
            else -> Rounding.DOWN
        }
        val quantity = DisplayedQuantity(target.unit, value, derived, rounding, heldAtFloor = exact < grid.floor)
        return ShownQuantity(quantity, target.approximate, target.per)
    }

    /** The US cup: 8 fl oz, 236.5882365 ml exactly. */
    private val US_CUP_ML: Rational = checkNotNull(QuantityUnit.FLUID_OUNCE.baseMultiple) * Rational.of(8)
    private val QUARTER = Rational.of(1, 4)
    private val THREE = Rational.of(3)
    private val FOUR = Rational.of(4)
    private val SIXTEEN = Rational.of(16)
    private val FORTY_EIGHT = Rational.of(48)
}

/**
 * The entry's measure of one [unit] — grams per unit on the entry's scale (IngredientEntryDto.FACTOR_UNITS), exactly as
 * given; for an `exact` unit, its base unit's measure times its exact multiple of it. Null where [factors] give no
 * usable one.
 */
fun measureOf(factors: Map<String, Double>, unit: QuantityUnit): Rational? {
    val base = unit.baseUnit
    if (base != null && base != unit) return measureOf(factors, base)?.let { it * checkNotNull(unit.baseMultiple) }
    return comparisonFactor(factors, unit)?.takeIf { it.isFinite() && it > 0 }?.let(::rationalOf)
}

/** [value] exactly as its shortest decimal reads (0.92 is 92/100), for a number the dictionary gives. */
internal fun rationalOf(value: Double): Rational {
    val decimal = BigDecimal.valueOf(value)
    return if (decimal.scale() >= 0) Rational.of(decimal.unscaledValue(), BigInteger.TEN.pow(decimal.scale()))
    else Rational.of(decimal.unscaledValue() * BigInteger.TEN.pow(-decimal.scale()))
}
