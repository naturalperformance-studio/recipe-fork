package app.recipefork.app.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.recipefork.app.card.CardAnchor
import app.recipefork.app.card.CardSession
import app.recipefork.app.card.HeldScale
import app.recipefork.app.card.StepToShow
import app.recipefork.app.card.TextCardSession
import app.recipefork.core.recipe.RecipeId
import app.recipefork.app.file.FileMessage
import app.recipefork.app.timer.TimerStep
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

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
        // Only an own recipe is edited, so the card shown on leaving the editor returns to the own recipes.
        saved[CARD_FROM_KEY] = LibraryView.OWN
    }

    /** Leaving the editor shows the card of the recipe just edited (FLOW-029 (1)). */
    fun closeEditor() {
        editing.value?.let { saved[VIEWING_KEY] = it }
        saved[EDITING_KEY] = null
    }

    /**
     * The recipe whose card is open, if one is (FLOW-029): kept like the editor's, so that a card open when the
     * process ends reopens on return. The editor opened from the card lies above it, and leaving it shows the card.
     */
    val viewing: StateFlow<String?> = saved.getStateFlow(VIEWING_KEY, null)

    fun openCard(recipeId: String, from: LibraryView? = null) {
        saved[VIEWING_KEY] = recipeId
        from?.let { saved[CARD_FROM_KEY] = it }
    }

    /**
     * Back from the card returns to the view its recipe was opened from: the own recipes, from which a saved recipe
     * is opened, or the marked ones, from which a found recipe is.
     */
    fun closeCard() {
        saved[VIEWING_KEY] = null
        saved[VIEW_KEY] = saved.get<LibraryView>(CARD_FROM_KEY) ?: LibraryView.OWN
    }

    /**
     * A recipe file was imported (FLOW-064 (1)): the view of marked recipes is shown, where it is listed now, in place
     * of whatever was open — an editor keeps what was entered as it is typed.
     */
    fun showImported() {
        saved[EDITING_KEY] = null
        saved[VIEWING_KEY] = null
        saved[VIEW_KEY] = LibraryView.MARKED
    }

    /**
     * A timer's notification was tapped: its recipe's card opens, in place of whatever was open — an editor keeps
     * what was entered as it is typed — and is brought to the timer's step (FLOW-013 (4)). Kept like the card, so
     * that the step is still shown should the process end before the card is drawn.
     */
    fun showStep(recipeId: String, position: Int) {
        saved[EDITING_KEY] = null
        saved[VIEWING_KEY] = recipeId
        saved[CARD_FROM_KEY] = null
        saved[STEP_KEY] = "$position/$recipeId"
        // The card is brought to the timer's step, not to the place it was left at.
        cards.holdAnchor(RecipeId(recipeId), null)
    }

    /**
     * What the person is to be told about a file — an import's outcome, a failed export — until they close it. Kept
     * like the view, so that a message shown when the screen turns, or when the process ends, is shown again once, and
     * a closed one never again.
     */
    val message: StateFlow<FileMessage?> = saved.getStateFlow<String?>(MESSAGE_KEY, null)
        .map(FileMessage::decoded)
        .stateIn(viewModelScope, SharingStarted.Eagerly, FileMessage.decoded(saved[MESSAGE_KEY]))

    /** Tells the person [message], in place of any not yet closed. */
    fun tell(message: FileMessage) {
        saved[MESSAGE_KEY] = message.encoded()
    }

    /** The message was closed. */
    fun told() {
        saved[MESSAGE_KEY] = null
    }

    /** The step the card is to be brought to, until it has been. */
    val stepToShow = StepToShow(
        saved.getStateFlow<String?>(STEP_KEY, null)
            .map(::stepOf)
            .stateIn(viewModelScope, SharingStarted.Eagerly, stepOf(saved[STEP_KEY])),
        shown = { saved[STEP_KEY] = null },
    )

    /**
     * What the session keeps of each card the reader opened — the count and fixing it was left at, and the line or
     * step at its top — kept like the view: through leaving the card, the home action and the end of the process,
     * and gone with the task (FLOW-017 (6, 7), FLOW-012, FLOW-058). Only short texts are kept, never a recipe.
     */
    val cards: CardSession get() = shownCards

    private val held = TextCardSession({ saved.get<String>(CARDS_KEY + it) }) { key, value ->
        if (value == null) saved.remove<String>(CARDS_KEY + key) else saved[CARDS_KEY + key] = value
    }

    /** Saved state stays small: only the [MAX_CARDS] cards most recently left at a count or a place are held. */
    private val shownCards = object : CardSession {
        // Reading a card on its opening shows it: a card read again is the most recent, whether or not the reader
        // then changes anything on it.
        override fun heldScale(recipe: RecipeId) = held.heldScale(recipe).also { if (it != null) shown(recipe) }
        override fun heldAnchor(recipe: RecipeId) = held.heldAnchor(recipe).also { if (it != null) shown(recipe) }

        override fun holdScale(recipe: RecipeId, scale: HeldScale) {
            shown(recipe)
            held.holdScale(recipe, scale)
        }

        override fun holdAnchor(recipe: RecipeId, anchor: CardAnchor?) {
            shown(recipe)
            held.holdAnchor(recipe, anchor)
        }
    }

    private fun shown(recipe: RecipeId) {
        val order = saved.get<String>(CARDS_ORDER_KEY)?.split(' ')?.filter { it.isNotEmpty() }.orEmpty()
        if (order.firstOrNull() == recipe.value) return
        val now = listOf(recipe.value) + (order - recipe.value)
        now.drop(MAX_CARDS).forEach { held.forget(RecipeId(it)) }
        saved[CARDS_ORDER_KEY] = now.take(MAX_CARDS).joinToString(" ")
    }

    private fun stepOf(value: String?): TimerStep? {
        val (position, recipe) = value?.split('/', limit = 2)?.takeIf { it.size == 2 } ?: return null
        return TimerStep(book = "", recipeId = recipe, stepPosition = position.toIntOrNull() ?: return null)
    }

    private companion object {
        const val CARDS_KEY = "library."
        const val CARDS_ORDER_KEY = "library.cards"
        const val MAX_CARDS = 50
        const val STEP_KEY = "library.step"
        const val MESSAGE_KEY = "library.file_message"
        const val VIEW_KEY = "library.view"
        const val EDITING_KEY = "library.editing"
        const val VIEWING_KEY = "library.viewing"
        const val CARD_FROM_KEY = "library.card_from"
    }
}
