package app.recipefork.app.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.HorizontalAlignmentLine
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import app.recipefork.app.Mode
import app.recipefork.app.R
import app.recipefork.app.book.MarkedRecipeRow
import app.recipefork.app.book.OwnRecipeRow
import app.recipefork.app.card.CardEnvironment
import app.recipefork.app.card.IngredientEntries
import app.recipefork.app.card.rememberCardEnvironment
import app.recipefork.app.data.DragHandleSide
import app.recipefork.app.data.UsualServings
import app.recipefork.app.editor.BlockAction
import app.recipefork.app.editor.EditorEnvironment
import app.recipefork.app.editor.interfaceLanguage
import app.recipefork.app.editor.newRecipe
import app.recipefork.app.editor.rememberEditorEnvironment
import app.recipefork.app.file.RecipeExport
import app.recipefork.app.file.RecipeImport
import app.recipefork.app.file.ImportOutcome
import app.recipefork.app.file.FileMessage
import app.recipefork.app.file.message
import app.recipefork.app.file.importSafely
import app.recipefork.app.recipe.BookFilters
import app.recipefork.app.recipe.BookLanguage
import app.recipefork.app.recipe.ListingParams
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.dictionary.TagSource
import app.recipefork.app.timer.CardTimers
import app.recipefork.app.timer.rememberCardTimers
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.RecipeState
import app.recipefork.core.recipe.SentStatus
import app.recipefork.core.search.BookOrder
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlin.math.min
import kotlin.math.roundToInt

/** How many own recipes are shown at a time, and more shown near the end (FLOW-041). */
const val OWN_PAGE = 30

/**
 * "My recipe book": the marked recipes and the recipes written in the active book (FLOW-034), and the editor on
 * a new recipe written from it (FLOW-042). An own recipe's entry opens its card (FLOW-029 (1)); the card and the editor
 * are destinations of their own, above the book (AppNavHost), which keeps its place beneath them. In [Mode.Serverless]
 * every own recipe's entry offers its export to a file (FLOW-063); in [Mode.Standard] no export exists (FLOW-062 (8)).
 *
 * The view of marked recipes lists the found recipes the book holds (FLOW-064 (1)), each opening its card and offering
 * its removal (FLOW-064 (6)); in [Mode.Serverless] it offers the import of a recipe file through the system's document
 * picker as well, and in [Mode.Standard] no import exists. An entry's actions are in its menu, at the end of its title
 * line; the list's header and its entries stay apart.
 *
 * The book's menu, beside its title, holds the serving count the reader usually cooks for (FLOW-017), which every
 * card opens at, the side the editor's drag column stands on, and the clearing of the media the device need not keep
 * (FLOW-060), stated first and done only once confirmed.
 */
@Composable
fun LibraryScreen(
    session: LibrarySession,
    recipes: RecipeRepository,
    mode: Mode = Mode.Standard,
    modifier: Modifier = Modifier,
    editor: EditorEnvironment = rememberEditorEnvironment(),
    card: CardEnvironment = rememberCardEnvironment(),
    files: RecipeImport = rememberRecipeImport(recipes),
    thumbnails: CoverThumbnails = rememberCoverThumbnails(),
    usualServings: UsualServings = card.usualServings,
    dragHandleSide: DragHandleSide = editor.dragHandleSide,
) {
    val view by session.view.collectAsState()
    val timers = rememberCardTimers()
    // The clearing of saved media (FLOW-060): asked about first, or said to have nothing to clear.
    var clearing by rememberSaveable { mutableStateOf<ClearingMedia?>(null) }
    when (clearing) {
        ClearingMedia.ASKING -> ClearMediaStatement(
            mode,
            onConfirm = {
                clearing = null
                session.viewModelScope.launch { recipes.clearMedia() }
            },
            onDecline = { clearing = null },
        )
        ClearingMedia.NOTHING -> NothingToClear(onDone = { clearing = null })
        null -> Unit
    }
    Column(modifier.fillMaxSize()) {
        // The book's top area: its title, with the book's menu beside it (FLOW-017), above the switch of views.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.library_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f).padding(16.dp).semantics { heading() }.testTag(LibraryTags.TITLE),
            )
            BookMenu(
                usualServings, dragHandleSide, Modifier.padding(end = 8.dp),
                onClearMedia = {
                    session.viewModelScope.launch {
                        clearing = if (recipes.clearableMedia().isEmpty()) ClearingMedia.NOTHING else ClearingMedia.ASKING
                    }
                },
            )
        }
        // The switch sits above the list and never scrolls with it (FLOW-034 (1)).
        Row(Modifier.fillMaxWidth().selectableGroup().testTag(LibraryTags.SWITCH)) {
            ViewTab(LibraryView.MARKED, view, R.string.library_view_marked, R.string.library_view_marked_action, session)
            ViewTab(LibraryView.OWN, view, R.string.library_view_own, R.string.library_view_own_action, session)
        }
        HorizontalDivider()
        when (view) {
            LibraryView.MARKED -> MarkedList(recipes, session, files, timers, mode, thumbnails, card.ingredients, editor.tags)
            LibraryView.OWN -> OwnList(recipes, session, editor, timers, mode, thumbnails, card.ingredients)
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.ViewTab(
    tab: LibraryView,
    current: LibraryView,
    @StringRes label: Int,
    @StringRes action: Int,
    session: LibrarySession,
) {
    val selected = tab == current
    Column(
        modifier = Modifier
            .weight(1f)
            .heightIn(min = 48.dp)
            .semantics { this.selected = selected }
            .clickable(onClickLabel = stringResource(action), role = Role.Tab) { session.choose(tab) }
            .testTag(LibraryTags.tab(tab)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(label),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(vertical = 12.dp),
        )
        Box(
            Modifier.fillMaxWidth().height(3.dp)
                .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface),
        )
    }
}

/**
 * Built from the device alone: nothing here waits on or fails for the network (FLOW-043 (5)). Its header searches and
 * orders it (FLOW-035); a book in which nothing is written says so in place of the list and the header.
 */
@Composable
private fun OwnList(
    recipes: RecipeRepository,
    session: LibrarySession,
    editor: EditorEnvironment,
    timers: CardTimers,
    mode: Mode,
    thumbnails: CoverThumbnails,
    dictionary: IngredientEntries,
) {
    val search = rememberSearchField(LibraryView.OWN, session)
    val params = rememberListingParams(LibraryView.OWN, session)
    val listed by remember(recipes, params, dictionary) { recipes.ownListed(params, dictionary::entries) }
        .collectAsState(initial = null)
    val query by session.query(LibraryView.OWN).collectAsState()
    val order by session.order(LibraryView.OWN).collectAsState()
    val filters by session.filters(LibraryView.OWN).collectAsState()
    val choices = rememberFilterChoices(dictionary, editor.tags)
    // A new query, order or filter shows its listing from the beginning (FLOW-041 (4)).
    var limit by rememberSaveable(query, order, filters) { mutableIntStateOf(OWN_PAGE) }
    val listState = rememberLazyListState()
    val found = listed?.rows
    val loaded = found?.take(limit)
    ScrollToTopOnChange(listed?.params, listState)
    // Near the end of what is shown, the next recipes are shown (FLOW-041 (5)).
    LaunchedEffect(listState, loaded?.size, limit) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .distinctUntilChanged()
            .collect { last ->
                val size = loaded?.size ?: return@collect
                if (size == limit && last >= size - OWN_PAGE / 3) limit += OWN_PAGE
            }
    }
    // The recipe whose deletion is being asked about, while it is (FLOW-053 (2)).
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    found?.firstOrNull { it.recipeId == deleting }?.let { row ->
        DeleteStatement(
            row,
            onConfirm = {
                deleting = null
                deleteRecipe(recipes, timers, RecipeId(row.recipeId))
            },
            onDecline = { deleting = null },
        )
    }
    val export = rememberExport(recipes, session)
    val shown = listed
    if (shown != null && shown.viewIsEmpty && !shown.narrowed) {
        NothingWritten(recipes, session, editor)
        return
    }
    Column(Modifier.fillMaxSize()) {
        // A book that lists recipes still offers to write one, above the list and never scrolled away (FLOW-042 (5)).
        if (shown != null) {
            WriteButton(R.string.library_own_new, LibraryTags.NEW, recipes, session, editor, Modifier.padding(16.dp))
            HorizontalDivider()
            ListHeader(
                search, order, onOrder = { session.orderBy(LibraryView.OWN, it) },
                filters, onFilters = { session.filter(LibraryView.OWN, it) }, choices, shown.languages, shown.hold,
            )
            HorizontalDivider()
        }
        if (shown != null && shown.narrowed && shown.rows.isEmpty()) {
            NothingFound(shown, onClearSearch = search::clear, onClearFilters = { session.filter(LibraryView.OWN, BookFilters()) })
            return@Column
        }
        val cover = coverOf(recipes, thumbnails)
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().testTag(LibraryTags.OWN_LIST)) {
            items(loaded.orEmpty(), key = { it.recipeId }) { row ->
                OwnEntry(
                    row,
                    mode,
                    cover,
                    onOpen = { session.openCard(row.recipeId, LibraryView.OWN) },
                    onDelete = { deleting = row.recipeId },
                    onExport = { export(row.recipeId, row.title) },
                )
            }
        }
    }
}

/**
 * The own-recipes view of a book in which nothing has been written: it says so, and its one offer writes a new
 * recipe in that book and opens it in the editor (FLOW-042).
 */
@Composable
private fun NothingWritten(recipes: RecipeRepository, session: LibrarySession, editor: EditorEnvironment) {
    Column(
        Modifier.fillMaxSize().padding(16.dp).testTag(LibraryTags.OWN_EMPTY),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.library_own_empty), style = MaterialTheme.typography.bodyLarge)
        WriteButton(R.string.library_own_write, LibraryTags.WRITE, recipes, session, editor)
    }
}

/** Writes a new recipe in the active book and opens it in the editor (FLOW-042 (4, 5)); a second tap adds none. */
@Composable
private fun WriteButton(
    @StringRes label: Int,
    tag: String,
    recipes: RecipeRepository,
    session: LibrarySession,
    editor: EditorEnvironment,
    modifier: Modifier = Modifier,
) {
    val language = interfaceLanguage(LocalConfiguration.current)
    var writing by remember { mutableStateOf(false) }
    Button(
        onClick = {
            if (writing) return@Button
            writing = true
            val recipe = newRecipe(editor, language)
            session.viewModelScope.launch {
                recipes.save(recipe)
                session.openEditor(recipe.id.value)
            }
        },
        modifier = modifier.heightIn(min = 48.dp).testTag(tag),
    ) { Text(stringResource(label)) }
}

/**
 * An own recipe's entry; tapping it opens the recipe's card (FLOW-029 (1)). Its actions are in its menu, at the end of
 * its title line: deletion for a recipe its owner may delete (FLOW-053 (1), ST-010), and in [Mode.Serverless] the
 * export of every recipe (FLOW-063). An entry with no action carries no menu, and its text takes the whole width.
 */
@Composable
private fun OwnEntry(
    row: OwnRecipeRow,
    mode: Mode,
    cover: EntryCover,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit,
) {
    val actions = buildList {
        if (mode == Mode.Serverless) add(EntryAction(R.string.file_export, LibraryTags.export(row.recipeId), onExport))
        if (row.state in DELETABLE) add(EntryAction(R.string.library_entry_delete, LibraryTags.delete(row.recipeId), onDelete))
    }
    EntryWithActions(row.recipeId, row.title, actions) { modifier -> EntryText(row, mode, cover, onOpen, modifier) }
}

@Composable
private fun EntryText(row: OwnRecipeRow, mode: Mode, cover: EntryCover, onOpen: () -> Unit, modifier: Modifier) {
    Row(
        modifier
            .heightIn(min = 48.dp)
            .semantics(mergeDescendants = true) {}
            .clickable(onClickLabel = stringResource(R.string.library_entry_open_action), role = Role.Button, onClick = onOpen)
            .testTag(LibraryTags.entry(row.recipeId))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        cover(row.coverId, LibraryTags.cover(row.recipeId))
        Column(Modifier.weight(1f)) { EntryLines(row, mode) }
    }
}

@Composable
private fun EntryLines(row: OwnRecipeRow, mode: Mode) {
    EntryTitle(row.recipeId, row.title)
    // The state is named in words, never carried by colour alone (A11Y-008).
    Text(stringResource(stateLabel(row.state, mode)), style = MaterialTheme.typography.labelLarge)
    // The sending mark is shown beside the state and never replaces it (FLOW-036 (6), FLOW-005); a build without the
    // server sends nothing, so it shows none (FLOW-062 (1)).
    if (mode == Mode.Standard) Text(stringResource(sentLabel(row.sent)), style = MaterialTheme.typography.bodySmall)
}

/** An entry's title, which tells the entry's menu where its first line stands ([TitleLine]). */
@Composable
private fun EntryTitle(recipeId: String, title: String) {
    // The middle of the first line, as the title was last laid out; the text is laid out while it is measured.
    val firstLine = remember { FirstLine() }
    Text(
        text = title.ifBlank { stringResource(R.string.common_untitled_recipe) },
        style = MaterialTheme.typography.titleMedium,
        onTextLayout = { firstLine.centre = ((it.getLineTop(0) + it.getLineBottom(0)) / 2).roundToInt() },
        modifier = Modifier
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val centre = firstLine.centre.coerceIn(0, placeable.height)
                layout(placeable.width, placeable.height, mapOf(TitleLine to centre)) { placeable.place(0, 0) }
            }
            .testTag(LibraryTags.entryTitle(recipeId)),
    )
}

/** Where the middle of a title's first line stands, in pixels from the title's top. */
private class FirstLine(var centre: Int = 0)

/** The middle of an entry's first title line, which the entry's menu stands level with. */
private val TitleLine = HorizontalAlignmentLine { a, b -> min(a, b) }

/** One item of an entry's menu: what it reads, its tag, and what choosing it does. */
private class EntryAction(@StringRes val label: Int, val tag: String, val onClick: () -> Unit)

/**
 * An entry of the book and, when it has any [actions], its menu at the end of its title line; the entry's own
 * [content] takes the rest of the width, or the whole width when there is no action.
 */
@Composable
private fun EntryWithActions(
    recipeId: String,
    title: String,
    actions: List<EntryAction>,
    content: @Composable (Modifier) -> Unit,
) {
    Row(Modifier.fillMaxWidth()) {
        content(Modifier.weight(1f).alignBy(TitleLine))
        if (actions.isNotEmpty()) {
            EntryMenu(recipeId, title, actions, Modifier.alignBy { it.measuredHeight / 2 }.padding(end = 4.dp))
        }
    }
    HorizontalDivider()
}

/**
 * The ⋮ of an entry, 48 by 48 dp (A11Y-003), named for the screen reader by the recipe it acts on (A11Y-005), with
 * the same name in a tooltip on a long press; it opens the menu of the entry's [actions].
 */
@Composable
private fun EntryMenu(recipeId: String, title: String, actions: List<EntryAction>, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val name = title.ifBlank { stringResource(R.string.common_untitled_recipe) }
    Box(modifier) {
        BlockAction(
            R.drawable.ic_row_menu,
            stringResource(R.string.library_entry_actions, name),
            LibraryTags.actions(recipeId),
            onClick = { expanded = !expanded },
            action = stringResource(R.string.library_entry_actions_action),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            actions.forEach { action ->
                DropdownMenuItem(
                    text = { Text(stringResource(action.label)) },
                    onClick = {
                        expanded = false
                        action.onClick()
                    },
                    modifier = Modifier.testTag(action.tag),
                )
            }
        }
    }
}

/**
 * Exports a recipe of the book, own or found (FLOW-063): its file is written and the system's share and save sheet
 * opens on it. Called with the recipe's identity and the title its entry shows. A tap while a file is being written
 * starts no second one. Export reads the recipe and changes nothing in it. An export that fails says so.
 */
@Composable
private fun rememberExport(recipes: RecipeRepository, session: LibrarySession): (recipeId: String, title: String) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var exporting by remember { mutableStateOf(false) }
    return { recipeId, entryTitle ->
        if (!exporting) {
            exporting = true
            scope.launch {
                try {
                    val title = entryTitle.ifBlank { context.getString(R.string.common_untitled_recipe) }
                    val sheet = context.getString(R.string.file_export_sheet, title)
                    if (!RecipeExport.share(context, recipes, RecipeId(recipeId), sheet)) {
                        session.tell(FileMessage(FileMessage.Kind.EXPORT_FAILED))
                    }
                } finally {
                    exporting = false
                }
            }
        }
    }
}

/** Shows an entry's cover, given its media identity (null for none) and the picture's tag. */
private typealias EntryCover = @Composable (mediaId: String?, tag: String) -> Unit

/** The covers of the active book's entries, read from where its media is kept (MEDIA-001). */
@Composable
private fun coverOf(recipes: RecipeRepository, thumbnails: CoverThumbnails): EntryCover {
    val book = recipes.bookName()
    val files = recipes.filesDirectory
    return remember(book, files, thumbnails) {
        { mediaId, tag -> CoverThumbnail(mediaId, book, files, thumbnails, tag) }
    }
}

/** The states in which an own recipe may be deleted by its owner (ST-010); in any other it may not. */
val DELETABLE = setOf(RecipeState.LOCAL, RecipeState.PRIVATE, RecipeState.REJECTED)

/**
 * The statement shown before anything is destroyed (FLOW-053 (2)): the recipe goes at once, with its photographs and
 * video, and cannot be brought back. Nothing is deleted until it is confirmed; declining it changes nothing.
 */
@Composable
private fun DeleteStatement(row: OwnRecipeRow, onConfirm: () -> Unit, onDecline: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDecline,
        modifier = Modifier.testTag(LibraryTags.DELETE_STATEMENT),
        title = {
            Text(
                stringResource(
                    R.string.library_delete_title,
                    row.title.ifBlank { stringResource(R.string.common_untitled_recipe) },
                ),
            )
        },
        text = { Text(stringResource(R.string.library_delete_statement)) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.heightIn(min = 48.dp).testTag(LibraryTags.DELETE_CONFIRM),
            ) { Text(stringResource(R.string.library_delete_confirm)) }
        },
        dismissButton = {
            TextButton(
                onClick = onDecline,
                modifier = Modifier.heightIn(min = 48.dp).testTag(LibraryTags.DELETE_DECLINE),
            ) { Text(stringResource(R.string.library_delete_decline)) }
        },
    )
}

/** Where the clearing of saved media stands while a dialog of it is shown. */
private enum class ClearingMedia { ASKING, NOTHING }

/**
 * The statement before saved media is cleared (FLOW-060): nothing is deleted until it is confirmed, and declining it
 * changes nothing. In [Mode.Serverless] it says the media cannot be fetched again (FLOW-060 (4)) and names nothing of
 * the server (FLOW-062 (1)); in [Mode.Standard] it says the media is fetched again when needed.
 */
@Composable
private fun ClearMediaStatement(mode: Mode, onConfirm: () -> Unit, onDecline: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDecline,
        modifier = Modifier.testTag(LibraryTags.CLEAR_MEDIA_STATEMENT),
        title = { Text(stringResource(R.string.library_clear_media_title)) },
        text = {
            Text(
                stringResource(
                    when (mode) {
                        Mode.Serverless -> R.string.library_clear_media_statement_serverless
                        Mode.Standard -> R.string.library_clear_media_statement
                    },
                ),
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.heightIn(min = 48.dp).testTag(LibraryTags.CLEAR_MEDIA_CONFIRM),
            ) { Text(stringResource(R.string.library_clear_media_confirm)) }
        },
        dismissButton = {
            TextButton(
                onClick = onDecline,
                modifier = Modifier.heightIn(min = 48.dp).testTag(LibraryTags.CLEAR_MEDIA_DECLINE),
            ) { Text(stringResource(R.string.library_clear_media_decline)) }
        },
    )
}

/** Said in place of the statement when the device holds no media the clearing would delete. */
@Composable
private fun NothingToClear(onDone: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDone,
        modifier = Modifier.testTag(LibraryTags.CLEAR_MEDIA_NOTHING),
        text = { Text(stringResource(R.string.library_clear_media_nothing)) },
        confirmButton = {
            TextButton(
                onClick = onDone,
                modifier = Modifier.heightIn(min = 48.dp).testTag(LibraryTags.CLEAR_MEDIA_OK),
            ) { Text(stringResource(R.string.library_clear_media_ok)) }
        },
    )
}

/**
 * Deletes [id] from the active book, on the device alone (FLOW-053 (6, 8)), and cancels its timers, so that none of
 * them announces itself or rings afterwards. It runs where the timers' work runs, which outlives the screen, one turn
 * among the timers' starts and cancels, so that it finishes however soon the view is left.
 */
fun deleteRecipe(recipes: RecipeRepository, timers: CardTimers, id: RecipeId) {
    timers.scope.launch {
        timers.turns.withLock {
            recipes.delete(id)
            timers.store.cancelRecipe(timers.book(), id)
        }
    }
}

/** The import of recipe files, writing found recipes' media under the application's own files. */
@Composable
fun rememberRecipeImport(recipes: RecipeRepository): RecipeImport {
    val context = LocalContext.current.applicationContext
    return remember(recipes, context) { RecipeImport(recipes, context.filesDir) }
}

/**
 * A new query, order or filter shows its listing from the beginning (FLOW-041 (4)): the list is brought to its top
 * when the [params] it lists for change from those it last listed for. A list restored — after a configuration change
 * or the end of the process — lists for the params it last did, and keeps its place.
 */
@Composable
private fun ScrollToTopOnChange(params: ListingParams?, state: LazyListState) {
    var seen by rememberSaveable { mutableStateOf<String?>(null) }
    val key = params?.key
    LaunchedEffect(key) {
        if (key == null) return@LaunchedEffect
        if (seen != null && seen != key) state.scrollToItem(0)
        seen = key
    }
}

/**
 * The view of marked recipes (FLOW-034 (2)): what the book marks, the found recipes among it. A found recipe's entry
 * opens its card and offers its removal, and in [Mode.Serverless] its export too (FLOW-063 (2)); one stating its author
 * names it, marked as stated and not verified (FLOW-064 (3)). In [Mode.Serverless] the view offers to import a recipe file above the list; with nothing marked it
 * says so, and that offer is the way out of it (FLOW-042). Its header, above the list, searches and orders it (FLOW-035).
 */
@Composable
private fun MarkedList(
    recipes: RecipeRepository,
    session: LibrarySession,
    files: RecipeImport,
    timers: CardTimers,
    mode: Mode,
    thumbnails: CoverThumbnails,
    dictionary: IngredientEntries,
    tags: TagSource,
) {
    val search = rememberSearchField(LibraryView.MARKED, session)
    val params = rememberListingParams(LibraryView.MARKED, session)
    val listed by remember(recipes, params, dictionary) { recipes.markedListed(params, dictionary::entries) }
        .collectAsState(initial = null)
    val order by session.order(LibraryView.MARKED).collectAsState()
    val filters by session.filters(LibraryView.MARKED).collectAsState()
    val choices = rememberFilterChoices(dictionary, tags)
    val listState = rememberLazyListState()
    ScrollToTopOnChange(listed?.params, listState)
    val marked = listed?.rows
    var removing by rememberSaveable { mutableStateOf<String?>(null) }
    marked?.firstOrNull { it.recipeId == removing }?.let { row ->
        RemoveStatement(
            row,
            onConfirm = {
                removing = null
                removeFound(files, timers, RecipeId(row.recipeId))
            },
            onDecline = { removing = null },
        )
    }
    val export = rememberExport(recipes, session)
    val shown = listed
    Column(Modifier.fillMaxSize()) {
        if (mode == Mode.Serverless && shown != null) {
            if (shown.viewIsEmpty && !shown.narrowed) {
                Text(
                    stringResource(R.string.library_marked_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(16.dp).testTag(LibraryTags.MARKED_EMPTY),
                )
            }
            ImportButton(files, session, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            HorizontalDivider()
        }
        // An empty view with no query keeps its empty state, with no header to search nothing with.
        if (shown != null && (!shown.viewIsEmpty || shown.narrowed)) {
            ListHeader(
                search, order, onOrder = { session.orderBy(LibraryView.MARKED, it) },
                filters, onFilters = { session.filter(LibraryView.MARKED, it) }, choices, shown.languages, shown.hold,
            )
            HorizontalDivider()
        }
        if (shown != null && shown.narrowed && shown.rows.isEmpty()) {
            NothingFound(shown, onClearSearch = search::clear, onClearFilters = { session.filter(LibraryView.MARKED, BookFilters()) })
            return@Column
        }
        val cover = coverOf(recipes, thumbnails)
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().testTag(LibraryTags.MARKED_LIST)) {
            items(marked.orEmpty(), key = { it.recipeId }) { row ->
                MarkedEntry(
                    row,
                    mode,
                    cover,
                    onOpen = { session.openCard(row.recipeId, LibraryView.MARKED) },
                    onRemove = { removing = row.recipeId },
                    onExport = { export(row.recipeId, row.title) },
                )
            }
        }
    }
}

/**
 * A marked recipe's entry: its title and any stated author; one whose content the book holds opens, and its menu, at
 * the end of its title line, offers its removal, and in [Mode.Serverless] its export as well (FLOW-063 (2)).
 */
@Composable
private fun MarkedEntry(
    row: MarkedRecipeRow,
    mode: Mode,
    cover: EntryCover,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
    onExport: () -> Unit,
) {
    val actions = if (!row.held) emptyList() else buildList {
        if (mode == Mode.Serverless) add(EntryAction(R.string.file_export, LibraryTags.export(row.recipeId), onExport))
        add(EntryAction(R.string.library_entry_remove, LibraryTags.remove(row.recipeId), onRemove))
    }
    EntryWithActions(row.recipeId, row.title, actions) { modifier -> MarkedEntryText(row, cover, onOpen, modifier) }
}

@Composable
private fun MarkedEntryText(row: MarkedRecipeRow, cover: EntryCover, onOpen: () -> Unit, modifier: Modifier) {
    val open = if (row.held) {
        Modifier.clickable(
            onClickLabel = stringResource(R.string.library_entry_open_action),
            role = Role.Button,
            onClick = onOpen,
        )
    } else {
        Modifier
    }
    Row(
        modifier
            .heightIn(min = 48.dp)
            .semantics(mergeDescendants = true) {}
            .then(open)
            .testTag(LibraryTags.entry(row.recipeId))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        cover(row.coverId, LibraryTags.cover(row.recipeId))
        Column(Modifier.weight(1f)) { MarkedEntryLines(row) }
    }
}

@Composable
private fun MarkedEntryLines(row: MarkedRecipeRow) {
    EntryTitle(row.recipeId, row.title)
    row.statedAuthor?.let {
        Text(
            stringResource(R.string.file_stated_author, it),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.testTag(LibraryTags.statedAuthor(row.recipeId)),
        )
    }
}

/**
 * Imports a recipe file the person picks with the system's document picker (FLOW-064). Any file may be picked, because
 * a file saved from a messenger rarely carries the product's type; one that is no recipe file is refused and said to
 * be. A tap while a file is being read starts no second import.
 */
@Composable
private fun ImportButton(files: RecipeImport, session: LibrarySession, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var importing by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) {
            importing = false
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            try {
                val outcome = files.importSafely(context, uri)
                session.tell(outcome.message())
                if (outcome is ImportOutcome.Added) session.showImported(outcome.id)
            } finally {
                importing = false
            }
        }
    }
    Button(
        onClick = {
            if (!importing) {
                importing = true
                picker.launch(arrayOf(ANY_FILE))
            }
        },
        modifier = modifier.heightIn(min = 48.dp).testTag(LibraryTags.IMPORT),
    ) { Text(stringResource(R.string.file_import)) }
}

/** Every type: the picker offers any file, and the importer decides what it holds. */
private const val ANY_FILE = "*/*"

/** The statement shown before a found recipe leaves the book with its content (FLOW-009 (4), FLOW-064 (6)). */
@Composable
private fun RemoveStatement(row: MarkedRecipeRow, onConfirm: () -> Unit, onDecline: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDecline,
        modifier = Modifier.testTag(LibraryTags.REMOVE_STATEMENT),
        title = {
            Text(
                stringResource(
                    R.string.library_remove_title,
                    row.title.ifBlank { stringResource(R.string.common_untitled_recipe) },
                ),
            )
        },
        text = { Text(stringResource(R.string.library_remove_statement)) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.heightIn(min = 48.dp).testTag(LibraryTags.REMOVE_CONFIRM),
            ) { Text(stringResource(R.string.library_remove_confirm)) }
        },
        dismissButton = {
            TextButton(
                onClick = onDecline,
                modifier = Modifier.heightIn(min = 48.dp).testTag(LibraryTags.REMOVE_DECLINE),
            ) { Text(stringResource(R.string.library_remove_decline)) }
        },
    )
}

/**
 * Removes the found recipe [id] from the active book with its held content and media (FLOW-064 (6)), and cancels its
 * timers, one turn among the timers' starts and cancels, where the timers' work runs, as [deleteRecipe] does.
 */
fun removeFound(files: RecipeImport, timers: CardTimers, id: RecipeId) {
    timers.scope.launch {
        timers.turns.withLock {
            files.remove(id)
            timers.store.cancelRecipe(timers.book(), id)
        }
    }
}

/**
 * The marker of FLOW-036: `local` and `private` are both a draft. In [Mode.Serverless] a recipe keeps the last state
 * the device knew (FLOW-062 (5)), worded with nothing of publication, review or a private link.
 */
@StringRes
fun stateLabel(state: RecipeState, mode: Mode): Int = when (state) {
    RecipeState.LOCAL, RecipeState.PRIVATE -> R.string.library_state_draft
    RecipeState.PENDING -> if (mode == Mode.Serverless) R.string.library_state_pending_serverless else R.string.library_state_pending
    RecipeState.REJECTED -> R.string.library_state_rejected
    RecipeState.LINK_SHARED -> if (mode == Mode.Serverless) R.string.library_state_link_shared_serverless else R.string.library_state_link_shared
    RecipeState.PUBLISHED -> if (mode == Mode.Serverless) R.string.library_state_published_serverless else R.string.library_state_published
    RecipeState.REPORTED_HIDDEN -> R.string.library_state_hidden
    RecipeState.REMOVED -> R.string.library_state_removed
}

/** The three marks of FLOW-005. */
@StringRes
fun sentLabel(sent: SentStatus): Int = when (sent) {
    SentStatus.NEVER -> R.string.library_sent_never
    SentStatus.SENT -> R.string.library_sent_sent
    SentStatus.SENT_WITH_CHANGES -> R.string.library_sent_with_changes
}

object LibraryTags {
    const val TITLE = "library-title"
    const val MENU = "library-menu"
    const val USUAL = "library-usual"
    const val USUAL_HELD = "library-usual-held"
    const val USUAL_DIALOG = "library-usual-dialog"
    const val USUAL_COUNT = "library-usual-count"
    const val USUAL_FEWER = "library-usual-fewer"
    const val USUAL_MORE = "library-usual-more"
    const val USUAL_SET = "library-usual-set"
    const val USUAL_CLEAR = "library-usual-clear"
    const val HANDLE_RIGHT = "library-handle-right"
    /** The book menu's clearing of saved media (FLOW-060), its statement and its message of nothing to clear. */
    const val CLEAR_MEDIA = "library-clear-media"
    const val CLEAR_MEDIA_STATEMENT = "library-clear-media-statement"
    const val CLEAR_MEDIA_CONFIRM = "library-clear-media-confirm"
    const val CLEAR_MEDIA_DECLINE = "library-clear-media-decline"
    const val CLEAR_MEDIA_NOTHING = "library-clear-media-nothing"
    const val CLEAR_MEDIA_OK = "library-clear-media-ok"
    const val SWITCH = "library-switch"
    const val OWN_LIST = "library-own"
    const val MARKED_LIST = "library-marked"
    const val MARKED_EMPTY = "library-marked-empty"
    const val IMPORT = "library-import"
    const val REMOVE_STATEMENT = "library-remove-statement"
    const val REMOVE_CONFIRM = "library-remove-confirm"
    const val REMOVE_DECLINE = "library-remove-decline"
    const val OWN_EMPTY = "library-own-empty"
    const val WRITE = "library-write"
    const val NEW = "library-new"
    /** The header above a view's list: its search field and its order. */
    const val HEADER = "library-header"
    const val SEARCH = "library-search"
    const val SEARCH_CLEAR = "library-search-clear"
    const val ORDER = "library-order"
    fun order(order: BookOrder) = "library-order-${order.name.lowercase()}"
    const val NOTHING_FOUND = "library-nothing-found"
    const val NOTHING_FOUND_CLEAR = "library-nothing-found-clear"
    const val NOTHING_FOUND_CLEAR_FILTERS = "library-nothing-found-clear-filters"
    /** The row of a view's filters, under its search field. */
    const val FILTERS = "library-filters"
    const val FILTER_ADD = "library-filter-add"
    const val FILTER_BY_INGREDIENT = "library-filter-by-ingredient"
    const val FILTER_BY_TAGS = "library-filter-by-tags"
    const val FILTER_BY_LANGUAGE = "library-filter-by-language"
    /** The chooser of the language filter, and each language it offers (L10N-005). */
    const val LANGUAGE_CHOOSER = "library-language-chooser"
    fun languageChoice(language: BookLanguage) = "library-language-choice-${language.name}"
    /** The chip of the language filter in force; tapping it removes the filter. */
    const val LANGUAGE_FILTER = "library-language-filter"
    const val FILTERS_CLEAR = "library-filters-clear"
    /** The chip of the ingredient filter in force; tapping it removes the filter. */
    const val INGREDIENT_FILTER = "library-ingredient-filter"
    fun tagFilter(tagId: Long) = "library-tag-filter-$tagId"
    const val INGREDIENT_CHOOSER = "library-ingredient-chooser"
    const val INGREDIENT_FIELD = "library-ingredient-field"
    fun ingredientChoice(entryId: Long) = "library-ingredient-choice-$entryId"
    const val TAG_CHOOSER = "library-tag-chooser"
    const val TAG_FIELD = "library-tag-field"
    const val TAG_NO_MATCH = "library-tag-no-match"
    const val TAG_CHOOSER_DONE = "library-tag-chooser-done"
    fun tagChoice(tagId: Long) = "library-tag-choice-$tagId"
    fun tab(view: LibraryView) = "library-tab-${view.name.lowercase()}"
    const val DELETE_STATEMENT = "library-delete-statement"
    const val DELETE_CONFIRM = "library-delete-confirm"
    const val DELETE_DECLINE = "library-delete-decline"
    fun entry(recipeId: String) = "library-entry-$recipeId"
    /** The ⋮ at the end of an entry's title line, which opens the menu of its actions. */
    fun actions(recipeId: String) = "library-actions-$recipeId"
    fun delete(recipeId: String) = "library-delete-$recipeId"
    fun export(recipeId: String) = "library-export-$recipeId"
    fun entryTitle(recipeId: String) = "library-entry-title-$recipeId"
    fun statedAuthor(recipeId: String) = "library-stated-author-$recipeId"
    fun remove(recipeId: String) = "library-remove-$recipeId"
    fun cover(recipeId: String) = "library-cover-$recipeId"
    /** The place a cover with the tag [coverTag] holds, whether or not its picture is read yet. */
    fun coverSlot(coverTag: String) = "$coverTag-slot"
}
