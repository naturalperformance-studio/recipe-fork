package app.recipefork.app.media

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** Reads how long a clip lasts (MEDIA-007); null when the file states no duration that can be read. */
fun interface ClipDuration {
    fun of(file: File): Duration?
}

/** Reads how long a clip offered by another app — a picked or recorded one — lasts (MEDIA-007); null as [ClipDuration]. */
fun interface ClipSourceDuration {
    fun of(source: Uri): Duration?
}

/** A clip's duration as the device's media framework reads it from the file's own metadata. */
object RetrieverClipDuration : ClipDuration {
    override fun of(file: File): Duration? = retrievedDuration { setDataSource(file.absolutePath) }
}

/** A clip's duration as the device's media framework reads it from what [context]'s resolver opens. */
class RetrieverClipSourceDuration(context: Context) : ClipSourceDuration {
    private val context = context.applicationContext

    override fun of(source: Uri): Duration? = retrievedDuration { setDataSource(context, source) }
}

private fun retrievedDuration(open: MediaMetadataRetriever.() -> Unit): Duration? = try {
    MediaMetadataRetriever().use { retriever ->
        retriever.open()
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.trim()?.toLongOrNull()?.milliseconds
    }
} catch (e: Exception) {
    // A file the framework cannot open as media states no duration.
    null
}
