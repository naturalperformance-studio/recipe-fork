package app.recipefork.app.editor

import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.fillMaxWidth
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
 * The row of a block's actions, under what the block holds: one icon each, side by side, going on to a second line only
 * when the block is too narrow for them all — at a large font scale nothing in it is shrunk or cut.
 */
@Composable
internal fun BlockActions(modifier: Modifier = Modifier, content: @Composable FlowRowScope.() -> Unit) {
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start, content = content)
}

/**
 * One action of a block as an icon, 48 by 48 dp (A11Y-003): the screen reader speaks its [label], which names the
 * block (A11Y-005), and a long press shows the same [label] in a tooltip, so its meaning is never left to the icon
 * alone. A click is announced as [action] when given. A [destructive] action — a removal — is drawn in the error colour.
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
                    role = Role.Button,
                    onClickLabel = action,
                    onClick = onClick,
                    onLongClick = { scope.launch { tooltip.show() } },
                )
                .testTag(tag),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(icon), contentDescription = label, tint = if (destructive) MaterialTheme.colorScheme.error else LocalContentColor.current)
        }
    }
}
