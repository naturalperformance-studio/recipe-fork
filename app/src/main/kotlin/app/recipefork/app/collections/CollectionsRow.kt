package app.recipefork.app.collections

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.recipefork.app.R
import app.recipefork.app.book.CollectionRow

/**
 * The collections of a view of the book (v0.36) — the reader's own above the own recipes, the imported ones above the
 * imported recipes — as a shelf of narrow spines scrolled sideways (v0.42, T-303): each names its collection and how
 * many recipes it holds, and opens it ([onOpen], with its identity). Nothing is drawn while there are none. Not
 * [enabled] — while the book's selection is on — no spine responds (v0.38).
 */
@Composable
internal fun CollectionsRow(collections: List<CollectionRow>, onOpen: (String) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    if (collections.isEmpty()) return
    val shelf = rememberLazyListState()
    // A lazy row keeps its first spine in place by its key: a collection made since — the newest, standing first — would
    // be put out of sight before it. The row is brought back to its start whenever its first spine changes.
    val first = collections.first().collectionId
    LaunchedEffect(first) { shelf.scrollToItem(0) }
    LazyRow(
        modifier.fillMaxWidth().testTag(CollectionScreenTags.ROW),
        state = shelf,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(SPINE_GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(collections, key = { it.collectionId }) { collection -> CollectionSpine(collection, onOpen, enabled) }
    }
}

/**
 * One collection of the shelf, as a book's spine: a 3 dp stripe at its start, its name on one line — a long one ending
 * with an ellipsis — and its count of recipes under it; at least 48 dp high, growing with the text. Tapping it opens it.
 */
@Composable
private fun CollectionSpine(collection: CollectionRow, onOpen: (String) -> Unit, enabled: Boolean) {
    val open = { onOpen(collection.collectionId) }
    val action = stringResource(R.string.collections_open_action)
    Surface(
        onClick = open,
        enabled = enabled,
        shape = MaterialTheme.shapes.extraSmall,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .sizeIn(minWidth = SPINE_MIN_HEIGHT, minHeight = SPINE_MIN_HEIGHT, maxWidth = SPINE_MAX_WIDTH)
            .semantics {
                if (enabled) {
                    onClick(label = action) {
                        open()
                        true
                    }
                }
            }
            .testTag(CollectionScreenTags.card(collection.collectionId)),
    ) {
        // The stripe runs the spine's height inside its padding, however tall the words make it.
        Row(
            Modifier
                .padding(start = 10.dp, end = 12.dp, top = 6.dp, bottom = 6.dp)
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .width(SPINE_STRIPE)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = STRIPE_ALPHA), MaterialTheme.shapes.extraSmall)
                    .testTag(CollectionScreenTags.spineStripe(collection.collectionId)),
            )
            Column {
                Text(
                    collection.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag(CollectionScreenTags.cardName(collection.collectionId)),
                )
                Text(
                    pluralStringResource(R.plurals.collections_count, collection.count, collection.count),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag(CollectionScreenTags.cardCount(collection.collectionId)),
                )
            }
        }
    }
}

/** A spine is at least this high, and this wide — a touch target — and grows with its words. */
internal val SPINE_MIN_HEIGHT = 48.dp

/** The stripe at a spine's start. */
internal val SPINE_STRIPE = 3.dp

/** The stripe is the secondary ink, lightened, as the redesign draws it. */
private const val STRIPE_ALPHA = 0.6f

/** The space between two spines. */
private val SPINE_GAP = 8.dp

/** A spine is at most this wide; a longer name ends with an ellipsis. */
internal val SPINE_MAX_WIDTH = 240.dp
