package app.recipefork.app.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.recipefork.app.R
import app.recipefork.app.data.LanguageChoice

/** The tags of one [ChoiceDialog]: the dialog, its Cancel, and each of its choices. */
internal class ChoiceTags<T>(val dialog: String, val cancel: String, val choice: (T) -> String)

/**
 * The choice of one setting's value among [choices], each a radio row 48 dp tall at least, the one in force marked.
 * Taking one chooses it at once; Cancel, or dismissing the dialog, changes nothing.
 */
@Composable
internal fun <T> ChoiceDialog(
    title: String,
    choices: List<T>,
    chosen: T,
    label: @Composable (T) -> String,
    cancel: String,
    tags: ChoiceTags<T>,
    onChoose: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(tags.dialog),
        title = { Text(title) },
        text = {
            Column(Modifier.selectableGroup()) {
                choices.forEach { choice ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .selectable(selected = choice == chosen, role = Role.RadioButton, onClick = { onChoose(choice) })
                            .testTag(tags.choice(choice)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = choice == chosen, onClick = null)
                        Text(label(choice), modifier = Modifier.padding(start = 16.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp).testTag(tags.cancel),
            ) { Text(cancel) }
        },
    )
}

/** The name a language choice is offered under: each language in itself, the device's in the interface's own. */
internal fun LanguageChoice.label(): Int = when (this) {
    LanguageChoice.ENGLISH -> R.string.language_english
    LanguageChoice.RUSSIAN -> R.string.language_russian
    LanguageChoice.DEVICE -> R.string.language_device
}

/** The interface language's dialog (L10N-002); its tags keep the names they had in the book's menu. */
object LanguageTags {
    const val DIALOG = "library-language-dialog"
    const val CANCEL = "library-language-cancel"
    fun choice(choice: LanguageChoice) = "library-language-${choice.name.lowercase()}"
}

/** The interface language's dialog: English, Russian or the device's. */
@Composable
internal fun LanguageDialog(chosen: LanguageChoice, onChoose: (LanguageChoice) -> Unit, onDismiss: () -> Unit) = ChoiceDialog(
    title = stringResource(R.string.language_menu),
    choices = LanguageChoice.entries,
    chosen = chosen,
    label = { stringResource(it.label()) },
    cancel = stringResource(R.string.language_cancel),
    tags = ChoiceTags(LanguageTags.DIALOG, LanguageTags.CANCEL, LanguageTags::choice),
    onChoose = onChoose,
    onDismiss = onDismiss,
)
