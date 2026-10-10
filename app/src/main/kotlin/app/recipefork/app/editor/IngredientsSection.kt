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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
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
 * The ingredient list, after the serving count (FLOW-024): each row reads as one line, as the card writes it, and opens
 * in a sheet of its fields on a tap (FLOW-023); rows are appended and deleted. Rows are named from the ingredient
 * dictionary of [ingredients] or in the author's own words (DOM-009).
 *
 * Which row's sheet is open is kept by the row's key, across the activity being recreated: the sheet follows its row
 * where it moves, and goes when the row is deleted. Asking [rows] for a row — the one added, or the next the keyboard's
 * Next goes to — opens its sheet with the name focused.
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
    // The key of the row whose sheet is open; null while none is.
    var open by rememberSaveable { mutableStateOf<Long?>(null) }
    val asked = rows.asked
    LaunchedEffect(asked, editable) {
        if (asked != null && editable && asked < (editor.recipe.value?.ingredients?.size ?: 0)) open = editor.ingredientKey(asked)
    }
    // The rows and the groups' headings stand in one column (DOM-022); a row is dragged among them alone, a heading with
    // its rows. A drop is the same single move as the row menu's (FLOW-025, A11Y-006).
    val items = recipe.listItems
    val drag = rememberRowDrag(scroll, ROW_SPACING) { from, to -> editor.moveIngredientItem(from, to) }
    val motion = rememberRowMotion()
    motion.update(recipe.ingredients.indices.map(editor::ingredientKey))
    // The sheet stands outside the list's column, so the list lays out as if it were not there.
    Box(Modifier.fillMaxWidth()) {
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
                // What the screen holds for a row or a heading — focus, a typed value — stays with it when items before
                // it are inserted, moved or deleted: each is keyed directly in the list, never inside a branch.
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
                                IngredientRow(position, row, editor, language, dictionary, drag, motion, place, headings) {
                                    // A row with no name yet opens with its name focused, the keyboard up.
                                    if (row.dictionaryEntry == null && row.freeTextName.isNullOrBlank()) rows.ask(position)
                                    else open = editor.ingredientKey(position)
                                }
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
                        // The row added opens with its name focused, the keyboard up.
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
        val shownKey = open
        val position = shownKey?.let(editor::ingredientPosition)
        if (shownKey != null && (position == null || !editable)) {
            // The row was deleted, or the recipe can no longer be changed: the sheet goes.
            LaunchedEffect(shownKey) { open = null }
        } else if (shownKey != null && position != null) {
            key(shownKey) {
                val typed = rememberTypedInSheet()
                IngredientSheet(position, recipe.ingredients[position], editor, language, dictionary, mode, rows, typed) { open = null }
            }
        }
    }
}

/** A row as a frozen recipe shows it: its line as the card writes it, and nothing to change it with. */
@Composable
private fun ReadOnlyIngredient(position: Int, row: Ingredient, language: InterfaceLanguage, dictionary: List<IngredientEntryEntity>?) {
    OutlinedCard(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.testTag(EditorTags.ingredient(position))) {
        Text(
            ingredientLineText(row, dictionary, language),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).testTag(IngredientSheetTags.line(position)),
        )
    }
}

/**
 * One row: its line as the card writes it — amount, unit, name with its qualifier, and its note (FLOW-023, DOM-023) —
 * and under it what the row's fields would mark: a name in the author's own words (FLOW-022 (2)), a quantity its field
 * does not hold (DOM-017 (6)), a note over its limit once left (FLOW-028). The line and its marks are one target, at
 * least 48 dp tall, whose tap ([onOpen]) opens the row's sheet. Beside it, its removal and its menu of inserting and
 * moving (FLOW-025); down its edge, the column it is dragged by.
 */
@Composable
private fun IngredientRow(
    position: Int,
    row: Ingredient,
    editor: RecipeEditor,
    language: InterfaceLanguage,
    dictionary: List<IngredientEntryEntity>?,
    drag: RowDrag,
    motion: RowMotion,
    place: ItemPlace,
    headings: RowFocus,
    onOpen: () -> Unit,
) {
    // An entry that gives a divisibility decides it (FLOW-023 (11)), as the sheet's quantity field reads it.
    val entryDivisible = row.dictionaryEntry?.let { id -> dictionary?.firstOrNull { it.id == id }?.divisible }
    val problem = QuantityInput.problem(
        if (row.unitType == UnitType.PIECE) row.copy(divisible = effectiveDivisible(row, entryDivisible)) else row,
    )
    val freeText = row.dictionaryEntry == null && !row.freeTextName.isNullOrBlank()
    val edit = stringResource(R.string.editor_ingredient_edit)
    // One bounded block per row, so where one ingredient ends and the next begins is plain; dragged, it moves whole.
    OutlinedCard(
        Modifier
            .fillMaxWidth()
            .then(motion.row(editor.ingredientKey(position)))
            .then(drag.row(place.at, MaterialTheme.colorScheme.surface, CardDefaults.outlinedShape))
            .testTag(EditorTags.ingredient(position)),
    ) {
        // The block is dragged by the column down its left edge — or its right, for a left hand. The only row of a
        // group has no handle: it cannot move without leaving its group with no rows. Its column stays, empty, so that
        // its line starts where the other rows' do.
        DragColumnBlock(drag, place.at, place.count, EditorTags.ingredientHandle(position), EditorTags.ingredientHandleIcon(position), still = !place.movable) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                // Where the line beside both icons would not keep room for a word of eight letters — at large font
                // scales — the line takes the whole width and the icons go to a row of their own under it.
                val measurer = rememberTextMeasurer()
                val lineStyle = MaterialTheme.typography.bodyLarge
                val density = LocalDensity.current
                val word = remember(lineStyle, density) { with(density) { measurer.measure(NAME_ROOM_SAMPLE, lineStyle).size.width.toDp() } }
                val under = maxWidth - LINE_PADDING_ACROSS - CONTROL_SIZE * 2 < word
                Column(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        Column(
                            Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .clickable(onClickLabel = edit, role = Role.Button, onClick = onOpen)
                                .padding(start = 12.dp, top = 12.dp, end = 4.dp, bottom = 12.dp)
                                .testTag(IngredientSheetTags.line(position)),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(ingredientLineText(row, dictionary, language), style = MaterialTheme.typography.bodyLarge)
                            if (freeText) {
                                Text(
                                    stringResource(R.string.editor_ingredient_free_text_mark),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.testTag(IngredientSheetTags.freeText(position)),
                                )
                            }
                            problem?.let {
                                Text(
                                    stringResource(problemMessage(it)),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.testTag(IngredientSheetTags.problem(position)),
                                )
                            }
                            BoundMark(editor, RecipePart.IngredientNote(position), IngredientSheetTags.noteMark(position), gap = 4.dp)
                        }
                        if (!under) RowControls(position, place, editor, headings)
                    }
                    if (under) BlockActions(Modifier.padding(start = 8.dp)) { RowControls(position, place, editor, headings) }
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

/** The name a row shows: its entry's label in the interface language (L10N-003), or the author's own words. */
internal fun Ingredient.nameIn(dictionary: List<IngredientEntryEntity>?, language: InterfaceLanguage): String =
    dictionaryEntry?.let { id -> dictionary?.firstOrNull { it.id == id }?.label(language) } ?: freeTextName.orEmpty()

/** The label of the entry the row names, in the interface language; null for free text, or before the dictionary is read. */
internal fun Ingredient.entryLabelIn(dictionary: List<IngredientEntryEntity>?, language: InterfaceLanguage): String? =
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
internal fun otherEntryNamed(text: String, row: Ingredient, dictionary: List<IngredientEntryEntity>?, language: InterfaceLanguage): IngredientEntryEntity? =
    dictionary?.let { IngredientOffer.of(text, it, language).exact }?.takeIf { it.id != row.dictionaryEntry }

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

/** How large each of a row's action icons is (A11Y-003). */
private val CONTROL_SIZE = 48.dp

/** What the line's padding takes across beside the drag column: 12 dp before it and 4 dp after it. */
private val LINE_PADDING_ACROSS = 16.dp

/** The room a line keeps beside the row's controls: eight figures, about as wide as a word of eight letters. */
private const val NAME_ROOM_SAMPLE = "00000000"
