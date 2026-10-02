package app.recipefork.app.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.recipefork.app.R
import app.recipefork.app.book.BookName
import app.recipefork.app.media.PhotoImage
import app.recipefork.app.media.cover
import app.recipefork.app.media.PhotoNotice
import app.recipefork.app.media.PhotoOffer
import app.recipefork.app.media.PhotoOutcome
import app.recipefork.app.media.PhotoStore
import app.recipefork.app.media.PhotoWork
import app.recipefork.app.media.rememberPhotoSources
import app.recipefork.core.recipe.Recipe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Attaches a photograph as the cover of the recipe [editor] holds: re-encoded and kept in [book] by [store], then set
 * as the cover, replacing any (MEDIA-009), whose file is deleted once the book no longer holds it. A photograph the
 * recipe does not take — refused, or offered to a recipe now frozen — leaves nothing behind. What the author is told
 * is [notice]. The work runs in [scope], which outlives the screen, so a photograph taken is not lost by leaving.
 */
class CoverAttaching(
    private val editor: RecipeEditor,
    private val store: PhotoStore,
    private val book: BookName,
    private val scope: CoroutineScope,
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
                    val set = editor.setCover(outcome.mediaId) { replaced -> replaced?.let { store.delete(book, it) } }
                    if (set) {
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
        if (editor.removeCover { store.delete(book, it) }) notice.value = PhotoNotice.REMOVED
    }
}

/**
 * The cover, at the title (FLOW-026): described as optional, taken with the camera or chosen from the gallery, and
 * removable. A frozen recipe shows its cover and nothing to change it; a frozen one without a cover shows nothing.
 */
@Composable
internal fun CoverSection(recipe: Recipe, editor: RecipeEditor, book: BookName, saving: CoroutineScope) {
    val context = LocalContext.current
    val store = remember(context) { PhotoStore(context.applicationContext.filesDir) }
    val attaching = remember(editor, store, book, saving) { CoverAttaching(editor, store, book, saving) }
    val notice by attaching.notice
    val cover = recipe.cover
    val editable = recipe.state.editable
    if (!editable && cover == null) return
    val sources = rememberPhotoSources(
        key = COVER_KEY,
        onPhoto = { attaching.attach(it) },
        onUnavailable = { attaching.notice.value = PhotoNotice.UNAVAILABLE },
    )
    Column(Modifier.fillMaxWidth().testTag(CoverTags.COVER), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (editable) Text(stringResource(R.string.media_cover), style = MaterialTheme.typography.labelLarge)
        cover?.let {
            PhotoImage(
                store.file(book, it.mediaId),
                stringResource(R.string.media_cover_description, recipe.title.ifBlank { stringResource(R.string.common_untitled_recipe) }),
                Modifier.testTag(CoverTags.IMAGE),
            )
        }
        if (editable) {
            FlowRow(Modifier.fillMaxWidth()) {
                ActionText(
                    text = stringResource(R.string.media_cover_take),
                    action = stringResource(R.string.media_cover_take_action),
                    onClick = sources::takePhoto,
                    modifier = Modifier.testTag(CoverTags.TAKE),
                )
                ActionText(
                    text = stringResource(R.string.media_cover_choose),
                    action = stringResource(R.string.media_cover_choose_action),
                    onClick = sources::choosePhoto,
                    modifier = Modifier.testTag(CoverTags.CHOOSE),
                )
                if (cover != null) {
                    ActionText(
                        text = stringResource(R.string.media_cover_remove),
                        action = stringResource(R.string.media_cover_remove_action),
                        onClick = attaching::remove,
                        modifier = Modifier.testTag(CoverTags.REMOVE),
                    )
                }
            }
        }
        notice?.let {
            Text(
                stringResource(it.text),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag(CoverTags.NOTICE),
            )
        }
    }
}

/** The key the cover's camera file is saved under while the camera is in front. */
private const val COVER_KEY = "editor.cover"

object CoverTags {
    const val COVER = "editor-cover"
    const val IMAGE = "editor-cover-image"
    const val TAKE = "editor-cover-take"
    const val CHOOSE = "editor-cover-choose"
    const val REMOVE = "editor-cover-remove"
    const val NOTICE = "editor-cover-notice"
}
