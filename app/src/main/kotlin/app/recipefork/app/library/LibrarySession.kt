package app.recipefork.app.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow

/** The two views of the recipe book (FLOW-034). */
enum class LibraryView { MARKED, OWN }

/**
 * What the recipe book section keeps for the length of a session (FLOW-058).
 *
 * It lives as long as the Activity's task: it survives leaving the section, the home action and a
 * configuration change in memory, and the end of the process through saved state. A task started anew — after
 * the application was removed from the recent applications, or after a restart of the device — has no saved
 * state, so a new session opens on the marked recipes (FLOW-034 (6)).
 */
class LibrarySession(private val saved: SavedStateHandle) : ViewModel() {
    val view: StateFlow<LibraryView> = saved.getStateFlow(VIEW_KEY, LibraryView.MARKED)

    fun choose(view: LibraryView) {
        saved[VIEW_KEY] = view
    }

    /**
     * The recipe open in the editor, if one is: kept like the view, so that an author who leaves the editor by the
     * home action, or whose process ends while away, returns to it (FLOW-024).
     */
    val editing: StateFlow<String?> = saved.getStateFlow(EDITING_KEY, null)

    fun openEditor(recipeId: String) {
        saved[EDITING_KEY] = recipeId
    }

    fun closeEditor() {
        saved[EDITING_KEY] = null
    }

    private companion object {
        const val VIEW_KEY = "library.view"
        const val EDITING_KEY = "library.editing"
    }
}
