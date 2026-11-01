package app.recipefork.app.timer

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.R
import app.recipefork.app.card.CardTags
import app.recipefork.app.card.assertCardTarget
import app.recipefork.app.card.assertWhole
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.editor.textsOf
import app.recipefork.app.ui.AccessibilityChecks
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.Timer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.time.Duration.Companion.minutes

/**
 * v0.45, T-325 — timers on Cooking as pills (redesign § 4.4, fixes § 4): a step's timer at rest is a 56 dp pill with
 * its name and duration; a running one is one control the whole width, its countdown over «<name> · running», with a
 * quiet «Cancel timer» under it at its end — not beside it — that still offers Undo; pressing the running timer again
 * still asks whether to restart it, with the time it has left. In the bar, timers whose steps are out of sight stand as
 * pills, a ringing one first, saying in words that it rings, with a filled Stop; a running one reads as on its step, with
 * its quiet cancel under it.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w360dp-h640dp")
class CardTimerPillsTest : CardTimersKit() {
    private val scheme = lightColorScheme()

    private val long = "Keep stirring slowly over a low heat, scraping the bottom of the pan so that nothing catches or burns"
    private val stew = Recipe.create(now, owner = null).copy(
        title = "Stew",
        steps = listOf(
            Step(0, "Soak the beans"),
            Step(1, "Boil the rice", timer = Timer(10.minutes, "Rice")),
            Step(2, "Boil the eggs", timer = Timer(5.minutes, "Eggs")),
        ) + (3..20).map { Step(it, long) },
    )

    private fun bounds(tag: String): Rect = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun colourAt(x: Float, y: Float): Color = compose.onRoot().captureToImage().toPixelMap()[x.toInt(), y.toInt()]

    private fun same(a: Color, b: Color) =
        abs(a.red - b.red) < 2f / 255 && abs(a.green - b.green) < 2f / 255 && abs(a.blue - b.blue) < 2f / 255

    /**
     * The element tagged [tag] is a pill [fill]ed: its colour just inside its top edge, above the middle of its round
     * start, and none at its corner where a rounded rectangle of a smaller radius would still be filled.
     */
    private fun assertPill(tag: String, fill: Color) {
        val box = bounds(tag)
        val h = box.height
        assertTrue("$tag is filled", same(fill, colourAt(box.left + h / 2, box.top + h * 0.1f)))
        val corner = colourAt(box.left + h * 0.07f, box.top + h * 0.14f)
        assertFalse("$tag's start is round, a half circle ($corner)", same(fill, corner))
    }

    private fun text(tag: String): String? = compose.onAllNodes(hasTestTag(tag), true).fetchSemanticsNodes().singleOrNull()
        ?.config?.getOrElseNullable(SemanticsProperties.Text) { null }?.joinToString("") { it.text }

    @Test
    fun aTimerAtRestIsA56dpPillWithItsNameAndDuration() {
        show(soup)
        control(2)
        compose.waitForIdle()
        compose.assertCardTarget(TimerTags.start(2))
        assertEquals(listOf("Beans", "1 h 30 min"), compose.textsOf(TimerTags.start(2)))
        assertPill(TimerTags.start(2), scheme.secondaryContainer)
    }

    @Test
    fun aRunningTimerIsOneWideControlWithAQuietCancelUnderIt() = runningIsOneControl()

    @Test
    @Config(qualifiers = "ru-w360dp-h640dp")
    fun inRussianARunningTimerIsOneWideControlWithAQuietCancelUnderIt() = runningIsOneControl()

    private fun runningIsOneControl() {
        show(soup)
        control(0)
        compose.waitForIdle()
        val atRest = bounds(TimerTags.start(0))
        start(0)
        awaitHeld { it.size == 1 }
        compose.waitUntil(PATIENCE_MS) { statusOf(0) == "10:00" && shown(TimerTags.cancel(0)) }
        compose.onNodeWithTag(TimerTags.cancel(0)).performScrollTo()
        compose.waitForIdle()

        // One control: the countdown over the name and that it runs; nothing else beside it.
        assertEquals(listOf("10:00", runningName("Bring the water to the…")), compose.textsOf(TimerTags.start(0)))
        val running = bounds(TimerTags.start(0))
        val cancel = bounds(TimerTags.cancel(0))
        // The whole width of its line: from where the pill at rest began to where the line ends, Cancel's end.
        assertEquals(atRest.left, running.left, 0.5f)
        assertEquals(cancel.right, running.right, 0.5f)
        assertTrue("Cancel ($cancel) stands under the running timer ($running)", cancel.top >= running.bottom - 0.5f)
        assertTrue("Cancel is narrower than the timer, at its end", cancel.width < running.width && cancel.left > running.left)
        assertTrue("the running timer is filled in the primary colour", same(scheme.primary, colourAt(running.right - 12f, running.top + running.height / 2)))
        assertPill(TimerTags.start(0), scheme.primary)

        // Cancel is quiet: a text button, neither filled nor edged, on the current step's container.
        assertTrue(same(scheme.primaryContainer, colourAt(cancel.right - 2f, cancel.top + cancel.height / 2)))
        assertTrue(same(scheme.primaryContainer, colourAt(cancel.left + 1f, cancel.top + cancel.height / 2)))
        assertEquals(listOf(context.getString(R.string.timer_cancel_timer)), compose.textsOf(TimerTags.cancel(0)))
        val node = compose.onNodeWithTag(TimerTags.cancel(0)).fetchSemanticsNode()
        assertEquals(Role.Button, node.config.getOrNull(SemanticsProperties.Role))
        assertEquals(context.getString(R.string.timer_cancel_action), AccessibilityChecks.clickActionLabel(node))
        listOf(TimerTags.start(0), TimerTags.cancel(0)).forEach {
            compose.assertCardTarget(it)
            compose.assertWhole(it)
        }
    }

    @Test
    fun pressingARunningTimerAgainAsksWithTheTimeItHasLeft() {
        show(soup)
        start(0)
        awaitHeld { it.size == 1 }
        pass(3.minutes)
        awaitStatus(0, "7:00")
        start(0)
        compose.waitUntil(PATIENCE_MS) { shown(TimerTags.RESTART_QUESTION) }
        val asked = compose.onNodeWithTag(TimerTags.RESTART_QUESTION).fetchSemanticsNode()
        fun texts(node: androidx.compose.ui.semantics.SemanticsNode): List<String> =
            node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } + node.children.flatMap(::texts)
        val words = texts(asked)
        assertTrue(words.toString(), context.getString(R.string.timer_restart_title) in words)
        assertTrue(
            words.toString(),
            context.getString(R.string.timer_restart_question, "Bring the water to the…", "7:00", "10 min") in words,
        )
        compose.onNodeWithTag(TimerTags.RESTART_DECLINE).performClick()
        compose.waitUntil(PATIENCE_MS) { !shown(TimerTags.RESTART_QUESTION) }
        awaitStatus(0, "7:00")
    }

    @Test
    fun theBarShowsTimersAsPillsTheRingingOneFirstWithAFilledStop() {
        runBlocking { recipes.save(stew) }
        show(stew)
        start(1)
        start(2)
        awaitHeld { it.size == 2 }
        // Eggs end first and ring; Rice runs on.
        pass(5.minutes)
        compose.runOnIdle { timers.ringing.ring(held().single { it.key.stepPosition == 2 }) }
        compose.onNodeWithTag(CardTags.step(20)).performScrollTo()
        compose.waitUntil(PATIENCE_MS) { shown(TimerTags.barStop(2)) && shown(TimerTags.barTimer(1)) }
        compose.waitForIdle()

        val ringing = bounds(TimerTags.barTimer(2))
        val running = bounds(TimerTags.barTimer(1))
        assertTrue("the ringing timer comes first", ringing.bottom <= running.top)

        // Ringing: its name over its step and the word that it rings, a filled Stop beside them, on the primary container.
        assertEquals("Eggs", text(TimerTags.barName(2)))
        assertEquals("Step 3 · Ringing", text(TimerTags.barStatus(2)))
        assertPill(TimerTags.barTimer(2), scheme.primaryContainer)
        val stop = bounds(TimerTags.barStop(2))
        assertTrue("Stop is filled", same(scheme.primary, colourAt(stop.right - 12f, stop.top + stop.height / 2)))
        compose.assertCardTarget(TimerTags.barStop(2))
        assertTrue(!shown(TimerTags.barCancel(2)))

        // Running: as on its step — its countdown over its name and that it runs — with its quiet cancel under it.
        assertEquals("5:00", text(TimerTags.barStatus(1)))
        assertEquals(runningName("Rice"), text(TimerTags.barName(1)))
        assertPill(TimerTags.barTimer(1), scheme.primary)
        compose.assertCardTarget(TimerTags.barTimer(1))
        val cancel = bounds(TimerTags.barCancel(1))
        assertTrue("its cancel stands under it", cancel.top >= running.bottom - 0.5f)
        assertEquals(running.right, cancel.right, 1f)
        assertTrue(same(scheme.surfaceContainer, colourAt(cancel.left + 1f, cancel.top + cancel.height / 2)))
        assertEquals(listOf(context.getString(R.string.timer_cancel_timer)), compose.textsOf(TimerTags.barCancel(1)))
        compose.assertCardTarget(TimerTags.barCancel(1))

        // Each pill brings its step: the line is a button that says so.
        listOf(TimerTags.barTimer(1), TimerTags.barTimer(2)).forEach { tag ->
            val line = compose.onNodeWithTag(tag).fetchSemanticsNode()
            assertEquals(context.getString(R.string.timer_bar_show_action), AccessibilityChecks.clickActionLabel(line))
        }
    }
}
