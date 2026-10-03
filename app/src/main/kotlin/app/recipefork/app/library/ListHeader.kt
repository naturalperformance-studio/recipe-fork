package app.recipefork.app.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import app.recipefork.app.card.IngredientEntries
import app.recipefork.app.dictionary.IngredientEntryEntity
import app.recipefork.app.dictionary.IngredientOffer
import app.recipefork.app.dictionary.TagEntity
import app.recipefork.app.dictionary.TagSource
import app.recipefork.app.dictionary.label
import app.recipefork.app.editor.numberLanguage
import app.recipefork.app.recipe.BookFilters
import app.recipefork.app.recipe.BookLanguage
import app.recipefork.app.recipe.Listed
import app.recipefork.core.scaling.InterfaceLanguage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import app.recipefork.app.R
import app.recipefork.app.editor.BlockAction
import app.recipefork.app.editor.interfaceLanguage
import app.recipefork.app.recipe.ListingParams
import app.recipefork.core.recipe.RecipeLanguage
import app.recipefork.core.search.BookOrder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.util.Locale

/**
 * What is typed in a view's search field, as the field shows it — the text and where the cursor stands — handed to the
 * session as it changes, so that the query outlives the field (FLOW-035 (4), FLOW-058).
 */
@Stable
class SearchField(initial: String, private val onQuery: (String) -> Unit) {
    var value by mutableStateOf(TextFieldValue(initial, TextRange(initial.length)))
        private set

    fun type(typed: TextFieldValue) {
        val changed = typed.text != value.text
        value = typed
        if (changed) onQuery(typed.text)
    }

    fun clear() = type(TextFieldValue(""))
}

/** The search field of [view], opening on the query the session holds for it. */
@Composable
internal fun rememberSearchField(view: LibraryView, session: LibrarySession): SearchField =
    remember(view, session) { SearchField(session.query(view).value) { session.search(view, it) } }

/** What [view] is listed by, as the session holds it, in the collation of the interface language. */
@Composable
internal fun rememberListingParams(view: LibraryView, session: LibrarySession): Flow<ListingParams> {
    val locale = if (interfaceLanguage(LocalConfiguration.current) == RecipeLanguage.RU) RUSSIAN else Locale.ENGLISH
    return remember(view, session, locale) {
        combine(session.query(view), session.order(view), session.filters(view)) { query, order, filters ->
            ListingParams(query, order, locale, filters)
        }
    }
}

private val RUSSIAN: Locale = Locale.forLanguageTag(RecipeLanguage.RU.name.lowercase())

/**
 * The header above a view's list: its search field (FLOW-035 (4)) and, at its end, the control that adds a filter
 * (FLOW-035, CAT-002, CAT-006) and the choice of the view's order (FLOW-035 (3)); under them, while any filter is on,
 * the row of the view's filters. The [languages] of the view's recipes are what its language filter offers (L10N-005);
 * the entries they [hold] keep an entry removed upstream offered by its ingredient filter (FLOW-001 (7)). It never
 * scrolls with the list, and is no taller for filters while none is on.
 */
@Composable
internal fun ListHeader(
    search: SearchField,
    order: BookOrder,
    onOrder: (BookOrder) -> Unit,
    filters: BookFilters,
    onFilters: (BookFilters) -> Unit,
    choices: FilterChoices,
    languages: List<BookLanguage>,
    hold: Set<Long> = emptySet(),
    modifier: Modifier = Modifier,
) {
    val focus = LocalFocusManager.current
    Column(modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 8.dp).testTag(LibraryTags.HEADER)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = search.value,
                onValueChange = search::type,
                singleLine = true,
                label = { Text(stringResource(R.string.library_search)) },
                trailingIcon = if (search.value.text.isEmpty()) null else {
                    {
                        BlockAction(
                            R.drawable.ic_clear_search,
                            stringResource(R.string.library_search_clear),
                            LibraryTags.SEARCH_CLEAR,
                            onClick = search::clear,
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                modifier = Modifier.weight(1f).testTag(LibraryTags.SEARCH),
            )
            AddFilter(filters, onFilters, choices, languages, hold)
            OrderChoice(order, onOrder)
        }
        if (filters.any) FilterRow(filters, onFilters, choices)
    }
}

/**
 * The view's order: a button named by the order in force, for the screen reader and in a tooltip, that opens the menu
 * of the three orders, the one in force selected.
 */
@Composable
private fun OrderChoice(order: BookOrder, onOrder: (BookOrder) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        BlockAction(
            R.drawable.ic_order,
            stringResource(R.string.library_order, stringResource(orderLabel(order))),
            LibraryTags.ORDER,
            onClick = { expanded = !expanded },
            action = stringResource(R.string.library_order_action),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            BookOrder.entries.forEach { choice ->
                DropdownMenuItem(
                    text = { Text(stringResource(orderLabel(choice))) },
                    leadingIcon = { RadioButton(selected = choice == order, onClick = null) },
                    onClick = {
                        expanded = false
                        onOrder(choice)
                    },
                    modifier = Modifier.semantics { selected = choice == order }.testTag(LibraryTags.order(choice)),
                )
            }
        }
    }
}

@StringRes
private fun orderLabel(order: BookOrder): Int = when (order) {
    BookOrder.NEWEST -> R.string.library_order_newest
    BookOrder.OLDEST -> R.string.library_order_oldest
    BookOrder.TITLE -> R.string.library_order_title
}

/**
 * A view narrowed to nothing says so — naming the query, when one was searched — and offers to clear the search, and
 * the filters when any is on (FLOW-035 (4)).
 */
@Composable
internal fun NothingFound(listed: Listed<*>, onClearSearch: () -> Unit, onClearFilters: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            if (listed.searched) {
                stringResource(R.string.library_nothing_found, listed.params.query.trim())
            } else {
                stringResource(R.string.library_nothing_matches)
            },
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.testTag(LibraryTags.NOTHING_FOUND),
        )
        if (listed.searched) {
            TextButton(
                onClick = onClearSearch,
                modifier = Modifier.heightIn(min = 48.dp).testTag(LibraryTags.NOTHING_FOUND_CLEAR),
            ) { Text(stringResource(R.string.library_search_clear)) }
        }
        if (listed.params.filters.any) {
            TextButton(
                onClick = onClearFilters,
                modifier = Modifier.heightIn(min = 48.dp).testTag(LibraryTags.NOTHING_FOUND_CLEAR_FILTERS),
            ) { Text(stringResource(R.string.library_filter_clear)) }
        }
    }
}

/**
 * What the filter row offers from the device's dictionaries: the ingredient entries and the tags, each read the first
 * time it is needed — to name a filter that is on, or to offer a choice — off the main thread, and null until it is.
 * A dictionary that cannot be read is tried again the next time it is needed.
 */
@Stable
class FilterChoices(private val ingredientSource: IngredientEntries, private val tagSource: TagSource) {
    var ingredients by mutableStateOf<List<IngredientEntryEntity>?>(null)
        private set
    var tags by mutableStateOf<List<TagEntity>?>(null)
        private set

    suspend fun readIngredients() {
        if (ingredients == null) ingredients = read { ingredientSource.entries() }
    }

    suspend fun readTags() {
        if (tags == null) tags = read { tagSource.tags() }
    }

    private suspend fun <T> read(source: suspend () -> List<T>): List<T>? = try {
        withContext(Dispatchers.Default) { source() }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
}

@Composable
internal fun rememberFilterChoices(ingredients: IngredientEntries, tags: TagSource): FilterChoices =
    remember(ingredients, tags) { FilterChoices(ingredients, tags) }

/** Which chooser of a filter is open. */
private enum class Chooser { INGREDIENT, TAGS, LANGUAGE }

/**
 * The control that adds a filter, beside the search field: a button named for the screen reader and in a tooltip, that
 * opens the menu of the three kinds — an ingredient, tags and a language — and then the chooser of the kind picked.
 */
@Composable
private fun AddFilter(
    filters: BookFilters,
    onFilters: (BookFilters) -> Unit,
    choices: FilterChoices,
    languages: List<BookLanguage>,
    hold: Set<Long>,
) {
    var expanded by remember { mutableStateOf(false) }
    var choosing by rememberSaveable { mutableStateOf<Chooser?>(null) }
    LaunchedEffect(choosing) {
        when (choosing) {
            Chooser.INGREDIENT -> choices.readIngredients()
            Chooser.TAGS -> choices.readTags()
            Chooser.LANGUAGE, null -> Unit
        }
    }
    Box {
        BlockAction(
            R.drawable.ic_filter,
            stringResource(R.string.library_filter_add),
            LibraryTags.FILTER_ADD,
            onClick = { expanded = !expanded },
            action = stringResource(R.string.library_filter_add_action),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.library_filter_ingredient)) },
                onClick = {
                    expanded = false
                    choosing = Chooser.INGREDIENT
                },
                modifier = Modifier.testTag(LibraryTags.FILTER_BY_INGREDIENT),
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.library_filter_tags)) },
                onClick = {
                    expanded = false
                    choosing = Chooser.TAGS
                },
                modifier = Modifier.testTag(LibraryTags.FILTER_BY_TAGS),
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.library_filter_language)) },
                onClick = {
                    expanded = false
                    choosing = Chooser.LANGUAGE
                },
                modifier = Modifier.testTag(LibraryTags.FILTER_BY_LANGUAGE),
            )
        }
    }
    when (choosing) {
        Chooser.INGREDIENT -> IngredientChooser(
            choices.ingredients,
            hold,
            onPick = {
                choosing = null
                onFilters(filters.copy(ingredient = it))
            },
            onDismiss = { choosing = null },
        )
        Chooser.TAGS -> TagChooser(choices.tags, filters, onFilters, onDismiss = { choosing = null })
        Chooser.LANGUAGE -> LanguageChooser(
            languages,
            filters.language,
            onPick = {
                choosing = null
                onFilters(filters.copy(language = it))
            },
            onDismiss = { choosing = null },
        )
        null -> Unit
    }
}

/**
 * The filters on, under the search field, while any is: a chip for each — the ingredient, each tag, then the language — which
 * removes it when tapped, and the one control that clears them all.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterRow(filters: BookFilters, onFilters: (BookFilters) -> Unit, choices: FilterChoices) {
    val needsIngredients = filters.ingredient != null
    val needsTags = filters.tags.isNotEmpty()
    LaunchedEffect(needsIngredients) { if (needsIngredients) choices.readIngredients() }
    LaunchedEffect(needsTags) { if (needsTags) choices.readTags() }
    val language = numberLanguage(LocalConfiguration.current)
    FlowRow(
        Modifier.fillMaxWidth().padding(end = 12.dp).testTag(LibraryTags.FILTERS),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.Center,
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        filters.ingredient?.let { id ->
            val name = choices.ingredients?.firstOrNull { it.id == id }?.label(language)
                ?: stringResource(R.string.library_filter_unknown_ingredient)
            FilterChipOn(name, LibraryTags.INGREDIENT_FILTER) { onFilters(filters.copy(ingredient = null)) }
        }
        filters.tags.sorted().forEach { id ->
            val name = choices.tags?.firstOrNull { it.id == id }?.label(language)
                ?: stringResource(R.string.library_filter_unknown_tag)
            FilterChipOn(name, LibraryTags.tagFilter(id)) { onFilters(filters.toggled(id)) }
        }
        filters.language?.let { chosen ->
            val name = stringResource(
                if (chosen == BookLanguage.NOT_GIVEN) R.string.library_filter_language_none_chip else languageLabel(chosen),
            )
            FilterChipOn(name, LibraryTags.LANGUAGE_FILTER) { onFilters(filters.copy(language = null)) }
        }
        TextButton(
            onClick = { onFilters(BookFilters()) },
            modifier = Modifier.heightIn(min = 48.dp).testTag(LibraryTags.FILTERS_CLEAR),
        ) { Text(stringResource(R.string.library_filter_clear)) }
    }
}

/** A filter that is on, named; tapping it removes it. */
@Composable
private fun FilterChipOn(name: String, tag: String, onRemove: () -> Unit) {
    InputChip(
        selected = true,
        onClick = onRemove,
        label = { Text(name) },
        trailingIcon = {
            Icon(
                painterResource(R.drawable.ic_clear_search),
                contentDescription = stringResource(R.string.library_filter_remove),
                modifier = Modifier.size(InputChipDefaults.IconSize),
            )
        },
        modifier = Modifier.testTag(tag),
    )
}

/**
 * The choice of the ingredient to filter by: its field offers the dictionary's entries as the editor's ingredient field
 * does (FLOW-057), and picking one filters by it. Free text is never offered: it names no entry (CAT-006). An entry
 * removed upstream is offered only while a recipe of the view [hold]s it (FLOW-001 (7)).
 */
@Composable
private fun IngredientChooser(
    dictionary: List<IngredientEntryEntity>?,
    hold: Set<Long>,
    onPick: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var typed by rememberSaveable { mutableStateOf("") }
    val language = numberLanguage(LocalConfiguration.current)
    val offered = dictionary?.let { IngredientOffer.of(typed, it, language, stillHeld = hold).entries }.orEmpty()
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(LibraryTags.INGREDIENT_CHOOSER),
        title = { Text(stringResource(R.string.library_filter_ingredient_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.library_filter_ingredient_hint), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.library_filter_ingredient_field)) },
                    modifier = Modifier.fillMaxWidth().testTag(LibraryTags.INGREDIENT_FIELD),
                )
                Column(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
                    offered.forEach { entry ->
                        val label = entry.label(language)
                        Text(
                            label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clickable(
                                    onClickLabel = stringResource(R.string.library_filter_ingredient_pick_action, label),
                                    role = Role.Button,
                                ) { onPick(entry.id) }
                                .padding(horizontal = 8.dp, vertical = 12.dp)
                                .testTag(LibraryTags.ingredientChoice(entry.id)),
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.library_filter_cancel))
            }
        },
    )
}

/** The choice of the tags to filter by: every tag of the dictionary, each chosen or not; a recipe must carry them all. */
@Composable
private fun TagChooser(
    dictionary: List<TagEntity>?,
    filters: BookFilters,
    onFilters: (BookFilters) -> Unit,
    onDismiss: () -> Unit,
) {
    val language = numberLanguage(LocalConfiguration.current)
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(LibraryTags.TAG_CHOOSER),
        title = { Text(stringResource(R.string.library_filter_tags_title)) },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.library_filter_tags_hint), style = MaterialTheme.typography.bodyMedium)
                if (dictionary != null && dictionary.isEmpty()) {
                    Text(stringResource(R.string.library_filter_tags_none), style = MaterialTheme.typography.bodyMedium)
                }
                dictionary.orEmpty().forEach { tag ->
                    val chosen = tag.id in filters.tags
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .toggleable(value = chosen, role = Role.Checkbox) { onFilters(filters.toggled(tag.id)) }
                            .testTag(LibraryTags.tagChoice(tag.id)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = chosen, onCheckedChange = null)
                        Text(tag.label(language), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp).testTag(LibraryTags.TAG_CHOOSER_DONE)) {
                Text(stringResource(R.string.library_filter_done))
            }
        },
    )
}

/**
 * The choice of the language to filter by (L10N-005): each language the view's recipes are written in, named in the
 * interface language, and "not given" when some declare none; picking one keeps the recipes written in it.
 */
@Composable
private fun LanguageChooser(
    languages: List<BookLanguage>,
    chosen: BookLanguage?,
    onPick: (BookLanguage) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(LibraryTags.LANGUAGE_CHOOSER),
        title = { Text(stringResource(R.string.library_filter_language_title)) },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.library_filter_language_hint), style = MaterialTheme.typography.bodyMedium)
                languages.forEach { language ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .selectable(selected = language == chosen, role = Role.RadioButton) { onPick(language) }
                            .testTag(LibraryTags.languageChoice(language)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = language == chosen, onClick = null)
                        Text(stringResource(languageLabel(language)), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.library_filter_cancel))
            }
        },
    )
}

@StringRes
private fun languageLabel(language: BookLanguage): Int = when (language) {
    BookLanguage.RU -> R.string.library_filter_language_ru
    BookLanguage.EN -> R.string.library_filter_language_en
    BookLanguage.NOT_GIVEN -> R.string.library_filter_language_none
}

/** The label of this tag in the interface [language]. */
private fun TagEntity.label(language: InterfaceLanguage): String =
    if (language == InterfaceLanguage.RUSSIAN) labelRu else labelEn
