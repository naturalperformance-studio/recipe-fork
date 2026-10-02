package app.recipefork.app.card

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.recipefork.app.R
import app.recipefork.core.recipe.Step

/**
 * The card's steps, in order and numbered as they read. A step holding no text yet is not a step a reader can
 * follow and is left out; with none left, so is the whole part, heading included (FLOW-019).
 */
@Composable
internal fun StepsList(steps: List<Step>) {
    val shown = steps.sortedBy { it.position }.filter { it.text.isNotBlank() }
    if (shown.isEmpty()) return
    Section(R.string.card_steps, CardTags.STEPS) {
        shown.forEachIndexed { index, step -> StepRow(index + 1, step) }
    }
}

/** One step: its number and its text, read by the screen reader as one line. */
@Composable
private fun StepRow(number: Int, step: Step) {
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.testTag(CardTags.step(step.position))) {
        Text("$number.", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.widthIn(min = 32.dp))
        Text(step.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    }
}
