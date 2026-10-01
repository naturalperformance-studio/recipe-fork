package app.recipefork.app.recipe

import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.FavouriteEntity
import app.recipefork.app.book.IngredientEntity
import app.recipefork.app.book.MediaEntity
import app.recipefork.app.book.OwnRecipeRow
import app.recipefork.app.book.RecipeEntity
import app.recipefork.app.book.RecipeRows
import app.recipefork.app.book.RecipeTagEntity
import app.recipefork.app.book.StepEntity
import app.recipefork.app.book.StepReferenceEntity
import app.recipefork.core.recipe.AccountId
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.IngredientReference
import app.recipefork.core.recipe.Media
import app.recipefork.core.recipe.MediaAttachment
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.StatedParent
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.Timer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlin.time.Duration.Companion.minutes

/** The recipes of the active book, read from and written to the device alone (FLOW-038, FLOW-043). */
@OptIn(ExperimentalCoroutinesApi::class)
class RecipeRepository(private val book: ActiveBook) {
    /** The first [limit] own recipes under `newest` (FLOW-039); a longer listing asks for a larger limit. */
    fun ownNewest(limit: Int): Flow<List<OwnRecipeRow>> =
        book.open.flatMapLatest { it.database.recipes().newest(limit) }

    /** What is marked in the active book. */
    fun marked(): Flow<List<FavouriteEntity>> = book.open.flatMapLatest { it.database.favourites().all() }

    suspend fun save(recipe: Recipe) = book.database.recipes().put(recipe.toRows())

    suspend fun get(id: RecipeId): Recipe? = book.database.recipes().get(id.value)?.toRecipe()

    /**
     * The recipe [id] of the active book as it is now and after every later write to it; null while the book
     * holds no such recipe. A screen that shows it never shows a version older than the last write.
     */
    fun observe(id: RecipeId): Flow<Recipe?> = book.open.flatMapLatest { open ->
        open.database.invalidationTracker.createFlow(*RECIPE_TABLES)
            .map { open.database.recipes().get(id.value)?.toRecipe() }
    }

    suspend fun count(): Int = book.database.recipes().count()

    private companion object {
        /** Every table a recipe's rows live in. */
        val RECIPE_TABLES = arrayOf("recipe", "ingredient", "step", "step_reference", "media", "recipe_tag")
    }
}

internal fun Recipe.toRows(): RecipeRows {
    val id = id.value
    return RecipeRows(
        recipe = RecipeEntity(
            recipeId = id, owner = owner?.value, parent = parent?.value, sent = sent,
            statedAuthor = statedAuthor, statedParentTitle = statedParent?.title,
            statedParentNickname = statedParent?.nickname, title = title, description = description,
            language = language, servings = servings, state = state, replacesParent = replacesParent,
            wasPublished = wasPublished, safetyFlag = safetyFlag, createdAt = createdAt,
            contentChangedAt = contentChangedAt,
        ),
        ingredients = ingredients.map {
            IngredientEntity(
                id, it.position, it.dictionaryEntry, it.qualifier, it.freeTextName, it.unitType, it.unit,
                it.quantity, it.pieceWeight, it.divisible, it.enteredQuantity,
            )
        },
        steps = steps.map { StepEntity(id, it.position, it.text, it.timer?.duration?.inWholeMinutes, it.timer?.label) },
        references = steps.flatMap { step ->
            step.ingredientReferences.map { StepReferenceEntity(id, step.position, it.textOffset, it.ingredientPosition) }
        },
        media = media.mapIndexed { index, it ->
            MediaEntity(
                it.mediaId, id, index, it.owner?.value, it.kind,
                (it.attachment as? MediaAttachment.ToStep)?.stepPosition, it.visibility,
            )
        },
        tags = tags.map { RecipeTagEntity(id, it) },
    )
}

internal fun RecipeRows.toRecipe(): Recipe {
    val references = references.groupBy { it.stepPosition }
    return Recipe(
        id = RecipeId(recipe.recipeId),
        owner = recipe.owner?.let(::AccountId),
        parent = recipe.parent?.let(::RecipeId),
        sent = recipe.sent,
        statedAuthor = recipe.statedAuthor,
        statedParent = recipe.statedParentTitle?.let { StatedParent(it, recipe.statedParentNickname.orEmpty()) },
        title = recipe.title,
        description = recipe.description,
        tags = tags.map { it.tagId }.toSet(),
        language = recipe.language,
        servings = recipe.servings,
        ingredients = ingredients.map {
            Ingredient(
                it.position, it.dictionaryEntry, it.qualifier, it.freeTextName, it.unitType, it.unit, it.quantity,
                it.pieceWeight, it.divisible, it.enteredQuantity,
            )
        },
        steps = steps.map { step ->
            Step(
                position = step.position,
                text = step.text,
                ingredientReferences = references[step.position].orEmpty()
                    .map { IngredientReference(it.ingredientPosition, it.textOffset) },
                timer = step.timerMinutes?.let { Timer(it.minutes, step.timerLabel) },
            )
        },
        media = media.map {
            Media(
                it.mediaId, it.owner?.let(::AccountId), it.kind,
                it.stepPosition?.let(MediaAttachment::ToStep) ?: MediaAttachment.Cover, it.visibility,
            )
        },
        state = recipe.state,
        replacesParent = recipe.replacesParent,
        wasPublished = recipe.wasPublished,
        safetyFlag = recipe.safetyFlag,
        createdAt = recipe.createdAt,
        contentChangedAt = recipe.contentChangedAt,
    )
}
