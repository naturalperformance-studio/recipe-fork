package app.recipefork.app.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import app.recipefork.app.ui.WordFitText
import app.recipefork.core.recipe.Recipe
import kotlinx.coroutines.CoroutineScope

/**
 * Attaches a photograph as the cover of the recipe [editor] holds, replacing any (MEDIA-009); see [PhotoAttaching].
 */
fun CoverAttaching(editor: RecipeEditor, store: PhotoStore, book: BookName, scope: CoroutineScope): PhotoAttaching =
    PhotoAttaching(store, book, scope, PhotoNotice.REMOVED, set = editor::setCover, unset = editor::removeCover)

/**
 * The cover, at the title (FLOW-026): described as optional; empty, a compact tile — not the cover's 4:3 frame — so a
 * new recipe's ingredients stay near the top. A tap on the tile or on the cover opens one sheet of taking a photo,
 * choosing one from the gallery and — for a cover set — its removal. A frozen recipe shows its cover and nothing to
 * change it; a frozen one without a cover shows nothing.
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
        if (editable && cover != null) Text(stringResource(R.string.media_cover), style = MaterialTheme.typography.labelLarge)
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
                if (working) PhotoWorking(Modifier.align(Alignment.TopEnd).padding(8.dp).testTag(CoverTags.WORKING))
            } else {
                CoverTile(working, onClick = { sheet = true })
            }
        }
    }
    if (editable && sheet) {
        val items = listOfNotNull(
            SourceItem(R.drawable.ic_step_photo, stringResource(R.string.media_source_take), CoverTags.TAKE, onClick = sources::takePhoto),
            SourceItem(R.drawable.ic_photo_library, stringResource(R.string.media_source_choose), CoverTags.CHOOSE, onClick = sources::choosePhoto),
            cover?.let {
                SourceItem(R.drawable.ic_remove_row, stringResource(R.string.media_cover_remove), CoverTags.REMOVE, destructive = true, onClick = attaching::remove)
            },
        )
        SourceSheet(
            title = stringResource(if (cover == null) R.string.media_cover_add else R.string.media_cover_change),
            items = items,
            onDismiss = { sheet = false },
        )
    }
}

/**
 * The empty cover: one tinted tile, a row at least [TILE_HEIGHT] tall — a picture with a plus, then what a tap does and,
 * muted under it, that a cover is optional; the words wrap at a large font scale, and the tile grows with them — a word
 * too wide for its line, «Необязательно», drawn smaller rather than broken ([WordFitText]). At its
 * end the row keeps a place for the sign that a photograph is being prepared, shown there while [working], so the words
 * never run under it and never move when it comes or goes.
 */
@Composable
private fun CoverTile(working: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = TILE_HEIGHT)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag(CoverTags.PLACEHOLDER),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(R.drawable.ic_add_photo),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
        Column(Modifier.weight(1f)) {
            WordFitText(stringResource(R.string.media_cover_add), style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface))
            WordFitText(
                stringResource(R.string.media_cover_optional),
                style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
            )
        }
        Box(Modifier.size(WORKING_SIZE), contentAlignment = Alignment.Center) {
            if (working) PhotoWorking(Modifier.testTag(CoverTags.WORKING))
        }
    }
}

/** The place the empty tile keeps at its end for the sign that a photograph is being prepared: the sign's own size. */
private val WORKING_SIZE = 36.dp

/** How tall the empty cover's tile is at least: a two-line list item's height. */
private val TILE_HEIGHT = 64.dp

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
