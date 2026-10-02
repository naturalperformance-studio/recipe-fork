package app.recipefork.app.editor

import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.foundation.background
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import app.recipefork.app.R
import app.recipefork.app.dictionary.IngredientEntryEntity
import app.recipefork.app.dictionary.IngredientOffer
import app.recipefork.app.dictionary.IngredientSource
import app.recipefork.app.dictionary.label
import app.recipefork.core.recipe.AcceptedValues
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.QuantityInput
import app.recipefork.core.recipe.QuantityProblem
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeLanguage
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.recipe.withTypedQuantity
import app.recipefork.core.scaling.InterfaceLanguage
import kotlinx.coroutines.launch

/** The interface language as numbers are read and written in it (L10N-009). */
fun numberLanguage(configuration: Configuration): InterfaceLanguage =
    if (interfaceLanguage(configuration) == RecipeLanguage.RU) InterfaceLanguage.RUSSIAN else InterfaceLanguage.ENGLISH

/** The name of [unit] in the interface language, as DOM-016 gives it. */
@StringRes
fun unitName(unit: QuantityUnit): Int = when (unit) {
    QuantityUnit.GRAM -> R.string.editor_unit_gram
    QuantityUnit.KILOGRAM -> R.string.editor_unit_kilogram
    QuantityUnit.TONNE -> R.string.editor_unit_tonne
    QuantityUnit.MILLILITRE -> R.string.editor_unit_millilitre
    QuantityUnit.LITRE -> R.string.editor_unit_litre
    QuantityUnit.CUBIC_METRE -> R.string.editor_unit_cubic_metre
    QuantityUnit.PIECE -> R.string.editor_unit_piece
    QuantityUnit.TEASPOON -> R.string.editor_unit_teaspoon
    QuantityUnit.TABLESPOON -> R.string.editor_unit_tablespoon
    QuantityUnit.CUP -> R.string.editor_unit_cup
    QuantityUnit.PINCH -> R.string.editor_unit_pinch
    QuantityUnit.TO_TASTE -> R.string.editor_unit_to_taste
}

/** What a marked quantity field says: the values it accepts (DOM-017 (6)), or how a number is written (DOM-019 (5)). */
@StringRes
fun problemMessage(problem: QuantityProblem): Int = when (problem) {
    QuantityProblem.Separators -> R.string.editor_quantity_separators
    is QuantityProblem.NotAccepted -> when (problem.accepts) {
        AcceptedValues.WHOLE_GRAMS -> R.string.editor_quantity_accepts_grams
        AcceptedValues.WHOLE_MILLILITRES -> R.string.editor_quantity_accepts_millilitres
        AcceptedValues.WHOLE_PIECES -> R.string.editor_quantity_accepts_whole_pieces
        AcceptedValues.HALVES -> R.string.editor_quantity_accepts_halves
        AcceptedValues.KITCHEN_FRACTIONS -> R.string.editor_quantity_accepts_fractions
    }
}

/** The unit a row shows in its unit field: "to taste" holds no unit of its own (DOM-002). */
private val Ingredient.shownUnit: QuantityUnit?
    get() = if (unitType == UnitType.TASTE_UNMEASURED) QuantityUnit.TO_TASTE else unit

/**
 * The authored serving count, after the tags (FLOW-024): a whole number of one or more, or nothing. Anything else
 * typed is not held — the field keeps what it held and says what it accepts (DOM-015).
 */
@Composable
internal fun ServingCount(recipe: Recipe, editor: RecipeEditor) {
    if (!recipe.state.editable) {
        Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.testTag(EditorTags.SERVINGS)) {
            Text(stringResource(R.string.editor_servings), style = MaterialTheme.typography.labelMedium)
            Text(
                recipe.servings?.toString() ?: stringResource(R.string.editor_servings_none),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        return
    }
    var refused by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    OutlinedTextField(
        value = recipe.servings?.toString().orEmpty(),
        onValueChange = { refused = !editor.setServings(it) },
        label = { Text(stringResource(R.string.editor_servings)) },
        supportingText = {
            Text(stringResource(R.string.editor_servings_accepts), modifier = Modifier.testTag(EditorTags.SERVINGS_ACCEPTS))
        },
        isError = refused,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
        modifier = Modifier.fillMaxWidth().testTag(EditorTags.SERVINGS),
    )
}

/**
 * The ingredient list, after the serving count (FLOW-024): rows of three fields each (FLOW-023), appended and deleted.
 * Rows are named from the ingredient dictionary of [ingredients] or in the author's own words (DOM-009).
 */
@Composable
internal fun Ingredients(recipe: Recipe, editor: RecipeEditor, ingredients: IngredientSource, scroll: EditorScroll) {
    val editable = recipe.state.editable
    val language = numberLanguage(LocalConfiguration.current)
    var dictionary by remember(ingredients) { mutableStateOf<List<IngredientEntryEntity>?>(null) }
    LaunchedEffect(ingredients) {
        // A dictionary that cannot be read offers nothing; every name can still be written as free text (FLOW-022).
        dictionary = runCatching { ingredients.ingredients() }.getOrDefault(emptyList())
    }
    // A drop is the same single move as the row menu's (FLOW-025, A11Y-006).
    val drag = rememberRowDrag(scroll, ROW_SPACING) { from, to -> editor.moveIngredient(from, to) }
    Column(
        Modifier.fillMaxWidth().zIndex(if (drag.dragged != null) 1f else 0f).testTag(EditorTags.INGREDIENTS),
        verticalArrangement = Arrangement.spacedBy(ROW_SPACING),
    ) {
        Text(
            stringResource(R.string.editor_ingredients),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        recipe.ingredients.forEachIndexed { position, row ->
            // What the screen holds for a row — focus, an open offer, a typed value — stays with it when rows before
            // it are inserted, moved or deleted.
            key(editor.ingredientKey(position)) {
                if (editable) IngredientRow(position, recipe.ingredients.size, row, editor, language, dictionary, drag)
                else ReadOnlyIngredient(position, row, language, dictionary)
            }
        }
        if (editable) {
            ActionText(
                text = stringResource(R.string.editor_ingredient_add),
                action = stringResource(R.string.editor_ingredient_add_action),
                onClick = { editor.addIngredient() },
                modifier = Modifier.testTag(EditorTags.INGREDIENT_ADD),
            )
        }
    }
}

/** A row as a frozen recipe shows it: what it holds, and nothing to change it with. */
@Composable
private fun ReadOnlyIngredient(position: Int, row: Ingredient, language: InterfaceLanguage, dictionary: List<IngredientEntryEntity>?) {
    OutlinedCard(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.testTag(EditorTags.ingredient(position))) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                row.nameIn(dictionary, language).takeIf { it.isNotBlank() } ?: stringResource(R.string.editor_ingredient_unnamed),
                style = MaterialTheme.typography.bodyLarge,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                QuantityInput.fieldText(row, language).takeIf { it.isNotEmpty() }?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
                row.shownUnit?.let { Text(stringResource(unitName(it)), style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
}

/**
 * One row: exactly the ingredient, the quantity and the unit (FLOW-023 (1)) — never a unit type, which follows from
 * the unit (FLOW-023 (7)). Beside them, only what the row calls for: the free-text warning (FLOW-022 (2)), the
 * divisibility question of a `piece` (FLOW-022 (5)) and the mark of a quantity the field does not hold (DOM-017 (6)).
 * Beside the ingredient, its removal and its menu of inserting and moving (FLOW-025).
 */
@Composable
private fun IngredientRow(
    position: Int,
    count: Int,
    row: Ingredient,
    editor: RecipeEditor,
    language: InterfaceLanguage,
    dictionary: List<IngredientEntryEntity>?,
    drag: RowDrag,
) {
    val problem = QuantityInput.problem(row)
    // The name, the quantity and the unit each take the height of the tallest of them, so the row's inputs are even.
    val heights = remember { EvenHeights() }
    // One bounded block per row, so where one ingredient ends and the next begins is plain; dragged, it moves whole.
    OutlinedCard(
        Modifier
            .fillMaxWidth()
            .then(drag.row(position, MaterialTheme.colorScheme.surface, CardDefaults.outlinedShape))
            .testTag(EditorTags.ingredient(position)),
    ) {
        // The block is dragged by the column down its left edge, so nothing that drags stands among the inputs.
        DragColumnBlock(drag, position, count, EditorTags.ingredientHandle(position), EditorTags.ingredientHandleIcon(position)) {
            // The name takes the whole first line up to its two controls, so its text has room at large font scales; the
            // quantity and the unit start the line below at the same left edge.
            Column(
                Modifier.fillMaxWidth().padding(start = 8.dp, top = 4.dp, end = 4.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    // Where the name beside both controls would not keep room for a word of eight letters — at large
                    // font scales — the two controls stand one above the other and the name takes the room of one.
                    val measurer = rememberTextMeasurer()
                    val nameStyle = MaterialTheme.typography.bodyLarge
                    val density = LocalDensity.current
                    val word = remember(nameStyle, density) { with(density) { measurer.measure(NAME_ROOM_SAMPLE, nameStyle).size.width.toDp() } }
                    val stacked = maxWidth - CONTROL_SIZE * 2 - NAME_FIELD_FURNITURE < word
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        IngredientNameField(position, row, editor, language, dictionary, Modifier.weight(1f), heights.of(EvenHeights.Input.NAME))
                        if (stacked) {
                            Column(Modifier.padding(top = 8.dp)) { RowControls(position, count, editor) }
                        } else {
                            Row(Modifier.padding(top = 8.dp)) { RowControls(position, count, editor) }
                        }
                    }
                }
                // "To taste" withdraws the quantity field (FLOW-023 (8)); it comes back empty with another unit (9).
                val quantity = row.unitType != UnitType.TASTE_UNMEASURED
                val quantityHeight = heights.of(EvenHeights.Input.QUANTITY)
                val unitHeight = heights.of(EvenHeights.Input.UNIT)
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    // The quantity's label is never wrapped onto the field above it (A11Y-004): where it does not fit on
                    // one line beside the unit, the quantity and the unit each take a line of their own.
                    val label = stringResource(R.string.editor_ingredient_quantity)
                    val measurer = rememberTextMeasurer()
                    val labelStyle = MaterialTheme.typography.bodySmall
                    val density = LocalDensity.current
                    val labelWidth = remember(label, labelStyle, density) { with(density) { measurer.measure(label, labelStyle).size.width.toDp() } }
                    val besideUnit = maxWidth - UNIT_WIDTH - 8.dp
                    if (!quantity || besideUnit >= labelWidth + QUANTITY_LABEL_ROOM) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                            if (quantity) {
                                QuantityField(position, row, editor, language, isError = problem != null, Modifier.weight(1f).then(quantityHeight))
                            }
                            UnitField(position, row, editor, Modifier.width(UNIT_WIDTH).then(unitHeight))
                        }
                    } else {
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            QuantityField(position, row, editor, language, isError = problem != null, Modifier.fillMaxWidth().then(quantityHeight))
                            UnitField(position, row, editor, Modifier.fillMaxWidth().then(unitHeight))
                        }
                    }
                }
                if (problem != null) {
                    Text(
                        stringResource(problemMessage(problem)),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.testTag(EditorTags.ingredientError(position)),
                    )
                }
                // No shipped entry supplies divisibility, so every `piece` row asks for it, picked from an entry or not.
                if (row.unitType == UnitType.PIECE) Divisibility(position, row, editor)
            }
        }
    }
}

/** The removal and the menu of the ingredient row at [position] of [count] (FLOW-025), each a 48 dp target. */
@Composable
private fun RowControls(position: Int, count: Int, editor: RecipeEditor) {
    // An icon, not a word, so the name keeps room for its text at large font scales.
    IconButton(
        onClick = { editor.removeIngredient(position) },
        modifier = Modifier.size(CONTROL_SIZE).testTag(EditorTags.ingredientRemove(position)),
    ) {
        Icon(
            painterResource(R.drawable.ic_remove_row),
            contentDescription = stringResource(R.string.editor_ingredient_remove_action, position + 1),
        )
    }
    RowMenu(
        position = position,
        count = count,
        name = stringResource(R.string.editor_ingredient_menu_action, position + 1),
        tag = EditorTags.ingredientMenu(position),
        actionTag = { EditorTags.ingredientAction(position, it) },
        onAction = { action ->
            when (action) {
                RowAction.INSERT_ABOVE -> editor.insertIngredient(position)
                RowAction.INSERT_BELOW -> editor.insertIngredient(position + 1)
                RowAction.MOVE_UP -> editor.moveIngredient(position, position - 1)
                RowAction.MOVE_DOWN -> editor.moveIngredient(position, position + 1)
            }
        },
    )
}

/** How large a row's removal and its menu are (A11Y-003). */
private val CONTROL_SIZE = 48.dp

/** The room a name's text keeps beside the row's controls: eight figures, about as wide as a word of eight letters. */
private const val NAME_ROOM_SAMPLE = "00000000"

/** What a name field takes across besides its text: its start padding and the free-text warning at its end. */
private val NAME_FIELD_FURNITURE = 16.dp + 48.dp

/** How wide the unit select is beside the quantity: room for its longest unit, "по вкусу", and its arrow. */
private val UNIT_WIDTH = 140.dp

/** What an outlined field takes across besides its floating label: its padding on both sides and the label's gaps. */
private val QUANTITY_LABEL_ROOM = 40.dp

/**
 * The free-text warning (FLOW-022 (2)) as an icon beside the name: its shape says there is something to know, its
 * full text is what the screen reader reads on reaching it, and a tap or a long press shows that text in a tooltip.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FreeTextWarning(position: Int) {
    val text = stringResource(R.string.editor_ingredient_free_text_warning)
    val state = rememberTooltipState(isPersistent = true)
    val scope = rememberCoroutineScope()
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip(Modifier.testTag(EditorTags.ingredientWarningText(position))) { Text(text) } },
        state = state,
    ) {
        IconButton(
            onClick = { scope.launch { state.show() } },
            modifier = Modifier.size(48.dp).testTag(EditorTags.ingredientWarning(position)),
        ) {
            Icon(painterResource(R.drawable.ic_warning), contentDescription = text)
        }
    }
}

/** The name a row shows: its entry's label in the interface language (L10N-003), or the author's own words. */
internal fun Ingredient.nameIn(dictionary: List<IngredientEntryEntity>?, language: InterfaceLanguage): String =
    dictionaryEntry?.let { id -> dictionary?.firstOrNull { it.id == id }?.label(language) } ?: freeTextName.orEmpty()

/**
 * The row's ingredient. What the author types is held as their own words at once, so leaving the editor loses
 * nothing (FLOW-024); while they type, the field offers the dictionary's entries for it and, beside them, adding the
 * text as free text (FLOW-057). Picking an entry names the row by it (DOM-009). Leaving the field with exactly an
 * offered entry's label typed names the row by that entry, because the entry is that ingredient (FLOW-057 (7)).
 */
@Composable
private fun IngredientNameField(
    position: Int,
    row: Ingredient,
    editor: RecipeEditor,
    language: InterfaceLanguage,
    dictionary: List<IngredientEntryEntity>?,
    modifier: Modifier,
    fieldModifier: Modifier,
) {
    var offering by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    fun held() = editor.recipe.value?.ingredients?.getOrNull(position) ?: row
    val shown = row.nameIn(dictionary, language)
    val offer = if (offering && dictionary != null && row.dictionaryEntry == null) IngredientOffer.of(shown, dictionary, language) else null
    Column(modifier) {
        OutlinedTextField(
            value = shown,
            onValueChange = {
                editor.setIngredientFreeText(position, it)
                offering = true
            },
            label = { Text(stringResource(R.string.editor_ingredient_name)) },
            // Only a name in the author's own words warns; an entry picked in its place takes the warning away (FLOW-022 (3)).
            trailingIcon = if (row.dictionaryEntry == null && !row.freeTextName.isNullOrBlank()) {
                { FreeTextWarning(position) }
            } else {
                null
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
            modifier = Modifier
                .fillMaxWidth()
                .then(fieldModifier)
                .onFocusChanged { state ->
                    if (focused && !state.isFocused) {
                        offering = false
                        val latest = held()
                        val typed = latest.freeTextName
                        if (latest.dictionaryEntry == null && typed != null && dictionary != null) {
                            IngredientOffer.of(typed, dictionary, language).exact?.let { editor.setIngredientEntry(position, it.id) }
                        }
                    }
                    focused = state.isFocused
                }
                .testTag(EditorTags.ingredientName(position)),
        )
        if (offer != null && (offer.entries.isNotEmpty() || offer.freeText != null)) {
            Column(Modifier.fillMaxWidth().testTag(IngredientNameTags.offer(position))) {
                offer.entries.forEach { entry ->
                    val label = entry.label(language)
                    OfferedChoice(
                        text = label,
                        action = stringResource(R.string.editor_ingredient_pick_action, label),
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

/** One choice of the ingredient field's offer: a line at least 48 dp tall whose click says what it does (A11Y-003, A11Y-005). */
@Composable
private fun OfferedChoice(text: String, action: String, onClick: () -> Unit, modifier: Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClickLabel = action, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

/** The test tags of the ingredient field's offer (FLOW-057). */
object IngredientNameTags {
    fun offer(position: Int) = "editor-ingredient-$position-offer"
    fun entry(position: Int, entryId: Long) = "editor-ingredient-$position-offer-$entryId"
    fun freeText(position: Int) = "editor-ingredient-$position-offer-free-text"
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
    isError: Boolean,
    modifier: Modifier,
) {
    // Kept across the activity being recreated, under the row's key, so it stays the row's own.
    var typed by rememberSaveable { mutableStateOf<String?>(null) }
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
        isError = isError,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
        modifier = modifier
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

    /** Lays out the input [key] at least as tall as the tallest of the others. */
    @Composable
    fun of(key: Input): Modifier {
        DisposableEffect(key) { onDispose { taller.remove(key) } }
        return Modifier.layout { measurable, constraints ->
            val others = taller.filterKeys { it != key }.values.maxOrNull() ?: 0
            val placeable = measurable.measure(
                constraints.copy(minHeight = others.coerceIn(constraints.minHeight, constraints.maxHeight)),
            )
            val own = if (placeable.height > others) placeable.height else 0
            if (taller[key] != own) taller[key] = own
            layout(placeable.width, placeable.height) { placeable.place(0, 0) }
        }
    }

    /** The inputs of a row. */
    enum class Input { NAME, QUANTITY, UNIT }
}

/**
 * The row's unit: exactly the twelve units of DOM-016, by their names in the interface language. It is drawn as the
 * outlined text fields beside it are — its label on its outline, its unit inside.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnitField(position: Int, row: Ingredient, editor: RecipeEditor, modifier: Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val labelStyle = MaterialTheme.typography.bodySmall
    // The outline sits where the label's middle is, as an outlined text field's does, and never higher than 8 dp.
    val outlineTop = maxOf(8.dp, with(LocalDensity.current) { labelStyle.lineHeight.toDp() } / 2)
    val background = MaterialTheme.colorScheme.surface
    val outline = MaterialTheme.colorScheme.outline
    Box(modifier, propagateMinConstraints = true) {
        Box(
            Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {}
                .clickable(onClickLabel = stringResource(R.string.editor_ingredient_unit_action), role = Role.DropdownList) {
                    expanded = true
                }
                .testTag(EditorTags.ingredientUnit(position))
                // The outline is drawn behind everything in it, so the label on it cuts it as a text field's does.
                .drawBehind {
                    val stroke = 1.dp.toPx()
                    val top = outlineTop.toPx()
                    drawRoundRect(
                        color = outline,
                        topLeft = Offset(stroke / 2, top + stroke / 2),
                        size = Size(size.width - stroke, size.height - top - stroke),
                        cornerRadius = CornerRadius(4.dp.toPx()),
                        style = Stroke(stroke),
                    )
                },
            propagateMinConstraints = true,
        ) {
            Text(
                stringResource(R.string.editor_ingredient_unit),
                style = labelStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .layout { measurable, constraints ->
                        val label = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                        layout(constraints.constrainWidth(label.width), constraints.constrainHeight(label.height)) {
                            label.place(12.dp.roundToPx(), outlineTop.roundToPx() - label.height / 2)
                        }
                    }
                    .background(background)
                    .padding(horizontal = 4.dp),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp + outlineTop)
                    .padding(top = outlineTop)
                    .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    row.shownUnit?.let { stringResource(unitName(it)) }.orEmpty(),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            QuantityUnit.entries.forEach { unit ->
                DropdownMenuItem(
                    text = { Text(stringResource(unitName(unit))) },
                    onClick = {
                        editor.setIngredientUnit(position, unit)
                        expanded = false
                    },
                    modifier = Modifier.testTag(EditorTags.unitOption(unit)),
                )
            }
        }
    }
}

/** The divisibility of a `piece` row in the author's own words, which no entry supplies (FLOW-022 (5)). */
@Composable
private fun Divisibility(position: Int, row: Ingredient, editor: RecipeEditor) {
    Column(Modifier.fillMaxWidth().selectableGroup().testTag(EditorTags.divisibility(position))) {
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
