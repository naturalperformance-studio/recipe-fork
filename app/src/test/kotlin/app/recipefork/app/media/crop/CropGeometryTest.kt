package app.recipefork.app.media.crop

import app.recipefork.app.media.Orientation
import app.recipefork.app.media.PixelSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The crop's arithmetic (FLOW-026): a 4:3 landscape frame over the photograph standing upright, opened at its widest
 * and centred; moved and zoomed by the author's fingers; never leaving the photograph, never narrower than 1600 of its
 * pixels where it is that large — else the largest that fits; and mapped back to the file's own pixels however the
 * photograph was turned.
 */
class CropGeometryTest {
    private val landscape = PixelSize(4000, 3000)
    private val portrait = PixelSize(3000, 4000)
    private val tolerance = 1e-6

    private fun assertInside(box: CropBox, source: PixelSize) {
        assertTrue("$box", box.left >= -tolerance && box.top >= -tolerance)
        assertTrue("$box", box.left + box.width <= source.width + tolerance && box.top + box.height <= source.height + tolerance)
    }

    private fun assertFourByThree(box: CropBox) = assertEquals(4.0 / 3.0, box.width / box.height, tolerance)

    @Test
    fun theCropOpensAtItsWidestCentred() {
        assertEquals(CropBox(0.0, 0.0, 4000.0), CropGeometry.initial(landscape))
        // Taller than wide: the frame spans the photograph's width, centred in its height.
        val tall = CropGeometry.initial(portrait)
        assertEquals(CropBox(0.0, 875.0, 3000.0), tall)
        assertEquals(2250.0, tall.height, tolerance)
        // Wider than 4:3: the frame spans its height, centred in its width.
        assertEquals(CropBox(400.0, 0.0, 2400.0), CropGeometry.initial(PixelSize(3200, 1800)))
    }

    @Test
    fun theFrameIsNeverNarrowerThan1600WhereThePhotographAllows() {
        assertEquals(1600.0, CropGeometry.minWidth(landscape), tolerance)
        assertEquals(1600.0, CropGeometry.minWidth(portrait), tolerance)
        // Zoomed far in about the centre: stops at 1600 wide.
        val zoomed = CropGeometry.moved(CropGeometry.initial(landscape), landscape, 1000f, 500f, 375f, 0f, 0f, 10f)
        assertEquals(1600.0, zoomed.width, tolerance)
        assertFourByThree(zoomed)
        assertInside(zoomed, landscape)
        // The point under the fingers stays put: the centre of the photograph.
        assertEquals(2000.0, zoomed.left + zoomed.width / 2, tolerance)
        assertEquals(1500.0, zoomed.top + zoomed.height / 2, tolerance)
    }

    @Test
    fun aSmallPhotographKeepsTheLargestFrameThatFits() {
        val small = PixelSize(1200, 1000)
        val opened = CropGeometry.initial(small)
        assertEquals(1200.0, opened.width, tolerance)
        assertEquals(900.0, opened.height, tolerance)
        // No zoom either way: the frame is both the narrowest and the widest allowed.
        assertEquals(opened.width, CropGeometry.moved(opened, small, 800f, 400f, 300f, 0f, 0f, 3f).width, tolerance)
        assertEquals(opened.width, CropGeometry.moved(opened, small, 800f, 400f, 300f, 0f, 0f, 0.3f).width, tolerance)
    }

    @Test
    fun theFrameIsNeverWiderThanThePhotograph() {
        val zoomedOut = CropGeometry.moved(CropBox(1000.0, 1000.0, 1600.0), landscape, 800f, 400f, 300f, 0f, 0f, 0.01f)
        assertEquals(4000.0, zoomedOut.width, tolerance)
        assertInside(zoomedOut, landscape)
    }

    @Test
    fun aDragMovesThePhotographUnderTheFrame() {
        // The frame 800 screen pixels wide over 1600 of the photograph: a screen pixel is two of its pixels.
        val box = CropBox(1000.0, 1000.0, 1600.0)
        // Fingers moving right drag the photograph right, so the frame shows what lies further left.
        val moved = CropGeometry.moved(box, landscape, 800f, 400f, 300f, 100f, -50f, 1f)
        assertEquals(CropBox(800.0, 1100.0, 1600.0), moved)
    }

    @Test
    fun theFrameNeverLeavesThePhotograph() {
        val box = CropBox(1000.0, 1000.0, 1600.0)
        listOf(10_000f to 0f, -10_000f to 0f, 0f to 10_000f, 0f to -10_000f, 10_000f to -10_000f).forEach { (x, y) ->
            val moved = CropGeometry.moved(box, landscape, 800f, 400f, 300f, x, y, 1f)
            assertInside(moved, landscape)
            assertEquals(1600.0, moved.width, tolerance)
        }
        // Zoomed out about a corner, the frame is pushed back inside.
        val out = CropGeometry.moved(CropBox(0.0, 0.0, 1600.0), landscape, 800f, 0f, 0f, 0f, 0f, 0.5f)
        assertEquals(CropBox(0.0, 0.0, 3200.0), out)
        val far = CropGeometry.clamp(CropBox(3000.0, 2900.0, 2000.0), landscape)
        assertEquals(CropBox(2000.0, 1500.0, 2000.0), far)
    }

    @Test
    fun theFrameInWholePixelsIsFourByThreeAndInside() {
        listOf(PixelSize(4000, 3000), PixelSize(3001, 4001), PixelSize(1201, 999), PixelSize(5, 5)).forEach { source ->
            val rect = CropGeometry.pixels(CropBox(0.4, 0.6, CropGeometry.maxWidth(source)), source)
            assertTrue("$rect in $source", rect.left >= 0 && rect.top >= 0 && rect.right <= source.width && rect.bottom <= source.height)
            assertEquals("$rect", rect.width.toLong() * 3 / 4, rect.height.toLong())
        }
    }

    /** The upright corner [u], [v] of a picture whose file is [raw], computed forward the way processing turns it. */
    private fun forward(x: Int, y: Int, raw: PixelSize, orientation: Orientation): Pair<Int, Int> {
        val mx = if (orientation.mirrored) raw.width - x else x
        return when (orientation.degrees) {
            90 -> raw.height - y to mx
            180 -> raw.width - mx to raw.height - y
            270 -> y to raw.width - mx
            else -> mx to y
        }
    }

    @Test
    fun theFrameMapsBackToTheFilesPixelsForEveryOrientation() {
        val raw = PixelSize(4000, 3000)
        (1..8).map { Orientation.ofExif(it) }.forEach { orientation ->
            val upright = CropGeometry.upright(raw, orientation)
            val rect = PixelRect(100, 200, 100 + 1600, 200 + 1200)
            val region = CropGeometry.toRaw(rect, raw, orientation)
            assertTrue("$orientation: $region", region.left >= 0 && region.top >= 0 && region.right <= raw.width && region.bottom <= raw.height)
            // Turned forward, the region's corners land on the upright rectangle's.
            val a = forward(region.left, region.top, raw, orientation)
            val b = forward(region.right, region.bottom, raw, orientation)
            assertEquals("$orientation", rect, PixelRect(minOf(a.first, b.first), minOf(a.second, b.second), maxOf(a.first, b.first), maxOf(a.second, b.second)))
            assertEquals(if (orientation.swapsSides) PixelSize(3000, 4000) else raw, upright)
        }
        // A quarter turn clockwise: the upright top-left is the file's bottom-left.
        assertEquals(PixelRect(0, 2000, 1200, 3000), CropGeometry.toRaw(PixelRect(0, 0, 1000, 1200), PixelSize(4000, 3000), Orientation(90, false)))
    }

    @Test
    fun aRegionIsReadShrunkOnlyAsFarAsProcessingKeeps() {
        assertEquals(1, CropGeometry.sampleSize(3000, 2250))
        assertEquals(2, CropGeometry.sampleSize(4000, 3000))
        assertEquals(1, CropGeometry.sampleSize(1600, 1200))
        assertEquals(4, CropGeometry.sampleSize(8000, 6000, 1600))
    }
}
