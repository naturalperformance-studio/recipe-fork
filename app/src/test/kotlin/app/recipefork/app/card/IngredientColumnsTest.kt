package app.recipefork.app.card

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.editor.textsOf
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.UnitType
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
 * Scenario 6 of cooking with busy hands: the quantities, each with its unit, stand in one right-aligned column as wide
 * as the longest of them, the names in a column beside it; the screen reader still reads a line as its name, then its
 * quantity. On a narrow phone at large text a long name wraps whole rather than being cut.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IngredientColumnsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun show(width: Int, fontScale: Float, lines: List<IngredientLine>) {
        val ordered = lines.indices.map {
            Ingredient(it, null, freeTextName = lines[it].name, unitType = UnitType.EXACT, unit = QuantityUnit.GRAM, quantity = Rational.ONE)
        }
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                MaterialTheme {
                    Box(Modifier.width(width.dp).padding(horizontal = 16.dp)) {
                        Column { IngredientsList(ordered, IngredientList(lines, holdsProportions = true), emptyList(), InterfaceLanguage.ENGLISH, onFix = {}, onRelease = {}) }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun node(text: String): SemanticsNode =
        compose.onAllNodes(isRoot().not() or isRoot(), useUnmergedTree = true).fetchSemanticsNodes().single { node ->
            node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString("") { it.text } == text
        }

    private fun bounds(text: String): Rect = node(text).boundsInRoot

    /** Where the first line of [text] is drawn: its glyphs, not the box it is set in. */
    private fun glyphs(text: String): Rect {
        val node = node(text)
        val layout = mutableListOf<TextLayoutResult>().also { node.config[SemanticsActions.GetTextLayoutResult].action!!(it) }.single()
        val box = node.boundsInRoot
        return Rect(box.left + layout.getLineLeft(0), box.top, box.left + layout.getLineRight(0), box.bottom)
    }

    @Test
    @Config(qualifiers = "en-w360dp-h640dp")
    fun quantitiesStandInARightAlignedColumnAsWideAsTheLongestTheNamesBesideIt() {
        val quantities = listOf("2 pc", "1250 g", "1½ tsp")
        val names = listOf("Eggs", "Flour", "Salt")
        show(360, 1f, names.indices.map { IngredientLine(names[it], quantities[it]) })
        val start = with(compose.density) { 16.dp.toPx() }
        val longest = quantities.maxBy { glyphs(it).width }
        // As wide as the longest quantity, give or take the few pixels of the glyphs' side bearings and the column's slack.
        assertEquals("the longest quantity starts the column", start, glyphs(longest).left, 3f)
        val column = glyphs(longest).right
        quantities.forEach { assertEquals("$it ends at the column's edge", column, glyphs(it).right, 0.5f) }
        val nameStart = bounds(names[0]).left
        assertTrue("the names stand beside the column", nameStart > column)
        names.forEach { assertEquals("$it starts the names' column", nameStart, bounds(it).left, 0.5f) }
        names.indices.forEach { assertTrue("${names[it]} is on its quantity's line", bounds(names[it]).top < bounds(quantities[it]).bottom) }
        // Read as before: the name, then the quantity.
        assertEquals(listOf("Flour", "1250 g"), compose.textsOf(CardTags.ingredient(1)))
    }

    @Test
    @Config(qualifiers = "en-w320dp-h640dp")
    fun atLargeTextOnANarrowPhoneALongNameWrapsWhole() {
        val name = "Skinless chicken breast, cut into large cubes"
        show(320, 2f, listOf(IngredientLine(name, "1250 g"), IngredientLine("Salt", "12 ½ heaped tablespoons")))
        val shown = bounds(name)
        assertTrue("the name stands beside the quantity", shown.left >= bounds("1250 g").right)
        assertTrue("the name stays on screen", shown.right <= with(compose.density) { 304.dp.toPx() } + 0.5f)
        compose.assertWhole(CardTags.ingredient(0))
        compose.assertWhole(CardTags.ingredient(1))
        assertTrue("the name wraps", compose.textLayoutsWithin(CardTags.ingredient(0)).first { it.layoutInput.text.text == name }.lineCount > 1)
    }
}
