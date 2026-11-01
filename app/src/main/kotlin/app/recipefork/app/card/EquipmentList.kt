package app.recipefork.app.card

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.HorizontalAlignmentLine
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import app.recipefork.app.R
import app.recipefork.app.timer.BesideOrUnder
import app.recipefork.app.ui.Motion
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The equipment the recipe needs, above its ingredients and on the same tonal surface, under its heading "Equipment",
 * one item a line in the author's order. With no equipment the part is left out, heading included.
 *
 * On [CardMode.COOKING] each line is the card's [CardTarget] tall and a tap marks it done, another unmarks it, as an
 * ingredient line ([marks], FLOW-068); once every item is marked the list folds into the one line "All gathered · show"
 * ([GatheredLine]), as the ingredients do, and whether the reader has shown it again is [shownAgain], held apart from
 * the ingredients'. An item the reader's kitchen lacks ([ShownEquipment.lacking]) says so under its name, "Not in your
 * kitchen", in words, read with the line; a tap still marks it.
 *
 * On [CardMode.VIEW] each item is a row of the page ([ViewEquipmentLine]): a square before its name, filled while the
 * kitchen has it ([ShownEquipment.had]), left empty with "Not in your kitchen" at the row's end while it lacks it, and
 * none — its place kept, so the names stand aligned — while the app cannot tell (free text, a consumable, a kitchen with
 * nothing ticked); a dotted rule under each row. A row is at least [VIEW_ROW_HEIGHT] tall, its mark shown but a tap leaving it as it is, and the list never
 * folds. A lacking item's row — and only such a row — is a button that opens My kitchen at the entry it names
 * ([onOpenKitchen]), at least the card's [CardTarget] tall.
 *
 * While My kitchen has nothing ticked — so no item is marked as lacking — View offers under the items a small text
 * button, "Set up My kitchen" ([onSetUpKitchen]), the card's [CardTarget] tall.
 */
@Composable
internal fun EquipmentList(
    equipment: List<ShownEquipment>,
    marks: CardMarks?,
    mode: CardMode,
    shownAgain: MutableState<Boolean> = remember { mutableStateOf(false) },
    onOpenKitchen: ((Long) -> Unit)? = null,
    onSetUpKitchen: (() -> Unit)? = null,
) {
    if (equipment.isEmpty()) return
    val gathered = marks != null && equipment.indices.all { marks.done(MarkedPart.EQUIPMENT, it) }
    val folded = foldedOnCooking(gathered, shownAgain, mode)
    GatheringSurface(EquipmentTags.SURFACE) {
        Section(R.string.card_equipment, CardTags.EQUIPMENT, gap = HEADING_GAP) {
            AnimatedVisibility(folded, enter = Motion.open, exit = Motion.close) {
                GatheredLine(R.string.card_equipment_show_action, EquipmentTags.GATHERED) { shownAgain.value = true }
            }
            AnimatedVisibility(!folded, enter = Motion.open, exit = Motion.close) {
                Column {
                    equipment.forEachIndexed { index, item ->
                        val done = marks?.done(MarkedPart.EQUIPMENT, index) == true
                        if (mode == CardMode.VIEW) {
                            val lacked = item.lackedEntry
                            ViewEquipmentLine(
                                item.text, item.inKitchen, index, done,
                                onOpenKitchen = if (lacked != null) onOpenKitchen?.let { open -> { open(lacked) } } else null,
                                stated = marks?.any == true,
                            )
                        } else {
                            EquipmentLine(
                                item.text, item.lacking, index, done,
                                onToggle = marks?.let { { it.toggle(MarkedPart.EQUIPMENT, index) } },
                                stated = marks?.any == true,
                            )
                        }
                    }
                    if (mode == CardMode.VIEW && onSetUpKitchen != null) {
                        TextButton(
                            onClick = onSetUpKitchen,
                            modifier = Modifier.heightIn(min = CardTarget).testTag(EquipmentTags.SET_UP_KITCHEN),
                        ) { Text(stringResource(R.string.card_equipment_set_up_kitchen)) }
                    }
                }
            }
        }
    }
}

/**
 * An item of equipment as the card shows it: the [text] it reads as; when the reader's kitchen lacks it, the equipment
 * dictionary entry it names, which My kitchen opens at; and whether the kitchen is known to have it ([had]). Neither —
 * free text, a consumable, a kitchen with nothing ticked — the app cannot tell.
 */
@Immutable
internal data class ShownEquipment(val text: String, val lackedEntry: Long? = null, val had: Boolean = false) {
    val lacking: Boolean get() = lackedEntry != null

    /** What the card can say of the item and the reader's kitchen. */
    val inKitchen: InKitchen get() = when {
        lacking -> InKitchen.LACKS
        had -> InKitchen.HAS
        else -> InKitchen.UNKNOWN
    }
}

/** Whether the reader's kitchen has an item of equipment, lacks it, or the app cannot tell. */
internal enum class InKitchen { HAS, LACKS, UNKNOWN }

/**
 * One item of equipment on Cooking: its name, wrapping, never cut, and — when the kitchen is [lacking] it — "Not in
 * your kitchen" under it, muted, part of the line the screen reader reads. With [onToggle] a tap marks it [done] or
 * unmarks it — a check box to the screen reader — the line is the card's [CardTarget] tall, and it starts with its done
 * mark ([DoneCircle]), an empty circle ticked once done; without (no marks to keep), its mark is only shown, read-only,
 * its state read while the recipe has any mark ([stated]). A line marked done is struck through and muted, and read as
 * done — three signs with its mark, never colour alone; the kitchen's mark stays as it is.
 */
@Composable
private fun EquipmentLine(
    item: String,
    lacking: Boolean,
    index: Int,
    done: Boolean,
    onToggle: (() -> Unit)?,
    stated: Boolean,
) {
    val marking = if (onToggle != null) Modifier.markedDone(done, onToggle) else Modifier.shownDone(done, stated)
    Box(
        cardPlace(CardPlaces.equipment(index))
            .fillMaxWidth()
            .then(marking)
            .semantics(mergeDescendants = true) {}
            .heightIn(min = if (onToggle != null) CardTarget else 0.dp)
            .padding(vertical = if (onToggle != null) LINE_PADDING else VIEW_LINE_PADDING)
            .testTag(CardTags.equipment(index)),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DONE_MARK_GAP),
        ) {
            if (onToggle != null) DoneCircle(done, EquipmentTags.mark(index))
            Column(Modifier.weight(1f)) {
                Text(item, style = MaterialTheme.typography.bodyLarge.done(done))
                if (lacking) NotInKitchen(index, MaterialTheme.typography.bodyMedium, chevron = false)
            }
        }
    }
}

/**
 * One item of equipment on View, as a row of the page (redesign-1 § 4.2): a [KitchenSquare] — filled while the kitchen
 * [has][InKitchen.HAS] the item, empty while it [lacks][InKitchen.LACKS] it, none but its place while the app cannot
 * tell ([InKitchen.UNKNOWN]; the owner, 2026-10-29) — then the name in the reading face, wrapping, never cut, the square level with its first line (F-2), and for a
 * lacking item "Not in your kitchen" at the row's end, beside the name where both fit, else under it. Shape and words,
 * never colour alone: the screen reader reads the line as its name and, for an item the kitchen has, "In your kitchen"
 * as its state; a lacking item's words are read with the line; of an item it cannot tell, nothing of the kitchen. While the recipe has any mark ([stated]) the
 * state says "Done" or "Not done" too, and a line marked [done] is struck through and muted; a tap never changes it.
 *
 * With [onOpenKitchen] — a lacking item — the row is a button "Open My kitchen" at least the card's [CardTarget] tall,
 * a chevron after the words hinting at it; otherwise it answers no tap and is at least [VIEW_ROW_HEIGHT] tall. A dotted
 * rule in the theme's outline variant runs under the row.
 */
@Composable
private fun ViewEquipmentLine(
    item: String,
    inKitchen: InKitchen,
    index: Int,
    done: Boolean,
    onOpenKitchen: (() -> Unit)?,
    stated: Boolean,
) {
    val lacking = inKitchen == InKitchen.LACKS
    val opening = stringResource(R.string.card_equipment_open_kitchen_action)
    val had = stringResource(R.string.card_equipment_in_kitchen).takeIf { inKitchen == InKitchen.HAS }
    val marked = if (stated) stringResource(if (done) R.string.card_marked_done else R.string.card_marked_not_done) else null
    val state = listOfNotNull(had, marked).joinToString(STATE_SEPARATOR)
    val opens = onOpenKitchen?.let { Modifier.clickable(onClickLabel = opening, role = Role.Button, onClick = it) } ?: Modifier
    // The square and the name stand centred in the row together; within them the square stands level with the name's
    // first line, centred on its height, however many lines the name wraps to (the owner, F-2, 2026-10-29).
    Box(
        cardPlace(CardPlaces.equipment(index))
            .fillMaxWidth()
            .dottedRule()
            .then(opens)
            .semantics(mergeDescendants = true) { if (state.isNotEmpty()) stateDescription = state }
            .heightIn(min = if (onOpenKitchen != null) CardTarget else VIEW_ROW_HEIGHT)
            .padding(vertical = VIEW_LINE_PADDING)
            .testTag(CardTags.equipment(index)),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(SQUARE_GAP)) {
            if (inKitchen == InKitchen.UNKNOWN) {
                Spacer(Modifier.width(SQUARE_SIZE))
            } else {
                KitchenSquare(
                    had = inKitchen == InKitchen.HAS,
                    Modifier.alignBy { it.measuredHeight / 2 }.testTag(EquipmentTags.square(index)),
                )
            }
            BesideOrUnder(
                Modifier.weight(1f).alignBy(FirstLineMiddle),
                main = { FirstLineMarked(item, MaterialTheme.typography.bodyLarge.done(done)) },
                trailing = if (lacking) ({ NotInKitchen(index, MaterialTheme.typography.labelLarge, chevron = onOpenKitchen != null) }) else null,
            )
        }
    }
}

/** The middle of a text's first line, from its top: what a row's square stands level with. */
private val FirstLineMiddle = HorizontalAlignmentLine(::min)

/**
 * [text] in [style], wrapping, never cut, giving its parents the middle of its first line as [FirstLineMiddle]. The
 * text's layout is told during its measure, so the line is known by the time the text is placed.
 */
@Composable
private fun FirstLineMarked(text: String, style: TextStyle) {
    val middle = remember { IntArray(1) }
    Text(
        text,
        style = style,
        onTextLayout = { laid ->
            middle[0] = if (laid.lineCount > 0) ((laid.getLineTop(0) + laid.getLineBottom(0)) / 2).roundToInt() else laid.size.height / 2
        },
        modifier = Modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            layout(placeable.width, placeable.height, mapOf(FirstLineMiddle to middle[0])) { placeable.place(0, 0) }
        },
    )
}

/**
 * "Not in your kitchen", muted, in [style] — the reading face's bodyMedium on Cooking, as before the redesign, the
 * working face's labelLarge on View; with [chevron], a chevron after it hinting that the row opens My kitchen.
 */
@Composable
private fun NotInKitchen(index: Int, style: TextStyle, chevron: Boolean) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CHEVRON_GAP)) {
        Text(
            stringResource(R.string.card_equipment_not_in_kitchen),
            style = style,
            color = color,
            modifier = Modifier.testTag(EquipmentTags.lacking(index)),
        )
        if (chevron) {
            Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = color, modifier = Modifier.size(CHEVRON_SIZE))
        }
    }
}

/**
 * The square before an item on View: [SQUARE_SIZE] with slightly rounded corners in the theme's muted ink, filled when
 * the kitchen has the item ([had]), only its outline [SQUARE_STROKE] wide when it lacks it. Drawn, not read: the line
 * says the same in words.
 */
@Composable
private fun KitchenSquare(had: Boolean, modifier: Modifier) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier.size(SQUARE_SIZE)) {
        val corner = CornerRadius(SQUARE_CORNER.toPx())
        if (had) {
            drawRoundRect(color, cornerRadius = corner)
        } else {
            val stroke = SQUARE_STROKE.toPx()
            drawRoundRect(
                color,
                topLeft = Offset(stroke / 2, stroke / 2),
                size = Size(size.width - stroke, size.height - stroke),
                cornerRadius = corner,
                style = Stroke(stroke),
            )
        }
    }
}

/** A dotted rule [RULE_WIDTH] thick in the theme's outline variant along the bottom edge of what it modifies. */
@Composable
private fun Modifier.dottedRule(): Modifier {
    val color = MaterialTheme.colorScheme.outlineVariant
    return drawBehind {
        val width = RULE_WIDTH.toPx()
        val y = size.height - width / 2f
        val dot = RULE_DOT.toPx()
        drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = width, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dot, dot)))
    }
}

/** The least height of an item's row on View that answers no tap (redesign-1 § 4.2). */
internal val VIEW_ROW_HEIGHT = 48.dp

/** The square before an item on View: its side, its outline when empty, its corners. */
internal val SQUARE_SIZE = 14.dp
private val SQUARE_STROKE = 1.5.dp
private val SQUARE_CORNER = 2.dp

/** The space between the square and the name. */
internal val SQUARE_GAP = 12.dp

/** The chevron after "Not in your kitchen" on View, and the space before it. */
private val CHEVRON_SIZE = 18.dp
private val CHEVRON_GAP = 6.dp

/** The dotted rule under an item's row on View: its thickness, and the length of each dot and each gap. */
private val RULE_WIDTH = 1.dp
private val RULE_DOT = 1.dp

/** Between the kitchen's state and the done mark's, as the screen reader reads an item's state on View. */
private const val STATE_SEPARATOR = ", "

/** Test tags of the equipment section's own parts. */
internal object EquipmentTags {
    /** The tonal surface the whole section sits on. */
    const val SURFACE = "card-equipment-surface"
    /** The one line the list folds into once every item is marked done. */
    const val GATHERED = "card-equipment-gathered"
    /** Under the items on View while My kitchen has nothing ticked: the way to set it up. */
    const val SET_UP_KITCHEN = "card-equipment-set-up-kitchen"

    /** The "Not in your kitchen" of the item at [index]. */
    fun lacking(index: Int) = "card-equipment-lacking-$index"

    /** The done mark before the item at [index] on Cooking. */
    fun mark(index: Int) = "card-equipment-mark-$index"

    /** The square before the item at [index] on View; none while the app cannot tell whether the kitchen has it. */
    fun square(index: Int) = "card-equipment-square-$index"
}
