package app.recipefork.core.timer

import app.recipefork.core.recipe.IngredientReference
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.Timer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes

/**
 * The short form a screen shows an unnamed timer by ([TimerName.short]): the derived name cut at a word to
 * [TimerName.SHORT_LENGTH] characters, with the derived name's rules; the notification keeps the whole derived name.
 */
class TimerNameShortTest {
    @Test
    fun `an English step is shown by its first words, cut at a word, with an ellipsis`() {
        val name = TimerName.derived("Bake until the crust is golden brown and the loaf sounds hollow when tapped")
        assertEquals("Bake until the crust is…", TimerName.short(name))
        // The notification's name is the 60-character one, as before.
        assertEquals("Bake until the crust is golden brown and the loaf sounds…", name)
    }

    @Test
    fun `a Russian step is shown by its first words, cut at a word, with an ellipsis`() {
        val name = TimerName.derived("Выпекайте до золотистой корочки около сорока минут при двухстах градусах")
        assertEquals("Выпекайте до золотистой…", TimerName.short(name))
    }

    @Test
    fun `a step shorter than the cut is shown whole, with no mark`() {
        assertEquals("Knead the dough", TimerName.short(TimerName.derived("Knead the dough")))
        assertEquals("Замесите тесто", TimerName.short(TimerName.derived("Замесите тесто")))
        val exactly = "a".repeat(10) + " " + "b".repeat(13) // 24 characters
        assertEquals(exactly, TimerName.short(exactly))
        assertEquals(exactly.dropLast(1) + "…", TimerName.short(exactly.dropLast(1) + "…"))
    }

    @Test
    fun `a single word longer than the cut is cut inside it`() {
        assertEquals("x".repeat(24) + "…", TimerName.short("x".repeat(40)))
    }

    @Test
    fun `a reference is one unit, never cut between its number and its unit`() {
        // "Whisk the eggs, with " is 21 characters; the reference reads "200 g" from the 22nd: its no-break space is the
        // 25th character, where the cut at 24 would otherwise fall.
        val text = "Whisk the eggs, with  of sugar until pale"
        val step = Step(0, text, listOf(IngredientReference(0, 21)), Timer(5.minutes))
        val name = TimerName.of(step, 0) { "200 g" }!!
        assertEquals("Whisk the eggs, with 200 g of sugar until pale", name)
        assertEquals("Whisk the eggs, with…", TimerName.short(name))
        // A reference ending before the cut stays whole in the short form.
        val early = Step(0, "Add  of sugar and whisk until pale and thick", listOf(IngredientReference(0, 4)), Timer(5.minutes))
        assertEquals("Add 1,5 кг of sugar and…", TimerName.short(TimerName.of(early, 0) { "1,5 кг" }!!))
    }

    @Test
    fun `a character outside the basic plane counts once and is never split`() {
        assertEquals("🍞".repeat(24) + "…", TimerName.short(TimerName.derived("🍞".repeat(70))))
        assertEquals("🍞".repeat(24), TimerName.short("🍞".repeat(24)))
    }
}
