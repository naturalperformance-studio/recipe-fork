package app.recipefork.app.media

/** The arithmetic of a clip's conversion (MEDIA-004), kept apart from the codec so that it is tested on its own. */
object ClipPlan {
    /**
     * The most video bitrate a converted clip is given, in bits per second. The limits allow about 3.2 Mbit/s over the
     * longest clip; 2 Mbit/s serves 720p well and lands a 15 second clip near 3.8 MB, leaving room for an encoder that
     * overshoots.
     */
    const val MAX_BITRATE: Int = 2_000_000

    /** The least: below it a very small clip would turn to blocks for a saving of a few kilobytes. */
    const val MIN_BITRATE: Int = 200_000

    /** The pixels per frame [MAX_BITRATE] is meant for: a frame at the limits' longer edge, 16:9. */
    private const val FULL_FRAME: Long = 1280L * 720

    /**
     * The video bitrate a clip converted to [size] is given: [MAX_BITRATE] in proportion to its pixels per frame, so
     * that a small clip is not inflated, and never more than [MAX_BITRATE] nor less than [MIN_BITRATE].
     */
    fun bitrate(size: PixelSize): Int =
        (MAX_BITRATE * size.width.toLong() * size.height / FULL_FRAME).coerceIn(MIN_BITRATE.toLong(), MAX_BITRATE.toLong()).toInt()

    /** The size a clip of [width] by [height] pixels, stored turned by [rotation] degrees, is shown at. */
    fun displayed(width: Int, height: Int, rotation: Int): PixelSize =
        if (rotation % 180 == 0) PixelSize(width, height) else PixelSize(height, width)

    /**
     * The size a clip shown at [shown] is converted to: its longer edge brought to [maxEdge] at most, keeping its
     * proportions, never enlarged; each side even, as video encoders require.
     */
    fun converted(shown: PixelSize, maxEdge: Int): PixelSize {
        val scaled = PhotoPlan.scaled(shown, maxEdge)
        return PixelSize(even(scaled.width), even(scaled.height))
    }

    private fun even(side: Int): Int = (side - side % 2).coerceAtLeast(2)
}
