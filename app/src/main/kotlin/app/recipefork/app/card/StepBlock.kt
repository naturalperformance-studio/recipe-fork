package app.recipefork.app.card

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.getTextLayoutResult
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.recipefork.app.timer.RecipeTimers
import app.recipefork.core.recipe.Step

/**
 * One step on [CardMode.VIEW], as a page of a cookbook sets it: its number large in the [margin], in the reading face,
 * muted ([MarginNumber]); beside it the step's text in body size ([stepTextStyle]), its references in the working face,
 * and the author's note under the text ([StepNote]) — read by the screen reader as one line that says the number once,
 * the note after the text. Under the text, in its column, the step's photograph or clip, named by its number, then its
 * timers as duration labels ([ViewTimer]) side by side, wrapping to the next line where they do not fit. A dotted rule
 * in the theme's outline-variant colour parts it from the next step, none after the [last]; the [first] stands a little
 * further under the section's heading.
 *
 * Nothing on it answers a tap but its media's own controls: a mark is shown, read-only — the text struck through and
 * muted when [done] — its state read while the recipe has any mark ([stated]).
 */
@Composable
internal fun StepBlock(
    number: Int,
    step: Step,
    references: CardReferences,
    timers: RecipeTimers,
    media: StepMediaOnCard,
    margin: Dp,
    modifier: Modifier = Modifier,
    done: Boolean = false,
    stated: Boolean = false,
    first: Boolean = true,
    last: Boolean = true,
) {
    val rule = if (last) Modifier else Modifier.dottedRule()
    Column(
        modifier.fillMaxWidth().then(rule)
            .padding(top = (if (first) VIEW_FIRST_STEP_ABOVE else VIEW_STEP_ABOVE) - SECTION_GAP, bottom = VIEW_STEP_BELOW)
            .testTag(StepRowTags.entry(step.position)),
    ) {
        Row(
            Modifier.fillMaxWidth().shownDone(done, stated).semantics(mergeDescendants = true) { text = AnnotatedString("$number.") }
                .testTag(CardTags.step(step.position)),
            horizontalArrangement = Arrangement.spacedBy(VIEW_MARGIN_GAP),
        ) {
            MarginNumber(number, step.position, margin)
            Column(Modifier.weight(1f)) {
                StepText(step, references, done = done, mode = CardMode.VIEW)
                step.note?.let { StepNote(it, step.position, CardMode.VIEW) }
            }
        }
        val indent = margin + VIEW_MARGIN_GAP
        media.of(step.position)?.let { item ->
            Box(Modifier.fillMaxWidth().padding(start = indent, top = VIEW_LABEL_GAP)) {
                StepMedia(item, number, step.position)
            }
        }
        if (step.timers.isNotEmpty()) {
            FlowRow(
                Modifier.fillMaxWidth().padding(start = indent, top = VIEW_LABEL_GAP).testTag(StepBlockTags.labels(step.position)),
                horizontalArrangement = Arrangement.spacedBy(VIEW_LABELS_GAP),
                verticalArrangement = Arrangement.spacedBy(VIEW_LABEL_GAP),
            ) {
                step.timers.indices.forEach { index -> ViewTimer(step, index, timers, references::quantity) }
            }
        }
    }
}

/** Test tags of a [StepBlock]'s parts. */
internal object StepBlockTags {
    /** The duration labels of the step at [position], side by side. */
    fun labels(position: Int) = "card-step-$position-labels"
}

/**
 * A step's [number] in the margin on [CardMode.VIEW]: [marginNumberStyle], in the theme's on-surface-variant colour, in a
 * column [width] wide. Decorative: the step's line says the number.
 */
@Composable
private fun MarginNumber(number: Int, position: Int, width: Dp) {
    // Its layout stays reachable — by a test for its size — though the node says nothing.
    val laidOut = remember { arrayOfNulls<TextLayoutResult>(1) }
    Text(
        "$number",
        style = marginNumberStyle(),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        softWrap = false,
        maxLines = 1,
        onTextLayout = { laidOut[0] = it },
        modifier = Modifier.width(width).clearAndSetSemantics {
            testTag = StepRowTags.number(position)
            showsCheck = false
            getTextLayoutResult { results -> laidOut[0]?.let { results += it } != null }
        },
    )
}

/** How a step's number reads in the margin: the theme's reading face — its small headline's — at 30 sp, set tight. */
@Composable
internal fun marginNumberStyle(): TextStyle =
    MaterialTheme.typography.headlineSmall.copy(fontSize = MARGIN_NUMBER_SIZE, lineHeight = MARGIN_NUMBER_LINE)

/**
 * The margin the numbers of [count] steps stand in on [CardMode.VIEW]: [VIEW_MARGIN] wide, or as wide as the widest
 * number needs where it is wider — a long recipe's, or any at a large font scale — so that no number is cut and every
 * step's text starts at the same line.
 */
@Composable
internal fun rememberStepMargin(count: Int): Dp {
    val measurer = rememberTextMeasurer()
    val style = marginNumberStyle()
    val density = LocalDensity.current
    return remember(count, style, density) {
        val widest = (1..maxOf(count, 1)).maxOf { measurer.measure("$it", style, softWrap = false, maxLines = 1).size.width }
        with(density) { maxOf(VIEW_MARGIN, widest.toDp()) }
    }
}

/** The dotted rule under a step on [CardMode.VIEW], at its bottom edge. */
@Composable
private fun Modifier.dottedRule(): Modifier {
    val color = MaterialTheme.colorScheme.outlineVariant
    return drawBehind {
        val width = RULE_WIDTH.toPx()
        val y = size.height - width / 2f
        val dot = RULE_DOT.toPx()
        drawLine(
            color, Offset(0f, y), Offset(size.width, y), strokeWidth = width,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(dot, dot)),
        )
    }
}

/** The size of a step's number in the margin. */
internal val MARGIN_NUMBER_SIZE = 30.sp

/** The line height of a step's number in the margin: tight, a little under its size. */
private val MARGIN_NUMBER_LINE = 28.5.sp

/** The least width of the margin the steps' numbers stand in. */
internal val VIEW_MARGIN = 34.dp

/** The gap between the margin and a step's text. */
internal val VIEW_MARGIN_GAP = 8.dp

/** The space above a step's text, under the rule above it. */
internal val VIEW_STEP_ABOVE = 10.dp

/** The space above the first step's text, under the section's heading. */
internal val VIEW_FIRST_STEP_ABOVE = 16.dp

/** The space below a step, above its rule. */
internal val VIEW_STEP_BELOW = 12.dp

/** The gap above a step's media and its duration labels, and between two lines of labels. */
internal val VIEW_LABEL_GAP = 8.dp

/** The gap between two duration labels side by side. */
internal val VIEW_LABELS_GAP = 6.dp

/** The width of the rule between steps. */
private val RULE_WIDTH = 1.dp

/** The length of a dot of the rule, and of the gap after it. */
private val RULE_DOT = 1.dp
