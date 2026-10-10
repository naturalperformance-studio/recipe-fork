package app.recipefork.app.timer

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.recipefork.app.card.CardTarget
import app.recipefork.app.ui.Haptic
import app.recipefork.app.ui.rememberHaptics

/**
 * A timer's control — start, stop its ringing, cancel or clear it — wherever a timer is shown: a tonal button at least
 * the card's [CardTarget] tall and wide (A11Y-003, A11Y-007), its [icon] before its [content], its click announced as
 * [action] (A11Y-005), which a long press shows in a tooltip so that its meaning is never left to the icon alone. Each
 * press answers with a confirm haptic — unless it [confirms] nothing, as a press that only asks a question — then does
 * [onClick]. The button itself is tagged [tag]. A timer [running] is told from one at rest at a glance: its control is
 * filled in the theme's primary colour, not tonal. A control at rest [onHighlight] — on the current step's primary
 * container, against which the tonal fill hardly shows — is outlined instead, filled with the surface and edged and
 * labelled in the primary colour.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerButton(
    @DrawableRes icon: Int,
    action: String,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    confirms: Boolean = true,
    running: Boolean = false,
    onHighlight: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    val haptics = rememberHaptics()
    val press = {
        if (confirms) haptics.perform(Haptic.CONFIRM)
        onClick()
    }
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip(Modifier.testTag(TimerTags.tooltip(tag))) { Text(action) } },
        state = rememberTooltipState(),
        modifier = modifier,
    ) {
        FilledTonalButton(
            onClick = press,
            modifier = Modifier
                .heightIn(min = CardTarget)
                .widthIn(min = CardTarget)
                .semantics { onClick(label = action) { press(); true } }
                .testTag(tag),
            colors = when {
                running -> ButtonDefaults.buttonColors()
                onHighlight -> ButtonDefaults.outlinedButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                )
                else -> ButtonDefaults.filledTonalButtonColors()
            },
            // The theme's outline falls short of 3:1 on the dark primary container; the primary colour meets it in both.
            border = if (!running && onHighlight) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            content()
        }
    }
}
