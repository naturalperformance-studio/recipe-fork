package app.recipefork.app.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.recipefork.app.R
import app.recipefork.core.recipe.EquipmentItem
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeBounds
import app.recipefork.core.recipe.RecipePart

/**
 * What the recipe is cooked with, between the serving count and the ingredients: one short line per item ("Oven",
 * "Frying pan 26 cm"), each in a field of its own with its removal and its menu of moving it. "Add" puts an item that
 * takes the focus; a recipe lists up to [RecipeBounds.MAX_EQUIPMENT], and an "Add" past that says so until the author's
 * next action, as the timers' limit does (GUIDE-007). An item over [RecipeBounds.MAX_EQUIPMENT_NAME] characters is kept
 * and marked on leaving it (FLOW-028); one left blank goes. The keyboard's Next walks the items, then [onPastLast] —
 * the ingredient list's first item; with none ([onPastLast] null), the last item says Done.
 *
 * Each item reads as [shown] gives it: an entry's label and its qualifier, or its free text; typing into an entry's item
 * makes it the text typed. A frozen recipe shows its items as text, and nothing where it lists none.
 */
@Composable
internal fun Equipment(
    recipe: Recipe,
    editor: RecipeEditor,
    focus: RowFocus,
    onPastLast: (() -> Unit)?,
    shown: (EquipmentItem) -> String = { it.shown(null) },
) {
    val items = recipe.equipment.map(shown)
    if (!recipe.state.editable) {
        if (items.isNotEmpty()) ReadOnlyEquipment(items)
        return
    }
    Column(Modifier.fillMaxWidth().testTag(EditorTags.EQUIPMENT), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        EquipmentHeading()
        items.forEachIndexed { index, item ->
            // What the screen holds for an item — its focus — stays with it as items before it go or move.
            key(editor.equipmentKey(index)) {
                val next = if (index < items.lastIndex) ({ focus.ask(index + 1) }) else onPastLast
                EquipmentLine(index, items.size, item, editor, focus, next)
            }
        }
        val full by editor.equipmentStatement.collectAsState()
        EditorButton(
            text = stringResource(R.string.editor_equipment_add),
            action = stringResource(R.string.editor_equipment_add_action),
            // The item added takes the focus, with the keyboard up.
            onClick = {
                val added = editor.recipe.value?.equipment?.size
                if (editor.addEquipment() && added != null) focus.ask(added)
            },
            modifier = Modifier.testTag(EditorTags.EQUIPMENT_ADD),
            weight = ButtonWeight.TONAL,
        )
        if (full) {
            Text(
                stringResource(R.string.editor_equipment_limit, RecipeBounds.MAX_EQUIPMENT),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { liveRegion = LiveRegionMode.Polite }
                    .testTag(EditorTags.EQUIPMENT_STATEMENT),
            )
        }
    }
}

@Composable
private fun EquipmentHeading() {
    Text(
        stringResource(R.string.editor_equipment),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.semantics { heading() },
    )
}

/**
 * The item at [index] of [count]: a one-line field, left trimmed — blank, it goes ([RecipeEditor.settleEquipment]) —
 * beside its removal, offered for undoing, and its menu of moving it one place up or down. [onNext] is where the
 * keyboard's Next goes; with none, the field says Done.
 */
@Composable
private fun EquipmentLine(index: Int, count: Int, item: String, editor: RecipeEditor, focus: RowFocus, onNext: (() -> Unit)?) {
    val key = editor.equipmentKey(index)
    val requester = remember { FocusRequester() }
    focus.TakenBy(index, requester)
    val manager = LocalFocusManager.current
    Column(Modifier.fillMaxWidth().testTag(EditorTags.equipment(index))) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            OutlinedTextField(
                value = item,
                onValueChange = { editor.setEquipment(index, it) },
                label = { Text(stringResource(R.string.editor_equipment_item, index + 1)) },
                placeholder = { Text(stringResource(R.string.editor_equipment_example)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = if (onNext == null) ImeAction.Done else ImeAction.Next,
                ),
                keyboardActions = KeyboardActions(onDone = { manager.clearFocus() }, onNext = { onNext?.invoke() }),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(requester)
                    .then(
                        // By the item's key, so it is this item that is settled and marked wherever it stands now.
                        leaving {
                            val now = editor.equipmentIndex(key) ?: return@leaving
                            editor.settleEquipment(now)
                            editor.equipmentIndex(key)?.let { editor.leave(RecipePart.EquipmentName(it)) }
                        },
                    )
                    .testTag(EditorTags.equipmentName(index)),
            )
            Row(Modifier.padding(top = 8.dp)) { EquipmentControls(index, count, editor) }
        }
        BoundMark(editor, RecipePart.EquipmentName(index), EditorTags.equipmentMark(index), gap = 4.dp, Modifier.padding(top = 4.dp))
    }
}

/** The removal of the item at [index] of [count], and its menu of moving it — none for an only item. */
@Composable
private fun EquipmentControls(index: Int, count: Int, editor: RecipeEditor) {
    val remove = stringResource(R.string.editor_equipment_remove_action, index + 1)
    BlockAction(
        icon = R.drawable.ic_remove_row,
        label = remove,
        tag = EditorTags.equipmentRemove(index),
        action = remove,
        onClick = { editor.removeEquipment(index) },
        destructive = true,
    )
    val moves = listOfNotNull(RowAction.MOVE_UP.takeIf { index > 0 }, RowAction.MOVE_DOWN.takeIf { index < count - 1 })
    if (moves.isEmpty()) return
    RowMenu(
        position = index,
        count = count,
        name = stringResource(R.string.editor_equipment_menu_action, index + 1),
        tag = EditorTags.equipmentMenu(index),
        actionTag = { EditorTags.equipmentAction(index, it) },
        onAction = { editor.moveEquipment(index, if (it == RowAction.MOVE_UP) index - 1 else index + 1) },
        actions = moves,
    )
}

/** The equipment as a frozen recipe shows it: its heading, then each item as a line of text. */
@Composable
private fun ReadOnlyEquipment(items: List<String>) {
    Column(Modifier.fillMaxWidth().testTag(EditorTags.EQUIPMENT), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        EquipmentHeading()
        items.forEachIndexed { index, item ->
            Text(item, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.fillMaxWidth().testTag(EditorTags.equipment(index)))
        }
    }
}
