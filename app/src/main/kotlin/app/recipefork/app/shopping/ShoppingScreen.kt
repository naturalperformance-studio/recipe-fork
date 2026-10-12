package app.recipefork.app.shopping

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import app.recipefork.app.R
import app.recipefork.app.RecipeForkApplication
import app.recipefork.app.card.IngredientEntries
import app.recipefork.app.card.ServingsControl
import app.recipefork.app.card.ServingsTags
import app.recipefork.app.card.done
import app.recipefork.app.dictionary.EquipmentSource
import app.recipefork.app.dictionary.IngredientEntryEntity
import app.recipefork.app.editor.BlockAction
import app.recipefork.app.library.EmptyState
import app.recipefork.app.library.TITLE_CLEARANCE
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.settings.servingsLanguage
import app.recipefork.app.ui.SnackbarAction
import app.recipefork.core.scaling.Rational
import app.recipefork.core.scaling.RequestedServings
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * What the shopping list reads and writes: the list kept on the device, the ingredient dictionary it names lines by, and
 * the equipment dictionary its own lines are offered from too.
 */
class ShoppingEnvironment(
    val store: ShoppingStore,
    val equipment: EquipmentSource = EquipmentSource { emptyList() },
    val ingredients: IngredientEntries,
)

/** The shopping list of the composition root: the device's list and the held dictionaries. */
@Composable
fun rememberShoppingEnvironment(): ShoppingEnvironment {
    val context = LocalContext.current.applicationContext
    return remember(context) {
        (context as RecipeForkApplication).container.let { container ->
            ShoppingEnvironment(container.shopping, container.dictionaries) { container.dictionaries.ingredients() }
        }
    }
}

/**
 * The shopping list (FLOW-074), a destination above the book: the recipes on it at the top, each with its title, the
 * card's serving control — a count changed there is kept, and every line it feeds changes at once — and its removal,
 * said with Undo; then what to buy, added up by [ShoppingTotals][app.recipefork.core.shopping.ShoppingTotals]: each
 * line's name and amount, under it, muted, what an approximate line counted and the recipes it comes from. A tap on a
 * line ticks it as bought — struck through, it moves to "In the basket" at the bottom — and another unticks it; a
 * ticked line whose amount grows is unticked, one that goes drops its tick ([settled]). The ⋮ clears the ticks, and
 * clears the list, each said with Undo. An Undo goes once anything else on the list changes. A recipe the book no
 * longer holds leaves the list by itself. "Add item" adds a line of the reader's own ([AddLineSheet]): it stands after
 * the recipes' lines with no recipe under it, ticks as they do and is removed with Undo; "Clear ticked" takes it away
 * once ticked. A list with neither recipes nor own lines says how to add a recipe. Back — the bar's or the system's —
 * returns to the book.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingScreen(
    shopping: ShoppingEnvironment,
    recipes: RecipeRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val store = shopping.store
    val book = remember(recipes) { recipes.bookName().fileName }
    val list by store.list.collectAsState()
    val entries by remember(store, recipes) { shoppingEntries(store, recipes) }.collectAsState(initial = null)
    // The dictionary, and whether it was read: a dictionary that could not be read is an empty one.
    val dictionary by produceState<Pair<Map<Long, IngredientEntryEntity>, Boolean>?>(null, shopping.ingredients) {
        value = try {
            shopping.ingredients.entries().associateBy { it.id } to true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Lines are named by the dictionary; without it they still add up, each entry an unnamed ingredient.
            emptyMap<Long, IngredientEntryEntity>() to false
        }
    }
    val language = servingsLanguage()
    val snackbars = remember { SnackbarHostState() }
    val change = rememberUndoableChange(store, snackbars)
    val shown = entries
    val held = dictionary?.first
    // The ticks settled against the lines the recipes make now, with every change of the list made here and whenever
    // the recipes change elsewhere — added from a card, edited, deleted. Only against the recipes and the dictionary
    // both read: lines made without the entries' measures are told apart otherwise, and would drop every tick.
    val read = dictionary?.takeIf { it.second }?.first
    val settle: (ShoppingList) -> ShoppingList = remember(book, shown, read) {
        val recipesRead = shown?.associate { it.recipe.id to it.recipe }
        if (recipesRead == null || read == null) { list -> list } else { list -> list.settled(book, recipesRead, read) }
    }
    LaunchedEffect(settle) { store.update(settle) }
    val resources = LocalContext.current.resources
    val lines = remember(shown, held, language, resources) {
        if (shown == null || held == null) null else listedLines(shown, held, language, resources)
    }
    val own = remember(list, book) { ownLines(list, book) }
    val all = lines?.plus(own)
    val ticked = all.orEmpty().filter { line ->
        TickedLine(book, line.tick.key).let { it in list.ticks && line.tick.keeps(list.ticks[it]) }
    }.map { it.tick.key }.toSet()
    var adding by rememberSaveable { mutableStateOf(false) }
    if (adding && held != null) {
        val ingredients = remember(held) { held.values.toList() }
        AddLineSheet(
            ingredients, shopping.equipment, language,
            onAdd = { text -> store.update { it.with(OwnLine.of(book, text)) } },
            onClose = { adding = false },
        )
    }
    Scaffold(
        modifier = modifier.fillMaxSize().testTag(ShoppingTags.SCREEN),
        floatingActionButton = {
            if (all != null) AddItem { adding = true }
        },
        snackbarHost = {
            SnackbarHost(snackbars) { data ->
                Snackbar(
                    modifier = Modifier.padding(12.dp).testTag(ShoppingTags.SNACKBAR),
                    action = data.visuals.actionLabel?.let { label -> { SnackbarAction(label, data::performAction) } },
                ) { Text(data.visuals.message) }
            }
        },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.shopping_title),
                        Modifier.padding(end = TITLE_CLEARANCE).semantics { heading() }.testTag(ShoppingTags.TITLE),
                    )
                },
                navigationIcon = {
                    val action = stringResource(R.string.shopping_back_action)
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
                            .testTag(ShoppingTags.BACK),
                    ) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.shopping_back))
                    }
                },
                actions = {
                    if (!shown.isNullOrEmpty() || (shown != null && own.isNotEmpty())) {
                        val cleared = stringResource(R.string.shopping_cleared)
                        val unticked = stringResource(R.string.shopping_ticks_cleared)
                        ListMenu(
                            onClearTicked = { change(unticked) { it.withoutTicked(book) } }.takeIf { ticked.isNotEmpty() },
                            onClear = { change(cleared) { ShoppingList() } },
                        )
                    }
                },
            )
        },
    ) { padding ->
        when {
            shown == null || all == null -> Box(Modifier.padding(padding).fillMaxSize().testTag(ShoppingTags.LOADING), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            shown.isEmpty() && own.isEmpty() -> EmptyState(
                picture = R.drawable.ic_shopping_cart,
                headline = stringResource(R.string.shopping_empty),
                sentence = stringResource(R.string.shopping_empty_sentence, stringResource(R.string.shopping_add)),
                action = null,
                tag = ShoppingTags.EMPTY,
                modifier = Modifier.padding(padding),
            )
            else -> {
                val context = LocalContext.current
                ListContent(
                    shown, all, ticked, padding,
                    onServings = { entry, count -> store.update { settle(it.withServings(entry.listed.book, entry.listed.recipe, count)) } },
                    onRemove = { entry, title ->
                        change(context.getString(R.string.shopping_removed, title)) { settle(it.without(entry.listed.book, entry.listed.recipe)) }
                    },
                    onTick = { line, bought -> store.update { it.withTick(TickedLine(book, line.key), line.amount, bought) } },
                    onRemoveOwn = { line ->
                        change(context.getString(R.string.shopping_own_removed, line.shown.text)) { it.withoutOwn(book, line.tick.key) }
                    },
                )
            }
        }
    }
}

/**
 * The recipes, each with its count ([onServings]) and its removal ([onRemove], told its title) — no part at all for a
 * list of own lines alone; then the lines to buy, and under "In the basket" the lines [ticked] (by their keys), each in
 * the order of the list, each ticked or unticked with a tap ([onTick], told whether the line is now bought), an own
 * line with its removal beside it ([onRemoveOwn]).
 */
@Composable
private fun ListContent(
    entries: List<ShoppingEntry>,
    lines: List<ListedLine>,
    ticked: Set<String>,
    padding: PaddingValues,
    onServings: (ShoppingEntry, Rational) -> Unit,
    onRemove: (ShoppingEntry, String) -> Unit,
    onTick: (LineTick, Boolean) -> Unit,
    onRemoveOwn: (ListedLine) -> Unit,
) {
    val (bought, toBuy) = lines.partition { it.tick.key in ticked }
    val untitled = stringResource(R.string.common_untitled_recipe)
    LazyColumn(
        // Beneath the bar, never under it; the last line kept clear of the navigation bar.
        Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()).testTag(ShoppingTags.LIST),
        state = rememberLazyListState(),
        // The last line kept clear of "Add item" as well.
        contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + 16.dp + FAB_CLEARANCE),
    ) {
        if (entries.isNotEmpty()) item(key = RECIPES_KEY) { Heading(R.string.shopping_recipes) }
        items(entries, key = { "${it.listed.book}/${it.listed.recipe.value}" }) { entry ->
            val title = entry.recipe.title.trim().ifEmpty { untitled }
            RecipeBlock(entry, title, onServings = { onServings(entry, it) }, onRemove = { onRemove(entry, title) })
        }
        item(key = LINES_KEY) {
            Column {
                if (entries.isNotEmpty()) HorizontalDivider(Modifier.padding(top = 8.dp))
                Heading(R.string.shopping_to_buy)
            }
        }
        itemsIndexed(toBuy, key = { _, line -> lineKey(line) }) { index, line ->
            Line(line, done = false, ShoppingTags.line(index), onToggle = { onTick(line.tick, true) }, { onRemoveOwn(line) }, Modifier.animateItem())
        }
        if (bought.isNotEmpty()) {
            item(key = BASKET_KEY) {
                Column(Modifier.animateItem()) {
                    HorizontalDivider(Modifier.padding(top = 8.dp))
                    Heading(R.string.shopping_basket)
                }
            }
        }
        itemsIndexed(bought, key = { _, line -> lineKey(line) }) { index, line ->
            Line(line, done = true, ShoppingTags.line(toBuy.size + index), onToggle = { onTick(line.tick, false) }, { onRemoveOwn(line) }, Modifier.animateItem())
        }
    }
}

/** A line's key in the list: what it is, so that it moves between the two parts as itself. */
private fun lineKey(line: ListedLine) = LINE_KEY + line.tick.key

/** The list's keys of its two headings. */
private const val RECIPES_KEY = "recipes"
private const val LINES_KEY = "lines"
private const val BASKET_KEY = "basket"
private const val LINE_KEY = "line/"

/** What the end of the list leaves free under "Add item": the button's height and its margin. */
private val FAB_CLEARANCE = 72.dp

/** "Add item", the list's floating action, which opens the field of a line of the reader's own. */
@Composable
private fun AddItem(onClick: () -> Unit) {
    val words = stringResource(R.string.shopping_own_add)
    val action = stringResource(R.string.shopping_own_add_action)
    ExtendedFloatingActionButton(
        text = { Text(words) },
        icon = { Icon(painterResource(R.drawable.ic_add_item), contentDescription = null) },
        onClick = onClick,
        // The button keeps its shown words from the screen reader, so they are given to the button itself, as the book's
        // floating action does.
        modifier = Modifier
            .semantics {
                text = AnnotatedString(words)
                onClick(label = action) {
                    onClick()
                    true
                }
            }
            .testTag(ShoppingTags.ADD),
    )
}

@Composable
private fun Heading(title: Int) {
    Text(
        stringResource(title),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).semantics { heading() },
    )
}

/**
 * A recipe on the list: its title and its removal; under them, for a recipe with a count, the card's serving control,
 * which walks the count as the card does, ¼ the lowest.
 */
@Composable
private fun RecipeBlock(entry: ShoppingEntry, title: String, onServings: (Rational) -> Unit, onRemove: () -> Unit) {
    val id = entry.recipe.id.value
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, bottom = 8.dp).testTag(ShoppingTags.recipe(id))) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f).padding(end = 8.dp).testTag(ShoppingTags.recipeTitle(id)),
            )
            BlockAction(R.drawable.ic_clear_search, stringResource(R.string.shopping_remove, title), ShoppingTags.remove(id), onClick = onRemove)
        }
        entry.servings?.let { count ->
            ServingsControl(
                modifier = Modifier.padding(end = 12.dp),
                count = RequestedServings.text(count, servingsLanguage()),
                approximate = false,
                canDecrease = count > RequestedServings.MIN,
                onDecrease = { onServings(RequestedServings.decreased(count)) },
                onIncrease = { onServings(RequestedServings.increased(count)) },
                tags = ServingsTags(ShoppingTags.servings(id), ShoppingTags.fewer(id), ShoppingTags.more(id)),
                fewerAction = R.string.shopping_servings_fewer_action,
                moreAction = R.string.shopping_servings_more_action,
            )
        }
    }
}

/**
 * A line: what to buy and how much; under it, muted, what an approximate line counted and the recipes it comes from.
 * The line is one target a tap ticks as bought, or unticks ([onToggle]) — a check box to the screen reader, as a done
 * mark on the card is; a line ticked ([done]) has its text struck through and muted, and is read as bought. An own line
 * has its removal ([onRemove]) beside it.
 */
@Composable
private fun Line(listed: ListedLine, done: Boolean, tag: String, onToggle: () -> Unit, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    val line = listed.shown
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(
            Modifier.weight(1f).heightIn(min = 48.dp).ticked(done, onToggle).padding(horizontal = 16.dp, vertical = 8.dp).testTag(tag),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(line.text, style = MaterialTheme.typography.bodyLarge.done(done))
            val muted = MaterialTheme.colorScheme.onSurfaceVariant
            line.counted?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = muted) }
            if (line.from.isNotEmpty()) Text(line.from, style = MaterialTheme.typography.bodyMedium, color = muted)
        }
        if (listed.own) {
            BlockAction(
                R.drawable.ic_clear_search, stringResource(R.string.shopping_remove, line.text), ShoppingTags.removeLine(tag), onClick = onRemove,
                modifier = Modifier.padding(end = 4.dp),
            )
        }
    }
}

/**
 * Makes a line a check box the reader ticks as bought with a tap, and unticks with another: toggleable, in the role of
 * a check box, read as "Bought" or "Not bought", its tap named "Mark as bought" or "Mark as not bought" — as the card's
 * done marks are ([app.recipefork.app.card.markedDone]).
 */
@Composable
private fun Modifier.ticked(done: Boolean, onToggle: () -> Unit): Modifier {
    val label = stringResource(if (done) R.string.shopping_untick_action else R.string.shopping_tick_action)
    val said = stringResource(if (done) R.string.shopping_ticked else R.string.shopping_unticked)
    return semantics {
        stateDescription = said
        onClick(label = label, action = null)
    }.toggleable(value = done, role = Role.Checkbox, onValueChange = { onToggle() })
}

/**
 * The list's ⋮: Clear ticked while a line is ticked ([onClearTicked]), which unticks every line and keeps the
 * recipes; then Clear list, a removal, in the theme's error colour.
 */
@Composable
private fun ListMenu(onClearTicked: (() -> Unit)?, onClear: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.padding(end = 4.dp)) {
        BlockAction(
            R.drawable.ic_row_menu,
            stringResource(R.string.shopping_menu),
            ShoppingTags.MENU,
            onClick = { expanded = !expanded },
            action = stringResource(R.string.shopping_menu_action),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (onClearTicked != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.shopping_clear_ticked)) },
                    onClick = {
                        expanded = false
                        onClearTicked()
                    },
                    modifier = Modifier.heightIn(min = 48.dp).testTag(ShoppingTags.CLEAR_TICKED),
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.shopping_clear)) },
                onClick = {
                    expanded = false
                    onClear()
                },
                colors = MenuDefaults.itemColors(textColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.heightIn(min = 48.dp).testTag(ShoppingTags.CLEAR),
            )
        }
    }
}

/**
 * Makes a change of the list at once and says it, [said], in [snackbars] with Undo, which puts back the
 * list as it was. The change is final once the statement goes — by itself, or replaced — or as soon as anything else
 * on the list changes.
 */
@Composable
private fun rememberUndoableChange(store: ShoppingStore, snackbars: SnackbarHostState): (String, (ShoppingList) -> ShoppingList) -> Unit {
    val scope = rememberCoroutineScope()
    val undo = stringResource(R.string.shopping_undo)
    return remember(store, snackbars, scope, undo) {
        { said, change ->
            val before = store.list.value
            store.update(change)
            val after = store.list.value
            if (after != before) {
                val visuals = Statement(said, undo)
                scope.launch {
                    snackbars.currentSnackbarData?.dismiss()
                    val result = coroutineScope {
                        // Anything changed since makes the change final: its Undo goes.
                        val changed = launch {
                            store.list.first { it != after }
                            snackbars.currentSnackbarData?.takeIf { it.visuals === visuals }?.dismiss()
                        }
                        snackbars.showSnackbar(visuals).also { changed.cancel() }
                    }
                    if (result == SnackbarResult.ActionPerformed) store.update { if (it == after) before else it }
                }
            }
        }
    }
}

/** A statement of the list with its Undo, told apart from any other by its identity. */
private class Statement(override val message: String, override val actionLabel: String) : SnackbarVisuals {
    override val withDismissAction = false
    override val duration = SnackbarDuration.Long
}

object ShoppingTags {
    const val SCREEN = "shopping"
    const val TITLE = "shopping-title"
    const val BACK = "shopping-back"
    const val MENU = "shopping-menu"
    const val CLEAR = "shopping-clear"
    const val CLEAR_TICKED = "shopping-clear-ticked"
    const val LOADING = "shopping-loading"
    const val LIST = "shopping-list"
    const val EMPTY = "shopping-empty"
    const val SNACKBAR = "shopping-snackbar"

    /** "Add item", and its sheet: the field, the typed text offered as written. */
    const val ADD = "shopping-add"
    const val ADD_SHEET = "shopping-add-sheet"
    const val ADD_FIELD = "shopping-add-field"
    const val ADD_TYPED = "shopping-add-typed"

    /** An ingredient ([ingredient]) or an item of equipment offered in the sheet, by its entry's id. */
    fun offer(id: Long, ingredient: Boolean) = "shopping-offer-${if (ingredient) "i" else "e"}-$id"

    /** A recipe's block on the list, its title, its removal, and its serving count with its − and +. */
    fun recipe(id: String) = "shopping-recipe-$id"
    fun recipeTitle(id: String) = "shopping-recipe-title-$id"
    fun remove(id: String) = "shopping-remove-$id"
    fun servings(id: String) = "shopping-servings-$id"
    fun fewer(id: String) = "shopping-fewer-$id"
    fun more(id: String) = "shopping-more-$id"

    /** The line at [index] of what to buy, the lines in the basket after the others. */
    fun line(index: Int) = "shopping-line-$index"

    /** The removal beside an own line, by the line's tag. */
    fun removeLine(lineTag: String) = "$lineTag-remove"
}
