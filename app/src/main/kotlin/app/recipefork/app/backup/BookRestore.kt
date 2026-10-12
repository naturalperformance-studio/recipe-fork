package app.recipefork.app.backup

import androidx.room.withTransaction
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.RecipeRows
import app.recipefork.app.kitchen.KitchenStore
import app.recipefork.app.media.PhotoStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** How a saved book goes into a book that holds recipes (OQ-041). */
enum class RestoreMode {
    /** Each recipe of the file the book does not hold is added; every recipe already there stays as it is. */
    ADD,

    /** The book becomes exactly the file's. */
    REPLACE,
}

/**
 * What a restore came to: [added] recipes of the file copied into the book, and [alreadyHeld] of them the book held
 * already, left untouched (none on [RestoreMode.REPLACE]).
 */
data class Restored(val added: Int, val alreadyHeld: Int)

/**
 * A restore refused before anything was written, for [refusal] — the file's book names a media item by what is no
 * media identity this application makes ([BookRestore.isMediaId]), which a file of its own never does — worded as an
 * opening's refusal is.
 */
class RestoreRefused(val refusal: BookFileRefusal, message: String) : Exception(message)

/**
 * Restores a saved book, opened ([OpenedBookFile]), into a book of the device
 * (decisions/20261012-a-book-file-holds-the-books-database.md): each recipe's rows are copied as they are — its
 * identity, state, parent, marks and sending state with them — in one transaction of the book's database; its media
 * files are copied into the book's media directory under [filesDirectory] before it, and the files of what the book
 * no longer holds deleted only after it commits; My kitchen ([kitchen]) is merged on [RestoreMode.ADD] and replaced
 * on [RestoreMode.REPLACE]. A failure or a cancel before the transaction commits leaves the book and its media as
 * they were.
 */
class BookRestore(
    private val filesDirectory: File,
    private val kitchen: KitchenStore,
    /** Told the count of recipes written so far, inside the transaction; a test makes it fail. */
    private val written: (Int) -> Unit = {},
) {
    /**
     * Restores [file] into the book [into] as [mode] has it and returns what it came to. [progress] is told the bytes
     * of media copied of a total known from the start. The work is cancelled by the caller's cancel up to the start of
     * the transaction, and nothing is changed; once the transaction has begun it runs to its end, and what it came to
     * is returned even to a caller cancelled meanwhile — the book is restored then (though a caller cancelled meanwhile
     * may be resumed with its cancel instead: the overload with `committed` is told it first). Throws [RestoreRefused]
     * for a file that cannot be believed, and whatever else stops it when it fails, the book left as it was.
     */
    suspend fun restore(
        file: OpenedBookFile,
        into: ActiveBook.Open,
        mode: RestoreMode,
        progress: (done: Long, total: Long) -> Unit = { _, _ -> },
    ): Restored = restore(file, into, mode, progress) {}

    /** [restore], telling [committed] what it came to once it is done, before anything resumes. */
    suspend fun restore(
        file: OpenedBookFile,
        into: ActiveBook.Open,
        mode: RestoreMode,
        progress: (done: Long, total: Long) -> Unit,
        committed: (Restored) -> Unit,
    ): Restored {
        val copied = mutableListOf<File>()
        val plan = try {
            withContext(Dispatchers.IO) { prepare(file, into, mode, copied, progress, coroutineContext.job) }
        } catch (e: Throwable) {
            withContext(NonCancellable + Dispatchers.IO) { copied.forEach { it.delete() } }
            throw e
        }
        // From here on it is not cancelled: the transaction runs to its end, and what it changed is finished.
        return withContext(NonCancellable) { commit(file, into, mode, plan, copied).also(committed) }
    }

    /** What is to be copied: the rows of the file's recipes the book is to take, read before the transaction. */
    private class Plan(val sourceIds: Set<String>, val rows: List<RecipeRows>)

    /**
     * Reads the rows of the recipes the book is to take — every one on [RestoreMode.REPLACE], those it neither holds
     * nor marks on [RestoreMode.ADD] — and copies the files of their media into the book's directory where no file of
     * that name is there yet, each written aside and moved into place once whole; each file moved so is added to
     * [copied].
     */
    private suspend fun prepare(
        file: OpenedBookFile,
        into: ActiveBook.Open,
        mode: RestoreMode,
        copied: MutableList<File>,
        progress: (Long, Long) -> Unit,
        job: Job,
    ): Plan {
        val source = file.database.recipes()
        // Every media identity of the file names a file; one that is no identity this application makes — a climb out
        // of the directory, a path — refuses the whole file before anything is read into the book.
        source.allMedia().firstOrNull { !isMediaId(it) }?.let {
            throw RestoreRefused(BookFileRefusal.DAMAGED, "a media item named ${it.take(80)}")
        }
        val sourceIds = source.ids()
        val held = if (mode == RestoreMode.REPLACE) emptySet() else heldIds(into)
        val rows = sourceIds.filterNot { it in held }.map { source.get(it) ?: throw IOException("the saved book changed") }
        val directory = PhotoStore(filesDirectory).directory(into.name)
        val files = rows.asSequence().flatMap { it.media }.map { it.mediaId }.distinct().sorted()
            .map { File(file.mediaDirectory, it) to File(directory, it) }
            .filter { (from, to) -> from.isFile && !to.exists() }.toList()
        val total = files.sumOf { it.first.length() }.coerceAtLeast(1)
        var done = 0L
        progress(0, total)
        if (files.isNotEmpty() && !directory.isDirectory && !directory.mkdirs()) throw IOException("cannot make $directory")
        files.forEach { (from, to) ->
            val partial = File(directory, ".${to.name}.part")
            try {
                from.inputStream().use { input ->
                    partial.outputStream().use { pump(input, it, job) { read -> done += read; progress(done, total) } }
                }
                // A file of that name come meanwhile is the book's own, and is kept.
                if (to.exists()) return@forEach
                if (!partial.renameTo(to)) throw IOException("cannot move $to into place")
                copied += to
            } finally {
                partial.delete()
            }
        }
        return Plan(sourceIds.toSet(), rows)
    }

    /**
     * Writes [plan] into the book in one transaction, then deletes the media files the book held before and holds no
     * more, and those copied in that it does not hold; then takes the file's kitchen. A transaction that fails deletes
     * what was copied in.
     */
    private suspend fun commit(file: OpenedBookFile, into: ActiveBook.Open, mode: RestoreMode, plan: Plan, copied: List<File>): Restored {
        val book = into.database
        val (restored, released) = try {
            val marks = file.database.favourites().list()
            book.withTransaction {
                val before = book.recipes().allMedia().toSet()
                if (mode == RestoreMode.REPLACE) book.recipes().clear()
                // Held again inside the transaction: whatever was written to the book since the plan is kept as it is.
                val held = heldIds(into)
                val added = HashSet<String>()
                plan.rows.filterNot { it.recipe.recipeId in held }.forEach { rows ->
                    book.recipes().put(rows)
                    added += rows.recipe.recipeId
                    written(added.size)
                }
                // A recipe's mark goes with it; a mark of what the file holds no content of goes where nothing of it is.
                marks.filter { it.recipeId in added || (it.recipeId !in plan.sourceIds && it.recipeId !in held) }
                    .forEach { book.favourites().put(it) }
                val after = book.recipes().allMedia().toSet()
                Restored(added.size, plan.sourceIds.size - added.size) to (before - after + copied.map { it.name }.filterNot { it in after })
            }
        } catch (e: Throwable) {
            withContext(Dispatchers.IO) { copied.forEach { it.delete() } }
            throw e
        }
        withContext(Dispatchers.IO) {
            val store = PhotoStore(filesDirectory)
            released.forEach { store.delete(into.name, it) }
            when (mode) {
                RestoreMode.ADD -> kitchen.hold(file.kitchen, has = true)
                RestoreMode.REPLACE -> kitchen.replace(file.kitchen)
            }
        }
        return restored
    }

    companion object {
        private val MEDIA_ID = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")

        /**
         * Whether [id] is a media identity as this application makes every one — a UUID's text — and so a plain name
         * of a file in a media directory: no separator, no `..`, no leading dot.
         */
        fun isMediaId(id: String): Boolean = MEDIA_ID.matches(id)
    }

    /** Every identity the book holds a recipe or a mark of. */
    private suspend fun heldIds(into: ActiveBook.Open): Set<String> =
        into.database.recipes().ids().toSet() + into.database.favourites().list().map { it.recipeId }
}
