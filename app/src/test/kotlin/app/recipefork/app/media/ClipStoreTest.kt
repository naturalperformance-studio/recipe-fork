package app.recipefork.app.media

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.BookName
import app.recipefork.app.file.RecipeImport
import app.recipefork.core.recipe.VideoLimits
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.RandomAccessFile
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Where a converted clip is kept and what is left of one that is not (MEDIA-004, MEDIA-006, MEDIA-007, MEDIA-008):
 * its duration is checked before any conversion; a converted clip is a file in its book's media directory beside
 * photographs; one too heavy, failed or cancelled leaves nothing.
 */
@RunWith(AndroidJUnit4::class)
class ClipStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val files = context.filesDir
    private val book = BookName.Pool
    private val source = Uri.parse("content://media/external/video/media/7")

    private fun left(): List<String> = PhotoStore(files).directory(book).list()?.toList().orEmpty()

    private class Converter(private val write: suspend (File) -> Unit = { it.writeBytes(ByteArray(1000) { 7 }) }) : ClipConverter {
        var calls = 0

        override suspend fun convert(source: Uri, target: File) {
            calls++
            write(target)
        }
    }

    private fun store(converter: ClipConverter, lasts: Duration? = 10.seconds) = ClipStore(files, converter, { lasts })

    @Test
    fun aConvertedClipIsKeptInItsBooksMediaDirectory() = runBlocking {
        val converter = Converter()
        val clips = store(converter)
        val stored = clips.add(book, source) as ClipOutcome.Stored

        assertEquals(1, converter.calls)
        assertEquals(RecipeImport.mediaDirectory(files, book), stored.file.parentFile)
        assertEquals(stored.mediaId, stored.file.name)
        assertEquals(clips.file(book, stored.mediaId), stored.file)
        assertEquals(1000L, stored.file.length())
        assertEquals(listOf(stored.mediaId), left())

        clips.delete(book, stored.mediaId)
        assertEquals(emptyList<String>(), left())
    }

    // MEDIA-007: the duration is the one thing checked at the door, before any conversion runs.
    @Test
    fun aClipTooLongIsRefusedBeforeAnyConversion() = runBlocking {
        listOf(40.seconds, 16.seconds, VideoLimits.MAX_DURATION + VideoLimits.TOLERANCE + 1.milliseconds).forEach { lasts ->
            val converter = Converter()
            assertEquals(ClipOutcome.Refused(ClipRefusal.TOO_LONG), store(converter, lasts).add(book, source))
            assertEquals(0, converter.calls)
        }
        assertEquals(emptyList<String>(), left())
    }

    @Test
    fun aClipOfFifteenSecondsIsConverted() = runBlocking {
        assertTrue(store(Converter(), VideoLimits.MAX_DURATION).add(book, source) is ClipOutcome.Stored)
    }

    // T-243 on the owner's phone: the camera's fifteen seconds with sound, the sound track running a little past the
    // picture, so the file states a little over fifteen seconds; it is converted, not refused as too long.
    @Test
    fun aFifteenSecondCameraClipWhoseSoundRunsPastItIsConverted() = runBlocking {
        val converter = Converter()
        assertTrue(store(converter, 15.seconds + 230.milliseconds).add(book, source) is ClipOutcome.Stored)
        assertEquals(1, converter.calls)
    }

    // MEDIA-007 (3): a clip whose duration cannot be read is refused, and MEDIA-008 governs the message.
    @Test
    fun aClipOfUnreadableDurationIsRefusedBeforeAnyConversion() = runBlocking {
        listOf<Duration?>(null, Duration.ZERO).forEach { lasts ->
            val converter = Converter()
            assertEquals(ClipOutcome.Refused(ClipRefusal.UNREADABLE), store(converter, lasts).add(book, source))
            assertEquals(0, converter.calls)
        }
        assertEquals(emptyList<String>(), left())
    }

    // MEDIA-006: converted and still over 6 MB, it is refused and nothing of it is kept.
    @Test
    fun aConvertedClipStillTooHeavyIsRefusedAndLeavesNothing() = runBlocking {
        val heavy = Converter { RandomAccessFile(it, "rw").use { file -> file.setLength(VideoLimits.MAX_BYTES + 1) } }
        assertEquals(ClipOutcome.Refused(ClipRefusal.TOO_LARGE), store(heavy).add(book, source))
        assertEquals(emptyList<String>(), left())

        val justFits = Converter { RandomAccessFile(it, "rw").use { file -> file.setLength(VideoLimits.MAX_BYTES) } }
        val stored = store(justFits).add(book, source) as ClipOutcome.Stored
        assertEquals(VideoLimits.MAX_BYTES, stored.file.length())
        assertTrue(stored.file.delete())
    }

    // MEDIA-008: a conversion that fails half-way attaches nothing and leaves no partial file.
    @Test
    fun aFailedConversionIsRefusedAndLeavesNothing() = runBlocking {
        val failures = listOf<suspend (File) -> Unit>(
            { it.writeBytes(ByteArray(500)); throw ClipConversionFailed("codec") },
            { it.writeBytes(ByteArray(500)); throw IllegalStateException("anything else") },
            { },
        )
        failures.forEach { failing ->
            assertEquals(ClipOutcome.Refused(ClipRefusal.UNREADABLE), store(Converter(failing)).add(book, source))
        }
        assertEquals(emptyList<String>(), left())
    }

    // MEDIA-008: a conversion cancelled half-way leaves no partial file either.
    @Test
    fun aCancelledConversionLeavesNothing() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val endless = Converter {
            it.writeBytes(ByteArray(500))
            started.complete(Unit)
            awaitCancellation()
        }
        val adding = async { store(endless).add(book, source) }
        started.await()
        assertEquals(1, left().size)

        adding.cancel()
        adding.join()

        assertTrue(adding.isCancelled)
        assertEquals(emptyList<String>(), left())
    }
}
