package app.recipefork.app.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.recipefork.app.R
import app.recipefork.app.media.PhotoStore
import app.recipefork.app.book.BookName
import app.recipefork.app.media.AnimatedImages
import app.recipefork.app.media.ClipStore
import app.recipefork.app.media.stepMedia
import app.recipefork.core.recipe.Media
import kotlinx.coroutines.CoroutineScope
import app.recipefork.app.dictionary.IngredientEntryEntity
import app.recipefork.app.dictionary.IngredientSource
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipePart
import app.recipefork.core.recipe.ReferenceText
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.StepRun
import app.recipefork.core.recipe.StepText
import app.recipefork.core.scaling.InterfaceLanguage
import app.recipefork.core.recipe.Timer
import app.recipefork.core.timer.TimerName
import kotlin.time.Duration

/** The space between two steps, or two ingredient rows. */
internal val ROW_SPACING = 12.dp

/**
 * The steps, after the ingredients (FLOW-024): numbered plain text, appended at the end, edited where each stands and
 * deleted. They accept input whatever else the recipe holds or lacks (FLOW-024 (2)), and no text is ever cut short.
 */
@Composable
internal fun Steps(
    recipe: Recipe,
    editor: RecipeEditor,
    source: IngredientSource,
    scroll: EditorScroll,
    store: PhotoStore,
    book: BookName,
    saving: CoroutineScope,
    clips: ClipStore? = null,
    work: StepMediaWork? = null,
    animated: AnimatedImages? = null,
    animations: ClipStore? = null,
) {
    val photos = rememberStepPhotos(store, book, saving, clips, work, animated, animations)
    // What each step's media work tells is said here, so that a step deleted while its clip converts still says it.
    work?.all?.forEach { (stepKey, attaching) -> key(stepKey) { SaysPhotoNotices(attaching) } }
    val editable = recipe.state.editable
    val steps = recipe.steps.sortedBy { it.position }
    val language = numberLanguage(LocalConfiguration.current)
    var dictionary by remember(source) { mutableStateOf<List<IngredientEntryEntity>?>(null) }
    LaunchedEffect(source) { dictionary = runCatching { source.ingredients() }.getOrDefault(emptyList()) }
    // A drop is the same single move as the row menu's (FLOW-025, A11Y-006).
    val drag = rememberRowDrag(scroll, ROW_SPACING) { from, to -> editor.moveStep(from, to) }
    val focus = remember { RowFocus() }
    val motion = rememberRowMotion()
    motion.update(steps.indices.map(editor::stepKey))
    RowsColumn(
        ROW_SPACING,
        Modifier.fillMaxWidth().zIndex(if (drag.dragged != null) 1f else 0f).testTag(EditorTags.STEPS),
    ) {
        Text(
            stringResource(R.string.editor_steps),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        // Under the heading: the steps left with none, or more than the limit (FLOW-028).
        BoundMark(editor, RecipePart.Steps, EditorTags.STEPS_MARK, gap = ROW_SPACING)
        if (!editable && steps.isEmpty()) {
            Text(
                stringResource(R.string.editor_steps_none),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.testTag(EditorTags.STEPS_NONE),
            )
        }
        val stating by editor.statement.collectAsState()
        steps.forEachIndexed { position, step ->
            // What the screen holds for a step — focus, a timer being attached — stays with it when steps before it
            // are inserted, moved or deleted.
            motion.LeavingAt(position)
            key(editor.stepKey(position)) {
                if (editable) StepRow(position, steps.size, step, recipe.stepMedia(position), recipe.ingredients, dictionary, language, editor, stating == position, drag, photos, focus, motion)
                else ReadOnlyStep(position, step, recipe.stepMedia(position), recipe.ingredients, language, photos)
            }
        }
        motion.LeavingAt(steps.size, last = true)
        if (editable) {
            EditorButton(
                text = stringResource(R.string.editor_step_add),
                action = stringResource(R.string.editor_step_add_action),
                onClick = {
                    val added = editor.recipe.value?.steps?.size
                    // The step added takes the focus in its text, with the keyboard up.
                    if (editor.addStep() && added != null) focus.ask(added)
                    // The author has moved on from the ingredients to the steps (FLOW-028 (1)).
                    editor.leave(RecipePart.Ingredients)
                },
                modifier = Modifier.testTag(EditorTags.STEP_ADD),
                weight = ButtonWeight.TONAL,
            )
        }
    }
}

/**
 * One step: its number in the field's label, its text over as many lines as it takes with its ingredient references in
 * it, the author's note under the text — shown where the step has one, or once the author asks for it from the step's
 * menu ([StepNoteField]) — its removal, its menu of inserting and moving (FLOW-025), the control that inserts a
 * reference (FLOW-031), the control that adds its timers (FLOW-027) and its photograph or clip (FLOW-026 (2)). Removing
 * the step deletes its item's file once the book no longer holds it.
 */
@Composable
private fun StepRow(
    position: Int,
    count: Int,
    step: Step,
    photo: Media?,
    ingredients: List<Ingredient>,
    dictionary: List<IngredientEntryEntity>?,
    language: InterfaceLanguage,
    editor: RecipeEditor,
    stating: Boolean,
    drag: RowDrag,
    photos: StepPhotos,
    focus: RowFocus,
    motion: RowMotion,
) {
    val field = remember { StepFieldState() }
    focus.TakenBy(position, field.focus)
    val key = editor.stepKey(position)
    val photoControl = rememberStepPhotoControl(position, editor, photos)
    // The timers the block shows: the step's own, and those added by its timer control that hold no duration yet — a
    // timer of no duration is no timer, and is not stored.
    val timers = rememberSaveable(saver = TimerRows.Saver) { TimerRows(step.timers.size) }
    val timerRows = timers.shown(step.timers.size)
    SideEffect { timers.settle(step.timers.size) }
    // A timer the author adds takes the focus in its hours, with the keyboard up.
    val timerFocus = remember { RowFocus() }
    // Left with an unnamed timer whose notification is or may be cut: what it will say shows until the step is
    // entered again (GUIDE-006). Nothing of it is stored.
    var leftCut by remember { mutableStateOf(false) }
    // References offered under the first step while it is first written in this editing session (GUIDE-008).
    var offering by remember { mutableStateOf(false) }
    // The note's field, asked for from the step's menu: it stays while the step holds a note, and goes once the author
    // leaves it empty.
    var noteAsked by rememberSaveable { mutableStateOf(false) }
    val noteShown = noteAsked || step.note != null
    val noteFocus = remember { RowFocus() }
    val offerShrinking = rememberShrinking()
    // One bounded block per step, as an ingredient's, so where one step ends and the next begins is plain; dragged, it
    // moves whole.
    OutlinedCard(
        Modifier
            .fillMaxWidth()
            .then(motion.row(key))
            .then(drag.row(position, MaterialTheme.colorScheme.surface, CardDefaults.outlinedShape))
            .testTag(EditorTags.step(position)),
    ) {
        // Dragged by the column down its left edge — or its right, for a left hand; everything of the step stands beside
        // it, at one left edge.
        DragColumnBlock(drag, position, count, EditorTags.stepHandle(position), EditorTags.stepHandleIcon(position)) {
            Column(Modifier.fillMaxWidth().padding(STEP_PADDING)) {
                // The text across the whole block; under it, one row of the step's actions as icons.
                StepTextField(
                    position = position,
                    step = step,
                    ingredients = ingredients,
                    language = language,
                    editor = editor,
                    state = field,
                    // In the field's own supporting text, under what the author types, so it moves nothing of it.
                    hint = if (!offering) null else {
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
                        // The field gets shorter as the offer goes from its supporting text.
                        .then(offerShrinking.modifier)
                        // Coming into a step moves the author on from the ingredients; leaving it marks it (FLOW-028).
                        .then(
                            leaving(
                                onEnter = {
                                    editor.leave(RecipePart.Ingredients)
                                    leftCut = false
                                    // Beginning the first step offers references, whatever the step says (GUIDE-008).
                                    if (editor.beginStep(position)) offering = true
                                },
                            ) {
                                if (offering) offerShrinking.shrinking()
                                offering = false
                                editor.leaveStep(key)
                                val left = editor.recipe.value
                                leftCut = left?.steps?.sortedBy { it.position }?.getOrNull(position)
                                    ?.let { s -> s.timers.indices.any { cutNotification(s, it, left.ingredients, language) != null } } == true
                            },
                        )
                        .testTag(EditorTags.stepText(position)),
                )
                // The step's own slot for what its text needs, right under the text and above its actions.
                BoundMark(editor, RecipePart.StepText(position), EditorTags.stepMark(position), gap = 0.dp)
                if (noteShown) StepNoteField(position, step, editor, noteFocus) { held -> if (!held) noteAsked = false }
                BlockActions {
                    InsertIngredient(position, ingredients, dictionary, language, editor, field)
                    BlockAction(
                        icon = R.drawable.ic_step_timer,
                        label = stringResource(R.string.editor_timer_label, position + 1),
                        tag = EditorTags.timerAttach(position),
                        action = stringResource(R.string.editor_timer_action, position + 1),
                        onClick = { if (!editor.attachTimer(position, timers.unheld)) timerFocus.ask(timers.open()) },
                    )
                    StepPhotoAction(position, photo, photoControl)
                    BlockAction(
                        icon = R.drawable.ic_remove_row,
                        label = stringResource(R.string.editor_step_remove_action, position + 1),
                        tag = EditorTags.stepRemove(position),
                        action = stringResource(R.string.editor_step_remove_action, position + 1),
                        onClick = { editor.removeStep(position) { removed -> removed.forEach { photos.store.delete(photos.book, it) } } },
                        destructive = true,
                    )
                    // The step's menu: its note, where it has none yet, then the four moves of FLOW-025 — the first step
                    // with no "Move up", the last with no "Move down". The row holds no room for another icon on a
                    // 360 dp phone.
                    val addNote = BlockMenuItem(stringResource(R.string.editor_step_note_add), StepNoteTags.add(position)) {
                        noteAsked = true
                        // The field the author asked for takes the focus, with the keyboard up, once the menu has gone.
                        noteFocus.ask(0)
                    }
                    val moves = RowAction.entries.filter {
                        it != RowAction.GROUP_ABOVE && (it != RowAction.MOVE_UP || position > 0) && (it != RowAction.MOVE_DOWN || position < count - 1)
                    }.map { action ->
                        BlockMenuItem(stringResource(action.label), EditorTags.stepAction(position, action)) {
                            when (action) {
                                RowAction.INSERT_ABOVE -> editor.insertStep(position)
                                RowAction.INSERT_BELOW -> editor.insertStep(position + 1)
                                RowAction.MOVE_UP -> editor.moveStep(position, position - 1)
                                RowAction.MOVE_DOWN -> editor.moveStep(position, position + 1)
                                // Steps have no groups; their menu never offers it.
                                RowAction.GROUP_ABOVE -> Unit
                            }
                        }
                    }
                    BlockMenu(
                        name = stringResource(R.string.editor_step_menu_action, position + 1),
                        tag = EditorTags.stepMenu(position),
                        items = listOfNotNull(addNote.takeIf { !noteShown }) + moves,
                    )
                }
                // Below the row, only what the step has: its timers, and its photograph, or that one is being prepared.
                if (timerRows.isNotEmpty() || stating || photo != null || photoControl.attaching.working.value) {
                    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                        StepTimers(position, step, timerRows, timers, timerFocus, ingredients, language, editor, stating, left = leftCut)
                        StepPhotoShown(position, photo, photos, photoControl)
                    }
                }
            }
        }
    }
}

/**
 * The author's own note on the step, beside the method, under the step's text: labelled as the author's, with the hint
 * that it stays with them when someone else forks the recipe (FORK-007). What the author types stays in the field as
 * typed — a space before the next word included — while it reads as the note the step holds, trimmed, blank as none.
 * Over [app.recipefork.core.recipe.RecipeBounds.MAX_STEP_NOTE] characters it is kept in full and marked on leaving the
 * field, as every limit is (FLOW-028). Leaving it, [onLeft] is told whether the step holds a note.
 */
@Composable
private fun StepNoteField(position: Int, step: Step, editor: RecipeEditor, focus: RowFocus, onLeft: (held: Boolean) -> Unit) {
    // Kept across the activity being recreated, under the step's key, so it stays the step's own.
    var typed by rememberSaveable { mutableStateOf<String?>(null) }
    // The step as the editor holds it now — [step] at the latest; the text in the field stands only while it is what the
    // step holds.
    fun held() = (editor.recipe.value?.steps?.sortedBy { it.position }?.getOrNull(position) ?: step).note
    val note = held()
    val shown = typed?.takeIf { Step.noteOf(it) == note } ?: note.orEmpty()
    val requester = remember { FocusRequester() }
    focus.TakenBy(0, requester)
    Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        OutlinedTextField(
            value = shown,
            onValueChange = {
                typed = it
                editor.setStepNote(position, it)
            },
            label = { Text(stringResource(R.string.editor_step_note)) },
            supportingText = { Text(stringResource(R.string.editor_step_note_hint)) },
            // Over several lines, the keyboard keeps its new-line key.
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(requester)
                .onFocusChanged { if (!it.isFocused) typed = null }
                .then(
                    leaving {
                        editor.leave(RecipePart.StepNote(position))
                        onLeft(held() != null)
                    },
                )
                .testTag(StepNoteTags.field(position)),
        )
        BoundMark(editor, RecipePart.StepNote(position), StepNoteTags.mark(position), gap = 4.dp, Modifier.padding(top = 4.dp))
    }
}

/** The test tags of a step's note in the editor. */
object StepNoteTags {
    fun add(position: Int) = "editor-step-$position-note-add"
    fun field(position: Int) = "editor-step-$position-note"
    fun mark(position: Int) = "editor-step-$position-note-mark"
}

/** The room between a step block's outline, or its drag column, and what the block holds. */
private val STEP_PADDING = PaddingValues(start = 8.dp, top = 4.dp, end = 4.dp, bottom = 0.dp)

/**
 * The step's timers (FLOW-027, DOM-024), under the step's row of actions, whose timer control adds one each time it is
 * chosen, one compact row under another in the order they were added. Choosing it where the step would show more than
 * five creates nothing and says why (GUIDE-007): what it says shows here while [stating].
 */
@Composable
private fun StepTimers(
    position: Int,
    step: Step,
    rows: List<TimerRows.Row>,
    timers: TimerRows,
    focus: RowFocus,
    ingredients: List<Ingredient>,
    language: InterfaceLanguage,
    editor: RecipeEditor,
    stating: Boolean,
    left: Boolean,
) {
    if (stating) {
        Text(
            stringResource(R.string.editor_timer_limit),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .semantics { liveRegion = LiveRegionMode.Polite }
                .testTag(EditorTags.timerStatement(position)),
        )
    }
    rows.forEachIndexed { place, row ->
        // What the screen holds for a timer — what was typed, a name given — stays with it as others go.
        key(row.key) {
            val index = rows.take(place).count { it.held }
            val timer = if (row.held) step.timers.getOrNull(index) else null
            Column(Modifier.fillMaxWidth().padding(top = if (place > 0) 8.dp else 0.dp)) {
                StepTimer(position, place, rows.size, index, step, timer, timers, focus, ingredients, language, editor, left)
            }
        }
    }
}

/**
 * One timer of the step, at [place] among the [count] its block shows and at [index] of the step's timers: its duration in hours and whole minutes — the
 * only thing asked — and, once it holds one ([timer]), its own removal and the name it will announce itself by. A timer
 * just added holds no duration yet and is not stored until it does.
 */
@Composable
private fun StepTimer(
    position: Int,
    place: Int,
    count: Int,
    index: Int,
    step: Step,
    timer: Timer?,
    timers: TimerRows,
    focus: RowFocus,
    ingredients: List<Ingredient>,
    language: InterfaceLanguage,
    editor: RecipeEditor,
    left: Boolean,
) {
    var hours by rememberSaveable { mutableStateOf(timer?.duration?.inWholeHours?.toString().orEmpty()) }
    var minutes by rememberSaveable { mutableStateOf(timer?.duration?.let { (it.inWholeMinutes % 60).toString() }.orEmpty()) }
    // The name the author gave, kept on screen while the duration is cleared and given back when it is entered again.
    var given by rememberSaveable { mutableStateOf(timer?.label) }
    val name = remember { FocusRequester() }
    val hoursField = remember { FocusRequester() }
    focus.TakenBy(place, hoursField)
    // Read as the rows stand when the author types, so the timer edited is this one, wherever the others went.
    fun hold(h: String, m: String) {
        val typedHours = h.toIntOrNull() ?: 0
        val typedMinutes = m.toIntOrNull() ?: 0
        val at = timers.index(place)
        if (timers.held(place)) {
            if (editor.setTimerDuration(position, at, typedHours, typedMinutes) && typedHours == 0 && typedMinutes == 0) timers.hold(place, false)
        } else if (editor.addTimer(position, at, typedHours, typedMinutes, given)) {
            timers.hold(place, true)
        }
    }
    // Several rows are told apart by their place in the block, top to bottom, whether or not each holds a duration —
    // its removal, its name and the notice of its removal alike; a step's only row reads as it always did.
    val number = (place + 1).takeIf { count > 1 }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // Two digits each: the field cannot hold more than 99 h 59 min (FLOW-027 (14)), and no seconds are offered.
        OutlinedTextField(
            value = hours,
            onValueChange = { typed ->
                if (typed != hours && typed.length <= 2 && typed.all { it in '0'..'9' }) {
                    hours = typed
                    hold(typed, minutes)
                }
            },
            label = { Text(stringResource(R.string.editor_timer_hours)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
            modifier = Modifier.weight(1f).focusRequester(hoursField).testTag(EditorTags.timerHours(position, place)),
        )
        OutlinedTextField(
            value = minutes,
            onValueChange = { typed ->
                if (typed != minutes && typed.length <= 2 && typed.all { it in '0'..'9' } && (typed.toIntOrNull() ?: 0) <= RecipeEditor.MAX_MINUTES) {
                    minutes = typed
                    hold(hours, typed)
                }
            },
            label = { Text(stringResource(R.string.editor_timer_minutes)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            modifier = Modifier.weight(1f).testTag(EditorTags.timerMinutes(position, place)),
        )
        // Takes this timer off, with any name the author gave it; the step's other timers stay. Undo puts it back. One
        // that holds no duration yet is no timer: it is only taken off the screen.
        IconButton(
            onClick = {
                when {
                    !timers.held(place) -> timers.remove(place)
                    editor.removeTimer(position, timers.index(place), number) -> timers.remove(place)
                }
            },
            // Laid out at 48 dp, not the 40 dp the button draws by default (A11Y-003).
            modifier = Modifier.align(Alignment.CenterVertically).size(48.dp).testTag(EditorTags.timerRemove(position, place)),
        ) {
            Icon(
                painterResource(R.drawable.ic_remove_timer),
                contentDescription = if (number != null) {
                    stringResource(R.string.editor_timer_remove_numbered_action, position + 1, number)
                } else {
                    stringResource(R.string.editor_timer_remove_action, position + 1)
                },
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
    if (timer != null) {
        TimerNameField(position, place, index, number, StepText.runs(step, ReferenceText.authored(ingredients, language)), timer, editor, name) { given = it }
        BoundMark(editor, RecipePart.TimerName(position, index), EditorTags.timerNameMark(position, place), gap = 0.dp)
        // Under the name, so that it neither moves the name field nor goes from above it once the timer is named.
        if (left) {
            cutNotification(step, index, ingredients, language)?.let { reading ->
                TimerNotification(position, place, number, reading) { name.requestFocus() }
            }
        }
    }
}

/**
 * What the notification of the timer at [index] of [step] will say at the authored count, where that timer is unnamed
 * and the notification is cut, or may be at a reader's larger count (GUIDE-006); null otherwise.
 */
internal fun cutNotification(step: Step, index: Int, ingredients: List<Ingredient>, language: InterfaceLanguage): TimerName.Reading? {
    val timer = step.timers.getOrNull(index) ?: return null
    if (timer.label != null) return null
    return TimerName.reading(StepText.runs(step, ReferenceText.authored(ingredients, language))).takeIf { it.mayBeCut }
}

/**
 * What a step's unnamed timer will announce itself by, shown once the author leaves the step (GUIDE-006): the
 * notification's text as it will read, that quantities in it follow the reader's count where one is shown, and the
 * offer to name the timer, which takes the author to its name field. It refuses nothing and asks no rewrite.
 */
@Composable
private fun TimerNotification(position: Int, place: Int, number: Int?, reading: TimerName.Reading, onName: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            // Appearing above the field the author went on to, it keeps that field where it is.
            .then(keepingTheFocusedFieldInPlace(0.dp))
            .padding(top = 8.dp)
            .testTag(EditorTags.timerNotification(position, place)),
    ) {
        // Read by the screen reader as it appears.
        Text(
            stringResource(R.string.editor_timer_notification, reading.name),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .semantics { liveRegion = LiveRegionMode.Polite }
                .testTag(EditorTags.timerNotificationText(position, place)),
        )
        if (reading.referenceShown) {
            Text(
                stringResource(R.string.editor_timer_notification_follows),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite }
                    .testTag(EditorTags.timerNotificationFollows(position, place)),
            )
        }
        EditorButton(
            text = stringResource(R.string.editor_timer_notification_name),
            action = if (number != null) {
                stringResource(R.string.editor_timer_notification_name_numbered_action, position + 1, number)
            } else {
                stringResource(R.string.editor_timer_notification_name_action, position + 1)
            },
            onClick = onName,
            modifier = Modifier.testTag(EditorTags.timerNotificationName(position, place)),
        )
    }
}

/**
 * The name the timer will announce itself by (FLOW-027 (5–7)): with none given, the one derived from the step, shown
 * filled in and following the step's text; a correction is held as written, whatever the step says afterwards, and
 * however long (DOM-012 is not enforced while typing, only marked on leaving the field — FLOW-028); an emptied field
 * gives the name back to the step.
 */
@Composable
private fun TimerNameField(
    position: Int,
    place: Int,
    index: Int,
    number: Int?,
    step: List<StepRun>,
    timer: Timer,
    editor: RecipeEditor,
    requester: FocusRequester,
    onGiven: (String?) -> Unit,
) {
    // A reference reads here as the editor shows it (FLOW-016 (8), at the authored scale).
    val shown = timer.label ?: TimerName.derived(step)
    // While the author is in the field it shows what they typed, so that emptying it does not refill it at once.
    var focused by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf("") }
    val value = if (focused) typed else shown
    OutlinedTextField(
        value = value,
        onValueChange = { text ->
            // A name is one line, its spaces ordinary ones: a derived name holds its references together with no-break
            // spaces, which a name the author gives — seeded from it, or pasted — never keeps.
            val name = text.replace('\n', ' ').replace(StepText.NO_BREAK_SPACE, ' ')
            if (name == value.replace(StepText.NO_BREAK_SPACE, ' ')) return@OutlinedTextField
            typed = name
            onGiven(name.ifEmpty { null })
            editor.setTimerLabel(position, index, name)
        },
        label = { Text(if (number != null) stringResource(R.string.editor_timer_name_numbered, number) else stringResource(R.string.editor_timer_name)) },
        supportingText = if (timer.label == null) {
            { Text(stringResource(R.string.editor_timer_name_follows), modifier = Modifier.testTag(EditorTags.timerNameFollows(position, place))) }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(requester)
            .onFocusChanged {
                if (it.isFocused && !focused) typed = shown.replace(StepText.NO_BREAK_SPACE, ' ')
                if (!it.isFocused && focused) editor.leaveStep(editor.stepKey(position), timer = index)
                focused = it.isFocused
            }
            .testTag(EditorTags.timerName(position, place)),
    )
}

/** A duration as hours and minutes, the way the author entered it. */
@Composable
internal fun durationText(duration: Duration): String {
    val hours = duration.inWholeHours.toInt()
    val minutes = (duration.inWholeMinutes % 60).toInt()
    return when {
        hours == 0 -> stringResource(R.string.editor_timer_duration_minutes, minutes)
        minutes == 0 -> stringResource(R.string.editor_timer_duration_hours, hours)
        else -> stringResource(R.string.editor_timer_duration, hours, minutes)
    }
}

/**
 * A step as a frozen recipe shows it: its number, its whole text with its references read at the authored scale, set
 * apart as the field sets them (FLOW-032 (5–7)), the author's note, its photograph and its timer, and nothing to change
 * them with.
 */
@Composable
private fun ReadOnlyStep(position: Int, step: Step, photo: Media?, ingredients: List<Ingredient>, language: InterfaceLanguage, photos: StepPhotos) {
    val display = StepDisplay.of(step, ingredients, language)
    // A block, as an editable step is, without its drag column.
    OutlinedCard(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.testTag(EditorTags.step(position))) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(stringResource(R.string.editor_step, position + 1), style = MaterialTheme.typography.labelMedium)
            Text(
                display.styled(MaterialTheme.colorScheme.secondaryContainer),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.testTag(EditorTags.stepText(position)),
            )
            step.note?.let {
                Text(
                    stringResource(R.string.editor_step_note_read_only, it),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag(StepNoteTags.field(position)),
                )
            }
            if (photo != null) StepMediaShown(position, photo, photos)
            // Every timer, in its order; several are told apart by their place, a step's only one reads as it always did.
            val numbered = step.timers.size > 1
            step.timers.forEachIndexed { index, timer ->
                val duration = durationText(timer.duration)
                val name = TimerName.of(step, index, ReferenceText.authored(ingredients, language)).orEmpty()
                Text(
                    if (numbered) stringResource(R.string.editor_timer_read_only_numbered, index + 1, duration) else stringResource(R.string.editor_timer_read_only, duration),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag(EditorTags.timerDuration(position, index)),
                )
                Text(
                    if (numbered) stringResource(R.string.editor_timer_name_read_only_numbered, index + 1, name) else stringResource(R.string.editor_timer_name_read_only, name),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag(EditorTags.timerName(position, index)),
                )
            }
        }
    }
}

/** The description, after the steps (FLOW-024): one optional field over as many lines as it takes. */
@Composable
internal fun Description(recipe: Recipe, editor: RecipeEditor) {
    if (!recipe.state.editable) {
        Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.testTag(EditorTags.DESCRIPTION)) {
            Text(stringResource(R.string.editor_description), style = MaterialTheme.typography.labelMedium)
            Text(
                recipe.description.ifEmpty { stringResource(R.string.editor_description_none) },
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.testTag(EditorTags.DESCRIPTION_TEXT),
            )
        }
        return
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = recipe.description,
            onValueChange = { editor.setDescription(it) },
            label = { Text(stringResource(R.string.editor_description_optional)) },
            minLines = 3,
            // Over several lines, the keyboard keeps its new-line key.
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier
                .fillMaxWidth()
                // The description comes after the ingredients and the steps: reaching it moves the author on from both.
                .then(
                    leaving(
                        onEnter = {
                            editor.leave(RecipePart.Ingredients)
                            editor.leave(RecipePart.Steps)
                        },
                    ) { editor.leave(RecipePart.Description) },
                )
                .testTag(EditorTags.DESCRIPTION),
        )
        BoundMark(editor, RecipePart.Description, EditorTags.DESCRIPTION_MARK, gap = 4.dp)
    }
}

/**
 * The timers a step's block shows (DOM-024), each under a key of its own so that what the screen holds for a timer —
 * what was typed, a name given — stays with it: the step's timers, in their order, and among them those the author added
 * that hold no duration yet, each where it was added. The rows that are [Row.held] stand for the step's timers in order.
 */
@Stable
internal class TimerRows private constructor(rows: List<Row>, private var next: Long) {
    /** One timer shown: [held] where the step carries it, not yet where it holds no duration. */
    data class Row(val key: Long, val held: Boolean)

    private val rows = mutableStateListOf<Row>().apply { addAll(rows) }

    /** The rows of a step carrying [held] timers, none added yet. */
    constructor(held: Int) : this(List(held) { Row(it.toLong(), held = true) }, held.toLong())

    /**
     * The rows shown beside a step carrying [held] timers. Where its timers changed other than through these rows — an
     * Undo put one back — they are the step's timers alone, each under a new key; [settle] then makes them the rows.
     */
    fun shown(held: Int): List<Row> = if (heldCount() == held) rows.toList() else List(held) { Row(next + it, held = true) }

    /** Makes the rows [shown] gave for [held] what they are. */
    fun settle(held: Int) {
        if (heldCount() == held) return
        val fresh = shown(held)
        next += held
        rows.clear()
        rows.addAll(fresh)
    }

    /** How many timers are shown that hold no duration yet. */
    val unheld: Int get() = rows.size - heldCount()

    /** Adds a timer that holds no duration yet after the others; returns its place. */
    fun open(): Int {
        rows += Row(next++, held = false)
        return rows.lastIndex
    }

    /** Where the timer at [place] stands among the step's timers — or would, once it holds a duration. */
    fun index(place: Int): Int = rows.take(place).count { it.held }

    fun held(place: Int): Boolean = rows.getOrNull(place)?.held == true

    /** The timer at [place] is now [held] by the step, or no longer. */
    fun hold(place: Int, held: Boolean) {
        rows[place] = rows[place].copy(held = held)
    }

    fun remove(place: Int) {
        rows.removeAt(place)
    }

    private fun heldCount() = rows.count { it.held }

    companion object {
        /** Saved as the next key to give, then each row's key, doubled, plus one where it is held. */
        val Saver: Saver<TimerRows, LongArray> = Saver(
            save = { longArrayOf(it.next, *it.rows.map { row -> row.key * 2 + if (row.held) 1 else 0 }.toLongArray()) },
            restore = { saved -> TimerRows(saved.drop(1).map { Row(it / 2, it % 2 == 1L) }, saved.first()) },
        )
    }
}
