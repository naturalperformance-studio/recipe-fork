package app.recipefork.app.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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

    // A hostile file states a canvas its device cannot hold: refused before anything of it is allocated.
    @Test
    fun aCanvasOverTheCapIsNoAnimation() {
        assertNull(AnimatedImage.parse(TestGif(listOf(Frame(10, width = 1, height = 1), Frame(10, width = 1, height = 1)), 65535, 65535).bytes()))
        assertNull(AnimatedImage.parse(TestWebp(listOf(100, 100), canvas = 16_000_000, frame = 1).bytes()))
        assertNull(AnimatedImage.parse(TestGif(listOf(Frame(10, width = 1, height = 1), Frame(10, width = 1, height = 1)), 3841, 2160).bytes()))
        // A 4K canvas is the most there may be.
        assertEquals(3840 * 2160L, AnimatedImage.MAX_CANVAS_PIXELS)
        val largest = AnimatedImage.parse(TestGif(listOf(Frame(10, width = 1, height = 1), Frame(10, width = 1, height = 1)), 3840, 2160).bytes())!!
        assertEquals(listOf(3840, 2160), listOf(largest.width, largest.height))
    }

    @Test
    fun aFrameReachingOutsideItsCanvasIsNoAnimation() {
        assertNull(AnimatedImage.parse(TestGif(listOf(Frame(10), Frame(10, left = 1))).bytes()))
        assertNull(AnimatedImage.parse(TestGif(listOf(Frame(10), Frame(10, top = 4, height = 5))).bytes()))
        assertNull(AnimatedImage.parse(TestGif(listOf(Frame(10), Frame(10, width = 65535, height = 65535))).bytes()))
        assertNull(AnimatedImage.parse(TestWebp(listOf(100, 100), canvas = 8, frame = 9).bytes()))
    }

    // However many frames a file holds, they are read only until they pass the longest a clip may last, its tolerance
    // included — so that what is read of one too long is too long still (T-274 review), and refused.
    @Test
    fun aFrameCountBombIsReadOnlyUntilItPassesTheLongestAClipMayLast() {
        assertEquals(15_500.milliseconds, AnimatedImage.READ_UNTIL)
        val shortest = TestGif(List(100_000) { Frame(2) }).bytes() // 20 ms each: 2000 s in all
        val gif = AnimatedImage.parse(shortest)!!
        assertEquals(776, gif.frames.size)
        assertEquals(15_520.milliseconds, gif.duration)

        val unstated = AnimatedImage.parse(TestGif(List(100_000) { Frame(0) }).bytes())!! // read as 100 ms each
        assertEquals(156, unstated.frames.size)
        assertEquals(15_600.milliseconds, unstated.duration)

        val webp = AnimatedImage.parse(TestWebp(List(100_000) { 11 }).bytes())!!
        assertEquals(1410, webp.frames.size)
        assertEquals(15_510.milliseconds, webp.duration)
        listOf(gif, unstated, webp).forEach { assertFalse("${it.duration}", app.recipefork.core.recipe.VideoLimits.accepts(it.duration)) }
    }

    // A JPEG or a PNG is told still from its signature: nothing more of it is read.
    @Test
    fun aStillFormatIsToldFromItsFirstBytesAlone() {
        listOf("still.jpg", "still.png").forEach { name ->
            val input = Counting(fixture(name))
            assertNull(name, AnimatedImage.read(input))
            assertTrue("$name: ${input.read} bytes read", input.read <= 12)
        }
    }

    @Test
    fun aFileOverTheByteCapIsNoAnimation() {
        val bytes = fixture("animated.gif")
        assertNotNull(AnimatedImage.read(Counting(bytes), maxBytes = bytes.size))
        val input = Counting(bytes)
        assertNull(AnimatedImage.read(input, maxBytes = bytes.size - 1))
        // A hostile file of endless bytes after a GIF's signature is read no further than the cap and one buffer.
        val endless = object : java.io.InputStream() {
            var read = 0L
            override fun read(): Int = if (read++ < 6) "GIF89a"[read.toInt() - 1].code else 0
        }
        assertNull(AnimatedImage.read(endless, maxBytes = 1_000_000))
        assertTrue("${endless.read} bytes read", endless.read < 1_000_000 + 128 * 1024)
        assertEquals(32 * 1024 * 1024, AnimatedImage.MAX_BYTES)
    }

    // A GIF or a WebP over the cap is told too large — never a still picture to crop; anything else over it is still.
    @Test
    fun aGifOrWebpOverTheByteCapIsToldTooLarge() {
        listOf("animated.gif", "animated.webp", "still.gif", "still.webp").forEach { name ->
            val bytes = fixture(name)
            assertEquals(name, ImageKind.TooLarge, AnimatedImage.examine(Counting(bytes), maxBytes = bytes.size - 1))
        }
        listOf("still.jpg", "still.png").forEach { name ->
            assertEquals(name, ImageKind.Still, AnimatedImage.examine(Counting(fixture(name)), maxBytes = 1))
        }
        assertEquals(ImageKind.Still, AnimatedImage.examine(Counting(fixture("still.gif"))))
        val animated = AnimatedImage.examine(Counting(fixture("animated.gif")))
        assertEquals(1700.milliseconds, (animated as ImageKind.Animated).animation.duration)
    }

    // A GIF or a WebP whose source states a size over the cap is told too large from its signature: nothing past it is
    // read, so a file of 33 MB is never held in memory to be refused (T-274: under a full suite's memory, it was).
    @Test
    fun aGifOrWebpStatingASizeOverTheCapIsToldTooLargeFromItsSignature() {
        listOf("animated.gif", "animated.webp").forEach { name ->
            val bytes = fixture(name)
            val input = Counting(bytes)
            assertEquals(name, ImageKind.TooLarge, AnimatedImage.examine(input, maxBytes = bytes.size - 1, states = bytes.size.toLong()))
            assertTrue("$name: ${input.read} bytes read", input.read <= 12)
            assertTrue(name, AnimatedImage.examine(Counting(bytes), maxBytes = bytes.size, states = bytes.size.toLong()) is ImageKind.Animated)
        }
        assertEquals(ImageKind.Still, AnimatedImage.examine(Counting(fixture("still.png")), maxBytes = 1, states = 1_000_000))
    }

    @Test
    fun anAnimationReadFromAStreamIsTheOneItsBytesHold() {
        val gif = AnimatedImage.read(Counting(fixture("animated.gif")))!!
        assertEquals(1700.milliseconds, gif.duration)
        val webp = AnimatedImage.read(Counting(fixture("animated.webp")))!!
        assertEquals(1400.milliseconds, webp.duration)
    }

    /** [bytes] as a stream that counts what is read of it. */
    private class Counting(bytes: ByteArray) : java.io.ByteArrayInputStream(bytes) {
        val read get() = pos
    }

    private fun u16(bytes: ByteArray, at: Int) = (bytes[at].toInt() and 0xff) or ((bytes[at + 1].toInt() and 0xff) shl 8)

    private fun indexOf(bytes: ByteArray, part: ByteArray): Int =
        (0..bytes.size - part.size).first { at -> part.indices.all { bytes[at + it] == part[it] } }
}
