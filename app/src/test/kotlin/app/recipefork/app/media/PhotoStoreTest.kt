package app.recipefork.app.media

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ApplicationProvider
import app.recipefork.app.book.BookName
import app.recipefork.app.file.RecipeImport
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch

/**
 * Where a photograph is kept and what is left of one that is not (MEDIA-006, MEDIA-008): a re-encoded photograph is a
 * plain JPEG in its book's media directory, beside imported media; a refused, failed or cancelled one leaves nothing.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val files = context.filesDir
    private val book = BookName.Pool
    private val original = TestPhotos.tag(TestPhotos.jpeg(File(context.cacheDir, "in.jpg"), 2400, 1800, noise = true))

    private fun left(book: BookName = this.book): List<String> = PhotoStore(files).directory(book).list()?.toList().orEmpty()

    @Test
    fun aPhotographIsKeptAsAPlainJpegInItsBooksMediaDirectory() = runBlocking {
        val store = PhotoStore(files)
        val stored = store.add(book) { original.inputStream() } as PhotoOutcome.Stored

        assertEquals(RecipeImport.mediaDirectory(files, book), stored.file.parentFile)
        assertEquals(File(files, "book-pool-media"), stored.file.parentFile)
        assertEquals(stored.mediaId, stored.file.name)
        assertEquals(store.file(book, stored.mediaId), stored.file)
        assertTrue(stored.file.length() in 1..1_000_000)
        assertEquals(listOf(stored.mediaId), left())

        store.delete(book, stored.mediaId)
        assertEquals(emptyList<String>(), left())
    }

    @Test
    fun eachBookKeepsItsOwnPhotographs() = runBlocking {
        val other = BookName.OfAccount("someone")
        val stored = PhotoStore(files).add(other) { original.inputStream() } as PhotoOutcome.Stored
        assertEquals(File(files, "book-someone-media"), stored.file.parentFile)
        assertEquals(emptyList<String>(), left())
    }

    // MEDIA-008 (1, 2)
    @Test
    fun anUnreadableFileIsRefusedAndNothingIsKept() = runBlocking {
        val outcome = PhotoStore(files).add(book) { ByteArrayInputStream(ByteArray(4096) { it.toByte() }) }
        assertEquals(PhotoOutcome.Refused(PhotoRefusal.UNREADABLE), outcome)
        assertEquals(emptyList<String>(), left())
    }

    // MEDIA-006 (1)
    @Test
    fun aPhotographThatDoesNotFitIsRefusedAndNothingIsKept() = runBlocking {
        val outcome = PhotoStore(files, PhotoReencoder(PhotoLimits(maxBytes = 5_000))).add(book) { original.inputStream() }
        assertEquals(PhotoOutcome.Refused(PhotoRefusal.TOO_LARGE), outcome)
        assertEquals(emptyList<String>(), left())
    }

    // MEDIA-008 (2): a write that fails half-way leaves no partial file behind.
    @Test
    fun aWriteFailingHalfWayLeavesNothingBehind() = runBlocking {
        val store = PhotoStore(files, write = { file, bytes ->
            file.writeBytes(bytes.copyOf(bytes.size / 2))
            throw IOException("device full")
        })
        assertEquals(PhotoOutcome.Refused(PhotoRefusal.NOT_STORED), store.add(book) { original.inputStream() })
        assertEquals(emptyList<String>(), left())
    }

    // A conversion cancelled half-way — the author left — keeps nothing either.
    @Test
    fun aCancelledWriteLeavesNothingBehind() = runBlocking {
        val writing = CompletableDeferred<Unit>()
        val cancelled = CountDownLatch(1)
        val store = PhotoStore(files, write = { file, bytes ->
            file.writeBytes(bytes.copyOf(bytes.size / 2))
            writing.complete(Unit)
            cancelled.await()
        })
        val adding = async(Dispatchers.Default) { store.add(book) { original.inputStream() } }
        writing.await()
        adding.cancel()
        cancelled.countDown()
        runCatching { adding.await() }
        assertTrue(adding.isCancelled)
        assertEquals(emptyList<String>(), left())
        assertFalse(store.directory(book).listFiles().orEmpty().any { it.name.endsWith(".part") })
    }
}
