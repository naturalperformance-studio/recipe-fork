package app.recipefork.app.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
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
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.recipefork.app.R
import app.recipefork.core.recipe.Bound
import app.recipefork.core.recipe.RecipePart
import app.recipefork.core.scaling.QuantityText
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

/**
 * The mark of a part the author left below DOM-006's minimum or over a limit of DOM-012 (FLOW-028), if [part] carries
 * one: a warning, never an error — the warning icon and words saying what the part needs, in the text's own colour, read
 * by the screen reader as one statement as it appears. It refuses nothing and goes once the part is completed. A part
 * that is one text field — the title, the description, a step's text — shows its mark in the field instead ([FieldMark]).
 */
@Composable
internal fun BoundMark(editor: RecipeEditor, part: RecipePart, tag: String, gap: Dp, modifier: Modifier = Modifier) {
    val marks by editor.marks.collectAsState()
    val bound = marks[part] ?: return
    val text = boundText(part, bound)
    Row(
        modifier
            .fillMaxWidth()
            .then(keepingTheFocusedFieldInPlace(gap))
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

/**
 * The mark of FLOW-028 the editor holds on [part] now, if any: what a text field of that part shows as its supporting
 * text, in error, while it stands.
 */
@Composable
internal fun fieldMark(editor: RecipeEditor, part: RecipePart): Bound? {
    val marks by editor.marks.collectAsState()
    return marks[part]
}

/**
 * The mark of a text field's [part] standing at [bound], as the field's supporting text — Material's place for what a
 * field needs, drawn in the error colour by the field's isError: its words at the start, and at the end a counter of the
 * [length] the part holds against its [limit]. When it appears is FLOW-028's, unchanged: on leaving the part. Appearing
 * under the field above the one the author went on to, it does not push that one away.
 */
@Composable
internal fun FieldMark(part: RecipePart, bound: Bound, length: Int, limit: Int, tag: String) {
    val language = numberLanguage(LocalConfiguration.current)
    // L10N-009: grouped as the interface language groups numbers.
    val count = QuantityText.whole(length.toLong(), language)
    val most = QuantityText.whole(limit.toLong(), language)
    val counted = stringResource(R.string.editor_mark_counted, count, most)
    Row(
        Modifier.fillMaxWidth().then(keepingTheFocusedFieldInPlace(FIELD_MARK_GAP)),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(fieldMarkText(part, bound), Modifier.weight(1f).testTag(tag))
        Text(
            stringResource(R.string.editor_mark_counter, count, most),
            modifier = Modifier.semantics { contentDescription = counted }.testTag(EditorTags.counter(tag)),
        )
    }
}

/** What a field of [part] says to the screen reader while its mark stands at [bound]: the field is in error, and why. */
@Composable
internal fun fieldMarkSemantics(part: RecipePart, bound: Bound?): SemanticsPropertyReceiver.() -> Unit {
    val text = bound?.let { fieldMarkText(part, it) }
    return { if (text != null) error(text) }
}

/** Between a field and its supporting text, as Material lays it out. */
private val FIELD_MARK_GAP = 4.dp

/** What the supporting text of a field of [part] says while it stands at [bound]: the counter beside it gives the numbers. */
@Composable
private fun fieldMarkText(part: RecipePart, bound: Bound): String = when (bound) {
    Bound.Missing -> boundText(part, bound)
    is Bound.OverLimit -> stringResource(R.string.editor_mark_field_over)
}

/**
 * A mark that appears above the field the author is in pushes that field down by its height and the [gap] its column
 * puts before it; the editor scrolls by as much, so the field stays where the author has it and is not cut off at the
 * edge of the screen. The modifier goes on the mark.
 */
@Composable
internal fun keepingTheFocusedFieldInPlace(gap: Dp): Modifier {
    val scroll = LocalEditorScroll.current
    val gapPx = with(LocalDensity.current) { gap.toPx() }
    val placed = remember { mutableStateOf<LayoutCoordinates?>(null) }
    LaunchedEffect(Unit) {
        if (scroll == null) return@LaunchedEffect
        val mark = snapshotFlow { placed.value }.filterNotNull().first()
        // Once the frame the mark came in has been laid out, focus moved included.
        withFrameNanos {}
        val focused = scroll.focused?.takeIf { it.isAttached } ?: return@LaunchedEffect
        if (mark.isAttached && mark.positionInRoot().y < focused.positionInRoot().y) {
            scroll.state.dispatchRawDelta(mark.size.height + gapPx)
        }
    }
    return Modifier.onPlaced { placed.value = it }
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
            RecipePart.Steps -> R.string.editor_mark_steps_missing
            // No minimum asks anything of these; within DOM-012 they are never missing.
            RecipePart.Description, is RecipePart.StepText, is RecipePart.StepNote, is RecipePart.TimerName,
            is RecipePart.IngredientNote, is RecipePart.GroupName, is RecipePart.EquipmentName,
            -> return ""
        },
    )
    is Bound.OverLimit -> stringResource(
        when (part) {
            RecipePart.Ingredients -> R.string.editor_mark_ingredients_over
            RecipePart.Steps -> R.string.editor_mark_steps_over
            RecipePart.Title, RecipePart.Description, is RecipePart.StepText, is RecipePart.StepNote, is RecipePart.TimerName,
            is RecipePart.IngredientNote, is RecipePart.GroupName, is RecipePart.EquipmentName,
            -> R.string.editor_mark_characters_over
            // No limit of DOM-012 applies to these.
            RecipePart.Language, RecipePart.Servings -> return ""
        },
        // L10N-009: grouped as the interface language groups numbers.
        QuantityText.whole(bound.limit.toLong(), numberLanguage(LocalConfiguration.current)),
        QuantityText.whole(bound.count.toLong(), numberLanguage(LocalConfiguration.current)),
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
