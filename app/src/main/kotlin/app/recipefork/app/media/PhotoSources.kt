package app.recipefork.app.media

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.recipefork.app.R
import app.recipefork.app.media.crop.CropScreen
import app.recipefork.app.media.crop.PhotoToCrop
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A picture the author handed over — taken with the camera or picked from the gallery — to be read by [open] as many
 * times as its processing needs; [release] is called once it has been read, and deletes what was held only for it.
 */
class PhotoOffer(val open: () -> InputStream, val release: () -> Unit)

/**
 * The two ways a photograph reaches the application (FLOW-026): the phone's own camera application, which this one
 * hands a file of its own to write the shot into and which needs no permission of this one, and the system photo
 * picker, which needs none either and hands over only the picture chosen. Either way the picture is cropped to the
 * recipe's 4:3 frame before it is handed over.
 */
class PhotoSources internal constructor(private val take: () -> Unit, private val choose: () -> Unit) {
    /**
     * Opens the phone's camera application; a phone with none, or one that cannot open it, says so and offers the
     * gallery instead.
     */
    fun takePhoto() = take()

    /** Opens the system photo picker, offering images only; a phone with none says so through `onUnavailable`. */
    fun choosePhoto() = choose()

    companion object {
        /**
         * Deletes the shots a process ended between the camera and the crop left a day ago or more, and whatever the
         * camera of earlier versions left behind. Called as the application starts.
         */
        fun clearLeftovers(context: Context) = Captures.clearLeftovers(context)
    }
}

/** What the photo sources of one place show over the editor, kept across the activity being recreated. */
private enum class SourceStage { CLOSED, CROPPING, NO_CAMERA, CAMERA_FAILED }

/**
 * The pictures from the gallery waiting in a crop, by the place they are for: held for the process rather than the
 * activity, so that turning the phone in the crop — which recreates the activity — does not lose them. A picture is
 * let go once its crop is done or cancelled. A shot from the camera needs no holding: its file is on disk.
 */
private object PendingCrops {
    private val held = ConcurrentHashMap<String, PhotoToCrop>()

    operator fun get(key: String): PhotoToCrop? = held[key]

    operator fun set(key: String, photo: PhotoToCrop) {
        held[key] = photo
    }

    fun remove(key: String) {
        held.remove(key)
    }
}

/**
 * Asks the camera application for one picture written into the content address given — that address alone granted to
 * it, to write and to read back what it wrote, and no other file of this application. The platform grants
 * EXTRA_OUTPUT of ACTION_IMAGE_CAPTURE the same way by itself (Intent.migrateExtraStreamToClipData) where no clip is
 * set; set here, the grant does not hang on that.
 */
internal class TakeOnePicture : ActivityResultContracts.TakePicture() {
    override fun createIntent(context: Context, input: Uri): Intent = super.createIntent(context, input).apply {
        clipData = ClipData.newRawUri(null, input)
        addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

/** A shot the camera application wrote into [file], to crop: turned upright by its own EXIF, and retaken at will. */
private fun shotToCrop(file: File) = PhotoToCrop({ file.inputStream() }, orientation = null, retake = true)

/**
 * The camera and the photo picker of one place a photograph is attached to — [key] names it, so that two places on
 * one screen keep their own apart. A picture taken or picked goes through the crop first (FLOW-026); [onPhoto]
 * receives every picture handed over from it; a camera, a picker or a crop closed hands over nothing. The shot's file
 * is deleted once its crop is done with — the cut read, cancelled or retaken — or as soon as the camera comes back
 * without one.
 *
 * A place that takes animations — a step, never the cover — is given [animations]: a picture picked from the gallery
 * that it reads as an animated GIF or WebP skips the crop and is handed to [onAnimation] as a clip (scenario 3); the
 * reading runs off the main thread, and tells a JPEG or a PNG still from its first bytes. A shot from the camera is
 * never one.
 */
@Composable
fun rememberPhotoSources(
    key: String,
    onPhoto: (PhotoOffer) -> Unit,
    onUnavailable: () -> Unit,
    animations: AnimatedImages? = null,
    onAnimation: (ClipOffer) -> Unit = {},
): PhotoSources {
    val context = LocalContext.current
    val resolver = context.applicationContext.contentResolver
    val deliver by rememberUpdatedState(onPhoto)
    val unavailable by rememberUpdatedState(onUnavailable)
    val animated by rememberUpdatedState(animations)
    val deliverAnimation by rememberUpdatedState(onAnimation)
    val reading = rememberCoroutineScope()
    val stage = rememberSaveable(key) { mutableStateOf(SourceStage.CLOSED) }
    // The file the camera application writes the current shot into, kept across the process being ended meanwhile.
    val shotPath = rememberSaveable(key) { mutableStateOf<String?>(null) }

    fun dropShot() {
        shotPath.value?.let { File(it).delete() }
        shotPath.value = null
    }
    fun closeCrop() {
        PendingCrops.remove(key)
        stage.value = SourceStage.CLOSED
    }
    val camera = rememberLauncherForActivityResult(TakeOnePicture()) { saved ->
        val file = shotPath.value?.let(::File)
        if (saved && file != null && file.length() > 0) {
            stage.value = SourceStage.CROPPING
        } else {
            dropShot()
        }
    }
    fun crop(uri: Uri) {
        val open = { resolver.openInputStream(uri) ?: throw FileNotFoundException(uri.toString()) }
        PendingCrops[key] = PhotoToCrop(open, orientation = null, retake = false)
        stage.value = SourceStage.CROPPING
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        dropShot()
        val images = animated
        if (images == null) {
            crop(uri)
            return@rememberLauncherForActivityResult
        }
        val done = PhotoWork.begin()
        // Started whatever happens to the scope meanwhile, so that what it counts in is always counted out.
        reading.launch(start = CoroutineStart.ATOMIC) {
            try {
                if (withContext(Dispatchers.IO) { images.isAnimatedOrStill(uri) }) {
                    deliverAnimation(ClipOffer(uri, release = holdReading(context, uri)))
                } else {
                    crop(uri)
                }
            } finally {
                done()
            }
        }
    }
    fun shoot() {
        dropShot()
        val file = try {
            Captures.create(context)
        } catch (e: IOException) {
            stage.value = SourceStage.CAMERA_FAILED
            return
        }
        shotPath.value = file.path
        try {
            camera.launch(Captures.uri(context, file))
        } catch (e: ActivityNotFoundException) {
            dropShot()
            stage.value = SourceStage.NO_CAMERA
        } catch (e: RuntimeException) {
            dropShot()
            stage.value = SourceStage.CAMERA_FAILED
        }
    }
    fun pick() {
        try {
            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        } catch (e: ActivityNotFoundException) {
            unavailable()
        }
    }
    when (stage.value) {
        SourceStage.CROPPING -> {
            val shot = shotPath.value?.let(::File)
            val photo = remember(key, shot) { shot?.takeIf { it.isFile }?.let(::shotToCrop) } ?: PendingCrops[key]
            if (photo == null) {
                // The process was ended while a gallery picture was in the crop: the picture went with it.
                SideEffect { stage.value = SourceStage.CLOSED }
            } else {
                CropScreen(
                    photo,
                    onDone = { offer ->
                        closeCrop()
                        shotPath.value = null
                        // The shot's file goes once the cut has been read from it, or once the offer is let go unread.
                        val cutRead = { offer.release(); shot?.delete(); Unit }
                        deliver(PhotoOffer(open = { offer.open().also { shot?.delete() } }, release = cutRead))
                    },
                    onRetake = {
                        closeCrop()
                        shoot()
                    },
                    onCancel = {
                        closeCrop()
                        dropShot()
                    },
                    onUnreadable = {
                        closeCrop()
                        shotPath.value = null
                        // Handed over as it is, so that processing refuses it and says why (MEDIA-008).
                        deliver(PhotoOffer(open = photo.open, release = { shot?.delete() }))
                    },
                )
            }
        }
        SourceStage.NO_CAMERA, SourceStage.CAMERA_FAILED -> CameraUnavailable(
            if (stage.value == SourceStage.NO_CAMERA) R.string.media_camera_none else R.string.media_camera_failed,
            onGallery = {
                stage.value = SourceStage.CLOSED
                pick()
            },
            onDismiss = { stage.value = SourceStage.CLOSED },
        )
        SourceStage.CLOSED -> Unit
    }
    return remember(context, camera, picker, stage, shotPath) { PhotoSources(take = ::shoot, choose = ::pick) }
}

/** Whether [source] is an animation; one that cannot be read as any is a still picture, for the crop to judge. */
private fun AnimatedImages.isAnimatedOrStill(source: Uri): Boolean = runCatching { isAnimated(source) }.getOrDefault(false)

/** Why no photo can be taken here — [text] says — with the gallery a tap away. */
@Composable
private fun CameraUnavailable(text: Int, onGallery: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(stringResource(text), Modifier.testTag(PhotoSourceTags.CAMERA_UNAVAILABLE_TEXT)) },
        confirmButton = {
            TextButton(onClick = onGallery, modifier = Modifier.heightIn(min = 48.dp).testTag(PhotoSourceTags.CAMERA_UNAVAILABLE_GALLERY)) {
                Text(stringResource(R.string.media_camera_gallery))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp).testTag(PhotoSourceTags.CAMERA_UNAVAILABLE_DISMISS)) {
                Text(stringResource(R.string.media_camera_dismiss))
            }
        },
        modifier = Modifier.testTag(PhotoSourceTags.CAMERA_UNAVAILABLE),
    )
}

object PhotoSourceTags {
    const val CAMERA_UNAVAILABLE = "camera-unavailable"
    const val CAMERA_UNAVAILABLE_TEXT = "camera-unavailable-text"
    const val CAMERA_UNAVAILABLE_GALLERY = "camera-unavailable-gallery"
    const val CAMERA_UNAVAILABLE_DISMISS = "camera-unavailable-dismiss"
}
