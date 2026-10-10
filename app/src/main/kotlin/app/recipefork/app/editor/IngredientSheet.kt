package app.recipefork.app.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.recipefork.app.Mode
import app.recipefork.app.R
import app.recipefork.app.card.ingredientLine
import app.recipefork.app.card.scalableQuantity
import app.recipefork.app.card.withNote
import app.recipefork.app.dictionary.IngredientEntryEntity
import app.recipefork.app.dictionary.IngredientOffer
import app.recipefork.app.dictionary.label
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.QuantityInput
import app.recipefork.core.recipe.QuantityProblem
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.RecipePart
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.recipe.effectiveDivisible
import app.recipefork.core.recipe.withTypedQuantity
import app.recipefork.core.scaling.InterfaceLanguage
import kotlinx.coroutines.launch

/**
 * What the author typed into the sheet's note and quantity fields, as typed, while it reads as what the row holds. It
 * is kept by the list that hosts the sheet, under the row's key, so it comes back with the sheet when the activity is
 * recreated: what a sheet's window composes itself is not restored.
 */
internal class TypedInSheet(val note: MutableState<String?>, val quantity: MutableState<String?>)

/** A [TypedInSheet] that outlives the activity being recreated, under the key the composition is in. */
@Composable
internal fun rememberTypedInSheet(): TypedInSheet {
    val note = rememberSaveable { mutableStateOf<String?>(null) }
    val quantity = rememberSaveable { mutableStateOf<String?>(null) }
    return remember(note, quantity) { TypedInSheet(note, quantity) }
}

/** The test tags of an ingredient row read as a line, and of the sheet it opens in. */
object IngredientSheetTags {
    const val SHEET = "editor-ingredient-sheet"
    const val DONE = "editor-ingredient-sheet-done"

    /** The row's line: what it holds, written as the card writes it; the row's click target. */
    fun line(position: Int) = "editor-ingredient-$position-line"

    /** The row's mark of a name in the author's own words (FLOW-022). */
    fun freeText(position: Int) = "editor-ingredient-$position-line-free-text"

    /** The row's mark of a quantity its field does not hold (DOM-017 (6)). */
    fun problem(position: Int) = "editor-ingredient-$position-line-problem"

    /** The row's mark of a note over its limit (FLOW-028). */
    fun noteMark(position: Int) = "editor-ingredient-$position-line-note-mark"
}

/**
 * What an ingredient row reads as in the editor: the line the card writes for it (FLOW-023) — its amount and unit, then
 * its name with the qualifier and, after a dash and muted, its note — so the editor and the card cannot read it apart.
 * Where the card computes no amount, the row shows what its quantity field holds before the unit, so what was typed
 * stays in sight; a row with no name reads as "Unnamed ingredient", and one holding nothing at all as "New ingredient".
 */
@Composable
internal fun ingredientLineText(row: Ingredient, dictionary: List<IngredientEntryEntity>?, language: InterfaceLanguage): AnnotatedString {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val unnamed = stringResource(R.string.editor_ingredient_unnamed)
    val fresh = stringResource(R.string.editor_ingredient_new)
    val entries = remember(dictionary) { dictionary?.associateBy { it.id }.orEmpty() }
    val line = ingredientLine(row, entries, language)
    val typed = QuantityInput.fieldText(row, language).takeIf { it.isNotEmpty() }
    val quantity = when {
        scalableQuantity(row, entries) != null -> line.quantity
        typed != null -> listOfNotNull(typed, line.quantity).joinToString(" ")
        line.name != null -> line.quantity
        else -> null
    }
    if (line.name == null && quantity == null && line.note == null) {
        return buildAnnotatedString { withStyle(SpanStyle(color = muted)) { append(fresh) } }
    }
    return buildAnnotatedString {
        quantity?.let { append(it); append(" ") }
        if (line.name == null) withStyle(SpanStyle(color = muted)) { append(unnamed) } else append(line.name)
        append(withNote("", line.note, muted))
    }
}

/**
 * The fields of one ingredient row in a Material 3 bottom sheet, in the order the row held them (FLOW-023): the name
 * with the dictionary's offers and the free-text hint, the note, the quantity and the unit, and the divisibility of a
 * `piece` where its entry gives none. Each edit applies as it is typed, so the sheet closes — by Done, a swipe down or
 * Back — keeping everything; closing it leaves its fields, which marks what is over its limit (FLOW-028).
 *
 * The keyboard's Next goes name → note → quantity → the unit's menu; a unit picked from there closes the sheet, unless
 * the row then asks for its divisibility. Where [rows] asks for the row at [position], its name takes the focus.
 *
 * Under the fields stand the row's [actions] (FLOW-025, DOM-022): one that takes the author elsewhere — another row, a
 * heading — leaves the fields first, as closing does; the removal closes the sheet, then removes the row.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun IngredientSheet(
    position: Int,
    row: Ingredient,
    editor: RecipeEditor,
    language: InterfaceLanguage,
    dictionary: List<IngredientEntryEntity>?,
    mode: Mode,
    rows: RowFocus,
    typed: TypedInSheet,
    actions: List<SheetAction>,
    onClose: () -> Unit,
) {
    CoversStatements()
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val at by rememberUpdatedState(position)
    // The sheet's own focus, which the fields are left by before it goes.
    var focus by remember { mutableStateOf<FocusManager?>(null) }
    val leave = {
        focus?.clearFocus()
        editor.leave(RecipePart.IngredientNote(at))
    }
    val close = {
        leave()
        onClose()
    }
    // Once on its way out — closing, or left for another row or a heading — the sheet takes no further action.
    val going = remember { Going() }
    val dismiss = { if (going.start()) close() }
    val done = {
        if (going.start()) {
            focus?.clearFocus()
            scope.launch { state.hide() }.invokeOnCompletion { close() }
        }
    }
    val entryDivisible = row.dictionaryEntry?.let { id -> dictionary?.firstOrNull { it.id == id }?.divisible }
    val chain = remember { RowChain() }
    chain.measured = row.unitType != UnitType.TASTE_UNMEASURED
    // A unit picked by the keyboard's Next ends the sheet, unless the row now asks whether a piece can be halved.
    chain.onUnitPicked = {
        val now = editor.recipe.value?.ingredients?.getOrNull(at)
        if (now == null || now.unitType != UnitType.PIECE || entryDivisible != null) done()
    }
    ModalBottomSheet(
        onDismissRequest = dismiss,
        sheetState = state,
        modifier = Modifier.testTag(IngredientSheetTags.SHEET),
        // No handle: the sheet opens whole, and Done, a swipe down or Back closes it.
        dragHandle = null,
    ) {
        val manager = LocalFocusManager.current
        SideEffect { focus = manager }
        // Asked for — added, or reached by the keyboard's Next, or tapped with no name yet — the row's name takes the
        // focus as soon as the sheet is laid out, with the keyboard up.
        rows.TakenInSheet(position, chain.name)
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.editor_ingredient_edit),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            TextButton(onClick = done, modifier = Modifier.heightIn(min = 48.dp).testTag(IngredientSheetTags.DONE)) {
                Text(stringResource(R.string.editor_done))
            }
        }
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(bottom = 8.dp)) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                IngredientFields(position, row, editor, language, dictionary, mode, chain, entryDivisible, typed)
            }
            SheetActions(actions) { action -> scope.take(action, going, state, leave, onClose) }
        }
    }
}

/** The fields of the row at [position], as [IngredientSheet] holds them. */
@Composable
private fun IngredientFields(
    position: Int,
    row: Ingredient,
    editor: RecipeEditor,
    language: InterfaceLanguage,
    dictionary: List<IngredientEntryEntity>?,
    mode: Mode,
    chain: RowChain,
    entryDivisible: Boolean?,
    typed: TypedInSheet,
) {
    // An entry that gives a divisibility decides it (FLOW-023 (11)): the quantity is checked against its grid even before
    // the row holds it — a draft written before, holding 1½ of an indivisible entry, is marked (DOM-017 (9)).
    val problem = QuantityInput.problem(
        if (row.unitType == UnitType.PIECE) row.copy(divisible = effectiveDivisible(row, entryDivisible)) else row,
    )
    // The name, the quantity and the unit each take the height of the tallest of them, so the inputs are even.
    val heights = remember { EvenHeights() }
    // What the name's hint and the quantity's error add under their fields, which the others do not take on.
    val nameUnder = remember { mutableIntStateOf(0) }
    val quantityUnder = remember { mutableIntStateOf(0) }
    IngredientNameField(position, row, editor, language, dictionary, mode, chain, heights.of(EvenHeights.Input.NAME) { nameUnder.intValue }, nameUnder)
    // How the ingredient is prepared, under its name and apart from it: the name says what it is.
    NoteField(position, row, editor, chain, typed.note)
    // "To taste" withdraws the quantity field (FLOW-023 (8)); it comes back empty with another unit (9).
    val quantity = row.unitType != UnitType.TASTE_UNMEASURED
    val quantityHeight = heights.of(EvenHeights.Input.QUANTITY) { quantityUnder.intValue }
    val unitHeight = heights.of(EvenHeights.Input.UNIT)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // The quantity's label is never wrapped onto the field above it (A11Y-004): where it does not fit on one line
        // beside the unit, the quantity and the unit each take a line of their own.
        val label = stringResource(R.string.editor_ingredient_quantity)
        val measurer = rememberTextMeasurer()
        val labelStyle = MaterialTheme.typography.bodySmall
        val density = LocalDensity.current
        val labelWidth = remember(label, labelStyle, density) { with(density) { measurer.measure(label, labelStyle).size.width.toDp() } }
        val besideUnit = maxWidth - UNIT_WIDTH - 8.dp
        if (!quantity || besideUnit >= labelWidth + QUANTITY_LABEL_ROOM) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                if (quantity) {
                    QuantityField(position, row, editor, language, chain, problem, typed.quantity, quantityUnder, Modifier.weight(1f).then(quantityHeight))
                }
                UnitField(position, row, editor, chain, Modifier.width(UNIT_WIDTH).then(unitHeight))
            }
        } else {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                QuantityField(position, row, editor, language, chain, problem, typed.quantity, quantityUnder, Modifier.fillMaxWidth().then(quantityHeight))
                UnitField(position, row, editor, chain, Modifier.fillMaxWidth().then(unitHeight))
            }
        }
    }
    // A `piece` row asks for its divisibility unless its entry gives one (FLOW-022 (5), FLOW-023 (11)).
    if (row.unitType == UnitType.PIECE && entryDivisible == null) Divisibility(position, row, editor)
}

/** How wide the unit select is beside the quantity: room for its longest unit, "по вкусу", and its arrow. */
private val UNIT_WIDTH = 140.dp

/** What an outlined field takes across besides its floating label: its padding on both sides and the label's gaps. */
private val QUANTITY_LABEL_ROOM = 40.dp

/**
 * The free-text warning (FLOW-022 (2)) as a hint in the name's supporting text, in its muted colour, never as an error:
 * the field and the screen reader read it with the name. A build without the server says nothing of publication in it
 * (FLOW-062 (1)).
 */
@Composable
private fun FreeTextHint(position: Int, mode: Mode, under: MutableIntState) {
    UnderField(
        stringResource(
            if (mode == Mode.Serverless) R.string.editor_ingredient_free_text_warning_serverless else R.string.editor_ingredient_free_text_warning,
        ),
        under,
        Modifier.testTag(EditorTags.ingredientWarning(position)),
    )
}

/**
 * A field's supporting text, whose height — with the gap Material 3 leaves above it — is told to [under] while it is
 * shown, so that the row's other inputs are made even with the field and not with what is written under it.
 */
@Composable
private fun UnderField(text: String, under: MutableIntState, modifier: Modifier) {
    val gap = with(LocalDensity.current) { SUPPORTING_GAP.roundToPx() }
    DisposableEffect(under) { onDispose { under.intValue = 0 } }
    Text(text, modifier.onSizeChanged { under.intValue = it.height + gap })
}

/** The gap Material 3 leaves between an outlined field and its supporting text. */
private val SUPPORTING_GAP = 4.dp

/**
 * The row's ingredient. What the author types is held as their own words at once, so leaving the editor loses
 * nothing (FLOW-024); while they type, the field offers the dictionary's entries for it and, beside them, adding the
 * text as free text (FLOW-057). Picking an entry names the row by it (DOM-009). Leaving the field with exactly an
 * offered entry's label typed names the row by that entry, because the entry is that ingredient (FLOW-057 (7)).
 * Typed past an entry's label, after a space, the words are the entry's qualifier and the entry stays; an edit inside
 * the label makes the row the author's own words again, with no qualifier (DOM-009, FLOW-023 (4)).
 */
@Composable
private fun IngredientNameField(
    position: Int,
    row: Ingredient,
    editor: RecipeEditor,
    language: InterfaceLanguage,
    dictionary: List<IngredientEntryEntity>?,
    mode: Mode,
    chain: RowChain,
    fieldModifier: Modifier,
    under: MutableIntState,
) {
    var offering by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    fun held() = editor.recipe.value?.ingredients?.getOrNull(position) ?: row
    // What the author typed past the entry's label stays as typed — a space before the next word included — while it
    // reads as the qualifier the row holds; once they leave the field, it reads as held.
    var typed by remember { mutableStateOf<String?>(null) }
    val label = row.entryLabelIn(dictionary, language)
    val shown = typed?.takeIf { label != null && qualifierTyped(it, label) == Qualified(row.qualifier) } ?: row.fieldNameIn(dictionary, language)
    val offer = if (offering && dictionary != null && row.dictionaryEntry == null) IngredientOffer.of(shown, dictionary, language) else null
    val hinting = !focused && row.dictionaryEntry == null && !row.freeTextName.isNullOrBlank()
    Column(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = shown,
            onValueChange = { text ->
                val latest = held()
                val kept = latest.entryLabelIn(dictionary, language)?.let { qualifierTyped(text, it) }
                val other = if (kept != null) otherEntryNamed(text, latest, dictionary, language) else null
                if (other != null) {
                    // "chicken" typed on to "chicken wings" names chicken wings, not chicken qualified by "wings".
                    typed = text
                    editor.setIngredientEntry(position, other.id)
                } else if (kept != null) {
                    typed = text
                    editor.setIngredientQualifier(position, kept.qualifier)
                } else {
                    typed = null
                    editor.setIngredientFreeText(position, text)
                    offering = true
                }
            },
            label = { Text(stringResource(R.string.editor_ingredient_name)) },
            // Only a name in the author's own words warns; an entry picked in its place takes the warning away (FLOW-022 (3)).
            // It comes once the field is left, so it never springs up under a name still being typed.
            supportingText = if (hinting) {
                { FreeTextHint(position, mode, under) }
            } else {
                null
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                // The note follows the name.
                imeAction = ImeAction.Next,
            ),
            keyboardActions = KeyboardActions(onNext = { chain.note.requestFocus() }),
            modifier = Modifier
                .fillMaxWidth()
                .then(fieldModifier)
                .focusRequester(chain.name)
                .onFocusChanged { state ->
                    if (focused && !state.isFocused) {
                        offering = false
                        typed = null
                        val latest = held()
                        val written = latest.freeTextName
                        if (latest.dictionaryEntry == null && written != null && dictionary != null) {
                            IngredientOffer.of(written, dictionary, language).exact?.let { editor.setIngredientEntry(position, it.id) }
                        } else if (latest.qualifier != null) {
                            // A label and a qualifier that together are another entry's label name that entry.
                            otherEntryNamed(latest.fieldNameIn(dictionary, language), latest, dictionary, language)
                                ?.let { editor.setIngredientEntry(position, it.id) }
                        }
                    }
                    focused = state.isFocused
                }
                .testTag(EditorTags.ingredientName(position)),
        )
        if (offer != null && (offer.entries.isNotEmpty() || offer.freeText != null)) {
            Column(Modifier.fillMaxWidth().testTag(IngredientNameTags.offer(position))) {
                offer.entries.forEach { entry ->
                    val entryLabel = entry.label(language)
                    OfferedChoice(
                        text = entryLabel,
                        synonym = offer.synonyms[entry.id],
                        synonymTag = IngredientNameTags.synonym(position, entry.id),
                        action = stringResource(R.string.editor_ingredient_pick_action, entryLabel),
                        onClick = {
                            editor.setIngredientEntry(position, entry.id)
                            offering = false
                            focus.clearFocus()
                        },
                        modifier = Modifier.testTag(IngredientNameTags.entry(position, entry.id)),
                    )
                }
                offer.freeText?.let { text ->
                    OfferedChoice(
                        text = stringResource(R.string.editor_ingredient_free_text_offer, text),
                        action = stringResource(R.string.editor_ingredient_free_text_action, text),
                        onClick = {
                            editor.setIngredientFreeText(position, text)
                            offering = false
                            focus.clearFocus()
                        },
                        modifier = Modifier.testTag(IngredientNameTags.freeText(position)),
                    )
                }
            }
        }
    }
}

/** The test tags of the ingredient field's offer (FLOW-057). */
object IngredientNameTags {
    fun offer(position: Int) = "editor-ingredient-$position-offer"
    fun entry(position: Int, entryId: Long) = "editor-ingredient-$position-offer-$entryId"
    fun freeText(position: Int) = "editor-ingredient-$position-offer-free-text"
    fun synonym(position: Int, entryId: Long) = "editor-ingredient-$position-offer-$entryId-synonym"
}

/**
 * How the row's ingredient is prepared ("finely chopped"), any row's, an entry's or free text's (DOM-023): labelled
 * apart from the name, whose qualifier says what the ingredient is. What the author types stays in the field as typed
 * — a space before the next word included — while it reads as the note the row holds, trimmed, blank as none; once
 * they leave the field, it reads as held. Over [app.recipefork.core.recipe.RecipeBounds.MAX_NOTE] characters it is kept
 * in full and marked on leaving the field, as every limit is (FLOW-028).
 */
@Composable
private fun NoteField(position: Int, row: Ingredient, editor: RecipeEditor, chain: RowChain, typedText: MutableState<String?>) {
    var typed by typedText
    // The row as the editor holds it now — [row] at the latest; the text in the field stands only while it is what the
    // row holds.
    val held = (editor.recipe.value?.ingredients?.getOrNull(position) ?: row).note
    val shown = typed?.takeIf { Ingredient.noteOf(it) == held } ?: held.orEmpty()
    val focus = LocalFocusManager.current
    Column(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = shown,
            onValueChange = {
                typed = it
                editor.setIngredientNote(position, it)
            },
            label = { Text(stringResource(R.string.editor_ingredient_note)) },
            placeholder = { Text(stringResource(R.string.editor_ingredient_note_example)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                // It goes on after the ingredient's name, as the card shows it: "onion — finely chopped".
                capitalization = KeyboardCapitalization.None,
                imeAction = ImeAction.Next,
            ),
            keyboardActions = KeyboardActions(
                onNext = {
                    // "To taste" withdraws the quantity: the note goes on to the unit.
                    if (chain.measured) chain.quantity.requestFocus()
                    else chain.openUnit(focus)
                },
            ),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(chain.note)
                .onFocusChanged { if (!it.isFocused) typed = null }
                .then(leaving { editor.leave(RecipePart.IngredientNote(position)) })
                .testTag(IngredientNoteTags.field(position)),
        )
        BoundMark(editor, RecipePart.IngredientNote(position), IngredientNoteTags.mark(position), gap = 4.dp, Modifier.padding(top = 4.dp))
    }
}

/** The test tags of an ingredient row's note (DOM-023). */
object IngredientNoteTags {
    fun field(position: Int) = "editor-ingredient-$position-note"
    fun mark(position: Int) = "editor-ingredient-$position-note-mark"
}

/**
 * The row's quantity. What the author types stays in the field as typed, held or not (DOM-017 (6)); once they leave
 * the field, a value it holds is written back as the grid writes it — `0,5 ч. л.` as ½, `1,500 kg` as 1.5
 * (DOM-017 (3), DOM-019 (3)). A later change of unit or divisibility leaves the text as it stands.
 */
@Composable
private fun QuantityField(
    position: Int,
    row: Ingredient,
    editor: RecipeEditor,
    language: InterfaceLanguage,
    chain: RowChain,
    problem: QuantityProblem?,
    typedText: MutableState<String?>,
    under: MutableIntState,
    modifier: Modifier,
) {
    var typed by typedText
    // The row as the editor holds it now; the text in the field stands only while it is what the row holds.
    fun held() = editor.recipe.value?.ingredients?.getOrNull(position) ?: row
    val shown = typed?.takeIf { held().withTypedQuantity(it) == held() } ?: QuantityInput.fieldText(held(), language)
    val focus = LocalFocusManager.current
    OutlinedTextField(
        value = shown,
        onValueChange = {
            typed = it
            editor.setIngredientQuantity(position, it)
        },
        label = { Text(stringResource(R.string.editor_ingredient_quantity)) },
        // What the field accepts, said in its own supporting text when it does not hold what was typed (DOM-017 (6)).
        isError = problem != null,
        supportingText = problem?.let { { UnderField(stringResource(problemMessage(it)), under, Modifier.testTag(EditorTags.ingredientError(position))) } },
        singleLine = true,
        // The unit follows the quantity: Next opens its menu.
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
        keyboardActions = KeyboardActions(onNext = { chain.openUnit(focus) }),
        modifier = modifier
            .focusRequester(chain.quantity)
            .onFocusChanged { focusState ->
                val latest = held()
                if (!focusState.isFocused && latest.quantity != null && QuantityInput.problem(latest) == null) {
                    typed = QuantityInput.fieldText(latest, language)
                }
            }
            .testTag(EditorTags.ingredientQuantity(position)),
    )
}

/**
 * The inputs of one row made even: each is laid out at least as tall as the tallest of the others, so a label wrapped
 * at a large font scale in one of them does not leave the others shorter. Each input is measured against the others
 * alone, so when the tallest grows shorter, the rest follow it down.
 */
@Stable
private class EvenHeights {
    /** For each input, how tall it was when taller than the others; 0 while it was not. */
    private val taller = mutableStateMapOf<Input, Int>()

    /**
     * Lays out the input [key] at least as tall as the tallest of the others; what [under] says is written under its
     * field — its supporting text — is not counted, neither as its height nor as the others'.
     */
    @Composable
    fun of(key: Input, under: () -> Int = { 0 }): Modifier {
        DisposableEffect(key) { onDispose { taller.remove(key) } }
        return Modifier.layout { measurable, constraints ->
            val others = taller.filterKeys { it != key }.values.maxOrNull() ?: 0
            val extra = under()
            val placeable = measurable.measure(
                constraints.copy(minHeight = (others + extra).coerceIn(constraints.minHeight, constraints.maxHeight)),
            )
            val box = placeable.height - extra
            val own = if (box > others) box else 0
            if (taller[key] != own) taller[key] = own
            layout(placeable.width, placeable.height) { placeable.place(0, 0) }
        }
    }

    /** The inputs of a row. */
    enum class Input { NAME, QUANTITY, UNIT }
}

/**
 * The row's unit: exactly the twelve units of DOM-016, by their names in the interface language, in Material 3's exposed
 * drop-down — a read-only outlined field, as the fields beside it are, over the menu of units.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnitField(position: Int, row: Ingredient, editor: RecipeEditor, chain: RowChain, modifier: Modifier) {
    var expanded by chain::unitOpen
    val action = stringResource(R.string.editor_ingredient_unit_action)
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { open ->
            // Opened by a tap, a unit picked stays where it is.
            if (open) chain.byNext = false
            expanded = open
        },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = row.shownUnit?.let { stringResource(unitName(it)) }.orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.editor_ingredient_unit)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
                .semantics { onClick(label = action, action = null) }
                .testTag(EditorTags.ingredientUnit(position)),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            QuantityUnit.entries.forEach { unit ->
                DropdownMenuItem(
                    text = { Text(stringResource(unitName(unit))) },
                    onClick = {
                        editor.setIngredientUnit(position, unit)
                        expanded = false
                        // Reached by the keyboard's Next, a unit picked ends the sheet's chain.
                        if (chain.byNext) chain.onUnitPicked()
                        chain.byNext = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                    modifier = Modifier.testTag(EditorTags.unitOption(unit)),
                )
            }
        }
    }
}

/** The unit a row shows in its unit field: "to taste" holds no unit of its own (DOM-002). */
private val Ingredient.shownUnit: QuantityUnit?
    get() = if (unitType == UnitType.TASTE_UNMEASURED) QuantityUnit.TO_TASTE else unit

/**
 * How the keyboard's Next goes through the sheet's fields: the name, the note, the quantity where the row has one, then
 * the unit, whose menu opens; a unit picked there calls [onUnitPicked].
 */
@Stable
private class RowChain {
    val name = FocusRequester()
    val note = FocusRequester()
    val quantity = FocusRequester()

    /** Whether the row has a quantity field: "to taste" withdraws it. */
    var measured by mutableStateOf(true)

    /** Whether the unit's menu is open. */
    var unitOpen by mutableStateOf(false)

    /** Whether the open unit menu was reached by the keyboard's Next. */
    var byNext = false

    var onUnitPicked: () -> Unit = {}

    /** Opens the unit's menu from the field before it: the keyboard goes, as the unit is not typed. */
    fun openUnit(focus: FocusManager) {
        focus.clearFocus()
        byNext = true
        unitOpen = true
    }
}

/** The divisibility of a `piece` row in the author's own words, or naming an entry that gives none (FLOW-022 (5)). */
@Composable
private fun Divisibility(position: Int, row: Ingredient, editor: RecipeEditor) {
    Column(Modifier.fillMaxWidth().padding(top = 4.dp).selectableGroup().testTag(EditorTags.divisibility(position))) {
        Text(stringResource(R.string.editor_ingredient_divisible_question), style = MaterialTheme.typography.bodyMedium)
        listOf(true to R.string.editor_ingredient_divisible_yes, false to R.string.editor_ingredient_divisible_no)
            .forEach { (divisible, label) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .selectable(selected = row.divisible == divisible, role = Role.RadioButton) {
                            editor.setIngredientDivisible(position, divisible)
                        }
                        .testTag(EditorTags.divisible(position, divisible)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = row.divisible == divisible, onClick = null)
                    Text(stringResource(label), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 8.dp))
                }
            }
    }
}

/** The test tags of a group's heading read as its name, and of the sheet it opens in. */
object GroupSheetTags {
    const val SHEET = "editor-group-sheet"
    const val DONE = "editor-group-sheet-done"

    /** The heading's line: its name, or a placeholder while it has none; the heading's click target. */
    fun line(index: Int) = "editor-group-$index-line"

    /** The mark of the name over its limit, under the field in the sheet (FLOW-028). */
    fun nameMark(index: Int) = "editor-group-$index-sheet-mark"
}

/**
 * The heading at [index] — whose key is [key] — in a Material 3 bottom sheet, as small as an ingredient's (DOM-022):
 * its name in a field, kept as typed however long and marked over [app.recipefork.core.recipe.RecipeBounds.MAX_GROUP_NAME]
 * characters once left (FLOW-028), and under it its [actions]. Closed — by Done, a swipe down or Back — or left by the
 * keyboard's Next for the group's first row ([onNext]; with no rows, the keyboard's Done closes the sheet), the name is
 * settled: one left blank is no name ([RecipeEditor.settleGroupName]). Where [headings] asks for [index], the name takes
 * the focus.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GroupSheet(
    index: Int,
    key: Long,
    name: String,
    editor: RecipeEditor,
    headings: RowFocus,
    actions: List<SheetAction>,
    onNext: (() -> Unit)?,
    onClose: () -> Unit,
) {
    CoversStatements()
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    // The name the heading had as the sheet opened, which a name left blank takes back.
    val entered = rememberSaveable { name }
    var focus by remember { mutableStateOf<FocusManager?>(null) }
    val leave: () -> Unit = { focus?.clearFocus() }
    // By the heading's key, so it is this heading that is settled wherever it stands now.
    val settle = {
        leave()
        editor.groupIndex(key)?.let { now ->
            editor.leave(RecipePart.GroupName(now))
            editor.settleGroupName(now, entered)
        }
    }
    val close = {
        settle()
        onClose()
    }
    // Once on its way out — closing, or left for a row — the sheet takes no further action.
    val going = remember { Going() }
    val dismiss = { if (going.start()) close() }
    val done = {
        if (going.start()) {
            leave()
            scope.launch { state.hide() }.invokeOnCompletion { close() }
        }
    }
    val requester = remember { FocusRequester() }
    ModalBottomSheet(
        onDismissRequest = dismiss,
        sheetState = state,
        modifier = Modifier.testTag(GroupSheetTags.SHEET),
        dragHandle = null,
    ) {
        val manager = LocalFocusManager.current
        SideEffect { focus = manager }
        headings.TakenInSheet(index, requester)
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.editor_group_edit),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            TextButton(onClick = done, modifier = Modifier.heightIn(min = 48.dp).testTag(GroupSheetTags.DONE)) {
                Text(stringResource(R.string.editor_done))
            }
        }
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(bottom = 8.dp)) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { editor.groupIndex(key)?.let { now -> editor.renameGroup(now, it) } },
                    label = { Text(stringResource(R.string.editor_group_name)) },
                    placeholder = { Text(stringResource(R.string.editor_group_name_example)) },
                    singleLine = true,
                    // Next goes on to the group's first row; a group with no rows yet ends the chain here.
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = if (onNext == null) ImeAction.Done else ImeAction.Next,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { done() },
                        onNext = {
                            if (going.start()) {
                                settle()
                                onNext?.invoke()
                            }
                        },
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(requester)
                        .then(leaving { editor.groupIndex(key)?.let { editor.leave(RecipePart.GroupName(it)) } })
                        .testTag(EditorTags.groupName(index)),
                )
                BoundMark(editor, RecipePart.GroupName(index), GroupSheetTags.nameMark(index), gap = 4.dp, Modifier.padding(top = 4.dp))
            }
            SheetActions(actions) { action -> scope.take(action, going, state, leave, onClose) }
        }
    }
}
