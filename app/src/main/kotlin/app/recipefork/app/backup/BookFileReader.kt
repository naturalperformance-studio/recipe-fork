package app.recipefork.app.backup

import android.content.Context
import android.database.sqlite.SQLiteFullException
import app.recipefork.app.book.BookDatabase
import app.recipefork.app.dictionary.DictionaryEntry
import app.recipefork.app.kitchen.KitchenJson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.EOFException
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipException
import java.util.zip.ZipInputStream

/** What opening a saved book came to. */
sealed interface BookFileOpening {
    /** It opened; the caller reads it, then closes it. */
    data class Opened(val book: OpenedBookFile) : BookFileOpening

    /** It was refused, for [refusal]; nothing of it is left on the device. */
    data class Refused(val refusal: BookFileRefusal) : BookFileOpening
}

/**
 * A saved book, opened: [database] is a book database of its own, brought to this application's schema by the same
 * migrations as a book on the phone, which nothing else opens and nothing is to write to; [mediaDirectory] holds its
 * media files under their names, as the book's own directory does; [kitchen] is the My kitchen it carried. [close]
 * closes the database and deletes everything unpacked.
 */
class OpenedBookFile internal constructor(
    val manifest: BookManifest,
    val database: BookDatabase,
    val mediaDirectory: File,
    val kitchen: Set<Long>,
    private val directory: File,
) : Closeable {
    override fun close() = BookFileReader.discard(database, directory)
}

/**
 * Opens a saved book ([BookFile]), trusting nothing in it: the manifest, which must be the file's first entry, is
 * judged before anything else is read; then the database, the media and the kitchen are unpacked into a directory of
 * their own under [directory], each entry's name checked and each held to a cap whatever size the archive declares;
 * what was unpacked is checked against the manifest, and the database opened as a book.
 */
class BookFileReader(
    private val context: Context,
    private val directory: File = File(context.cacheDir, "opened-books"),
    private val equipment: () -> List<DictionaryEntry> = { BookDatabase.shippedEquipment(context) },
) {
    /**
     * Opens the saved book [input] holds, telling [progress] the bytes read of it so far. A refusal, a failure or a
     * cancel leaves nothing unpacked behind. [input] is closed once read.
     */
    suspend fun open(input: InputStream, progress: (read: Long) -> Unit = {}): BookFileOpening {
        var opened: OpenedBookFile? = null
        try {
            return withContext(Dispatchers.IO) {
                opening(input, progress, coroutineContext.job).also { opened = (it as? BookFileOpening.Opened)?.book }
            }
        } catch (e: CancellationException) {
            // Cancelled just as it opened: what was opened is closed, not handed to no one.
            withContext(NonCancellable) { opened?.close() }
            throw e
        }
    }

    private suspend fun opening(input: InputStream, progress: (Long) -> Unit, job: Job): BookFileOpening {
        var target: File? = null
        var database: BookDatabase? = null
        return try {
            val unpacked = input.use {
                val into = Workspaces.take(directory).also { target = it }
                unpack(Counted(input, job, progress), into, job)
            }
            val unpackedInto = target!!
            val file = File(unpackedInto, BookFile.DATABASE)
            // A file that is no SQLite database would be deleted by Android's corruption handler and opened empty.
            if (!isSqlite(file)) throw RefusedBookFile.damaged("the database is no SQLite file")
            database = BookDatabase.open(context, file, equipment)
            val recipes = try {
                database.recipes().count()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (generateSequence<Throwable>(e) { it.cause }.any { it is SQLiteFullException }) throw IOException("the device is full", e)
                throw RefusedBookFile.damaged("the database does not open", e)
            }
            if (recipes != unpacked.manifest.recipes) throw RefusedBookFile.damaged("$recipes recipes, not ${unpacked.manifest.recipes}")
            BookFileOpening.Opened(OpenedBookFile(unpacked.manifest, database, File(unpackedInto, MEDIA), unpacked.kitchen, unpackedInto))
        } catch (e: RefusedBookFile) {
            discard(database, target)
            BookFileOpening.Refused(e.refusal)
        } catch (e: IOException) {
            discard(database, target)
            BookFileOpening.Refused(BookFileRefusal.CANNOT_READ)
        } catch (e: Throwable) {
            withContext(NonCancellable) { discard(database, target) }
            throw e
        }
    }

    private class Unpacked(val manifest: BookManifest, val kitchen: Set<Long>)

    private fun unpack(input: InputStream, target: File, job: Job): Unpacked = try {
        ZipInputStream(input.buffered()).use { zip -> unpack(zip, target, job) }
    } catch (e: ZipException) {
        throw RefusedBookFile.damaged("a damaged archive", e)
    } catch (e: EOFException) {
        throw RefusedBookFile.damaged("a truncated archive", e)
    } catch (e: IllegalArgumentException) {
        throw RefusedBookFile.damaged("a name that is no text", e)
    }

    private fun unpack(zip: ZipInputStream, target: File, job: Job): Unpacked {
        val first = zip.nextEntry
        if (first?.name != BookFile.MANIFEST) throw RefusedBookFile(BookFileRefusal.NOT_A_BOOK_FILE, "no manifest first")
        val manifest = BookManifest.decode(text(zip, MANIFEST_CAP, job))
        val media = File(target, MEDIA).apply { if (!mkdirs()) throw IOException("cannot make $this") }
        var database = false
        var kitchen: Set<Long>? = null
        val names = HashSet<String>()
        var entries = 1
        while (true) {
            val name = zip.nextEntry?.name ?: break
            if (++entries > MAX_ENTRIES) throw RefusedBookFile.damaged("more entries than a book holds")
            val mediaName = name.removePrefix(BookFile.MEDIA)
            when {
                name == BookFile.DATABASE -> {
                    if (database) throw RefusedBookFile.damaged("two databases")
                    database = true
                    File(target, BookFile.DATABASE).outputStream().use { pump(zip, it, job, DATABASE_CAP) }
                }
                name == BookFile.KITCHEN -> {
                    if (kitchen != null) throw RefusedBookFile.damaged("two kitchens")
                    kitchen = KitchenJson.decode(text(zip, KITCHEN_CAP, job)) ?: throw RefusedBookFile.damaged("the kitchen")
                }
                name.startsWith(BookFile.MEDIA) && BookFile.isMediaName(mediaName) -> {
                    if (!names.add(mediaName)) throw RefusedBookFile.damaged("$name twice")
                    File(media, mediaName).outputStream().use { pump(zip, it, job, MEDIA_CAP) }
                }
                name == BookFile.MANIFEST -> throw RefusedBookFile.damaged("two manifests")
                // Anything else — a later application's addition, or a climb out of the directory — is never written.
                else -> pump(zip, Nowhere, job, MEDIA_CAP)
            }
        }
        if (!database) throw RefusedBookFile.damaged("no database")
        if (names.size != manifest.media) throw RefusedBookFile.damaged("${names.size} media files, not ${manifest.media}")
        return Unpacked(manifest, kitchen ?: throw RefusedBookFile.damaged("no kitchen"))
    }

    /** Whether [file] begins as every SQLite database does. */
    private fun isSqlite(file: File): Boolean {
        val head = ByteArray(SQLITE_HEADER.size)
        var read = 0
        file.inputStream().use { input ->
            while (read < head.size) read += input.read(head, read, head.size - read).takeIf { it >= 0 } ?: break
        }
        return read == head.size && head.contentEquals(SQLITE_HEADER)
    }

    private fun text(zip: InputStream, cap: Long, job: Job): String =
        ByteArrayOutputStream().also { pump(zip, it, job, cap) }.toByteArray().toString(Charsets.UTF_8)

    /** [input], telling [progress] how much has been read and stopping when [job] is cancelled. */
    private class Counted(input: InputStream, private val job: Job, private val progress: (Long) -> Unit) : FilterInputStream(input) {
        private var read = 0L

        override fun read(): Int {
            val one = ByteArray(1)
            return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            job.ensureActive()
            val n = super.read(b, off, len)
            if (n > 0) {
                read += n
                progress(read)
            }
            return n
        }
    }

    /** Discards what it is given. */
    private object Nowhere : OutputStream() {
        override fun write(b: Int) = Unit
        override fun write(b: ByteArray, off: Int, len: Int) = Unit
    }

    internal companion object {
        private const val MEDIA = "media"
        private const val MANIFEST_CAP = 64L * 1024
        private const val KITCHEN_CAP = 1024L * 1024
        private const val DATABASE_CAP = 2L * 1024 * 1024 * 1024
        private const val MEDIA_CAP = 256L * 1024 * 1024
        private const val MAX_ENTRIES = 200_000

        private val SQLITE_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

        fun discard(database: BookDatabase?, directory: File?) {
            database?.close()
            directory?.let(Workspaces::release)
        }
    }
}
