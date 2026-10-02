package app.recipefork.app.media

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import java.io.File
import java.io.FileNotFoundException

/**
 * The camera and the photo picker of one place a photograph is attached to — [key] names it, so that two places on
 * one screen keep their own pictures apart. [onPhoto] receives every picture handed over; a camera cancelled or a
 * picker closed hands over nothing.
 */
@Composable
fun rememberPhotoSources(key: String, onPhoto: (PhotoOffer) -> Unit, onUnavailable: () -> Unit): PhotoSources {
    val context = LocalContext.current
    val resolver = context.applicationContext.contentResolver
    val deliver by rememberUpdatedState(onPhoto)
    val unavailable by rememberUpdatedState(onUnavailable)
    // The camera's file outlives the activity being recreated while the camera is in front.
    val pending = rememberSaveable(key) { mutableStateOf<String?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        val path = pending.value ?: return@rememberLauncherForActivityResult
        pending.value = null
        val file = File(path)
        if (taken && file.length() > 0) {
            deliver(PhotoOffer(open = { file.inputStream() }, release = { file.delete() }))
        } else {
            file.delete()
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            deliver(PhotoOffer(open = { resolver.openInputStream(uri) ?: throw FileNotFoundException(uri.toString()) }, release = {}))
        }
    }
    return remember(context, pending, camera, picker) {
        PhotoSources(context, { pending.value = it }, camera, picker) { unavailable() }
    }
}
