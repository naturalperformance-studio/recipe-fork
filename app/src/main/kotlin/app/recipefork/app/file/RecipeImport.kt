package app.recipefork.app.file

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import app.recipefork.app.MainActivity
import app.recipefork.app.book.BookName
import app.recipefork.app.dictionary.EquipmentSource
import app.recipefork.app.media.ClipDuration
import app.recipefork.app.media.RetrieverClipDuration
import app.recipefork.app.recipe.EquipmentNames
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.recipe.TakenRecipe
import app.recipefork.core.file.RecipeFile
import app.recipefork.core.file.RecipeFileContent
import app.recipefork.core.recipe.EquipmentItem
import app.recipefork.core.recipe.Media
import app.recipefork.core.recipe.MediaKind
import app.recipefork.core.recipe.MediaVisibility
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.RecipeState
import app.recipefork.core.recipe.StatedParent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.time.Instant
import java.util.UUID

/** [open], each stream it opens counting what is read of it into one share of [total] told to [progress]. */
internal fun counting(open: () -> InputStream, total: Long, progress: (Float) -> Unit): () -> InputStream {
    var read = 0L
    return {
        object : java.io.FilterInputStream(open()) {
            override fun read(): Int = super.read().also { if (it >= 0) told(1) }
            override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) told(it) }
            private fun told(bytes: Int) {
                read += bytes
                progress((read.toFloat() / total).coerceAtMost(1f))
            }
        }
    }
}

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
 * The book is the one active when the import began, whichever becomes active meanwhile; a cancelled import stops
 * reading at once and keeps nothing.
 *
 * The file's equipment is named against the device's [equipment] dictionary: an item a file of format 4 wrote as its
 * text finds its entry as a book's item did on moving to version 7 ([EquipmentNames]), and an entry the dictionary does
 * not hold is kept as free text — its label, in the recipe's language, with its qualifier — where the file gives its
 * labels, so that it reads as it was written.
 */
class RecipeImport(
    private val recipes: RecipeRepository,
    private val filesDirectory: File,
    private val clock: () -> Instant = Instant::now,
    private val limits: ArchiveLimits = ArchiveLimits(),
    private val clipDuration: ClipDuration = RetrieverClipDuration,
    private val equipment: EquipmentSource = EquipmentSource { emptyList() },
) {
    /**
     * Imports the file [open] opens. When its [size] in bytes is known, [progress] is told the share of the work done
     * as it is read: the file is read twice — the document is checked first, the media written next — so each reading
     * counts for half.
     */
    suspend fun import(size: Long = UNKNOWN_SIZE, progress: (Float) -> Unit = {}, open: () -> InputStream): ImportOutcome {
        val recipes = recipes.inOpenBook()
        val counted = if (size <= 0) open else counting(open, 2 * size, progress)
        return withContext(Dispatchers.IO) { importInto(recipes, counted) }
    }

    private suspend fun importInto(recipes: RecipeRepository, open: () -> InputStream): ImportOutcome = coroutineScope {
        val work = coroutineContext
        val alive = { work.ensureActive() }
        // A dictionary that cannot be read names nothing: the file is read as its own words have it.
        val dictionary = try {
            equipment.equipment()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
        val names = EquipmentNames(dictionary)
        val content = try {
            RecipeArchiveReader.document(open, limits, alive, names::entryOf)
        } catch (e: RefusedFile) {
            return@coroutineScope ImportOutcome.Refused(e.refusal)
        }.let { withKnownEntries(it, dictionary.mapTo(HashSet()) { entry -> entry.id }) }
        val held = recipes.get(content.id)
        if (held != null || recipes.isMarked(content.id)) {
            // Named as the book holds it, which is what the person will find there.
            return@coroutineScope ImportOutcome.AlreadyInBook(content.id, held?.title ?: content.title)
        }
        val directory = mediaDirectory(filesDirectory, recipes.bookName())
        val written = mutableListOf<Pair<Media, File>>()
        try {
            RecipeArchiveReader.media(open, content, target = { item ->
                if (!directory.isDirectory && !directory.mkdirs()) throw RefusedFile(Refusal.CANNOT_OPEN, "no media directory")
                val media = Media(UUID.randomUUID().toString(), null, item.kind, item.attachment, MediaVisibility.PRIVATE)
                File(directory, media.mediaId).also { written += media to it }
            }, limits, alive)
            // MEDIA-007: a clip is held to its length before the recipe is believed, each read from its file once written.
            written.filter { (media, _) -> media.kind == MediaKind.VIDEO }.forEach { (_, file) ->
                alive()
                RecipeArchiveReader.checkClip(file, clipDuration::of)
            }
            val at = clock()
            val recipe = foundRecipe(content, written.map { it.first }, at)
            if (!recipes.addFound(recipe, at)) {
                written.forEach { it.second.delete() }
                return@coroutineScope ImportOutcome.AlreadyInBook(content.id, content.title)
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

    /**
     * Opens [uri] through [context]'s content resolver and imports what it holds. A `file:` address into the
     * application's own data is refused unread: no other application has any file of its own there to hand over.
     */
    suspend fun import(context: Context, uri: Uri, progress: (Float) -> Unit = {}): ImportOutcome =
        if (isPrivateFile(context, uri)) {
            ImportOutcome.Refused(Refusal.CANNOT_OPEN)
        } else {
            val size = withContext(Dispatchers.IO) { sizeOf(context, uri) }
            import(size, progress) { context.contentResolver.openInputStream(uri) ?: throw java.io.FileNotFoundException("$uri") }
        }

    /**
     * Removes the found recipe [id] from the active book with its held content and its media files (FLOW-064 (6)).
     * Returns whether the book marked it.
     */
    suspend fun remove(id: RecipeId): Boolean = recipes.inOpenBook().removeMarked(id, filesDirectory)

    /**
     * Takes the found recipe [id] out of the active book with its held content and its mark, its media files left
     * until the removal is final or undone (FLOW-053, [RecipeRepository.take]); null when the book held no such recipe.
     */
    suspend fun take(id: RecipeId): TakenRecipe? = recipes.inOpenBook().take(id, filesDirectory)

    companion object {
        /**
         * [content] with each item of equipment naming an entry the dictionary does not hold — of the ids [known] — as
         * free text, its label in the recipe's language and its qualifier, where the file gives its labels; kept as it
         * is where it does not. With no dictionary at all, nothing is changed.
         */
        fun withKnownEntries(content: RecipeFileContent, known: Set<Long>): RecipeFileContent {
            if (known.isEmpty()) return content
            val equipment = content.equipment.map { item ->
                val entry = item.dictionaryEntry?.takeIf { it !in known } ?: return@map item
                content.equipmentLabels[entry]?.let { EquipmentItem.free(item.shown(it.of(content.language))) } ?: item
            }
            return if (equipment == content.equipment) content else content.copy(equipment = equipment)
        }

        /** The size of a file whose size is not known. */
        const val UNKNOWN_SIZE = -1L

        /** The size in bytes of the file at [uri], as its provider or its file system states it, or [UNKNOWN_SIZE]. */
        fun sizeOf(context: Context, uri: Uri): Long = try {
            if (uri.scheme == android.content.ContentResolver.SCHEME_FILE) {
                uri.path?.let { File(it).length() }?.takeIf { it > 0 } ?: UNKNOWN_SIZE
            } else {
                context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0).takeIf { it > 0 } else null
                } ?: UNKNOWN_SIZE
            }
        } catch (e: Exception) {
            UNKNOWN_SIZE
        }

        /**
         * Whether [uri] is a `file:` address inside the application's own data — its files, its cache with the
         * exports, its books' databases — however it is spelt: the path is resolved, links and `..` with it.
         */
        fun isPrivateFile(context: Context, uri: Uri): Boolean {
            if (uri.scheme != android.content.ContentResolver.SCHEME_FILE) return false
            val path = uri.path ?: return true
            val file = try {
                File(path).canonicalFile
            } catch (e: java.io.IOException) {
                return true
            }
            val own = listOfNotNull(
                context.dataDir, context.filesDir, context.cacheDir, context.noBackupFilesDir, context.codeCacheDir,
                context.getDatabasePath(BookName.Pool.fileName).parentFile,
            )
            return own.any { root ->
                val canonical = try { root.canonicalFile } catch (e: java.io.IOException) { root.absoluteFile }
                file == canonical || file.startsWith(canonical)
            }
        }

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
                ingredientGroups = content.ingredientGroups,
                equipment = content.equipment,
                steps = content.steps,
                media = media,
            ).inState(FOUND_STATE)

        /** The state a found recipe is held in: not editable. */
        val FOUND_STATE = RecipeState.LINK_SHARED
    }
}

/**
 * A recipe file handed to the application by another (FLOW-064): the `content:` or `file:` address an `ACTION_VIEW`
 * intent carries, or the stream an `ACTION_SEND` of the product's type carries — never a saved book ([BookFileIntents]),
 * which the import would refuse. A timer's notification is a view of the application's own address, and is no file.
 */
object RecipeFileIntents {
    internal val FILE_SCHEMES = setOf(android.content.ContentResolver.SCHEME_CONTENT, android.content.ContentResolver.SCHEME_FILE)

    fun fileOf(intent: Intent?): Uri? = when (intent?.action) {
        Intent.ACTION_VIEW -> intent.data
        Intent.ACTION_SEND -> intent.takeIf { it.type == RecipeFile.MIME_TYPE }?.let(::streamOf)
        else -> null
    }?.takeIf { it.scheme in FILE_SCHEMES && !BookFileIntents.isBookFile(it) && !CollectionFileIntents.isCollectionFile(it, intent?.type) }

    @Suppress("DEPRECATION")
    internal fun streamOf(intent: Intent): Uri? = intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri

    /**
     * The intent that hands the file [intent] opens or shares on to [MainActivity], in the application's own task —
     * the one already open, its screen followed as a newly opened file — with the reading of the file granted on: a
     * recipe file as a view, to be imported; a saved book as [BookFileIntents.ACTION_RESTORE], to be restored; a
     * collection file as [CollectionFileIntents.ACTION_IMPORT], to be imported as a collection. Null where [intent]
     * holds no file.
     */
    fun forward(context: Context, intent: Intent?): Intent? {
        val book = BookFileIntents.arriving(intent)
        val collection = if (book == null) CollectionFileIntents.arriving(intent) else null
        val uri = book ?: collection ?: fileOf(intent) ?: return null
        val action = when {
            book != null -> BookFileIntents.ACTION_RESTORE
            collection != null -> CollectionFileIntents.ACTION_IMPORT
            else -> Intent.ACTION_VIEW
        }
        return Intent(context, MainActivity::class.java)
            .setAction(action)
            .setDataAndType(uri, intent?.type)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
            .also { it.clipData = ClipData.newRawUri(null, uri) }
    }

    /**
     * Hands the file [intent] opens or shares on to [MainActivity] through [start] ([forward]). An address the
     * application was handed no reading of cannot have its reading granted on — the platform refuses the start — so it
     * is handed on bare, and the import says the file cannot be read.
     */
    fun handOn(context: Context, intent: Intent?, start: (Intent) -> Unit) {
        val forward = forward(context, intent) ?: return
        try {
            start(forward)
        } catch (e: SecurityException) {
            start(Intent(forward).apply {
                clipData = null
                removeFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
        }
    }
}

/** Imports [uri], turning any failure of the device into a refusal said to the person rather than a crash. */
suspend fun RecipeImport.importSafely(context: Context, uri: Uri, progress: (Float) -> Unit = {}): ImportOutcome = try {
    import(context, uri, progress)
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    ImportOutcome.Refused(Refusal.CANNOT_OPEN)
}
