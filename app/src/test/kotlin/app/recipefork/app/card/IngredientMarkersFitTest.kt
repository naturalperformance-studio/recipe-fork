package app.recipefork.app.card

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.InterfaceLanguage
import app.recipefork.core.scaling.Rational
import app.recipefork.core.scaling.Rounding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The recalculated list on a phone (content padded 16 dp each side). On a 360 dp-wide screen at font scale 1.0 the
 * section line, every arrow and the fixed note each stay on one line, and an arrow sits right after its quantity on
 * the quantity's own line. On a 320 dp-wide screen at font scale 2.0 a long quantity never squeezes its arrow away.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IngredientMarkersFitTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    @Config(qualifiers = "ru-w360dp-h640dp")
    fun markersStayOnTheirLineInRussian() = fits(
        InterfaceLanguage.RUSSIAN,
        names = listOf("Куриное филе без кожи, нарезанное крупными кубиками", "Яйца куриные", "Соль морская мелкая"),
        quantities = listOf("1250 г", "5 шт.", "1½ ч. л."),
        fixed = "зафиксировано",
    )

    @Test
    @Config(qualifiers = "en-w360dp-h640dp")
    fun markersStayOnTheirLineInEnglish() = fits(
        InterfaceLanguage.ENGLISH,
        names = listOf("Skinless chicken breast, cut into large cubes", "Free-range chicken eggs", "Fine sea salt"),
        quantities = listOf("1250 g", "5 pc", "1½ tsp"),
        fixed = "fixed",
    )

    @Test
    @Config(qualifiers = "ru-w320dp-h640dp")
    fun aLongQuantityAtLargeTextKeepsItsArrowInRussian() = keepsArrow(
        InterfaceLanguage.RUSSIAN,
        name = "Мука пшеничная высшего сорта",
        quantity = "12 ½ ст. л. с горкой",
    )

    @Test
    @Config(qualifiers = "en-w320dp-h640dp")
    fun aLongQuantityAtLargeTextKeepsItsArrowInEnglish() = keepsArrow(
        InterfaceLanguage.ENGLISH,
        name = "Plain wheat flour",
        quantity = "12 ½ heaped tablespoons",
    )

    private fun show(language: InterfaceLanguage, width: Int, fontScale: Float, lines: List<IngredientLine>) {
        val ordered = lines.indices.map {
            Ingredient(it, null, freeTextName = lines[it].name, unitType = UnitType.EXACT, unit = QuantityUnit.GRAM, quantity = Rational.ONE)
        }
        val list = IngredientList(lines, holdsProportions = false)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                MaterialTheme {
                    Box(Modifier.width(width.dp).padding(horizontal = 16.dp)) {
                        Column { IngredientsList(ordered, list, emptyList(), language, onFix = {}, onRelease = {}) }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun fits(language: InterfaceLanguage, names: List<String>, quantities: List<String>, fixed: String) {
        show(
            language, width = 360, fontScale = 1f,
            lines = listOf(
                IngredientLine(names[0], quantities[0], derived = true, rounding = Rounding.UP, fixed = true),
                IngredientLine(names[1], quantities[1], derived = true, rounding = Rounding.DOWN),
                IngredientLine(names[2], quantities[2], derived = true, rounding = Rounding.UP),
            ),
        )
        // The section line: shown once, laid out on one line of its style.
        assertEquals(1, compose.onAllNodes(hasTestTag(IngredientTags.RECALCULATED), useUnmergedTree = true).fetchSemanticsNodes().size)
        val legend = compose.onNodeWithTag(IngredientTags.RECALCULATED, useUnmergedTree = true).fetchSemanticsNode()
        val lineHeight = with(compose.density) { 16.dp.toPx() } // bodySmall: 16 sp line height at font scale 1.0
        assertTrue("the section line is one line high (${legend.size.height} px)", legend.size.height < 1.5f * lineHeight)

        // The fixed note: one line.
        assertEquals("the fixed note is laid out on one line", 1, layoutOf(textNode(fixed)).lineCount)

        // Every arrow right after its quantity, on the quantity's line; every quantity on one line.
        quantities.forEachIndexed { position, quantity ->
            val shown = textNode(quantity)
            assertEquals("$quantity is laid out on one line", 1, layoutOf(shown).lineCount)
            val q = shown.boundsInRoot
            val arrow = arrowBounds(position)
            assertTrue("the arrow after $quantity follows it ($arrow after $q)", arrow.left >= q.right - 0.5f)
            assertTrue("the arrow after $quantity is on its line ($arrow by $q)", arrow.top < q.bottom && arrow.bottom > q.top)
            assertTrue("the arrow after $quantity stays inside the screen ($arrow)", arrow.right <= with(compose.density) { 344.dp.toPx() } + 0.5f)
        }
    }

    private fun keepsArrow(language: InterfaceLanguage, name: String, quantity: String) {
        show(language, width = 320, fontScale = 2f, lines = listOf(IngredientLine(name, quantity, derived = true, rounding = Rounding.UP)))
        val arrow = arrowBounds(0)
        val q = textNode(quantity).boundsInRoot
        val glyph = with(compose.density) { 8.dp.toPx() }
        assertTrue("the arrow keeps its width at font scale 2.0 ($arrow)", arrow.width >= glyph)
        assertTrue("the arrow follows the quantity ($arrow after $q)", arrow.left >= q.right - 0.5f)
        assertTrue("the arrow stays inside the screen ($arrow)", arrow.right <= with(compose.density) { 304.dp.toPx() } + 0.5f)
    }

    private fun arrowBounds(position: Int) =
        compose.onNodeWithTag(IngredientTags.rounding(position), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun textNode(text: String): SemanticsNode =
        compose.onAllNodes(isRoot().not() or isRoot(), useUnmergedTree = true).fetchSemanticsNodes().single { node ->
            node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString("") { it.text } == text
        }

    private fun layoutOf(node: SemanticsNode): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action!!(layouts)
        return layouts.single()
    }
}
