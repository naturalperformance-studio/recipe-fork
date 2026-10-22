package app.recipefork.app.library

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalTextStyle
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
import androidx.compose.runtime.Stable
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
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
import app.recipefork.app.RecipeForkApplication
import app.recipefork.app.book.CollectionRow
import app.recipefork.app.collections.CollectionRepository
import app.recipefork.app.collections.CollectionsRow
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
import app.recipefork.app.print.PrintProgress
import app.recipefork.app.print.PrintTags
import app.recipefork.app.print.PrintWay
import app.recipefork.app.recipe.BookFilters
import app.recipefork.app.recipe.BookLanguage
import app.recipefork.app.media.SourceItem
import app.recipefork.app.media.SourceSheet
import app.recipefork.app.recipe.ListingParams
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.settings.SettingsTags
import app.recipefork.app.timer.CardTimers
import app.recipefork.app.timer.rememberCardTimers
import app.recipefork.app.ui.SnackbarAction
import app.recipefork.app.ui.FittedText
import app.recipefork.app.ui.FittedWords
import app.recipefork.app.ui.fitWordsTogether
import app.recipefork.app.ui.widestWord
import app.recipefork.app.ui.isLargeText
import app.recipefork.core.recipe.CollectionOrigin
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.RecipeState
import app.recipefork.core.recipe.SentStatus
import app.recipefork.core.search.BookOrder
import kotlin.math.min
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** What the bar's title keeps clear at its end, so that wrapped it never reaches under an action (TopAppBar's title inset). */
internal val TITLE_CLEARANCE = 12.dp

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
 * of saved media (FLOW-060). The shopping list's action, before it, opens the shopping list ([onOpenShopping], FLOW-074).
 *
 * The title and the settings action stand in a top bar that scrolls away as a list goes down and returns as it goes
 * up; the tabs of the two views stay beneath it, and each view's search field and filters scroll away and return the
 * same way ([ScrollAwayHeader], v0.38). The views are pages swiped between, the tabs following; each keeps its own place
 * in its list. A view's action — a new recipe, or the import of a file — floats at the bottom right, shrinking to its
 * icon while the list goes down, and kept to its icon at large text. The screen is drawn edge to edge: the bar stands
 * below the status bar, the action above the navigation bar, and the lists run under the navigation bar, their last
 * entry kept clear of it.
 *
 * While entries are chosen (the selection), the bar is the selection's, tinted, and the view does not change: the
 * pages are not swiped, and the tabs, the search, the filters and the collections do not respond (v0.38). A notice the
 * book gives ends when the reader shows the other view.
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
    onOpenShopping: () -> Unit = {},
    collections: CollectionRepository = rememberBookCollections(),
) {
    val view by session.view.collectAsState()
    val bookKitchen = rememberBookKitchen(kitchen)
    val timers = rememberCardTimers()
    // The session holds the view shown; the pager follows it, and tells it the view a swipe settles on.
    val pager = remember { PagerState(currentPage = view.ordinal) { VIEWS.size } }
    val focus = LocalFocusManager.current
    val snackbars = remember { SnackbarHostState() }
    // The reader shows a view: one shown in place of another ends the notice the book was giving (v0.38).
    val choose: (LibraryView) -> Unit = remember(session, snackbars) {
        { chosen ->
            if (chosen != session.view.value) snackbars.currentSnackbarData?.dismiss()
            session.choose(chosen)
        }
    }
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { choose(VIEWS[it]) }
    }
    // A field left focused on the view moved away from would keep its page composed beside the one shown.
    LaunchedEffect(pager) { snapshotFlow { pager.isScrollInProgress }.collect { if (it) focus.clearFocus() } }
    LaunchedEffect(view) { if (pager.currentPage != view.ordinal) pager.animateScrollToPage(view.ordinal) }
    // Each view's place in its list, kept here so that the view's action knows which way its list last moved.
    val markedList = rememberLazyListState()
    val ownList = rememberLazyListState()
    // And how far each view's header has scrolled away above it, kept with it.
    val markedHeader = rememberHeaderScroll()
    val ownHeader = rememberHeaderScroll()
    // Whether each view, as it last showed, offers its action at the bottom (its empty state carries its own).
    val offers = remember { mutableStateMapOf<LibraryView, Boolean>() }
    val write = rememberWrite(recipes, session, editor)
    // "New recipe" offers its two ways to start — from scratch, or from a pasted text (T-280) — in a small sheet.
    var choosing by rememberSaveable { mutableStateOf(false) }
    val chooseNew: () -> Unit = { choosing = true }
    if (choosing) NewRecipeChoice(onWrite = write, onPaste = { session.openPaste() }, onDismiss = { choosing = false })
    val import = rememberImport(files, session)
    val bar = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val shown = VIEWS[pager.currentPage]
    val notice = rememberFileNotice(session, snackbars, markedList, ownList)
    RemovalNotice(session, snackbars)
    CollectionDeletionNotice(session, collections, snackbars)
    val adding = rememberBookAdding(session, recipes, card, collections, snackbars, onOpenShopping)
    val printing = rememberBookPrinting(session, recipes, card, snackbars)
    val work by session.work.collectAsState()
    val selection by session.selection.collectAsState()
    val selecting = selection != null
    SelectionStartFeedback(session)
    val restored by session.restored.collectAsState()
    // What each view lists, as the selection sees it: what "Select all" chooses and what an action applies to.
    val selectables = remember { mutableStateMapOf<LibraryView, List<Selectable>>() }
    BackHandler(enabled = selection != null) { session.endSelection() }
    // The height of the view's action, in pixels: the tallest it has been laid out.
    var actionHeight by remember { mutableIntStateOf(0) }
    // Which way the reader last moved the list shown — before the bar and the header take any of it — so that the
    // view's action shows its words going up even while they alone move.
    val way = remember { ScrollWay() }
    LaunchedEffect(shown) { way.up = true }
    Scaffold(
        // While selecting, the title bar is not shown: it takes none of the list's drags, and comes back as it was.
        modifier = modifier.fillMaxSize().nestedScroll(way).then(if (selection == null) Modifier.nestedScroll(bar.nestedScrollConnection) else Modifier),
        snackbarHost = { LibrarySnackbarHost(snackbars) },
        topBar = {
            Column {
                // While entries are chosen, the selection's bar stands in place of the title, and does not scroll away;
                // the one fades into the other.
                AnimatedContent(
                    selection,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    contentKey = { it != null },
                ) { chosen ->
                    // The bar fading out takes no touch: its actions are no longer the ones shown.
                    Box(Modifier.leaving(transition.targetState == EnterExitState.PostExit)) {
                        if (chosen != null) {
                            SelectionBar(
                                chosen.count,
                                selectionActions(chosen, selectables[chosen.view].orEmpty(), session, recipes, files, timers, card.marks, adding, printing),
                                onSelectAll = { session.selectAll(chosen.view, selectables[chosen.view].orEmpty().map { it.recipeId }) },
                                onClose = session::endSelection,
                            )
                        } else TopAppBar(
                            title = {
                                Text(
                                    text = stringResource(R.string.library_title),
                                    // Material sets the title in 12 dp past the room it is measured for: a title wrapped
                                    // at a large font scale would reach under the first action.
                                    modifier = Modifier.padding(end = TITLE_CLEARANCE).semantics { heading() }.testTag(LibraryTags.TITLE),
                                )
                            },
                            actions = {
                                BlockAction(
                                    R.drawable.ic_shopping_cart,
                                    stringResource(R.string.shopping_open),
                                    LibraryTags.SHOPPING,
                                    onClick = onOpenShopping,
                                    action = stringResource(R.string.shopping_open_action),
                                )
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
                    }
                }
                // The switch of views stays above the list and never scrolls with it (FLOW-034 (1)).
                // The width each tab's words have: the row's share less the tab's margins, the window's width until the
                // row is laid out (the row asks its tabs' intrinsic height, so the words cannot wait on their constraints).
                var rowWidth by remember { mutableIntStateOf(0) }
                val words = (rowWidth.takeIf { it > 0 } ?: LocalWindowInfo.current.containerSize.width) / VIEWS.size -
                    with(LocalDensity.current) { (TAB_TEXT_PADDING * 2).roundToPx() }
                // In [Mode.Serverless] the marked view holds only the recipes imported from files, and is named for them.
                val markedLabel = if (mode == Mode.Serverless) R.string.library_view_marked_serverless else R.string.library_view_marked
                val labelSize = rememberTabLabelSize(listOf(stringResource(markedLabel), stringResource(R.string.library_view_own)), words)
                PrimaryTabRow(
                    selectedTabIndex = pager.currentPage,
                    modifier = Modifier.onSizeChanged { rowWidth = it.width }.testTag(LibraryTags.SWITCH),
                ) {
                    val markedAction =
                        if (mode == Mode.Serverless) R.string.library_view_marked_action_serverless else R.string.library_view_marked_action
                    // While entries are chosen the view does not change: the tabs do not respond (v0.38).
                    ViewTab(LibraryView.MARKED, shown, markedLabel, markedAction, choose, words, labelSize, enabled = !selecting)
                    ViewTab(LibraryView.OWN, shown, R.string.library_view_own, R.string.library_view_own_action, choose, words, labelSize, enabled = !selecting)
                }
            }
        },
        floatingActionButton = {
            ViewAction(
                shown, mode, offered = offers[shown] == true && selection == null,
                list = if (shown == LibraryView.OWN) ownList else markedList,
                way = way,
                onWrite = chooseNew, onImport = import,
                // The tallest it has been: shrunk to its icon as a list goes down, it moves nothing.
                modifier = Modifier.onSizeChanged { actionHeight = maxOf(actionHeight, it.height) },
            )
        },
    ) { padding ->
        // The lists run under the navigation bar, their last entry kept clear of it and of the view's action — taller
        // than its usual height where its words take two lines at a large font scale.
        val room = maxOf(ACTION_ROOM, with(LocalDensity.current) { actionHeight.toDp() } + ACTION_MARGINS)
        val bottom = padding.calculateBottomPadding() + room
        val layout = LocalLayoutDirection.current
        HorizontalPager(
            state = pager,
            key = { VIEWS[it].name },
            // While entries are chosen the view does not change: the pages are not swiped (v0.38).
            userScrollEnabled = !selecting,
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
                    markedList, bottom, import, notice, adding, printing, onOffer = { offers[LibraryView.MARKED] = it },
                    collections = rememberListed(collections, CollectionOrigin.IMPORTED),
                    selection = selection?.takeIf { it.view == LibraryView.MARKED },
                    selecting = selecting,
                    restored = restored,
                    header = markedHeader,
                    onListed = { if (selectables[LibraryView.MARKED] != it) selectables[LibraryView.MARKED] = it },
                )
                LibraryView.OWN -> OwnList(
                    recipes, session, editor, timers, card.marks, mode, thumbnails, card.ingredients, bookKitchen,
                    ownList, bottom, notice, chooseNew, adding, printing, onOffer = { offers[LibraryView.OWN] = it },
                    collections = rememberListed(collections, CollectionOrigin.OWN),
                    selection = selection?.takeIf { it.view == LibraryView.OWN },
                    selecting = selecting,
                    restored = restored,
                    header = ownHeader,
                    onListed = { if (selectables[LibraryView.OWN] != it) selectables[LibraryView.OWN] = it },
                )
            }
        }
        // A file being imported or exported shows its progress over the top of the list, shifting nothing; so does a
        // document being made to print or share.
        val progressAt = Modifier.padding(top = padding.calculateTopPadding())
        val file = work
        if (file != null) FileProgress(file, progressAt) else if (printing.busy) PrintProgress(progressAt)
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
 * returns that recipe's identity while it is said, so that its list is brought to it. A collection a file brought
 * (v0.36) is said the same way, its Open opening the collection, and [lists] brought to their top, where it stands.
 * The message is closed once the snackbar goes; a failure is the dialog's to say (AppNavHost).
 */
@Composable
private fun rememberFileNotice(session: LibrarySession, snackbars: SnackbarHostState, vararg lists: LazyListState): String? {
    val message by session.message.collectAsState()
    val notice = message?.takeIf { it.isNotice && (it.recipeId.isNotEmpty() || it.collectionId.isNotEmpty()) }
    val resources = LocalContext.current.resources
    val open = stringResource(R.string.file_open)
    LaunchedEffect(notice) {
        if (notice == null) return@LaunchedEffect
        // A list not laid out yet is brought there once it is; the notice waits for neither.
        if (notice.collectionId.isNotEmpty()) lists.forEach { list -> launch { list.scrollToItem(0) } }
        val result = snackbars.showSnackbar(notice.text(resources), actionLabel = open, duration = SnackbarDuration.Long)
        session.told()
        if (result != SnackbarResult.ActionPerformed) return@LaunchedEffect
        if (notice.collectionId.isNotEmpty()) session.openCollection(notice.collectionId) else session.openCard(notice.recipeId, session.view.value)
    }
    return notice?.recipeId?.takeIf { it.isNotEmpty() }
}

/**
 * The book's snackbar, and a collection's: one notice at a time, above the keyboard where it is open, gone by itself after a while or
 * swiped away either way, read by the screen reader as it comes (the host's live region). At large text its action
 * stands on a line of its own ([isLargeText]).
 */
@Composable
internal fun LibrarySnackbarHost(snackbars: SnackbarHostState) {
    SnackbarHost(snackbars, Modifier.imePadding().testTag(LibraryTags.SNACKBAR)) { data ->
        val swipe = rememberSwipeToDismissBoxState()
        // Only the snackbar itself is swiped: the margin around it lets a tap through to the list beneath.
        SwipeToDismissBox(swipe, backgroundContent = {}, modifier = Modifier.padding(12.dp), onDismiss = { data.dismiss() }) {
            val label = data.visuals.actionLabel
            Snackbar(
                modifier = Modifier.testTag(LibraryTags.NOTICE),
                action = label?.let { { SnackbarAction(it, data::performAction) } },
                // At large text the action stands on a line of its own, leaving the words the whole width (v0.38).
                actionOnNewLine = isLargeText(),
            ) { Text(data.visuals.message) }
        }
    }
}

/**
 * Says that a recipe left the book, named by its title — "“Plum cake” deleted", or "“Plum cake” removed" for a found
 * one; "Recipe deleted", "Recipe removed" for one with no title — with Undo, for as long as
 * the session offers to undo it (FLOW-053). Undo puts it back; the notice gone without it — by itself, swiped away,
 * replaced by another notice, or the book left — makes the leaving final. A notice cut short by the screen turning is
 * said again. An Undo the book could not take back is said to have failed — for several, how many could not come back.
 */
@Composable
internal fun RemovalNotice(session: LibrarySession, snackbars: SnackbarHostState) {
    val removal by session.removal.collectAsState()
    val said = removal?.let { taken ->
        val title = taken.title.trim()
        when {
            // Several left together: counted, never named.
            taken.count > 1 -> pluralStringResource(
                if (taken.found) R.plurals.library_recipes_removed else R.plurals.library_recipes_deleted, taken.count, taken.count,
            )
            title.isEmpty() -> stringResource(if (taken.found) R.string.library_recipe_removed else R.string.library_recipe_deleted)
            taken.found -> stringResource(R.string.library_recipe_removed_titled, title)
            else -> stringResource(R.string.library_recipe_deleted_titled, title)
        }
    }
    val undo = stringResource(R.string.library_undo)
    LaunchedEffect(removal) {
        val shown = removal ?: return@LaunchedEffect
        val result = snackbars.showSnackbar(said.orEmpty(), actionLabel = undo, duration = SnackbarDuration.Long)
        if (result == SnackbarResult.ActionPerformed) session.undoRemoval(shown) else session.endRemoval(shown)
    }
    // An Undo the book could not take back is said, once.
    val failed by session.undoFailed.collectAsState()
    // How many could not come back: one alone is said as before, several are counted.
    val many = failed?.notBack ?: 0
    val notBack = if (many > 1) pluralStringResource(R.plurals.library_undo_failed_several, many, many) else stringResource(R.string.library_undo_failed)
    LaunchedEffect(failed) {
        val said = failed ?: return@LaunchedEffect
        snackbars.showSnackbar(notBack, duration = SnackbarDuration.Long)
        session.undoFailureSaid(said)
    }
}

/**
 * Says that a collection was deleted from its screen — "Collection “New Year's menu” deleted" — with Undo, for as long
 * as the session offers to undo it (v0.36). Undo puts the collection back with its name and order, and the recipes
 * of it the book still holds; the notice gone without it leaves it deleted. Its recipes stay in the book either way.
 */
@Composable
private fun CollectionDeletionNotice(session: LibrarySession, collections: CollectionRepository, snackbars: SnackbarHostState) {
    val deleted by session.collectionDeletion.collectAsState()
    val said = deleted?.let { stringResource(R.string.collection_deleted, it.name) }
    val undo = stringResource(R.string.collection_undo)
    LaunchedEffect(deleted) {
        val shown = deleted ?: return@LaunchedEffect
        val result = snackbars.showSnackbar(said.orEmpty(), actionLabel = undo, duration = SnackbarDuration.Long)
        // Put back where the session's work runs, which outlives the book's screen.
        if (session.endCollectionDeletion(shown) && result == SnackbarResult.ActionPerformed) {
            session.viewModelScope.launch { collections.restore(shown) }
        }
    }
}

/**
 * Tells [onListed] what [view] lists, as the selection sees it — every entry its search and filters let through, not
 * only those shown so far — once it is read; and the selection forgets an entry chosen that it lists no more.
 */
@Composable
private fun ListedForSelection(view: LibraryView, session: LibrarySession, listed: List<Selectable>?, onListed: (List<Selectable>) -> Unit) {
    SideEffect { if (listed != null) onListed(listed) }
    LaunchedEffect(listed) { if (listed != null) session.keepListed(view, listed.mapTo(HashSet()) { it.recipeId }) }
}

/**
 * The actions of the selection's bar on the recipes [selection] chose of [listed], in this order: "Add to collection" and
 * "Add to shopping list" ([adding]), on those the book holds the content of, in the order they were chosen; then Print
 * and Share as PDF ([printing]) of those, in the order the view lists them, as one document; then Delete in
 * the own view, Remove in the marked one, offered while it applies to one of them at least, and taking only those it
 * applies to — at once, asking nothing, one notice with one Undo for all (FLOW-053). An action taken ends the selection;
 * Print and Share as PDF end it once they have run to their end, the document handed over or its failure said, if it
 * is still the selection on.
 */
private fun selectionActions(
    selection: BookSelection,
    listed: List<Selectable>,
    session: LibrarySession,
    recipes: RecipeRepository,
    files: RecipeImport,
    timers: CardTimers,
    marks: DoneMarks,
    adding: BookAdding,
    printing: BookPrinting,
): List<SelectionAction> {
    val chosen = listed.filter { it.recipeId in selection.ids }
    val removable = chosen.filter { it.removable }.map { RecipeId(it.recipeId) }
    val holding = chosen.filter { it.holds }.mapTo(HashSet()) { it.recipeId }
    // In the order they were chosen ("Select all": in the view's).
    val held = selection.ids.filter { it in holding }
    return buildList {
        if (held.isNotEmpty()) {
            add(SelectionAction(R.string.collection_sheet_menu, LibraryTags.SELECTION_ADD_TO_COLLECTION, R.drawable.ic_add_to_collection) {
                adding.addAllToCollection(held)
            })
            add(SelectionAction(R.string.shopping_add, LibraryTags.SELECTION_ADD_TO_SHOPPING, R.drawable.ic_add_shopping) {
                adding.addAllToShopping(held)
            })
            // In the order the view lists them.
            val shown = chosen.filter { it.holds }.map { it.recipeId }
            add(SelectionAction(R.string.print_menu_print, LibraryTags.SELECTION_PRINT) { printing.all(shown, PrintWay.PRINT) })
            add(SelectionAction(R.string.print_menu_share, LibraryTags.SELECTION_SHARE_PDF) { printing.all(shown, PrintWay.SHARE) })
        }
        if (removable.isNotEmpty()) {
            val own = selection.view == LibraryView.OWN
            add(
                SelectionAction(
                    if (own) R.string.library_entry_delete else R.string.library_entry_remove,
                    if (own) LibraryTags.SELECTION_DELETE else LibraryTags.SELECTION_REMOVE,
                    destructive = true,
                ) {
                    session.endSelection()
                    if (own) deleteRecipes(recipes, timers, marks, removable, session::removed) else removeAllFound(files, timers, marks, removable, session::removed)
                },
            )
        }
    }
}

/** The collections of [origin] the book holds, as they change; none until they are read. */
@Composable
private fun rememberListed(collections: CollectionRepository, origin: CollectionOrigin): List<CollectionRow> {
    val listed by remember(collections, origin) { collections.listed(origin) }.collectAsState(initial = emptyList())
    return listed
}

/** The collections of the active book, from the composition root (v0.36). */
@Composable
internal fun rememberBookCollections(): CollectionRepository {
    val context = LocalContext.current.applicationContext
    return remember(context) { (context as RecipeForkApplication).container.collections }
}

/**
 * Brings [list] to the entry of [recipeId] once, when [rows] list it; nothing when they do not. [before] items stand
 * above the rows in the list — the row of collections.
 */
@Composable
private fun RevealOnNotice(recipeId: String?, rows: List<String>?, list: LazyListState, before: Int = 0) {
    val at = if (recipeId == null) -1 else rows?.indexOf(recipeId) ?: -1
    LaunchedEffect(recipeId, at >= 0) { if (at >= 0) list.animateScrollToItem(at + before) }
}

/**
 * Brings the entry of [recipeId], put back by an Undo (v0.38), into view — only when it is not wholly in view already, so
 * that a recipe put back among those shown moves nothing — once the [list] is laid out with the [shown] rows, and then
 * tells [onDone]. One the view lists ([listed]) but does not show yet — beyond the rows shown so far — is not sought:
 * [onDone] is told at once, so that the list does not jump to it later. [before] items stand above the rows.
 */
@Composable
private fun RevealRestored(
    recipeId: String?,
    listed: List<String>?,
    shown: List<String>?,
    list: LazyListState,
    before: Int,
    onDone: (String) -> Unit,
) {
    val at = if (recipeId == null) -1 else shown?.indexOf(recipeId) ?: -1
    val beyond = recipeId != null && at < 0 && listed?.contains(recipeId) == true
    val items = (shown?.size ?: 0) + before
    LaunchedEffect(recipeId, at, beyond, items) {
        if (recipeId == null) return@LaunchedEffect
        if (beyond) return@LaunchedEffect onDone(recipeId)
        if (at < 0) return@LaunchedEffect
        // Laid out with it among its items: only then does the layout say where it stands.
        val laid = snapshotFlow { list.layoutInfo }.first { it.totalItemsCount == items }
        val item = laid.visibleItemsInfo.firstOrNull { it.key == recipeId }
        val whole = item != null && item.offset >= laid.viewportStartOffset && item.offset + item.size <= laid.viewportEndOffset
        if (!whole) list.animateScrollToItem(at + before)
        onDone(recipeId)
    }
}

/** The key of the row of collections among a list's items: no recipe's identity is it. */
private const val COLLECTIONS_KEY = "collections"

/** The views in the order of their tabs and pages: a view's page is its ordinal. */
private val VIEWS = LibraryView.entries

/** The room kept under a list's last entry for the view's action: the action's usual height and its margins. */
private val ACTION_ROOM = 88.dp

/** The margins of the view's action: below it, to the bottom of the screen, and above it, to what it must not cover. */
private val ACTION_MARGINS = 32.dp

/** How long a view waits on its list before it shows that the list is coming, so that a quick one shows no flash. */
internal const val LOADING_DELAY_MS = 300L

@Composable
private fun ViewTab(
    tab: LibraryView,
    current: LibraryView,
    @StringRes label: Int,
    @StringRes action: Int,
    choose: (LibraryView) -> Unit,
    width: Int,
    size: TextStyle,
    enabled: Boolean = true,
) {
    val actionLabel = stringResource(action)
    Tab(
        selected = tab == current,
        onClick = { choose(tab) },
        text = { TabLabel(stringResource(label), width, size) },
        enabled = enabled,
        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .heightIn(min = 48.dp)
            // The tab announces what choosing it does: which recipes it shows (A11Y-005).
            .semantics {
                if (enabled) {
                    onClick(label = actionLabel) {
                        choose(tab)
                        true
                    }
                }
            }
            .testTag(LibraryTags.tab(tab)),
    )
}

/**
 * The size of the tabs' words, each tab's given [width] pixels: the tabs' style, made smaller as far as the widest word
 * of any of [labels] needs to fit — a long Russian word at a large font scale on a narrow phone — so that every tab is
 * drawn at one size ([fitWordsTogether]). Only its size and line height are meant.
 */
@Composable
private fun rememberTabLabelSize(labels: List<String>, width: Int): TextStyle {
    val style = MaterialTheme.typography.titleSmall
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(labels, style, width, measurer, density) { fitWordsTogether(labels, style, width, measurer, density) }
}

/**
 * A tab's words, given the [width] in pixels they have, at the tabs' one [size]: on one line where they fit, else
 * wrapped between words; a word wider than its room even at the least size is not broken, but the words keep to one
 * line and end with an ellipsis.
 */
@Composable
private fun TabLabel(text: String, width: Int, size: TextStyle) {
    val style = LocalTextStyle.current.merge(TextStyle(fontSize = size.fontSize, lineHeight = size.lineHeight))
    val measurer = rememberTextMeasurer()
    val overflows = remember(text, style, width, measurer) { widestWord(listOf(text), style, measurer) > width }
    FittedText(text, FittedWords(style, overflows), Modifier.testTag(LibraryTags.TAB_LABEL))
}

/** Which way the list shown was last moved by the reader: up (towards its top) or down. */
@Stable
private class ScrollWay : NestedScrollConnection {
    var up by mutableStateOf(true)

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (available.y != 0f) up = available.y > 0f
        return Offset.Zero
    }
}

/** The margin Material 3 keeps on each side of a tab's words. */
private val TAB_TEXT_PADDING = 16.dp

/**
 * The action of the view shown, at the bottom right: writing a new recipe in the own view (FLOW-042 (5)), importing a
 * recipe file in the marked view in [Mode.Serverless] (FLOW-064); none in the marked view in [Mode.Standard], nor
 * while the view's empty state offers its own. It shrinks to its icon while the [list] goes down, and shows its words
 * again as it goes up or stands at its top; at large text ([isLargeText]) it starts and stays shrunk to its icon, its
 * words still read to the screen reader.
 */
@Composable
private fun ViewAction(
    view: LibraryView,
    mode: Mode,
    offered: Boolean,
    list: LazyListState,
    way: ScrollWay,
    onWrite: () -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!offered) return
    // At large text its words would cover the first entry: it stays shrunk to its icon (v0.38).
    val large = isLargeText()
    val expanded by remember(list, way, large) { derivedStateOf { !large && (!list.canScrollBackward || way.up) } }
    when {
        view == LibraryView.OWN -> FloatingAction(R.string.library_own_new, R.drawable.ic_new_recipe, LibraryTags.NEW, expanded, onWrite, modifier)
        // Its short words stay whole on a narrow phone at large text; the screen reader hears the whole action.
        mode == Mode.Serverless -> FloatingAction(
            R.string.file_import_short, R.drawable.ic_import_file, LibraryTags.IMPORT, expanded, onImport, modifier,
            action = R.string.file_import,
        )
    }
}

@Composable
private fun FloatingAction(
    @StringRes label: Int,
    @DrawableRes icon: Int,
    tag: String,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @StringRes action: Int? = null,
) {
    val words = stringResource(label)
    val actionLabel = action?.let { stringResource(it) }
    ExtendedFloatingActionButton(
        text = { Text(words) },
        icon = { Icon(painterResource(icon), contentDescription = null) },
        onClick = onClick,
        expanded = expanded,
        // The action reads its words to the screen reader, shrunk to its icon or not: the button keeps its shown words
        // from the screen reader, so they are given to the button itself.
        modifier = modifier
            .semantics {
                text = AnnotatedString(words)
                if (actionLabel != null) {
                    onClick(label = actionLabel) {
                        onClick()
                        true
                    }
                }
            }
            .testTag(tag),
    )
}

/**
 * Shown in place of a view's list while it is read: nothing at first, then, should the reading take longer than
 * [LOADING_DELAY_MS], a progress indicator, so that a list read quickly shows no flash of it.
 */
@Composable
internal fun ListLoading(modifier: Modifier = Modifier) {
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
    adding: BookAdding,
    printing: BookPrinting,
    onOffer: (Boolean) -> Unit,
    collections: List<CollectionRow> = emptyList(),
    selection: BookSelection? = null,
    selecting: Boolean = false,
    restored: String? = null,
    header: HeaderScroll = rememberHeaderScroll(),
    onListed: (List<Selectable>) -> Unit = {},
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
    // The collections stand above the recipes while the view is not narrowed by a search or a filter (v0.36).
    val above = if (listed?.narrowed == false && collections.isNotEmpty()) 1 else 0
    ScrollToTopOnChange(listed?.params, listState)
    RevealOnNotice(reveal, loaded?.map { it.recipeId }, listState, above)
    // A recipe an Undo put back is brought into view (v0.38).
    RevealRestored(restored, found?.map { it.recipeId }, loaded?.map { it.recipeId }, listState, above, onDone = session::restoredShown)
    // Near the end of what is shown, the next recipes are shown (FLOW-041 (5)).
    LaunchedEffect(listState, loaded?.size, limit, above) {
        snapshotFlow { (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1) - above }
            .distinctUntilChanged()
            .collect { last ->
                val size = loaded?.size ?: return@collect
                if (size == limit && last >= size - OWN_PAGE / 3) limit += OWN_PAGE
            }
    }
    val export = rememberExport(recipes, session)
    val shown = listed
    val selectable = remember(found) { found?.map { Selectable(it.recipeId, removable = it.state in DELETABLE, holds = true) } }
    ListedForSelection(LibraryView.OWN, session, selectable, onListed)
    // A book that lists recipes still offers to write one (FLOW-042 (5)), from the view's action.
    val offered = shown != null && !(shown.viewIsEmpty && !shown.narrowed)
    SideEffect { onOffer(offered) }
    if (shown != null && shown.viewIsEmpty && !shown.narrowed) {
        // A book with no recipe written in it may still hold collections of found ones: they stand above.
        Column(Modifier.fillMaxSize()) {
            CollectionsRow(collections, session::openCollection, Modifier.padding(top = ENTRY_GAP), enabled = !selecting)
            NothingWritten(onWrite, Modifier.padding(bottom = bottom))
        }
        return
    }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().nestedScroll(header.connection)) {
            if (shown != null) {
                ScrollAwayHeader(header) {
                    ListHeader(
                        search, order, onOrder = { session.orderBy(LibraryView.OWN, it) },
                        filters, onFilters = { session.filter(LibraryView.OWN, it) }, choices, shown.languages, shown.hold, shown.carries,
                        kitchen = sheetKitchen(kitchen, session),
                        enabled = !selecting,
                    )
                    HorizontalDivider()
                }
            }
            // While the list is read, no list is laid out: an empty one would bring the view's kept place to its top.
            if (shown == null) return@Column
            if (shown.narrowed && shown.rows.isEmpty()) {
                // No list to scroll it back: the header is shown whole.
                LaunchedEffect(Unit) { header.reveal() }
                // The view's action floats over the bottom: the state keeps clear of it, as the list does.
                NothingFound(
                    shown, onClearSearch = search::clear, onClearFilters = { session.filter(LibraryView.OWN, BookFilters()) },
                    modifier = Modifier.padding(bottom = bottom),
                )
                return@Column
            }
            val cover = coverOf(recipes, thumbnails)
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(top = ENTRY_GAP, bottom = bottom),
                modifier = Modifier.fillMaxSize().testTag(LibraryTags.OWN_LIST),
            ) {
                // The entries' rows meet at their hairlines; the collections keep the gap above them.
                if (above > 0) item(key = COLLECTIONS_KEY) {
                    CollectionsRow(collections, session::openCollection, Modifier.padding(bottom = ENTRY_GAP), enabled = !selecting)
                }
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
                        adding = adding,
                        printing = printing,
                        selected = selection?.let { row.recipeId in it.ids },
                        onSelect = { session.select(LibraryView.OWN, row.recipeId) },
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
        engraving = Engraving.MARK,
        headline = stringResource(R.string.library_own_empty),
        sentence = stringResource(R.string.library_own_empty_sentence),
        action = EmptyAction(stringResource(R.string.library_own_new), R.drawable.ic_new_recipe, LibraryTags.WRITE, onWrite),
        tag = LibraryTags.OWN_EMPTY,
        modifier = modifier,
    )
}

/**
 * What "New recipe" offers (T-280): writing one from scratch — a new recipe in the active book, opened in the editor
 * ([onWrite]) — or reading one from a pasted text, on the paste screen ([onPaste]).
 */
@Composable
private fun NewRecipeChoice(onWrite: () -> Unit, onPaste: () -> Unit, onDismiss: () -> Unit) {
    SourceSheet(
        stringResource(R.string.library_own_new),
        listOf(
            SourceItem(R.drawable.ic_edit, stringResource(R.string.paste_choice_write), LibraryTags.FROM_SCRATCH, onClick = onWrite),
            SourceItem(R.drawable.ic_paste_text, stringResource(R.string.paste_choice_paste), LibraryTags.PASTE, onClick = onPaste),
        ),
        onDismiss = onDismiss,
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
 * An own recipe's entry, a row of the book's contents ([BookEntryRow]); tapping it opens the recipe's card (FLOW-029 (1)). Its actions are in its menu, at the
 * end of its title line, from the top ([entryActions]): "Add to collection" and "Add to shopping list" ([adding]), Print
 * and Share as PDF ([printing]), in [Mode.Serverless] the export of every recipe (FLOW-063), and deletion for a recipe
 * its owner may delete (FLOW-053 (1), ST-010).
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
    adding: BookAdding,
    printing: BookPrinting,
    selected: Boolean? = null,
    onSelect: (() -> Unit)? = null,
) {
    val removal = if (row.state in DELETABLE) {
        EntryAction(R.string.library_entry_delete, LibraryTags.delete(row.recipeId), destructive = true, onClick = onDelete)
    } else {
        null
    }
    val actions = entryActions(row.recipeId, mode, adding, printing, onExport, removal)
    BookEntryRow(row.recipeId, row.title, row.coverId, cover, actions, onOpen, modifier, selected = selected, onSelect = onSelect) {
        EntryTitle(row.recipeId, row.title)
        if (row.forked) EntryProvenance(row.recipeId, row.parentTitle)
        EntryTags(row.recipeId, tags)
        // The state is named in words, never carried by colour alone (A11Y-008); a serverless draft names none.
        stateLabel(row.state, mode)?.let { Text(stringResource(it), style = MaterialTheme.typography.labelLarge) }
        // The sending mark is shown beside the state and never replaces it (FLOW-036 (6), FLOW-005); a build without
        // the server sends nothing, so it shows none (FLOW-062 (1)).
        if (mode == Mode.Standard) Text(stringResource(sentLabel(row.sent)), style = MaterialTheme.typography.bodySmall)
    }
}

/**
 * The items of an entry's menu, from the top: "Add to collection", "Add to shopping list" — "Update in shopping list"
 * once the recipe is on the list, as on the card — then Print and Share as PDF of the recipe as its card prints it
 * ([printing], T-264), then in [Mode.Serverless] the export to a file (FLOW-063), and last [removal], where the recipe
 * may leave the book.
 */
private fun entryActions(
    recipeId: String,
    mode: Mode,
    adding: BookAdding,
    printing: BookPrinting,
    onExport: () -> Unit,
    removal: EntryAction?,
): List<EntryAction> =
    buildList {
        add(EntryAction(R.string.collection_sheet_menu, LibraryTags.addToCollection(recipeId)) { adding.addToCollection(recipeId) })
        val shopping = if (adding.listed(recipeId)) R.string.card_shopping_update else R.string.shopping_add
        add(EntryAction(shopping, LibraryTags.addToShopping(recipeId)) { adding.addToShopping(recipeId) })
        add(EntryAction(R.string.print_menu_print, LibraryTags.print(recipeId)) { printing.one(recipeId, PrintWay.PRINT) })
        add(EntryAction(R.string.print_menu_share, LibraryTags.sharePdf(recipeId)) { printing.one(recipeId, PrintWay.SHARE) })
        if (mode == Mode.Serverless) add(EntryAction(R.string.file_export, LibraryTags.export(recipeId), onClick = onExport))
        removal?.let(::add)
    }

/** The space above a list's first entry, and between the row of collections and the entries. */
internal val ENTRY_GAP = 12.dp

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
    adding: BookAdding,
    printing: BookPrinting,
    onOffer: (Boolean) -> Unit,
    collections: List<CollectionRow> = emptyList(),
    selection: BookSelection? = null,
    selecting: Boolean = false,
    restored: String? = null,
    header: HeaderScroll = rememberHeaderScroll(),
    onListed: (List<Selectable>) -> Unit = {},
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
    // The imported collections stand above the recipes while the view is not narrowed (v0.36); an empty view shows
    // them above its empty state instead.
    val above = if (listed?.narrowed == false && listed?.viewIsEmpty == false && collections.isNotEmpty()) 1 else 0
    RevealOnNotice(reveal, marked?.map { it.recipeId }, listState, above)
    // A recipe an Undo put back is brought into view (v0.38).
    val markedIds = marked?.map { it.recipeId }
    RevealRestored(restored, markedIds, markedIds, listState, above, onDone = session::restoredShown)
    val export = rememberExport(recipes, session)
    val shown = listed
    val selectable = remember(marked) { marked?.map { Selectable(it.recipeId, removable = it.held, holds = it.held) } }
    ListedForSelection(LibraryView.MARKED, session, selectable, onListed)
    val empty = shown != null && shown.viewIsEmpty && !shown.narrowed
    val offered = mode == Mode.Serverless && shown != null && !empty
    SideEffect { onOffer(offered) }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().nestedScroll(header.connection)) {
            // The empty view's one offer is the view's own action: the import of a recipe file in [Mode.Serverless];
            // in [Mode.Standard] the view has no action, and its empty state none.
            if (empty) {
                // Imported collections whose recipes were all removed still stand above, to be opened or deleted.
                CollectionsRow(collections, session::openCollection, Modifier.padding(top = ENTRY_GAP), enabled = !selecting)
                val serverless = mode == Mode.Serverless
                EmptyState(
                    engraving = Engraving.MARK,
                    headline = stringResource(if (serverless) R.string.library_marked_empty_serverless else R.string.library_marked_empty),
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
                ScrollAwayHeader(header) {
                    ListHeader(
                        search, order, onOrder = { session.orderBy(LibraryView.MARKED, it) },
                        filters, onFilters = { session.filter(LibraryView.MARKED, it) }, choices, shown.languages, shown.hold, shown.carries,
                        kitchen = sheetKitchen(kitchen, session),
                        enabled = !selecting,
                    )
                    HorizontalDivider()
                }
            }
            // While the list is read, no list is laid out: an empty one would bring the view's kept place to its top.
            if (shown == null) return@Column
            if (shown.narrowed && shown.rows.isEmpty()) {
                // No list to scroll it back: the header is shown whole.
                LaunchedEffect(Unit) { header.reveal() }
                // The view's action floats over the bottom: the state keeps clear of it, as the list does.
                NothingFound(
                    shown, onClearSearch = search::clear, onClearFilters = { session.filter(LibraryView.MARKED, BookFilters()) },
                    modifier = Modifier.padding(bottom = bottom),
                )
                return@Column
            }
            val cover = coverOf(recipes, thumbnails)
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(top = ENTRY_GAP, bottom = bottom),
                modifier = Modifier.fillMaxSize().testTag(LibraryTags.MARKED_LIST),
            ) {
                // The entries' rows meet at their hairlines; the collections keep the gap above them.
                if (above > 0) item(key = COLLECTIONS_KEY) {
                    CollectionsRow(collections, session::openCollection, Modifier.padding(bottom = ENTRY_GAP), enabled = !selecting)
                }
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
                        adding = adding,
                        printing = printing,
                        selected = selection?.let { row.recipeId in it.ids },
                        onSelect = { session.select(LibraryView.MARKED, row.recipeId) },
                    )
                }
            }
        }
        if (shown == null) ListLoading()
    }
}

/**
 * A marked recipe's entry, a row of the book's contents ([BookEntryRow]): its title, any stated author and its tags; one whose content the book holds opens,
 * and its menu, at the end of its title line, offers "Add to collection" and "Add to shopping list" ([adding]), Print and
 * Share as PDF ([printing]), in [Mode.Serverless] its export (FLOW-063 (2)), and its removal ([entryActions]). One whose
 * content the book does not hold offers nothing.
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
    adding: BookAdding,
    printing: BookPrinting,
    selected: Boolean? = null,
    onSelect: (() -> Unit)? = null,
) {
    val actions = if (!row.held) {
        emptyList()
    } else {
        val removal = EntryAction(R.string.library_entry_remove, LibraryTags.remove(row.recipeId), destructive = true, onClick = onRemove)
        entryActions(row.recipeId, mode, adding, printing, onExport, removal)
    }
    BookEntryRow(row.recipeId, row.title, row.coverId, cover, actions, onOpen.takeIf { row.held }, modifier, selected = selected, onSelect = onSelect) {
        EntryTitle(row.recipeId, row.title)
        if (row.forked) EntryProvenance(row.recipeId, row.parentTitle)
        row.statedAuthor?.let { EntryAuthor(row.recipeId, it) }
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
    /** The action beside the settings that opens the shopping list. */
    const val SHOPPING = "library-shopping"
    /** The clearing of saved media's dialogs, now the settings' ([SettingsTags]); named here too for older tests. */
    const val CLEAR_MEDIA_STATEMENT = SettingsTags.CLEAR_MEDIA_STATEMENT
    const val CLEAR_MEDIA_CONFIRM = SettingsTags.CLEAR_MEDIA_CONFIRM
    const val CLEAR_MEDIA_DECLINE = SettingsTags.CLEAR_MEDIA_DECLINE
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
    /** "New recipe"'s two ways to start (T-280): from scratch, and from a pasted text. */
    const val FROM_SCRATCH = "library-from-scratch"
    const val PASTE = "library-paste"
    /** The header above a view's list: its search field and its order. */
    const val HEADER = "library-header"
    /** The room the header takes above the list, less what has scrolled away of it (v0.38). */
    const val HEADER_AREA = "library-header-area"
    const val SEARCH = "library-search"
    const val SEARCH_CLEAR = "library-search-clear"
    /** The search field's short label, and the longer words inside it, shown while it is focused and empty. */
    const val SEARCH_LABEL = "library-search-label"
    const val SEARCH_PLACEHOLDER = "library-search-placeholder"
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
    /** The sheet's way into My kitchen: "Set up My kitchen" while it is empty, "Edit My kitchen" once it is not. */
    const val EQUIPMENT_KITCHEN = "library-equipment-kitchen"
    /** The one action of a view that the filter "Enough equipment" alone narrowed to nothing: switching it off. */
    const val NOTHING_FOUND_EQUIPMENT_OFF = "library-nothing-found-equipment-off"
    fun tab(view: LibraryView) = "library-tab-${view.name.lowercase()}"
    /** The words of a tab, found inside the node tagged with [tab]. */
    const val TAB_LABEL = "library-tab-label"
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
    /** The selection's bar, in place of the book's title while entries are chosen: its ✕, its count, its ⋮ and items. */
    const val SELECTION_BAR = "library-selection-bar"
    /** A bar fading out as the other fades in, which takes no touch and is hidden from the screen reader. */
    const val BAR_LEAVING = "library-bar-leaving"
    const val SELECTION_CLOSE = "library-selection-close"
    const val SELECTION_COUNT = "library-selection-count"
    const val SELECTION_MENU = "library-selection-menu"
    const val SELECT_ALL = "library-select-all"
    const val SELECTION_DELETE = "library-selection-delete"
    const val SELECTION_REMOVE = "library-selection-remove"
    /** The selection's "Add to collection" and "Add to shopping list", in its bar. */
    const val SELECTION_ADD_TO_COLLECTION = "library-selection-add-to-collection"
    const val SELECTION_ADD_TO_SHOPPING = "library-selection-add-to-shopping"
    /** An entry's "Add to collection" and "Add to shopping list" ("Update in shopping list"), in its menu. */
    fun addToCollection(recipeId: String) = "library-add-to-collection-$recipeId"
    fun addToShopping(recipeId: String) = "library-add-to-shopping-$recipeId"

    // Print and Share as PDF (T-264): an entry's, the selection's, and the progress while a document is made.
    fun print(recipeId: String) = "library-print-$recipeId"
    fun sharePdf(recipeId: String) = "library-share-pdf-$recipeId"
    const val SELECTION_PRINT = "library-selection-print"
    const val SELECTION_SHARE_PDF = "library-selection-share-pdf"
    const val PRINT_PROGRESS = PrintTags.PROGRESS
    /** An entry's mark of being chosen, over its cover's bottom-end corner, while the selection is on. */
    fun selectionMark(recipeId: String) = "library-selection-mark-$recipeId"
    /** The place a cover with the tag [coverTag] holds, whether or not its picture is read yet. */
    fun coverSlot(coverTag: String) = "$coverTag-slot"
}
