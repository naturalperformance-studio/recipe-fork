package app.recipefork.app.library

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.recipefork.app.card.DoneMarks
import app.recipefork.app.file.RecipeImport
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.recipe.TakenRecipe
import app.recipefork.app.timer.CardTimers
import app.recipefork.core.recipe.RecipeId
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock

/**
 * The confirm button of a statement before data is destroyed: it names the action, and is drawn in the theme's error
 * colour so that it reads as destructive.
 */
@Composable
internal fun DestructiveConfirm(@StringRes label: Int, tag: String, target: Dp = 48.dp, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
        modifier = Modifier.heightIn(min = target).widthIn(min = target).testTag(tag),
    ) { Text(stringResource(label)) }
}

/**
 * Recipes taken out of the book together — one from its entry or its card, or several chosen in the book's selection —
 * asking nothing, whose leaving is not yet final (FLOW-053): the book says so with one Undo while its notice is shown
 * ([LibrarySession.removal]). [undo] puts every one of them back whole; [finish] makes their leaving final, deleting the
 * files of their media that no other recipe holds. Whichever comes first is done, once; the other then does nothing.
 * Both run where the timers' work runs, which outlives the screen, one turn among the timers' starts and cancels.
 */
class PendingRemoval internal constructor(private val taken: List<TakenRecipe>, private val timers: CardTimers) {
    /** One recipe taken out alone. */
    internal constructor(taken: TakenRecipe, timers: CardTimers) : this(listOf(taken), timers)

    init {
        require(taken.isNotEmpty())
    }

    /** The identities of the recipes that left, in the order they were taken. */
    val recipeIds: List<String> get() = taken.map { it.id.value }

    /** How many recipes left together. */
    val count: Int get() = taken.size

    /** Whether they were found recipes, removed, rather than own ones, deleted. */
    val found: Boolean get() = taken.all { it.found }

    /** The title of the first that left, which the notice of one alone names it by; blank for a recipe never titled. */
    val title: String get() = taken.first().title

    private val settled = AtomicBoolean(false)

    /** How many of them [undo] could not put back: none until it has run. */
    @Volatile
    var notBack: Int = 0
        private set

    /**
     * Puts each recipe back as it was: its content, photographs and clips, its place in the book and in its
     * collections, a found one's mark. Their done marks and their timers, ended as they left, stay ended. Should the book
     * not take one back — it holds that recipe again, or a media item of it, or cannot be written — that one's leaving is
     * final instead, as [finish] makes it, the others come back all the same, and [onFailed] is told once, [notBack]
     * counting them. [onBack] is told the identities of those put back, in the order they were taken, when any were.
     */
    fun undo(onFailed: () -> Unit = {}, onBack: (List<String>) -> Unit = {}) {
        if (!settled.compareAndSet(false, true)) return
        timers.scope.launch {
            timers.turns.withLock {
                val back = mutableListOf<String>()
                taken.forEach { recipe ->
                    if (recipe.putBack()) {
                        back += recipe.id.value
                    } else {
                        recipe.release()
                    }
                }
                notBack = taken.size - back.size
                if (back.isNotEmpty()) onBack(back)
                if (notBack > 0) onFailed()
            }
        }
    }

    /** The leaving is final: the files of the recipes' media that no recipe of the book holds are deleted (MEDIA-012). */
    fun finish() {
        if (settled.compareAndSet(false, true)) timers.scope.launch { timers.turns.withLock { taken.forEach { it.release() } } }
    }
}

/**
 * Deletes the own recipe [id] from the active book, on the device alone (FLOW-053 (6, 8)), asking nothing: it leaves
 * the book at once, its timers are cancelled, so that none of them announces itself or rings afterwards, and its done
 * [marks] are dropped (FLOW-068). Its media files stay until [onTaken], told of it, has it undone or finished — at
 * once, by default.
 */
fun deleteRecipe(
    recipes: RecipeRepository,
    timers: CardTimers,
    marks: DoneMarks,
    id: RecipeId,
    onTaken: (PendingRemoval) -> Unit = PendingRemoval::finish,
) = deleteRecipes(recipes, timers, marks, listOf(id), onTaken)

/**
 * Deletes the own recipes [ids] from the active book together, each as [deleteRecipe] deletes one; [onTaken] is told
 * once, of all that left, so that one Undo brings them all back.
 */
fun deleteRecipes(
    recipes: RecipeRepository,
    timers: CardTimers,
    marks: DoneMarks,
    ids: List<RecipeId>,
    onTaken: (PendingRemoval) -> Unit = PendingRemoval::finish,
) = take(timers, marks, ids, onTaken) { recipes.take(it) }

/**
 * Removes the found recipe [id] from the active book with its held content and its mark (FLOW-064 (6)), as
 * [deleteRecipe] deletes an own one.
 */
fun removeFound(
    files: RecipeImport,
    timers: CardTimers,
    marks: DoneMarks,
    id: RecipeId,
    onTaken: (PendingRemoval) -> Unit = PendingRemoval::finish,
) = removeAllFound(files, timers, marks, listOf(id), onTaken)

/** Removes the found recipes [ids] from the active book together, as [deleteRecipes] deletes own ones. */
fun removeAllFound(
    files: RecipeImport,
    timers: CardTimers,
    marks: DoneMarks,
    ids: List<RecipeId>,
    onTaken: (PendingRemoval) -> Unit = PendingRemoval::finish,
) = take(timers, marks, ids, onTaken) { files.take(it) }

/**
 * Takes each of [ids] out of the book by [taking], cancels its timers and drops its done [marks], in one turn among the
 * timers' starts and cancels, where the timers' work runs, so that it finishes however soon the view is left; [onTaken]
 * is told once of every one that left, none if none did. A session cleared before it finishes is told of a removal it
 * never says nor finishes: the files of their media are left to the media sweep, which skips a book that holds no
 * recipe at all.
 */
private fun take(
    timers: CardTimers,
    marks: DoneMarks,
    ids: List<RecipeId>,
    onTaken: (PendingRemoval) -> Unit,
    taking: suspend (RecipeId) -> TakenRecipe?,
) {
    if (ids.isEmpty()) return
    timers.scope.launch {
        timers.turns.withLock {
            val taken = ids.distinct().mapNotNull { id ->
                taking(id).also {
                    timers.store.cancelRecipe(timers.book(), id)
                    marks.clear(timers.book(), id)
                }
            }
            if (taken.isNotEmpty()) onTaken(PendingRemoval(taken, timers))
        }
    }
}
