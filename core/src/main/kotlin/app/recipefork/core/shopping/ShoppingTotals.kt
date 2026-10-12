package app.recipefork.core.shopping

import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.UnitSystem
import app.recipefork.core.scaling.AuthoredQuantity
import app.recipefork.core.scaling.Rational
import app.recipefork.core.scaling.RoundingGrid
import app.recipefork.core.scaling.ScaleFactor
import app.recipefork.core.scaling.ScaledRecipe
import app.recipefork.core.scaling.Scaling
import app.recipefork.core.scaling.UnitConversion
import app.recipefork.core.scaling.measureOf

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
 * - In a reader's system (v0.35), a measured line's exact total — merged, in the unit of its largest part — is
 *   converted once by [UnitConversion.convert] and rounded on the shown unit's grid, as the card shows an amount
 *   ([ShoppingLine.shown]). On a line of several parts shown approximate, each part written in a unit that does not
 *   convert exactly into the shown one (another dimension, or a household unit) is noted with what it counted as in
 *   the shown unit, so a note names the chosen system's unit ("2 cups counted as ≈ 260 g" in Metric); a line of one
 *   part converted through the dictionary notes nothing, its "≈" says enough, as on the card. The line itself — its unit,
 *   its amount, what it merged — stays as the recipes are written, so that what a line is does not change with the
 *   system.
 *
 * Lines stand in the order of their first row, recipe by recipe. A recipe given twice counts twice.
 *
 * This is a deliberate departure from DOM-011 for the shopping list alone: here an entry's factors do yield a shown,
 * approximate amount.
 */
object ShoppingTotals {

    /**
     * The lines of a shopping list made from [recipes], reading each dictionary entry's measures from [entries]; each
     * measured line also [shown][ShoppingLine.shown] in [system], where one is given.
     */
    fun of(recipes: List<ShoppingRecipe>, entries: Map<Long, EntryMeasures>, system: UnitSystem? = null): List<ShoppingLine> {
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
                    else -> drafts += own(name, Group(listOf(part)), emptyMap(), system)
                }
            }
        }
        byEntry.forEach { (id, parts) -> drafts += entryLines(ShoppingName.Entry(id), parts, entries[id], system) }
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

    private fun entryLines(name: ShoppingName.Entry, parts: List<Part>, measures: EntryMeasures?, system: UnitSystem?): List<Draft> {
        val (taste, amounts) = parts.partition { it.authored!!.unit == QuantityUnit.TO_TASTE }
        val groups = amounts.groupBy { it.key }.values.map(::Group)
        val factors = measures?.factors.orEmpty()
        val (convertible, unmeasured) = groups.partition { measureOf(factors, it.unit) != null }
        val lines = mutableListOf<Draft>()
        if (convertible.size >= 2) lines += merged(name, convertible, factors, system) else convertible.mapTo(lines) { own(name, it, factors, system) }
        unmeasured.mapTo(lines) { own(name, it, factors, system) }
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

    private fun own(name: ShoppingName, group: Group, factors: Map<String, Double>, system: UnitSystem?): Draft = Draft(
        group.order, name, measured(group.unit, group.sum, group.divisible), sources = group.parts,
        shown = system?.let { shownIn(it, group, group.sum, listOf(group to group.sum), factors, approximate = false) },
    )

    private fun merged(name: ShoppingName, groups: List<Group>, factors: Map<String, Double>, system: UnitSystem?): Draft {
        val grams = groups.associateWith { it.sum * measureOf(factors, it.unit)!! }
        val target = groups.maxBy { grams.getValue(it) }
        val targetFactor = measureOf(factors, target.unit)!!
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
            shown = system?.let { into ->
                val inTarget = groups.map { it to (others[it] ?: it.sum) }
                shownIn(into, target, total, inTarget, factors, approximate = true)
            },
        )
    }

    /**
     * A line in [target]'s unit, [total] there exactly, shown in [system]; [parts] are its groups, each with its exact
     * amount in [target]'s unit. On an approximate line of several parts, a part is noted where its unit does not go
     * exactly into the shown one; a line shown exactly, or of one part, notes none.
     */
    private fun shownIn(
        system: UnitSystem,
        target: Group,
        total: Rational,
        parts: List<Pair<Group, Rational>>,
        factors: Map<String, Double>,
        approximate: Boolean,
    ): ShoppingShown {
        val converted = UnitConversion.convert(target.unit, total, factors, system, divisible = target.divisible)
        val unit = converted.quantity.unit
        val grid = checkNotNull(RoundingGrid.of(unit, target.divisible.takeIf { unit == target.unit }))
        val inexact = approximate || converted.approximate
        // A line of one part converted through the dictionary is that part: "≈" says enough, as on the card.
        val noted = parts.takeIf { inexact && it.size > 1 }.orEmpty()
        val counted = noted.filter { (group, _) -> !convertsExactly(group.unit, unit) }.map { (group, inTarget) ->
            val inShown = inTarget / converted.writtenPerShown
            CountedPart(amount(group.unit, group.sum, group.divisible), ShoppingAmount(unit, grid.nearest(inShown)), inShown < grid.floor)
        }
        return ShoppingShown(ShoppingAmount(unit, checkNotNull(converted.quantity.value)), inexact, counted)
    }

    /** Whether an amount in [from] reads in [to] by fixed ratios alone: one unit, or `exact` units of one dimension. */
    private fun convertsExactly(from: QuantityUnit, to: QuantityUnit): Boolean =
        from == to || (from.dimension != null && from.dimension == to.dimension)

    private fun measured(unit: QuantityUnit, exact: Rational, divisible: Boolean?) =
        ShoppingQuantity.Measured(amount(unit, exact, divisible))

    /** [exact] rounded once, on the grid of [unit]. */
    private fun amount(unit: QuantityUnit, exact: Rational, divisible: Boolean?): ShoppingAmount =
        ShoppingAmount(unit, checkNotNull(RoundingGrid.of(unit, divisible)).nearest(exact))

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
        val shown: ShoppingShown? = null,
    ) {
        fun line() = ShoppingLine(name, quantity, approximate, counted, sources.sortedBy { it.order }.map { it.recipe }.distinct(), shown)
    }
}
