package app.recipefork.app.library

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.recipefork.app.R
import app.recipefork.app.dictionary.IngredientEntryEntity
import app.recipefork.app.dictionary.IngredientOffer
import app.recipefork.app.dictionary.TagEntity
import app.recipefork.app.dictionary.TagOffer
import app.recipefork.app.dictionary.label
import app.recipefork.app.editor.numberLanguage
import app.recipefork.app.recipe.BookFilters
import app.recipefork.app.recipe.BookLanguage
import kotlinx.coroutines.launch

/**
 * Every filter of a view in one bottom sheet: a section for the ingredient, one for the tags, one for the language
 * while the view holds recipes of more than one language (counting "not given" as one) and,
 * while [equipmentOffered] — My kitchen has something that counts — one for the equipment. A choice applies at once
 * and the sheet stays open for the next; Done, a swipe down or a tap outside closes it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FiltersSheet(
    filters: BookFilters,
    onFilters: (BookFilters) -> Unit,
    choices: FilterChoices,
    languages: List<BookLanguage>,
    hold: Set<Long>,
    equipmentOffered: Boolean,
    onClose: () -> Unit,
) {
    LaunchedEffect(choices) {
        choices.readIngredients()
        choices.readTags()
    }
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = onClose, sheetState = state, modifier = Modifier.testTag(LibraryTags.FILTERS_SHEET)) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.library_filter_sheet_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            TextButton(
                onClick = { scope.launch { state.hide() }.invokeOnCompletion { onClose() } },
                modifier = Modifier.heightIn(min = 48.dp).testTag(LibraryTags.FILTERS_SHEET_DONE),
            ) { Text(stringResource(R.string.library_filter_done)) }
        }
        Column(
            Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            IngredientSection(choices.ingredients, hold, filters.ingredient) { onFilters(filters.copy(ingredient = it)) }
            HorizontalDivider()
            TagSection(choices.tags, filters, onFilters)
            // A view of fewer than two languages has nothing to filter by language: the section is left out.
            if (languages.size > 1) {
                HorizontalDivider()
                LanguageSection(languages, filters.language) { onFilters(filters.copy(language = it)) }
            }
            if (equipmentOffered) {
                HorizontalDivider()
                EquipmentSection(filters.equipment) { onFilters(filters.copy(equipment = it)) }
            }
        }
    }
}

@Composable
private fun SectionTitle(@StringRes title: Int, @StringRes hint: Int) {
    Text(
        stringResource(title),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.semantics { heading() },
    )
    Text(stringResource(hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** One choice of a section: the whole row, at least 48 dp tall, picks it; [control] shows whether it is chosen. */
@Composable
private fun ChoiceRow(label: String, tag: String, toggle: Modifier, control: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).then(toggle).testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        control()
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 8.dp))
    }
}

/**
 * The ingredient to filter by: its field offers the dictionary's entries as the editor's ingredient field does
 * (FLOW-057); the entry chosen stands first, selected, and tapping it again removes the filter. Free text is never
 * offered: it names no entry (CAT-006). An entry removed upstream is offered only while a recipe of the view [hold]s it
 * (FLOW-001 (7)).
 */
@Composable
private fun IngredientSection(
    dictionary: List<IngredientEntryEntity>?,
    hold: Set<Long>,
    chosen: Long?,
    onChoose: (Long?) -> Unit,
) {
    var typed by rememberSaveable { mutableStateOf("") }
    val language = numberLanguage(LocalConfiguration.current)
    val current = dictionary?.firstOrNull { it.id == chosen }
    val offered = dictionary?.let { IngredientOffer.of(typed, it, language, stillHeld = hold).entries }.orEmpty().filter { it.id != chosen }
    Column(Modifier.testTag(LibraryTags.INGREDIENT_CHOOSER), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(R.string.library_filter_ingredient, R.string.library_filter_ingredient_hint)
        OutlinedTextField(
            value = typed,
            onValueChange = { typed = it },
            singleLine = true,
            label = { Text(stringResource(R.string.library_filter_ingredient_field)) },
            modifier = Modifier.fillMaxWidth().testTag(LibraryTags.INGREDIENT_FIELD),
        )
        Column {
            (listOfNotNull(current) + offered).forEach { entry ->
                val on = entry.id == chosen
                val pick = Modifier.selectable(selected = on, role = Role.RadioButton) {
                    onChoose(if (on) null else entry.id)
                    if (!on) typed = ""
                }
                ChoiceRow(entry.label(language), LibraryTags.ingredientChoice(entry.id), pick) { RadioButton(selected = on, onClick = null) }
            }
        }
    }
}

/**
 * The tags to filter by; a recipe must carry them all. The dictionary holds hundreds of tags, so they are searched,
 * never listed: the tags chosen stand first, checked, and typing offers up to [TagOffer.LIMIT] more whose label has a
 * word beginning with the text, as the editor's tag search does. Picking one checks it and clears the field.
 */
@Composable
private fun TagSection(dictionary: List<TagEntity>?, filters: BookFilters, onFilters: (BookFilters) -> Unit) {
    val language = numberLanguage(LocalConfiguration.current)
    var typed by rememberSaveable { mutableStateOf("") }
    val chosen = dictionary.orEmpty().filter { it.id in filters.tags }.sortedBy { it.label(language).lowercase() }
    val offered = dictionary?.let { TagOffer.of(typed, it, language, excluded = filters.tags) }.orEmpty()
    Column(Modifier.testTag(LibraryTags.TAG_CHOOSER), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(R.string.library_filter_tags, R.string.library_filter_tags_hint)
        if (dictionary != null && dictionary.isEmpty()) {
            Text(stringResource(R.string.library_filter_tags_none), style = MaterialTheme.typography.bodyMedium)
        }
        OutlinedTextField(
            value = typed,
            onValueChange = { typed = it },
            singleLine = true,
            label = { Text(stringResource(R.string.library_filter_tags_field)) },
            modifier = Modifier.fillMaxWidth().testTag(LibraryTags.TAG_FIELD),
        )
        if (offered.isEmpty() && typed.isNotBlank() && dictionary != null) {
            Text(
                stringResource(R.string.library_filter_tags_no_match),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag(LibraryTags.TAG_NO_MATCH),
            )
        }
        Column {
            (chosen + offered).forEach { tag ->
                val on = tag.id in filters.tags
                val toggle = Modifier.toggleable(value = on, role = Role.Checkbox) {
                    onFilters(filters.toggled(tag.id))
                    if (!on) typed = ""
                }
                ChoiceRow(tag.label(language), LibraryTags.tagChoice(tag.id), toggle) { Checkbox(checked = on, onCheckedChange = null) }
            }
        }
    }
}

/**
 * The language to filter by (L10N-005): each language the view's recipes are written in, named in the interface
 * language, and "not given" when some declare none; picking one keeps the recipes written in it, picking it again
 * removes the filter.
 */
@Composable
private fun LanguageSection(languages: List<BookLanguage>, chosen: BookLanguage?, onChoose: (BookLanguage?) -> Unit) {
    Column(Modifier.testTag(LibraryTags.LANGUAGE_CHOOSER), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(R.string.library_filter_language, R.string.library_filter_language_hint)
        Column {
            languages.forEach { language ->
                val on = language == chosen
                val pick = Modifier.selectable(selected = on, role = Role.RadioButton) { onChoose(if (on) null else language) }
                ChoiceRow(stringResource(languageLabel(language)), LibraryTags.languageChoice(language), pick) {
                    RadioButton(selected = on, onClick = null)
                }
            }
        }
    }
}

/**
 * The filter "Enough equipment": checked, it hides every recipe naming an entry of equipment My kitchen lacks; an item
 * of free text hides none.
 */
@Composable
private fun EquipmentSection(on: Boolean, onChoose: (Boolean) -> Unit) {
    Column(Modifier.testTag(LibraryTags.EQUIPMENT_CHOOSER), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(R.string.library_filter_equipment, R.string.library_filter_equipment_hint)
        val toggle = Modifier.toggleable(value = on, role = Role.Checkbox) { onChoose(it) }
        ChoiceRow(stringResource(R.string.library_filter_equipment_enough), LibraryTags.EQUIPMENT_CHOICE, toggle) {
            Checkbox(checked = on, onCheckedChange = null)
        }
    }
}

@StringRes
internal fun languageLabel(language: BookLanguage): Int = when (language) {
    BookLanguage.RU -> R.string.library_filter_language_ru
    BookLanguage.EN -> R.string.library_filter_language_en
    BookLanguage.NOT_GIVEN -> R.string.library_filter_language_none
}
