package app.recipefork.app.media.crop

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.media.Orientation
import app.recipefork.app.media.PhotoReencoder
import app.recipefork.app.media.PixelSize
import app.recipefork.app.media.TestPhotos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * A picture opened for the crop and cut by it (FLOW-026): read shrunk for the screen, turned upright whichever way it
 * was taken — by the camera's own turn or by the file's EXIF — and cut, at full sharpness, to exactly the framed part,
 * holding nothing but pixels.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CropPictureTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dir = File(context.cacheDir, "crop-picture").apply { mkdirs() }

    private fun decode(jpeg: ByteArray): Bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)

    private fun isRed(color: Int) = Color.red(color) > 150 && Color.green(color) < 100 && Color.blue(color) < 100
    private fun isBlue(color: Int) = Color.blue(color) > 150 && Color.red(color) < 100 && Color.green(color) < 100
    private fun isGreen(color: Int) = Color.green(color) > 150 && Color.red(color) < 100 && Color.blue(color) < 100

    /** Upright, the picture's top is red and its bottom blue. */
    private fun assertUpright(bitmap: Bitmap) {
        assertTrue("top", isRed(bitmap.getPixel(bitmap.width / 2, bitmap.height / 8)))
        assertTrue("bottom", isBlue(bitmap.getPixel(bitmap.width / 2, bitmap.height * 7 / 8)))
    }

    /**
     * A file whose pixels, turned by EXIF [exif], stand as a [width] by [height] picture red on top and blue below: the
     * pixels are written turned the other way, as a phone's camera writes them.
     */
    private fun turnedFile(name: String, width: Int, height: Int, exif: Int): File {
        val upright = TestPhotos.jpeg(File(dir, "upright.jpg"), width, height)
        val bitmap = BitmapFactory.decodeFile(upright.path)
        val orientation = Orientation.ofExif(exif)
        val matrix = android.graphics.Matrix().apply {
            postRotate(-orientation.degrees.toFloat())
            if (orientation.mirrored) postScale(-1f, 1f)
        }
        val stored = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        val file = File(dir, name)
        file.outputStream().use { stored.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        return TestPhotos.tag(file, exif)
    }

    private fun gallery(file: File) = PhotoToCrop({ file.inputStream() }, orientation = null, retake = false)

    private fun cutAtWidest(photo: PhotoToCrop): Bitmap {
        val picture = CropPicture.open(photo)
        assertNotNull(picture)
        val rect = CropGeometry.pixels(CropGeometry.initial(picture!!.upright), picture.upright)
        return decode(CropPicture.cut(photo, picture.raw, picture.orientation, rect))
    }

    // A gallery photo carrying an EXIF turn is shown, and cut, the right way up.
    @Test
    fun aGalleryPhotoWithAnExifTurnStandsUpright() {
        listOf(
            ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_ROTATE_180, ExifInterface.ORIENTATION_ROTATE_270,
            ExifInterface.ORIENTATION_FLIP_VERTICAL, ExifInterface.ORIENTATION_TRANSPOSE,
        ).forEach { exif ->
            val photo = gallery(turnedFile("turned-$exif.jpg", 1200, 900, exif))
            val picture = CropPicture.open(photo)!!
            assertEquals("EXIF $exif", PixelSize(1200, 900), picture.upright)
            assertUpright(picture.preview)
            val cut = cutAtWidest(photo)
            assertEquals("EXIF $exif", 1200, cut.width)
            assertEquals(900, cut.height)
            assertUpright(cut)
        }
    }

    // A shot taken upside down — the camera saying it turns half a turn — is cut the right way up.
    @Test
    fun aShotTakenUpsideDownStandsUpright() {
        val upright = TestPhotos.jpeg(File(dir, "shot.jpg"), 1600, 1200)
        val bitmap = BitmapFactory.decodeFile(upright.path)
        val turned = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, android.graphics.Matrix().apply { postRotate(180f) }, true)
        val jpeg = ByteArrayOutputStream().also { turned.compress(Bitmap.CompressFormat.JPEG, 95, it) }.toByteArray()
        val photo = PhotoToCrop({ ByteArrayInputStream(jpeg) }, Orientation(180, false), retake = true)

        assertUpright(cutAtWidest(photo))
    }

    // A shot taken sideways — the sensor's picture a quarter turned — stands upright, taller than wide, and the
    // widest frame is a band across its middle.
    @Test
    fun aShotTakenSidewaysStandsUpright() {
        val upright = TestPhotos.jpeg(File(dir, "tall.jpg"), 900, 1200)
        val bitmap = BitmapFactory.decodeFile(upright.path)
        val sensor = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, android.graphics.Matrix().apply { postRotate(-90f) }, true)
        val jpeg = ByteArrayOutputStream().also { sensor.compress(Bitmap.CompressFormat.JPEG, 95, it) }.toByteArray()
        val photo = PhotoToCrop({ ByteArrayInputStream(jpeg) }, Orientation(90, false), retake = true)

        val picture = CropPicture.open(photo)!!
        assertEquals(PixelSize(900, 1200), picture.upright)
        val cut = cutAtWidest(photo)
        assertEquals(900, cut.width)
        assertEquals(675, cut.height)
        // The band across the middle straddles the red top and the blue bottom.
        assertTrue(isRed(cut.getPixel(450, 20)) && isBlue(cut.getPixel(450, 655)))
    }

    // The cut is exactly the framed part, read at full sharpness where processing keeps it.
    @Test
    fun theCutIsExactlyTheFramedPartAtFullSharpness() {
        val bitmap = Bitmap.createBitmap(3200, 2400, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.RED)
            drawRect(800f, 600f, 2400f, 1800f, Paint().apply { color = Color.GREEN })
        }
        val jpeg = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it) }.toByteArray()
        val photo = PhotoToCrop({ ByteArrayInputStream(jpeg) }, Orientation.Upright, retake = true)
        val picture = CropPicture.open(photo)!!
        assertTrue("the preview is read shrunk", picture.preview.width < 3200)

        val cut = decode(CropPicture.cut(photo, picture.raw, picture.orientation, PixelRect(800, 600, 2400, 1800)))

        assertEquals(1600, cut.width)
        assertEquals(1200, cut.height)
        for (y in 8 until cut.height - 8 step 37) for (x in 8 until cut.width - 8 step 37) {
            assertTrue("($x, $y)", isGreen(cut.getPixel(x, y)))
        }
    }

    // What the cut writes holds no metadata: nothing of the place, the device or the time survives.
    @Test
    fun theCutHoldsNoMetadata() {
        val file = TestPhotos.tag(TestPhotos.jpeg(File(dir, "tagged.jpg"), 1600, 1200))
        val photo = gallery(file)
        val picture = CropPicture.open(photo)!!
        val cut = CropPicture.cut(photo, picture.raw, picture.orientation, CropGeometry.pixels(CropGeometry.initial(picture.upright), picture.upright))
        val written = File(dir, "cut.jpg").apply { writeBytes(cut) }
        val exif = ExifInterface(written.path)
        TestPhotos.IDENTIFYING.keys.forEach { assertNull(it, exif.getAttribute(it)) }
    }

    // What Done hands over is processed like any photograph: a 4:3 landscape JPEG of at most 1600 pixels and 1 MB,
    // holding no metadata.
    @Test
    fun theCutIsProcessedWithinTheLimits() {
        val file = TestPhotos.tag(TestPhotos.jpeg(File(dir, "large.jpg"), 3000, 4000, noise = true), ExifInterface.ORIENTATION_ROTATE_90)
        val photo = gallery(file)
        val picture = CropPicture.open(photo)!!
        assertEquals(PixelSize(4000, 3000), picture.upright)
        val cut = CropPicture.cut(photo, picture.raw, picture.orientation, CropGeometry.pixels(CropGeometry.initial(picture.upright), picture.upright))

        val stored = PhotoReencoder().reencode { ByteArrayInputStream(cut) }

        assertTrue(stored.size <= 1_000_000)
        val bitmap = decode(stored)
        assertEquals(1600, bitmap.width)
        assertEquals(1200, bitmap.height)
        val written = File(dir, "stored.jpg").apply { writeBytes(stored) }
        val exif = ExifInterface(written.path)
        TestPhotos.IDENTIFYING.keys.forEach { assertNull(it, exif.getAttribute(it)) }
    }

    // A file that is no picture cannot be opened for the crop.
    @Test
    fun aFileThatIsNoPictureIsNotOpened() {
        val file = File(dir, "not.jpg").apply { writeText("not a photo at all") }
        assertNull(CropPicture.open(gallery(file)))
    }
}
