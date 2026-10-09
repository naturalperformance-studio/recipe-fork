package app.recipefork.app.card

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.recipefork.app.R
import app.recipefork.app.media.stepMedia
import app.recipefork.app.timer.durationText
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.ReferenceText
import app.recipefork.core.recipe.StepText
import app.recipefork.core.scaling.InterfaceLanguage
import kotlin.time.Duration.Companion.minutes

/**
 * What the line under the card's title says of a recipe: how many ingredient rows it has, how many steps the card
 * shows, and its timers' durations added up and rounded ([minutes]), null when it has no timer.
 */
internal data class RecipeSummary(val ingredients: Int, val steps: Int, val minutes: Int?) {
    companion object {
        /**
         * The summary of [recipe] as its card shows it: every ingredient row, and the steps the card lists — those
         * that read as something or carry a photo or clip, as [StepsList] keeps them — with the timers of those steps.
         */
        fun of(recipe: Recipe, language: InterfaceLanguage): RecipeSummary {
            val quantity = ReferenceText.authored(recipe.ingredients, language)
            val steps = recipe.steps.filter { StepText.render(it, quantity).isNotBlank() || recipe.stepMedia(it.position) != null }
            val timers = steps.flatMap { it.timers }
            val total = timers.sumOf { it.duration.inWholeMinutes }
            return RecipeSummary(recipe.ingredients.size, steps.size, if (timers.isEmpty()) null else rounded(total))
        }

        /**
         * [minutes] as the summary gives them: under an hour to the nearest 5 minutes, never below 5; from an hour to
         * the nearest 10 minutes; a half rounds up.
         */
        fun rounded(minutes: Long): Int = if (minutes < 60) {
            ((minutes + 2) / 5 * 5).coerceAtLeast(5).toInt()
        } else {
            ((minutes + 5) / 10 * 10).toInt()
        }
    }
}

/**
 * The summary line under the card's title, muted: "9 ingredients · 4 steps · timers ≈ 1 h 10 min", each part left
 * out where the recipe has none of it, and the whole line where it has nothing (FLOW-019). The screen reader reads it
 * as one sentence, its parts parted by commas and "≈" said as a word.
 */
@Composable
internal fun RecipeSummaryLine(summary: RecipeSummary, modifier: Modifier = Modifier) {
    val ingredients = summary.ingredients.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.card_summary_ingredients, it, it) }
    val steps = summary.steps.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.card_summary_steps, it, it) }
    // Written as the app writes a timer's duration: hours and minutes, either left out where it is none.
    val duration = summary.minutes?.let { durationText(it.minutes) }
    val counts = listOfNotNull(ingredients, steps)
    val shown = joined(counts + listOfNotNull(duration?.let { stringResource(R.string.card_summary_timers, it) }), spoken = false)
        ?: return
    val spoken = joined(counts + listOfNotNull(duration?.let { stringResource(R.string.card_summary_timers_spoken, it) }), spoken = true)
    Text(
        shown,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.semantics { spoken?.let { contentDescription = it } }.testTag(CardTags.SUMMARY),
    )
}

/** [parts] as one line — parted by "·" as shown, by commas as [spoken] — or null for none. */
@Composable
private fun joined(parts: List<String>, spoken: Boolean): String? = when (parts.size) {
    0 -> null
    1 -> parts[0]
    2 -> stringResource(if (spoken) R.string.card_summary_spoken_two else R.string.card_summary_two, parts[0], parts[1])
    else -> stringResource(if (spoken) R.string.card_summary_spoken_three else R.string.card_summary_three, parts[0], parts[1], parts[2])
}
