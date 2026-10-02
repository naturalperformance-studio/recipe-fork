package app.recipefork.core.timer

import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.Timer
import kotlin.test.Test
import kotlin.test.assertEquals
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
