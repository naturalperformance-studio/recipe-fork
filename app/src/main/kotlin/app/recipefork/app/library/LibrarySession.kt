package app.recipefork.app.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.recipefork.app.card.CardAnchor
import app.recipefork.app.card.CardMode
import app.recipefork.app.card.CardSession
import app.recipefork.app.card.HeldScale
import app.recipefork.app.card.StepToShow
import app.recipefork.app.card.TextCardSession
import app.recipefork.app.data.HeldLastBookView
import app.recipefork.app.data.LastBookView
import app.recipefork.app.recipe.BookFilters
import app.recipefork.core.recipe.RecipeCollection
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.search.BookOrder
import app.recipefork.app.file.FileMessage
import app.recipefork.app.file.ImportOutcome
import app.recipefork.app.file.RecipeImport
import app.recipefork.app.file.importSafely
import app.recipefork.app.file.message
import app.recipefork.app.timer.TimerStep
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/**
 * A file being imported or exported, and how far: [done] is the share of it done, or null while it is not known.
 */
data class FileWork(val kind: Kind, val done: Float? = null) {
    enum class Kind { IMPORT, EXPORT }
}

/**
 * Imports the file at [uri] (FLOW-064), its progress shown in the book while it is read; the person is told what became
 * of it, and a recipe now in the book is shown there ([LibrarySession.showImported], [LibrarySession.showHeld]).
 */
suspend fun LibrarySession.importFile(context: android.content.Context, files: RecipeImport, uri: android.net.Uri) {
    val outcome = withProgress(FileWork.Kind.IMPORT) { progress -> files.importSafely(context, uri, progress) }
    tell(outcome.message())
    when (outcome) {
        is ImportOutcome.Added -> showImported(outcome.id)
        is ImportOutcome.AlreadyInBook -> showHeld()
        is ImportOutcome.Refused -> Unit
    }
}

/** The two views of the recipe book (FLOW-034). */
enum class LibraryView { MARKED, OWN }

/**
 * What the recipe book section keeps for the length of a session (FLOW-058).
 *
 * It lives as long as the Activity's task: it survives leaving the section, the home action and a
 * configuration change in memory, and the end of the process through saved state. A task started anew — after
 * the application was removed from the recent applications, or after a restart of the device — has no saved
 * state, so a new session opens on the view last shown, which the device keeps in [lastView]; the first one ever
 * opens on the marked recipes (FLOW-034 (6), as the owner decided: the book opens where it was left).
 */
class LibrarySession(
    private val saved: SavedStateHandle,
    private val lastView: LastBookView = HeldLastBookView(),
) : ViewModel() {
    init {
        if (!saved.contains(VIEW_KEY)) saved[VIEW_KEY] = lastView.read() ?: LibraryView.MARKED
    }

    val view: StateFlow<LibraryView> = saved.getStateFlow(VIEW_KEY, LibraryView.MARKED)

    /** Shows [view]; a selection made in the other view ends. */
    fun choose(view: LibraryView) {
        if (selecting.value?.view != view) endSelection()
        show(view)
    }

    private val selecting = MutableStateFlow<BookSelection?>(null)

    /**
     * The entries chosen in the book's selection, while it is on: started by a long press on an entry, ended by ✕, Back,
     * the other view shown, an action taken on it, or the last entry taken off. Held in memory alone, like the removal it
     * may lead to: a new process starts with none.
     */
    val selection: StateFlow<BookSelection?> = selecting.asStateFlow()

    /**
     * The entry [recipeId] of [view] was long pressed, or tapped while selecting: a selection starts with it chosen, or,
     * one on in that view, it is chosen or taken off — and the selection ends once none is left.
     */
    fun select(view: LibraryView, recipeId: String) {
        selecting.update { current ->
            when {
                current == null || current.view != view -> BookSelection(view, setOf(recipeId))
                recipeId in current.ids -> (current.ids - recipeId).takeIf { it.isNotEmpty() }?.let { current.copy(ids = it) }
                else -> current.copy(ids = current.ids + recipeId)
            }
        }
    }

    /** Every entry [view] lists, [recipeIds], is chosen ("Select all"); none listed changes nothing. */
    fun selectAll(view: LibraryView, recipeIds: Collection<String>) {
        if (recipeIds.isEmpty()) return
        selecting.update { current -> if (current != null && current.view != view) current else BookSelection(view, recipeIds.toSet()) }
    }

    /**
     * [view] lists [recipeIds] now: an entry chosen that it no longer lists — gone from the book, or hidden by a search or
     * a filter — is chosen no more, and the selection ends once none is left.
     */
    fun keepListed(view: LibraryView, recipeIds: Set<String>) {
        selecting.update { current ->
            if (current == null || current.view != view || recipeIds.containsAll(current.ids)) {
                current
            } else {
                current.ids.filterTo(LinkedHashSet()) { it in recipeIds }.takeIf { it.isNotEmpty() }?.let { current.copy(ids = it) }
            }
        }
    }

    fun endSelection() {
        selecting.value = null
    }

    /** Shows [view], and keeps it on the device as the view last shown. */
    private fun show(view: LibraryView) {
        saved[VIEW_KEY] = view
        lastView.keep(view)
    }

    /**
     * What is typed in the search field of [view] (FLOW-035 (4)): each view keeps its own, kept like the view — through
     * leaving the section, the home action and the end of the process — and empty in a new session.
     */
    fun query(view: LibraryView): StateFlow<String> = saved.getStateFlow(QUERY_KEY + view.name, "")

    fun search(view: LibraryView, query: String) {
        saved[QUERY_KEY + view.name] = query
    }

    /** The order [view] is listed in (FLOW-035 (3)): each view keeps its own choice, kept like the query. */
    fun order(view: LibraryView): StateFlow<BookOrder> = saved.getStateFlow(ORDER_KEY + view.name, BookOrder.NEWEST)

    fun orderBy(view: LibraryView, order: BookOrder) {
        saved[ORDER_KEY + view.name] = order
    }

    /**
     * The filters [view] is narrowed by (FLOW-035): each view keeps its own, kept like the query, and none in a new
     * session.
     */
    fun filters(view: LibraryView): StateFlow<BookFilters> = saved.getStateFlow(FILTERS_KEY + view.name, BookFilters())

    fun filter(view: LibraryView, filters: BookFilters) {
        saved[FILTERS_KEY + view.name] = filters
    }

    /**
     * The recipe open in the editor, if one is: kept like the view, so that an author who leaves the editor by the
     * home action, or whose process ends while away, returns to it (FLOW-024).
     */
    val editing: StateFlow<String?> = saved.getStateFlow(EDITING_KEY, null)

    fun openEditor(recipeId: String) {
        closeNotice()
        finishRemoval()
        saved[SETTINGS_KEY] = false
        saved[SHOPPING_KEY] = false
        shutKitchen()
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
        closeNotice()
        finishRemoval()
        saved[SETTINGS_KEY] = false
        saved[SHOPPING_KEY] = false
        shutKitchen()
        saved[VIEWING_KEY] = recipeId
        from?.let { saved[CARD_FROM_KEY] = it }
    }

    /**
     * Back from the card returns to the view its recipe was opened from: the own recipes, from which a saved recipe
     * is opened, or the marked ones, from which a found recipe is.
     */
    fun closeCard() {
        shutKitchen()
        saved[VIEWING_KEY] = null
        show(saved.get<LibraryView>(CARD_FROM_KEY) ?: LibraryView.OWN)
    }

    /**
     * Whether the settings are open, above the book: kept like the card, so that settings open when the screen turns
     * or the process ends are open again on return. Whatever else the session opens — a card, the editor, the book
     * after an import — is shown in their place.
     */
    val settings: StateFlow<Boolean> = saved.getStateFlow(SETTINGS_KEY, false)

    fun openSettings() {
        finishRemoval()
        shutKitchen()
        shutCollection()
        saved[SHOPPING_KEY] = false
        saved[SETTINGS_KEY] = true
    }

    fun closeSettings() {
        shutKitchen()
        saved[SETTINGS_KEY] = false
    }

    /**
     * Whether My kitchen is open: kept like the settings. It lies above the settings when opened from them, above the
     * book from its filters, above a recipe's card to set it up ([openKitchen]), or above a recipe's card when opened
     * from one of its items of equipment ([openKitchenAt]); it is
     * shown only above what it was opened from, and whatever opens something else — the settings, a card, the editor,
     * the book — closes it, so it never comes back above anything else.
     */
    val kitchen: StateFlow<Boolean> = saved.getStateFlow(KITCHEN_KEY, false)

    /**
     * The equipment dictionary entry My kitchen was opened at from a recipe's card ([openKitchenAt]), while it is open
     * so; null when it was opened from the settings, or is not open.
     */
    val kitchenAt: StateFlow<Long?> = saved.getStateFlow(KITCHEN_AT_KEY, null)

    fun openKitchen() {
        saved[KITCHEN_AT_KEY] = null
        saved[KITCHEN_KEY] = true
    }

    /**
     * An item of equipment the open card marks as not in the kitchen was tapped: My kitchen opens above the card, at the
     * entry [entryId] it names; back from it returns to the card.
     */
    fun openKitchenAt(entryId: Long) {
        saved[KITCHEN_AT_KEY] = entryId
        saved[KITCHEN_KEY] = true
    }

    fun closeKitchen() = shutKitchen()

    /**
     * Whether the shopping list is open, above the book (FLOW-074): kept like the settings, so that a list open when the
     * screen turns or the process ends is open again on return. Whatever else the session opens — the settings, a card,
     * the editor, the book after an import — is shown in its place.
     */
    val shopping: StateFlow<Boolean> = saved.getStateFlow(SHOPPING_KEY, false)

    /** The shopping list opens above the book; a removal the book still offers to undo is final now. */
    fun openShopping() {
        finishRemoval()
        saved[SETTINGS_KEY] = false
        shutKitchen()
        shutCollection()
        saved[SHOPPING_KEY] = true
    }

    fun closeShopping() {
        saved[SHOPPING_KEY] = false
    }

    private fun shutKitchen() {
        saved[KITCHEN_KEY] = false
        saved[KITCHEN_AT_KEY] = null
    }

    /**
     * The collection whose screen is open above the book, if one is (v0.36): kept like the card, so that a collection
     * open when the screen turns or the process ends is open again on return. A card opened from it lies above it, and
     * back from the card returns to it; the settings, the shopping list, an import's book and a timer's card are shown in
     * its place.
     */
    val collection: StateFlow<String?> = saved.getStateFlow(COLLECTION_KEY, null)

    /** The collection [id] opens above the book; the book is left, so a removal it still offers to undo is final now. */
    fun openCollection(id: String) {
        closeNotice()
        finishRemoval()
        saved[SETTINGS_KEY] = false
        saved[SHOPPING_KEY] = false
        shutKitchen()
        saved[COLLECTION_KEY] = id
    }

    /**
     * Back to the book: a recipe's removal the collection's screen still offered to undo is final now, as on opening it,
     * so the book does not say it again.
     */
    fun closeCollection() {
        finishRemoval()
        shutCollection()
    }

    /**
     * The shopping list opens above the collection open (v0.36, its "Add all to shopping list"), and back from it returns
     * there; a removal the collection's screen still offers to undo is final now.
     */
    fun openShoppingAboveCollection() {
        if (collection.value == null) return openShopping()
        finishRemoval()
        saved[SETTINGS_KEY] = false
        shutKitchen()
        saved[VIEWING_KEY] = null
        saved[EDITING_KEY] = null
        saved[SHOPPING_KEY] = true
    }

    private fun shutCollection() {
        saved[COLLECTION_KEY] = null
    }

    private val deletedCollection = MutableStateFlow<RecipeCollection?>(null)

    /**
     * The collection last deleted from its screen, while the book offers to undo that (v0.36): its screen closes, and the
     * book says so with Undo until its notice goes. Held in memory alone: should the process end first, the deletion is
     * final. Nothing waits on it to be final — the collection's recipes stay in the book either way.
     */
    val collectionDeletion: StateFlow<RecipeCollection?> = deletedCollection.asStateFlow()

    /**
     * [collection] was deleted from its screen: the screen closes, and the book says so with Undo in place of whatever its
     * snackbar said — a recipe's removal still offered is final now.
     */
    fun collectionDeleted(collection: RecipeCollection) {
        closeNotice()
        finishRemoval()
        shutCollection()
        deletedCollection.value = collection
    }

    /**
     * The deletion of [collection] is no longer offered to undo — Undo was taken, or its notice went. Returns whether it
     * was still offered, so that Undo puts it back once at most.
     */
    fun endCollectionDeletion(collection: RecipeCollection): Boolean = deletedCollection.compareAndSet(collection, null)

    /**
     * A recipe file was imported as the recipe [id] (FLOW-064 (1)): the view of marked recipes is shown, where it is
     * listed now, in place of whatever was open — an editor keeps what was entered as it is typed.
     *
     * The recipe is new to the book, so nothing the session held of a card of that identity is its: a file carries
     * its recipe's identity, and the card of a recipe since deleted or removed — exported, perhaps, after a count or a
     * fixing was set on it — would otherwise reopen the arrival at that count and fixing, as if the file had carried
     * them. It opens as any card does the first time.
     *
     * The view's search and filters are cleared, so that the recipe is listed, and the list brought to it.
     */
    fun showImported(id: RecipeId) {
        held.forget(id)
        forgetShown(id)
        search(LibraryView.MARKED, "")
        filter(LibraryView.MARKED, BookFilters())
        showHeld()
        show(LibraryView.MARKED)
    }

    /**
     * A recipe file brought a recipe the book holds already: the book is shown, on the view shown last, in place of
     * whatever was open — an editor keeps what was entered as it is typed — so that its snackbar offers to open it.
     */
    fun showHeld() {
        finishRemoval()
        saved[SETTINGS_KEY] = false
        saved[SHOPPING_KEY] = false
        shutKitchen()
        shutCollection()
        saved[EDITING_KEY] = null
        saved[VIEWING_KEY] = null
    }

    /**
     * A saved book was opened from another application: the settings are shown above the book, in place of whatever
     * was open — an editor keeps what was entered as it is typed — for its restore asks, runs and is said there
     * (FLOW-073).
     */
    fun showBackup() {
        showHeld()
        openSettings()
    }

    /**
     * A timer's notification was tapped: its recipe's card opens, in place of whatever was open — an editor keeps
     * what was entered as it is typed — and is brought to the timer's step (FLOW-013 (4)). Kept like the card, so
     * that the step is still shown should the process end before the card is drawn.
     */
    fun showStep(recipeId: String, position: Int) {
        closeNotice()
        finishRemoval()
        saved[SETTINGS_KEY] = false
        saved[SHOPPING_KEY] = false
        shutKitchen()
        shutCollection()
        saved[EDITING_KEY] = null
        saved[VIEWING_KEY] = recipeId
        saved[CARD_FROM_KEY] = null
        saved[STEP_KEY] = "$position/$recipeId"
        // The card opens on Cooking and is brought to the timer's step there, not to the place it was left at.
        cards.holdAnchor(RecipeId(recipeId), null, CardMode.COOKING)
    }

    /**
     * What the person is to be told about a file — an import's outcome, a failed export — until they close it. Kept
     * like the view, so that a message shown when the screen turns, or when the process ends, is shown again once, and
     * a closed one never again.
     */
    val message: StateFlow<FileMessage?> = saved.getStateFlow<String?>(MESSAGE_KEY, null)
        .map(FileMessage::decoded)
        .stateIn(viewModelScope, SharingStarted.Eagerly, FileMessage.decoded(saved[MESSAGE_KEY]))

    /** Tells the person [message], in place of any not yet closed, and of a removal's notice, which is final now. */
    fun tell(message: FileMessage) {
        finishRemoval()
        saved[MESSAGE_KEY] = message.encoded()
    }

    /** The message was closed. */
    fun told() {
        saved[MESSAGE_KEY] = null
    }

    /**
     * The book is left for a card or the editor: a recipe a file brought, said in the book's snackbar, is said no more,
     * so that coming back neither says it again nor brings the list back to it. A failure's dialog stays until closed.
     */
    private fun closeNotice() {
        if (FileMessage.decoded(saved[MESSAGE_KEY])?.isNotice == true) told()
    }

    private val pending = MutableStateFlow<PendingRemoval?>(null)

    /**
     * The recipe last taken out of the book from its entry or its card, while its leaving can still be undone
     * (FLOW-053): the book's notice says so, with Undo, until it goes. Held in memory alone: should the process end
     * first, the leaving is final, and the media sweep deletes what the recipe's media left on the device.
     */
    val removal: StateFlow<PendingRemoval?> = pending.asStateFlow()

    /**
     * [removal] left the book: said with Undo in place of whatever the book's snackbar said. A removal said before it
     * is final now.
     */
    fun removed(removal: PendingRemoval) {
        closeNotice()
        failedUndo.value = null
        deletedCollection.value = null
        pending.getAndUpdate { removal }?.takeIf { it !== removal }?.finish()
    }

    /**
     * Undo was taken on [removal]'s notice: the recipe is put back. Nothing once that removal is no longer offered.
     * Should the book not take it back, [undoFailed] says so.
     */
    fun undoRemoval(removal: PendingRemoval) {
        if (pending.compareAndSet(removal, null)) removal.undo { failedUndo.value = removal }
    }

    private val failedUndo = MutableStateFlow<PendingRemoval?>(null)

    /** The removal whose Undo the book could not take back, until the book has said so. */
    val undoFailed: StateFlow<PendingRemoval?> = failedUndo.asStateFlow()

    /** The book said that [removal] could not be undone. */
    fun undoFailureSaid(removal: PendingRemoval) {
        failedUndo.compareAndSet(removal, null)
    }

    /** [removal]'s notice went without Undo: its leaving is final. Nothing once that removal is no longer offered. */
    fun endRemoval(removal: PendingRemoval) {
        if (pending.compareAndSet(removal, null)) removal.finish()
    }

    /** The book is left, or another notice said: a removal still offered is final, and so is a collection's deletion. */
    private fun finishRemoval() {
        failedUndo.value = null
        deletedCollection.value = null
        pending.getAndUpdate { null }?.finish()
    }

    override fun onCleared() {
        finishRemoval()
    }

    private val working = MutableStateFlow<FileWork?>(null)
    private var works = 0

    /** The file being imported or exported, while one is; shown in the book in place of nothing. */
    val work: StateFlow<FileWork?> = working.asStateFlow()

    /**
     * Does [block], shown meanwhile as a file work of [kind]; what [block] tells its progress of the share done shows,
     * changing by a hundredth at least.
     */
    suspend fun <T> withProgress(kind: FileWork.Kind, block: suspend (progress: (Float) -> Unit) -> T): T {
        works++
        working.value = FileWork(kind)
        try {
            return block { done ->
                val shown = working.value?.done
                if (shown == null || done - shown >= PROGRESS_STEP || (done >= 1f && shown < 1f)) working.value = FileWork(kind, done)
            }
        } finally {
            if (--works == 0) working.value = null
        }
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

    /**
     * The scale the reader left [recipe]'s card at this session, if any, read without the card being shown: unlike
     * [cards], it leaves the cards most recently shown as they were.
     */
    fun heldScale(recipe: RecipeId): HeldScale? = held.heldScale(recipe)

    /** Saved state stays small: only the [MAX_CARDS] cards most recently left at a count or a place are held. */
    private val shownCards = object : CardSession {
        // Reading a card on its opening shows it: a card read again is the most recent, whether or not the reader
        // then changes anything on it.
        override fun heldScale(recipe: RecipeId) = held.heldScale(recipe).also { if (it != null) shown(recipe) }
        override fun heldAnchor(recipe: RecipeId, tab: CardMode) = held.heldAnchor(recipe, tab).also { if (it != null) shown(recipe) }

        override fun holdScale(recipe: RecipeId, scale: HeldScale) {
            shown(recipe)
            held.holdScale(recipe, scale)
        }

        override fun holdAnchor(recipe: RecipeId, anchor: CardAnchor?, tab: CardMode) {
            shown(recipe)
            held.holdAnchor(recipe, anchor, tab)
        }
    }

    private fun shown(recipe: RecipeId) {
        val order = saved.get<String>(CARDS_ORDER_KEY)?.split(' ')?.filter { it.isNotEmpty() }.orEmpty()
        if (order.firstOrNull() == recipe.value) return
        val now = listOf(recipe.value) + (order - recipe.value)
        now.drop(MAX_CARDS).forEach { held.forget(RecipeId(it)) }
        saved[CARDS_ORDER_KEY] = now.take(MAX_CARDS).joinToString(" ")
    }

    private fun forgetShown(recipe: RecipeId) {
        val order = saved.get<String>(CARDS_ORDER_KEY)?.split(' ')?.filter { it.isNotEmpty() } ?: return
        if (recipe.value in order) saved[CARDS_ORDER_KEY] = (order - recipe.value).joinToString(" ")
    }

    private fun stepOf(value: String?): TimerStep? {
        val (position, recipe) = value?.split('/', limit = 2)?.takeIf { it.size == 2 } ?: return null
        return TimerStep(book = "", recipeId = recipe, stepPosition = position.toIntOrNull() ?: return null)
    }

    private companion object {
        const val CARDS_KEY = "library."
        const val QUERY_KEY = "library.query."
        const val ORDER_KEY = "library.order."
        const val FILTERS_KEY = "library.filters."
        const val CARDS_ORDER_KEY = "library.cards"
        const val MAX_CARDS = 50
        const val PROGRESS_STEP = 0.01f
        const val STEP_KEY = "library.step"
        const val SETTINGS_KEY = "library.settings"
        const val KITCHEN_KEY = "library.kitchen"
        const val KITCHEN_AT_KEY = "library.kitchen_at"
        const val SHOPPING_KEY = "library.shopping"
        const val MESSAGE_KEY = "library.file_message"
        const val VIEW_KEY = "library.view"
        const val EDITING_KEY = "library.editing"
        const val VIEWING_KEY = "library.viewing"
        const val CARD_FROM_KEY = "library.card_from"
        const val COLLECTION_KEY = "library.collection"
    }
}
