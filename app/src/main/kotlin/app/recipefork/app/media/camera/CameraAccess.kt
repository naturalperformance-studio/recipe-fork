package app.recipefork.app.media.camera

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.lifecycle.LifecycleOwner

/**
 * The phone's camera, as the camera screen uses it: opened, showing its viewfinder, taking a picture, closed. The
 * application's own is [CameraXAccess]; a test hands the screen one of its own through [LocalCameraAccess].
 */
fun interface CameraAccess {
    /**
     * Opens the back camera for as long as [owner] is started, or gives null where the phone has none. Throws where
     * the camera is there but cannot be opened.
     */
    suspend fun open(context: Context, owner: LifecycleOwner): OpenCamera?
}

/** A camera opened by [CameraAccess.open]; [close] releases it, and nothing else is called after it. */
interface OpenCamera {
    /** The live picture of [CameraFrame]'s frame, filling [modifier]'s bounds, which are that frame's shape. */
    @Composable
    fun Viewfinder(modifier: Modifier)

    /** Takes a picture: exactly what the viewfinder shows, upright, as a JPEG. Throws where none could be taken. */
    suspend fun takePicture(): ByteArray

    fun close()
}

/** The camera the camera screen opens. */
val LocalCameraAccess = staticCompositionLocalOf<CameraAccess> { CameraXAccess }
