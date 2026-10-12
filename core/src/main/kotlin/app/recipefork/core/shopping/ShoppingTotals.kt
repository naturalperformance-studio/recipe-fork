package app.recipefork.core.shopping

import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.comparisonFactor
import app.recipefork.core.scaling.AuthoredQuantity
import app.recipefork.core.scaling.Rational
import app.recipefork.core.scaling.RoundingGrid
import app.recipefork.core.scaling.ScaleFactor
import app.recipefork.core.scaling.ScaledRecipe
import app.recipefork.core.scaling.Scaling
import java.math.BigDecimal
import java.math.BigInteger

/**
 * The rule that adds up a shopping list (FLOW-074) from recipes, each at its own serving count.
 *
 * - Every recipe's rows are scaled as the card scales them, by the exact factor of its [ScaledRecipe]; a recipe with no
 *   serving count is scaled as one serving, at one. Amounts are added as exact fractions and rounded once, on the grid
 *   of the line's unit. `exact` amounts add exactly in the line unit of their ladder (ounces for oz and lb) where every
 *   part stands in one system, and in the base unit of their dimension (gram, millilitre) where metric and US parts
 *   meet. A line is rounded on the line's grid even from a single
 *   row DOM-018 keeps as written: 1½ pieces of an entry now indivisible read 2, where the card shows 1½ at its
 *   authored count.
 * - A part counted into an approximate line is noted at its value on the line's grid; one below that grid's floor is
 *   noted as below it ("< ¼ cup"), never as the floor, which the line's total did not count.
 * - One entry's rows in one unit (every `exact` unit of one dimension counting as one) add exactly into one line;
 *   qualifiers do not split it. Where its rows stand in several such units, every unit the entry has a measure for
 *   merges into one approximate line in the unit of the largest part — by the entry's grams per unit, the first to
 *   appear at a tie — each other part counted as part × its unit's factor ÷ the line unit's factor. A unit the entry has
 *   no measure for stays a line of its own.
 * - An entry's rows to taste are one "to taste" line, or, where the entry has a measured line, mark its first one
 *   "+ to taste".
 * - A free-text row is a line of its own, never merged; a row that cannot be scaled is a line of its own, as written;
 *   a row naming nothing is left out.
 *
 * Lines stand in the order of their first row, recipe by recipe. A recipe given twice counts twice.
 *
 * This is a deliberate departure from DOM-011 for the shopping list alone: here an entry's factors do yield a shown,
 * approximate amount.
 */
object ShoppingTotals {

    /** The lines of a shopping list made from [recipes], reading each dictionary entry's measures from [entries]. */
    fun of(recipes: List<ShoppingRecipe>, entries: Map<Long, EntryMeasures>): List<ShoppingLine> {
        val drafts = mutableListOf<Draft>()
        val byEntry = LinkedHashMap<Long, MutableList<Part>>()
        var order = 0
        for (recipe in recipes) {
            val authored = recipe.ingredients.map { Scaling.authored(it, it.dictionaryEntry?.let(entries::get)?.divisible) }
            val factor = factorOf(recipe, authored.filterNotNull())
            recipe.ingredients.forEachIndexed { i, row ->
                val part = Part(order++, recipe.recipe, row, authored[i], factor)
                val name = nameOf(row) ?: return@forEachIndexed
                when {
                    part.authored == null -> drafts += Draft(part.order, name, ShoppingQuantity.AsWritten(row), sources = listOf(part))
                    name is ShoppingName.Entry -> byEntry.getOrPut(name.id) { mutableListOf() } += part
                    part.authored.unit == QuantityUnit.TO_TASTE ->
                        drafts += Draft(part.order, name, ShoppingQuantity.ToTaste, sources = listOf(part))
                    else -> drafts += Group(listOf(part)).let { Draft(it.order, name, measured(it.unit, it.sum, it.divisible), sources = it.parts) }
                }
            }
        }
        byEntry.forEach { (id, parts) -> drafts += entryLines(ShoppingName.Entry(id), parts, entries[id]) }
        return drafts.sortedBy { it.order }.map(Draft::line)
    }

    /** The exact factor the card scales [recipe] by: a recipe with no count is one serving at one. */
    private fun factorOf(recipe: ShoppingRecipe, authored: List<AuthoredQuantity>): ScaleFactor {
        val counted = recipe.authoredServings?.takeIf { it >= 1 }
        val requested = counted?.let { recipe.requestedServings ?: Rational.of(it.toLong()) } ?: Rational.ONE
        return ScaledRecipe.of(counted ?: 1, authored, requested).factor
    }

    private fun nameOf(row: Ingredient): ShoppingName? =
        row.dictionaryEntry?.let(ShoppingName::Entry)
            ?: row.freeTextName?.trim()?.takeIf { it.isNotEmpty() }?.let(ShoppingName::FreeText)

    private fun entryLines(name: ShoppingName.Entry, parts: List<Part>, measures: EntryMeasures?): List<Draft> {
        val (taste, amounts) = parts.partition { it.authored!!.unit == QuantityUnit.TO_TASTE }
        val groups = amounts.groupBy { it.key }.values.map(::Group)
        val factors = measures?.factors.orEmpty()
        val (convertible, unmeasured) = groups.partition { factorOf(factors, it.unit) != null }
        val lines = mutableListOf<Draft>()
        if (convertible.size >= 2) lines += merged(name, convertible, factors) else convertible.mapTo(lines) { own(name, it) }
        unmeasured.mapTo(lines) { own(name, it) }
        if (taste.isEmpty()) return lines
        val first = lines.minByOrNull { it.order }
            ?: return listOf(Draft(taste.first().order, name, ShoppingQuantity.ToTaste, sources = taste))
        val measured = first.quantity as ShoppingQuantity.Measured
        lines[lines.indexOfFirst { it === first }] = first.copy(
            order = minOf(first.order, taste.first().order),
            quantity = measured.copy(plusToTaste = true),
            sources = first.sources + taste,
        )
        return lines
    }

    private fun own(name: ShoppingName, group: Group): Draft =
        Draft(group.order, name, measured(group.unit, group.sum, group.divisible), sources = group.parts)

    private fun merged(name: ShoppingName, groups: List<Group>, factors: Map<String, Double>): Draft {
        val grams = groups.associateWith { it.sum * factorOf(factors, it.unit)!! }
        val target = groups.maxBy { grams.getValue(it) }
        val targetFactor = factorOf(factors, target.unit)!!
        val others = groups.filter { it !== target }.associateWith { grams.getValue(it) / targetFactor }
        val total = others.values.fold(target.sum, Rational::plus)
        return Draft(
            order = groups.minOf { it.order },
            name = name,
            quantity = measured(target.unit, total, target.divisible),
            approximate = true,
            counted = others.map { (group, inTarget) ->
                val grid = checkNotNull(RoundingGrid.of(target.unit, target.divisible))
                CountedPart(
                    original = amount(group.unit, group.sum, group.divisible),
                    countedAs = amount(target.unit, inTarget, target.divisible),
                    belowFloor = inTarget < grid.floor,
                )
            },
            sources = groups.flatMap { it.parts },
        )
    }

    private fun measured(unit: QuantityUnit, exact: Rational, divisible: Boolean?) =
        ShoppingQuantity.Measured(amount(unit, exact, divisible))

    /** [exact] rounded once, on the grid of [unit]. */
    private fun amount(unit: QuantityUnit, exact: Rational, divisible: Boolean?): ShoppingAmount =
        ShoppingAmount(unit, checkNotNull(RoundingGrid.of(unit, divisible)).nearest(exact))

    /**
     * The entry's grams per one [unit], exactly as given — for an `exact` unit, the factor of its base unit times its
     * exact multiple of it; null where the entry gives no usable one.
     */
    private fun factorOf(factors: Map<String, Double>, unit: QuantityUnit): Rational? {
        val base = unit.baseUnit
        if (base != null && base != unit) return factorOf(factors, base)?.let { it * checkNotNull(unit.baseMultiple) }
        return comparisonFactor(factors, unit)?.takeIf { it.isFinite() && it > 0 }?.let(::rationalOf)
    }

    private fun rationalOf(value: Double): Rational {
        val decimal = BigDecimal.valueOf(value)
        return if (decimal.scale() >= 0) Rational.of(decimal.unscaledValue(), BigInteger.TEN.pow(decimal.scale()))
        else Rational.of(decimal.unscaledValue() * BigInteger.TEN.pow(-decimal.scale()))
    }

    /** One row of one recipe, at the order it was given in; [authored] is null for a row that cannot be scaled. */
    private class Part(val order: Int, val recipe: RecipeId, val row: Ingredient, val authored: AuthoredQuantity?, factor: ScaleFactor) {
        /** What the row adds up with: the base unit of its dimension for `exact` (g and oz add up), its own unit otherwise. */
        val key: QuantityUnit? = authored?.unit?.let { it.baseUnit ?: it }

        /** The unit the row's line is in: the line unit of its ladder for `exact`, its own otherwise. */
        val unit: QuantityUnit? = authored?.unit?.let { it.lineUnit ?: it }

        /** The exact amount at the recipe's factor, in [unit]; null for a row with no amount. */
        val exact: Rational? = authored?.quantity?.let { quantity ->
            (authored.unit.lineMultiple?.let { quantity * it } ?: quantity) * factor.value
        }

        /** [exact] in [target], a unit of [unit]'s dimension (or [unit] itself), exactly. */
        fun exactIn(target: QuantityUnit): Rational {
            val amount = exact!!
            if (target == unit) return amount
            return amount * checkNotNull(unit!!.baseMultiple) / checkNotNull(target.baseMultiple)
        }
    }

    /**
     * Rows of one entry in one unit — every `exact` unit of a dimension counting as one — added exactly: in their line
     * unit where they share one, else in the dimension's base unit.
     */
    private class Group(val parts: List<Part>) {
        val order: Int = parts.minOf { it.order }
        val unit: QuantityUnit = parts.map { it.unit!! }.distinct().singleOrNull() ?: parts.first().key!!
        val sum: Rational = parts.map { it.exactIn(unit) }.reduce(Rational::plus)

        /** For `piece`: halves where any of its rows is divisible. */
        val divisible: Boolean? = if (unit == QuantityUnit.PIECE) parts.any { it.authored!!.divisible == true } else null
    }

    private data class Draft(
        val order: Int,
        val name: ShoppingName,
        val quantity: ShoppingQuantity,
        val approximate: Boolean = false,
        val counted: List<CountedPart> = emptyList(),
        val sources: List<Part>,
    ) {
        fun line() = ShoppingLine(name, quantity, approximate, counted, sources.sortedBy { it.order }.map { it.recipe }.distinct())
    }
}
