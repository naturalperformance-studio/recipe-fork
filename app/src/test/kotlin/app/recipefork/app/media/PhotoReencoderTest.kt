package app.recipefork.app.media

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayInputStream
import java.io.File

/**
 * MEDIA-002, MEDIA-003, MEDIA-006, MEDIA-008 on real pictures: what the re-encoder writes is a plain JPEG of at most
 * 1 MB and 1600 pixels on its longer edge, the right way up, carrying no metadata at all — read back field by field.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoReencoderTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val directory = File(context.cacheDir, "photos").apply { mkdirs() }

    private fun reencoded(file: File, limits: PhotoLimits = PhotoLimits()): ByteArray =
        PhotoReencoder(limits).reencode { file.inputStream() }

    private fun written(bytes: ByteArray): File = File(directory, "out.jpg").apply { writeBytes(bytes) }

    private fun size(bytes: ByteArray): PixelSize {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        return PixelSize(options.outWidth, options.outHeight)
    }

    // MEDIA-002 (1), MEDIA-003 (1, 2, 4): a large phone photograph carrying every identifying field.
    @Test
    fun aLargePhotographIsShrunkAndCarriesNoMetadata() {
        val original = TestPhotos.tag(TestPhotos.jpeg(File(directory, "in.jpg"), 4000, 3000, noise = true))
        // The original does carry what is to be removed, so its absence below is the re-encoder's doing.
        assertEquals("PhoneMaker", ExifInterface(original.path).getAttribute(ExifInterface.TAG_MAKE))
        assertTrue(ExifInterface(original.path).getLatLong(FloatArray(2)))

        val bytes = reencoded(original)

        assertTrue("${bytes.size} bytes", bytes.size <= 1_000_000)
        assertEquals(PixelSize(1600, 1200), size(bytes))
        val exif = ExifInterface(written(bytes).path)
        assertFalse(exif.getLatLong(FloatArray(2)))
        TestPhotos.IDENTIFYING.keys.forEach { assertNull(it, exif.getAttribute(it)) }
        assertFalse(exif.hasThumbnail())
        // A plain JPEG, with no EXIF block in it at all.
        assertEquals(0xFF.toByte(), bytes[0])
        assertEquals(0xD8.toByte(), bytes[1])
        assertFalse(String(bytes, Charsets.ISO_8859_1).contains("Exif\u0000"))
    }

    // MEDIA-003 (3): a portrait photograph whose camera recorded it sideways is turned in its pixels.
    @Test
    fun theOrientationIsKeptInThePixels() {
        val original = TestPhotos.tag(TestPhotos.jpeg(File(directory, "in.jpg"), 2000, 1000), ExifInterface.ORIENTATION_ROTATE_90)

        val bytes = reencoded(original)

        assertEquals(PixelSize(800, 1600), size(bytes))
        val exif = ExifInterface(written(bytes).path)
        assertEquals(ExifInterface.ORIENTATION_UNDEFINED, exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED))
        // Turned a quarter clockwise, the red top half is now on the right and the blue bottom on the left.
        val picture = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        val right = picture.getPixel(600, 800)
        val left = picture.getPixel(200, 800)
        assertTrue("right ${Integer.toHexString(right)}", Color.red(right) > 200 && Color.blue(right) < 60)
        assertTrue("left ${Integer.toHexString(left)}", Color.blue(left) > 200 && Color.red(left) < 60)
    }

    // MEDIA-002 (2, 3): a small photograph is still re-encoded, and never enlarged.
    @Test
    fun aSmallPhotographIsReencodedAndNotEnlarged() {
        val original = TestPhotos.tag(TestPhotos.jpeg(File(directory, "in.jpg"), 300, 200))

        val bytes = reencoded(original)

        assertEquals(PixelSize(300, 200), size(bytes))
        assertNotEquals(original.readBytes().toList(), bytes.toList())
        assertNull(ExifInterface(written(bytes).path).getAttribute(ExifInterface.TAG_MODEL))
    }

    @Test
    fun aTransparentPictureIsReencodedOnWhite() {
        val bytes = reencoded(TestPhotos.transparentPng(File(directory, "in.png"), 400, 400))

        val picture = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        val bottom = picture.getPixel(200, 300)
        assertTrue(Integer.toHexString(bottom), Color.red(bottom) > 230 && Color.green(bottom) > 230 && Color.blue(bottom) > 230)
    }

    // MEDIA-008 (1): a file that is not a picture.
    @Test
    fun aFileThatIsNotAPictureIsRefusedAsUnreadable() {
        assertRefused(PhotoRefusal.UNREADABLE) { PhotoReencoder().reencode { ByteArrayInputStream("not a photo".toByteArray()) } }
        val truncated = TestPhotos.jpeg(File(directory, "in.jpg"), 400, 300).readBytes().copyOf(20)
        assertRefused(PhotoRefusal.UNREADABLE) { PhotoReencoder().reencode { ByteArrayInputStream(truncated) } }
        assertRefused(PhotoRefusal.UNREADABLE) { PhotoReencoder().reencode { throw java.io.FileNotFoundException("gone") } }
    }

    // MEDIA-006 (1): one that does not fit even at the lowest quality, every quality tried first.
    @Test
    fun aPhotographThatDoesNotFitIsRefusedAsTooLarge() {
        val original = TestPhotos.jpeg(File(directory, "in.jpg"), 1200, 900, noise = true)
        val lowest = PhotoReencoder(PhotoLimits(qualities = listOf(40))).reencode { original.inputStream() }

        assertRefused(PhotoRefusal.TOO_LARGE) { reencoded(original, PhotoLimits(maxBytes = lowest.size - 1)) }
        // At exactly the weight of the lowest quality it fits, at that quality.
        assertArrayEquals(lowest, reencoded(original, PhotoLimits(maxBytes = lowest.size)))
    }

    private fun assertRefused(refusal: PhotoRefusal, block: () -> Unit) {
        try {
            block()
            fail("not refused")
        } catch (e: RefusedPhoto) {
            assertEquals(refusal, e.refusal)
        }
    }
}
