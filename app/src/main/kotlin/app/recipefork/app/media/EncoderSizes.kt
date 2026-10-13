package app.recipefork.app.media

import android.media.MediaCodecInfo
import kotlin.math.ceil
import kotlin.math.max

/**
 * The picture sizes a video encoder takes, as it states them (MediaCodecInfo.VideoCapabilities): the least width and
 * height, the multiple each side must be, and whether a given size is taken at all. Kept apart from the codec so that
 * the arithmetic of fitting a picture to them is tested on its own.
 */
interface EncoderSizes {
    val minWidth: Int
    val minHeight: Int
    val widthAlignment: Int
    val heightAlignment: Int

    fun supports(width: Int, height: Int): Boolean

    companion object {
        /**
         * The sizes [capabilities] states, read from the very encoder that is to run; an encoder stating none is taken
         * to take any size, and its own refusal says otherwise.
         */
        fun of(capabilities: MediaCodecInfo.VideoCapabilities?): EncoderSizes = if (capabilities == null) ANY else object : EncoderSizes {
            override val minWidth = capabilities.supportedWidths.lower
            override val minHeight = capabilities.supportedHeights.lower
            override val widthAlignment = capabilities.widthAlignment
            override val heightAlignment = capabilities.heightAlignment

            override fun supports(width: Int, height: Int) = capabilities.isSizeSupported(width, height)
        }

        private val ANY = object : EncoderSizes {
            override val minWidth = 2
            override val minHeight = 2
            override val widthAlignment = 2
            override val heightAlignment = 2

            override fun supports(width: Int, height: Int) = true
        }

        /**
         * The size a picture planned at [planned] is encoded at by an encoder taking [sizes]: [planned] itself where it
         * is taken; otherwise enlarged, keeping its proportions, to the encoder's least width and height and each side
         * to its multiple — the MediaTek encoder of the owner's phone takes nothing as small as 160 x 120 (T-243) — and
         * further while the encoder still refuses it, never past [maxEdge] on either side. Where no size up to that is
         * taken, [planned] is given back, and the encoder's own refusal says the rest.
         */
        fun fitted(planned: PixelSize, sizes: EncoderSizes, maxEdge: Int): PixelSize {
            if (planned.width >= sizes.minWidth && planned.height >= sizes.minHeight && sizes.supports(planned.width, planned.height)) {
                return planned
            }
            var scale = max(1.0, max(sizes.minWidth.toDouble() / planned.width, sizes.minHeight.toDouble() / planned.height))
            var size = scaledTo(planned, scale, sizes, maxEdge)
            // The largest a side may grow to: the longer edge, down to its multiple.
            val ceiling = scaledTo(planned, Double.MAX_VALUE, sizes, maxEdge)
            while (!sizes.supports(size.width, size.height)) {
                // Grown as far as either side may go, and still refused: no size is taken.
                if (size == ceiling) return planned
                scale *= GROWTH
                size = scaledTo(planned, scale, sizes, maxEdge)
            }
            return size
        }

        private fun scaledTo(planned: PixelSize, scale: Double, sizes: EncoderSizes, maxEdge: Int) = PixelSize(
            side(planned.width * scale, sizes.minWidth, sizes.widthAlignment, maxEdge),
            side(planned.height * scale, sizes.minHeight, sizes.heightAlignment, maxEdge),
        )

        /** A side of [length] rounded up to [alignment] — and to an even number — at least [least], at most [maxEdge]. */
        private fun side(length: Double, least: Int, alignment: Int, maxEdge: Int): Int {
            val step = lcm(alignment.coerceAtLeast(1), 2)
            val up = ceil(length - EPSILON).coerceAtMost(maxEdge.toDouble() + step).toInt().coerceAtLeast(least)
            val aligned = (up + step - 1) / step * step
            return if (aligned <= maxEdge) aligned else maxEdge / step * step
        }

        private fun lcm(a: Int, b: Int): Int {
            tailrec fun gcd(x: Int, y: Int): Int = if (y == 0) x else gcd(y, x % y)
            return a / gcd(a, b) * b
        }

        /** How much larger each further try is, when the encoder still refuses a size at its stated least. */
        private const val GROWTH = 1.25

        /** What floating-point arithmetic adds to a side scaled to an exact whole number. */
        private const val EPSILON = 1e-9
    }
}
