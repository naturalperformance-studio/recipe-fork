package app.recipefork.core.api

import kotlinx.serialization.Serializable

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
    companion object {
        const val PATH = "/api/v1/dictionaries"
    }
}

/** An entry of the ingredient dictionary; [parentId] is null for a top-level entry (DOM-010). */
@Serializable
data class IngredientEntryDto(
    val id: Long,
    val labelRu: String,
    val labelEn: String,
    val parentId: Long? = null,
)

/** A tag of the tag dictionary. */
@Serializable
data class TagDto(
    val id: Long,
    val labelRu: String,
    val labelEn: String,
)
