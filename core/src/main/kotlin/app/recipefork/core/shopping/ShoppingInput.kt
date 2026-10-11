package app.recipefork.core.shopping

import app.recipefork.core.api.IngredientEntryDto
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.scaling.Rational

/**
 * What the shopping list reads of a dictionary entry: its [divisible] flag (null where the dictionary says nothing) and
 * its measures — its comparison [factors], grams per unit, under the unit codes of [IngredientEntryDto.FACTOR_UNITS].
 */
data class EntryMeasures(
    val divisible: Boolean? = null,
    val factors: Map<String, Double> = emptyMap(),
) {
    companion object {
        /** The measures of [entry]. */
        fun of(entry: IngredientEntryDto): EntryMeasures = EntryMeasures(entry.divisible, entry.factors)

        /** The measures of every entry of [entries], by its id. */
        fun of(entries: Collection<IngredientEntryDto>): Map<Long, EntryMeasures> = entries.associate { it.id to of(it) }
    }
}

/**
 * One recipe's contribution to a shopping list: its ingredient rows, the serving count it was authored for (null for
 * none) and the count the reader asked for (null for the authored one). A recipe with no serving count is taken as
 * authored, as the card shows it, whatever count is asked for.
 */
data class ShoppingRecipe(
    val recipe: RecipeId,
    val ingredients: List<Ingredient>,
    val authoredServings: Int?,
    val requestedServings: Rational? = null,
)
