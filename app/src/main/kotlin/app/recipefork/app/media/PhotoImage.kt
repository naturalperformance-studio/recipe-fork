package app.recipefork.app.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * A photograph kept on the device, read off the main thread and decoded no larger than the width it is shown at.
 * It fills the width: a landscape picture is shown whole, a taller one cropped to a square about its centre. Until it
 * is read nothing is shown in its place, and when it cannot be read at all, [unavailable] — by default nothing.
 * [description] is its text alternative; [modifier] is the picture's, once there is one.
 */
@Composable
fun PhotoImage(file: File, description: String, modifier: Modifier = Modifier, unavailable: @Composable () -> Unit = {}) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val widthPx = with(LocalDensity.current) { maxWidth.roundToPx() }.coerceAtLeast(1)
        // Null until read; then the picture, or a holder of nothing once it could not be read.
        val read by produceState<Decoded?>(null, file, widthPx) {
            value = Decoded(withContext(Dispatchers.IO) { decodeForDisplay(file, widthPx) })
        }
        val decoded = read ?: return@BoxWithConstraints
        if (decoded.image == null) unavailable()
        decoded.image?.let {
            Image(
                bitmap = it,
                contentDescription = description,
                contentScale = ContentScale.Crop,
                modifier = modifier.fillMaxWidth().aspectRatio(maxOf(it.width.toFloat() / it.height, 1f)),
            )
        }
    }
}

private class Decoded(val image: ImageBitmap?)

/** [file] decoded down by a power of two to no less than [width] pixels wide where it is wider; null if unreadable. */
fun decodeForDisplay(file: File, width: Int): ImageBitmap? = try {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
        null
    } else {
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= width) sample *= 2
        BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
    }
} catch (e: OutOfMemoryError) {
    null
} catch (e: RuntimeException) {
    null
}

/**
 * [file] decoded to fill a [width] by [height] frame, cropped about its centre: read down by a power of two, never
 * whole, then scaled until it just covers the frame, and cut to it. A photograph too small to cover the frame is kept
 * as it is. Null if unreadable.
 */
fun decodeFilling(file: File, width: Int, height: Int): ImageBitmap? = try {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
        null
    } else {
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= width && bounds.outHeight / (sample * 2) >= height) sample *= 2
        BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?.let { croppedToFill(it, width, height) }
            ?.asImageBitmap()
    }
} catch (e: OutOfMemoryError) {
    null
} catch (e: RuntimeException) {
    null
}

/** [bitmap] scaled down until it just covers [width] by [height] and cut to it about its centre; itself if smaller. */
private fun croppedToFill(bitmap: Bitmap, width: Int, height: Int): Bitmap {
    val scale = maxOf(width.toDouble() / bitmap.width, height.toDouble() / bitmap.height)
    if (scale >= 1.0) return bitmap
    val scaledWidth = Math.round(bitmap.width * scale).toInt().coerceAtLeast(width)
    val scaledHeight = Math.round(bitmap.height * scale).toInt().coerceAtLeast(height)
    val scaled = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
    if (scaled !== bitmap) bitmap.recycle()
    val cropped = Bitmap.createBitmap(scaled, (scaledWidth - width) / 2, (scaledHeight - height) / 2, width, height)
    if (cropped !== scaled) scaled.recycle()
    return cropped
}
