package app.recipefork.app.card

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
 *
 * The control opens with its amount field focused and the keyboard up; what the field does not hold is said under
 * it, and the keyboard's Done applies as Recalculate does.
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
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    // One tap on the line is enough: the amount is ready to type, on the number keyboard.
    LaunchedEffect(focus) {
        focus.requestFocus()
        keyboard?.show()
    }
    Column(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag(CardTags.FIX),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.card_fix_amount)) },
                isError = reading is FixedAmountReading.Refused,
                supportingText = (reading as? FixedAmountReading.Refused)?.let { refused ->
                    {
                        Text(
                            stringResource(problemMessage(refused.problem)),
                            modifier = Modifier
                                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
                                .testTag(CardTags.FIX_PROBLEM),
                        )
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { fix() }),
                modifier = Modifier.weight(3f).heightIn(min = CardTarget).focusRequester(focus).testTag(CardTags.FIX_FIELD),
            )
            if (offer.units.size == 1) {
                Text(
                    UnitLabels.of(unit, language),
                    style = MaterialTheme.typography.bodyLarge,
                    // Level with the field's box, below the room its label takes on the outline.
                    modifier = Modifier.padding(top = 8.dp).heightIn(min = CardTarget).wrapContentHeight(),
                )
            } else {
                UnitMenu(offer.units, unit, language, { unit = it }, Modifier.weight(2f))
            }
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
 * The unit the amount is typed in, chosen from the [units] fixing allows (SCALE-018) in a standard exposed drop-down,
 * each choice at least the card's [CardTarget] high.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnitMenu(
    units: List<QuantityUnit>,
    unit: QuantityUnit,
    language: InterfaceLanguage,
    onPick: (QuantityUnit) -> Unit,
    modifier: Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = UnitLabels.of(unit, language),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(stringResource(R.string.card_fix_unit)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
                .heightIn(min = CardTarget)
                .testTag(FixTags.UNIT),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            units.forEach { choice ->
                DropdownMenuItem(
                    text = { Text(UnitLabels.of(choice, language), style = MaterialTheme.typography.bodyLarge) },
                    onClick = {
                        onPick(choice)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                    modifier = Modifier.heightIn(min = CardTarget).testTag(CardTags.fixUnit(choice)),
                )
            }
        }
    }
}

/** Test tags of the fixing control's own parts. */
internal object FixTags {
    /** The unit drop-down, where fixing offers more than one unit; its choices are tagged [CardTags.fixUnit]. */
    const val UNIT = "card-fix-unit"
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
