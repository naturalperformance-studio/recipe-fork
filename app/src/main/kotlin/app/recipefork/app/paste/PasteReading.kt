package app.recipefork.app.paste

import android.content.Context
import android.content.Intent
import app.recipefork.app.MainActivity
import app.recipefork.app.book.BookName
import app.recipefork.app.editor.EditorEnvironment
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.api.DictionariesDto
import app.recipefork.core.paste.RecipeFromText
import app.recipefork.core.recipe.AccountId
import app.recipefork.core.recipe.RecipeId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads a pasted or shared text into a recipe of the owner's (T-280): through [RecipeFromText] with the dictionaries
 * the editor offers from, its source text the text as given, written to the active book as the editor writes a new
 * recipe. The reading runs off the main thread.
 */
class PasteReading(private val recipes: RecipeRepository, private val environment: EditorEnvironment) {
    /** The recipe [text] holds, now in the book, or null when it holds none — and nothing is written. */
    suspend fun read(text: String): RecipeId? {
        val dictionaries = withContext(Dispatchers.IO) {
            DictionariesDto(
                version = "",
                ingredients = environment.ingredients.ingredients().map { it.toDto() },
                tags = emptyList(),
                equipment = environment.equipment.equipment().map { it.toDto() },
            )
        }
        // Owned as the editor's new recipe is: by the active book's account, the pool's when none is active.
        val owner = (environment.book.open.value.name as? BookName.OfAccount)?.let { AccountId(it.accountId) }
        val at = environment.clock()
        val recipe = withContext(Dispatchers.Default) {
            RecipeFromText.read(text, dictionaries, at, owner)?.withSourceText(text)
        } ?: return null
        recipes.save(recipe)
        return recipe.id
    }
}

/** A text shared to the application (T-280): `text/plain` sent with ACTION_SEND. */
object PasteIntents {
    /**
     * The text [intent] shares, or null when it shares none: its EXTRA_TEXT, with its EXTRA_SUBJECT as a first line where
     * one is given and the text does not already begin with it.
     */
    fun textOf(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_SEND) return null
        if (intent.type?.startsWith("text/") != true) return null
        val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.takeIf { it.isNotBlank() } ?: return null
        val subject = intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            ?: return text
        return if (text.trimStart().startsWith(subject)) text else subject + "\n" + text
    }

    /**
     * The text [intent] shares ([textOf]), handed on to [MainActivity] in the application's own task, as a shared file is;
     * null where it shares none.
     */
    fun forward(context: Context, intent: Intent?): Intent? {
        val text = textOf(intent) ?: return null
        return Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_SEND)
            .setType(TEXT_TYPE)
            .putExtra(Intent.EXTRA_TEXT, text)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }

    private const val TEXT_TYPE = "text/plain"
}
