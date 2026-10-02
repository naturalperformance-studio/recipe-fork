package app.recipefork.app.media

import app.recipefork.app.book.ActiveBook
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Clears a book's media directory of what no recipe of the book holds (MEDIA-012, done in a batch): files left by a
 * deletion that did not finish, by an import or an attachment stopped half-way, and the half-written `.part` files of
 * either. Only files older than [age] are touched, so that an attachment, a fork or an import still in flight — whose
 * file is written before the book holds it — is never swept from under it. Whatever cannot be read of the book stops
 * the sweep before it deletes anything.
 */
class MediaSweep(
    private val filesDirectory: File,
    private val now: () -> Long = System::currentTimeMillis,
    private val age: Duration = AGE,
) {
    /** Sweeps the media directory of the book [open]; returns the files it deleted. Runs off the main thread. */
    suspend fun sweep(open: ActiveBook.Open): List<File> = withContext(Dispatchers.IO) {
        val directory = PhotoStore(filesDirectory).directory(open.name)
        val files = directory.listFiles()?.filter { it.isFile } ?: return@withContext emptyList()
        val cutoff = now() - age.inWholeMilliseconds
        val old = files.filter { it.lastModified() < cutoff }
        if (old.isEmpty()) return@withContext emptyList()
        // A book that holds no recipe at all beside photographs on disk is more likely a book file lost and made
        // anew than one whose every recipe was deleted: its photographs are kept rather than swept.
        if (open.database.recipes().count() == 0) return@withContext emptyList()
        val held = open.database.recipes().allMedia().toSet()
        old.filter { it.name.endsWith(PART) || it.name !in held }.filter { it.delete() }
    }

    /** Sweeps each book of [books] once as it is opened, in [scope]. */
    fun everyOpened(books: StateFlow<ActiveBook.Open>, scope: CoroutineScope): Job =
        scope.launch {
            books.collect {
                try {
                    sweep(it)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // A sweep that cannot run now runs at the book's next opening; nothing is lost by waiting.
                }
            }
        }

    companion object {
        /** Long past any attachment, fork or import in flight. */
        val AGE = 10.minutes
        private const val PART = ".part"
    }
}
