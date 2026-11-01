package app.recipefork.app.timer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
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
import app.recipefork.core.timer.TimerName

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
 * first, then the running ones. A group of one shows its timer on its own, as a pill ([BarLine]), each timer of a step
 * on its own (DOM-024) — its name, or its step's [number] where it has none, followed, for an unnamed timer of a step with
 * several, by the number it started with, as on the card and its notification. Tapping a timer brings the card to its
 * step ([onShow]). A group of two or more gathers into one line ([MergedLine]) that opens into its timers' pills and
 * closes again. Never taller than a third of the screen; more lines scroll within it. It rises from the bottom edge as
 * its first line comes and sinks below it, still showing its last lines, as the last goes; between, it grows and shrinks
 * with its lines.
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
                style = MaterialTheme.typography.labelLarge,
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
                        look = TimerLook.FILLED,
                    ) { Text(stringResource(R.string.timer_stop)) }
                } else if (!open) {
                    Text(
                        remainingText(group.first().remaining(timers.now)),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.testTag(TimerTags.BAR_RUNNING_STATUS),
                    )
                    Text(
                        stringResource(R.string.timer_bar_more, count - 1),
                        style = MaterialTheme.typography.labelLarge,
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

/**
 * [timer]'s name in the bar: its own, a derived one short as the card shows it ([TimerName.short]), or its step's
 * [number] where it has none, told apart by its number.
 */
@Composable
private fun barName(timer: RunningTimer, number: Int): String {
    val own = if (timer.source != null) TimerName.short(timer.name) else timer.name
    val name = own.ifBlank { stringResource(R.string.timer_bar_step, number) }
    return timer.number?.let { stringResource(R.string.timer_numbered, name, it) } ?: name
}

/**
 * One timer of the bar as a pill a row's width, inset from the bar's edges, at least the card's [CardTarget] tall;
 * tapping the pill brings the card to its step ([onShow]). Running, the pill is filled in the primary colour and reads as
 * the timer on its step does — the time it has left over its name and that it runs ([RunningFace]) — with a quiet
 * «Cancel timer» under it at its end. Ringing, the pill is on the primary container: its name over its step and the
 * word that it rings, and beside them, where the name keeps room for its words, otherwise under them ([BesideOrUnder]),
 * a filled Stop.
 */
@Composable
private fun BarLine(timer: RunningTimer, ringing: Boolean, timers: RecipeTimers, number: Int, onShow: () -> Unit) {
    val key = timer.key
    val position = key.stepPosition
    val index = key.index
    val cancelled = LocalTimerCancelled.current
    val name = barName(timer, number)
    val colours = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalAlignment = Alignment.End) {
        Surface(
            Modifier.fillMaxWidth(),
            // Half the least height: a pill at that height, its ends still clear of what it holds when it grows taller.
            shape = RoundedCornerShape(CardTarget / 2),
            color = if (ringing) colours.primaryContainer else colours.primary,
            contentColor = if (ringing) colours.onPrimaryContainer else colours.onPrimary,
        ) {
            val line = Modifier
                .fillMaxWidth()
                .heightIn(min = CardTarget)
                .clickable(
                    onClickLabel = stringResource(R.string.timer_bar_show_action),
                    role = Role.Button,
                    onClick = onShow,
                )
                .testTag(TimerTags.barTimer(position, index))
            if (ringing) {
                BesideOrUnder(
                    line.padding(start = 20.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    gap = 12.dp,
                    main = {
                        Column {
                            Text(
                                name,
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.testTag(TimerTags.barName(position, index)),
                            )
                            Text(
                                // Its step, unless its name is already its step's.
                                if (timer.name.isBlank()) {
                                    stringResource(R.string.timer_bar_ringing)
                                } else {
                                    stringResource(R.string.timer_bar_ringing_step, number)
                                },
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.testTag(TimerTags.barStatus(position, index)),
                            )
                        }
                    },
                    trailing = {
                        TimerButton(
                            icon = R.drawable.ic_timer_stop,
                            action = stringResource(R.string.timer_stop_action),
                            tag = TimerTags.barStop(position, index),
                            onClick = { timers.stopRinging(key) },
                            look = TimerLook.FILLED,
                        ) { Text(stringResource(R.string.timer_stop)) }
                    },
                )
            } else {
                Row(
                    line.padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_step_timer),
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    RunningFace(
                        remainingText(timer.remaining(timers.now)),
                        name,
                        TimerTags.barStatus(position, index),
                        nameTag = TimerTags.barName(position, index),
                    )
                }
            }
        }
        if (!ringing) {
            TimerButton(
                icon = R.drawable.ic_close,
                action = stringResource(R.string.timer_cancel_action),
                tag = TimerTags.barCancel(position, index),
                onClick = { timers.cancel(key, cancelled) },
                look = TimerLook.QUIET,
            ) { Text(stringResource(R.string.timer_cancel_timer)) }
        }
    }
}
