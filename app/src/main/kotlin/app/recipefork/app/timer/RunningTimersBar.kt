package app.recipefork.app.timer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.recipefork.app.R
import app.recipefork.app.card.CardTarget
import app.recipefork.app.ui.Motion

/**
 * The timers of this recipe the bar at the bottom of the card shows: each running, or ringing once ended, whose step is
 * not [onScreen] — the soonest to end first, so that one ringing comes before any still running.
 */
internal fun RecipeTimers.inBar(onScreen: (stepPosition: Int) -> Boolean): List<RunningTimer> {
    val now = now
    val ringing = ringing
    return held.values
        .filter { !it.hasEnded(now) || it.key in ringing }
        .filterNot { onScreen(it.key.stepPosition) }
        .sortedBy { it.endsAt }
}

/**
 * The bar pinned at the bottom of the card while timers of its recipe run out of sight (FLOW-016): one line for each of
 * [shown], each timer of a step on its own (DOM-024) — its name, or its step's [number] where it has none, followed,
 * for an unnamed timer of a step with several, by the number it started with, as on the card and its notification;
 * the time it has left or that it rings, and the control that cancels it or stops its ringing — beside the name where
 * the name keeps room for its words, under it otherwise ([BesideOrUnder]). Tapping a line brings
 * the card to its step ([onShow]). Never taller than a third of the screen; more lines scroll within it. It rises
 * from the bottom edge as its first line comes and sinks below it, still showing its last lines, as the last goes;
 * between, it grows and shrinks with its lines.
 */
@Composable
internal fun RunningTimersBar(
    timers: RecipeTimers,
    shown: List<RunningTimer>,
    number: (stepPosition: Int) -> Int,
    onShow: (stepPosition: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val most = (LocalConfiguration.current.screenHeightDp / 3).dp
    // The lines the bar last showed, which it keeps while it sinks away.
    val held = remember { arrayOf(shown) }
    if (shown.isNotEmpty()) held[0] = shown
    val lines = held[0]
    AnimatedVisibility(shown.isNotEmpty(), modifier, enter = Motion.rise, exit = Motion.sink) {
        Bar(timers, lines, number, onShow, most)
    }
}

@Composable
private fun Bar(
    timers: RecipeTimers,
    shown: List<RunningTimer>,
    number: (stepPosition: Int) -> Int,
    onShow: (stepPosition: Int) -> Unit,
    most: Dp,
) {
    Surface(
        Modifier.fillMaxWidth().heightIn(max = most).testTag(TimerTags.BAR),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 3.dp,
    ) {
        Column(
            Modifier
                .animateContentSize(Motion.appearing())
                .windowInsetsPadding(
                    WindowInsets.navigationBars.union(WindowInsets.displayCutout)
                        .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
                )
                .verticalScroll(rememberScrollState())
                .padding(vertical = 4.dp),
        ) {
            shown.forEach { timer ->
                val position = timer.key.stepPosition
                BarLine(
                    timer = timer,
                    ringing = timer.key in timers.ringing,
                    timers = timers,
                    number = number(position),
                    onShow = { onShow(position) },
                )
            }
        }
    }
}

@Composable
private fun BarLine(timer: RunningTimer, ringing: Boolean, timers: RecipeTimers, number: Int, onShow: () -> Unit) {
    val key = timer.key
    val position = key.stepPosition
    val index = key.index
    // The name first, its time and its control beside it at the end where the name keeps room for its longest word;
    // otherwise the name has the line's whole width and the time and the control stand under it, at the end, so that
    // a name at a large font scale on a narrow phone is never squeezed into breaking inside a word (A11Y-004).
    BesideOrUnder(
        Modifier
            .fillMaxWidth()
            .heightIn(min = CardTarget)
            .clickable(
                onClickLabel = stringResource(R.string.timer_bar_show_action),
                role = Role.Button,
                onClick = onShow,
            )
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .testTag(TimerTags.barTimer(position, index)),
        gap = 12.dp,
        main = {
            val name = timer.name.ifBlank { stringResource(R.string.timer_bar_step, number) }
            Text(
                timer.number?.let { stringResource(R.string.timer_numbered, name, it) } ?: name,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.testTag(TimerTags.barName(position, index)),
            )
        },
        trailing = {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (ringing) stringResource(R.string.timer_bar_ringing) else remainingText(timer.remaining(timers.now)),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.testTag(TimerTags.barStatus(position, index)),
                )
                if (ringing) {
                    TimerButton(
                        icon = R.drawable.ic_timer_stop,
                        action = stringResource(R.string.timer_stop_action),
                        tag = TimerTags.barStop(position, index),
                        onClick = { timers.stopRinging(key) },
                    ) { Text(stringResource(R.string.timer_stop)) }
                } else {
                    TimerButton(
                        icon = R.drawable.ic_remove_timer,
                        action = stringResource(R.string.timer_cancel_action),
                        tag = TimerTags.barCancel(position, index),
                        onClick = { timers.cancel(key) },
                    ) { Text(stringResource(R.string.timer_cancel)) }
                }
            }
        },
    )
}
