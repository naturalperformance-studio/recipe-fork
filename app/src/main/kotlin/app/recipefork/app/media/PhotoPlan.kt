package app.recipefork.app.media

/** What a photograph is brought within on the device before it is kept (MEDIA-002). */
data class PhotoLimits(
    /** The most a stored photograph weighs: 1 MB. */
    val maxBytes: Int = 1_000_000,
    /** The longest a stored photograph's longer edge is, in pixels. */
    val maxEdge: Int = 1600,
    /** The JPEG qualities tried in turn until the photograph fits [maxBytes]; the last is the floor (MEDIA-006). */
    val qualities: List<Int> = listOf(90, 80, 70, 60, 50, 40),
) {
    init {
        require(maxBytes > 0 && maxEdge > 0 && qualities.isNotEmpty())
    }
}

/** A width and a height in pixels. */
data class PixelSize(val width: Int, val height: Int) {
    val longerEdge: Int get() = maxOf(width, height)
}

/**
 * How a photograph is turned so that it shows the right way up, read from its EXIF orientation: a rotation clockwise
 * in degrees, after an optional mirroring across the vertical axis. It is applied to the pixels, so the stored file
 * needs no orientation of its own (MEDIA-003).
 */
data class Orientation(val degrees: Int, val mirrored: Boolean) {
    /** Whether the photograph's width and height trade places when it is turned. */
    val swapsSides: Boolean get() = degrees % 180 != 0

    companion object {
        val Upright = Orientation(0, false)

        /** The turning an EXIF orientation value (1 to 8) asks for; any other value asks for none. */
        fun ofExif(value: Int): Orientation = when (value) {
            2 -> Orientation(0, true)
            3 -> Orientation(180, false)
            4 -> Orientation(180, true)
            5 -> Orientation(90, true)
            6 -> Orientation(90, false)
            7 -> Orientation(270, true)
            8 -> Orientation(270, false)
            else -> Upright
        }
    }
}

/** The arithmetic of re-encoding a photograph, apart from any picture (MEDIA-002, MEDIA-006). */
object PhotoPlan {
    /** [size] brought within [maxEdge] on its longer edge, proportions kept; never enlarged (MEDIA-002). */
    fun scaled(size: PixelSize, maxEdge: Int): PixelSize {
        val longer = size.longerEdge
        if (longer <= maxEdge) return size
        val factor = maxEdge.toDouble() / longer
        return PixelSize(
            (size.width * factor).toInt().coerceIn(1, maxEdge),
            (size.height * factor).toInt().coerceIn(1, maxEdge),
        )
    }

    /**
     * The largest power of two a picture of [size] can be decoded down by and still keep at least [atLeast] pixels on
     * its longer edge — or its own, where that is shorter — so that a large photograph is never held whole in memory.
     */
    fun sampleSize(size: PixelSize, atLeast: Int): Int {
        var sample = 1
        while (size.longerEdge / (sample * 2) >= atLeast) sample *= 2
        return sample
    }

    /**
     * The first encoding, from the highest quality of [qualities] down, that weighs at most [maxBytes]; null when even
     * the lowest does not (MEDIA-006). [encode] makes the bytes at one quality.
     */
    fun firstThatFits(qualities: List<Int>, maxBytes: Int, encode: (quality: Int) -> ByteArray): ByteArray? {
        for (quality in qualities.sortedDescending()) {
            val bytes = encode(quality)
            if (bytes.size <= maxBytes) return bytes
        }
        return null
    }
}
