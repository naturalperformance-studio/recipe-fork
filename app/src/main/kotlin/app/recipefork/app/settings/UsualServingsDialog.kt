package app.recipefork.app.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.recipefork.app.R
import app.recipefork.app.card.ServingsControl
import app.recipefork.app.card.ServingsTags
import app.recipefork.app.editor.interfaceLanguage
import app.recipefork.core.recipe.RecipeLanguage
import app.recipefork.core.scaling.InterfaceLanguage
import app.recipefork.core.scaling.Rational
import app.recipefork.core.scaling.RequestedServings
import java.math.BigInteger

/** The count the choice starts at while no usual count is set. */
internal val FIRST_CHOICE: Rational = Rational.of(2)

/**
 * The choice of the usual count: two choices, "Not set" — each recipe opens at its author's count — or a usual count,
 * which the card's own serving control ([ServingsControl]) walks under it — − and + walk exactly the counts a reader
 * can request on a card (SCALE-010, SCALE-011), from ¼ up with no ceiling, and choose the count. "Set" keeps the choice
 * — the count shown, or none ([onSet] null) — and Cancel, like dismissing the dialog, changes nothing. It opens on
 * the setting in force: the count [initial], or "Not set" while [notSet].
 */
@Composable
internal fun UsualServingsDialog(
    initial: Rational,
    notSet: Boolean,
    language: InterfaceLanguage,
    onSet: (Rational?) -> Unit,
    onDismiss: () -> Unit,
) {
    // Both kept as the screen turns: the count walked to, and the choice.
    var shown by rememberSaveable(saver = COUNT_SAVER) { mutableStateOf(initial) }
    var none by rememberSaveable { mutableStateOf(notSet) }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(SettingsTags.USUAL_DIALOG),
        title = { Text(stringResource(R.string.library_usual_servings)) },
        text = {
            Column(Modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Choice(stringResource(R.string.library_usual_not_set), selected = none, tag = SettingsTags.USUAL_NOT_SET) { none = true }
                Choice(stringResource(R.string.library_usual_count_choice), selected = !none, tag = SettingsTags.USUAL_COUNT_CHOICE) { none = false }
                ServingsControl(
                    modifier = Modifier.padding(start = CONTROL_INDENT),
                    count = RequestedServings.text(shown, language),
                    approximate = false,
                    canDecrease = shown > RequestedServings.MIN,
                    onDecrease = {
                        shown = RequestedServings.decreased(shown)
                        none = false
                    },
                    onIncrease = {
                        shown = RequestedServings.increased(shown)
                        none = false
                    },
                    tags = ServingsTags(SettingsTags.USUAL_COUNT, SettingsTags.USUAL_FEWER, SettingsTags.USUAL_MORE),
                    fewerAction = R.string.library_usual_fewer_action,
                    moreAction = R.string.library_usual_more_action,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSet(if (none) null else shown) },
                modifier = Modifier.heightIn(min = 48.dp).testTag(SettingsTags.USUAL_SET),
            ) { Text(stringResource(R.string.library_usual_set)) }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp).testTag(SettingsTags.USUAL_CANCEL),
            ) { Text(stringResource(R.string.library_usual_cancel)) }
        },
    )
}

/** The count walked to, kept as its numerator and denominator. */
private val COUNT_SAVER = listSaver<MutableState<Rational>, String>(
    save = { listOf(it.value.numerator.toString(), it.value.denominator.toString()) },
    restore = { mutableStateOf(Rational.of(BigInteger(it[0]), BigInteger(it[1]))) },
)

/** How far the count's control stands in, under its choice's label rather than its radio button. */
private val CONTROL_INDENT = 48.dp

/** One of the dialog's two choices: the whole row, 48 dp tall at least, picks it. */
@Composable
private fun Choice(label: String, selected: Boolean, tag: String, onPick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onPick)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(horizontal = 12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
internal fun servingsLanguage(): InterfaceLanguage = when (interfaceLanguage(LocalConfiguration.current)) {
    RecipeLanguage.RU -> InterfaceLanguage.RUSSIAN
    RecipeLanguage.EN -> InterfaceLanguage.ENGLISH
}
