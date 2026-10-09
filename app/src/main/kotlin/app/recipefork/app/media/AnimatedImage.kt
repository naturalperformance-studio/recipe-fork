package app.recipefork.app.media

import java.io.ByteArrayOutputStream
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * An animated GIF or WebP as its container lays it out: a canvas of [width] by [height] pixels and the [frames] drawn
 * on it one after another. Its pixels are not decoded here; each frame is handed out as a still image of its own
 * ([AnimationFrame.still]) for the platform's decoder, and composed onto the canvas by whoever plays it.
 */
class AnimatedImage(val width: Int, val height: Int, val frames: List<AnimationFrame>) {
    /** How long it lasts once through: the sum of its frames' delays. */
    val duration: Duration get() = frames.fold(Duration.ZERO) { sum, frame -> sum + frame.delay }

    companion object {
        /**
         * A delay this short or shorter is read as [DEFAULT_DELAY], as browsers read it: a GIF whose frames state
         * none, or one of 10 ms, was made to be shown at a tenth of a second per frame.
         */
        val SHORTEST_DELAY: Duration = 10.milliseconds

        /** What a frame stating a delay of [SHORTEST_DELAY] or less is shown for. */
        val DEFAULT_DELAY: Duration = 100.milliseconds

        /**
         * The animation [bytes] hold: an animated GIF or an animated WebP of two frames or more. Null for anything
         * else — a still GIF or WebP, a PNG, a JPEG, a file that is no image — and for one too damaged to read a
         * frame of.
         */
        fun parse(bytes: ByteArray): AnimatedImage? = when {
            Gif.matches(bytes) -> Gif.parse(bytes)
            WebP.matches(bytes) -> WebP.parse(bytes)
            else -> null
        }?.takeIf { it.frames.size >= 2 && it.width > 0 && it.height > 0 }

        internal fun delay(stated: Duration): Duration = if (stated <= SHORTEST_DELAY) DEFAULT_DELAY else stated
    }
}

/**
 * One frame of an [AnimatedImage]: a picture of [width] by [height] pixels drawn at [left], [top] of the canvas and
 * shown for [delay]; drawn over what is there if it [blends], else replacing it; and afterwards left as it is or
 * cleared away as [disposal] says.
 */
class AnimationFrame(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
    val delay: Duration,
    val disposal: Disposal,
    val blends: Boolean,
    private val encode: () -> ByteArray,
) {
    /** The frame alone, as a still image of its own size in the animation's format, for the platform to decode. */
    fun still(): ByteArray = encode()
}

/** What becomes of a frame's area once it has been shown, before the next frame is drawn. */
enum class Disposal {
    /** It stays as drawn. */
    KEEP,

    /** It is cleared to transparent. */
    BACKGROUND,

    /** It returns to what it was before the frame was drawn. */
    PREVIOUS,
}

/** The GIF container (GIF89a): its blocks read, its image data left compressed. */
private object Gif {
    private const val EXTENSION = 0x21
    private const val IMAGE = 0x2C
    private const val TRAILER = 0x3B
    private const val GRAPHIC_CONTROL = 0xF9

    fun matches(bytes: ByteArray): Boolean =
        bytes.size >= 13 && String(bytes, 0, 6, Charsets.ISO_8859_1).let { it == "GIF89a" || it == "GIF87a" }

    fun parse(bytes: ByteArray): AnimatedImage? {
        val reader = Reader(bytes)
        reader.skip(6)
        val width = reader.u16()
        val height = reader.u16()
        val packed = reader.u8()
        reader.skip(2)
        val globalTable = if (packed and 0x80 != 0) 3 * (1 shl ((packed and 7) + 1)) else 0
        val screen = bytes.copyOfRange(10, 13).also { it[1] = 0; it[2] = 0 } // its colour table's flags; no background
        val palette = reader.bytes(globalTable) ?: return null
        val frames = mutableListOf<AnimationFrame>()
        var control: ByteArray? = null
        while (reader.remaining > 0) {
            when (reader.u8()) {
                EXTENSION -> {
                    val label = reader.u8()
                    if (label == GRAPHIC_CONTROL) {
                        val start = reader.position
                        if (!reader.skipSubBlocks()) break
                        control = bytes.copyOfRange(start, reader.position)
                    } else if (!reader.skipSubBlocks()) {
                        break
                    }
                }
                IMAGE -> {
                    val start = reader.position
                    val left = reader.u16()
                    val top = reader.u16()
                    val frameWidth = reader.u16()
                    val frameHeight = reader.u16()
                    val framePacked = reader.u8()
                    val localTable = if (framePacked and 0x80 != 0) 3 * (1 shl ((framePacked and 7) + 1)) else 0
                    if (reader.bytes(localTable + 1) == null || !reader.skipSubBlocks()) break
                    if (frameWidth == 0 || frameHeight == 0) return null
                    val image = bytes.copyOfRange(start + 8, reader.position) // its flags, colour table and data
                    val gce = control
                    control = null
                    // The graphic control block: size 4, flags, delay in hundredths of a second, transparent index.
                    val flags = gce?.getOrNull(1)?.toInt()?.and(0xff) ?: 0
                    val hundredths = if (gce != null && gce.size >= 4) (gce[2].toInt() and 0xff) or ((gce[3].toInt() and 0xff) shl 8) else 0
                    val disposal = when ((flags shr 2) and 7) {
                        2 -> Disposal.BACKGROUND
                        3 -> Disposal.PREVIOUS
                        else -> Disposal.KEEP
                    }
                    frames += AnimationFrame(
                        left, top, frameWidth, frameHeight, AnimatedImage.delay((hundredths * 10).milliseconds), disposal, blends = true,
                    ) { still(screen, palette, gce, frameWidth, frameHeight, image) }
                }
                TRAILER -> break
                else -> break
            }
        }
        return AnimatedImage(width, height, frames)
    }

    /** A GIF of the one frame: the animation's colour table, the frame's transparency, and its image at 0, 0. */
    private fun still(screen: ByteArray, palette: ByteArray, control: ByteArray?, width: Int, height: Int, image: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(32 + palette.size + image.size)
        out.write("GIF89a".toByteArray(Charsets.ISO_8859_1))
        out.u16(width)
        out.u16(height)
        out.write(screen)
        out.write(palette)
        if (control != null && control.size >= 5) {
            out.write(EXTENSION)
            out.write(GRAPHIC_CONTROL)
            out.write(4)
            out.write(control[1].toInt() and 0x01) // the transparency flag alone; no delay, no disposal
            out.u16(0)
            out.write(control[4].toInt())
            out.write(0)
        }
        out.write(IMAGE)
        out.u16(0)
        out.u16(0)
        out.u16(width)
        out.u16(height)
        out.write(image)
        out.write(TRAILER)
        return out.toByteArray()
    }

    private fun ByteArrayOutputStream.u16(value: Int) {
        write(value and 0xff)
        write((value shr 8) and 0xff)
    }

    private class Reader(private val bytes: ByteArray) {
        var position = 0
        val remaining get() = bytes.size - position

        fun u8(): Int = if (position < bytes.size) bytes[position++].toInt() and 0xff else { position++; -1 }

        fun u16(): Int = u8() or (u8() shl 8)

        fun skip(count: Int) {
            position += count
        }

        fun bytes(count: Int): ByteArray? =
            if (count > remaining) null else bytes.copyOfRange(position, position + count).also { position += count }

        /** Skips a run of data sub-blocks and its terminator; false if the file ends first. */
        fun skipSubBlocks(): Boolean {
            while (true) {
                if (remaining <= 0) return false
                val size = u8()
                if (size == 0) return true
                if (size > remaining) return false
                position += size
            }
        }
    }
}

/** The WebP container (RIFF): an extended file whose frames are ANMF chunks. */
private object WebP {
    private const val ANIMATION_FLAG = 0x02
    private const val ALPHA_FLAG = 0x10

    fun matches(bytes: ByteArray): Boolean =
        bytes.size >= 16 && fourCc(bytes, 0) == "RIFF" && fourCc(bytes, 8) == "WEBP"

    fun parse(bytes: ByteArray): AnimatedImage? {
        val chunks = chunks(bytes, 12, bytes.size)
        val header = chunks.firstOrNull() ?: return null
        if (header.type != "VP8X" || header.size < 10) return null
        if (bytes[header.start].toInt() and ANIMATION_FLAG == 0) return null
        val width = u24(bytes, header.start + 4) + 1
        val height = u24(bytes, header.start + 7) + 1
        val frames = chunks.filter { it.type == "ANMF" && it.size >= 16 }.map { chunk ->
            val at = chunk.start
            val frameWidth = u24(bytes, at + 6) + 1
            val frameHeight = u24(bytes, at + 9) + 1
            val flags = bytes[at + 15].toInt()
            val data = chunks(bytes, at + 16, at + chunk.size).filter { it.type == "ALPH" || it.type == "VP8 " || it.type == "VP8L" }
            AnimationFrame(
                left = u24(bytes, at) * 2,
                top = u24(bytes, at + 3) * 2,
                width = frameWidth,
                height = frameHeight,
                delay = AnimatedImage.delay(u24(bytes, at + 12).milliseconds),
                disposal = if (flags and 0x01 != 0) Disposal.BACKGROUND else Disposal.KEEP,
                blends = flags and 0x02 == 0,
            ) { still(bytes, data, frameWidth, frameHeight) }
        }
        return AnimatedImage(width, height, frames)
    }

    /** A WebP of the one frame: an extended header of its size, then its alpha and picture chunks as they were. */
    private fun still(bytes: ByteArray, data: List<Chunk>, width: Int, height: Int): ByteArray {
        val body = ByteArrayOutputStream()
        body.write("VP8X".toByteArray(Charsets.ISO_8859_1))
        body.u32(10)
        body.write(if (data.any { it.type == "ALPH" }) ALPHA_FLAG else 0)
        body.write(ByteArray(3))
        body.u24(width - 1)
        body.u24(height - 1)
        for (chunk in data) body.write(bytes, chunk.start - 8, 8 + chunk.size + (chunk.size and 1))
        val out = ByteArrayOutputStream(12 + body.size())
        out.write("RIFF".toByteArray(Charsets.ISO_8859_1))
        out.u32(4 + body.size())
        out.write("WEBP".toByteArray(Charsets.ISO_8859_1))
        body.writeTo(out)
        return out.toByteArray()
    }

    /** A chunk whose [size] bytes of contents begin at [start]. */
    private class Chunk(val type: String, val start: Int, val size: Int)

    /** The chunks between [from] and [until]; one running past the end ends the list. */
    private fun chunks(bytes: ByteArray, from: Int, until: Int): List<Chunk> {
        val chunks = mutableListOf<Chunk>()
        var position = from
        val end = minOf(until, bytes.size)
        while (position + 8 <= end) {
            val size = u32(bytes, position + 4)
            if (size < 0 || size > end - position - 8) break
            chunks += Chunk(fourCc(bytes, position), position + 8, size.toInt())
            position += 8 + size.toInt() + (size.toInt() and 1)
        }
        return chunks
    }

    private fun fourCc(bytes: ByteArray, at: Int) = String(bytes, at, 4, Charsets.ISO_8859_1)

    private fun u24(bytes: ByteArray, at: Int): Int =
        (bytes[at].toInt() and 0xff) or ((bytes[at + 1].toInt() and 0xff) shl 8) or ((bytes[at + 2].toInt() and 0xff) shl 16)

    private fun u32(bytes: ByteArray, at: Int): Long = (u24(bytes, at).toLong()) or ((bytes[at + 3].toLong() and 0xff) shl 24)

    private fun ByteArrayOutputStream.u24(value: Int) {
        write(value and 0xff)
        write((value shr 8) and 0xff)
        write((value shr 16) and 0xff)
    }

    private fun ByteArrayOutputStream.u32(value: Int) {
        u24(value)
        write((value shr 24) and 0xff)
    }
}
