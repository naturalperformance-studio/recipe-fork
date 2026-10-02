package app.recipefork.app.media.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The frame the camera keeps (FLOW-026): the largest centred 4:3 rectangle that is wider than tall once the picture is
 * turned as the phone was held — the same rectangle the viewfinder shows.
 */
class CameraFrameTest {
    /** The frame's width and height as the author sees it, once [crop] is turned by [rotation]. */
    private fun seen(crop: PixelRect, rotation: Int): Pair<Int, Int> =
        if (rotation % 180 == 0) crop.width to crop.height else crop.height to crop.width

    // The phone held upright: the sensor lies a quarter turned, so the frame is a band across its full height.
    @Test
    fun aPhoneHeldUprightKeepsABandAcrossTheSensor() {
        val sensor = PixelRect(0, 0, 4000, 3000)
        listOf(90, 270).forEach { rotation ->
            val crop = CameraFrame.crop(sensor, rotation)
            assertEquals(PixelRect(875, 0, 3125, 3000), crop)
            assertEquals(3000 to 2250, seen(crop, rotation))
        }
    }

    // Held sideways, the sensor's own 4:3 is the frame already: nothing is cut.
    @Test
    fun aPhoneHeldSidewaysKeepsTheWholeSensor() {
        val sensor = PixelRect(0, 0, 4000, 3000)
        listOf(0, 180).forEach { assertEquals(sensor, CameraFrame.crop(sensor, it)) }
    }

    // The crop the camera reports — the viewport the viewfinder shares — comes back as it is when it is the frame.
    @Test
    fun theCameraOwnCropIsKeptWhenItIsTheFrame() {
        val viewport = PixelRect(875, 0, 3125, 3000)
        assertEquals(viewport, CameraFrame.crop(viewport, 90))
        val sideways = PixelRect(100, 50, 500, 350)
        assertEquals(sideways, CameraFrame.crop(sideways, 0))
    }

    // Whatever the crop reported, the frame is centred in it, inside it, and 4:3 wider than tall as seen.
    @Test
    fun anyCropIsCentredFourByThreeLandscape() {
        val crops = listOf(PixelRect(0, 0, 1920, 1080), PixelRect(10, 20, 1010, 2020), PixelRect(0, 0, 3000, 3000), PixelRect(7, 3, 4007, 3003))
        for (within in crops) for (rotation in listOf(0, 90, 180, 270)) {
            val crop = CameraFrame.crop(within, rotation)
            val (w, h) = seen(crop, rotation)
            assertTrue("$within $rotation: $w x $h", w > h)
            assertEquals("$within $rotation: $w x $h", 4.0 / 3.0, w.toDouble() / h, 0.01)
            assertTrue(crop.left >= within.left && crop.top >= within.top && crop.right <= within.right && crop.bottom <= within.bottom)
            // Centred: equal margins, to a pixel.
            assertTrue(kotlin.math.abs((crop.left - within.left) - (within.right - crop.right)) <= 1)
            assertTrue(kotlin.math.abs((crop.top - within.top) - (within.bottom - crop.bottom)) <= 1)
            // The largest such: it fills the crop one way.
            assertTrue(crop.width == within.width || crop.height == within.height)
        }
    }

    // The picture is read shrunk by a power of two, never below what processing keeps.
    @Test
    fun thePictureIsReadNoSmallerThanProcessingKeeps() {
        assertEquals(1, CameraFrame.sampleSize(3000, 2250))
        assertEquals(2, CameraFrame.sampleSize(4000, 3000))
        assertEquals(4, CameraFrame.sampleSize(6400, 4800))
        assertEquals(1, CameraFrame.sampleSize(800, 600))
    }
}
