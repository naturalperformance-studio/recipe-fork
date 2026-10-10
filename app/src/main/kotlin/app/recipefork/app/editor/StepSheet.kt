package app.recipefork.app.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.recipefork.app.R
import app.recipefork.app.dictionary.IngredientEntryEntity
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.Media
import app.recipefork.core.recipe.RecipePart
import app.recipefork.core.recipe.Step
import app.recipefork.core.scaling.InterfaceLanguage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/** The test tags of a step read as text, and of the sheet it opens in. */
object StepSheetTags {
    const val SHEET = "editor-step-sheet"
    const val DONE = "editor-step-sheet-done"

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
 * One step in a Material 3 bottom sheet: its text with its references and the control that inserts one (FLOW-031), the
 * author's note (FORK-007), its timers and its photograph or clip as the step's block held them, then its actions —
 * moving it up or down (FLOW-025, A11Y-006), putting a step above or below it, and removing it, which closes the sheet
 * and offers Undo. Each edit applies as it is typed, so the sheet closes — by Done, a swipe down or Back — keeping
 * everything; closing it leaves its fields, which marks what is over its limit (FLOW-028).
 *
 * Where [focus] asks for the step at [position], its text takes the focus, with the keyboard up.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val key = editor.stepKey(position)
    // The sheet's own focus, which the fields are left by before it goes.
    var manager by remember { mutableStateOf<FocusManager?>(null) }
    // Once the sheet is closing — by Done, or by an action that ends it — nothing more is acted on: a second tap
    // while it goes acts on nothing, and the sheet is closed once.
    val ended = remember { AtomicBoolean(false) }
    val closed = remember { AtomicBoolean(false) }
    val close = close@{
        if (!closed.compareAndSet(false, true)) return@close
        manager?.clearFocus()
        editor.stepPosition(key)?.let { editor.leave(RecipePart.StepNote(it)) }
        host.close()
    }
    val done = done@{
        if (!ended.compareAndSet(false, true)) return@done
        manager?.clearFocus()
        scope.launch { state.hide() }.invokeOnCompletion { close() }
        Unit
    }
    ModalBottomSheet(
        onDismissRequest = close,
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
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.editor_step, position + 1),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            TextButton(onClick = done, modifier = Modifier.heightIn(min = 48.dp).testTag(StepSheetTags.DONE)) {
                Text(stringResource(R.string.editor_done))
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                // A touch in the sheet is the author's next action, as one in the editor is (GUIDE-007).
                .onEveryTap { editor.noteAction() }
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            StepFields(position, step, media, ingredients, dictionary, language, editor, stating, photos, held)
            StepActions(key, position, count, editor, photos, host, ended) { manager?.clearFocus() }
        }
    }
}

/** The fields of the step at [position], as [StepSheet] holds them. */
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
        // In the field's own supporting text, under what the author types, so it moves nothing of it.
        hint = if (!held.offering) null else {
            {
                Text(
                    stringResource(R.string.editor_references_offered),
                    modifier = Modifier
                        // Its own node, read as it comes, apart from the text being typed.
                        .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
                        .testTag(EditorTags.stepReferencesOffered(position)),
                )
            }
        },
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
    BoundMark(editor, RecipePart.StepText(position), EditorTags.stepMark(position), gap = 0.dp)
    // The text's controls as the step's block held them: inserting a reference, adding a timer, its photo or clip.
    val timers = held.timers
    val timerRows = timers.shown(step.timers.size)
    BlockActions {
        InsertIngredient(position, ingredients, dictionary, language, editor, held.field)
        BlockAction(
            icon = R.drawable.ic_step_timer,
            label = stringResource(R.string.editor_timer_label, position + 1),
            tag = EditorTags.timerAttach(position),
            action = stringResource(R.string.editor_timer_action, position + 1),
            onClick = { if (!editor.attachTimer(position, timers.unheld)) held.timerFocus.ask(timers.open()) },
        )
        StepPhotoAction(position, media, held.photo)
    }
    StepNoteField(position, step, editor, held.note)
    // Its timers, and its photograph, or that one is being prepared.
    if (timerRows.isNotEmpty() || stating || media != null || held.photo.attaching.working.value) {
        Column(Modifier.fillMaxWidth()) {
            StepTimers(position, step, timerRows, timers, held.timerFocus, ingredients, language, editor, stating, left = held.leftCut)
            StepPhotoShown(position, media, photos, held.photo)
        }
    }
}

/**
 * The step's actions, under its fields: the four of FLOW-025 — the first step with no "Move up", the last with no
 * "Move down" — each one tap, so nothing is done only by dragging (A11Y-006), then its removal in the error colour.
 * Each first takes the focus off the sheet's fields ([leave]), so that what they mark is marked. Each finds its step
 * by its [key] as it runs, and does nothing where the step is gone; one that ends the sheet — a removal, an insertion,
 * which opens the new step — runs once, and nothing runs after it ([ended]).
 */
@Composable
private fun StepActions(
    key: Long,
    position: Int,
    count: Int,
    editor: RecipeEditor,
    photos: StepPhotos,
    host: StepSheetHost,
    ended: AtomicBoolean,
    leave: () -> Unit,
) {
    val actions = RowAction.entries.filter {
        it != RowAction.GROUP_ABOVE && (it != RowAction.MOVE_UP || position > 0) && (it != RowAction.MOVE_DOWN || position < count - 1)
    }
    FlowRow(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        actions.forEach { action ->
            TextButton(
                onClick = click@{
                    val at = editor.stepPosition(key) ?: return@click
                    val ends = action == RowAction.INSERT_ABOVE || action == RowAction.INSERT_BELOW
                    if (if (ends) !ended.compareAndSet(false, true) else ended.get()) return@click
                    leave()
                    when (action) {
                        RowAction.INSERT_ABOVE -> host.insert(true)
                        RowAction.INSERT_BELOW -> host.insert(false)
                        RowAction.MOVE_UP -> editor.moveStep(at, at - 1)
                        RowAction.MOVE_DOWN -> editor.moveStep(at, at + 1)
                        // Steps have no groups; it is never offered.
                        RowAction.GROUP_ABOVE -> Unit
                    }
                },
                modifier = Modifier.heightIn(min = 48.dp).testTag(EditorTags.stepAction(position, action)),
            ) {
                Text(stringResource(action.label))
            }
        }
        // Removing the step closes its sheet; the editor offers Undo, and deletes its item's file once it is final.
        TextButton(
            onClick = click@{
                val at = editor.stepPosition(key) ?: return@click
                if (!ended.compareAndSet(false, true)) return@click
                leave()
                editor.removeStep(at) { removed -> removed.forEach { photos.store.delete(photos.book, it) } }
                host.close()
            },
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.heightIn(min = 48.dp).testTag(EditorTags.stepRemove(position)),
        ) {
            Text(stringResource(R.string.editor_step_remove_action, position + 1))
        }
    }
}

/**
 * Called by a field at [position] in a sheet: where it is the one asked, [requester] takes the focus as soon as the
 * sheet is laid out, with the keyboard up — asked once the field has started its input, and again once the sheet's
 * window has the focus, should that come after. The request outlives the ask it answers.
 */
@Composable
internal fun RowFocus.TakenInSheet(position: Int, requester: FocusRequester) {
    val keyboard = LocalSoftwareKeyboardController.current
    val window = LocalWindowInfo.current
    val focusing = rememberCoroutineScope()
    if (asked != position) return
    LaunchedEffect(this, position) {
        taken(position)
        focusing.launch {
            withFrameNanos {}
            runCatching { requester.requestFocus() }
            withFrameNanos {}
            keyboard?.show()
            snapshotFlow { window.isWindowFocused }.first { it }
            keyboard?.show()
        }
    }
}
