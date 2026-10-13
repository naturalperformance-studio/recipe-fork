package app.recipefork.app.editor

import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * One action of a block as an icon, 48 by 48 dp (A11Y-003): the screen reader speaks its [label], which names the
 * block (A11Y-005), and a long press shows the same [label] in a tooltip, so its meaning is never left to the icon
 * alone. A click is announced as [action] when given. A [destructive] action — a removal — is drawn in the error colour.
 * One not [enabled] does not respond to a click, and is drawn dimmed as Material draws a disabled icon button.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun BlockAction(
    @DrawableRes icon: Int,
    label: String,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    action: String? = null,
    destructive: Boolean = false,
    enabled: Boolean = true,
) {
    val tooltip = rememberTooltipState()
    val scope = rememberCoroutineScope()
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip(Modifier.testTag(EditorTags.tooltip(tag))) { Text(label) } },
        state = tooltip,
        // The long press is the button's own, so that the button stays one element for the screen reader.
        enableUserInput = false,
        modifier = modifier,
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .combinedClickable(
                    enabled = enabled,
                    role = Role.Button,
                    onClickLabel = action,
                    onClick = onClick,
                    onLongClick = { scope.launch { tooltip.show() } },
                )
                .testTag(tag),
            contentAlignment = Alignment.Center,
        ) {
            val tint = if (destructive) MaterialTheme.colorScheme.error else LocalContentColor.current
            // Not enabled, it is drawn as Material draws a disabled icon button.
            Icon(painterResource(icon), contentDescription = label, tint = if (enabled) tint else tint.copy(alpha = DISABLED_ALPHA))
        }
    }
}

/** How opaque Material draws the content of a disabled control. */
private const val DISABLED_ALPHA = 0.38f
