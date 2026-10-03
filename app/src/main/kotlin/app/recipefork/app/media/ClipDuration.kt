package app.recipefork.app.media

import android.media.MediaMetadataRetriever
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** Reads how long a clip lasts (MEDIA-007); null when the file states no duration that can be read. */
fun interface ClipDuration {
    fun of(file: File): Duration?
}

/** A clip's duration as the device's media framework reads it from the file's own metadata. */
object RetrieverClipDuration : ClipDuration {
    override fun of(file: File): Duration? = try {
        MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.trim()?.toLongOrNull()?.milliseconds
        }
    } catch (e: Exception) {
        // A file the framework cannot open as media states no duration.
        null
    }
}
