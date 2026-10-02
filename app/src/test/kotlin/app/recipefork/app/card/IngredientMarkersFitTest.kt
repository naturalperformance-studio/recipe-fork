package app.recipefork.app.card

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
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
 * The recalculated list on a 360 dp-wide phone (content padded 16 dp each side) at font scale 1.0: the section line,
 * every arrow and the fixed note each stay on one line, and an arrow sits right after its quantity on the quantity's
 * own line rather than wrapping under it.
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
        up = "округлено вверх, точное значение меньше",
        down = "округлено вниз, точное значение больше",
    )

    @Test
    @Config(qualifiers = "en-w360dp-h640dp")
    fun markersStayOnTheirLineInEnglish() = fits(
        InterfaceLanguage.ENGLISH,
        names = listOf("Skinless chicken breast, cut into large cubes", "Free-range chicken eggs", "Fine sea salt"),
        quantities = listOf("1250 g", "5 pc", "1½ tsp"),
        fixed = "fixed",
        up = "rounded up, exact value was lower",
        down = "rounded down, exact value was higher",
    )

    private fun fits(
        language: InterfaceLanguage,
        names: List<String>,
        quantities: List<String>,
        fixed: String,
        up: String,
        down: String,
    ) {
        val ordered = names.indices.map { Ingredient(it, null, freeTextName = names[it], unitType = UnitType.EXACT, unit = QuantityUnit.GRAM, quantity = Rational.ONE) }
        val list = IngredientList(
            lines = listOf(
                IngredientLine(names[0], quantities[0], derived = true, rounding = Rounding.UP, fixed = true),
                IngredientLine(names[1], quantities[1], derived = true, rounding = Rounding.DOWN),
                IngredientLine(names[2], quantities[2], derived = true, rounding = Rounding.UP),
            ),
            holdsProportions = false,
        )
        compose.setContent {
            MaterialTheme {
                Box(Modifier.width(360.dp).padding(horizontal = 16.dp)) {
                    Column { IngredientsList(ordered, list, emptyList(), language, onFix = {}, onRelease = {}) }
                }
            }
        }
        compose.waitForIdle()
        val nodes = compose.onAllNodes(isRoot().not() or isRoot(), useUnmergedTree = true).fetchSemanticsNodes()
        fun textNode(text: String) = nodes.filter { node ->
            node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString("") { it.text } == text
        }

        // The section line: shown once, laid out on one line of its style.
        val legend = compose.onNodeWithTag(IngredientTags.RECALCULATED, useUnmergedTree = true).fetchSemanticsNode()
        assertEquals(1, compose.onAllNodes(hasTestTag(IngredientTags.RECALCULATED), useUnmergedTree = true).fetchSemanticsNodes().size)
        val lineHeight = with(compose.density) { 16.dp.toPx() } // bodySmall: 16 sp line height at font scale 1.0
        assertTrue("the section line is one line high (${legend.size.height} px)", legend.size.height < 1.5f * lineHeight)

        // The fixed note: one line.
        assertEquals("the fixed note is laid out on one line", 1, layoutOf(textNode(fixed).single()).lineCount)

        // Every arrow right after its quantity, on the quantity's line; every quantity on one line.
        listOf(quantities[0] to up, quantities[1] to down, quantities[2] to up).forEachIndexed { i, (quantity, spoken) ->
            val shown = textNode(quantity).single()
            assertEquals("$quantity is laid out on one line", 1, layoutOf(shown).lineCount)
            val q = shown.boundsInRoot
            val arrow = textNode(spoken)[if (i == 2) 1 else 0].boundsInRoot
            assertTrue("the arrow after $quantity follows it ($arrow after $q)", arrow.left >= q.right - 0.5f)
            assertTrue("the arrow after $quantity is on its line ($arrow by $q)", arrow.top < q.bottom && arrow.bottom > q.top)
            assertTrue("the arrow after $quantity stays inside the screen ($arrow)", arrow.right <= with(compose.density) { 344.dp.toPx() } + 0.5f)
        }
    }

    private fun layoutOf(node: SemanticsNode): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action!!(layouts)
        return layouts.single()
    }
}
