package app.recipefork.app.book

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import app.recipefork.core.recipe.CollectionOrigin
import app.recipefork.core.recipe.MediaKind
import app.recipefork.core.recipe.MediaVisibility
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.RecipeLanguage
import app.recipefork.core.recipe.RecipeState
import app.recipefork.core.recipe.SentStatus
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.Rational
import java.time.Instant

/** A recipe written in this book, in any of its eight states (FLOW-038: held in full, never evicted). */
@Entity(tableName = "recipe", indices = [Index("content_changed_at")])
data class RecipeEntity(
    @PrimaryKey @ColumnInfo(name = "recipe_id") val recipeId: String,
    val owner: String?,
    val parent: String?,
    val sent: SentStatus,
    @ColumnInfo(name = "stated_author") val statedAuthor: String?,
    @ColumnInfo(name = "stated_parent_title") val statedParentTitle: String?,
    @ColumnInfo(name = "stated_parent_nickname") val statedParentNickname: String?,
    val title: String,
    val description: String,
    val language: RecipeLanguage?,
    val servings: Int?,
    val state: RecipeState,
    @ColumnInfo(name = "replaces_parent") val replacesParent: Boolean,
    @ColumnInfo(name = "was_published") val wasPublished: Boolean,
    @ColumnInfo(name = "safety_flag") val safetyFlag: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "content_changed_at") val contentChangedAt: Instant,
    /** The text the recipe was made from, exactly as pasted; null for none (version 10). */
    @ColumnInfo(name = "source_text") val sourceText: String? = null,
)

private const val CASCADE = ForeignKey.CASCADE

@Entity(
    tableName = "ingredient",
    primaryKeys = ["recipe_id", "position"],
    foreignKeys = [ForeignKey(RecipeEntity::class, ["recipe_id"], ["recipe_id"], onDelete = CASCADE)],
)
data class IngredientEntity(
    @ColumnInfo(name = "recipe_id") val recipeId: String,
    val position: Int,
    @ColumnInfo(name = "dictionary_entry") val dictionaryEntry: Long?,
    val qualifier: String?,
    @ColumnInfo(name = "free_text_name") val freeTextName: String?,
    @ColumnInfo(name = "unit_type") val unitType: UnitType,
    val unit: QuantityUnit?,
    val quantity: Rational?,
    @ColumnInfo(name = "piece_weight") val pieceWeight: Rational?,
    val divisible: Boolean?,
    /** What the author typed when it reads as no quantity; added in version 2 (FLOW-024 (6)). */
    @ColumnInfo(name = "entered_quantity") val enteredQuantity: String? = null,
    /** How the ingredient is prepared (DOM-023); null for none. Added in version 4. */
    val note: String? = null,
)

/**
 * A heading of a recipe's ingredient list (DOM-022), the [position]th of its headings; the rows from [firstIngredient]
 * up to the next heading belong to it. Added in version 4.
 */
@Entity(
    tableName = "ingredient_group",
    primaryKeys = ["recipe_id", "position"],
    foreignKeys = [ForeignKey(RecipeEntity::class, ["recipe_id"], ["recipe_id"], onDelete = CASCADE)],
)
data class IngredientGroupEntity(
    @ColumnInfo(name = "recipe_id") val recipeId: String,
    val position: Int,
    val name: String,
    @ColumnInfo(name = "first_ingredient") val firstIngredient: Int,
)

/**
 * The [position]th item of the equipment a recipe is cooked with, named as an ingredient row is: by an equipment
 * dictionary entry and its [qualifier], or by the author's [freeTextName]. Added in version 6 as the item's text alone;
 * since version 7 named so.
 */
@Entity(
    tableName = "recipe_equipment",
    primaryKeys = ["recipe_id", "position"],
    foreignKeys = [ForeignKey(RecipeEntity::class, ["recipe_id"], ["recipe_id"], onDelete = CASCADE)],
)
data class EquipmentEntity(
    @ColumnInfo(name = "recipe_id") val recipeId: String,
    val position: Int,
    @ColumnInfo(name = "dictionary_entry") val dictionaryEntry: Long?,
    val qualifier: String?,
    @ColumnInfo(name = "free_text_name") val freeTextName: String?,
)

@Entity(
    tableName = "step",
    primaryKeys = ["recipe_id", "position"],
    foreignKeys = [ForeignKey(RecipeEntity::class, ["recipe_id"], ["recipe_id"], onDelete = CASCADE)],
)
data class StepEntity(
    @ColumnInfo(name = "recipe_id") val recipeId: String,
    val position: Int,
    val text: String,
    /** The author's note on the step; null for none. Added in version 8. */
    val note: String? = null,
)

/**
 * The [position]th timer of the recipe's step at [stepPosition] (DOM-024): its duration in whole [minutes] and its
 * [label], null for none. Added in version 5, when a step's one timer left its row.
 */
@Entity(
    tableName = "step_timer",
    primaryKeys = ["recipe_id", "step_position", "position"],
    foreignKeys = [ForeignKey(RecipeEntity::class, ["recipe_id"], ["recipe_id"], onDelete = CASCADE)],
)
data class StepTimerEntity(
    @ColumnInfo(name = "recipe_id") val recipeId: String,
    @ColumnInfo(name = "step_position") val stepPosition: Int,
    val position: Int,
    val minutes: Long,
    val label: String?,
)

@Entity(
    tableName = "step_reference",
    primaryKeys = ["recipe_id", "step_position", "text_offset"],
    foreignKeys = [ForeignKey(RecipeEntity::class, ["recipe_id"], ["recipe_id"], onDelete = CASCADE)],
)
data class StepReferenceEntity(
    @ColumnInfo(name = "recipe_id") val recipeId: String,
    @ColumnInfo(name = "step_position") val stepPosition: Int,
    @ColumnInfo(name = "text_offset") val textOffset: Int,
    @ColumnInfo(name = "ingredient_position") val ingredientPosition: Int,
    /** The part of the ingredient the reference reads, in [shareUnit]; null for the whole. Added in version 3. */
    val share: Rational? = null,
    @ColumnInfo(name = "share_unit") val shareUnit: QuantityUnit? = null,
)

@Entity(
    tableName = "media",
    indices = [Index("recipe_id")],
    foreignKeys = [ForeignKey(RecipeEntity::class, ["recipe_id"], ["recipe_id"], onDelete = CASCADE)],
)
data class MediaEntity(
    @PrimaryKey @ColumnInfo(name = "media_id") val mediaId: String,
    @ColumnInfo(name = "recipe_id") val recipeId: String,
    /** Its order within the recipe. */
    val position: Int,
    val owner: String?,
    val kind: MediaKind,
    /** The step it is attached to; null for the recipe's cover. */
    @ColumnInfo(name = "step_position") val stepPosition: Int?,
    val visibility: MediaVisibility,
)

@Entity(
    tableName = "recipe_tag",
    primaryKeys = ["recipe_id", "tag_id"],
    foreignKeys = [ForeignKey(RecipeEntity::class, ["recipe_id"], ["recipe_id"], onDelete = CASCADE)],
)
data class RecipeTagEntity(
    @ColumnInfo(name = "recipe_id") val recipeId: String,
    @ColumnInfo(name = "tag_id") val tagId: Long,
)

/**
 * A recipe marked in this book (FLOW-009); the view of marked recipes lists what this table holds. A recipe imported
 * from a file is marked so (FLOW-064), its content held in the recipe tables under the same identity: a recipe whose
 * identity is marked is a found one, never written here, and so is not listed among the own recipes.
 */
@Entity(tableName = "favourite")
data class FavouriteEntity(
    @PrimaryKey @ColumnInfo(name = "recipe_id") val recipeId: String,
    val title: String,
    @ColumnInfo(name = "added_at") val addedAt: Instant,
)

/**
 * A named collection of the book's recipes (v0.36): made here ([CollectionOrigin.OWN]) or arrived in a collection file
 * ([CollectionOrigin.IMPORTED]), with the author's nickname that file states, if any. Its recipes are its
 * [CollectionRecipeEntity] rows. Added in version 9.
 */
@Entity(tableName = "collection")
data class CollectionEntity(
    @PrimaryKey @ColumnInfo(name = "collection_id") val collectionId: String,
    val name: String,
    val origin: CollectionOrigin,
    @ColumnInfo(name = "stated_author") val statedAuthor: String?,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
)

/**
 * The recipe [recipeId] in the collection [collectionId], ordered among its others by [position], the smaller first.
 * It goes with either: a recipe deleted from the book leaves every collection, and a deleted collection takes its rows
 * and no recipe. Added in version 9.
 */
@Entity(
    tableName = "collection_recipe",
    primaryKeys = ["collection_id", "recipe_id"],
    indices = [Index("recipe_id")],
    foreignKeys = [
        ForeignKey(CollectionEntity::class, ["collection_id"], ["collection_id"], onDelete = CASCADE),
        ForeignKey(RecipeEntity::class, ["recipe_id"], ["recipe_id"], onDelete = CASCADE),
    ],
)
data class CollectionRecipeEntity(
    @ColumnInfo(name = "collection_id") val collectionId: String,
    @ColumnInfo(name = "recipe_id") val recipeId: String,
    val position: Int,
)

/** A collection as a list of collections shows it: with how many recipes it holds. */
data class CollectionRow(
    @ColumnInfo(name = "collection_id") val collectionId: String,
    val name: String,
    val origin: CollectionOrigin,
    @ColumnInfo(name = "stated_author") val statedAuthor: String?,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    val count: Int,
)

/** A recipe of a collection as the collection's screen lists it. */
data class CollectionRecipeRow(
    @ColumnInfo(name = "recipe_id") val recipeId: String,
    val title: String,
    /** The media identity of the recipe's cover, if it holds one (MEDIA-001). */
    @ColumnInfo(name = "cover_id") val coverId: String? = null,
    /** The author's nickname a found recipe's file states, never verified; null for none. */
    @ColumnInfo(name = "stated_author") val statedAuthor: String? = null,
    /** Whether it is a found recipe — marked in the book — rather than one written in it. */
    val found: Boolean = false,
    /** Whether the recipe is a version of another one: it has a parent (FORK-010). */
    val forked: Boolean = false,
    /**
     * The title of the recipe's parent as its entry names it (FORK-010): the one the version recorded, else the title of
     * the parent the book holds; null for a recipe without a parent, or one whose parent is gone and recorded none.
     */
    @ColumnInfo(name = "parent_title") val parentTitle: String? = null,
)

/** What an entry of the view of marked recipes shows. */
data class MarkedRecipeRow(
    @ColumnInfo(name = "recipe_id") val recipeId: String,
    val title: String,
    /** The author's nickname as the recipe's file states it, never verified (FLOW-064 (3)). */
    @ColumnInfo(name = "stated_author") val statedAuthor: String?,
    /** Whether the book holds the recipe's content, which its card then shows. */
    val held: Boolean,
    /** The media identity of the cover the book holds of it, if any (MEDIA-001). */
    @ColumnInfo(name = "cover_id") val coverId: String? = null,
    /** The language the content the book holds declares (L10N-004); null for none, or no content held. */
    val language: RecipeLanguage? = null,
    /** The description of the content the book holds, searched as the title is (L10N-006); empty for none. */
    val description: String = "",
    /** Whether the recipe is a version of another one: it has a parent (FORK-010). */
    val forked: Boolean = false,
    /**
     * The title of the recipe's parent as its entry names it (FORK-010): the one the version recorded, else the title of
     * the parent the book holds; null for a recipe without a parent, or one whose parent is gone and recorded none.
     */
    @ColumnInfo(name = "parent_title") val parentTitle: String? = null,
)

/** What an entry of the own-recipes view shows. */
data class OwnRecipeRow(
    @ColumnInfo(name = "recipe_id") val recipeId: String,
    val title: String,
    val state: RecipeState,
    val sent: SentStatus,
    /** The media identity of the recipe's cover, if it holds one (MEDIA-001). */
    @ColumnInfo(name = "cover_id") val coverId: String? = null,
    /** The language the recipe declares (L10N-004); null for none. */
    val language: RecipeLanguage? = null,
    /** The recipe's description, searched as the title is (L10N-006). */
    val description: String = "",
    /** Whether the recipe is a version of another one: it has a parent (FORK-010). */
    val forked: Boolean = false,
    /**
     * The title of the recipe's parent as its entry names it (FORK-010): the one the version recorded, else the title of
     * the parent the book holds; null for a recipe without a parent, or one whose parent is gone and recorded none.
     */
    @ColumnInfo(name = "parent_title") val parentTitle: String? = null,
)

/**
 * A dictionary entry of equipment a recipe names, as the book's filter "Enough equipment" reads it: an item of free
 * text names no entry and is not one of these.
 */
data class RecipeEquipmentRow(
    @ColumnInfo(name = "recipe_id") val recipeId: String,
    @ColumnInfo(name = "dictionary_entry") val dictionaryEntry: Long,
)

/**
 * An ingredient row as a view of the book is searched and filtered by it (FLOW-035): what names it — its dictionary
 * entry, its qualifier and its free text (FLOW-035 (4)) — and its quantity, with the serving count its recipe was
 * authored for, which a filter by an ingredient orders by (CAT-002).
 */
data class IngredientLineRow(
    @ColumnInfo(name = "recipe_id") val recipeId: String,
    @ColumnInfo(name = "dictionary_entry") val dictionaryEntry: Long?,
    val qualifier: String?,
    @ColumnInfo(name = "free_text_name") val freeTextName: String?,
    @ColumnInfo(name = "unit_type") val unitType: UnitType = UnitType.EXACT,
    val unit: QuantityUnit? = null,
    val quantity: Rational? = null,
    val servings: Int? = null,
)
