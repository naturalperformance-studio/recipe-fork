package app.recipefork.app.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
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
    SaysPhotoNotices(attaching)
    val working by attaching.working
    val cover = recipe.cover
    val editable = recipe.state.editable
    if (!editable && cover == null) return
    val sources = rememberPhotoSources(
        key = COVER_KEY,
        onPhoto = { attaching.attach(it) },
        onUnavailable = { attaching.tell(PhotoNotice.UNAVAILABLE) },
    )
    Column(Modifier.fillMaxWidth().testTag(CoverTags.COVER), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (editable) Text(stringResource(R.string.media_cover), style = MaterialTheme.typography.labelLarge)
        if (cover != null) {
            Box(Modifier.fillMaxWidth()) {
                PhotoImage(
                    store.file(book, cover.mediaId),
                    stringResource(R.string.media_cover_description, recipe.title.ifBlank { stringResource(R.string.common_untitled_recipe) }),
                    Modifier.testTag(CoverTags.IMAGE),
                )
                if (working) PhotoWorking(Modifier.align(Alignment.TopEnd).padding(8.dp).testTag(CoverTags.WORKING))
            }
        } else if (working) {
            PhotoWorking(Modifier.testTag(CoverTags.WORKING))
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
    }
}

/** Says at the bottom of the editor what [attaching] tells the author, once each. */
@Composable
internal fun SaysPhotoNotices(attaching: PhotoAttaching) {
    val statements = LocalEditorStatements.current
    val statement by attaching.statement
    val text = statement?.let { stringResource(it.notice.text) }
    LaunchedEffect(statement) {
        val told = statement ?: return@LaunchedEffect
        statements?.say(text.orEmpty())
        attaching.stated(told)
    }
}

/**
 * That a photograph is being prepared for a place: a small indicator on its photograph, or where it will show — no
 * text under it. The screen reader names it.
 */
@Composable
internal fun PhotoWorking(modifier: Modifier = Modifier) {
    val said = stringResource(R.string.media_working)
    Box(
        modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .semantics { contentDescription = said },
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
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
    const val WORKING = "editor-cover-working"
}
