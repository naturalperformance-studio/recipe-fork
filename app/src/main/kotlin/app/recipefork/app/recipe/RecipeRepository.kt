package app.recipefork.app.recipe

import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookDatabase
import app.recipefork.app.book.BookName
import app.recipefork.app.dictionary.IngredientEntryEntity
import app.recipefork.app.book.FavouriteEntity
import app.recipefork.app.book.MarkedRecipeRow
import app.recipefork.app.book.IngredientEntity
import app.recipefork.app.book.IngredientLineRow
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
import app.recipefork.core.recipe.IngredientShare
import app.recipefork.core.recipe.Media
import app.recipefork.core.recipe.MediaAttachment
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.SentStatus
import app.recipefork.core.recipe.StatedParent
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.Timer
import app.recipefork.app.media.PhotoStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlin.time.Duration.Companion.minutes

/** The recipes of the active book, read from and written to the device alone (FLOW-038, FLOW-043). */
@OptIn(ExperimentalCoroutinesApi::class)
open class RecipeRepository(
    private val book: ActiveBook,
    /**
     * The directory the books' media directories are kept under — the application's files — where work on a recipe
     * also copies or deletes its media files; null where the files are no concern of the caller's.
     */
    val filesDirectory: java.io.File? = null,
) {
    /** The first [limit] own recipes under `newest` (FLOW-039); a longer listing asks for a larger limit. */
    fun ownNewest(limit: Int): Flow<List<OwnRecipeRow>> =
        book.open.flatMapLatest { it.database.recipes().newest(limit) }

    /** What is marked in the active book, the found recipes among it (FLOW-009, FLOW-064). */
    fun marked(): Flow<List<MarkedRecipeRow>> = book.open.flatMapLatest { it.database.favourites().marked() }

    /**
     * The own recipes of the active book as their view lists them for each of [params] (FLOW-035): searched by title,
     * description and ingredient, with the ingredient names of [dictionary], filtered by ingredient, tags and language, and ordered. Worked out where the book's queries
     * run, never on the main thread.
     */
    fun ownListed(
        params: Flow<ListingParams>,
        dictionary: suspend () -> List<IngredientEntryEntity>,
    ): Flow<Listed<OwnRecipeRow>> =
        listed(params, { it.recipes().own() }) {
            BookSearch(dictionary, OwnRecipeRow::recipeId, OwnRecipeRow::title, OwnRecipeRow::language, OwnRecipeRow::description)
        }

    /** What is marked in the active book as its view lists it for each of [params], as [ownListed] does. */
    fun markedListed(
        params: Flow<ListingParams>,
        dictionary: suspend () -> List<IngredientEntryEntity>,
    ): Flow<Listed<MarkedRecipeRow>> =
        listed(params, { it.favourites().marked() }) {
            BookSearch(
                dictionary, MarkedRecipeRow::recipeId, MarkedRecipeRow::title,
                MarkedRecipeRow::language, MarkedRecipeRow::description,
            )
        }

    private fun <R> listed(
        params: Flow<ListingParams>,
        rows: (BookDatabase) -> Flow<List<R>>,
        search: () -> BookSearch<R>,
    ): Flow<Listed<R>> = book.open.flatMapLatest { open ->
        val searching = search()
        val recipes = open.database.recipes()
        combine(rows(open.database), recipes.ingredientLines(), recipes.allTags(), params) { shown, lines, tags, asked ->
            Listing(shown, lines, tags, asked)
        }
            .mapLatest { searching.listed(it.rows, it.lines, it.tags, it.params) }
            .flowOn(open.database.queryExecutor.asCoroutineDispatcher())
    }

    /**
     * The book active now, held: every read and write of what this returns goes to that book, even when another
     * becomes active meanwhile. Work that spans several steps — an import — holds the book it began in.
     */
    fun inOpenBook(): RecipeRepository =
        book.open.value.let { RecipeRepository(ActiveBook(it.name, it.database), filesDirectory) }

    /** The name of the active book, whose media directory a found recipe's media is written to. */
    fun bookName(): BookName = book.open.value.name

    /**
     * Adds [recipe] to the active book as a found recipe, marked at [at] (FLOW-064 (1)). Returns false, adding
     * nothing, when the book already holds a recipe of that identity — found before, or the person's own (FLOW-064 (4)).
     */
    suspend fun addFound(recipe: Recipe, at: java.time.Instant): Boolean =
        book.database.recipes().putFound(recipe.toRows(), FavouriteEntity(recipe.id.value, recipe.title, at))

    /** Whether the recipe [id] of the active book is a marked one: found, not written in this book. */
    suspend fun isMarked(id: RecipeId): Boolean = book.database.recipes().isMarked(id.value)

    /**
     * Removes the marked recipe [id] from the active book with its held content (FLOW-064 (6)) and deletes the media
     * files it held under [files] that nothing else holds (MEDIA-012). Returns whether the book marked it.
     */
    suspend fun removeMarked(id: RecipeId, files: java.io.File? = filesDirectory): Boolean {
        val open = book.open.value
        val media = open.database.recipes().removeMarked(id.value) ?: return false
        release(open, media, files)
        return true
    }

    /**
     * A fork of [parent] in the active book that nothing has changed since it was made (FLOW-015): every edit of
     * content moves its content time past its creation (FLOW-039), so an unchanged fork is one whose two times are
     * equal. Null when the book holds no such fork.
     */
    suspend fun unchangedForkOf(parent: RecipeId): RecipeId? =
        book.database.recipes().unchangedForkOf(parent.value)?.let(::RecipeId)

    open suspend fun save(recipe: Recipe) = book.database.recipes().put(recipe.toRows())

    /**
     * Deletes the recipe [id] from the active book with everything it holds, on the device alone (FLOW-053 (6, 8)),
     * and the media files it held that nothing else holds (MEDIA-012). It is gone at once and cannot be brought back.
     * Returns whether the book held it.
     */
    suspend fun delete(id: RecipeId): Boolean {
        val open = book.open.value
        val media = open.database.recipes().deleteHolding(id.value) ?: return false
        release(open, media, filesDirectory)
        return true
    }

    /**
     * Deletes, under [files], the files of the media items [media] that a recipe of the book [open] no longer holds:
     * the one way a recipe's leaving the book takes its photographs with it (MEDIA-012). A file a fork copied is the
     * fork's own, under its own identity, and stays. Finished even when the caller is cancelled: the rows are gone.
     */
    private suspend fun release(open: ActiveBook.Open, media: List<String>, files: java.io.File?) {
        if (files == null || media.isEmpty()) return
        withContext(NonCancellable) {
            val held = open.database.recipes().held(media).toSet()
            val store = PhotoStore(files)
            media.filterNot { it in held }.forEach { store.delete(open.name, it) }
        }
    }

    /**
     * The media files of the active book that [clearMedia] would delete, those on the device now: the media of every
     * found recipe and of every own recipe sent without changes since (FLOW-060 (1)). A recipe never sent, or sent
     * with changes not sent — which may hold media sent nowhere — keeps its media (FLOW-060 (2)). Empty when the
     * media files are no concern of this repository's.
     */
    suspend fun clearableMedia(): List<java.io.File> {
        val files = filesDirectory ?: return emptyList()
        val open = book.open.value
        val store = PhotoStore(files)
        val ids = open.database.recipes().clearableMedia(SentStatus.SENT)
        return withContext(Dispatchers.IO) { ids.map { store.file(open.name, it) }.filter { it.isFile } }
    }

    /**
     * Deletes the files of [clearableMedia] from the device (FLOW-060); every media row stays, so a recipe still
     * names the media it no longer holds a file of. Returns how many files were deleted. Finished even when the caller
     * is cancelled.
     */
    suspend fun clearMedia(): Int = withContext(NonCancellable) {
        val files = clearableMedia()
        withContext(Dispatchers.IO) { files.count { it.delete() } }
    }

    suspend fun get(id: RecipeId): Recipe? = book.database.recipes().get(id.value)?.toRecipe()

    /**
     * The recipe [id] of the active book as it is now and after every later write to it; null while the book
     * holds no such recipe. A screen that shows it never shows a version older than the last write. A read that fails —
     * a row that does not read back, storage that refuses — ends the flow with its failure; open, so that a test can
     * make it fail.
     */
    open fun observe(id: RecipeId): Flow<Recipe?> = book.open.flatMapLatest { open ->
        open.database.invalidationTracker.createFlow(*RECIPE_TABLES)
            .map { open.database.recipes().get(id.value)?.toRecipe() }
    }

    suspend fun count(): Int = book.database.recipes().count()

    private companion object {
        /** Every table a recipe's rows live in. */
        val RECIPE_TABLES = arrayOf("recipe", "ingredient", "step", "step_reference", "media", "recipe_tag")
    }
}

/** What one listing of a view is worked out from. */
private class Listing<R>(
    val rows: List<R>,
    val lines: List<IngredientLineRow>,
    val tags: List<RecipeTagEntity>,
    val params: ListingParams,
)

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
            step.ingredientReferences.map {
                StepReferenceEntity(id, step.position, it.textOffset, it.ingredientPosition, it.share?.amount, it.share?.unit)
            }
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
                    .map { reference ->
                        val share = reference.share?.let { amount -> reference.shareUnit?.let { IngredientShare(amount, it) } }
                        IngredientReference(reference.ingredientPosition, reference.textOffset, share)
                    },
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
