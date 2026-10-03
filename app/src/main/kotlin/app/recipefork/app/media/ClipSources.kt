package app.recipefork.app.media

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import app.recipefork.core.recipe.VideoLimits
import java.io.File
import java.io.IOException

/**
 * A clip the author handed over — recorded with the camera or picked from the gallery — to be read through [source];
 * [release] is called once it has been converted, refused or cancelled, and deletes what was held only for it.
 */
class ClipOffer(val source: Uri, val release: () -> Unit)

/**
 * The two ways a step's clip reaches the application (FLOW-026), as a photograph's do: the phone's own camera
 * application, recording into a file of this one's and stopped by it at the limit's duration (MEDIA-007), which
 * needs no permission of this one; and the system photo picker, offering videos only. Neither crops.
 */
class ClipSources internal constructor(private val record: () -> Unit, private val choose: () -> Unit) {
    /** Opens the phone's camera application to record one clip; a phone with none says so. */
    fun recordClip() = record()

    /** Opens the system photo picker, offering videos only; a phone with none says so. */
    fun chooseClip() = choose()
}

/** What the camera application gave back from recording: [returned] is the address it named, if it named one. */
internal class RecordedClip(val returned: Uri?)

/**
 * Asks the camera application for one clip written into the content address given, stopped at
 * [VideoLimits.MAX_DURATION] — that address alone granted to it, to write and to read back. Gives back null when it
 * was cancelled.
 */
internal class RecordOneClip : ActivityResultContract<Uri, RecordedClip?>() {
    override fun createIntent(context: Context, input: Uri): Intent = Intent(MediaStore.ACTION_VIDEO_CAPTURE)
        .putExtra(MediaStore.EXTRA_OUTPUT, input)
        .putExtra(MediaStore.EXTRA_DURATION_LIMIT, VideoLimits.MAX_DURATION.inWholeSeconds.toInt())
        .apply {
            clipData = ClipData.newRawUri(null, input)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

    override fun parseResult(resultCode: Int, intent: Intent?): RecordedClip? =
        if (resultCode == Activity.RESULT_OK) RecordedClip(intent?.data) else null
}

/**
 * The camera and the video picker of one place a clip is attached to — [key] names it, so that two places on one
 * screen keep their own apart. [onClip] receives every clip handed over; a camera or a picker closed hands over
 * nothing, and a camera that recorded nothing leaves no file. [onUnavailable] is told what could not be opened.
 */
@Composable
fun rememberClipSources(key: String, onClip: (ClipOffer) -> Unit, onUnavailable: (PhotoNotice) -> Unit): ClipSources {
    val context = LocalContext.current
    val deliver by rememberUpdatedState(onClip)
    val unavailable by rememberUpdatedState(onUnavailable)
    // The file the camera application records the current clip into, kept across the process being ended meanwhile.
    val clipPath = rememberSaveable(key) { mutableStateOf<String?>(null) }

    fun dropClip() {
        clipPath.value?.let { File(it).delete() }
        clipPath.value = null
    }
    val camera = rememberLauncherForActivityResult(RecordOneClip()) { recorded ->
        val file = clipPath.value?.let(::File)
        clipPath.value = null
        when {
            recorded == null -> file?.delete()
            file != null && file.length() > 0 -> deliver(ClipOffer(Uri.fromFile(file), release = { file.delete() }))
            recorded.returned != null -> {
                // A camera application that kept the clip where it chose, and said where. Should it not have granted
                // this application the reading of that address, reading it fails and the author is told the clip
                // could not be read (MEDIA-008); nothing is attached and nothing is lost.
                file?.delete()
                deliver(ClipOffer(recorded.returned, release = {}))
            }
            else -> file?.delete()
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) deliver(ClipOffer(uri, release = holdReading(context, uri)))
    }
    fun record() {
        dropClip()
        val file = try {
            Captures.createClip(context)
        } catch (e: IOException) {
            unavailable(PhotoNotice.CLIP_CAMERA_FAILED)
            return
        }
        clipPath.value = file.path
        try {
            camera.launch(Captures.uri(context, file))
        } catch (e: ActivityNotFoundException) {
            dropClip()
            unavailable(PhotoNotice.CLIP_NO_CAMERA)
        } catch (e: RuntimeException) {
            dropClip()
            unavailable(PhotoNotice.CLIP_CAMERA_FAILED)
        }
    }
    fun pick() {
        try {
            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
        } catch (e: ActivityNotFoundException) {
            unavailable(PhotoNotice.CLIP_UNAVAILABLE)
        }
    }
    return remember(context, camera, picker, clipPath) { ClipSources(record = ::record, choose = ::pick) }
}

/**
 * Keeps the reading of the picked [uri] past the activity that picked it, where the picker lets it be kept — the
 * documents picker some phones fall back on grants it to the activity alone, and turning the phone mid-conversion
 * would end it. Gives back what lets it go once the clip is done with. Where it cannot be kept, a reading cut short
 * is told as a clip that could not be read (MEDIA-008).
 */
private fun holdReading(context: Context, uri: Uri): () -> Unit {
    val resolver = context.applicationContext.contentResolver
    val held = try {
        resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        true
    } catch (e: RuntimeException) {
        // A picker that grants no lasting reading (SecurityException), or an address that takes none.
        false
    }
    return {
        if (held) {
            try {
                resolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: RuntimeException) {
                // Already let go.
            }
        }
    }
}
