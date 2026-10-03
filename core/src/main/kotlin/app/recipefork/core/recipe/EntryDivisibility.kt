package app.recipefork.core.recipe

import app.recipefork.core.api.IngredientEntryDto

/**
 * The divisibility a row is read with (decisions/20260920-divisibility-belongs-to-the-ingredient.md): the flag of the
 * dictionary entry the row names, when the entry sets one, and otherwise what the row itself holds — the author's
 * answer for free text, or for an entry the dictionary says nothing about.
 *
 * [entryDivisible] is the flag of the row's entry, null when the row names none or the entry sets none.
 */
fun effectiveDivisible(row: Ingredient, entryDivisible: Boolean?): Boolean? =
    if (row.dictionaryEntry != null && entryDivisible != null) entryDivisible else row.divisible

/** [effectiveDivisible] for a row and the dictionary [entry] it names; an entry other than the row's is not read. */
fun effectiveDivisible(row: Ingredient, entry: IngredientEntryDto?): Boolean? =
    effectiveDivisible(row, entry?.takeIf { it.id == row.dictionaryEntry }?.divisible)

/**
 * The comparison factor (DOM-011, CAT-009) of [unit] among an entry's [factors]: an exact unit through its multiple of
 * the gram or the millilitre, every other unit by its own code; null when the entry gives none for it, and always for
 * `taste_unmeasured`, which holds no quantity. It orders and nothing else.
 */
fun comparisonFactor(factors: Map<String, Double>, unit: QuantityUnit): Double? {
    val base = unit.baseUnit
    if (base != null) return factors[factorCode(base)]?.times(unit.baseMultiple!!)
    return factors[factorCode(unit) ?: return null]
}

/** The code a factor of [unit] is held under (IngredientEntryDto.FACTOR_UNITS); null for a unit holding none. */
private fun factorCode(unit: QuantityUnit): String? = when (unit) {
    QuantityUnit.GRAM -> "g"
    QuantityUnit.MILLILITRE -> "ml"
    QuantityUnit.PIECE -> "pc"
    QuantityUnit.TEASPOON -> "tsp"
    QuantityUnit.TABLESPOON -> "tbsp"
    QuantityUnit.CUP -> "cup"
    QuantityUnit.PINCH -> "pinch"
    else -> null
}
