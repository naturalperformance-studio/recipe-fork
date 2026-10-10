package app.recipefork.app.kitchen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.triStateToggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.recipefork.app.R
import app.recipefork.app.RecipeForkApplication
import app.recipefork.app.dictionary.EquipmentEntryEntity
import app.recipefork.app.dictionary.EquipmentOffer
import app.recipefork.app.dictionary.EquipmentSource
import app.recipefork.app.dictionary.label
import app.recipefork.app.library.EmptyAction
import app.recipefork.app.library.EmptyState
import app.recipefork.app.settings.servingsLanguage
import app.recipefork.core.scaling.InterfaceLanguage

/** What My kitchen reads and writes: the ticked set, and the equipment dictionary it lists. */
class KitchenEnvironment(val store: KitchenStore, val equipment: EquipmentSource)

/** The kitchen of the composition root: the device's ticked set and the held dictionaries. */
@Composable
fun rememberKitchenEnvironment(): KitchenEnvironment {
    val context = LocalContext.current.applicationContext
    return remember(context) {
        (context as RecipeForkApplication).container.let { KitchenEnvironment(it.kitchen, it.dictionaries) }
    }
}

/**
 * My kitchen, a destination above the settings: the equipment dictionary by its groups, each item a checkbox row the
 * reader ticks when the kitchen has it, each group a row that ticks or unticks all its items at once. A search — in
 * either language, by synonyms, as the editor's equipment field finds — narrows the list to the items found. What is
 * ticked is kept at once ([KitchenStore]). Back — the bar's or the system's — returns to the settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KitchenScreen(kitchen: KitchenEnvironment, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val dictionary by produceState<List<EquipmentEntryEntity>?>(null, kitchen.equipment) { value = kitchen.equipment.equipment() }
    val held by kitchen.store.held.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    val language = servingsLanguage()
    val listed = rememberLazyListState()
    val results = rememberLazyListState()
    Scaffold(
        modifier = modifier.fillMaxSize().testTag(KitchenTags.SCREEN),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.kitchen_title), Modifier.semantics { heading() }.testTag(KitchenTags.TITLE)) },
                navigationIcon = {
                    val action = stringResource(R.string.kitchen_back_action)
                    IconButton(
                        onClick = onBack,
                        // Material's icon button lays out at 40 dp; a target is 48 by 48 dp at least (A11Y-003).
                        modifier = Modifier
                            .size(48.dp)
                            .semantics {
                                onClick(label = action) {
                                    onBack()
                                    true
                                }
                            }
                            .testTag(KitchenTags.BACK),
                    ) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.kitchen_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).imePadding().fillMaxSize()) {
            SearchField(query, onQuery = { query = it })
            val entries = dictionary
            when {
                entries == null -> Box(Modifier.fillMaxSize().testTag(KitchenTags.LOADING), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                query.isBlank() -> {
                    val groups = remember(entries, language) { Kitchen.groups(entries, language) }
                    Groups(groups, held, language, kitchen.store, listed)
                }
                else -> Found(query, entries, held, language, kitchen.store, results, onClear = { query = "" })
            }
        }
    }
}

/** The search over the dictionary: free text in either language, no capitals forced; Search closes the keyboard. */
@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit) {
    val focus = LocalFocusManager.current
    OutlinedTextField(
        value = query,
        onValueChange = onQuery,
        singleLine = true,
        label = { Text(stringResource(R.string.kitchen_search)) },
        trailingIcon = if (query.isEmpty()) null else {
            {
                IconButton(onClick = { onQuery("") }, modifier = Modifier.size(48.dp).testTag(KitchenTags.SEARCH_CLEAR)) {
                    Icon(painterResource(R.drawable.ic_clear_search), contentDescription = stringResource(R.string.kitchen_search_clear))
                }
            }
        },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp).testTag(KitchenTags.SEARCH),
    )
}

/** Every group with its items, a group's row ticking all of them: ticked when all are, partly when some are. */
@Composable
private fun Groups(
    groups: List<KitchenGroup>,
    held: Set<Long>,
    language: InterfaceLanguage,
    store: KitchenStore,
    state: LazyListState,
) {
    LazyColumn(Modifier.fillMaxSize().testTag(KitchenTags.LIST), state = state) {
        groups.forEach { (group, items) ->
            item(key = group.id) {
                val ticked = items.count { it.id in held }
                val toggle = when (ticked) {
                    0 -> ToggleableState.Off
                    items.size -> ToggleableState.On
                    else -> ToggleableState.Indeterminate
                }
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text(group.label(language), style = MaterialTheme.typography.titleMedium) },
                    supportingContent = { Text(stringResource(R.string.kitchen_group_ticked, ticked, items.size)) },
                    leadingContent = { TriStateCheckbox(state = toggle, onClick = null) },
                    modifier = Modifier
                        .triStateToggleable(toggle, role = Role.Checkbox) {
                            store.hold(items.map { it.id }, has = toggle != ToggleableState.On)
                        }
                        .testTag(KitchenTags.group(group.id)),
                )
            }
            items(items, key = { it.id }) { item ->
                Item(item, held, language, store, supporting = null)
            }
        }
    }
}

/**
 * The items a search finds, in the order the editor's equipment field offers them, each with its group beneath it —
 * and the synonym it was found by, when none of its labels found it; or, when none is, that nothing was found.
 */
@Composable
private fun Found(
    query: String,
    dictionary: List<EquipmentEntryEntity>,
    held: Set<Long>,
    language: InterfaceLanguage,
    store: KitchenStore,
    state: LazyListState,
    onClear: () -> Unit,
) {
    val groups = remember(dictionary, language) { Kitchen.groups(dictionary, language).associateBy { it.group.id } }
    val offer = remember(query, dictionary, language) { EquipmentOffer.of(query, dictionary, language) }
    val found = offer.entries.filter { entry -> groups[entry.parentId]?.items?.any { it.id == entry.id } == true }
    if (found.isEmpty()) {
        EmptyState(
            picture = R.drawable.ic_search_off,
            headline = stringResource(R.string.kitchen_nothing_found, query.trim()),
            sentence = null,
            action = EmptyAction(stringResource(R.string.kitchen_show_all), null, KitchenTags.EMPTY_CLEAR, onClear),
            tag = KitchenTags.EMPTY,
        )
        return
    }
    LazyColumn(Modifier.fillMaxSize().testTag(KitchenTags.RESULTS), state = state) {
        items(found, key = { it.id }) { item ->
            val group = groups.getValue(item.parentId!!).group.label(language)
            Item(item, held, language, store, supporting = listOfNotNull(offer.synonyms[item.id], group).joinToString(" · "))
        }
    }
}

/** One item: a checkbox row, the whole row ticking or unticking it. */
@Composable
private fun Item(item: EquipmentEntryEntity, held: Set<Long>, language: InterfaceLanguage, store: KitchenStore, supporting: String?) {
    val has = item.id in held
    ListItem(
        headlineContent = { Text(item.label(language)) },
        supportingContent = supporting?.let { { Text(it) } },
        leadingContent = { Checkbox(checked = has, onCheckedChange = null) },
        modifier = Modifier
            .toggleable(value = has, role = Role.Checkbox) { store.hold(listOf(item.id), it) }
            .testTag(KitchenTags.item(item.id)),
    )
}

object KitchenTags {
    const val SCREEN = "kitchen"
    const val TITLE = "kitchen-title"
    const val BACK = "kitchen-back"
    const val SEARCH = "kitchen-search"
    const val SEARCH_CLEAR = "kitchen-search-clear"
    const val LOADING = "kitchen-loading"
    const val LIST = "kitchen-list"
    const val RESULTS = "kitchen-results"
    const val EMPTY = "kitchen-nothing-found"
    const val EMPTY_CLEAR = "kitchen-nothing-found-clear"

    /** A group's row, which ticks all its items. */
    fun group(id: Long) = "kitchen-group-$id"

    /** An item's row. */
    fun item(id: Long) = "kitchen-item-$id"
}
