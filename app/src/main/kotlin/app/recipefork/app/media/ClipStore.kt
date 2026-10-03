package app.recipefork.app.media

import android.net.Uri
import app.recipefork.app.book.BookName
import app.recipefork.core.recipe.VideoLimits
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Why a clip was not attached (MEDIA-006, MEDIA-007, MEDIA-008). */
enum class ClipRefusal {
    /** It lasts longer than [VideoLimits.MAX_DURATION]; refused before any conversion (MEDIA-007). */
    TOO_LONG,

    /** Its duration could not be read, or it could not be converted (MEDIA-007, MEDIA-008). */
    UNREADABLE,

    /** Converted, it still weighs more than [VideoLimits.MAX_BYTES] (MEDIA-006). */
    TOO_LARGE,

    /** It was converted but could not be kept on the device. */
    NOT_STORED,
}

/** What became of a clip offered to a step. */
sealed interface ClipOutcome {
    /** It was converted and kept under [mediaId], in its book's media directory, as [file]. */
    data class Stored(val mediaId: String, val file: File) : ClipOutcome

    /** It was refused for [refusal], and nothing of it is kept. */
    data class Refused(val refusal: ClipRefusal) : ClipOutcome
}

/**
 * The clips of the recipe books on this device, kept as photographs are ([PhotoStore]): each converted by [converter]
 * (MEDIA-004) and kept as a file named by its media identity in its book's media directory under [filesDirectory].
 * A clip's [duration] is read first and one too long, or of no duration that can be read, is refused before anything
 * is converted (MEDIA-007). The conversion is written aside and moved into place once complete and within
 * [maxBytes], so a clip too heavy, failed or cancelled half-way leaves nothing behind (MEDIA-006, MEDIA-008).
 */
class ClipStore(
    filesDirectory: File,
    private val converter: ClipConverter,
    private val duration: ClipSourceDuration,
    private val maxBytes: Long = VideoLimits.MAX_BYTES,
) {
    private val photos = PhotoStore(filesDirectory)

    /** The file the media item [mediaId] of [book] is kept in, whether or not it exists. */
    fun file(book: BookName, mediaId: String): File = photos.file(book, mediaId)

    /** Converts the clip [source] and keeps it in [book] under a new media identity. */
    suspend fun add(book: BookName, source: Uri): ClipOutcome = withContext(Dispatchers.IO) {
        val length = duration.of(source)
        if (length == null || !length.isPositive()) return@withContext ClipOutcome.Refused(ClipRefusal.UNREADABLE)
        if (!VideoLimits.accepts(length)) return@withContext ClipOutcome.Refused(ClipRefusal.TOO_LONG)

        val mediaId = UUID.randomUUID().toString()
        val directory = photos.directory(book)
        val target = File(directory, mediaId)
        val partial = File(directory, ".$mediaId.part")
        var kept = false
        try {
            if (!directory.isDirectory && !directory.mkdirs()) return@withContext ClipOutcome.Refused(ClipRefusal.NOT_STORED)
            try {
                converter.convert(source, partial)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Whatever stops the conversion is a clip it cannot process (MEDIA-008), never a crash.
                return@withContext ClipOutcome.Refused(ClipRefusal.UNREADABLE)
            }
            ensureActive()
            val weight = partial.length()
            when {
                !partial.isFile || weight == 0L -> ClipOutcome.Refused(ClipRefusal.UNREADABLE)
                weight > maxBytes -> ClipOutcome.Refused(ClipRefusal.TOO_LARGE)
                !partial.renameTo(target) -> ClipOutcome.Refused(ClipRefusal.NOT_STORED)
                else -> {
                    kept = true
                    ClipOutcome.Stored(mediaId, target)
                }
            }
        } finally {
            partial.delete()
            if (!kept) target.delete()
        }
    }

    /** Deletes the file of the media item [mediaId] of [book]; one already gone is no failure. */
    suspend fun delete(book: BookName, mediaId: String) = photos.delete(book, mediaId)
}
