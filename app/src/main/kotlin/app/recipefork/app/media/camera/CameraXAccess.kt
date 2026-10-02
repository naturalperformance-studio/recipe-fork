package app.recipefork.app.media.camera

import android.content.Context
import android.util.Rational
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ViewPort
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
 * The back camera through CameraX (decisions/20261002-in-app-camera-with-camerax.md). The viewfinder and the picture
 * share one [ViewPort] of [CameraFrame]'s shape, so both are given the same crop of the sensor; the picture is then
 * cut to that crop by [FrameCut].
 */
object CameraXAccess : CameraAccess {
    override suspend fun open(context: Context, owner: LifecycleOwner): OpenCamera? {
        val provider = ProcessCameraProvider.awaitInstance(context.applicationContext)
        val selector = CameraSelector.DEFAULT_BACK_CAMERA
        if (!provider.hasCamera(selector)) return null
        val rotation = ContextCompat.getDisplayOrDefault(context).rotation
        // Both streams in the sensor's own 4:3, so that the one crop shows the same scene in each.
        val resolution = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .build()
        val preview = Preview.Builder().setResolutionSelector(resolution).setTargetRotation(rotation).build()
        val capture = ImageCapture.Builder()
            .setResolutionSelector(resolution)
            .setTargetRotation(rotation)
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
        // The frame as the screen stands: 4 wide, 3 high.
        val viewPort = ViewPort.Builder(Rational(CameraFrame.WIDTH, CameraFrame.HEIGHT), rotation)
            .setScaleType(ViewPort.FILL_CENTER)
            .build()
        val group = UseCaseGroup.Builder().setViewPort(viewPort).addUseCase(preview).addUseCase(capture).build()
        provider.bindToLifecycle(owner, selector, group)
        return CameraXCamera(provider, preview, capture)
    }
}

private class CameraXCamera(
    private val provider: ProcessCameraProvider,
    private val preview: Preview,
    private val capture: ImageCapture,
) : OpenCamera {
    @Composable
    override fun Viewfinder(modifier: Modifier) {
        AndroidView(
            factory = { context ->
                PreviewView(context).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    preview.surfaceProvider = surfaceProvider
                }
            },
            modifier = modifier,
        )
    }

    override suspend fun takePicture(): ByteArray = suspendCancellableCoroutine { continuation ->
        capture.takePicture(
            Dispatchers.IO.asExecutor(),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val result = runCatching {
                        val buffer = image.planes[0].buffer
                        val jpeg = ByteArray(buffer.remaining()).also { buffer.get(it) }
                        val crop = image.cropRect
                        FrameCut.cut(jpeg, PixelRect(crop.left, crop.top, crop.right, crop.bottom), image.imageInfo.rotationDegrees)
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
        preview.surfaceProvider = null
        provider.unbind(preview, capture)
    }
}
