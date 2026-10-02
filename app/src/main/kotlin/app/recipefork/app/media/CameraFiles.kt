package app.recipefork.app.media

import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * The provider that hands the system camera the one file it writes a photograph into (FLOW-026): a provider of its
 * own, sharing nothing but that file's directory, apart from the one the exports are shared by.
 */
class CameraFileProvider : FileProvider()

/**
 * A picture the author handed over — taken with the camera or picked from the gallery — to be read by [open] as many
 * times as its processing needs; [release] is called once it has been read, and deletes what was held only for it.
 */
class PhotoOffer(val open: () -> java.io.InputStream, val release: () -> Unit)

/**
 * The two ways a photograph reaches the application, neither asking for a permission at run time (FLOW-026): the
 * system camera, which writes the picture into a file this application shares with it for that one picture, and the
 * system photo picker, which hands over only the picture chosen.
 */
class PhotoSources internal constructor(
    private val context: Context,
    private val pending: PendingCameraFile,
    private val camera: ManagedActivityResultLauncher<Uri, Boolean>,
    private val picker: ManagedActivityResultLauncher<PickVisualMediaRequest, Uri?>,
    private val onUnavailable: () -> Unit,
) {
    /** Where the camera's file is remembered while the camera is in front, across the activity being recreated. */
    fun interface PendingCameraFile {
        fun set(path: String?)
    }

    /** Opens the system camera; a device with no camera application says so through `onUnavailable`. */
    fun takePhoto() {
        val directory = cameraDirectory(context)
        // One picture is taken at a time; whatever an earlier, interrupted one left is no one's.
        directory.listFiles()?.forEach { it.delete() }
        val file = File(directory, UUID.randomUUID().toString() + EXTENSION)
        try {
            if (!directory.isDirectory && !directory.mkdirs()) throw IOException("no camera directory")
            file.createNewFile()
        } catch (e: IOException) {
            return onUnavailable()
        }
        pending.set(file.path)
        try {
            camera.launch(FileProvider.getUriForFile(context, authority(context), file))
        } catch (e: ActivityNotFoundException) {
            pending.set(null)
            file.delete()
            onUnavailable()
        }
    }

    /** Opens the system photo picker, offering images only. */
    fun choosePhoto() {
        try {
            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        } catch (e: ActivityNotFoundException) {
            onUnavailable()
        }
    }

    companion object {
        private const val DIRECTORY = "camera"
        private const val EXTENSION = ".jpg"
        private const val AUTHORITY_SUFFIX = ".camera"

        /** The directory the camera writes into, which [CameraFileProvider] shares. */
        fun cameraDirectory(context: Context) = File(context.cacheDir, DIRECTORY)

        /** The authority of [CameraFileProvider]. */
        fun authority(context: Context) = context.packageName + AUTHORITY_SUFFIX
    }
}
