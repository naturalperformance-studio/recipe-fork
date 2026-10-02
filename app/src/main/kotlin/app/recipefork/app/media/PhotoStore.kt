package app.recipefork.app.media

import app.recipefork.app.book.BookName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.UUID

/** What became of a photograph offered to a recipe. */
sealed interface PhotoOutcome {
    /** It was re-encoded and kept under [mediaId], in its book's media directory, as [file]. */
    data class Stored(val mediaId: String, val file: File) : PhotoOutcome

    /** It was refused for [refusal], and nothing of it is kept (MEDIA-006, MEDIA-008). */
    data class Refused(val refusal: PhotoRefusal) : PhotoOutcome
}

/**
 * The photographs of the recipe books on this device: each re-encoded (MEDIA-002, MEDIA-003) and kept as a plain JPEG
 * named by its media identity in its book's media directory under [filesDirectory] — the directory a found recipe's
 * media is imported to, so every photograph of a book is held alike (decisions/20260928-one-database-per-recipe-book.md).
 * A photograph is only ever whole in its place: it is written aside and moved there once complete, so one that fails
 * or is cancelled half-way leaves nothing behind (MEDIA-008).
 */
class PhotoStore(
    private val filesDirectory: File,
    private val reencoder: PhotoReencoder = PhotoReencoder(),
    /** Writes the bytes to the file; replaced in a test to fail half-way. */
    private val write: (File, ByteArray) -> Unit = { file, bytes -> file.writeBytes(bytes) },
) {
    /** The directory the media of [book] is kept in. */
    fun directory(book: BookName): File = File(filesDirectory, book.mediaDirectoryName)

    /** The file the media item [mediaId] of [book] is kept in, whether or not it exists. */
    fun file(book: BookName, mediaId: String): File = File(directory(book), mediaId)

    /** Re-encodes the picture [open] reads and keeps it in [book] under a new media identity. */
    suspend fun add(book: BookName, open: () -> InputStream): PhotoOutcome = withContext(Dispatchers.IO) {
        val bytes = try {
            reencoder.reencode(open)
        } catch (e: RefusedPhoto) {
            return@withContext PhotoOutcome.Refused(e.refusal)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Whatever else stops the codec is a file it cannot process (MEDIA-008), never a crash.
            return@withContext PhotoOutcome.Refused(PhotoRefusal.UNREADABLE)
        } catch (e: OutOfMemoryError) {
            return@withContext PhotoOutcome.Refused(PhotoRefusal.UNREADABLE)
        }
        ensureActive()
        val mediaId = UUID.randomUUID().toString()
        val directory = directory(book)
        val target = File(directory, mediaId)
        val partial = File(directory, ".$mediaId.part")
        var kept = false
        try {
            if (!directory.isDirectory && !directory.mkdirs()) return@withContext PhotoOutcome.Refused(PhotoRefusal.NOT_STORED)
            write(partial, bytes)
            ensureActive()
            if (!partial.renameTo(target)) return@withContext PhotoOutcome.Refused(PhotoRefusal.NOT_STORED)
            kept = true
            PhotoOutcome.Stored(mediaId, target)
        } catch (e: IOException) {
            PhotoOutcome.Refused(PhotoRefusal.NOT_STORED)
        } finally {
            partial.delete()
            if (!kept) target.delete()
        }
    }

    /**
     * Copies the file of the media item [from] of [book] to the item [to], whole or not at all: written aside and moved
     * into place once complete. Returns whether it was copied; an item whose file is gone copies nothing.
     */
    suspend fun copy(book: BookName, from: String, to: String): Boolean = withContext(Dispatchers.IO) {
        val source = file(book, from)
        val target = file(book, to)
        val partial = File(directory(book), ".$to.part")
        var kept = false
        try {
            if (!source.isFile) return@withContext false
            source.copyTo(partial, overwrite = true)
            kept = partial.renameTo(target)
            kept
        } catch (e: IOException) {
            false
        } finally {
            partial.delete()
            if (!kept) target.delete()
        }
    }

    /** Deletes the file of the media item [mediaId] of [book]; one already gone is no failure. */
    suspend fun delete(book: BookName, mediaId: String) {
        withContext(Dispatchers.IO) { file(book, mediaId).delete() }
    }
}
