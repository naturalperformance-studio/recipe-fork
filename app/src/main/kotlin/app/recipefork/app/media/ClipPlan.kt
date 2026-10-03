package app.recipefork.app.media

/** The arithmetic of a clip's conversion (MEDIA-004), kept apart from the codec so that it is tested on its own. */
object ClipPlan {
    /**
     * Video bitrate of a converted clip, in bits per second. The limits allow about 3.2 Mbit/s over the longest clip;
     * 2 Mbit/s serves 720p well and lands a 15 second clip near 3.8 MB, leaving room for an encoder that overshoots.
     */
    const val BITRATE: Int = 2_000_000

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
