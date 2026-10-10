package app.recipefork.app.editor

import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import app.recipefork.app.Mode
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
import app.recipefork.core.recipe.RecipeBounds
import app.recipefork.core.recipe.RecipeLanguage
import app.recipefork.core.recipe.RecipePart
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.recipe.effectiveDivisible
import app.recipefork.core.recipe.withTypedQuantity
import app.recipefork.core.scaling.InterfaceLanguage

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
internal fun ServingCount(recipe: Recipe, editor: RecipeEditor, requester: FocusRequester, onNext: (() -> Unit)?) {
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
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = recipe.servings?.toString().orEmpty(),
            onValueChange = { refused = !editor.setServings(it) },
            label = { Text(stringResource(R.string.editor_servings)) },
            supportingText = {
                Text(stringResource(R.string.editor_servings_accepts), modifier = Modifier.testTag(EditorTags.SERVINGS_ACCEPTS))
            },
            isError = refused,
            singleLine = true,
            // Next goes on to the first item of equipment or, with none, the ingredient list's first item, a row or a
            // group's heading ([onNext]); with neither, Done closes the keyboard and adds nothing.
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = if (onNext == null) ImeAction.Done else ImeAction.Next,
            ),
            keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }, onNext = { onNext?.invoke() }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(requester)
                .then(leaving { editor.leave(RecipePart.Servings) })
                .testTag(EditorTags.SERVINGS),
        )
        BoundMark(editor, RecipePart.Servings, EditorTags.SERVINGS_MARK, gap = 4.dp)
    }
}

/**
 * The ingredient list, after the serving count (FLOW-024): rows of three fields each (FLOW-023), appended and deleted.
 * Rows are named from the ingredient dictionary of [ingredients] or in the author's own words (DOM-009).
 */
@Composable
internal fun Ingredients(
    recipe: Recipe,
    editor: RecipeEditor,
    ingredients: IngredientSource,
    scroll: EditorScroll,
    mode: Mode,
    rows: RowFocus,
    headings: RowFocus,
) {
    val editable = recipe.state.editable
    val language = numberLanguage(LocalConfiguration.current)
    var dictionary by remember(ingredients) { mutableStateOf<List<IngredientEntryEntity>?>(null) }
    LaunchedEffect(ingredients) {
        // A dictionary that cannot be read offers nothing; every name can still be written as free text (FLOW-022).
        dictionary = runCatching { ingredients.ingredients() }.getOrDefault(emptyList()).also(editor::knowEntries)
    }
    // The rows and the groups' headings stand in one column (DOM-022); a row is dragged among them alone, a heading with
    // its rows. A drop is the same single move as the row menu's (FLOW-025, A11Y-006).
    val items = recipe.listItems
    val drag = rememberRowDrag(scroll, ROW_SPACING) { from, to -> editor.moveIngredientItem(from, to) }
    val motion = rememberRowMotion()
    motion.update(recipe.ingredients.indices.map(editor::ingredientKey))
    RowsColumn(
        ROW_SPACING,
        Modifier.fillMaxWidth().zIndex(if (drag.dragged != null) 1f else 0f).testTag(EditorTags.INGREDIENTS),
    ) {
        Text(
            stringResource(R.string.editor_ingredients),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        // Under the heading: the list left empty, or longer than the limit (FLOW-028).
        BoundMark(editor, RecipePart.Ingredients, EditorTags.INGREDIENTS_MARK, gap = ROW_SPACING)
        items.forEachIndexed { at, item ->
            // What the screen holds for a row or a heading — focus, an open offer, a typed value — stays with it when
            // items before it are inserted, moved or deleted: each is keyed directly in the list, never inside a branch.
            if (item is ListItem.Row) motion.LeavingAt(item.position)
            val itemKey: Any = when (item) {
                is ListItem.Row -> editor.ingredientKey(item.position)
                is ListItem.Heading -> GroupKey(editor.groupKey(item.index))
            }
            key(itemKey) {
                when (item) {
                    is ListItem.Row -> {
                        val position = item.position
                        val row = recipe.ingredients[position]
                        if (editable) {
                            val place = ItemPlace(at, items, recipe.ingredientGroups.any { it.firstPosition == position })
                            IngredientRow(position, recipe.ingredients.size, row, editor, language, dictionary, drag, mode, rows, motion, place, headings)
                        } else {
                            ReadOnlyIngredient(position, row, language, dictionary)
                        }
                    }
                    is ListItem.Heading -> {
                        if (editable) GroupHeading(item.index, at, items, recipe, editor, drag, headings, rows)
                        else ReadOnlyHeading(item.index, recipe.ingredientGroups[item.index].name)
                    }
                }
            }
        }
        motion.LeavingAt(recipe.ingredients.size, last = true)
        if (editable) {
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                EditorButton(
                    text = stringResource(R.string.editor_ingredient_add),
                    action = stringResource(R.string.editor_ingredient_add_action),
                    // The row added takes the focus in its name, with the keyboard up.
                    onClick = {
                        val added = editor.recipe.value?.ingredients?.size
                        if (editor.addIngredient() && added != null) rows.ask(added)
                    },
                    modifier = Modifier.testTag(EditorTags.INGREDIENT_ADD),
                    weight = ButtonWeight.TONAL,
                )
                // A heading after the last row, whose group the rows added next fill; where one with no rows yet
                // already stands there, it takes the focus instead of a second one.
                EditorButton(
                    text = stringResource(R.string.editor_group_add),
                    action = stringResource(R.string.editor_group_add_action),
                    onClick = {
                        val now = editor.recipe.value ?: return@EditorButton
                        val count = now.ingredients.size
                        if (editor.addGroup(count)) headings.ask(now.ingredientGroups.size)
                        else if (now.ingredientGroups.lastOrNull()?.firstPosition == count) headings.ask(now.ingredientGroups.lastIndex)
                    },
                    modifier = Modifier.testTag(EditorTags.GROUP_ADD),
                    weight = ButtonWeight.TEXT,
                )
            }
        }
    }
}

/** A row as a frozen recipe shows it: what it holds, and nothing to change it with. */
@Composable
private fun ReadOnlyIngredient(position: Int, row: Ingredient, language: InterfaceLanguage, dictionary: List<IngredientEntryEntity>?) {
    OutlinedCard(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.testTag(EditorTags.ingredient(position))) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                row.fieldNameIn(dictionary, language).takeIf { it.isNotBlank() } ?: stringResource(R.string.editor_ingredient_unnamed),
                style = MaterialTheme.typography.bodyLarge,
            )
            // How it is prepared, apart from the name and muted, as the card shows it (DOM-023).
            row.note?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag(IngredientNoteTags.field(position)),
                )
            }
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
 * One row: the ingredient, how it is prepared (its note, DOM-023), the quantity and the unit (FLOW-023 (1)) — never a
 * unit type, which follows from the unit (FLOW-023 (7)). Under them, only what the row calls for: the free-text hint
 * under the name (FLOW-022 (2)), the divisibility question of a `piece` (FLOW-022 (5)) and, in the quantity's own supporting text, what the field
 * accepts when it does not hold what was typed (DOM-017 (6)).
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
    mode: Mode,
    rows: RowFocus,
    motion: RowMotion,
    place: ItemPlace,
    headings: RowFocus,
) {
    // An entry that gives a divisibility decides it (FLOW-023 (11)): the quantity is checked against its grid even before
    // the row holds it — a draft written before, holding 1½ of an indivisible entry, is marked (DOM-017 (9)).
    val entryDivisible = row.dictionaryEntry?.let { id -> dictionary?.firstOrNull { it.id == id }?.divisible }
    val problem = QuantityInput.problem(
        if (row.unitType == UnitType.PIECE) row.copy(divisible = effectiveDivisible(row, entryDivisible)) else row,
    )
    // The name, the quantity and the unit each take the height of the tallest of them, so the row's inputs are even.
    val heights = remember { EvenHeights() }
    // What the name's hint and the quantity's error add under their fields, which the others do not take on.
    val nameUnder = remember { mutableIntStateOf(0) }
    val quantityUnder = remember { mutableIntStateOf(0) }
    // The keyboard's Next goes name → note → quantity → unit → the next row's name; the last row's last field says Done.
    val chain = remember { RowChain() }
    // The chain follows the list as it is shown: after a group's last row comes the next group's heading.
    chain.last = place.next == null
    chain.measured = row.unitType != UnitType.TASTE_UNMEASURED
    chain.onUnitPicked = { focusOn(place.next, rows, headings) }
    rows.TakenBy(position, chain.name)
    // One bounded block per row, so where one ingredient ends and the next begins is plain; dragged, it moves whole.
    OutlinedCard(
        Modifier
            .fillMaxWidth()
            .then(motion.row(editor.ingredientKey(position)))
            .then(drag.row(place.at, MaterialTheme.colorScheme.surface, CardDefaults.outlinedShape))
            .testTag(EditorTags.ingredient(position)),
    ) {
        // The block is dragged by the column down its left edge — or its right, for a left hand — so nothing that drags
        // stands among the inputs.
        // The only row of a group has no handle: it cannot move without leaving its group with no rows. Its column
        // stays, empty, so that its inputs line up with the other rows'.
        DragColumnBlock(drag, place.at, place.count, EditorTags.ingredientHandle(position), EditorTags.ingredientHandleIcon(position), still = !place.movable) {
            // The name takes the first line up to the row's two action icons, so the block is no taller than its two
            // lines of inputs; the quantity and the unit start the line below at the same left edge.
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                // Where the name beside both icons would not keep room for a word of eight letters — at large font
                // scales — the name takes the whole line and the icons go to a row of their own under the inputs.
                val measurer = rememberTextMeasurer()
                val nameStyle = MaterialTheme.typography.bodyLarge
                val density = LocalDensity.current
                val word = remember(nameStyle, density) { with(density) { measurer.measure(NAME_ROOM_SAMPLE, nameStyle).size.width.toDp() } }
                val under = maxWidth - BLOCK_PADDING_ACROSS - CONTROL_SIZE * 2 - NAME_FIELD_FURNITURE < word
                Column(
                    Modifier.fillMaxWidth().padding(start = 8.dp, top = 4.dp, end = 4.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        IngredientNameField(position, row, editor, language, dictionary, mode, chain, Modifier.weight(1f), heights.of(EvenHeights.Input.NAME) { nameUnder.intValue }, nameUnder)
                        if (!under) Row(Modifier.padding(top = 8.dp)) { RowControls(position, place, editor, headings) }
                    }
                    // How the ingredient is prepared, under its name and apart from it: the name says what it is.
                    NoteField(position, row, editor, chain)
                    // "To taste" withdraws the quantity field (FLOW-023 (8)); it comes back empty with another unit (9).
                    val quantity = row.unitType != UnitType.TASTE_UNMEASURED
                    val quantityHeight = heights.of(EvenHeights.Input.QUANTITY) { quantityUnder.intValue }
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
                                    QuantityField(position, row, editor, language, chain, problem, quantityUnder, Modifier.weight(1f).then(quantityHeight))
                                }
                                UnitField(position, row, editor, chain, Modifier.width(UNIT_WIDTH).then(unitHeight))
                            }
                        } else {
                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                QuantityField(position, row, editor, language, chain, problem, quantityUnder, Modifier.fillMaxWidth().then(quantityHeight))
                                UnitField(position, row, editor, chain, Modifier.fillMaxWidth().then(unitHeight))
                            }
                        }
                    }
                    if (under) BlockActions { RowControls(position, place, editor, headings) }
                    // A `piece` row asks for its divisibility unless its entry gives one (FLOW-022 (5), FLOW-023 (11)).
                    if (row.unitType == UnitType.PIECE && entryDivisible == null) Divisibility(position, row, editor)
                }
            }
        }
    }
}

/**
 * Where an ingredient row stands in the list as the editor shows it: at item [at] of [items], rows and headings
 * together ([listItems]); [startsGroup] where a heading stands right above it.
 */
private class ItemPlace(val at: Int, items: List<ListItem>, val startsGroup: Boolean) {
    val count = items.size

    /** The item after the row, a row or a heading, which the keyboard's Next goes to; null after the last. */
    val next: ListItem? = items.getOrNull(at + 1)

    /** Whether the row can move: the only row of a group cannot ([movable]). */
    val movable = items.movable(at)
}

/** Asks [item] — a row or a group's heading — to take the focus with the keyboard up; none for null. */
internal fun focusOn(item: ListItem?, rows: RowFocus, headings: RowFocus) = when (item) {
    is ListItem.Row -> rows.ask(item.position)
    is ListItem.Heading -> headings.ask(item.index)
    null -> Unit
}

/** A heading's key among the ingredient list's items, apart from the rows' keys. */
private data class GroupKey(val key: Long)

/**
 * The removal and the menu of the ingredient row at [position], standing at [place] (FLOW-025): icons as a step's are,
 * 48 dp each, named by the row for the screen reader and shown in a tooltip on long press. Moved up or down, the row
 * trades places with the item next to it — past a heading, it leaves its group for the one it now stands in (DOM-022).
 * A group's heading put above the row takes the focus in its name, as [headings] asks.
 */
@Composable
private fun RowControls(position: Int, place: ItemPlace, editor: RecipeEditor, headings: RowFocus) {
    val remove = stringResource(R.string.editor_ingredient_remove_action, position + 1)
    BlockAction(
        icon = R.drawable.ic_remove_row,
        label = remove,
        tag = EditorTags.ingredientRemove(position),
        action = remove,
        onClick = { editor.removeIngredient(position) },
        destructive = true,
    )
    RowMenu(
        position = place.at,
        count = place.count,
        name = stringResource(R.string.editor_ingredient_menu_action, position + 1),
        tag = EditorTags.ingredientMenu(position),
        actionTag = { EditorTags.ingredientAction(position, it) },
        onAction = { action ->
            when (action) {
                // A row put above or below this one joins its group.
                RowAction.INSERT_ABOVE -> editor.insertIngredient(position)
                RowAction.INSERT_BELOW -> editor.insertIngredient(position + 1, intoGroupAbove = true)
                RowAction.MOVE_UP -> editor.moveIngredientItem(place.at, place.at - 1)
                RowAction.MOVE_DOWN -> editor.moveIngredientItem(place.at, place.at + 1)
                RowAction.GROUP_ABOVE -> {
                    val index = editor.recipe.value?.ingredientGroups?.count { it.firstPosition < position }
                    if (editor.addGroup(position) && index != null) headings.ask(index)
                }
            }
        },
        actions = RowAction.entries.filter {
            when (it) {
                // A move that would leave a group with no rows is not offered.
                RowAction.MOVE_UP -> place.movable && place.at > 0
                RowAction.MOVE_DOWN -> place.movable && place.at < place.count - 1
                RowAction.GROUP_ABOVE -> !place.startsGroup
                else -> true
            }
        },
    )
}

/**
 * The heading of the group at [index], standing at item [at] of [items] (DOM-022): its name in a field of its own —
 * a name over [RecipeBounds.MAX_GROUP_NAME] characters kept and marked on leaving it, as every limit is (FLOW-028); one
 * left blank is no name ([RecipeEditor.settleGroupName]). Beside it its removal and its menu, which moves the group
 * past the one above or below; dragged by its column, the heading carries its rows with it.
 */
@Composable
private fun GroupHeading(
    index: Int,
    at: Int,
    items: List<ListItem>,
    recipe: Recipe,
    editor: RecipeEditor,
    drag: RowDrag,
    headings: RowFocus,
    rows: RowFocus,
) {
    val group = recipe.ingredientGroups[index]
    val key = editor.groupKey(index)
    val owned = recipe.groupRows(index)
    val requester = remember { FocusRequester() }
    headings.TakenBy(index, requester)
    // The name the heading had as the author came to it, which a name left blank takes back.
    var entered by remember { mutableStateOf("") }
    val focus = LocalFocusManager.current
    val span = blockAt(items, at)
    Box(Modifier.fillMaxWidth().then(drag.row(at, MaterialTheme.colorScheme.surface)).testTag(EditorTags.group(index))) {
        // A heading with no rows yet has no handle: it stays last until rows fill its group. Its column stays, empty.
        DragColumnBlock(drag, at, items.size, EditorTags.groupHandle(index), EditorTags.groupHandleIcon(index), span, groupLanding(items, at, span), still = !items.movable(at)) {
            Column(Modifier.fillMaxWidth().padding(start = 8.dp, top = 4.dp, end = 4.dp, bottom = 4.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    OutlinedTextField(
                        value = group.name,
                        onValueChange = { editor.renameGroup(index, it) },
                        label = { Text(stringResource(R.string.editor_group_name)) },
                        placeholder = { Text(stringResource(R.string.editor_group_name_example)) },
                        textStyle = MaterialTheme.typography.titleMedium,
                        singleLine = true,
                        // Next goes on to the group's first row; a group with no rows yet ends the chain here.
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = if (owned.isEmpty()) ImeAction.Done else ImeAction.Next,
                        ),
                        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }, onNext = { rows.ask(owned.first) }),
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(requester)
                            .then(
                                // By the heading's key, so it is this heading that is settled wherever it stands now.
                                leaving(
                                    onEnter = { entered = editor.recipe.value?.ingredientGroups?.getOrNull(index)?.name.orEmpty() },
                                    onLeave = {
                                        val now = editor.groupIndex(key) ?: return@leaving
                                        editor.leave(RecipePart.GroupName(now))
                                        editor.settleGroupName(now, entered)
                                    },
                                ),
                            )
                            .testTag(EditorTags.groupName(index)),
                    )
                    Row(Modifier.padding(top = 8.dp)) { GroupControls(index, recipe, editor) }
                }
                BoundMark(editor, RecipePart.GroupName(index), EditorTags.groupMark(index), gap = 4.dp, Modifier.padding(top = 4.dp))
            }
        }
    }
}

/**
 * The removal of the heading at [index] — its rows stay, in the group above or in none — and its menu, which moves the
 * group, heading and rows, past the group above or below it; never above the rows no group owns, nor past a group
 * with no rows yet. A group with nowhere to go has no menu.
 */
@Composable
private fun GroupControls(index: Int, recipe: Recipe, editor: RecipeEditor) {
    val remove = stringResource(R.string.editor_group_remove_action, index + 1)
    BlockAction(
        icon = R.drawable.ic_remove_row,
        label = remove,
        tag = EditorTags.groupRemove(index),
        action = remove,
        onClick = { editor.removeGroup(index) },
        destructive = true,
    )
    val filled = !recipe.groupRows(index).isEmpty()
    val moves = listOfNotNull(
        RowAction.MOVE_UP.takeIf { filled && index > 0 },
        RowAction.MOVE_DOWN.takeIf { filled && index + 1 < recipe.ingredientGroups.size && !recipe.groupRows(index + 1).isEmpty() },
    )
    if (moves.isEmpty()) return
    RowMenu(
        position = index,
        count = recipe.ingredientGroups.size,
        name = stringResource(R.string.editor_group_menu_action, index + 1),
        tag = EditorTags.groupMenu(index),
        actionTag = { EditorTags.groupAction(index, it) },
        onAction = { editor.moveGroup(index, down = it == RowAction.MOVE_DOWN) },
        actions = moves,
    )
}

/** A heading as a frozen recipe shows it: its name, a heading for the screen reader. */
@Composable
private fun ReadOnlyHeading(index: Int, name: String) {
    Text(
        name,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.fillMaxWidth().semantics { heading() }.testTag(EditorTags.group(index)),
    )
}

/** How large each of a row's action icons is (A11Y-003). */
private val CONTROL_SIZE = 48.dp

/** What the block's padding takes across beside the drag column: 8 dp before its inputs and 4 dp after them. */
private val BLOCK_PADDING_ACROSS = 12.dp

/** The room a name's text keeps beside the row's controls: eight figures, about as wide as a word of eight letters. */
private const val NAME_ROOM_SAMPLE = "00000000"

/** What a name field takes across besides its text: its padding, and room at its end for a 48 dp control. */
private val NAME_FIELD_FURNITURE = 16.dp + 48.dp

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

/** The name a row shows: its entry's label in the interface language (L10N-003), or the author's own words. */
internal fun Ingredient.nameIn(dictionary: List<IngredientEntryEntity>?, language: InterfaceLanguage): String =
    dictionaryEntry?.let { id -> dictionary?.firstOrNull { it.id == id }?.label(language) } ?: freeTextName.orEmpty()

/** The label of the entry the row names, in the interface language; null for free text, or before the dictionary is read. */
private fun Ingredient.entryLabelIn(dictionary: List<IngredientEntryEntity>?, language: InterfaceLanguage): String? =
    dictionaryEntry?.let { id -> dictionary?.firstOrNull { it.id == id }?.label(language) }

/** What the row's name field reads: its entry's label and, after a space, the entry's qualifier; or the author's own words. */
internal fun Ingredient.fieldNameIn(dictionary: List<IngredientEntryEntity>?, language: InterfaceLanguage): String {
    val name = nameIn(dictionary, language)
    val qualifier = qualifier?.takeIf { entryLabelIn(dictionary, language) != null && it.isNotBlank() } ?: return name
    return "$name $qualifier"
}

/** What a name field typed past an entry's label keeps of it: the entry, with [qualifier] beside it or none. */
internal data class Qualified(val qualifier: String?)

/**
 * What [text] typed into the field of a row named by the entry labelled [label] keeps of it (DOM-009): the label alone
 * keeps the entry with no qualifier; the label, then a space or a comma and words, keeps it with those words — without
 * the separators — as its qualifier; null once the label itself is edited, which makes the row the author's own words.
 */
internal fun qualifierTyped(text: String, label: String): Qualified? {
    if (!text.startsWith(label)) return null
    val rest = text.substring(label.length)
    if (rest.isNotEmpty() && !rest.first().let { it.isWhitespace() || it == ',' }) return null
    return Qualified(rest.trim { it.isWhitespace() || it == ',' }.ifEmpty { null })
}

/**
 * The offerable entry, other than the one [row] names, whose label in either language is exactly [text] as FLOW-057
 * folds it: a qualifier is no substitute for the entry that is named whole (FLOW-023 (3), FLOW-057 (7)).
 */
private fun otherEntryNamed(text: String, row: Ingredient, dictionary: List<IngredientEntryEntity>?, language: InterfaceLanguage): IngredientEntryEntity? =
    dictionary?.let { IngredientOffer.of(text, it, language).exact }?.takeIf { it.id != row.dictionaryEntry }

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
    modifier: Modifier,
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
    Column(modifier) {
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
                // The note follows the name on every row.
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
                    val label = entry.label(language)
                    OfferedChoice(
                        text = label,
                        synonym = offer.synonyms[entry.id],
                        synonymTag = IngredientNameTags.synonym(position, entry.id),
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

/**
 * One choice of a dictionary field's offer — an ingredient's or an item of equipment's: a line at least 48 dp tall whose click says what it does (A11Y-003,
 * A11Y-005) — with the [synonym] the entry was found by, if any, beside it in a muted style: «картофель · картошка».
 */
@Composable
internal fun OfferedChoice(
    text: String,
    action: String,
    onClick: () -> Unit,
    modifier: Modifier,
    synonym: String? = null,
    synonymTag: String = "",
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClickLabel = action, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f, fill = synonym == null))
        if (synonym != null) {
            Text(
                " · $synonym",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false).testTag(synonymTag),
            )
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
 * they leave the field, it reads as held. Over [RecipeBounds.MAX_NOTE] characters it is kept in full and marked on
 * leaving the field, as every limit is (FLOW-028).
 */
@Composable
private fun NoteField(position: Int, row: Ingredient, editor: RecipeEditor, chain: RowChain) {
    // Kept across the activity being recreated, under the row's key, so it stays the row's own.
    var typed by rememberSaveable { mutableStateOf<String?>(null) }
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
                // "To taste" withdraws the quantity: on the last row the note is then its last typed field.
                imeAction = if (chain.last && !chain.measured) ImeAction.Done else ImeAction.Next,
            ),
            keyboardActions = KeyboardActions(
                onDone = { focus.clearFocus() },
                onNext = {
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
    under: MutableIntState,
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
        // What the field accepts, said in its own supporting text when it does not hold what was typed (DOM-017 (6)).
        isError = problem != null,
        supportingText = problem?.let { { UnderField(stringResource(problemMessage(it)), under, Modifier.testTag(EditorTags.ingredientError(position))) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = if (chain.last) ImeAction.Done else ImeAction.Next),
        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }, onNext = { chain.openUnit(focus) }),
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
                        // Reached by the keyboard's Next, a unit picked goes on to the next row's name.
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

/**
 * How the keyboard's Next goes through one ingredient row: the name, the note, the quantity where the row has one, then
 * the unit, whose menu opens; a unit picked there goes on to the next row's name. The last row's last keyboard field
 * says Done.
 */
@Stable
private class RowChain {
    val name = FocusRequester()
    val note = FocusRequester()
    val quantity = FocusRequester()

    /** Whether the row is the list's last. */
    var last by mutableStateOf(false)

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
