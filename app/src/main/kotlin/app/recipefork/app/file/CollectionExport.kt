package app.recipefork.app.file

import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import app.recipefork.app.collections.CollectionRepository
import app.recipefork.app.dictionary.EquipmentSource
import app.recipefork.app.media.PhotoStore
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.file.CollectionFile
import app.recipefork.core.file.CollectionFileContent
import app.recipefork.core.file.RecipeFile
import app.recipefork.core.recipe.CollectionId
import app.recipefork.core.recipe.RecipeCollection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Sharing a collection as a file (v0.36): its file ([CollectionFile]) — the collection's document and each of its
 * recipes, own and found, as its recipe file holds it, with the photographs and clips the device holds — is written to
 * the application's cache and handed to the system's share and save sheet through the [FileProvider], as a recipe's
 * export is (FLOW-063). Each recipe is written by the rules of [RecipeExport]: a found one keeps its stated author and
 * parent. Sharing reads the book and changes nothing in it.
 *
 * Only the latest export is kept: once the file is written whole, the files of earlier exports, recipes' and
 * collections', go. One that fails leaves them as they were and keeps nothing of itself.
 */
object CollectionExport {
    /** Where a file is written before it is whole: outside what the [FileProvider] shares. */
    private const val STAGING = "exports-staging"
    private const val FALLBACK_NAME = "collection"

    /**
     * Writes the file of the collection [id] of the active book and returns its shareable address, or null when the
     * book holds no such collection. [progress] is told the share of the file written, of the size its documents and
     * its media make.
     */
    suspend fun write(
        context: Context,
        recipes: RecipeRepository,
        collections: CollectionRepository,
        id: CollectionId,
        equipment: EquipmentSource = equipmentDictionaryOf(context),
        progress: (Float) -> Unit = {},
    ): Uri? {
        val collection = collections.get(id) ?: return null
        return write(context, recipes, collection, equipment, progress)
    }

    /** Writes the file of [collection], as the book holds it, and returns its shareable address. */
    private suspend fun write(
        context: Context,
        recipes: RecipeRepository,
        collection: RecipeCollection,
        equipment: EquipmentSource,
        progress: (Float) -> Unit,
    ): Uri {
        val book = recipes.inOpenBook()
        val held = collection.recipes.mapNotNull { book.get(it) }
        val store = PhotoStore(context.filesDir)
        val name = book.bookName()
        val labels = RecipeExport.equipmentLabels(held, equipment)
        val contents = withContext(Dispatchers.IO) {
            held.map { recipe -> RecipeExport.fileContent(book, recipe, labels) { store.file(name, it).isFile } }
        }
        val document = CollectionFileContent.of(collection, ownerNickname = null, recipes = contents.map { it.id })
        val file = withContext(Dispatchers.IO) {
            val fileName = fileName(collection.name)
            val staging = File(context.cacheDir, STAGING)
            staging.deleteRecursively()
            if (!staging.mkdirs()) throw IOException("cannot make $staging")
            try {
                val staged = File(staging, fileName)
                val total = CollectionFile.encode(document).toByteArray(Charsets.UTF_8).size +
                    contents.sumOf { content ->
                        RecipeFile.encode(content).toByteArray(Charsets.UTF_8).size +
                            content.media.sumOf { store.file(name, it.mediaId).length() }
                    }
                staged.outputStream().buffered().use { out ->
                    CollectionArchive.write(document, contents, RecipeExport.counting(out, total.coerceAtLeast(1), progress)) { _, item ->
                        store.file(name, item.mediaId)
                    }
                }
                // Whole: it is put in its place first, and only then do the earlier exports go.
                val exports = File(context.cacheDir, RecipeExport.DIRECTORY)
                val folder = collection.id.value.filter { it.isLetterOrDigit() || it == '-' }.ifEmpty { FALLBACK_NAME }
                val directory = File(exports, folder)
                if (!directory.isDirectory && !directory.mkdirs()) throw IOException("cannot make $directory")
                val target = File(directory, fileName)
                if (!staged.renameTo(target)) throw IOException("cannot move $staged")
                exports.listFiles().orEmpty().filter { it != directory }.forEach { it.deleteRecursively() }
                directory.listFiles().orEmpty().filter { it != target }.forEach { it.deleteRecursively() }
                target
            } finally {
                staging.deleteRecursively()
            }
        }
        // The archive compresses what it can, so what is written falls short of the total: written is whole.
        progress(1f)
        return FileProvider.getUriForFile(context, RecipeExport.authority(context), file)
    }

    /**
     * Writes the file of the collection [id], telling [progress] how far, and opens the share and save sheet on it,
     * headed by [sheetTitle] of the collection's name. Returns false when no file could be written or no sheet opened,
     * so that the person can be told.
     */
    suspend fun share(
        context: Context,
        recipes: RecipeRepository,
        collections: CollectionRepository,
        id: CollectionId,
        sheetTitle: (name: String) -> CharSequence,
        equipment: EquipmentSource = equipmentDictionaryOf(context),
        progress: (Float) -> Unit = {},
    ): Boolean =
        try {
            // One read of the collection gives both the file and the sheet's title.
            val collection = collections.get(id)
            if (collection != null) {
                val uri = write(context, recipes, collection, equipment, progress)
                RecipeExport.open(context, shareSheet(context, uri, sheetTitle(collection.name)))
            }
            collection != null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }

    /**
     * The sheet that lets the person save or send the collection's file at [uri]; [title] heads it. The application
     * itself is not offered in it: the collection is in its book already.
     */
    fun shareSheet(context: Context, uri: Uri, title: CharSequence): Intent {
        val send = Intent(Intent.ACTION_SEND)
            .setType(CollectionFile.MIME_TYPE)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(null, uri)
        return Intent.createChooser(send, title)
            .putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(ComponentName(context, OpenRecipeFileActivity::class.java)))
    }

    /**
     * The file's name: the collection's [name] made safe as a recipe file's title is ([RecipeExport.fileName]) — in
     * ASCII, so that a messenger keeps the extension — `collection` when nothing of it is left, then the extension.
     */
    fun fileName(name: String): String = RecipeExport.safeFileName(name, CollectionFile.EXTENSION, FALLBACK_NAME)
}
