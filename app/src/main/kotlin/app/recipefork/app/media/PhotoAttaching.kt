package app.recipefork.app.media

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import app.recipefork.app.book.BookName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Attaches a photograph to one place of a recipe — its cover, or one of its steps: re-encoded and kept in [book] by
 * [store], then handed to [set], which puts it in place of any photograph that place held (MEDIA-001, MEDIA-009) and,
 * once the book holds the change, gives back the one replaced, whose file is then deleted. [unset] takes the place's
 * photograph off the same way. A photograph the place does not take — refused, or offered to a recipe now frozen or
 * to a step since deleted — leaves nothing behind. What the author is told is [notice]; [removed] is what they are
 * told once the photograph is taken off. The work runs in [scope], which outlives the screen, so a photograph taken is
 * not lost by leaving.
 */
class PhotoAttaching(
    private val store: PhotoStore,
    private val book: BookName,
    private val scope: CoroutineScope,
    private val removed: PhotoNotice,
    private val set: (mediaId: String, written: suspend (replaced: String?) -> Unit) -> Boolean,
    private val unset: (written: suspend (removed: String) -> Unit) -> Boolean,
    val notice: MutableState<PhotoNotice?> = mutableStateOf(null),
) {
    fun attach(offer: PhotoOffer): Job {
        notice.value = PhotoNotice.WORKING
        val done = PhotoWork.begin()
        val job = scope.launch {
            val outcome = try {
                store.add(book, offer.open)
            } finally {
                offer.release()
            }
            notice.value = when (outcome) {
                is PhotoOutcome.Refused -> PhotoNotice.of(outcome.refusal)
                is PhotoOutcome.Stored -> {
                    if (set(outcome.mediaId) { replaced -> replaced?.let { store.delete(book, it) } }) {
                        PhotoNotice.PROCESSED
                    } else {
                        store.delete(book, outcome.mediaId)
                        null
                    }
                }
            }
        }
        job.invokeOnCompletion { done() }
        return job
    }

    fun remove() {
        if (unset { store.delete(book, it) }) notice.value = removed
    }
}
