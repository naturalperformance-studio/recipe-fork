package app.recipefork.app.media

import androidx.media3.common.util.UnstableApi
import androidx.media3.container.MdtaMetadataEntry
import androidx.media3.common.Metadata
import androidx.media3.container.Mp4LocationData
import androidx.media3.container.Mp4TimestampData
import app.recipefork.core.recipe.VideoLimits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** What a clip is converted to (MEDIA-003, MEDIA-004), as far as it is decided before the codec runs. */
@androidx.annotation.OptIn(UnstableApi::class)
class ClipPlanTest {
    private fun converted(width: Int, height: Int, rotation: Int = 0) =
        ClipPlan.converted(ClipPlan.displayed(width, height, rotation), VideoLimits.MAX_LONGER_EDGE)

    // MEDIA-004 (4): a 4K clip comes out at 1280 on its longer edge, in whichever orientation it was shot.
    @Test
    fun aLargeClipIsBroughtToTheLongerEdgeInEitherOrientation() {
        assertEquals(PixelSize(1280, 720), converted(3840, 2160))
        assertEquals(PixelSize(720, 1280), converted(3840, 2160, rotation = 90))
        assertEquals(PixelSize(720, 1280), converted(2160, 3840))
        assertEquals(PixelSize(1280, 720), converted(1920, 1080, rotation = 180))
        assertEquals(PixelSize(720, 1280), converted(1920, 1080, rotation = 270))
    }

    // MEDIA-004 (5): a clip already within the limit is not enlarged.
    @Test
    fun aSmallClipIsNotEnlarged() {
        assertEquals(PixelSize(640, 480), converted(640, 480))
        assertEquals(PixelSize(480, 640), converted(640, 480, rotation = 90))
        assertEquals(PixelSize(1280, 720), converted(1280, 720))
    }

    @Test
    fun eachSideIsEven() {
        assertEquals(PixelSize(1280, 538), converted(2000, 841))
        assertEquals(PixelSize(320, 240), converted(321, 241))
    }

    // MEDIA-004 / MEDIA-006: the bitrate aims a clip of the longest duration well under the weight limit.
    @Test
    fun theLongestClipAtTheBitrateWeighsWellUnderTheLimit() {
        val bytes = ClipPlan.MAX_BITRATE.toLong() / 8 * VideoLimits.MAX_DURATION.inWholeSeconds
        assertTrue("$bytes", bytes <= VideoLimits.MAX_BYTES * 2 / 3)
        assertEquals(ClipPlan.MAX_BITRATE, ClipPlan.bitrate(PixelSize(1280, 720)))
        assertEquals(ClipPlan.MAX_BITRATE, ClipPlan.bitrate(PixelSize(720, 1280)))
        assertEquals(ClipPlan.MAX_BITRATE, ClipPlan.bitrate(PixelSize(1280, 1280)))
    }

    // A small clip is given bits in proportion to its pixels, not inflated to what a full frame needs.
    @Test
    fun aSmallClipIsGivenFewerBits() {
        assertEquals(500_000, ClipPlan.bitrate(PixelSize(640, 360)))
        assertEquals(1_000_000, ClipPlan.bitrate(PixelSize(1280, 360)))
        assertEquals(ClipPlan.MIN_BITRATE, ClipPlan.bitrate(PixelSize(160, 96)))
    }

    // MEDIA-003: the muxer is handed nothing of the original's metadata, and a time of none rather than of now.
    @Test
    fun theOutputKeepsNoneOfTheOriginalsMetadata() {
        val entries = mutableSetOf<Metadata.Entry>(
            Mp4LocationData(48.8584f, 2.2945f),
            Mp4TimestampData(3_800_000_000, 3_800_000_000),
            MdtaMetadataEntry("com.android.manufacturer", "Maker".toByteArray(), MdtaMetadataEntry.TYPE_INDICATOR_STRING),
        )
        OrientationOnly.updateMetadataEntries(entries)
        assertEquals(setOf<Metadata.Entry>(Mp4TimestampData(0, 0)), entries)
    }
}
