package app.recipefork.app.media

import android.content.res.Resources
import androidx.annotation.StringRes
import app.recipefork.app.R
import app.recipefork.core.recipe.VideoLimits

/**
 * What the author is told about a photograph or a clip they offered (MEDIA-005, MEDIA-006, MEDIA-007, MEDIA-008):
 * said at the bottom of the editor, and gone by itself. That one is being prepared is shown on the place it is for,
 * not told. [args] fill [text]'s placeholders: a clip's limits are read from [VideoLimits], never written twice.
 */
enum class PhotoNotice(@StringRes val text: Int, val args: List<Any> = emptyList()) {
    /** It was attached, re-encoded and stripped of its data (MEDIA-005). */
    PROCESSED(R.string.media_processed),

    UNREADABLE(R.string.media_refused_unreadable),
    TOO_LARGE(R.string.media_refused_too_large),
    NOT_STORED(R.string.media_refused_not_stored),

    /** No photo picker could be opened. */
    UNAVAILABLE(R.string.media_unavailable),

    /** The cover photograph was removed. */
    REMOVED(R.string.media_removed),

    /** A step's photograph was removed. */
    STEP_REMOVED(R.string.media_step_removed),

    /** A clip was attached: converted to the limits and its sound removed (MEDIA-005). */
    CLIP_PROCESSED(R.string.media_clip_processed, listOf(VideoLimits.MAX_LONGER_EDGE, VideoLimits.MAX_BYTES / MEGABYTE)),

    /** The clip lasts longer than the limit, which it names (MEDIA-007). */
    CLIP_TOO_LONG(R.string.media_clip_refused_too_long, listOf(VideoLimits.MAX_DURATION.inWholeSeconds)),

    CLIP_UNREADABLE(R.string.media_clip_refused_unreadable),
    CLIP_TOO_LARGE(R.string.media_clip_refused_too_large, listOf(VideoLimits.MAX_BYTES / MEGABYTE)),
    CLIP_NOT_STORED(R.string.media_clip_refused_not_stored),

    /** No video picker could be opened. */
    CLIP_UNAVAILABLE(R.string.media_clip_unavailable),

    /** The phone has no camera application to record a clip with. */
    CLIP_NO_CAMERA(R.string.media_clip_camera_none),

    /** The camera application could not be opened to record a clip. */
    CLIP_CAMERA_FAILED(R.string.media_clip_camera_failed),

    /** A step's clip was removed. */
    STEP_CLIP_REMOVED(R.string.media_step_clip_removed),
    ;

    /** The notice as said, in [resources]' language. */
    fun said(resources: Resources): String = resources.getString(text, *args.toTypedArray())

    companion object {
        fun of(refusal: PhotoRefusal): PhotoNotice = when (refusal) {
            PhotoRefusal.UNREADABLE -> UNREADABLE
            PhotoRefusal.TOO_LARGE -> TOO_LARGE
            PhotoRefusal.NOT_STORED -> NOT_STORED
        }

        fun of(refusal: ClipRefusal): PhotoNotice = when (refusal) {
            ClipRefusal.TOO_LONG -> CLIP_TOO_LONG
            ClipRefusal.UNREADABLE -> CLIP_UNREADABLE
            ClipRefusal.TOO_LARGE -> CLIP_TOO_LARGE
            ClipRefusal.NOT_STORED -> CLIP_NOT_STORED
        }
    }
}

private const val MEGABYTE = 1024L * 1024
