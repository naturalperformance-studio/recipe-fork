package app.recipefork.app.editor

import android.content.res.Configuration
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.dictionary.TagEntity
import app.recipefork.app.dictionary.TagSource
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.recipe.AccountId
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.RecipeLanguage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant

/** What the editor needs beyond the recipes of the active book: the book itself, the tag dictionary and a clock. */
class EditorEnvironment(
    val book: ActiveBook,
    val tags: TagSource,
    val clock: () -> Instant = Instant::now,
)

/** The author's interface language (L10N-001): Russian where the interface is Russian, English otherwise. */
fun interfaceLanguage(configuration: Configuration): RecipeLanguage =
    if (configuration.locales[0].language == "ru") RecipeLanguage.RU else RecipeLanguage.EN

/**
 * A new recipe in `local`, in the active book — the pool's when no account is active (FLOW-042, FLOW-002) — whose
 * declared language is already the author's interface language (L10N-004, DOM-006).
 */
fun newRecipe(environment: EditorEnvironment, language: RecipeLanguage): Recipe {
    val owner = (environment.book.open.value.name as? BookName.OfAccount)?.let { AccountId(it.accountId) }
    return Recipe.create(environment.clock(), owner).copy(language = language)
}

/**
 * One recipe open in the editor. Every change is written to the active book as it is made, in the order it was
 * made, so that leaving the editor at any point loses nothing (FLOW-024). Only a state that `entity-states`
 * makes editable accepts a change (FLOW-002).
 */
class RecipeEditor(
    private val recipes: RecipeRepository,
    val id: RecipeId,
    private val clock: () -> Instant,
    /** Outlives the screen, so that a change made just before leaving is still written. */
    private val saving: CoroutineScope,
) {
    private val current = MutableStateFlow<Recipe?>(null)

    /** The recipe as the author has it now; null until it is read from the book. */
    val recipe: StateFlow<Recipe?> = current.asStateFlow()

    /** Reads the recipe once every change still being written, by this editor or an earlier one, is in the book. */
    suspend fun load() {
        current.value = writes.withLock { recipes.get(id) }
    }

    val editable: Boolean get() = current.value?.state?.editable == true

    fun setTitle(title: String) = edit { it.copy(title = title) }

    fun setLanguage(language: RecipeLanguage?) = edit { it.copy(language = language) }

    /** A tag is committed only as an entry the dictionary offered (DOM-008). */
    fun addTag(tag: TagEntity) = edit { it.copy(tags = it.tags + tag.id) }

    fun removeTag(tagId: Long) = edit { it.copy(tags = it.tags - tagId) }

    /** Applies [change] and writes the result; a recipe in a frozen state is left as it is. */
    private fun edit(change: (Recipe) -> Recipe): Boolean {
        val recipe = current.value ?: return false
        if (!recipe.state.editable) return false
        val next = recipe.edited(clock(), change)
        current.value = next
        // Launched in order and queued on a fair lock, so the book receives the changes in the order made.
        saving.launch { writes.withLock { recipes.save(next) } }
        return true
    }

    private companion object {
        /** One queue of writes for every editor, so that an editor opened again reads what the last one wrote. */
        val writes = Mutex()
    }
}
