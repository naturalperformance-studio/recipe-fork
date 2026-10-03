package app.recipefork.app.library

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

/** The name a language choice is offered under: each language in itself, the device's in the interface's own. */
internal fun LanguageChoice.label(): Int = when (this) {
    LanguageChoice.ENGLISH -> R.string.language_english
    LanguageChoice.RUSSIAN -> R.string.language_russian
    LanguageChoice.DEVICE -> R.string.language_device
}

/**
 * The choice of the interface language (L10N-002): English, Russian or the device's, the one in force marked. Taking
 * one chooses it at once; dismissing the dialog changes nothing.
 */
@Composable
internal fun LanguageDialog(chosen: LanguageChoice, onChoose: (LanguageChoice) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(LanguageTags.DIALOG),
        title = { Text(stringResource(R.string.language_menu)) },
        text = {
            Column(Modifier.selectableGroup()) {
                LanguageChoice.entries.forEach { choice ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .selectable(selected = choice == chosen, role = Role.RadioButton, onClick = { onChoose(choice) })
                            .testTag(LanguageTags.choice(choice)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = choice == chosen, onClick = null)
                        Text(stringResource(choice.label()), modifier = Modifier.padding(start = 16.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp).testTag(LanguageTags.CANCEL),
            ) { Text(stringResource(R.string.language_cancel)) }
        },
    )
}

object LanguageTags {
    const val MENU_ITEM = "library-language"
    const val DIALOG = "library-language-dialog"
    const val CANCEL = "library-language-cancel"
    fun choice(choice: LanguageChoice) = "library-language-${choice.name.lowercase()}"
}
