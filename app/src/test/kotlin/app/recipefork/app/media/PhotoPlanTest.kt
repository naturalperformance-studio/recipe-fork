package app.recipefork.app.media

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The arithmetic of MEDIA-002 and MEDIA-006, apart from any picture. */
class PhotoPlanTest {
    // MEDIA-002 (1, 3)
    @Test
    fun theLongerEdgeIsBroughtWithin1600AndNeverEnlarged() {
        assertEquals(PixelSize(1600, 1200), PhotoPlan.scaled(PixelSize(4000, 3000), 1600))
        assertEquals(PixelSize(1200, 1600), PhotoPlan.scaled(PixelSize(3000, 4000), 1600))
        assertEquals(PixelSize(1600, 1), PhotoPlan.scaled(PixelSize(20000, 2), 1600))
        assertEquals(PixelSize(1600, 900), PhotoPlan.scaled(PixelSize(1600, 900), 1600))
        assertEquals(PixelSize(640, 480), PhotoPlan.scaled(PixelSize(640, 480), 1600))
    }

    @Test
    fun aLargePictureIsDecodedDownButNeverBelowWhatItIsScaledTo() {
        assertEquals(2, PhotoPlan.sampleSize(PixelSize(4000, 3000), 1600))
        assertEquals(4, PhotoPlan.sampleSize(PixelSize(8000, 6000), 1600))
        assertEquals(1, PhotoPlan.sampleSize(PixelSize(3199, 2000), 1600))
        assertEquals(1, PhotoPlan.sampleSize(PixelSize(640, 480), 1600))
    }

    // MEDIA-006 (1): the quality is lowered step by step before the photograph is given up.
    @Test
    fun theQualityIsLoweredUntilThePhotographFits() {
        val tried = mutableListOf<Int>()
        val fits = PhotoPlan.firstThatFits(listOf(90, 80, 70, 60), maxBytes = 70) { quality ->
            tried += quality
            ByteArray(quality)
        }
        assertArrayEquals(ByteArray(70), fits)
        assertEquals(listOf(90, 80, 70), tried)
    }

    @Test
    fun aPhotographThatFitsAtNoQualityIsGivenUpAfterTheLowest() {
        val tried = mutableListOf<Int>()
        assertNull(PhotoPlan.firstThatFits(listOf(40, 90, 60), maxBytes = 10) { quality -> ByteArray(quality).also { tried += quality } })
        assertEquals(listOf(90, 60, 40), tried)
    }

    @Test
    fun theDefaultLimitsAreOneMegabyteAnd1600PixelsWithAFloorOnQuality() {
        val limits = PhotoLimits()
        assertEquals(1_000_000, limits.maxBytes)
        assertEquals(1600, limits.maxEdge)
        assertEquals(40, limits.qualities.min())
    }

    // MEDIA-003 (3): every EXIF orientation is turned into a rotation of the pixels.
    @Test
    fun everyExifOrientationIsReadAsATurning() {
        assertEquals(Orientation.Upright, Orientation.ofExif(1))
        assertEquals(Orientation.Upright, Orientation.ofExif(0))
        assertEquals(Orientation(0, true), Orientation.ofExif(2))
        assertEquals(Orientation(180, false), Orientation.ofExif(3))
        assertEquals(Orientation(180, true), Orientation.ofExif(4))
        assertEquals(Orientation(90, true), Orientation.ofExif(5))
        assertEquals(Orientation(90, false), Orientation.ofExif(6))
        assertEquals(Orientation(270, true), Orientation.ofExif(7))
        assertEquals(Orientation(270, false), Orientation.ofExif(8))
        assertEquals(listOf(5, 6, 7, 8), (1..8).filter { Orientation.ofExif(it).swapsSides })
    }
}
