package app.recipefork.app.timer

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.card.CardTags
import app.recipefork.app.card.assertCardTarget
import app.recipefork.app.card.assertWhole
import app.recipefork.app.editor.PATIENCE_MS
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
 * v0.30, T-220 — a step's timer on Cooking, at a glance: a running timer's control is filled in the theme's primary
 * colour, one at rest tonal; its Cancel stands beside it where they fit; and the current step's container takes in its
 * timers, so that they read as the step's own.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w360dp-h640dp")
class CardTimerAtAGlanceTest : CardTimersKit() {
    private val scheme = lightColorScheme()

    private fun bounds(tag: String): Rect = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    /** The colour drawn at the root's point ([x], [y]). */
    private fun colourAt(x: Float, y: Float): Color {
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        return pixels[x.toInt(), y.toInt()]
    }

    /** The colour drawn in the element tagged [tag], at its centre's height, a little in from its end. */
    private fun fillOf(tag: String): Color = bounds(tag).let { colourAt(it.right - 12f, it.top + it.height / 2) }

    private fun same(a: Color, b: Color) = abs(a.red - b.red) < 2f / 255 && abs(a.green - b.green) < 2f / 255 && abs(a.blue - b.blue) < 2f / 255

    @Test
    fun aRunningTimerIsFilledInThePrimaryColourAndOneAtRestIsTonal() {
        show(soup)
        start(0)
        awaitHeld { it.size == 1 }
        compose.waitUntil(PATIENCE_MS) { statusOf(0) == "10:00" }
        control(0)
        compose.waitForIdle()
        assertTrue("the running timer is filled in the primary colour", same(scheme.primary, fillOf(TimerTags.start(0))))
        control(2)
        compose.waitForIdle()
        assertTrue("the timer at rest is tonal", same(scheme.secondaryContainer, fillOf(TimerTags.start(2))))
        assertFalse(same(scheme.primary, fillOf(TimerTags.start(2))))
    }

    // v0.30, T-221: on the current step's primary container a control at rest — the timer not started, and Cancel beside
    // a running one — is outlined on the surface, standing off the container rather than tonal on it; the running
    // timer stays filled in the primary colour.
    @Test
    fun onTheCurrentStepAControlAtRestStandsOffTheContainer() {
        show(soup)
        control(0)
        compose.waitForIdle()
        assertStandsOff(TimerTags.start(0))
        start(0)
        awaitHeld { it.size == 1 }
        compose.waitUntil(PATIENCE_MS) { statusOf(0) == "10:00" && shown(TimerTags.cancel(0)) }
        compose.onNodeWithTag(TimerTags.cancel(0)).performScrollTo()
        compose.waitForIdle()
        assertTrue("the running timer stays filled in the primary colour", same(scheme.primary, fillOf(TimerTags.start(0))))
        assertStandsOff(TimerTags.cancel(0))
    }

    /** The control tagged [tag] is filled with the surface, not tonal, and its edge drawn apart from the container. */
    private fun assertStandsOff(tag: String) {
        val fill = fillOf(tag)
        assertTrue("$tag is filled with the surface ($fill)", same(scheme.surface, fill))
        assertFalse(same(scheme.secondaryContainer, fill))
        val box = bounds(tag)
        val edge = colourAt(box.left + 0.5f, box.top + box.height / 2)
        assertFalse("$tag's edge ($edge) is drawn, not the container", same(scheme.primaryContainer, edge) || same(scheme.surface, edge))
    }

    // Where they fit, Cancel stands beside the running timer's control, at its end; each a card target.
    @Test
    fun cancelStandsBesideStartWhereItFits() = cancelBesideStart()

    @Test
    @Config(qualifiers = "ru-w360dp-h640dp")
    fun inRussianCancelStandsBesideStartWhereItFits() = cancelBesideStart()

    private fun cancelBesideStart() {
        show(soup)
        start(0)
        awaitHeld { it.size == 1 }
        compose.waitUntil(PATIENCE_MS) { shown(TimerTags.cancel(0)) }
        compose.onNodeWithTag(TimerTags.cancel(0)).performScrollTo()
        compose.waitForIdle()
        val start = bounds(TimerTags.start(0))
        val cancel = bounds(TimerTags.cancel(0))
        assertTrue("Cancel ($cancel) stands beside Start ($start)", cancel.left >= start.right && cancel.top < start.bottom && cancel.bottom > start.top)
        listOf(TimerTags.start(0), TimerTags.cancel(0)).forEach {
            compose.assertCardTarget(it)
            compose.assertWhole(it)
        }
    }

    // The current step's container takes in its timer: the gap between the step's text and its timer is drawn on it;
    // the next step's is not.
    @Test
    fun theCurrentStepsContainerTakesInItsTimer() {
        val boiled = Recipe.create(now, owner = null).copy(
            title = "Eggs",
            steps = listOf(Step(0, "Boil the eggs", timer = Timer(8.minutes, "Eggs")), Step(1, "Cool them", timer = Timer(2.minutes, "Cool"))),
        )
        runBlocking { recipes.save(boiled) }
        show(boiled)
        compose.waitUntil(PATIENCE_MS) { shown(TimerTags.start(1)) }
        compose.waitForIdle()
        fun gapColour(position: Int): Color {
            val text = bounds(CardTags.step(position))
            val timer = bounds(TimerTags.start(position))
            assertTrue("the timer stands under its step's text", timer.top > text.bottom)
            return colourAt(timer.left + 4f, (text.bottom + timer.top) / 2)
        }
        assertTrue("the current step's timer is within its container", same(scheme.primaryContainer, gapColour(0)))
        // Below the timer, still within the container.
        val timer = bounds(TimerTags.start(0))
        assertTrue(same(scheme.primaryContainer, colourAt(timer.left + 4f, timer.bottom + 2f)))
        assertTrue("the next step is on the card's surface", same(scheme.surface, gapColour(1)))
    }
}

/**
 * v0.30, T-220 — the bar of running timers at twice the font scale on a 320 dp phone, in English and Russian: a timer's
 * name keeps whole words — its time and Cancel go under it rather than squeeze it — and Cancel stays a card target.
 * T-226: the line several running timers gather into, and the line of several finished ones with its Stop, read whole
 * and keep card targets; opened, each timer's line does as before.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RunningTimersBarAtLargeTextTest : CardTimersKit() {
    override val fontScale = 2f

    private val long = "Keep stirring slowly over a low heat, scraping the bottom of the pan so that nothing catches or burns"

    private fun text(tag: String): String? = compose.onAllNodes(hasTestTag(tag), true).fetchSemanticsNodes().singleOrNull()
        ?.config?.getOrElseNullable(SemanticsProperties.Text) { null }?.joinToString("") { it.text }

    private fun barReadsWhole(names: List<String>, more: String, finished: String) {
        val recipe = Recipe.create(now, owner = null).copy(
            title = "Dough",
            steps = names.mapIndexed { i, name -> Step(i, "Rest the dough", timer = Timer((10 + i).minutes, name)) } +
                (names.size until names.size + 12).map { Step(it, long) },
        )
        runBlocking { recipes.save(recipe) }
        show(recipe)
        names.indices.forEach { start(it) }
        awaitHeld { it.size == names.size }
        compose.onNodeWithTag(CardTags.step(names.size + 11)).performScrollTo()
        compose.waitUntil(PATIENCE_MS) { shown(TimerTags.BAR_RUNNING) }
        compose.waitForIdle()
        assertEquals(more, text(TimerTags.BAR_MORE))
        compose.assertWhole(TimerTags.BAR_RUNNING)
        compose.assertCardTarget(TimerTags.BAR_RUNNING)

        compose.onNodeWithTag(TimerTags.BAR_RUNNING).performClick()
        compose.waitUntil(PATIENCE_MS) { names.indices.all { shown(TimerTags.barTimer(it)) } }
        compose.waitForIdle()
        names.indices.forEach { position ->
            compose.onNodeWithTag(TimerTags.barTimer(position)).performScrollTo()
            compose.waitForIdle()
            compose.assertWhole(TimerTags.barTimer(position))
            compose.assertCardTarget(TimerTags.barCancel(position))
        }

        // The first two end and ring: one line for them, with its Stop, above the one still running.
        pass(11.minutes)
        compose.runOnIdle { held().filter { it.key.stepPosition < 2 }.forEach { timers.ringing.ring(it) } }
        compose.waitUntil(PATIENCE_MS) { shown(TimerTags.BAR_FINISHED) }
        compose.onNodeWithTag(TimerTags.BAR_FINISHED).performScrollTo()
        compose.waitForIdle()
        assertEquals(finished, text(TimerTags.BAR_FINISHED_NAME))
        compose.assertWhole(TimerTags.BAR_FINISHED)
        compose.assertCardTarget(TimerTags.BAR_FINISHED)
        compose.assertCardTarget(TimerTags.BAR_STOP_ALL)
    }

    @Test
    @Config(qualifiers = "en-w320dp-h640dp")
    fun aTimersNameReadsWholeInEnglish() =
        barReadsWhole(listOf("Chill", "Chill the dough overnight", "Proofing"), "+2 more", "2 timers finished")

    @Test
    @Config(qualifiers = "ru-w320dp-h640dp")
    fun aTimersNameReadsWholeInRussian() = barReadsWhole(
        listOf("Охладить", "Охлаждение теста в холодильнике", "Расстойка"),
        "ещё 2",
        "Закончились таймеры: 2",
    )
}
