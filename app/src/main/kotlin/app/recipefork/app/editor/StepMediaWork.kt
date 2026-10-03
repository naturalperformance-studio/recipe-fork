package app.recipefork.app.editor

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
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
 * The media work of one recipe's steps, held past the screen that began it — the phone turned, or the editor left and
 * opened again, while a clip is converted: each step's [PhotoAttaching] by the step's key, and the recipe's [stepKeys]
 * themselves, so that a step keeps its key across both and the work always reaches the step it was offered for. The
 * new screen shows the conversion still running at that step, can cancel it, and says what it ends in once. The work
 * places what it prepared through the editor shown now, once that one has read the recipe; left for good, the last
 * editor shown takes it, as before. Once the editor is left for good with no work running, all of it is let go.
 */
internal class StepMediaWork : ViewModel() {
    private val editors = MutableStateFlow<RecipeEditor?>(null)
    private val attachings = mutableStateMapOf<Long, PhotoAttaching>()

    /** The keys of the recipe's steps, given out by the editor and kept here with the work that uses them. */
    var stepKeys: RowKeys? = null

    /** Every step's attaching, by the step's key — those of steps deleted since among them, until let go. */
    val all: Map<Long, PhotoAttaching> get() = attachings

    /** The editor shown now; the work places through it from now on. */
    fun show(editor: RecipeEditor) {
        editors.value = editor
    }

    /** The attaching of the step whose key is [key], made with [photos] the first time it is asked for. */
    fun attaching(key: Long, photos: StepPhotos): PhotoAttaching = attachings.getOrPut(key) {
        StepPhotoAttaching({ editors.value }, photos, key, ready = ::shownEditorRead)
    }

    /** The editor was left for good: with no work running, the work, the keys and the editor are let go. */
    fun left() {
        if (attachings.values.any { it.working.value }) return
        attachings.clear()
        stepKeys = null
        editors.value = null
    }

    /** Waits until the editor shown now has read the recipe; false when it never does, as a recipe gone is never read. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun shownEditorRead(): Boolean =
        withTimeoutOrNull(READ_PATIENCE) { editors.filterNotNull().flatMapLatest { it.recipe }.first { it != null } } != null

    private companion object {
        val READ_PATIENCE = 10.seconds
    }
}

/**
 * The step media work of [recipeId] in the screen's activity — the activity's, not the editor destination's, which is
 * let go as the editor is left, while the work outlives it.
 */
@Composable
internal fun rememberStepMediaWork(recipeId: RecipeId): StepMediaWork =
    viewModel(
        viewModelStoreOwner = LocalActivity.current as? ViewModelStoreOwner ?: checkNotNull(LocalViewModelStoreOwner.current),
        key = WORK_KEY + recipeId.value,
    ) { StepMediaWork() }

/** The prefix of the key a recipe's step media work is held under; the recipe's identity follows. */
private const val WORK_KEY = "step-media-"
