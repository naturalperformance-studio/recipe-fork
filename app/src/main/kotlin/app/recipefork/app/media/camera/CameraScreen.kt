package app.recipefork.app.media.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.recipefork.app.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch

/**
 * The application's own camera (FLOW-026): a full-screen window over the editor with the live viewfinder of
 * [CameraFrame]'s 4:3 landscape frame, a shutter, and a way out. The picture taken goes to [onShot]; back or Close
 * leave through [onClose] with nothing. The camera is opened as the window shows and released as it leaves.
 */
@Composable
fun CameraScreen(camera: CameraAccess, onShot: (ByteArray) -> Unit, onClose: () -> Unit) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) {
        CameraContent(camera, onShot, onClose)
    }
}

private sealed interface CameraState {
    data object Opening : CameraState
    data class Open(val camera: OpenCamera) : CameraState
    data object NoCamera : CameraState
    data object Failed : CameraState
}

@Composable
private fun CameraContent(access: CameraAccess, onShot: (ByteArray) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var state by remember { mutableStateOf<CameraState>(CameraState.Opening) }
    LaunchedEffect(access, owner) {
        val camera = try {
            access.open(context, owner)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            state = CameraState.Failed
            return@LaunchedEffect
        }
        if (camera == null) {
            state = CameraState.NoCamera
            return@LaunchedEffect
        }
        try {
            state = CameraState.Open(camera)
            awaitCancellation()
        } finally {
            camera.close()
        }
    }
    Column(
        Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding().padding(16.dp).testTag(CameraTags.SCREEN),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth()) {
            TextButton(
                onClick = onClose,
                modifier = Modifier.heightIn(min = 48.dp).testTag(CameraTags.CLOSE)
                    .semantics { contentDescription = context.getString(R.string.media_camera_close_action) },
            ) { Text(stringResource(R.string.media_camera_close), color = Color.White) }
        }
        when (val current = state) {
            CameraState.Opening -> Box(Modifier.fillMaxWidth().aspectRatio(CameraFrame.ASPECT))
            is CameraState.Open -> Shooting(current.camera, onShot)
            CameraState.NoCamera -> Message(R.string.media_camera_none)
            CameraState.Failed -> Message(R.string.media_camera_failed)
        }
    }
}

/** The viewfinder, and under it the shutter and what became of the last press of it. */
@Composable
private fun Shooting(camera: OpenCamera, onShot: (ByteArray) -> Unit) {
    val scope = rememberCoroutineScope()
    var taking by remember(camera) { mutableStateOf(false) }
    var missed by remember(camera) { mutableStateOf(false) }
    val viewfinder = stringResource(R.string.media_camera_viewfinder)
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        camera.Viewfinder(
            Modifier.fillMaxWidth().aspectRatio(CameraFrame.ASPECT)
                .semantics { contentDescription = viewfinder }.testTag(CameraTags.VIEWFINDER),
        )
        Button(
            onClick = {
                taking = true
                missed = false
                scope.launch {
                    val picture = try {
                        camera.takePicture()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        null
                    }
                    taking = false
                    if (picture == null) missed = true else onShot(picture)
                }
            },
            enabled = !taking,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
            modifier = Modifier.size(88.dp).testTag(CameraTags.SHUTTER),
        ) {
            Text(stringResource(R.string.media_camera_shutter), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium)
        }
        if (taking || missed) {
            Text(
                stringResource(if (taking) R.string.media_camera_taking else R.string.media_camera_missed),
                color = Color.White,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag(CameraTags.MESSAGE),
            )
        }
    }
}

@Composable
private fun Message(text: Int) {
    Text(
        stringResource(text),
        color = Color.White,
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }.testTag(CameraTags.MESSAGE),
    )
}

object CameraTags {
    const val SCREEN = "camera-screen"
    const val VIEWFINDER = "camera-viewfinder"
    const val SHUTTER = "camera-shutter"
    const val CLOSE = "camera-close"
    const val MESSAGE = "camera-message"
    const val PERMISSION = "camera-permission"
    const val PERMISSION_TEXT = "camera-permission-text"
    const val PERMISSION_SETTINGS = "camera-permission-settings"
    const val PERMISSION_DISMISS = "camera-permission-dismiss"
}
