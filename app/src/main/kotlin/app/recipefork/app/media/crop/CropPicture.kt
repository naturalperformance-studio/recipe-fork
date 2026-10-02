package app.recipefork.app.media.crop

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Matrix
import android.graphics.Rect
import app.recipefork.app.media.Orientation
import app.recipefork.app.media.PixelSize
import app.recipefork.app.media.exifOrientation
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

/**
 * A picture handed over to be cropped before it is attached: [open] reads it as its file holds it, as many times as
 * needed; [orientation] is how it turns to stand upright — given by the camera that took it, or, null, read from the
 * file's own EXIF, as for a picture from the gallery. [retake] says whether it came from the camera, which can take
 * another.
 */
class PhotoToCrop(val open: () -> InputStream, val orientation: Orientation?, val retake: Boolean)

/**
 * A picture opened for cropping: its file's size [raw], the [orientation] that stands it upright, the [upright] size
 * that gives, and [preview] — the picture upright, read shrunk so that memory stays bounded however large it is.
 */
class CropPicture(val raw: PixelSize, val orientation: Orientation, val preview: Bitmap) {
    val upright: PixelSize get() = CropGeometry.upright(raw, orientation)

    companion object {
        /** The longer side a preview keeps at least, where the picture is that large. */
        const val PREVIEW_LONGEST = 1600

        /** Opens [photo] for cropping, or null where it cannot be read as a picture. */
        fun open(photo: PhotoToCrop): CropPicture? = try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            photo.open().use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                null
            } else {
                val raw = PixelSize(bounds.outWidth, bounds.outHeight)
                val orientation = photo.orientation ?: exifOrientation(photo.open)
                val options = BitmapFactory.Options().apply {
                    inSampleSize = CropGeometry.sampleSize(raw.width, raw.height, PREVIEW_LONGEST)
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                photo.open().use { BitmapFactory.decodeStream(it, null, options) }
                    ?.let { CropPicture(raw, orientation, turned(it, orientation)) }
            }
        } catch (e: IOException) {
            null
        } catch (e: RuntimeException) {
            null
        } catch (e: OutOfMemoryError) {
            null
        }

        /**
         * The part [rect] — in the pixels of the picture standing upright — of [photo], whose file is [raw] and turns by
         * [orientation]: read from the file at full sharpness (shrunk only by as much as processing would shrink it
         * anyway), turned upright, and written as a JPEG holding nothing but pixels. Throws [IOException] where it
         * cannot be read.
         */
        fun cut(photo: PhotoToCrop, raw: PixelSize, orientation: Orientation, rect: PixelRect): ByteArray {
            val region = CropGeometry.toRaw(rect, raw, orientation)
            val options = BitmapFactory.Options().apply {
                inSampleSize = CropGeometry.sampleSize(region.width, region.height)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bytes = photo.open().use { it.readBytes() }
            val part = regionOf(bytes, region, options) ?: throw IOException("the picture cannot be read")
            val upright = turned(part, orientation)
            val out = ByteArrayOutputStream()
            upright.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
            upright.recycle()
            return out.toByteArray()
        }

        /**
         * [region] of the picture [bytes] hold, read at [options]' shrinking: through a region decoder, or — for a
         * format it does not read — the whole picture read at that shrinking and the region cut out of it.
         */
        private fun regionOf(bytes: ByteArray, region: PixelRect, options: BitmapFactory.Options): Bitmap? {
            val decoder = try {
                @Suppress("DEPRECATION")
                BitmapRegionDecoder.newInstance(bytes, 0, bytes.size, false)
            } catch (e: IOException) {
                null
            }
            if (decoder != null) {
                try {
                    return decoder.decodeRegion(Rect(region.left, region.top, region.right, region.bottom), options)
                } finally {
                    decoder.recycle()
                }
            }
            val whole = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
            val sample = options.inSampleSize.coerceAtLeast(1)
            val left = (region.left / sample).coerceIn(0, whole.width - 1)
            val top = (region.top / sample).coerceIn(0, whole.height - 1)
            val width = (region.width / sample).coerceIn(1, whole.width - left)
            val height = (region.height / sample).coerceIn(1, whole.height - top)
            return Bitmap.createBitmap(whole, left, top, width, height).also { if (it !== whole) whole.recycle() }
        }

        private const val QUALITY = 95

        /** [bitmap] mirrored and turned as [orientation] says — the same turn processing applies — recycled if replaced. */
        private fun turned(bitmap: Bitmap, orientation: Orientation): Bitmap {
            if (Math.floorMod(orientation.degrees, 360) == 0 && !orientation.mirrored) return bitmap
            val matrix = Matrix().apply {
                if (orientation.mirrored) postScale(-1f, 1f)
                postRotate(orientation.degrees.toFloat())
            }
            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also {
                if (it !== bitmap) bitmap.recycle()
            }
        }
    }
}
