package app.recipefork.app.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.kitchen.KitchenStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/** What the book's saving or restoring is at, as the settings show it (FLOW-073). */
sealed interface BackupState {
    /** Nothing runs. */
    data object Idle : BackupState

    /** [work] runs; [done] is the share of it done, null while it is not known. */
    data class Working(val work: BackupWork, val done: Float? = null) : BackupState

    /** A file was opened for a book that holds [inBook] recipes: add to it, or replace it? */
    data class Asking(val inBook: Int) : BackupState

    /** Replacing was chosen: the book's [inBook] recipes will go — confirmed once more. */
    data class ConfirmingReplace(val inBook: Int) : BackupState

    /** It ended, and [notice] is to be said once. */
    data class Ended(val notice: BackupNotice) : BackupState
}

enum class BackupWork { SAVING, READING, ADDING, REPLACING }

/** What a saving or a restore came to; a cancel says nothing. */
sealed interface BackupNotice {
    data class Saved(val recipes: Int) : BackupNotice
    data class Restored(val recipes: Int) : BackupNotice
    data class Added(val added: Int, val alreadyHeld: Int) : BackupNotice
    data class Refused(val refusal: BookFileRefusal) : BackupNotice
    data object SaveFailed : BackupNotice
    data object RestoreFailed : BackupNotice
}

/** The documents the person chose through the system's pickers. */
interface BookDocuments {
    fun output(uri: Uri): OutputStream
    fun input(uri: Uri): InputStream

    /** The document's size in bytes; null when its provider does not say. */
    fun size(uri: Uri): Long?

    /** Deletes the document, so that no part of a book is left where the person chose; never fails. */
    fun delete(uri: Uri)
}

class ResolverDocuments(context: Context) : BookDocuments {
    private val resolver = context.contentResolver

    override fun output(uri: Uri): OutputStream = resolver.openOutputStream(uri, "w") ?: throw IOException("no stream for $uri")
    override fun input(uri: Uri): InputStream = resolver.openInputStream(uri) ?: throw IOException("no stream for $uri")

    override fun size(uri: Uri): Long? = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else null }
    }.getOrNull()?.takeIf { it > 0 }

    override fun delete(uri: Uri) {
        runCatching { DocumentsContract.deleteDocument(resolver, uri) }
    }
}

/**
 * Saves the active [book] to a file and restores one into it (FLOW-073), one operation at a time, in [scope], which
 * outlives every screen: a screen turned or left while it runs finds the same operation, and its [state], when shown
 * again. A notice is held until a screen has said it ([noticeSaid]).
 *
 * [restoreFrom] is the one way into a restore — from the settings' picker, and from a book file another application
 * hands over.
 */
class BookBackup(
    private val book: ActiveBook,
    private val kitchen: KitchenStore,
    private val documents: BookDocuments,
    private val writer: BookFileWriter,
    private val reader: BookFileReader,
    private val restorer: BookRestore,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val current = MutableStateFlow<BackupState>(BackupState.Idle)
    val state: StateFlow<BackupState> = current.asStateFlow()

    private val lock = Any()
    private var job: Job? = null

    /** The file opened while the person is asked how it goes into the book. */
    private var opened: OpenedBookFile? = null

    /** Saves the book to the document [to]; a cancel or a failure deletes it. False, doing nothing, while one runs. */
    fun save(to: Uri): Boolean = begin(BackupWork.SAVING) {
        val notice = try {
            val manifest = documents.output(to).use { out ->
                writer.write(book.open.value, kitchen.held.value, out) { done, total -> progress(BackupWork.SAVING, done, total) }
            }
            // A cancel during the final copy lets the file be finished; it still ends cancelled, the file deleted.
            ensureActive()
            BackupNotice.Saved(manifest.recipes)
        } catch (e: CancellationException) {
            null
        } catch (e: Exception) {
            BackupNotice.SaveFailed
        }
        if (notice !is BackupNotice.Saved) withContext(NonCancellable) { documents.delete(to) }
        end(notice)
    }

    /**
     * Restores the book file [from]: reads it, then — into a book that holds recipes — asks whether to add or replace
     * ([add], [replace], [confirmReplace]); into an empty one it adds without asking. False, doing nothing, while
     * another operation runs or waits for an answer.
     */
    fun restoreFrom(from: Uri): Boolean = reading { documents.size(from) to documents.input(from) }

    /**
     * [restoreFrom] for a book file another application handed over, whose reading is granted only while the activity
     * it was handed to lives: it is opened at once, on the caller's thread, and read afterwards however soon that
     * activity goes. One that cannot be opened is refused as unreadable; while another operation runs it is closed
     * again, and false.
     */
    fun restoreHandedOver(from: Uri): Boolean {
        val opened = runCatching { documents.size(from) to documents.input(from) }
        return reading { opened.getOrThrow() }.also { started -> if (!started) opened.getOrNull()?.second?.close() }
    }

    /** Restores the book file [open] gives with its size, as [restoreFrom] tells. */
    private fun reading(open: () -> Pair<Long?, InputStream>): Boolean = begin(BackupWork.READING) {
        val opening = try {
            val (size, input) = open()
            reader.open(input) { read -> progress(BackupWork.READING, read, size) }
        } catch (e: CancellationException) {
            end(null)
            throw e
        } catch (e: Exception) {
            BookFileOpening.Refused(BookFileRefusal.CANNOT_READ)
        }
        val file = when (opening) {
            is BookFileOpening.Refused -> return@begin end(BackupNotice.Refused(opening.refusal))
            is BookFileOpening.Opened -> opening.book
        }
        val inBook = try {
            book.database.recipes().count()
        } catch (e: CancellationException) {
            file.close()
            end(null)
            throw e
        } catch (e: Exception) {
            file.close()
            return@begin end(BackupNotice.RestoreFailed)
        }
        if (inBook == 0) return@begin restoring(file, RestoreMode.ADD, asked = false)
        // Held for the answer unless cancelled meanwhile: a cancel takes the lock to find what to close.
        val asking = synchronized(lock) {
            isActive.also {
                if (it) {
                    opened = file
                    current.value = BackupState.Asking(inBook)
                    job = null
                }
            }
        }
        if (!asking) {
            file.close()
            end(null)
        }
    }

    /** Adds the opened file's recipes the book does not hold. */
    fun add() = answer(RestoreMode.ADD) { it is BackupState.Asking }

    /** Replacing was chosen: it is confirmed once more, naming the recipes that go. */
    fun replace() = synchronized(lock) {
        (current.value as? BackupState.Asking)?.let { current.value = BackupState.ConfirmingReplace(it.inBook) }
    }

    /** Replacing was confirmed: the book becomes the file's. */
    fun confirmReplace() = answer(RestoreMode.REPLACE) { it is BackupState.ConfirmingReplace }

    /**
     * Cancels what runs, or the question asked: nothing changes in the book, and a saving's document is deleted. A
     * restore already writing the book ends restored, and says so.
     */
    fun cancel() {
        val file = synchronized(lock) {
            job?.cancel()
            opened.also {
                if (it != null) current.value = BackupState.Idle
                opened = null
            }
        }
        file?.let { scope.launch { it.close() } }
    }

    /** [notice] was said. */
    fun noticeSaid(notice: BackupNotice) = synchronized(lock) {
        if (current.value == BackupState.Ended(notice)) current.value = BackupState.Idle
    }

    private fun begin(work: BackupWork, block: suspend CoroutineScope.() -> Unit): Boolean = synchronized(lock) {
        val now = current.value
        if (now !is BackupState.Idle && now !is BackupState.Ended) return false
        current.value = BackupState.Working(work)
        job = scope.launch(block = block)
        true
    }

    /** The question [at] was answered by [mode]: the opened file goes into the book so. */
    private fun answer(mode: RestoreMode, at: (BackupState) -> Boolean) = synchronized(lock) {
        val file = opened?.takeIf { at(current.value) } ?: return
        opened = null
        current.value = BackupState.Working(work(mode))
        job = scope.launch { restoring(file, mode, asked = true) }
    }

    private fun work(mode: RestoreMode) = if (mode == RestoreMode.REPLACE) BackupWork.REPLACING else BackupWork.ADDING

    /**
     * Restores [file] as [mode] has it, then closes it. A cancel before the book is written changes nothing and says
     * nothing; once the book is being written it is finished, and what it came to is held at once, before anything
     * could resume this cancelled, so that it is said.
     */
    private suspend fun restoring(file: OpenedBookFile, mode: RestoreMode, asked: Boolean) {
        val work = work(mode)
        current.value = BackupState.Working(work)
        var restored: Restored? = null
        var failure: BackupNotice? = null
        try {
            restorer.restore(file, book.open.value, mode, { done, total -> progress(work, done, total) }) { restored = it }
        } catch (e: CancellationException) {
            // Cancelled before the book was written, or resumed with the cancel after it was: [restored] says which.
        } catch (e: RestoreRefused) {
            failure = BackupNotice.Refused(e.refusal)
        } catch (e: Exception) {
            failure = BackupNotice.RestoreFailed
        } finally {
            file.close()
        }
        end(
            restored?.let {
                if (mode == RestoreMode.ADD && asked) BackupNotice.Added(it.added, it.alreadyHeld) else BackupNotice.Restored(it.added)
            } ?: failure,
        )
    }

    private fun progress(work: BackupWork, done: Long, total: Long?) {
        val share = total?.takeIf { it > 0 }?.let { (done.toFloat() / it).coerceIn(0f, 1f) } ?: return
        current.update { now ->
            val shown = (now as? BackupState.Working)?.takeIf { it.work == work } ?: return@update now
            val before = shown.done
            if (before == null || share - before >= STEP || (share >= 1f && before < 1f)) BackupState.Working(work, share) else now
        }
    }

    private fun end(notice: BackupNotice?) = synchronized(lock) {
        current.value = notice?.let(BackupState::Ended) ?: BackupState.Idle
        job = null
    }

    companion object {
        /** The type a book file is created as. */
        const val MIME = "application/octet-stream"

        /** The progress shown changes by a hundredth at least. */
        private const val STEP = 0.01f
    }
}
