package app.recipefork.app.card

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.recipefork.app.R
import app.recipefork.app.ui.WordFitText
import kotlinx.coroutines.launch

/**
 * "Original text" from a recipe's card (v0.39): a sheet under Material's drag handle, titled so, holding the text the
 * recipe was made from exactly as the book keeps it, selectable, with one action, Copy, beside the title. Copy puts the
 * whole text on the clipboard and closes the sheet; [onClose] is then told it was copied, for the card to say so. A
 * swipe down, the handle or Back closes it having copied nothing. Copy is the card's [CardTarget] tall, as the sheet
 * stands over the card.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceTextSheet(text: String, onClose: (copied: Boolean) -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val closed by rememberUpdatedState(onClose)
    var going by remember { mutableStateOf(false) }
    val label = stringResource(R.string.card_source_text)
    val copy = {
        if (!going) {
            going = true
            copyToClipboard(context, label, text)
            scope.launch { state.hide() }.invokeOnCompletion { closed(true) }
        }
    }
    ModalBottomSheet(
        onDismissRequest = {
            if (!going) {
                going = true
                closed(false)
            }
        },
        sheetState = state,
        modifier = Modifier.testTag(SourceTextTags.SHEET),
        // Material's handle, as the card's other sheets have: as wide as it is tall, for a full touch target.
        dragHandle = { BottomSheetDefaults.DragHandle(width = 48.dp) },
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            // The title wraps only between words: at a large font scale, a word too wide for its room is drawn smaller.
            BoxWithConstraints(Modifier.weight(1f)) {
                WordFitText(
                    label,
                    MaterialTheme.typography.titleLarge,
                    constraints.maxWidth,
                    Modifier.semantics { heading() }.testTag(SourceTextTags.TITLE),
                )
            }
            TextButton(onClick = copy, modifier = Modifier.heightIn(min = CardTarget).widthIn(min = CardTarget).testTag(SourceTextTags.COPY)) {
                Text(stringResource(R.string.card_source_text_copy))
            }
        }
        SelectionContainer(Modifier.fillMaxWidth().testTag(SourceTextTags.SELECTABLE)) {
            Text(
                text,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp)
                    .testTag(SourceTextTags.TEXT),
            )
        }
    }
}

/** Puts [text] on the clipboard as plain text, under [label]. */
private fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}

object SourceTextTags {
    const val SHEET = "source-text-sheet"
    const val TITLE = "source-text-title"
    const val TEXT = "source-text-text"
    /** What holds the text and makes it selectable. */
    const val SELECTABLE = "source-text-selectable"
    const val COPY = "source-text-copy"
}
