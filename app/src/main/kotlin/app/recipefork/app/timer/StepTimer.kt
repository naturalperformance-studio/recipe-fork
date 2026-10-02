package app.recipefork.app.timer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.recipefork.app.R
import app.recipefork.app.RecipeForkApplication
import app.recipefork.app.book.BookName
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.Step
import app.recipefork.core.timer.TimerName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import kotlin.time.Duration

/**
 * The running timers as the card reads them, the ended ones ringing, and the book whose recipes the card shows.
 * Starting and cancelling run in [scope], which outlives the card, so that each finishes its write and the scheduling
 * or withdrawing of its announcement however soon the card is left (FLOW-013 (7)); they run one at a time.
 */
class CardTimers(
    val store: RunningTimers,
    val scope: CoroutineScope,
    val access: NotificationAccess,
    val ringing: TimerRinging = TimerRinging(store.clock, NoRinging),
    val book: () -> BookName,
) {
    internal val turns = Mutex()
}

/** Rings nothing: the card's timers where nothing announces them. */
object NoRinging : RingingSound {
    override fun start() = Unit
    override fun stop() = Unit
}

/** The card's timers; tests provide their own, held in memory and run by a clock they move. */
val LocalCardTimers = staticCompositionLocalOf<CardTimers?> { null }

/** The card's timers: those provided, or else the device's own from the composition root. */
@Composable
fun rememberCardTimers(): CardTimers {
    val provided = LocalCardTimers.current
    val context = LocalContext.current.applicationContext
    return remember(provided, context) {
        provided ?: (context as RecipeForkApplication).container.let { container ->
            CardTimers(container.timers, container.timerWork, AndroidNotificationAccess(context), container.timerRinging) {
                container.activeBook.open.value.name
            }
        }
    }
}

/**
 * The timers of one recipe as its card shows them: what is held for each step, the present moment they are read at,
 * and the restart question while it is asked. The moment moves only while one of them is running.
 */
@Stable
class RecipeTimers internal constructor(
    private val card: CardTimers,
    private val book: BookName,
    private val recipeId: RecipeId,
) {
    private val store get() = card.store
    internal val access get() = card.access

    internal var held by mutableStateOf<Map<Int, RunningTimer>>(emptyMap())
    internal var now: Instant by mutableStateOf(store.clock.now())

    /** The ended timers ringing now, of every recipe. */
    internal var ringing by mutableStateOf<Set<TimerKey>>(emptySet())

    /** The recipe's title as the card shows it now: what a timer started from it announces (FLOW-013 (3)). */
    internal var title: String = ""

    /** The step whose running timer the reader started again, while they are asked whether to restart it. */
    internal var asking by mutableStateOf<Step?>(null)
        private set

    /** How many timers this card has started: each start is followed by a look at notifications (FLOW-013 (8–13)). */
    internal var starts by mutableIntStateOf(0)
        private set

    private fun key(step: Step) = TimerKey(book, recipeId, step.position)

    /**
     * Starts [step]'s timer from its full duration. One that is running is not restarted but asked about (FLOW-021);
     * one that has ended starts again with no question (FLOW-021 (5)). Whether it runs is read from the store, after
     * any start or cancel still being written, never from what the card has loaded so far.
     */
    internal fun start(step: Step) {
        val title = title
        card.scope.launch {
            card.turns.withLock {
                val running = store.get(key(step))?.takeUnless { it.hasEnded(store.clock.now()) }
                if (running != null) {
                    withContext(Dispatchers.Main) { asking = step }
                } else {
                    startNow(step, title)
                }
            }
        }
    }

    /** The reader confirmed: the timer starts again from its full duration; what it had counted is gone. */
    internal fun confirmRestart() {
        val step = asking ?: return
        asking = null
        val title = title
        card.scope.launch { card.turns.withLock { startNow(step, title) } }
    }

    /** The reader declined: the timer keeps running with the time it had. */
    internal fun declineRestart() {
        asking = null
    }

    /** The reader stopped [step]'s timer ringing; it stays finished, and its notification stays, silent. */
    internal fun stopRinging(step: Step) {
        card.ringing.stop(key(step))
    }

    internal fun rings(step: Step): Boolean = key(step) in ringing

    internal fun cancel(step: Step) {
        card.scope.launch { card.turns.withLock { store.cancel(key(step)) } }
    }

    /**
     * Drops each held timer whose step no longer carries it — no timer at that position any more, or one with another
     * name or duration — so that none is left hidden and uncancellable, nor announced as what the step no longer is.
     */
    internal fun dropStale(steps: List<Step>, held: Map<Int, RunningTimer>) {
        val stale = held.values.filter { timer ->
            val step = steps.firstOrNull { it.position == timer.key.stepPosition }
            step == null || step.timer?.duration != timer.duration || TimerName.of(step) != timer.name
        }
        if (stale.isEmpty()) return
        card.scope.launch {
            card.turns.withLock {
                stale.forEach { timer ->
                    // Only the very timer found stale: one started again meanwhile is the step's own.
                    if (store.get(timer.key)?.endsAt == timer.endsAt) store.cancel(timer.key)
                }
            }
        }
    }

    private suspend fun startNow(step: Step, title: String) {
        val duration = step.timer?.duration ?: return
        val name = TimerName.of(step) ?: return
        store.start(key(step), name, title, duration)
        withContext(Dispatchers.Main) { starts++ }
    }
}

/** The timers of [recipeId], titled [title], in the card's book, kept current while the card shows them. */
@Composable
fun rememberRecipeTimers(recipeId: RecipeId, title: String): RecipeTimers {
    val card = rememberCardTimers()
    val book = remember(card) { card.book() }
    val timers = remember(card, book, recipeId) { RecipeTimers(card, book, recipeId) }
    timers.title = title
    LaunchedEffect(timers) {
        card.store.ofRecipe(book, recipeId).collect { timers.held = it }
    }
    LaunchedEffect(timers) {
        card.ringing.ringing.collect { timers.ringing = it }
    }
    val held = timers.held
    LaunchedEffect(timers, held) {
        val clock = card.store.clock
        timers.now = clock.now()
        if (held.values.all { it.hasEnded(timers.now) }) return@LaunchedEffect
        // Read the clock until the last of them has ended, and once more to show it ended.
        clock.ticks.transformWhile { now ->
            emit(now)
            held.values.any { !it.hasEnded(now) }
        }.collect { timers.now = it }
    }
    return timers
}

/**
 * A step's timer on the card (FLOW-016): a control named by the timer's name that starts it, with its duration beside
 * it; once started, the time remaining instead, counting down by the clock, then that it has finished. A started
 * timer has an action beside it that cancels it, or clears it once finished; while a finished one rings, another
 * beside it stops the ringing.
 */
@Composable
fun StepTimer(step: Step, timers: RecipeTimers, modifier: Modifier = Modifier) {
    val timer = step.timer ?: return
    val name = TimerName.of(step) ?: return
    val held = timers.held[step.position]
    val now = timers.now
    val finished = held != null && held.hasEnded(now)
    val status = when {
        held == null -> durationText(timer.duration)
        finished -> stringResource(R.string.timer_finished)
        else -> remainingText(held.remaining(now))
    }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .clickable(
                    onClickLabel = stringResource(R.string.timer_start_action),
                    role = Role.Button,
                    onClick = { timers.start(step) },
                )
                .testTag(TimerTags.start(step.position)),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                name,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f, fill = false),
            )
            Text(
                status,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.testTag(TimerTags.status(step.position)),
            )
        }
        if (finished && timers.rings(step)) {
            TimerAction(
                text = stringResource(R.string.timer_stop),
                action = stringResource(R.string.timer_stop_action),
                onClick = { timers.stopRinging(step) },
                modifier = Modifier.testTag(TimerTags.stop(step.position)),
            )
        }
        if (held != null) {
            TimerAction(
                text = stringResource(if (finished) R.string.timer_clear else R.string.timer_cancel),
                action = stringResource(if (finished) R.string.timer_clear_action else R.string.timer_cancel_action),
                onClick = { timers.cancel(step) },
                modifier = Modifier.testTag(TimerTags.cancel(step.position)),
            )
        }
    }
}

/** The question asked when a running timer is started again (FLOW-021); nothing while none is asked. */
@Composable
fun RestartQuestion(timers: RecipeTimers) {
    val step = timers.asking ?: return
    val timer = step.timer ?: return
    val remaining = timers.held[step.position]?.remaining(timers.now) ?: Duration.ZERO
    AlertDialog(
        onDismissRequest = timers::declineRestart,
        title = { Text(stringResource(R.string.timer_restart_title)) },
        text = {
            Text(
                stringResource(
                    R.string.timer_restart_question,
                    TimerName.of(step).orEmpty(),
                    remainingText(remaining),
                    durationText(timer.duration),
                ),
            )
        },
        confirmButton = {
            TextButton(
                onClick = timers::confirmRestart,
                modifier = Modifier.heightIn(min = 48.dp).testTag(TimerTags.RESTART_CONFIRM),
            ) { Text(stringResource(R.string.timer_restart_confirm)) }
        },
        dismissButton = {
            TextButton(
                onClick = timers::declineRestart,
                modifier = Modifier.heightIn(min = 48.dp).testTag(TimerTags.RESTART_DECLINE),
            ) { Text(stringResource(R.string.timer_restart_decline)) }
        },
        modifier = Modifier.testTag(TimerTags.RESTART_QUESTION),
    )
}

/** An action beside a started timer, at least 48 by 48 dp (A11Y-003), announced as what it does (A11Y-005). */
@Composable
private fun TimerAction(text: String, action: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .heightIn(min = 48.dp)
            .widthIn(min = 48.dp)
            .clickable(onClickLabel = action, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
    }
}

/** A timer's full duration as the author set it: hours and whole minutes. */
@Composable
private fun durationText(duration: Duration): String {
    val hours = duration.inWholeHours.toInt()
    val minutes = (duration.inWholeMinutes % 60).toInt()
    return when {
        hours == 0 -> stringResource(R.string.timer_duration_minutes, minutes)
        minutes == 0 -> stringResource(R.string.timer_duration_hours, hours)
        else -> stringResource(R.string.timer_duration, hours, minutes)
    }
}

/**
 * The time remaining as a countdown reads it: minutes and seconds, with hours before them from an hour up. A part of a
 * second left counts as a whole one, so the countdown reads 0:00 only once the timer has ended.
 */
fun remainingText(remaining: Duration): String {
    val seconds = (remaining.inWholeMilliseconds + 999) / 1000
    val h = seconds / 3600
    val m = seconds % 3600 / 60
    val s = seconds % 60
    fun two(n: Long) = n.toString().padStart(2, '0')
    return if (h > 0) "$h:${two(m)}:${two(s)}" else "$m:${two(s)}"
}

object TimerTags {
    const val RESTART_QUESTION = "timer-restart"
    const val RESTART_CONFIRM = "timer-restart-confirm"
    const val RESTART_DECLINE = "timer-restart-decline"
    const val UNHEARD = "timer-unheard"
    const val UNHEARD_TEXT = "timer-unheard-text"
    const val UNHEARD_SETTINGS = "timer-unheard-settings"
    const val UNHEARD_DISMISS = "timer-unheard-dismiss"
    fun start(position: Int) = "timer-start-$position"
    fun status(position: Int) = "timer-status-$position"
    fun cancel(position: Int) = "timer-cancel-$position"
    fun stop(position: Int) = "timer-stop-$position"
}
