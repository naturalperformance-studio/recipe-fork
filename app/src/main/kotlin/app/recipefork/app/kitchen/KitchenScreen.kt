package app.recipefork.app.kitchen

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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
import app.recipefork.app.ui.SnackbarAction
import app.recipefork.app.ui.WordFitText
import app.recipefork.core.scaling.InterfaceLanguage
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

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

/** Where My kitchen was opened from, which its back returns to. */
enum class KitchenFrom(@StringRes val backAction: Int) {
    SETTINGS(R.string.kitchen_back_action),
    RECIPE(R.string.kitchen_back_to_recipe_action),
    BOOK(R.string.kitchen_back_to_book_action),
}

/**
 * My kitchen, a destination above the settings, the book's filters or a recipe's card ([from]): the equipment
 * dictionary by its groups, each group folded until its row is tapped, saying how many of its items are ticked, its
 * checkbox ticking or unticking all its items at once — said in the snackbar with Undo, which puts back exactly the
 * ticks the group had — and, unfolded, each item a checkbox row the reader ticks when the kitchen has it. A search — in
 * either language, by synonyms, as the editor's equipment field finds — narrows the list to the items found. What is
 * ticked is kept at once ([KitchenStore]). Back — the bar's or the system's — returns to where it was opened from. A
 * dictionary that cannot be read is said so, with "Try again", never a crash.
 *
 * Opened from a recipe's item of equipment the kitchen lacks, it opens [at] the entry that item names: its group is
 * unfolded, the list is scrolled to its row once, as it opens, and the row stands out on the primary container, saying
 * "Needed for the recipe", for as long as My kitchen stays open; back returns to the recipe.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KitchenScreen(
    kitchen: KitchenEnvironment,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    at: Long? = null,
    from: KitchenFrom = if (at == null) KitchenFrom.SETTINGS else KitchenFrom.RECIPE,
) {
    var attempt by remember { mutableIntStateOf(0) }
    val dictionary by produceState<Result<List<EquipmentEntryEntity>>?>(null, kitchen.equipment, attempt) {
        value = null
        value = try {
            Result.success(kitchen.equipment.equipment())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    val held by kitchen.store.held.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    val language = servingsLanguage()
    val listed = rememberLazyListState()
    val results = rememberLazyListState()
    // The groups unfolded: none as it opens, but the one holding the entry it was opened at.
    val unfolded = rememberSaveable(saver = UNFOLDED_SAVER) { mutableStateOf(emptySet<Long>()) }
    // Brought to the entry it was opened at once, not again after the screen turns or the reader has scrolled away.
    var brought by rememberSaveable { mutableStateOf(at == null) }
    val snackbars = remember { SnackbarHostState() }
    val tickGroup = rememberGroupTick(kitchen.store, snackbars, language)
    Scaffold(
        modifier = modifier.fillMaxSize().testTag(KitchenTags.SCREEN),
        snackbarHost = {
            SnackbarHost(snackbars, Modifier.imePadding()) { data ->
                Snackbar(
                    modifier = Modifier.padding(12.dp),
                    action = data.visuals.actionLabel?.let { label -> { SnackbarAction(label, data::performAction) } },
                ) { Text(data.visuals.message) }
            }
        },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.kitchen_title), Modifier.semantics { heading() }.testTag(KitchenTags.TITLE)) },
                navigationIcon = {
                    val action = stringResource(from.backAction)
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
            val read = dictionary
            val entries = read?.getOrNull()
            when {
                read == null -> Box(Modifier.fillMaxSize().testTag(KitchenTags.LOADING), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                entries == null -> EmptyState(
                    picture = R.drawable.ic_warning,
                    headline = stringResource(R.string.kitchen_unreadable),
                    sentence = null,
                    action = EmptyAction(stringResource(R.string.kitchen_retry), null, KitchenTags.UNREADABLE_RETRY) { attempt++ },
                    tag = KitchenTags.UNREADABLE,
                )
                query.isBlank() -> {
                    val groups = remember(entries, language) { Kitchen.groups(entries, language) }
                    if (!brought) {
                        LaunchedEffect(groups) {
                            groupOf(groups, at)?.let { unfolded.value += it }
                            rowOf(groups, at, unfolded.value)?.let { row ->
                                // The unfolded group's items are laid out first, then the list is brought to the row.
                                snapshotFlow { listed.layoutInfo.totalItemsCount }.first { it > row }
                                listed.scrollToItem(row)
                            }
                            brought = true
                        }
                    }
                    Groups(
                        groups, held, language, kitchen.store, listed, at,
                        unfolded = unfolded.value,
                        onFold = { id -> unfolded.value = if (id in unfolded.value) unfolded.value - id else unfolded.value + id },
                        onTickGroup = tickGroup,
                    )
                }
                else -> Found(query, entries, held, language, kitchen.store, results, at, onClear = { query = "" })
            }
        }
    }
}

/** The unfolded groups' ids, kept across a turn of the screen and the end of the process. */
private val UNFOLDED_SAVER = listSaver<MutableState<Set<Long>>, Long>(
    save = { it.value.toList() },
    restore = { mutableStateOf(it.toSet()) },
)

/**
 * Ticks or unticks every item of a group at once, from its checkbox, and says so in [snackbars] with Undo: Undo puts
 * back exactly the ticks the group had. The change is final once the statement goes — by itself, or replaced — or as
 * soon as anything else in the kitchen is ticked or unticked.
 */
@Composable
private fun rememberGroupTick(store: KitchenStore, snackbars: SnackbarHostState, language: InterfaceLanguage): (KitchenGroup, Boolean) -> Unit {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val undo = stringResource(R.string.kitchen_undo)
    return remember(store, snackbars, scope, context, undo, language) {
        { (group, items): KitchenGroup, has: Boolean ->
            val ids = items.map { it.id }
            val before = store.held.value.intersect(ids.toSet())
            store.hold(ids, has)
            val after = store.held.value
            val label = group.label(language)
            val said = context.getString(if (has) R.string.kitchen_group_ticked_all else R.string.kitchen_group_unticked_all, label)
            val visuals = Statement(said, undo)
            scope.launch {
                snackbars.currentSnackbarData?.dismiss()
                val result = coroutineScope {
                    // Anything ticked since makes the change final: its Undo goes.
                    val changed = launch {
                        store.held.first { it != after }
                        snackbars.currentSnackbarData?.takeIf { it.visuals === visuals }?.dismiss()
                    }
                    snackbars.showSnackbar(visuals).also { changed.cancel() }
                }
                if (result == SnackbarResult.ActionPerformed) {
                    store.hold(ids.filter { it !in before }, has = false)
                    store.hold(before, has = true)
                }
            }
        }
    }
}

/** A statement of My kitchen with its Undo, told apart from any other by its identity. */
private class Statement(override val message: String, override val actionLabel: String) : SnackbarVisuals {
    override val withDismissAction = false
    override val duration = SnackbarDuration.Long
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

/** The group that is or holds the entry [id], or null when none of [groups] does. */
private fun groupOf(groups: List<KitchenGroup>, id: Long?): Long? =
    groups.firstOrNull { (group, items) -> group.id == id || items.any { it.id == id } }?.group?.id

/**
 * The index of the row of the entry [id] — a group's or an item's — in the list of [groups], the [unfolded] ones
 * showing their items, or null when not listed.
 */
private fun rowOf(groups: List<KitchenGroup>, id: Long?, unfolded: Set<Long>): Int? {
    var row = 0
    groups.forEach { (group, items) ->
        if (group.id == id) return row
        val open = group.id in unfolded
        val item = items.indexOfFirst { it.id == id }
        if (item >= 0) return if (open) row + 1 + item else row
        row += 1 + if (open) items.size else 0
    }
    return null
}

/** The colours of the row of the entry My kitchen was opened at: on the primary container, standing out of the list. */
@Composable
private fun standingOut() = ListItemDefaults.colors(
    containerColor = MaterialTheme.colorScheme.primaryContainer,
    headlineColor = MaterialTheme.colorScheme.onPrimaryContainer,
    supportingColor = MaterialTheme.colorScheme.onPrimaryContainer,
)

/**
 * Every group, folded but those [unfolded]: its row says how many of its items are ticked and shows or hides them
 * ([onFold]); its checkbox ticks all of them ([onTickGroup]) — ticked when all are, partly when some are. The row of
 * the entry [at] stands out.
 */
@Composable
private fun Groups(
    groups: List<KitchenGroup>,
    held: Set<Long>,
    language: InterfaceLanguage,
    store: KitchenStore,
    state: LazyListState,
    at: Long?,
    unfolded: Set<Long>,
    onFold: (Long) -> Unit,
    onTickGroup: (KitchenGroup, Boolean) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize().testTag(KitchenTags.LIST), state = state) {
        groups.forEach { kitchenGroup ->
            val (group, items) = kitchenGroup
            val open = group.id in unfolded
            item(key = group.id) {
                Column(Modifier.animateItem()) {
                    HorizontalDivider()
                    GroupRow(kitchenGroup, held, language, open, opened = group.id == at, onFold = { onFold(group.id) }, onTick = onTickGroup)
                }
            }
            if (open) {
                items(items, key = { it.id }) { item ->
                    Item(item, held, language, store, supporting = null, opened = item.id == at, modifier = Modifier.animateItem())
                }
            }
        }
    }
}

/**
 * A group's row: its name, "n of m" ticked beneath it, a chevron at its end; a tap shows or hides its items ([onFold]).
 * Its checkbox, a target of its own, ticks every item when not all are, else unticks them all ([onTick]).
 */
@Composable
private fun GroupRow(
    kitchenGroup: KitchenGroup,
    held: Set<Long>,
    language: InterfaceLanguage,
    open: Boolean,
    opened: Boolean,
    onFold: () -> Unit,
    onTick: (KitchenGroup, Boolean) -> Unit,
) {
    val (group, items) = kitchenGroup
    val ticked = items.count { it.id in held }
    val toggle = when (ticked) {
        0 -> ToggleableState.Off
        items.size -> ToggleableState.On
        else -> ToggleableState.Indeterminate
    }
    val label = group.label(language)
    val needed = stringResource(R.string.kitchen_from_recipe)
    val tick = stringResource(R.string.kitchen_group_tick, label)
    val folding = stringResource(if (open) R.string.kitchen_group_collapse_action else R.string.kitchen_group_expand_action)
    val folded = stringResource(if (open) R.string.kitchen_group_expanded else R.string.kitchen_group_collapsed)
    ListItem(
        headlineContent = {
            // A word wider than the name's room — a long Russian word at a large font scale on a narrow phone — is drawn
            // smaller until it fits, so that no word is broken.
            BoxWithConstraints {
                WordFitText(
                    label,
                    MaterialTheme.typography.titleMedium,
                    constraints.maxWidth,
                    Modifier.testTag(KitchenTags.groupName(group.id)),
                )
            }
        },
        supportingContent = {
            val count = stringResource(R.string.kitchen_group_ticked, ticked, items.size)
            Text(if (opened) "$count · $needed" else count)
        },
        leadingContent = {
            TriStateCheckbox(
                state = toggle,
                onClick = { onTick(kitchenGroup, toggle != ToggleableState.On) },
                // The list item lays its leading part out at the checkbox's own size; the target is 48 dp (A11Y-003).
                modifier = Modifier.size(48.dp).semantics { contentDescription = tick }.testTag(KitchenTags.groupTick(group.id)),
            )
        },
        trailingContent = {
            Icon(
                painterResource(R.drawable.ic_expand_more),
                contentDescription = null,
                modifier = Modifier.rotate(if (open) 180f else 0f),
            )
        },
        colors = if (opened) standingOut() else ListItemDefaults.colors(),
        modifier = Modifier
            .clickable(onClickLabel = folding, role = Role.Button, onClick = onFold)
            .semantics { stateDescription = folded }
            .testTag(KitchenTags.group(group.id)),
    )
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
    at: Long?,
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
            Item(item, held, language, store, supporting = listOfNotNull(offer.synonyms[item.id], group).joinToString(" · "), opened = item.id == at)
        }
    }
}

/** One item: a checkbox row, the whole row ticking or unticking it; standing out when My kitchen was [opened] at it. */
@Composable
private fun Item(
    item: EquipmentEntryEntity,
    held: Set<Long>,
    language: InterfaceLanguage,
    store: KitchenStore,
    supporting: String?,
    opened: Boolean,
    modifier: Modifier = Modifier,
) {
    val has = item.id in held
    val needed = stringResource(R.string.kitchen_from_recipe)
    val said = if (opened) listOfNotNull(supporting, needed).joinToString(" · ") else supporting
    ListItem(
        headlineContent = { Text(item.label(language)) },
        supportingContent = said?.let { { Text(it) } },
        leadingContent = { Checkbox(checked = has, onCheckedChange = null) },
        colors = if (opened) standingOut() else ListItemDefaults.colors(),
        modifier = modifier
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
    /** The equipment dictionary could not be read, and its "Try again". */
    const val UNREADABLE = "kitchen-unreadable"
    const val UNREADABLE_RETRY = "kitchen-unreadable-retry"

    /** A group's row, which shows or hides its items. */
    fun group(id: Long) = "kitchen-group-$id"

    /** A group's name, in its row. */
    fun groupName(id: Long) = "kitchen-group-name-$id"

    /** A group's checkbox, which ticks or unticks all its items. */
    fun groupTick(id: Long) = "kitchen-group-tick-$id"

    /** An item's row. */
    fun item(id: Long) = "kitchen-item-$id"
}
