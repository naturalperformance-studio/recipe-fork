package app.recipefork.app.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import app.recipefork.app.R
import app.recipefork.app.ui.fitWords
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * One action of a sheet — an ingredient row's, a heading's, a step's: what it says, the tag tests find it by, and what a
 * tap on it does. One that [leaves] the sheet for something else has its fields left first; a [destructive] one — a
 * removal — says it in the error colour, leaves the fields, and is done once the sheet has gone down.
 */
internal class SheetAction(
    val text: String,
    val tag: String,
    val leaves: Boolean = false,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * The top of a sheet — an ingredient row's, a heading's, a step's: its [title] as a heading, then "Done" ([onDone]). The
 * title wraps between words; a word wider than its room — a long Russian word at a large font scale on a narrow phone —
 * is drawn smaller until it fits, so that no word is broken.
 */
@Composable
internal fun SheetHeader(title: String, titleTag: String, doneTag: String, onDone: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        BoxWithConstraints(Modifier.weight(1f)) {
            val style = MaterialTheme.typography.titleLarge
            val measurer = rememberTextMeasurer()
            val width = constraints.maxWidth
            val fitted = remember(title, style, width, measurer) { fitWords(title, style, width, measurer) }
            Text(title, style = fitted, modifier = Modifier.semantics { heading() }.testTag(titleTag))
        }
        TextButton(onClick = onDone, modifier = Modifier.heightIn(min = 48.dp).testTag(doneTag)) {
            Text(stringResource(R.string.editor_done))
        }
    }
}

/**
 * A sheet's [actions] under its fields, apart from them by a divider: one list item each, at least 56 dp tall, its whole
 * width the target, a removal in the error colour. Each is one tap, so nothing done by dragging is done only so
 * (A11Y-006); a tap hands its action to [take].
 */
@Composable
internal fun SheetActions(actions: List<SheetAction>, take: (SheetAction) -> Unit) {
    if (actions.isEmpty()) return
    HorizontalDivider(Modifier.padding(top = 12.dp, bottom = 4.dp))
    actions.forEach { action ->
        ListItem(
            headlineContent = { Text(action.text) },
            colors = ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                headlineColor = if (action.destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(role = Role.Button) { take(action) }
                .testTag(action.tag),
        )
    }
}

/** Whether a sheet is on its way out: closing, or left for another row, a heading or a new step. */
internal class Going {
    private var going = false

    /** Sets the sheet on its way out; false where it already was, so that whatever asked does nothing. */
    fun start(): Boolean = !going.also { going = true }

    val now: Boolean get() = going
}

/**
 * Does [action] of the sheet whose state is [state], unless the sheet is [going] already — a second tap during its way
 * out does nothing: [leave] first where it [SheetAction.leaves] the sheet's fields, which sets the sheet on its way out;
 * a removal once the sheet has gone down, after which the sheet is done ([onClose]).
 */
@OptIn(ExperimentalMaterial3Api::class)
internal fun CoroutineScope.take(action: SheetAction, going: Going, state: SheetState, leave: () -> Unit, onClose: () -> Unit) {
    if (going.now || (action.leaves || action.destructive) && !going.start()) return
    if (action.leaves) leave()
    if (!action.destructive) return action.onClick()
    launch { state.hide() }.invokeOnCompletion {
        action.onClick()
        onClose()
    }
}
