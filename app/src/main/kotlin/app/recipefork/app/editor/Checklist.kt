package app.recipefork.app.editor

import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.recipefork.app.R
import app.recipefork.core.timer.TimerName
import kotlinx.coroutines.launch

/**
 * One item of the checklist (GUIDE-004): what a good recipe does, said as advice, and why. It names nothing in the
 * recipe being written — the checklist is the same for every recipe.
 */
internal enum class ChecklistItem(@StringRes val title: Int, @StringRes val text: Int) {
    /** The timer's name is cut from the opening of its step, at the very length [TimerName] cuts at (FLOW-016). */
    TIMER_OPENING(R.string.editor_checklist_1_title, R.string.editor_checklist_1_text),
    ONE_ACTION(R.string.editor_checklist_2_title, R.string.editor_checklist_2_text),
    PHOTO_AT_ITS_STEP(R.string.editor_checklist_3_title, R.string.editor_checklist_3_text),
    REFERENCES(R.string.editor_checklist_4_title, R.string.editor_checklist_4_text),
    SUGGESTED_INGREDIENTS(R.string.editor_checklist_5_title, R.string.editor_checklist_5_text),
    METHOD_IN_THE_STEPS(R.string.editor_checklist_6_title, R.string.editor_checklist_6_text),
    SERVING_COUNT(R.string.editor_checklist_7_title, R.string.editor_checklist_7_text),
    ;

    /** The item's explanation; the first one gives the number of characters a timer's name keeps. */
    @Composable
    fun explanation(): String =
        if (this == TIMER_OPENING) stringResource(text, TimerName.LENGTH) else stringResource(text)
}

/**
 * The checklist's control at the editor's top (GUIDE-003): an icon with its label, 48 dp high, whose long press shows
 * what it offers in a tooltip. It is there for every recipe in every state, a frozen one too, and opens the checklist
 * in front of the editor, which stays where it was beneath. Whether it was ever opened is kept nowhere.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun ChecklistControl(modifier: Modifier = Modifier) {
    var open by rememberSaveable { mutableStateOf(false) }
    val tooltip = rememberTooltipState()
    val scope = rememberCoroutineScope()
    val label = stringResource(R.string.editor_checklist)
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
        tooltip = { PlainTooltip(Modifier.testTag(ChecklistTags.TOOLTIP)) { Text(stringResource(R.string.editor_checklist_tooltip)) } },
        state = tooltip,
        // The long press is the control's own, so that it stays one element for the screen reader.
        enableUserInput = false,
        modifier = modifier,
    ) {
        Row(
            Modifier
                .heightIn(min = 48.dp)
                .widthIn(min = 48.dp)
                .combinedClickable(
                    role = Role.Button,
                    onClickLabel = stringResource(R.string.editor_checklist_action),
                    onClick = { open = true },
                    onLongClick = { scope.launch { tooltip.show() } },
                )
                .padding(horizontal = 12.dp)
                .testTag(ChecklistTags.CONTROL),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(painterResource(R.drawable.ic_checklist), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(label, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        }
    }
    if (open) Checklist(onClose = { open = false })
}

/**
 * The checklist itself (GUIDE-004): the seven items, to be read and nothing else — no tick boxes, nothing that changes
 * the recipe — scrolling where they are longer than the screen, closed by its button, by back or by a touch outside.
 */
@Composable
internal fun Checklist(onClose: () -> Unit) {
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
                        Text(item.explanation(), style = MaterialTheme.typography.bodyMedium)
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
