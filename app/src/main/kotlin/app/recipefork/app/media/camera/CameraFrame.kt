package app.recipefork.app.media.camera

/** A rectangle of pixels, [left] and [top] inclusive, [right] and [bottom] exclusive. */
data class PixelRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top

    /** The part of this rectangle inside [other], or an empty one where they do not meet. */
    fun intersect(other: PixelRect): PixelRect {
        val l = maxOf(left, other.left)
        val t = maxOf(top, other.top)
        val r = minOf(right, other.right).coerceAtLeast(l)
        val b = minOf(bottom, other.bottom).coerceAtLeast(t)
        return PixelRect(l, t, r, b)
    }
}

/**
 * The frame a recipe's photograph is taken in (FLOW-026, decisions/20261002-in-app-camera-with-camerax.md): 4:3 and
 * wider than tall as the author sees it, whichever way the camera's sensor lies. The viewfinder shows this frame, and
 * the picture taken is cut to the same one.
 */
object CameraFrame {
    /** The frame's width to its height, as seen. */
    const val WIDTH = 4
    const val HEIGHT = 3

    /** The frame's width over its height, for the viewfinder's layout. */
    const val ASPECT: Float = WIDTH.toFloat() / HEIGHT

    /** The longer side a cut picture keeps at least, where the camera gives as much: what processing shrinks it to. */
    const val LONGEST = 1600

    /**
     * The largest rectangle centred in [within] — in the coordinates of the picture as the sensor wrote it — that,
     * once turned by [rotationDegrees] to stand as the author held the phone, is [WIDTH]:[HEIGHT] and wider than tall.
     * A rectangle already that shape comes back as it is.
     */
    fun crop(within: PixelRect, rotationDegrees: Int): PixelRect {
        val turned = Math.floorMod(rotationDegrees, 180) != 0
        // Turned a quarter, the sensor's width is the frame's height.
        val ratioW = if (turned) HEIGHT else WIDTH
        val ratioH = if (turned) WIDTH else HEIGHT
        val w = within.width
        val h = within.height
        if (w <= 0 || h <= 0) return within
        val (cutW, cutH) = if (w.toLong() * ratioH > h.toLong() * ratioW) {
            (h.toLong() * ratioW / ratioH).toInt() to h
        } else {
            w to (w.toLong() * ratioH / ratioW).toInt()
        }
        val left = within.left + (w - cutW) / 2
        val top = within.top + (h - cutH) / 2
        return PixelRect(left, top, left + cutW, top + cutH)
    }

    /**
     * The power of two a [width] by [height] cut can be read at, shrunk, while its longer side stays at least
     * [longest] — the most memory reading it can save without losing what processing keeps.
     */
    fun sampleSize(width: Int, height: Int, longest: Int = LONGEST): Int {
        val side = maxOf(width, height)
        var sample = 1
        while (side / (sample * 2) >= longest) sample *= 2
        return sample
    }
}
