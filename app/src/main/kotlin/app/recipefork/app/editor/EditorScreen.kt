package app.recipefork.app.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.recipefork.app.R
import app.recipefork.app.RecipeForkApplication
import app.recipefork.app.dictionary.DictionaryCatalog
import app.recipefork.app.dictionary.DictionarySeeding
import app.recipefork.app.dictionary.DictionarySnapshot
import app.recipefork.app.dictionary.PreferencesSeededVersion
import app.recipefork.app.dictionary.TagEntity
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.RecipeLanguage
import kotlinx.coroutines.CoroutineScope

/** The editor's environment from the composition root: the active book and the device's dictionaries. */
@Composable
fun rememberEditorEnvironment(): EditorEnvironment {
    val context = LocalContext.current.applicationContext
    return remember(context) {
        val container = (context as RecipeForkApplication).container
        val dao = container.database.dictionaryDao()
        val seeding = DictionarySeeding(dao, PreferencesSeededVersion(context)) { DictionarySnapshot.read(context) }
        EditorEnvironment(container.activeBook, DictionaryCatalog(dao, seeding))
    }
}

/**
 * The editor: one scrolling screen in the order of FLOW-024 — the title with the declared language beside it, the
 * tags, the serving count and the ingredients. The parts after them are written by later tasks.
 */
@Composable
fun EditorScreen(
    recipeId: RecipeId,
    recipes: RecipeRepository,
    environment: EditorEnvironment,
    saving: CoroutineScope,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val editor = remember(recipeId) { RecipeEditor(recipes, recipeId, environment.clock, saving) }
    LaunchedEffect(editor) { editor.load() }
    var dictionary by remember { mutableStateOf<List<TagEntity>?>(null) }
    LaunchedEffect(environment) { dictionary = environment.tags.tags() }
    val recipe by editor.recipe.collectAsState()
    BackHandler(onBack = onClose)

    Column(modifier.fillMaxSize().testTag(EditorTags.SCREEN)) {
        ActionText(
            text = stringResource(R.string.editor_back),
            action = stringResource(R.string.editor_back_action),
            onClick = onClose,
            modifier = Modifier.padding(horizontal = 8.dp).testTag(EditorTags.BACK),
        )
        HorizontalDivider()
        val shown = recipe ?: return@Column
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag(EditorTags.FORM),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!shown.state.editable) {
                Text(
                    stringResource(R.string.editor_frozen),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.testTag(EditorTags.FROZEN),
                )
            }
            TitleAndLanguage(shown, editor)
            Tags(shown, editor, dictionary)
            ServingCount(shown, editor)
            Ingredients(shown, editor)
        }
    }
}

@Composable
private fun TitleAndLanguage(recipe: Recipe, editor: RecipeEditor) {
    val editable = recipe.state.editable
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        if (editable) {
            OutlinedTextField(
                value = recipe.title,
                onValueChange = { editor.setTitle(it) },
                label = { Text(stringResource(R.string.editor_title)) },
                singleLine = true,
                modifier = Modifier.weight(1f).testTag(EditorTags.TITLE),
            )
        } else {
            Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}.testTag(EditorTags.TITLE)) {
                Text(stringResource(R.string.editor_title), style = MaterialTheme.typography.labelMedium)
                Text(recipe.title.ifBlank { stringResource(R.string.common_untitled_recipe) }, style = MaterialTheme.typography.titleLarge)
            }
        }
        Spacer(Modifier.width(8.dp))
        // The declared language sits beside the title (FLOW-024): changing it is one action (L10N-004).
        Language(recipe, editor)
    }
}

@Composable
private fun Language(recipe: Recipe, editor: RecipeEditor) {
    val editable = recipe.state.editable
    val language = recipe.language
    val name = stringResource(
        when (language) {
            RecipeLanguage.RU -> R.string.editor_language_ru
            RecipeLanguage.EN -> R.string.editor_language_en
            null -> R.string.editor_language_none
        },
    )
    // One action changes it: to the other language, or, where it was cleared, back to the interface language.
    val next = when (language) {
        RecipeLanguage.RU -> RecipeLanguage.EN
        RecipeLanguage.EN -> RecipeLanguage.RU
        null -> interfaceLanguage(LocalConfiguration.current)
    }
    val action = stringResource(if (next == RecipeLanguage.RU) R.string.editor_language_to_ru else R.string.editor_language_to_en)
    Column(Modifier.widthIn(min = 96.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(
            Modifier
                .heightIn(min = 56.dp)
                .widthIn(min = 96.dp)
                .semantics(mergeDescendants = true) {}
                .then(if (editable) Modifier.clickable(onClickLabel = action, role = Role.Button) { editor.setLanguage(next) } else Modifier)
                .testTag(EditorTags.LANGUAGE)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.editor_language), style = MaterialTheme.typography.labelMedium)
            Text(name, style = MaterialTheme.typography.titleMedium)
        }
        if (editable && language != null) {
            ActionText(
                text = stringResource(R.string.editor_language_clear),
                action = stringResource(R.string.editor_language_clear_action),
                onClick = { editor.setLanguage(null) },
                modifier = Modifier.testTag(EditorTags.LANGUAGE_CLEAR),
            )
        }
    }
}

/** The label of [tag] in the interface language. */
@Composable
private fun label(tag: TagEntity): String =
    if (interfaceLanguage(LocalConfiguration.current) == RecipeLanguage.RU) tag.labelRu else tag.labelEn

/** The tag editor: tags come from the dictionary only, and typed text is never committed (DOM-008). */
@Composable
private fun Tags(recipe: Recipe, editor: RecipeEditor, dictionary: List<TagEntity>?) {
    val editable = recipe.state.editable
    Column(Modifier.fillMaxWidth().testTag(EditorTags.TAGS), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(R.string.editor_tags),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        val known = dictionary.orEmpty().filter { it.id in recipe.tags }
        known.forEach { tag ->
            val shown = label(tag)
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(EditorTags.chosen(tag.id)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(shown, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                if (editable) {
                    ActionText(
                        text = stringResource(R.string.editor_tag_remove),
                        action = stringResource(R.string.editor_tag_remove_action, shown),
                        onClick = { editor.removeTag(tag.id) },
                        modifier = Modifier.testTag(EditorTags.remove(tag.id)),
                    )
                }
            }
        }
        if (editable && dictionary != null) TagSearch(recipe, editor, dictionary)
    }
}

@Composable
private fun TagSearch(recipe: Recipe, editor: RecipeEditor, dictionary: List<TagEntity>) {
    var query by rememberSaveable { mutableStateOf("") }
    val focus = LocalFocusManager.current
    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        label = { Text(stringResource(R.string.editor_tag_search)) },
        singleLine = true,
        // The keyboard's action closes the keyboard and commits nothing: only an offered entry becomes a tag.
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
        modifier = Modifier.fillMaxWidth().testTag(EditorTags.TAG_SEARCH),
    )
    val sought = query.trim()
    val offered = dictionary.filter { it.id !in recipe.tags }.map { it to label(it) }
        .filter { (_, shown) -> shown.contains(sought, ignoreCase = true) }
        .sortedBy { (_, shown) -> shown.lowercase() }
    if (offered.isEmpty() && sought.isNotEmpty()) {
        Text(
            stringResource(R.string.editor_tag_no_match),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag(EditorTags.NO_MATCH),
        )
    }
    Column(Modifier.fillMaxWidth().testTag(EditorTags.OFFERED)) {
        offered.forEach { (tag, shown) ->
            Text(
                shown,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable(onClickLabel = stringResource(R.string.editor_tag_add_action, shown), role = Role.Button) {
                        editor.addTag(tag)
                        query = ""
                    }
                    .testTag(EditorTags.offered(tag.id))
                    .padding(vertical = 12.dp),
            )
        }
    }
}

/**
 * A text the author taps, at least 48 by 48 dp (A11Y-003), whose click the screen reader announces as what it
 * does — [action] — beside the visible [text] (A11Y-005).
 */
@Composable
internal fun ActionText(text: String, action: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
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

object EditorTags {
    const val SCREEN = "editor"
    const val BACK = "editor-back"
    const val FORM = "editor-form"
    const val FROZEN = "editor-frozen"
    const val TITLE = "editor-title"
    const val LANGUAGE = "editor-language"
    const val LANGUAGE_CLEAR = "editor-language-clear"
    const val TAGS = "editor-tags"
    const val TAG_SEARCH = "editor-tag-search"
    const val OFFERED = "editor-tags-offered"
    const val NO_MATCH = "editor-tag-no-match"
    fun offered(tagId: Long) = "editor-tag-offered-$tagId"
    fun chosen(tagId: Long) = "editor-tag-chosen-$tagId"
    fun remove(tagId: Long) = "editor-tag-remove-$tagId"
    const val SERVINGS = "editor-servings"
    const val SERVINGS_ACCEPTS = "editor-servings-accepts"
    const val INGREDIENTS = "editor-ingredients"
    const val INGREDIENT_ADD = "editor-ingredient-add"
    fun ingredient(position: Int) = "editor-ingredient-$position"
    fun ingredientName(position: Int) = "editor-ingredient-$position-name"
    fun ingredientQuantity(position: Int) = "editor-ingredient-$position-quantity"
    fun ingredientUnit(position: Int) = "editor-ingredient-$position-unit"
    fun ingredientRemove(position: Int) = "editor-ingredient-$position-remove"
    fun ingredientError(position: Int) = "editor-ingredient-$position-error"
    fun ingredientWarning(position: Int) = "editor-ingredient-$position-warning"
    fun divisibility(position: Int) = "editor-ingredient-$position-divisibility"
    fun divisible(position: Int, divisible: Boolean) = "editor-ingredient-$position-divisible-$divisible"
    fun unitOption(unit: QuantityUnit) = "editor-unit-${unit.name}"
}
