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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.recipefork.app.R
import app.recipefork.app.RecipeForkApplication
import app.recipefork.app.card.IngredientEntries
import app.recipefork.app.card.ServingsControl
import app.recipefork.app.card.ServingsTags
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

/** What the shopping list reads and writes: the list kept on the device, and the ingredient dictionary it names lines by. */
class ShoppingEnvironment(val store: ShoppingStore, val ingredients: IngredientEntries)

/** The shopping list of the composition root: the device's list and the held dictionaries. */
@Composable
fun rememberShoppingEnvironment(): ShoppingEnvironment {
    val context = LocalContext.current.applicationContext
    return remember(context) {
        (context as RecipeForkApplication).container.let { container ->
            ShoppingEnvironment(container.shopping) { container.dictionaries.ingredients() }
        }
    }
}

/**
 * The shopping list (FLOW-074), a destination above the book: the recipes on it at the top, each with its title, the
 * card's serving control — a count changed there is kept, and every line it feeds changes at once — and its removal,
 * said with Undo; then what to buy, added up by [ShoppingTotals][app.recipefork.core.shopping.ShoppingTotals]: each
 * line's name and amount, under it, muted, what an approximate line counted and the recipes it comes from. The ⋮
 * clears the list, said with Undo. An Undo goes once anything else on the list changes. A recipe the book no longer
 * holds leaves the list by itself; an empty list says how to add one. Back — the bar's or the system's — returns to
 * the book.
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
    val entries by remember(store, recipes) { shoppingEntries(store, recipes) }.collectAsState(initial = null)
    val dictionary by produceState<Map<Long, IngredientEntryEntity>?>(null, shopping.ingredients) {
        value = try {
            shopping.ingredients.entries().associateBy { it.id }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Lines are named by the dictionary; without it they still add up, each entry an unnamed ingredient.
            emptyMap()
        }
    }
    val language = servingsLanguage()
    val snackbars = remember { SnackbarHostState() }
    val change = rememberUndoableChange(store, snackbars)
    val shown = entries
    Scaffold(
        modifier = modifier.fillMaxSize().testTag(ShoppingTags.SCREEN),
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
                    if (!shown.isNullOrEmpty()) {
                        val cleared = stringResource(R.string.shopping_cleared)
                        ListMenu(onClear = { change(cleared) { ShoppingList() } })
                    }
                },
            )
        },
    ) { padding ->
        val lines = dictionary?.let { held ->
            val resources = LocalContext.current.resources
            remember(shown, held, language, resources) { shown?.let { shownLines(it, held, language, resources) } }
        }
        when {
            shown == null || lines == null -> Box(Modifier.padding(padding).fillMaxSize().testTag(ShoppingTags.LOADING), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            shown.isEmpty() -> EmptyState(
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
                    shown, lines, padding,
                    onServings = { entry, count -> store.update { it.withServings(entry.listed.book, entry.listed.recipe, count) } },
                    onRemove = { entry, title ->
                        change(context.getString(R.string.shopping_removed, title)) { it.without(entry.listed.book, entry.listed.recipe) }
                    },
                )
            }
        }
    }
}

/** The recipes, each with its count ([onServings]) and its removal ([onRemove], told its title), then the lines. */
@Composable
private fun ListContent(
    entries: List<ShoppingEntry>,
    lines: List<ShownLine>,
    padding: PaddingValues,
    onServings: (ShoppingEntry, Rational) -> Unit,
    onRemove: (ShoppingEntry, String) -> Unit,
) {
    val untitled = stringResource(R.string.common_untitled_recipe)
    LazyColumn(
        // Beneath the bar, never under it; the last line kept clear of the navigation bar.
        Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()).testTag(ShoppingTags.LIST),
        state = rememberLazyListState(),
        contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + 16.dp),
    ) {
        item(key = "recipes") { Heading(R.string.shopping_recipes) }
        items(entries, key = { "${it.listed.book}/${it.listed.recipe.value}" }) { entry ->
            val title = entry.recipe.title.trim().ifEmpty { untitled }
            RecipeBlock(entry, title, onServings = { onServings(entry, it) }, onRemove = { onRemove(entry, title) })
        }
        item(key = "lines") {
            Column {
                HorizontalDivider(Modifier.padding(top = 8.dp))
                Heading(R.string.shopping_to_buy)
            }
        }
        itemsIndexed(lines) { index, line -> Line(line, Modifier.testTag(ShoppingTags.line(index))) }
    }
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

/** A line: what to buy and how much; under it, muted, what an approximate line counted and the recipes it comes from. */
@Composable
private fun Line(line: ShownLine, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 16.dp, vertical = 8.dp).semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(line.text, style = MaterialTheme.typography.bodyLarge)
        val muted = MaterialTheme.colorScheme.onSurfaceVariant
        line.counted?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = muted) }
        if (line.from.isNotEmpty()) Text(line.from, style = MaterialTheme.typography.bodyMedium, color = muted)
    }
}

/** The list's ⋮: Clear list, a removal, in the theme's error colour. */
@Composable
private fun ListMenu(onClear: () -> Unit) {
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
    const val LOADING = "shopping-loading"
    const val LIST = "shopping-list"
    const val EMPTY = "shopping-empty"
    const val SNACKBAR = "shopping-snackbar"

    /** A recipe's block on the list, its title, its removal, and its serving count with its − and +. */
    fun recipe(id: String) = "shopping-recipe-$id"
    fun recipeTitle(id: String) = "shopping-recipe-title-$id"
    fun remove(id: String) = "shopping-remove-$id"
    fun servings(id: String) = "shopping-servings-$id"
    fun fewer(id: String) = "shopping-fewer-$id"
    fun more(id: String) = "shopping-more-$id"

    /** The line at [index] of what to buy. */
    fun line(index: Int) = "shopping-line-$index"
}
