package app.recipefork.app.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.recipefork.app.R
import app.recipefork.app.dictionary.TagEntity
import app.recipefork.app.dictionary.TagOffer
import app.recipefork.app.dictionary.label
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeLanguage
import app.recipefork.core.recipe.RecipePart

/**
 * The declared language beside the title (FLOW-024): a single-choice segmented switch [RU | EN], where tapping a
 * language declares it in one action (L10N-004). A recipe left without a language has neither chosen; Clear leaves
 * it so. A recipe that cannot be changed shows its language as text.
 */
@Composable
internal fun Language(recipe: Recipe, editor: RecipeEditor) {
    val language = recipe.language
    if (!recipe.state.editable) {
        Column(
            Modifier
                .heightIn(min = 56.dp)
                .widthIn(min = 96.dp)
                .semantics(mergeDescendants = true) {}
                .testTag(EditorTags.LANGUAGE)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.editor_language), style = MaterialTheme.typography.labelMedium)
            Text(stringResource(languageName(language)), style = MaterialTheme.typography.titleMedium)
        }
        return
    }
    Column(Modifier.testTag(EditorTags.LANGUAGE), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.editor_language), style = MaterialTheme.typography.labelMedium)
        SingleChoiceSegmentedButtonRow {
            RecipeLanguage.entries.forEachIndexed { index, option ->
                val name = stringResource(languageName(option))
                val action = stringResource(if (option == RecipeLanguage.RU) R.string.editor_language_to_ru else R.string.editor_language_to_en)
                SegmentedButton(
                    selected = language == option,
                    onClick = { editor.setLanguage(option) },
                    shape = SegmentedButtonDefaults.itemShape(index, RecipeLanguage.entries.size),
                    icon = {},
                    // The screen reader names the language in full and says what a tap does (A11Y-005); the
                    // segment's role and selection say which is declared.
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .semantics { onClick(label = action, action = null) }
                        .testTag(EditorTags.language(option)),
                    label = {
                        Text(
                            stringResource(if (option == RecipeLanguage.RU) R.string.editor_language_ru_short else R.string.editor_language_en_short),
                            modifier = Modifier.clearAndSetSemantics { contentDescription = name },
                        )
                    },
                )
            }
        }
        if (language != null) {
            ActionText(
                text = stringResource(R.string.editor_language_clear),
                action = stringResource(R.string.editor_language_clear_action),
                // Clearing it is leaving it without a language: the mark of FLOW-028 says what that costs.
                onClick = {
                    editor.setLanguage(null)
                    editor.leave(RecipePart.Language)
                },
                modifier = Modifier.testTag(EditorTags.LANGUAGE_CLEAR),
            )
        }
    }
}

private fun languageName(language: RecipeLanguage?): Int = when (language) {
    RecipeLanguage.RU -> R.string.editor_language_ru
    RecipeLanguage.EN -> R.string.editor_language_en
    null -> R.string.editor_language_none
}

/** The label of [tag] in the interface language. */
@Composable
private fun label(tag: TagEntity): String =
    if (interfaceLanguage(LocalConfiguration.current) == RecipeLanguage.RU) tag.labelRu else tag.labelEn

/**
 * The tag editor: the recipe's tags as input chips, each removed by a tap on it, and the search whose offers show
 * as suggestion chips that add the tag. Tags come from the dictionary only, and typed text is never committed (DOM-008).
 */
@Composable
internal fun Tags(recipe: Recipe, editor: RecipeEditor, dictionary: List<TagEntity>?) {
    val editable = recipe.state.editable
    Column(Modifier.fillMaxWidth().testTag(EditorTags.TAGS), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(R.string.editor_tags),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        val known = dictionary.orEmpty().filter { it.id in recipe.tags }
        if (known.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                known.forEach { tag ->
                    Box(Modifier.testTag(EditorTags.chosen(tag.id))) {
                        if (editable) TagChip(tag.id, label(tag)) { editor.removeTag(tag.id) } else FrozenTag(label(tag))
                    }
                }
            }
        }
        if (editable && dictionary != null) TagSearch(recipe, editor, dictionary)
    }
}

/** A tag of the recipe as an input chip: a tap on it — or on its cross — removes it. */
@Composable
private fun TagChip(id: Long, shown: String, onRemove: () -> Unit) {
    val action = stringResource(R.string.editor_tag_remove_action, shown)
    InputChip(
        selected = true,
        onClick = onRemove,
        label = { Text(shown) },
        trailingIcon = {
            Icon(
                painterResource(R.drawable.ic_clear_search),
                contentDescription = null,
                modifier = Modifier.size(InputChipDefaults.IconSize).testTag(EditorTags.removeIcon(id)),
            )
        },
        modifier = Modifier.touchTarget().semantics { onClick(label = action, action = null) }.testTag(EditorTags.remove(id)),
    )
}

/** A tag of a recipe that cannot be changed: the chip's look, and nothing to tap. */
@Composable
private fun FrozenTag(shown: String) {
    Surface(
        shape = InputChipDefaults.shape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(shown, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
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
    // Hundreds of tags are searched, never listed: nothing is offered until a word is typed.
    val language = numberLanguage(LocalConfiguration.current)
    val offered = TagOffer.of(query, dictionary, language, excluded = recipe.tags).map { it to it.label(language) }
    if (offered.isEmpty() && query.isNotBlank()) {
        Text(
            stringResource(R.string.editor_tag_no_match),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag(EditorTags.NO_MATCH),
        )
    }
    FlowRow(Modifier.fillMaxWidth().testTag(EditorTags.OFFERED), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        offered.forEach { (tag, shown) ->
            val action = stringResource(R.string.editor_tag_add_action, shown)
            SuggestionChip(
                onClick = {
                    editor.addTag(tag)
                    query = ""
                },
                label = { Text(shown) },
                border = SuggestionChipDefaults.suggestionChipBorder(enabled = true, borderColor = MaterialTheme.colorScheme.outline),
                modifier = Modifier.touchTarget().semantics { onClick(label = action, action = null) }.testTag(EditorTags.offered(tag.id)),
            )
        }
    }
}

/** A chip laid out at least 48 by 48 dp, so that it is a whole touch target (A11Y-003). */
private fun Modifier.touchTarget(): Modifier = heightIn(min = 48.dp).widthIn(min = 48.dp)
