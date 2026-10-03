package app.recipefork.app.media

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import app.recipefork.app.media.camera.CameraPermissionExplanation
import app.recipefork.app.media.camera.CameraScreen
import app.recipefork.app.media.camera.LocalCameraAccess
import app.recipefork.app.media.crop.CropScreen
import app.recipefork.app.media.crop.PhotoToCrop
import java.io.ByteArrayInputStream
import java.io.FileNotFoundException
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * A picture the author handed over — taken with the camera or picked from the gallery — to be read by [open] as many
 * times as its processing needs; [release] is called once it has been read, and deletes what was held only for it.
 */
class PhotoOffer(val open: () -> InputStream, val release: () -> Unit)

/**
 * The two ways a photograph reaches the application (FLOW-026): the application's own camera, which asks for the
 * camera permission the first time it is opened and never earlier, and the system photo picker, which needs no
 * permission and hands over only the picture chosen. Either way the picture is cropped to the recipe's 4:3 frame
 * before it is handed over.
 */
class PhotoSources internal constructor(private val take: () -> Unit, private val choose: () -> Unit) {
    /**
     * Opens the camera screen; without the permission, asks for it first, and, refused, explains why the camera needs
     * it and offers the application's settings.
     */
    fun takePhoto() = take()

    /** Opens the system photo picker, offering images only; a phone with none says so through `onUnavailable`. */
    fun choosePhoto() = choose()

    companion object {
        /** Where the system camera of earlier versions wrote its pictures; nothing writes there any more. */
        internal fun cameraDirectory(context: Context) = legacyCameraDirectory(context)

        /** Deletes what the system camera of earlier versions may have left behind. Called as the application starts. */
        fun clearLeftovers(context: Context) {
            cameraDirectory(context).deleteRecursively()
        }
    }
}

/** What the camera of one place shows over the editor, kept across the activity being recreated. */
private enum class CameraStage { CLOSED, OPEN, EXPLAINING, CROPPING }

/**
 * The pictures waiting in a crop, by the place they are for: held for the process rather than the activity, so that
 * turning the phone in the crop — which recreates the activity — does not lose the shot. A picture is let go once its
 * crop is done, cancelled or retaken.
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
 * The camera and the photo picker of one place a photograph is attached to — [key] names it, so that two places on
 * one screen keep their own apart. A picture taken or picked goes through the crop first (FLOW-026); [onPhoto]
 * receives every picture handed over from it; a camera, a picker or a crop closed hands over nothing.
 */
@Composable
fun rememberPhotoSources(key: String, onPhoto: (PhotoOffer) -> Unit, onUnavailable: () -> Unit): PhotoSources {
    val context = LocalContext.current
    val resolver = context.applicationContext.contentResolver
    val camera = LocalCameraAccess.current
    val deliver by rememberUpdatedState(onPhoto)
    val unavailable by rememberUpdatedState(onUnavailable)
    val stage = rememberSaveable(key) { mutableStateOf(CameraStage.CLOSED) }
    // Refused once already: the platform then tells whether it would still ask.
    val refused = rememberSaveable(key) { mutableStateOf(false) }
    // The platform's request for the camera is up: a further "Take a photo" waits for its answer, not a second one.
    val asking = rememberSaveable(key) { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        asking.value = false
        refused.value = !granted
        stage.value = if (granted) CameraStage.OPEN else CameraStage.EXPLAINING
    }
    fun crop(photo: PhotoToCrop) {
        PendingCrops[key] = photo
        stage.value = CameraStage.CROPPING
    }
    fun closeCrop() {
        PendingCrops.remove(key)
        stage.value = CameraStage.CLOSED
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val open = { resolver.openInputStream(uri) ?: throw FileNotFoundException(uri.toString()) }
            crop(PhotoToCrop(open, orientation = null, retake = false))
        }
    }
    when (stage.value) {
        CameraStage.OPEN -> CameraScreen(
            camera,
            onShot = { shot ->
                crop(PhotoToCrop({ ByteArrayInputStream(shot.jpeg) }, Orientation(Math.floorMod(shot.rotationDegrees, 360), false), retake = true))
            },
            onClose = { stage.value = CameraStage.CLOSED },
        )
        CameraStage.CROPPING -> {
            val photo = PendingCrops[key]
            if (photo == null) {
                // The process was ended while the crop was up: the picture went with it.
                SideEffect { stage.value = CameraStage.CLOSED }
            } else {
                CropScreen(
                    photo,
                    onDone = { offer ->
                        closeCrop()
                        deliver(offer)
                    },
                    onRetake = {
                        PendingCrops.remove(key)
                        stage.value = CameraStage.OPEN
                    },
                    onCancel = ::closeCrop,
                    onUnreadable = {
                        closeCrop()
                        // Handed over as it is, so that processing refuses it and says why (MEDIA-008).
                        deliver(PhotoOffer(open = photo.open, release = {}))
                    },
                )
            }
        }
        CameraStage.EXPLAINING -> CameraPermissionExplanation(onDone = { stage.value = CameraStage.CLOSED })
        CameraStage.CLOSED -> Unit
    }
    return remember(context, permission, picker, stage, refused, asking) {
        PhotoSources(
            take = {
                when {
                    asking.value -> Unit
                    ContextCompat.checkSelfPermission(context, CAMERA) == PackageManager.PERMISSION_GRANTED ->
                        stage.value = CameraStage.OPEN
                    // Refused for good: the platform would not ask again, so the explanation comes straight away.
                    refused.value && context.activity()?.shouldShowRequestPermissionRationale(CAMERA) == false ->
                        stage.value = CameraStage.EXPLAINING
                    else -> try {
                        asking.value = true
                        permission.launch(CAMERA)
                    } catch (e: ActivityNotFoundException) {
                        asking.value = false
                        stage.value = CameraStage.EXPLAINING
                    }
                }
            },
            choose = {
                try {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                } catch (e: ActivityNotFoundException) {
                    unavailable()
                }
            },
        )
    }
}

private const val CAMERA = Manifest.permission.CAMERA

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}
