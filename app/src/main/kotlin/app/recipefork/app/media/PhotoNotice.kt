package app.recipefork.app.media

import androidx.annotation.StringRes
import app.recipefork.app.R

/** What the author is told about a photograph they offered (MEDIA-005, MEDIA-006, MEDIA-008). */
enum class PhotoNotice(@StringRes val text: Int) {
    /** It is being processed. */
    WORKING(R.string.media_working),

    /** It was attached, re-encoded and stripped of its data (MEDIA-005). */
    PROCESSED(R.string.media_processed),

    UNREADABLE(R.string.media_refused_unreadable),
    TOO_LARGE(R.string.media_refused_too_large),
    NOT_STORED(R.string.media_refused_not_stored),

    /** No camera application or photo picker could be opened. */
    UNAVAILABLE(R.string.media_unavailable),

    /** The photograph was removed. */
    REMOVED(R.string.media_removed),
    ;

    companion object {
        fun of(refusal: PhotoRefusal): PhotoNotice = when (refusal) {
            PhotoRefusal.UNREADABLE -> UNREADABLE
            PhotoRefusal.TOO_LARGE -> TOO_LARGE
            PhotoRefusal.NOT_STORED -> NOT_STORED
        }
    }
}
