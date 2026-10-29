package app.recipefork.app.card

import androidx.compose.foundation.layout.Column
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.text.TextLayoutResult
import app.recipefork.app.ui.Motion
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.recipefork.app.R
import app.recipefork.app.library.HyphenFitText
import app.recipefork.app.ui.appColors
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.LayoutDirection
import app.recipefork.app.book.BookName
import app.recipefork.app.media.PhotoImage
import app.recipefork.app.media.PhotoStore
import app.recipefork.app.media.stepMedia
import app.recipefork.core.recipe.MediaKind
import androidx.compose.material3.MaterialTheme
import app.recipefork.core.recipe.Recipe
import java.io.File
import app.recipefork.app.timer.RecipeTimers
import app.recipefork.app.timer.RestartQuestion
import app.recipefork.app.timer.StepTimer
import app.recipefork.app.timer.TimerNotificationsCheck
import app.recipefork.app.timer.TimerStep
import app.recipefork.app.timer.rememberRecipeTimers
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.ReferenceQuantity
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.StepText
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.InteractionSource
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.snapshotFlow
import app.recipefork.app.timer.rememberCardTimers

/**
 * The step a card is to be shown at, once: the step of a timer whose notification the reader tapped (FLOW-013 (4)).
 * [shown] is told once the card has brought it into view.
 */
class StepToShow(val pending: StateFlow<TimerStep?>, val shown: () -> Unit)

/** The step to show, provided by the application's session; none where nothing leads to a step. */
val LocalStepToShow = staticCompositionLocalOf<StepToShow?> { null }

/**
 * The card's steps, in order and numbered as they read. A step that reads as nothing yet — no text and no reference
 * to show — is not a step a reader can follow and is left out; with none left, so is the whole part, heading included (FLOW-019). A step carrying a timer
 * shows it beneath its text, startable there and running there (FLOW-016). A held timer its step no longer carries
 * is dropped. Opened from a timer's notification, the card is brought to that timer's step (FLOW-013 (4)).
 *
 * Each ingredient reference in a step reads as [references] gives it: its line in the list at the scale in force
 * (FLOW-032), and a timer started from the step is named with it as it reads at that moment (FLOW-016 (8)).
 *
 * A tap on a step's line — its number and text — marks it done, and a tap again unmarks it ([marks], FLOW-068); its
 * media and its timer keep their own taps. Without [marks] a step is not marked.
 *
 * On Cooking the steps read as a stepper, each circle joined to the next by a line, and a step marked done folds to its
 * first line with a check in its circle ([StepRow]); a tap on it unmarks it and it unfolds.
 *
 * On Cooking the first step not marked done is the current one: drawn, with its timers, on the theme's primary
 * container and read as the current step. A step marked done brings the next one not marked after it into view, wholly
 * above the [coveredBelow] the navigation bar covers at the bottom of the viewport, scrolling smoothly — unless the
 * reader is scrolling themselves ([readerScrolling]), or until they take hold of the card's scroll ([readerInteractions]);
 * a step unmarked, or a step marked with none unmarked after it,
 * moves nothing.
 *
 * All of the above is the [CardMode.COOKING] presentation. In [CardMode.VIEW] the steps read as a page of a cookbook
 * ([StepBlock]): each number large in a margin of its own, the text beside it in body size, its references in the
 * working face, each timer a duration label ([ViewTimer]) with nothing that starts it; its done mark is shown but a tap
 * leaves it as it is, and no step is current. There a step's note, when the notes are the reader's [ownNotes] — the
 * recipe is the book's own, not a found one — is pencilled in the margin after the duration labels ([MarginNote]); a
 * found recipe's notes, its author's, keep the muted look under the text ([StepNote]), as on Cooking.
 */
@Composable
internal fun StepsList(
    steps: List<Step>,
    recipeId: RecipeId,
    title: String = "",
    references: CardReferences = CardReferences.NONE,
    media: StepMediaOnCard = StepMediaOnCard.NONE,
    marks: CardMarks? = null,
    mode: CardMode = CardMode.COOKING,
    readerScrolling: () -> Boolean = { false },
    coveredBelow: Dp = 0.dp,
    readerInteractions: InteractionSource? = null,
    ownNotes: Boolean = true,
) {
    // A step that is only a reference reads as one, one that is only its author's note reads as that, and one that is
    // only a photograph or a clip shows it — or says it is unavailable (FLOW-061); one holding nothing that reads or
    // shows is left out.
    val shown = steps.sortedBy { it.position }.filter { isListed(it, references::quantity, media.of(it.position) != null) }
    val timers = rememberRecipeTimers(recipeId, title)
    val held = timers.held
    LaunchedEffect(timers, steps, held) { timers.dropStale(steps, held) }
    // Whether the card has read the recipe's timers: until then a step marked done cannot tell whether a timer of its
    // own holds it open, so it neither animates its fold nor is brought into view.
    val card = rememberCardTimers()
    var settled by remember(timers) { mutableStateOf(false) }
    LaunchedEffect(timers) {
        combine(card.store.ofRecipe(card.book(), recipeId), snapshotFlow { timers.held }) { stored, read -> stored.keys == read.keys }
            .first { it }
        // A few frames on, once the steps stand as the timers read make them, not while they are changing to it.
        repeat(FOLD_SETTLED_FRAMES) { withFrameNanos { } }
        settled = true
    }
    val cooking = mode == CardMode.COOKING
    // Each step can be brought into view: by a timer's notification, or as the next to do once one is marked.
    val requesters = remember { HashMap<Int, BringIntoViewRequester>() }
    fun requester(position: Int) = requesters.getOrPut(position) { BringIntoViewRequester() }
    // A step is brought wholly above what covers the bottom of the viewport — the navigation bar, which the content runs
    // under — not only as far as the viewport's edge.
    val places = LocalCardPlaces.current
    val covered by rememberUpdatedState(with(LocalDensity.current) { coveredBelow.toPx() })
    suspend fun bring(position: Int) {
        val height = places?.placeOf(CardPlaces.step(position))?.height
        requester(position).bringIntoView(height?.let { Rect(Offset.Zero, Size(1f, it + covered)) })
    }

    /**
     * Brings the step at [position] into view, following it a frame at a time while the step at [changing] still
     * changes its height — folding or unfolding — and once more as it has settled: a step that folds above another
     * pulls it up, where its top is scrolled off out of view with it; a step that unfolds grows below where it was
     * brought.
     */
    suspend fun follow(position: Int, changing: Int) = coroutineScope {
        fun height() = places?.placeOf(CardPlaces.step(changing))?.height
        val following = launch {
            var height = height()
            var still = 0
            var frames = 0
            while (still < FOLD_SETTLED_FRAMES && frames++ < FOLD_MOST_FRAMES) {
                launch { bring(position) }
                withFrameNanos { }
                val now = height()
                still = if (now == height) still + 1 else 0
                height = now
            }
            bring(position)
        }
        // A scroll of the reader's own ends the following there, the bringing in flight with it: the card never turns
        // back to the step once the reader has taken hold of it.
        val reader = readerInteractions?.let { source ->
            launch {
                source.interactions.first { it is DragInteraction.Start }
                following.cancel()
            }
        }
        following.join()
        reader?.cancel()
    }
    // A timer's step is shown on Cooking, where the timer runs: the card opens on that tab for it.
    val toShow = LocalStepToShow.current?.takeIf { cooking }
    val pending = toShow?.pending?.collectAsState()?.value?.takeIf { it.recipeId == recipeId.value }
    val targetShown = pending != null && shown.any { it.position == pending.stepPosition }
    LaunchedEffect(pending) {
        if (pending == null) return@LaunchedEffect
        if (targetShown) {
            // Once its timers are read, so that a step marked done stands unfolded by the timer it was opened for, and
            // once laid out, the step comes into view.
            snapshotFlow { settled }.first { it }
            withFrameNanos { }
            follow(pending.stepPosition, changing = pending.stepPosition)
        }
        toShow.shown()
    }
    // The step marked done and the step to bring into view next, set as a step is marked done.
    var next by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val scrolling by rememberUpdatedState(readerScrolling)
    LaunchedEffect(next) {
        val asked = next ?: return@LaunchedEffect
        try {
            // Once the mark is drawn; the bringing animates, and a scroll of the reader's own stops it.
            withFrameNanos { }
            // The step marked folds over the frames that follow, pulling up what comes after it.
            if (!scrolling()) follow(asked.second, changing = asked.first)
        } finally {
            // Done with, however it ended — unless a later mark has asked again — so the same mark asks anew.
            if (next == asked) next = null
        }
    }
    if (shown.isEmpty()) return
    val current = if (cooking && marks != null) shown.firstOrNull { !marks.done(MarkedPart.STEP, it.position) }?.position else null
    // On View every step's number stands in one margin, as wide as the widest of them needs.
    val margin = if (cooking) 0.dp else rememberStepMargin(shown.size)
    Section(R.string.card_steps, CardTags.STEPS) {
        shown.forEachIndexed { index, step ->
            val modifier = cardPlace(CardPlaces.step(step.position)).bringIntoViewRequester(requester(step.position))
            if (!cooking) {
                StepBlock(
                    index + 1, step, references, timers, media, margin, modifier,
                    done = marks?.done(MarkedPart.STEP, step.position) == true,
                    stated = marks?.any == true,
                    first = index == 0,
                    last = index == shown.lastIndex,
                    ownNote = ownNotes,
                )
                return@forEachIndexed
            }
            StepRow(
                index + 1, step, references, timers, media, modifier,
                done = marks?.done(MarkedPart.STEP, step.position) == true,
                onToggle = marks?.takeIf { cooking }?.let { marked ->
                    {
                        val marking = !marked.done(MarkedPart.STEP, step.position)
                        marked.toggle(MarkedPart.STEP, step.position)
                        if (marking) {
                            next = shown.drop(index + 1).firstOrNull { !marked.done(MarkedPart.STEP, it.position) }
                                ?.let { step.position to it.position }
                        }
                    }
                },
                mode = mode,
                current = step.position == current,
                stated = marks?.any == true,
                last = index == shown.lastIndex,
                animate = settled,
            )
        }
    }
    RestartQuestion(timers)
    TimerNotificationsCheck(timers)
}

/** How many frames a step keeps its height before its fold or unfold is taken as settled. */
private const val FOLD_SETTLED_FRAMES = 3

/** The most frames a step is followed while another folds or unfolds, whatever happens meanwhile. */
private const val FOLD_MOST_FRAMES = 120

/**
 * Whether the card lists [step]: it reads as something — its text, each reference read by [quantity], or the author's
 * note — or it carries a photo or a clip ([hasMedia]). The list of steps and the summary under the title ([RecipeSummary])
 * both keep a step by this.
 */
internal fun isListed(step: Step, quantity: ReferenceQuantity, hasMedia: Boolean): Boolean =
    StepText.render(step, quantity).isNotBlank() || step.note != null || hasMedia

/**
 * One step: its number in a filled circle and its text beside it, with the author's note under the text, muted and
 * smaller ([StepNote]), read by the screen reader as one line that says the number once — the circle itself is
 * decorative — and the note after the text; then its photograph or its clip, named by the step's number, and its
 * timer, if it carries them, set under the text, the timer a clear gap below the rest. Steps after the first stand apart from the one before. An item whose
 * file the device does not hold, or cannot show, says so in its place (FLOW-061).
 *
 * The number and text are one target, the card's [CardTarget] tall at least, that marks the step [done] or unmarks it
 * ([onToggle]) — a check box to the screen reader; a step marked done has its text struck through and muted, and is
 * read as done.
 *
 * On Cooking the steps read as a stepper: each circle is joined to the next one's by a vertical line ([stepLine]),
 * none after the [last]; a step marked done shows a check in its circle in place of its number and folds to its first
 * line, ellipsized — its note, media and timers folded away — unless one of its timers is running or ringing, which
 * holds it open until that timer is cancelled or its ringing stopped. Folding and unfolding animate ([Motion]) — once
 * the card has read the recipe's timers ([animate]); before, a step stands as they make it at once. The screen reader
 * still reads a folded step's text whole; its note folds away with the rest.
 *
 * The [current] step — its number and text, its media and its timers — stands on the theme's primary container, which
 * reaches a little beyond it into the gutter and the space between steps, so that nothing moves as the current step
 * changes; its text is in the container's own colour, and the screen reader reads it as the current step.
 *
 * This is the [CardMode.COOKING] step; [CardMode.VIEW] shows a [StepBlock].
 */
@Composable
private fun StepRow(
    number: Int,
    step: Step,
    references: CardReferences,
    timers: RecipeTimers,
    media: StepMediaOnCard,
    modifier: Modifier = Modifier,
    done: Boolean = false,
    onToggle: (() -> Unit)? = null,
    mode: CardMode = CardMode.COOKING,
    current: Boolean = false,
    stated: Boolean = false,
    last: Boolean = true,
    animate: Boolean = true,
) {
    val cooking = mode == CardMode.COOKING
    val spacing = StepSpacing
    // Read from the timers as they tick, but changing only as a timer of the step starts, ends, rings or goes.
    val timing by remember(step, timers) { derivedStateOf { timers.holdOpen(step) } }
    val folded = cooking && done && !timing
    val circle = rememberStepCircle(number)
    // The current step's container takes in the whole step — its number and text, its media and its timers — so that
    // its timers read as its own.
    val highlight = if (current) Modifier.currentStep() else Modifier
    val line = if (cooking && !last) Modifier.stepLine(circle.side, below = spacing) else Modifier
    Column(
        modifier.fillMaxWidth().padding(top = if (number > 1) spacing else 0.dp).then(highlight).then(line)
            .testTag(StepRowTags.entry(step.position)),
    ) {
        // The current step is read as such; it is never done, so its state says that it is current.
        val currentState = stringResource(R.string.card_current_step).takeIf { current }
        val marking = when {
            onToggle != null && cooking -> Modifier.heightIn(min = CardTarget).markedDone(done, onToggle, state = currentState)
            currentState != null -> Modifier.semantics { stateDescription = currentState }
            else -> Modifier.shownDone(done, stated)
        }
        Row(
            Modifier.fillMaxWidth().then(marking).semantics(mergeDescendants = true) { text = AnnotatedString("$number.") }
                .testTag(CardTags.step(step.position)),
            horizontalArrangement = Arrangement.spacedBy(StepGap),
        ) {
            val color = if (current) MaterialTheme.colorScheme.onPrimaryContainer else LocalContentColor.current
            CompositionLocalProvider(LocalContentColor provides color) {
                StepNumber(circle, step.position, checked = cooking && done)
                Column(Modifier.weight(1f).then(if (cooking && animate) Modifier.animateContentSize(Motion.appearing()) else Modifier)) {
                    StepText(step, references, done = done, mode = mode, maxLines = if (folded) 1 else Int.MAX_VALUE)
                    if (!folded) step.note?.let { StepNote(it, step.position, mode) }
                }
            }
        }
        AnimatedVisibility(
            !folded,
            enter = if (animate) Motion.open else EnterTransition.None,
            exit = if (animate) Motion.close else ExitTransition.None,
        ) {
            Column {
                media.of(step.position)?.let { item ->
                    Box(Modifier.fillMaxWidth().padding(start = StepIndent, top = 8.dp)) {
                        StepMedia(item, number, step.position)
                    }
                }
                step.timers.indices.forEach { index ->
                    // Each timer on its own line: the first a clear gap below the rest, the others closer under it.
                    val gap = if (index == 0) TimerGap else TimersGap
                    StepTimer(step, index, timers, references::quantity, Modifier.padding(start = StepIndent, top = gap), onHighlight = current)
                }
            }
        }
    }
}

/**
 * Whether a timer of [step] is running, or has finished and rings: what holds a step marked done open on Cooking. One
 * that has ended but is not yet announced is about to ring, and holds it too, so that the step does not fold and
 * unfold again in between.
 */
private fun RecipeTimers.holdOpen(step: Step): Boolean = step.timers.indices.any { index ->
    val key = key(step, index)
    val timer = held[key] ?: return@any false
    !timer.hasEnded(now) || !timer.announced || rings(key)
}

/**
 * The stepper's line from a step's circle, [side] across, down to the next step's, which stands [below] it: in the
 * theme's on-surface-variant colour, a small gap clear of each circle, drawn over the current step's container too
 * (ContrastTest).
 */
@Composable
private fun Modifier.stepLine(side: Dp, below: Dp): Modifier {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    return drawBehind {
        val x = side.toPx() / 2f
        val gap = STEP_LINE_GAP.toPx()
        val top = side.toPx() + gap
        val bottom = size.height + below.toPx() - gap
        if (bottom > top) drawLine(color, Offset(x, top), Offset(x, bottom), strokeWidth = STEP_LINE_WIDTH.toPx())
    }
}

/** The width of the stepper's line between two steps' circles. */
internal val STEP_LINE_WIDTH = 2.dp

/** The gap between the stepper's line and the circles it joins. */
private val STEP_LINE_GAP = 4.dp

/**
 * Draws the current step's container behind it: the theme's primary container in its medium corners, reaching
 * [CURRENT_OUTSET_SIDE] beyond the step on each side and [CURRENT_OUTSET_END] above and below, outside its layout.
 */
@Composable
private fun Modifier.currentStep(): Modifier {
    val fill = MaterialTheme.colorScheme.primaryContainer
    val shape = MaterialTheme.shapes.medium
    return drawBehind {
        val side = CURRENT_OUTSET_SIDE.toPx()
        val end = CURRENT_OUTSET_END.toPx()
        val outline = shape.createOutline(Size(size.width + 2 * side, size.height + 2 * end), layoutDirection, this)
        translate(-side, -end) { drawOutline(outline, fill) }
    }
}

/** How far the current step's container reaches into the card's gutter on each side. */
internal val CURRENT_OUTSET_SIDE = 8.dp

/** How far the current step's container reaches above and below the step, into the space between steps. */
internal val CURRENT_OUTSET_END = 4.dp

/** Test tags of a step's parts on the card. */
internal object StepRowTags {
    fun number(position: Int) = "card-step-$position-number"
    fun entry(position: Int) = "card-step-$position-entry"
    fun note(position: Int) = "card-step-$position-note"

    /** The reader's margin note of the step at [position] on View, its rule, mark and words ([MarginNote]). */
    fun margin(position: Int) = "card-step-$position-margin-note"

    /** The pencil mark of that margin note. */
    fun mark(position: Int) = "card-step-$position-margin-mark"
}

/**
 * The author's note on a step, under its text: muted, in the theme's on-surface-variant colour — on the current step's
 * container too — and a size of the type scale smaller than the step's text ([stepNoteStyle]). Its own composable, shared
 * by [StepRow] and [StepBlock] — there for a found recipe's notes — so that the note's look is drawn in one place.
 */
@Composable
internal fun StepNote(note: String, position: Int, mode: CardMode) {
    Text(
        note,
        style = stepNoteStyle(mode),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(top = STEP_NOTE_GAP).testTag(StepRowTags.note(position)),
    )
}

/** How a step's note reads: the theme's body style, large on [CardMode.COOKING] and medium on [CardMode.VIEW]. */
@Composable
internal fun stepNoteStyle(mode: CardMode) = when (mode) {
    CardMode.VIEW -> MaterialTheme.typography.bodyMedium
    CardMode.COOKING -> MaterialTheme.typography.bodyLarge
}

/** The gap between a step's text and its note. */
internal val STEP_NOTE_GAP = 4.dp

/**
 * The reader's own note on a step on [CardMode.VIEW], pencilled in the margin (the redesign's pencil note): in
 * [marginNoteStyle], the reading face's real italic, after a pencil mark [MARGIN_NOTE_MARK] across, standing level with
 * its first line, and a dashed rule [MARGIN_NOTE_RULE] wide down its start edge — all in the theme's graphite
 * ([app.recipefork.app.ui.AppColors.graphite]), which nothing else is drawn in. It wraps between words, and after the
 * hyphen of a hyphenated word; only a word wider than its room is hyphenated across lines ([HyphenFitText]). The mark
 * is decorative: the screen reader reads the note's words.
 */
@Composable
internal fun MarginNote(note: String, position: Int, modifier: Modifier = Modifier) {
    val graphite = MaterialTheme.appColors.graphite
    val style = marginNoteStyle()
    // The mark stands level with the note's first line, as the line grows with the font scale.
    val markTop = with(LocalDensity.current) { ((style.lineHeight.toDp() - MARGIN_NOTE_MARK) / 2).coerceAtLeast(0.dp) }
    Row(
        modifier.fillMaxWidth().testTag(StepRowTags.margin(position)).dashedRule(graphite)
            .padding(start = MARGIN_NOTE_INSET, top = MARGIN_NOTE_PADDING, bottom = MARGIN_NOTE_PADDING),
        horizontalArrangement = Arrangement.spacedBy(MARGIN_NOTE_MARK_GAP),
    ) {
        Icon(
            painterResource(R.drawable.ic_margin_note),
            contentDescription = null,
            tint = graphite,
            modifier = Modifier.padding(top = markTop).size(MARGIN_NOTE_MARK).testTag(StepRowTags.mark(position)),
        )
        HyphenFitText(note, style, Modifier.weight(1f).testTag(StepRowTags.note(position)), color = graphite)
    }
}

/** How the reader's margin note reads: the reading face's real italic at 14.5 sp, its line 1.4 times its size. */
@Composable
internal fun marginNoteStyle(): TextStyle = MaterialTheme.typography.bodyMedium.copy(
    fontSize = MARGIN_NOTE_SIZE,
    lineHeight = MARGIN_NOTE_LINE,
    fontStyle = FontStyle.Italic,
)

/** The dashed rule down the start edge of a margin note, in [color]. */
private fun Modifier.dashedRule(color: Color): Modifier = drawBehind {
    val width = MARGIN_NOTE_RULE.toPx()
    val x = if (layoutDirection == LayoutDirection.Rtl) size.width - width / 2f else width / 2f
    drawLine(
        color, Offset(x, 0f), Offset(x, size.height), strokeWidth = width,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(MARGIN_NOTE_DASH.toPx(), MARGIN_NOTE_DASH_GAP.toPx())),
    )
}

/** The size of a margin note's words, and their line height. */
internal val MARGIN_NOTE_SIZE = 14.5.sp
internal val MARGIN_NOTE_LINE = 20.3.sp

/** The width of the dashed rule down a margin note, and the length of a dash of it and of the gap after it. */
internal val MARGIN_NOTE_RULE = 1.5.dp
internal val MARGIN_NOTE_DASH = 4.5.dp
internal val MARGIN_NOTE_DASH_GAP = 3.dp

/** The size of the pencil mark before a margin note's words, and the gap between them. */
internal val MARGIN_NOTE_MARK = 16.dp
internal val MARGIN_NOTE_MARK_GAP = 7.dp

/** How far a margin note's mark stands from its rule, and the room above and below its words, inside the rule. */
internal val MARGIN_NOTE_INSET = 10.dp
internal val MARGIN_NOTE_PADDING = 2.dp

/** The space between one step and the next, beyond the section's own. */
internal val StepSpacing = 16.dp

/** The gap between a step's number and its text. */
private val StepGap = 12.dp

/** The least size of a step's circle; it grows with the font scale and stays a circle. */
internal val StepNumberSize = 32.dp

/** The gap above a step's timer, setting it clearly apart from the step's text and its media. */
internal val TimerGap = 12.dp

/** The gap between one timer of a step and the next. */
internal val TimersGap = 8.dp

/** Where a step's photograph, clip and timer start: under its text. */
internal val StepIndent = StepNumberSize + StepGap

/** A step's number as its circle draws it, and the circle's side. */
private class StepCircle(val number: TextLayoutResult, val side: Dp)

/** The circle of step [number]: as large as the number needs and never less than [StepNumberSize]. */
@Composable
private fun rememberStepCircle(number: Int): StepCircle {
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onPrimary)
    val density = LocalDensity.current
    return remember(number, style, density) {
        val shown = measurer.measure("$number", style, softWrap = false, maxLines = 1)
        StepCircle(shown, with(density) { maxOf(StepNumberSize, (maxOf(shown.size.width, shown.size.height)).toDp() + 8.dp) })
    }
}

/** Whether a step's circle shows a check in place of its number: what a test reads of a circle that says nothing. */
internal val ShowsCheck = SemanticsPropertyKey<Boolean>(SHOWS_CHECK_KEY)
private const val SHOWS_CHECK_KEY = "card.shows-check"
internal var SemanticsPropertyReceiver.showsCheck by ShowsCheck

/**
 * A step's number in a filled [circle] in the theme's primary colour — or, [checked], a check in its place, as a step
 * marked done shows on Cooking. Drawn, not set as text: it is decorative, the step's line says the number and its state.
 */
@Composable
private fun StepNumber(circle: StepCircle, position: Int, checked: Boolean) {
    val fill = MaterialTheme.colorScheme.primary
    val mark = MaterialTheme.colorScheme.onPrimary
    val shown = circle.number
    Box(
        Modifier
            .size(circle.side)
            .clearAndSetSemantics { testTag = StepRowTags.number(position); showsCheck = checked }
            .drawBehind {
                drawCircle(fill)
                if (checked) {
                    val s = size.minDimension
                    val tick = Path().apply {
                        moveTo(s * 0.28f, s * 0.52f)
                        lineTo(s * 0.44f, s * 0.67f)
                        lineTo(s * 0.73f, s * 0.36f)
                    }
                    drawPath(tick, mark, style = Stroke(width = s * 0.09f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                } else {
                    drawText(shown, topLeft = Offset((size.width - shown.size.width) / 2f, (size.height - shown.size.height) / 2f))
                }
            },
    )
}

@Composable
internal fun StepMedia(item: StepMediaItem, number: Int, position: Int) {
    val photoUnavailable = @Composable { MediaUnavailable(stringResource(R.string.card_step_photo_unavailable, number), CardTags.stepMediaUnavailable(position)) }
    val clipUnavailable = @Composable { MediaUnavailable(stringResource(R.string.card_step_video_unavailable, number), CardTags.stepMediaUnavailable(position)) }
    when (item) {
        is StepMediaItem.Photo -> PhotoImage(
            item.file,
            stringResource(R.string.media_step_description, number),
            Modifier.testTag(CardTags.stepPhoto(position)),
            unavailable = photoUnavailable,
        )
        is StepMediaItem.Clip -> StepVideo(
            item.file,
            description = stringResource(R.string.card_step_video_description, number),
            playAction = stringResource(R.string.card_video_play),
            pauseAction = stringResource(R.string.card_video_pause),
            unavailable = clipUnavailable,
            videoTag = CardTags.stepVideo(position),
            controlTag = CardTags.stepVideoControl(position),
        )
        is StepMediaItem.Missing -> if (item.kind == MediaKind.VIDEO) clipUnavailable() else photoUnavailable()
    }
}

/** The line that stands where media the device does not hold, or cannot show, would be (FLOW-061). */
@Composable
internal fun MediaUnavailable(text: String, tag: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.fillMaxWidth().testTag(tag),
    )
}

/** What a step shows of the item it carries. */
internal sealed interface StepMediaItem {
    /** A photograph whose file the device holds. */
    data class Photo(val file: File) : StepMediaItem

    /** A clip whose file the device holds. */
    data class Clip(val file: File) : StepMediaItem

    /** An item of [kind] whose file the device does not hold (FLOW-061). */
    data class Missing(val kind: MediaKind) : StepMediaItem
}

/** The item each of a recipe's steps carries, by the step's position; null for a step that carries none. */
internal fun interface StepMediaOnCard {
    fun of(position: Int): StepMediaItem?

    companion object {
        val NONE = StepMediaOnCard { null }
    }
}

@Composable
internal fun StepMediaOnCard(recipe: Recipe, book: BookName): StepMediaOnCard {
    val context = LocalContext.current
    val store = remember(context) { PhotoStore(context.applicationContext.filesDir) }
    return remember(store, recipe, book) {
        // Looked up once for the recipe as it stands, not at every composition.
        val held = recipe.steps.mapNotNull { step ->
            recipe.stepMedia(step.position)?.let { media ->
                val file = store.file(book, media.mediaId)
                step.position to when {
                    !file.isFile -> StepMediaItem.Missing(media.kind)
                    media.kind == MediaKind.VIDEO -> StepMediaItem.Clip(file)
                    else -> StepMediaItem.Photo(file)
                }
            }
        }.toMap()
        StepMediaOnCard { position -> held[position] }
    }
}
