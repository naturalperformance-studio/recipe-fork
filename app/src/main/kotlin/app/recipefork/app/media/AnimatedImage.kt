package app.recipefork.app.media

import app.recipefork.core.recipe.VideoLimits
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * An animated GIF or WebP as its container lays it out: a canvas of [width] by [height] pixels and the [frames] drawn
 * on it one after another. Its pixels are not decoded here; each frame is handed out as a still image of its own
 * ([AnimationFrame.still]) for the platform's decoder, and composed onto the canvas by whoever plays it.
 */
class AnimatedImage(val width: Int, val height: Int, val frames: List<AnimationFrame>) {
    /**
     * How long it lasts once through: the sum of its frames' delays — or, for one longer than it was read up to
     * ([parse]'s `longest`), the sum up to the first frame that passes it.
     */
    val duration: Duration = frames.fold(Duration.ZERO) { sum, frame -> sum + frame.delay }

    companion object {
        /**
         * A delay this short or shorter is read as [DEFAULT_DELAY], as browsers read it: a GIF whose frames state
         * none, or one of 10 ms, was made to be shown at a tenth of a second per frame.
         */
        val SHORTEST_DELAY: Duration = 10.milliseconds

        /** What a frame stating a delay of [SHORTEST_DELAY] or less is shown for. */
        val DEFAULT_DELAY: Duration = 100.milliseconds

        /**
         * The most pixels an animation's canvas may have: a 4K frame's. Every frame is composed on a canvas this
         * large, and a hostile file may state any size its container can hold — 65535 pixels a side for a GIF.
         */
        const val MAX_CANVAS_PIXELS: Long = 3840L * 2160

        /**
         * The most a file read as an animation may weigh. A larger one is no animation this device will convert —
         * a clip from it would weigh more than [VideoLimits.MAX_BYTES] several times over.
         */
        const val MAX_BYTES: Int = 32 * 1024 * 1024

        /** The bytes enough to tell a GIF or a WebP from anything else: a WebP's RIFF header. */
        private const val SIGNATURE_BYTES = 12

        /**
         * The animation [input] holds, read from it only once its first bytes show a GIF or a WebP — a JPEG or a PNG
         * is never read past its signature — and only up to [maxBytes]: a file larger than that is no animation.
         * Null as [parse].
         */
        fun read(input: InputStream, maxBytes: Int = MAX_BYTES, longest: Duration = VideoLimits.MAX_DURATION): AnimatedImage? =
            (examine(input, maxBytes, longest) as? ImageKind.Animated)?.animation

        /**
         * What [input] holds, read as [read] reads it: the animation, or a still picture — or, for a GIF or a WebP
         * larger than [maxBytes], one too large to be read at all ([ImageKind.TooLarge]). A source that [states] its
         * size over [maxBytes] is told too large from its signature alone, nothing of it held in memory.
         */
        fun examine(
            input: InputStream,
            maxBytes: Int = MAX_BYTES,
            longest: Duration = VideoLimits.MAX_DURATION,
            states: Long? = null,
        ): ImageKind {
            val signature = ByteArray(SIGNATURE_BYTES)
            var read = 0
            while (read < SIGNATURE_BYTES) {
                val count = input.read(signature, read, SIGNATURE_BYTES - read)
                if (count < 0) break
                read += count
            }
            if (!Gif.matches(signature, read) && !WebP.matches(signature, read)) return ImageKind.Still
            if (states != null && states > maxBytes) return ImageKind.TooLarge
            val bytes = ByteArrayOutputStream(64 * 1024)
            bytes.write(signature, 0, read)
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (bytes.size() + count > maxBytes) return ImageKind.TooLarge
                bytes.write(buffer, 0, count)
            }
            return parse(bytes.toByteArray(), longest)?.let(ImageKind::Animated) ?: ImageKind.Still
        }

        /**
         * The animation [bytes] hold: an animated GIF or an animated WebP of two frames or more. Null for anything
         * else — a still GIF or WebP, a PNG, a JPEG, a file that is no image — for one too damaged to read a frame
         * of, one whose canvas has more than [MAX_CANVAS_PIXELS], and one with a frame reaching outside its canvas.
         * Its frames are read until their delays pass [longest]: what lasts longer than a clip may is not read to the
         * end, however many frames it holds.
         */
        fun parse(bytes: ByteArray, longest: Duration = VideoLimits.MAX_DURATION): AnimatedImage? {
            val frames = Frames(longest)
            val image = when {
                Gif.matches(bytes, bytes.size) -> Gif.parse(bytes, frames)
                WebP.matches(bytes, bytes.size) -> WebP.parse(bytes, frames)
                else -> null
            } ?: return null
            return image.takeIf { it.frames.size >= 2 }
        }

        internal fun delay(stated: Duration): Duration = if (stated <= SHORTEST_DELAY) DEFAULT_DELAY else stated
    }
}

/** What a picture offered to a step is: an animation, a still picture, or a GIF or WebP too large to be read. */
sealed interface ImageKind {
    /** An animated GIF or WebP, read. */
    class Animated(val animation: AnimatedImage) : ImageKind

    /** Anything else: a still GIF or WebP, a PNG, a JPEG, a file that is no image or cannot be read as an animation. */
    data object Still : ImageKind

    /** A GIF or a WebP larger than [AnimatedImage.MAX_BYTES]: no animation this device will convert. */
    data object TooLarge : ImageKind
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

/** The frames read of an animation, until their delays pass [longest]. */
private class Frames(private val longest: Duration) {
    val list = mutableListOf<AnimationFrame>()
    private var lasting = Duration.ZERO

    /** Whether a canvas of [width] by [height] pixels may be composed at all. */
    fun canvasFits(width: Int, height: Int): Boolean =
        width > 0 && height > 0 && width.toLong() * height <= AnimatedImage.MAX_CANVAS_PIXELS

    /**
     * Adds [frame] to those of a canvas of [width] by [height]; false when it reaches outside the canvas, and the
     * animation is not to be read at all.
     */
    fun add(frame: AnimationFrame, width: Int, height: Int): Boolean {
        if (frame.width <= 0 || frame.height <= 0 || frame.left < 0 || frame.top < 0) return false
        if (frame.left + frame.width > width || frame.top + frame.height > height) return false
        list += frame
        lasting += frame.delay
        return true
    }

    /** Whether the frames read already last longer than [longest]: the rest is not read. */
    val full: Boolean get() = lasting > longest
}

/** The GIF container (GIF89a): its blocks read, its image data left compressed. */
private object Gif {
    private const val EXTENSION = 0x21
    private const val IMAGE = 0x2C
    private const val TRAILER = 0x3B
    private const val GRAPHIC_CONTROL = 0xF9

    fun matches(bytes: ByteArray, length: Int): Boolean =
        length >= 6 && String(bytes, 0, 6, Charsets.ISO_8859_1).let { it == "GIF89a" || it == "GIF87a" }

    fun parse(bytes: ByteArray, frames: Frames): AnimatedImage? {
        if (bytes.size < 13) return null
        val reader = Reader(bytes)
        reader.skip(6)
        val width = reader.u16()
        val height = reader.u16()
        if (!frames.canvasFits(width, height)) return null
        val packed = reader.u8()
        reader.skip(2)
        val globalTable = if (packed and 0x80 != 0) 3 * (1 shl ((packed and 7) + 1)) else 0
        if (globalTable > reader.remaining) return null
        val palette = reader.position until reader.position + globalTable
        reader.skip(globalTable)
        var control: IntRange? = null
        while (reader.remaining > 0 && !frames.full) {
            when (reader.u8()) {
                EXTENSION -> {
                    val label = reader.u8()
                    val start = reader.position
                    if (!reader.skipSubBlocks()) break
                    if (label == GRAPHIC_CONTROL) control = start until reader.position
                }
                IMAGE -> {
                    if (reader.remaining < 9) break
                    val left = reader.u16()
                    val top = reader.u16()
                    val frameWidth = reader.u16()
                    val frameHeight = reader.u16()
                    val start = reader.position
                    val framePacked = reader.u8()
                    val localTable = if (framePacked and 0x80 != 0) 3 * (1 shl ((framePacked and 7) + 1)) else 0
                    if (localTable + 1 > reader.remaining) break
                    reader.skip(localTable + 1)
                    if (!reader.skipSubBlocks()) break
                    val image = start until reader.position // its flags, colour table and data
                    // The graphic control block: size 4, flags, delay in hundredths of a second, transparent index.
                    val gce = control?.takeIf { it.count() >= 5 }
                    control = null
                    val flags = gce?.let { bytes[it.first + 1].toInt() and 0xff } ?: 0
                    val hundredths = gce?.let { (bytes[it.first + 2].toInt() and 0xff) or ((bytes[it.first + 3].toInt() and 0xff) shl 8) } ?: 0
                    val transparent = gce?.let { bytes[it.first + 4] }
                    val disposal = when ((flags shr 2) and 7) {
                        2 -> Disposal.BACKGROUND
                        3 -> Disposal.PREVIOUS
                        else -> Disposal.KEEP
                    }
                    val frame = AnimationFrame(
                        left, top, frameWidth, frameHeight, AnimatedImage.delay((hundredths * 10).milliseconds), disposal, blends = true,
                    ) { still(bytes, packed, palette, flags and 0x01, transparent, frameWidth, frameHeight, image) }
                    if (!frames.add(frame, width, height)) return null
                }
                else -> break // the trailer, or a block no GIF has
            }
        }
        return AnimatedImage(width, height, frames.list)
    }

    /** A GIF of the one frame: the animation's colour table, the frame's transparency, and its image at 0, 0. */
    private fun still(
        bytes: ByteArray,
        packed: Int,
        palette: IntRange,
        transparency: Int,
        transparent: Byte?,
        width: Int,
        height: Int,
        image: IntRange,
    ): ByteArray {
        val out = ByteArrayOutputStream(32 + palette.count() + image.count())
        out.write("GIF89a".toByteArray(Charsets.ISO_8859_1))
        out.u16(width)
        out.u16(height)
        out.write(packed) // its colour table's flags
        out.write(0) // no background colour
        out.write(0) // no aspect ratio
        out.write(bytes, palette.first, palette.count())
        if (transparent != null) {
            out.write(EXTENSION)
            out.write(GRAPHIC_CONTROL)
            out.write(4)
            out.write(transparency) // the transparency flag alone; no delay, no disposal
            out.u16(0)
            out.write(transparent.toInt())
            out.write(0)
        }
        out.write(IMAGE)
        out.u16(0)
        out.u16(0)
        out.u16(width)
        out.u16(height)
        out.write(bytes, image.first, image.count())
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

    fun matches(bytes: ByteArray, length: Int): Boolean =
        length >= 12 && fourCc(bytes, 0) == "RIFF" && fourCc(bytes, 8) == "WEBP"

    fun parse(bytes: ByteArray, frames: Frames): AnimatedImage? {
        val header = chunkAt(bytes, 12, bytes.size) ?: return null
        if (header.type != "VP8X" || header.size < 10) return null
        if (bytes[header.start].toInt() and ANIMATION_FLAG == 0) return null
        val width = u24(bytes, header.start + 4) + 1
        val height = u24(bytes, header.start + 7) + 1
        if (!frames.canvasFits(width, height)) return null
        var position = header.next
        while (!frames.full) {
            val chunk = chunkAt(bytes, position, bytes.size) ?: break
            position = chunk.next
            if (chunk.type != "ANMF" || chunk.size < 16) continue
            val at = chunk.start
            val frameWidth = u24(bytes, at + 6) + 1
            val frameHeight = u24(bytes, at + 9) + 1
            val flags = bytes[at + 15].toInt()
            val frame = AnimationFrame(
                left = u24(bytes, at) * 2,
                top = u24(bytes, at + 3) * 2,
                width = frameWidth,
                height = frameHeight,
                delay = AnimatedImage.delay(u24(bytes, at + 12).milliseconds),
                disposal = if (flags and 0x01 != 0) Disposal.BACKGROUND else Disposal.KEEP,
                blends = flags and 0x02 == 0,
            ) { still(bytes, chunk, frameWidth, frameHeight) }
            if (!frames.add(frame, width, height)) return null
        }
        return AnimatedImage(width, height, frames.list)
    }

    /** A WebP of the one frame in [frame]: an extended header of its size, then its alpha and picture as they were. */
    private fun still(bytes: ByteArray, frame: Chunk, width: Int, height: Int): ByteArray {
        val data = mutableListOf<Chunk>()
        var position = frame.start + 16
        while (true) {
            val chunk = chunkAt(bytes, position, frame.start + frame.size) ?: break
            if (chunk.type == "ALPH" || chunk.type == "VP8 " || chunk.type == "VP8L") data += chunk
            position = chunk.next
        }
        val body = ByteArrayOutputStream()
        body.write("VP8X".toByteArray(Charsets.ISO_8859_1))
        body.u32(10)
        body.write(if (data.any { it.type == "ALPH" }) ALPHA_FLAG else 0)
        body.write(ByteArray(3))
        body.u24(width - 1)
        body.u24(height - 1)
        for (chunk in data) body.write(bytes, chunk.start - 8, 8 + chunk.size + (chunk.size and 1).coerceAtMost(bytes.size - chunk.start - chunk.size))
        val out = ByteArrayOutputStream(12 + body.size())
        out.write("RIFF".toByteArray(Charsets.ISO_8859_1))
        out.u32(4 + body.size())
        out.write("WEBP".toByteArray(Charsets.ISO_8859_1))
        body.writeTo(out)
        return out.toByteArray()
    }

    /** A chunk whose [size] bytes of contents begin at [start]; the next chunk, if any, begins at [next]. */
    private class Chunk(val type: String, val start: Int, val size: Int) {
        val next: Int get() = start + size + (size and 1)
    }

    /** The chunk at [position], if one lies whole before [until]. */
    private fun chunkAt(bytes: ByteArray, position: Int, until: Int): Chunk? {
        val end = minOf(until, bytes.size)
        if (position < 0 || position + 8 > end) return null
        val size = u32(bytes, position + 4)
        if (size > end - position - 8) return null
        return Chunk(fourCc(bytes, position), position + 8, size.toInt())
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
