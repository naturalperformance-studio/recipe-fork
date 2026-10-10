package app.recipefork.app.editor

import android.media.MediaMetadataRetriever
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.ui.draw.clipToBounds
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
import app.recipefork.app.card.StepVideo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * A step's clip in the editor, watched in place (MEDIA-004): the card's own box, player and round control
 * ([StepVideo]), showing the clip's first frame — read off the main thread — until the author plays it. It never starts
 * by itself here; a tap on the control or on the clip plays it, muted and looping, and another pauses it. It holds a
 * player only while it is on screen, as the card's clip does. The screen reader calls it the author's video of the
 * step, as the card does (A11Y-001), and never describes it; [working] while another item offered is being prepared.
 * A clip the device cannot play is shown by its first frame alone, with a play mark so that it is not taken for a
 * photograph.
 */
@Composable
internal fun StepClipFrame(position: Int, file: File, working: Boolean = false) {
    val frame by produceState<ImageBitmap?>(null, file) { value = withContext(Dispatchers.IO) { firstFrame(file) } }
    val said = stringResource(R.string.media_step_clip_description, position + 1)
    val indicator: @Composable BoxScope.() -> Unit = {
        if (working) PhotoWorking(Modifier.align(Alignment.TopEnd).padding(8.dp).testTag(EditorTags.stepPhotoWorking(position)))
    }
    StepVideo(
        file,
        description = said,
        playAction = stringResource(R.string.card_video_play),
        pauseAction = stringResource(R.string.card_video_pause),
        unavailable = { StillClipFrame(position, frame, said, indicator) },
        videoTag = EditorTags.stepClipImage(position),
        controlTag = EditorTags.stepClipControl(position),
        modifier = Modifier.padding(vertical = 4.dp).background(MaterialTheme.colorScheme.surfaceVariant),
        autoplay = false,
        poster = frame,
        overlay = indicator,
    )
}

/** A clip that cannot be played, shown by its first [frame] — or a plain frame — with a play mark over it. */
@Composable
private fun StillClipFrame(position: Int, frame: ImageBitmap?, said: String, overlay: @Composable BoxScope.() -> Unit) {
    val ratio = frame?.let { maxOf(it.width.toFloat() / it.height, 1f) } ?: PLACEHOLDER_RATIO
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(ratio)
            .clipToBounds()
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
        overlay()
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
        Modifier.fillMaxWidth().testTag(EditorTags.stepClipConverting(position)),
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
