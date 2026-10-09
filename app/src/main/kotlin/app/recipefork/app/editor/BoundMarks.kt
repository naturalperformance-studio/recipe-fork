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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import app.recipefork.core.scaling.QuantityText
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope

/**
 * The mark of a part the author left below DOM-006's minimum or over a limit of DOM-012 (FLOW-028), if [part] carries
 * one: a warning, never an error — the warning icon and words saying what the part needs, in the text's own colour, read
 * by the screen reader as one statement as it appears. It refuses nothing and goes once the part is completed.
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

/**
 * Something above the field the author is in that gets shorter — a field whose supporting text goes — would pull that
 * field up by as much; once the editor is laid out shorter, the editor scrolls back by the height it lost. Its
 * [modifier] goes on the thing, and [shrinking] is called as what it loses is taken away.
 */
internal class Shrinking(private val scroll: EditorScroll?, private val scope: CoroutineScope) {
    private var top = 0f
    private var height = 0

    val modifier: Modifier = Modifier.onGloballyPositioned {
        top = it.positionInRoot().y
        height = it.size.height
    }

    fun shrinking() {
        val scroll = scroll ?: return
        val stood = top
        val tall = height
        scope.launch {
            // Once the frame it shrank in has been laid out, focus moved included.
            withFrameNanos {}
            withFrameNanos {}
            val lost = tall - height
            val focused = scroll.focused?.takeIf { it.isAttached } ?: return@launch
            if (lost > 0 && stood <= focused.positionInRoot().y) scroll.state.dispatchRawDelta(-lost.toFloat())
        }
    }
}

/** A [Shrinking] in the editor the composition is in. */
@Composable
internal fun rememberShrinking(): Shrinking {
    val scroll = LocalEditorScroll.current
    val scope = rememberCoroutineScope()
    return remember(scroll, scope) { Shrinking(scroll, scope) }
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
            RecipePart.Description, is RecipePart.StepText, is RecipePart.TimerName, is RecipePart.IngredientNote,
            is RecipePart.GroupName, is RecipePart.EquipmentName,
            -> return ""
        },
    )
    is Bound.OverLimit -> stringResource(
        when (part) {
            RecipePart.Ingredients -> R.string.editor_mark_ingredients_over
            RecipePart.Steps -> R.string.editor_mark_steps_over
            RecipePart.Title, RecipePart.Description, is RecipePart.StepText, is RecipePart.TimerName, is RecipePart.IngredientNote,
            is RecipePart.GroupName, is RecipePart.EquipmentName,
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
