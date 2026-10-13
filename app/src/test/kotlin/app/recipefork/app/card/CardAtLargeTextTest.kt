package app.recipefork.app.card

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.R
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.timer.CardTimersKit
import app.recipefork.app.timer.TimerTags
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.Timer
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.Rational
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.time.Duration.Companion.minutes

/**
 * v0.38, T-270: the card at twice the font scale on a 320 dp phone. View's Fork starts shrunk to its icon and stays so,
 * and never covers the title or its summary line, standing aside while they are under it; the tabs take the bar's
 * scrolled colour as the recipe scrolls under them, and its colour at rest again at the top; the card's notice puts
 * its Undo on a line of its own from a font scale of 1.5.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w320dp-h640dp", fontScale = 2f)
class CardAtLargeTextTest : CardTimersKit() {
    override val fontScale: Float get() = context.resources.configuration.fontScale

    /** A title long enough at this size to bring its summary line down to where Fork floats as the card opens. */
    private val chicken = Recipe.create(now, owner = null).copy(
        title = "Chicken with potatoes, lemon and rosemary, roasted slowly in the oven",
        servings = 4,
        ingredients = (0 until 8).map {
            Ingredient(it, null, freeTextName = "Ingredient $it", unitType = UnitType.EXACT, unit = QuantityUnit.GRAM, quantity = Rational.of(100))
        },
        steps = (0 until 4).map { Step(it, "Roast until golden, turning the pieces once.", timer = Timer(45.minutes).takeIf { _ -> it == 0 }) },
    )

    private fun showView() {
        runBlocking { recipes.save(chicken) }
        show(chicken)
        compose.onNodeWithTag(CardTags.tab(CardMode.VIEW)).performClick()
        compose.waitUntil(PATIENCE_MS) { shown(CardTags.SUMMARY) }
        compose.waitForIdle()
    }

    private fun bounds(tag: String): Rect = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun forkShown() = shown(CardTags.FORK)

    /** The content of the tab shown: the one of the pager's pages lying within the screen. */
    private fun content() = compose.onAllNodes(hasTestTag(CardTags.CONTENT)).let { all ->
        val index = all.fetchSemanticsNodes().indexOfFirst { it.boundsInRoot.left >= -0.5f && it.boundsInRoot.left < it.boundsInRoot.width / 2 }
        all[index]
    }

    private fun forkCollapsed(): Boolean = bounds(CardTags.FORK).let { abs(it.width - it.height) < 1f }

    /** While Fork shows it is its icon alone, and lies over no part of the title or the summary line in sight. */
    private fun assertForkShrunkAndClear(where: String): Boolean {
        if (!forkShown()) return false
        val fork = bounds(CardTags.FORK)
        assertTrue("$where: Fork $fork shows its word", forkCollapsed())
        listOf(CardTags.TITLE, CardTags.SUMMARY).forEach { tag ->
            val part = bounds(tag)
            if (part.width > 0f && part.height > 0f) assertTrue("$where: Fork $fork covers $tag $part", !fork.overlaps(part))
        }
        return true
    }

    @Test
    fun forkStartsAsItsIconAndNeverCoversTheTitleOrItsSummary() {
        showView()
        var seen = assertForkShrunkAndClear("as the card opens")
        // Down the recipe a little at a time, and back up to the top.
        repeat(30) { step ->
            content().performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 40f) }
            compose.waitForIdle()
            seen = assertForkShrunkAndClear("scrolled down, step $step") || seen
        }
        content().performTouchInput { swipeDown() }
        compose.waitForIdle()
        seen = assertForkShrunkAndClear("scrolled up") || seen
        content().performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, -1_000_000f) }
        compose.waitForIdle()
        seen = assertForkShrunkAndClear("back at the top") || seen
        assertTrue("Fork never showed", seen)
        compose.scrollUntilForkShows()
        assertTrue(forkCollapsed())
        compose.assertCardTarget(CardTags.FORK)
    }

    /** The colour drawn at ([x], [y]) pixels from the top left of the element tagged [tag]. */
    private fun colourAt(tag: String, x: (width: Int) -> Int, y: Int): Color {
        val pixels = compose.onNodeWithTag(tag).captureToImage().toPixelMap()
        return pixels[x(pixels.width), y]
    }

    private fun same(expected: Color, actual: Color) =
        listOf(expected.red to actual.red, expected.green to actual.green, expected.blue to actual.blue).all { (e, a) -> abs(e - a) < 2f / 255 }

    private fun barColour() = colourAt(CardTags.BAR, { it - 2 }, 2)

    private fun tabsColour() = colourAt(CardTags.TABS, { 2 }, 2)

    private fun assertTopIn(what: String, expected: Color) {
        compose.waitUntil(PATIENCE_MS) { same(expected, barColour()) && same(expected, tabsColour()) }
        assertTrue("$what: the bar is ${barColour()}, the tabs ${tabsColour()}, not $expected", same(expected, barColour()) && same(expected, tabsColour()))
    }

    @Test
    fun theTabsTakeTheBarsScrolledColour() {
        val scheme = lightColorScheme()
        showView()
        assertTopIn("at the top", scheme.surface)
        content().performTouchInput { swipeUp() }
        assertTopIn("scrolled", scheme.surfaceContainer)
        repeat(3) { content().performTouchInput { swipeDown() } }
        assertTopIn("back at the top", scheme.surface)

        // Cooking keeps its own scroll: at its top, the colour at rest; scrolled, the scrolled colour.
        compose.onNodeWithTag(CardTags.tab(CardMode.COOKING)).performClick()
        compose.waitForIdle()
        assertTopIn("Cooking at its top", scheme.surface)
        content().performTouchInput { swipeUp() }
        assertTopIn("Cooking scrolled", scheme.surfaceContainer)
    }

    /** A running timer cancelled: the notice says so with Undo, below its words or [beside] them. */
    private fun assertUndoPlaced(said: String, undo: String, beside: Boolean) {
        runBlocking { recipes.save(chicken) }
        show(chicken)
        start(0)
        awaitHeld { it.size == 1 }
        compose.onNodeWithTag(TimerTags.cancel(0)).performScrollTo().performClick()
        val inNotice = hasAnyAncestor(hasTestTag(CardTags.SNACKBAR))
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(hasText(undo) and inNotice, true).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        val words = compose.onNode(hasText(said) and inNotice, useUnmergedTree = true).getBoundsInRoot()
        val action = compose.onNode(hasText(undo) and inNotice, useUnmergedTree = true).getBoundsInRoot()
        if (beside) {
            assertTrue("$undo ($action) stands beside the words ($words)", action.left >= words.right && action.top < words.bottom)
        } else {
            assertTrue("$undo ($action) stands below the words ($words)", action.top >= words.bottom)
        }
    }

    @Test
    fun atTwiceTheFontScaleTheNoticesUndoStandsOnItsOwnLine() =
        assertUndoPlaced(context.getString(R.string.card_timer_cancelled), "Undo", beside = false)

    @Test
    @Config(qualifiers = "ru-w320dp-h640dp", fontScale = 2f)
    fun inRussianAtTwiceTheFontScaleTheNoticesUndoStandsOnItsOwnLine() =
        assertUndoPlaced("Таймер отменён", "Вернуть", beside = false)

    @Test
    @Config(qualifiers = "en-w320dp-h640dp", fontScale = 1.5f)
    fun atOneAndAHalfTheNoticesUndoStandsOnItsOwnLine() =
        assertUndoPlaced("Timer cancelled", "Undo", beside = false)

    // At the usual font scale, beside its words: CardTimerCancelUndoTest (a class-wide font scale here cannot be undone
    // for one test).
}
