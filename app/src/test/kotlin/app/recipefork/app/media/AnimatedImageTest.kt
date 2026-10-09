package app.recipefork.app.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Which images are animations and how long they last (scenario 3): an animated GIF or WebP is read frame by frame and
 * lasts the sum of its frames' delays; a still GIF or WebP, a PNG and a JPEG are no animation.
 *
 * The files under resources/animation were made with Pillow: animated.gif and animated.webp are a red square moving
 * across a blue 160 x 120 canvas in ten frames, the GIF's delays 100, 150, 200, 250, 100, 150, 200, 250, 100, 200 ms
 * (1.7 s), the WebP's (lossy, with alpha) 120, 80, 200, 160, 40, 300, 100, 90, 110, 200 ms (1.4 s); long.gif and
 * long.webp are two frames of 8 s each; the still ones are one picture each.
 */
class AnimatedImageTest {
    private fun fixture(name: String): ByteArray =
        checkNotNull(javaClass.getResourceAsStream("/animation/$name")) { "no fixture $name" }.use { it.readBytes() }

    private fun animation(name: String): AnimatedImage = AnimatedImage.parse(fixture(name)) ?: throw AssertionError("$name is not animated")

    @Test
    fun anAnimatedGifIsAnAnimationLastingTheSumOfItsDelays() {
        val gif = animation("animated.gif")

        assertEquals(160, gif.width)
        assertEquals(120, gif.height)
        assertEquals(10, gif.frames.size)
        assertEquals(listOf(100, 150, 200, 250, 100, 150, 200, 250, 100, 200), gif.frames.map { it.delay.inWholeMilliseconds.toInt() })
        assertEquals(1700.milliseconds, gif.duration)
    }

    @Test
    fun anAnimatedWebpIsAnAnimationLastingTheSumOfItsDurations() {
        val webp = animation("animated.webp")

        assertEquals(160, webp.width)
        assertEquals(120, webp.height)
        assertEquals(listOf(120, 80, 200, 160, 40, 300, 100, 90, 110, 200), webp.frames.map { it.delay.inWholeMilliseconds.toInt() })
        assertEquals(1400.milliseconds, webp.duration)
        // The first frame covers the canvas and replaces it; the moving square's frames are drawn over what is there.
        val first = webp.frames.first()
        assertEquals(listOf(0, 0, 160, 120), listOf(first.left, first.top, first.width, first.height))
        assertFalse(first.blends)
        assertTrue(webp.frames.any { it.blends && it.width < 160 })
    }

    @Test
    fun animationsOverFifteenSecondsLastTheirFullLength() {
        assertEquals(16.seconds, animation("long.gif").duration)
        assertEquals(16.seconds, animation("long.webp").duration)
    }

    @Test
    fun stillImagesAndOtherFilesAreNoAnimation() {
        listOf("still.gif", "still.webp", "still-alpha.webp", "still.png", "still.jpg").forEach { name ->
            assertNull(name, AnimatedImage.parse(fixture(name)))
        }
        assertNull(AnimatedImage.parse(ByteArray(0)))
        assertNull(AnimatedImage.parse("GIF89a".toByteArray()))
        assertNull(AnimatedImage.parse(ByteArray(4000) { (it * 31).toByte() }))
    }

    @Test
    fun aGifOfOneFrameIsStillThoughItSaysItLoops() {
        assertNull(AnimatedImage.parse(TestGif(listOf(Frame(50))).bytes(loops = true)))
    }

    @Test
    fun aWebpMarkedAnimatedWithOneFrameIsStill() {
        assertNull(AnimatedImage.parse(TestWebp(listOf(500)).bytes()))
        assertNull(AnimatedImage.parse(TestWebp(listOf(500, 500), animated = false).bytes()))
    }

    @Test
    fun aDelayOfTenMillisecondsOrLessIsReadAsATenthOfASecondAsBrowsersDo() {
        // GIF delays are in hundredths: 0, 1 (10 ms), 2 (20 ms), 7 (70 ms).
        val gif = AnimatedImage.parse(TestGif(listOf(Frame(0), Frame(1), Frame(2), Frame(7))).bytes())!!
        assertEquals(listOf(100, 100, 20, 70), gif.frames.map { it.delay.inWholeMilliseconds.toInt() })
        assertEquals(290.milliseconds, gif.duration)

        val webp = AnimatedImage.parse(TestWebp(listOf(0, 10, 11, 33)).bytes())!!
        assertEquals(listOf(100, 100, 11, 33), webp.frames.map { it.delay.inWholeMilliseconds.toInt() })
        assertEquals(244.milliseconds, webp.duration)
    }

    @Test
    fun aGifFrameWithoutAGraphicControlBlockLastsATenthOfASecond() {
        val gif = AnimatedImage.parse(TestGif(listOf(Frame(null), Frame(30))).bytes())!!
        assertEquals(400.milliseconds, gif.duration)
    }

    @Test
    fun aGifStatesHowEachFrameIsDisposedOf() {
        val gif = AnimatedImage.parse(TestGif((0..4).map { Frame(10, disposal = it) }).bytes())!!
        assertEquals(
            listOf(Disposal.KEEP, Disposal.KEEP, Disposal.BACKGROUND, Disposal.PREVIOUS, Disposal.KEEP),
            gif.frames.map { it.disposal },
        )
        assertTrue(gif.frames.all { it.blends })
    }

    @Test
    fun aWebpStatesHowEachFrameIsBlendedAndDisposedOf() {
        val webp = AnimatedImage.parse(TestWebp(listOf(100, 100, 100, 100), flags = listOf(0, 1, 2, 3)).bytes())!!
        assertEquals(listOf(Disposal.KEEP, Disposal.BACKGROUND, Disposal.KEEP, Disposal.BACKGROUND), webp.frames.map { it.disposal })
        assertEquals(listOf(true, true, false, false), webp.frames.map { it.blends })
    }

    @Test
    fun aTruncatedGifKeepsTheFramesReadWhole() {
        val whole = TestGif(listOf(Frame(10), Frame(10), Frame(10))).bytes()
        val cut = whole.copyOf(whole.size - 8)
        assertEquals(2, AnimatedImage.parse(cut)!!.frames.size)
    }

    @Test
    fun aGifFrameIsHandedOutAsAStillGifOfItsOwnSizeAtTheOrigin() {
        val gif = AnimatedImage.parse(TestGif(listOf(Frame(10), Frame(10, left = 3, top = 2, width = 4, height = 5, transparent = true))).bytes())!!
        val frame = gif.frames[1]
        assertEquals(listOf(3, 2, 4, 5), listOf(frame.left, frame.top, frame.width, frame.height))

        val still = frame.still()

        assertEquals("GIF89a", String(still, 0, 6, Charsets.ISO_8859_1))
        assertEquals(4, u16(still, 6))
        assertEquals(5, u16(still, 8))
        assertNull("a still GIF is no animation", AnimatedImage.parse(still))
        // Its graphic control block keeps the transparency and nothing else; its image sits at 0, 0.
        val control = indexOf(still, byteArrayOf(0x21, 0xF9.toByte(), 4))
        assertEquals(0x01, still[control + 3].toInt())
        assertEquals(0, u16(still, control + 4))
        val image = indexOf(still, byteArrayOf(0x2C))
        assertEquals(listOf(0, 0, 4, 5), listOf(u16(still, image + 1), u16(still, image + 3), u16(still, image + 5), u16(still, image + 7)))
        assertEquals(0x3B, still.last().toInt())
    }

    @Test
    fun aWebpFrameIsHandedOutAsAStillWebpOfItsOwnSize() {
        val webp = animation("animated.webp")
        val frame = webp.frames.first { it.width < 160 }

        val still = frame.still()

        assertEquals("RIFF", String(still, 0, 4, Charsets.ISO_8859_1))
        assertEquals(still.size - 8, u16(still, 4) or (u16(still, 6) shl 16))
        assertEquals("WEBPVP8X", String(still, 8, 8, Charsets.ISO_8859_1))
        assertEquals(0x10, still[20].toInt() and 0x12) // alpha, not animated
        assertEquals(frame.width - 1, u16(still, 24) or (still[26].toInt() and 0xff shl 16))
        assertEquals(frame.height - 1, u16(still, 27) or (still[29].toInt() and 0xff shl 16))
        assertEquals("ALPH", String(still, 30, 4, Charsets.ISO_8859_1))
        assertTrue(indexOf(still, "VP8 ".toByteArray()) > 30)
        assertNull(AnimatedImage.parse(still))
    }

    private fun u16(bytes: ByteArray, at: Int) = (bytes[at].toInt() and 0xff) or ((bytes[at + 1].toInt() and 0xff) shl 8)

    private fun indexOf(bytes: ByteArray, part: ByteArray): Int =
        (0..bytes.size - part.size).first { at -> part.indices.all { bytes[at + it] == part[it] } }

    /** A GIF frame: its delay in hundredths (null for no graphic control block), disposal, place and transparency. */
    private class Frame(
        val hundredths: Int?,
        val disposal: Int = 1,
        val left: Int = 0,
        val top: Int = 0,
        val width: Int = 8,
        val height: Int = 8,
        val transparent: Boolean = false,
    )

    /** A GIF of [frames] on an 8 x 8 canvas; their image data is a placeholder the reader never decodes. */
    private class TestGif(private val frames: List<Frame>) {
        fun bytes(loops: Boolean = false): ByteArray {
            val out = ByteArrayOutputStream()
            out.write("GIF89a".toByteArray())
            out.u16(8)
            out.u16(8)
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
    private class TestWebp(private val durations: List<Int>, private val animated: Boolean = true, private val flags: List<Int>? = null) {
        fun bytes(): ByteArray {
            val body = ByteArrayOutputStream()
            body.chunk("VP8X", byteArrayOf(if (animated) 0x02 else 0, 0, 0, 0, 7, 0, 0, 7, 0, 0))
            body.chunk("ANIM", ByteArray(6))
            durations.forEachIndexed { i, duration ->
                val frame = ByteArrayOutputStream()
                frame.u24(0)
                frame.u24(0)
                frame.u24(7)
                frame.u24(7)
                frame.u24(duration)
                frame.write(flags?.get(i) ?: 0)
                frame.chunk("VP8L", byteArrayOf(0x2F, 7, 0xC0.toByte(), 1, 0))
                body.chunk("ANMF", frame.toByteArray())
            }
            val out = ByteArrayOutputStream()
            out.write("RIFF".toByteArray())
            out.u32(4 + body.size())
            out.write("WEBP".toByteArray())
            body.writeTo(out)
            return out.toByteArray()
        }
    }

    private companion object {
        fun ByteArrayOutputStream.u16(value: Int) {
            write(value and 0xff)
            write((value shr 8) and 0xff)
        }

        fun ByteArrayOutputStream.u24(value: Int) {
            u16(value)
            write((value shr 16) and 0xff)
        }

        fun ByteArrayOutputStream.u32(value: Int) {
            u24(value)
            write((value shr 24) and 0xff)
        }

        fun ByteArrayOutputStream.chunk(type: String, contents: ByteArray) {
            write(type.toByteArray())
            u32(contents.size)
            write(contents)
            if (contents.size % 2 == 1) write(0)
        }
    }
}
