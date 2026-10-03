package app.recipefork.app.media

import android.net.Uri
import java.io.File

/**
 * Converts a step's clip on the device (MEDIA-004): at most [app.recipefork.core.recipe.VideoLimits.MAX_LONGER_EDGE]
 * pixels on its longer edge and never enlarged, no audio track, H.264 in MP4, carrying no metadata but its orientation
 * (MEDIA-003). Its weight is aimed well under the limit but not guaranteed: the caller checks it (MEDIA-006).
 */
fun interface ClipConverter {
    /**
     * Writes the clip [source] converted to [target]. Throws [ClipConversionFailed] when it cannot be converted
     * (MEDIA-008); cancelled, it stops the conversion before returning. Whatever it leaves in [target] on failure is
     * the caller's to delete.
     */
    suspend fun convert(source: Uri, target: File)
}

/** A clip the device could not convert (MEDIA-008). */
class ClipConversionFailed(message: String, cause: Throwable? = null) : Exception(message, cause)
