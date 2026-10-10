package app.recipefork.app.editor

import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.onFocusedBoundsChanged
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.recipefork.app.R
import app.recipefork.app.dictionary.IngredientEntryEntity
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.Media
import app.recipefork.core.recipe.RecipePart
import app.recipefork.core.recipe.Step
import app.recipefork.core.scaling.InterfaceLanguage
import kotlinx.coroutines.launch

/** The test tags of a step read as text, and of the sheet it opens in. */
object StepSheetTags {
    const val SHEET = "editor-step-sheet"
    const val TITLE = "editor-step-sheet-title"
    const val DONE = "editor-step-sheet-done"

    /** The sheet's part of the step's timers, under its name, with the control that adds one. */
    const val TIMERS = "editor-step-sheet-timers"

    /** The sheet's part of the step's photograph or clip, under its name, with what it is told and the control that adds one. */
    const val MEDIA = "editor-step-sheet-media"

    /** What went wrong with an item offered at the step while its sheet is open, in the sheet's media part. */
    fun mediaFailure(position: Int) = "editor-step-$position-media-failure"

    /** The step as its row reads: its number, text, note, timers and picture; the row's click target. */
    fun line(position: Int) = "editor-step-$position-line"

    /** The step's text on its row, its references read as the card reads them; or the placeholder of a step with none. */
    fun text(position: Int) = "editor-step-$position-line-text"

    /** The author's note on the row, muted. */
    fun note(position: Int) = "editor-step-$position-line-note"

    /** The timer at [index] of the step, on its row: "Name · 40 min". */
    fun timer(position: Int, index: Int) = "editor-step-$position-line-timer-$index"

    /** The small picture of the step's photograph or clip on its row. */
    fun thumbnail(position: Int) = "editor-step-$position-line-thumbnail"

    /** The row's marks of FLOW-028: its text, its note and its timers' names over their limits. */
    fun textMark(position: Int) = "editor-step-$position-line-mark"
    fun noteMark(position: Int) = "editor-step-$position-line-note-mark"
    fun timerNameMark(position: Int, index: Int) = "editor-step-$position-line-timer-$index-mark"

    /** The row's mark of a part of an ingredient more than the ingredient's quantity. */
    fun partWarning(position: Int) = "editor-step-$position-line-part-warning"
}

/**
 * What the editor holds for one editable step beside the step itself, kept by its row — under the step's key, so that
 * it stays with the step as others move, and is restored with the activity — and handed to the step's sheet while it
 * is open: what a sheet's window composes itself is not restored.
 */
@Stable
internal class StepHeld(
    val field: StepFieldState,
    val timers: TimerRows,
    /** The note as typed, while it reads as the note the step holds. */
    val note: MutableState<String?>,
) {
    /** The step's photograph and clip work and their launchers, as its row has them now. */
    lateinit var photo: StepPhotoControl

    /** The timers the author adds take the focus in their hours, with the keyboard up. */
    val timerFocus = RowFocus()

    /**
     * Left with an unnamed timer whose notification is or may be cut: what it will say shows until the step is entered
     * again (GUIDE-006). Nothing of it is stored.
     */
    var leftCut by mutableStateOf(false)

    /** References offered under the first step while it is first written in this editing session (GUIDE-008). */
    var offering by mutableStateOf(false)
}

@Composable
internal fun rememberStepHeld(position: Int, step: Step, editor: RecipeEditor, photos: StepPhotos): StepHeld {
    val field = remember { StepFieldState() }
    val timers = rememberSaveable(saver = TimerRows.Saver) { TimerRows(step.timers.size) }
    val note = rememberSaveable { mutableStateOf<String?>(null) }
    val photo = rememberStepPhotoControl(position, editor, photos)
    // A timer of no duration is no timer, and is not stored: the rows follow the step's timers as they change.
    SideEffect { timers.settle(step.timers.size) }
    // The same holder for as long as the step is on screen: what it keeps — a timer being added, what the author left —
    // lasts across recompositions.
    return remember(field, timers, note) { StepHeld(field, timers, note) }.also { it.photo = photo }
}

/**
 * What the step's sheet asks of the list that hosts it: to put a new step [above] or below this one, which then opens
 * in its own sheet, and that the sheet is closed.
 */
internal class StepSheetHost(val insert: (above: Boolean) -> Unit, val close: () -> Unit)

/**
 * One step in a Material 3 bottom sheet: its text with its references and the button that inserts one (FLOW-031), then
 * its timers and its photograph or clip, each a part of its own with the button that adds to it — all within reach
 * with the keyboard up — then the one-time offer of references (GUIDE-008) and the author's note (FORK-007), then its
 * actions as the ingredient sheet has them ([SheetActions]) — moving it up or down (FLOW-025, A11Y-006), putting a step
 * above or below it, and removing it, which closes the sheet and offers Undo. Each edit applies as it is typed, so the
 * sheet closes — by Done, a swipe down or Back — keeping everything; closing it leaves its fields, which marks what is
 * over its limit (FLOW-028).
 *
 * A [fresh] step — just added or inserted — is titled "New step" and offers its actions only once it holds something.
 * Where [focus] asks for the step at [position], its text takes the focus, with the keyboard up.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun StepSheet(
    position: Int,
    count: Int,
    step: Step,
    media: Media?,
    ingredients: List<Ingredient>,
    dictionary: List<IngredientEntryEntity>?,
    language: InterfaceLanguage,
    editor: RecipeEditor,
    stating: Boolean,
    photos: StepPhotos,
    held: StepHeld,
    focus: RowFocus,
    host: StepSheetHost,
    fresh: Boolean,
) {
    // The editor's statements — an Undo among them — wait until the sheet goes, rather than show behind it.
    CoversStatements()
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val key = editor.stepKey(position)
    // The sheet's own focus, which the fields are left by before it goes.
    var manager by remember { mutableStateOf<FocusManager?>(null) }
    val leave: () -> Unit = { manager?.clearFocus() }
    val close = {
        leave()
        editor.stepPosition(key)?.let { editor.leave(RecipePart.StepNote(it)) }
        host.close()
    }
    // Once on its way out — closing, or left for a new step — the sheet takes no further action: a second tap while it
    // goes acts on nothing, and the sheet is closed once.
    val going = remember { Going() }
    val dismiss = { if (going.start()) close() }
    val done = {
        if (going.start()) {
            leave()
            scope.launch { state.hide() }.invokeOnCompletion { close() }
        }
    }
    // A step just added offers no actions until it holds something: its fields are all there is to it.
    val actions = if (fresh && holdsNothing(step, media)) emptyList() else stepActions(key, position, count, editor, photos, host, leave)
    ModalBottomSheet(
        onDismissRequest = dismiss,
        sheetState = state,
        modifier = Modifier.testTag(StepSheetTags.SHEET),
        // No handle: the sheet opens whole, and Done, a swipe down or Back closes it.
        dragHandle = null,
    ) {
        val focusManager = LocalFocusManager.current
        SideEffect { manager = focusManager }
        // Asked for — the step just added or inserted — its text takes the focus as soon as the sheet is laid out, with
        // the keyboard up.
        focus.TakenInSheet(position, held.field.focus)
        SheetHeader(
            stringResource(if (fresh) R.string.editor_step_new else R.string.editor_step_edit),
            StepSheetTags.TITLE,
            StepSheetTags.DONE,
            done,
        )
        // The sheet's own scrolling, and where the field the author is in stands in it: a mark or a timer's display
        // appearing above that field scrolls the sheet by as much, so the field stays where the author has it.
        val scrolled = rememberScrollState()
        val scroll = remember(scrolled) { EditorScroll(scrolled) }
        CompositionLocalProvider(LocalEditorScroll provides scroll) {
            Column(
                Modifier
                    .fillMaxWidth()
                    // A touch in the sheet is the author's next action, as one in the editor is (GUIDE-007).
                    .onEveryTap { editor.noteAction() }
                    .imePadding()
                    .onGloballyPositioned { scroll.viewport = it }
                    .onFocusedBoundsChanged { scroll.focused = it }
                    .verticalScroll(scrolled)
                    .padding(bottom = 8.dp),
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(SHEET_GAP)) {
                    StepFields(position, step, media, ingredients, dictionary, language, editor, stating, photos, held)
                }
                SheetActions(actions) { action -> scope.take(action, going, state, leave, close) }
            }
        }
    }
}

/**
 * The fields of the step at [position], as [StepSheet] holds them: its text and the button inserting a reference, its
 * timers, its picture — what a step is mostly written with, first — then the offer of references, then its note.
 */
@Composable
private fun StepFields(
    position: Int,
    step: Step,
    media: Media?,
    ingredients: List<Ingredient>,
    dictionary: List<IngredientEntryEntity>?,
    language: InterfaceLanguage,
    editor: RecipeEditor,
    stating: Boolean,
    photos: StepPhotos,
    held: StepHeld,
) {
    val key = editor.stepKey(position)
    StepTextField(
        position = position,
        step = step,
        ingredients = ingredients,
        language = language,
        editor = editor,
        state = held.field,
        modifier = Modifier
            .fillMaxWidth()
            // Coming into a step moves the author on from the ingredients; leaving it marks it (FLOW-028).
            .then(
                leaving(
                    onEnter = {
                        editor.leave(RecipePart.Ingredients)
                        held.leftCut = false
                        // Beginning the first step offers references, whatever the step says (GUIDE-008).
                        if (editor.beginStep(position)) held.offering = true
                    },
                ) {
                    held.offering = false
                    editor.leaveStep(key)
                    val left = editor.recipe.value
                    held.leftCut = left?.steps?.sortedBy { it.position }?.getOrNull(position)
                        ?.let { s -> s.timers.indices.any { cutNotification(s, it, left.ingredients, language) != null } } == true
                },
            )
            .testTag(EditorTags.stepText(position)),
    )
    // What the text needs, right under it.
    BoundMark(editor, RecipePart.StepText(position), EditorTags.stepMark(position), gap = SHEET_GAP)
    // The text's own control: inserting a reference.
    InsertIngredient(position, ingredients, dictionary, language, editor, held.field)
    // Its timers, then its photograph or clip: each a part of its own, named once it holds something.
    val timers = held.timers
    val shownTimers = timers.shown(step.timers.size)
    SheetPart(stringResource(R.string.editor_step_timers), StepSheetTags.TIMERS, named = shownTimers.isNotEmpty()) {
        StepTimers(position, step, shownTimers, timers, held.timerFocus, ingredients, language, editor, left = held.leftCut)
        PartButton(
            icon = R.drawable.ic_step_timer,
            text = stringResource(R.string.editor_timer_add),
            action = stringResource(R.string.editor_timer_action, position + 1),
            tag = EditorTags.timerAttach(position),
        ) { if (!editor.attachTimer(position, timers.unheld)) held.timerFocus.ask(timers.open()) }
        // Choosing it where the step would show more than five creates nothing and says why, under it (GUIDE-007).
        if (stating) {
            Text(
                stringResource(R.string.editor_timer_limit),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { liveRegion = LiveRegionMode.Polite }
                    .testTag(EditorTags.timerStatement(position)),
            )
        }
    }
    SheetPart(stringResource(R.string.media_step_part), StepSheetTags.MEDIA, named = media != null || held.photo.preparing) {
        StepMediaPart(position, media, photos, held.photo)
    }
    // Under the buttons, so that it never pushes them from reach: once, while the first step is begun (GUIDE-008).
    if (held.offering) {
        Text(
            stringResource(R.string.editor_references_offered),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                // Its own node, read as it comes, apart from the text being typed.
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
                .testTag(EditorTags.stepReferencesOffered(position)),
        )
    }
    StepNoteField(position, step, editor, held.note)
}

/**
 * One part of a step's sheet — its timers, its photograph or clip: where it is [named] — it holds something — its
 * [title], a heading for the screen reader; then what it holds, ending in the button that adds to it. An empty part is
 * its button alone, which says what it adds, so that a new step's buttons stand within reach of the keyboard.
 */
@Composable
private fun SheetPart(title: String, tag: String, named: Boolean, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = if (named) 12.dp else 0.dp).testTag(tag), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (named) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.semantics { heading() },
            )
        }
        content()
    }
}

/**
 * The control that adds to a part of a step's sheet — a timer, a photograph or clip: Material 3's text button with its
 * [icon] before its [text], at least 48 dp tall (A11Y-003), its click announced as [action], which names the step
 * (A11Y-005).
 */
@Composable
internal fun PartButton(@DrawableRes icon: Int, text: String, action: String, tag: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        contentPadding = ButtonDefaults.TextButtonWithIconContentPadding,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .semantics { onClick(label = action, action = null) }
            .testTag(tag),
    ) {
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Text(text)
    }
}

/**
 * The step's actions, under its fields, as the ingredient sheet has them: the four of FLOW-025 — the first step with no
 * "Move up", the last with no "Move down" — each one tap, so nothing is done only by dragging (A11Y-006), then its
 * removal in the error colour. A move first takes the focus off the sheet's fields ([leave]), so that what they mark is
 * marked, and keeps the sheet open on the step where it now stands; an insertion opens the new step in its own sheet;
 * the removal closes the sheet, then removes the step — the editor offers Undo, deleting its item's file once that is
 * final. Each finds its step by its [key] as it runs, and does nothing where the step is gone.
 */
@Composable
private fun stepActions(
    key: Long,
    position: Int,
    count: Int,
    editor: RecipeEditor,
    photos: StepPhotos,
    host: StepSheetHost,
    leave: () -> Unit,
): List<SheetAction> {
    val label = RowAction.entries.associateWith { stringResource(it.label) }
    val remove = stringResource(R.string.editor_step_remove)
    fun move(by: Int) {
        val at = editor.stepPosition(key) ?: return
        leave()
        editor.moveStep(at, at + by)
    }
    fun action(action: RowAction, leaves: Boolean, onClick: () -> Unit) =
        SheetAction(label.getValue(action), EditorTags.stepAction(position, action), leaves = leaves, onClick = onClick)
    return buildList {
        if (position > 0) add(action(RowAction.MOVE_UP, false) { move(-1) })
        if (position < count - 1) add(action(RowAction.MOVE_DOWN, false) { move(1) })
        add(action(RowAction.INSERT_ABOVE, true) { host.insert(true) })
        add(action(RowAction.INSERT_BELOW, true) { host.insert(false) })
        add(
            SheetAction(remove, EditorTags.stepRemove(position), leaves = true, destructive = true) {
                editor.stepPosition(key)?.let { at -> editor.removeStep(at) { removed -> removed.forEach { photos.store.delete(photos.book, it) } } }
            },
        )
    }
}

/** The space between one part of a step's sheet and the next. */
private val SHEET_GAP = 4.dp
