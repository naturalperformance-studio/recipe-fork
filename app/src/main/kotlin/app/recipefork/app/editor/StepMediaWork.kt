package app.recipefork.app.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import app.recipefork.app.media.PhotoAttaching
import app.recipefork.core.recipe.RecipeId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * The media work of one recipe's steps, held past the activity being recreated — the phone turned while a clip is
 * converted: each step's [PhotoAttaching] by the step's key, which outlives the recreation too, so that the new screen
 * shows the conversion still running, can cancel it, and says what it ends in once. The work places what it prepared
 * through the editor shown now, once that one has read the recipe, never through one whose screen is gone; left for
 * good, the last editor shown takes it, as before.
 */
internal class StepMediaWork : ViewModel() {
    private val editors = MutableStateFlow<RecipeEditor?>(null)
    private val attachings = HashMap<Long, PhotoAttaching>()

    /** The editor shown now; the work places through it from now on. */
    fun show(editor: RecipeEditor) {
        editors.value = editor
    }

    /** The attaching of the step whose key is [key], made with [photos] the first time it is asked for. */
    fun attaching(key: Long, photos: StepPhotos): PhotoAttaching = attachings.getOrPut(key) {
        StepPhotoAttaching({ editors.value }, photos, key, ready = ::shownEditorRead)
    }

    /** Waits until the editor shown now has read the recipe — or gives up, as a recipe gone is never read. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun shownEditorRead() {
        withTimeoutOrNull(READ_PATIENCE) { editors.filterNotNull().flatMapLatest { it.recipe }.first { it != null } }
    }

    private companion object {
        val READ_PATIENCE = 10.seconds
    }
}

/** The step media work of [recipeId] in the screen's activity, showing [editor] from now on. */
@Composable
internal fun rememberStepMediaWork(recipeId: RecipeId, editor: RecipeEditor): StepMediaWork {
    val work = viewModel(key = WORK_KEY + recipeId.value) { StepMediaWork() }
    return remember(work, editor) { work.apply { show(editor) } }
}

/** The prefix of the key a recipe's step media work is held under; the recipe's identity follows. */
private const val WORK_KEY = "step-media-"
