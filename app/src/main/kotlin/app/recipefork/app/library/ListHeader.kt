package app.recipefork.app.library

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.painterResource
import app.recipefork.app.card.IngredientEntries
import app.recipefork.app.dictionary.IngredientEntryEntity
import app.recipefork.app.dictionary.TagEntity
import app.recipefork.app.dictionary.TagSource
import app.recipefork.app.dictionary.label
import app.recipefork.app.editor.numberLanguage
import app.recipefork.app.recipe.BookFilters
import app.recipefork.app.recipe.BookLanguage
import app.recipefork.app.recipe.Listed
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
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
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

/** The gap between the line of the search field and the row of filter chips under it. */
internal val FILTER_ROW_GAP = 12.dp

private val RUSSIAN: Locale = Locale.forLanguageTag(RecipeLanguage.RU.name.lowercase())

/**
 * The header above a view's list: its search field (FLOW-035 (4)) and, under it, [FILTER_ROW_GAP] apart, the row of
 * quick filters (FLOW-035, CAT-002, CAT-006), whose "All filters" opens the sheet of every filter and whose second chip
 * chooses the view's order (FLOW-035 (3)). The [languages] of the view's recipes are what its language filter offers (L10N-005); the entries
 * they [hold] keep an entry removed upstream offered by its ingredient filter (FLOW-001 (7)); the tags they [carries]
 * most are offered as quick filters; the sheet's equipment section is My [kitchen]'s ([SheetKitchen]), left out while
 * it is not known yet. It stands at the top of its view's page, under the tabs, and scrolls away as the list goes down
 * and back as it goes up ([ScrollAwayHeader]). While the book's selection is on it is not [enabled]: nothing in it
 * responds (v0.38).
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
    carries: Map<String, List<Long>> = emptyMap(),
    kitchen: SheetKitchen? = null,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    Column(
        modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 8.dp).testTag(LibraryTags.HEADER),
        verticalArrangement = Arrangement.spacedBy(FILTER_ROW_GAP),
    ) {
        OutlinedTextField(
            value = search.value,
            onValueChange = search::type,
            enabled = enabled,
            singleLine = true,
            // What is typed and the hint are set in the working face, Golos, whose narrower letters keep the whole hint
            // on a 360 dp phone (T-304).
            textStyle = MaterialTheme.typography.titleSmall,
            // A short label on one line, which the field keeps room for above its outline when it rises there; the
            // longer words stand inside as its placeholder, cut short only where even they do not fit.
            label = {
                Text(
                    stringResource(R.string.library_search),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag(LibraryTags.SEARCH_LABEL),
                )
            },
            placeholder = {
                Text(
                    stringResource(R.string.library_search_placeholder),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag(LibraryTags.SEARCH_PLACEHOLDER),
                )
            },
            trailingIcon = if (search.value.text.isEmpty()) null else {
                {
                    BlockAction(
                        R.drawable.ic_clear_search,
                        stringResource(R.string.library_search_clear),
                        LibraryTags.SEARCH_CLEAR,
                        onClick = search::clear,
                        enabled = enabled,
                    )
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
            // The field ends a gutter from the screen's edge: nothing stands beside it, so the whole hint fits (T-304).
            modifier = Modifier.fillMaxWidth().padding(end = 12.dp).testTag(LibraryTags.SEARCH),
        )
        QuickFilters(
            filters, onFilters, choices, languages, carries, onAll = { sheetOpen = true }, order = order, onOrder = onOrder,
            enabled = enabled,
        )
    }
    if (sheetOpen) FiltersSheet(filters, onFilters, choices, languages, hold, kitchen, onClose = { sheetOpen = false })
}

/**
 * The view's order (FLOW-035 (3)): a chip reading the order in force in words, with a chevron, that opens the menu of
 * the three orders, the one in force selected. The screen reader hears the order in force and, as the chip's click
 * label, the action of choosing another (T-304).
 */
@Composable
private fun OrderChip(order: BookOrder, onOrder: (BookOrder) -> Unit, enabled: Boolean = true) {
    var expanded by remember { mutableStateOf(false) }
    val action = stringResource(R.string.library_order_action)
    val toggle = { expanded = !expanded }
    Box {
        AssistChip(
            onClick = toggle,
            enabled = enabled,
            label = { Text(stringResource(orderLabel(order))) },
            trailingIcon = {
                Icon(
                    painterResource(R.drawable.ic_expand_more),
                    contentDescription = null,
                    modifier = Modifier.size(AssistChipDefaults.IconSize),
                )
            },
            // The click label stands on the chip's own node; a chip that does not respond offers no action to name.
            modifier = Modifier
                .semantics { if (enabled) onClick(label = action) { toggle(); true } }
                .chipTarget(LibraryTags.ORDER),
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
 * A view narrowed to nothing says so — naming the query, when one was searched — and offers one way out of it
 * (FLOW-035 (4), FLOW-042 (1)): clearing the search, the filters, or both when both are on. Narrowed by the filter
 * "Enough equipment" alone, it says the kitchen lacks something for every recipe, and offers to switch that filter off.
 */
@Composable
internal fun NothingFound(listed: Listed<*>, onClearSearch: () -> Unit, onClearFilters: () -> Unit, modifier: Modifier = Modifier) {
    val searched = listed.searched
    val filtered = listed.params.filters.any
    if (!searched && listed.params.filters == BookFilters(equipment = true)) {
        EmptyState(
            picture = R.drawable.ic_filter,
            headline = stringResource(R.string.library_nothing_equipment),
            sentence = null,
            action = EmptyAction(
                stringResource(R.string.library_filter_equipment_off), null, LibraryTags.NOTHING_FOUND_EQUIPMENT_OFF, onClearFilters,
            ),
            tag = LibraryTags.NOTHING_FOUND,
            modifier = modifier,
        )
        return
    }
    val action = when {
        searched && filtered -> EmptyAction(
            stringResource(R.string.library_search_and_filters_clear), null, LibraryTags.NOTHING_FOUND_CLEAR_ALL,
        ) {
            onClearFilters()
            onClearSearch()
        }
        searched -> EmptyAction(stringResource(R.string.library_search_clear), null, LibraryTags.NOTHING_FOUND_CLEAR, onClearSearch)
        else -> EmptyAction(stringResource(R.string.library_filter_clear), null, LibraryTags.NOTHING_FOUND_CLEAR_FILTERS, onClearFilters)
    }
    EmptyState(
        picture = if (searched) R.drawable.ic_search_off else R.drawable.ic_filter,
        headline = if (searched) {
            stringResource(R.string.library_nothing_found, listed.params.query.trim())
        } else {
            stringResource(R.string.library_nothing_matches)
        },
        sentence = null,
        action = action,
        tag = LibraryTags.NOTHING_FOUND,
        modifier = modifier,
    )
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
        // A read of the device's database, on the threads kept for waiting on it, as the kitchen's is.
        withContext(Dispatchers.IO) { source() }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
}

@Composable
internal fun rememberFilterChoices(ingredients: IngredientEntries, tags: TagSource): FilterChoices =
    remember(ingredients, tags) { FilterChoices(ingredients, tags) }

/** How many of the most used tags the row of quick filters offers. */
private const val QUICK_TAGS = 5

/**
 * The tags the recipes of a view [carries] most, the most used first and, among equally used, the lower identity first;
 * those in [excluded] left out.
 */
internal fun mostUsedTags(carries: Map<String, List<Long>>, excluded: Set<Long> = emptySet()): List<Long> =
    carries.values.flatMap { it.distinct() }.filter { it !in excluded }.groupingBy { it }.eachCount().entries
        .sortedWith(compareByDescending<Map.Entry<Long, Int>> { it.value }.thenBy { it.key })
        .map { it.key }

/**
 * The row of quick filters under the search field, scrolled sideways: "All filters", which opens [FiltersSheet]; the
 * view's [order] in words ([OrderChip]); a selected chip for each filter in force — the ingredient, each tag, the
 * language, then "Enough equipment" — which removes it when tapped, and, while any is, the one control that clears them all; then the quick offers, each applied by one tap: the other
 * languages of the view while it holds more than one, and the tags its recipes carry most, up to [QUICK_TAGS].
 */
@Composable
private fun QuickFilters(
    filters: BookFilters,
    onFilters: (BookFilters) -> Unit,
    choices: FilterChoices,
    languages: List<BookLanguage>,
    carries: Map<String, List<Long>>,
    onAll: () -> Unit,
    order: BookOrder,
    onOrder: (BookOrder) -> Unit,
    enabled: Boolean = true,
) {
    val popular = remember(carries, filters.tags) { mostUsedTags(carries, filters.tags) }
    val needsIngredients = filters.ingredient != null
    val needsTags = filters.tags.isNotEmpty() || popular.isNotEmpty()
    LaunchedEffect(needsIngredients) { if (needsIngredients) choices.readIngredients() }
    LaunchedEffect(needsTags) { if (needsTags) choices.readTags() }
    val language = numberLanguage(LocalConfiguration.current)
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(end = 12.dp).testTag(LibraryTags.FILTERS),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AssistChip(
            onClick = onAll,
            enabled = enabled,
            label = { Text(stringResource(R.string.library_filter_add)) },
            leadingIcon = {
                Icon(painterResource(R.drawable.ic_filter), contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize))
            },
            modifier = Modifier.chipTarget(LibraryTags.FILTER_ADD),
        )
        OrderChip(order, onOrder, enabled)
        filters.ingredient?.let { id ->
            val name = choices.ingredients?.firstOrNull { it.id == id }?.label(language)
                ?: stringResource(R.string.library_filter_unknown_ingredient)
            FilterOn(name, LibraryTags.INGREDIENT_FILTER, enabled) { onFilters(filters.copy(ingredient = null)) }
        }
        filters.tags.sorted().forEach { id ->
            val name = choices.tags?.firstOrNull { it.id == id }?.label(language)
                ?: stringResource(R.string.library_filter_unknown_tag)
            FilterOn(name, LibraryTags.tagFilter(id), enabled) { onFilters(filters.toggled(id)) }
        }
        filters.language?.let { chosen ->
            FilterOn(stringResource(languageChipLabel(chosen)), LibraryTags.LANGUAGE_FILTER, enabled) { onFilters(filters.copy(language = null)) }
        }
        if (filters.equipment) {
            FilterOn(stringResource(R.string.library_filter_equipment_enough), LibraryTags.EQUIPMENT_FILTER, enabled) {
                onFilters(filters.copy(equipment = false))
            }
        }
        if (filters.any) {
            TextButton(
                onClick = { onFilters(BookFilters()) },
                enabled = enabled,
                modifier = Modifier.heightIn(min = 48.dp).testTag(LibraryTags.FILTERS_CLEAR),
            ) { Text(stringResource(R.string.library_filter_clear)) }
        }
        if (languages.size > 1) {
            languages.filter { it != filters.language }.forEach { offer ->
                FilterOffer(stringResource(languageChipLabel(offer)), LibraryTags.languageOffer(offer), enabled) {
                    onFilters(filters.copy(language = offer))
                }
            }
        }
        val known = choices.tags.orEmpty().associateBy { it.id }
        popular.mapNotNull { known[it] }.take(QUICK_TAGS).forEach { tag ->
            FilterOffer(tag.label(language), LibraryTags.tagOffer(tag.id), enabled) { onFilters(filters.toggled(tag.id)) }
        }
    }
}

@StringRes
private fun languageChipLabel(language: BookLanguage): Int =
    if (language == BookLanguage.NOT_GIVEN) R.string.library_filter_language_none_chip else languageLabel(language)

/** A filter in force, named and selected; tapping it removes it. */
@Composable
private fun FilterOn(name: String, tag: String, enabled: Boolean, onRemove: () -> Unit) {
    FilterChip(
        selected = true,
        onClick = onRemove,
        enabled = enabled,
        label = { Text(name) },
        trailingIcon = {
            Icon(
                painterResource(R.drawable.ic_clear_search),
                contentDescription = stringResource(R.string.library_filter_remove),
                modifier = Modifier.size(FilterChipDefaults.IconSize),
            )
        },
        modifier = Modifier.chipTarget(tag),
    )
}

/**
 * A chip drawn at its own 32 dp, with a touch target of at least 48 by 48 dp around it (A11Y-003). The chip's semantics
 * merge outside the target, so that the element the screen reader and the touch-target check find, tagged [tag], is
 * the target, not the chip drawn within it — as the editor's tags do.
 */
private fun Modifier.chipTarget(tag: String): Modifier =
    semantics(mergeDescendants = true) {}.testTag(tag).minimumInteractiveComponentSize()

/** A filter offered, not selected; tapping it applies it. */
@Composable
private fun FilterOffer(name: String, tag: String, enabled: Boolean, onApply: () -> Unit) {
    FilterChip(selected = false, onClick = onApply, enabled = enabled, label = { Text(name) }, modifier = Modifier.chipTarget(tag))
}
