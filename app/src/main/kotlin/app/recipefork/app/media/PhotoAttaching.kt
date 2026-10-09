package app.recipefork.app.media

import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import app.recipefork.app.book.BookName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
 *
 * A place that takes clips — a step, never the cover — is given [clips] and [setClip]: a clip offered is converted by
 * [clips] (MEDIA-004) and handed to [setClip] as a photograph is to [set], replacing whatever item the place held.
 * While one is being converted, [converting] holds, and [cancelClip] stops it, leaving nothing of it behind. The last
 * item offered wins: a photograph offered stops a clip still being converted there. What is prepared waits for
 * [ready] before it is placed, and is not placed if it says no. A clip the place does not take — its step deleted
 * meanwhile, its recipe frozen or gone — is not kept, and the author is told so.
 *
 * A place that takes clips may take animations too (scenario 3): given [animations], an animated GIF or WebP offered
 * to [attachAnimation] is turned into a silent clip by it and attached exactly as a clip is.
 */
class PhotoAttaching(
    private val store: PhotoStore,
    private val book: BookName,
    private val scope: CoroutineScope,
    private val removed: PhotoNotice,
    private val set: (mediaId: String, written: suspend (replaced: String?) -> Unit) -> Boolean,
    private val unset: (written: suspend (removed: String) -> Unit) -> Boolean,
    private val clips: ClipStore? = null,
    private val setClip: (mediaId: String, written: suspend (replaced: String?) -> Unit) -> Boolean = { _, _ -> false },
    private val ready: suspend () -> Boolean = { true },
    private val animations: ClipStore? = null,
) {
    /** One thing to tell the author: each told is a statement of its own, even where its [notice] is the last one's. */
    class Statement(val notice: PhotoNotice)

    private val last = mutableStateOf<Statement?>(null)
    private val pending = mutableStateOf<Statement?>(null)
    // Written only from the work's own coroutines, on [scope]'s dispatcher, so no write from another thread lands last.
    private val preparing = mutableIntStateOf(0)
    private val convertingClips = mutableIntStateOf(0)
    private var clipJob: Job? = null

    /** What the author was told last, said or not. */
    val notice: State<PhotoNotice?> = derivedStateOf { last.value?.notice }

    /** What the author is to be told and has not been yet. */
    val statement: State<Statement?> get() = pending

    /** Whether a photograph or a clip offered here is being prepared. */
    val working: State<Boolean> = derivedStateOf { preparing.intValue > 0 }

    /** Whether a clip offered here is being converted; [cancelClip] stops it. */
    val converting: State<Boolean> = derivedStateOf { convertingClips.intValue > 0 }

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

    fun attach(offer: PhotoOffer): Job = work {
        clipJob?.cancel()
        val outcome = try {
            store.add(book, offer.open)
        } finally {
            offer.release()
        }
        when (outcome) {
            is PhotoOutcome.Refused -> PhotoNotice.of(outcome.refusal)
            is PhotoOutcome.Stored -> placed(outcome.mediaId, set, PhotoNotice.PROCESSED, notPlaced = null)
        }
    }

    /**
     * Converts the clip [offer] and attaches it in place of whatever item the place held; a clip still being converted
     * here is stopped first, as only one item is held. A place that takes no clips refuses it as unreadable.
     */
    fun attachClip(offer: ClipOffer): Job = attachClip(offer, clips)

    /**
     * Turns the animation [offer] into a silent clip and attaches it as [attachClip] does a clip — refused as a clip
     * is, and stopped as one by a later offer or [cancelClip]. A place that takes no animations refuses it as
     * unreadable.
     */
    fun attachAnimation(offer: ClipOffer): Job = attachClip(offer, animations)

    private fun attachClip(offer: ClipOffer, converting: ClipStore?): Job {
        clipJob?.cancel()
        val job = work(clip = true) {
            val outcome = try {
                converting?.add(book, offer.source) ?: ClipOutcome.Refused(ClipRefusal.UNREADABLE)
            } finally {
                // The capture's own file is deleted however the conversion ends, cancelled included.
                withContext(NonCancellable + Dispatchers.IO) { offer.release() }
            }
            when (outcome) {
                is ClipOutcome.Refused -> PhotoNotice.of(outcome.refusal)
                is ClipOutcome.Stored -> placed(outcome.mediaId, setClip, PhotoNotice.CLIP_PROCESSED, PhotoNotice.CLIP_NOT_ATTACHED)
            }
        }
        clipJob = job
        return job
    }

    /** Stops the clip being converted here, if one is: nothing of it is attached or kept, and nothing is said. */
    fun cancelClip() {
        clipJob?.cancel()
    }

    /** Hands [mediaId] to [place] once [ready]; what was not taken is deleted, and [notPlaced] said of it. */
    private suspend fun placed(
        mediaId: String,
        place: (mediaId: String, written: suspend (replaced: String?) -> Unit) -> Boolean,
        told: PhotoNotice,
        notPlaced: PhotoNotice?,
    ): PhotoNotice? {
        return if (ready() && place(mediaId) { replaced -> replaced?.let { store.delete(book, it) } }) {
            told
        } else {
            store.delete(book, mediaId)
            notPlaced
        }
    }

    /** Runs [prepare] in [scope], counted while it runs, and tells the author what it gives back. */
    private fun work(clip: Boolean = false, prepare: suspend () -> PhotoNotice?): Job {
        val done = PhotoWork.begin()
        // Started whatever happens to the scope meanwhile, so that what it counts in is always counted out.
        val job = scope.launch(start = CoroutineStart.ATOMIC) {
            preparing.intValue += 1
            if (clip) convertingClips.intValue += 1
            try {
                prepare()?.let(::tell)
            } finally {
                preparing.intValue -= 1
                if (clip) convertingClips.intValue -= 1
            }
        }
        job.invokeOnCompletion { done() }
        return job
    }

    /** Takes the place's item off and tells the author [told]. */
    fun remove(told: PhotoNotice = removed) {
        if (unset { store.delete(book, it) }) tell(told)
    }
}
