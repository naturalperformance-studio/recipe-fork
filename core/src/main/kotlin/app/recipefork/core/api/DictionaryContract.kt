package app.recipefork.core.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.MessageDigest

/**
 * Both fixed dictionaries as the server serves them and the device holds them (FLOW-001).
 *
 * [version] changes whenever either dictionary changes in the store; the server also sends it
 * as the response's ETag, so a device that already holds this version is answered with 304.
 */
@Serializable
data class DictionariesDto(
    val version: String,
    val ingredients: List<IngredientEntryDto>,
    val tags: List<TagDto>,
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
 * that every recipe already naming it keeps its name (DOM-009, FLOW-001).
 *
 * Every field after [labelEn] has a default, which the encoding omits, so an entry that sets none of them encodes
 * exactly as before they existed, and the version digest of an unchanged dictionary stays what it was.
 */
@Serializable
data class IngredientEntryDto(
    val id: Long,
    val labelRu: String,
    val labelEn: String,
    val parentId: Long? = null,
    val divisible: Boolean? = null,
    val factors: Map<String, Double> = emptyMap(),
    val removed: Boolean = false,
) {
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

/** A tag of the tag dictionary. */
@Serializable
data class TagDto(
    val id: Long,
    val labelRu: String,
    val labelEn: String,
)
