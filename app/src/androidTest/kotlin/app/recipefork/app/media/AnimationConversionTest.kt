package app.recipefork.app.media

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ImageFormat
import android.media.ImageReader
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.recipefork.app.book.BookName
import app.recipefork.app.card.ClipPlayer
import app.recipefork.app.card.PlatformClipPlayer
import app.recipefork.core.recipe.VideoLimits
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.minutes

/**
 * An animated GIF or WebP attached to a step becomes a silent clip (scenario 3) on a real device, by its real codecs:
 * converted within the clip limits (MEDIA-004, MEDIA-006), refused when longer than a clip may be (MEDIA-007), and
 * played by the card's own player. The fixtures in assets/animation are those of AnimatedImageTest, plus large.gif:
 * two frames of 0.5 s at 1600 x 900, a blue canvas and then a red square on it.
 */
@RunWith(AndroidJUnit4::class)
class AnimationConversionTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext.applicationContext
    private val book = BookName.OfAccount("animation-conversion")
    private val sources = File(TestClips.directory(context), "animation-conversion")
    private val images = ResolverAnimatedImages(context)

    /** The real converter, counted. */
    private class Counted(private val converter: ClipConverter) : ClipConverter {
        var calls = 0

        override suspend fun convert(source: Uri, target: File) {
            calls++
            converter.convert(source, target)
        }
    }

    private val converter = Counted(AnimationEncoder(images))
    private val animations = ClipStore(context.filesDir, converter, AnimationDuration(images))

    private fun left(): List<String> = PhotoStore(context.filesDir).directory(book).list()?.toList().orEmpty()

    /** The fixture [name], handed over by content address as a gallery hands a picked image. */
    private fun source(name: String): Uri {
        val file = File(sources, name).apply { parentFile?.mkdirs() }
        instrumentation.context.assets.open("animation/$name").use { input -> file.outputStream().use { input.copyTo(it) } }
        return TestClips.uri(context, file)
    }

    private fun stored(name: String): ClipOutcome.Stored =
        runBlocking { animations.add(book, source(name)) }.let { it as? ClipOutcome.Stored ?: throw AssertionError("not stored: $it") }

    private fun tracks(file: File): List<String> {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.path)
            return (0 until extractor.trackCount).map { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)!! }
        } finally {
            extractor.release()
        }
    }

    private fun <T> retrieved(file: File, read: (MediaMetadataRetriever) -> T): T = MediaMetadataRetriever().use {
        it.setDataSource(file.path)
        read(it)
    }

    private fun shown(file: File): PixelSize = retrieved(file) {
        PixelSize(
            it.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)!!.toInt(),
            it.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)!!.toInt(),
        )
    }

    private fun lasts(file: File): Long = retrieved(file) { it.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong() }

    /** The clip's picture nearest to [ms] milliseconds. */
    private fun frameAt(file: File, ms: Long): Bitmap =
        retrieved(file) { it.getFrameAtTime(ms * 1000, MediaMetadataRetriever.OPTION_CLOSEST)!! }

    private fun assertRed(at: String, pixel: Int) =
        assertTrue("$at is ${Integer.toHexString(pixel)}", Color.red(pixel) > 180 && Color.blue(pixel) < 90 && Color.green(pixel) < 90)

    private fun assertBlue(at: String, pixel: Int) =
        assertTrue("$at is ${Integer.toHexString(pixel)}", Color.blue(pixel) > 180 && Color.red(pixel) < 90 && Color.green(pixel) < 90)

    /** What every converted animation is: a silent H.264 MP4 within the clip limits, carrying no metadata. */
    private fun assertWithinTheClipLimits(clip: File) {
        assertEquals(listOf(MediaFormat.MIMETYPE_VIDEO_AVC), tracks(clip))
        assertTrue("the clip weighs ${clip.length()}", clip.length() in 1..VideoLimits.MAX_BYTES)
        val boxes = Mp4Boxes(clip)
        assertEquals("ftyp", boxes.all.first().type)
        assertEquals(emptySet<String>(), boxes.types() intersect Mp4Boxes.IDENTIFYING)
        assertEquals(0L, boxes.creationTime())
        assertTrue(shown(clip).longerEdge <= VideoLimits.MAX_LONGER_EDGE)
    }

    /**
     * The red square at its place in each frame of the moving-square fixtures, and the blue canvas around it, a quarter
     * into each frame's delay — nearer its own picture than any other: every frame composed, in its order and for its
     * time.
     */
    private fun assertTheSquareMoves(clip: File, delays: List<Long>) {
        var start = 0L
        delays.forEachIndexed { k, delay ->
            val picture = frameAt(clip, start + delay / 4)
            assertRed("the square of frame $k", picture.getPixel(30 + 12 * k, 60))
            assertBlue("the canvas of frame $k", picture.getPixel(5, 110))
            if (k > 0) assertBlue("where the square of frame $k left", picture.getPixel(30 + 12 * (k - 1) - 14, 60))
            start += delay
        }
    }

    /**
     * Plays [clip] as a step's card does, with the platform's player, onto a surface read like a screen; returns once
     * it has drawn [pictures] pictures without failing.
     */
    private fun assertTheCardPlays(clip: File, pictures: Int) = runBlocking {
        val drawn = AtomicInteger()
        val thread = HandlerThread("pictures").apply { start() }
        val size = shown(clip)
        val reader = ImageReader.newInstance(size.width, size.height, ImageFormat.PRIVATE, 3)
        reader.setOnImageAvailableListener({ it.acquireLatestImage()?.close(); drawn.incrementAndGet() }, Handler(thread.looper))
        val ready = CompletableDeferred<Triple<Int, Int, Int>>()
        val failed = AtomicBoolean()
        val media = withContext(Dispatchers.Main) { MediaPlayer() }
        val player = PlatformClipPlayer(media)
        try {
            withContext(Dispatchers.Main) {
                player.open(
                    clip,
                    object : ClipPlayer.Listener {
                        override fun ready(width: Int, height: Int, rotation: Int) {
                            ready.complete(Triple(width, height, rotation))
                        }

                        override fun failed() {
                            failed.set(true)
                            ready.completeExceptionally(AssertionError("the card's player cannot play it"))
                        }
                    },
                )
                player.surface(reader.surface)
                player.play()
            }
            withTimeout(1.minutes) {
                assertEquals(Triple(size.width, size.height, 0), ready.await())
                while (drawn.get() < pictures && !failed.get()) delay(20)
            }
            assertFalse(failed.get())
            assertTrue(withContext(Dispatchers.Main) { media.isPlaying })
        } finally {
            withContext(Dispatchers.Main) { player.release() }
            reader.close()
            thread.quitSafely()
        }
    }

    @After
    fun tearDown() {
        sources.deleteRecursively()
        PhotoStore(context.filesDir).directory(book).deleteRecursively()
    }

    // Scenario 3: an animated GIF and WebP are animations; a still image is not.
    @Test
    fun animatedGifsAndWebpsAreToldFromStillImages() {
        assertTrue(images.isAnimated(source("animated.gif")))
        assertTrue(images.isAnimated(source("animated.webp")))
        assertFalse(images.isAnimated(source("still.png")))
    }

    // Scenario 3, MEDIA-004: an animated GIF attaches as a silent clip lasting its frames' delays, every frame in place
    // for its own time, which the card's player plays.
    @Test
    fun anAnimatedGifBecomesASilentClipTheCardPlays() {
        val delays = listOf(100L, 150, 200, 250, 100, 150, 200, 250, 100, 200)

        val stored = stored("animated.gif")

        assertEquals(1, converter.calls)
        assertEquals(listOf(stored.mediaId), left())
        assertWithinTheClipLimits(stored.file)
        assertEquals(PixelSize(160, 120), shown(stored.file))
        assertTrue("it lasts ${lasts(stored.file)} ms", lasts(stored.file) in 1650L..1750L)
        assertTheSquareMoves(stored.file, delays)
        assertTheCardPlays(stored.file, pictures = delays.size)
    }

    // Scenario 3, MEDIA-004: an animated WebP — lossy, its later frames blended over the first — does the same.
    @Test
    fun anAnimatedWebpBecomesASilentClipTheCardPlays() {
        val delays = listOf(120L, 80, 200, 160, 40, 300, 100, 90, 110, 200)

        val stored = stored("animated.webp")

        assertWithinTheClipLimits(stored.file)
        assertEquals(PixelSize(160, 120), shown(stored.file))
        assertTrue("it lasts ${lasts(stored.file)} ms", lasts(stored.file) in 1350L..1450L)
        assertTheSquareMoves(stored.file, delays)
        assertTheCardPlays(stored.file, pictures = delays.size)
    }

    // MEDIA-004 (1, 5): an animation larger than the limit is brought to 1280 on its longer edge.
    @Test
    fun aLargeAnimationIsBroughtWithinTheLongerEdge() {
        val stored = stored("large.gif")

        assertWithinTheClipLimits(stored.file)
        assertEquals(PixelSize(1280, 720), shown(stored.file))
        assertBlue("the canvas", frameAt(stored.file, 100).getPixel(640, 360))
        assertRed("the square", frameAt(stored.file, 600).getPixel(640, 360))
    }

    // MEDIA-007: an animation over 15 seconds is refused at once, as a clip is, and no conversion runs.
    @Test
    fun anAnimationOverFifteenSecondsIsRefusedAsTooLong() {
        assertEquals(ClipOutcome.Refused(ClipRefusal.TOO_LONG), runBlocking { animations.add(book, source("long.gif")) })
        assertEquals(0, converter.calls)
        assertEquals(emptyList<String>(), left())
    }

    // MEDIA-008: a still image offered as an animation is refused as unreadable, and nothing is attached.
    @Test
    fun aStillImageIsNoAnimationToConvert() {
        assertEquals(ClipOutcome.Refused(ClipRefusal.UNREADABLE), runBlocking { animations.add(book, source("still.png")) })
        assertEquals(0, converter.calls)
        assertEquals(emptyList<String>(), left())
    }
}
