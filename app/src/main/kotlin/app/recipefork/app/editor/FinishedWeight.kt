package app.recipefork.app.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import app.recipefork.app.R
import app.recipefork.core.recipe.Recipe

/**
 * What the finished dish weighs, under the serving count (v0.44): whole grams, or nothing. Anything else typed is not
 * held — the field keeps what it held and is drawn as refused, as the serving count is; the hint stays. The hint names
 * the serving count as it stands, so it follows that field as it is typed.
 */
@Composable
internal fun FinishedWeight(recipe: Recipe, editor: RecipeEditor, requester: FocusRequester, onNext: (() -> Unit)?) {
    if (!recipe.state.editable) {
        Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.testTag(EditorTags.FINISHED_WEIGHT)) {
            Text(stringResource(R.string.editor_finished_weight), style = MaterialTheme.typography.labelMedium)
            Text(
                recipe.finishedWeightGrams?.let { stringResource(R.string.editor_finished_weight_value, it) }
                    ?: stringResource(R.string.editor_finished_weight_none),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        return
    }
    var refused by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val servings = recipe.servings
    OutlinedTextField(
        value = recipe.finishedWeightGrams?.toString().orEmpty(),
        onValueChange = { refused = !editor.setFinishedWeight(it) },
        label = { Text(stringResource(R.string.editor_finished_weight)) },
        suffix = { Text(stringResource(R.string.editor_finished_weight_suffix)) },
        supportingText = {
            Text(
                if (servings == null) {
                    stringResource(R.string.editor_finished_weight_hint_no_servings)
                } else {
                    stringResource(
                        R.string.editor_finished_weight_hint,
                        pluralStringResource(R.plurals.editor_finished_weight_servings, servings, servings),
                    )
                },
                modifier = Modifier.testTag(EditorTags.FINISHED_WEIGHT_HINT),
            )
        },
        isError = refused,
        singleLine = true,
        // Next goes on as the serving count's did: to the first item of equipment or, with none, the ingredient list's
        // first item ([onNext]); with neither, Done closes the keyboard and adds nothing.
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = if (onNext == null) ImeAction.Done else ImeAction.Next,
        ),
        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }, onNext = { onNext?.invoke() }),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(requester)
            .testTag(EditorTags.FINISHED_WEIGHT),
    )
}
