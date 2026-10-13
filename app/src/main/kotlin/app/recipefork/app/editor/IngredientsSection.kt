package app.recipefork.app.editor

import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
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
import app.recipefork.core.recipe.RecipeLanguage
import app.recipefork.core.recipe.RecipePart
import app.recipefork.core.recipe.UnitSystem
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
    QuantityUnit.OUNCE -> R.string.editor_unit_ounce
    QuantityUnit.POUND -> R.string.editor_unit_pound
    QuantityUnit.FLUID_OUNCE -> R.string.editor_unit_fluid_ounce
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
        AcceptedValues.OUNCES -> R.string.editor_quantity_accepts_ounces
        AcceptedValues.FLUID_OUNCES -> R.string.editor_quantity_accepts_fluid_ounces
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
 * The ingredient list, after the serving count (FLOW-024): each row reads as one line, as the card writes it, and each
 * group's heading as its name; a tap opens a sheet of the row's fields and actions (FLOW-023, FLOW-025), or of the
 * heading's (DOM-022). Rows are named from the ingredient dictionary of [ingredients] or in the author's own words
 * (DOM-009); a row's unit menu offers the units of the reader's system of [units] (v0.35).
 *
 * Which row's or heading's sheet is open is kept by its key, across the activity being recreated: the sheet follows its
 * row where it moves, and goes when the row is deleted. Asking [rows] for a row — the one added, or the next the
 * keyboard's Next goes to — opens its sheet with the name focused, and [headings] does the same for a heading; the field
 * the author came from is left first. A row added — by "Add ingredient" or a sheet's insert — and closed holding
 * nothing goes again, with nothing to undo.
 */
@Composable
internal fun Ingredients(
    recipe: Recipe,
    editor: RecipeEditor,
    ingredients: IngredientSource,
    scroll: EditorScroll,
    mode: Mode,
    units: UnitSystem,
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
    // The key of the row just added whose sheet is open on it: closed holding nothing, it goes again.
    var fresh by rememberSaveable { mutableStateOf<Long?>(null) }
    // The key of the heading whose sheet is open; null while none is.
    var openGroup by rememberSaveable { mutableStateOf<Long?>(null) }
    // The field the author came from by the keyboard's Next — the serving count, an item of equipment — is left before
    // the sheet opens, so that what is done on leaving it is done, and the keyboard does not go back to it afterwards.
    val focus = LocalFocusManager.current
    val asked = rows.asked
    LaunchedEffect(asked, editable) {
        if (asked != null && editable && asked < (editor.recipe.value?.ingredients?.size ?: 0)) {
            focus.clearFocus()
            openGroup = null
            open = editor.ingredientKey(asked)
        }
    }
    val askedHeading = headings.asked
    LaunchedEffect(askedHeading, editable) {
        if (askedHeading != null && editable && askedHeading < (editor.recipe.value?.ingredientGroups?.size ?: 0)) {
            focus.clearFocus()
            open = null
            openGroup = editor.groupKey(askedHeading)
        }
    }
    // The rows and the groups' headings stand in one column (DOM-022); a row is dragged among them alone, a heading with
    // its rows. A drop is the same single move as the sheet's (FLOW-025, A11Y-006).
    val items = recipe.listItems
    val drag = rememberRowDrag(scroll, ROW_SPACING) { from, to -> editor.moveIngredientItem(from, to) }
    val motion = rememberRowMotion()
    motion.update(recipe.ingredients.indices.map(editor::ingredientKey))
    // The sheets stand outside the list's column, so the list lays out as if they were not there.
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
                                IngredientRow(position, row, editor, language, dictionary, drag, motion, ItemPlace(at, items, false)) {
                                    // A row with no name yet opens with its name focused, the keyboard up.
                                    if (row.dictionaryEntry == null && row.freeTextName.isNullOrBlank()) rows.ask(position)
                                    else open = editor.ingredientKey(position)
                                }
                            } else {
                                ReadOnlyIngredient(position, row, language, dictionary)
                            }
                        }
                        is ListItem.Heading -> {
                            val index = item.index
                            if (editable) {
                                GroupHeading(index, at, items, recipe, editor, drag) {
                                    // A heading with no name yet opens with its name focused, the keyboard up.
                                    if (recipe.ingredientGroups[index].name.isBlank()) headings.ask(index)
                                    else openGroup = editor.groupKey(index)
                                }
                            } else {
                                ReadOnlyHeading(index, recipe.ingredientGroups[index].name)
                            }
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
                        // The row added opens in its sheet with its name focused, the keyboard up.
                        onClick = {
                            val added = editor.recipe.value?.ingredients?.size
                            if (editor.addIngredient() && added != null) {
                                fresh = editor.ingredientKey(added)
                                rows.ask(added)
                            }
                        },
                        modifier = Modifier.testTag(EditorTags.INGREDIENT_ADD),
                        weight = ButtonWeight.TONAL,
                    )
                    // A heading after the last row, whose group the rows added next fill, opened in its sheet; where one
                    // with no rows yet already stands there, its sheet opens instead of a second one.
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
            // The row was deleted, or the recipe can no longer be changed: the sheet goes — unless it has gone on to
            // another row meanwhile.
            LaunchedEffect(shownKey) {
                if (open == shownKey) open = null
                if (fresh == shownKey) fresh = null
            }
        } else if (shownKey != null && position != null) {
            key(shownKey) {
                val typed = rememberTypedInSheet()
                val place = ItemPlace(items.indexOf(ListItem.Row(position)), items, recipe.ingredientGroups.any { it.firstPosition == position })
                val row = recipe.ingredients[position]
                val isNew = fresh == shownKey
                // A row just added offers no actions until it holds something: its fields are all there is to it.
                val actions = if (isNew && row.holdsNothing()) emptyList() else rowActions(
                    position, place, editor,
                    // The row put above or below opens in the sheet in this one's place, as one added does; this one,
                    // added and left holding nothing, goes as it would on closing.
                    onInserted = { at ->
                        val inserted = editor.ingredientKey(at)
                        if (fresh == shownKey) dropIfEmpty(editor, shownKey)
                        fresh = inserted
                        open = inserted
                        editor.ingredientPosition(inserted)?.let(rows::ask)
                    },
                    // The heading put above the row opens in its sheet, with its name focused; the row stays.
                    onGroup = { index ->
                        fresh = null
                        headings.ask(index)
                    },
                )
                IngredientSheet(position, row, editor, language, dictionary, mode, units, rows, typed, isNew, actions) {
                    open = null
                    if (fresh == shownKey) {
                        fresh = null
                        dropIfEmpty(editor, shownKey)
                    }
                }
            }
        }
        val groupKey = openGroup
        val groupIndex = groupKey?.let(editor::groupIndex)
        if (groupKey != null && (groupIndex == null || !editable)) {
            // The heading was removed, or the recipe can no longer be changed: the sheet goes.
            LaunchedEffect(groupKey) { if (openGroup == groupKey) openGroup = null }
        } else if (groupKey != null && groupIndex != null) {
            key(GroupKey(groupKey)) {
                val owned = recipe.groupRows(groupIndex)
                GroupSheet(
                    groupIndex, groupKey, recipe.ingredientGroups[groupIndex].name, editor, headings,
                    groupActions(groupIndex, groupKey, recipe, editor),
                    onNext = if (owned.isEmpty()) null else ({ rows.ask(owned.first) }),
                ) { openGroup = null }
            }
        }
    }
}

/** Drops the row whose key is [key] where it still stands holding nothing ([RecipeEditor.dropIngredient]). */
private fun dropIfEmpty(editor: RecipeEditor, key: Long) {
    val at = editor.ingredientPosition(key) ?: return
    if (editor.recipe.value?.ingredients?.getOrNull(at)?.holdsNothing() == true) editor.dropIngredient(at)
}

/** Whether a row holds nothing the author wrote: no name, no amount — held or typed — and no note. Its unit is no content. */
private fun Ingredient.holdsNothing(): Boolean =
    dictionaryEntry == null && freeTextName.isNullOrBlank() && quantity == null && enteredQuantity.isNullOrBlank() && note.isNullOrBlank()

/** A row as a frozen recipe shows it: its line as the card writes it, and nothing to change it with. */
@Composable
private fun ReadOnlyIngredient(position: Int, row: Ingredient, language: InterfaceLanguage, dictionary: List<IngredientEntryEntity>?) {
    // Plain text, at the form's own margin: nothing on a recipe that cannot be changed is drawn as a box to tap.
    Box(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.testTag(EditorTags.ingredient(position))) {
        Text(
            ingredientLineText(row, dictionary, language),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag(IngredientSheetTags.line(position)),
        )
    }
}

/**
 * One row: its line as the card writes it — amount, unit, name with its qualifier, and its note (FLOW-023, DOM-023) —
 * and under it what the row's fields would mark: a name in the author's own words (FLOW-022 (2)), a quantity its field
 * does not hold (DOM-017 (6)), a note over its limit once left (FLOW-028). The line and its marks are one target, at
 * least 48 dp tall, whose tap ([onOpen]) opens the row's sheet, where its actions are; down its edge, the column it is
 * dragged by.
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
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable(onClickLabel = edit, role = Role.Button, onClick = onOpen)
                    .padding(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 12.dp)
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

/** Asks [item] — a row or a group's heading — to open in its sheet with the focus in its name; none for null. */
internal fun focusOn(item: ListItem?, rows: RowFocus, headings: RowFocus) = when (item) {
    is ListItem.Row -> rows.ask(item.position)
    is ListItem.Heading -> headings.ask(item.index)
    null -> Unit
}

/** A heading's key among the ingredient list's items, apart from the rows' keys. */
private data class GroupKey(val key: Long)

/**
 * The actions of the ingredient row at [position], standing at [place], in its sheet (FLOW-025, DOM-022): moved up or
 * down, the row trades places with the item next to it — past a heading, it leaves its group for the one it now stands
 * in — and its sheet goes with it; none moves the only row of a group. A row put above or below joins its group and is
 * [onInserted]; a heading put above it is [onGroup], by its index. The removal, last and in the error colour, closes the
 * sheet and is offered for undoing.
 */
@Composable
private fun rowActions(
    position: Int,
    place: ItemPlace,
    editor: RecipeEditor,
    onInserted: (Int) -> Unit,
    onGroup: (Int) -> Unit,
): List<SheetAction> {
    val label = RowAction.entries.associateWith { stringResource(it.label) }
    val remove = stringResource(R.string.editor_ingredient_remove)
    // Each action finds its row by its key when it is done, wherever it stands then; a row gone is left alone.
    val key = editor.ingredientKey(position)
    fun now(): Int? = editor.ingredientPosition(key)
    fun move(by: Int) {
        val items = editor.recipe.value?.listItems ?: return
        val at = items.indexOf(ListItem.Row(now() ?: return))
        editor.moveIngredientItem(at, at + by)
    }
    fun action(action: RowAction, leaves: Boolean, onClick: () -> Unit) =
        SheetAction(label.getValue(action), EditorTags.ingredientAction(position, action), leaves = leaves, onClick = onClick)
    return buildList {
        if (place.movable && place.at > 0) add(action(RowAction.MOVE_UP, false) { move(-1) })
        if (place.movable && place.at < place.count - 1) add(action(RowAction.MOVE_DOWN, false) { move(1) })
        add(action(RowAction.INSERT_ABOVE, true) { now()?.let { if (editor.insertIngredient(it)) onInserted(it) } })
        add(action(RowAction.INSERT_BELOW, true) { now()?.let { if (editor.insertIngredient(it + 1, intoGroupAbove = true)) onInserted(it + 1) } })
        if (!place.startsGroup) {
            add(
                action(RowAction.GROUP_ABOVE, true) {
                    val at = now() ?: return@action
                    val index = editor.recipe.value?.ingredientGroups?.count { it.firstPosition < at }
                    if (editor.addGroup(at) && index != null) onGroup(index)
                },
            )
        }
        add(SheetAction(remove, EditorTags.ingredientRemove(position), leaves = true, destructive = true) { now()?.let(editor::removeIngredient) })
    }
}

/**
 * The heading of the group at [index], standing at item [at] of [items] (DOM-022): its name — or, while it has none, a
 * muted placeholder — and the mark of a name over its limit, as one target at least 48 dp tall whose tap ([onOpen])
 * opens its sheet; dragged by its column, the heading carries its rows with it.
 */
@Composable
private fun GroupHeading(
    index: Int,
    at: Int,
    items: List<ListItem>,
    recipe: Recipe,
    editor: RecipeEditor,
    drag: RowDrag,
    onOpen: () -> Unit,
) {
    val name = recipe.ingredientGroups[index].name
    val span = blockAt(items, at)
    val edit = stringResource(R.string.editor_group_edit)
    Box(Modifier.fillMaxWidth().then(drag.row(at, MaterialTheme.colorScheme.surface)).testTag(EditorTags.group(index))) {
        // A heading with no rows yet has no handle: it stays last until rows fill its group. Its column stays, empty.
        DragColumnBlock(drag, at, items.size, EditorTags.groupHandle(index), EditorTags.groupHandleIcon(index), span, groupLanding(items, at, span), still = !items.movable(at)) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable(onClickLabel = edit, role = Role.Button, onClick = onOpen)
                    .semantics { heading() }
                    .padding(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 12.dp)
                    .testTag(GroupSheetTags.line(index)),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    name.ifBlank { stringResource(R.string.editor_group_unnamed) },
                    style = MaterialTheme.typography.titleMedium,
                    color = if (name.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
                )
                BoundMark(editor, RecipePart.GroupName(index), EditorTags.groupMark(index), gap = 4.dp)
            }
        }
    }
}

/**
 * The actions of the heading at [index], whose key is [key], in its sheet: moving the group, heading and rows, past the
 * group above or below it — never above the rows no group owns, nor past a group with no rows yet — and its removal,
 * last and in the error colour, which leaves its rows where they are, in the group above or in none, and is offered for
 * undoing.
 */
@Composable
private fun groupActions(index: Int, key: Long, recipe: Recipe, editor: RecipeEditor): List<SheetAction> {
    val up = stringResource(RowAction.MOVE_UP.label)
    val down = stringResource(RowAction.MOVE_DOWN.label)
    val remove = stringResource(R.string.editor_group_remove)
    val filled = !recipe.groupRows(index).isEmpty()
    // Each action finds its heading by its [key] when it is done, wherever it stands then; a heading gone is left alone.
    fun now(): Int? = editor.groupIndex(key)
    return buildList {
        if (filled && index > 0) {
            add(SheetAction(up, EditorTags.groupAction(index, RowAction.MOVE_UP)) { now()?.let { editor.moveGroup(it, down = false) } })
        }
        if (filled && index + 1 < recipe.ingredientGroups.size && !recipe.groupRows(index + 1).isEmpty()) {
            add(SheetAction(down, EditorTags.groupAction(index, RowAction.MOVE_DOWN)) { now()?.let { editor.moveGroup(it, down = true) } })
        }
        add(SheetAction(remove, EditorTags.groupRemove(index), leaves = true, destructive = true) { now()?.let(editor::removeGroup) })
    }
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
