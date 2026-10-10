package app.recipefork.app.card

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.recipefork.app.R
import app.recipefork.app.ui.Motion

/**
 * The equipment the recipe needs, above its ingredients and on the same tonal surface, under its heading "Equipment",
 * one item a line in the author's order. With no equipment the part is left out, heading included.
 *
 * On [CardMode.COOKING] each line is the card's [CardTarget] tall and a tap marks it done, another unmarks it, as an
 * ingredient line ([marks], FLOW-068); once every item is marked the list folds into the one line "All gathered · show"
 * ([GatheredLine]), as the ingredients do, and whether the reader has shown it again is [shownAgain], held apart from
 * the ingredients'. On [CardMode.VIEW] a line is as tall as its text with a modest space around it, its mark shown but
 * a tap leaving it as it is, and the list never folds.
 *
 * On both tabs an item the reader's kitchen lacks ([ShownEquipment.lacking]) says so under its name, "Not in your
 * kitchen", in words, read with the line. On View such a line — and only such a line — is a button that opens My
 * kitchen at the entry it names ([onOpenKitchen]), at least the card's [CardTarget] tall; on Cooking a tap still marks it.
 */
@Composable
internal fun EquipmentList(
    equipment: List<ShownEquipment>,
    marks: CardMarks?,
    mode: CardMode,
    shownAgain: MutableState<Boolean> = remember { mutableStateOf(false) },
    onOpenKitchen: ((Long) -> Unit)? = null,
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
                        val lacked = item.lackedEntry
                        EquipmentLine(
                            item.text, item.lacking, index,
                            done = marks?.done(MarkedPart.EQUIPMENT, index) == true,
                            onToggle = marks?.takeIf { mode == CardMode.COOKING }?.let { { it.toggle(MarkedPart.EQUIPMENT, index) } },
                            onOpenKitchen = if (mode == CardMode.VIEW && lacked != null) onOpenKitchen?.let { open -> { open(lacked) } } else null,
                            stated = marks?.any == true,
                        )
                    }
                }
            }
        }
    }
}

/**
 * An item of equipment as the card shows it: the [text] it reads as, and — when the reader's kitchen lacks it — the
 * equipment dictionary entry it names, which My kitchen opens at.
 */
@Immutable
internal data class ShownEquipment(val text: String, val lackedEntry: Long? = null) {
    val lacking: Boolean get() = lackedEntry != null
}

/**
 * One item of equipment: its name, wrapping, never cut, and — when the kitchen is [lacking] it — "Not in your kitchen"
 * under it, muted, part of the line the screen reader reads. With [onToggle] — on Cooking — a tap marks it [done] or
 * unmarks it — a check box to the screen reader — and the line is the card's [CardTarget] tall; without, its mark is
 * only shown, read-only, its state read while the recipe has any mark ([stated]), and the line is its text
 * with [VIEW_LINE_PADDING] above and below. A line marked done is struck through and muted, and read as done; the
 * kitchen's mark stays as it is. With [onOpenKitchen] — on View, an item the kitchen lacks — a tap opens My kitchen,
 * the line a button "Open My kitchen" the card's [CardTarget] tall at least.
 */
@Composable
private fun EquipmentLine(
    item: String,
    lacking: Boolean,
    index: Int,
    done: Boolean,
    onToggle: (() -> Unit)?,
    onOpenKitchen: (() -> Unit)?,
    stated: Boolean,
) {
    val opening = stringResource(R.string.card_equipment_open_kitchen_action)
    val marking = when {
        onToggle != null -> Modifier.markedDone(done, onToggle)
        onOpenKitchen != null -> Modifier.shownDone(done, stated).clickable(onClickLabel = opening, role = Role.Button, onClick = onOpenKitchen)
        else -> Modifier.shownDone(done, stated)
    }
    Box(
        cardPlace(CardPlaces.equipment(index))
            .fillMaxWidth()
            .then(marking)
            .semantics(mergeDescendants = true) {}
            .heightIn(min = if (onToggle != null || onOpenKitchen != null) CardTarget else 0.dp)
            .padding(vertical = if (onToggle != null) LINE_PADDING else VIEW_LINE_PADDING)
            .testTag(CardTags.equipment(index)),
        contentAlignment = Alignment.CenterStart,
    ) {
        Column {
            Text(item, style = MaterialTheme.typography.bodyLarge.done(done))
            if (lacking) {
                Text(
                    stringResource(R.string.card_equipment_not_in_kitchen),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag(EquipmentTags.lacking(index)),
                )
            }
        }
    }
}

/** Test tags of the equipment section's own parts. */
internal object EquipmentTags {
    /** The tonal surface the whole section sits on. */
    const val SURFACE = "card-equipment-surface"
    /** The one line the list folds into once every item is marked done. */
    const val GATHERED = "card-equipment-gathered"

    /** The "Not in your kitchen" under the item at [index]. */
    fun lacking(index: Int) = "card-equipment-lacking-$index"
}
