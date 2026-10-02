package app.recipefork.app.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

/** Why a photograph was not attached (MEDIA-006, MEDIA-008). */
enum class PhotoRefusal {
    /** It could not be read as a picture: corrupt, unsupported, or not a picture at all (MEDIA-008). */
    UNREADABLE,

    /** Even at the lowest quality allowed it weighs more than its limit (MEDIA-006). */
    TOO_LARGE,

    /** It was processed but could not be kept on the device. */
    NOT_STORED,
}

/** A photograph refused for [refusal]; nothing of it is kept. */
class RefusedPhoto(val refusal: PhotoRefusal, cause: Throwable? = null) : Exception(refusal.name, cause)

/**
 * Re-encodes a photograph on the device (MEDIA-002, MEDIA-003): decoded the right way up, brought within the limits'
 * longer edge, and written as a plain JPEG at the highest quality that fits the limits' weight. The JPEG carries no
 * metadata at all: the orientation is applied to the pixels, so the picture needs none, and nothing of the place,
 * the device, the time or the person who took it survives.
 */
open class PhotoReencoder(private val limits: PhotoLimits = PhotoLimits()) {
    /**
     * The re-encoded bytes of the picture [open] reads; [open] is called once per pass over the original. Throws
     * [RefusedPhoto] when the picture cannot be read or does not fit.
     */
    open fun reencode(open: () -> InputStream): ByteArray {
        val original = bounds(open)
        val orientation = orientation(open)
        val sample = PhotoPlan.sampleSize(original, limits.maxEdge)
        val decoded = decode(open, sample)
        try {
            val target = PhotoPlan.scaled(PixelSize(decoded.width, decoded.height), limits.maxEdge)
            val upright = turned(decoded, target, orientation)
            try {
                return PhotoPlan.firstThatFits(limits.qualities, limits.maxBytes) { quality ->
                    ByteArrayOutputStream().also { upright.compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()
                } ?: throw RefusedPhoto(PhotoRefusal.TOO_LARGE)
            } finally {
                upright.recycle()
            }
        } finally {
            decoded.recycle()
        }
    }

    private fun bounds(open: () -> InputStream): PixelSize {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        reading { open().use { BitmapFactory.decodeStream(it, null, options) } }
        if (options.outWidth <= 0 || options.outHeight <= 0) throw RefusedPhoto(PhotoRefusal.UNREADABLE)
        return PixelSize(options.outWidth, options.outHeight)
    }

    /** The EXIF orientation, where the file carries one; a picture without EXIF is upright. */
    private fun orientation(open: () -> InputStream): Orientation = try {
        open().use { Orientation.ofExif(ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) }
    } catch (e: IOException) {
        Orientation.Upright
    } catch (e: RuntimeException) {
        Orientation.Upright
    }

    private fun decode(open: () -> InputStream, sample: Int): Bitmap {
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return reading { open().use { BitmapFactory.decodeStream(it, null, options) } }
            ?: throw RefusedPhoto(PhotoRefusal.UNREADABLE)
    }

    /**
     * [source] scaled to [target] and turned by [orientation], on white so that a transparent picture reads as it
     * looked rather than on black.
     */
    private fun turned(source: Bitmap, target: PixelSize, orientation: Orientation): Bitmap {
        val matrix = Matrix().apply {
            postScale(
                target.width.toFloat() / source.width * if (orientation.mirrored) -1f else 1f,
                target.height.toFloat() / source.height,
            )
            postRotate(orientation.degrees.toFloat())
            val area = RectF(0f, 0f, source.width.toFloat(), source.height.toFloat()).also { mapRect(it) }
            postTranslate(-area.left, -area.top)
        }
        val (width, height) = if (orientation.swapsSides) target.height to target.width else target.width to target.height
        val result = try {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        } catch (e: OutOfMemoryError) {
            throw RefusedPhoto(PhotoRefusal.UNREADABLE, e)
        }
        Canvas(result).apply {
            drawColor(Color.WHITE)
            drawBitmap(source, matrix, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        }
        return result
    }

    /** Runs a read of the original, turning whatever stops it into a refusal of an unreadable picture (MEDIA-008). */
    private fun <T> reading(read: () -> T): T = try {
        read()
    } catch (e: IOException) {
        throw RefusedPhoto(PhotoRefusal.UNREADABLE, e)
    } catch (e: SecurityException) {
        throw RefusedPhoto(PhotoRefusal.UNREADABLE, e)
    } catch (e: IllegalArgumentException) {
        throw RefusedPhoto(PhotoRefusal.UNREADABLE, e)
    } catch (e: OutOfMemoryError) {
        throw RefusedPhoto(PhotoRefusal.UNREADABLE, e)
    }
}
