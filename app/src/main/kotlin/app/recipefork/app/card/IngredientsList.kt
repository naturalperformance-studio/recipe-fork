package app.recipefork.app.card

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import app.recipefork.app.R
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.scaling.InterfaceLanguage
import app.recipefork.core.scaling.Rounding

/**
 * The card's ingredients, in order, at the scale in force — a requested serving count (SCALE-001) or the amount the
 * reader fixed of one ingredient (SCALE-002). Once any quantity is computed at a scale other than the authored one,
 * one line at the top of the section says the quantities are recalculated, with a legend for the arrows (SCALE-004
 * (1), met for the section rather than per line); a quantity rounding moved carries ↑ where the exact value was
 * lower — a floor-held one included — and ↓ where it was higher, each read aloud in words (SCALE-004, SCALE-005).
 * Once two or more lines are held at their floors, a warning says the quantities no longer hold the authored
 * proportions (SCALE-009). A fixed line says it is fixed. None of it is carried by colour (A11Y-008). With no
 * ingredients the part is left out, heading included (FLOW-019).
 *
 * Tapping a line that carries a quantity opens its fixing control below it ([FixAmount]); a line "to taste", an
 * unfinished one, or one off its grid offers nothing (SCALE-018 (7)).
 */
@Composable
internal fun IngredientsList(
    ordered: List<Ingredient>,
    list: IngredientList,
    fixings: List<CardFixing>,
    language: InterfaceLanguage,
    onFix: (CardFixing) -> Unit,
    onRelease: (Int) -> Unit,
) {
    if (ordered.isEmpty()) return
    var open by remember(ordered) { mutableStateOf<Int?>(null) }
    Section(R.string.card_ingredients, CardTags.INGREDIENTS) {
        if (list.recalculated) RecalculatedLegend()
        if (!list.holdsProportions) {
            Text(
                stringResource(R.string.card_proportions_warning),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { liveRegion = LiveRegionMode.Polite }
                    .testTag(CardTags.PROPORTIONS),
            )
        }
        val column = quantityColumn(list.lines)
        list.lines.forEachIndexed { i, line ->
            val position = ordered[i].position
            IngredientRow(line, position, column, cardPlace(CardPlaces.ingredient(position))) { open = if (open == position) null else position }
            val offer = line.fixing
            if (offer != null && open == position) {
                key(position) {
                    FixAmount(
                        offer = offer,
                        current = fixings.firstOrNull { it.position == position },
                        language = language,
                        onFix = { amount, unit -> onFix(CardFixing(position, amount, unit)); open = null },
                        onRelease = { onRelease(position); open = null },
                        onClose = { open = null },
                    )
                }
            }
        }
    }
}

/** Whether any quantity of the list was computed at a scale other than the authored one: the section says so once. */
internal val IngredientList.recalculated: Boolean get() = lines.any { it.derived }

/** Test tags of the ingredients section's own parts. */
internal object IngredientTags {
    const val RECALCULATED = "card-recalculated"
    /** The arrow after the quantity of the line at [position], where rounding moved it. */
    fun rounding(position: Int) = "card-ingredient-$position-rounding"
}

/**
 * The text a sighted reader sees on a part whose screen-reader text is set apart from it — the arrows and the
 * section line — kept in the semantics tree for tests, never read aloud.
 */
internal val ShownText = SemanticsPropertyKey<String>(SHOWN_TEXT_KEY)
private const val SHOWN_TEXT_KEY = "card.shown-text"
internal var SemanticsPropertyReceiver.shownText by ShownText

/**
 * The one line that says the section's quantities are recalculated and what the arrows mean; read by the screen
 * reader in full words rather than as the arrows.
 */
@Composable
private fun RecalculatedLegend() {
    val spoken = stringResource(R.string.card_quantities_recalculated_spoken)
    val shown = stringResource(R.string.card_quantities_recalculated)
    Text(
        shown,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { text = AnnotatedString(spoken); shownText = shown; testTag = IngredientTags.RECALCULATED },
    )
}

/**
 * The list's quantity column, in pixels: [whole] is the width of its longest quantity with its arrow, laid out on one
 * line in the list's style at the font scale in force — none where no line carries a quantity; [word] is that of the
 * longest word of any quantity with the arrow, the least the column narrows to so that a quantity wraps only between
 * words.
 */
private data class QuantityColumn(val whole: Int, val word: Int)

@Composable
private fun quantityColumn(lines: List<IngredientLine>): QuantityColumn {
    val measurer = rememberTextMeasurer()
    val quantityStyle = MaterialTheme.typography.bodyLarge
    val arrowStyle = MaterialTheme.typography.labelMedium
    val density = LocalDensity.current
    return remember(lines, quantityStyle, arrowStyle, density) {
        fun width(text: String) = measurer.measure(text, quantityStyle, softWrap = false, maxLines = 1).size.width
        val arrows = lines.map { line ->
            roundingMark(line.rounding)?.takeIf { line.derived }?.first?.let {
                measurer.measure(it, arrowStyle, softWrap = false, maxLines = 1).size.width + with(density) { ARROW_GAP.roundToPx() }
            } ?: 0
        }
        // A pixel's slack, so rounding never wraps the longest quantity.
        val whole = lines.indices.maxOfOrNull { i -> lines[i].quantity?.let { width(it) + arrows[i] + 1 } ?: 0 } ?: 0
        val word = lines.indices.maxOfOrNull { i ->
            lines[i].quantity?.split(' ')?.maxOf { width(it) }?.let { it + arrows[i] + 1 } ?: 0
        } ?: 0
        QuantityColumn(whole, word)
    }
}

/**
 * One ingredient: its quantity, right-aligned in the list's quantity [column] and followed by an
 * arrow where rounding moved it, its name beside it, and under the name whether it is fixed. Where the column would take
 * more than its share of a narrow line or large text, it narrows and the quantity wraps in it; the name wraps, never
 * cut. Read by the screen reader as one line — the name, then the quantity — the arrow as the words it stands for; a
 * line that offers fixing is one action that opens its control.
 */
@Composable
private fun IngredientRow(line: IngredientLine, position: Int, column: QuantityColumn, place: Modifier, onOpen: () -> Unit) {
    val tappable = if (line.fixing != null) {
        Modifier
            .heightIn(min = CardTarget)
            .clickable(onClickLabel = stringResource(R.string.card_fix_action), role = Role.Button, onClick = onOpen)
    } else {
        Modifier
    }
    Column(
        place.fillMaxWidth().then(tappable).semantics(mergeDescendants = true) {}.testTag(CardTags.ingredient(position)),
        verticalArrangement = Arrangement.Center,
    ) {
        val name: @Composable () -> Unit = {
            Text(line.name ?: stringResource(R.string.card_unnamed_ingredient), style = MaterialTheme.typography.bodyLarge)
        }
        val quantity: @Composable () -> Unit = {
            line.quantity?.let { quantity ->
                Row(Modifier.fillMaxWidth()) {
                    // The arrow is measured first, so a long quantity wraps before it squeezes the arrow away.
                    Text(
                        quantity,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f).alignByBaseline(),
                    )
                    if (line.derived) {
                        roundingMark(line.rounding)?.let { (arrow, spoken) ->
                            RoundingArrow(arrow, stringResource(spoken), IngredientTags.rounding(position))
                        }
                    }
                }
            }
        }
        val note: @Composable () -> Unit = { if (line.fixed) Note(stringResource(R.string.card_quantity_fixed)) }
        // The quantity stands at the line's start, the name beside it and the fixed note under the name.
        Layout(contents = listOf(name, quantity, note), modifier = Modifier.fillMaxWidth()) { (names, quantities, notes), constraints ->
            val gap = if (column.whole > 0) QUANTITY_GAP.roundToPx() else 0
            val width = constraints.maxWidth
            val share = maxOf((width * QUANTITY_SHARE).toInt(), column.word)
            val quantityWidth = minOf(column.whole, share, width)
            // The quantity takes the column's whole width and sets itself against its end.
            val shownQuantity = quantities.firstOrNull()?.measure(Constraints.fixedWidth(quantityWidth.coerceAtLeast(0)))
            val nameConstraints = Constraints(maxWidth = (width - quantityWidth - gap).coerceAtLeast(0))
            val shownName = names.first().measure(nameConstraints)
            val shownNote = notes.firstOrNull()?.measure(nameConstraints)
            val height = maxOf(shownName.height + (shownNote?.height ?: 0), shownQuantity?.height ?: 0)
            layout(width, height) {
                // Placed in the order they read: the screen reader takes a line's parts in the order they are placed.
                shownName.place(quantityWidth + gap, 0)
                shownQuantity?.place(0, 0)
                shownNote?.place(quantityWidth + gap, shownName.height)
            }
        }
    }
}

/** The space between the quantity column and the names. */
private val QUANTITY_GAP = 12.dp

/** The most of a line's width the quantity column takes, unless a word needs more; a longer quantity wraps in it. */
private const val QUANTITY_SHARE = 0.4f

/** The space before a quantity's arrow. */
private val ARROW_GAP = 2.dp

/** The arrow after a rounded quantity: small and secondary on screen, the words [spoken] for the screen reader. */
@Composable
private fun RowScope.RoundingArrow(arrow: String, spoken: String, tag: String) {
    Text(
        arrow,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.alignByBaseline().padding(start = ARROW_GAP).clearAndSetSemantics {
            text = AnnotatedString(spoken)
            shownText = arrow
            testTag = tag
        },
    )
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall)
}

/** The arrow a recalculated quantity carries and the words it is read as, or null where the grid held it exactly. */
internal fun roundingMark(rounding: Rounding): Pair<String, Int>? = when (rounding) {
    Rounding.NONE -> null
    Rounding.UP -> "↑" to R.string.card_quantity_rounded_up
    Rounding.DOWN -> "↓" to R.string.card_quantity_rounded_down
}
