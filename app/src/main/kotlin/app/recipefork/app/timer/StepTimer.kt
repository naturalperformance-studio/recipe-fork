package app.recipefork.app.timer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.recipefork.app.R
import app.recipefork.app.RecipeForkApplication
import app.recipefork.app.book.BookName
import app.recipefork.app.card.CardTarget
import app.recipefork.app.ui.Haptic
import app.recipefork.app.ui.rememberHaptics
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.ReferenceQuantity
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
 * The timers of one recipe as its card shows them: what is held for each timer of each step, each on its own
 * (DOM-024), the present moment they are read at, and the restart question while it is asked. The moment moves only
 * while one of them is running.
 */
@Stable
class RecipeTimers internal constructor(
    private val card: CardTimers,
    private val book: BookName,
    private val recipeId: RecipeId,
) {
    private val store get() = card.store
    internal val access get() = card.access

    internal var held by mutableStateOf<Map<TimerKey, RunningTimer>>(emptyMap())
    internal var now: Instant by mutableStateOf(store.clock.now())

    /** The ended timers ringing now, of every recipe. */
    internal var ringing by mutableStateOf<Set<TimerKey>>(emptySet())

    /** The recipe's title as the card shows it now: what a timer started from it announces (FLOW-013 (3)). */
    internal var title: String = ""

    /**
     * The timer of a step that the reader started again while it ran, while they are asked whether to restart it, and
     * the name it would start again under.
     */
    internal var asking by mutableStateOf<Restart?>(null)
        private set

    /** How many timers this card has started: each start is followed by a look at notifications (FLOW-013 (8–13)). */
    internal var starts by mutableIntStateOf(0)
        private set

    internal fun key(step: Step, index: Int) = TimerKey(book, recipeId, step.position, index)

    /** The timer at [index] of [step] as held, or null where none is held for it. */
    internal fun heldOf(step: Step, index: Int): RunningTimer? = held[key(step, index)]

    /**
     * Starts the timer at [index] of [step] from its full duration, under [name]: the name it reads by on the card at
     * this moment (FLOW-016 (8)), which it keeps for as long as it is held. One that is running is not restarted but
     * asked about (FLOW-021); one that has ended starts again with no question (FLOW-021 (5)). The step's other timers
     * are left as they are. Whether it runs is read from the store, after any start or cancel still being written,
     * never from what the card has loaded so far.
     */
    internal fun start(step: Step, index: Int, name: String) {
        val title = title
        card.scope.launch {
            card.turns.withLock {
                val running = store.get(key(step, index))?.takeUnless { it.hasEnded(store.clock.now()) }
                if (running != null) {
                    withContext(Dispatchers.Main) { asking = Restart(step, index, name) }
                } else {
                    startNow(step, index, name, title)
                }
            }
        }
    }

    /** The reader confirmed: the timer starts again from its full duration; what it had counted is gone. */
    internal fun confirmRestart() {
        val restart = asking ?: return
        asking = null
        val title = title
        card.scope.launch { card.turns.withLock { startNow(restart.step, restart.index, restart.name, title) } }
    }

    /** The reader declined: the timer keeps running with the time it had. */
    internal fun declineRestart() {
        asking = null
    }

    /**
     * The reader stopped the ringing of the timer of [key], one of this recipe's; it stays finished, and its
     * notification stays, silent. Any other timer ringing rings on.
     */
    internal fun stopRinging(key: TimerKey) {
        card.ringing.stop(key)
    }

    internal fun rings(key: TimerKey): Boolean = key in ringing

    /** Cancels the timer of [key], one of this recipe's; the other timers of its step run on. */
    internal fun cancel(key: TimerKey) {
        card.scope.launch { card.turns.withLock { store.cancel(key) } }
    }

    /**
     * Drops each held timer its step no longer carries — no step at its position, no timer at its place among the
     * step's timers, or one there with another name or duration — so that none is left hidden and uncancellable, nor
     * announced as what the step no longer is. Each timer is matched on its own: the others of its step keep running.
     * A name read with the step's references at another scale is the step's own: a timer is never dropped because
     * the reader changed the count or fixed an ingredient since it started.
     *
     * A timer is the step's timer at its index: if that one's duration or name differs — also because an earlier
     * timer of the step was removed — the running timer ends; no timer takes over another's run.
     */
    internal fun dropStale(steps: List<Step>, held: Map<TimerKey, RunningTimer>) {
        val stale = held.values.filter { timer ->
            val index = timer.key.index
            val step = steps.firstOrNull { it.position == timer.key.stepPosition }
            step == null || step.timers.getOrNull(index)?.duration != timer.duration ||
                !TimerName.holds(step, index, timer.name, timer.source)
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

    private suspend fun startNow(step: Step, index: Int, name: String, title: String) {
        val duration = step.timers.getOrNull(index)?.duration ?: return
        val source = TimerName.source(step, index)
        store.start(key(step, index), name, title, duration, source, TimerName.number(step, index))
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

/** A start the reader is asked to confirm: the timer at [index] of [step], to start again under [name]. */
internal data class Restart(val step: Step, val index: Int, val name: String)

/**
 * The name the timer at [index] of [step] reads by on the card: told apart, where it shares its name with the step's
 * other timers, by its number (DOM-024). While it is held, the name and the number it started with, as its
 * notification and the bar read them; otherwise [current], its name as the step reads now, and its number there.
 */
@Composable
internal fun shownName(step: Step, index: Int, held: RunningTimer?, current: String): String {
    val name = held?.name ?: current
    val number = if (held != null) held.number else TimerName.number(step, index)
    return number?.let { stringResource(R.string.timer_numbered, name, it) } ?: name
}

/**
 * The timer at [index] of [step] on the card (FLOW-016), apart from the step's other timers (DOM-024): a [TimerButton]
 * named by the timer's name — each reference in a derived one read as [quantity] gives it, as the step reads now
 * (FLOW-016 (8)), or, while it is held, the name it was started under; an unnamed one of a step with several told
 * apart by its number — that starts it, with its duration beside it; once started, the time remaining instead,
 * counting down by the clock, then that it has finished. A running timer's control is filled in the theme's primary
 * colour, one at rest tonal ([TimerButton]). A started timer has an action beside it, where it fits, that cancels it,
 * or clears it once finished; while a finished one rings, another beside it stops the ringing ([BesideOrUnder]).
 */
@Composable
fun StepTimer(
    step: Step,
    index: Int,
    timers: RecipeTimers,
    quantity: ReferenceQuantity,
    modifier: Modifier = Modifier,
) {
    val timer = step.timers.getOrNull(index) ?: return
    val current = TimerName.of(step, index, quantity) ?: return
    val key = timers.key(step, index)
    val held = timers.held[key]
    val name = shownName(step, index, held, current)
    val now = timers.now
    val finished = held != null && held.hasEnded(now)
    val status = when {
        held == null -> durationText(timer.duration)
        finished -> stringResource(R.string.timer_finished)
        else -> remainingText(held.remaining(now))
    }
    val running = held != null && !finished
    // The control first, then its actions beside it where they fit; where they do not — a long name, a large font, a
    // narrow screen — the actions go under it at its end, and the status under the name, each whole (A11Y-004).
    BesideOrUnder(
        modifier.fillMaxWidth(),
        main = {
            TimerButton(
                icon = if (running) R.drawable.ic_step_timer else R.drawable.ic_video_play,
                action = stringResource(R.string.timer_start_action),
                tag = TimerTags.start(step.position, index),
                onClick = { timers.start(step, index, current) },
                // A press on a running timer only asks whether to restart it: the answer confirms, the question does not.
                confirms = !running,
                // A running timer is filled in the theme's primary colour, told at a glance from one at rest.
                running = running,
            ) {
                FlowRow(
                    Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        name,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.align(Alignment.CenterVertically),
                    )
                    Text(
                        status,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.align(Alignment.CenterVertically)
                            .testTag(TimerTags.status(step.position, index)),
                    )
                }
            }
        },
        trailing = if (held == null) null else {
            {
                // Stop and Clear side by side where they fit, Clear under Stop where they do not, each whole.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (finished && timers.rings(key)) {
                        TimerButton(
                            icon = R.drawable.ic_timer_stop,
                            action = stringResource(R.string.timer_stop_action),
                            tag = TimerTags.stop(step.position, index),
                            onClick = { timers.stopRinging(key) },
                        ) { Text(stringResource(R.string.timer_stop)) }
                    }
                    TimerButton(
                        icon = R.drawable.ic_remove_timer,
                        action = stringResource(if (finished) R.string.timer_clear_action else R.string.timer_cancel_action),
                        tag = TimerTags.cancel(step.position, index),
                        onClick = { timers.cancel(key) },
                    ) { Text(stringResource(if (finished) R.string.timer_clear else R.string.timer_cancel)) }
                }
            }
        },
        mainShare = 0.5f,
    )
}

/**
 * [main] with [trailing] beside it at its end where they fit — [main] keeping room for its longest word and at least
 * [mainShare] of the width — [main] then filling the width [trailing] leaves; otherwise [main] the whole width and
 * [trailing] [gap] under it, at its end. Without [trailing], [main] the whole width. Nothing is squeezed into breaking
 * inside a word for want of room beside the other (A11Y-004).
 */
@Composable
internal fun BesideOrUnder(
    modifier: Modifier = Modifier,
    gap: Dp = 8.dp,
    mainShare: Float = 0f,
    main: @Composable () -> Unit,
    trailing: (@Composable () -> Unit)?,
) {
    Layout(contents = listOf(main, trailing ?: {}), modifier = modifier) { (mains, trailings), constraints ->
        val width = constraints.maxWidth
        val space = gap.roundToPx()
        val shownTrailing = trailings.firstOrNull()?.measure(Constraints(maxWidth = width))
        val room = width - (shownTrailing?.width ?: 0) - space
        val beside = shownTrailing != null &&
            room >= maxOf(mains.single().minIntrinsicWidth(Constraints.Infinity), (width * mainShare).toInt())
        // Whatever it holds stands centred in the least height it is given.
        fun place(height: Int, placing: Placeable.PlacementScope.(top: Int) -> Unit) =
            maxOf(height, constraints.minHeight).let { tall -> layout(width, tall) { placing((tall - height) / 2) } }
        when {
            shownTrailing == null -> {
                val shown = mains.single().measure(Constraints(minWidth = width, maxWidth = width))
                place(shown.height) { top -> shown.place(0, top) }
            }
            beside -> {
                val shown = mains.single().measure(Constraints(minWidth = room, maxWidth = room))
                val height = maxOf(shown.height, shownTrailing.height)
                place(height) { top ->
                    shown.place(0, top + (height - shown.height) / 2)
                    shownTrailing.place(width - shownTrailing.width, top + (height - shownTrailing.height) / 2)
                }
            }
            else -> {
                val shown = mains.single().measure(Constraints(minWidth = width, maxWidth = width))
                place(shown.height + space + shownTrailing.height) { top ->
                    shown.place(0, top)
                    shownTrailing.place(width - shownTrailing.width, top + shown.height + space)
                }
            }
        }
    }
}

/** The question asked when a running timer is started again (FLOW-021); nothing while none is asked. */
@Composable
fun RestartQuestion(timers: RecipeTimers) {
    val restart = timers.asking ?: return
    val step = restart.step
    val timer = step.timers.getOrNull(restart.index) ?: return
    val held = timers.heldOf(step, restart.index)
    val remaining = held?.remaining(timers.now) ?: Duration.ZERO
    val haptics = rememberHaptics()
    AlertDialog(
        onDismissRequest = timers::declineRestart,
        title = { Text(stringResource(R.string.timer_restart_title)) },
        text = {
            Text(
                stringResource(
                    R.string.timer_restart_question,
                    shownName(step, restart.index, held, restart.name),
                    remainingText(remaining),
                    durationText(timer.duration),
                ),
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    haptics.perform(Haptic.CONFIRM)
                    timers.confirmRestart()
                },
                modifier = Modifier.heightIn(min = CardTarget).widthIn(min = CardTarget).testTag(TimerTags.RESTART_CONFIRM),
            ) { Text(stringResource(R.string.timer_restart_confirm)) }
        },
        dismissButton = {
            TextButton(
                onClick = timers::declineRestart,
                modifier = Modifier.heightIn(min = CardTarget).widthIn(min = CardTarget).testTag(TimerTags.RESTART_DECLINE),
            ) { Text(stringResource(R.string.timer_restart_decline)) }
        },
        modifier = Modifier.testTag(TimerTags.RESTART_QUESTION),
    )
}

/** A timer's full duration as the author set it: hours and whole minutes. */
@Composable
internal fun durationText(duration: Duration): String {
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
    // A step's first timer is tagged by its step alone, as the step's only timer always was; each other by its place.
    private fun of(position: Int, index: Int) = if (index == 0) "$position" else "$position-$index"
    fun start(position: Int, index: Int = 0) = "timer-start-${of(position, index)}"
    fun status(position: Int, index: Int = 0) = "timer-status-${of(position, index)}"
    fun cancel(position: Int, index: Int = 0) = "timer-cancel-${of(position, index)}"
    fun stop(position: Int, index: Int = 0) = "timer-stop-${of(position, index)}"
    fun tooltip(control: String) = "$control-tooltip"
    const val BAR = "timer-bar"
    fun barTimer(position: Int, index: Int = 0) = "timer-bar-${of(position, index)}"
    fun barStatus(position: Int, index: Int = 0) = "timer-bar-status-${of(position, index)}"
    fun barName(position: Int, index: Int = 0) = "timer-bar-name-${of(position, index)}"
    fun barCancel(position: Int, index: Int = 0) = "timer-bar-cancel-${of(position, index)}"
    fun barStop(position: Int, index: Int = 0) = "timer-bar-stop-${of(position, index)}"
}
