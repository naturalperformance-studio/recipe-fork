package app.recipefork.app.timer

import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.card.CardMode
import app.recipefork.app.card.CardTags
import app.recipefork.app.card.chooseCardTab
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.ui.AppTheme
import app.recipefork.app.ui.WorkingFamily
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.Timer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.time.Duration.Companion.minutes

/**
 * T-297, design QA: what is counted and pressed is set in the working face, Golos Text — a step timer's name and its
 * time, the bar's names, times and counts in its label style with tabular figures, and the card's summary line in the
 * small body style.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w360dp-h640dp")
class WorkingFaceTest : CardTimersKit() {
    @Composable
    override fun Theme(content: @Composable () -> Unit) = AppTheme(content = content)

    private val long = "Keep stirring slowly over a low heat, scraping the bottom of the pan so that nothing catches or burns"
    private val stew = Recipe.create(now, owner = null).copy(
        title = "Stew",
        steps = listOf(
            Step(0, "Boil the rice", timer = Timer(10.minutes, "Rice")),
            Step(1, "Boil the eggs", timer = Timer(5.minutes, "Eggs")),
        ) + (2..14).map { Step(it, long) },
    )

    private fun styleOf(tag: String): TextStyle = mutableListOf<TextLayoutResult>().also {
        compose.onNode(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!(it)
    }.single().layoutInput.style

    private fun assertWorking(tag: String, tabular: Boolean = true) {
        val style = styleOf(tag)
        assertEquals("$tag in Golos Text", WorkingFamily, style.fontFamily)
        assertEquals("$tag's figures", if (tabular) "tnum" else null, style.fontFeatureSettings)
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun theTimersAndTheSummaryAreSetInTheWorkingFace() {
        runBlocking { recipes.save(stew) }
        show(stew)
        compose.chooseCardTab(CardMode.VIEW)
        // The summary under the title, "2 steps · timers ≈ 15 min": the small body style, Golos Text.
        assertWorking(CardTags.SUMMARY, tabular = false)

        compose.chooseCardTab(CardMode.COOKING)
        start(0)
        awaitHeld { it.size == 1 }
        compose.waitUntil(PATIENCE_MS) { statusOf(0) == "10:00" }
        control(0)
        assertWorking(TimerTags.status(0))

        // One running out of sight: its line in the bar, its name and time left.
        compose.onNodeWithTag(CardTags.step(14)).performScrollTo()
        compose.waitUntil(PATIENCE_MS) { exists(TimerTags.barStatus(0)) }
        assertWorking(TimerTags.barStatus(0))
        assertWorking(TimerTags.barName(0))

        // Two running: one line counting down the soonest, and how many more.
        compose.onNodeWithTag(CardTags.step(1)).performScrollTo()
        start(1)
        awaitHeld { it.size == 2 }
        compose.onNodeWithTag(CardTags.step(14)).performScrollTo()
        compose.waitUntil(PATIENCE_MS) { exists(TimerTags.BAR_RUNNING_STATUS) }
        assertWorking(TimerTags.BAR_RUNNING_STATUS)
        assertWorking(TimerTags.BAR_RUNNING_NAME)
        assertWorking(TimerTags.BAR_MORE)
    }
}
