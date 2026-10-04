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
) {
    val photos = rememberStepPhotos(store, book, saving, clips, work)
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
    Column(
        Modifier.fillMaxWidth().zIndex(if (drag.dragged != null) 1f else 0f).testTag(EditorTags.STEPS),
        verticalArrangement = Arrangement.spacedBy(ROW_SPACING),
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
            key(editor.stepKey(position)) {
                if (editable) StepRow(position, steps.size, step, recipe.stepMedia(position), recipe.ingredients, dictionary, language, editor, stating == position, drag, photos, focus)
                else ReadOnlyStep(position, step, recipe.stepMedia(position), recipe.ingredients, language, photos)
            }
        }
        if (editable) {
            ActionText(
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
            )
        }
    }
}

/**
 * One step: its number in the field's label, its text over as many lines as it takes with its ingredient references in
 * it, its removal, its menu of inserting and moving (FLOW-025), the control that inserts a reference (FLOW-031), the
 * control that attaches its timer (FLOW-027) and its photograph or clip (FLOW-026 (2)). Removing the step deletes its
 * item's file once the book no longer holds it.
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
) {
    val field = remember { StepFieldState() }
    focus.TakenBy(position, field.focus)
    val key = editor.stepKey(position)
    val photoControl = rememberStepPhotoControl(position, editor, photos)
    // Opened by the timer's control before any duration is held: a timer of no duration is no timer, and is not stored.
    var timerOpen by rememberSaveable { mutableStateOf(false) }
    // Left with an unnamed timer whose notification is or may be cut: what it will say shows until the step is
    // entered again (GUIDE-006). Nothing of it is stored.
    var leftCut by remember { mutableStateOf(false) }
    val timerName = remember { FocusRequester() }
    // References offered under the first step while it is first written in this editing session (GUIDE-008).
    var offering by remember { mutableStateOf(false) }
    val offerShrinking = rememberShrinking()
    // One bounded block per step, as an ingredient's, so where one step ends and the next begins is plain; dragged, it
    // moves whole.
    OutlinedCard(
        Modifier
            .fillMaxWidth()
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
                                    ?.let { cutNotification(it, left.ingredients, language) } != null
                            },
                        )
                        .testTag(EditorTags.stepText(position)),
                )
                // The step's own slot for what its text needs, right under the text and above its actions.
                BoundMark(editor, RecipePart.StepText(position), EditorTags.stepMark(position), gap = 0.dp)
                BlockActions {
                    InsertIngredient(position, ingredients, dictionary, language, editor, field)
                    BlockAction(
                        icon = R.drawable.ic_step_timer,
                        label = stringResource(R.string.editor_timer_label, position + 1),
                        tag = EditorTags.timerAttach(position),
                        action = stringResource(R.string.editor_timer_action, position + 1),
                        onClick = { if (!editor.attachTimer(position)) timerOpen = true },
                    )
                    StepPhotoAction(position, photo, photoControl)
                    BlockAction(
                        icon = R.drawable.ic_remove_row,
                        label = stringResource(R.string.editor_step_remove_action, position + 1),
                        tag = EditorTags.stepRemove(position),
                        action = stringResource(R.string.editor_step_remove_action, position + 1),
                        onClick = { editor.removeStep(position) { removed -> removed.forEach { photos.store.delete(photos.book, it) } } },
                    )
                    RowMenu(
                        position = position,
                        count = count,
                        name = stringResource(R.string.editor_step_menu_action, position + 1),
                        tag = EditorTags.stepMenu(position),
                        actionTag = { EditorTags.stepAction(position, it) },
                        onAction = { action ->
                            when (action) {
                                RowAction.INSERT_ABOVE -> editor.insertStep(position)
                                RowAction.INSERT_BELOW -> editor.insertStep(position + 1)
                                RowAction.MOVE_UP -> editor.moveStep(position, position - 1)
                                RowAction.MOVE_DOWN -> editor.moveStep(position, position + 1)
                            }
                        },
                    )
                }
                // Below the row, only what the step has: its timer, and its photograph, or that one is being prepared.
                if (step.timer != null || timerOpen || stating || photo != null || photoControl.attaching.working.value) {
                    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                        StepTimer(
                            position, step, ingredients, language, editor, stating, timerOpen,
                            left = leftCut,
                            name = timerName,
                        ) { timerOpen = it }
                        StepPhotoShown(position, photo, photos, photoControl)
                    }
                }
            }
        }
    }
}

/** The room between a step block's outline, or its drag column, and what the block holds. */
private val STEP_PADDING = PaddingValues(start = 8.dp, top = 4.dp, end = 4.dp, bottom = 0.dp)

/**
 * The step's timer (FLOW-027), under the step's row of actions, whose timer control attaches it — or, [open], begins
 * one: its duration in hours and whole minutes — the only thing asked — and, once it has one, the name it will announce
 * itself by. The control stays on a step that carries a timer, and choosing it there creates nothing and says why
 * (GUIDE-007): what it says shows here while [stating].
 */
@Composable
private fun StepTimer(
    position: Int,
    step: Step,
    ingredients: List<Ingredient>,
    language: InterfaceLanguage,
    editor: RecipeEditor,
    stating: Boolean,
    open: Boolean,
    left: Boolean,
    name: FocusRequester,
    onOpen: (Boolean) -> Unit,
) {
    val timer = step.timer
    var hours by rememberSaveable { mutableStateOf(timer?.duration?.inWholeHours?.toString().orEmpty()) }
    var minutes by rememberSaveable { mutableStateOf(timer?.duration?.let { (it.inWholeMinutes % 60).toString() }.orEmpty()) }
    // The name the author gave, kept on screen while the duration is cleared and given back when it is entered again.
    var given by rememberSaveable { mutableStateOf(timer?.label) }
    fun hold(h: String, m: String) = editor.setTimerDuration(position, h.toIntOrNull() ?: 0, m.toIntOrNull() ?: 0, given)

    if (stating) {
        Text(
            stringResource(R.string.editor_timer_one_per_step),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .semantics { liveRegion = LiveRegionMode.Polite }
                .testTag(EditorTags.timerStatement(position)),
        )
    }
    if (timer == null && !open) return
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // Two digits each: the field cannot hold more than 99 h 59 min (FLOW-027 (14)), and no seconds are offered.
        OutlinedTextField(
            value = hours,
            onValueChange = { typed ->
                if (typed != hours && typed.length <= 2 && typed.all { it in '0'..'9' }) {
                    hours = typed
                    onOpen(true)
                    hold(typed, minutes)
                }
            },
            label = { Text(stringResource(R.string.editor_timer_hours)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
            modifier = Modifier.weight(1f).testTag(EditorTags.timerHours(position)),
        )
        OutlinedTextField(
            value = minutes,
            onValueChange = { typed ->
                if (typed != minutes && typed.length <= 2 && typed.all { it in '0'..'9' } && (typed.toIntOrNull() ?: 0) <= RecipeEditor.MAX_MINUTES) {
                    minutes = typed
                    onOpen(true)
                    hold(hours, typed)
                }
            },
            label = { Text(stringResource(R.string.editor_timer_minutes)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            modifier = Modifier.weight(1f).testTag(EditorTags.timerMinutes(position)),
        )
        if (timer != null) {
            // Takes the timer off, with any name the author gave it.
            IconButton(
                onClick = {
                    if (editor.removeTimer(position)) {
                        onOpen(false)
                        hours = ""
                        minutes = ""
                        given = null
                    }
                },
                // Laid out at 48 dp, not the 40 dp the button draws by default (A11Y-003).
                modifier = Modifier.align(Alignment.CenterVertically).size(48.dp).testTag(EditorTags.timerRemove(position)),
            ) {
                Icon(
                    painterResource(R.drawable.ic_remove_timer),
                    contentDescription = stringResource(R.string.editor_timer_remove_action, position + 1),
                )
            }
        }
    }
    if (timer != null) {
        TimerNameField(position, StepText.runs(step, ReferenceText.authored(ingredients, language)), timer, editor, name) { given = it }
        BoundMark(editor, RecipePart.TimerName(position), EditorTags.timerNameMark(position), gap = 0.dp)
        // Under the name, so that it neither moves the name field nor goes from above it once the timer is named.
        if (left) cutNotification(step, ingredients, language)?.let { reading -> TimerNotification(position, reading) { name.requestFocus() } }
    }
}

/**
 * What the notification of [step]'s timer will say at the authored count, where its timer is unnamed and the
 * notification is cut, or may be at a reader's larger count (GUIDE-006); null otherwise.
 */
internal fun cutNotification(step: Step, ingredients: List<Ingredient>, language: InterfaceLanguage): TimerName.Reading? {
    val timer = step.timer ?: return null
    if (timer.label != null) return null
    return TimerName.reading(StepText.runs(step, ReferenceText.authored(ingredients, language))).takeIf { it.mayBeCut }
}

/**
 * What a step's unnamed timer will announce itself by, shown once the author leaves the step (GUIDE-006): the
 * notification's text as it will read, that quantities in it follow the reader's count where one is shown, and the
 * offer to name the timer, which takes the author to its name field. It refuses nothing and asks no rewrite.
 */
@Composable
private fun TimerNotification(position: Int, reading: TimerName.Reading, onName: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            // Appearing above the field the author went on to, it keeps that field where it is.
            .then(keepingTheFocusedFieldInPlace(0.dp))
            .padding(top = 8.dp)
            .testTag(EditorTags.timerNotification(position)),
    ) {
        // Read by the screen reader as it appears.
        Text(
            stringResource(R.string.editor_timer_notification, reading.name),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .semantics { liveRegion = LiveRegionMode.Polite }
                .testTag(EditorTags.timerNotificationText(position)),
        )
        if (reading.referenceShown) {
            Text(
                stringResource(R.string.editor_timer_notification_follows),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite }
                    .testTag(EditorTags.timerNotificationFollows(position)),
            )
        }
        ActionText(
            text = stringResource(R.string.editor_timer_notification_name),
            action = stringResource(R.string.editor_timer_notification_name_action, position + 1),
            onClick = onName,
            modifier = Modifier.testTag(EditorTags.timerNotificationName(position)),
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
private fun TimerNameField(position: Int, step: List<StepRun>, timer: Timer, editor: RecipeEditor, requester: FocusRequester, onGiven: (String?) -> Unit) {
    // A reference reads here as the editor shows it (FLOW-016 (8), at the authored scale).
    val shown = timer.label ?: TimerName.derived(step)
    // While the author is in the field it shows what they typed, so that emptying it does not refill it at once.
    var focused by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf("") }
    val value = if (focused) typed else shown
    OutlinedTextField(
        value = value,
        onValueChange = { text ->
            // A name is one line.
            val name = text.replace('\n', ' ')
            if (name == value) return@OutlinedTextField
            typed = name
            onGiven(name.ifEmpty { null })
            editor.setTimerLabel(position, name)
        },
        label = { Text(stringResource(R.string.editor_timer_name)) },
        supportingText = if (timer.label == null) {
            { Text(stringResource(R.string.editor_timer_name_follows), modifier = Modifier.testTag(EditorTags.timerNameFollows(position))) }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(requester)
            .onFocusChanged {
                if (it.isFocused && !focused) typed = shown
                if (!it.isFocused && focused) editor.leaveStep(editor.stepKey(position), timerName = true)
                focused = it.isFocused
            }
            .testTag(EditorTags.timerName(position)),
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
 * apart as the field sets them (FLOW-032 (5–7)), its photograph and its timer, and nothing to change them with.
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
            if (photo != null) StepMediaShown(position, photo, photos)
            val timer = step.timer ?: return@Column
            Text(
                stringResource(R.string.editor_timer_read_only, durationText(timer.duration)),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag(EditorTags.timerDuration(position)),
            )
            Text(
                stringResource(R.string.editor_timer_name_read_only, TimerName.of(step, ReferenceText.authored(ingredients, language)).orEmpty()),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag(EditorTags.timerName(position)),
            )
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
