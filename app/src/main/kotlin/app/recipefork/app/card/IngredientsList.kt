package app.recipefork.app.card

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.material3.ripple
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.offset
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import app.recipefork.app.R
import app.recipefork.app.ui.Motion
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.scaling.InterfaceLanguage
import app.recipefork.core.scaling.Rounding

/**
 * The card's ingredients, on a tonal surface of the theme, in order, at the scale in force — a requested serving count (SCALE-001) or the amount the
 * reader fixed of one ingredient (SCALE-002). A status line always stands at the top of the section: at the authored
 * scale it says the quantities are as the author wrote them; once any quantity is computed at another scale, it says
 * they are recalculated, with a legend for the arrows (SCALE-004 (1), met for the section rather than per line) —
 * its height that of the taller of the two, so the list never moves between them; a quantity rounding moved carries ↑ where the exact value was
 * lower — a floor-held one included — and ↓ where it was higher, each read aloud in words (SCALE-004, SCALE-005).
 * Once two or more lines are held at their floors, a warning says the quantities no longer hold the authored
 * proportions (SCALE-009). A fixed line says it is fixed. A line's note follows its name, muted (DOM-023). None of it is carried by colour (A11Y-008). With no
 * ingredients the part is left out, heading included (FLOW-019).
 *
 * Tapping a line marks it done, and tapping it again unmarks it ([marks], FLOW-068). Tapping the amount of a line that
 * carries a quantity opens its fixing control below it ([FixAmount]); a line "to taste", an unfinished one, or one off
 * its grid offers no fixing (SCALE-018 (7)). Without [marks] a line is not marked.
 */
@Composable
internal fun IngredientsList(
    ordered: List<Ingredient>,
    list: IngredientList,
    fixings: List<CardFixing>,
    language: InterfaceLanguage,
    onFix: (CardFixing) -> Unit,
    onRelease: (Int) -> Unit,
    marks: CardMarks? = null,
) {
    if (ordered.isEmpty()) return
    var open by remember(ordered) { mutableStateOf<Int?>(null) }
    // The list stands apart from the steps on a tonal surface of the theme, in its medium corners. The surface reaches
    // into the card's gutter, so the lines keep the whole width of the content and wrap no sooner (A11Y-004).
    Surface(
        Modifier.fillMaxWidth().bleed(SURFACE_BLEED).testTag(IngredientTags.SURFACE),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Box(Modifier.padding(horizontal = SURFACE_BLEED, vertical = SURFACE_PADDING)) { Lines(ordered, list, fixings, language, open, { open = it }, onFix, onRelease, marks) }
    }
}

/** The list's heading, status, warning and lines, with the fixing control [open] under its line. */
@Composable
private fun Lines(
    ordered: List<Ingredient>,
    list: IngredientList,
    fixings: List<CardFixing>,
    language: InterfaceLanguage,
    open: Int?,
    onOpen: (Int?) -> Unit,
    onFix: (CardFixing) -> Unit,
    onRelease: (Int) -> Unit,
    marks: CardMarks?,
) {
    Section(R.string.card_ingredients, CardTags.INGREDIENTS) {
        // The warning unfolds under the status line and folds away into it, the gap above it included, so the lines
        // below move with it rather than jump; the status line itself keeps its height.
        Column {
            ScaleStatus(list.recalculated)
            AnimatedVisibility(!list.holdsProportions, enter = Motion.open, exit = Motion.close) {
                Text(
                    stringResource(R.string.card_proportions_warning),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = SECTION_GAP)
                        .semantics { liveRegion = LiveRegionMode.Polite }
                        .testTag(CardTags.PROPORTIONS),
                )
            }
        }
        val column = quantityColumn(list.lines)
        list.lines.forEachIndexed { i, line ->
            val position = ordered[i].position
            // The line and its fixing control are one part of the section, so the gap above the control unfolds with it.
            Column {
                IngredientRow(
                    line, position, column, cardPlace(CardPlaces.ingredient(position)),
                    done = marks?.done(MarkedPart.INGREDIENT, position) == true,
                    onToggle = marks?.let { { it.toggle(MarkedPart.INGREDIENT, position) } },
                ) { onOpen(if (open == position) null else position) }
                val offer = line.fixing
                if (offer != null) {
                    key(position) {
                        FixAmountUnder(
                            open = open == position,
                            offer = offer,
                            current = fixings.firstOrNull { it.position == position },
                            language = language,
                            onFix = { amount, unit -> onFix(CardFixing(position, amount, unit)); onOpen(null) },
                            onRelease = { onRelease(position); onOpen(null) },
                            onClose = { onOpen(null) },
                        )
                    }
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
    /** The tonal surface the whole section sits on. */
    const val SURFACE = "card-ingredients-surface"
    /** The status line above the list, always present: as authored, or recalculated. */
    const val STATUS = "card-scale-status"
    /** The status line's text at the authored scale. */
    const val AUTHORED = "card-as-authored"
    /** The arrow after the quantity of the line at [position], where rounding moved it. */
    fun rounding(position: Int) = "card-ingredient-$position-rounding"
    /** The name of the line at [position], with its note after it where it carries one. */
    fun name(position: Int) = "card-ingredient-$position-name"
}

/**
 * The text a sighted reader sees on a part whose screen-reader text is set apart from it — the arrows and the
 * section line — kept in the semantics tree for tests, never read aloud.
 */
internal val ShownText = SemanticsPropertyKey<String>(SHOWN_TEXT_KEY)
private const val SHOWN_TEXT_KEY = "card.shown-text"
internal var SemanticsPropertyReceiver.shownText by ShownText

/**
 * The status line above the list, always present: "as the author wrote it" at the authored scale, otherwise the line
 * that says the quantities are recalculated and what the arrows mean, read by the screen reader in full words rather
 * than as the arrows. It is as high as the taller of the two texts at the width and font scale in force, so changing
 * between them never moves the list below.
 */
@Composable
private fun ScaleStatus(recalculated: Boolean) {
    val spoken = stringResource(R.string.card_quantities_recalculated_spoken)
    val shown = stringResource(R.string.card_quantities_recalculated)
    val authored = stringResource(R.string.card_quantities_as_authored)
    val style = MaterialTheme.typography.bodySmall
    val measurer = rememberTextMeasurer()
    Layout(
        content = {
            if (recalculated) {
                Text(
                    shown,
                    style = style,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clearAndSetSemantics {
                        text = AnnotatedString(spoken); shownText = shown; testTag = IngredientTags.RECALCULATED
                    },
                )
            } else {
                Text(
                    authored,
                    style = style,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag(IngredientTags.AUTHORED),
                )
            }
        },
        modifier = Modifier.fillMaxWidth().testTag(IngredientTags.STATUS),
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val text = measurables.single().measure(Constraints(maxWidth = width))
        fun heightOf(it: String) = measurer.measure(it, style, constraints = Constraints(maxWidth = width), density = this).size.height
        val height = maxOf(text.height, heightOf(shown), heightOf(authored))
        layout(width, height) { text.place(0, 0) }
    }
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
 * cut. Read by the screen reader as one line — the name, then the quantity — the arrow as the words it stands for.
 *
 * A tap on the line marks it [done] or unmarks it ([onToggle], FLOW-068); a line marked done is struck through and
 * muted, and read as done. A line that offers fixing opens its control from its amount — a target of its own, at
 * least [AMOUNT_TARGET] wide and the line's height, named for what it does — and offers the same to the screen reader
 * as the line's second action, beside the marking ([onOpen]). A line that answers a tap is the card's [CardTarget] tall.
 */
@Composable
private fun IngredientRow(
    line: IngredientLine,
    position: Int,
    column: QuantityColumn,
    place: Modifier,
    done: Boolean,
    onToggle: (() -> Unit)?,
    onOpen: () -> Unit,
) {
    val fixable = line.fixing != null
    val fixLabel = stringResource(R.string.card_fix_action)
    val opening by rememberUpdatedState(onOpen)
    val marking = if (onToggle != null) Modifier.markedDone(done, onToggle) else Modifier
    val fixing = if (fixable) {
        Modifier.semantics { customActions = listOf(CustomAccessibilityAction(fixLabel) { opening(); true }) }
    } else {
        Modifier
    }
    val tall = onToggle != null || fixable
    Column(place.fillMaxWidth().then(marking).then(fixing).semantics(mergeDescendants = true) {}.testTag(CardTags.ingredient(position))) {
        val name: @Composable () -> Unit = {
            val named = line.name ?: stringResource(R.string.card_unnamed_ingredient)
            val muted = MaterialTheme.colorScheme.onSurfaceVariant
            Text(
                remember(named, line.note, muted) { withNote(named, line.note, muted) },
                style = MaterialTheme.typography.bodyLarge.done(done),
                modifier = Modifier.testTag(IngredientTags.name(position)),
            )
        }
        val quantity: @Composable () -> Unit = {
            line.quantity?.let { quantity ->
                Row(Modifier.fillMaxWidth()) {
                    // The arrow is measured first, so a long quantity wraps before it squeezes the arrow away.
                    Text(
                        quantity,
                        style = MaterialTheme.typography.bodyLarge.done(done),
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
        val fixedMark: @Composable () -> Unit = { if (line.fixed) FixedMark(stringResource(R.string.card_quantity_fixed), done) }
        val amount: @Composable () -> Unit = { if (fixable) AmountTarget(fixLabel, CardTags.fixAmount(position)) { opening() } }
        // The quantity stands at the line's start, the name beside it and the fixed mark under the name; the amount's
        // target lies over the quantity, as tall as the line, and never over the name.
        Layout(contents = listOf(name, quantity, fixedMark, amount), modifier = Modifier.fillMaxWidth()) { (names, quantities, notes, amounts), constraints ->
            val gap = if (column.whole > 0) QUANTITY_GAP.roundToPx() else 0
            val width = constraints.maxWidth
            val share = maxOf((width * QUANTITY_SHARE).toInt(), column.word)
            val fitted = minOf(column.whole, share, width)
            // Short quantities ("2 pc", "1") still leave the amount's target its least width before the names begin:
            // the column is then as wide as that target, less the gap, the same on every line so the names stay aligned.
            val quantityWidth = if (column.whole > 0) maxOf(fitted, AMOUNT_TARGET.roundToPx() - gap).coerceAtMost(width) else fitted
            // The quantity takes the column's whole width and sets itself against its end.
            val shownQuantity = quantities.firstOrNull()?.measure(Constraints.fixedWidth(quantityWidth.coerceAtLeast(0)))
            val nameConstraints = Constraints(maxWidth = (width - quantityWidth - gap).coerceAtLeast(0))
            val shownName = names.first().measure(nameConstraints)
            val shownNote = notes.firstOrNull()?.measure(nameConstraints)
            val content = maxOf(shownName.height + (shownNote?.height ?: 0), shownQuantity?.height ?: 0)
            val height = if (tall) maxOf(content, CardTarget.roundToPx()) else content
            val top = (height - content) / 2
            // It ends where the name begins at the latest, so a tap on the name always marks the line.
            val amountWidth = maxOf(quantityWidth + gap / 2, AMOUNT_TARGET.roundToPx()).coerceAtMost(minOf(width, quantityWidth + gap))
            val shownAmount = amounts.firstOrNull()?.measure(Constraints.fixed(amountWidth, height))
            layout(width, height) {
                // Placed in the order they read: the screen reader takes a line's parts in the order they are placed.
                shownName.place(quantityWidth + gap, top)
                shownQuantity?.place(0, top)
                shownNote?.place(quantityWidth + gap, top + shownName.height)
                // Last, so that it lies over the line and takes the taps on the amount.
                shownAmount?.place(0, 0)
            }
        }
    }
}

/** The least width of the target an amount that offers fixing takes, the card's own; it is as tall as its line. */
private val AMOUNT_TARGET = CardTarget

/**
 * The target over a line's amount that opens its fixing control ([onOpen]): a tap on it opens the control, not the
 * line's marking, and shows the theme's ripple; the screen reader finds it as the line's second action, named by
 * [label], since the line reads as one.
 */
@Composable
private fun AmountTarget(label: String, tag: String, onOpen: () -> Unit) {
    val open by rememberUpdatedState(onOpen)
    val presses = remember { MutableInteractionSource() }
    Box(
        Modifier
            .indication(presses, ripple())
            .pointerInput(presses) {
                detectTapGestures(
                    onPress = { offset ->
                        val press = PressInteraction.Press(offset)
                        presses.tryEmit(press)
                        presses.tryEmit(if (tryAwaitRelease()) PressInteraction.Release(press) else PressInteraction.Cancel(press))
                    },
                    onTap = { open() },
                )
            }
            .semantics { onClick(label = label) { open(); true } }
            .testTag(tag),
    )
}

/** How far the tonal surface reaches into the card's gutter on each side; the section's lines start where they would without it. */
private val SURFACE_BLEED = 8.dp

/** The space above and below the section on its tonal surface. */
private val SURFACE_PADDING = 12.dp

/** Laid out [by] wider on each side than the room it is given, the room it takes still only that. */
private fun Modifier.bleed(by: Dp) = layout { measurable, constraints ->
    val extra = by.roundToPx() * 2
    val placeable = measurable.measure(constraints.offset(horizontal = extra))
    layout((placeable.width - extra).coerceAtLeast(0), placeable.height) { placeable.place(-extra / 2, 0) }
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

/** What a fixed line says under its name. */
@Composable
private fun FixedMark(text: String, done: Boolean) {
    Text(text, style = MaterialTheme.typography.bodySmall.done(done))
}

/**
 * The name of a line followed by its [note] (DOM-023), set apart by a dash and drawn in the [muted] colour, so that how
 * the ingredient is prepared reads apart from what it is; the whole wraps between words as one text.
 */
internal fun withNote(name: String, note: String?, muted: Color): AnnotatedString = buildAnnotatedString {
    append(name)
    if (note != null) withStyle(SpanStyle(color = muted)) { append(NOTE_SEPARATOR); append(note) }
}

/** What stands between a line's name and its note. */
private const val NOTE_SEPARATOR = " — "

/** The arrow a recalculated quantity carries and the words it is read as, or null where the grid held it exactly. */
internal fun roundingMark(rounding: Rounding): Pair<String, Int>? = when (rounding) {
    Rounding.NONE -> null
    Rounding.UP -> "↑" to R.string.card_quantity_rounded_up
    Rounding.DOWN -> "↓" to R.string.card_quantity_rounded_down
}
