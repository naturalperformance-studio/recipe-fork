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
 * A recipe taken out of the book from its entry or its card, asking nothing, whose leaving is not yet final (FLOW-053):
 * the book says so with Undo while its notice is shown ([LibrarySession.removal]). [undo] puts it back whole; [finish]
 * makes its leaving final, deleting the files of its media that no other recipe holds. Whichever comes first is done,
 * once; the other then does nothing. Both run where the timers' work runs, which outlives the screen, one turn among
 * the timers' starts and cancels.
 */
class PendingRemoval internal constructor(private val taken: TakenRecipe, private val timers: CardTimers) {
    val recipeId: String get() = taken.id.value

    /** Whether it was a found recipe, removed, rather than an own one, deleted. */
    val found: Boolean get() = taken.found

    private val settled = AtomicBoolean(false)

    /**
     * Puts the recipe back as it was: its content, photographs and clips, its place in the book, a found one's mark.
     * Its done marks and its timers, ended as it left, stay ended. Should the book not take it back — it holds that
     * recipe again, or a media item of it, or cannot be written — the leaving is final instead, as [finish] makes it,
     * and [onFailed] is told.
     */
    fun undo(onFailed: () -> Unit = {}) {
        if (!settled.compareAndSet(false, true)) return
        timers.scope.launch {
            timers.turns.withLock {
                if (!taken.putBack()) {
                    taken.release()
                    onFailed()
                }
            }
        }
    }

    /** The leaving is final: the files of the recipe's media that no recipe of the book holds are deleted (MEDIA-012). */
    fun finish() {
        if (settled.compareAndSet(false, true)) timers.scope.launch { timers.turns.withLock { taken.release() } }
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
) = take(timers, marks, id, onTaken) { recipes.take(id) }

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
) = take(timers, marks, id, onTaken) { files.take(id) }

/**
 * Takes [id] out of the book by [taking], cancels its timers and drops its done [marks], one turn among the timers'
 * starts and cancels, where the timers' work runs, so that it finishes however soon the view is left. A session
 * cleared before it finishes is told of a removal it never says nor finishes: the files of its media are left to the
 * media sweep, which skips a book that holds no recipe at all.
 */
private fun take(
    timers: CardTimers,
    marks: DoneMarks,
    id: RecipeId,
    onTaken: (PendingRemoval) -> Unit,
    taking: suspend () -> TakenRecipe?,
) {
    timers.scope.launch {
        timers.turns.withLock {
            val taken = taking()
            timers.store.cancelRecipe(timers.book(), id)
            marks.clear(timers.book(), id)
            taken?.let { onTaken(PendingRemoval(it, timers)) }
        }
    }
}
