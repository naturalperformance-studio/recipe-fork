package app.recipefork.app.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.recipefork.app.R
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.Timer
import app.recipefork.core.timer.TimerName
import kotlin.time.Duration

/**
 * The steps, after the ingredients (FLOW-024): numbered plain text, appended at the end, edited where each stands and
 * deleted. They accept input whatever else the recipe holds or lacks (FLOW-024 (2)), and no text is ever cut short.
 */
@Composable
internal fun Steps(recipe: Recipe, editor: RecipeEditor) {
    val editable = recipe.state.editable
    val steps = recipe.steps.sortedBy { it.position }
    Column(Modifier.fillMaxWidth().testTag(EditorTags.STEPS), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(R.string.editor_steps),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
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
                if (editable) StepRow(position, steps.size, step, editor, stating == position) else ReadOnlyStep(position, step)
            }
        }
        if (editable) {
            ActionText(
                text = stringResource(R.string.editor_step_add),
                action = stringResource(R.string.editor_step_add_action),
                onClick = { editor.addStep() },
                modifier = Modifier.testTag(EditorTags.STEP_ADD),
            )
        }
    }
}

/**
 * One step: its number in the field's label, its text over as many lines as it takes, its removal, its menu of
 * inserting and moving (FLOW-025), and the control that attaches its timer (FLOW-027).
 */
@Composable
private fun StepRow(position: Int, count: Int, step: Step, editor: RecipeEditor, stating: Boolean) {
    Column(Modifier.fillMaxWidth().testTag(EditorTags.step(position))) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = step.text,
                onValueChange = { editor.setStepText(position, it) },
                label = { Text(stringResource(R.string.editor_step, position + 1)) },
                minLines = 2,
                modifier = Modifier.weight(1f).testTag(EditorTags.stepText(position)),
            )
            ActionText(
                text = stringResource(R.string.editor_step_remove),
                action = stringResource(R.string.editor_step_remove_action, position + 1),
                onClick = { editor.removeStep(position) },
                modifier = Modifier.testTag(EditorTags.stepRemove(position)),
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
        StepTimer(position, step, editor, stating)
    }
}

/**
 * The step's timer (FLOW-027): a control that attaches it, then its duration in hours and whole minutes — the only
 * thing asked — and, once it has one, the name it will announce itself by. The control stays on a step that carries a
 * timer, and choosing it there creates nothing and says why (GUIDE-007).
 */
@Composable
private fun StepTimer(position: Int, step: Step, editor: RecipeEditor, stating: Boolean) {
    val timer = step.timer
    // Opened by the control before any duration is held: a timer of no duration is no timer, and is not stored.
    var open by rememberSaveable { mutableStateOf(false) }
    var hours by rememberSaveable { mutableStateOf(timer?.duration?.inWholeHours?.toString().orEmpty()) }
    var minutes by rememberSaveable { mutableStateOf(timer?.duration?.let { (it.inWholeMinutes % 60).toString() }.orEmpty()) }
    // The name the author gave, kept on screen while the duration is cleared and given back when it is entered again.
    var given by rememberSaveable { mutableStateOf(timer?.label) }
    fun hold(h: String, m: String) = editor.setTimerDuration(position, h.toIntOrNull() ?: 0, m.toIntOrNull() ?: 0, given)

    ActionText(
        text = stringResource(R.string.editor_timer),
        action = stringResource(R.string.editor_timer_action, position + 1),
        onClick = { if (!editor.attachTimer(position)) open = true },
        modifier = Modifier.testTag(EditorTags.timerAttach(position)),
    )
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
                    open = true
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
                    open = true
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
                        open = false
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
    if (timer != null) TimerNameField(position, step.text, timer, editor) { given = it }
}

/**
 * The name the timer will announce itself by (FLOW-027 (5–7)): with none given, the one derived from the step, shown
 * filled in and following the step's text; a correction is held as written, whatever the step says afterwards, and
 * however long (DOM-012 is not enforced while typing); an emptied field gives the name back to the step.
 */
@Composable
private fun TimerNameField(position: Int, stepText: String, timer: Timer, editor: RecipeEditor, onGiven: (String?) -> Unit) {
    val shown = timer.label ?: TimerName.derived(stepText)
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
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged {
                if (it.isFocused && !focused) typed = shown
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

/** A step as a frozen recipe shows it: its number, its whole text and its timer, and nothing to change them with. */
@Composable
private fun ReadOnlyStep(position: Int, step: Step) {
    Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.testTag(EditorTags.step(position))) {
        Text(stringResource(R.string.editor_step, position + 1), style = MaterialTheme.typography.labelMedium)
        Text(step.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag(EditorTags.stepText(position)))
        val timer = step.timer ?: return@Column
        Text(
            stringResource(R.string.editor_timer_read_only, durationText(timer.duration)),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag(EditorTags.timerDuration(position)),
        )
        Text(
            stringResource(R.string.editor_timer_name_read_only, TimerName.of(step).orEmpty()),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag(EditorTags.timerName(position)),
        )
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
    OutlinedTextField(
        value = recipe.description,
        onValueChange = { editor.setDescription(it) },
        label = { Text(stringResource(R.string.editor_description_optional)) },
        minLines = 3,
        modifier = Modifier.fillMaxWidth().testTag(EditorTags.DESCRIPTION),
    )
}
