package app.recipefork.app.file

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import app.recipefork.app.R
import app.recipefork.app.book.BookName
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.file.RecipeFileContent
import app.recipefork.core.recipe.Media
import app.recipefork.core.recipe.MediaVisibility
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.RecipeState
import app.recipefork.core.recipe.StatedParent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.time.Instant
import java.util.UUID

/** What became of a file opened for import (FLOW-064). */
sealed interface ImportOutcome {
    /** The recipe is in the book now, as a found recipe. */
    data class Added(val id: RecipeId, val title: String) : ImportOutcome

    /** The book already holds that recipe — imported before, or the person's own — and nothing was added. */
    data class AlreadyInBook(val id: RecipeId, val title: String) : ImportOutcome

    /** The file was refused, for [refusal], and nothing was added. */
    data class Refused(val refusal: Refusal) : ImportOutcome
}

/**
 * Import of a recipe file (FLOW-064): the recipe it holds is added to the active book — the pool while no account is
 * active — as a found recipe, marked and not editable, keeping the author and the parent the file states, never
 * verified. Its media is written under names generated here, into the book's media directory under [filesDirectory].
 * A recipe the book already holds adds nothing; a file that cannot be believed is refused and nothing of it is kept.
 */
class RecipeImport(
    private val recipes: RecipeRepository,
    private val filesDirectory: File,
    private val clock: () -> Instant = Instant::now,
    private val limits: ArchiveLimits = ArchiveLimits(),
) {
    suspend fun import(open: () -> InputStream): ImportOutcome = withContext(Dispatchers.IO) {
        val content = try {
            RecipeArchiveReader.document(open, limits)
        } catch (e: RefusedFile) {
            return@withContext ImportOutcome.Refused(e.refusal)
        }
        val held = recipes.get(content.id)
        if (held != null || recipes.isMarked(content.id)) {
            // Named as the book holds it, which is what the person will find there.
            return@withContext ImportOutcome.AlreadyInBook(content.id, held?.title ?: content.title)
        }
        val directory = mediaDirectory(filesDirectory, recipes.bookName())
        val written = mutableListOf<Pair<Media, File>>()
        try {
            RecipeArchiveReader.media(open, content, target = { item ->
                if (!directory.isDirectory && !directory.mkdirs()) throw RefusedFile(Refusal.CANNOT_OPEN, "no media directory")
                val media = Media(UUID.randomUUID().toString(), null, item.kind, item.attachment, MediaVisibility.PRIVATE)
                File(directory, media.mediaId).also { written += media to it }
            }, limits)
            val at = clock()
            val recipe = foundRecipe(content, written.map { it.first }, at)
            if (!recipes.addFound(recipe, at)) {
                written.forEach { it.second.delete() }
                return@withContext ImportOutcome.AlreadyInBook(content.id, content.title)
            }
            ImportOutcome.Added(recipe.id, recipe.title)
        } catch (e: RefusedFile) {
            written.forEach { it.second.delete() }
            ImportOutcome.Refused(e.refusal)
        } catch (e: Throwable) {
            written.forEach { it.second.delete() }
            throw e
        }
    }

    /** Opens [uri] through [context]'s content resolver and imports what it holds. */
    suspend fun import(context: Context, uri: Uri): ImportOutcome = import {
        context.contentResolver.openInputStream(uri) ?: throw java.io.FileNotFoundException("$uri")
    }

    /**
     * Removes the found recipe [id] from the active book with its held content and its media files (FLOW-064 (6)).
     * Returns whether the book marked it.
     */
    suspend fun remove(id: RecipeId): Boolean = withContext(Dispatchers.IO) {
        val book = recipes.bookName()
        val media = recipes.removeMarked(id) ?: return@withContext false
        val directory = mediaDirectory(filesDirectory, book)
        media.forEach { File(directory, it).delete() }
        true
    }

    companion object {
        /** The directory the media of [book] is kept in. */
        fun mediaDirectory(filesDirectory: File, book: BookName) = File(filesDirectory, book.mediaDirectoryName)

        /**
         * The found recipe a file's [content] makes, at [at]: its identity, content and stated provenance as the file
         * gives them, in no account (the author's is unknown), never sent, and frozen — a state no editing is allowed
         * in, as a recipe handed from one person to another by private link is (FLOW-064 (2)).
         */
        fun foundRecipe(content: RecipeFileContent, media: List<Media>, at: Instant): Recipe =
            Recipe.create(at, owner = null, parent = content.parent?.id, id = content.id).copy(
                statedAuthor = content.author,
                statedParent = content.parent?.let { parent -> parent.title?.let { StatedParent(it, parent.nickname.orEmpty()) } },
                title = content.title,
                description = content.description,
                tags = content.tags,
                language = content.language,
                servings = content.servings,
                ingredients = content.ingredients,
                steps = content.steps,
                media = media,
            ).inState(FOUND_STATE)

        /** The state a found recipe is held in: not editable. */
        val FOUND_STATE = RecipeState.LINK_SHARED
    }
}

/**
 * A recipe file handed to the application by another (FLOW-064): the `content:` or `file:` address an `ACTION_VIEW`
 * intent carries. A timer's notification is a view of the application's own address, and is no file.
 */
object RecipeFileIntents {
    private val FILE_SCHEMES = setOf(android.content.ContentResolver.SCHEME_CONTENT, android.content.ContentResolver.SCHEME_FILE)

    fun fileOf(intent: Intent?): Uri? =
        intent?.takeIf { it.action == Intent.ACTION_VIEW }?.data?.takeIf { it.scheme in FILE_SCHEMES }
}

/** Tells the person what became of the file they opened. */
fun announce(context: Context, outcome: ImportOutcome) {
    val untitled = context.getString(R.string.common_untitled_recipe)
    val text = when (outcome) {
        is ImportOutcome.Added -> context.getString(R.string.file_imported, outcome.title.ifBlank { untitled })
        is ImportOutcome.AlreadyInBook -> context.getString(R.string.file_import_already, outcome.title.ifBlank { untitled })
        is ImportOutcome.Refused -> context.getString(
            when (outcome.refusal) {
                Refusal.CANNOT_OPEN -> R.string.file_import_cannot_open
                Refusal.NOT_A_RECIPE_FILE -> R.string.file_import_not_a_recipe
                Refusal.LATER_VERSION -> R.string.file_import_later
                Refusal.DAMAGED -> R.string.file_import_damaged
                Refusal.BREAKS_RULES -> R.string.file_import_breaks_rules
            },
        )
    }
    Toast.makeText(context.applicationContext, text, Toast.LENGTH_LONG).show()
}

/** Imports [uri], turning any failure of the device into a refusal said to the person rather than a crash. */
suspend fun RecipeImport.importSafely(context: Context, uri: Uri): ImportOutcome = try {
    import(context, uri)
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    ImportOutcome.Refused(Refusal.CANNOT_OPEN)
}
