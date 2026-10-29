package app.recipefork.app.card

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.recipefork.app.R
import app.recipefork.app.timer.RecipeTimers
import app.recipefork.app.timer.durationText
import app.recipefork.app.timer.remainingText
import app.recipefork.app.timer.shownName
import app.recipefork.core.recipe.ReferenceQuantity
import app.recipefork.core.recipe.Step
import app.recipefork.core.timer.TimerName

/**
 * The timer at [index] of [step] as the card's [CardMode.VIEW] shows it: a duration label — its name and its duration
 * ("Oven · 40 min") in the working face, after the timer's clock, in a thin outline — named as the cooking card names it
 * ([shownName]); while it is held, the time remaining instead, counting down by the clock, then that it has finished.
 * The label is as wide as its words and wraps them where the line is too narrow for them. Nothing on it starts, cancels
 * or stops a timer, and the screen reader reads it as text with no action; the clock and the outline are decorative.
 */
@Composable
internal fun ViewTimer(
    step: Step,
    index: Int,
    timers: RecipeTimers,
    quantity: ReferenceQuantity,
    modifier: Modifier = Modifier,
) {
    val timer = step.timers.getOrNull(index) ?: return
    val current = TimerName.of(step, index, quantity) ?: return
    val held = timers.held[timers.key(step, index)]
    val name = shownName(step, index, held, current)
    val now = timers.now
    val status = when {
        held == null -> durationText(timer.duration)
        held.hasEnded(now) -> stringResource(R.string.timer_finished)
        else -> remainingText(held.remaining(now))
    }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val shape = MaterialTheme.shapes.large
    Row(
        modifier
            .testTag(ViewTimerTags.label(step.position, index))
            .border(LABEL_OUTLINE, MaterialTheme.colorScheme.outlineVariant, shape)
            .padding(start = LABEL_PADDING_START, end = LABEL_PADDING_END, top = LABEL_PADDING_VERTICAL, bottom = LABEL_PADDING_VERTICAL),
        horizontalArrangement = Arrangement.spacedBy(ICON_GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_step_timer), contentDescription = null, tint = muted, modifier = Modifier.size(ICON_SIZE))
        Text(
            stringResource(R.string.card_view_timer, name, status),
            style = MaterialTheme.typography.labelLarge.copy(fontSize = LABEL_TEXT_SIZE, lineHeight = LABEL_TEXT_LINE),
            color = muted,
            modifier = Modifier.weight(1f, fill = false).testTag(ViewTimerTags.line(step.position, index)),
        )
    }
}

/** The size of the clock before a timer's words. */
private val ICON_SIZE = 16.dp

/** The space between the clock and the label's words. */
private val ICON_GAP = 6.dp

/** The width of a duration label's outline. */
private val LABEL_OUTLINE = 1.dp

/** The space inside a duration label before its clock. */
private val LABEL_PADDING_START = 7.dp

/** The space inside a duration label after its words. */
private val LABEL_PADDING_END = 10.dp

/** The space inside a duration label above and below its words. */
private val LABEL_PADDING_VERTICAL = 3.dp

/** The size of a duration label's words. */
internal val LABEL_TEXT_SIZE = 13.sp

/** The line height of a duration label's words. */
private val LABEL_TEXT_LINE = 18.sp

/** Test tags of the duration labels of [CardMode.VIEW]. */
internal object ViewTimerTags {
    /** The text of the timer at [index] of the step at [position]. */
    fun line(position: Int, index: Int = 0) = "card-view-timer-$position-$index"

    /** The whole duration label of the timer at [index] of the step at [position]: its clock, words and outline. */
    fun label(position: Int, index: Int = 0) = "card-view-timer-$position-$index-label"
}
