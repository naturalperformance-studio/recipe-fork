package app.recipefork.app.card

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
import androidx.compose.material3.FilledTonalIconButton
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
import app.recipefork.app.library.DELETABLE
import app.recipefork.app.library.PendingRemoval
import app.recipefork.app.library.deleteRecipe
import app.recipefork.app.library.rememberRecipeImport
import app.recipefork.app.library.removeFound
import app.recipefork.app.timer.rememberCardTimers
import androidx.compose.material3.Button
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
 * View is one scrolling page: the cover, the title with its summary, the provenance, the serving count, the ingredients
 * in order, the steps numbered, the description. A part the recipe does not hold is left out with no heading and no
 * placeholder (FLOW-019 (1–3)), so a recipe holding only a title still reads as a whole recipe. Its bar holds back, the
 * title once it has scrolled away, and, only while the recipe is editable, the edit action that opens the editor on it
 * (FLOW-029 (2–4)); its ⋮ holds the recipe's removal — Delete for an own recipe in a deletable state, Remove for a found
 * one — which takes it out of the book at once, asking nothing, and closes the card to the book, which says so with
 * Undo ([onRemoved], FLOW-053). Fork, its one primary action, floats at the bottom end on every recipe, editable or
 * frozen (FORK-001, FORK-007) — a found recipe is held frozen, so its card offers fork and never edit (FLOW-064 (2)).
 *
 * Cooking holds nothing that distracts from cooking: no cover, no summary, no description, no fork, no edit. Its bar
 * holds back and the title, and a ⋮ only while the recipe has done marks, holding their clearing (FLOW-068); its page
 * the serving count, the ingredients and the steps, in lines the card's [CardTarget] tall that mark done, amounts fixed
 * from their lines, timers started from their steps. The screen stays on while it is shown (FLOW-014), and on either
 * tab while one of the recipe's timers runs.
 *
 * Nothing inside the recipe edits it; the serving count's − and +, which change how the recipe is read and never the
 * recipe (SCALE-001, SCALE-011), are one control in the content, shared by both tabs, and a fork's provenance, which
 * opens its parent's card, the other. It opens each time at the reader's usual count, or at the authored count while
 * none is set (FLOW-017).
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
    session: CardSession = remember { TextCardSession.inMemory() },
    onRemoved: (PendingRemoval) -> Unit = PendingRemoval::finish,
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
            environment.equipment.equipment()
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
    Scaffold(
        modifier
            .keepScreenAwake(active = tab == CardMode.COOKING || timing)
            .fillMaxSize()
            .nestedScroll(bar.nestedScrollConnection)
            .testTag(CardTags.SCREEN),
        topBar = {
            Column {
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
                    marked = !viewing && marks?.any == true,
                    onClearMarks = { marks?.clear() },
                )
                // The tabs stay under the bar and never scroll with the recipe.
                if (recipe != null) CardTabs(pager)
            }
        },
        // Fork floats at the bottom end of View, above the bar of running timers when it shows and above the navigation
        // bar; it shows its word at the top and while the reader scrolls up, and shrinks to its icon while they scroll
        // down.
        floatingActionButton = {
            if (recipe != null && tab == CardMode.VIEW) {
                val viewScroll = scrolls[CardMode.VIEW.ordinal]
                var expanded by remember(viewScroll) { mutableStateOf(true) }
                LaunchedEffect(viewScroll) {
                    var last = viewScroll.value
                    snapshotFlow { viewScroll.value }.collect { now ->
                        if (now == 0 || now < last) expanded = true else if (now > last) expanded = false
                        last = now
                    }
                }
                ForkAction(expanded, onFork)
            }
        },
        bottomBar = {
            timersBar?.let {
                RunningTimersBar(it.timers, it.shown, it.number, onShow = { position ->
                    places.placeOf(CardPlaces.step(position))?.let { step -> scope.launch { scroll.animateScrollTo(step.top) } }
                })
            }
        },
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { padding ->
        // The keyboard shrinks the content's viewport, so that a focused field is brought above it; the navigation
        // bar does not: the content runs on under it, padded at its end to scroll clear of it.
        // The bar of running timers shortens the viewport instead, so that it covers nothing.
        val keyboard = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
        val barShown = timersBar?.shown?.isNotEmpty() == true
        val bottom = if (barShown) 0.dp else (padding.calculateBottomPadding() - keyboard).coerceAtLeast(0.dp)
        val direction = LocalLayoutDirection.current
        Box(
            Modifier
                .fillMaxSize()
                .padding(
                    top = padding.calculateTopPadding(),
                    start = padding.calculateStartPadding(direction),
                    end = padding.calculateEndPadding(direction),
                    bottom = if (barShown) maxOf(keyboard, padding.calculateBottomPadding()) else keyboard,
                )
                .consumeWindowInsets(padding),
        ) {
            val loaded = entries
            when {
                shown == CardContent.Missing -> Text(
                    stringResource(R.string.card_missing),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(16.dp).testTag(CardTags.MISSING),
                )
                shown == CardContent.Unreadable -> Unreadable(onRetry = { attempt++ })
                recipe != null && loaded != null && marks != null -> {
                    // Read once, not collected: the usual count only sets where a card opens, never moves an open one.
                    val scale = rememberScaleInForce(recipe, environment.usualServings.count.value, session)
                    HorizontalPager(
                        state = pager,
                        key = { TABS[it].name },
                        modifier = Modifier.fillMaxSize().testTag(CardTags.PAGER),
                    ) { page ->
                        val mode = TABS[page]
                        RecipeContent(
                            recipe, loaded, recipes, scale, mode, session, marks, onOpenParent, scrolls[page], placesOf[page],
                            shownAgain,
                            equipmentEntries = equipmentEntries,
                            kitchen = kitchen,
                            // View leaves room for the floating fork action too, so that the recipe's end scrolls clear of it.
                            bottom = if (mode == CardMode.VIEW) bottom + FORK_ROOM else bottom,
                        ) { if (mode == CardMode.VIEW) titleOut = it }
                    }
                }
                else -> Loading()
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
    PrimaryTabRow(selectedTabIndex = pager.currentPage, modifier = Modifier.testTag(CardTags.TABS)) {
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
        val ordered = recipe.ingredients.sortedBy { it.position }
        val opening = session.heldScale(recipe.id)?.restored(authored, ordered, usual) ?: CardScale.opening(authored, usual)
        ScaleInForce(opening, recipe.id, ordered, session)
    }
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
 * The screen's bar: back at its start; the recipe's [title] once the content's heading has scrolled out of view, beside
 * back ([BarTitle]) — the content holds it whole; and the recipe's actions at its end — edit while the recipe is
 * editable, then the ⋮ while it holds anything. Fork is not here but the screen's floating action ([ForkAction]), so
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
                if (removal != null || marked) CardMenu(removal, onRemove, marked, onClearMarks)
            }
        },
        scrollBehavior = scrollBehavior,
    )
}

/** The card's side and bottom gutters. */
private val GUTTER = 16.dp

/** The space above the card's first part, under the bar. */
private val CONTENT_TOP = 8.dp

/** The space between the card's parts: cover, title, servings, ingredients, steps. */
private val PART_GAP = 8.dp

/** The space between the steps and the description, which reads apart from them. */
private val DESCRIPTION_GAP = 16.dp

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

/** The room the content leaves at its end for the floating fork action: its height and its spacing, and a gap. */
private val FORK_ROOM = 88.dp

/**
 * The card's floating fork action (FORK-001), on every recipe shown: its icon and its word while [expanded], its icon
 * alone otherwise; the screen reader reads the word either way and announces its click as what it does (A11Y-005). It
 * is at least the card's [CardTarget] tall.
 */
@Composable
private fun ForkAction(expanded: Boolean, onFork: () -> Unit) {
    val word = stringResource(R.string.card_fork)
    val action = stringResource(R.string.card_fork_action)
    ExtendedFloatingActionButton(
        text = { Text(word) },
        icon = { Icon(painterResource(R.drawable.ic_fork), contentDescription = null) },
        onClick = onFork,
        expanded = expanded,
        modifier = Modifier
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
    equipmentEntries: Map<Long, DictionaryEntry>,
    kitchen: Kitchen,
    bottom: Dp,
    onTitleOut: (Boolean) -> Unit,
) {
    val language = when (interfaceLanguage(LocalConfiguration.current)) {
        RecipeLanguage.RU -> InterfaceLanguage.RUSSIAN
        RecipeLanguage.EN -> InterfaceLanguage.ENGLISH
    }
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
                Column {
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
            }
            // The equipment stands above the serving count and the ingredients, as the editor lists it before them; an
            // item naming an entry the kitchen lacks says so (free text never does).
            val equipment = remember(recipe.equipment, equipmentEntries, language, kitchen) {
                val reading = if (language == InterfaceLanguage.RUSSIAN) RecipeLanguage.RU else RecipeLanguage.EN
                recipe.equipment.map { item ->
                    ShownEquipment(EquipmentNames.shown(item, equipmentEntries, reading), item.dictionaryEntry?.let(kitchen::lacks) == true)
                }
            }
            EquipmentList(equipment, marks, mode, shownAgain.equipment)
            val authored = recipe.servings?.takeIf { it >= 1 }
            val ordered = held.ordered
            val scale = held.scale
            val list = ingredientList(ordered, entries, language, authored, scale)
            // On Cooking the serving count stands right on the ingredients' tinted panel, with no gap: six lines are in
            // sight as the card opens on a 360 × 640 dp phone.
            CountAndIngredients(packed = !viewing) {
                list.servingCount?.let { count ->
                    ServingsControl(
                        modifier = cardPlace(CardPlaces.SERVINGS_KEY),
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
                recipe.steps, recipe.id, recipe.title, CardReferences.of(ordered, list, language, entries),
                StepMediaOnCard(recipe, recipes.bookName()), marks, mode,
                readerScrolling = { scroll.isScrollInProgress },
                coveredBelow = bottom,
            )
            if (viewing && recipe.description.isNotBlank()) {
                // The description stands further apart from the steps than the parts above stand from each other.
                Text(
                    recipe.description,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = DESCRIPTION_GAP - PART_GAP).then(cardPlace(CardPlaces.DESCRIPTION_KEY)).testTag(CardTags.DESCRIPTION),
                )
            }
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
    val untitled = stringResource(R.string.common_untitled_recipe)
    val author = stated?.nickname?.takeIf { it.isNotBlank() }
    when (val shown = content) {
        ParentContent.Loading -> Unit
        ParentContent.Unavailable -> Text(
            when {
                stated == null -> stringResource(R.string.card_parent_unavailable)
                author == null -> stringResource(R.string.card_parent_named_unavailable, stated.title.ifBlank { untitled })
                else -> stringResource(R.string.card_parent_stated_unavailable, stated.title.ifBlank { untitled }, author)
            },
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
            val title = (stated?.title ?: shown.title).ifBlank { untitled }
            Text(
                if (author == null) {
                    stringResource(R.string.card_parent, title)
                } else {
                    stringResource(R.string.card_parent_stated, title, author)
                },
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
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
 * (SCALE-002, SCALE-008); beside it the fewer and more actions as one connected group [ − | + ]. At ¼ the fewer action
 * is disabled and does nothing.
 */
@Composable
internal fun ServingsControl(
    modifier: Modifier,
    count: String,
    approximate: Boolean,
    canDecrease: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    // Each step of the count answers with a light tick; at ¼ the fewer action is disabled, so a refused step gives none.
    val haptics = rememberHaptics()
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(if (approximate) R.string.card_servings_approximate else R.string.card_servings, count),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite }.testTag(CardTags.SERVINGS),
        )
        Spacer(Modifier.width(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(CONNECTED_GAP)) {
            // A fully round outer side is the connected group's shape, at any size; the inner corners are the theme's.
            val outer = CornerSize(50)
            val inner = MaterialTheme.shapes.small
            StepAction(
                icon = R.drawable.ic_servings_fewer,
                name = stringResource(R.string.card_servings_fewer),
                action = stringResource(R.string.card_servings_fewer_action),
                shape = inner.copy(topStart = outer, bottomStart = outer),
                enabled = canDecrease,
                onClick = {
                    haptics.perform(Haptic.TICK)
                    onDecrease()
                },
                modifier = Modifier.testTag(CardTags.SERVINGS_FEWER),
            )
            StepAction(
                icon = R.drawable.ic_servings_more,
                name = stringResource(R.string.card_servings_more),
                action = stringResource(R.string.card_servings_more_action),
                shape = inner.copy(topEnd = outer, bottomEnd = outer),
                enabled = true,
                onClick = {
                    haptics.perform(Haptic.TICK)
                    onIncrease()
                },
                modifier = Modifier.testTag(CardTags.SERVINGS_MORE),
            )
        }
    }
}

/** The seam between the halves of a connected group: they read as one control, yet each half as its own target. */
private val CONNECTED_GAP = 2.dp

/**
 * One half of the − | + group: a tonal icon button the card's [CardTarget] square (A11Y-003, A11Y-007), its colours the
 * theme's and its [shape] rounded outside and joined to the other half inside. Its one click — by touch or by the
 * screen reader — is named by [name] and announced as [action] (A11Y-005): the label is merged onto the button's own
 * click, which stays its only action.
 */
@Composable
private fun StepAction(
    @DrawableRes icon: Int,
    name: String,
    action: String,
    shape: Shape,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilledTonalIconButton(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        modifier = modifier
            .size(CardTarget)
            .semantics {
                contentDescription = name
                onClick(label = action, action = null)
            },
    ) {
        Icon(painterResource(icon), contentDescription = null)
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
 * The card's ⋮, after its other actions, shown while it holds anything: Clear marks while the recipe has done marks
 * (FLOW-068), which takes every mark of the recipe away at once, and the recipe's removal — Delete for an own recipe
 * in a deletable state, Remove for a found one — in the theme's error colour. Each item is the card's [CardTarget]
 * tall.
 */
@Composable
private fun CardMenu(removal: Removal?, onRemove: () -> Unit, marked: Boolean, onClearMarks: () -> Unit) {
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
    const val PROVENANCE = "card-provenance"
    const val STATED_AUTHOR = "card-stated-author"
    const val SUMMARY = "card-summary"
    const val MISSING = "card-missing"
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
