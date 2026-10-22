package app.recipefork.app.collections

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import app.recipefork.app.Mode
import app.recipefork.app.R
import app.recipefork.app.book.CollectionRecipeRow
import app.recipefork.app.book.CollectionRow
import app.recipefork.app.card.CardEnvironment
import app.recipefork.app.card.rememberCardEnvironment
import app.recipefork.app.data.DragHandleSide
import app.recipefork.app.data.HeldDragHandleSide
import app.recipefork.app.editor.BlockAction
import app.recipefork.app.editor.DragColumnBlock
import app.recipefork.app.editor.EditorScroll
import app.recipefork.app.editor.LocalDragColumnOnRight
import app.recipefork.app.editor.RowDrag
import app.recipefork.app.editor.rememberRowDrag
import app.recipefork.app.library.CoverThumbnails
import app.recipefork.app.library.ENTRY_GAP
import app.recipefork.app.library.EmptyState
import app.recipefork.app.library.EntryAction
import app.recipefork.app.library.BookEntryRow
import app.recipefork.app.library.EntryAuthor
import app.recipefork.app.library.EntryCover
import app.recipefork.app.library.EntryTitle
import app.recipefork.app.library.LibrarySession
import app.recipefork.app.library.LibrarySnackbarHost
import app.recipefork.app.library.ListLoading
import app.recipefork.app.library.RemovalNotice
import app.recipefork.app.library.TITLE_CLEARANCE
import app.recipefork.app.library.coverOf
import app.recipefork.app.library.rememberCoverThumbnails
import app.recipefork.app.media.PhotoStore
import app.recipefork.app.print.PrintWay
import app.recipefork.app.print.RecipesDocument
import app.recipefork.app.print.PrintProgress
import app.recipefork.app.print.rememberPrintOut
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.recipe.CollectionId
import app.recipefork.core.recipe.CollectionOrigin
import app.recipefork.core.recipe.RecipeCollection
import app.recipefork.core.recipe.RecipeId
import app.recipefork.app.shopping.rememberAddAllToShopping
import app.recipefork.app.ui.LineFitText
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * A collection's screen (v0.36), above the book: its recipes in its order, each as the book's entry shows it and opening
 * its card, which lies above this screen — back from it returns here. The bar names the collection; its ⋮ offers Share in
 * [Mode.Serverless], then, for the reader's own, Rename and Delete, and for an imported one Delete. Deleting it closes the screen, and the book
 * says so with Undo; its recipes stay in the book. In an own collection a recipe's ⋮ moves it up or down or removes it
 * from the collection — with Undo — and the drag column down its edge reorders it; an imported one is frozen: it shows
 * the author its file states, and its entries carry neither. A collection with no recipes says how one is added.
 *
 * The ⋮ of a collection with recipes, own or imported, puts them all on the shopping list ("Add all to shopping list"),
 * each at the count its card would open with; one listed already keeps its count. The screen says how many were added,
 * with Open, which opens the list above it.
 *
 * Its ⋮ then offers Print and Share as PDF (v0.37, T-262, scenario 7), in both modes: a document named by the
 * collection — a first page with its name and its recipes in order, then each recipe from a new page, at the count its
 * card would open with — handed to the system's printing or written as a PDF and shared, as a card's are.
 *
 * Once the book holds the collection no more — deleted here, or its book replaced — the screen closes.
 *
 * At large text (v0.38) the bar's name keeps to one line drawn smaller, or else two (T-297), ending with an ellipsis
 * where it is too long even so ([LineFitText]), and a notice's action stands on a line of its own ([LibrarySnackbarHost],
 * the book's).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionScreen(
    id: CollectionId,
    session: LibrarySession,
    collections: CollectionRepository,
    recipes: RecipeRepository,
    modifier: Modifier = Modifier,
    thumbnails: CoverThumbnails = rememberCoverThumbnails(),
    dragHandleSide: DragHandleSide = remember { HeldDragHandleSide() },
    card: CardEnvironment = rememberCardEnvironment(),
    mode: Mode = Mode.Standard,
) {
    val read by remember(collections, id) { collections.observe(id).map(::Read) }.collectAsState(initial = null)
    val listed by remember(collections, id) { collections.recipes(id) }.collectAsState(initial = null)
    val collection = read?.row
    LaunchedEffect(read) {
        if (read != null && collection == null && session.collection.value == id.value) session.closeCollection()
    }
    val scope = session.viewModelScope
    val snackbars = remember { SnackbarHostState() }
    // A recipe deleted from its card, opened from here, is said here with Undo, as the book says it.
    RemovalNotice(session, snackbars)
    var removed by remember { mutableStateOf<RemovedRecipe?>(null) }
    RemovedNotice(id, removed, collections, session, snackbars, onEnd = { removed = null })
    // The order the reader just dragged into, shown until the book's order follows.
    var moved by remember { mutableStateOf<List<CollectionRecipeRow>?>(null) }
    LaunchedEffect(listed) { moved = null }
    val rows = moved ?: listed
    // Every recipe of it on the shopping list (v0.36, T-257), in its order, at the counts their cards open with; Open opens
    // the list above the collection.
    val addAllToShopping = rememberAddAllToShopping(recipes, card, session::heldScale, snackbars, session::openShoppingAboveCollection)
    val addAll = { addAllToShopping { collections.recipes(id).first().map { RecipeId(it.recipeId) } } }
    var preparing by remember { mutableStateOf(false) }
    val print = rememberPrintCollection(id, session, collections, recipes, card, snackbars, onBusy = { preparing = it })
    var renaming by rememberSaveable { mutableStateOf(false) }
    if (renaming && collection != null) {
        RenameDialog(
            collection.name,
            onRename = { name ->
                renaming = false
                scope.launch { collections.rename(id, name) }
            },
            onDismiss = { renaming = false },
        )
    }
    val share = rememberCollectionShare(id, session, collections, recipes)
    var deleting by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val scroll = remember(scrollState) { EditorScroll(scrollState) }
    val delete = {
        if (!deleting) {
            deleting = true
            scope.launch {
                val whole = collections.get(id)
                if (whole != null && collections.delete(id)) session.collectionDeleted(whole) else deleting = false
            }
        }
    }
    Scaffold(
        modifier = modifier.fillMaxSize().testTag(CollectionScreenTags.SCREEN),
        snackbarHost = { LibrarySnackbarHost(snackbars) },
        topBar = {
            TopAppBar(
                title = {
                    // On one line or two, never under the bar's actions nor spilling below it at a large font scale (v0.38, T-297).
                    BoxWithConstraints(Modifier.padding(end = TITLE_CLEARANCE)) {
                        LineFitText(
                            collection?.name.orEmpty(),
                            LocalTextStyle.current,
                            constraints.maxWidth,
                            Modifier.semantics { heading() }.testTag(CollectionScreenTags.TITLE),
                        )
                    }
                },
                navigationIcon = { BackAction(session::closeCollection) },
                actions = {
                    if (collection != null) {
                        CollectionMenu(
                            editable = collection.origin.editable,
                            onShare = if (mode == Mode.Serverless) share::start else null,
                            onAddAll = addAll.takeIf { listed?.isNotEmpty() == true },
                            onPrint = { way: PrintWay -> print(collection.name, way) }.takeIf { listed?.isNotEmpty() == true },
                            onRename = { renaming = true },
                            onDelete = delete,
                        )
                    }
                },
                modifier = Modifier.testTag(CollectionScreenTags.BAR),
            )
        },
    ) { padding ->
        CollectionShareProgress(share, Modifier.padding(top = padding.calculateTopPadding()))
        if (preparing) PrintProgress(Modifier.padding(top = padding.calculateTopPadding()))
        val shown = rows
        if (collection == null || shown == null) {
            ListLoading(Modifier.padding(padding))
            return@Scaffold
        }
        val handleRight by dragHandleSide.right.collectAsState()
        val author = collection.statedAuthor?.takeIf { collection.origin == CollectionOrigin.IMPORTED }
        if (shown.isEmpty()) {
            // The empty state scrolls by itself at a large font scale.
            Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).padding(top = ENTRY_GAP)) {
                author?.let { StatedAuthor(it) }
                val own = collection.origin.editable
                EmptyState(
                    picture = R.drawable.ic_empty_book,
                    headline = stringResource(if (own) R.string.collection_empty else R.string.collection_imported_empty),
                    sentence = if (own) stringResource(R.string.collection_empty_sentence, stringResource(R.string.collection_sheet_menu)) else null,
                    action = null,
                    tag = CollectionScreenTags.EMPTY,
                )
            }
            return@Scaffold
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .onGloballyPositioned { scroll.viewport = it }
                .verticalScroll(scroll.state)
                .padding(vertical = ENTRY_GAP)
                .testTag(CollectionScreenTags.LIST),
        ) {
            // The recipes' rows meet at their hairlines, as in the book; the stated author keeps a gap above them.
            author?.let { StatedAuthor(it, Modifier.padding(bottom = ENTRY_GAP)) }
            val editable = collection.origin.editable
            val move: (Int, Int) -> Unit = { from, to ->
                val now = rows.orEmpty()
                if (from in now.indices && to in now.indices && from != to) {
                    val next = now.toMutableList().apply { add(to, removeAt(from)) }
                    moved = next
                    scope.launch {
                        // Refused — the collection gone or frozen meanwhile — the stored order shows again.
                        if (collections.reorder(id, next.map { RecipeId(it.recipeId) }) != CollectionChange.DONE) moved = null
                    }
                }
            }
            val drag = if (editable) rememberRowDrag(scroll, 0.dp, move) else null
            val cover = coverOf(recipes, thumbnails)
            CompositionLocalProvider(LocalDragColumnOnRight provides handleRight) {
                shown.forEachIndexed { position, row ->
                    key(row.recipeId) {
                        CollectionEntry(
                            row, position, shown.size, cover, drag, editable,
                            onOpen = { session.openCard(row.recipeId, session.view.value) },
                            onMove = move,
                            onRemove = {
                                scope.launch {
                                    if (collections.remove(id, RecipeId(row.recipeId)) == CollectionChange.DONE) {
                                        removed = RemovedRecipe(row.recipeId, row.title, position)
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

/** The line naming the author an imported collection's file states, marked as stated and not verified. */
@Composable
private fun StatedAuthor(author: String, modifier: Modifier = Modifier) {
    Text(
        stringResource(R.string.file_stated_author, author),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 16.dp).testTag(CollectionScreenTags.STATED_AUTHOR),
    )
}

/** The collection as last read: [row] is null once the book holds it no more. */
private class Read(val row: CollectionRow?)

/** A recipe just taken out of the collection, from the place [at], while its Undo is offered. */
private class RemovedRecipe(val recipeId: String, val title: String, val at: Int)

/**
 * A recipe of the collection as the book's entry shows it — its cover, its title and the author a found one's file
 * states — opening its card. In an own collection ([editable]) it is dragged by the column down its edge, and its ⋮ moves it up or down — what the drag does, for the screen reader (A11Y-006) — or takes it out of the
 * collection, in the error colour; in an imported one it carries neither.
 */
@Composable
private fun CollectionEntry(
    row: CollectionRecipeRow,
    position: Int,
    count: Int,
    cover: EntryCover,
    drag: RowDrag?,
    editable: Boolean,
    onOpen: () -> Unit,
    onMove: (Int, Int) -> Unit,
    onRemove: () -> Unit,
) {
    val actions = if (!editable) emptyList() else buildList {
        if (position > 0) {
            add(EntryAction(R.string.collection_move_up, CollectionScreenTags.moveUp(row.recipeId)) { onMove(position, position - 1) })
        }
        if (position < count - 1) {
            add(EntryAction(R.string.collection_move_down, CollectionScreenTags.moveDown(row.recipeId)) { onMove(position, position + 1) })
        }
        add(EntryAction(R.string.collection_remove, CollectionScreenTags.remove(row.recipeId), destructive = true, onClick = onRemove))
    }
    BookEntryRow(
        row.recipeId, row.title, row.coverId, cover, actions, onOpen,
        modifier = Modifier,
        // Lifted while dragged, on the page's own colour: the row has no card of its own.
        inner = drag?.row(position, MaterialTheme.colorScheme.surface) ?: Modifier,
        wrap = { content ->
            DragColumnBlock(drag, position, count, CollectionScreenTags.handle(row.recipeId), CollectionScreenTags.handleIcon(row.recipeId)) {
                content()
            }
        },
    ) {
        EntryTitle(row.recipeId, row.title)
        row.statedAuthor?.let { EntryAuthor(row.recipeId, it) }
    }
}

/**
 * Prints, or writes as a PDF and shares, the collection [id] under the name it is given (v0.37, T-262): its recipes as
 * they stand in it when the item is taken, each at the count its card would open with, read as [RecipesDocument.read]
 * reads them. A failure — none of them read back, no PDF written — is said in [snackbars]; a tap while one is being
 * made starts nothing ([rememberPrintOut]); [onBusy] hears whether one is being made.
 */
@Composable
private fun rememberPrintCollection(
    id: CollectionId,
    session: LibrarySession,
    collections: CollectionRepository,
    recipes: RecipeRepository,
    card: CardEnvironment,
    snackbars: SnackbarHostState,
    onBusy: (Boolean) -> Unit,
): (String, PrintWay) -> Unit {
    val printOut = rememberPrintOut(snackbars, onBusy)
    val context = LocalContext.current
    return { name, way ->
        val resources = context.resources
        val photos = PhotoStore(context.applicationContext.filesDir)
        printOut(way) {
            val members = collections.recipes(id).first().map { RecipeId(it.recipeId) }
            RecipesDocument.read(name, members, recipes, card, session::heldScale, resources, photos)
        }
    }
}

/**
 * Says that a recipe was taken out of the collection — "“Soup” removed from the collection" — with Undo, which puts it
 * back at its place; the notice gone without it, or replaced by the next removal's, leaves it out. The recipe stays in
 * the book either way.
 */
@Composable
private fun RemovedNotice(
    id: CollectionId,
    removed: RemovedRecipe?,
    collections: CollectionRepository,
    session: LibrarySession,
    snackbars: SnackbarHostState,
    onEnd: () -> Unit,
) {
    val said = removed?.let {
        val title = it.title.trim()
        if (title.isEmpty()) stringResource(R.string.collection_removed_untitled) else stringResource(R.string.collection_removed, title)
    }
    val undo = stringResource(R.string.collection_undo)
    LaunchedEffect(removed) {
        val shown = removed ?: return@LaunchedEffect
        // The newest statement replaces the one shown — "Added 3 recipes", say — rather than waiting behind it.
        snackbars.currentSnackbarData?.dismiss()
        val result = snackbars.showSnackbar(said.orEmpty(), actionLabel = undo, duration = SnackbarDuration.Long)
        // Put back where the session's work runs, which outlives this screen.
        if (result == SnackbarResult.ActionPerformed) session.viewModelScope.launch { collections.putBack(id, RecipeId(shown.recipeId), shown.at) }
        onEnd()
    }
}

/** The bar's back arrow, a 48 dp target, which returns to the book. */
@Composable
private fun BackAction(onBack: () -> Unit) {
    val action = stringResource(R.string.collection_back_action)
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
            .testTag(CollectionScreenTags.BACK),
    ) {
        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.collection_back))
    }
}

/**
 * The collection's ⋮, in the order every ⋮ keeps (v0.38, T-269): "Add all to shopping list" while it has recipes
 * ([onAddAll]), then Print and Share as PDF while it has recipes ([onPrint]), then Export, where [onShare] is given (files
 * are a [Mode.Serverless] feature), then Rename for an own collection, then Delete collection, in the theme's error
 * colour. An imported collection offers no Rename.
 */
@Composable
private fun CollectionMenu(
    editable: Boolean,
    onShare: (() -> Unit)?,
    onAddAll: (() -> Unit)?,
    onPrint: ((PrintWay) -> Unit)?,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.padding(end = 4.dp)) {
        BlockAction(
            R.drawable.ic_row_menu,
            stringResource(R.string.collection_menu),
            CollectionScreenTags.MENU,
            onClick = { expanded = !expanded },
            action = stringResource(R.string.collection_menu_action),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (onAddAll != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.collection_add_all_to_shopping)) },
                    onClick = {
                        expanded = false
                        onAddAll()
                    },
                    modifier = Modifier.heightIn(min = 48.dp).testTag(CollectionScreenTags.ADD_ALL_TO_SHOPPING),
                )
            }
            // Worded as on a recipe's card.
            if (onPrint != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.print_menu_print)) },
                    onClick = {
                        expanded = false
                        onPrint(PrintWay.PRINT)
                    },
                    modifier = Modifier.heightIn(min = 48.dp).testTag(CollectionScreenTags.PRINT),
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.print_menu_share)) },
                    onClick = {
                        expanded = false
                        onPrint(PrintWay.SHARE)
                    },
                    modifier = Modifier.heightIn(min = 48.dp).testTag(CollectionScreenTags.SHARE_PDF),
                )
            }
            // The collection as a file (serverless mode only), named as a recipe's file action is.
            if (onShare != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.file_export)) },
                    onClick = {
                        expanded = false
                        onShare()
                    },
                    modifier = Modifier.heightIn(min = 48.dp).testTag(CollectionShareTags.SHARE),
                )
            }
            if (editable) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.collection_rename)) },
                    onClick = {
                        expanded = false
                        onRename()
                    },
                    modifier = Modifier.heightIn(min = 48.dp).testTag(CollectionScreenTags.RENAME),
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.collection_delete)) },
                onClick = {
                    expanded = false
                    onDelete()
                },
                colors = MenuDefaults.itemColors(textColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.heightIn(min = 48.dp).testTag(CollectionScreenTags.DELETE),
            )
        }
    }
}

/**
 * Asks the collection's new name, [current] at first, the cursor at its end: at most [RecipeCollection.MAX_NAME]
 * characters, and Rename offered once it holds more than spaces. Done closes the keyboard.
 */
@Composable
private fun RenameDialog(current: String, onRename: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(current, TextRange(current.length))) }
    val valid = RecipeCollection.nameOf(text.text) != null
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(CollectionScreenTags.RENAME_DIALOG),
        title = { Text(stringResource(R.string.collection_rename_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.copy(text = it.text.take(RecipeCollection.MAX_NAME)) },
                label = { Text(stringResource(R.string.collection_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag(CollectionScreenTags.RENAME_FIELD),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onRename(text.text) },
                enabled = valid,
                modifier = Modifier.heightIn(min = 48.dp).widthIn(min = 48.dp).testTag(CollectionScreenTags.RENAME_CONFIRM),
            ) { Text(stringResource(R.string.collection_rename_confirm)) }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp).widthIn(min = 48.dp).testTag(CollectionScreenTags.RENAME_CANCEL),
            ) { Text(stringResource(R.string.collection_cancel)) }
        },
    )
    LaunchedEffect(focus) { focus.requestFocus() }
}

object CollectionScreenTags {
    const val SCREEN = "collection"
    const val BAR = "collection-bar"
    const val TITLE = "collection-title"
    const val BACK = "collection-back"
    const val MENU = "collection-menu"
    const val RENAME = "collection-rename"
    const val DELETE = "collection-delete"
    const val ADD_ALL_TO_SHOPPING = "collection-add-all-to-shopping"
    const val PRINT = "collection-print"
    const val SHARE_PDF = "collection-share-pdf"
    const val RENAME_DIALOG = "collection-rename-dialog"
    const val RENAME_FIELD = "collection-rename-field"
    const val RENAME_CONFIRM = "collection-rename-confirm"
    const val RENAME_CANCEL = "collection-rename-cancel"

    /** The scrolling column of the collection's recipes, and the empty state shown in place of them. */
    const val LIST = "collection-list"
    const val EMPTY = "collection-empty"

    /** The line naming the author an imported collection's file states. */
    const val STATED_AUTHOR = "collection-stated-author"

    /** A recipe's drag column and its icon, and the items of its ⋮. */
    fun handle(recipeId: String) = "collection-handle-$recipeId"
    fun handleIcon(recipeId: String) = "collection-handle-icon-$recipeId"
    fun moveUp(recipeId: String) = "collection-move-up-$recipeId"
    fun moveDown(recipeId: String) = "collection-move-down-$recipeId"
    fun remove(recipeId: String) = "collection-remove-$recipeId"

    /** The book's row of collections, and a collection's card in it with its name and count. */
    const val ROW = "collections-row"
    fun card(collectionId: String) = "collections-card-$collectionId"
    fun cardName(collectionId: String) = "collections-card-name-$collectionId"
    fun cardCount(collectionId: String) = "collections-card-count-$collectionId"
}
