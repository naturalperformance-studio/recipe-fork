package app.recipefork.core.recipe

import app.recipefork.core.scaling.InterfaceLanguage
import app.recipefork.core.scaling.Rational
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReferenceSpacingTest {
    private val flour = Ingredient(0, null, null, "мука", UnitType.EXACT, QuantityUnit.GRAM, Rational.of(200))
    private val salt = Ingredient(1, null, null, "соль", UnitType.TASTE_UNMEASURED, null, null)
    private val russian = ReferenceText.authored(listOf(flour, salt), InterfaceLanguage.RUSSIAN)

    private fun reads(step: Step) = StepText.render(step, russian)

    /** "|" in [typed] marks where the flour goes; the step as it then reads. */
    private fun inserted(typed: String): String {
        val at = typed.indexOf('|')
        return reads(ReferenceSpacing.insert(Step(0, typed.replace("|", "")), 0, at, afterExisting = false).step)
    }

    // A reference against a word gets a space on that side.
    @Test
    fun `a reference put against a word is spaced from it`() {
        assertEquals("Всыпьте 200\u00A0г муки", inserted("Всыпьте|муки"))
        assertEquals("Всыпьте 200\u00A0г муки", inserted("Всыпьте |муки"))
        assertEquals("Всыпьте 200\u00A0г муки", inserted("Всыпьте| муки"))
        assertEquals("Всыпьте 200\u00A0г муки", inserted("Всыпьте | муки"))
        val result = ReferenceSpacing.insert(Step(0, "Всыпьтемуки"), 0, 7, afterExisting = false)
        assertEquals(Step(0, "Всыпьте  муки", listOf(IngredientReference(0, 8))), result.step)
        assertTrue(result.spaceAfter)
    }

    // None at the start or the end of the text, next to a space or a line break, or before punctuation.
    @Test
    fun `no space at an edge, next to a space or before punctuation`() {
        assertEquals("200\u00A0г муки", inserted("|муки"))
        assertEquals("Добавьте 200\u00A0г", inserted("Добавьте|"))
        assertEquals("200\u00A0г", inserted("|"))
        listOf(",", ".", ":", ";", ")", "!", "?").forEach { mark ->
            assertEquals("Добавьте 200\u00A0г$mark", inserted("Добавьте|$mark"))
        }
        assertEquals("Муки (200\u00A0г)", inserted("Муки (|)"))
        assertEquals("Муки:\n200\u00A0г", inserted("Муки:\n|"))
        assertFalse(ReferenceSpacing.insert(Step(0, "Добавьте."), 0, 8, afterExisting = false).spaceAfter)
    }

    // Next to another reference at the same offset: a space between the two, on the side the new one goes.
    @Test
    fun `next to another reference the two are spaced apart`() {
        val step = Step(0, "Всыпьте  муки", listOf(IngredientReference(0, 8)))
        val after = ReferenceSpacing.insert(step, 1, 8, afterExisting = true)
        assertEquals(Step(0, "Всыпьте   муки", listOf(IngredientReference(0, 8), IngredientReference(1, 9))), after.step)
        assertFalse(after.spaceAfter)
        val before = ReferenceSpacing.insert(step, 1, 8, afterExisting = false)
        assertEquals(Step(0, "Всыпьте   муки", listOf(IngredientReference(1, 8), IngredientReference(0, 9))), before.step)
        assertTrue(before.spaceAfter)
        assertEquals("Всыпьте по\u00A0вкусу 200\u00A0г муки", reads(before.step))
        // Against a word on the other side too.
        val tight = ReferenceSpacing.insert(Step(0, "иx", listOf(IngredientReference(0, 1))), 1, 1, afterExisting = true)
        assertEquals("и200\u00A0г по\u00A0вкусу x", reads(tight.step))
    }

    /** The step [typed] with the flour at "|", closed up where it is taken out; the step as it then reads. */
    private fun removed(typed: String): String {
        val at = typed.indexOf('|')
        return reads(ReferenceSpacing.closeUp(Step(0, typed.replace("|", "")), at).step)
    }

    // Single spacing is left where a reference is taken out.
    @Test
    fun `a reference taken out leaves single spacing`() {
        assertEquals("Всыпьте муки", removed("Всыпьте | муки"))
        assertEquals("муки", removed("| муки"))
        assertEquals("Добавьте", removed("Добавьте |"))
        assertEquals("добавьте.", removed("добавьте |."))
        listOf(",", ":", ";", ")", "!", "?").forEach { mark -> assertEquals("добавьте$mark x", removed("добавьте |$mark x")) }
        assertEquals("Муки:\nвсыпьте", removed("Муки:\n| всыпьте"))
        assertEquals("Всыпьте\nмуки", removed("Всыпьте |\nмуки"))
        // Nothing to close up: the text stays as typed.
        assertEquals("Всыпьтемуки", removed("Всыпьте|муки"))
        assertEquals("Всыпьте муки", removed("Всыпьте |муки"))
        assertEquals("(муки)", removed("(|муки)"))
        assertNull(ReferenceSpacing.closeUp(Step(0, "Всыпьте муки"), 8).removedAt)
    }

    @Test
    fun `closing up keeps the other references at their place`() {
        val step = Step(0, "a   b ", listOf(IngredientReference(1, 1), IngredientReference(0, 6)))
        val closed = ReferenceSpacing.closeUp(step, 2)
        assertEquals(Step(0, "a  b ", listOf(IngredientReference(1, 1), IngredientReference(0, 5))), closed.step)
        assertEquals(2, closed.removedAt)
        // Next to another reference nothing goes.
        val against = Step(0, "a  b", listOf(IngredientReference(1, 2)))
        assertEquals(against, ReferenceSpacing.closeUp(against, 2).step)
    }

    // An ingredient deleted from the recipe: each of its references goes, closed up where it stood.
    @Test
    fun `references to a deleted ingredient go with their spaces`() {
        val step = Step(0, "Всыпьте  муки и  соли, затем ", listOf(IngredientReference(0, 8), IngredientReference(1, 16), IngredientReference(0, 29)))
        val left = ReferenceSpacing.withoutReferencesTo(step, 0)
        assertEquals(Step(0, "Всыпьте муки и  соли, затем", listOf(IngredientReference(1, 15))), left)
        assertEquals("Всыпьте муки и по\u00A0вкусу соли, затем", reads(left))
    }
}
