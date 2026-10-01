package app.recipefork.app.card

import app.recipefork.app.dictionary.IngredientEntryEntity
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.AuthoredQuantity
import app.recipefork.core.scaling.DisplayedQuantity
import app.recipefork.core.scaling.InterfaceLanguage
import app.recipefork.core.scaling.QuantityText
import app.recipefork.core.scaling.Rational
import app.recipefork.core.scaling.RoundingGrid
import app.recipefork.core.scaling.ScaleFactor
import app.recipefork.core.scaling.ScaledRecipe
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
): List<IngredientLine> {
    val scalable = ingredients.map(::scalableQuantity)
    val scaled = authoredServings?.takeIf { it >= 1 }?.let { authored ->
        val requested = requestedServings ?: Rational.of(authored.toLong())
        ScaledRecipe.of(authored, scalable.filterNotNull(), requested)
    }
    var next = 0
    return ingredients.mapIndexed { i, ingredient ->
        val quantity = scalable[i]?.let { authored ->
            scaled?.lines?.get(next++)?.quantity ?: Scaling.display(authored, ScaleFactor.ONE)
        }
        lineOf(ingredient, entries, language, quantity)
    }
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
    val text = quantity?.let { runCatching { QuantityText.of(it, language) }.getOrNull() }
        ?: unitOf(ingredient)?.let { UnitLabels.of(it, language) }
    return IngredientLine(nameOf(ingredient, entries, language), text)
}

private fun nameOf(ingredient: Ingredient, entries: Map<Long, IngredientEntryEntity>, language: InterfaceLanguage): String? {
    val entry = ingredient.dictionaryEntry?.let(entries::get)
    val base = entry?.let { if (language == InterfaceLanguage.RUSSIAN) it.labelRu else it.labelEn }
        ?: ingredient.freeTextName?.trim()?.takeIf { it.isNotEmpty() }
        ?: return null
    val qualifier = ingredient.qualifier?.trim()?.takeIf { it.isNotEmpty() } ?: return base
    return "$base, $qualifier"
}
