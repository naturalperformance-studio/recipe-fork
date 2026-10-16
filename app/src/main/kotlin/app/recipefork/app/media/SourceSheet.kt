package app.recipefork.app.media

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.recipefork.app.ui.WordFitText

/**
 * One row of a [SourceSheet]: its [icon] and what it says, the tag tests find it by, and what a tap on it does; a
 * [destructive] one — a removal — says it in the error colour.
 */
class SourceItem(
    @DrawableRes val icon: Int,
    val text: String,
    val tag: String,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * Where a photograph or a clip comes from — the camera, the gallery — and, for media already attached, its removal:
 * one modal bottom sheet of [items] under a [title], shared by the cover and the steps; their words wrap whole at any
 * font scale ([WordFitText]). A tap on an item closes the sheet and does what the item says; a swipe down, a tap
 * outside or back close it with nothing done.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceSheet(title: String, items: List<SourceItem>, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = Modifier.testTag(SourceSheetTags.SHEET),
        // As wide as it is tall, so that the handle's own actions (expand, dismiss) have a full touch target.
        dragHandle = { BottomSheetDefaults.DragHandle(width = 48.dp) },
    ) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 8.dp)) {
            // As the editor's sheets title theirs, a word too wide for the line drawn smaller rather than broken.
            Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                WordFitText(title, Modifier.semantics { heading() }.testTag(SourceSheetTags.TITLE), MaterialTheme.typography.titleLarge)
            }
            items.forEach { item ->
                ListItem(
                    // «Сфотографировать» at a large font scale is wider than the line: drawn smaller, never broken.
                    headlineContent = { WordFitText(item.text) },
                    // Said by the words beside it, so the screen reader reads them alone.
                    leadingContent = { Icon(painterResource(item.icon), contentDescription = null, modifier = Modifier.testTag(SourceSheetTags.icon(item.tag))) },
                    colors = ListItemDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        headlineColor = if (item.destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        leadingIconColor = if (item.destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    modifier = Modifier
                        .heightIn(min = 56.dp)
                        .clickable(role = Role.Button) {
                            onDismiss()
                            item.onClick()
                        }
                        .testTag(item.tag),
                )
            }
        }
    }
}

object SourceSheetTags {
    const val SHEET = "source-sheet"
    const val TITLE = "source-sheet-title"

    /** The icon before the item tagged [item]. */
    fun icon(item: String) = "$item-icon"
}
