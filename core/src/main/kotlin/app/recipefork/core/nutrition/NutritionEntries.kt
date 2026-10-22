package app.recipefork.core.nutrition

import app.recipefork.core.api.IngredientEntryDto
import app.recipefork.core.api.NutritionDto

/**
 * What the nutrition of a dish reads of a dictionary entry: its [parentId] (DOM-010), its measures — its [factors],
 * grams per unit, under the unit codes of [IngredientEntryDto.FACTOR_UNITS] — and what 100 g of it holds, null where
 * the dictionary does not say. [noNutrition] marks an entry known to have no values of its own, which does not take its
 * parent's.
 */
data class NutritionEntry(
    val parentId: Long? = null,
    val factors: Map<String, Double> = emptyMap(),
    val nutrition: NutritionDto? = null,
    val noNutrition: Boolean = false,
) {
    companion object {
        /** What [entry] gives the nutrition of a dish. */
        fun of(entry: IngredientEntryDto): NutritionEntry =
            NutritionEntry(entry.parentId, entry.factors, entry.nutrition, entry.noNutrition)
    }
}

/** The dictionary entries the nutrition of a dish reads, by id; however they are held. */
fun interface NutritionEntries {
    /** The entry of [id]; null where the dictionary holds none. */
    fun entry(id: Long): NutritionEntry?

    companion object {
        /** The entries of [entries], by their ids. */
        fun of(entries: Collection<IngredientEntryDto>): NutritionEntries {
            val byId = entries.associate { it.id to NutritionEntry.of(it) }
            return NutritionEntries(byId::get)
        }
    }
}
