package app.recipefork.app.card

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.timer.CardTimersKit
import app.recipefork.app.timer.TimerTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A11Y-004 on the stepper: on a 320 dp-wide phone at twice the font scale, in English and in Russian, Cooking's steps —
 * one done and held open by its running timer, the current one, and one done and folded — show every circle beside its
 * text and every entry under the one before, nothing cut and nothing overlapping; the folded one's single line ends in
 * an ellipsis within the screen, and every step's line keeps the card's target.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CardStepperFitTest : CardTimersKit() {
    override val fontScale = 2f

    @Test
    @Config(qualifiers = "en-w320dp-h640dp")
    fun theStepsFitInEnglish() = stepsFit()

    @Test
    @Config(qualifiers = "ru-w320dp-h640dp")
    fun theStepsFitInRussian() = stepsFit()

    /** Where the element tagged [tag] is laid out, unclipped by the viewport. */
    private fun rect(tag: String): Rect {
        val node = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
        val at = node.positionInRoot
        return Rect(at.x, at.y, at.x + node.size.width, at.y + node.size.height)
    }

    private fun mark(position: Int) {
        compose.onNodeWithTag(CardTags.step(position)).performScrollTo().performClick()
        compose.waitForIdle()
    }

    private fun stepsFit() {
        show(soup)
        start(0)
        awaitHeld { it.size == 1 }
        mark(0)
        mark(2)

        // The first held open by its timer, the long third folded to a line.
        assertTrue(shown(TimerTags.start(0)))
        assertFalse(shown(TimerTags.start(2)))
        val folded = compose.textLayoutsWithin(StepTags.text(2)).single()
        assertEquals(1, folded.lineCount)
        assertTrue("the folded line ends in an ellipsis", folded.isLineEllipsized(0))
        assertTrue("within its room", folded.getLineRight(0) <= folded.size.width + 0.5f)

        (0..2).forEach { position ->
            compose.onNodeWithTag(StepRowTags.entry(position), useUnmergedTree = true).performScrollTo()
            compose.waitForIdle()
            compose.assertCardTarget(CardTags.step(position))
            val circle = rect(StepRowTags.number(position))
            val text = rect(StepTags.text(position))
            assertTrue("step $position's circle $circle stands beside its text $text", circle.right <= text.left + 0.5f)
            assertTrue("step $position's text lies within the screen", text.right <= rect(CardTags.SCREEN).right + 0.5f)
        }
        listOf(0, 1).forEach { compose.assertWhole(StepTags.text(it)) }
        listOf(TimerTags.start(0), TimerTags.cancel(0)).forEach {
            compose.onNodeWithTag(it).performScrollTo()
            compose.waitForIdle()
            compose.assertWhole(it)
        }
        (0..1).forEach { position ->
            val above = rect(StepRowTags.entry(position))
            val below = rect(StepRowTags.entry(position + 1))
            assertTrue("step ${position + 1} ($below) starts under step $position ($above)", below.top >= above.bottom - 0.5f)
        }
    }
}
