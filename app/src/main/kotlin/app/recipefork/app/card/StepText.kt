package app.recipefork.app.card

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import app.recipefork.app.R
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.ReferenceText
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.StepRun
import app.recipefork.core.recipe.StepText
import app.recipefork.core.scaling.Rounding

/**
 * What the card's ingredient references read as at the scale in force: each one exactly its line in the ingredient
 * list — the same quantity, the same rounding — so a step and the list never disagree (SCALE-007, DOM-007). A row the
 * list shows without a quantity reads as the list writes it, its unit alone; one with nothing to show reads as the
 * empty placeholder. A reference to no row of the recipe reads as nothing.
 */
internal class CardReferences(private val lines: Map<Int, IngredientLine>) {
    /** What the reference to the row at [position] reads as, with no mark: what a timer is named with too. */
    fun quantity(position: Int): String? = lines[position]?.let { it.quantity ?: ReferenceText.PLACEHOLDER }

    /** Which way rounding moved the reference's quantity, as its list line shows it; none for one not recalculated. */
    fun rounding(position: Int): Rounding = lines[position]?.takeIf { it.derived }?.rounding ?: Rounding.NONE

    companion object {
        /** No references read: a card whose list is not known. */
        val NONE = CardReferences(emptyMap())

        /** The references to [ordered] as their lines [list] show them, the two in the same order. */
        fun of(ordered: List<Ingredient>, list: IngredientList): CardReferences =
            CardReferences(ordered.zip(list.lines).associate { (ingredient, line) -> ingredient.position to line })
    }
}

/** Test tags of the parts of a step's text. */
internal object StepTags {
    fun text(position: Int) = "card-step-$position-text"
}

/**
 * A step's text on the card, each reference read as [references] gives it, followed by the list's arrow where rounding
 * moved it (FLOW-032 (3), SCALE-004). The screen reader reads it as one sentence, an arrow as the words it stands for.
 */
@Composable
internal fun StepText(step: Step, references: CardReferences, modifier: Modifier = Modifier) {
    val runs = StepText.runs(step, references::quantity)
    // Each rounded reference: its arrow, and how the screen reader reads the reference with it.
    val marks = runs.filterIsInstance<StepRun.Reference>().associate { run ->
        run.ingredientPosition to roundingMark(references.rounding(run.ingredientPosition))?.let { (arrow, words) ->
            arrow to stringResource(R.string.card_step_reference_rounded, run.text, stringResource(words))
        }
    }
    val arrowStyle = SpanStyle(
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = MaterialTheme.typography.labelMedium.fontSize,
    )
    val shown = buildAnnotatedString {
        runs.forEach { run ->
            append(run.text)
            val arrow = (run as? StepRun.Reference)?.let { marks[it.ingredientPosition]?.first }
            if (arrow != null) withStyle(arrowStyle) { append(ARROW_GAP + arrow) }
        }
    }
    val spoken = runs.joinToString("") { run ->
        (run as? StepRun.Reference)?.let { marks[it.ingredientPosition]?.second } ?: run.text
    }
    val tag = StepTags.text(step.position)
    Text(
        shown,
        style = MaterialTheme.typography.bodyLarge,
        modifier = modifier.clearAndSetSemantics {
            text = AnnotatedString(spoken)
            shownText = shown.text
            testTag = tag
        },
    )
}

/** A narrow space that never breaks, so the arrow stays on its quantity's line. */
private const val ARROW_GAP = "\u202F"
