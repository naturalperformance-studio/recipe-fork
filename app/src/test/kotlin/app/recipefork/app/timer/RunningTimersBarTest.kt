package app.recipefork.app.timer

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.R
import app.recipefork.app.card.CardTags
import app.recipefork.app.card.assertCardTarget
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.ui.AccessibilityChecks
import app.recipefork.app.ui.Haptic
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.Timer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.minutes

/**
 * The bar of running timers (FLOW-016): while a timer of the recipe runs, or rings, and its step is out of sight, a bar
 * at the bottom of the card shows it — its name, its time left and its control — the soonest first, never taller than a
 * third of the screen; a tap on it brings its step back; it covers nothing of the card, and goes once nothing is to show.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "en-w320dp-h640dp")
class RunningTimersBarTest : CardTimersKit() {
    private val long = "Keep stirring slowly over a low heat, scraping the bottom of the pan so that nothing catches or burns"
    private val stew = Recipe.create(now, owner = null).copy(
        title = "Stew",
        steps = listOf(
            Step(0, "Soak the beans"),
            Step(1, "Boil the rice", timer = Timer(10.minutes, "Rice")),
            Step(2, "Boil the eggs", timer = Timer(5.minutes, "Eggs")),
            Step(3, "Roast the peppers", timer = Timer(20.minutes, "Peppers")),
            Step(4, "Bake the bread", timer = Timer(30.minutes, "Bread")),
            Step(5, "Rest the dough", timer = Timer(40.minutes, "Dough")),
        ) + (6..25).map { Step(it, long) },
    )
    private val last = 25

    private fun showStew() {
        runBlocking { recipes.save(stew) }
        show(stew)
    }

    private fun scrollToTheEnd() {
        compose.onNodeWithTag(CardTags.step(last)).performScrollTo()
        compose.waitForIdle()
    }

    private fun barShown() = shown(TimerTags.BAR)

    private fun text(tag: String): String? = compose.onAllNodes(hasTestTag(tag), true).fetchSemanticsNodes().singleOrNull()
        ?.config?.getOrElseNullable(SemanticsProperties.Text) { null }?.joinToString("") { it.text }

    private fun top(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().positionInRoot.y

    @Test
    fun aRunningTimerOutOfSightShowsInTheBarWithItsNameAndTimeLeft() {
        showStew()
        start(1)
        awaitHeld { it.size == 1 }
        // Its step on screen: no bar.
        compose.waitForIdle()
        assertTrue(!barShown())

        scrollToTheEnd()
        compose.waitUntil(PATIENCE_MS) { barShown() }
        compose.waitUntil(PATIENCE_MS) { text(TimerTags.barStatus(1)) == "10:00" }
        val line = compose.onNodeWithTag(TimerTags.barTimer(1)).fetchSemanticsNode()
        assertTrue(line.config.getOrElseNullable(SemanticsProperties.Text) { null }.orEmpty().any { it.text == "Rice" })
        assertEquals(context.getString(R.string.timer_bar_show_action), AccessibilityChecks.clickActionLabel(line))
        compose.assertCardTarget(TimerTags.barCancel(1))

        pass(1.minutes)
        compose.waitUntil(PATIENCE_MS) { text(TimerTags.barStatus(1)) == "9:00" }
    }

    @Test
    fun severalAreListedSoonestFirstAndTheBarScrollsWithinAThirdOfTheScreen() {
        showStew()
        (1..5).forEach { start(it) }
        awaitHeld { it.size == 5 }
        scrollToTheEnd()
        compose.waitUntil(PATIENCE_MS) { barShown() && (1..5).all { shown(TimerTags.barTimer(it)) } }

        // Eggs (5 min), Rice (10), Peppers (20), Bread (30), Dough (40).
        val order = listOf(2, 1, 3, 4, 5).map { top(TimerTags.barTimer(it)) }
        assertEquals(order.sorted(), order)

        val bar = compose.onNodeWithTag(TimerTags.BAR).fetchSemanticsNode()
        val heightDp = bar.size.height / bar.layoutInfo.density.density
        assertTrue("the bar is $heightDp dp tall", heightDp <= 640 / 3f + 0.5f)
        // The last of them lies beyond the bar's bottom, and is reached by scrolling within the bar.
        val barBottom = top(TimerTags.BAR) + bar.size.height
        fun lastBottom() = compose.onNodeWithTag(TimerTags.barTimer(5)).fetchSemanticsNode().let { it.positionInRoot.y + it.size.height }
        assertTrue(lastBottom() > barBottom)
        compose.onNodeWithTag(TimerTags.barTimer(5)).performScrollTo()
        compose.waitForIdle()
        assertTrue(lastBottom() <= barBottom + 0.5f)
    }

    @Test
    fun tappingATimerBringsItsStepBackAndTheBarGoes() {
        showStew()
        start(1)
        awaitHeld { it.size == 1 }
        scrollToTheEnd()
        compose.waitUntil(PATIENCE_MS) { barShown() }

        compose.onNodeWithTag(TimerTags.barTimer(1)).performClick()
        compose.waitUntil(PATIENCE_MS) { !barShown() }
        // Its step is on screen again, and still running.
        assertTrue(top(CardTags.step(1)) >= top(CardTags.SCREEN))
        compose.onNodeWithTag(TimerTags.start(1)).assertExists()
        assertEquals(1, held().size)
    }

    @Test
    fun theBarCancelsATimerAndCoversNothing() {
        showStew()
        start(1)
        awaitHeld { it.size == 1 }
        scrollToTheEnd()
        compose.waitUntil(PATIENCE_MS) { barShown() }
        // The content is padded so that its end scrolls clear of the bar.
        val lastStep = compose.onNodeWithTag(CardTags.step(last)).fetchSemanticsNode().boundsInRoot.bottom
        assertTrue(lastStep <= top(TimerTags.BAR) + 0.5f)

        haptics.clear()
        compose.onNodeWithTag(TimerTags.barCancel(1)).performClick()
        awaitHeld { it.isEmpty() }
        compose.waitUntil(PATIENCE_MS) { !barShown() }
        assertEquals(listOf(Haptic.CONFIRM), haptics.all)
    }

    @Test
    fun aRingingTimerShowsRingingWithItsStop() {
        showStew()
        start(2)
        awaitHeld { it.size == 1 }
        scrollToTheEnd()
        compose.waitUntil(PATIENCE_MS) { barShown() }

        pass(5.minutes)
        // Ended and not ringing: nothing runs, so no bar.
        compose.waitUntil(PATIENCE_MS) { !barShown() }
        compose.runOnIdle { timers.ringing.ring(held().single()) }
        compose.waitUntil(PATIENCE_MS) { shown(TimerTags.barStop(2)) }
        assertEquals("Ringing", text(TimerTags.barStatus(2)))
        assertTrue(!shown(TimerTags.barCancel(2)))
        compose.assertCardTarget(TimerTags.barStop(2))

        haptics.clear()
        compose.onNodeWithTag(TimerTags.barStop(2)).performClick()
        compose.waitUntil(PATIENCE_MS) { !barShown() }
        assertTrue(timers.ringing.ringing.value.isEmpty())
        assertEquals(listOf(Haptic.CONFIRM), haptics.all)
    }
}
