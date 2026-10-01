package app.recipefork.app.card

import app.recipefork.app.dictionary.IngredientEntryEntity
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.AuthoredQuantity
import app.recipefork.core.scaling.InterfaceLanguage
import app.recipefork.core.scaling.QuantityText
import app.recipefork.core.scaling.Rational
import app.recipefork.core.scaling.RoundingGrid
import app.recipefork.core.scaling.ScaleFactor
import app.recipefork.core.scaling.Scaling
import app.recipefork.core.scaling.UnitLabels

/**
 * One ingredient line of the card, as text.
 *
 * [name] is the dictionary entry's label in the interface language or the free-text name, followed by the
 * qualifier; null when the row names nothing the device can show, which the screen words itself. [quantity] is
 * what `QuantityText` writes for the row, the unit's label alone where the row holds no quantity the card can
 * compute from, or null where it holds no unit either.
 */
data class IngredientLine(val name: String?, val quantity: String?)

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
): IngredientLine = IngredientLine(nameOf(ingredient, entries, language), quantityOf(ingredient, language, factor))

private fun nameOf(ingredient: Ingredient, entries: Map<Long, IngredientEntryEntity>, language: InterfaceLanguage): String? {
    val entry = ingredient.dictionaryEntry?.let(entries::get)
    val base = entry?.let { if (language == InterfaceLanguage.RUSSIAN) it.labelRu else it.labelEn }
        ?: ingredient.freeTextName?.trim()?.takeIf { it.isNotEmpty() }
        ?: return null
    val qualifier = ingredient.qualifier?.trim()?.takeIf { it.isNotEmpty() } ?: return base
    return "$base, $qualifier"
}

private fun quantityOf(ingredient: Ingredient, language: InterfaceLanguage, factor: ScaleFactor): String? {
    val unit = ingredient.unit
        ?: if (ingredient.unitType == UnitType.TASTE_UNMEASURED) QuantityUnit.TO_TASTE else return null
    return computed(unit, ingredient, language, factor) ?: UnitLabels.of(unit, language)
}

/** The quantity `:core` writes for the row, or null where the row holds nothing it can be computed from. */
private fun computed(unit: QuantityUnit, ingredient: Ingredient, language: InterfaceLanguage, factor: ScaleFactor): String? {
    if (unit.type.carriesQuantity) {
        val quantity = ingredient.quantity ?: return null
        if (quantity.signum() <= 0) return null
        val grid = runCatching { RoundingGrid.of(unit, ingredient.divisible) }.getOrNull() ?: return null
        // Only a value on its grid is written as authored; anything else is not the author's number.
        if (!grid.holds(unit.baseMultiple?.let { quantity * Rational.of(it) } ?: quantity)) return null
    }
    return runCatching {
        val authored = AuthoredQuantity(unit, if (unit.type.carriesQuantity) ingredient.quantity else null, ingredient.divisible)
        QuantityText.of(Scaling.display(authored, factor), language)
    }.getOrNull()
}
