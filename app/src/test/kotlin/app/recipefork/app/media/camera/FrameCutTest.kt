package app.recipefork.app.media.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream

/**
 * What is stored is what the viewfinder showed (FLOW-026): the picture the camera takes is cut to the frame, turned
 * upright, and nothing outside the frame survives.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FrameCutTest {
    /**
     * A sensor's picture of [width] by [height]: green inside [inside], red outside it — what the viewfinder does not
     * show — and a blue mark along the sensor's left edge inside it, which is the top of the scene turned upright by 90°.
     */
    private fun picture(width: Int, height: Int, inside: PixelRect, mark: Boolean = false): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.RED)
        canvas.drawRect(inside.left.toFloat(), inside.top.toFloat(), inside.right.toFloat(), inside.bottom.toFloat(), Paint().apply { color = Color.GREEN })
        if (mark) {
            canvas.drawRect(inside.left.toFloat(), inside.top.toFloat(), inside.left + 40f, inside.bottom.toFloat(), Paint().apply { color = Color.BLUE })
        }
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it) }.toByteArray()
    }

    private fun decode(jpeg: ByteArray): Bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)

    private fun isRed(color: Int) = Color.red(color) > 150 && Color.green(color) < 100 && Color.blue(color) < 100
    private fun isBlue(color: Int) = Color.blue(color) > 150 && Color.red(color) < 100 && Color.green(color) < 100

    /** Every pixel but a margin of [edge] along the borders, where JPEG blends neighbours. */
    private fun Bitmap.anyInside(edge: Int, test: (Int) -> Boolean): Boolean {
        for (y in edge until height - edge step 4) for (x in edge until width - edge step 4) if (test(getPixel(x, y))) return true
        return false
    }

    // Held upright: the sensor's picture lies a quarter turned; the stored photo is 4:3 wider than tall, upright, and
    // holds only what the viewfinder showed.
    @Test
    fun anUprightShotIsCutToTheLandscapeFrameAndTurned() {
        val frame = PixelRect(350, 0, 1250, 1200) // the band of a 1600 x 1200 sensor a phone held upright keeps
        val jpeg = picture(1600, 1200, frame, mark = true)

        val photo = decode(FrameCut.cut(jpeg, PixelRect(0, 0, 1600, 1200), 90))

        assertEquals(1200, photo.width)
        assertEquals(900, photo.height)
        assertTrue("nothing outside the frame is kept", !photo.anyInside(8) { isRed(it) })
        // The sensor's left edge, turned by 90°, is the top of the photo.
        assertTrue(isBlue(photo.getPixel(photo.width / 2, 10)))
        assertTrue(!isBlue(photo.getPixel(photo.width / 2, photo.height - 10)))
    }

    // The camera's own crop — the viewport the viewfinder shares — is honoured: what lies outside it is not stored.
    @Test
    fun theCropTheCameraReportsIsHonoured() {
        val viewport = PixelRect(200, 100, 1000, 700) // 800 x 600: the frame, held sideways
        val jpeg = picture(1200, 800, viewport)

        val photo = decode(FrameCut.cut(jpeg, viewport, 0))

        assertEquals(800, photo.width)
        assertEquals(600, photo.height)
        assertTrue(!photo.anyInside(8) { isRed(it) })
    }

    // A large picture is read shrunk, but never below the 1600 pixels processing keeps.
    @Test
    fun aLargePictureIsReadShrunkNoFurtherThanProcessingKeeps() {
        val jpeg = picture(4000, 3000, PixelRect(0, 0, 4000, 3000))

        val photo = decode(FrameCut.cut(jpeg, PixelRect(0, 0, 4000, 3000), 0))

        assertEquals(2000, photo.width)
        assertEquals(1500, photo.height)
    }
}
