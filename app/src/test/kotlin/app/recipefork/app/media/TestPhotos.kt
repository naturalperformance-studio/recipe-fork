package app.recipefork.app.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.ExifInterface
import androidx.compose.ui.test.IdlingResource
import java.io.File
import java.util.Random

/** Photographs made for a test, as a phone's camera would hand them over. */
object TestPhotos {
    /** Busy while a photograph is being processed and attached, for a screen test to wait on (as on a `TestBook`). */
    val idling: IdlingResource = object : IdlingResource {
        override val isIdleNow: Boolean get() = PhotoWork.isIdle
        override fun getDiagnosticMessageIfBusy() = "a photograph is being processed"
    }

    /**
     * A JPEG of [width] by [height] written to [file]: its top half red and its bottom half blue, so that which way up
     * it is shows in its pixels, overlaid with [noise] — random dots that resist compression — where asked.
     */
    fun jpeg(file: File, width: Int, height: Int, noise: Boolean = false, quality: Int = 95): File {
        val bitmap = halves(width, height, noise)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it) }
        bitmap.recycle()
        return file
    }

    /** A PNG of [width] by [height] with a transparent bottom half. */
    fun transparentPng(file: File, width: Int, height: Int): File {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawRect(0f, 0f, width.toFloat(), height / 2f, Paint().apply { color = Color.RED })
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return file
    }

    /** Writes into [file] — a JPEG — every field MEDIA-003 names: place, device, time and person, and [orientation]. */
    fun tag(file: File, orientation: Int = ExifInterface.ORIENTATION_NORMAL): File {
        val exif = ExifInterface(file.path)
        IDENTIFYING.forEach { (tag, value) -> exif.setAttribute(tag, value) }
        exif.setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
        exif.saveAttributes()
        return file
    }

    /** The identifying fields [tag] writes, as an EXIF reader reads them back. */
    val IDENTIFYING = mapOf(
        ExifInterface.TAG_GPS_LATITUDE to "55/1,45/1,2100/100",
        ExifInterface.TAG_GPS_LATITUDE_REF to "N",
        ExifInterface.TAG_GPS_LONGITUDE to "37/1,37/1,1700/100",
        ExifInterface.TAG_GPS_LONGITUDE_REF to "E",
        ExifInterface.TAG_MAKE to "PhoneMaker",
        ExifInterface.TAG_MODEL to "Phone 12 Pro",
        ExifInterface.TAG_DATETIME to "2026:09:30 19:45:00",
        ExifInterface.TAG_DATETIME_ORIGINAL to "2026:09:30 19:45:00",
        ExifInterface.TAG_ARTIST to "Ivan the Cook",
        ExifInterface.TAG_SOFTWARE to "CameraApp 3.1",
    )

    private fun halves(width: Int, height: Int, noise: Boolean): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawRect(0f, 0f, width.toFloat(), height / 2f, Paint().apply { color = Color.RED })
        canvas.drawRect(0f, height / 2f, width.toFloat(), height.toFloat(), Paint().apply { color = Color.BLUE })
        if (noise) {
            val random = Random(7)
            val pixels = IntArray(width)
            for (y in 0 until height) {
                bitmap.getPixels(pixels, 0, width, 0, y, width, 1)
                for (x in 0 until width) {
                    val shift = random.nextInt(96) - 48
                    val c = pixels[x]
                    pixels[x] = Color.rgb(
                        (Color.red(c) + shift).coerceIn(0, 255),
                        (Color.green(c) + random.nextInt(96) - 48).coerceIn(0, 255),
                        (Color.blue(c) + shift).coerceIn(0, 255),
                    )
                }
                bitmap.setPixels(pixels, 0, width, 0, y, width, 1)
            }
        }
        return bitmap
    }
}
