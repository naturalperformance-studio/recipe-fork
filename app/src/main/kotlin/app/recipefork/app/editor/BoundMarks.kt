package app.recipefork.app.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.recipefork.app.R
import app.recipefork.core.recipe.Bound
import app.recipefork.core.recipe.RecipePart

/**
 * The mark of a part the author left below DOM-006's minimum or over a limit of DOM-012 (FLOW-028), if [part] carries
 * one: a warning, never an error — the warning icon and words saying what the part needs, in the text's own colour, read
 * by the screen reader as one statement as it appears. It refuses nothing and goes once the part is completed.
 */
@Composable
internal fun BoundMark(editor: RecipeEditor, part: RecipePart, tag: String, modifier: Modifier = Modifier) {
    val marks by editor.marks.collectAsState()
    val bound = marks[part] ?: return
    val text = boundText(part, bound)
    Row(
        modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
            .testTag(tag),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(R.drawable.ic_warning),
            contentDescription = stringResource(R.string.editor_mark_warning),
            modifier = Modifier.size(20.dp),
        )
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

/** What the mark of [part] says while it stands at [bound]. */
@Composable
private fun boundText(part: RecipePart, bound: Bound): String = when (bound) {
    Bound.Missing -> stringResource(
        when (part) {
            RecipePart.Title -> R.string.editor_mark_title_missing
            RecipePart.Language -> R.string.editor_mark_language_missing
            RecipePart.Servings -> R.string.editor_mark_servings_missing
            RecipePart.Ingredients -> R.string.editor_mark_ingredients_missing
            else -> R.string.editor_mark_steps_missing
        },
    )
    is Bound.OverLimit -> stringResource(
        when (part) {
            RecipePart.Ingredients -> R.string.editor_mark_ingredients_over
            RecipePart.Steps -> R.string.editor_mark_steps_over
            else -> R.string.editor_mark_characters_over
        },
        bound.limit,
        bound.count,
    )
}

/**
 * Calls [onEnter] when focus comes into what this modifier is on, and [onLeave] when it goes out of it — the moment
 * FLOW-028 marks a part.
 */
@Composable
internal fun leaving(onEnter: () -> Unit = {}, onLeave: () -> Unit): Modifier {
    val enter by rememberUpdatedState(onEnter)
    val leave by rememberUpdatedState(onLeave)
    return remember {
        var held = false
        Modifier.onFocusChanged { state ->
            if (state.hasFocus && !held) enter()
            if (!state.hasFocus && held) leave()
            held = state.hasFocus
        }
    }
}
