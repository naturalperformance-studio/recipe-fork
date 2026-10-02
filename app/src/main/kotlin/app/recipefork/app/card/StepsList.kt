package app.recipefork.app.card

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.recipefork.app.R
import app.recipefork.app.book.BookName
import app.recipefork.app.media.PhotoImage
import app.recipefork.app.media.PhotoStore
import app.recipefork.app.media.stepPhoto
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
 */
@Composable
internal fun StepsList(
    steps: List<Step>,
    recipeId: RecipeId,
    title: String = "",
    references: CardReferences = CardReferences.NONE,
    photos: StepPhotosOnCard = StepPhotosOnCard.NONE,
) {
    // A step that is only a reference reads as one, and one that is only a photograph shows it; one holding nothing
    // that reads or shows is left out.
    val shown = steps.sortedBy { it.position }
        .filter { StepText.render(it, references::quantity).isNotBlank() || photos.of(it.position) != null }
    val timers = rememberRecipeTimers(recipeId, title)
    val held = timers.held
    LaunchedEffect(timers, steps, held) { timers.dropStale(steps, held) }
    val toShow = LocalStepToShow.current
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
            StepRow(index + 1, step, references, timers, photos, modifier)
        }
    }
    RestartQuestion(timers)
    TimerNotificationsCheck(timers)
}

/**
 * One step: its number and its text, read by the screen reader as one line, then its photograph, named by the step's
 * number, and its timer, if it carries them.
 */
@Composable
private fun StepRow(number: Int, step: Step, references: CardReferences, timers: RecipeTimers, photos: StepPhotosOnCard, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.testTag(CardTags.step(step.position))) {
            Text("$number.", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.widthIn(min = 32.dp))
            StepText(step, references, Modifier.weight(1f))
        }
        photos.of(step.position)?.let { file ->
            Box(Modifier.fillMaxWidth().padding(start = 32.dp, top = 8.dp)) {
                PhotoImage(file, stringResource(R.string.media_step_description, number), Modifier.testTag(CardTags.stepPhoto(step.position)))
            }
        }
        if (step.timer != null) StepTimer(step, timers, references::quantity, Modifier.padding(start = 32.dp))
    }
}

/**
 * The files of the photographs a recipe's steps carry, by the step's position: only those the device holds, so that a
 * step holding nothing but a photograph whose file is gone is left out rather than shown as a bare number.
 */
internal fun interface StepPhotosOnCard {
    fun of(position: Int): File?

    companion object {
        val NONE = StepPhotosOnCard { null }
    }
}

@Composable
internal fun StepPhotosOnCard(recipe: Recipe, book: BookName): StepPhotosOnCard {
    val context = LocalContext.current
    val store = remember(context) { PhotoStore(context.applicationContext.filesDir) }
    return remember(store, recipe, book) {
        // Looked up once for the recipe as it stands, not at every composition.
        val held = recipe.steps.mapNotNull { step ->
            recipe.stepPhoto(step.position)?.let { store.file(book, it.mediaId) }?.takeIf { it.isFile }?.let { step.position to it }
        }.toMap()
        StepPhotosOnCard { position -> held[position] }
    }
}
