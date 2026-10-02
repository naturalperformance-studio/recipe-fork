package app.recipefork.app.media.crop

import app.recipefork.app.media.Orientation
import app.recipefork.app.media.PixelSize
import kotlin.math.roundToInt

/** A rectangle of pixels, [left] and [top] inclusive, [right] and [bottom] exclusive. */
data class PixelRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

/**
 * The crop's frame over a photograph, in the photograph's own pixels as it stands upright: its top-left corner and
 * its width; its height follows from [CropGeometry]'s 4:3.
 */
data class CropBox(val left: Double, val top: Double, val width: Double) {
    val height: Double get() = width * CropGeometry.HEIGHT / CropGeometry.WIDTH
}

/**
 * The arithmetic of the crop a photograph goes through before it is attached (FLOW-026): a 4:3 frame, wider than
 * tall, over the photograph standing upright. The frame never leaves the photograph, and never gets narrower than
 * [MIN_WIDTH] pixels of it where the photograph is that large — else it is the largest frame that fits.
 */
object CropGeometry {
    const val WIDTH = 4
    const val HEIGHT = 3

    /** The frame's width over its height. */
    const val ASPECT: Float = WIDTH.toFloat() / HEIGHT

    /** The narrowest the frame gets, in the photograph's pixels: what processing keeps of a photograph's longer edge. */
    const val MIN_WIDTH = 1600

    /** The widest frame [source] holds. */
    fun maxWidth(source: PixelSize): Double = minOf(source.width.toDouble(), source.height.toDouble() * WIDTH / HEIGHT)

    /** The narrowest the frame may get over [source]. */
    fun minWidth(source: PixelSize): Double = minOf(MIN_WIDTH.toDouble(), maxWidth(source))

    /** The frame a crop opens with: the widest, centred. */
    fun initial(source: PixelSize): CropBox {
        val width = maxWidth(source)
        val height = width * HEIGHT / WIDTH
        return CropBox((source.width - width) / 2, (source.height - height) / 2, width)
    }

    /** [box] brought within its limits over [source]: no narrower or wider than allowed, and inside the photograph. */
    fun clamp(box: CropBox, source: PixelSize): CropBox {
        val width = box.width.coerceIn(minWidth(source), maxWidth(source))
        val height = width * HEIGHT / WIDTH
        return CropBox(
            box.left.coerceIn(0.0, maxOf(0.0, source.width - width)),
            box.top.coerceIn(0.0, maxOf(0.0, source.height - height)),
            width,
        )
    }

    /**
     * [box] after one step of the author's gesture over the frame, which is [frameWidth] screen pixels wide and holds
     * the photograph under it: the fingers' centre, now at ([centroidX], [centroidY]) from the frame's top-left corner,
     * moved by ([panX], [panY]) and spread by [zoom]. The photograph follows the fingers — the point that was under them
     * stays under them — and the result is [clamp]ed.
     */
    fun moved(
        box: CropBox,
        source: PixelSize,
        frameWidth: Float,
        centroidX: Float,
        centroidY: Float,
        panX: Float,
        panY: Float,
        zoom: Float,
    ): CropBox {
        if (frameWidth <= 0f) return box
        val perScreen = box.width / frameWidth
        // The photograph's point that was under the fingers before this step.
        val x = box.left + (centroidX - panX) * perScreen
        val y = box.top + (centroidY - panY) * perScreen
        val width = (box.width / (if (zoom > 0f) zoom else 1f)).coerceIn(minWidth(source), maxWidth(source))
        val after = width / frameWidth
        return clamp(CropBox(x - centroidX * after, y - centroidY * after, width), source)
    }

    /** [box] as whole pixels of [source], 4:3 and inside it. */
    fun pixels(box: CropBox, source: PixelSize): PixelRect {
        val clamped = clamp(box, source)
        val width = clamped.width.roundToInt().coerceIn(1, source.width)
        val height = (width.toLong() * HEIGHT / WIDTH).toInt().coerceIn(1, source.height)
        val left = clamped.left.roundToInt().coerceIn(0, source.width - width)
        val top = clamped.top.roundToInt().coerceIn(0, source.height - height)
        return PixelRect(left, top, left + width, top + height)
    }

    /** The size [raw] — a picture as its file holds it — stands at once turned by [orientation]. */
    fun upright(raw: PixelSize, orientation: Orientation): PixelSize =
        if (orientation.swapsSides) PixelSize(raw.height, raw.width) else raw

    /**
     * [rect], in the pixels of the picture standing upright, as the pixels of [raw] — the picture as its file holds
     * it — that [orientation] turns into it: the region to read from the file.
     */
    fun toRaw(rect: PixelRect, raw: PixelSize, orientation: Orientation): PixelRect {
        val a = rawPoint(rect.left, rect.top, raw, orientation)
        val b = rawPoint(rect.right, rect.bottom, raw, orientation)
        return PixelRect(minOf(a.first, b.first), minOf(a.second, b.second), maxOf(a.first, b.first), maxOf(a.second, b.second))
    }

    /** An upright corner (u, v) as a corner of [raw]: the turn undone, then the mirroring. */
    private fun rawPoint(u: Int, v: Int, raw: PixelSize, orientation: Orientation): Pair<Int, Int> {
        val w = raw.width
        val h = raw.height
        val (x, y) = when (Math.floorMod(orientation.degrees, 360)) {
            90 -> v to h - u
            180 -> w - u to h - v
            270 -> w - v to u
            else -> u to v
        }
        return (if (orientation.mirrored) w - x else x) to y
    }

    /**
     * The power of two a [width] by [height] region can be read at, shrunk, while its longer side stays at least
     * [longest] — the most memory reading it saves without losing what processing keeps.
     */
    fun sampleSize(width: Int, height: Int, longest: Int = MIN_WIDTH): Int {
        val side = maxOf(width, height)
        var sample = 1
        while (side / (sample * 2) >= longest) sample *= 2
        return sample
    }
}
