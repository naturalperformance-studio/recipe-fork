package app.recipefork.app.card

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The serving count and its [ − | + ] group on a 320 dp-wide phone (the card's content is padded 16 dp on each side):
 * at font scales 1.0 and 2.0, in English and Russian, with a long approximate count, the group stays beside the count
 * on its line, each half whole at 56 dp within the content, and the count is laid out without being clipped.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ServingsControlFitTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    @Config(qualifiers = "en-w320dp-h640dp")
    fun theGroupFitsBesideTheCountInEnglish() = fits("Servings: ≈12½")

    @Test
    @Config(qualifiers = "ru-w320dp-h640dp")
    fun theGroupFitsBesideTheCountInRussian() = fits("Порций: ≈12½")

    private fun fits(expected: String) {
        var fontScale by mutableFloatStateOf(1f)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                MaterialTheme {
                    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag(CONTENT)) {
                        ServingsControl(
                            modifier = Modifier,
                            count = "12½",
                            approximate = true,
                            canDecrease = true,
                            onDecrease = {},
                            onIncrease = {},
                        )
                    }
                }
            }
        }
        listOf(1f, 2f).forEach { scale ->
            fontScale = scale
            compose.waitForIdle()
            val at = "at font scale $scale"
            val content = compose.onNodeWithTag(CONTENT).fetchSemanticsNode()
            val density = content.layoutInfo.density.density
            assertEquals("the content is the 320 dp phone less its gutters", 288f, content.size.width / density, 0.5f)
            val box = content.boundsInRoot
            val countNode = compose.onNodeWithTag(CardTags.SERVINGS, useUnmergedTree = true).fetchSemanticsNode()
            val count = countNode.boundsInRoot
            val fewer = compose.onNodeWithTag(CardTags.SERVINGS_FEWER).fetchSemanticsNode().boundsInRoot
            val more = compose.onNodeWithTag(CardTags.SERVINGS_MORE).fetchSemanticsNode().boundsInRoot
            listOf("−" to fewer, "+" to more).forEach { (name, half) ->
                assertEquals("$name's width $at", 56f, half.width / density, 0.5f)
                assertEquals("$name's height $at", 56f, half.height / density, 0.5f)
                assertTrue("$name lies within the content $at: $half in $box",
                    half.left >= box.left && half.right <= box.right + 0.5f)
                assertEquals("$name is on the count's line $at", count.center.y, half.center.y, 1f)
            }
            assertTrue("− comes before + $at", fewer.right <= more.left + 0.5f)
            assertTrue("the count ends before the group $at: $count, $fewer", count.right <= fewer.left + 0.5f)
            val layouts = mutableListOf<TextLayoutResult>()
            countNode.config[SemanticsActions.GetTextLayoutResult].action!!(layouts)
            val layout = layouts.single()
            assertEquals(expected, layout.layoutInput.text.text)
            assertFalse("the count is not clipped $at", layout.hasVisualOverflow)
            // The count may wrap, but never inside a word.
            val words = expected.split(' ')
            assertTrue("the count's ${layout.lineCount} lines keep its words whole $at", layout.lineCount <= words.size)
            (0 until layout.lineCount).forEach { line ->
                val text = expected.substring(layout.getLineStart(line), layout.getLineEnd(line)).trim()
                assertTrue("line '$text' $at holds whole words", text.split(' ').all { it in words })
            }
        }
    }

    private companion object {
        const val CONTENT = "servings-fit-content"
    }
}
