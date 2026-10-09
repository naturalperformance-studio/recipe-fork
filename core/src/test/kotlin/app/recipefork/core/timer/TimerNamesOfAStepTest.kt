package app.recipefork.core.timer

import app.recipefork.core.recipe.IngredientReference
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.Timer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * DOM-024 over FLOW-016: each timer of a step announces itself by its own name — given, or derived from the step's
 * text — and an unnamed one of several is told apart by its number; a step with one timer reads as it always did.
 */
class TimerNamesOfAStepTest {
    private val text = "Boil the pasta, meanwhile reduce the sauce"
    private val pasta = Step(0, text, timers = listOf(Timer(10.minutes), Timer(6.minutes, "Sauce"), Timer(2.minutes)))

    @Test
    fun `each timer is named by its own given name or else by its step's text`() {
        assertEquals(text, TimerName.of(pasta, 0))
        assertEquals("Sauce", TimerName.of(pasta, 1))
        assertEquals(text, TimerName.of(pasta, 2))
        assertNull(TimerName.of(pasta, 3))
        assertNull(TimerName.of(pasta, -1))
        assertNull(TimerName.of(Step(0, text), 0))
    }

    @Test
    fun `an unnamed timer of several is told apart by its place among the step's timers, from 1`() {
        assertEquals(1, TimerName.number(pasta, 0))
        assertEquals(3, TimerName.number(pasta, 2))
        // Two unnamed timers share their derived name and differ by their numbers.
        assertEquals(TimerName.of(pasta, 0), TimerName.of(pasta, 2))
        assertTrue(TimerName.number(pasta, 0) != TimerName.number(pasta, 2))
    }

    @Test
    fun `a named timer needs no number, nor does a timer the step does not carry`() {
        assertNull(TimerName.number(pasta, 1))
        assertNull(TimerName.number(pasta, 3))
        assertNull(TimerName.number(Step(0, text), 0))
    }

    @Test
    fun `a step with one timer reads exactly as before, with no number`() {
        val one = Step(0, text, timer = Timer(10.minutes))
        assertEquals(text, TimerName.of(one, 0))
        assertEquals(TimerName.of(one), TimerName.of(one, 0))
        assertNull(TimerName.number(one, 0))
        val named = Step(0, text, timer = Timer(10.minutes, "Pasta"))
        assertEquals("Pasta", TimerName.of(named, 0))
        assertNull(TimerName.number(named, 0))
    }

    @Test
    fun `each timer's name follows its step's text as the card reads it`() {
        val flour = Step(0, "Всыпьте  муки", listOf(IngredientReference(0, 8)), listOf(Timer(5.minutes, "Мука"), Timer(3.minutes)))
        assertEquals("Мука", TimerName.of(flour, 0) { "200 г" })
        assertEquals("Всыпьте 200 г муки", TimerName.of(flour, 1) { "200 г" })
        assertEquals("Всыпьте 400 г муки", TimerName.of(flour, 1) { "400 г" })
        assertNull(TimerName.of(flour, 2) { "200 г" })
    }

    @Test
    fun `the source of each timer's derived name is read per timer`() {
        assertEquals(TimerName.source(Step(0, text, timer = Timer(1.minutes))), TimerName.source(pasta, 0))
        assertEquals(TimerName.source(pasta, 0), TimerName.source(pasta, 2))
        assertNull(TimerName.source(pasta, 1))
        assertNull(TimerName.source(pasta, 3))
    }

    @Test
    fun `a held timer is the step's own at its place`() {
        assertTrue(TimerName.holds(pasta, 1, "Sauce", null))
        assertFalse(TimerName.holds(pasta, 0, "Sauce", null))
        assertFalse(TimerName.holds(pasta, 2, "Sauce", null))
        assertTrue(TimerName.holds(pasta, 2, text, TimerName.source(pasta, 2)))
        assertTrue(TimerName.holds(pasta, 0, text, null))
        assertFalse(TimerName.holds(pasta, 1, text, TimerName.source(pasta, 0)))
        // The timer at that place removed.
        assertFalse(TimerName.holds(pasta.withoutTimer(2), 2, text, TimerName.source(pasta, 2)))
        // The author's words changed under an unnamed timer.
        assertFalse(TimerName.holds(pasta.copy(text = "Boil the pasta"), 2, text, TimerName.source(pasta, 2)))
    }

    // The bridge the card and the runtime read until they name each timer: the step's first timer.
    @Test
    fun `the one-timer reading names the step's first timer`() {
        val sauceFirst = pasta.copy(timers = pasta.timers.reversed())
        assertEquals(text, TimerName.of(pasta))
        assertEquals("Sauce", TimerName.of(pasta.copy(timers = listOf(Timer(6.minutes, "Sauce"), Timer(10.minutes)))))
        assertEquals(TimerName.of(sauceFirst, 0), TimerName.of(sauceFirst) { "x" })
        assertEquals(TimerName.source(pasta, 0), TimerName.source(pasta))
        assertNull(TimerName.source(pasta.copy(timers = listOf(Timer(6.minutes, "Sauce"), Timer(10.minutes)))))
        assertTrue(TimerName.holds(pasta.copy(timers = listOf(Timer(6.minutes, "Sauce"), Timer(10.minutes))), "Sauce", null))
        assertFalse(TimerName.holds(pasta, "Sauce", null))
    }
}
