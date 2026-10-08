package app.recipefork.app.settings

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.recipefork.app.Mode
import app.recipefork.app.R
import app.recipefork.app.library.DestructiveConfirm

/** Where the clearing of saved media stands while a dialog of it is shown. */
internal enum class ClearingMedia { ASKING, NOTHING }

/**
 * The statement before saved media is cleared (FLOW-060): nothing is deleted until it is confirmed, and declining it
 * changes nothing. In [Mode.Serverless] it says the media cannot be fetched again (FLOW-060 (4)) and names nothing of
 * the server (FLOW-062 (1)); in [Mode.Standard] it says the media is fetched again when needed.
 */
@Composable
internal fun ClearMediaStatement(mode: Mode, onConfirm: () -> Unit, onDecline: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDecline,
        modifier = Modifier.testTag(SettingsTags.CLEAR_MEDIA_STATEMENT),
        title = { Text(stringResource(R.string.library_clear_media_title)) },
        text = {
            Text(
                stringResource(
                    when (mode) {
                        Mode.Serverless -> R.string.library_clear_media_statement_serverless
                        Mode.Standard -> R.string.library_clear_media_statement
                    },
                ),
            )
        },
        confirmButton = {
            DestructiveConfirm(R.string.library_clear_media_confirm, SettingsTags.CLEAR_MEDIA_CONFIRM, onClick = onConfirm)
        },
        dismissButton = {
            TextButton(
                onClick = onDecline,
                modifier = Modifier.heightIn(min = 48.dp).testTag(SettingsTags.CLEAR_MEDIA_DECLINE),
            ) { Text(stringResource(R.string.library_clear_media_decline)) }
        },
    )
}

/** Said in place of the statement when the device holds no media the clearing would delete. */
@Composable
internal fun NothingToClear(onDone: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDone,
        modifier = Modifier.testTag(SettingsTags.CLEAR_MEDIA_NOTHING),
        text = { Text(stringResource(R.string.library_clear_media_nothing)) },
        confirmButton = {
            TextButton(
                onClick = onDone,
                modifier = Modifier.heightIn(min = 48.dp).testTag(SettingsTags.CLEAR_MEDIA_OK),
            ) { Text(stringResource(R.string.library_clear_media_ok)) }
        },
    )
}
