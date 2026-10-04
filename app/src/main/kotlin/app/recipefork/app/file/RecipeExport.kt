package app.recipefork.app.file

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.media.PhotoStore
import app.recipefork.core.file.FileMedia
import app.recipefork.core.file.RecipeFile
import app.recipefork.core.file.RecipeFileCheck
import app.recipefork.core.file.RecipeFileContent
import app.recipefork.core.recipe.Media
import app.recipefork.core.recipe.RecipeId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Export of a recipe to a file (FLOW-063): the file is written to the application's cache and handed to the
 * operating system's share and save sheet through a [FileProvider]; where it goes is the person's choice, and the
 * product sends it nowhere itself. Export only reads the recipe: it changes nothing in it, and freezes nothing.
 */
object RecipeExport {
    /** The directory under the cache that the [FileProvider] shares (res/xml/file_paths.xml). */
    private const val DIRECTORY = "exports"

    fun authority(context: Context) = context.packageName + ".files"

    /**
     * Writes the file of the recipe [id] of the active book, with every photograph the device holds for it, and returns
     * its shareable address, or null when the book holds no such recipe. No account exists on the device yet, so no recipe is an account's own and the file names
     * an author only where the recipe states one (FLOW-063 (3)). [progress] is told the share of the file written, of
     * the size its document and its media make.
     */
    suspend fun write(context: Context, recipes: RecipeRepository, id: RecipeId, progress: (Float) -> Unit = {}): Uri? {
        val book = recipes.inOpenBook()
        val recipe = book.get(id) ?: return null
        val parent = recipe.parent?.let { book.get(it) }
        val store = PhotoStore(context.filesDir)
        val name = book.bookName()
        val content = withContext(Dispatchers.IO) {
            RecipeFileContent.of(recipe, parentInBook = parent, ownerNickname = null)
                .copy(media = fileMedia(recipe.media) { store.file(name, it).isFile })
        }
        val file = withContext(Dispatchers.IO) {
            // Only the file of this export is kept: those of earlier exports, and of recipes since deleted, go first.
            val exports = File(context.cacheDir, DIRECTORY)
            exports.deleteRecursively()
            val directory = File(exports, id.value.filter { it.isLetterOrDigit() || it == '-' }.ifEmpty { FALLBACK_NAME })
            if (!directory.mkdirs()) throw IOException("cannot make $directory")
            File(directory, fileName(recipe.title)).also { file ->
                val total = RecipeFile.encode(content).length + content.media.sumOf { store.file(name, it.mediaId).length() }
                file.outputStream().buffered().use { out ->
                    RecipeArchive.write(content, counting(out, total.coerceAtLeast(1), progress)) { store.file(name, it.mediaId) }
                }
            }
        }
        return FileProvider.getUriForFile(context, authority(context), file)
    }

    /** [out], telling [progress] the share of [total] bytes written through it. */
    private fun counting(out: java.io.OutputStream, total: Long, progress: (Float) -> Unit) = object : java.io.FilterOutputStream(out) {
        private var written = 0L
        override fun write(b: Int) {
            out.write(b)
            told(1)
        }
        override fun write(b: ByteArray, off: Int, len: Int) {
            out.write(b, off, len)
            told(len)
        }
        private fun told(bytes: Int) {
            written += bytes
            progress((written.toFloat() / total).coerceAtMost(1f))
        }
    }

    /**
     * The document's entries for the recipe's [media] (FLOW-063): every item whose file the device holds — [held] says
     * which — each under `media/` by its identity. An item whose file is gone is left out, so the document never names
     * an entry the archive does not hold.
     */
    fun fileMedia(media: List<Media>, held: (String) -> Boolean): List<FileMedia> = media
        .map { FileMedia(it.mediaId, it.kind, it.attachment, RecipeFile.MEDIA_DIRECTORY + it.mediaId) }
        .filter { RecipeFileCheck.isMediaEntry(it.entry) && held(it.mediaId) }

    /**
     * Writes the file of the recipe [id], telling [progress] how far, and opens the share and save sheet on it, headed
     * [sheetTitle]. Returns false
     * when no file could be written or no sheet opened, so that the person can be told.
     */
    suspend fun share(
        context: Context,
        recipes: RecipeRepository,
        id: RecipeId,
        sheetTitle: CharSequence,
        progress: (Float) -> Unit = {},
    ): Boolean =
        try {
            val uri = write(context, recipes, id, progress)
            if (uri != null) open(context, shareSheet(uri, sheetTitle))
            uri != null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }

    /** The sheet that lets the person save or send the file at [uri]; [title] heads it. */
    fun shareSheet(uri: Uri, title: CharSequence): Intent {
        val send = Intent(Intent.ACTION_SEND)
            .setType(RecipeFile.MIME_TYPE)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(null, uri)
        return Intent.createChooser(send, title)
    }

    /** Opens [sheet] from [context]; outside an activity, in a task of its own. */
    fun open(context: Context, sheet: Intent) {
        if (context.findActivity() == null) sheet.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(sheet)
    }

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

    /**
     * The file's name: the recipe's title with what a file system or a messenger would refuse taken out, or `recipe`
     * for a recipe with no title; then the product's extension.
     */
    fun fileName(title: String): String {
        val forbidden = "/\\:*?\"<>|"
        val cleaned = title.map { if (it in forbidden || it.isISOControl()) ' ' else it }.joinToString("")
            .replace(Regex("\\s+"), " ").trim().trim('.').take(MAX_NAME).trim()
        return (cleaned.ifEmpty { FALLBACK_NAME }) + "." + RecipeFile.EXTENSION
    }

    private const val MAX_NAME = 80
    private const val FALLBACK_NAME = "recipe"
}
