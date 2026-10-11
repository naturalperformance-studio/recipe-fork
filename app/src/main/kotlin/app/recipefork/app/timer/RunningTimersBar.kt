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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
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
 * The bar pinned at the bottom of the card while timers of its recipe run out of sight (FLOW-016): the ringing ones
 * first, then the running ones. A group of one shows its timer on its own line, each timer of a step on its own
 * (DOM-024) — its name, or its step's [number] where it has none, followed, for an unnamed timer of a step with several,
 * by the number it started with, as on the card and its notification; the time it has left or that it rings, and the
 * control that cancels it or stops its ringing — beside the name where the name keeps room for its words, under it
 * otherwise ([BesideOrUnder]). Tapping a line brings the card to its step ([onShow]). A group of two or more gathers
 * into one line ([MergedLine]) that opens into its timers' lines and closes again. Never taller than a third of the
 * screen; more lines scroll within it. It rises from the bottom edge as its first line comes and sinks below it, still
 * showing its last lines, as the last goes; between, it grows and shrinks with its lines.
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
            // In the bar an ended timer is a ringing one: one ended and silent is not shown.
            val (finished, running) = shown.partition { it.hasEnded(timers.now) }
            TimersGroup(timers, finished, finished = true, number, onShow)
            TimersGroup(timers, running, finished = false, number, onShow)
        }
    }
}

/**
 * The bar's [group] of finished (ringing) or running timers, the soonest first: a lone timer on its line; two or more
 * gathered into one line, closed at first, that opens into each timer's line. Once the group is down to one, it closes.
 */
@Composable
private fun TimersGroup(
    timers: RecipeTimers,
    group: List<RunningTimer>,
    finished: Boolean,
    number: (stepPosition: Int) -> Int,
    onShow: (stepPosition: Int) -> Unit,
) {
    val merged = group.size >= 2
    var open by remember(merged) { mutableStateOf(false) }
    if (merged) MergedLine(timers, group, finished, open, number(group.first().key.stepPosition)) { open = !open }
    if (!merged || open) {
        group.forEach { timer ->
            val position = timer.key.stepPosition
            BarLine(timer, ringing = finished, timers, number(position), onShow = { onShow(position) })
        }
    }
}

/**
 * Two or more timers in one line: finished ones as how many have finished, with one Stop that silences them all;
 * running ones as the soonest to end — its name and live countdown — and how many more run, or, while open, how many
 * run. Tapping it opens or closes the list of its timers ([onToggle]). The screen reader reads it with its count.
 */
@Composable
private fun MergedLine(
    timers: RecipeTimers,
    group: List<RunningTimer>,
    finished: Boolean,
    open: Boolean,
    number: Int,
    onToggle: () -> Unit,
) {
    val count = group.size
    BesideOrUnder(
        Modifier
            .fillMaxWidth()
            .heightIn(min = CardTarget)
            .clickable(
                onClickLabel = stringResource(if (open) R.string.timer_bar_hide_action else R.string.timer_bar_list_action),
                role = Role.Button,
                onClick = onToggle,
            )
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .testTag(if (finished) TimerTags.BAR_FINISHED else TimerTags.BAR_RUNNING),
        gap = 12.dp,
        main = {
            Text(
                when {
                    finished -> pluralStringResource(R.plurals.timer_bar_finished, count, count)
                    open -> pluralStringResource(R.plurals.timer_bar_running, count, count)
                    else -> barName(group.first(), number)
                },
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.testTag(if (finished) TimerTags.BAR_FINISHED_NAME else TimerTags.BAR_RUNNING_NAME),
            )
        },
        trailing = {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (finished) {
                    TimerButton(
                        icon = R.drawable.ic_timer_stop,
                        action = stringResource(R.string.timer_bar_stop_all_action),
                        tag = TimerTags.BAR_STOP_ALL,
                        onClick = { group.forEach { timers.stopRinging(it.key) } },
                    ) { Text(stringResource(R.string.timer_stop)) }
                } else if (!open) {
                    Text(
                        remainingText(group.first().remaining(timers.now)),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.testTag(TimerTags.BAR_RUNNING_STATUS),
                    )
                    Text(
                        stringResource(R.string.timer_bar_more, count - 1),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.testTag(TimerTags.BAR_MORE),
                    )
                }
                Icon(
                    painterResource(R.drawable.ic_expand_more),
                    contentDescription = null,
                    modifier = Modifier.rotate(if (open) 180f else 0f),
                )
            }
        },
    )
}

/** [timer]'s name in the bar: its own, or its step's [number] where it has none, told apart by its number. */
@Composable
private fun barName(timer: RunningTimer, number: Int): String {
    val name = timer.name.ifBlank { stringResource(R.string.timer_bar_step, number) }
    return timer.number?.let { stringResource(R.string.timer_numbered, name, it) } ?: name
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
            Text(
                barName(timer, number),
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
