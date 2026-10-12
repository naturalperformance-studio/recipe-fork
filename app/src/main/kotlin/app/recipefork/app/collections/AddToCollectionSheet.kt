package app.recipefork.app.collections

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import android.content.res.Resources
import app.recipefork.app.R
import app.recipefork.app.card.CardTarget
import app.recipefork.app.ui.WordFitText
import app.recipefork.core.recipe.CollectionId
import app.recipefork.core.recipe.CollectionOrigin
import app.recipefork.core.recipe.RecipeCollection
import app.recipefork.core.recipe.RecipeId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * "Add to collection" from a recipe's card (v0.36): a sheet listing the reader's own collections, the last made first,
 * each with a checkbox ticked where the recipe already is; an imported collection is frozen and not listed. A tick puts
 * the recipe at the end of that collection and an untick takes it out, each written as it is made, one after another.
 * "New collection…" opens a name field in its place; Create makes the collection and puts the recipe in it, so that it
 * shows ticked.
 *
 * A change that is not written is undone on the sheet and said in the sheet's own snackbar, which stands over the card's
 * while the sheet is open: "Couldn't change the collection", or "Couldn't create the collection" where Create made none.
 *
 * Done, a swipe down or Back closes the sheet once every change is written; [onClose] is then given what the card says
 * of it — "In 2 collections", or "Not in any collection" — or, where nothing changed, the last failure, or null where
 * none failed either. Each row is the card's [CardTarget] tall at least, as the sheet stands over the card.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToCollectionSheet(recipe: RecipeId, collections: CollectionRepository, onClose: (notice: String?) -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    val closed by rememberUpdatedState(onClose)
    val listed by remember(collections) { collections.listed(CollectionOrigin.OWN) }.collectAsState(initial = null)
    val holding by remember(collections, recipe) { collections.holding(recipe) }.collectAsState(initial = null)
    // A tick shows at once; it is kept here until the book says the same, or dropped where the change was not made.
    val ticking = remember { mutableStateMapOf<CollectionId, Boolean>() }
    LaunchedEffect(holding) {
        val held = holding ?: return@LaunchedEffect
        ticking.keys.filter { (it in held) == ticking[it] }.forEach(ticking::remove)
    }
    // The changes are written one at a time, in the order they were made; closing waits for the last of them.
    val writes = remember { Mutex() }
    var changed by remember { mutableStateOf(false) }
    var going by remember { mutableStateOf(false) }
    // A change not written is said at once, in the sheet; the last of them, where nothing else changed, as it closes.
    val notices = remember { SnackbarHostState() }
    var failed by remember { mutableStateOf<String?>(null) }

    fun fail(@StringRes text: Int) {
        val said = resources.getString(text)
        failed = said
        scope.launch {
            notices.currentSnackbarData?.dismiss()
            notices.showSnackbar(said, duration = SnackbarDuration.Short)
        }
    }

    fun write(id: CollectionId, change: suspend () -> Boolean) {
        scope.launch {
            writes.withLock {
                if (attempt(change) == true) {
                    changed = true
                } else {
                    ticking.remove(id)
                    fail(R.string.collection_sheet_change_failed)
                }
            }
        }
    }

    fun toggle(id: CollectionId, on: Boolean) {
        if (going) return
        ticking[id] = on
        write(id) {
            if (on) {
                collections.addTo(recipe, listOf(id)).isNotEmpty()
            } else {
                collections.remove(id, recipe) == CollectionChange.DONE
            }
        }
    }

    fun create(name: String) {
        if (going) return
        scope.launch {
            writes.withLock {
                val id = attempt { collections.create(name) }
                if (id == null) {
                    fail(R.string.collection_sheet_create_failed)
                    return@withLock
                }
                changed = true
                ticking[id] = true
                if (attempt { collections.addTo(recipe, listOf(id)).isNotEmpty() } != true) {
                    // The collection is made, empty: it shows as the book holds it, unticked.
                    ticking.remove(id)
                    fail(R.string.collection_sheet_change_failed)
                }
            }
        }
    }

    // Once every change is written, what the recipe is in now, as the sheet lists it, is said — where anything changed.
    fun finish() {
        scope.launch {
            writes.withLock {}
            val notice = if (!changed) {
                failed
            } else {
                try {
                    val own = collections.listed(CollectionOrigin.OWN).first().map { CollectionId(it.collectionId) }.toSet()
                    noticeOf(resources, collections.holding(recipe).first().count { it in own })
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
            }
            closed(notice)
        }
    }

    val done = {
        if (!going) {
            going = true
            scope.launch { state.hide() }.invokeOnCompletion { finish() }
        }
    }
    ModalBottomSheet(
        onDismissRequest = {
            if (!going) {
                going = true
                finish()
            }
        },
        sheetState = state,
        modifier = Modifier.testTag(AddToCollectionTags.SHEET),
        // No handle: the sheet opens whole, and Done, a swipe down or Back closes it.
        dragHandle = null,
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            // The title wraps only between words: at a large font scale, a word too wide for its room is drawn smaller.
            BoxWithConstraints(Modifier.weight(1f)) {
                WordFitText(
                    stringResource(R.string.collection_sheet_title),
                    MaterialTheme.typography.titleLarge,
                    constraints.maxWidth,
                    Modifier.semantics { heading() }.testTag(AddToCollectionTags.TITLE),
                )
            }
            TextButton(onClick = done, modifier = Modifier.heightIn(min = CardTarget).testTag(AddToCollectionTags.DONE)) {
                Text(stringResource(R.string.collection_sheet_done))
            }
        }
        Box(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
                NewCollection(onCreate = ::create)
                val rows = listed
                val held = holding
                if (rows != null && held != null) {
                    if (rows.isEmpty()) {
                        Text(
                            stringResource(R.string.collection_sheet_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).testTag(AddToCollectionTags.EMPTY),
                        )
                    }
                    rows.forEach { row ->
                        val id = CollectionId(row.collectionId)
                        val ticked = ticking[id] ?: (id in held)
                        ListItem(
                            headlineContent = { Text(row.name) },
                            leadingContent = { Checkbox(checked = ticked, onCheckedChange = null) },
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = CardTarget)
                                .toggleable(value = ticked, role = Role.Checkbox) { toggle(id, it) }
                                .testTag(AddToCollectionTags.collection(row.collectionId)),
                        )
                    }
                }
            }
            // Over the sheet's foot, above the keyboard where it is up; the host reads each notice out as it comes.
            SnackbarHost(notices, Modifier.align(Alignment.BottomCenter).imePadding().testTag(AddToCollectionTags.NOTICE))
        }
    }
}

/** What [change] came to; null where it failed. */
private suspend fun <T> attempt(change: suspend () -> T): T? =
    try {
        change()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

/**
 * "New collection…", and in its place once tapped a field for the name, which takes the focus with the keyboard up, and
 * Create, which hands the name to [onCreate] and turns the row back. The name is cut at [RecipeCollection.MAX_NAME]
 * characters; Create waits for one that is not blank. The keyboard's Done only closes the keyboard.
 */
@Composable
private fun NewCollection(onCreate: (String) -> Unit) {
    var naming by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    if (!naming) {
        ListItem(
            headlineContent = { Text(stringResource(R.string.collection_sheet_new), color = MaterialTheme.colorScheme.primary) },
            leadingContent = { Icon(painterResource(R.drawable.ic_add_item), contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = CardTarget)
                .clickable(role = Role.Button) { naming = true }
                .testTag(AddToCollectionTags.NEW),
        )
        return
    }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { focus.requestFocus() }
    val named = RecipeCollection.nameOf(name)
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(RecipeCollection.MAX_NAME) },
            label = { Text(stringResource(R.string.collection_sheet_name)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }),
            modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag(AddToCollectionTags.NAME),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(
                onClick = {
                    if (named != null) {
                        onCreate(named)
                        name = ""
                        naming = false
                    }
                },
                enabled = named != null,
                modifier = Modifier.heightIn(min = CardTarget).testTag(AddToCollectionTags.CREATE),
            ) { Text(stringResource(R.string.collection_sheet_create)) }
        }
    }
}

/** What the card says once the sheet closes on a change: the recipe is in [count] of the reader's collections. */
internal fun noticeOf(resources: Resources, count: Int): String =
    if (count == 0) {
        resources.getString(R.string.collection_sheet_in_none)
    } else {
        resources.getQuantityString(R.plurals.collection_sheet_in, count, count)
    }

object AddToCollectionTags {
    const val SHEET = "add-to-collection"
    const val TITLE = "add-to-collection-title"
    const val DONE = "add-to-collection-done"
    const val NEW = "add-to-collection-new"
    const val NAME = "add-to-collection-name"
    const val CREATE = "add-to-collection-create"
    const val EMPTY = "add-to-collection-empty"
    const val NOTICE = "add-to-collection-notice"

    /** The row of the collection [id]. */
    fun collection(id: String) = "add-to-collection-$id"
}
