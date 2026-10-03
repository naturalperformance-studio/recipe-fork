package app.recipefork.app.editor

import android.media.MediaMetadataRetriever
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.recipefork.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * A step's clip in the editor: its first frame, read off the main thread, with a play mark over it so that it is not
 * taken for a photograph — or, until the frame is read or where it cannot be, a plain frame with the mark. The screen
 * reader calls it the author's video of the step, as the card does (A11Y-001), and never describes it; [working]
 * while another item offered is being prepared.
 */
@Composable
internal fun StepClipFrame(position: Int, file: File, working: Boolean = false) {
    val frame by produceState<ImageBitmap?>(null, file) { value = withContext(Dispatchers.IO) { firstFrame(file) } }
    val said = stringResource(R.string.media_step_clip_description, position + 1)
    val ratio = frame?.let { maxOf(it.width.toFloat() / it.height, 1f) } ?: PLACEHOLDER_RATIO
    Box(
        Modifier
            .padding(vertical = 4.dp)
            .fillMaxWidth()
            .aspectRatio(ratio)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .semantics {
                contentDescription = said
                role = Role.Image
            }
            .testTag(EditorTags.stepClipImage(position)),
    ) {
        frame?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        Box(
            Modifier
                .align(Alignment.Center)
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                .testTag(EditorTags.stepClipMark(position)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_clip_mark), contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
        }
        if (working) {
            PhotoWorking(Modifier.align(Alignment.TopEnd).padding(8.dp).testTag(EditorTags.stepPhotoWorking(position)))
        }
    }
}

/**
 * That a clip offered at the step at [position] is being converted, with what stops it: an indicator, a short text
 * the screen reader is told as it appears, and a 48 dp Cancel that names the step.
 */
@Composable
internal fun ClipConverting(position: Int, onCancel: () -> Unit) {
    val cancelSaid = stringResource(R.string.media_clip_cancel_action, position + 1)
    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, top = 4.dp).testTag(EditorTags.stepClipConverting(position)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
        Text(
            stringResource(R.string.media_clip_converting),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
        )
        TextButton(
            onClick = onCancel,
            modifier = Modifier
                .heightIn(min = 48.dp)
                .semantics { contentDescription = cancelSaid }
                .testTag(EditorTags.stepClipCancel(position)),
        ) {
            Text(stringResource(R.string.media_clip_cancel))
        }
    }
}

/** The first frame of the clip kept in [file], as the device's media framework reads it; null where none can be. */
internal fun firstFrame(file: File): ImageBitmap? = try {
    MediaMetadataRetriever().use { retriever ->
        retriever.setDataSource(file.path)
        retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.asImageBitmap()
    }
} catch (e: Exception) {
    null
} catch (e: OutOfMemoryError) {
    null
}

/** The shape of a clip's frame until it is read: the usual landscape clip's. */
private const val PLACEHOLDER_RATIO = 16f / 9f
