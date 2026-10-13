package app.recipefork.app.print

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.recipefork.app.R

/**
 * What a screen shows while the document its ⋮'s Print or Share as PDF took is being made (v0.38, T-269): under the
 * bar, over the top of the screen's content — shifting nothing — a running linear progress with "Preparing the
 * document" written under it, the same on the book, a card, a collection and the shopping list. The screen reader reads
 * the words as they appear (a polite live region), the progress and its words as one.
 */
@Composable
fun PrintProgress(modifier: Modifier = Modifier) {
    Surface(modifier.zIndex(1f).fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(
            Modifier
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
                .testTag(PrintTags.PROGRESS),
        ) {
            LinearProgressIndicator(Modifier.fillMaxWidth(), trackColor = MaterialTheme.colorScheme.surfaceContainerHighest)
            Text(
                stringResource(R.string.print_preparing),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).testTag(PrintTags.PROGRESS_WORDS),
            )
        }
    }
}

object PrintTags {
    /** The progress and its words, as the screen reader is given them. */
    const val PROGRESS = "print-progress"

    /** The words alone, as drawn. */
    const val PROGRESS_WORDS = "print-progress-words"
}
