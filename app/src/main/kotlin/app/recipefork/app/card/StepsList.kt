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
 * All of the above is the [CardMode.COOKING] presentation. In [CardMode.VIEW] a step is as tall as its text, the steps a
 * modest [VIEW_STEP_SPACING] apart; its done mark is shown but a tap leaves it as it is; and each timer reads as its name
 * and duration, or its remaining time while it runs, with nothing that starts it ([ViewTimer]).
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
) {
    // A step that is only a reference reads as one, and one that is only a photograph or a clip shows it — or says it
    // is unavailable (FLOW-061); one holding nothing that reads or shows is left out.
    val shown = steps.sortedBy { it.position }
        .filter { StepText.render(it, references::quantity).isNotBlank() || media.of(it.position) != null }
    val timers = rememberRecipeTimers(recipeId, title)
    val held = timers.held
    LaunchedEffect(timers, steps, held) { timers.dropStale(steps, held) }
    // A timer's step is shown on Cooking, where the timer runs: the card opens on that tab for it.
    val toShow = LocalStepToShow.current?.takeIf { mode == CardMode.COOKING }
    val pending = toShow?.pending?.collectAsState()?.value?.takeIf { it.recipeId == recipeId.value }
    val target = remember { BringIntoViewRequester() }
    val targetShown = pending != null && shown.any { it.position == pending.stepPosition }
    LaunchedEffect(pending) {
        if (pending == null) return@LaunchedEffect
        if (targetShown) {
            // Once laid out, the step comes into view.
            withFrameNanos { }
            target.bringIntoView()
        }
        toShow.shown()
    }
    if (shown.isEmpty()) return
    Section(R.string.card_steps, CardTags.STEPS) {
        shown.forEachIndexed { index, step ->
            val place = cardPlace(CardPlaces.step(step.position))
            val modifier = if (targetShown && step.position == pending?.stepPosition) {
                place.bringIntoViewRequester(target)
            } else {
                place
            }
            StepRow(
                index + 1, step, references, timers, media, modifier,
                done = marks?.done(MarkedPart.STEP, step.position) == true,
                onToggle = marks?.takeIf { mode == CardMode.COOKING }?.let { { it.toggle(MarkedPart.STEP, step.position) } },
                mode = mode,
            )
        }
    }
    RestartQuestion(timers)
    TimerNotificationsCheck(timers)
}

/**
 * One step: its number in a filled circle and its text beside it, read by the screen reader as one line that says the
 * number once — the circle itself is decorative — then its photograph or its clip, named by the step's number, and its
 * timer, if it carries them, set under the text, the timer a clear gap below the rest. Steps after the first stand apart from the one before. An item whose
 * file the device does not hold, or cannot show, says so in its place (FLOW-061).
 *
 * The number and text are one target, the card's [CardTarget] tall at least, that marks the step [done] or unmarks it
 * ([onToggle]); a step marked done has its text struck through and muted, and is read as done.
 *
 * In [CardMode.VIEW] the number and text answer no tap and are as tall as they are; a mark is shown and read as done; a
 * timer is a line of text ([ViewTimer]), closer under the text than in cooking.
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
) {
    val cooking = mode == CardMode.COOKING
    val spacing = if (cooking) StepSpacing else VIEW_STEP_SPACING
    Column(modifier.fillMaxWidth().padding(top = if (number > 1) spacing else 0.dp)) {
        val marking = if (onToggle != null && cooking) Modifier.heightIn(min = CardTarget).markedDone(done, onToggle) else Modifier.shownDone(done)
        Row(
            Modifier.fillMaxWidth().then(marking).semantics(mergeDescendants = true) { text = AnnotatedString("$number.") }
                .testTag(CardTags.step(step.position)),
            horizontalArrangement = Arrangement.spacedBy(StepGap),
        ) {
            StepNumber(number, step.position)
            StepText(step, references, Modifier.weight(1f), done)
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
                StepTimer(step, index, timers, references::quantity, Modifier.padding(start = StepIndent, top = gap))
            } else {
                val gap = if (index == 0) VIEW_TIMER_GAP else VIEW_TIMERS_GAP
                ViewTimer(step, index, timers, references::quantity, Modifier.padding(start = StepIndent, top = gap))
            }
        }
    }
}

/** Test tags of a step's parts on the card. */
internal object StepRowTags {
    fun number(position: Int) = "card-step-$position-number"
}

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
