package app.recipefork.app.card

import app.recipefork.app.dictionary.IngredientEntryEntity
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.UnitSystem
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.AuthoredQuantity
import app.recipefork.core.scaling.DisplayedQuantity
import app.recipefork.core.scaling.FixedAmount
import app.recipefork.core.scaling.InterfaceLanguage
import app.recipefork.core.scaling.QuantityText
import app.recipefork.core.scaling.Rational
import app.recipefork.core.scaling.Rounding
import app.recipefork.core.scaling.ScaleFactor
import app.recipefork.core.scaling.ScaledRecipe
import app.recipefork.core.scaling.ServingCount
import app.recipefork.core.scaling.Scaling
import app.recipefork.core.scaling.ShownQuantity
import app.recipefork.core.scaling.UnitConversion
import app.recipefork.core.scaling.UnitLabels

/**
 * One ingredient line of the card, as text.
 *
 * [name] is the dictionary entry's label in the interface language or the free-text name, followed by the
 * qualifier; null when the row names nothing the device can show, which the screen words itself. [note] is how the
 * ingredient is prepared, as the author wrote it — never scaled — shown after the name (DOM-023). [quantity] is
 * what `QuantityText` writes for the row, standing before the name; the unit's label alone where the row holds a
 * quantity the card cannot compute from; null where the row holds no quantity yet — a bare unit before a name reads
 * as nothing — and for a row to taste, whose [toTaste] words follow the name instead ("salt — to taste").
 * A quantity converted into the reader's system through the entry's measures starts with "≈" ("≈ 2 cups").
 *
 * [derived] is true exactly when [quantity] was computed at a scale other than the authored one (SCALE-004 (1)),
 * or converted into another unit for the reader's system (v0.35), whatever its value — the card says so once for its
 * whole list; [rounding] says which way the grid moved it from the exact value, shown as an arrow after the quantity,
 * and reads [Rounding.UP] for a quantity held at its floor (SCALE-005). A row shown unscaled and as written is never
 * derived.
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
    /** How the ingredient is prepared (DOM-023), as written at every scale; null for none. */
    val note: String? = null,
    /** "to taste" in the interface language for a row measured by taste, read after its name; null for any other row. */
    val toTaste: String? = null,
)

/**
 * What fixing a row offers: the units the amount may be typed in, the one shown first ([authoredUnit] — the author's,
 * or on a converted line the unit it is shown in), and its divisibility. A row shown converted is fixed in its
 * [written] unit, each shown unit holding [writtenPerShown] of the written one's line unit ([ShownQuantity]).
 */
data class FixOffer(
    val units: List<QuantityUnit>,
    val authoredUnit: QuantityUnit,
    val divisible: Boolean?,
    val written: QuantityUnit = authoredUnit,
    val writtenPerShown: Rational = Rational.ONE,
) {
    /**
     * The fixing of the row at [position] by [amount] typed in [unit]: as typed where the row is shown as written;
     * otherwise turned back into the written unit's line unit by the ratio the line was shown by, so that the scale is
     * set in the written unit and the line, shown again by the same ratio, reads the typed value.
     */
    fun fixing(position: Int, amount: Rational, unit: QuantityUnit): CardFixing {
        if (writtenPerShown == Rational.ONE && unit in FixedAmount.units(written)) return CardFixing(position, amount, unit)
        val shown = unit.lineMultiple?.let { amount * it } ?: amount
        return CardFixing(position, shown * writtenPerShown, written.lineUnit ?: written)
    }

    /** [fixing] as this offer's control shows it: in one of its [units], turned from the written unit where it is not. */
    fun typed(fixing: CardFixing): CardFixing {
        if (fixing.unit in units) return fixing
        val written = fixing.unit.lineMultiple?.let { fixing.amount * it } ?: fixing.amount
        return CardFixing(fixing.position, written / writtenPerShown, authoredUnit)
    }
}

/**
 * The card's ingredient list at a scale: its [lines] in order, whether the displayed quantities still hold the
 * authored proportions — false once two or more lines are held at their floors (SCALE-009) — and the serving count
 * the scale displays, null for a recipe without one.
 */
data class IngredientList(
    val lines: List<IngredientLine>,
    val holdsProportions: Boolean,
    val servingCount: ServingCount? = null,
    /** The exact factor the lines were scaled by, which a step's part of an ingredient is scaled by too. */
    val factor: ScaleFactor = ScaleFactor.ONE,
)

/**
 * An amount the reader fixed on the card: of the row at [position], [amount] in [unit] — as typed, or, typed on a line
 * shown converted, in the written unit ([FixOffer.fixing]).
 */
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

    companion object {
        /**
         * The scale a card opens at, for a recipe authored for [authored] servings: the reader's [usual] count where
         * one is set, the authored count otherwise (FLOW-017 (1, 2)); a recipe with no count ignores the setting.
         */
        fun opening(authored: Int?, usual: Rational?): CardScale =
            CardScale(authored?.takeIf { it >= 1 }?.let { usual ?: Rational.of(it.toLong()) })
    }
}

/**
 * The line of [ingredient] at [factor] — the authored values at [ScaleFactor.ONE].
 *
 * A row the author has not finished is shown by its name alone; one whose value is off its unit's grid by its name and
 * its unit, without a computed quantity. Nothing about a row makes this throw.
 */
fun ingredientLine(
    ingredient: Ingredient,
    entries: Map<Long, IngredientEntryEntity>,
    language: InterfaceLanguage,
    factor: ScaleFactor = ScaleFactor.ONE,
): IngredientLine {
    val scalable = scalableQuantity(ingredient, entries)
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

/** The measures of the entry [ingredient] names among [entries], by which its amounts convert; none for free text. */
internal fun factorsOf(ingredient: Ingredient, entries: Map<Long, IngredientEntryEntity>): Map<String, Double> =
    ingredient.dictionaryEntry?.let(entries::get)?.factors.orEmpty()

/**
 * [shown] as a line or a reference writes it, for an amount written in [written]: derived where scaling computed it or
 * where it is shown in another unit than written, so that a converted amount carries its rounding arrow and the list
 * reads as recalculated.
 */
internal fun marked(shown: ShownQuantity, written: QuantityUnit): DisplayedQuantity {
    val converted = shown.quantity.unit != (written.lineUnit ?: written)
    return if (converted && !shown.quantity.derived) shown.quantity.copy(derived = true) else shown.quantity
}

/** A quantity's text marked approximate where it was converted through the entry's measures: "≈ 260 g". */
internal fun approximately(text: String, approximate: Boolean): String = if (approximate) "≈$NO_BREAK$text" else text

/** A space that never breaks, so "≈" stays with its number. */
private const val NO_BREAK = "\u00A0"

/**
 * The list at [scale]: every row that can be scaled is computed through one [ScaledRecipe] from the exact factor in
 * force — that of the fixing, or of the requested count with none (SCALE-002, SCALE-003) — and
 * every derived marker and the proportions warning follow it whichever set it. A fixing of a row that offers none is
 * ignored. A recipe with no serving count shows no count, and is scaled by a fixing all the same.
 *
 * In [system], each scaled amount is then shown in the reader's system ([UnitConversion.display]) by its entry's
 * measures, "≈" where converted through them, and offers fixing in the unit shown; a null [system] shows every amount
 * in the unit it was written in. The proportions warning counts the lines held at their floors as shown.
 */
fun ingredientList(
    ingredients: List<Ingredient>,
    entries: Map<Long, IngredientEntryEntity>,
    language: InterfaceLanguage,
    authoredServings: Int?,
    scale: CardScale,
    system: UnitSystem? = null,
): IngredientList {
    val scalable = ingredients.map { scalableQuantity(it, entries) }
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
    val quantities = ingredients.mapIndexed { i, ingredient ->
        val authoredQuantity = scalable[i] ?: return@mapIndexed null
        system?.let { UnitConversion.display(authoredQuantity, scaled.factor, factorsOf(ingredient, entries), it) }
            ?: ShownQuantity(scaled.lines[indices.getValue(i)].quantity, approximate = false, writtenPerShown = Rational.ONE)
    }
    val lines = ingredients.mapIndexed { i, ingredient ->
        val authoredQuantity = scalable[i]
        val shown = quantities[i]
        if (authoredQuantity == null || shown == null) return@mapIndexed lineOf(ingredient, entries, language, null)
        val line = scaled.lines[indices.getValue(i)]
        val written = lineOf(ingredient, entries, language, marked(shown, authoredQuantity.unit), shown.approximate)
        val offer = authoredQuantity.takeIf { it.quantity != null }?.let {
            if (shown.quantity.unit == (it.unit.lineUnit ?: it.unit)) {
                FixOffer(FixedAmount.units(it.unit), it.unit, it.divisible)
            } else {
                FixOffer(FixedAmount.units(shown.quantity.unit), shown.quantity.unit, it.divisible, it.unit, shown.writtenPerShown)
            }
        }
        written.copy(
            fixed = line.fixed,
            fixing = offer,
        )
    }
    return IngredientList(
        lines,
        holdsProportions = quantities.count { it?.quantity?.heldAtFloor == true } < 2,
        servingCount = counted?.let { scaled.servingCount },
        factor = scaled.factor,
    )
}

/**
 * What scaling reads of [ingredient], or null where the row is incomplete or off its grid and so is shown unscaled
 * (`Scaling.authored`). A `piece` row is read with its effective divisibility: the flag of the dictionary entry it
 * names among [entries] where the entry sets one, the row's own otherwise. The one place the card decides it.
 */
internal fun scalableQuantity(ingredient: Ingredient, entries: Map<Long, IngredientEntryEntity>): AuthoredQuantity? =
    Scaling.authored(ingredient, ingredient.dictionaryEntry?.let(entries::get)?.divisible)

private fun unitOf(ingredient: Ingredient): QuantityUnit? =
    ingredient.unit ?: if (ingredient.unitType == UnitType.TASTE_UNMEASURED) QuantityUnit.TO_TASTE else null

private fun lineOf(
    ingredient: Ingredient,
    entries: Map<Long, IngredientEntryEntity>,
    language: InterfaceLanguage,
    quantity: DisplayedQuantity?,
    approximate: Boolean = false,
): IngredientLine {
    val name = nameOf(ingredient, entries, language)
    // The note is the author's words, whatever the scale (DOM-023).
    val note = Ingredient.noteOf(ingredient.note)
    val unit = unitOf(ingredient)
    // "to taste" is no amount: it follows the name, as a cook says it — "salt — to taste", «соль — по вкусу».
    if (unit == QuantityUnit.TO_TASTE) return IngredientLine(name, null, note = note, toTaste = UnitLabels.of(unit, language))
    val computed = quantity?.let { runCatching { QuantityText.of(it, language) }.getOrNull() }?.let { approximately(it, approximate) }
    if (computed == null) {
        // A unit stands only by the number it measures: a row with no quantity yet shows none ("Grains of paradise").
        val shown = unit?.takeIf { ingredient.quantity != null }?.let { UnitLabels.of(it, language) }
        return IngredientLine(name, shown, note = note)
    }
    return IngredientLine(name, computed, derived = quantity.derived, rounding = quantity.rounding, note = note)
}

private fun nameOf(ingredient: Ingredient, entries: Map<Long, IngredientEntryEntity>, language: InterfaceLanguage): String? {
    val entry = ingredient.dictionaryEntry?.let(entries::get)
    val base = entry?.let { if (language == InterfaceLanguage.RUSSIAN) it.labelRu else it.labelEn }
        ?: ingredient.freeTextName?.trim()?.takeIf { it.isNotEmpty() }
        ?: return null
    val qualifier = ingredient.qualifier?.trim()?.takeIf { it.isNotEmpty() } ?: return base
    return "$base, $qualifier"
}
