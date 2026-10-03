package app.recipefork.app.media.camera

import android.content.Context
import android.view.OrientationEventListener
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * The back camera through CameraX (decisions/20261002-in-app-camera-with-camerax.md). The viewfinder shows the whole
 * frame of the sensor and the picture is that whole frame; the crop is chosen after the shot. The capture follows how
 * the phone is held, so that a picture taken sideways or upside down comes out the right way up.
 */
object CameraXAccess : CameraAccess {
    override suspend fun open(context: Context, owner: LifecycleOwner): OpenCamera? {
        val provider = ProcessCameraProvider.awaitInstance(context.applicationContext)
        val selector = CameraSelector.DEFAULT_BACK_CAMERA
        if (!provider.hasCamera(selector)) return null
        val rotation = ContextCompat.getDisplayOrDefault(context).rotation
        // Both streams in the sensor's own 4:3, so that the viewfinder shows the frame the picture is.
        val resolution = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .build()
        val preview = Preview.Builder().setResolutionSelector(resolution).setTargetRotation(rotation).build()
        val capture = ImageCapture.Builder()
            .setResolutionSelector(resolution)
            .setTargetRotation(rotation)
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
        provider.bindToLifecycle(owner, selector, preview, capture)
        return CameraXCamera(context.applicationContext, provider, preview, capture)
    }
}

/** A back camera opened through CameraX; [turned] is how it hears of the phone being turned. */
internal class CameraXCamera(
    context: Context,
    private val provider: ProcessCameraProvider,
    private val preview: Preview,
    private val capture: ImageCapture,
) : OpenCamera {
    private val turns = object : OrientationEventListener(context) {
        override fun onOrientationChanged(orientation: Int) = turned(orientation)
    }.also { it.enable() }

    /** The phone is now turned [degrees] clockwise from its natural way up: the next picture is taken that way. */
    fun turned(degrees: Int) {
        DeviceTurn.targetRotation(degrees)?.let { capture.targetRotation = it }
    }

    @Composable
    override fun Viewfinder(modifier: Modifier) {
        AndroidView(
            factory = { context ->
                PreviewView(context).apply {
                    scaleType = PreviewView.ScaleType.FIT_CENTER
                    preview.surfaceProvider = surfaceProvider
                }
            },
            modifier = modifier,
        )
    }

    override suspend fun takePicture(): Shot = suspendCancellableCoroutine { continuation ->
        capture.takePicture(
            Dispatchers.IO.asExecutor(),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val result = runCatching {
                        val buffer = image.planes[0].buffer
                        Shot(ByteArray(buffer.remaining()).also { buffer.get(it) }, image.imageInfo.rotationDegrees)
                    }
                    image.close()
                    if (continuation.isActive) {
                        result.fold({ continuation.resume(it) }, { continuation.resumeWithException(it) })
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    if (continuation.isActive) continuation.resumeWithException(exception)
                }
            },
        )
    }

    override fun close() {
        turns.disable()
        preview.surfaceProvider = null
        provider.unbind(preview, capture)
    }
}
