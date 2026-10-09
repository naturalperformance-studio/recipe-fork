package app.recipefork.core.recipe

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/** DOM-024: a step carries from none to five timers, in order, each with its own duration and name. */
class StepTimersTest {
    private val step = Step(0, "Boil the pasta, meanwhile reduce the sauce")

    @Test
    fun aStepTakesTimersInOrderUpToFive() {
        assertEquals(5, RecipeBounds.MAX_TIMERS)
        val timers = listOf(Timer(10.minutes, "Pasta"), Timer(6.minutes), Timer(2.minutes, "Garlic"), Timer(1.minutes), Timer(3.minutes, "Herbs"))
        var held = step
        timers.forEachIndexed { index, timer ->
            assertTrue(held.canAddTimer, "before timer $index")
            held = checkNotNull(held.withTimer(timer)) { "timer $index refused" }
            assertEquals(timers.take(index + 1), held.timers)
        }
        assertFalse(held.canAddTimer)
    }

    @Test
    fun aSixthTimerIsRefused() {
        val five = step.copy(timers = List(5) { Timer((it + 1).minutes) })
        assertNull(five.withTimer(Timer(7.minutes)))
        assertFalse(five.canAddTimer)
        val four = step.copy(timers = List(4) { Timer((it + 1).minutes) })
        assertTrue(four.canAddTimer)
        assertEquals(5, four.withTimer(Timer(7.minutes))?.timers?.size)
    }

    @Test
    fun timersAreRemovedOneByOneAndTheOthersKeepTheirOrder() {
        val three = step.copy(timers = listOf(Timer(10.minutes, "Pasta"), Timer(6.minutes), Timer(2.minutes, "Garlic")))
        assertEquals(listOf(Timer(10.minutes, "Pasta"), Timer(2.minutes, "Garlic")), three.withoutTimer(1).timers)
        assertEquals(listOf(Timer(6.minutes), Timer(2.minutes, "Garlic")), three.withoutTimer(0).timers)
        assertEquals(listOf(Timer(10.minutes, "Pasta"), Timer(6.minutes)), three.withoutTimer(2).timers)
        assertEquals(three, three.withoutTimer(3))
        assertEquals(three, three.withoutTimer(-1))
        assertEquals(emptyList(), three.withoutTimer(0).withoutTimer(0).withoutTimer(0).timers)
    }

    // The bridge for the editor, the card and the timer runtime until they read every timer.
    @Test
    fun theOneTimerBridgeReadsAndWritesTheFirstTimer() {
        assertNull(step.timer)
        assertEquals(emptyList(), Step(0, "x", timer = null).timers)
        assertEquals(listOf(Timer(5.minutes)), Step(0, "x", timer = Timer(5.minutes)).timers)
        val two = step.copy(timers = listOf(Timer(10.minutes, "Pasta"), Timer(6.minutes)))
        assertEquals(Timer(10.minutes, "Pasta"), two.timer)
        assertEquals(listOf(Timer(1.minutes)), two.copy(timer = Timer(1.minutes)).timers)
        assertEquals(emptyList(), two.copy(timer = null).timers)
        assertEquals(two.copy(text = "y", timers = emptyList()), two.copy(text = "y", timer = null))
    }
}
