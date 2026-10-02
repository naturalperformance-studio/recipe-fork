package app.recipefork.app.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.recipefork.app.R
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.Step

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
        steps.forEachIndexed { position, step ->
            if (editable) StepRow(position, step, editor) else ReadOnlyStep(position, step)
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

/** One step: its number in the field's label, its text over as many lines as it takes, and its removal. */
@Composable
private fun StepRow(position: Int, step: Step, editor: RecipeEditor) {
    Row(Modifier.fillMaxWidth().testTag(EditorTags.step(position)), verticalAlignment = Alignment.CenterVertically) {
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
    }
}

/** A step as a frozen recipe shows it: its number and its whole text, and nothing to change it with. */
@Composable
private fun ReadOnlyStep(position: Int, step: Step) {
    Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.testTag(EditorTags.step(position))) {
        Text(stringResource(R.string.editor_step, position + 1), style = MaterialTheme.typography.labelMedium)
        Text(step.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag(EditorTags.stepText(position)))
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
