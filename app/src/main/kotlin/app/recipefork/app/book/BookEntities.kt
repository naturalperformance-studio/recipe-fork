package app.recipefork.app.book

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
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
    /** The step's timer in whole minutes; null when the step has none (DOM-013). */
    @ColumnInfo(name = "timer_minutes") val timerMinutes: Long?,
    @ColumnInfo(name = "timer_label") val timerLabel: String?,
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
 * A recipe marked in this book (FLOW-009). Nothing can be marked yet; the view of marked recipes reads
 * this table so that it lists what the book holds.
 */
@Entity(tableName = "favourite")
data class FavouriteEntity(
    @PrimaryKey @ColumnInfo(name = "recipe_id") val recipeId: String,
    val title: String,
    @ColumnInfo(name = "added_at") val addedAt: Instant,
)

/** What an entry of the own-recipes view shows. */
data class OwnRecipeRow(
    @ColumnInfo(name = "recipe_id") val recipeId: String,
    val title: String,
    val state: RecipeState,
    val sent: SentStatus,
)
