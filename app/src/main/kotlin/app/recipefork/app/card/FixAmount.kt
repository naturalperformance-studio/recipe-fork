package app.recipefork.app.card

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.recipefork.app.R
import app.recipefork.app.editor.problemMessage
import app.recipefork.app.ui.Motion
import app.recipefork.core.recipe.QuantityInput
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.scaling.FixedAmount
import app.recipefork.core.scaling.FixedAmountReading
import app.recipefork.core.scaling.InterfaceLanguage
import app.recipefork.core.scaling.Rational
import app.recipefork.core.scaling.UnitLabels

/**
 * The fixing control of one ingredient while it is [open] under its line: it unfolds as it opens and folds away as it
 * closes, the lines below moving with it. Once unfolded it is brought into sight, above the keyboard and above the
 * [coveredBelow] the card's content runs under ([FixAmount]).
 */
@Composable
internal fun FixAmountUnder(
    open: Boolean,
    offer: FixOffer,
    current: CardFixing?,
    language: InterfaceLanguage,
    onFix: (Rational, QuantityUnit) -> Unit,
    onRelease: () -> Unit,
    onClose: () -> Unit,
    coveredBelow: Dp = 0.dp,
) {
    AnimatedVisibility(open, enter = Motion.open, exit = Motion.close) {
        val unfolding = transition
        Box(Modifier.padding(top = SECTION_GAP)) {
            FixAmount(
                offer, current, language, onFix, onRelease, onClose, coveredBelow,
                unfolded = { unfolding.currentState == EnterExitState.Visible && unfolding.targetState == EnterExitState.Visible },
            )
        }
    }
}

/**
 * The fixing control of one ingredient (SCALE-002, SCALE-018): the amount the reader has, typed as the quantity
 * field reads a number (DOM-019), in one of the units the author's unit reads in without conversion. An amount the
 * control does not hold is answered with what it holds, and fixes nothing; nothing typed changes nothing either —
 * clearing the control is not a release (SCALE-018 (8)). A fixed row offers its release here (SCALE-017).
 *
 * The control opens with its amount field focused and the keyboard up, an amount fixed before selected whole in it, so
 * that what is typed replaces it; what the field does not hold is said under it, and the keyboard's Done applies as
 * Recalculate does. Once [unfolded], it is brought into sight in the scrolling card it stands in, clear of the keyboard
 * and of the [coveredBelow] the card runs under ([inSight]), and again each time the keyboard changes the room the card
 * has: opened under a line low on the card, it would otherwise stand below the screen, and the reader would see the
 * keyboard come up and nothing else.
 */
@Composable
internal fun FixAmount(
    offer: FixOffer,
    current: CardFixing?,
    language: InterfaceLanguage,
    onFix: (Rational, QuantityUnit) -> Unit,
    onRelease: () -> Unit,
    onClose: () -> Unit,
    coveredBelow: Dp = 0.dp,
    unfolded: () -> Boolean = { true },
) {
    var unit by remember { mutableStateOf(current?.unit?.takeIf { it in offer.units } ?: offer.authoredUnit) }
    // An amount fixed before stands in the field selected whole, so that what the reader types takes its place.
    var field by remember {
        val written = current?.let { QuantityInput.write(it.amount, it.unit, language) }.orEmpty()
        mutableStateOf(TextFieldValue(written, TextRange(0, written.length)))
    }
    val reading = FixedAmount.read(field.text, unit, offer.divisible)
    val fix = { (reading as? FixedAmountReading.Accepted)?.let { onFix(it.amount, unit) } }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    // One tap on the line is enough: the amount is ready to type, on the number keyboard.
    LaunchedEffect(focus) {
        focus.requestFocus()
        keyboard?.show()
    }
    val whole = remember { SightOf() }
    val amount = remember { SightOf() }
    val keys = WindowInsets.ime
    val density = LocalDensity.current
    val covered by rememberUpdatedState(with(density) { coveredBelow.toPx() })
    val isUnfolded by rememberUpdatedState(unfolded)
    // Not while it unfolds: brought into sight then, it is taken as it stands in the fold, already in sight.
    LaunchedEffect(whole, amount, keys, density) {
        snapshotFlow { isUnfolded() to keys.getBottom(density) }.collectLatest { (shown, _) ->
            if (shown) {
                withFrameNanos {}
                inSight(whole, amount, covered)
            }
        }
    }
    Column(
        Modifier.fillMaxWidth().then(whole.modifier).padding(vertical = 4.dp).testTag(CardTags.FIX),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(amount.modifier, verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = field,
                onValueChange = { field = it },
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

/** A part of the control that can be brought into sight, as large as it was last laid out. */
private class SightOf {
    val requester = BringIntoViewRequester()
    var size = IntSize.Zero
    val modifier: Modifier = Modifier.bringIntoViewRequester(requester).onSizeChanged { size = it }

    /** Brings the part into sight with [below] pixels more under it, which the card's content runs under. */
    suspend fun bring(below: Float) = requester.bringIntoView(Rect(0f, 0f, size.width.toFloat(), size.height + below))
}

/**
 * Brings the control into sight with [below] pixels clear under it: the [whole] of it where it fits, and in any case its
 * [amount], so that in a short room — a large font with the keyboard up — the field stands in sight, the actions under it
 * a scroll away.
 */
private suspend fun inSight(whole: SightOf, amount: SightOf, below: Float) {
    whole.bring(below)
    amount.bring(below)
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
 * One action of the control: a text button, or a filled one for the [primary], at least the card's [CardTarget] square
 * (A11Y-003, A11Y-007), its colours, shape and type the theme's; announced by what it does (A11Y-005).
 */
@Composable
private fun FixAction(text: Int, action: Int, enabled: Boolean, onClick: () -> Unit, tag: String, primary: Boolean = false) {
    val said = stringResource(action)
    val modifier = Modifier
        .heightIn(min = CardTarget)
        .widthIn(min = CardTarget)
        .semantics { if (enabled) onClick(label = said) { onClick(); true } }
        .testTag(tag)
    val label: @Composable RowScope.() -> Unit = { Text(stringResource(text)) }
    if (primary) {
        Button(onClick = onClick, enabled = enabled, modifier = modifier, content = label)
    } else {
        TextButton(onClick = onClick, enabled = enabled, modifier = modifier, content = label)
    }
}
