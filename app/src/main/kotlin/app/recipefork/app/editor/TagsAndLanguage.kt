package app.recipefork.app.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.HorizontalAlignmentLine
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import kotlin.math.max
import kotlin.math.min
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
            EditorButton(
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
 * The tag editor: the recipe's tags as input chips, each removed by its cross alone, and the search whose offers show
 * as suggestion chips that add the tag. Tags come from the dictionary only, and typed text is never committed (DOM-008).
 * A recipe that cannot be changed and has no tags shows no part for them.
 */
@Composable
internal fun Tags(recipe: Recipe, editor: RecipeEditor, dictionary: List<TagEntity>?) {
    val editable = recipe.state.editable
    if (!editable && recipe.tags.isEmpty()) return
    Column(Modifier.fillMaxWidth().testTag(EditorTags.TAGS), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(R.string.editor_tags),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        val known = dictionary.orEmpty().filter { it.id in recipe.tags }
        if (known.isNotEmpty()) {
            FlowRow(horizontalArrangement = TAG_GAP, verticalArrangement = LINE_GAP) {
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

/**
 * A tag of the recipe as an input chip, drawn as Material's selected input chip with its trailing cross: only the cross
 * does anything — a tap on it removes the tag — and the rest of the chip, its label, does nothing, so that a tap meant
 * for the words never takes a tag away. The cross is an icon button with a touch area of the minimum interactive size
 * (48 dp, A11Y-003) centred on it, reaching past the chip as drawn — into the gap around it — rather than making the
 * chip taller; the screen reader finds the label, then the cross, named for what it removes.
 */
@Composable
private fun TagChip(id: Long, shown: String, onRemove: () -> Unit) {
    val action = stringResource(R.string.editor_tag_remove_action, shown)
    val minimum = LocalMinimumInteractiveComponentSize.current.takeIf { it.isSpecified } ?: 48.dp
    Layout(
        contents = listOf(
            { TagSurface(shown, Modifier.testTag(EditorTags.chip(id)), trailing = InputChipDefaults.IconSize + CROSS_PADDING) },
            {
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                    IconButton(onClick = onRemove, modifier = Modifier.size(minimum).testTag(EditorTags.remove(id))) {
                        Icon(
                            painterResource(R.drawable.ic_clear_search),
                            contentDescription = action,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(InputChipDefaults.IconSize).testTag(EditorTags.removeIcon(id)),
                        )
                    }
                }
            },
        ),
        modifier = Modifier.lineGap(),
    ) { (chips, crosses), constraints ->
        val chip = chips.single().measure(constraints.copy(minWidth = 0, minHeight = 0))
        val cross = crosses.single().measure(Constraints())
        // The cross centred where the chip leaves room for it: its icon's width and the padding after it, at its end.
        val centreX = chip.width - CROSS_PADDING.roundToPx() - InputChipDefaults.IconSize.roundToPx() / 2
        layout(chip.width, chip.height) {
            chip.place(0, 0)
            cross.place(centreX - cross.width / 2, (chip.height - cross.height) / 2)
        }
    }
}

/** Between a chip's trailing cross and its end, as Material's input chip draws it. */
private val CROSS_PADDING = 8.dp

/** A tag of a recipe that cannot be changed: the chip's look, and nothing to tap. */
@Composable
private fun FrozenTag(shown: String) {
    TagSurface(shown, Modifier.lineGap(), trailing = 0.dp)
}

/**
 * A tag as Material's selected input chip draws it: its shape and colours, at least its height, its label after 12 dp,
 * then [trailing] room — for a cross over it — or the same 12 dp.
 */
@Composable
private fun TagSurface(shown: String, modifier: Modifier, trailing: Dp) {
    Surface(
        shape = InputChipDefaults.shape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        // One element for the screen reader: the tag's words.
        modifier = modifier.semantics(mergeDescendants = true) {},
    ) {
        Row(
            Modifier.heightIn(min = InputChipDefaults.Height).padding(start = 12.dp, end = if (trailing > 0.dp) 0.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(shown, style = MaterialTheme.typography.labelLarge)
            if (trailing > 0.dp) Spacer(Modifier.width(8.dp + trailing))
        }
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
    FlowRow(Modifier.fillMaxWidth().testTag(EditorTags.OFFERED), horizontalArrangement = TAG_GAP, verticalArrangement = LINE_GAP) {
        offered.forEach { (tag, shown) ->
            val action = stringResource(R.string.editor_tag_add_action, shown)
            ChipTarget { target ->
                SuggestionChip(
                    onClick = {
                        editor.addTag(tag)
                        query = ""
                    },
                    label = { Text(shown) },
                    border = SuggestionChipDefaults.suggestionChipBorder(enabled = true, borderColor = MaterialTheme.colorScheme.outline),
                    modifier = target(EditorTags.offered(tag.id)) { onClick(label = action, action = null) },
                )
            }
        }
    }
}

/** The gap between drawn tag chips beside each other, and between wrapped lines of them. */
private val CHIP_GAP = 8.dp

/** Tag chips side by side: [CHIP_GAP] apart as drawn, a chip being laid out no wider than it is drawn unless it is drawn under 48 dp wide. */
private val TAG_GAP = Arrangement.spacedBy(CHIP_GAP)

/** Wrapped lines of tag chips: nothing between them, each chip laid out half the gap above and below where it is drawn ([lineGap]). */
private val LINE_GAP = Arrangement.spacedBy(0.dp)

/**
 * A chip drawn at Material's height — 32 dp, or more as the font grows — with a touch area of at least the minimum
 * interactive size (48 dp, A11Y-003) around it, and laid out in its line half the gap above and below where it is
 * drawn, so that wrapped lines are [CHIP_GAP] apart as drawn, their touch areas overlapping between them. [content] is
 * given what makes a chip so: its modifier, which carries the chip's test tag and its own [semantics]. The chip's own
 * minimum interactive size is left to the touch area here, which says how tall the chip is drawn.
 */
@Composable
private fun ChipTarget(content: @Composable (target: (tag: String, semantics: SemanticsPropertyReceiver.() -> Unit) -> Modifier) -> Unit) {
    val minimum = LocalMinimumInteractiveComponentSize.current.takeIf { it.isSpecified } ?: 48.dp
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        content { tag, semantics ->
            // The chip's semantics merge here, so that the element the screen reader and the touch-target check find is
            // the touch area, not the drawn chip within it.
            Modifier.lineGap().semantics(mergeDescendants = true, properties = semantics).testTag(tag).touchArea(minimum)
        }
    }
}

/** Where the chip within a [touchArea] is drawn from, below the area's top. */
private val DrawnTop = HorizontalAlignmentLine { a, b -> min(a, b) }

/** Where the chip within a [touchArea] is drawn to, below the area's top. */
private val DrawnBottom = HorizontalAlignmentLine { a, b -> max(a, b) }

/**
 * At least [minimum] by [minimum] around what it holds, which is drawn centred within it: what Material's minimum
 * interactive size does — touches around the drawn chip are the chip's — saying through [DrawnTop] and [DrawnBottom]
 * where the chip is drawn.
 */
private fun Modifier.touchArea(minimum: Dp): Modifier = layout { measurable, constraints ->
    val drawn = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    val width = constraints.constrainWidth(max(drawn.width, minimum.roundToPx()))
    val height = constraints.constrainHeight(max(drawn.height, minimum.roundToPx()))
    val top = (height - drawn.height) / 2
    layout(width, height, mapOf(DrawnTop to top, DrawnBottom to top + drawn.height)) { drawn.place((width - drawn.width) / 2, top) }
}

/**
 * Laid out half [CHIP_GAP] above and below where the chip is drawn — the touch area around a smaller chip reaching
 * beyond, over the gap — so that lines of chips are [CHIP_GAP] apart as drawn, whatever the height they are drawn at.
 */
private fun Modifier.lineGap(): Modifier = layout { measurable, constraints ->
    val area = measurable.measure(constraints.copy(minHeight = 0))
    // A chip with no touch area of its own here — one that cannot be tapped — is drawn as it is laid out.
    val top = area[DrawnTop].takeIf { it != AlignmentLine.Unspecified } ?: 0
    val drawn = (area[DrawnBottom].takeIf { it != AlignmentLine.Unspecified } ?: area.height) - top
    val half = (CHIP_GAP / 2).roundToPx()
    val height = constraints.constrainHeight(drawn + 2 * half)
    layout(area.width, height) { area.place(0, half - top) }
}
