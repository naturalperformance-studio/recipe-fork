package app.recipefork.app.media.crop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.recipefork.app.R
import app.recipefork.app.media.PhotoOffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import kotlin.math.roundToInt

/**
 * The crop a photograph goes through before it is attached (FLOW-026): a full-screen window with the photograph under
 * a fixed 4:3 landscape frame, which the author drags and pinches to frame it — the frame never leaving the photograph
 * nor getting narrower than [CropGeometry] allows. Done hands the framed part to [onDone], as a photograph to process
 * like any; Retake — for a photograph from the camera — goes back to [onRetake]; Cancel or back leave through
 * [onCancel] with nothing. A picture that cannot be read goes on to [onUnreadable], which hands it over as it is so that
 * processing says why it is refused.
 */
@Composable
fun CropScreen(
    photo: PhotoToCrop,
    onDone: (PhotoOffer) -> Unit,
    onRetake: () -> Unit,
    onCancel: () -> Unit,
    onUnreadable: () -> Unit,
) {
    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false, decorFitsSystemWindows = false),
    ) {
        CropContent(photo, onDone, onRetake, onCancel, onUnreadable)
    }
}

private sealed interface CropLoad {
    data object Opening : CropLoad
    class Ready(val picture: CropPicture) : CropLoad
    data object Unreadable : CropLoad
}

@Composable
private fun CropContent(
    photo: PhotoToCrop,
    onDone: (PhotoOffer) -> Unit,
    onRetake: () -> Unit,
    onCancel: () -> Unit,
    onUnreadable: () -> Unit,
) {
    val load by produceState<CropLoad>(CropLoad.Opening, photo) {
        value = withContext(Dispatchers.IO) { CropPicture.open(photo) }?.let { CropLoad.Ready(it) } ?: CropLoad.Unreadable
    }
    val unreadable by rememberUpdatedState(onUnreadable)
    if (load == CropLoad.Unreadable) LaunchedEffect(photo) { unreadable() }
    val ready = load as? CropLoad.Ready
    val box = remember(ready) { ready?.let { mutableStateOf(CropGeometry.initial(it.picture.upright)) } }
    Column(Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding().testTag(CropTags.SCREEN)) {
        Text(
            stringResource(R.string.media_crop_title),
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (ready != null && box != null) {
                Framing(ready.picture, box)
            } else {
                Text(
                    stringResource(R.string.media_crop_opening),
                    color = Color.White,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag(CropTags.MESSAGE),
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (photo.retake) {
                TextButton(onClick = onRetake, modifier = Modifier.heightIn(min = 48.dp).testTag(CropTags.RETAKE)) {
                    Text(stringResource(R.string.media_crop_retake), color = Color.White)
                }
            } else {
                TextButton(onClick = onCancel, modifier = Modifier.heightIn(min = 48.dp).testTag(CropTags.CANCEL)) {
                    Text(stringResource(R.string.media_crop_cancel), color = Color.White)
                }
            }
            Button(
                onClick = {
                    val picture = ready?.picture ?: return@Button
                    val rect = CropGeometry.pixels(box?.value ?: return@Button, picture.upright)
                    // Cut as processing first reads it, off the screen's thread and outliving the screen.
                    val cut = lazy { CropPicture.cut(photo, picture.raw, picture.orientation, rect) }
                    onDone(PhotoOffer(open = { ByteArrayInputStream(cut.value) }, release = {}))
                },
                enabled = ready != null,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                modifier = Modifier.heightIn(min = 48.dp).testTag(CropTags.DONE),
            ) { Text(stringResource(R.string.media_crop_done)) }
        }
    }
}

/** The photograph under the fixed frame, moved and zoomed by the author's fingers. */
@Composable
private fun Framing(picture: CropPicture, box: MutableState<CropBox>) {
    val image = remember(picture) { picture.preview.asImageBitmap() }
    val description = stringResource(R.string.media_crop_frame)
    val margin = with(LocalDensity.current) { 16.dp.toPx() }
    val border = with(LocalDensity.current) { 2.dp.toPx() }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val areaW = constraints.maxWidth.toFloat()
        val areaH = constraints.maxHeight.toFloat()
        val frameW = minOf(areaW - 2 * margin, (areaH - 2 * margin) * CropGeometry.ASPECT).coerceAtLeast(1f)
        val frameH = frameW / CropGeometry.ASPECT
        val frameLeft = (areaW - frameW) / 2
        val frameTop = (areaH - frameH) / 2
        val upright = picture.upright
        Canvas(
            Modifier.fillMaxSize()
                .pointerInput(picture, frameW, frameLeft, frameTop) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        box.value = CropGeometry.moved(
                            box.value, upright, frameW,
                            centroid.x - frameLeft, centroid.y - frameTop, pan.x, pan.y, zoom,
                        )
                    }
                }
                .semantics { contentDescription = description }
                .testTag(CropTags.FRAME),
        ) {
            val shown = box.value
            val scale = frameW / shown.width.toFloat()
            drawImage(
                image,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(image.width, image.height),
                dstOffset = IntOffset((frameLeft - shown.left * scale).roundToInt(), (frameTop - shown.top * scale).roundToInt()),
                dstSize = IntSize((upright.width * scale).roundToInt(), (upright.height * scale).roundToInt()),
                filterQuality = FilterQuality.Medium,
            )
            clipRect(frameLeft, frameTop, frameLeft + frameW, frameTop + frameH, ClipOp.Difference) {
                drawRect(Color.Black.copy(alpha = 0.6f))
            }
            drawRect(Color.White, Offset(frameLeft, frameTop), Size(frameW, frameH), style = Stroke(border))
        }
    }
}

object CropTags {
    const val SCREEN = "crop-screen"
    const val FRAME = "crop-frame"
    const val DONE = "crop-done"
    const val RETAKE = "crop-retake"
    const val CANCEL = "crop-cancel"
    const val MESSAGE = "crop-message"
}
