package app.recipefork.app.editor

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.recipefork.app.Mode
import app.recipefork.app.R
import app.recipefork.core.timer.TimerName

/**
 * One item of the checklist (GUIDE-004): what a good recipe does, said as advice, and why. It names nothing in the
 * recipe being written — the checklist is the same for every recipe.
 */
internal enum class ChecklistItem(
    @StringRes val title: Int,
    @StringRes val text: Int,
    /** The explanation in [Mode.Serverless], where nothing is ever published, where it differs from [text]. */
    @StringRes val serverlessText: Int = text,
) {
    /** The timer's name is cut from the opening of its step, at the very length [TimerName] cuts at (FLOW-016). */
    TIMER_OPENING(R.string.editor_checklist_1_title, R.string.editor_checklist_1_text),
    ONE_ACTION(R.string.editor_checklist_2_title, R.string.editor_checklist_2_text),
    PHOTO_AT_ITS_STEP(R.string.editor_checklist_3_title, R.string.editor_checklist_3_text),
    REFERENCES(R.string.editor_checklist_4_title, R.string.editor_checklist_4_text),
    SUGGESTED_INGREDIENTS(
        R.string.editor_checklist_5_title,
        R.string.editor_checklist_5_text,
        serverlessText = R.string.editor_checklist_5_text_serverless,
    ),
    METHOD_IN_THE_STEPS(R.string.editor_checklist_6_title, R.string.editor_checklist_6_text),
    SERVING_COUNT(R.string.editor_checklist_7_title, R.string.editor_checklist_7_text),
    ;

    /**
     * The item's explanation in [mode] — a build without the server says nothing of publication — the first one
     * giving the number of characters a timer's name keeps.
     */
    @Composable
    fun explanation(mode: Mode): String {
        val text = if (mode == Mode.Serverless) serverlessText else text
        return if (this == TIMER_OPENING) stringResource(text, TimerName.LENGTH) else stringResource(text)
    }
}

/**
 * The checklist's control at the editor's top (GUIDE-003): an icon-only 48 dp action named "Checklist" for the screen
 * reader, whose long press shows what it offers in a tooltip. It is there for every recipe in every state, a frozen one
 * too, and opens the checklist in front of the editor, which stays where it was beneath; its wording follows the
 * build's [mode]. Whether it was ever opened is kept nowhere.
 */
@Composable
internal fun ChecklistControl(mode: Mode) {
    var open by rememberSaveable { mutableStateOf(false) }
    EditorBarAction(
        icon = R.drawable.ic_checklist,
        label = stringResource(R.string.editor_checklist),
        action = stringResource(R.string.editor_checklist_action),
        tooltip = stringResource(R.string.editor_checklist_tooltip),
        tag = ChecklistTags.CONTROL,
        tooltipTag = ChecklistTags.TOOLTIP,
        onClick = { open = true },
    )
    if (open) Checklist(mode, onClose = { open = false })
}

/**
 * The checklist itself (GUIDE-004): the seven items, to be read and nothing else — no tick boxes, nothing that changes
 * the recipe — scrolling where they are longer than the screen, closed by its button, by back or by a touch outside.
 */
@Composable
internal fun Checklist(mode: Mode, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        modifier = Modifier.testTag(ChecklistTags.DIALOG),
        title = {
            Text(stringResource(R.string.editor_checklist), modifier = Modifier.semantics { heading() })
        },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()).testTag(ChecklistTags.ITEMS),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ChecklistItem.entries.forEach { item ->
                    Column(Modifier.testTag(ChecklistTags.item(item.ordinal)), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(item.title), style = MaterialTheme.typography.titleSmall)
                        Text(item.explanation(mode), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onClose, modifier = Modifier.heightIn(min = 48.dp).testTag(ChecklistTags.CLOSE)) {
                Text(stringResource(R.string.editor_checklist_close))
            }
        },
    )
}

object ChecklistTags {
    const val CONTROL = "editor-checklist"
    const val TOOLTIP = "editor-checklist-tooltip"
    const val DIALOG = "editor-checklist-dialog"
    const val ITEMS = "editor-checklist-items"
    const val CLOSE = "editor-checklist-close"
    fun item(index: Int) = "editor-checklist-item-$index"
}
