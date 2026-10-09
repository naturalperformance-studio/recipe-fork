package app.recipefork.app.card

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import app.recipefork.app.R
import app.recipefork.app.timer.RecipeTimers
import app.recipefork.app.timer.durationText
import app.recipefork.app.timer.remainingText
import app.recipefork.app.timer.shownName
import app.recipefork.core.recipe.ReferenceQuantity
import app.recipefork.core.recipe.Step
import app.recipefork.core.timer.TimerName

/**
 * The timer at [index] of [step] as the card's [CardMode.VIEW] shows it: one line of text, its name and its duration
 * ("Oven · 40 min"), named as the cooking card names it ([shownName]); while it is held, the time remaining instead,
 * counting down by the clock, then that it has finished. Nothing on it starts, cancels or stops a timer, and the screen
 * reader reads it as text with no action; the timer's clock icon before it is decorative.
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
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ICON_GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_step_timer), contentDescription = null, tint = muted, modifier = Modifier.size(ICON_SIZE))
        Text(
            stringResource(R.string.card_view_timer, name, status),
            style = MaterialTheme.typography.bodyLarge,
            color = muted,
            modifier = Modifier.weight(1f).testTag(ViewTimerTags.line(step.position, index)),
        )
    }
}

/** The size of the clock before a timer's line. */
private val ICON_SIZE = 18.dp

/** The space between the clock and the line's text. */
private val ICON_GAP = 8.dp

/** Test tags of the timer lines of [CardMode.VIEW]. */
internal object ViewTimerTags {
    /** The text of the timer at [index] of the step at [position]. */
    fun line(position: Int, index: Int = 0) = "card-view-timer-$position-$index"
}
