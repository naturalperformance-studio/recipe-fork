package app.recipefork.app.media

import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.recipefork.app.book.BookName
import app.recipefork.core.recipe.VideoLimits
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * A step's clip converted on a real device by its real codecs (MEDIA-003, MEDIA-004, MEDIA-006, MEDIA-007,
 * MEDIA-008): from clips made here as a phone's camera makes them — larger than the limit, with sound, turned, and
 * marked with the place they were shot — handed over by content address as another app hands them.
 */
@RunWith(AndroidJUnit4::class)
class VideoConversionTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
    private val book = BookName.OfAccount("video-conversion")
    private val sources = File(TestClips.directory(context), "video-conversion")

    /** The real converter, counted. */
    private class Counted(private val converter: ClipConverter) : ClipConverter {
        var calls = 0

        override suspend fun convert(source: Uri, target: File) {
            calls++
            converter.convert(source, target)
        }
    }

    private val converter = Counted(ClipTransformer(context))
    private val clips = ClipStore(context.filesDir, converter, RetrieverClipSourceDuration(context))

    private fun left(): List<String> = PhotoStore(context.filesDir).directory(book).list()?.toList().orEmpty()

    private fun clip(name: String, width: Int, height: Int, seconds: Int, fps: Int = 24, rotation: Int = 0) =
        TestClips.write(File(sources, name), width, height, seconds, fps, rotation = rotation, location = PLACE)

    private fun add(source: File): ClipOutcome = runBlocking { clips.add(book, TestClips.uri(context, source)) }

    private fun stored(source: File): ClipOutcome.Stored = add(source).let { it as? ClipOutcome.Stored ?: throw AssertionError("not stored: $it") }

    /** The size the clip in [file] is shown at, its rotation applied. */
    private fun shown(file: File): PixelSize = MediaMetadataRetriever().use {
        it.setDataSource(file.path)
        fun read(key: Int) = it.extractMetadata(key)!!.toInt()
        ClipPlan.displayed(
            read(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH),
            read(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT),
            read(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION),
        )
    }

    private fun tracks(file: File): List<String> {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.path)
            return (0 until extractor.trackCount).map { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)!! }
        } finally {
            extractor.release()
        }
    }

    @After
    fun tearDown() {
        sources.deleteRecursively()
        PhotoStore(context.filesDir).directory(book).deleteRecursively()
    }

    // MEDIA-004 (1, 4), MEDIA-006: a large clip of about 12 seconds, shot upright with sound, attaches as a silent
    // H.264 MP4 at 1280 on its longer edge, still upright, and under 6 MB.
    @Test
    fun aLargeClipIsConvertedSmallUprightAndSilent() {
        val source = TestClips.write(File(sources, "large.mp4"), 1920, 1080, seconds = 12, keyFrames = 0, rotation = 90, location = PLACE)
        assertTrue("the original weighs ${source.length()}", source.length() > VideoLimits.MAX_BYTES)
        assertEquals(setOf(MediaFormat.MIMETYPE_VIDEO_AVC, MediaFormat.MIMETYPE_AUDIO_AAC), tracks(source).toSet())
        assertEquals(PixelSize(1080, 1920), shown(source))

        val stored = stored(source)

        assertEquals(1, converter.calls)
        assertEquals(listOf(stored.mediaId), left())
        assertTrue("the clip weighs ${stored.file.length()}", stored.file.length() in 1..VideoLimits.MAX_BYTES)
        assertEquals(PixelSize(720, 1280), shown(stored.file))
        assertEquals(listOf(MediaFormat.MIMETYPE_VIDEO_AVC), tracks(stored.file))
        assertEquals("ftyp", Mp4Boxes(stored.file).all.first().type)
        val lasts = MediaMetadataRetriever().use {
            it.setDataSource(stored.file.path)
            it.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
        }
        assertTrue("it lasts $lasts ms", lasts in 11_000..13_000)
    }

    // MEDIA-004 (5): a clip already within the limit is converted — silenced — but not enlarged.
    @Test
    fun aSmallClipIsNotEnlarged() {
        val stored = stored(clip("small.mp4", 640, 360, seconds = 2))

        assertEquals(PixelSize(640, 360), shown(stored.file))
        assertEquals(listOf(MediaFormat.MIMETYPE_VIDEO_AVC), tracks(stored.file))
    }

    // MEDIA-003: the converted clip keeps nothing of where, when or on what it was shot; only its orientation.
    @Test
    fun theConvertedClipKeepsNoMetadataButItsOrientation() {
        val source = clip("placed.mp4", 1280, 720, seconds = 2, rotation = 270)
        val original = Mp4Boxes(source)
        val place = original.text("\u00a9xyz").drop(4)
        assertTrue(place, place.startsWith("+55.7"))
        assertTrue(original.creationTime() > 0)

        val stored = stored(source)

        val boxes = Mp4Boxes(stored.file)
        assertEquals(emptySet<String>(), boxes.types() intersect Mp4Boxes.IDENTIFYING)
        assertFalse(String(stored.file.readBytes(), Charsets.ISO_8859_1).contains(place))
        assertEquals(0L, boxes.creationTime())
        assertEquals(PixelSize(720, 1280), shown(stored.file))
    }

    // MEDIA-006: a clip that converts but still weighs more than its limit is refused, and nothing of it is kept.
    @Test
    fun aConvertedClipOverItsWeightIsRefusedAndLeavesNothing() {
        val strict = ClipStore(context.filesDir, converter, RetrieverClipSourceDuration(context), maxBytes = 10_000)
        val outcome = runBlocking { strict.add(book, TestClips.uri(context, clip("heavy.mp4", 640, 360, seconds = 2))) }

        assertEquals(ClipOutcome.Refused(ClipRefusal.TOO_LARGE), outcome)
        assertEquals(1, converter.calls)
        assertEquals(emptyList<String>(), left())
    }

    // MEDIA-007: a clip over 15 seconds is refused at once, and no conversion runs.
    @Test
    fun aClipOverFifteenSecondsIsRefusedBeforeAnyConversion() {
        listOf(40, 16).forEach { seconds ->
            // At a size every phone's encoder takes: the owner's took nothing as small as 160 x 96 (T-243).
            val source = clip("long-$seconds.mp4", 640, 360, seconds, fps = 2)
            val started = System.nanoTime()

            assertEquals(ClipOutcome.Refused(ClipRefusal.TOO_LONG), add(source))

            assertTrue((System.nanoTime() - started) < 3_000_000_000L)
        }
        assertEquals(0, converter.calls)
        assertEquals(emptyList<String>(), left())
    }

    // MEDIA-007, T-243 on the owner's phone: the camera's full fifteen seconds with sound, the sound track running a
    // little past the picture so that the file states more than fifteen seconds, is accepted and converted.
    @Test
    fun aFifteenSecondCameraClipWhoseSoundRunsPastItIsAccepted() {
        val source = TestClips.write(File(sources, "fifteen.mp4"), 640, 360, seconds = 15, fps = 4, soundPastMs = 300)
        val states = MediaMetadataRetriever().use {
            it.setDataSource(source.path)
            it.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
        }
        assertTrue("the file states $states ms", states > 15_000)

        val stored = stored(source)

        assertEquals(1, converter.calls)
        assertEquals(listOf(MediaFormat.MIMETYPE_VIDEO_AVC), tracks(stored.file))
    }

    // MEDIA-007 (3), MEDIA-008: a file whose duration cannot be read is refused, and nothing is attached.
    @Test
    fun aFileThatIsNoClipIsRefusedAsUnreadable() {
        assertEquals(ClipOutcome.Refused(ClipRefusal.UNREADABLE), add(TestClips.junk(File(sources, "junk.mp4"))))
        assertEquals(0, converter.calls)
        assertEquals(emptyList<String>(), left())
    }

    // MEDIA-008: a conversion cancelled half-way stops the codec and leaves no partial file behind.
    @Test
    fun aCancelledConversionLeavesNothing() = runBlocking {
        val source = clip("cancelled.mp4", 1920, 1080, seconds = 6)
        val started = CompletableDeferred<Unit>()
        val watched = ClipStore(context.filesDir, { from, to ->
            started.complete(Unit)
            converter.convert(from, to)
        }, RetrieverClipSourceDuration(context))
        val adding = async { watched.add(book, TestClips.uri(context, source)) }
        started.await()
        withTimeout(1.minutes) { while (left().isEmpty()) delay(20) }

        adding.cancel()
        adding.join()
        delay(2.seconds)

        assertTrue(adding.isCancelled)
        assertEquals(emptyList<String>(), left())
    }

    private companion object {
        val PLACE = 55.7558f to 37.6173f
    }
}
