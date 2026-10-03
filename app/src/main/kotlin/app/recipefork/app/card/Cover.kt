package app.recipefork.app.card

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import app.recipefork.app.R
import app.recipefork.app.book.BookName
import app.recipefork.app.media.PhotoImage
import app.recipefork.app.media.PhotoStore
import app.recipefork.app.media.cover
import app.recipefork.core.recipe.Recipe

/**
 * The recipe's cover at the top of the card, named for the screen reader by the recipe's title. A recipe without a
 * cover shows nothing in its place (FLOW-019); one whose cover the device does not hold, or cannot read, says the
 * cover is unavailable (FLOW-061).
 */
@Composable
internal fun Cover(recipe: Recipe, book: BookName, modifier: Modifier = Modifier) {
    val cover = recipe.cover ?: return
    val context = LocalContext.current
    val store = remember(context) { PhotoStore(context.applicationContext.filesDir) }
    val file = remember(store, book, cover.mediaId) { store.file(book, cover.mediaId) }
    val unavailable = @Composable { MediaUnavailable(stringResource(R.string.card_cover_unavailable), CardTags.COVER_UNAVAILABLE, modifier) }
    if (!remember(file) { file.isFile }) {
        unavailable()
        return
    }
    PhotoImage(
        file,
        stringResource(R.string.media_cover_description, recipe.title.ifBlank { stringResource(R.string.common_untitled_recipe) }),
        modifier.testTag(CardTags.COVER),
        unavailable = unavailable,
    )
}
