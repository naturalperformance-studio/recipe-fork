package app.recipefork.app.media.camera

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.recipefork.app.R

/**
 * Why no photo can be taken: the camera permission was refused (FLOW-026). The application's settings, where it can
 * be allowed, are a tap away; the gallery needs no permission and is still offered where the photo was asked for.
 */
@Composable
fun CameraPermissionExplanation(onDone: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(stringResource(R.string.media_camera_permission_title)) },
        text = { Text(stringResource(R.string.media_camera_permission_text), Modifier.testTag(CameraTags.PERMISSION_TEXT)) },
        confirmButton = {
            TextButton(
                onClick = {
                    onDone()
                    openApplicationSettings(context)
                },
                modifier = Modifier.heightIn(min = 48.dp).testTag(CameraTags.PERMISSION_SETTINGS),
            ) { Text(stringResource(R.string.media_camera_permission_settings)) }
        },
        dismissButton = {
            TextButton(
                onClick = onDone,
                modifier = Modifier.heightIn(min = 48.dp).testTag(CameraTags.PERMISSION_DISMISS),
            ) { Text(stringResource(R.string.media_camera_permission_dismiss)) }
        },
        modifier = Modifier.testTag(CameraTags.PERMISSION),
    )
}
