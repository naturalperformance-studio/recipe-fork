package app.recipefork.app.collections

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.recipefork.app.R
import app.recipefork.app.book.CollectionRow

/**
 * The collections of a view of the book (v0.36) — the reader's own above the own recipes, the imported ones above the
 * imported recipes — as a row of cards under its heading, scrolled sideways: each names its collection and how many
 * recipes it holds, and opens it ([onOpen], with its identity). Nothing is drawn while there are none.
 */
@Composable
internal fun CollectionsRow(collections: List<CollectionRow>, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    if (collections.isEmpty()) return
    Column(modifier.fillMaxWidth().testTag(CollectionScreenTags.ROW), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.collections_heading),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp).semantics { heading() },
        )
        // Every card as tall as the tallest, so that the row reads as one band whatever a name's length.
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .height(IntrinsicSize.Max)
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            collections.forEach { collection -> CollectionCard(collection, onOpen) }
        }
    }
}

/** One collection of the row: its name, at most three lines, and its count of recipes; tapping it opens it. */
@Composable
private fun CollectionCard(collection: CollectionRow, onOpen: (String) -> Unit) {
    val open = { onOpen(collection.collectionId) }
    val action = stringResource(R.string.collections_open_action)
    Card(
        onClick = open,
        modifier = Modifier
            .widthIn(min = CARD_MIN_WIDTH, max = CARD_MAX_WIDTH)
            .heightIn(min = 48.dp)
            .fillMaxHeight()
            .semantics {
                onClick(label = action) {
                    open()
                    true
                }
            }
            .testTag(CollectionScreenTags.card(collection.collectionId)),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                collection.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 3,
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

/** A collection's card is at least this wide, so that a short name still reads as a card. */
private val CARD_MIN_WIDTH = 136.dp

/** A collection's card is at most this wide; a longer name wraps. */
private val CARD_MAX_WIDTH = 220.dp
