package app.recipefork.app.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import app.recipefork.app.R
import app.recipefork.app.dictionary.IngredientEntryEntity
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.IngredientReference
import app.recipefork.core.recipe.ReferenceText
import app.recipefork.core.recipe.Step
import app.recipefork.core.scaling.InterfaceLanguage

/** A reference as a step's field shows it: the characters from [start] to [end] of the shown text. */
internal data class ShownReference(val start: Int, val end: Int, val reference: IngredientReference)

/** A step's text and references, as the author leaves them in its field. */
internal data class StepContent(val text: String, val references: List<IngredientReference>)

/** What an edit of the shown text leaves: the step's new [content], and where the cursor stands in its shown text. */
internal data class StepEdit(val content: StepContent, val cursor: Int, val removedAReference: Boolean)

/**
 * A step as its field shows it (FLOW-031, FLOW-032 (5–7)): its typed text with each reference read as [quantity]
 * gives it, at the offset it sits at. A reference is one unit of the shown text — the cursor never stands inside one,
 * and an edit that touches any of its characters removes all of it and nothing of the typed text around it.
 */
internal class StepDisplay(val content: StepContent, private val quantity: (Int) -> String?) {
    val text: String
    val references: List<ShownReference>

    init {
        val shown = StringBuilder()
        val references = mutableListOf<ShownReference>()
        var at = 0
        for (reference in content.references.sortedBy { it.textOffset }) {
            val reads = quantity(reference.ingredientPosition) ?: continue
            val offset = reference.textOffset.coerceIn(at, content.text.length)
            shown.append(content.text, at, offset)
            references += ShownReference(shown.length, shown.length + reads.length, reference.copy(textOffset = offset))
            shown.append(reads)
            at = offset
        }
        shown.append(content.text, at, content.text.length)
        text = shown.toString()
        this.references = references
    }

    /** Where in the step's own text the shown position [p] falls: every reference character before it left out. */
    private fun typed(p: Int): Int = p - references.sumOf { overlap(it.start, it.end, 0, p) }

    /** The reference [p] stands strictly inside, if any. */
    private fun inside(p: Int): ShownReference? = references.firstOrNull { it.start < p && p < it.end }

    /**
     * The characters from [start] to [end] of the shown text replaced by [inserted]. Every reference the replaced
     * characters touch is removed whole; the typed text is changed only where it was replaced, and every other
     * reference stays at its place in the sentence. Null where the edit would bring two references to one offset in
     * the text, which a step cannot hold: the edit is then not made.
     */
    fun edit(start: Int, end: Int, inserted: String): StepEdit? {
        var from = start
        var to = end
        // Typed into the middle of a reference: the typing goes after it.
        if (from == to) inside(from)?.let { from = it.end; to = it.end }
        val removed = if (from < to) references.filter { it.start < to && from < it.end } else emptyList()
        val typedFrom = typed(from)
        val typedTo = typed(to)
        val text = content.text.substring(0, typedFrom) + inserted + content.text.substring(typedTo)
        val shift = inserted.length - (typedTo - typedFrom)
        val kept = references.filter { it !in removed }.map {
            if (it.start >= to) it.reference.copy(textOffset = it.reference.textOffset + shift) else it.reference
        }
        if (kept.distinctBy { it.textOffset }.size != kept.size) return null
        val cursor = from - removed.sumOf { overlap(it.start, it.end, 0, from) } + inserted.length
        return StepEdit(StepContent(text, kept), cursor, removed.isNotEmpty())
    }

    /**
     * A reference to the ingredient at [ingredientPosition] put at the shown position [at], and the cursor after it.
     * Where another reference already sits at that offset of the text, a space is typed between the two.
     */
    fun insert(at: Int, ingredientPosition: Int): StepEdit {
        val p = inside(at)?.end ?: at
        val offset = typed(p)
        val new = IngredientReference(ingredientPosition, offset)
        val there = references.firstOrNull { it.reference.textOffset == offset }
        if (there == null) {
            val references = content.references + new
            return StepEdit(StepContent(content.text, references.sortedBy { it.textOffset }), cursorAfter(references, new), false)
        }
        val text = content.text.substring(0, offset) + " " + content.text.substring(offset)
        // The reference already there stays on its side of the cursor; the space goes between it and the new one.
        val before = there.end <= p
        val moved = content.references.map {
            if (it.textOffset > offset || (it.textOffset == offset && !before)) it.copy(textOffset = it.textOffset + 1) else it
        }
        val placed = if (before) new.copy(textOffset = offset + 1) else new
        val references = (moved + placed).sortedBy { it.textOffset }
        return StepEdit(StepContent(text, references), cursorAfter(references, placed, text), false)
    }

    private fun cursorAfter(references: List<IngredientReference>, reference: IngredientReference, text: String = content.text): Int {
        val shown = StepDisplay(StepContent(text, references), quantity)
        return shown.references.first { it.reference == reference }.end
    }

    /**
     * [selection] kept off the inside of every reference: a cursor inside one goes to its nearer edge, or, moved there
     * by one character from [before], on past it; a range grows to take in every reference it touches.
     */
    fun snap(selection: TextRange, before: TextRange = selection): TextRange {
        fun edge(p: Int, moved: Int): Int {
            val reference = inside(p) ?: return p
            return when {
                moved == 1 -> reference.end
                moved == -1 -> reference.start
                p - reference.start <= reference.end - p -> reference.start
                else -> reference.end
            }
        }
        if (selection.collapsed) {
            val p = selection.start.coerceIn(0, text.length)
            return TextRange(edge(p, p - before.start.coerceIn(0, text.length)))
        }
        fun outward(p: Int, low: Boolean): Int = inside(p)?.let { if (low) it.start else it.end } ?: p
        val low = outward(selection.min.coerceIn(0, text.length), low = true)
        val high = outward(selection.max.coerceIn(0, text.length), low = false)
        return if (selection.reversed) TextRange(high, low) else TextRange(low, high)
    }

    /** The shown text with each reference set apart from the typed text by weight and an underline, not colour alone. */
    fun styled(highlight: Color): AnnotatedString = buildAnnotatedString {
        append(text)
        references.forEach {
            addStyle(
                SpanStyle(fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline, background = highlight),
                it.start,
                it.end,
            )
        }
    }

    companion object {
        /** [step] as the editor shows it: each reference at the authored scale (FLOW-032 (5)). */
        fun of(step: Step, ingredients: List<Ingredient>, language: InterfaceLanguage) =
            StepDisplay(StepContent(step.text, step.ingredientReferences), ReferenceText.authored(ingredients, language))

        private fun overlap(a: Int, b: Int, c: Int, d: Int): Int = (minOf(b, d) - maxOf(a, c)).coerceAtLeast(0)
    }
}

/**
 * Which characters an edit of a text field replaced, from the text and selection before it and after it: [start] to
 * [end] of the old text, replaced by [inserted]. The selections settle what the texts alone cannot — which of two
 * equal characters was typed or deleted.
 */
internal data class TextChange(val start: Int, val end: Int, val inserted: String) {
    companion object {
        fun between(old: TextFieldValue, new: TextFieldValue): TextChange {
            val a = old.text
            val b = new.text
            val limit = minOf(a.length, b.length, old.selection.min, new.selection.min)
            var prefix = 0
            while (prefix < limit && a[prefix] == b[prefix]) prefix++
            val suffixLimit = minOf(
                a.length - prefix,
                b.length - prefix,
                (a.length - old.selection.max).coerceAtLeast(0),
                (b.length - new.selection.max).coerceAtLeast(0),
            )
            var suffix = 0
            while (suffix < suffixLimit && a[a.length - 1 - suffix] == b[b.length - 1 - suffix]) suffix++
            return TextChange(prefix, a.length - suffix, b.substring(prefix, b.length - suffix))
        }
    }
}

/** What a step's field holds beside the step itself: where the cursor stands, and the ingredients being offered. */
@Stable
internal class StepFieldState {
    /** Null until the author places it: the end of the text. */
    var selection by mutableStateOf<TextRange?>(null)
    var composition by mutableStateOf<TextRange?>(null)

    /** True while this recipe's ingredients are offered for a reference (FLOW-031). */
    var offering by mutableStateOf(false)

    /** Where the `@` the author typed stands in the shown text while the offer it opened is open; null otherwise. */
    var trigger by mutableStateOf<Int?>(null)

    val focus = FocusRequester()

    fun close() {
        offering = false
        trigger = null
    }
}

/** The character that offers the recipe's ingredients when typed into a step (FLOW-031 (1)). */
internal const val TRIGGER = "@"

/**
 * The text of the step at [position], with its references shown at the authored scale (FLOW-032 (5–7)) and kept one
 * unit each (FLOW-031 (4)). Typing [TRIGGER] offers this recipe's ingredients (FLOW-031 (1)).
 */
@Composable
internal fun StepTextField(
    position: Int,
    step: Step,
    ingredients: List<Ingredient>,
    language: InterfaceLanguage,
    editor: RecipeEditor,
    state: StepFieldState,
    modifier: Modifier = Modifier,
) {
    // The newest step and rows, ahead of the screen when the author types faster than it redraws.
    fun current(): StepDisplay {
        val recipe = editor.recipe.value
        val held = recipe?.steps?.sortedBy { it.position }?.getOrNull(position) ?: step
        return StepDisplay.of(held, recipe?.ingredients ?: ingredients, language)
    }
    fun shown(display: StepDisplay) = TextFieldValue(
        display.text,
        display.snap(state.selection ?: TextRange(display.text.length)),
        state.composition?.takeIf { it.max <= display.text.length },
    )
    val display = current()
    val highlight = MaterialTheme.colorScheme.secondaryContainer
    OutlinedTextField(
        value = shown(display),
        onValueChange = { new ->
            val now = current()
            val old = shown(now)
            if (new.text == old.text) {
                state.selection = now.snap(new.selection, old.selection)
                state.composition = new.composition
                return@OutlinedTextField
            }
            val change = TextChange.between(old, new)
            val edit = now.edit(change.start, change.end, change.inserted) ?: return@OutlinedTextField
            editor.setStepText(position, edit.content.text, edit.content.references)
            val next = StepDisplay(edit.content) { ReferenceText.authored(editor.recipe.value?.ingredients ?: ingredients, language)(it) }
            if (edit.removedAReference || next.text != new.text) {
                state.selection = TextRange(edit.cursor)
                state.composition = null
            } else {
                state.selection = next.snap(new.selection, old.selection)
                state.composition = new.composition
            }
            // The trigger as the last character typed; text pasted or typed after it opens nothing.
            if (change.inserted.endsWith(TRIGGER) && change.start == change.end && !edit.removedAReference) {
                state.trigger = edit.cursor - TRIGGER.length
                state.offering = true
            }
        },
        visualTransformation = { text ->
            // The shown text is the field's own text: only its style changes, so every offset maps to itself.
            if (text.text == display.text) TransformedText(display.styled(highlight), OffsetMapping.Identity)
            else TransformedText(text, OffsetMapping.Identity)
        },
        label = { Text(stringResource(R.string.editor_step, position + 1)) },
        minLines = 2,
        modifier = modifier.focusRequester(state.focus),
    )
}

/**
 * The control that inserts a reference into the step at [position] (FLOW-031 (2)), and the offer of this recipe's
 * [ingredients] it opens — the same offer typing [TRIGGER] opens. Every row of this recipe is offered, one with no
 * quantity yet and one "to taste" included, and no row of any other recipe (FLOW-031 (3), DOM-007). Choosing one
 * puts its reference at the cursor, or in place of the typed [TRIGGER]; dismissing the offer leaves the text as typed.
 */
@Composable
internal fun InsertIngredient(
    position: Int,
    ingredients: List<Ingredient>,
    dictionary: List<IngredientEntryEntity>?,
    language: InterfaceLanguage,
    editor: RecipeEditor,
    state: StepFieldState,
) {
    Box {
        ActionText(
            text = stringResource(R.string.editor_step_insert_ingredient),
            action = stringResource(R.string.editor_step_insert_ingredient_action, position + 1),
            onClick = {
                state.trigger = null
                state.offering = true
            },
            modifier = Modifier.testTag(EditorTags.stepInsert(position)),
        )
        DropdownMenu(expanded = state.offering, onDismissRequest = { state.close() }) {
            if (ingredients.isEmpty()) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.editor_step_insert_ingredient_none)) },
                    onClick = { state.close() },
                    enabled = false,
                    modifier = Modifier.testTag(EditorTags.stepOfferNone(position)),
                )
            }
            val unnamed = stringResource(R.string.editor_ingredient_unnamed)
            ingredients.forEachIndexed { row, ingredient ->
                val name = ingredient.nameIn(dictionary, language).takeIf { it.isNotBlank() } ?: unnamed
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.editor_step_offered_ingredient, name, ReferenceText.authored(ingredient, language))) },
                    onClick = {
                        choose(position, row, language, editor, state)
                        state.close()
                        runCatching { state.focus.requestFocus() }
                    },
                    modifier = Modifier.testTag(EditorTags.stepOffer(position, row)),
                )
            }
        }
    }
}

/** The reference to the row at [row] put where the offer was opened from: in place of the typed `@`, or at the cursor. */
private fun choose(
    position: Int,
    row: Int,
    language: InterfaceLanguage,
    editor: RecipeEditor,
    state: StepFieldState,
) {
    val recipe = editor.recipe.value ?: return
    val step = recipe.steps.sortedBy { it.position }.getOrNull(position) ?: return
    var display = StepDisplay.of(step, recipe.ingredients, language)
    var at = display.snap(state.selection ?: TextRange(display.text.length)).max
    val trigger = state.trigger
    if (trigger != null && display.text.startsWith(TRIGGER, trigger)) {
        // Between two references the `@` gives way to a space, so that they keep two places in the text.
        val removed = display.edit(trigger, trigger + TRIGGER.length, "")
            ?: display.edit(trigger, trigger + TRIGGER.length, " ")
            ?: return
        display = StepDisplay(removed.content, ReferenceText.authored(recipe.ingredients, language))
        at = removed.cursor
    }
    val inserted = display.insert(at, row)
    editor.setStepText(position, inserted.content.text, inserted.content.references)
    state.selection = TextRange(inserted.cursor)
    state.composition = null
}
