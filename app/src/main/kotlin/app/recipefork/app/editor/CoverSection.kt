package app.recipefork.app.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import app.recipefork.app.media.PhotoAttaching
import app.recipefork.app.media.PhotoStore
import app.recipefork.app.media.rememberPhotoSources
import app.recipefork.core.recipe.Recipe
import kotlinx.coroutines.CoroutineScope

/**
 * Attaches a photograph as the cover of the recipe [editor] holds, replacing any (MEDIA-009); see [PhotoAttaching].
 */
fun CoverAttaching(editor: RecipeEditor, store: PhotoStore, book: BookName, scope: CoroutineScope): PhotoAttaching =
    PhotoAttaching(store, book, scope, PhotoNotice.REMOVED, set = editor::setCover, unset = editor::removeCover)

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
