package app.recipefork.app.card

import android.content.res.Configuration
import android.content.res.Resources
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ripple
import androidx.compose.foundation.background
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.graphics.Color
import app.recipefork.app.timer.LocalTimerCancelled
import app.recipefork.app.ui.Motion
import app.recipefork.app.ui.isLargeText
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.delay
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import app.recipefork.core.scaling.Rational
import app.recipefork.core.scaling.RequestedServings
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.runtime.Stable
import app.recipefork.core.recipe.Ingredient
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.layout.onGloballyPositioned
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.rememberUpdatedState
import app.recipefork.app.kitchen.Kitchen
import app.recipefork.app.collections.AddToCollectionSheet
import app.recipefork.app.library.DELETABLE
import app.recipefork.app.library.PendingRemoval
import app.recipefork.app.library.deleteRecipe
import app.recipefork.app.library.rememberRecipeImport
import app.recipefork.app.library.removeFound
import androidx.compose.ui.platform.LocalContext
import app.recipefork.app.media.PhotoStore
import app.recipefork.app.print.PrintWay
import app.recipefork.app.print.RecipeDocument
import app.recipefork.app.print.PrintProgress
import app.recipefork.app.print.rememberPrintOut
import app.recipefork.app.shopping.ListedRecipe
import app.recipefork.app.shopping.rememberAddToShopping
import app.recipefork.app.timer.rememberCardTimers
import androidx.compose.material3.Button
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import app.recipefork.app.library.EmptyAction
import app.recipefork.app.library.EmptyState
import app.recipefork.app.ui.SnackbarAction
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.recipefork.app.ui.Haptic
import app.recipefork.app.ui.rememberHaptics
import app.recipefork.app.R
import app.recipefork.app.timer.RecipeTimers
import app.recipefork.app.timer.RunningTimer
import app.recipefork.app.timer.RunningTimersBar
import app.recipefork.app.timer.inBar
import app.recipefork.app.timer.rememberRecipeTimers
import androidx.compose.runtime.derivedStateOf
import app.recipefork.app.dictionary.DictionaryEntry
import app.recipefork.app.dictionary.EquipmentEntryEntity
import app.recipefork.app.dictionary.IngredientEntryEntity
import app.recipefork.app.editor.interfaceLanguage
import app.recipefork.app.recipe.EquipmentNames
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.RecipeLanguage
import app.recipefork.core.recipe.StatedParent
import app.recipefork.core.recipe.UnitSystem
import app.recipefork.core.scaling.InterfaceLanguage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/**
 * What the card has of its recipe: nothing yet, the recipe, word that the book no longer holds it, or word that it
 * could not be read from the device.
 */
private sealed interface CardContent {
    data object Loading : CardContent
    data object Missing : CardContent
    data object Unreadable : CardContent
    /** The recipe, and whether the book holds it as a found one ([RecipeRepository.isMarked]). */
    data class Shown(val recipe: Recipe, val marked: Boolean) : CardContent
}

/** How the card's ⋮ takes a recipe out of the book: an own recipe is deleted, a found one removed. */
private enum class Removal { DELETE, REMOVE }

/** The removal the card offers for [recipe]: deletion in a deletable state (ST-010), removal for a found one. */
private fun removalOf(recipe: Recipe, marked: Boolean): Removal? = when {
    marked -> Removal.REMOVE
    recipe.state in DELETABLE -> Removal.DELETE
    else -> null
}

/** Whether the book holds [id] as a found recipe; a book that cannot say is taken to hold it as its own. */
private suspend fun markedIn(recipes: RecipeRepository, id: RecipeId): Boolean = try {
    recipes.isMarked(id)
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    false
}

/**
 * The recipe card: the saved recipe exactly as a reader sees it, not an editor with reader styling (FLOW-029 (1)).
 *
 * Under the top bar sit two tabs, always in sight: View, compact and for deciding, and Cooking, large and for busy hands
 * ([CardMode]). A tap on a tab or a horizontal swipe switches them; each keeps its own scroll. The card opens on the tab
 * last chosen on the device — View the first time ([CardEnvironment.lastTab]) — and on Cooking when a timer's
 * notification brings it to the timer's step (FLOW-013 (4)). The tab kept as last chosen is the one the card settles
 * on, so a Cooking a notification brought is kept too, and the next card opens on it.
 *
 * View is one scrolling page: the cover, the title with its summary, the provenance, the description, the equipment, the
 * serving count, the ingredients in order, the steps numbered. A part the recipe does not hold is left out with no
 * heading and no placeholder (FLOW-019 (1–3)), so a recipe holding only a title still reads as a whole recipe. Its bar holds back, the
 * title once it has scrolled away, and, only while the recipe is editable, the edit action that opens the editor on it
 * (FLOW-029 (2–4)); its ⋮ holds the recipe's removal — Delete for an own recipe in a deletable state, Remove for a found
 * one — which takes it out of the book at once, asking nothing, and closes the card to the book, which says so with
 * Undo ([onRemoved], FLOW-053). Fork, its one primary action, floats at the bottom end on every recipe, editable or
 * frozen (FORK-001, FORK-007) — a found recipe is held frozen, so its card offers fork and never edit (FLOW-064 (2)).
 *
 * Cooking holds nothing that distracts from cooking: no cover, no summary, no description, no fork, no edit. Its bar
 * holds back, the title and the ⋮, holding the recipe's shopping-list action and, while the recipe has done marks,
 * their clearing (FLOW-068) — which View's ⋮ holds too while there are marks, and which the card's snackbar offers to
 * undo; its page
 * the serving count, the ingredients and the steps, in lines the card's [CardTarget] tall that mark done, amounts fixed
 * from their lines, timers started from their steps. The screen stays on while it is shown (FLOW-014), and on either
 * tab while one of the recipe's timers runs.
 *
 * Nothing inside the recipe edits it; the serving count's − and +, which change how the recipe is read and never the
 * recipe (SCALE-001, SCALE-011), are one control in the content, shared by both tabs, and a fork's provenance, which
 * opens its parent's card, the other. It opens each time at the reader's usual count, or at the authored count while
 * none is set (FLOW-017).
 *
 * On View an item of equipment the reader's kitchen lacks opens My kitchen at it ([onOpenKitchen], given the equipment
 * dictionary entry it names), so that it is ticked there and the card found again on return; without [onOpenKitchen]
 * it answers no tap. While My kitchen is known to have nothing ticked — nothing at all, or, the equipment dictionary
 * read, nothing that counts — View offers under the equipment to set it up ([onSetUpKitchen]).
 *
 * Either tab's ⋮ always offers to put the recipe on the reader's shopping list at the count shown — the count a fixed
 * amount implies while one is fixed, none for a recipe with no count — or, once it is on the list, to set it to that
 * count, never adding it twice (FLOW-074); the card's snackbar says so, and its Open opens the list ([onOpenShopping]).
 * Right after it, while the card is given the book's collections ([CardEnvironment.collections]), "Add to collection"
 * opens a sheet of the reader's own collections ([AddToCollectionSheet], v0.36), whose changes apply as they are ticked
 * and which the card's snackbar confirms as it closes ("In 2 collections").
 * After it, Print hands the recipe to the system's print service and Share as PDF opens the share sheet on it as a PDF
 * file, each as View reads it at the count and fixings the card holds at that moment (FLOW-075 (1)).
 *
 * [onBack] is the bar's back action; the system's back, and its predictive gesture, are the navigation host's, which
 * leaves the card for the destination beneath it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardScreen(
    recipeId: RecipeId,
    recipes: RecipeRepository,
    environment: CardEnvironment,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
    onFork: () -> Unit = {},
    onOpenParent: (RecipeId) -> Unit = {},
    onOpenKitchen: ((Long) -> Unit)? = null,
    onSetUpKitchen: (() -> Unit)? = null,
    session: CardSession = remember { TextCardSession.inMemory() },
    onRemoved: (PendingRemoval) -> Unit = PendingRemoval::finish,
    onOpenShopping: () -> Unit = {},
) {
    // Each attempt reads the recipe afresh; a read that fails — a row that does not read back, the storage refusing —
    // is said to be a failure, not an absence, and "Try again" makes the next attempt (FLOW-020 (3)), shown loading
    // until it is read.
    var attempt by remember(recipeId, recipes) { mutableIntStateOf(0) }
    val content by remember(recipeId, recipes, attempt) {
        recipes.observe(recipeId)
            .map<Recipe?, CardContent> { recipe -> recipe?.let { CardContent.Shown(it, markedIn(recipes, recipeId)) } ?: CardContent.Missing }
            .onStart { emit(CardContent.Loading) }
            .catch { failure -> if (failure is Exception && failure !is CancellationException) emit(CardContent.Unreadable) else throw failure }
    }.collectAsState(initial = CardContent.Loading)
    var entries by remember(environment) { mutableStateOf<Map<Long, IngredientEntryEntity>?>(null) }
    var equipmentDictionary by remember(environment) { mutableStateOf<List<EquipmentEntryEntity>>(emptyList()) }
    val equipmentEntries = remember(equipmentDictionary) { equipmentDictionary.associateBy<DictionaryEntry, Long> { it.id } }
    // What the reader's kitchen has, as it changes: ticking an item in My kitchen marks an open card afresh.
    val ticked by environment.kitchen.held.collectAsState()
    val kitchen = remember(ticked, equipmentDictionary) { Kitchen(ticked, equipmentDictionary) }
    // Whether the equipment dictionary has been read: until it is — and for good if it cannot be — a kitchen with
    // anything ticked is not known to be empty, so View offers no set-up.
    var equipmentKnown by remember(environment) { mutableStateOf(false) }
    val setUpKitchen = onSetUpKitchen?.takeIf { ticked.isEmpty() || (equipmentKnown && kitchen.isEmpty) }
    LaunchedEffect(environment) {
        // Rows whose entry cannot be read are named as unnamed; the card itself never fails for it.
        val read = try {
            environment.ingredients.entries()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
        // So are the equipment entries an item names: an item whose entry cannot be read shows its qualifier alone.
        val equipment = try {
            environment.equipment.equipment().also { equipmentKnown = true }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
        equipmentDictionary = equipment
        entries = read.associateBy { it.id }
    }
    // The bar draws under the status bar and stays, its colour changing as the recipe scrolls under it — each tab's own
    // scroll, so that turning to a tab at its top shows the bar as at the top; the content keeps clear of the other bars,
    // the cutout and the keyboard, its last part above the navigation bar once scrolled to the end.
    val barStates = TABS.map { rememberTopAppBarState() }
    val shown = content
    val recipe = (shown as? CardContent.Shown)?.recipe
    val removal = (shown as? CardContent.Shown)?.let { removalOf(it.recipe, it.marked) }
    // The recipe leaves the book at once, asking nothing, and the card closes to the book, which says so with Undo.
    val remove = rememberRemoval(recipes, environment.marks, onRemoved)
    // The tab last chosen on the device, View the first time; a card brought to a timer's step opens on Cooking, and an
    // open card is turned to it. The tab kept as last chosen is the one the card settles on, whoever turned it: a
    // Cooking a notification brought is kept as chosen, as a tap on it would be.
    val toStep = LocalStepToShow.current?.pending?.collectAsState()?.value?.recipeId == recipeId.value
    val pager = remember(recipeId) {
        val opening = if (toStep) CardMode.COOKING else environment.lastTab.read() ?: CardMode.VIEW
        PagerState(currentPage = opening.ordinal) { TABS.size }
    }
    LaunchedEffect(pager) { snapshotFlow { pager.settledPage }.collect { environment.lastTab.keep(TABS[it]) } }
    LaunchedEffect(pager, toStep) { if (toStep) pager.scrollToPage(CardMode.COOKING.ordinal) }
    val tab = TABS[pager.currentPage]
    val bar = TopAppBarDefaults.pinnedScrollBehavior(barStates[tab.ordinal])
    // The title is View's heading; View's bar repeats it only once that heading has scrolled out of view — as when the
    // reader scrolls on, or the card opens at a place further down. Cooking's bar holds it always.
    var titleOut by remember(recipeId) { mutableStateOf(false) }
    // Each tab its own scroll. The place is restored by the part that was first at the top, not by a scroll in pixels,
    // which a change of text scale or orientation while away would make point elsewhere (FLOW-012 (5)).
    val scrolls = remember(recipeId) { TABS.map { ScrollState(0) } }
    val placesOf = remember(recipeId) { TABS.map { CardPlaces() } }
    val scroll = scrolls[tab.ordinal]
    val places = placesOf[tab.ordinal]
    // Whether the reader showed again the ingredients Cooking folded once every line was marked: the card's, not its
    // page's, which leaves the composition as the other tab is shown, so that it outlasts a turn to View and back and
    // the activity being recreated.
    val ingredientsShownAgain = rememberSaveable(recipeId) { mutableStateOf(false) }
    // The same of the equipment, which folds on its own.
    val equipmentShownAgain = rememberSaveable(recipeId) { mutableStateOf(false) }
    val shownAgain = remember(ingredientsShownAgain, equipmentShownAgain) { ShownAgain(equipmentShownAgain, ingredientsShownAgain) }
    // The recipe's timers running, or ringing, while their step is out of sight on the tab shown: in a bar at the bottom.
    val timersBar = recipe?.let { runningOutOfSight(it, scroll, places) }
    val timing = timersBar?.timers?.let { timers -> timers.held.values.any { !it.hasEnded(timers.now) || timers.rings(it.key) } } == true
    // The reader's done marks on the recipe's lines and steps, kept on the device (FLOW-068).
    val marks = recipe?.let { rememberCardMarks(environment.marks, recipes.bookName(), it) }
    val scope = rememberCoroutineScope()
    // The card's statements: one at a time, the newest replacing the one before (ui-conventions "Statements").
    val snackbars = remember { SnackbarHostState() }
    val clearMarks = rememberClearMarks(marks, snackbars)
    val loaded = entries
    // The system of units the amounts are shown in: a change in the settings shows them anew at once.
    val units by environment.unitSystem.system.collectAsState()
    // The scale the recipe is read at, one for both tabs, once what it is read with is at hand.
    val held = if (recipe != null && loaded != null && marks != null) {
        // Read once, not collected: the usual count only sets where a card opens, never moves an open one.
        rememberScaleInForce(recipe, environment.usualServings.count.value, session)
    } else {
        null
    }
    // Whether the recipe is on the shopping list, as the list changes: the ⋮ offers to update it rather than add it.
    val book = recipes.bookName().fileName
    val shoppingList by environment.shopping.list.collectAsState()
    val listed = recipe != null && shoppingList.recipes.any { it.isOf(book, recipe.id) }
    val language = cardLanguage()
    val addToShopping = rememberAddToShopping(environment.shopping, snackbars, onOpenShopping)
    // The sheet of the reader's collections, open over the card while it is asked for; its notice is the card's statement.
    val collections = environment.collections
    var collecting by rememberSaveable(recipeId) { mutableStateOf(false) }
    val say = rememberStatement(snackbars)
    // The recipe on paper or in a PDF, made at the tap from what the card holds then: its scale, the dictionaries, the
    // parent's title as the book holds it, the cover and step photos the device holds, the interface's language. While it
    // is made, the card says so under its tabs.
    var preparing by remember(recipeId) { mutableStateOf(false) }
    val printOut = rememberPrintOut(snackbars, onBusy = { preparing = it })
    val context = LocalContext.current
    val printRecipe: ((PrintWay) -> Unit)? = if (recipe != null && loaded != null && held != null) {
        { way ->
            val scale = held.scale
            val resources = context.resources
            val photos = PhotoStore(context.applicationContext.filesDir)
            val name = recipes.bookName()
            printOut(way) {
                val parentTitle = recipe.parent?.let { parent ->
                    try {
                        recipes.get(parent)?.title
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        null
                    }
                }
                RecipeDocument.of(recipe, loaded, equipmentEntries, scale, resources, parentTitle, units) { photos.file(name, it) }
            }
        }
    } else {
        null
    }
    // Where the serving count stands on View, in the root, as laid out — for the floating fork action to keep clear of.
    var servingsShown by remember(recipeId) { mutableStateOf<Rect?>(null) }
    // So does the title with its summary line under it.
    var headingShown by remember(recipeId) { mutableStateOf<Rect?>(null) }
    // A running timer cancelled, on its step or in the bar, is said so with Undo, which puts it back as it was.
    val timerCancelled = rememberTimerCancelled(snackbars)
    // Where the floating fork action's bottom stands, in the root: above the bar of running timers or the navigation
    // bar, by Material's spacing.
    var forkFloor by remember { mutableFloatStateOf(Float.NaN) }
    var forkHeight by remember { mutableIntStateOf(0) }
    // The bar and the tabs under it are one surface: the bar's colour, which turns to its scrolled colour as the recipe
    // scrolls under it, is the tabs' too.
    val barColors = TopAppBarDefaults.topAppBarColors()
    val barState = bar.state
    val scrolledUnder by remember(barState) { derivedStateOf { barState.overlappedFraction > 0.01f } }
    val barColor by animateColorAsState(
        if (scrolledUnder) barColors.scrolledContainerColor else barColors.containerColor,
        Motion.appearing(),
    )
    Scaffold(
        modifier
            .keepScreenAwake(active = tab == CardMode.COOKING || timing)
            .fillMaxSize()
            .nestedScroll(bar.nestedScrollConnection)
            .testTag(CardTags.SCREEN),
        topBar = {
            Column(Modifier.background(barColor)) {
                val viewing = tab == CardMode.VIEW
                TopBar(
                    shown = recipe != null,
                    title = recipe?.title?.ifBlank { stringResource(R.string.common_untitled_recipe) }?.takeIf { !viewing || titleOut },
                    editable = viewing && recipe?.state?.editable == true,
                    scrollBehavior = bar,
                    onBack = onBack,
                    onEdit = onEdit,
                    removal = removal?.takeIf { viewing },
                    onRemove = {
                        if (recipe != null && removal != null) {
                            remove(recipe, removal)
                            onBack()
                        }
                    },
                    marked = marks?.any == true,
                    onClearMarks = clearMarks,
                    listed = listed,
                    onAddToShopping = {
                        if (recipe != null) addToShopping(ListedRecipe(book, recipe.id, shownCount(recipe, held, loaded, language, environment.usualServings.count.value)))
                    },
                    onAddToCollection = if (collections != null) { { collecting = true } } else null,
                    onPrint = printRecipe,
                )
                // The tabs stay under the bar and never scroll with the recipe.
                if (recipe != null) CardTabs(pager)
            }
        },
        // Fork floats at the bottom end of View, above the bar of running timers when it shows and above the navigation
        // bar; it shows its word at the top and while the reader scrolls up, and shrinks to its icon while they scroll
        // down; at large text it stays its icon throughout (v0.38), as the book's action does. It never covers the
        // title, its summary line or the serving count: while one of them stands where Fork floats — at whatever size it
        // is drawn — Fork stands aside, and comes back as soon as the reader scrolls it clear.
        floatingActionButton = {
            if (recipe != null && tab == CardMode.VIEW) {
                val viewScroll = scrolls[CardMode.VIEW.ordinal]
                var expanded by remember(viewScroll) { mutableStateOf(true) }
                val large = isLargeText()
                LaunchedEffect(viewScroll) {
                    var last = viewScroll.value
                    snapshotFlow { viewScroll.value }.collect { now ->
                        if (now == 0 || now < last) expanded = true else if (now > last) expanded = false
                        last = now
                    }
                }
                val least = with(LocalDensity.current) { CardTarget.roundToPx() }
                val clear = forkClearOf(listOf(headingShown, servingsShown), forkFloor, maxOf(forkHeight, least))
                AnimatedVisibility(clear, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
                    ForkAction(expanded && !large, onFork, Modifier.onSizeChanged { forkHeight = it.height })
                }
            }
        },
        snackbarHost = { CardSnackbarHost(snackbars) },
        bottomBar = {
            timersBar?.let {
                CompositionLocalProvider(LocalTimerCancelled provides timerCancelled) {
                    RunningTimersBar(it.timers, it.shown, it.number, onShow = { position ->
                        places.placeOf(CardPlaces.step(position))?.let { step -> scope.launch { scroll.animateScrollTo(step.top) } }
                    })
                }
            }
        },
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { padding ->
        // The keyboard shrinks the content's viewport, so that a focused field is brought above it; the navigation
        // bar does not: the content runs on under it, padded at its end to scroll clear of it.
        // The bar of running timers shortens the viewport instead, so that it covers nothing.
        val keyboard = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
        val barShown = timersBar?.shown?.isNotEmpty() == true
        val density = LocalDensity.current
        val bottom = if (barShown) 0.dp else (padding.calculateBottomPadding() - keyboard).coerceAtLeast(0.dp)
        val direction = LocalLayoutDirection.current
        Box(
            Modifier
                .fillMaxSize()
                // The scaffold floats its action Material's spacing above what its content padding keeps clear below:
                // the bar of running timers, or the navigation bar.
                .onGloballyPositioned { area ->
                    forkFloor = area.boundsInRoot().bottom - with(density) { (padding.calculateBottomPadding() + FAB_SPACING).toPx() }
                }
                .padding(
                    top = padding.calculateTopPadding(),
                    start = padding.calculateStartPadding(direction),
                    end = padding.calculateEndPadding(direction),
                    bottom = if (barShown) maxOf(keyboard, padding.calculateBottomPadding()) else keyboard,
                )
                .consumeWindowInsets(padding),
        ) {
            when {
                shown == CardContent.Missing -> Missing(onBack)
                shown == CardContent.Unreadable -> Unreadable(onRetry = { attempt++ })
                recipe != null && loaded != null && marks != null && held != null -> {
                    HorizontalPager(
                        state = pager,
                        key = { TABS[it].name },
                        modifier = Modifier.fillMaxSize().testTag(CardTags.PAGER),
                    ) { page ->
                        val mode = TABS[page]
                        // A timer cancelled on a step is said in the card's notice, with Undo.
                        CompositionLocalProvider(LocalTimerCancelled provides timerCancelled) {
                            RecipeContent(
                                recipe, loaded, recipes, held, mode, session, marks, onOpenParent, scrolls[page], placesOf[page],
                                shownAgain, units,
                                equipmentEntries = equipmentEntries,
                                kitchen = kitchen,
                                onOpenKitchen = onOpenKitchen,
                                onSetUpKitchen = setUpKitchen,
                                onServingsPlaced = if (mode == CardMode.VIEW) { bounds -> servingsShown = bounds } else null,
                                onHeadingPlaced = if (mode == CardMode.VIEW) { bounds -> headingShown = bounds } else null,
                                // View leaves room for the floating fork action too, so that the recipe's end scrolls clear of it.
                                bottom = if (mode == CardMode.VIEW) bottom + FORK_ROOM else bottom,
                            ) { if (mode == CardMode.VIEW) titleOut = it }
                        }
                    }
                }
                else -> Loading()
            }
            if (preparing) PrintProgress(Modifier.align(Alignment.TopCenter))
        }
    }
    if (collecting && collections != null && recipe != null) {
        AddToCollectionSheet(recipeId, collections) { notice ->
            collecting = false
            notice?.let(say)
        }
    }
}

/**
 * Says a statement in the card's snackbar, replacing whatever it said before (making a clearing of marks final), for a
 * short while and with no action.
 */
@Composable
private fun rememberStatement(snackbars: SnackbarHostState): (String) -> Unit {
    val scope = rememberCoroutineScope()
    return remember(scope, snackbars) {
        { text ->
            scope.launch {
                snackbars.currentSnackbarData?.dismiss()
                snackbars.showSnackbar(text, duration = SnackbarDuration.Short)
            }
        }
    }
}

/**
 * Says "Timer cancelled" with Undo once the reader cancels a running timer, replacing whatever the card said before;
 * Undo runs what puts the timer back as it was ([app.recipefork.app.timer.RecipeTimers.restore]). Nothing is left to
 * make final when the notice goes: the timer is cancelled, and its announcement withdrawn, at the tap.
 */
@Composable
private fun rememberTimerCancelled(snackbars: SnackbarHostState): (undo: () -> Unit) -> Unit {
    val scope = rememberCoroutineScope()
    val said = stringResource(R.string.card_timer_cancelled)
    val undoLabel = stringResource(R.string.card_undo)
    return remember(scope, snackbars, said, undoLabel) {
        { undo ->
            scope.launch {
                snackbars.currentSnackbarData?.dismiss()
                val result = snackbars.showSnackbar(said, actionLabel = undoLabel, duration = SnackbarDuration.Long)
                if (result == SnackbarResult.ActionPerformed) undo()
            }
        }
    }
}

/** The card's tabs in the order of their tabs and pages: a tab's page is its ordinal. */
private val TABS = CardMode.entries

/**
 * The card's two tabs, View and Cooking, M3 primary tabs under the bar: the one shown is selected, and a tap on the
 * other turns the card to it. Each is the card's [CardTarget] tall at least, and announces what choosing it does
 * (A11Y-005).
 */
@Composable
private fun CardTabs(pager: PagerState) {
    val scope = rememberCoroutineScope()
    // On the bar's colour, which the column holding the bar and the tabs draws.
    PrimaryTabRow(selectedTabIndex = pager.currentPage, containerColor = Color.Transparent, modifier = Modifier.testTag(CardTags.TABS)) {
        TABS.forEach { tab ->
            val viewing = tab == CardMode.VIEW
            val label = stringResource(if (viewing) R.string.card_tab_view else R.string.card_tab_cooking)
            val action = stringResource(if (viewing) R.string.card_tab_view_action else R.string.card_tab_cooking_action)
            val choose = { scope.launch { pager.animateScrollToPage(tab.ordinal) } }
            Tab(
                selected = pager.currentPage == tab.ordinal,
                onClick = { choose() },
                text = { Text(label) },
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .heightIn(min = CardTarget)
                    .semantics {
                        onClick(label = action) {
                            choose()
                            true
                        }
                    }
                    .testTag(CardTags.tab(tab)),
            )
        }
    }
}

/**
 * The scale the card reads its recipe at — the count in force and any fixing — one for both tabs, and its change, which
 * the [session] keeps.
 */
@Stable
private class ScaleInForce(
    opening: CardScale,
    private val recipe: RecipeId,
    val ordered: List<Ingredient>,
    private val session: CardSession,
) {
    var scale by mutableStateOf(opening)
        private set

    fun change(to: CardScale) {
        scale = to
        session.holdScale(recipe, HeldScale.of(to, ordered))
    }
}

/**
 * The scale of [recipe] as the card opens it: at the reader's [usual] count, or at the authored one with none set
 * (FLOW-017 (1, 2)) — unless the reader left it at another this session, which it comes back at (FLOW-017 (7),
 * FLOW-012). The scale is the card's alone, kept by the session: a change of count leaves the setting as it is
 * (FLOW-017 (3)). A fixing is of a row as it stood, so a change to that row drops it.
 */
@Composable
private fun rememberScaleInForce(recipe: Recipe, usual: Rational?, session: CardSession): ScaleInForce {
    val authored = recipe.servings?.takeIf { it >= 1 }
    return remember(recipe.id, authored, recipe.ingredients) {
        ScaleInForce(session.openingScale(recipe, usual), recipe.id, recipe.ingredients.sortedBy { it.position }, session)
    }
}

/**
 * The serving count the card shows [recipe] at, [held] with the dictionary [entries]: the count requested, or the one
 * a fixed amount implies while one is fixed; none for a recipe with no count. Before the card has its scale, the count
 * it opens at, with the reader's [usual] count.
 */
private fun shownCount(
    recipe: Recipe,
    held: ScaleInForce?,
    entries: Map<Long, IngredientEntryEntity>?,
    language: InterfaceLanguage,
    usual: Rational?,
): Rational? {
    val authored = recipe.servings?.takeIf { it >= 1 }
    if (held == null || entries == null) return CardScale.opening(authored, usual).requested
    return servingsShown(recipe, held.scale, entries, language)
}

/** The language the card writes its lines and counts in: the interface's. */
@Composable
private fun cardLanguage(): InterfaceLanguage = cardLanguage(LocalConfiguration.current)

/** The language the card writes in under [configuration]: the interface's, as a printed recipe is written too (FLOW-075). */
internal fun cardLanguage(configuration: Configuration): InterfaceLanguage = when (interfaceLanguage(configuration)) {
    RecipeLanguage.RU -> InterfaceLanguage.RUSSIAN
    RecipeLanguage.EN -> InterfaceLanguage.ENGLISH
}

/** The recipe's [timers] and those of them [shown] in the bar; [number] is each step's number by its position. */
private class TimersBar(val timers: RecipeTimers, val shown: List<RunningTimer>, val number: (Int) -> Int)

/**
 * The timers of [recipe] running, or ringing, whose step is not within the card's viewport of [scroll] — none before
 * the card is laid out.
 */
@Composable
private fun runningOutOfSight(recipe: Recipe, scroll: ScrollState, places: CardPlaces): TimersBar {
    val timers = rememberRecipeTimers(recipe.id, recipe.title)
    val shown by remember(timers, scroll, places) {
        derivedStateOf {
            if (scroll.viewportSize == 0) {
                emptyList()
            } else {
                timers.inBar { position ->
                    val step = places.placeOf(CardPlaces.step(position))
                    step != null && step.top < scroll.value + scroll.viewportSize && step.top + step.height > scroll.value
                }
            }
        }
    }
    val positions = remember(recipe.steps) { recipe.steps.map { it.position }.sorted() }
    return TimersBar(timers, shown) { position -> positions.indexOf(position) + 1 }
}

/**
 * Said while the recipe is still being read: nothing for a moment, so that a quick read does not flash, then an
 * indicator in place of the blank.
 */
@Composable
private fun Loading() {
    var showing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(LOADING_INDICATOR_DELAY_MS)
        showing = true
    }
    if (showing) {
        val said = stringResource(R.string.card_loading)
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.semantics { contentDescription = said }.testTag(CardTags.LOADING))
        }
    }
}

/** How long a read may take before the card says it is still reading. */
internal const val LOADING_INDICATOR_DELAY_MS = 300L

/** The word that the recipe could not be read from the device, and the offer to read it again (FLOW-020 (3)). */
@Composable
private fun Unreadable(onRetry: () -> Unit) {
    Column(Modifier.padding(16.dp).testTag(CardTags.UNREADABLE)) {
        Text(stringResource(R.string.card_unreadable), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onRetry,
            modifier = Modifier.heightIn(min = CardTarget).testTag(CardTags.RETRY),
        ) { Text(stringResource(R.string.card_retry)) }
    }
}

/**
 * Said when the book no longer holds the recipe: the app's empty state — a picture, that the recipe is not found, that
 * it is no longer in this recipe book — with one way out, back to the book ([onBack]).
 */
@Composable
private fun Missing(onBack: () -> Unit) {
    EmptyState(
        picture = R.drawable.ic_search_off,
        headline = stringResource(R.string.card_missing_headline),
        sentence = stringResource(R.string.card_missing),
        action = EmptyAction(stringResource(R.string.card_missing_back), R.drawable.ic_arrow_back, CardTags.MISSING_BACK, onBack),
        tag = CardTags.MISSING,
        actionHeight = CardTarget,
    )
}

/** Material's spacing between the floating action and what it floats above. */
private val FAB_SPACING = 16.dp

/**
 * Whether the floating fork action, [height] tall with its bottom at [floor] in the root, stands clear of each of
 * [parts] as it is shown in the root — the title with its summary line, the serving count's row — each clipped to the
 * part in sight, empty where the recipe shows none: their heights do not meet. It sits at their end, where the count's
 * − and + are and a line's last words, so whether drawn with its word or as its icon alone it would cover them. Not
 * clear until every part is known, so that it never shows over one for a moment as the card opens.
 */
internal fun forkClearOf(parts: List<Rect?>, floor: Float, height: Int): Boolean {
    if (floor.isNaN() || parts.any { it == null }) return false
    return parts.filterNotNull().all { part -> part.height <= 0f || part.bottom <= floor - height || part.top >= floor }
}

/**
 * Clears every done mark of the recipe (FLOW-068) from the card's ⋮, on either tab, and says so with Undo: Undo puts
 * back exactly the marks cleared. The clearing is final once the statement goes — by itself, replaced, or as the card
 * is left — or as soon as the reader marks anything again.
 */
@Composable
private fun rememberClearMarks(marks: CardMarks?, snackbars: SnackbarHostState): () -> Unit {
    val scope = rememberCoroutineScope()
    val latest = rememberUpdatedState(marks)
    val cleared = stringResource(R.string.card_marks_cleared)
    val undo = stringResource(R.string.card_undo)
    return remember(scope, snackbars, cleared, undo) {
        {
            val taken = latest.value?.clear().orEmpty()
            if (taken.isNotEmpty()) {
                scope.launch {
                    snackbars.currentSnackbarData?.dismiss()
                    val result = coroutineScope {
                        // A mark made since makes the clearing final: its Undo goes.
                        val marking = launch {
                            snapshotFlow { latest.value?.any == true }.first { it }
                            snackbars.currentSnackbarData?.dismiss()
                        }
                        snackbars.showSnackbar(cleared, actionLabel = undo, duration = SnackbarDuration.Long).also { marking.cancel() }
                    }
                    if (result == SnackbarResult.ActionPerformed) latest.value?.restore(taken)
                }
            }
        }
    }
}

/**
 * The card's snackbar: one statement at a time, above the floating action and the bar of running timers, read by the
 * screen reader as it comes (the host's live region); its action 48 dp tall at least ([SnackbarAction]), and at large
 * text ([isLargeText]) on a line of its own.
 */
@Composable
private fun CardSnackbarHost(snackbars: SnackbarHostState) {
    SnackbarHost(snackbars) { data ->
        Snackbar(
            modifier = Modifier.padding(12.dp).testTag(CardTags.SNACKBAR),
            action = data.visuals.actionLabel?.let { label -> { SnackbarAction(label, data::performAction) } },
            // At large text the action stands on a line of its own, leaving the words the whole width (v0.38).
            actionOnNewLine = isLargeText(),
        ) { Text(data.visuals.message) }
    }
}

/**
 * The screen's bar: back at its start; the recipe's [title] once the content's heading has scrolled out of view, beside
 * back ([BarTitle]) — the content holds it whole; and the recipe's actions at its end — edit while the recipe is
 * editable, then the ⋮, always, as it always holds the shopping-list action. Fork is not here but the screen's floating action ([ForkAction]), so
 * that a title of some thirty characters reads whole on a 360 dp phone. Each icon is one the screen reader names, whose
 * click it announces as what it does (A11Y-005) and whose name a long press shows in a tooltip.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(
    shown: Boolean,
    title: String?,
    editable: Boolean,
    scrollBehavior: TopAppBarScrollBehavior,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    removal: Removal?,
    onRemove: () -> Unit,
    marked: Boolean,
    onClearMarks: () -> Unit,
    listed: Boolean,
    onAddToShopping: () -> Unit,
    onAddToCollection: (() -> Unit)?,
    onPrint: ((PrintWay) -> Unit)?,
) {
    // The last title shown is kept while it fades out, so that it does not vanish before the fade.
    var last by remember { mutableStateOf(title) }
    if (title != null) last = title
    TopAppBar(
        title = {
            AnimatedVisibility(visible = title != null, enter = fadeIn(), exit = fadeOut()) {
                last?.let { BarTitle(it) }
            }
        },
        modifier = Modifier.testTag(CardTags.BAR),
        navigationIcon = {
            BarAction(
                icon = R.drawable.ic_arrow_back,
                label = stringResource(R.string.card_back),
                action = stringResource(R.string.card_back_action),
                tag = CardTags.BACK,
                onClick = onBack,
            )
        },
        actions = {
            if (shown) {
                if (editable) {
                    BarAction(
                        icon = R.drawable.ic_edit,
                        label = stringResource(R.string.card_edit),
                        action = stringResource(R.string.card_edit_action),
                        tag = CardTags.EDIT,
                        onClick = onEdit,
                    )
                }
                CardMenu(removal, onRemove, marked, onClearMarks, listed, onAddToShopping, onAddToCollection, onPrint)
            }
        },
        // On the colour the column holding the bar and the tabs draws: the bar's, turning to its scrolled colour.
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent),
        scrollBehavior = scrollBehavior,
    )
}

/** The card's side and bottom gutters. */
private val GUTTER = 16.dp

/** The space above the card's first part, under the bar. */
private val CONTENT_TOP = 8.dp

/** The space between the card's parts: cover, title, description, servings, ingredients, steps. */
private val PART_GAP = 8.dp

/** The most lines the bar gives the recipe's title. */
private const val BAR_TITLE_LINES = 2

/**
 * The recipe's [title] in the bar: in the bar's own large title style where it fits on one line; otherwise smaller, on
 * up to [BAR_TITLE_LINES] lines, ellipsized at the end of the last.
 */
@Composable
private fun BarTitle(title: String) {
    val measurer = rememberTextMeasurer()
    val large = MaterialTheme.typography.titleLarge
    BoxWithConstraints {
        val width = constraints.maxWidth
        val fits = remember(title, large, width, measurer) {
            !measurer.measure(title, large, maxLines = 1, softWrap = false, constraints = Constraints(maxWidth = width)).hasVisualOverflow
        }
        Text(
            title,
            style = if (fits) large else MaterialTheme.typography.titleMedium,
            maxLines = if (fits) 1 else BAR_TITLE_LINES,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag(CardTags.BAR_TITLE),
        )
    }
}

/**
 * The room the content leaves at its end for the floating fork action: Material's spacing below it, its height — the
 * card's [CardTarget] — and the same spacing again as a gap above it.
 */
private val FORK_ROOM = FAB_SPACING + CardTarget + FAB_SPACING

/**
 * The card's floating fork action (FORK-001), on every recipe shown: its icon and its word while [expanded], its icon
 * alone otherwise; the screen reader reads the word either way and announces its click as what it does (A11Y-005). It
 * is at least the card's [CardTarget] tall.
 */
@Composable
private fun ForkAction(expanded: Boolean, onFork: () -> Unit, modifier: Modifier = Modifier) {
    val word = stringResource(R.string.card_fork)
    val action = stringResource(R.string.card_fork_action)
    ExtendedFloatingActionButton(
        text = { Text(word) },
        icon = { Icon(painterResource(R.drawable.ic_fork), contentDescription = null) },
        onClick = onFork,
        expanded = expanded,
        modifier = modifier
            .heightIn(min = CardTarget)
            .semantics {
                text = AnnotatedString(word)
                onClick(label = action, action = null)
            }
            .testTag(CardTags.FORK),
    )
}

/**
 * The recipe as the tab [mode] shows it, scrolled by [scroll]: View the whole of it, Cooking only what cooking needs —
 * the equipment, the serving count, the ingredients and the steps.
 */
@Composable
private fun RecipeContent(
    recipe: Recipe,
    entries: Map<Long, IngredientEntryEntity>,
    recipes: RecipeRepository,
    held: ScaleInForce,
    mode: CardMode,
    session: CardSession,
    marks: CardMarks,
    onOpenParent: (RecipeId) -> Unit,
    scroll: ScrollState,
    places: CardPlaces,
    shownAgain: ShownAgain,
    units: UnitSystem,
    equipmentEntries: Map<Long, DictionaryEntry>,
    kitchen: Kitchen,
    onOpenKitchen: ((Long) -> Unit)?,
    onSetUpKitchen: (() -> Unit)?,
    onServingsPlaced: ((Rect) -> Unit)?,
    onHeadingPlaced: ((Rect) -> Unit)?,
    bottom: Dp,
    onTitleOut: (Boolean) -> Unit,
) {
    val language = cardLanguage()
    val viewing = mode == CardMode.VIEW
    // A card brought to a timer's step goes there on Cooking, not to the place it was left at (FLOW-013 (4)).
    val toStep = !viewing && LocalStepToShow.current?.pending?.collectAsState()?.value?.recipeId == recipe.id.value
    KeptPlace(recipe.id, mode, scroll, places, session, restore = !toStep)
    val titleOut by rememberUpdatedState(onTitleOut)
    if (viewing) {
        LaunchedEffect(scroll, places) {
            snapshotFlow { places.placeOf(CardPlaces.TITLE_KEY)?.let { scroll.value >= it.top + it.height } == true }
                .collect { titleOut(it) }
        }
    }
    CompositionLocalProvider(LocalCardPlaces provides places) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .onGloballyPositioned(places::content)
                // A tight top and gaps, so that more of the ingredients is in sight as the card opens; Cooking's first part
                // stands right under the tabs, whose line sets it apart.
                .padding(start = GUTTER, top = if (viewing) CONTENT_TOP else 0.dp, end = GUTTER, bottom = GUTTER)
                .padding(bottom = bottom)
                .testTag(CardTags.CONTENT),
            verticalArrangement = Arrangement.spacedBy(PART_GAP),
        ) {
            if (viewing) {
                Cover(recipe, recipes.bookName(), cardPlace(CardPlaces.COVER_KEY))
                Column(onHeadingPlaced?.let { placed -> Modifier.onGloballyPositioned { placed(it.boundsInRoot()) } } ?: Modifier) {
                    // The title whole, on as many lines as it takes (A11Y-004); the bar repeats it once it scrolls away.
                    Text(
                        recipe.title.ifBlank { stringResource(R.string.common_untitled_recipe) },
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = cardPlace(CardPlaces.TITLE_KEY).semantics { heading() }.testTag(CardTags.TITLE),
                    )
                    // Right under the title, as its subtitle, muted: the counts of ingredients and steps and the timers'
                    // time together, for deciding.
                    RecipeSummaryLine(remember(recipe, language) { RecipeSummary.of(recipe, language) })
                }
                // A found recipe's author as its file states it, marked every time it is shown (FLOW-064 (3)).
                recipe.statedAuthor?.let {
                    Text(
                        stringResource(R.string.file_stated_author, it),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.testTag(CardTags.STATED_AUTHOR),
                    )
                }
                recipe.parent?.let { Provenance(it, recipe.statedParent, recipes, onOpenParent) }
                // The author's description stands with the title block it tells about, before the equipment and the
                // ingredients, the card's gap from either.
                if (recipe.description.isNotBlank()) {
                    Text(
                        recipe.description,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = cardPlace(CardPlaces.DESCRIPTION_KEY).testTag(CardTags.DESCRIPTION),
                    )
                }
            }
            // The equipment stands above the serving count and the ingredients, as the editor lists it before them; an
            // item naming an entry the kitchen lacks says so (free text never does).
            val equipment = remember(recipe.equipment, equipmentEntries, language, kitchen) {
                val reading = if (language == InterfaceLanguage.RUSSIAN) RecipeLanguage.RU else RecipeLanguage.EN
                recipe.equipment.map { item ->
                    val entry = item.dictionaryEntry
                    ShownEquipment(EquipmentNames.shown(item, equipmentEntries, reading), entry?.takeIf(kitchen::lacks))
                }
            }
            EquipmentList(equipment, marks, mode, shownAgain.equipment, onOpenKitchen, onSetUpKitchen)
            val authored = recipe.servings?.takeIf { it >= 1 }
            val ordered = held.ordered
            val scale = held.scale
            val list = remember(ordered, entries, language, authored, scale, units) {
                ingredientList(ordered, entries, language, authored, scale, units)
            }
            val references = remember(ordered, list, language, entries, units) { CardReferences.of(ordered, list, language, entries, units) }
            // A recipe that shows no count says so, so that nothing waits on where the count stands.
            if (list.servingCount == null && onServingsPlaced != null) LaunchedEffect(Unit) { onServingsPlaced(Rect.Zero) }
            // On Cooking the serving count stands right on the ingredients' tinted panel, with no gap: six lines are in
            // sight as the card opens on a 360 × 640 dp phone.
            CountAndIngredients(packed = !viewing) {
                list.servingCount?.let { count ->
                    ServingsControl(
                        modifier = cardPlace(CardPlaces.SERVINGS_KEY)
                            .then(onServingsPlaced?.let { placed -> Modifier.onGloballyPositioned { placed(it.boundsInRoot()) } } ?: Modifier),
                        count = RequestedServings.text(count.value, language),
                        approximate = count.approximate,
                        canDecrease = count.value > RequestedServings.MIN,
                        // A change of count walks from the count shown, and releases every fixing (SCALE-016).
                        onDecrease = { held.change(scale.request(RequestedServings.decreased(count.value))) },
                        onIncrease = { held.change(scale.request(RequestedServings.increased(count.value))) },
                    )
                }
                IngredientsList(
                    ordered,
                    list,
                    scale.fixings,
                    language,
                    onFix = { held.change(scale.fix(it)) },
                    onRelease = { held.change(scale.release(it)) },
                    marks = marks,
                    groups = recipe.ingredientGroups,
                    mode = mode,
                    shownAgain = shownAgain.ingredients,
                )
            }
            // On Cooking a step marked done brings the next into view, unless the reader is scrolling the tab themselves.
            StepsList(
                recipe.steps, recipe.id, recipe.title, references,
                StepMediaOnCard(recipe, recipes.bookName()), marks, mode,
                readerScrolling = { scroll.isScrollInProgress },
                coveredBelow = bottom,
                readerInteractions = scroll.interactionSource,
            )
        }
    }
}

/** Whether the reader showed again the lists Cooking folded once every line of them was marked: each list its own. */
private class ShownAgain(val equipment: MutableState<Boolean>, val ingredients: MutableState<Boolean>)

/** The serving count and the ingredients: as parts of the card, the card's gap between them, or [packed], none. */
@Composable
private fun CountAndIngredients(packed: Boolean, parts: @Composable () -> Unit) {
    if (packed) Column { parts() } else parts()
}

/**
 * Keeps the reader's place on the [tab] of the card of [recipe] for the session: the part first at the top of [scroll]
 * whenever they move. A card coming back to a place held for it is brought there, and kept there while what lies above it is
 * still being laid out — a cover, a photograph, a timer that ended — until the reader scrolls themselves.
 */
@Composable
private fun KeptPlace(recipe: RecipeId, tab: CardMode, scroll: ScrollState, places: CardPlaces, session: CardSession, restore: Boolean) {
    // Decided once, as the card is drawn: a step shown later moves the reader as their own scroll does.
    val restoring = remember(recipe) { restore }
    LaunchedEffect(recipe, tab, scroll, places, session) {
        val anchor = if (restoring) session.heldAnchor(recipe, tab) else null
        if (anchor != null) {
            // Every scroll this has made: a move seen late, after the next target was set, is still its own.
            val ours = mutableSetOf(scroll.value)
            coroutineScope {
                val pin = launch {
                    snapshotFlow { places.scrollOf(anchor)?.coerceIn(0, scroll.maxValue) }.filterNotNull().collect { target ->
                        ours += target
                        scroll.scrollTo(target)
                    }
                }
                // Any move but this one's is the reader's own.
                snapshotFlow { scroll.value }.first { it !in ours }
                pin.cancel()
            }
        }
        snapshotFlow { places.anchorAt(scroll.value) }.collect { session.holdAnchor(recipe, it, tab) }
    }
}

/** What the provenance area knows of the parent: nothing yet, its title, or that the book does not hold it. */
private sealed interface ParentContent {
    data object Loading : ParentContent
    data object Unavailable : ParentContent
    data class Held(val title: String) : ParentContent
}

/**
 * The provenance area: the one direct parent, named by its title (FORK-003 (3), FORK-004 (1), FLOW-065). While the
 * book holds the parent, tapping it opens the parent's card; a parent the book does not hold is said to be
 * unavailable and opens nothing. No descendant is ever listed or counted (FORK-004 (3)).
 *
 * A parent the recipe names in text, [stated] — the one a found recipe's file states, or the found recipe a fork was
 * made from — is named by that title, and by that author marked as not verified (FLOW-065 (4, 5)), held or not.
 */
@Composable
private fun Provenance(parent: RecipeId, stated: StatedParent?, recipes: RecipeRepository, onOpenParent: (RecipeId) -> Unit) {
    val content by remember(parent, recipes) {
        recipes.observe(parent).map { it?.let { held -> ParentContent.Held(held.title) } ?: ParentContent.Unavailable }
    }.collectAsState(initial = ParentContent.Loading)
    val resources = LocalResources.current
    when (val shown = content) {
        ParentContent.Loading -> Unit
        ParentContent.Unavailable -> Text(
            provenanceText(resources, stated, held = null),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag(CardTags.PROVENANCE),
        )
        is ParentContent.Held -> Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = CardTarget)
                .clickable(
                    onClickLabel = stringResource(R.string.card_parent_open_action),
                    role = Role.Button,
                    onClick = { onOpenParent(parent) },
                )
                .testTag(CardTags.PROVENANCE),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                provenanceText(resources, stated, shown.title),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/**
 * What the provenance area says in [resources]' language: the parent the book holds by its title [held] — or by the
 * [stated] one's, with its author marked as not verified — or, [held] null, that the book does not hold it. The card's
 * line and a printed recipe's (FLOW-075).
 */
internal fun provenanceText(resources: Resources, stated: StatedParent?, held: String?): String {
    val untitled = resources.getString(R.string.common_untitled_recipe)
    val author = stated?.nickname?.takeIf { it.isNotBlank() }
    if (held == null) {
        return when {
            stated == null -> resources.getString(R.string.card_parent_unavailable)
            author == null -> resources.getString(R.string.card_parent_named_unavailable, stated.title.ifBlank { untitled })
            else -> resources.getString(R.string.card_parent_stated_unavailable, stated.title.ifBlank { untitled }, author)
        }
    }
    val title = (stated?.title ?: held).ifBlank { untitled }
    return if (author == null) resources.getString(R.string.card_parent, title) else resources.getString(R.string.card_parent_stated, title, author)
}

/** The space between a section's heading and its parts. */
internal val SECTION_GAP = 8.dp

/** One part of the recipe under its heading, [gap] between the heading and each part; the lists of ingredients and of steps are each one. */
@Composable
internal fun Section(@StringRes title: Int, tag: String, gap: Dp = SECTION_GAP, lines: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().testTag(tag), verticalArrangement = Arrangement.spacedBy(gap)) {
        Text(
            stringResource(title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        lines()
    }
}

/**
 * The serving count and the two actions that walk it (SCALE-011): the count as the reader requested it, never
 * marked approximate, or, while a fixed ingredient sets the scale, the count it implies marked as approximate
 * (SCALE-002, SCALE-008); beside it the fewer and more actions as one connected group [ − | + ], drawn smaller than
 * their targets. At ¼ the fewer action is disabled and does nothing. The settings' usual count walks with it too, its
 * parts [tagged][tags] and its actions announced as [fewerAction] and [moreAction] there.
 */
@Composable
internal fun ServingsControl(
    modifier: Modifier,
    count: String,
    approximate: Boolean,
    canDecrease: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    tags: ServingsTags = ServingsTags(CardTags.SERVINGS, CardTags.SERVINGS_FEWER, CardTags.SERVINGS_MORE),
    @StringRes fewerAction: Int = R.string.card_servings_fewer_action,
    @StringRes moreAction: Int = R.string.card_servings_more_action,
) {
    // Each step of the count answers with a light tick; at ¼ the fewer action is disabled, so a refused step gives none.
    val haptics = rememberHaptics()
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(if (approximate) R.string.card_servings_approximate else R.string.card_servings, count),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite }.testTag(tags.count),
        )
        Spacer(Modifier.width(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(CONNECTED_GAP)) {
            // A fully round outer side is the connected group's shape, at any size; the inner corners are the theme's.
            val outer = CornerSize(50)
            val inner = MaterialTheme.shapes.small
            StepAction(
                icon = R.drawable.ic_servings_fewer,
                name = stringResource(R.string.card_servings_fewer),
                action = stringResource(fewerAction),
                shape = inner.copy(topStart = outer, bottomStart = outer),
                side = Alignment.CenterEnd,
                enabled = canDecrease,
                onClick = {
                    haptics.perform(Haptic.TICK)
                    onDecrease()
                },
                tag = tags.fewer,
            )
            StepAction(
                icon = R.drawable.ic_servings_more,
                name = stringResource(R.string.card_servings_more),
                action = stringResource(moreAction),
                shape = inner.copy(topEnd = outer, bottomEnd = outer),
                side = Alignment.CenterStart,
                enabled = true,
                onClick = {
                    haptics.perform(Haptic.TICK)
                    onIncrease()
                },
                tag = tags.more,
            )
        }
    }
}

/** The test tags of a [ServingsControl]'s count and its − and +. */
internal class ServingsTags(val count: String, val fewer: String, val more: String)

/** The seam between the halves of a connected group: they read as one control, yet each half as its own target. */
private val CONNECTED_GAP = 2.dp

/**
 * How large a half of the − | + group is drawn, in each direction: smaller than its target, which stays the card's
 * [CardTarget] square around it (A11Y-003 — the target is the rule, not the drawing).
 */
internal val STEP_DRAWING = 40.dp

/**
 * One half of the − | + group: a target the card's [CardTarget] square (A11Y-003, A11Y-007), tagged [tag], that
 * answers a touch anywhere in it; within it, on the [side] where it meets the other half, a tonal drawing
 * [STEP_DRAWING] square, its colours the theme's and its [shape] rounded outside and joined to the other half inside.
 * Its one click — by touch or by the screen reader — is named by [name] and announced as [action] (A11Y-005).
 */
@Composable
private fun StepAction(
    @DrawableRes icon: Int,
    name: String,
    action: String,
    shape: Shape,
    side: Alignment,
    enabled: Boolean,
    onClick: () -> Unit,
    tag: String,
) {
    val interaction = remember { MutableInteractionSource() }
    val colors = IconButtonDefaults.filledTonalIconButtonColors()
    val content = if (enabled) colors.contentColor else colors.disabledContentColor
    Box(
        Modifier
            .size(CardTarget)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClickLabel = action,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { contentDescription = name }
            .testTag(tag),
        contentAlignment = side,
    ) {
        // The press shows on the drawing, in its content colour, wherever in the target the touch lands.
        Box(
            Modifier
                .size(STEP_DRAWING)
                .clip(shape)
                .background(if (enabled) colors.containerColor else colors.disabledContainerColor)
                .indication(interaction, ripple(color = content))
                .testTag(CardTags.stepDrawing(tag)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(icon),
                contentDescription = null,
                tint = content,
            )
        }
    }
}

/**
 * An icon button of the bar, the card's [CardTarget] square (A11Y-003, A11Y-007): the screen reader speaks its [label]
 * and announces its click as [action] (A11Y-005); a long press shows the [label] in a tooltip, so that its meaning is
 * never left to the icon alone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BarAction(@DrawableRes icon: Int, label: String, action: String, tag: String, onClick: () -> Unit) {
    val tooltip = rememberTooltipState()
    val scope = rememberCoroutineScope()
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
        tooltip = { PlainTooltip(Modifier.testTag(CardTags.tooltip(tag))) { Text(label) } },
        state = tooltip,
        // The long press is the button's own, so that the button stays one element for the screen reader.
        enableUserInput = false,
    ) {
        Box(
            Modifier
                .size(CardTarget)
                .clip(CircleShape)
                .combinedClickable(
                    role = Role.Button,
                    onClickLabel = action,
                    onClick = onClick,
                    onLongClickLabel = stringResource(R.string.card_action_show_name),
                    onLongClick = { scope.launch { tooltip.show() } },
                )
                .testTag(tag),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(icon), contentDescription = label)
        }
    }
}

/**
 * The card's ⋮, after its other actions, always shown, in the order every ⋮ of a recipe keeps (v0.38, T-269): first
 * "Add to collection" where the card is given the book's collections ([onAddToCollection], v0.36), then the
 * shopping-list action — "Add to shopping list", or "Update in shopping list" while the recipe is [listed] (FLOW-074) —
 * then, once the recipe is read with its scale, Print and Share as PDF ([onPrint], FLOW-075), then Clear marks while
 * the recipe has done marks (FLOW-068), which takes every mark of the recipe away at once, and the recipe's removal —
 * Delete for an own recipe in a deletable state, Remove for a found one — in the theme's error colour. Each item is the
 * card's [CardTarget] tall.
 */
@Composable
private fun CardMenu(
    removal: Removal?,
    onRemove: () -> Unit,
    marked: Boolean,
    onClearMarks: () -> Unit,
    listed: Boolean,
    onAddToShopping: () -> Unit,
    onAddToCollection: (() -> Unit)?,
    onPrint: ((PrintWay) -> Unit)?,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        BarAction(
            icon = R.drawable.ic_row_menu,
            label = stringResource(R.string.card_menu),
            action = stringResource(R.string.card_menu_action),
            tag = CardTags.MENU,
            onClick = { expanded = !expanded },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (onAddToCollection != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.collection_sheet_menu)) },
                    onClick = {
                        expanded = false
                        onAddToCollection()
                    },
                    modifier = Modifier.heightIn(min = CardTarget).testTag(CardTags.COLLECTION),
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(if (listed) R.string.card_shopping_update else R.string.shopping_add)) },
                onClick = {
                    expanded = false
                    onAddToShopping()
                },
                modifier = Modifier.heightIn(min = CardTarget).testTag(CardTags.SHOPPING),
            )
            if (onPrint != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.print_menu_print)) },
                    onClick = {
                        expanded = false
                        onPrint(PrintWay.PRINT)
                    },
                    modifier = Modifier.heightIn(min = CardTarget).testTag(CardTags.PRINT),
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.print_menu_share)) },
                    onClick = {
                        expanded = false
                        onPrint(PrintWay.SHARE)
                    },
                    modifier = Modifier.heightIn(min = CardTarget).testTag(CardTags.SHARE_PDF),
                )
            }
            if (marked) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.card_clear_marks)) },
                    onClick = {
                        expanded = false
                        onClearMarks()
                    },
                    modifier = Modifier.heightIn(min = CardTarget).testTag(CardTags.CLEAR_MARKS),
                )
            }
            if (removal != null) DropdownMenuItem(
                text = {
                    Text(stringResource(if (removal == Removal.DELETE) R.string.library_entry_delete else R.string.library_entry_remove))
                },
                onClick = {
                    expanded = false
                    onRemove()
                },
                // Taking a recipe out of the book is destructive: the item says so in the theme's error colour.
                colors = MenuDefaults.itemColors(textColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.heightIn(min = CardTarget)
                    .testTag(if (removal == Removal.DELETE) CardTags.DELETE else CardTags.REMOVE),
            )
        }
    }
}

/**
 * Takes a recipe out of the book from its card by the book's own path, asking nothing (FLOW-053): an own one deleted,
 * a found one removed, with its timers and its done [marks]; [onRemoved] is told of it, to say it with Undo.
 */
@Composable
private fun rememberRemoval(
    recipes: RecipeRepository,
    marks: DoneMarks,
    onRemoved: (PendingRemoval) -> Unit,
): (Recipe, Removal) -> Unit {
    val timers = rememberCardTimers()
    val files = rememberRecipeImport(recipes)
    val told by rememberUpdatedState(onRemoved)
    return remember(recipes, marks, timers, files) {
        { recipe, removal ->
            when (removal) {
                Removal.DELETE -> deleteRecipe(recipes, timers, marks, recipe.id) { told(it) }
                Removal.REMOVE -> removeFound(files, timers, marks, recipe.id) { told(it) }
            }
        }
    }
}

/**
 * Said when the reader forks a recipe of which they already hold an unchanged fork (FLOW-015): they may open that
 * fork, or make another anyway; dismissing it makes nothing.
 */
@Composable
fun HeldForkDialog(onOpen: () -> Unit, onAnother: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(CardTags.FORK_HELD),
        text = { Text(stringResource(R.string.card_fork_held)) },
        confirmButton = {
            TextButton(onClick = onOpen, modifier = Modifier.heightIn(min = CardTarget).widthIn(min = CardTarget).testTag(CardTags.FORK_HELD_OPEN)) {
                Text(stringResource(R.string.card_fork_held_open))
            }
        },
        dismissButton = {
            TextButton(onClick = onAnother, modifier = Modifier.heightIn(min = CardTarget).widthIn(min = CardTarget).testTag(CardTags.FORK_HELD_ANOTHER)) {
                Text(stringResource(R.string.card_fork_held_another))
            }
        },
    )
}

object CardTags {
    const val FORK_HELD = "card-fork-held"
    const val FORK_HELD_OPEN = "card-fork-held-open"
    const val FORK_HELD_ANOTHER = "card-fork-held-another"
    const val SCREEN = "card"
    const val TABS = "card-tabs"
    const val PAGER = "card-pager"
    const val BAR = "card-bar"
    const val BACK = "card-back"
    const val BAR_TITLE = "card-bar-title"
    const val LOADING = "card-loading"
    const val EDIT = "card-edit"
    const val FORK = "card-fork"
    const val MENU = "card-menu"
    const val DELETE = "card-delete"
    const val REMOVE = "card-remove"
    const val CLEAR_MARKS = "card-clear-marks"
    const val SHOPPING = "card-shopping"
    const val COLLECTION = "card-collection"
    const val PRINT = "card-print"
    const val SHARE_PDF = "card-share-pdf"
    const val PROVENANCE = "card-provenance"
    const val STATED_AUTHOR = "card-stated-author"
    const val SUMMARY = "card-summary"
    const val MISSING = "card-missing"
    const val MISSING_BACK = "card-missing-back"
    const val SNACKBAR = "card-snackbar"
    const val UNREADABLE = "card-unreadable"
    const val RETRY = "card-retry"
    const val CONTENT = "card-content"
    const val TITLE = "card-title"
    const val COVER = "card-cover"
    const val COVER_UNAVAILABLE = "card-cover-unavailable"
    const val SERVINGS = "card-servings"
    const val SERVINGS_FEWER = "card-servings-fewer"
    const val SERVINGS_MORE = "card-servings-more"
    const val EQUIPMENT = "card-equipment"
    const val INGREDIENTS = "card-ingredients"
    const val PROPORTIONS = "card-proportions"
    const val STEPS = "card-steps"
    const val DESCRIPTION = "card-description"
    const val FIX = "card-fix"
    const val FIX_FIELD = "card-fix-field"
    const val FIX_PROBLEM = "card-fix-problem"
    const val FIX_CONFIRM = "card-fix-confirm"
    const val FIX_RELEASE = "card-fix-release"
    const val FIX_CANCEL = "card-fix-cancel"
    /** The tab that shows the card in [mode]. */
    fun tab(mode: CardMode) = "card-tab-${mode.name.lowercase()}"
    /** The tooltip the bar's action tagged [action] shows on a long press. */
    fun tooltip(action: String) = "$action-tooltip"
    /** The drawing within a [ServingsControl] half tagged [half]. */
    fun stepDrawing(half: String) = "$half-drawing"
    fun fixUnit(unit: QuantityUnit) = "card-fix-unit-${unit.name.lowercase()}"
    fun ingredient(position: Int) = "card-ingredient-$position"
    /** The item of equipment at [index] of the recipe's equipment. */
    fun equipment(index: Int) = "card-equipment-$index"
    /** The amount of the ingredient line at [position], which opens its fixing control. */
    fun fixAmount(position: Int) = "card-ingredient-$position-amount"
    fun step(position: Int) = "card-step-$position"
    fun stepPhoto(position: Int) = "card-step-$position-photo"
    fun stepVideo(position: Int) = "card-step-$position-video"
    fun stepVideoControl(position: Int) = "card-step-$position-video-control"
    fun stepMediaUnavailable(position: Int) = "card-step-$position-media-unavailable"
}
