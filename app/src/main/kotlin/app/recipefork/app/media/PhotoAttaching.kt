package app.recipefork.app.media

import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import app.recipefork.app.book.BookName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Attaches a photograph to one place of a recipe — its cover, or one of its steps: re-encoded and kept in [book] by
 * [store], then handed to [set], which puts it in place of any photograph that place held (MEDIA-001, MEDIA-009) and,
 * once the book holds the change, gives back the one replaced, whose file is then deleted. [unset] takes the place's
 * photograph off the same way. A photograph the place does not take — refused, or offered to a recipe now frozen or
 * to a step since deleted — leaves nothing behind. What the author is told is [statement] until it has been said,
 * and [notice] after; [removed] is what they are told once the photograph is taken off. While a photograph is being
 * prepared, [working] holds. The work runs in [scope], which outlives the screen, so a photograph taken is not lost by
 * leaving. Held by the screen and not across its recreation, it is let go when the phone is turned mid-preparation:
 * the photograph is still attached, but the indicator is gone and "attached" is not said — accepted; it is never said
 * twice.
 */
class PhotoAttaching(
    private val store: PhotoStore,
    private val book: BookName,
    private val scope: CoroutineScope,
    private val removed: PhotoNotice,
    private val set: (mediaId: String, written: suspend (replaced: String?) -> Unit) -> Boolean,
    private val unset: (written: suspend (removed: String) -> Unit) -> Boolean,
) {
    /** One thing to tell the author: each told is a statement of its own, even where its [notice] is the last one's. */
    class Statement(val notice: PhotoNotice)

    private val last = mutableStateOf<Statement?>(null)
    private val pending = mutableStateOf<Statement?>(null)
    // Written only from the work's own coroutines, on [scope]'s dispatcher, so no write from another thread lands last.
    private val preparing = mutableIntStateOf(0)

    /** What the author was told last, said or not. */
    val notice: State<PhotoNotice?> = derivedStateOf { last.value?.notice }

    /** What the author is to be told and has not been yet. */
    val statement: State<Statement?> get() = pending

    /** Whether a photograph offered here is being prepared. */
    val working: State<Boolean> = derivedStateOf { preparing.intValue > 0 }

    /** Tells the author [notice]. */
    fun tell(notice: PhotoNotice) {
        val told = Statement(notice)
        last.value = told
        pending.value = told
    }

    /** [told] has been said. */
    fun stated(told: Statement) {
        if (pending.value === told) pending.value = null
    }

    fun attach(offer: PhotoOffer): Job {
        val done = PhotoWork.begin()
        // Started whatever happens to the scope meanwhile, so that what it counts in is always counted out.
        val job = scope.launch(start = CoroutineStart.ATOMIC) {
            preparing.intValue += 1
            try {
                val outcome = try {
                    store.add(book, offer.open)
                } finally {
                    offer.release()
                }
                val told = when (outcome) {
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
                told?.let(::tell)
            } finally {
                preparing.intValue -= 1
            }
        }
        job.invokeOnCompletion { done() }
        return job
    }

    fun remove() {
        if (unset { store.delete(book, it) }) tell(removed)
    }
}
