package app.recipefork.app.file

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.room.withTransaction
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.collections.CollectionRepository
import app.recipefork.app.collections.ImportedCollection
import app.recipefork.app.dictionary.EquipmentSource
import app.recipefork.app.library.LibrarySession
import app.recipefork.app.library.LibraryView
import app.recipefork.app.library.FileWork
import app.recipefork.app.media.ClipDuration
import app.recipefork.app.media.RetrieverClipDuration
import app.recipefork.app.recipe.BookFilters
import app.recipefork.app.recipe.EquipmentNames
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.file.CollectionFile
import app.recipefork.core.recipe.CollectionId
import app.recipefork.core.recipe.CollectionOrigin
import app.recipefork.core.recipe.Media
import app.recipefork.core.recipe.MediaKind
import app.recipefork.core.recipe.MediaVisibility
import app.recipefork.core.recipe.RecipeCollection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.time.Instant
import java.util.UUID

/** What became of a collection file opened for import (v0.36). */
sealed interface CollectionImportOutcome {
    /**
     * The collection is in the book: made an imported one, or — held as one already — [updated] to the file's name and
     * order. The file listed [recipes]; [held] of them were in the book already, and the rest were added as found.
     */
    data class Imported(val id: CollectionId, val name: String, val recipes: Int, val held: Int, val updated: Boolean) : CollectionImportOutcome

    /** The book holds a collection of the file's identity as the reader's own: nothing was added, nor changed. */
    data class OwnHeld(val id: CollectionId, val name: String) : CollectionImportOutcome

    /** The file was refused, for [refusal], and nothing was added. */
    data class Refused(val refusal: Refusal) : CollectionImportOutcome
}

/** The reader's own collection of the file's identity was found as the import wrote: the writing is undone. */
private class OwnCollectionMeanwhile(val name: String) : Exception()

/**
 * Import of a collection file (v0.36): each recipe it holds is imported as a recipe file is ([RecipeImport]) — a found
 * recipe, frozen, its media written under names generated here — save one the book holds already, by its identity,
 * which is left as it is; the collection is made an imported one, or, held as one already, takes the file's name and
 * order and the recipes it lacks ([CollectionRepository.putImported]). The reader's own collection of that identity —
 * shared and come back — is never touched: such a file is refused whole, and nothing of it is added.
 *
 * The whole file is read and believed before anything is written: a recipe that cannot be believed refuses the whole
 * file. The recipes and the collection are then written in one transaction of the book's database; a refusal or a
 * failure leaves no media file of the import behind. The book is the one active when the import began.
 */
class CollectionImport(
    private val book: ActiveBook,
    private val filesDirectory: File,
    private val clock: () -> Instant = Instant::now,
    private val limits: CollectionLimits = CollectionLimits(),
    private val clipDuration: ClipDuration = RetrieverClipDuration,
    private val equipment: EquipmentSource = EquipmentSource { emptyList() },
) {
    /**
     * Imports the file [open] opens. When its [size] in bytes is known, [progress] is told the share of the work done
     * as it is read: the file is read twice — checked whole first, the media written next — so each reading counts for
     * half. The second reading stops at the last media item to write, and is left out when there is none: the work is
     * told whole once the import is done.
     */
    suspend fun import(size: Long = RecipeImport.UNKNOWN_SIZE, progress: (Float) -> Unit = {}, open: () -> InputStream): CollectionImportOutcome {
        val held = book.open.value.let { ActiveBook(it.name, it.database) }
        val counted = if (size <= 0) open else counting(open, 2 * size, progress)
        return withContext(Dispatchers.IO) { importInto(held, counted) }.also { if (size > 0) progress(1f) }
    }

    /**
     * Opens [uri] through [context]'s content resolver and imports what it holds. A `file:` address into the
     * application's own data is refused unread, as a recipe file's is.
     */
    suspend fun import(context: Context, uri: Uri, progress: (Float) -> Unit = {}): CollectionImportOutcome =
        if (RecipeImport.isPrivateFile(context, uri)) {
            CollectionImportOutcome.Refused(Refusal.CANNOT_OPEN)
        } else {
            val size = withContext(Dispatchers.IO) { RecipeImport.sizeOf(context, uri) }
            import(size, progress) { context.contentResolver.openInputStream(uri) ?: throw java.io.FileNotFoundException("$uri") }
        }

    private suspend fun importInto(book: ActiveBook, open: () -> InputStream): CollectionImportOutcome = coroutineScope {
        val work = coroutineContext
        val alive = { work.ensureActive() }
        val recipes = RecipeRepository(book, filesDirectory)
        val collections = CollectionRepository(book, clock)
        // A dictionary that cannot be read names nothing: the file is read as its own words have it.
        val dictionary = try {
            equipment.equipment()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
        val names = EquipmentNames(dictionary)
        val known = dictionary.mapTo(HashSet()) { it.id }
        val file = try {
            CollectionArchiveReader.read(open, limits, alive, names::entryOf)
        } catch (e: RefusedFile) {
            return@coroutineScope CollectionImportOutcome.Refused(e.refusal)
        }
        val document = file.document
        val name = RecipeCollection.nameOf(document.name)!!
        collections.get(document.id)?.takeIf { it.origin == CollectionOrigin.OWN }?.let {
            return@coroutineScope CollectionImportOutcome.OwnHeld(it.id, it.name)
        }
        val contents = file.recipes.map { RecipeImport.withKnownEntries(it, known) }
        val missing = contents.indices.filter { place ->
            val id = contents[place].id
            recipes.get(id) == null && !recipes.isMarked(id)
        }.toSet()
        val directory = RecipeImport.mediaDirectory(filesDirectory, book.open.value.name)
        val written = HashMap<Int, MutableList<Pair<Media, File>>>()
        fun delete(places: Collection<Int>) = places.forEach { place -> written[place]?.forEach { it.second.delete() } }
        try {
            CollectionArchiveReader.media(open, file, missing, target = { place, item ->
                if (!directory.isDirectory && !directory.mkdirs()) throw RefusedFile(Refusal.CANNOT_OPEN, "no media directory")
                val media = Media(UUID.randomUUID().toString(), null, item.kind, item.attachment, MediaVisibility.PRIVATE)
                File(directory, media.mediaId).also { written.getOrPut(place) { mutableListOf() } += media to it }
            }, limits, alive)
            // MEDIA-007: a clip is held to its length before any recipe is believed, each read from its file once written.
            written.values.flatten().filter { (media, _) -> media.kind == MediaKind.VIDEO }.forEach { (_, clip) ->
                alive()
                RecipeArchiveReader.checkClip(clip, clipDuration::of)
            }
            val at = clock()
            val (added, put) = book.database.withTransaction {
                // A recipe the book took meanwhile is held, and keeps what it has; the file's copy of its media goes.
                val added = missing.filter { place ->
                    val media = written[place].orEmpty().map { it.first }
                    recipes.addFound(RecipeImport.foundRecipe(contents[place], media, at), at)
                }
                when (val put = collections.putImported(document.id, name, document.author, document.recipes)) {
                    ImportedCollection.OWN_HELD -> throw OwnCollectionMeanwhile(collections.get(document.id)?.name ?: name)
                    ImportedCollection.INVALID_NAME -> throw RefusedFile(Refusal.DAMAGED, "the name")
                    else -> added to put
                }
            }
            delete(missing - added.toSet())
            CollectionImportOutcome.Imported(
                document.id, name, recipes = contents.size, held = contents.size - added.size,
                updated = put == ImportedCollection.UPDATED,
            )
        } catch (e: RefusedFile) {
            delete(written.keys)
            CollectionImportOutcome.Refused(e.refusal)
        } catch (e: OwnCollectionMeanwhile) {
            delete(written.keys)
            CollectionImportOutcome.OwnHeld(document.id, e.name)
        } catch (e: Throwable) {
            delete(written.keys)
            throw e
        }
    }
}

/** Imports [uri], turning any failure of the device into a refusal said to the person rather than a crash. */
suspend fun CollectionImport.importSafely(context: Context, uri: Uri, progress: (Float) -> Unit = {}): CollectionImportOutcome = try {
    import(context, uri, progress)
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    CollectionImportOutcome.Refused(Refusal.CANNOT_OPEN)
}

/** What the person is told of [this] outcome. */
fun CollectionImportOutcome.message(): FileMessage = when (this) {
    is CollectionImportOutcome.Imported -> FileMessage(
        if (updated) FileMessage.Kind.COLLECTION_UPDATED else FileMessage.Kind.COLLECTION_ADDED,
        title = name, collectionId = id.value, recipes = recipes, held = held,
    )
    is CollectionImportOutcome.OwnHeld -> FileMessage(FileMessage.Kind.COLLECTION_OWN, title = name, collectionId = id.value)
    is CollectionImportOutcome.Refused -> FileMessage(
        when (refusal) {
            Refusal.CANNOT_OPEN -> FileMessage.Kind.CANNOT_OPEN
            Refusal.NOT_A_RECIPE_FILE -> FileMessage.Kind.NOT_A_COLLECTION_FILE
            Refusal.LATER_VERSION -> FileMessage.Kind.COLLECTION_LATER_VERSION
            Refusal.DAMAGED -> FileMessage.Kind.COLLECTION_DAMAGED
            Refusal.BREAKS_RULES -> FileMessage.Kind.COLLECTION_BREAKS_RULES
            Refusal.CLIP_TOO_LONG -> FileMessage.Kind.COLLECTION_CLIP_TOO_LONG
        },
    )
}

/**
 * Imports the collection file at [uri] (v0.36), its progress shown in the book while it is read; the person is told
 * what became of it. A collection now in the book is shown where it stands — above the view of imported recipes, that
 * view's search and filters cleared; the reader's own one the file matched, above the own recipes.
 */
suspend fun LibrarySession.importCollectionFile(context: Context, files: CollectionImport, uri: Uri) {
    val outcome = withProgress(FileWork.Kind.IMPORT) { progress -> files.importSafely(context, uri, progress) }
    val view = when (outcome) {
        is CollectionImportOutcome.Imported -> LibraryView.MARKED
        is CollectionImportOutcome.OwnHeld -> LibraryView.OWN
        is CollectionImportOutcome.Refused -> null
    }
    if (view != null) {
        search(view, "")
        filter(view, BookFilters())
        showHeld()
        choose(view)
    }
    tell(outcome.message())
}

/**
 * A collection file (v0.36) opened or shared from another application: told from a recipe file by its name, or by the
 * product's type for a collection, and handed on to [MainActivity][app.recipefork.app.MainActivity] as [ACTION_IMPORT]
 * — never as a view, which would import it as a recipe.
 */
object CollectionFileIntents {
    /** What [OpenRecipeFileActivity] hands a collection file on to MainActivity as. */
    const val ACTION_IMPORT = "app.recipefork.app.action.IMPORT_COLLECTION"

    private const val SUFFIX = "." + CollectionFile.EXTENSION

    /** Whether [uri]'s path names a collection file, its extension in any case, or [type] is the collection's own. */
    fun isCollectionFile(uri: Uri, type: String? = null): Boolean =
        type == CollectionFile.MIME_TYPE || uri.path?.endsWith(SUFFIX, ignoreCase = true) == true

    /** The collection file [intent] opens or shares, as another application starts it; null where it holds none. */
    fun arriving(intent: Intent?): Uri? = when (intent?.action) {
        Intent.ACTION_VIEW -> intent.data
        Intent.ACTION_SEND -> RecipeFileIntents.streamOf(intent) ?: intent.data
        else -> null
    }?.takeIf { it.scheme in RecipeFileIntents.FILE_SCHEMES && isCollectionFile(it, intent?.type) && !BookFileIntents.isBookFile(it) }

    /** The collection file [intent] hands MainActivity to import; null where it is no such hand-over. */
    fun toImport(intent: Intent?): Uri? =
        intent?.takeIf { it.action == ACTION_IMPORT }?.data?.takeIf { it.scheme in RecipeFileIntents.FILE_SCHEMES }
}
