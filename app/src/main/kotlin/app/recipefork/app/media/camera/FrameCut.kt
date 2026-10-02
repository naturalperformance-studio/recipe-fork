package app.recipefork.app.media.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Matrix
import android.graphics.Rect
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * Cuts a picture the camera took to the frame its viewfinder showed: [jpeg] as the sensor wrote it, [cropRect] what
 * the camera says the viewfinder showed of it, [rotationDegrees] how far it turns to stand as the phone was held. The
 * result is a JPEG of [CameraFrame]'s frame, upright, holding nothing but pixels; processing then shrinks and
 * re-encodes it like any photograph.
 */
object FrameCut {
    private const val QUALITY = 95

    fun cut(jpeg: ByteArray, cropRect: PixelRect, rotationDegrees: Int): ByteArray {
        @Suppress("DEPRECATION")
        val decoder = BitmapRegionDecoder.newInstance(jpeg, 0, jpeg.size, false)
            ?: throw IOException("the picture cannot be read")
        try {
            val whole = PixelRect(0, 0, decoder.width, decoder.height)
            val shown = cropRect.intersect(whole).takeIf { it.width > 0 && it.height > 0 } ?: whole
            val frame = CameraFrame.crop(shown, rotationDegrees)
            val options = BitmapFactory.Options().apply { inSampleSize = CameraFrame.sampleSize(frame.width, frame.height) }
            val region = decoder.decodeRegion(Rect(frame.left, frame.top, frame.right, frame.bottom), options)
                ?: throw IOException("the picture cannot be read")
            val upright = if (Math.floorMod(rotationDegrees, 360) == 0) {
                region
            } else {
                val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                Bitmap.createBitmap(region, 0, 0, region.width, region.height, matrix, true).also {
                    if (it !== region) region.recycle()
                }
            }
            val out = ByteArrayOutputStream()
            upright.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
            upright.recycle()
            return out.toByteArray()
        } finally {
            decoder.recycle()
        }
    }
}
