package app.recipefork.app.library

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** The one way out of an empty state: its words, its icon when it has one, and what it does. */
internal class EmptyAction(val label: String, @DrawableRes val icon: Int?, val tag: String, val onClick: () -> Unit)

/**
 * A listing that holds nothing: a picture, a one-line headline naming what is empty, at most one sentence, and at most
 * one action that leads out of it (FLOW-042). The headline is read as a heading; the picture is left to the words.
 * The state stands near the top of its view, so that its action is in reach above the keyboard.
 */
@Composable
internal fun EmptyState(
    @DrawableRes picture: Int,
    headline: String,
    sentence: String?,
    action: EmptyAction?,
    tag: String,
    modifier: Modifier = Modifier,
) {
    Column(
        // At a large font scale the state scrolls rather than clipping its action.
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 24.dp).testTag(tag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            painterResource(picture),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(56.dp).testTag(LibraryTags.emptyPicture(tag)),
        )
        Text(
            headline,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() }.testTag(LibraryTags.emptyHeadline(tag)),
        )
        if (sentence != null) {
            Text(
                sentence,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag(LibraryTags.emptySentence(tag)),
            )
        }
        if (action != null) {
            FilledTonalButton(
                onClick = action.onClick,
                contentPadding = if (action.icon == null) ButtonDefaults.ContentPadding else ButtonDefaults.ButtonWithIconContentPadding,
                modifier = Modifier.padding(top = 8.dp).heightIn(min = 48.dp).testTag(action.tag),
            ) {
                if (action.icon != null) {
                    Icon(painterResource(action.icon), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Text(action.label, modifier = Modifier.padding(start = ButtonDefaults.IconSpacing))
                } else {
                    Text(action.label)
                }
            }
        }
    }
}
