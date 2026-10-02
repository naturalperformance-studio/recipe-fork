package app.recipefork.app.card

import app.recipefork.app.dictionary.IngredientEntryEntity
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.AuthoredQuantity
import app.recipefork.core.scaling.DisplayedQuantity
import app.recipefork.core.scaling.FixedAmount
import app.recipefork.core.scaling.InterfaceLanguage
import app.recipefork.core.scaling.QuantityText
import app.recipefork.core.scaling.Rational
import app.recipefork.core.scaling.Rounding
import app.recipefork.core.scaling.RoundingGrid
import app.recipefork.core.scaling.ScaleFactor
import app.recipefork.core.scaling.ScaledRecipe
import app.recipefork.core.scaling.ServingCount
import app.recipefork.core.scaling.Scaling
import app.recipefork.core.scaling.UnitLabels

/**
 * One ingredient line of the card, as text.
 *
 * [name] is the dictionary entry's label in the interface language or the free-text name, followed by the
 * qualifier; null when the row names nothing the device can show, which the screen words itself. [quantity] is
 * what `QuantityText` writes for the row, the unit's label alone where the row holds no quantity the card can
 * compute from, or null where it holds no unit either.
 *
 * [derived] is true exactly when [quantity] was computed at a scale other than the authored one (SCALE-004 (1)),
 * whatever its value; [rounding] says which way the grid moved it from the exact value, and reads [Rounding.UP] for
 * a quantity held at its floor (SCALE-005). A row shown unscaled is never derived.
 */
data class IngredientLine(
    val name: String?,
    val quantity: String?,
    val derived: Boolean = false,
    val rounding: Rounding = Rounding.NONE,
    /** True while the reader has fixed this row's amount (SCALE-002). */
    val fixed: Boolean = false,
    /** How the reader may fix this row's amount (SCALE-018), or null where the row offers no fixing. */
    val fixing: FixOffer? = null,
)

/** What fixing a row offers: the units the amount may be typed in, the author's first, and its divisibility. */
data class FixOffer(val units: List<QuantityUnit>, val authoredUnit: QuantityUnit, val divisible: Boolean?)

/**
 * The card's ingredient list at a scale: its [lines] in order, whether the displayed quantities still hold the
 * authored proportions — false once two or more lines are held at their floors (SCALE-009) — and the serving count
 * the scale displays, null for a recipe without one.
 */
data class IngredientList(
    val lines: List<IngredientLine>,
    val holdsProportions: Boolean,
    val servingCount: ServingCount? = null,
)

/** An amount the reader fixed on the card: of the row at [position], [amount] in [unit] as typed. */
data class CardFixing(val position: Int, val amount: Rational, val unit: QuantityUnit)

/**
 * How the card is scaled: at the serving count [requested] (null for a recipe with none), with the row the reader
 * fixed since, which sets the scale (SCALE-002). The card holds at most one fixing: fixing a row releases any other.
 */
data class CardScale(val requested: Rational?, val fixings: List<CardFixing> = emptyList()) {
    /** [fixing] sets the scale; any earlier fixing, of this row or another, is released. [requested] is kept. */
    fun fix(fixing: CardFixing): CardScale = copy(fixings = listOf(fixing))

    /** The row at [position] is no longer fixed; the count in force before the first fixing is again (SCALE-017). */
    fun release(position: Int): CardScale = copy(fixings = fixings.filter { it.position != position })

    /** The reader asks for [count]: every fixing is released (SCALE-016). */
    fun request(count: Rational): CardScale = CardScale(count)
}

/**
 * The line of [ingredient] at [factor] — the authored values at [ScaleFactor.ONE].
 *
 * A row the author has not finished, or whose value is off its unit's grid, is shown by its name and whatever it
 * holds: its unit, without a computed quantity. Nothing about a row makes this throw.
 */
fun ingredientLine(
    ingredient: Ingredient,
    entries: Map<Long, IngredientEntryEntity>,
    language: InterfaceLanguage,
    factor: ScaleFactor = ScaleFactor.ONE,
): IngredientLine {
    val scalable = scalableQuantity(ingredient)
    return lineOf(ingredient, entries, language, scalable?.let { Scaling.display(it, factor) })
}

/**
 * Every line of [ingredients], in the order given, for a recipe authored for [authoredServings] and shown at
 * [requestedServings] (SCALE-001).
 *
 * Every row that can be scaled is computed through one [ScaledRecipe], from the exact factor of the requested
 * count and never from a rounded one (SCALE-003); at the authored count every value is the authored one. A row
 * that cannot be scaled ([scalableQuantity]) is shown as [ingredientLine] shows it, unscaled, and stops no other
 * row from scaling. A recipe with no serving count is shown as authored.
 */
fun ingredientLines(
    ingredients: List<Ingredient>,
    entries: Map<Long, IngredientEntryEntity>,
    language: InterfaceLanguage,
    authoredServings: Int?,
    requestedServings: Rational?,
): List<IngredientLine> = ingredientList(ingredients, entries, language, authoredServings, requestedServings).lines

/**
 * The list [ingredientLines] shows, with whether it still holds the authored proportions (SCALE-009): only rows
 * that scale are counted, and a list shown as authored always holds them.
 */
fun ingredientList(
    ingredients: List<Ingredient>,
    entries: Map<Long, IngredientEntryEntity>,
    language: InterfaceLanguage,
    authoredServings: Int?,
    requestedServings: Rational?,
): IngredientList = ingredientList(ingredients, entries, language, authoredServings, CardScale(requestedServings))

/**
 * The list at [scale]: every row that can be scaled is computed through one [ScaledRecipe] from the exact factor in
 * force — that of the fixing, or of the requested count with none (SCALE-002, SCALE-003) — and
 * every derived marker and the proportions warning follow it whichever set it. A fixing of a row that offers none is
 * ignored. A recipe with no serving count shows no count, and is scaled by a fixing all the same.
 */
fun ingredientList(
    ingredients: List<Ingredient>,
    entries: Map<Long, IngredientEntryEntity>,
    language: InterfaceLanguage,
    authoredServings: Int?,
    scale: CardScale,
): IngredientList {
    val scalable = ingredients.map(::scalableQuantity)
    val counted = authoredServings?.takeIf { it >= 1 }
    // A recipe without a count is scaled as one serving; only a fixing can move it, and no count is shown.
    val authored = counted ?: 1
    val requested = counted?.let { scale.requested ?: Rational.of(it.toLong()) } ?: Rational.ONE
    val indices = HashMap<Int, Int>()
    scalable.forEachIndexed { i, quantity -> if (quantity != null) indices[i] = indices.size }
    var scaled = ScaledRecipe.of(authored, scalable.filterNotNull(), requested)
    for (fixing in scale.fixings) {
        val i = ingredients.indexOfFirst { it.position == fixing.position }.takeIf { it >= 0 } ?: continue
        val authoredQuantity = scalable[i]?.takeIf { it.quantity != null } ?: continue
        if (fixing.unit !in FixedAmount.units(authoredQuantity.unit) || fixing.amount.signum() <= 0) continue
        scaled = scaled.fix(indices.getValue(i), fixing.amount, fixing.unit)
    }
    val lines = ingredients.mapIndexed { i, ingredient ->
        val line = scalable[i]?.let { scaled.lines[indices.getValue(i)] }
        val shown = lineOf(ingredient, entries, language, line?.quantity)
        if (line == null) return@mapIndexed shown
        val offer = scalable[i]?.takeIf { it.quantity != null }?.let { FixOffer(FixedAmount.units(it.unit), it.unit, it.divisible) }
        shown.copy(
            fixed = line.fixed,
            fixing = offer,
        )
    }
    return IngredientList(lines, holdsProportions = scaled.holdsProportions, servingCount = counted?.let { scaled.servingCount })
}

/**
 * What scaling reads of [ingredient], or null where the row is incomplete or off its grid and so is shown
 * unscaled: a unit that carries a quantity with none, or none above zero; a `piece` without its divisibility; a
 * value that is not on its unit's grid; anything [AuthoredQuantity] refuses. The one place the card decides it.
 */
internal fun scalableQuantity(ingredient: Ingredient): AuthoredQuantity? {
    val unit = unitOf(ingredient) ?: return null
    val quantity = if (unit.type.carriesQuantity) ingredient.quantity ?: return null else null
    if (quantity != null) {
        if (quantity.signum() <= 0) return null
        val grid = runCatching { RoundingGrid.of(unit, ingredient.divisible) }.getOrNull() ?: return null
        // Only a value on its grid is the author's number; anything else is not scaled from.
        if (!grid.holds(unit.baseMultiple?.let { quantity * Rational.of(it) } ?: quantity)) return null
    }
    return runCatching { AuthoredQuantity(unit, quantity, ingredient.divisible) }.getOrNull()
}

private fun unitOf(ingredient: Ingredient): QuantityUnit? =
    ingredient.unit ?: if (ingredient.unitType == UnitType.TASTE_UNMEASURED) QuantityUnit.TO_TASTE else null

private fun lineOf(
    ingredient: Ingredient,
    entries: Map<Long, IngredientEntryEntity>,
    language: InterfaceLanguage,
    quantity: DisplayedQuantity?,
): IngredientLine {
    val computed = quantity?.let { runCatching { QuantityText.of(it, language) }.getOrNull() }
    val name = nameOf(ingredient, entries, language)
    if (computed == null) return IngredientLine(name, unitOf(ingredient)?.let { UnitLabels.of(it, language) })
    return IngredientLine(name, computed, derived = quantity.derived, rounding = quantity.rounding)
}

private fun nameOf(ingredient: Ingredient, entries: Map<Long, IngredientEntryEntity>, language: InterfaceLanguage): String? {
    val entry = ingredient.dictionaryEntry?.let(entries::get)
    val base = entry?.let { if (language == InterfaceLanguage.RUSSIAN) it.labelRu else it.labelEn }
        ?: ingredient.freeTextName?.trim()?.takeIf { it.isNotEmpty() }
        ?: return null
    val qualifier = ingredient.qualifier?.trim()?.takeIf { it.isNotEmpty() } ?: return base
    return "$base, $qualifier"
}
