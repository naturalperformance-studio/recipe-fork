package app.recipefork.core.file

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `recipe.json`, the document of a recipe file (decisions/20260928-recipe-file-is-a-zip.md), as it is written.
 *
 * Every value is plain JSON — text, whole numbers, booleans — so that a reader can check each field against the
 * rules a sending must pass before anything of it is believed: enumerations are written as their names in the
 * domain model (`taste_unmeasured`, `gram`), a quantity as an exact fraction (`"3/2"`), a timer in whole minutes.
 */
@Serializable
internal data class RecipeDocument(
    @SerialName("format_version") val formatVersion: Int,
    val recipe: DocumentRecipe,
    val author: DocumentAuthor? = null,
    val parent: DocumentParent? = null,
    val media: List<DocumentMedia> = emptyList(),
)

@Serializable
internal data class DocumentRecipe(
    val id: String,
    val title: String,
    val description: String,
    /** `ru` or `en`; absent while undeclared. */
    val language: String? = null,
    val tags: List<Long>,
    val servings: Int? = null,
    val ingredients: List<DocumentIngredient>,
    /** The headings of the ingredient list, in order; added in format 2, absent from format 1. */
    @SerialName("ingredient_groups") val ingredientGroups: List<DocumentGroup> = emptyList(),
    val steps: List<DocumentStep>,
)

/** A heading of the ingredient list: its name, and the position of the first ingredient under it. */
@Serializable
internal data class DocumentGroup(val name: String, @SerialName("first_ingredient") val firstIngredient: Int)

@Serializable
internal data class DocumentIngredient(
    val position: Int,
    @SerialName("dictionary_entry") val dictionaryEntry: Long? = null,
    @SerialName("free_text_name") val freeTextName: String? = null,
    val qualifier: String? = null,
    @SerialName("unit_type") val unitType: String,
    val unit: String? = null,
    val quantity: String? = null,
    @SerialName("piece_weight") val pieceWeight: String? = null,
    val divisible: Boolean? = null,
    @SerialName("entered_quantity") val enteredQuantity: String? = null,
    /** How the ingredient is prepared; added in format 2, absent for none. */
    val note: String? = null,
)

@Serializable
internal data class DocumentStep(
    val position: Int,
    val text: String,
    val references: List<DocumentReference> = emptyList(),
    val timer: DocumentTimer? = null,
)

@Serializable
internal data class DocumentReference(
    @SerialName("ingredient_position") val ingredientPosition: Int,
    @SerialName("text_offset") val textOffset: Int,
    /** The part of the ingredient the reference reads; absent for its whole quantity. */
    val share: DocumentShare? = null,
)

/** A part of an ingredient: an exact fraction in a unit, named as the ingredient's unit is. */
@Serializable
internal data class DocumentShare(val quantity: String, val unit: String)

@Serializable
internal data class DocumentTimer(val minutes: Long, val label: String? = null)

@Serializable
internal data class DocumentAuthor(val nickname: String)

@Serializable
internal data class DocumentParent(val id: String, val title: String? = null, val nickname: String? = null)

@Serializable
internal data class DocumentMedia(
    val id: String,
    /** `photo` or `video`. */
    val kind: String,
    /** The step it belongs to; absent for the recipe's cover. */
    val step: Int? = null,
    /** Its entry's name within the archive, under `media/`. */
    val entry: String,
)

/** Only the version, read before anything else so that a later format is refused, not misread. */
@Serializable
internal data class DocumentVersion(@SerialName("format_version") val formatVersion: Int)
