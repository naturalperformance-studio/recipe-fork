package app.recipefork.app.editor

import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.recipefork.app.R
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

/** The ingredient list, after the serving count (FLOW-024): rows of three fields each (FLOW-023), appended and deleted. */
@Composable
internal fun Ingredients(recipe: Recipe, editor: RecipeEditor) {
    val editable = recipe.state.editable
    val language = numberLanguage(LocalConfiguration.current)
    Column(Modifier.fillMaxWidth().testTag(EditorTags.INGREDIENTS), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(R.string.editor_ingredients),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        recipe.ingredients.forEachIndexed { position, row ->
            if (editable) IngredientRow(position, row, editor, language) else ReadOnlyIngredient(position, row, language)
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
private fun ReadOnlyIngredient(position: Int, row: Ingredient, language: InterfaceLanguage) {
    Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.testTag(EditorTags.ingredient(position))) {
        Text(
            row.freeTextName?.takeIf { it.isNotBlank() } ?: stringResource(R.string.editor_ingredient_unnamed),
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

/**
 * One row: exactly the ingredient, the quantity and the unit (FLOW-023 (1)) — never a unit type, which follows from
 * the unit (FLOW-023 (7)). Beside them, only what the row calls for: the free-text warning (FLOW-022 (2)), the
 * divisibility question of a `piece` (FLOW-022 (5)) and the mark of a quantity the field does not hold (DOM-017 (6)).
 */
@Composable
private fun IngredientRow(position: Int, row: Ingredient, editor: RecipeEditor, language: InterfaceLanguage) {
    val problem = QuantityInput.problem(row)
    Column(Modifier.fillMaxWidth().testTag(EditorTags.ingredient(position)), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IngredientNameField(position, row, editor, Modifier.weight(1f))
            ActionText(
                text = stringResource(R.string.editor_ingredient_remove),
                action = stringResource(R.string.editor_ingredient_remove_action, position + 1),
                onClick = { editor.removeIngredient(position) },
                modifier = Modifier.testTag(EditorTags.ingredientRemove(position)),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
            // "To taste" withdraws the quantity field (FLOW-023 (8)); it comes back empty with another unit (9).
            if (row.unitType != UnitType.TASTE_UNMEASURED) {
                QuantityField(position, row, editor, language, isError = problem != null, Modifier.weight(1f))
            }
            UnitField(position, row, editor, Modifier.width(160.dp))
        }
        if (problem != null) {
            Text(
                stringResource(problemMessage(problem)),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag(EditorTags.ingredientError(position)),
            )
        }
        // Every name is the author's own words until the dictionary offers entries (T-052).
        if (row.dictionaryEntry == null && !row.freeTextName.isNullOrBlank()) {
            Text(
                stringResource(R.string.editor_ingredient_free_text_warning),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag(EditorTags.ingredientWarning(position)),
            )
        }
        // No entry supplies divisibility yet, so every `piece` row asks for it (the increment's decision log).
        if (row.unitType == UnitType.PIECE) Divisibility(position, row, editor)
    }
}

/** The row's ingredient, typed as the author's own words. Self-contained, so the dictionary's offer can replace it. */
@Composable
private fun IngredientNameField(position: Int, row: Ingredient, editor: RecipeEditor, modifier: Modifier) {
    OutlinedTextField(
        value = row.freeTextName.orEmpty(),
        onValueChange = { editor.setIngredientName(position, it) },
        label = { Text(stringResource(R.string.editor_ingredient_name)) },
        singleLine = true,
        modifier = modifier.testTag(EditorTags.ingredientName(position)),
    )
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
    var typed by remember { mutableStateOf<String?>(null) }
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

/** The row's unit: exactly the twelve units of DOM-016, by their names in the interface language. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnitField(position: Int, row: Ingredient, editor: RecipeEditor, modifier: Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(top = 8.dp)
                .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.extraSmall)
                .semantics(mergeDescendants = true) {}
                .clickable(onClickLabel = stringResource(R.string.editor_ingredient_unit_action), role = Role.DropdownList) {
                    expanded = true
                }
                .testTag(EditorTags.ingredientUnit(position))
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.editor_ingredient_unit), style = MaterialTheme.typography.labelSmall)
                Text(row.shownUnit?.let { stringResource(unitName(it)) }.orEmpty(), style = MaterialTheme.typography.bodyLarge)
            }
            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
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
