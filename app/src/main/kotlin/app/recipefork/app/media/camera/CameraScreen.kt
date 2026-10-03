package app.recipefork.app.media.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
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
 * The application's own camera (FLOW-026): a full-screen window over the editor, the live viewfinder filling it with
 * the whole frame the camera takes, and over it a way out at the top and the shutter at the bottom. The picture taken
 * goes to [onShot] — to be cropped next; back or Close leave through [onClose] with nothing. The camera is opened as
 * the window shows and released as it leaves.
 */
@Composable
fun CameraScreen(camera: CameraAccess, onShot: (Shot) -> Unit, onClose: () -> Unit) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false, decorFitsSystemWindows = false),
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
private fun CameraContent(access: CameraAccess, onShot: (Shot) -> Unit, onClose: () -> Unit) {
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
    Box(Modifier.fillMaxSize().background(Color.Black).testTag(CameraTags.SCREEN)) {
        when (val current = state) {
            CameraState.Opening -> Unit
            is CameraState.Open -> Shooting(current.camera, onShot)
            CameraState.NoCamera -> Message(R.string.media_camera_none)
            CameraState.Failed -> Message(R.string.media_camera_failed)
        }
        Box(Modifier.align(Alignment.TopStart).safeDrawingPadding().padding(8.dp)) {
            TextButton(
                onClick = onClose,
                colors = ButtonDefaults.textButtonColors(containerColor = Color.Black.copy(alpha = 0.4f)),
                modifier = Modifier.heightIn(min = 48.dp).testTag(CameraTags.CLOSE)
                    .semantics { contentDescription = context.getString(R.string.media_camera_close_action) },
            ) { Text(stringResource(R.string.media_camera_close), color = Color.White) }
        }
    }
}

/** The viewfinder over the whole window, and over it at the bottom the shutter and what became of its last press. */
@Composable
private fun BoxScope.Shooting(camera: OpenCamera, onShot: (Shot) -> Unit) {
    val scope = rememberCoroutineScope()
    var taking by remember(camera) { mutableStateOf(false) }
    var missed by remember(camera) { mutableStateOf(false) }
    val viewfinder = stringResource(R.string.media_camera_viewfinder)
    camera.Viewfinder(
        Modifier.fillMaxSize().semantics { contentDescription = viewfinder }.testTag(CameraTags.VIEWFINDER),
    )
    Column(
        Modifier.align(Alignment.BottomCenter).fillMaxWidth().safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (taking || missed) {
            Text(
                stringResource(if (taking) R.string.media_camera_taking else R.string.media_camera_missed),
                color = Color.White,
                modifier = Modifier.background(Color.Black.copy(alpha = 0.5f)).padding(horizontal = 8.dp, vertical = 4.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite }.testTag(CameraTags.MESSAGE),
            )
        }
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
    }
}

@Composable
private fun BoxScope.Message(text: Int) {
    Text(
        stringResource(text),
        color = Color.White,
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.align(Alignment.Center).fillMaxWidth().padding(24.dp)
            .semantics { liveRegion = LiveRegionMode.Polite }.testTag(CameraTags.MESSAGE),
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
