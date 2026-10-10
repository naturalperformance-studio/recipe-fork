package app.recipefork.app.card

import androidx.compose.foundation.layout.Column
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
 * On Cooking the first step not marked done is the current one: drawn, with its timers, on the theme's primary
 * container and read as the current step. A step marked done brings the next one not marked after it into view, wholly
 * above the [coveredBelow] the navigation bar covers at the bottom of the viewport, scrolling smoothly — unless the
 * reader is scrolling themselves ([readerScrolling]); a step unmarked, or a step marked with none unmarked after it,
 * moves nothing.
 *
 * All of the above is the [CardMode.COOKING] presentation. In [CardMode.VIEW] a step is as tall as its text, the steps a
 * modest [VIEW_STEP_SPACING] apart, its text a size smaller ([stepTextStyle]); its done mark is shown but a tap leaves it
 * as it is; no step is current; and each timer reads as its name and duration, or its remaining time while it runs, with
 * nothing that starts it ([ViewTimer]).
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
) {
    // A step that is only a reference reads as one, one that is only its author's note reads as that, and one that is
    // only a photograph or a clip shows it — or says it is unavailable (FLOW-061); one holding nothing that reads or
    // shows is left out.
    val shown = steps.sortedBy { it.position }.filter { isListed(it, references::quantity, media.of(it.position) != null) }
    val timers = rememberRecipeTimers(recipeId, title)
    val held = timers.held
    LaunchedEffect(timers, steps, held) { timers.dropStale(steps, held) }
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
    // A timer's step is shown on Cooking, where the timer runs: the card opens on that tab for it.
    val toShow = LocalStepToShow.current?.takeIf { cooking }
    val pending = toShow?.pending?.collectAsState()?.value?.takeIf { it.recipeId == recipeId.value }
    val targetShown = pending != null && shown.any { it.position == pending.stepPosition }
    LaunchedEffect(pending) {
        if (pending == null) return@LaunchedEffect
        if (targetShown) {
            // Once laid out, the step comes into view.
            withFrameNanos { }
            bring(pending.stepPosition)
        }
        toShow.shown()
    }
    // The step to bring into view next, set as a step is marked done.
    var next by remember { mutableStateOf<Int?>(null) }
    val scrolling by rememberUpdatedState(readerScrolling)
    LaunchedEffect(next) {
        val position = next ?: return@LaunchedEffect
        // Once the mark is drawn; the bringing animates, and a scroll of the reader's own stops it.
        withFrameNanos { }
        if (!scrolling()) bring(position)
        next = null
    }
    if (shown.isEmpty()) return
    val current = if (cooking && marks != null) shown.firstOrNull { !marks.done(MarkedPart.STEP, it.position) }?.position else null
    Section(R.string.card_steps, CardTags.STEPS) {
        shown.forEachIndexed { index, step ->
            val modifier = cardPlace(CardPlaces.step(step.position)).bringIntoViewRequester(requester(step.position))
            StepRow(
                index + 1, step, references, timers, media, modifier,
                done = marks?.done(MarkedPart.STEP, step.position) == true,
                onToggle = marks?.takeIf { cooking }?.let { marked ->
                    {
                        val marking = !marked.done(MarkedPart.STEP, step.position)
                        marked.toggle(MarkedPart.STEP, step.position)
                        if (marking) {
                            next = shown.drop(index + 1).firstOrNull { !marked.done(MarkedPart.STEP, it.position) }?.position
                        }
                    }
                },
                mode = mode,
                current = step.position == current,
                stated = marks?.any == true,
            )
        }
    }
    RestartQuestion(timers)
    TimerNotificationsCheck(timers)
}

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
 * The [current] step — its number and text, its media and its timers — stands on the theme's primary container, which
 * reaches a little beyond it into the gutter and the space between steps, so that nothing moves as the current step
 * changes; its text is in the container's own colour, and the screen reader reads it as the current step.
 *
 * In [CardMode.VIEW] the number and text answer no tap and are as tall as they are; a mark is shown, read-only, its
 * state read while the recipe has any mark ([stated]); a timer is a line of text ([ViewTimer]), closer under the text
 * than in cooking.
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
) {
    val cooking = mode == CardMode.COOKING
    val spacing = if (cooking) StepSpacing else VIEW_STEP_SPACING
    // The current step's container takes in the whole step — its number and text, its media and its timers — so that
    // its timers read as its own.
    val highlight = if (current) Modifier.currentStep() else Modifier
    Column(modifier.fillMaxWidth().padding(top = if (number > 1) spacing else 0.dp).then(highlight)) {
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
                StepNumber(number, step.position)
                Column(Modifier.weight(1f)) {
                    StepText(step, references, done = done, mode = mode)
                    step.note?.let { StepNote(it, step.position, mode) }
                }
            }
        }
        media.of(step.position)?.let { item ->
            Box(Modifier.fillMaxWidth().padding(start = StepIndent, top = 8.dp)) {
                StepMedia(item, number, step.position)
            }
        }
        step.timers.indices.forEach { index ->
            // Each timer on its own line: the first a clear gap below the rest, the others closer under it.
            if (cooking) {
                val gap = if (index == 0) TimerGap else TimersGap
                StepTimer(step, index, timers, references::quantity, Modifier.padding(start = StepIndent, top = gap), onHighlight = current)
            } else {
                val gap = if (index == 0) VIEW_TIMER_GAP else VIEW_TIMERS_GAP
                ViewTimer(step, index, timers, references::quantity, Modifier.padding(start = StepIndent, top = gap))
            }
        }
    }
}

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
    fun note(position: Int) = "card-step-$position-note"
}

/**
 * The author's note on a step, under its text: muted, in the theme's on-surface-variant colour — on the current step's
 * container too — and a size of the type scale smaller than the step's text ([stepNoteStyle]).
 */
@Composable
private fun StepNote(note: String, position: Int, mode: CardMode) {
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

/** The space between one step and the next, beyond the section's own. */
internal val StepSpacing = 16.dp

/** The space between one step and the next in [CardMode.VIEW], beyond the section's own: 12 dp in all, half of cooking's. */
internal val VIEW_STEP_SPACING = 4.dp

/** The gap above a step's first timer line in [CardMode.VIEW]. */
internal val VIEW_TIMER_GAP = 4.dp

/** The gap between one timer line of a step and the next in [CardMode.VIEW]. */
internal val VIEW_TIMERS_GAP = 2.dp

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

/**
 * A step's number in a filled circle in the theme's primary colour, as large as the number needs and never less than
 * [StepNumberSize]. Drawn, not set as text: it is decorative, the step's line says the number.
 */
@Composable
private fun StepNumber(number: Int, position: Int) {
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onPrimary)
    val fill = MaterialTheme.colorScheme.primary
    val density = LocalDensity.current
    val shown = remember(number, style, density) { measurer.measure("$number", style, softWrap = false, maxLines = 1) }
    val side = with(density) { maxOf(StepNumberSize, (maxOf(shown.size.width, shown.size.height)).toDp() + 8.dp) }
    Box(
        Modifier
            .size(side)
            .clearAndSetSemantics { testTag = StepRowTags.number(position) }
            .drawBehind {
                drawCircle(fill)
                drawText(shown, topLeft = Offset((size.width - shown.size.width) / 2f, (size.height - shown.size.height) / 2f))
            },
    )
}

@Composable
private fun StepMedia(item: StepMediaItem, number: Int, position: Int) {
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
