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
import app.recipefork.app.card.namedLine
import app.recipefork.app.dictionary.IngredientEntryEntity
import app.recipefork.app.dictionary.IngredientOffer
import app.recipefork.app.dictionary.label
import app.recipefork.app.ui.widestWord
import app.recipefork.core.recipe.AcceptedValues
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.QuantityInput
import app.recipefork.core.recipe.QuantityProblem
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.RecipePart
import app.recipefork.core.recipe.UnitSystem
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.recipe.effectiveDivisible
import app.recipefork.core.recipe.withTypedQuantity
import app.recipefork.core.scaling.InterfaceLanguage
import app.recipefork.core.scaling.UnitLabels
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
    const val TITLE = "editor-ingredient-sheet-title"
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
 * its name with the qualifier, "to taste" after a dash for a row to taste and, after a dash and muted, its note
 * ([namedLine]) — so the editor and the card cannot read it apart. A row with no quantity yet shows no unit; where the
 * card computes no amount from a quantity the row holds, the row shows what its quantity field holds before the unit, so
 * what was typed stays in sight; a row with no name reads as "Unnamed ingredient", and one holding nothing at all as
 * "New ingredient".
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
        // What the field holds stands as the amount, its unit after it, though the card computes nothing from it.
        typed != null -> listOfNotNull(typed, row.unit?.let { UnitLabels.of(it, language) }).joinToString(" ")
        else -> line.quantity
    }
    if (line.name == null && quantity == null && line.toTaste == null && line.note == null) {
        return buildAnnotatedString { withStyle(SpanStyle(color = muted)) { append(fresh) } }
    }
    val name = buildAnnotatedString {
        if (line.name == null) withStyle(SpanStyle(color = muted)) { append(unnamed) } else append(line.name)
    }
    return buildAnnotatedString {
        quantity?.let { append(it); append(" ") }
        append(namedLine(name, line, muted))
    }
}

/**
 * The fields of one ingredient row in a Material 3 bottom sheet: the name with the dictionary's offers and the free-text
 * hint, the quantity and the unit, the divisibility of a `piece` where its entry gives none, and last the optional note
 * — the amount the row needs before how it is prepared. Each edit applies as it is typed, so the sheet closes — by Done,
 * a swipe down or Back — keeping everything; closing it leaves its fields, which marks what is over its limit (FLOW-028).
 *
 * The keyboard's Next goes name → quantity → the unit's menu → note, and picking an offer of the name goes on as Next
 * does; a unit picked by Next takes the author to the note, with the keyboard up, and the note's Done closes the sheet.
 * Where [rows] asks for the row at [position], its name takes the focus.
 *
 * A [fresh] row — just added — is titled "New ingredient". Under the fields stand the row's [actions] (FLOW-025,
 * DOM-022): one that takes the author elsewhere — another row, a heading — leaves the fields first, as closing does; the
 * removal closes the sheet, then removes the row.
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
    units: UnitSystem,
    rows: RowFocus,
    typed: TypedInSheet,
    fresh: Boolean,
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
    // The note, the chain's last field, ends it: its Done closes the sheet.
    chain.onDone = done
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
        SheetHeader(
            stringResource(if (fresh) R.string.editor_ingredient_new else R.string.editor_ingredient_edit),
            IngredientSheetTags.TITLE,
            IngredientSheetTags.DONE,
            done,
        )
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(bottom = 8.dp)) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                IngredientFields(position, row, editor, language, dictionary, mode, units, chain, entryDivisible, typed)
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
    units: UnitSystem,
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
    // "To taste" withdraws the quantity field (FLOW-023 (8)); it comes back empty with another unit (9).
    val quantity = row.unitType != UnitType.TASTE_UNMEASURED
    val quantityHeight = heights.of(EvenHeights.Input.QUANTITY) { quantityUnder.intValue }
    val unitHeight = heights.of(EvenHeights.Input.UNIT)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // No label is wrapped onto the field above it, nor broken inside a word (A11Y-004): each is measured as it rests
        // in its empty field, in the field's own text style. The unit select is as wide as its label or its longest
        // unit needs beside its arrow; where the quantity's label — or a word of what the quantity may say under it —
        // does not fit beside it, the quantity and the unit each take a line of their own.
        val measurer = rememberTextMeasurer()
        val resting = MaterialTheme.typography.bodyLarge
        val under = MaterialTheme.typography.bodySmall
        val density = LocalDensity.current
        val quantityLabel = stringResource(R.string.editor_ingredient_quantity)
        val unitTexts = listOf(stringResource(R.string.editor_ingredient_unit)) + QuantityUnit.entries.map { stringResource(unitName(it)) }
        val messages = QUANTITY_MESSAGES.map { stringResource(it) }
        val needs = remember(quantityLabel, unitTexts, messages, resting, under, density) {
            with(density) {
                val unitNeed = unitTexts.maxOf { measurer.measure(it, resting, softWrap = false, maxLines = 1).size.width }.toDp() + UNIT_ROOM
                val quantityNeed = maxOf(
                    measurer.measure(quantityLabel, resting, softWrap = false, maxLines = 1).size.width,
                    widestWord(messages, under, measurer),
                ).toDp() + LABEL_ROOM
                unitNeed to quantityNeed
            }
        }
        val unitWidth = maxOf(UNIT_WIDTH, needs.first)
        val besideUnit = maxWidth - unitWidth - 8.dp
        if (!quantity && unitWidth <= maxWidth || quantity && besideUnit >= needs.second) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                if (quantity) {
                    QuantityField(position, row, editor, language, chain, problem, typed.quantity, quantityUnder, Modifier.weight(1f).then(quantityHeight))
                }
                UnitField(position, row, editor, units, chain, Modifier.width(unitWidth).then(unitHeight))
            }
        } else {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // Stacked too — a unit wider than the sheet — "to taste" has no quantity field.
                if (quantity) {
                    QuantityField(position, row, editor, language, chain, problem, typed.quantity, quantityUnder, Modifier.fillMaxWidth().then(quantityHeight))
                }
                UnitField(position, row, editor, units, chain, Modifier.fillMaxWidth().then(unitHeight))
            }
        }
    }
    // A `piece` row asks for its divisibility unless its entry gives one (FLOW-022 (5), FLOW-023 (11)).
    if (row.unitType == UnitType.PIECE && entryDivisible == null) Divisibility(position, row, editor)
    // How the ingredient is prepared, last: optional, after the amount the row needs.
    NoteField(position, row, editor, chain, typed.note)
}

/** How wide the unit select is at least beside the quantity: room for its longest unit, "по вкусу", and its arrow. */
private val UNIT_WIDTH = 140.dp

/** What an outlined field takes across besides its label or its text: its padding on both sides and the label's gaps. */
internal val LABEL_ROOM = 40.dp

/** What the unit select takes across besides its label or its unit: [LABEL_ROOM] and its 48 dp arrow. */
private val UNIT_ROOM = LABEL_ROOM + 48.dp

/** Everything the quantity field may say under itself (DOM-017 (6), DOM-019 (5)): no word of it is broken. */
private val QUANTITY_MESSAGES =
    (listOf<QuantityProblem>(QuantityProblem.Separators) + AcceptedValues.entries.map { QuantityProblem.NotAccepted(it) }).map(::problemMessage)

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
                // The quantity follows the name.
                imeAction = ImeAction.Next,
            ),
            keyboardActions = KeyboardActions(onNext = { chain.afterName(focus) }),
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
                        // Picked, the author goes on to the amount, as Next takes them.
                        onClick = {
                            editor.setIngredientEntry(position, entry.id)
                            offering = false
                            chain.afterName(focus)
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
                            chain.afterName(focus)
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
    // Reached by a unit picked by Next: the note takes the focus once the unit's menu has gone, with the keyboard up.
    chain.toNote.TakenInSheet(NOTE, chain.note)
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
                // The last field: Done closes the sheet.
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { chain.onDone() }),
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
 * The row's unit, by its name in the interface language, in Material 3's exposed drop-down — a read-only outlined field,
 * as the fields beside it are, over the menu of the units of the reader's system [units] and the shared ones, the
 * kitchen's usual ones first ([menuUnits]). A row held in a unit of the other system shows it as written, and its menu
 * offers it while the row holds it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnitField(position: Int, row: Ingredient, editor: RecipeEditor, units: UnitSystem, chain: RowChain, modifier: Modifier) {
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
            menuUnits(units, row.shownUnit).forEach { unit ->
                DropdownMenuItem(
                    text = { Text(stringResource(unitName(unit))) },
                    onClick = {
                        editor.setIngredientUnit(position, unit)
                        expanded = false
                        // Reached by the keyboard's Next, a unit picked goes on to the note.
                        if (chain.byNext) chain.toNote.ask(NOTE)
                        chain.byNext = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                    modifier = Modifier.testTag(EditorTags.unitOption(unit)),
                )
            }
        }
    }
}

/**
 * Every unit in the order the unit menu lists them: those a kitchen uses most first, so that a phone shows them without
 * scrolling the menu — then the rest. The domain's own order ([QuantityUnit.entries]) is left as it is.
 */
internal val MENU_UNITS: List<QuantityUnit> = listOf(
    QuantityUnit.PIECE,
    QuantityUnit.GRAM,
    QuantityUnit.KILOGRAM,
    QuantityUnit.MILLILITRE,
    QuantityUnit.LITRE,
    QuantityUnit.OUNCE,
    QuantityUnit.POUND,
    QuantityUnit.FLUID_OUNCE,
    QuantityUnit.TEASPOON,
    QuantityUnit.TABLESPOON,
    QuantityUnit.CUP,
    QuantityUnit.PINCH,
    QuantityUnit.TO_TASTE,
).let { usual -> usual + QuantityUnit.entries.filterNot { it in usual } }

/**
 * The units a row's menu offers in [system], in [MENU_UNITS]' order: the system's own and those both systems share — and
 * [held], the unit the row holds, where it is of the other system, so that the row keeps it as written (v0.35).
 */
internal fun menuUnits(system: UnitSystem, held: QuantityUnit?): List<QuantityUnit> =
    MENU_UNITS.filter { it.system == null || it.system == system || it == held }

/** The unit a row shows in its unit field: "to taste" holds no unit of its own (DOM-002). */
private val Ingredient.shownUnit: QuantityUnit?
    get() = if (unitType == UnitType.TASTE_UNMEASURED) QuantityUnit.TO_TASTE else unit

/**
 * How the keyboard's Next goes through the sheet's fields: the name, the quantity where the row has one, then the unit,
 * whose menu opens; a unit picked there goes on to the note ([toNote]), whose Done calls [onDone].
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

    /** Asks the note to take the focus, with the keyboard up, once the unit's menu has closed. */
    val toNote = RowFocus()

    var onDone: () -> Unit = {}

    /** Goes on from the name: to the quantity, or — "to taste" withdraws it — to the unit's menu. */
    fun afterName(focus: FocusManager) {
        if (measured) runCatching { quantity.requestFocus() } else openUnit(focus)
    }

    /** Opens the unit's menu from the field before it: the keyboard goes, as the unit is not typed. */
    fun openUnit(focus: FocusManager) {
        focus.clearFocus()
        byNext = true
        unitOpen = true
    }
}

/** What [RowChain.toNote] asks for: the sheet holds one row, one note. */
private const val NOTE = 0

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
    const val TITLE = "editor-group-sheet-title"
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
        SheetHeader(stringResource(R.string.editor_group_edit), GroupSheetTags.TITLE, GroupSheetTags.DONE, done)
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
