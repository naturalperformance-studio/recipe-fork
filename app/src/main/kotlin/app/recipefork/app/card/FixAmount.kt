package app.recipefork.app.card

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.recipefork.app.R
import app.recipefork.app.editor.problemMessage
import app.recipefork.core.recipe.QuantityInput
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.scaling.FixedAmount
import app.recipefork.core.scaling.FixedAmountReading
import app.recipefork.core.scaling.InterfaceLanguage
import app.recipefork.core.scaling.Rational
import app.recipefork.core.scaling.UnitLabels

/**
 * The fixing control of one ingredient (SCALE-002, SCALE-018): the amount the reader has, typed as the quantity
 * field reads a number (DOM-019), in one of the units the author's unit reads in without conversion. An amount the
 * control does not hold is answered with what it holds, and fixes nothing; nothing typed changes nothing either —
 * clearing the control is not a release (SCALE-018 (8)). A fixed row offers its release here (SCALE-017).
 */
@Composable
internal fun FixAmount(
    offer: FixOffer,
    current: CardFixing?,
    language: InterfaceLanguage,
    onFix: (Rational, QuantityUnit) -> Unit,
    onRelease: () -> Unit,
    onClose: () -> Unit,
) {
    var unit by remember { mutableStateOf(current?.unit?.takeIf { it in offer.units } ?: offer.authoredUnit) }
    var text by remember { mutableStateOf(current?.let { QuantityInput.write(it.amount, it.unit, language) }.orEmpty()) }
    val reading = FixedAmount.read(text, unit, offer.divisible)
    val fix = { (reading as? FixedAmountReading.Accepted)?.let { onFix(it.amount, unit) } }
    Column(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag(CardTags.FIX),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.card_fix_amount)) },
                isError = reading is FixedAmountReading.Refused,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { fix() }),
                modifier = Modifier.weight(1f).heightIn(min = CardTarget).testTag(CardTags.FIX_FIELD),
            )
            if (offer.units.size == 1) Text(UnitLabels.of(unit, language), style = MaterialTheme.typography.bodyLarge)
        }
        if (offer.units.size > 1) {
            Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                offer.units.forEach { choice ->
                    val selected = choice == unit
                    Box(
                        Modifier
                            .heightIn(min = CardTarget)
                            .widthIn(min = CardTarget)
                            .border(
                                if (selected) 2.dp else 1.dp,
                                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                MaterialTheme.shapes.small,
                            )
                            .selectable(selected = selected, role = Role.RadioButton, onClick = { unit = choice })
                            .padding(horizontal = 12.dp)
                            .testTag(CardTags.fixUnit(choice)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(UnitLabels.of(choice, language), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
        (reading as? FixedAmountReading.Refused)?.let {
            Text(
                stringResource(problemMessage(it.problem)),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag(CardTags.FIX_PROBLEM),
            )
        }
        // Release sits on its own line, apart from closing and recalculating; those two wrap as whole buttons when
        // their labels do not fit side by side (a large font, a narrow screen), the primary one always last.
        if (current != null) {
            FixAction(R.string.card_fix_release, R.string.card_fix_release_action, true, onRelease, CardTags.FIX_RELEASE)
        }
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FixAction(R.string.card_fix_cancel, R.string.card_fix_cancel_action, true, onClose, CardTags.FIX_CANCEL)
            FixAction(
                R.string.card_fix_confirm,
                R.string.card_fix_confirm_action,
                reading is FixedAmountReading.Accepted,
                { fix() },
                CardTags.FIX_CONFIRM,
                primary = true,
            )
        }
    }
}

/**
 * One action of the control, at least the card's [CardTarget] square (A11Y-003, A11Y-007), announced by what it does (A11Y-005); the [primary]
 * one is filled.
 */
@Composable
private fun FixAction(text: Int, action: Int, enabled: Boolean, onClick: () -> Unit, tag: String, primary: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    val fill = if (primary) {
        Modifier.background(if (enabled) colors.primary else colors.onSurface.copy(alpha = 0.12f), MaterialTheme.shapes.small)
    } else {
        Modifier
    }
    val color = when {
        !enabled -> colors.onSurface.copy(alpha = 0.38f)
        primary -> colors.onPrimary
        else -> colors.primary
    }
    Box(
        Modifier
            .heightIn(min = CardTarget)
            .widthIn(min = CardTarget)
            .then(fill)
            .clip(MaterialTheme.shapes.small)
            .clickable(enabled = enabled, onClickLabel = stringResource(action), role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp)
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Text(stringResource(text), color = color, style = MaterialTheme.typography.labelLarge)
    }
}
