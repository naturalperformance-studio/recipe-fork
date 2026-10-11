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
import app.recipefork.app.ui.assertChangesGradually
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
 * Two or more running, or two or more finished, gather into one line that opens into each of them (T-226).
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

    private fun merged(tag: String): List<String> = compose.onNodeWithTag(tag).fetchSemanticsNode()
        .config.getOrElseNullable(SemanticsProperties.Text) { null }.orEmpty().map { it.text }

    private fun clickLabel(tag: String) = AccessibilityChecks.clickActionLabel(compose.onNodeWithTag(tag).fetchSemanticsNode())

    private fun ring(position: Int) = compose.runOnIdle { timers.ringing.ring(held().single { it.key.stepPosition == position }) }

    // T-226: two or more running gather into one line — the soonest, its countdown and how many more.
    @Test
    fun severalRunningShowAsOneLineCountingDownTheSoonest() {
        showStew()
        (1..3).forEach { start(it) }
        awaitHeld { it.size == 3 }
        scrollToTheEnd()
        compose.waitUntil(PATIENCE_MS) { shown(TimerTags.BAR_RUNNING) }

        // Eggs (5 min) ends first, before Rice (10) and Peppers (20); none of them has a line of its own.
        assertEquals("Eggs", text(TimerTags.BAR_RUNNING_NAME))
        compose.waitUntil(PATIENCE_MS) { text(TimerTags.BAR_RUNNING_STATUS) == "5:00" }
        assertEquals("+2 more", text(TimerTags.BAR_MORE))
        assertTrue((1..3).none { shown(TimerTags.barTimer(it)) })
        // The screen reader reads the line whole, with its count, and what a tap on it does.
        assertEquals(listOf("Eggs", "5:00", "+2 more"), merged(TimerTags.BAR_RUNNING))
        assertEquals("Show these timers", clickLabel(TimerTags.BAR_RUNNING))
        compose.assertCardTarget(TimerTags.BAR_RUNNING)

        // Live: it counts down, and once Eggs is out of it the next soonest leads.
        pass(1.minutes)
        compose.waitUntil(PATIENCE_MS) { text(TimerTags.BAR_RUNNING_STATUS) == "4:00" }
        compose.onNodeWithTag(TimerTags.cancel(2)).performScrollTo().performClick()
        awaitHeld { it.size == 2 }
        scrollToTheEnd()
        compose.waitUntil(PATIENCE_MS) { text(TimerTags.BAR_RUNNING_NAME) == "Rice" && text(TimerTags.BAR_MORE) == "+1 more" }
        assertEquals("9:00", text(TimerTags.BAR_RUNNING_STATUS))

        // One left: it shows as a lone timer does.
        compose.onNodeWithTag(TimerTags.cancel(1)).performScrollTo().performClick()
        awaitHeld { it.size == 1 }
        scrollToTheEnd()
        compose.waitUntil(PATIENCE_MS) { shown(TimerTags.barTimer(3)) }
        assertTrue(!shown(TimerTags.BAR_RUNNING))
        assertEquals("19:00", text(TimerTags.barStatus(3)))
    }

    // T-226: tapping the line opens the list, soonest first, scrolling within a third of the screen; each timer cancels
    // on its own and leads to its step; a tap on the line closes it.
    @Test
    fun theRunningLineOpensIntoEachTimerToCancelOrShowItsStep() {
        showStew()
        (1..5).forEach { start(it) }
        awaitHeld { it.size == 5 }
        scrollToTheEnd()
        compose.waitUntil(PATIENCE_MS) { shown(TimerTags.BAR_RUNNING) }
        compose.onNodeWithTag(TimerTags.BAR_RUNNING).performClick()
        compose.waitUntil(PATIENCE_MS) { (1..5).all { shown(TimerTags.barTimer(it)) } }
        assertEquals("5 timers running", text(TimerTags.BAR_RUNNING_NAME))
        assertEquals("Hide these timers", clickLabel(TimerTags.BAR_RUNNING))

        // Eggs (5 min), Rice (10), Peppers (20), Bread (30), Dough (40), all under the line.
        val order = listOf(2, 1, 3, 4, 5).map { top(TimerTags.barTimer(it)) }
        assertEquals(order.sorted(), order)
        assertTrue(top(TimerTags.BAR_RUNNING) < order.first())

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

        // Each cancels on its own.
        compose.onNodeWithTag(TimerTags.barCancel(5)).performScrollTo().performClick()
        awaitHeld { it.size == 4 }
        assertEquals(setOf(1, 2, 3, 4), held().map { it.key.stepPosition }.toSet())
        compose.waitUntil(PATIENCE_MS) { !shown(TimerTags.barTimer(5)) && text(TimerTags.BAR_RUNNING_NAME) == "4 timers running" }

        // A tap on the line closes the list.
        compose.onNodeWithTag(TimerTags.BAR_RUNNING).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { (1..4).none { shown(TimerTags.barTimer(it)) } }
        assertEquals("Eggs", text(TimerTags.BAR_RUNNING_NAME))

        // Open again, a timer's line still brings its step back.
        compose.onNodeWithTag(TimerTags.BAR_RUNNING).performClick()
        compose.waitUntil(PATIENCE_MS) { shown(TimerTags.barTimer(4)) }
        compose.onNodeWithTag(TimerTags.barTimer(4)).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { !shown(TimerTags.barTimer(4)) }
        assertTrue(top(CardTags.step(4)) >= top(CardTags.SCREEN))
        assertEquals(4, held().size)
    }

    // T-226: finished timers ringing gather into one line above the running, with one Stop that silences them all.
    @Test
    fun finishedTimersShowAsOneLineWithOneStop() {
        showStew()
        (1..3).forEach { start(it) }
        awaitHeld { it.size == 3 }
        scrollToTheEnd()
        pass(10.minutes)
        ring(1)
        compose.waitUntil(PATIENCE_MS) { shown(TimerTags.barStop(1)) }
        // One finished names itself, as before.
        assertTrue(!shown(TimerTags.BAR_FINISHED))
        assertEquals("Ringing", text(TimerTags.barStatus(1)))

        ring(2)
        compose.waitUntil(PATIENCE_MS) { shown(TimerTags.BAR_FINISHED) }
        assertEquals(listOf("2 timers finished"), merged(TimerTags.BAR_FINISHED))
        assertEquals("Show these timers", clickLabel(TimerTags.BAR_FINISHED))
        assertTrue(!shown(TimerTags.barTimer(1)) && !shown(TimerTags.barTimer(2)))
        // Above the one still running, which shows as a lone timer does.
        assertTrue(top(TimerTags.BAR_FINISHED) < top(TimerTags.barTimer(3)))
        compose.assertCardTarget(TimerTags.BAR_FINISHED)
        compose.assertCardTarget(TimerTags.BAR_STOP_ALL)
        assertEquals(
            "Stop all these timers ringing",
            AccessibilityChecks.clickActionLabel(compose.onNodeWithTag(TimerTags.BAR_STOP_ALL, true).fetchSemanticsNode()),
        )

        haptics.clear()
        compose.onNodeWithTag(TimerTags.BAR_STOP_ALL).performClick()
        compose.waitUntil(PATIENCE_MS) { !shown(TimerTags.BAR_FINISHED) }
        assertTrue(timers.ringing.ringing.value.isEmpty())
        assertEquals(listOf(Haptic.CONFIRM), haptics.all)
        assertTrue(shown(TimerTags.barTimer(3)))
        assertEquals(3, held().size)
    }

    // T-226: the finished line opens into each finished timer, stopped on its own.
    @Test
    fun theFinishedLineOpensIntoEachTimerToStopOnItsOwn() {
        showStew()
        (1..2).forEach { start(it) }
        awaitHeld { it.size == 2 }
        scrollToTheEnd()
        pass(10.minutes)
        ring(1)
        ring(2)
        compose.waitUntil(PATIENCE_MS) { shown(TimerTags.BAR_FINISHED) }

        compose.onNodeWithTag(TimerTags.BAR_FINISHED).performClick()
        compose.waitUntil(PATIENCE_MS) { shown(TimerTags.barStop(1)) && shown(TimerTags.barStop(2)) }
        assertEquals("Hide these timers", clickLabel(TimerTags.BAR_FINISHED))
        compose.onNodeWithTag(TimerTags.barStop(2)).performClick()
        compose.waitUntil(PATIENCE_MS) { !shown(TimerTags.BAR_FINISHED) }
        assertEquals(setOf(held().single { it.key.stepPosition == 1 }.key), timers.ringing.ringing.value)
        // The one left names itself, still ringing.
        assertEquals("Ringing", text(TimerTags.barStatus(1)))
        compose.onNodeWithTag(TimerTags.barTimer(1)).performClick()
        compose.waitUntil(PATIENCE_MS) { !barShown() }
        assertTrue(top(CardTags.step(1)) >= top(CardTags.SCREEN))
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

    // T-159: the bar rises from the bottom edge as a timer comes into it and sinks below it as the last goes.
    @Test
    fun theBarRisesAndSinks() {
        showStew()
        start(2)
        awaitHeld { it.size == 1 }
        scrollToTheEnd()
        compose.waitUntil(PATIENCE_MS) { barShown() }
        pass(5.minutes)
        compose.waitUntil(PATIENCE_MS) { !barShown() }
        val ended = held().single()
        fun barTop() = if (barShown()) top(TimerTags.BAR) else compose.onNodeWithTag(CardTags.SCREEN).fetchSemanticsNode().boundsInRoot.bottom
        compose.assertChangesGradually("the bar rising", ::barTop) { compose.runOnUiThread { timers.ringing.ring(ended) } }
        compose.assertChangesGradually("the bar sinking", ::barTop) { compose.onNodeWithTag(TimerTags.barStop(2)).performClick() }
        compose.waitUntil(PATIENCE_MS) { !barShown() }
    }
}
