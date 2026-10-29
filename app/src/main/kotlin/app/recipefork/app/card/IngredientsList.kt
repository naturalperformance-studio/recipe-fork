package app.recipefork.app.card

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.MutableState
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
import androidx.compose.ui.semantics.heading
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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.offset
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.em
import app.recipefork.app.ui.isLargeText
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import app.recipefork.app.R
import app.recipefork.app.ui.Motion
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.IngredientGroup
import app.recipefork.core.scaling.InterfaceLanguage
import app.recipefork.core.scaling.Rounding
import androidx.compose.ui.semantics.getTextLayoutResult
import androidx.compose.ui.text.TextLayoutResult
import app.recipefork.core.scaling.UnitLabels
import app.recipefork.core.recipe.QuantityUnit
import kotlin.math.roundToInt

/**
 * The card's ingredients, on a tonal surface of the theme, in order, at the scale in force — a requested serving count (SCALE-001) or the amount the
 * reader fixed of one ingredient (SCALE-002). A status line always stands at the top of the section: at the authored
 * scale it says the quantities are as the author wrote them; once any quantity is computed at another scale, it says
 * they are recalculated, with a legend for the arrows (SCALE-004 (1), met for the section rather than per line) —
 * its height that of the taller of the two, so the list never moves between them; a quantity rounding moved carries ↑ where the exact value was
 * lower — a floor-held one included — and ↓ where it was higher, each read aloud in words (SCALE-004, SCALE-005).
 * Once two or more lines are held at their floors, a warning says the quantities no longer hold the authored
 * proportions (SCALE-009). A fixed line says it is fixed. A line's note follows its name, muted (DOM-023). None of
 * it is carried by colour (A11Y-008). With no ingredients the part is left out, heading included (FLOW-019).
 *
 * Tapping a line marks it done, and tapping it again unmarks it ([marks], FLOW-068). Tapping the amount of a line that
 * carries a quantity opens its fixing control below it ([FixAmount]); a line "to taste", an unfinished one, or one off
 * its grid offers no fixing (SCALE-018 (7)). Without [marks] a line is not marked.
 *
 * Each of the [groups] stands as a subheading over its lines, a heading for the screen reader; lines above the first
 * heading stand under none, and a heading with no lines yet is not shown (DOM-022). A group is presentation only: the
 * lines, their scaling, fixing, marks and references read exactly as without it.
 *
 * Once every line is marked done, the list folds under its heading into one line, "All gathered · show"
 * ([GatheredLine]); a tap on it shows the lines again, still marked. A line unmarked, or the marks cleared, shows the
 * lines again too, and marking every line once more folds them again. Whether the reader has shown the list again is
 * [shownAgain], which the card holds above its tabs, so that it outlasts a turn to View and back.
 *
 * All of the above is the [CardMode.COOKING] presentation. In [CardMode.VIEW] a line is as tall as its text with a
 * modest space around it, its done mark is shown but a tap leaves it as it is, no line offers fixing, and the list
 * never folds; a fixing control open as the mode changes closes. The scale, the status line, the warning, groups and
 * notes are as in cooking, set as a page of a cookbook: the quantities in a column of their own in the working face,
 * their arrows in it, above the names at large text ([PageLine]); the group subheadings in small capitals.
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
    groups: List<IngredientGroup> = emptyList(),
    mode: CardMode = CardMode.COOKING,
    shownAgain: MutableState<Boolean> = remember { mutableStateOf(false) },
) {
    if (ordered.isEmpty()) return
    var open by remember(ordered, mode) { mutableStateOf<Int?>(null) }
    // Every line marked: on Cooking the list folds, unless the reader has shown it again since. A line not marked ends
    // the showing again, so that marking every line once more folds the list again.
    val gathered = marks != null && ordered.all { marks.done(MarkedPart.INGREDIENT, it.position) }
    val folded = foldedOnCooking(gathered, shownAgain, mode)
    LaunchedEffect(folded) { if (folded) open = null }
    GatheringSurface(IngredientTags.SURFACE) {
        Lines(ordered, list, fixings, language, open, { open = it }, onFix, onRelease, marks, groups, mode, folded) { shownAgain.value = true }
    }
}

/**
 * Whether a list whose every line is marked ([gathered]) stands folded: on [CardMode.COOKING] alone, and not once the
 * reader has shown it again ([shownAgain]). A line not marked ends the showing again, so that marking every line once
 * more folds the list again.
 */
@Composable
internal fun foldedOnCooking(gathered: Boolean, shownAgain: MutableState<Boolean>, mode: CardMode): Boolean {
    LaunchedEffect(gathered) { if (!gathered) shownAgain.value = false }
    return mode == CardMode.COOKING && gathered && !shownAgain.value
}

/**
 * A list of what is gathered before cooking — the ingredients, the equipment — standing apart from the steps on a
 * tonal surface of the theme, in its medium corners. The surface reaches into the card's gutter, so the lines keep the
 * whole width of the content and wrap no sooner (A11Y-004).
 */
@Composable
internal fun GatheringSurface(tag: String, content: @Composable () -> Unit) {
    Surface(
        Modifier.fillMaxWidth().bleed(SURFACE_BLEED).testTag(tag),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Box(Modifier.padding(horizontal = SURFACE_BLEED, vertical = SURFACE_PADDING)) { content() }
    }
}

/**
 * The list's heading, status, warning and lines, with the fixing control [open] under its line; or, [folded], the
 * heading and the one line that shows the rest ([onShow]).
 */
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
    groups: List<IngredientGroup>,
    mode: CardMode,
    folded: Boolean,
    onShow: () -> Unit,
) {
    // A small gap under the heading; the status line, the warning and the lines then stand one against the next, so that
    // more lines are in sight as the card opens, and a group's subheading has the same gap above it on the first line
    // as on any other.
    Section(R.string.card_ingredients, CardTags.INGREDIENTS, gap = HEADING_GAP) {
        // Folded, the lines fold up into the one line that says so, and unfold from it again.
        AnimatedVisibility(folded, enter = Motion.open, exit = Motion.close) {
            GatheredLine(R.string.card_ingredients_show_action, IngredientTags.GATHERED, onShow)
        }
        AnimatedVisibility(!folded, enter = Motion.open, exit = Motion.close) { AllLines(ordered, list, fixings, language, open, onOpen, onFix, onRelease, marks, groups, mode) }
    }
}

/** The list's status, warning and lines, with the fixing control [open] under its line. */
@Composable
private fun AllLines(
    ordered: List<Ingredient>,
    list: IngredientList,
    fixings: List<CardFixing>,
    language: InterfaceLanguage,
    open: Int?,
    onOpen: (Int?) -> Unit,
    onFix: (CardFixing) -> Unit,
    onRelease: (Int) -> Unit,
    marks: CardMarks?,
    groups: List<IngredientGroup>,
    mode: CardMode,
) {
    val cooking = mode == CardMode.COOKING
    val column = quantityColumn(list.lines)
    val page = if (cooking) null else pageColumn(list.lines)
    // The status line, then the lines one against the next, with no gap between them: each line's own height is its
    // space. The warning unfolds under the status line and folds away into it, the gap above it included, so the
    // lines below move with it rather than jump; the status line itself keeps its height.
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
        list.lines.forEachIndexed { i, line ->
            groups.forEachIndexed { index, group -> if (group.firstPosition == i) GroupHeading(index, group.name, mode) }
            val position = ordered[i].position
            // The line and its fixing control are one part of the section, so the gap above the control unfolds with it.
            Column {
                IngredientRow(
                    line, position, column, page, cardPlace(CardPlaces.ingredient(position)),
                    done = marks?.done(MarkedPart.INGREDIENT, position) == true,
                    onToggle = marks?.takeIf { cooking }?.let { { it.toggle(MarkedPart.INGREDIENT, position) } },
                    stated = marks?.any == true,
                    mode = mode,
                ) { onOpen(if (open == position) null else position) }
                val offer = line.fixing?.takeIf { cooking }
                if (offer != null) {
                    key(position) {
                        FixAmountUnder(
                            open = open == position,
                            offer = offer,
                            current = fixings.firstOrNull { it.position == position }?.let(offer::typed),
                            language = language,
                            onFix = { amount, unit -> onFix(offer.fixing(position, amount, unit)); onOpen(null) },
                            onRelease = { onRelease(position); onOpen(null) },
                            onClose = { onOpen(null) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * The one line a list folds into once every line of it is marked: "All gathered · show", the word that shows the list
 * in the theme's accent colour. The whole line, the card's [CardTarget] tall, is one M3 text button that shows the list
 * again ([onShow]); the screen reader reads it as all gathered and names its click [showAction].
 */
@Composable
internal fun GatheredLine(@StringRes showAction: Int, tag: String, onShow: () -> Unit) {
    val gathered = stringResource(R.string.card_ingredients_gathered)
    val show = stringResource(R.string.card_ingredients_show)
    val showLabel = stringResource(showAction)
    val accent = MaterialTheme.colorScheme.primary
    val line = stringResource(R.string.card_ingredients_folded, gathered, show)
    val shown = remember(line, show, accent) {
        buildAnnotatedString {
            append(line)
            val at = line.lastIndexOf(show)
            if (at >= 0) addStyle(SpanStyle(color = accent), at, at + show.length)
        }
    }
    val showing by rememberUpdatedState(onShow)
    // The text starts where the lines do and reads in the list's own colour, "show" in the accent; the press shows on
    // the theme's small corners rather than a pill across the whole width.
    TextButton(
        onClick = onShow,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = CardTarget)
            .semantics { onClick(label = showLabel) { showing(); true } }
            .testTag(tag),
        shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
        contentPadding = PaddingValues(0.dp),
    ) {
        Text(
            shown,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f).clearAndSetSemantics { text = AnnotatedString(gathered); shownText = line },
        )
    }
}

/** The space between the section's heading and its status line. */
internal val HEADING_GAP = 4.dp

/**
 * The subheading of the group at [index], over its first line (DOM-022): a heading for the screen reader, a modest
 * [GROUP_GAP] above it and nothing below it but its first line's own space. On [CardMode.VIEW] it is set as a
 * cookbook sets it, in small capitals of the working face — the label style in capitals, spaced [GROUP_TRACKING] apart
 * and muted — and read as the name as it was written, not letter by letter.
 */
@Composable
private fun GroupHeading(index: Int, name: String, mode: CardMode) {
    val place = Modifier.fillMaxWidth().padding(top = GROUP_GAP)
    if (mode == CardMode.COOKING) {
        Text(name, style = MaterialTheme.typography.titleSmall, modifier = place.semantics { heading() }.testTag(IngredientTags.group(index)))
    } else {
        Text(
            name.uppercase(),
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = GROUP_TRACKING),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = place.semantics { heading(); contentDescription = name }.testTag(IngredientTags.group(index)),
        )
    }
}

/** The space between the letters of a group's subheading in small capitals on [CardMode.VIEW]. */
internal val GROUP_TRACKING = 0.08.em

/** The space above a group's subheading, apart from the line before it. */
private val GROUP_GAP = 12.dp

/** Whether any quantity of the list was computed at a scale other than the authored one: the section says so once. */
internal val IngredientList.recalculated: Boolean get() = lines.any { it.derived }

/** Test tags of the ingredients section's own parts. */
internal object IngredientTags {
    const val RECALCULATED = "card-recalculated"
    /** The tonal surface the whole section sits on. */
    const val SURFACE = "card-ingredients-surface"
    /** The one line the list folds into once every ingredient is marked done. */
    const val GATHERED = "card-ingredients-gathered"
    /** The status line above the list, always present: as authored, or recalculated. */
    const val STATUS = "card-scale-status"
    /** The status line's text at the authored scale. */
    const val AUTHORED = "card-as-authored"
    /** The arrow after the quantity of the line at [position], where rounding moved it. */
    fun rounding(position: Int) = "card-ingredient-$position-rounding"
    /** The name of the line at [position], with its note after it where it carries one. */
    fun name(position: Int) = "card-ingredient-$position-name"
    /** The quantity of the line at [position], with its arrow where it carries one. */
    fun quantity(position: Int) = "card-ingredient-$position-quantity"
    /** The subheading of the group at [index] of the recipe's headings. */
    fun group(index: Int) = "card-ingredient-group-$index"
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
 *
 * In [CardMode.VIEW] the line answers no tap and offers no fixing: it is its text with [VIEW_LINE_PADDING] above and
 * below, a mark it carries shown, read-only, its state read while the recipe has any mark ([stated]); it is set as a
 * page of a cookbook in the [page] column ([PageLine]).
 */
@Composable
private fun IngredientRow(
    line: IngredientLine,
    position: Int,
    column: QuantityColumn,
    page: PageColumn?,
    place: Modifier,
    done: Boolean,
    onToggle: (() -> Unit)?,
    stated: Boolean,
    mode: CardMode,
    onOpen: () -> Unit,
) {
    val cooking = mode == CardMode.COOKING
    val fixable = cooking && line.fixing != null
    val fixLabel = stringResource(R.string.card_fix_action)
    val opening by rememberUpdatedState(onOpen)
    val marking = if (onToggle != null && cooking) Modifier.markedDone(done, onToggle) else Modifier.shownDone(done, stated)
    val fixing = if (fixable) {
        Modifier.semantics { customActions = listOf(CustomAccessibilityAction(fixLabel) { opening(); true }) }
    } else {
        Modifier
    }
    val tall = cooking && (onToggle != null || fixable)
    val padding = if (cooking) LINE_PADDING else VIEW_LINE_PADDING
    Column(place.fillMaxWidth().then(marking).then(fixing).semantics(mergeDescendants = true) {}.testTag(CardTags.ingredient(position))) {
        val name: @Composable () -> Unit = { LineName(line, position, done) }
        if (page != null) PageLine(line, position, page, done, name) else CookingLine(line, position, column, done, tall, padding, fixable, fixLabel, name) { opening() }
    }
}

/**
 * One ingredient on [CardMode.COOKING]: its quantity, right-aligned in the list's quantity [column] and followed by an
 * arrow where rounding moved it, its [name] beside it, and under the name whether it is fixed; over the amount, where the
 * line is [fixable], its own target ([onOpen]). The line is its text with [padding] above and below, and the card's
 * [CardTarget] tall at least where it is [tall].
 */
@Composable
private fun CookingLine(
    line: IngredientLine,
    position: Int,
    column: QuantityColumn,
    done: Boolean,
    tall: Boolean,
    padding: Dp,
    fixable: Boolean,
    fixLabel: String,
    name: @Composable () -> Unit,
    onOpen: () -> Unit,
) {
    val opening by rememberUpdatedState(onOpen)
    val quantity: @Composable () -> Unit = {
        line.quantity?.let { quantity ->
            Row(Modifier.fillMaxWidth().testTag(IngredientTags.quantity(position))) {
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
    Layout(contents = listOf(name, quantity, fixedMark, amount), modifier = Modifier.fillMaxWidth()) { (names, quantities, fixedMarks, amounts), constraints ->
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
        val shownFixed = fixedMarks.firstOrNull()?.measure(nameConstraints)
        val content = maxOf(shownName.height + (shownFixed?.height ?: 0), shownQuantity?.height ?: 0)
        // A line is its text with a little space above and below it, and never less than the card's target where it
        // answers a tap: one line of text is the target's height exactly, and each line it wraps onto adds no more than itself.
        val height = maxOf(content + 2 * padding.roundToPx(), if (tall) CardTarget.roundToPx() else 0)
        val top = (height - content) / 2
        // It ends where the name begins at the latest, so a tap on the name always marks the line.
        val amountWidth = maxOf(quantityWidth + gap / 2, AMOUNT_TARGET.roundToPx()).coerceAtMost(minOf(width, quantityWidth + gap))
        val shownAmount = amounts.firstOrNull()?.measure(Constraints.fixed(amountWidth, height))
        layout(width, height) {
            // Placed in the order they read: the screen reader takes a line's parts in the order they are placed.
            shownName.place(quantityWidth + gap, top)
            shownQuantity?.place(0, top)
            shownFixed?.place(quantityWidth + gap, top + shownName.height)
            // Last, so that it lies over the line and takes the taps on the amount.
            shownAmount?.place(0, 0)
        }
    }
}

/** The least space above and below a line's text, so that lines that wrap still read apart from the next. */
internal val LINE_PADDING = 8.dp

/** The space above and below a line's text in [CardMode.VIEW]: compact, yet lines that wrap still read apart. */
internal val VIEW_LINE_PADDING = 4.dp

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
private val SURFACE_PADDING = 8.dp

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
    RoundingArrowText(arrow, spoken, tag, Modifier.alignByBaseline().padding(start = ARROW_GAP))
}

/** A rounded quantity's arrow, small and secondary, read as the words [spoken]. */
@Composable
private fun RoundingArrowText(arrow: String, spoken: String, tag: String, modifier: Modifier = Modifier) {
    Text(
        arrow,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.clearAndSetSemantics {
            text = AnnotatedString(spoken)
            shownText = arrow
            testTag = tag
        },
    )
}

/** A line's name, with "to taste" and its note after it ([namedLine]), in the reading face; struck through once [done]. */
@Composable
private fun LineName(line: IngredientLine, position: Int, done: Boolean) {
    val named = line.name ?: stringResource(R.string.card_unnamed_ingredient)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        remember(named, line, muted) { namedLine(AnnotatedString(named), line, muted) },
        style = MaterialTheme.typography.bodyLarge.done(done),
        modifier = Modifier.testTag(IngredientTags.name(position)),
    )
}

/**
 * The quantity column of the list on [CardMode.VIEW], as a page of a cookbook sets it: [shown] where any line carries
 * a quantity; [width], in pixels, [PAGE_COLUMN], a longer quantity wrapping in it between words — or wider, the same on
 * every line, only where one word of a quantity would not fit it even alone ([pageColumnWidth]); [above] at large text
 * ([isLargeText]), where every quantity of the list stands above its name instead of beside it.
 */
internal data class PageColumn(val shown: Boolean, val width: Int, val above: Boolean)

@Composable
private fun pageColumn(lines: List<IngredientLine>): PageColumn {
    val measurer = rememberTextMeasurer()
    val quantityStyle = pageQuantityStyle()
    val arrowStyle = MaterialTheme.typography.labelMedium
    val density = LocalDensity.current
    val large = isLargeText()
    return remember(lines, quantityStyle, arrowStyle, density, large) {
        val shown = lines.any { it.quantity != null }
        PageColumn(shown, pageColumnWidth(lines, measurer, quantityStyle, arrowStyle, density), above = shown && large)
    }
}

/**
 * The width of the quantity column, in pixels: [PAGE_COLUMN], unless a word of a quantity ([unitsKeptWhole]), with a
 * pixel's slack as in the cooking column, is wider; or, on a line with an arrow, unless the arrow and the quantity's
 * first word together are, as they stand on its first line.
 */
private fun pageColumnWidth(lines: List<IngredientLine>, measurer: TextMeasurer, style: TextStyle, arrowStyle: TextStyle, density: Density): Int {
    fun width(text: String, of: TextStyle) = measurer.measure(text, of, softWrap = false, maxLines = 1).size.width
    val needed = lines.maxOfOrNull { line ->
        val words = unitsKeptWhole(line.quantity ?: return@maxOfOrNull 0).split(' ').map { width(it, style) + 1 }
        val arrow = roundingMark(line.rounding)?.takeIf { line.derived }?.first
        maxOf(words.max(), arrow?.let { arrowRoom(it, measurer, arrowStyle, density) + words.first() } ?: 0)
    } ?: 0
    return maxOf(with(density) { PAGE_COLUMN.roundToPx() }, needed)
}

/** The room an [arrow] takes before a quantity: its width and [PAGE_ARROW_GAP], in pixels. */
private fun arrowRoom(arrow: String, measurer: TextMeasurer, arrowStyle: TextStyle, density: Density): Int =
    measurer.measure(arrow, arrowStyle, softWrap = false, maxLines = 1).size.width + with(density) { PAGE_ARROW_GAP.roundToPx() }

/** Every unit label of more than one word, in either interface language, longest first («жидк. унц.», «ст. л.», "fl oz"). */
private val SPACED_UNIT_LABELS: List<String> = QuantityUnit.entries
    .flatMap { unit -> InterfaceLanguage.entries.map { UnitLabels.of(unit, it) } }
    .filter { ' ' in it }
    .distinct()
    .sortedByDescending { it.length }

/**
 * [quantity] with the words of each unit label in it joined by no-break spaces, so that a quantity wrapping in the
 * column breaks between its number and its unit, never inside the unit («1½» / «ст. л.», not «1½ ст.» / «л.»).
 */
internal fun unitsKeptWhole(quantity: String): String =
    SPACED_UNIT_LABELS.fold(quantity) { text, label -> text.replace(label, label.replace(' ', NO_BREAK_SPACE)) }

private const val NO_BREAK_SPACE = '\u00A0'

/**
 * A quantity on [CardMode.VIEW]: the body line's size in the working face — the theme's label face, Golos Text Medium
 * with tabular figures — so that amounts read apart from the names in the reading face and their figures line up.
 */
@Composable
internal fun pageQuantityStyle(): TextStyle = MaterialTheme.typography.let {
    it.bodyLarge.copy(fontFamily = it.labelLarge.fontFamily, fontWeight = it.labelLarge.fontWeight, fontFeatureSettings = it.labelLarge.fontFeatureSettings)
}

/**
 * One ingredient on [CardMode.VIEW]: its quantity right-aligned in the list's column ([PageColumn.width]) at the line's
 * start, wrapping in it between words where it is longer, its name [QUANTITY_GAP] beside it and the fixed mark under
 * the name; or, at large text, where the column stands above the names
 * ([PageColumn.above]), the quantity on a line of its own over the name, both at the line's start and given the whole
 * width. The line is its text with [VIEW_LINE_PADDING] above and below. Read as on Cooking: the name, then the quantity.
 */
@Composable
private fun PageLine(line: IngredientLine, position: Int, page: PageColumn, done: Boolean, name: @Composable () -> Unit) {
    val quantity: @Composable () -> Unit = { line.quantity?.let { PageQuantity(it, line, position, done, column = page.width.takeUnless { page.above }) } }
    val fixedMark: @Composable () -> Unit = { if (line.fixed) FixedMark(stringResource(R.string.card_quantity_fixed), done) }
    Layout(contents = listOf(name, quantity, fixedMark), modifier = Modifier.fillMaxWidth()) { (names, quantities, fixedMarks), constraints ->
        val width = constraints.maxWidth
        val padding = VIEW_LINE_PADDING.roundToPx()
        if (page.above) {
            val whole = Constraints(maxWidth = width)
            val shownQuantity = quantities.firstOrNull()?.measure(whole)
            val shownName = names.first().measure(whole)
            val shownFixed = fixedMarks.firstOrNull()?.measure(whole)
            val over = shownQuantity?.height ?: 0
            val height = over + shownName.height + (shownFixed?.height ?: 0) + 2 * padding
            layout(width, height) {
                // Placed in the order they read: the name, then the quantity.
                shownName.place(0, padding + over)
                shownQuantity?.place(0, padding)
                shownFixed?.place(0, padding + over + shownName.height)
            }
        } else {
            val column = if (page.shown) page.width.coerceAtMost(width) else 0
            val start = if (page.shown) (column + QUANTITY_GAP.roundToPx()).coerceAtMost(width) else 0
            val shownQuantity = quantities.firstOrNull()?.measure(Constraints.fixedWidth(column))
            val nameConstraints = Constraints(maxWidth = width - start)
            val shownName = names.first().measure(nameConstraints)
            val shownFixed = fixedMarks.firstOrNull()?.measure(nameConstraints)
            // The quantity sits on the name's first baseline, as the two read along one line.
            val nameBaseline = shownName[FirstBaseline]
            val quantityBaseline = shownQuantity?.get(FirstBaseline) ?: 0
            val quantityTop = (nameBaseline - quantityBaseline).coerceAtLeast(0)
            val nameTop = (quantityBaseline - nameBaseline).coerceAtLeast(0)
            val content = maxOf(nameTop + shownName.height + (shownFixed?.height ?: 0), quantityTop + (shownQuantity?.height ?: 0))
            layout(width, content + 2 * padding) {
                shownName.place(start, padding + nameTop)
                shownQuantity?.place(0, padding + quantityTop)
                shownFixed?.place(start, padding + nameTop + shownName.height)
            }
        }
    }
}

/**
 * A quantity on [CardMode.VIEW] in the [pageQuantityStyle], its units kept whole ([unitsKeptWhole]), with the arrow
 * rounding gave it ([roundingMark]) [PAGE_ARROW_GAP] before its first line, on that line's baseline, however it wraps;
 * read as the quantity as written, then the words of its arrow. In a [column] the quantity is set against its end,
 * each line ending at the column's edge ([inColumn]). Without a column, the two are as wide as they are, at the start.
 */
@Composable
private fun PageQuantity(quantity: String, line: IngredientLine, position: Int, done: Boolean, column: Int?) {
    val mark = roundingMark(line.rounding)?.takeIf { line.derived }
    val arrowStyle = MaterialTheme.typography.labelMedium
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val base = pageQuantityStyle().done(done)
    val style = if (column == null) base else base.copy(textAlign = TextAlign.End)
    val set = remember(quantity, mark, style, arrowStyle, column, density) {
        val kept = unitsKeptWhole(quantity)
        if (column == null) InColumn(kept, firstLine = 0) else {
            val before = mark?.let { arrowRoom(it.first, measurer, arrowStyle, density) } ?: 0
            inColumn(kept, before, column, measurer, style)
        }
    }
    // The text as laid out, offered to the semantics tree, since its own semantics give way to the quantity as written.
    val laidOut = remember { arrayOfNulls<TextLayoutResult>(1) }
    val arrow: @Composable () -> Unit = { mark?.let { (sign, spoken) -> RoundingArrowText(sign, stringResource(spoken), IngredientTags.rounding(position)) } }
    val amount: @Composable () -> Unit = {
        Text(
            set.text,
            style = style,
            onTextLayout = { laidOut[0] = it },
            modifier = Modifier.clearAndSetSemantics {
                text = AnnotatedString(quantity)
                shownText = set.text
                getTextLayoutResult { results -> laidOut[0]?.let { results.add(it); true } ?: false }
            },
        )
    }
    Layout(contents = listOf(amount, arrow), modifier = Modifier.testTag(IngredientTags.quantity(position))) { (amounts, arrows), constraints ->
        val shownArrow = arrows.firstOrNull()?.measure(Constraints())
        val room = shownArrow?.let { it.width + PAGE_ARROW_GAP.roundToPx() } ?: 0
        val width: Int
        val textStart: Int
        val shownAmount = if (column != null) {
            width = constraints.maxWidth
            textStart = 0
            amounts.single().measure(Constraints.fixedWidth(width))
        } else {
            val textWidth = minOf(amounts.single().maxIntrinsicWidth(Constraints.Infinity), (constraints.maxWidth - room).coerceAtLeast(0))
            width = room + textWidth
            textStart = room
            amounts.single().measure(Constraints.fixedWidth(textWidth))
        }
        val arrowTop = shownArrow?.let { shownAmount[FirstBaseline] - it[FirstBaseline] } ?: 0
        val top = (-arrowTop).coerceAtLeast(0)
        val height = maxOf(top + shownAmount.height, top + arrowTop + (shownArrow?.height ?: 0))
        layout(width, height) {
            // Placed in the order they read: the quantity, then the words of its arrow.
            shownAmount.place(textStart, top)
            shownArrow?.place((textStart + set.firstLine - room).coerceAtLeast(0), top + arrowTop)
        }
    }
}

/** A quantity's [text] as set in the column, and where its first line begins in it, in pixels. */
private data class InColumn(val text: String, val firstLine: Int)

/**
 * [kept] set against the end of a [column], wrapping between words: as it wraps, where its first line leaves an arrow
 * [before] wide room before it; otherwise broken after as many of its words as leave that room, so that the arrow
 * always stands just before the first line. [pageColumnWidth] keeps the first word and the arrow within the column.
 */
private fun inColumn(kept: String, before: Int, column: Int, measurer: TextMeasurer, style: TextStyle): InColumn {
    fun firstLine(text: String) = measurer.measure(text, style, constraints = Constraints.fixedWidth(column)).getLineLeft(0).roundToInt()
    val wrapped = firstLine(kept)
    if (wrapped >= before) return InColumn(kept, wrapped)
    val words = kept.split(' ')
    fun width(text: String) = measurer.measure(text, style, softWrap = false, maxLines = 1).size.width
    val fitting = (1..words.size).lastOrNull { width(words.take(it).joinToString(" ")) <= column - before } ?: 1
    if (fitting == words.size) return InColumn(kept, wrapped)
    val broken = words.take(fitting).joinToString(" ") + "\n" + words.drop(fitting).joinToString(" ")
    return InColumn(broken, firstLine(broken))
}

/** The width of the quantity column on [CardMode.VIEW], the decided design's. */
internal val PAGE_COLUMN = 82.dp

/** The space between a quantity's arrow and the quantity it stands before on [CardMode.VIEW]. */
private val PAGE_ARROW_GAP = 3.dp

/** What a fixed line says under its name. */
@Composable
private fun FixedMark(text: String, done: Boolean) {
    Text(text, style = MaterialTheme.typography.bodySmall.done(done))
}

/**
 * What a [line] reads after its amount, the one wording the card and the editor share (FLOW-023): its [name]; for a row
 * to taste, "to taste" after a dash — "salt — to taste", «соль — по вкусу»; then its note (DOM-023), set apart by a dash
 * and drawn in the [muted] colour, so that how the ingredient is prepared reads apart from what it is. The whole wraps
 * between words as one text.
 */
internal fun namedLine(name: AnnotatedString, line: IngredientLine, muted: Color): AnnotatedString = buildAnnotatedString {
    append(name)
    line.toTaste?.let { append(NOTE_SEPARATOR); append(it) }
    line.note?.let { note -> withStyle(SpanStyle(color = muted)) { append(NOTE_SEPARATOR); append(note) } }
}

/** What stands between a line's name and what follows it: "to taste", its note. */
private const val NOTE_SEPARATOR = " — "

/** The arrow a recalculated quantity carries and the words it is read as, or null where the grid held it exactly. */
internal fun roundingMark(rounding: Rounding): Pair<String, Int>? = when (rounding) {
    Rounding.NONE -> null
    Rounding.UP -> "↑" to R.string.card_quantity_rounded_up
    Rounding.DOWN -> "↓" to R.string.card_quantity_rounded_down
}
