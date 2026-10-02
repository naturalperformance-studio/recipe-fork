package app.recipefork.app.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.IngredientReference
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.ReferenceText
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.StepText
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.InterfaceLanguage
import app.recipefork.core.scaling.Rational
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A step's field as a model: references one unit each, the typed text around them kept exactly (FLOW-031 (4)). */
class StepDisplayTest {
    private val flour = Ingredient(0, null, null, "мука", UnitType.EXACT, QuantityUnit.GRAM, Rational.of(200))
    private val salt = Ingredient(1, null, null, "соль", UnitType.TASTE_UNMEASURED, null, null)
    private val rows = listOf(flour, salt)
    private val step = Step(0, "Всыпьте  муки", listOf(IngredientReference(0, 8)))

    private fun display(text: String = step.text, references: List<IngredientReference> = step.ingredientReferences) =
        StepDisplay.of(Step(0, text, references), rows, InterfaceLanguage.RUSSIAN)

    @Test
    fun theFieldShowsTheStepAsTheSharedRenderingReadsIt() {
        val shown = display()
        assertEquals("Всыпьте 200 г муки", shown.text)
        assertEquals(StepText.render(step, ReferenceText.authored(rows, InterfaceLanguage.RUSSIAN)), shown.text)
        assertEquals(listOf(ShownReference(8, 13, IngredientReference(0, 8))), shown.references)
    }

    // FLOW-031 (4): deleting into a reference from after it removes it whole and leaves the typed text exactly.
    @Test
    fun deletingIntoAReferenceRemovesItWholeAndLeavesTheText() {
        val back = display().edit(12, 13, "")!!
        assertEquals(StepContent("Всыпьте  муки", emptyList()), back.content)
        assertEquals(8, back.cursor)
        assertTrue(back.removedAReference)
        val forward = display().edit(8, 9, "")!!
        assertEquals(StepContent("Всыпьте  муки", emptyList()), forward.content)
        // A selection reaching into it from the typed text: the typed characters go, and the reference.
        val across = display().edit(6, 10, "")!!
        assertEquals(StepContent("Всыпьт муки", emptyList()), across.content)
    }

    // Typing before or after a reference keeps it at its place in the sentence.
    @Test
    fun typingAroundAReferenceKeepsItAtItsPlace() {
        val before = display().edit(0, 0, "Теперь в")!!
        assertEquals(StepContent("Теперь вВсыпьте  муки", listOf(IngredientReference(0, 16))), before.content)
        val after = display().edit(14, 18, "сахара")!!
        assertEquals(StepContent("Всыпьте  сахара", listOf(IngredientReference(0, 8))), after.content)
        val rightAfter = display().edit(13, 13, ",")!!
        assertEquals(StepContent("Всыпьте , муки", listOf(IngredientReference(0, 8))), rightAfter.content)
        val rightBefore = display().edit(8, 8, "ещё ")!!
        assertEquals(StepContent("Всыпьте ещё  муки", listOf(IngredientReference(0, 12))), rightBefore.content)
        val deletedBefore = display().edit(0, 3, "")!!
        assertEquals(StepContent("пьте  муки", listOf(IngredientReference(0, 5))), deletedBefore.content)
        // Typed inside a reference: the typing goes after it, and the reference stays whole.
        val inside = display().edit(10, 10, "x")!!
        assertEquals(StepContent("Всыпьте x муки", listOf(IngredientReference(0, 8))), inside.content)
    }

    // The cursor never stands inside a reference; a selection grows to take it in whole.
    @Test
    fun theCursorDoesNotLandInsideAReference() {
        val shown = display()
        assertEquals(TextRange(8), shown.snap(TextRange(9)))
        assertEquals(TextRange(13), shown.snap(TextRange(12)))
        // Moved by one character, it goes on past the reference.
        assertEquals(TextRange(13), shown.snap(TextRange(9), before = TextRange(8)))
        assertEquals(TextRange(8), shown.snap(TextRange(12), before = TextRange(13)))
        assertEquals(TextRange(6, 13), shown.snap(TextRange(6, 10)))
        assertEquals(TextRange(13, 8), shown.snap(TextRange(12, 9)))
        assertEquals(TextRange(3), shown.snap(TextRange(3)))
    }

    // FLOW-031 (1, 2): a reference put at the cursor; next to another, a space keeps the two at two offsets.
    @Test
    fun insertingAReference() {
        val empty = display("Всыпьте  муки", emptyList())
        val put = empty.insert(8, 0)
        assertEquals(step.ingredientReferences, put.content.references)
        assertEquals(13, put.cursor)
        val second = display().insert(13, 1)
        assertEquals(StepContent("Всыпьте   муки", listOf(IngredientReference(0, 8), IngredientReference(1, 9))), second.content)
        assertEquals("Всыпьте 200 г по вкусу муки", display(second.content.text, second.content.references).text)
        val first = display().insert(8, 1)
        assertEquals(StepContent("Всыпьте   муки", listOf(IngredientReference(1, 8), IngredientReference(0, 9))), first.content)
        assertEquals(16, first.cursor)
    }

    // Two references brought to one offset cannot be held: the edit is not made.
    @Test
    fun anEditThatWouldMergeTwoReferencesIsNotMade() {
        val two = display("a b", listOf(IngredientReference(0, 1), IngredientReference(1, 2)))
        assertEquals("a200 г по вкусуb", two.text)
        assertNull(two.edit(6, 7, ""))
    }

    @Test
    fun aReferenceIsSetApartByWeightAndUnderlineNotColourAlone() {
        val styled = display().styled(Color.Yellow)
        val span = styled.spanStyles.single()
        assertEquals(8, span.start)
        assertEquals(13, span.end)
        assertEquals(FontWeight.Bold, span.item.fontWeight)
        assertEquals(TextDecoration.Underline, span.item.textDecoration)
    }

    // The selections settle which of two equal characters was deleted or typed.
    @Test
    fun aChangeIsReadWhereTheCursorWas() {
        assertEquals(TextChange(0, 1, ""), TextChange.between(TextFieldValue("aa", TextRange(1)), TextFieldValue("a", TextRange(0))))
        assertEquals(TextChange(1, 2, ""), TextChange.between(TextFieldValue("aa", TextRange(2)), TextFieldValue("a", TextRange(1))))
        assertEquals(TextChange(0, 0, "a"), TextChange.between(TextFieldValue("a", TextRange(0)), TextFieldValue("aa", TextRange(1))))
        assertEquals(TextChange(2, 2, "@"), TextChange.between(TextFieldValue("ab", TextRange(2)), TextFieldValue("ab@", TextRange(3))))
        assertFalse(TextChange.between(TextFieldValue("ab", TextRange(2)), TextFieldValue("xy", TextRange(2))).inserted.isEmpty())
    }
}
