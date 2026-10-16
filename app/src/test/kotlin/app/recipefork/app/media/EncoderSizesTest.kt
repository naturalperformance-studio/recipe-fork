package app.recipefork.app.media

import app.recipefork.core.recipe.VideoLimits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A small animation brought up to the least picture the device's encoder takes before it is encoded (T-243: the
 * MediaTek encoder of the owner's phone refuses 160 x 120), keeping its proportions; told by a stand-in for the
 * encoder's stated capabilities.
 */
class EncoderSizesTest {
    private class Encoder(
        override val minWidth: Int,
        override val minHeight: Int,
        override val widthAlignment: Int = 2,
        override val heightAlignment: Int = 2,
        private val refuses: (Int, Int) -> Boolean = { _, _ -> false },
    ) : EncoderSizes {
        override fun supports(width: Int, height: Int) =
            width >= minWidth && height >= minHeight && width % widthAlignment == 0 && height % heightAlignment == 0 && !refuses(width, height)
    }

    private fun fitted(width: Int, height: Int, encoder: EncoderSizes) =
        EncoderSizes.fitted(PixelSize(width, height), encoder, VideoLimits.MAX_LONGER_EDGE)

    private fun assertProportions(width: Int, height: Int, size: PixelSize, alignment: Int) {
        // Within what rounding each side up to its multiple adds.
        val expected = size.width.toDouble() * height / width
        assertTrue("$size keeps $width:$height", kotlin.math.abs(size.height - expected) <= alignment)
    }

    @Test
    fun aSizeTheEncoderTakesIsKept() {
        assertEquals(PixelSize(160, 120), fitted(160, 120, Encoder(64, 64)))
        assertEquals(PixelSize(1280, 720), fitted(1280, 720, Encoder(176, 144, 16, 16)))
    }

    @Test
    fun anAnimationSmallerThanTheEncodersLeastIsEnlargedKeepingItsProportions() {
        val encoder = Encoder(176, 144, 16, 16)
        val gif = fitted(160, 120, encoder)
        assertEquals(PixelSize(192, 144), gif)
        assertTrue(encoder.supports(gif.width, gif.height))

        val upright = fitted(120, 160, encoder)
        assertTrue("$upright", upright.width >= 176 && upright.height >= 144 && encoder.supports(upright.width, upright.height))
        assertProportions(120, 160, upright, 16)

        val wide = fitted(320, 96, Encoder(320, 240, 16, 16))
        assertEquals(PixelSize(800, 240), wide)
    }

    @Test
    fun eachSideIsAMultipleOfItsAlignmentAndEven() {
        val size = fitted(150, 100, Encoder(200, 200, widthAlignment = 16, heightAlignment = 8))
        assertEquals(0, size.width % 16)
        assertEquals(0, size.height % 8)
        assertTrue("$size", size.width >= 200 && size.height >= 200)
        assertProportions(150, 100, size, 16)

        val odd = fitted(101, 75, Encoder(150, 1, widthAlignment = 1, heightAlignment = 1))
        assertEquals(0, odd.width % 2)
        assertEquals(0, odd.height % 2)
    }

    // An encoder whose stated least is not all it refuses: enlarged further until a size is taken.
    @Test
    fun aSizeStillRefusedIsEnlargedFurther() {
        val encoder = Encoder(176, 144, 16, 16, refuses = { width, height -> width * height < 320 * 240 })
        val size = fitted(160, 120, encoder)
        assertTrue("$size", encoder.supports(size.width, size.height))
        assertProportions(160, 120, size, 16)
    }

    // Never past the longer edge, whatever the encoder asks of a very narrow strip.
    @Test
    fun noSideGoesPastTheLongerEdge() {
        val size = fitted(1200, 20, Encoder(176, 144, 16, 16))
        assertTrue("$size", size.width <= VideoLimits.MAX_LONGER_EDGE && size.height >= 144)
    }

    // An encoder that takes no size at all ends the search — even where its multiple does not divide the longer edge,
    // so that the sides stop short of it — and the planned size is given back for the encoder to refuse (T-274 review).
    @Test
    fun anEncoderTakingNoSizeEndsTheSearch() {
        listOf(Encoder(176, 144, 16, 16) { _, _ -> true }, Encoder(176, 144, 512, 512) { _, _ -> true }).forEach { encoder ->
            assertEquals(PixelSize(160, 120), fitted(160, 120, encoder))
        }
    }
}
