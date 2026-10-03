package app.recipefork.app.card

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import app.recipefork.core.scaling.Rational
import app.recipefork.core.scaling.RequestedServings
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.layout.onGloballyPositioned
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
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
import app.recipefork.app.R
import app.recipefork.app.dictionary.IngredientEntryEntity
import app.recipefork.app.editor.interfaceLanguage
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
    data class Shown(val recipe: Recipe) : CardContent
}

/**
 * The recipe card: the saved recipe exactly as a reader sees it, not an editor with reader styling (FLOW-029 (1)).
 * One scrolling screen (the card intro of `cooking`): the cover, the title, the serving count, the ingredients in
 * order, the steps numbered, the description. A part the recipe does not hold is left out with no heading and no
 * placeholder (FLOW-019 (1–3)), so a recipe holding only a title still reads as a whole recipe.
 *
 * The top bar holds the screen's actions, outside the recipe: back to the book, the fork action on every recipe,
 * editable or frozen (FORK-001, FORK-007), and, only while the recipe is editable, the edit action that opens the
 * editor on it (FLOW-029 (2–4)); a found recipe is held frozen, so its card offers fork and never edit (FLOW-064 (2)). Nothing inside the recipe edits it; the serving count's − and +, which change how
 * the recipe is read and never the recipe (SCALE-001, SCALE-011), are one control in the content, and a fork's
 * provenance, which opens its parent's card, the other. It opens each time at the reader's usual count, or at the
 * authored count while none is set (FLOW-017).
 */
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
) {
    // Each attempt reads the recipe afresh; a read that fails — a row that does not read back, the storage refusing —
    // is said to be a failure, not an absence, and "Try again" makes the next attempt (FLOW-020 (3)), shown loading
    // until it is read.
    var attempt by remember(recipeId, recipes) { mutableIntStateOf(0) }
    val content by remember(recipeId, recipes, attempt) {
        recipes.observe(recipeId)
            .map<Recipe?, CardContent> { recipe -> recipe?.let(CardContent::Shown) ?: CardContent.Missing }
            .onStart { emit(CardContent.Loading) }
            .catch { failure -> if (failure is Exception && failure !is CancellationException) emit(CardContent.Unreadable) else throw failure }
    }.collectAsState(initial = CardContent.Loading)
    var entries by remember(environment) { mutableStateOf<Map<Long, IngredientEntryEntity>?>(null) }
    LaunchedEffect(environment) {
        // Rows whose entry cannot be read are named as unnamed; the card itself never fails for it.
        val read = try {
            environment.ingredients.entries()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
        entries = read.associateBy { it.id }
    }
    BackHandler(onBack = onBack)

    Column(modifier.fillMaxSize().testTag(CardTags.SCREEN)) {
        val shown = content
        TopBar(
            shown = shown is CardContent.Shown,
            editable = shown is CardContent.Shown && shown.recipe.state.editable,
            onBack = onBack,
            onFork = onFork,
            onEdit = onEdit,
        )
        HorizontalDivider()
        when (shown) {
            CardContent.Loading -> Unit
            CardContent.Missing -> Text(
                stringResource(R.string.card_missing),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(16.dp).testTag(CardTags.MISSING),
            )
            CardContent.Unreadable -> Unreadable(onRetry = { attempt++ })
            is CardContent.Shown -> entries?.let {
                // Read once, not collected: the usual count only sets where a card opens, never moves an open one.
                RecipeContent(shown.recipe, it, recipes, environment.usualServings.count.value, session, onOpenParent)
            }
        }
    }
}

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
 * The screen's bar: back on one side, the recipe's actions on the other — fork on any recipe shown, edit beside it.
 * Where they do not fit on one line — a large font, a narrow screen — the recipe's actions wrap below back, and edit
 * below fork, each action whole and never squeezed (A11Y-003 (2), A11Y-004).
 */
@Composable
private fun TopBar(shown: Boolean, editable: Boolean, onBack: () -> Unit, onFork: () -> Unit, onEdit: () -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp).testTag(CardTags.BAR),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        BarAction(
            text = stringResource(R.string.card_back),
            action = stringResource(R.string.card_back_action),
            onClick = onBack,
            modifier = Modifier.testTag(CardTags.BACK),
        )
        if (shown) {
            FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                BarAction(
                    text = stringResource(R.string.card_fork),
                    action = stringResource(R.string.card_fork_action),
                    onClick = onFork,
                    modifier = Modifier.testTag(CardTags.FORK),
                )
                if (editable) {
                    BarAction(
                        text = stringResource(R.string.card_edit),
                        action = stringResource(R.string.card_edit_action),
                        onClick = onEdit,
                        modifier = Modifier.testTag(CardTags.EDIT),
                    )
                }
            }
        }
    }
}

@Composable
private fun RecipeContent(
    recipe: Recipe,
    entries: Map<Long, IngredientEntryEntity>,
    recipes: RecipeRepository,
    usual: Rational?,
    session: CardSession,
    onOpenParent: (RecipeId) -> Unit,
) {
    val language = when (interfaceLanguage(LocalConfiguration.current)) {
        RecipeLanguage.RU -> InterfaceLanguage.RUSSIAN
        RecipeLanguage.EN -> InterfaceLanguage.ENGLISH
    }
    // The place is restored by the part that was first at the top, not by a scroll in pixels, which a change of text
    // scale or orientation while away would make point elsewhere (FLOW-012 (5)).
    val scroll = remember(recipe.id) { ScrollState(0) }
    val places = remember(recipe.id) { CardPlaces() }
    // A card brought to a timer's step goes there, not to the place it was left at (FLOW-013 (4)).
    val toStep = LocalStepToShow.current?.pending?.collectAsState()?.value?.recipeId == recipe.id.value
    KeptPlace(recipe.id, scroll, places, session, restore = !toStep)
    CompositionLocalProvider(LocalCardPlaces provides places) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .onGloballyPositioned(places::content)
                .padding(16.dp)
                .testTag(CardTags.CONTENT),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Cover(recipe, recipes.bookName(), cardPlace(CardPlaces.COVER_KEY))
            Text(
                recipe.title.ifBlank { stringResource(R.string.common_untitled_recipe) },
                style = MaterialTheme.typography.headlineSmall,
                modifier = cardPlace(CardPlaces.TITLE_KEY).semantics { heading() }.testTag(CardTags.TITLE),
            )
            // A found recipe's author as its file states it, marked every time it is shown (FLOW-064 (3)).
            recipe.statedAuthor?.let {
                Text(
                    stringResource(R.string.file_stated_author, it),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag(CardTags.STATED_AUTHOR),
                )
            }
            recipe.parent?.let { Provenance(it, recipe.statedParent, recipes, onOpenParent) }
            // The card opens at the reader's usual count, or at the authored one with none set (FLOW-017 (1, 2)) — unless
            // the reader left it at another this session, which it comes back at (FLOW-017 (7), FLOW-012). The scale — the
            // count in force and any fixing — is the card's alone, kept by the session: a change of count leaves the
            // setting as it is (FLOW-017 (3)). A fixing is of a row as it stood, so a change to that row drops it.
            val authored = recipe.servings?.takeIf { it >= 1 }
            val ordered = recipe.ingredients.sortedBy { it.position }
            var held by remember(recipe.id, authored, recipe.ingredients) {
                mutableStateOf(
                    session.heldScale(recipe.id)?.restored(authored, ordered, usual) ?: CardScale.opening(authored, usual),
                )
            }
            val scale = held
            fun change(to: CardScale) {
                held = to
                session.holdScale(recipe.id, HeldScale.of(to, ordered))
            }
            val list = ingredientList(ordered, entries, language, authored, scale)
            list.servingCount?.let { count ->
                ServingsControl(
                    modifier = cardPlace(CardPlaces.SERVINGS_KEY),
                    count = RequestedServings.text(count.value, language),
                    approximate = count.approximate,
                    canDecrease = count.value > RequestedServings.MIN,
                    // A change of count walks from the count shown, and releases every fixing (SCALE-016).
                    onDecrease = { change(scale.request(RequestedServings.decreased(count.value))) },
                    onIncrease = { change(scale.request(RequestedServings.increased(count.value))) },
                )
            }
            IngredientsList(
                ordered,
                list,
                scale.fixings,
                language,
                onFix = { change(scale.fix(it)) },
                onRelease = { change(scale.release(it)) },
            )
            StepsList(recipe.steps, recipe.id, recipe.title, CardReferences.of(ordered, list, language, entries), StepPhotosOnCard(recipe, recipes.bookName()))
            if (recipe.description.isNotBlank()) {
                Text(
                    recipe.description,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = cardPlace(CardPlaces.DESCRIPTION_KEY).testTag(CardTags.DESCRIPTION),
                )
            }
        }
    }
}

/**
 * Keeps the reader's place on the card of [recipe] for the session: the part first at the top of [scroll] whenever
 * they move. A card coming back to a place held for it is brought there, and kept there while what lies above it is
 * still being laid out — a cover, a photograph, a timer that ended — until the reader scrolls themselves.
 */
@Composable
private fun KeptPlace(recipe: RecipeId, scroll: ScrollState, places: CardPlaces, session: CardSession, restore: Boolean) {
    // Decided once, as the card is drawn: a step shown later moves the reader as their own scroll does.
    val restoring = remember(recipe) { restore }
    LaunchedEffect(recipe, scroll, places, session) {
        val anchor = if (restoring) session.heldAnchor(recipe) else null
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
        snapshotFlow { places.anchorAt(scroll.value) }.collect { session.holdAnchor(recipe, it) }
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

/** One part of the recipe under its heading; the lists of ingredients and of steps are each one. */
@Composable
internal fun Section(@StringRes title: Int, tag: String, lines: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().testTag(tag), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
 * (SCALE-002, SCALE-008); between a fewer and a more action. At ¼ the fewer action is disabled and does nothing.
 */
@Composable
private fun ServingsControl(
    modifier: Modifier,
    count: String,
    approximate: Boolean,
    canDecrease: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(if (approximate) R.string.card_servings_approximate else R.string.card_servings, count),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite }.testTag(CardTags.SERVINGS),
        )
        StepAction(
            glyph = MINUS,
            name = stringResource(R.string.card_servings_fewer),
            action = stringResource(R.string.card_servings_fewer_action),
            enabled = canDecrease,
            onClick = onDecrease,
            modifier = Modifier.testTag(CardTags.SERVINGS_FEWER),
        )
        StepAction(
            glyph = PLUS,
            name = stringResource(R.string.card_servings_more),
            action = stringResource(R.string.card_servings_more_action),
            enabled = true,
            onClick = onIncrease,
            modifier = Modifier.testTag(CardTags.SERVINGS_MORE),
        )
    }
}

/**
 * A − or + action, the card's [CardTarget] square (A11Y-003, A11Y-007): the screen reader names it by [name] rather than by its glyph, and
 * announces its click as [action] (A11Y-005).
 */
@Composable
private fun StepAction(
    glyph: String,
    name: String,
    action: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .size(CardTarget)
            .clickable(enabled = enabled, onClickLabel = action, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = name },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            glyph,
            color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

private const val MINUS = "\u2212"
private const val PLUS = "+"

/**
 * An action of the bar, at least the card's [CardTarget] square (A11Y-003, A11Y-007), whose click the screen reader announces as what it
 * does — [action] — beside the visible [text] (A11Y-005).
 */
@Composable
private fun BarAction(text: String, action: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .heightIn(min = CardTarget)
            .widthIn(min = CardTarget)
            .clickable(onClickLabel = action, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
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
    const val BAR = "card-bar"
    const val BACK = "card-back"
    const val EDIT = "card-edit"
    const val FORK = "card-fork"
    const val PROVENANCE = "card-provenance"
    const val STATED_AUTHOR = "card-stated-author"
    const val MISSING = "card-missing"
    const val UNREADABLE = "card-unreadable"
    const val RETRY = "card-retry"
    const val CONTENT = "card-content"
    const val TITLE = "card-title"
    const val COVER = "card-cover"
    const val SERVINGS = "card-servings"
    const val SERVINGS_FEWER = "card-servings-fewer"
    const val SERVINGS_MORE = "card-servings-more"
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
    fun fixUnit(unit: QuantityUnit) = "card-fix-unit-${unit.name.lowercase()}"
    fun ingredient(position: Int) = "card-ingredient-$position"
    fun step(position: Int) = "card-step-$position"
    fun stepPhoto(position: Int) = "card-step-$position-photo"
}
