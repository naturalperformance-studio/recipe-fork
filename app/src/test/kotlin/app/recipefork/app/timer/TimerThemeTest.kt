package app.recipefork.app.timer

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.R
import app.recipefork.app.card.CardTags
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.ui.EditedAppTheme
import app.recipefork.app.ui.appColorScheme
import app.recipefork.app.ui.editedThemeResources
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.Timer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.time.Duration.Companion.minutes

/**
 * A running timer's colours are the theme's roles (ADR 20261004): with `app_theme.xml` edited, a running timer on its
 * step is drawn in the edited primary colour and its cancel in the edited tonal colours, and the bar of running timers
 * on the edited surface, its name and time left in the scheme's text colour.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w360dp-h640dp")
class TimerThemeTest : CardTimersKit() {
    private val edited by lazy {
        editedThemeResources(
            compose.activity,
            colors = mapOf(
                R.color.theme_light_secondaryContainer to Color(0xFFB2EBF2),
                R.color.theme_light_onSecondaryContainer to Color(0xFF00363D),
                R.color.theme_light_surfaceContainer to Color(0xFFFFF8E1),
                R.color.theme_light_primary to Color(0xFF00696E),
                R.color.theme_light_onPrimary to Color(0xFFFFFFFE),
            ),
        )
    }
    private val scheme: ColorScheme by lazy { appColorScheme(edited, dark = false) }

    @Composable
    override fun Theme(content: @Composable () -> Unit) = EditedAppTheme(edited, content)

    private val long = "Keep stirring slowly over a low heat, scraping the bottom of the pan so that nothing catches or burns"
    private val rice = Recipe.create(now, owner = null).copy(
        title = "Rice",
        steps = listOf(Step(0, "Boil the rice", timer = Timer(10.minutes, "Rice"))) + (1..12).map { Step(it, long) },
    )

    private fun layoutOf(tag: String, text: String? = null): TextLayoutResult {
        val matcher = if (text == null) hasTestTag(tag) else hasAnyAncestor(hasTestTag(tag)) and hasText(text)
        return mutableListOf<TextLayoutResult>().also {
            compose.onNode(matcher, useUnmergedTree = true).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!(it)
        }.single()
    }

    private fun colourAt(tag: String, x: Float, y: Float): Color {
        val pixels = compose.onNodeWithTag(tag).captureToImage().toPixelMap()
        return pixels[(pixels.width * x).toInt(), (pixels.height * y).toInt()]
    }

    private fun assertSameColour(what: String, expected: Color, actual: Color) {
        val close = listOf(expected.red to actual.red, expected.green to actual.green, expected.blue to actual.blue)
            .all { (e, a) -> kotlin.math.abs(e - a) < 2f / 255 }
        assertTrue("$what is $actual, not $expected", close)
    }

    @Test
    fun aRunningTimerOnItsStepAndInTheBarTakesTheThemesRoles() {
        runBlocking { recipes.save(rice) }
        show(rice)
        start(0)
        awaitHeld { it.size == 1 }
        compose.waitUntil(PATIENCE_MS) { statusOf(0) == "10:00" }

        control(0)
        // A running timer is filled in the primary colour, its time left in the colour on it; its cancel stays tonal.
        assertSameColour("the running timer's time left", scheme.onPrimary, layoutOf(TimerTags.status(0)).layoutInput.style.color)
        assertSameColour("the running timer's container", scheme.primary, colourAt(TimerTags.start(0), 0.5f, 0.05f))
        val cancel = context.getString(R.string.timer_cancel)
        compose.onNodeWithTag(TimerTags.cancel(0)).performScrollTo()
        assertSameColour("its cancel's label", scheme.onSecondaryContainer, layoutOf(TimerTags.cancel(0), cancel).layoutInput.style.color)

        compose.onNodeWithTag(CardTags.step(12)).performScrollTo()
        compose.waitUntil(PATIENCE_MS) { shown(TimerTags.BAR) && statusOfBar() == "10:00" }
        assertSameColour("the bar", scheme.surfaceContainer, colourAt(TimerTags.BAR, 0.01f, 0.02f))
        assertSameColour("the bar's time left", scheme.onSurface, layoutOf(TimerTags.barStatus(0)).layoutInput.style.color)
        assertSameColour("the bar's timer name", scheme.onSurface, layoutOf(TimerTags.barTimer(0), "Rice").layoutInput.style.color)
        assertSameColour("the bar's cancel", scheme.onSecondaryContainer, layoutOf(TimerTags.barCancel(0), cancel).layoutInput.style.color)
    }

    private fun statusOfBar(): String? = compose.onAllNodes(hasTestTag(TimerTags.barStatus(0)), true).fetchSemanticsNodes()
        .singleOrNull()?.let { node -> layoutOfNode(node) }

    private fun layoutOfNode(node: androidx.compose.ui.semantics.SemanticsNode): String? =
        mutableListOf<TextLayoutResult>().also { node.config[SemanticsActions.GetTextLayoutResult].action!!(it) }
            .singleOrNull()?.layoutInput?.text?.text
}
