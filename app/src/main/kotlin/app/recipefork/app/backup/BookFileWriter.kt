package app.recipefork.app.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.withTransaction
import app.recipefork.app.BuildConfig
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.file.RecipeImport
import app.recipefork.app.kitchen.KitchenJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Saves the whole of a book to a file ([BookFile]): its database, its media directory as it is, and My kitchen. The file
 * is built in [workDirectory], a directory of the application's own cleared before and after every write, and only
 * once it is whole is it copied to where the person chose; a write cancelled or failed before then has written nothing
 * there.
 */
class BookFileWriter(
    context: Context,
    private val filesDirectory: File = context.filesDir,
    private val workDirectory: File = File(context.cacheDir, "saving-book"),
    private val appVersion: String = BuildConfig.VERSION_NAME,
    private val clock: () -> Instant = Instant::now,
) {
    /**
     * Writes the file of [book], with the [kitchen] held, to [out] and returns its manifest. [progress] is told the
     * bytes done of a total known from the start: the book's database, media and kitchen read into the file, then as
     * much again for the file copied to [out]. Cancelling stops the write while the file is built, leaving [out]
     * untouched; a cancel that comes while the whole file is copied to [out] lets the copy end, so [out] is never left
     * holding part of it, and the write still ends cancelled. Throws [IOException] when it fails; [out] is the caller's.
     */
    suspend fun write(
        book: ActiveBook.Open,
        kitchen: Set<Long>,
        out: OutputStream,
        progress: (done: Long, total: Long) -> Unit = { _, _ -> },
    ): BookManifest = withContext(Dispatchers.IO) {
        val job = coroutineContext.job
        workDirectory.deleteRecursively()
        if (!workDirectory.mkdirs()) throw IOException("cannot make $workDirectory")
        try {
            val copy = File(workDirectory, BookFile.DATABASE)
            snapshot(book, copy, job)
            val recipes = fold(copy)
            val media = RecipeImport.mediaDirectory(filesDirectory, book.name).listFiles().orEmpty()
                .filter { it.isFile && BookFile.isMediaName(it.name) }.sortedBy { it.name }
            val kitchenBytes = KitchenJson.encode(kitchen).toByteArray(Charsets.UTF_8)
            val manifest = BookManifest(appVersion = appVersion, savedAt = clock(), recipes = recipes, media = media.size)
            val inputs = (copy.length() + media.sumOf { it.length() } + kitchenBytes.size).coerceAtLeast(1)
            val total = 2 * inputs
            var done = 0L
            val count: (Int) -> Unit = { done += it; progress(done.coerceAtMost(inputs), total) }

            val whole = File(workDirectory, "book.zip")
            ZipOutputStream(whole.outputStream().buffered()).use { zip ->
                zip.entry(BookFile.MANIFEST) { it.write(manifest.encode().toByteArray(Charsets.UTF_8)) }
                zip.entry(BookFile.DATABASE) { entry -> copy.inputStream().use { pump(it, entry, job, count = count) } }
                // Photographs and clips are compressed already: storing them as they are saves the time of trying.
                zip.setLevel(Deflater.NO_COMPRESSION)
                media.forEach { file ->
                    zip.entry(BookFile.MEDIA + file.name) { entry ->
                        val input = try {
                            file.inputStream()
                        } catch (e: IOException) {
                            throw IOException("the book changed while it was saved", e)
                        }
                        input.use { pump(it, entry, job, count = count) }
                    }
                }
                zip.setLevel(Deflater.DEFAULT_COMPRESSION)
                zip.entry(BookFile.KITCHEN) { it.write(kitchenBytes) }
            }
            job.ensureActive()
            // The file is whole: it is moved over the place chosen, to its end, so that place never holds part of it.
            withContext(NonCancellable) {
                val length = whole.length().coerceAtLeast(1)
                var moved = 0L
                whole.inputStream().use { input ->
                    pump(input, out, null) { moved += it; progress(inputs + moved * inputs / length, total) }
                }
                out.flush()
            }
            progress(total, total)
            manifest
        } finally {
            withContext(NonCancellable) { workDirectory.deleteRecursively() }
        }
    }

    /**
     * Copies [book]'s database file, with its write-ahead log, to [copy] inside a transaction of the book's database:
     * while it lasts nothing writes to the book, and so neither file changes, while its recipes are still read. The
     * transaction writes nothing and rolls back if the copy fails. SQLite runs no checkpoint on a connection inside a
     * transaction, and API 29 has neither a read-only transaction nor `VACUUM INTO`, so the log is folded into the copy
     * afterwards ([fold]), not into the book.
     */
    private suspend fun snapshot(book: ActiveBook.Open, copy: File, job: Job) {
        val path = book.database.openHelper.writableDatabase.path ?: throw IOException("the book is held in memory")
        book.database.withTransaction {
            File(path).inputStream().use { input -> copy.outputStream().use { pump(input, it, job) } }
            val log = File("$path-wal")
            if (log.isFile) log.inputStream().use { input -> File("${copy.path}-wal").outputStream().use { pump(input, it, job) } }
        }
    }

    /**
     * Folds the write-ahead log copied beside [copy] into it — a full checkpoint, then the journal of a lone file — and
     * returns how many recipes the copy holds.
     */
    private fun fold(copy: File): Int {
        val recipes = SQLiteDatabase.openDatabase(copy.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.rawQuery("pragma wal_checkpoint(full)", null).use { it.moveToFirst() }
            db.rawQuery("pragma journal_mode=delete", null).use { it.moveToFirst() }
            db.rawQuery("select count(*) from recipe", null).use { it.moveToFirst(); it.getInt(0) }
        }
        if (File("${copy.path}-wal").exists()) throw IOException("the copy of the book kept its log")
        return recipes
    }

    private fun ZipOutputStream.entry(name: String, write: (OutputStream) -> Unit) {
        putNextEntry(ZipEntry(name))
        write(this)
        closeEntry()
    }
}

/**
 * Copies [input] to [out] and returns how much: asks [job] before every read, so that a cancel stops it; tells [count]
 * each read's size; refuses the file as damaged once more than [cap] bytes have come.
 */
internal fun pump(input: InputStream, out: OutputStream, job: Job?, cap: Long = Long.MAX_VALUE, count: (Int) -> Unit = {}): Long {
    val buffer = ByteArray(64 * 1024)
    var total = 0L
    while (true) {
        job?.ensureActive()
        val read = input.read(buffer)
        if (read < 0) return total
        total += read
        if (total > cap) throw RefusedBookFile.damaged("an entry over its cap")
        out.write(buffer, 0, read)
        count(read)
    }
}
