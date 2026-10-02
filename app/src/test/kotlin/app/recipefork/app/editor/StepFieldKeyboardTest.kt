package app.recipefork.app.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import app.recipefork.core.recipe.IngredientReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A step's field driven as a keyboard drives it, edit by edit (StepFieldState.change): the field's buffer holds what the
 * keyboard last made of it until the screen is redrawn, so several edits can arrive against a text the step no longer
 * shows. Whatever the sequence, a reference's characters never become typed text.
 */
class StepFieldKeyboardTest {
    private val quantities = mapOf(0 to "200 г", 1 to "по вкусу")

    /** The step, the field's state, and the field's buffer as the keyboard sees it. */
    private inner class Field(var content: StepContent, cursor: Int? = null) {
        val state = StepFieldState().apply { selection = cursor?.let { TextRange(it) } }
        var buffer: TextFieldValue = shown()
        var trigger: Int? = null

        fun display() = StepDisplay(content) { quantities[it] }

        fun shown() = state.shown(display())

        /** The screen redrawn: the buffer takes what the field is given. */
        fun redraw() {
            buffer = shown()
        }

        /** The keyboard changes the buffer and the field reports it. */
        fun keyboard(edit: (TextFieldValue) -> TextFieldValue) {
            buffer = edit(buffer)
            trigger = state.change(buffer, display(), { StepDisplay(it) { p -> quantities[p] } }, { content = it })
        }

        fun backspace() = keyboard { b ->
            val at = b.selection.start
            TextFieldValue(b.text.removeRange(at - 1, at), TextRange(at - 1))
        }

        fun type(text: String) = keyboard { b ->
            val at = b.selection.start
            TextFieldValue(b.text.substring(0, at) + text + b.text.substring(at), TextRange(at + text.length))
        }

        /** A keyboard committing [text] over the characters from [start] to [end], as autocorrect and composing do. */
        fun commit(start: Int, end: Int, text: String) = keyboard { b ->
            TextFieldValue(b.text.substring(0, start) + text + b.text.substring(end), TextRange(start + text.length))
        }

        fun compose(start: Int, end: Int) = keyboard { b -> b.copy(composition = TextRange(start, end)) }
    }

    private val flour = StepContent("Всыпьте  муки", listOf(IngredientReference(0, 8)))

    // Two backspaces before the screen redraws: the second is made to the text with the reference's characters still
    // in it, and must not leave "20" behind as typed text.
    @Test
    fun twoBackspacesInOneFrameAfterAReference() {
        val field = Field(flour, cursor = 13)
        field.backspace()
        assertEquals(StepContent("Всыпьте  муки", emptyList()), field.content)
        field.backspace()
        assertEquals(StepContent("Всыпьте  муки", emptyList()), field.content)
        field.redraw()
        assertEquals("Всыпьте  муки", field.buffer.text)
        assertEquals(TextRange(8), field.buffer.selection)
        field.backspace()
        assertEquals(StepContent("Всыпьте муки", emptyList()), field.content)
    }

    // Typing on, several characters in one frame, after the reference was removed or the typing moved past it.
    @Test
    fun typingInOneFrameAfterAnAdjustedEdit() {
        val removed = Field(flour, cursor = 13)
        removed.backspace()
        removed.type("с")
        removed.type("о")
        assertFalse(removed.content.text.any { it.isDigit() })
        assertEquals(emptyList<IngredientReference>(), removed.content.references)

        val moved = Field(flour, cursor = 13)
        moved.keyboard { TextFieldValue("Всыпьте 20x0 г муки", TextRange(11)) }
        moved.type("y")
        assertEquals(flour.references, moved.content.references)
        assertEquals("Всыпьте  муки", moved.content.text.filter { it !in "xy" })
        assertEquals(setOf('x', 'y'), moved.content.text.filter { it in "xy" }.toSet())
        assertEquals(2, moved.content.text.count { it in "xy" })
    }

    @Test
    fun twoCharactersInOneFrameWithNothingAdjusted() {
        val field = Field(flour, cursor = 18)
        field.type(" и")
        field.type(" соли")
        assertEquals(StepContent("Всыпьте  муки и соли", flour.references), field.content)
    }

    // A composing region next to a reference is never held over it, and a commit over it types only new characters.
    @Test
    fun aComposingRewriteNextToAReference() {
        val field = Field(flour, cursor = 13)
        field.compose(12, 13)
        assertNull(field.shown().composition)
        field.commit(12, 13, "гр")
        assertEquals(StepContent("Всыпьте р муки", flour.references), field.content)

        val other = Field(flour, cursor = 13)
        other.compose(12, 13)
        other.commit(12, 13, "кг")
        // Read as "к" put before the reference's "г": it goes after the reference, which stays whole.
        assertEquals(StepContent("Всыпьте к муки", flour.references), other.content)
        // Composing on after the reference, the region now its own.
        other.redraw()
        other.compose(13, 15)
        assertEquals(TextRange(13, 15), other.shown().composition)
    }

    // Autocorrect whose region reaches into a reference, then a second edit in the same frame.
    @Test
    fun anAutocorrectCommitReachingIntoAReference() {
        val field = Field(flour, cursor = 9)
        field.commit(0, 9, "Всыпте 2")
        assertEquals(StepContent("Всыпте  муки", listOf(IngredientReference(0, 7))), field.content)
        field.redraw()
        assertEquals("Всыпте 200 г муки", field.buffer.text)
    }

    // The only space between two references, backspaced: the reference before the cursor goes, nothing is refused.
    @Test
    fun backspacingTheSpaceBetweenTwoReferences() {
        val field = Field(StepContent("a b", listOf(IngredientReference(0, 1), IngredientReference(1, 2))), cursor = 7)
        assertEquals("a200 г по вкусуb", field.buffer.text)
        field.backspace()
        assertEquals(StepContent("ab", listOf(IngredientReference(1, 1))), field.content)
        field.redraw()
        assertEquals(TextRange(1), field.buffer.selection)
    }

    @Test
    fun theTriggerIsReported() {
        val field = Field(StepContent("Всыпьте ", emptyList()), cursor = 8)
        field.type("@")
        assertEquals(8, field.trigger)
    }
}
