package app.recipefork.app.file

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.file.RecipeFile
import app.recipefork.core.file.RecipeFileContent
import app.recipefork.core.recipe.RecipeId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

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
     * Writes the file of the recipe [id] of the active book and returns its shareable address, or null when the book
     * holds no such recipe. No account exists on the device yet, so no recipe is an account's own and the file names
     * an author only where the recipe states one (FLOW-063 (3)).
     */
    suspend fun write(context: Context, recipes: RecipeRepository, id: RecipeId): Uri? {
        val recipe = recipes.get(id) ?: return null
        val parent = recipe.parent?.let { recipes.get(it) }
        val content = RecipeFileContent.of(recipe, parentInBook = parent, ownerNickname = null)
        val file = withContext(Dispatchers.IO) {
            // One directory per recipe, emptied first: a file shared a moment ago under another title is not left behind.
            val directory = File(File(context.cacheDir, DIRECTORY), id.value.filter { it.isLetterOrDigit() || it == '-' })
            directory.deleteRecursively()
            directory.mkdirs()
            File(directory, fileName(recipe.title)).also { file ->
                file.outputStream().buffered().use { RecipeArchive.write(content, it) }
            }
        }
        return FileProvider.getUriForFile(context, authority(context), file)
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
