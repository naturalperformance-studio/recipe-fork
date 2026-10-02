package app.recipefork.app.card

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.scaling.InterfaceLanguage
import app.recipefork.core.scaling.Rational
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The fixing control's actions on a 360 dp-wide phone (the card's content is padded 16 dp on each side): at font
 * scales 1.0, 1.3 and 2.0, in Russian and English, every action's label is laid out whole on one line, and every
 * action lies entirely within the control.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FixActionsFitTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    @Config(qualifiers = "ru-w360dp-h640dp")
    fun everyActionStaysWholeInRussian() = fits(InterfaceLanguage.RUSSIAN, "Снять фиксацию", "Закрыть", "Пересчитать")

    @Test
    @Config(qualifiers = "en-w360dp-h640dp")
    fun everyActionStaysWholeInEnglish() = fits(InterfaceLanguage.ENGLISH, "Release", "Cancel", "Recalculate")

    private fun fits(language: InterfaceLanguage, release: String, cancel: String, confirm: String) {
        var fontScale by mutableFloatStateOf(1f)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                MaterialTheme {
                    Box(Modifier.width(360.dp).padding(horizontal = 16.dp)) {
                        FixAmount(
                            offer = FixOffer(listOf(QuantityUnit.GRAM, QuantityUnit.KILOGRAM), QuantityUnit.GRAM, null),
                            current = CardFixing(0, Rational.of(300), QuantityUnit.GRAM),
                            language = language,
                            onFix = { _, _ -> },
                            onRelease = {},
                            onClose = {},
                        )
                    }
                }
            }
        }
        listOf(1f, 1.3f, 2f).forEach { scale ->
            fontScale = scale
            compose.waitForIdle()
            val control = compose.onNodeWithTag(CardTags.FIX).fetchSemanticsNode().boundsInRoot
            listOf(CardTags.FIX_RELEASE to release, CardTags.FIX_CANCEL to cancel, CardTags.FIX_CONFIRM to confirm)
                .forEach { (tag, label) ->
                    val at = "$label at font scale $scale"
                    val button = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
                    val bounds = button.boundsInRoot
                    assertTrue("$at lies within the control: $bounds in $control",
                        bounds.left >= control.left && bounds.right <= control.right &&
                            bounds.top >= control.top && bounds.bottom <= control.bottom)
                    val text = compose.onNodeWithTag(tag, useUnmergedTree = true).onChildren()
                        .fetchSemanticsNodes().single { it.config.getOrNull(SemanticsActions.GetTextLayoutResult) != null }
                    val layouts = mutableListOf<TextLayoutResult>()
                    text.config[SemanticsActions.GetTextLayoutResult].action!!(layouts)
                    val layout = layouts.single()
                    assertEquals(label, layout.layoutInput.text.text)
                    assertEquals("$at is laid out on one line", 1, layout.lineCount)
                    val whole = layout.multiParagraph.intrinsics.maxIntrinsicWidth
                    assertTrue("$at gets its label's whole width (${layout.size.width} px for $whole px)",
                        layout.size.width + 0.5f >= whole && bounds.width >= whole)
                }
        }
    }
}
