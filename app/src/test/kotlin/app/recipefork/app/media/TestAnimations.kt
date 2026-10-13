package app.recipefork.app.media

import java.io.ByteArrayOutputStream

// GIFs and WebPs written out for the media tests (AnimatedImageTest, AnimationClipsTest): their frames' timing and
// placement as asked, their image data a placeholder the reader never decodes.

/** A GIF frame: its delay in hundredths (null for no graphic control block), disposal, place and transparency. */
internal class Frame(
    val hundredths: Int?,
    val disposal: Int = 1,
    val left: Int = 0,
    val top: Int = 0,
    val width: Int = 8,
    val height: Int = 8,
    val transparent: Boolean = false,
)

/** A GIF of [frames] on an 8 x 8 canvas; their image data is a placeholder the reader never decodes. */
internal class TestGif(private val frames: List<Frame>, private val width: Int = 8, private val height: Int = 8) {
    fun bytes(loops: Boolean = false): ByteArray {
        val out = ByteArrayOutputStream()
        out.write("GIF89a".toByteArray())
        out.u16(width)
        out.u16(height)
        out.write(0x80) // a global colour table of two colours
        out.write(0)
        out.write(0)
        out.write(byteArrayOf(0, 0, 0, -1, -1, -1))
        if (loops) {
            out.write(byteArrayOf(0x21, 0xFF.toByte(), 11))
            out.write("NETSCAPE2.0".toByteArray())
            out.write(byteArrayOf(3, 1, 0, 0, 0))
        }
        for (frame in frames) {
            frame.hundredths?.let { delay ->
                out.write(byteArrayOf(0x21, 0xF9.toByte(), 4))
                out.write((frame.disposal shl 2) or (if (frame.transparent) 1 else 0))
                out.u16(delay)
                out.write(1)
                out.write(0)
            }
            out.write(0x2C)
            out.u16(frame.left)
            out.u16(frame.top)
            out.u16(frame.width)
            out.u16(frame.height)
            out.write(0)
            out.write(2) // the code size, then one sub-block of data and the terminator
            out.write(byteArrayOf(3, 0x44, 0x01, 0x05, 0))
        }
        out.write(0x3B)
        return out.toByteArray()
    }
}

/** A WebP of frames lasting [durations] ms with [flags] (blending and disposal), animated if [animated]. */
internal class TestWebp(
    private val durations: List<Int>,
    private val animated: Boolean = true,
    private val flags: List<Int>? = null,
    private val canvas: Int = 8,
    private val frame: Int = 8,
) {
    fun bytes(): ByteArray {
        val body = ByteArrayOutputStream()
        body.chunk(
            "VP8X",
            ByteArrayOutputStream().apply {
                write(if (animated) 0x02 else 0)
                write(ByteArray(3))
                u24(canvas - 1)
                u24(canvas - 1)
            }.toByteArray(),
        )
        body.chunk("ANIM", ByteArray(6))
        durations.forEachIndexed { i, duration ->
            val anmf = ByteArrayOutputStream()
            anmf.u24(0)
            anmf.u24(0)
            anmf.u24(frame - 1)
            anmf.u24(frame - 1)
            anmf.u24(duration)
            anmf.write(flags?.get(i) ?: 0)
            anmf.chunk("VP8L", byteArrayOf(0x2F, 7, 0xC0.toByte(), 1, 0))
            body.chunk("ANMF", anmf.toByteArray())
        }
        val out = ByteArrayOutputStream()
        out.write("RIFF".toByteArray())
        out.u32(4 + body.size())
        out.write("WEBP".toByteArray())
        body.writeTo(out)
        return out.toByteArray()
    }
}

internal fun ByteArrayOutputStream.u16(value: Int) {
    write(value and 0xff)
    write((value shr 8) and 0xff)
}

internal fun ByteArrayOutputStream.u24(value: Int) {
    u16(value)
    write((value shr 16) and 0xff)
}

internal fun ByteArrayOutputStream.u32(value: Int) {
    u24(value)
    write((value shr 24) and 0xff)
}

internal fun ByteArrayOutputStream.chunk(type: String, contents: ByteArray) {
    write(type.toByteArray())
    u32(contents.size)
    write(contents)
    if (contents.size % 2 == 1) write(0)
}
