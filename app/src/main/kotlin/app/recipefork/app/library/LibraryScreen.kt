package app.recipefork.app.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.HorizontalAlignmentLine
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import app.recipefork.app.Mode
import app.recipefork.app.R
import app.recipefork.app.book.MarkedRecipeRow
import app.recipefork.app.book.OwnRecipeRow
import app.recipefork.app.card.CardEnvironment
import app.recipefork.app.card.DoneMarks
import app.recipefork.app.card.IngredientEntries
import app.recipefork.app.card.rememberCardEnvironment
import app.recipefork.app.dictionary.TagSource
import app.recipefork.app.dictionary.label
import app.recipefork.app.editor.BlockAction
import app.recipefork.app.editor.EditorEnvironment
import app.recipefork.app.editor.interfaceLanguage
import app.recipefork.app.editor.newRecipe
import app.recipefork.app.editor.numberLanguage
import app.recipefork.app.editor.rememberEditorEnvironment
import app.recipefork.app.file.FileMessage
import app.recipefork.app.file.RecipeExport
import app.recipefork.app.file.equipmentDictionaryOf
import app.recipefork.app.file.RecipeImport
import app.recipefork.app.kitchen.Kitchen
import app.recipefork.app.kitchen.KitchenEnvironment
import app.recipefork.app.kitchen.rememberKitchenEnvironment
import app.recipefork.app.recipe.BookFilters
import app.recipefork.app.recipe.BookLanguage
import app.recipefork.app.recipe.ListingParams
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.settings.SettingsTags
import app.recipefork.app.timer.CardTimers
import app.recipefork.app.timer.rememberCardTimers
import app.recipefork.app.ui.SnackbarAction
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.RecipeState
import app.recipefork.core.recipe.SentStatus
import app.recipefork.core.search.BookOrder
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

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
 * The settings action, beside the book's title, opens the settings ([onOpenSettings]): the interface language, the
 * serving count the reader usually cooks for (FLOW-017), the side the editor's drag column stands on, and the clearing
 * of saved media (FLOW-060).
 *
 * The title and the settings action stand in a top bar that scrolls away as a list goes down and returns as it goes
 * up; the tabs of the two views stay beneath it. The views are pages swiped between, the tabs following; each keeps its own place
 * in its list. A view's action — a new recipe, or the import of a file — floats at the bottom right, shrinking to its
 * icon while the list goes down. The screen is drawn edge to edge: the bar stands below the status bar, the action
 * above the navigation bar, and the lists run under the navigation bar, their last entry kept clear of it.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    kitchen: KitchenEnvironment = rememberKitchenEnvironment(),
    onOpenSettings: () -> Unit = {},
) {
    val view by session.view.collectAsState()
    val bookKitchen = rememberBookKitchen(kitchen)
    val timers = rememberCardTimers()
    // The session holds the view shown; the pager follows it, and tells it the view a swipe settles on.
    val pager = remember { PagerState(currentPage = view.ordinal) { VIEWS.size } }
    val focus = LocalFocusManager.current
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { session.choose(VIEWS[it]) }
    }
    // A field left focused on the view moved away from would keep its page composed beside the one shown.
    LaunchedEffect(pager) { snapshotFlow { pager.isScrollInProgress }.collect { if (it) focus.clearFocus() } }
    LaunchedEffect(view) { if (pager.currentPage != view.ordinal) pager.animateScrollToPage(view.ordinal) }
    // Each view's place in its list, kept here so that the view's action knows which way its list last moved.
    val markedList = rememberLazyListState()
    val ownList = rememberLazyListState()
    // Whether each view, as it last showed, offers its action at the bottom (its empty state carries its own).
    val offers = remember { mutableStateMapOf<LibraryView, Boolean>() }
    val write = rememberWrite(recipes, session, editor)
    val import = rememberImport(files, session)
    val bar = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val shown = VIEWS[pager.currentPage]
    val snackbars = remember { SnackbarHostState() }
    val notice = rememberFileNotice(session, snackbars)
    RemovalNotice(session, snackbars)
    val work by session.work.collectAsState()
    Scaffold(
        modifier = modifier.fillMaxSize().nestedScroll(bar.nestedScrollConnection),
        snackbarHost = { LibrarySnackbarHost(snackbars) },
        topBar = {
            Column {
                // The book's title, with the settings action beside it; it scrolls away as the list goes down
                // and returns as it goes up.
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.library_title),
                            modifier = Modifier.semantics { heading() }.testTag(LibraryTags.TITLE),
                        )
                    },
                    actions = {
                        BlockAction(
                            R.drawable.ic_settings,
                            stringResource(R.string.settings_open),
                            LibraryTags.SETTINGS,
                            onClick = onOpenSettings,
                            modifier = Modifier.padding(end = 4.dp),
                            action = stringResource(R.string.settings_open_action),
                        )
                    },
                    scrollBehavior = bar,
                    modifier = Modifier.testTag(LibraryTags.TOP_BAR),
                )
                // The switch of views stays above the list and never scrolls with it (FLOW-034 (1)).
                PrimaryTabRow(selectedTabIndex = pager.currentPage, modifier = Modifier.testTag(LibraryTags.SWITCH)) {
                    ViewTab(LibraryView.MARKED, shown, R.string.library_view_marked, R.string.library_view_marked_action, session)
                    ViewTab(LibraryView.OWN, shown, R.string.library_view_own, R.string.library_view_own_action, session)
                }
            }
        },
        floatingActionButton = {
            ViewAction(
                shown, mode, offered = offers[shown] == true,
                list = if (shown == LibraryView.OWN) ownList else markedList,
                onWrite = write, onImport = import,
            )
        },
    ) { padding ->
        // The lists run under the navigation bar, their last entry kept clear of it and of the view's action.
        val bottom = padding.calculateBottomPadding() + ACTION_ROOM
        val layout = LocalLayoutDirection.current
        HorizontalPager(
            state = pager,
            key = { VIEWS[it].name },
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = padding.calculateTopPadding(),
                    start = padding.calculateStartPadding(layout),
                    end = padding.calculateEndPadding(layout),
                )
                .consumeWindowInsets(padding)
                .testTag(LibraryTags.PAGER),
        ) { page ->
            when (VIEWS[page]) {
                LibraryView.MARKED -> MarkedList(
                    recipes, session, files, timers, card.marks, mode, thumbnails, card.ingredients, editor.tags, bookKitchen,
                    markedList, bottom, import, notice, onOffer = { offers[LibraryView.MARKED] = it },
                )
                LibraryView.OWN -> OwnList(
                    recipes, session, editor, timers, card.marks, mode, thumbnails, card.ingredients, bookKitchen,
                    ownList, bottom, notice, write, onOffer = { offers[LibraryView.OWN] = it },
                )
            }
        }
        // A file being imported or exported shows its progress over the top of the list, shifting nothing.
        work?.let { FileProgress(it, Modifier.padding(top = padding.calculateTopPadding())) }
    }
}

/**
 * The progress of a file being imported or exported, in place, over the top of the view: how far, where its size is
 * known, and that it is under way otherwise. The screen reader names what is under way.
 */
@Composable
private fun FileProgress(work: FileWork, modifier: Modifier = Modifier) {
    val label = stringResource(if (work.kind == FileWork.Kind.IMPORT) R.string.file_importing else R.string.file_exporting)
    val tagged = modifier.fillMaxWidth().semantics { contentDescription = label }.testTag(LibraryTags.FILE_PROGRESS)
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val done = work.done
    if (done == null) LinearProgressIndicator(tagged, trackColor = track) else LinearProgressIndicator({ done }, tagged, trackColor = track)
}

/**
 * Says a recipe a file brought — imported now, or held already — in the book's snackbar, whose Open opens its card;
 * returns that recipe's identity while it is said, so that its list is brought to it. The message is closed once the
 * snackbar goes; a failure is the dialog's to say (AppNavHost).
 */
@Composable
private fun rememberFileNotice(session: LibrarySession, snackbars: SnackbarHostState): String? {
    val message by session.message.collectAsState()
    val notice = message?.takeIf { it.isNotice && it.recipeId.isNotEmpty() }
    val resources = LocalContext.current.resources
    val open = stringResource(R.string.file_open)
    LaunchedEffect(notice) {
        if (notice == null) return@LaunchedEffect
        val result = snackbars.showSnackbar(notice.text(resources), actionLabel = open, duration = SnackbarDuration.Long)
        session.told()
        if (result == SnackbarResult.ActionPerformed) session.openCard(notice.recipeId, session.view.value)
    }
    return notice?.recipeId
}

/**
 * The book's snackbar: one notice at a time, above the keyboard where it is open, gone by itself after a while or
 * swiped away either way, read by the screen reader as it comes (the host's live region).
 */
@Composable
private fun LibrarySnackbarHost(snackbars: SnackbarHostState) {
    SnackbarHost(snackbars, Modifier.imePadding().testTag(LibraryTags.SNACKBAR)) { data ->
        val swipe = rememberSwipeToDismissBoxState()
        // Only the snackbar itself is swiped: the margin around it lets a tap through to the list beneath.
        SwipeToDismissBox(swipe, backgroundContent = {}, modifier = Modifier.padding(12.dp), onDismiss = { data.dismiss() }) {
            val label = data.visuals.actionLabel
            Snackbar(
                modifier = Modifier.testTag(LibraryTags.NOTICE),
                action = label?.let { { SnackbarAction(it, data::performAction) } },
            ) { Text(data.visuals.message) }
        }
    }
}

/**
 * Says that a recipe left the book — "Recipe deleted", or "Recipe removed" for a found one — with Undo, for as long as
 * the session offers to undo it (FLOW-053). Undo puts it back; the notice gone without it — by itself, swiped away,
 * replaced by another notice, or the book left — makes the leaving final. A notice cut short by the screen turning is
 * said again. An Undo the book could not take back is said to have failed.
 */
@Composable
private fun RemovalNotice(session: LibrarySession, snackbars: SnackbarHostState) {
    val removal by session.removal.collectAsState()
    val deleted = stringResource(R.string.library_recipe_deleted)
    val removed = stringResource(R.string.library_recipe_removed)
    val undo = stringResource(R.string.library_undo)
    LaunchedEffect(removal) {
        val shown = removal ?: return@LaunchedEffect
        val result = snackbars.showSnackbar(if (shown.found) removed else deleted, actionLabel = undo, duration = SnackbarDuration.Long)
        if (result == SnackbarResult.ActionPerformed) session.undoRemoval(shown) else session.endRemoval(shown)
    }
    // An Undo the book could not take back is said, once.
    val failed by session.undoFailed.collectAsState()
    val notBack = stringResource(R.string.library_undo_failed)
    LaunchedEffect(failed) {
        val said = failed ?: return@LaunchedEffect
        snackbars.showSnackbar(notBack, duration = SnackbarDuration.Long)
        session.undoFailureSaid(said)
    }
}

/** Brings [list] to the entry of [recipeId] once, when [rows] list it; nothing when they do not. */
@Composable
private fun RevealOnNotice(recipeId: String?, rows: List<String>?, list: LazyListState) {
    val at = if (recipeId == null) -1 else rows?.indexOf(recipeId) ?: -1
    LaunchedEffect(recipeId, at >= 0) { if (at >= 0) list.animateScrollToItem(at) }
}

/** The views in the order of their tabs and pages: a view's page is its ordinal. */
private val VIEWS = LibraryView.entries

/** The room kept under a list's last entry for the view's action: the action's height and its margins. */
private val ACTION_ROOM = 88.dp

/** How long a view waits on its list before it shows that the list is coming, so that a quick one shows no flash. */
internal const val LOADING_DELAY_MS = 300L

@Composable
private fun ViewTab(
    tab: LibraryView,
    current: LibraryView,
    @StringRes label: Int,
    @StringRes action: Int,
    session: LibrarySession,
) {
    val actionLabel = stringResource(action)
    Tab(
        selected = tab == current,
        onClick = { session.choose(tab) },
        text = { Text(stringResource(label)) },
        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .heightIn(min = 48.dp)
            // The tab announces what choosing it does: which recipes it shows (A11Y-005).
            .semantics {
                onClick(label = actionLabel) {
                    session.choose(tab)
                    true
                }
            }
            .testTag(LibraryTags.tab(tab)),
    )
}

/**
 * The action of the view shown, at the bottom right: writing a new recipe in the own view (FLOW-042 (5)), importing a
 * recipe file in the marked view in [Mode.Serverless] (FLOW-064); none in the marked view in [Mode.Standard], nor
 * while the view's empty state offers its own. It shrinks to its icon while the [list] goes down, and shows its words
 * again as it goes up or stands at its top.
 */
@Composable
private fun ViewAction(
    view: LibraryView,
    mode: Mode,
    offered: Boolean,
    list: LazyListState,
    onWrite: () -> Unit,
    onImport: () -> Unit,
) {
    if (!offered) return
    val expanded by remember(list) { derivedStateOf { !list.canScrollBackward || !list.lastScrolledForward } }
    when {
        view == LibraryView.OWN -> FloatingAction(R.string.library_own_new, R.drawable.ic_new_recipe, LibraryTags.NEW, expanded, onWrite)
        mode == Mode.Serverless -> FloatingAction(R.string.file_import, R.drawable.ic_import_file, LibraryTags.IMPORT, expanded, onImport)
    }
}

@Composable
private fun FloatingAction(@StringRes label: Int, @DrawableRes icon: Int, tag: String, expanded: Boolean, onClick: () -> Unit) {
    val words = stringResource(label)
    ExtendedFloatingActionButton(
        text = { Text(words) },
        icon = { Icon(painterResource(icon), contentDescription = null) },
        onClick = onClick,
        expanded = expanded,
        // The action reads its words to the screen reader, shrunk to its icon or not: the button keeps its shown words
        // from the screen reader, so they are given to the button itself.
        modifier = Modifier.semantics { text = AnnotatedString(words) }.testTag(tag),
    )
}

/**
 * Shown in place of a view's list while it is read: nothing at first, then, should the reading take longer than
 * [LOADING_DELAY_MS], a progress indicator, so that a list read quickly shows no flash of it.
 */
@Composable
private fun ListLoading(modifier: Modifier = Modifier) {
    var showing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(LOADING_DELAY_MS)
        showing = true
    }
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (showing) {
            val description = stringResource(R.string.library_opening)
            CircularProgressIndicator(Modifier.semantics { contentDescription = description }.testTag(LibraryTags.LOADING))
        }
    }
}

/**
 * Built from the device alone: nothing here waits on or fails for the network (FLOW-043 (5)). Its header searches and
 * orders it (FLOW-035); a book in which nothing is written says so in place of the list and the header. A new recipe
 * is written from the view's action at the bottom ([ViewAction]); the view tells [onOffer] whether it offers it.
 */
@Composable
private fun OwnList(
    recipes: RecipeRepository,
    session: LibrarySession,
    editor: EditorEnvironment,
    timers: CardTimers,
    marks: DoneMarks,
    mode: Mode,
    thumbnails: CoverThumbnails,
    dictionary: IngredientEntries,
    kitchen: Kitchen?,
    listState: LazyListState,
    bottom: Dp,
    reveal: String?,
    onWrite: () -> Unit,
    onOffer: (Boolean) -> Unit,
) {
    val search = rememberSearchField(LibraryView.OWN, session)
    val params = rememberListingParams(LibraryView.OWN, session)
    val kitchens = rememberKitchenFlow(kitchen)
    val listed by remember(recipes, params, dictionary) { recipes.ownListed(params, kitchens, dictionary::entries) }
        .collectAsState(initial = null)
    SwitchOffEquipmentWithoutKitchen(LibraryView.OWN, session, kitchen)
    val query by session.query(LibraryView.OWN).collectAsState()
    val order by session.order(LibraryView.OWN).collectAsState()
    val filters by session.filters(LibraryView.OWN).collectAsState()
    val choices = rememberFilterChoices(dictionary, editor.tags)
    val tagsOf = rememberEntryTags(choices, listed?.carries)
    // A new query, order or filter shows its listing from the beginning (FLOW-041 (4)).
    var limit by rememberSaveable(query, order, filters) { mutableIntStateOf(OWN_PAGE) }
    val found = listed?.rows
    val loaded = found?.take(limit)
    ScrollToTopOnChange(listed?.params, listState)
    RevealOnNotice(reveal, loaded?.map { it.recipeId }, listState)
    // Near the end of what is shown, the next recipes are shown (FLOW-041 (5)).
    LaunchedEffect(listState, loaded?.size, limit) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .distinctUntilChanged()
            .collect { last ->
                val size = loaded?.size ?: return@collect
                if (size == limit && last >= size - OWN_PAGE / 3) limit += OWN_PAGE
            }
    }
    val export = rememberExport(recipes, session)
    val shown = listed
    // A book that lists recipes still offers to write one (FLOW-042 (5)), from the view's action.
    val offered = shown != null && !(shown.viewIsEmpty && !shown.narrowed)
    SideEffect { onOffer(offered) }
    if (shown != null && shown.viewIsEmpty && !shown.narrowed) {
        NothingWritten(onWrite, Modifier.padding(bottom = bottom))
        return
    }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (shown != null) {
                ListHeader(
                    search, order, onOrder = { session.orderBy(LibraryView.OWN, it) },
                    filters, onFilters = { session.filter(LibraryView.OWN, it) }, choices, shown.languages, shown.hold, shown.carries,
                    equipmentOffered = offersEquipment(kitchen),
                )
                HorizontalDivider()
            }
            // While the list is read, no list is laid out: an empty one would bring the view's kept place to its top.
            if (shown == null) return@Column
            if (shown.narrowed && shown.rows.isEmpty()) {
                NothingFound(shown, onClearSearch = search::clear, onClearFilters = { session.filter(LibraryView.OWN, BookFilters()) })
                return@Column
            }
            val cover = coverOf(recipes, thumbnails)
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(top = ENTRY_GAP, bottom = bottom),
                verticalArrangement = Arrangement.spacedBy(ENTRY_GAP),
                modifier = Modifier.fillMaxSize().testTag(LibraryTags.OWN_LIST),
            ) {
                items(loaded.orEmpty(), key = { it.recipeId }) { row ->
                    OwnEntry(
                        row,
                        mode,
                        cover,
                        tagsOf(row.recipeId),
                        Modifier.animateItem(),
                        onOpen = { session.openCard(row.recipeId, LibraryView.OWN) },
                        // Deleted at once, asking nothing; the book says so with Undo (FLOW-053).
                        onDelete = { deleteRecipe(recipes, timers, marks, RecipeId(row.recipeId), session::removed) },
                        onExport = { export(row.recipeId, row.title) },
                    )
                }
            }
        }
        if (shown == null) ListLoading()
    }
}

/**
 * The own-recipes view of a book in which nothing has been written: it says so, and its one offer, the view's own
 * action, writes a new recipe in that book and opens it in the editor (FLOW-042).
 */
@Composable
private fun NothingWritten(onWrite: () -> Unit, modifier: Modifier = Modifier) {
    EmptyState(
        picture = R.drawable.ic_empty_book,
        headline = stringResource(R.string.library_own_empty),
        sentence = stringResource(R.string.library_own_empty_sentence),
        action = EmptyAction(stringResource(R.string.library_own_new), R.drawable.ic_new_recipe, LibraryTags.WRITE, onWrite),
        tag = LibraryTags.OWN_EMPTY,
        modifier = modifier,
    )
}

/** Writes a new recipe in the active book and opens it in the editor (FLOW-042 (4, 5)); a second tap adds none. */
@Composable
private fun rememberWrite(recipes: RecipeRepository, session: LibrarySession, editor: EditorEnvironment): () -> Unit {
    val language = interfaceLanguage(LocalConfiguration.current)
    var writing by remember { mutableStateOf(false) }
    return {
        if (!writing) {
            writing = true
            val recipe = newRecipe(editor, language)
            session.viewModelScope.launch {
                recipes.save(recipe)
                session.openEditor(recipe.id.value)
            }
        }
    }
}

/**
 * An own recipe's entry, a card; tapping it opens the recipe's card (FLOW-029 (1)). Its actions are in its menu, at the
 * end of its title line: deletion for a recipe its owner may delete (FLOW-053 (1), ST-010), and in [Mode.Serverless]
 * the export of every recipe (FLOW-063). An entry with no action carries no menu, and its text takes the whole width.
 */
@Composable
private fun OwnEntry(
    row: OwnRecipeRow,
    mode: Mode,
    cover: EntryCover,
    tags: String,
    modifier: Modifier,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit,
) {
    val actions = buildList {
        if (mode == Mode.Serverless) add(EntryAction(R.string.file_export, LibraryTags.export(row.recipeId), onExport))
        if (row.state in DELETABLE) add(EntryAction(R.string.library_entry_delete, LibraryTags.delete(row.recipeId), onDelete))
    }
    EntryCard(row.recipeId, row.title, row.coverId, cover, actions, onOpen, modifier) {
        EntryTitle(row.recipeId, row.title)
        EntryTags(row.recipeId, tags)
        // The state is named in words, never carried by colour alone (A11Y-008); a serverless draft names none.
        stateLabel(row.state, mode)?.let { Text(stringResource(it), style = MaterialTheme.typography.labelLarge) }
        // The sending mark is shown beside the state and never replaces it (FLOW-036 (6), FLOW-005); a build without
        // the server sends nothing, so it shows none (FLOW-062 (1)).
        if (mode == Mode.Standard) Text(stringResource(sentLabel(row.sent)), style = MaterialTheme.typography.bodySmall)
    }
}

/**
 * An entry of the book as a card: its cover across the card's width in the 4:3 frame, when it has one, and beneath
 * it the entry's [lines], the first of them its title. When there are any [actions], their menu stands at the end of
 * the title's first line, and the text keeps clear of it. [onOpen], when given, opens the recipe.
 */
@Composable
private fun EntryCard(
    recipeId: String,
    title: String,
    coverId: String?,
    cover: EntryCover,
    actions: List<EntryAction>,
    onOpen: (() -> Unit)?,
    modifier: Modifier,
    lines: @Composable ColumnScope.() -> Unit,
) {
    val open = onOpen?.let {
        Modifier.clickable(onClickLabel = stringResource(R.string.library_entry_open_action), role = Role.Button, onClick = it)
    } ?: Modifier
    Card(modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Layout(
            content = {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .semantics(mergeDescendants = true) {}
                        .then(open)
                        .testTag(LibraryTags.entry(recipeId)),
                ) {
                    cover(coverId, LibraryTags.cover(recipeId))
                    Column(
                        Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = if (actions.isEmpty()) 16.dp else MENU_ROOM),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        content = lines,
                    )
                }
                if (actions.isNotEmpty()) EntryMenu(recipeId, title, actions)
            },
        ) { measurables, constraints ->
            val entry = measurables[0].measure(constraints)
            val menu = measurables.getOrNull(1)?.measure(Constraints())
            layout(entry.width, entry.height) {
                entry.place(0, 0)
                if (menu != null) {
                    // Level with the middle of the title's first line, 4 dp from the card's end.
                    val line = entry[TitleLine].takeIf { it != AlignmentLine.Unspecified } ?: (entry.height / 2)
                    menu.place(entry.width - menu.width - 4.dp.roundToPx(), line - menu.height / 2)
                }
            }
        }
    }
}

/** The room kept at the end of an entry's text for its ⋮: the ⋮ itself and its margin. */
private val MENU_ROOM = 52.dp

/** The space between two entries of a list, and above the first. */
private val ENTRY_GAP = 12.dp

/**
 * The tags a recipe carries, as its entry shows them: one muted line of their labels in the interface language,
 * cut short where it does not fit; nothing for a recipe that carries none.
 */
@Composable
private fun EntryTags(recipeId: String, tags: String) {
    if (tags.isEmpty()) return
    Text(
        tags,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.testTag(LibraryTags.entryTags(recipeId)),
    )
}

/**
 * The tags of each recipe of a view as its entry shows them, by the recipe's identity: their labels in the interface
 * language, as the tag filter names them (L10N-003), in alphabetical order. The tag dictionary is read once a recipe
 * of the view carries a tag; until it is, the entries show none.
 */
@Composable
private fun rememberEntryTags(choices: FilterChoices, carries: Map<String, List<Long>>?): (String) -> String {
    val needed = !carries.isNullOrEmpty()
    LaunchedEffect(choices, needed) { if (needed) choices.readTags() }
    val language = numberLanguage(LocalConfiguration.current)
    val dictionary = choices.tags
    return remember(dictionary, carries, language) {
        val labels = dictionary.orEmpty().associate { it.id to it.label(language) }
        val byRecipe = carries.orEmpty().mapValues { (_, ids) ->
            ids.mapNotNull(labels::get).sortedBy { it.lowercase() }.joinToString(TAG_SEPARATOR)
        }
        return@remember { recipeId: String -> byRecipe[recipeId].orEmpty() }
    }
}

/** What stands between two tags on an entry's line of tags. */
private const val TAG_SEPARATOR = " · "

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
 * starts no second one; its progress shows meanwhile. Export reads the recipe and changes nothing in it. An export that
 * fails says so.
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
                    val shared = session.withProgress(FileWork.Kind.EXPORT) { progress ->
                        RecipeExport.share(context, recipes, RecipeId(recipeId), sheet, progress)
                    }
                    if (!shared) {
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

/** The import of recipe files, writing found recipes' media under the application's own files. */
@Composable
fun rememberRecipeImport(recipes: RecipeRepository): RecipeImport {
    val context = LocalContext.current.applicationContext
    return remember(recipes, context) { RecipeImport(recipes, context.filesDir, equipment = equipmentDictionaryOf(context)) }
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
 * names it, marked as stated and not verified (FLOW-064 (3)). In [Mode.Serverless] the view offers to import a recipe
 * file from its action at the bottom ([ViewAction]); with nothing marked it says so, and the import, offered beneath,
 * is the way out of it (FLOW-042). Its header, above the list, searches and orders it (FLOW-035).
 */
@Composable
private fun MarkedList(
    recipes: RecipeRepository,
    session: LibrarySession,
    files: RecipeImport,
    timers: CardTimers,
    marks: DoneMarks,
    mode: Mode,
    thumbnails: CoverThumbnails,
    dictionary: IngredientEntries,
    tags: TagSource,
    kitchen: Kitchen?,
    listState: LazyListState,
    bottom: Dp,
    onImport: () -> Unit,
    reveal: String?,
    onOffer: (Boolean) -> Unit,
) {
    val search = rememberSearchField(LibraryView.MARKED, session)
    val params = rememberListingParams(LibraryView.MARKED, session)
    val kitchens = rememberKitchenFlow(kitchen)
    val listed by remember(recipes, params, dictionary) { recipes.markedListed(params, kitchens, dictionary::entries) }
        .collectAsState(initial = null)
    SwitchOffEquipmentWithoutKitchen(LibraryView.MARKED, session, kitchen)
    val order by session.order(LibraryView.MARKED).collectAsState()
    val filters by session.filters(LibraryView.MARKED).collectAsState()
    val choices = rememberFilterChoices(dictionary, tags)
    val tagsOf = rememberEntryTags(choices, listed?.carries)
    ScrollToTopOnChange(listed?.params, listState)
    val marked = listed?.rows
    RevealOnNotice(reveal, marked?.map { it.recipeId }, listState)
    val export = rememberExport(recipes, session)
    val shown = listed
    val empty = shown != null && shown.viewIsEmpty && !shown.narrowed
    val offered = mode == Mode.Serverless && shown != null && !empty
    SideEffect { onOffer(offered) }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // The empty view's one offer is the view's own action: the import of a recipe file in [Mode.Serverless];
            // in [Mode.Standard] the view has no action, and its empty state none.
            if (empty) {
                val serverless = mode == Mode.Serverless
                EmptyState(
                    picture = R.drawable.ic_empty_book,
                    headline = stringResource(R.string.library_marked_empty),
                    sentence = stringResource(
                        if (serverless) R.string.library_marked_empty_sentence_serverless else R.string.library_marked_empty_sentence,
                    ),
                    action = if (serverless) {
                        EmptyAction(stringResource(R.string.file_import), R.drawable.ic_import_file, LibraryTags.IMPORT, onImport)
                    } else {
                        null
                    },
                    tag = LibraryTags.MARKED_EMPTY,
                    modifier = Modifier.padding(bottom = bottom),
                )
            }
            // An empty view with no query keeps its empty state, with no header to search nothing with.
            if (shown != null && !empty) {
                ListHeader(
                    search, order, onOrder = { session.orderBy(LibraryView.MARKED, it) },
                    filters, onFilters = { session.filter(LibraryView.MARKED, it) }, choices, shown.languages, shown.hold, shown.carries,
                    equipmentOffered = offersEquipment(kitchen),
                )
                HorizontalDivider()
            }
            // While the list is read, no list is laid out: an empty one would bring the view's kept place to its top.
            if (shown == null) return@Column
            if (shown.narrowed && shown.rows.isEmpty()) {
                NothingFound(shown, onClearSearch = search::clear, onClearFilters = { session.filter(LibraryView.MARKED, BookFilters()) })
                return@Column
            }
            val cover = coverOf(recipes, thumbnails)
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(top = ENTRY_GAP, bottom = bottom),
                verticalArrangement = Arrangement.spacedBy(ENTRY_GAP),
                modifier = Modifier.fillMaxSize().testTag(LibraryTags.MARKED_LIST),
            ) {
                items(marked.orEmpty(), key = { it.recipeId }) { row ->
                    MarkedEntry(
                        row,
                        mode,
                        cover,
                        tagsOf(row.recipeId),
                        Modifier.animateItem(),
                        onOpen = { session.openCard(row.recipeId, LibraryView.MARKED) },
                        // Removed at once, asking nothing; the book says so with Undo (FLOW-053).
                        onRemove = { removeFound(files, timers, marks, RecipeId(row.recipeId), session::removed) },
                        onExport = { export(row.recipeId, row.title) },
                    )
                }
            }
        }
        if (shown == null) ListLoading()
    }
}

/**
 * A marked recipe's entry, a card: its title, any stated author and its tags; one whose content the book holds opens,
 * and its menu, at the end of its title line, offers its removal, and in [Mode.Serverless] its export as well
 * (FLOW-063 (2)).
 */
@Composable
private fun MarkedEntry(
    row: MarkedRecipeRow,
    mode: Mode,
    cover: EntryCover,
    tags: String,
    modifier: Modifier,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
    onExport: () -> Unit,
) {
    val actions = if (!row.held) emptyList() else buildList {
        if (mode == Mode.Serverless) add(EntryAction(R.string.file_export, LibraryTags.export(row.recipeId), onExport))
        add(EntryAction(R.string.library_entry_remove, LibraryTags.remove(row.recipeId), onRemove))
    }
    EntryCard(row.recipeId, row.title, row.coverId, cover, actions, onOpen.takeIf { row.held }, modifier) {
        EntryTitle(row.recipeId, row.title)
        row.statedAuthor?.let {
            Text(
                stringResource(R.string.file_stated_author, it),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag(LibraryTags.statedAuthor(row.recipeId)),
            )
        }
        EntryTags(row.recipeId, tags)
    }
}

/**
 * Imports a recipe file the person picks with the system's document picker (FLOW-064). Any file may be picked, because
 * a file saved from a messenger rarely carries the product's type; one that is no recipe file is refused and said to
 * be. A tap while a file is being read starts no second import; its progress shows while it is read, and a recipe it
 * brings is said in the snackbar, its list brought to it.
 */
@Composable
private fun rememberImport(files: RecipeImport, session: LibrarySession): () -> Unit {
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
                session.importFile(context, files, uri)
            } finally {
                importing = false
            }
        }
    }
    return {
        if (!importing) {
            importing = true
            picker.launch(arrayOf(ANY_FILE))
        }
    }
}

/** Every type: the picker offers any file, and the importer decides what it holds. */
private const val ANY_FILE = "*/*"

/**
 * The marker of FLOW-036: `local` and `private` are both a draft. In [Mode.Serverless] nothing can be published, so a
 * draft carries no marker (null); a recipe keeps the last other state the device knew (FLOW-062 (5)), worded with
 * nothing of publication, review or a private link.
 */
@StringRes
fun stateLabel(state: RecipeState, mode: Mode): Int? = when (state) {
    RecipeState.LOCAL, RecipeState.PRIVATE -> if (mode == Mode.Serverless) null else R.string.library_state_draft
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
    /** The action beside the title that opens the settings. */
    const val SETTINGS = "library-settings"
    /** The clearing of saved media's dialogs, now the settings' ([SettingsTags]); named here too for older tests. */
    const val CLEAR_MEDIA_STATEMENT = SettingsTags.CLEAR_MEDIA_STATEMENT
    const val CLEAR_MEDIA_CONFIRM = SettingsTags.CLEAR_MEDIA_CONFIRM
    const val CLEAR_MEDIA_DECLINE = SettingsTags.CLEAR_MEDIA_DECLINE
    const val CLEAR_MEDIA_NOTHING = SettingsTags.CLEAR_MEDIA_NOTHING
    const val CLEAR_MEDIA_OK = SettingsTags.CLEAR_MEDIA_OK
    /** The book's top bar: its title and its settings action, which scrolls away as a list goes down. */
    const val TOP_BAR = "library-top-bar"
    const val SWITCH = "library-switch"
    /** The pages of the two views, swiped between. */
    const val PAGER = "library-pager"
    /** The indicator a view shows while its list is read for longer than a moment. */
    const val LOADING = "library-loading"
    const val OWN_LIST = "library-own"
    const val MARKED_LIST = "library-marked"
    const val MARKED_EMPTY = "library-marked-empty"
    const val IMPORT = "library-import"
    /** The progress of a file being imported or exported, and the snackbar that says a recipe a file brought. */
    const val FILE_PROGRESS = "library-file-progress"
    const val SNACKBAR = "library-snackbar"
    /** The notice the snackbar shows. */
    const val NOTICE = "library-notice"
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
    const val NOTHING_FOUND_CLEAR_ALL = "library-nothing-found-clear-all"
    /** The row of a view's filters, under its search field. */
    const val FILTERS = "library-filters"
    /** "All filters" in the row, which opens the sheet of every filter. */
    const val FILTER_ADD = "library-filter-add"
    /** A quick filter the row offers: a language of the view, or one of the tags its recipes carry most. */
    fun languageOffer(language: BookLanguage) = "library-language-offer-${language.name}"
    fun tagOffer(tagId: Long) = "library-tag-offer-$tagId"
    /** The sheet of every filter, and the control that closes it. */
    const val FILTERS_SHEET = "library-filters-sheet"
    const val FILTERS_SHEET_DONE = "library-filters-sheet-done"
    /** The sheet's section of the language filter, and each language it offers (L10N-005). */
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
    fun tagChoice(tagId: Long) = "library-tag-choice-$tagId"
    /** The sheet's section of the filter "Enough equipment", its choice, and the chip of it in force. */
    const val EQUIPMENT_CHOOSER = "library-equipment-chooser"
    const val EQUIPMENT_CHOICE = "library-equipment-choice"
    const val EQUIPMENT_FILTER = "library-equipment-filter"
    /** The one action of a view that the filter "Enough equipment" alone narrowed to nothing: switching it off. */
    const val NOTHING_FOUND_EQUIPMENT_OFF = "library-nothing-found-equipment-off"
    fun tab(view: LibraryView) = "library-tab-${view.name.lowercase()}"
    /** The parts of an empty state tagged [state]: its picture, its headline and its sentence. */
    fun emptyPicture(state: String) = "$state-picture"
    fun emptyHeadline(state: String) = "$state-headline"
    fun emptySentence(state: String) = "$state-sentence"
    fun entry(recipeId: String) = "library-entry-$recipeId"
    /** The ⋮ at the end of an entry's title line, which opens the menu of its actions. */
    fun actions(recipeId: String) = "library-actions-$recipeId"
    fun delete(recipeId: String) = "library-delete-$recipeId"
    fun export(recipeId: String) = "library-export-$recipeId"
    fun entryTitle(recipeId: String) = "library-entry-title-$recipeId"
    /** The line of the tags an entry's recipe carries. */
    fun entryTags(recipeId: String) = "library-entry-tags-$recipeId"
    fun statedAuthor(recipeId: String) = "library-stated-author-$recipeId"
    fun remove(recipeId: String) = "library-remove-$recipeId"
    fun cover(recipeId: String) = "library-cover-$recipeId"
    /** The place a cover with the tag [coverTag] holds, whether or not its picture is read yet. */
    fun coverSlot(coverTag: String) = "$coverTag-slot"
}
