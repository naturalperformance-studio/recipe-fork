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
 * [file] decoded as a thumbnail whose shorter side is [size] pixels — enough for a [size]-pixel square cropped about
 * its centre: read down by a power of two, never whole, then scaled to that size. A smaller photograph is kept as it
 * is. Null if unreadable.
 */
fun decodeThumbnail(file: File, size: Int): ImageBitmap? = try {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    val shorter = minOf(bounds.outWidth, bounds.outHeight)
    if (shorter <= 0) {
        null
    } else {
        var sample = 1
        while (shorter / (sample * 2) >= size) sample *= 2
        BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?.let { scaledToShorterSide(it, size) }
            ?.asImageBitmap()
    }
} catch (e: OutOfMemoryError) {
    null
} catch (e: RuntimeException) {
    null
}

/** [bitmap] scaled down so that its shorter side is [size] pixels, or itself where it is no larger. */
private fun scaledToShorterSide(bitmap: Bitmap, size: Int): Bitmap {
    val shorter = minOf(bitmap.width, bitmap.height)
    if (shorter <= size) return bitmap
    val width = (bitmap.width.toLong() * size / shorter).toInt().coerceAtLeast(1)
    val height = (bitmap.height.toLong() * size / shorter).toInt().coerceAtLeast(1)
    val scaled = Bitmap.createScaledBitmap(bitmap, width, height, true)
    if (scaled !== bitmap) bitmap.recycle()
    return scaled
}
