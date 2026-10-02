package app.recipefork.app.card

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import app.recipefork.core.scaling.InterfaceLanguage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.map

/** What the card has of its recipe: nothing yet, the recipe, or word that the book no longer holds it. */
private sealed interface CardContent {
    data object Loading : CardContent
    data object Missing : CardContent
    data class Shown(val recipe: Recipe) : CardContent
}

/**
 * The recipe card: the saved recipe exactly as a reader sees it, not an editor with reader styling (FLOW-029 (1)).
 * One scrolling screen (the card intro of `cooking`): the title, the serving count, the ingredients in
 * order, the steps numbered, the description. A part the recipe does not hold is left out with no heading and no
 * placeholder (FLOW-019 (1–3)), so a recipe holding only a title still reads as a whole recipe.
 *
 * The top bar holds the screen's actions, outside the recipe: back to the book, the fork action on every recipe,
 * editable or frozen (FORK-001, FORK-007), and, only while the recipe is editable, the edit action that opens the
 * editor on it (FLOW-029 (2–4)). Nothing inside the recipe edits it; the serving count's − and +, which change how
 * the recipe is read and never the recipe (SCALE-001, SCALE-011), are one control in the content, and a fork's
 * provenance, which opens its parent's card, the other. It opens at the authored count each time.
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
) {
    val content by remember(recipeId, recipes) {
        recipes.observe(recipeId).map { recipe -> recipe?.let(CardContent::Shown) ?: CardContent.Missing }
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
            is CardContent.Shown -> entries?.let { RecipeContent(shown.recipe, it, recipes, onOpenParent) }
        }
    }
}

/** The screen's bar: back on one side, the recipe's actions on the other — fork on any recipe shown, edit beside it. */
@Composable
private fun TopBar(shown: Boolean, editable: Boolean, onBack: () -> Unit, onFork: () -> Unit, onEdit: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp).testTag(CardTags.BAR),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BarAction(
            text = stringResource(R.string.card_back),
            action = stringResource(R.string.card_back_action),
            onClick = onBack,
            modifier = Modifier.testTag(CardTags.BACK),
        )
        Spacer(Modifier.weight(1f))
        if (shown) {
            BarAction(
                text = stringResource(R.string.card_fork),
                action = stringResource(R.string.card_fork_action),
                onClick = onFork,
                modifier = Modifier.testTag(CardTags.FORK),
            )
        }
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

@Composable
private fun RecipeContent(
    recipe: Recipe,
    entries: Map<Long, IngredientEntryEntity>,
    recipes: RecipeRepository,
    onOpenParent: (RecipeId) -> Unit,
) {
    val language = when (interfaceLanguage(LocalConfiguration.current)) {
        RecipeLanguage.RU -> InterfaceLanguage.RUSSIAN
        RecipeLanguage.EN -> InterfaceLanguage.ENGLISH
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag(CardTags.CONTENT),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            recipe.title.ifBlank { stringResource(R.string.common_untitled_recipe) },
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() }.testTag(CardTags.TITLE),
        )
        recipe.parent?.let { Provenance(it, recipes, onOpenParent) }
        // The card always opens at the authored count (FLOW-017 (2)); the scale is the card's alone, and a fixing is
        // of the rows as they stand, so a change to them starts the scale anew.
        val authored = recipe.servings?.takeIf { it >= 1 }
        var scale by remember(recipe.id, authored, recipe.ingredients) {
            mutableStateOf(CardScale(authored?.let { Rational.of(it.toLong()) }))
        }
        val ordered = recipe.ingredients.sortedBy { it.position }
        val list = ingredientList(ordered, entries, language, authored, scale)
        list.servingCount?.let { count ->
            ServingsControl(
                count = RequestedServings.text(count.value, language),
                approximate = count.approximate,
                canDecrease = count.value > RequestedServings.MIN,
                // A change of count walks from the count shown, and releases every fixing (SCALE-016).
                onDecrease = { scale = scale.request(RequestedServings.decreased(count.value)) },
                onIncrease = { scale = scale.request(RequestedServings.increased(count.value)) },
            )
        }
        IngredientsList(
            ordered,
            list,
            scale.fixings,
            language,
            onFix = { scale = scale.fix(it) },
            onRelease = { scale = scale.release(it) },
        )
        StepsList(recipe.steps, recipe.id, recipe.title)
        if (recipe.description.isNotBlank()) {
            Text(
                recipe.description,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.testTag(CardTags.DESCRIPTION),
            )
        }
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
 */
@Composable
private fun Provenance(parent: RecipeId, recipes: RecipeRepository, onOpenParent: (RecipeId) -> Unit) {
    val content by remember(parent, recipes) {
        recipes.observe(parent).map { it?.let { held -> ParentContent.Held(held.title) } ?: ParentContent.Unavailable }
    }.collectAsState(initial = ParentContent.Loading)
    when (val shown = content) {
        ParentContent.Loading -> Unit
        ParentContent.Unavailable -> Text(
            stringResource(R.string.card_parent_unavailable),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag(CardTags.PROVENANCE),
        )
        is ParentContent.Held -> Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(
                    onClickLabel = stringResource(R.string.card_parent_open_action),
                    role = Role.Button,
                    onClick = { onOpenParent(parent) },
                )
                .testTag(CardTags.PROVENANCE),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                stringResource(
                    R.string.card_parent,
                    shown.title.ifBlank { stringResource(R.string.common_untitled_recipe) },
                ),
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
    count: String,
    approximate: Boolean,
    canDecrease: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
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
 * A − or + action, 48 by 48 dp (A11Y-003): the screen reader names it by [name] rather than by its glyph, and
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
            .size(48.dp)
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
 * An action of the bar, at least 48 by 48 dp (A11Y-003), whose click the screen reader announces as what it
 * does — [action] — beside the visible [text] (A11Y-005).
 */
@Composable
private fun BarAction(text: String, action: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .heightIn(min = 48.dp)
            .widthIn(min = 48.dp)
            .clickable(onClickLabel = action, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
    }
}

object CardTags {
    const val SCREEN = "card"
    const val BAR = "card-bar"
    const val BACK = "card-back"
    const val EDIT = "card-edit"
    const val FORK = "card-fork"
    const val PROVENANCE = "card-provenance"
    const val MISSING = "card-missing"
    const val CONTENT = "card-content"
    const val TITLE = "card-title"
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
}
