package app.recipefork.core.timer

import app.recipefork.core.recipe.IngredientReference
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.StepRun
import app.recipefork.core.recipe.Timer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class TimerNameTest {
    // FLOW-016 (4): 60 characters or fewer is the whole text, with nothing marking it as cut.
    @Test
    fun `a step of 60 characters or fewer is its whole text with no mark`() {
        assertEquals("курица в духовку на 30 минут", TimerName.derived("курица в духовку на 30 минут"))
        val sixty = "a".repeat(10) + " " + "b".repeat(49)
        assertEquals(60, sixty.length)
        assertEquals(sixty, TimerName.derived(sixty))
        val fiftyNine = sixty.dropLast(1)
        assertEquals(fiftyNine, TimerName.derived(fiftyNine))
    }

    // FLOW-016 (3): longer text is cut at a word boundary within the first 60 characters, with an ellipsis.
    @Test
    fun `a longer step is cut at the last word boundary within 60 characters, with an ellipsis`() {
        val text = "Bake the chicken in the oven at 190 degrees until the skin is golden and crisp"
        val name = TimerName.derived(text)
        assertEquals("Bake the chicken in the oven at 190 degrees until the skin…", name)
        assertTrue(name.removeSuffix(TimerName.ELLIPSIS).length <= 60)
        assertTrue(text.startsWith(name.removeSuffix(TimerName.ELLIPSIS)))
    }

    @Test
    fun `a 61-character step whose 60th character ends a word keeps those 60, then the ellipsis`() {
        val first = "word ".repeat(11) + "abcde" // 60 characters, ending in a whole word
        assertEquals(60, first.length)
        assertEquals("$first…", TimerName.derived("$first z"))
        assertEquals("$first…", TimerName.derived("$first, and so on"))
    }

    @Test
    fun `a cut never leaves half a word`() {
        val text = "word ".repeat(11) + "abcdefgh" // the 60th character falls inside "abcdefgh"
        assertEquals("word ".repeat(11).trimEnd() + "…", TimerName.derived(text))
    }

    @Test
    fun `a cut at a hyphen inside a word leaves the whole word out`() {
        val before = "word ".repeat(10) + "abcdef " // 57 characters; the 61st is the hyphen of "кое-где"
        assertEquals(57, before.length)
        assertEquals(before.trimEnd() + "…", TimerName.derived(before + "кое-где лежит соль"))
    }

    @Test
    fun `a cut at an apostrophe inside a word leaves the whole word out`() {
        val before = "word ".repeat(10) + "abcdef " // the 61st character is the apostrophe of "don't"
        assertEquals(before.trimEnd() + "…", TimerName.derived(before + "don't stir it yet"))
        assertEquals(before.trimEnd() + "…", TimerName.derived(before + "don\u2019t stir it yet"))
    }

    @Test
    fun `a cut at a combining mark never parts it from its letter`() {
        val before = "word ".repeat(11) + "abc " // 59 characters; the 61st is the accent combining with "e"
        assertEquals(59, before.length)
        assertEquals(before.trimEnd() + "…", TimerName.derived(before + "e\u0301tude for the oven"))
    }

    @Test
    fun `a single word longer than 60 characters is cut at 60`() {
        val word = "x".repeat(75)
        assertEquals("x".repeat(60) + "…", TimerName.derived(word))
    }

    @Test
    fun `a name is one line, whatever breaks the step holds`() {
        assertEquals("Knead. Let it rise.", TimerName.derived("  Knead.\n\nLet it   rise.\n"))
    }

    @Test
    fun `a character outside the basic plane counts once and is never split`() {
        val text = "🍞".repeat(70) // 70 loaves, 140 UTF-16 units
        val name = TimerName.derived(text)
        assertEquals("🍞".repeat(60) + "…", name)
        assertEquals("🍞".repeat(30), TimerName.derived("🍞".repeat(30)))
    }

    @Test
    fun `an empty step derives an empty name`() {
        assertEquals("", TimerName.derived(""))
        assertEquals("", TimerName.derived("  \n "))
    }

    // FLOW-016 (5, 6, 7): a given name is the name whatever the step says; with none, the name follows the step.
    @Test
    fun `a timer's name is its given name, or else the one its step derives`() {
        val long = "Bake the chicken in the oven at 190 degrees until the skin is golden and crisp"
        assertEquals("Oven", TimerName.of(Step(0, long, timer = Timer(30.minutes, "Oven"))))
        assertEquals("Oven", TimerName.of(Step(0, "Something else entirely", timer = Timer(30.minutes, "Oven"))))
        assertEquals(TimerName.derived(long), TimerName.of(Step(0, long, timer = Timer(30.minutes))))
        assertEquals("Rest the dough", TimerName.of(Step(0, "Rest the dough", timer = Timer(30.minutes))))
        assertNull(TimerName.of(Step(0, long)))
        // A given name over 60 characters is kept whole.
        val given = "n".repeat(80)
        assertEquals(given, TimerName.of(Step(0, long, timer = Timer(1.minutes, given))))
    }
}

class TimerNameWithReferencesTest {
    private val quantities = mapOf(0 to "200 г", 1 to "по вкусу")
    private fun name(step: Step) = TimerName.of(step) { quantities[it] }

    // FLOW-016 (8): a reference within the first 60 characters reads as the screen shows it.
    @Test
    fun `a reference inside the name reads as shown`() {
        val step = Step(0, "Всыпьте  муки и перемешайте", listOf(IngredientReference(0, 8)), Timer(5.minutes))
        assertEquals("Всыпьте 200 г муки и перемешайте", name(step))
    }

    @Test
    fun `a reference wholly inside the first 60 characters of a long step is kept, the cut falling after it`() {
        val text = "Всыпьте  муки, затем медленно перемешивайте венчиком до однородной густой массы"
        val step = Step(0, text, listOf(IngredientReference(0, 8)), Timer(5.minutes))
        val shown = name(step)!!
        assertTrue(shown.startsWith("Всыпьте 200 г муки,"))
        assertTrue(shown.endsWith(TimerName.ELLIPSIS))
        assertTrue(shown.removeSuffix(TimerName.ELLIPSIS).length <= 60)
    }

    // A reference across the cut is one unit: the cut falls before it, never between its number and its unit.
    @Test
    fun `a reference across the 60-character cut is left out whole`() {
        val before = "a".repeat(10) + " " + "b".repeat(45) + " " // 57 characters
        val step = Step(0, before + " муки", listOf(IngredientReference(0, before.length)), Timer(5.minutes))
        // Rendered: 57 characters, then "200 г": the 60th character is the space inside the reference.
        assertEquals(before.trimEnd() + TimerName.ELLIPSIS, name(step))
        val taste = Step(0, before + " соль", listOf(IngredientReference(1, before.length)), Timer(5.minutes))
        assertEquals(before.trimEnd() + TimerName.ELLIPSIS, name(taste))
    }

    @Test
    fun `a reference ending exactly at the cut is kept whole`() {
        val shorter = "a".repeat(10) + " " + "b".repeat(43) + " " // 55 characters, then "200 г" ends at the 60th
        val step = Step(0, shorter + " муки и так далее", listOf(IngredientReference(0, shorter.length)), Timer(5.minutes))
        assertEquals(shorter + "200 г" + TimerName.ELLIPSIS, name(step))
    }

    @Test
    fun `plain text names are unchanged, and a named timer ignores its references`() {
        val step = Step(0, "Всыпьте  муки", listOf(IngredientReference(0, 8)), Timer(5.minutes, "Мука"))
        assertEquals("Мука", name(step))
        assertEquals(TimerName.derived("  Knead.\n\nLet it   rise.\n"), TimerName.derived(listOf(StepRun.Typed("  Knead.\n\nLet it   rise.\n"))))
        assertNull(name(Step(0, "Mix")))
    }
}

/** A held timer's name still fits its step when the reader changed the scale since its start (FLOW-016 (8)). */
class TimerNameFitsTest {
    private val long = "Всыпьте  муки, затем медленно перемешивайте венчиком до однородной густой массы"
    private val flour = Step(0, "Всыпьте  муки и перемешайте", listOf(IngredientReference(0, 8)), Timer(5.minutes))
    private val longFlour = Step(0, long, listOf(IngredientReference(0, 8)), Timer(5.minutes))

    private fun at(step: Step, quantity: String) = TimerName.of(step) { quantity }!!

    @Test
    fun `a name derived at any scale fits its step`() {
        listOf("200 г", "400 г", "1,2 кг", "___ г", "по вкусу").forEach { quantity ->
            assertTrue(TimerName.fits(flour, at(flour, quantity)), quantity)
            assertTrue(TimerName.fits(longFlour, at(longFlour, quantity)), quantity)
        }
    }

    @Test
    fun `a name from other text, or of a timer the step no longer carries, does not fit`() {
        assertFalse(TimerName.fits(flour, "Всыпьте 200 г сахара и перемешайте"))
        assertFalse(TimerName.fits(flour, "Варите 200 г муки и перемешайте"))
        assertFalse(TimerName.fits(flour.copy(timer = null), at(flour, "200 г")))
        assertFalse(TimerName.fits(flour.copy(timer = Timer(5.minutes, "Мука")), at(flour, "200 г")))
        assertTrue(TimerName.fits(flour.copy(timer = Timer(5.minutes, "Мука")), "Мука"))
        assertFalse(TimerName.fits(longFlour, "Насыпьте 200 г муки, затем" + TimerName.ELLIPSIS))
    }

    @Test
    fun `without references a name fits exactly as derived`() {
        val plain = Step(0, "Bring the water to the boil", timer = Timer(10.minutes))
        assertTrue(TimerName.fits(plain, "Bring the water to the boil"))
        assertFalse(TimerName.fits(plain, "Bring the water"))
    }
}
