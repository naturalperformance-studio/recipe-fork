package app.recipefork.app.file

import android.content.Intent
import android.net.Uri
import app.recipefork.app.backup.BookFile

/**
 * A saved book (FLOW-073) opened or shared from another application: told from a recipe file by its name, and handed on
 * to [MainActivity][app.recipefork.app.MainActivity] as [ACTION_RESTORE], which starts the book's restore — never as a
 * view, which would import it as a recipe.
 */
object BookFileIntents {
    /** What [OpenRecipeFileActivity] hands a book file on to MainActivity as. */
    const val ACTION_RESTORE = "app.recipefork.app.action.RESTORE_BOOK"

    private const val SUFFIX = "." + BookFile.EXTENSION

    /** Whether [uri]'s path names a saved book, its extension in any case. */
    fun isBookFile(uri: Uri): Boolean = uri.path?.endsWith(SUFFIX, ignoreCase = true) == true

    /** The book file [intent] opens or shares, as another application starts it; null where it holds none. */
    fun arriving(intent: Intent?): Uri? = when (intent?.action) {
        Intent.ACTION_VIEW -> intent.data
        Intent.ACTION_SEND -> RecipeFileIntents.streamOf(intent) ?: intent.data
        else -> null
    }?.takeIf { it.scheme in RecipeFileIntents.FILE_SCHEMES && isBookFile(it) }

    /** The book file [intent] hands MainActivity to restore; null where it is no such hand-over. */
    fun toRestore(intent: Intent?): Uri? =
        intent?.takeIf { it.action == ACTION_RESTORE }?.data?.takeIf { it.scheme in RecipeFileIntents.FILE_SCHEMES }
}
