package app.recipefork.app.editor

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import app.recipefork.app.R
import app.recipefork.app.dictionary.IngredientEntryEntity
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.IngredientReference
import app.recipefork.core.recipe.IngredientShare
import app.recipefork.core.recipe.IngredientShares
import app.recipefork.core.recipe.ReferenceQuantity
import app.recipefork.core.recipe.ReferenceSpacing
import app.recipefork.core.recipe.ReferenceText
import app.recipefork.core.recipe.ShareReading
import app.recipefork.core.recipe.Step
import app.recipefork.core.scaling.UnitLabels
import app.recipefork.core.scaling.InterfaceLanguage
import kotlinx.coroutines.flow.first

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
internal class StepDisplay(val content: StepContent, private val quantity: ReferenceQuantity) {
    val text: String
    val references: List<ShownReference>

    init {
        val shown = StringBuilder()
        val references = mutableListOf<ShownReference>()
        var at = 0
        for (reference in content.references.sortedBy { it.textOffset }) {
            val reads = quantity(reference) ?: continue
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
     * The characters from [start] to [end] of the shown text replaced by [inserted].
     *
     * A deletion that touches any character of a reference removes the whole reference. A replacement by text — a
     * keyboard rewriting a word, autocorrect — removes the references it covers whole, and keeps one it only reaches
     * into: the replaced range stops at that reference's edge, and the reference's own characters the keyboard
     * re-commits are dropped from the inserted text, so a reference's characters never become typed text. Text typed
     * into the middle of a reference goes after it. The typed text is changed only where it was replaced, and every
     * other reference stays at its place in the sentence.
     *
     * A deletion that would bring two references to one offset of the text — which a step cannot hold — removes the
     * one before the cursor too, as deleting into it would. Null only where no such reading exists.
     */
    fun edit(start: Int, end: Int, inserted: String): StepEdit? {
        var from = start.coerceIn(0, text.length)
        var to = end.coerceIn(from, text.length)
        var typing = inserted
        if (from == to) {
            inside(from)?.let { from = it.end; to = it.end }
        } else if (typing.isNotEmpty()) {
            inside(from)?.let { reference ->
                val cut = text.substring(from, minOf(reference.end, to))
                typing = typing.removePrefix(cut)
                from = reference.end
            }
            inside(to)?.let { reference ->
                val cut = text.substring(maxOf(reference.start, start), to)
                typing = typing.removeSuffix(cut)
                to = reference.start
            }
            if (from > to) to = from
        }
        var removed = if (from < to) references.filter { it.start < to && from < it.end } else emptyList()
        fun kept() = references.filter { it !in removed }
        if (typing.isEmpty() && from < to) {
            // Two references brought together: the one before the cursor goes as well.
            val after = kept().filter { it.start >= to }.map { it.reference.textOffset - (typed(to) - typed(from)) }.toSet()
            removed = removed + kept().filter { it.end <= from && it.reference.textOffset in after }
        }
        val typedFrom = typed(from)
        val typedTo = typed(to)
        val content = StepContent(content.text.substring(0, typedFrom) + typing + content.text.substring(typedTo), kept().map {
            if (it.start >= to) it.reference.copy(textOffset = it.reference.textOffset + typing.length - (typedTo - typedFrom)) else it.reference
        })
        if (content.references.distinctBy { it.textOffset }.size != content.references.size) return null
        val cursor = from - removed.sumOf { overlap(it.start, it.end, 0, from) } + typing.length
        if (typing.isEmpty() && removed.isNotEmpty()) {
            // Deleted into a reference: single spacing is left where it stood (ReferenceSpacing.closeUp).
            val closed = ReferenceSpacing.closeUp(Step(0, content.text, content.references), typedFrom)
            val shift = if ((closed.removedAt ?: typedFrom) < typedFrom) 1 else 0
            return StepEdit(StepContent(closed.step.text, closed.step.ingredientReferences), cursor - shift, true)
        }
        return StepEdit(content, cursor, removed.isNotEmpty())
    }

    /** Whether [range] reaches into a reference without covering it whole: a keyboard's composing region must not. */
    fun splits(range: TextRange): Boolean = inside(range.min) != null || inside(range.max) != null

    /**
     * A reference to the ingredient at [ingredientPosition] — to its whole, or to the part [share] of it — put at the
     * shown position [at], spaced from a word on either side ([ReferenceSpacing.insert]), and the cursor after it and
     * any space typed after it. Next to another reference at that offset of the text, the new one goes on the side of
     * it the cursor stands, a space between.
     */
    fun insert(at: Int, ingredientPosition: Int, share: IngredientShare? = null): StepEdit {
        val p = inside(at)?.end ?: at
        val offset = typed(p)
        val there = references.firstOrNull { it.reference.textOffset == offset }
        val inserted = ReferenceSpacing.insert(Step(0, content.text, content.references), ingredientPosition, offset, afterExisting = there != null && there.end <= p, share = share)
        val spaced = StepContent(inserted.step.text, inserted.step.ingredientReferences)
        val end = StepDisplay(spaced, quantity).references.first { it.reference == inserted.reference }.end
        return StepEdit(spaced, end + if (inserted.spaceAfter) 1 else 0, false)
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

    /**
     * The shown text with each reference set apart from the typed text by weight and an underline, not colour alone; a
     * part of an ingredient as a whole is. A reference [marked] — a part more than its whole — is tinted [warning].
     */
    fun styled(highlight: Color, warning: Color = highlight, marked: (IngredientReference) -> Boolean = { false }): AnnotatedString =
        buildAnnotatedString {
            append(text)
            references.forEach {
                val background = if (marked(it.reference)) warning else highlight
                addStyle(
                    SpanStyle(fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline, background = background),
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
    /** How many characters the change takes out and puts in. */
    val size: Int get() = end - start + inserted.length

    companion object {
        /**
         * The change from [old] to [new]: the smallest one, and, of equally small ones, the one the selections place
         * — where the cursor was and is.
         */
        fun between(old: TextFieldValue, new: TextFieldValue): TextChange {
            val placed = of(old.text, new.text, minOf(old.selection.min, new.selection.min), old.text.length - old.selection.max, new.text.length - new.selection.max)
            val smallest = of(old.text, new.text, Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE)
            return if (smallest.size < placed.size) smallest else placed
        }

        private fun of(a: String, b: String, prefixLimit: Int, oldTail: Int, newTail: Int): TextChange {
            val limit = minOf(a.length, b.length, prefixLimit)
            var prefix = 0
            while (prefix < limit && a[prefix] == b[prefix]) prefix++
            val suffixLimit = minOf(a.length - prefix, b.length - prefix, oldTail.coerceAtLeast(0), newTail.coerceAtLeast(0))
            var suffix = 0
            while (suffix < suffixLimit && a[a.length - 1 - suffix] == b[b.length - 1 - suffix]) suffix++
            return TextChange(prefix, a.length - suffix, b.substring(prefix, b.length - suffix))
        }
    }
}

/**
 * Positions of the text [from] read in the text [to], where the two differ in one stretch: before it and after it a
 * position keeps its place; inside it, [low] goes to the stretch's start and [high] to its end.
 */
internal class Positions(from: String, to: String) {
    private val prefix: Int
    private val suffix: Int
    private val fromLength = from.length
    private val toLength = to.length

    init {
        var p = 0
        while (p < minOf(from.length, to.length) && from[p] == to[p]) p++
        var s = 0
        while (s < minOf(from.length, to.length) - p && from[from.length - 1 - s] == to[to.length - 1 - s]) s++
        prefix = p
        suffix = s
    }

    fun low(p: Int): Int = when {
        p <= prefix -> p
        p >= fromLength - suffix -> p - fromLength + toLength
        else -> prefix
    }

    fun high(p: Int): Int = when {
        p <= prefix -> p
        p >= fromLength - suffix -> p - fromLength + toLength
        else -> toLength - suffix
    }
}

/** Undo or redo pressed on a keyboard: Ctrl+Z, Ctrl+Shift+Z or Ctrl+Y. */
private fun KeyEvent.isUndo(): Boolean = isCtrlPressed && (key == Key.Z || key == Key.Y)

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

    /**
     * True from a reference being chosen — or the offer `@` opened being dismissed — until the field has focus again
     * with the keyboard shown: the offer took them, and the author types on where the reference went.
     */
    var refocus by mutableStateOf(false)

    /**
     * What the field's own buffer holds where it differs from what the step shows: after an edit the field was not
     * given back as it came — a reference removed whole, typing moved past one — and until the field is redrawn, a
     * keyboard's next edit is made to this text, not to the step's.
     */
    private var buffer: TextFieldValue? = null

    /** What the field shows for [display]: its text, the cursor off the inside of every reference. */
    fun shown(display: StepDisplay) = TextFieldValue(
        display.text,
        display.snap(selection ?: TextRange(display.text.length)),
        composition?.takeIf { it.max <= display.text.length && !display.splits(it) },
    )

    /**
     * The field reports [new]. Read against whichever text the field's buffer held — the step as shown in [now], or
     * the [buffer] of an edit made since that the screen has not yet redrawn — and applied to the step. The step's new
     * content goes to [commit], [redisplay] showing it. Returns where a just-typed [TRIGGER] stands, or null.
     */
    fun change(new: TextFieldValue, now: StepDisplay, redisplay: (StepContent) -> StepDisplay, commit: (StepContent) -> Unit): Int? {
        val shown = shown(now)
        val held = buffer
        // The text the keyboard edited: the one from which its edit is the smaller.
        val stale = held != null && held.text != shown.text && TextChange.between(held, new).size < TextChange.between(shown, new).size
        val base = if (stale) held!! else shown
        val map = if (stale) Positions(base.text, shown.text) else null
        if (new.text == base.text) {
            selection = now.snap(map?.let { TextRange(it.low(new.selection.start), it.low(new.selection.end)) } ?: new.selection, shown.selection)
            composition = new.composition.takeIf { !stale }
            if (!stale) buffer = null
            return null
        }
        val raw = TextChange.between(base, new)
        val change = when {
            map == null -> raw
            raw.start == raw.end -> TextChange(map.low(raw.start), map.low(raw.start), raw.inserted)
            else -> TextChange(map.low(raw.start), maxOf(map.low(raw.start), map.high(raw.end)), raw.inserted)
        }
        val edit = now.edit(change.start, change.end, change.inserted)
        if (edit == null) {
            buffer = new
            version++
            return null
        }
        commit(edit.content)
        val next = redisplay(edit.content)
        if (stale || edit.removedAReference || next.text != new.text) {
            selection = TextRange(edit.cursor)
            composition = null
            buffer = new.takeIf { next.text != it.text }
        } else {
            selection = next.snap(new.selection, shown.selection)
            composition = new.composition?.takeIf { !next.splits(it) }
            buffer = null
        }
        version++
        // The trigger as the last character typed; text pasted or typed after it opens nothing.
        return if (change.inserted.endsWith(TRIGGER) && change.start == change.end && !edit.removedAReference) {
            edit.cursor - TRIGGER.length
        } else {
            null
        }
    }

    /** The field was given what the step shows: its buffer is that text again, and the next edit is made to it. */
    fun redrawn() {
        buffer = null
    }

    /** Changed on every edit, so the field is redrawn — and its buffer replaced — even when nothing it shows changed. */
    var version by mutableStateOf(0)
        private set

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
    val display = current()
    val highlight = MaterialTheme.colorScheme.secondaryContainer
    val warning = MaterialTheme.colorScheme.errorContainer
    // Parts more than their ingredient's quantity as it stands: marked, never refused (the author may change either).
    val rows = editor.recipe.value?.ingredients ?: ingredients
    val oversized = display.references.mapNotNull { shown ->
        val share = shown.reference.share ?: return@mapNotNull null
        val row = rows.firstOrNull { it.position == shown.reference.ingredientPosition } ?: return@mapNotNull null
        if (IngredientShares.exceeds(share, row)) shown.reference to (display.text.substring(shown.start, shown.end) to ReferenceText.authored(row, language)) else null
    }.toMap()
    state.version // Read, so that every edit redraws the field.
    SideEffect { state.redrawn() }
    val keyboard = LocalSoftwareKeyboardController.current
    val window = LocalWindowInfo.current
    if (state.refocus) {
        LaunchedEffect(state) {
            // Once the offer is gone and this window has its focus back, so the keyboard serves this field again.
            snapshotFlow { !state.offering && window.isWindowFocused }.first { it }
            runCatching { state.focus.requestFocus() }
            keyboard?.show()
            state.refocus = false
        }
    }
    val interaction = remember { MutableInteractionSource() }
    val colors = OutlinedTextFieldDefaults.colors()
    val shown = state.shown(display)
    val transformation = VisualTransformation { text ->
        // The shown text is the field's own text: only its style changes, so every offset maps to itself.
        if (text.text == display.text) TransformedText(display.styled(highlight, warning) { it in oversized }, OffsetMapping.Identity)
        else TransformedText(text, OffsetMapping.Identity)
    }
    // An outlined field as dense as its touch target allows (A11Y-003), so that a one-line step takes little height: no
    // floating label above its text, but the step's number before it — read by the screen reader as the step's name.
    val name = stringResource(R.string.editor_step, position + 1)
    BasicTextField(
        value = shown,
        onValueChange = { new ->
            val trigger = state.change(
                new,
                current(),
                redisplay = { StepDisplay(it, ReferenceText.authored(editor.recipe.value?.ingredients ?: ingredients, language)) },
                commit = { editor.setStepText(position, it.text, it.references) },
            )
            if (trigger != null) {
                state.trigger = trigger
                state.offering = true
            }
        },
        visualTransformation = transformation,
        textStyle = LocalTextStyle.current.merge(TextStyle(color = MaterialTheme.colorScheme.onSurface)),
        cursorBrush = SolidColor(colors.cursorColor),
        interactionSource = interaction,
        // No undo: an earlier value of the field would bring a removed reference back as typed text.
        modifier = modifier
            .heightIn(min = 48.dp)
            .semantics(mergeDescendants = true) {}
            .focusRequester(state.focus)
            .onPreviewKeyEvent { it.isUndo() },
        decorationBox = { inner ->
            OutlinedTextFieldDefaults.DecorationBox(
                value = shown.text,
                innerTextField = inner,
                enabled = true,
                singleLine = false,
                visualTransformation = transformation,
                interactionSource = interaction,
                prefix = {
                    Text(
                        stringResource(R.string.editor_step_number, position + 1),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clearAndSetSemantics { text = AnnotatedString(name) },
                    )
                },
                supportingText = if (oversized.isEmpty()) null else {
                    {
                        Text(
                            oversized.values.map { (part, whole) -> stringResource(R.string.editor_step_part_more_than_whole, part, whole) }.joinToString("\n"),
                            modifier = Modifier.testTag(EditorTags.stepPartWarning(position)),
                        )
                    }
                },
                colors = colors,
                contentPadding = DENSE_FIELD_PADDING,
                container = { OutlinedTextFieldDefaults.Container(enabled = true, isError = false, interactionSource = interaction, colors = colors) },
            )
        },
    )
}

/** Between a step field's outline and its text: dense, so that one line of text makes a field of about 48 dp. */
private val DENSE_FIELD_PADDING = PaddingValues(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)

/**
 * The control that inserts a reference into the step at [position] (FLOW-031 (2)), and the offer of this recipe's
 * [ingredients] it opens — the same offer typing [TRIGGER] opens. Every row of this recipe is offered, one with no
 * quantity yet and one "to taste" included, and no row of any other recipe (FLOW-031 (3), DOM-007). Choosing one
 * puts its reference at the cursor, or in place of the typed [TRIGGER], spaced from the words around it; dismissing the
 * offer leaves the text as typed. Either way the author types on: choosing, or dismissing the offer [TRIGGER] opened,
 * gives the step's field its focus and the keyboard back, the cursor after the reference and any space typed after it.
 *
 * Each row holding a quantity is offered a second time, as a part of it ([IngredientShares.offered]): choosing that
 * asks for the part in the row's own unit ([PartDialog]), and the reference then reads that part instead of the whole.
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
    var asking by remember { mutableStateOf<AskedPart?>(null) }
    Box {
        BlockAction(
            icon = R.drawable.ic_insert_ingredient,
            label = stringResource(R.string.editor_step_insert_ingredient_label, position + 1),
            tag = EditorTags.stepInsert(position),
            action = stringResource(R.string.editor_step_insert_ingredient_action, position + 1),
            onClick = {
                state.trigger = null
                state.offering = true
            },
        )
        DropdownMenu(
            expanded = state.offering,
            onDismissRequest = {
                // Dismissing the offer the author's typing opened returns them to their typing.
                if (state.trigger != null) state.refocus = true
                state.close()
            },
        ) {
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
                        state.refocus = true
                        state.close()
                    },
                    modifier = Modifier.testTag(EditorTags.stepOffer(position, row)),
                )
                if (IngredientShares.offered(ingredient)) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.editor_step_offered_part, name)) },
                        onClick = {
                            // The menu goes; where the `@` stands is kept for the part's insertion.
                            asking = AskedPart(row, name, state.trigger)
                            state.offering = false
                        },
                        modifier = Modifier.testTag(EditorTags.stepOfferPart(position, row)),
                    )
                }
            }
        }
    }
    asking?.let { asked ->
        val ingredient = ingredients.getOrNull(asked.row)
        if (ingredient == null) {
            asking = null
            return@let
        }
        PartDialog(
            name = asked.name,
            ingredient = ingredient,
            language = language,
            onInsert = { share ->
                asking = null
                state.trigger = asked.trigger
                choose(position, asked.row, language, editor, state, share)
                state.refocus = true
                state.close()
            },
            onDismiss = {
                asking = null
                // Dismissing the part the author's typing asked for returns them to their typing.
                if (asked.trigger != null) state.refocus = true
                state.close()
            },
        )
    }
}

/** A part being asked for: of the row at [row], named [name], for the offer opened at [trigger] (or at the cursor). */
private data class AskedPart(val row: Int, val name: String, val trigger: Int?)

/**
 * Asks for a part of [ingredient], named [name], in its own unit: typed and read like its quantity field (DOM-019),
 * held only on its unit type's grid and above zero (DOM-017), and no more than its quantity. Until it is, the field
 * says what it accepts and nothing is inserted.
 */
@Composable
private fun PartDialog(
    name: String,
    ingredient: Ingredient,
    language: InterfaceLanguage,
    onInsert: (IngredientShare) -> Unit,
    onDismiss: () -> Unit,
) {
    var typed by remember { mutableStateOf("") }
    val reading = IngredientShares.read(typed, ingredient)
    val unit = ingredient.unit?.let { UnitLabels.of(it, language) }.orEmpty()
    val whole = ReferenceText.authored(ingredient, language)
    val focus = remember { FocusRequester() }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(EditorTags.PART_DIALOG),
        title = { Text(stringResource(R.string.editor_part_title, name)) },
        text = {
            OutlinedTextField(
                value = typed,
                onValueChange = { typed = it },
                label = { Text(stringResource(R.string.editor_part_field, unit)) },
                singleLine = true,
                isError = reading is ShareReading.Refused || reading is ShareReading.MoreThanWhole,
                supportingText = {
                    val message = when (reading) {
                        is ShareReading.Refused -> stringResource(problemMessage(reading.problem))
                        ShareReading.MoreThanWhole -> stringResource(R.string.editor_part_more_than_whole, whole)
                        else -> stringResource(R.string.editor_part_of_whole, whole)
                    }
                    Text(message, modifier = Modifier.testTag(EditorTags.PART_PROBLEM))
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { (reading as? ShareReading.Accepted)?.let { onInsert(it.share) } }),
                modifier = Modifier.focusRequester(focus).testTag(EditorTags.PART_FIELD),
            )
            LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
        },
        confirmButton = {
            TextButton(
                onClick = { (reading as? ShareReading.Accepted)?.let { onInsert(it.share) } },
                enabled = reading is ShareReading.Accepted,
                modifier = Modifier.testTag(EditorTags.PART_INSERT),
            ) { Text(stringResource(R.string.editor_part_insert)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag(EditorTags.PART_CANCEL)) {
                Text(stringResource(R.string.editor_part_cancel))
            }
        },
    )
}

/**
 * The reference to the row at [row] — its whole, or the part [share] of it — put where the offer was opened from: in
 * place of the typed `@`, or at the cursor.
 */
private fun choose(
    position: Int,
    row: Int,
    language: InterfaceLanguage,
    editor: RecipeEditor,
    state: StepFieldState,
    share: IngredientShare? = null,
) {
    val recipe = editor.recipe.value ?: return
    val step = recipe.steps.sortedBy { it.position }.getOrNull(position) ?: return
    var display = StepDisplay.of(step, recipe.ingredients, language)
    var at = display.snap(state.selection ?: TextRange(display.text.length)).max
    val trigger = state.trigger
    if (trigger != null && display.text.startsWith(TRIGGER, trigger)) {
        // Between two references the `@` gives way to a space, so that they keep two places in the text.
        val removed = display.edit(trigger, trigger + TRIGGER.length, "")?.takeIf { !it.removedAReference }
            ?: display.edit(trigger, trigger + TRIGGER.length, " ")
            ?: return
        display = StepDisplay(removed.content, ReferenceText.authored(recipe.ingredients, language))
        at = removed.cursor
    }
    val inserted = display.insert(at, row, share)
    editor.setStepText(position, inserted.content.text, inserted.content.references)
    state.selection = TextRange(inserted.cursor)
    state.composition = null
}
