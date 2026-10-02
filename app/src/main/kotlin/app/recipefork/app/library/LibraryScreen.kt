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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import app.recipefork.app.card.CardScreen
import app.recipefork.app.card.forkRecipe
import app.recipefork.app.card.keepScreenAwake
import app.recipefork.app.card.rememberCardEnvironment
import app.recipefork.app.editor.EditorEnvironment
import app.recipefork.app.editor.EditorScreen
import app.recipefork.app.editor.interfaceLanguage
import app.recipefork.app.editor.newRecipe
import app.recipefork.app.editor.rememberEditorEnvironment
import app.recipefork.app.file.RecipeExport
import app.recipefork.app.file.RecipeImport
import app.recipefork.app.file.ImportOutcome
import app.recipefork.app.file.FileMessage
import app.recipefork.app.file.FileMessageDialog
import app.recipefork.app.file.message
import app.recipefork.app.file.importSafely
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.timer.CardTimers
import app.recipefork.app.timer.rememberCardTimers
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.RecipeState
import app.recipefork.core.recipe.SentStatus
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock

/** How many own recipes are read from the device at a time, and asked for again near the end (FLOW-041). */
const val OWN_PAGE = 30

/**
 * "My recipe book": the marked recipes and the recipes written in the active book (FLOW-034), and the editor on
 * a new recipe written from it (FLOW-042). An own recipe's entry opens its card (FLOW-029 (1)); the card's edit
 * action opens the editor on it, and leaving the editor shows that card again. The card's fork action writes a fork
 * and opens the editor on it, so that leaving it shows the fork's card; a fork's provenance opens its parent's card
 * in place of the fork's. In [Mode.Serverless] every own recipe's entry offers its export to a file (FLOW-063); in
 * [Mode.Standard] no export exists (FLOW-062 (8)).
 *
 * The view of marked recipes lists the found recipes the book holds (FLOW-064 (1)), each opening its card and offering
 * its removal (FLOW-064 (6)); in [Mode.Serverless] it offers the import of a recipe file through the system's document
 * picker as well, and in [Mode.Standard] no import exists.
 *
 * What became of a file — an import's outcome, a failed export — is said in a dialog over whatever is shown, until it
 * is closed.
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
) {
    val message by session.message.collectAsState()
    message?.let { FileMessageDialog(it, onDone = session::told) }
    val editing by session.editing.collectAsState()
    editing?.let { id ->
        EditorScreen(RecipeId(id), recipes, editor, session.viewModelScope, onClose = session::closeEditor, modifier = modifier)
        return
    }
    val viewing by session.viewing.collectAsState()
    viewing?.let { id ->
        // One fork per tap: a second tap while the first is being written adds none.
        var forking by remember(id) { mutableStateOf(false) }
        CardScreen(
            RecipeId(id), recipes, card,
            onBack = session::closeCard,
            onEdit = { session.openEditor(id) },
            // The screen stays on while the card is shown, and is left to the device once it is not (FLOW-014).
            modifier = modifier.keepScreenAwake(),
            onFork = {
                if (!forking) {
                    forking = true
                    session.viewModelScope.launch {
                        // The editor opens on the fork; leaving it shows the fork's card (FLOW-029 (1)).
                        // The guard stays up once a fork is written: a tap landing before the editor replaces the
                        // card must not write a second one. Only a fork that was not written lets the card try again.
                        val fork = forkRecipe(recipes, editor.book, RecipeId(id), editor.clock())
                        if (fork != null) session.openEditor(fork.value) else forking = false
                    }
                }
            },
            onOpenParent = { parent -> session.openCard(parent.value) },
        )
        return
    }
    val view by session.view.collectAsState()
    val timers = rememberCardTimers()
    Column(modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.library_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(16.dp).semantics { heading() }.testTag(LibraryTags.TITLE),
        )
        // The switch sits above the list and never scrolls with it (FLOW-034 (1)).
        Row(Modifier.fillMaxWidth().selectableGroup().testTag(LibraryTags.SWITCH)) {
            ViewTab(LibraryView.MARKED, view, R.string.library_view_marked, R.string.library_view_marked_action, session)
            ViewTab(LibraryView.OWN, view, R.string.library_view_own, R.string.library_view_own_action, session)
        }
        HorizontalDivider()
        when (view) {
            LibraryView.MARKED -> MarkedList(recipes, session, files, timers, mode, thumbnails)
            LibraryView.OWN -> OwnList(recipes, session, editor, timers, mode, thumbnails)
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

/** Built from the device alone: nothing here waits on or fails for the network (FLOW-043 (5)). */
@Composable
private fun OwnList(
    recipes: RecipeRepository,
    session: LibrarySession,
    editor: EditorEnvironment,
    timers: CardTimers,
    mode: Mode,
    thumbnails: CoverThumbnails,
) {
    var limit by rememberSaveable { mutableIntStateOf(OWN_PAGE) }
    val rows by remember(limit) { recipes.ownNewest(limit) }.collectAsState(initial = null)
    val listState = rememberLazyListState()
    val loaded = rows
    // Near the end of what is loaded, the next recipes are read from the device (FLOW-041 (5)).
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
    loaded?.firstOrNull { it.recipeId == deleting }?.let { row ->
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
    if (loaded != null && loaded.isEmpty()) {
        NothingWritten(recipes, session, editor)
        return
    }
    Column(Modifier.fillMaxSize()) {
        // A book that lists recipes still offers to write one, above the list and never scrolled away (FLOW-042 (5)).
        if (loaded != null) {
            WriteButton(R.string.library_own_new, LibraryTags.NEW, recipes, session, editor, Modifier.padding(16.dp))
            HorizontalDivider()
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
 * An own recipe's entry; tapping it opens the recipe's card (FLOW-029 (1)). A recipe its owner may delete carries the
 * offer to beside it; any other carries none and the entry takes the whole width (FLOW-053 (1), ST-010).
 *
 * In [Mode.Serverless] every entry offers its export as well (FLOW-063). Its offers then stand on a line of their own
 * under the entry's text, which keeps the whole width, so that a title stays readable on a narrow phone at a large
 * font scale.
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
    val deletable = row.state in DELETABLE
    when (mode) {
        Mode.Standard -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            EntryText(row, cover, onOpen, Modifier.weight(1f))
            if (deletable) DeleteOffer(row, onDelete, Modifier.padding(end = 8.dp))
        }
        Mode.Serverless -> Column(Modifier.fillMaxWidth()) {
            EntryText(row, cover, onOpen, Modifier.fillMaxWidth())
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = onExport,
                    modifier = Modifier.heightIn(min = 48.dp).testTag(LibraryTags.export(row.recipeId)),
                ) { Text(stringResource(R.string.file_export)) }
                if (deletable) DeleteOffer(row, onDelete)
            }
        }
    }
    HorizontalDivider()
}

@Composable
private fun EntryText(row: OwnRecipeRow, cover: EntryCover, onOpen: () -> Unit, modifier: Modifier) {
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
        Column(Modifier.weight(1f)) { EntryLines(row) }
    }
}

@Composable
private fun EntryLines(row: OwnRecipeRow) {
    Text(
        text = row.title.ifBlank { stringResource(R.string.common_untitled_recipe) },
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.testTag(LibraryTags.entryTitle(row.recipeId)),
    )
    // The state is named in words, never carried by colour alone (A11Y-008).
    Text(stringResource(stateLabel(row.state)), style = MaterialTheme.typography.labelLarge)
    // The sending mark is shown beside the state and never replaces it (FLOW-036 (6), FLOW-005).
    Text(stringResource(sentLabel(row.sent)), style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun DeleteOffer(row: OwnRecipeRow, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(
        onClick = onDelete,
        modifier = modifier.heightIn(min = 48.dp).testTag(LibraryTags.delete(row.recipeId)),
    ) { Text(stringResource(R.string.library_entry_delete)) }
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
 * The view of marked recipes (FLOW-034 (2)): what the book marks, the found recipes among it. A found recipe's entry
 * opens its card and offers its removal, and in [Mode.Serverless] its export too (FLOW-063 (2)); one stating its author
 * names it, marked as stated and not verified (FLOW-064 (3)). In [Mode.Serverless] the view offers to import a recipe file above the list; with nothing marked it
 * says so, and that offer is the way out of it (FLOW-042).
 */
@Composable
private fun MarkedList(
    recipes: RecipeRepository,
    session: LibrarySession,
    files: RecipeImport,
    timers: CardTimers,
    mode: Mode,
    thumbnails: CoverThumbnails,
) {
    val marked by remember(recipes) { recipes.marked() }.collectAsState(initial = null)
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
    val loaded = marked
    Column(Modifier.fillMaxSize()) {
        if (mode == Mode.Serverless && loaded != null) {
            if (loaded.isEmpty()) {
                Text(
                    stringResource(R.string.library_marked_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(16.dp).testTag(LibraryTags.MARKED_EMPTY),
                )
            }
            ImportButton(files, session, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            HorizontalDivider()
        }
        val cover = coverOf(recipes, thumbnails)
        LazyColumn(Modifier.fillMaxSize().testTag(LibraryTags.MARKED_LIST)) {
            items(loaded.orEmpty(), key = { it.recipeId }) { row ->
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
 * A marked recipe's entry: its title and any stated author; one whose content the book holds opens and is removable.
 * In [Mode.Serverless] such an entry offers its export as well (FLOW-063 (2)), and its offers then stand on a line of
 * their own under the entry's text, as an own entry's do.
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
    when {
        mode == Mode.Serverless && row.held -> Column(Modifier.fillMaxWidth()) {
            MarkedEntryText(row, cover, onOpen, Modifier.fillMaxWidth())
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = onExport,
                    modifier = Modifier.heightIn(min = 48.dp).testTag(LibraryTags.export(row.recipeId)),
                ) { Text(stringResource(R.string.file_export)) }
                RemoveOffer(row, onRemove)
            }
        }
        else -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            MarkedEntryText(row, cover, onOpen, Modifier.weight(1f))
            if (row.held) RemoveOffer(row, onRemove, Modifier.padding(end = 8.dp))
        }
    }
    HorizontalDivider()
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
    Text(
        text = row.title.ifBlank { stringResource(R.string.common_untitled_recipe) },
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.testTag(LibraryTags.entryTitle(row.recipeId)),
    )
    row.statedAuthor?.let {
        Text(
            stringResource(R.string.file_stated_author, it),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.testTag(LibraryTags.statedAuthor(row.recipeId)),
        )
    }
}

@Composable
private fun RemoveOffer(row: MarkedRecipeRow, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(
        onClick = onRemove,
        modifier = modifier.heightIn(min = 48.dp).testTag(LibraryTags.remove(row.recipeId)),
    ) { Text(stringResource(R.string.library_entry_remove)) }
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
                if (outcome is ImportOutcome.Added) session.showImported()
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

/** The marker of FLOW-036: `local` and `private` are both a draft. */
@StringRes
fun stateLabel(state: RecipeState): Int = when (state) {
    RecipeState.LOCAL, RecipeState.PRIVATE -> R.string.library_state_draft
    RecipeState.PENDING -> R.string.library_state_pending
    RecipeState.REJECTED -> R.string.library_state_rejected
    RecipeState.LINK_SHARED -> R.string.library_state_link_shared
    RecipeState.PUBLISHED -> R.string.library_state_published
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
    fun tab(view: LibraryView) = "library-tab-${view.name.lowercase()}"
    const val DELETE_STATEMENT = "library-delete-statement"
    const val DELETE_CONFIRM = "library-delete-confirm"
    const val DELETE_DECLINE = "library-delete-decline"
    fun entry(recipeId: String) = "library-entry-$recipeId"
    fun delete(recipeId: String) = "library-delete-$recipeId"
    fun export(recipeId: String) = "library-export-$recipeId"
    fun entryTitle(recipeId: String) = "library-entry-title-$recipeId"
    fun statedAuthor(recipeId: String) = "library-stated-author-$recipeId"
    fun remove(recipeId: String) = "library-remove-$recipeId"
    fun cover(recipeId: String) = "library-cover-$recipeId"
    /** The place a cover with the tag [coverTag] holds, whether or not its picture is read yet. */
    fun coverSlot(coverTag: String) = "$coverTag-slot"
}
