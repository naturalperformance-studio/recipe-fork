package app.recipefork.app.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
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
import app.recipefork.app.media.SourceItem
import app.recipefork.app.media.SourceSheet
import app.recipefork.app.media.rememberPhotoSources
import app.recipefork.core.recipe.Recipe
import kotlinx.coroutines.CoroutineScope

/**
 * Attaches a photograph as the cover of the recipe [editor] holds, replacing any (MEDIA-009); see [PhotoAttaching].
 */
fun CoverAttaching(editor: RecipeEditor, store: PhotoStore, book: BookName, scope: CoroutineScope): PhotoAttaching =
    PhotoAttaching(store, book, scope, PhotoNotice.REMOVED, set = editor::setCover, unset = editor::removeCover)

/**
 * The cover, at the title (FLOW-026): described as optional; empty, a placeholder of the cover's size. A tap on the
 * placeholder or on the cover opens one sheet of the camera, the gallery and — for a cover set — its removal. A frozen recipe shows its cover and nothing to change it; a frozen one without a cover shows nothing.
 */
@Composable
internal fun CoverSection(recipe: Recipe, editor: RecipeEditor, store: PhotoStore, book: BookName, saving: CoroutineScope) {
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
    var sheet by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().testTag(CoverTags.COVER), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (editable) Text(stringResource(R.string.media_cover), style = MaterialTheme.typography.labelLarge)
        Box(Modifier.fillMaxWidth()) {
            if (cover != null) {
                PhotoImage(
                    store.file(book, cover.mediaId),
                    stringResource(R.string.media_cover_description, recipe.title.ifBlank { stringResource(R.string.common_untitled_recipe) }),
                    Modifier.testTag(CoverTags.IMAGE),
                )
                if (editable) {
                    // Over the photograph rather than around it, so that the photograph keeps its own description.
                    val change = stringResource(R.string.media_cover_change)
                    Box(
                        Modifier
                            .matchParentSize()
                            .clickable(onClickLabel = change, role = Role.Button) { sheet = true }
                            .semantics { contentDescription = change }
                            .testTag(CoverTags.CHANGE),
                    )
                }
            } else {
                CoverPlaceholder(onClick = { sheet = true })
            }
            if (working) PhotoWorking(Modifier.align(Alignment.TopEnd).padding(8.dp).testTag(CoverTags.WORKING))
        }
    }
    if (editable && sheet) {
        val items = listOfNotNull(
            SourceItem(stringResource(R.string.media_cover_take), CoverTags.TAKE, sources::takePhoto),
            SourceItem(stringResource(R.string.media_cover_choose), CoverTags.CHOOSE, sources::choosePhoto),
            cover?.let { SourceItem(stringResource(R.string.media_cover_remove), CoverTags.REMOVE, attaching::remove) },
        )
        SourceSheet(
            title = stringResource(if (cover == null) R.string.media_cover_add else R.string.media_cover_change),
            items = items,
            onDismiss = { sheet = false },
        )
    }
}

/** The empty cover: the cover's own 4:3 frame, tinted, with a camera and what a tap does. */
@Composable
private fun CoverPlaceholder(onClick: () -> Unit) {
    val add = stringResource(R.string.media_cover_add)
    Column(
        Modifier
            .fillMaxWidth()
            .aspectRatio(COVER_RATIO)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(role = Role.Button, onClick = onClick)
            .testTag(CoverTags.PLACEHOLDER),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painterResource(R.drawable.ic_step_photo),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp),
        )
        Text(add, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
    }
}

/** The cover's shape: the crop's 4:3 landscape frame. */
private const val COVER_RATIO = 4f / 3f

/** The key the cover's camera file is saved under while the camera is in front. */
private const val COVER_KEY = "editor.cover"

object CoverTags {
    const val COVER = "editor-cover"
    const val IMAGE = "editor-cover-image"
    const val PLACEHOLDER = "editor-cover-placeholder"
    const val CHANGE = "editor-cover-change"
    const val TAKE = "editor-cover-take"
    const val CHOOSE = "editor-cover-choose"
    const val REMOVE = "editor-cover-remove"
    const val WORKING = "editor-cover-working"
}
