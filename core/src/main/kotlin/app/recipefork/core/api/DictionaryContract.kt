package app.recipefork.core.api

import app.recipefork.core.search.DictionaryEntry
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.MessageDigest

/**
 * The fixed dictionaries as the server serves them and the device holds them (FLOW-001).
 *
 * [version] changes whenever any dictionary changes in the store; the server also sends it
 * as the response's ETag, so a device that already holds this version is answered with 304.
 *
 * [equipment] is maintained in the repository alone (dictionary/README.md): a snapshot or a server answer without it
 * reads as an empty one, and an empty one is not encoded, so the digest of dictionaries without equipment is what it
 * was before the field existed.
 */
@Serializable
data class DictionariesDto(
    val version: String,
    val ingredients: List<IngredientEntryDto>,
    val tags: List<TagDto>,
    val equipment: List<EquipmentEntryDto> = emptyList(),
) {
    /**
     * The version this content is given: the lowercase hex SHA-256 of the compact JSON of these dictionaries with an
     * empty version, whatever [version] holds now.
     */
    fun digest(): String {
        val content = Json.encodeToString(serializer(), copy(version = ""))
        val digest = MessageDigest.getInstance("SHA-256").digest(content.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /** These dictionaries with [version] set to their [digest]. */
    fun versioned(): DictionariesDto = copy(version = digest())

    companion object {
        const val PATH = "/api/v1/dictionaries"
    }
}

/**
 * An entry of the ingredient dictionary; [parentId] is null for a top-level entry (DOM-010).
 *
 * [divisible] decides whether a `piece` quantity of it may hold halves (decisions/20260920-divisibility-belongs-to-the-
 * ingredient.md); null while the dictionary does not say, and the author is then asked as for free text. [factors]
 * are its comparison factors (DOM-011, CAT-009): per unit code of [FACTOR_UNITS], a positive number on a scale shared by
 * its family; they only order, and nothing ever shows them. A [removed] entry is offered to nobody, yet stays so
 * that every recipe already naming it keeps its name (DOM-009, FLOW-001). [synonymsRu] and [synonymsEn] are the other
 * words cooks write for it in each language (картошка for картофель, icing sugar for powdered sugar): the field and
 * the book's search may find the entry by them, while its labels stay what it is shown as. [nutrition] is what 100 g of
 * the raw product holds, when the dictionary knows it; where the value comes from stays in the sources
 * (dictionary/README.md). [noNutrition] marks a child entry known to have no values of its own, which must not take its
 * parent's (a different form no source holds); such an entry has no [nutrition].
 *
 * Every field after [labelEn] has a default, which the encoding omits, so an entry that sets none of them encodes
 * exactly as before they existed, and the version digest of an unchanged dictionary stays what it was.
 */
@Serializable
data class IngredientEntryDto(
    override val id: Long,
    override val labelRu: String,
    override val labelEn: String,
    override val parentId: Long? = null,
    val divisible: Boolean? = null,
    val factors: Map<String, Double> = emptyMap(),
    override val removed: Boolean = false,
    override val synonymsRu: List<String> = emptyList(),
    override val synonymsEn: List<String> = emptyList(),
    val nutrition: NutritionDto? = null,
    val noNutrition: Boolean = false,
) : DictionaryEntry {
    companion object {
        /**
         * The unit codes a factor may be given for: `g` and `ml` for the exact units — a kilogram or a litre is
         * compared through its multiple of them — and `pc`, `tsp`, `tbsp`, `cup` and `pinch` each on its own.
         */
        val FACTOR_UNITS: Set<String> = linkedSetOf("g", "ml", "pc", "tsp", "tbsp", "cup", "pinch")

        /** Whether [factors] name only units of [FACTOR_UNITS], each with a finite number above zero. */
        fun validFactors(factors: Map<String, Double>): Boolean =
            factors.all { (unit, factor) -> unit in FACTOR_UNITS && factor.isFinite() && factor > 0 }
    }
}

/**
 * What 100 g of a raw product holds: its energy in [kcal], and its [protein], [fat] and [carbs] in grams. Each is a
 * finite number of at least zero, within [MAX_KCAL] and [MAX_GRAMS], and the three grams together are within
 * [MAX_GRAMS] too.
 */
@Serializable
data class NutritionDto(
    val kcal: Double,
    val protein: Double,
    val fat: Double,
    val carbs: Double,
) {
    companion object {
        /** The most energy 100 g may hold: pure fat holds about 900 kcal. */
        const val MAX_KCAL = 900.0

        /** The most grams of protein, fat and carbohydrate, each and together, that 100 g may hold. */
        const val MAX_GRAMS = 100.0
    }
}

/** A tag of the tag dictionary. */
@Serializable
data class TagDto(
    val id: Long,
    val labelRu: String,
    val labelEn: String,
)

/**
 * An entry of the equipment dictionary: a top-level entry is a group (stovetop cookware, appliances…), and its
 * leaves — [parentId] naming it — are the items a kitchen has. Like an ingredient entry, a [removed] entry is offered
 * to nobody yet keeps naming what holds it, and [synonymsRu] and [synonymsEn] are the other words cooks write for it.
 *
 * Every field after [labelEn] has a default, which the encoding omits.
 */
@Serializable
data class EquipmentEntryDto(
    override val id: Long,
    override val labelRu: String,
    override val labelEn: String,
    override val parentId: Long? = null,
    override val removed: Boolean = false,
    override val synonymsRu: List<String> = emptyList(),
    override val synonymsEn: List<String> = emptyList(),
) : DictionaryEntry
