package app.recipefork.app.card

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.getTextLayoutResult
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import app.recipefork.app.R
import app.recipefork.app.dictionary.IngredientEntryEntity
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.IngredientReference
import app.recipefork.core.recipe.IngredientShares
import app.recipefork.core.recipe.ReferenceText
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.StepRun
import app.recipefork.core.recipe.StepText
import app.recipefork.core.scaling.InterfaceLanguage
import app.recipefork.core.scaling.QuantityText
import app.recipefork.core.scaling.Rounding

/**
 * What the card's ingredient references read as at the scale in force: a whole reference exactly its line in the
 * ingredient list — the same quantity, the same rounding — so a step and the list never disagree (SCALE-007, DOM-007).
 * A part of an ingredient is scaled by the same exact factor as the list and rounded on its own grid, the grid of its
 * ingredient, never taken from the list's line. A row with no quantity yet reads as the editor writes it, the visible
 * empty placeholder then its unit (FLOW-032); a row the list shows unscaled for any other reason reads as the list
 * writes it, and a part of it as the editor writes the part. A reference to no row of the recipe reads as nothing.
 */
internal class CardReferences(
    private val quantities: Map<Int, String>,
    private val roundings: Map<Int, Rounding> = emptyMap(),
    private val parts: (IngredientReference) -> Pair<String, Rounding>? = { null },
) {
    /** What [reference] reads as, with no mark: what a timer is named with too. */
    fun quantity(reference: IngredientReference): String? =
        if (reference.share != null && reference.ingredientPosition in quantities) {
            parts(reference)?.first ?: quantities[reference.ingredientPosition]
        } else {
            quantities[reference.ingredientPosition]
        }

    /** Which way rounding moved [reference]'s quantity, as the list shows a line; none for one not recalculated. */
    fun rounding(reference: IngredientReference): Rounding =
        (if (reference.share != null) parts(reference)?.second else null)
            ?: roundings[reference.ingredientPosition] ?: Rounding.NONE

    companion object {
        /** No references read: a card whose list is not known. */
        val NONE = CardReferences(emptyMap())

        /**
         * The references to [ordered] as their lines [list] show them in [language], the two in the same order; a
         * `piece` part is rounded by its row's effective divisibility, read from [entries] as the list reads it.
         */
        fun of(
            ordered: List<Ingredient>,
            list: IngredientList,
            language: InterfaceLanguage,
            entries: Map<Long, IngredientEntryEntity>,
        ): CardReferences {
            val pairs = ordered.zip(list.lines)
            val scalable = ordered.associate { it.position to scalableQuantity(it, entries) }
            val quantities = pairs.associate { (ingredient, line) ->
                val unfinished = ingredient.quantity == null && scalable[ingredient.position] == null
                ingredient.position to if (unfinished || line.quantity == null) {
                    ReferenceText.authored(ingredient, language)
                } else {
                    line.quantity
                }
            }
            val roundings = pairs.associate { (ingredient, line) ->
                ingredient.position to if (line.derived) line.rounding else Rounding.NONE
            }
            val rows = pairs.associate { (ingredient, line) -> ingredient.position to (ingredient to line) }
            val parts = { reference: IngredientReference ->
                val share = reference.share
                val (ingredient, line) = rows[reference.ingredientPosition] ?: (null to null)
                when {
                    share == null || ingredient == null || line == null -> null
                    !IngredientShares.fits(share, ingredient.unit) -> null
                    // A row the list cannot scale shows its part as the editor writes it.
                    scalable[reference.ingredientPosition]?.quantity == null || line.quantity == null ->
                        ReferenceText.authored(ingredient, share, language) to Rounding.NONE
                    else -> IngredientShares.display(
                        share, ingredient, list.factor, scalable.getValue(reference.ingredientPosition)?.divisible,
                    )?.let { shown ->
                        runCatching { QuantityText.of(shown, language) }.getOrNull()
                            ?.let { it to if (shown.derived) shown.rounding else Rounding.NONE }
                    } ?: (ReferenceText.authored(ingredient, share, language) to Rounding.NONE)
                }
            }
            return CardReferences(quantities, roundings, parts)
        }
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
    // Each rounded reference, by its run: its arrow, and how the screen reader reads the reference with it.
    val marks = runs.withIndex().filter { it.value is StepRun.Reference }.associate { (index, run) ->
        val reference = run as StepRun.Reference
        val rounding = references.rounding(IngredientReference(reference.ingredientPosition, 0, reference.share))
        index to roundingMark(rounding)?.let { (arrow, words) ->
            arrow to stringResource(R.string.card_step_reference_rounded, run.text, stringResource(words))
        }
    }
    val arrowStyle = SpanStyle(
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = MaterialTheme.typography.labelMedium.fontSize,
    )
    val shown = buildAnnotatedString {
        runs.forEachIndexed { index, run ->
            append(run.text)
            val arrow = marks[index]?.first
            if (arrow != null) withStyle(arrowStyle) { append(ARROW_GAP + arrow) }
        }
    }
    val spoken = runs.withIndex().joinToString("") { (index, run) -> marks[index]?.second ?: run.text }
    val tag = StepTags.text(step.position)
    // The text as laid out stays reachable — by the screen reader for its bounds, by a test for its size — though the
    // node's own text is replaced by what is spoken.
    val laidOut = remember { arrayOfNulls<TextLayoutResult>(1) }
    Text(
        shown,
        style = stepTextStyle,
        onTextLayout = { laidOut[0] = it },
        modifier = modifier.clearAndSetSemantics {
            text = AnnotatedString(spoken)
            shownText = shown.text
            testTag = tag
            getTextLayoutResult { results -> laidOut[0]?.let { results += it } != null }
        },
    )
}

/**
 * How a step reads on the card: larger than the ingredient list and than body text anywhere else in the product, so
 * it can be read at arm's length (A11Y-007): the theme's title style, a size up from body text; it follows the font
 * scale like any text (A11Y-004).
 */
internal val stepTextStyle: TextStyle
    @Composable get() = MaterialTheme.typography.titleLarge

/** A narrow space that never breaks, so the arrow stays on its quantity's line. */
private const val ARROW_GAP = "\u202F"
