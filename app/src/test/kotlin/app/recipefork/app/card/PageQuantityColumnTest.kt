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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.editor.textsOf
import app.recipefork.app.ui.AppTheme
import app.recipefork.app.ui.ReadingFamily
import app.recipefork.app.ui.WorkingFamily
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.IngredientGroup
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.InterfaceLanguage
import app.recipefork.core.scaling.Rational
import app.recipefork.core.scaling.Rounding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * T-313, scenario 3 of the recipe card as a cookbook page: on View each amount stands right-aligned in a column of
 * [PAGE_COLUMN] at the line's start, in the working face with tabular figures, its rounding arrow in the column before
 * it and read in words; the name beside it in the reading face, the preparation muted after a dash; a group's
 * subheading in small capitals of the working face, read as written. At large text the column stands above the names
 * and nothing is cut, even at twice the font scale on a 320 dp phone. Cooking keeps its own presentation.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PageQuantityColumnTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val english = listOf(
        IngredientLine("Olive oil", "2 tbsp"),
        IngredientLine("Onion", "1 pc", derived = true, rounding = Rounding.DOWN, note = "sliced"),
        IngredientLine("Sweet pepper", "2 pc", derived = true, rounding = Rounding.UP, note = "in strips"),
        IngredientLine("Tomatoes", "800 g", derived = true, note = "diced"),
    )

    private val russian = listOf(
        IngredientLine("Масло оливковое", "2 ст. л."),
        IngredientLine("Лук репчатый", "1 шт", derived = true, rounding = Rounding.DOWN, note = "полукольцами"),
        IngredientLine("Перец сладкий", "2 шт", derived = true, rounding = Rounding.UP, note = "соломкой"),
        IngredientLine("Помидоры", "800 г", derived = true, note = "кубиками"),
    )

    private var muted = Color.Unspecified

    private fun show(
        lines: List<IngredientLine>,
        width: Int = 360,
        fontScale: Float = 1f,
        mode: CardMode = CardMode.VIEW,
        groups: List<IngredientGroup> = emptyList(),
        language: InterfaceLanguage = InterfaceLanguage.ENGLISH,
    ) {
        val ordered = lines.indices.map {
            Ingredient(it, null, freeTextName = lines[it].name, unitType = UnitType.EXACT, unit = QuantityUnit.GRAM, quantity = Rational.ONE)
        }
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                AppTheme(dark = false) {
                    muted = MaterialTheme.colorScheme.onSurfaceVariant
                    Box(Modifier.width(width.dp).padding(horizontal = 16.dp)) {
                        Column {
                            IngredientsList(
                                ordered, IngredientList(lines, holdsProportions = true), emptyList(), language,
                                onFix = {}, onRelease = {}, groups = groups, mode = mode,
                            )
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun px(dp: Dp) = with(compose.density) { dp.toPx() }

    private fun unmerged(tag: String): SemanticsNode = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()

    private fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString("") { it.text }

    private fun node(text: String): SemanticsNode =
        compose.onAllNodes(isRoot().not() or isRoot(), useUnmergedTree = true).fetchSemanticsNodes().single { text(it) == text }

    /** The text layout of the quantity of the line at [position]. */
    private fun quantityLayout(position: Int): TextLayoutResult = compose.textLayoutsWithin(IngredientTags.quantity(position)).single()

    /** Where the glyphs of the first line of the quantity at [position] are drawn, not the box they are set in. */
    private fun quantityGlyphs(position: Int): Rect {
        val layout = quantityLayout(position)
        val box = node(layout.layoutInput.text.text).boundsInRoot
        return Rect(box.left + layout.getLineLeft(0), box.top, box.left + layout.getLineRight(0), box.bottom)
    }

    private fun nameBounds(position: Int) = unmerged(IngredientTags.name(position)).boundsInRoot

    private fun arrowBounds(position: Int) = unmerged(IngredientTags.rounding(position)).boundsInRoot

    private fun assertWorkingFace(what: String, style: TextStyle) {
        assertEquals("$what in Golos Text", WorkingFamily, style.fontFamily)
        assertEquals("$what in Medium", FontWeight.Medium, style.fontWeight)
        assertEquals("$what with tabular figures", "tnum", style.fontFeatureSettings)
    }

    private fun assertColumn(lines: List<IngredientLine>, spoken: Pair<String, String>) {
        val start = px(16.dp)
        val columnEnd = start + px(PAGE_COLUMN)
        lines.indices.forEach { position ->
            val quantity = unmerged(IngredientTags.quantity(position)).boundsInRoot
            assertEquals("the column of line $position starts the line", start, quantity.left, 0.5f)
            assertEquals("the column of line $position is ${PAGE_COLUMN} wide", px(PAGE_COLUMN), quantity.width, 0.5f)
            // Right-aligned: each amount ends at the column's edge, give or take its glyphs' side bearing.
            assertEquals("${lines[position].quantity} ends at the column's edge", columnEnd, quantityGlyphs(position).right, 2f)
            assertEquals("${lines[position].quantity} stands on one line", 1, quantityLayout(position).lineCount)
            assertEquals("the name of line $position stands 12 dp beside the column", columnEnd + px(12.dp), nameBounds(position).left, 0.5f)
            assertTrue("the name of line $position is on its amount's line", nameBounds(position).top < quantityGlyphs(position).bottom)
            assertWorkingFace("${lines[position].quantity}", quantityLayout(position).layoutInput.style)
            val name = compose.textLayoutsWithin(IngredientTags.name(position)).single()
            assertEquals("${lines[position].name} in Literata", ReadingFamily, name.layoutInput.style.fontFamily)
        }
        // The preparation follows the name after a dash, muted.
        val named = compose.textLayoutsWithin(IngredientTags.name(1)).single().layoutInput.text
        assertEquals("${lines[1].name} — ${lines[1].note}", named.text)
        val note = named.spanStyles.single()
        assertEquals("the preparation is muted", muted, note.item.color)
        assertEquals(" — ${lines[1].note}", named.text.substring(note.start, note.end))
        // The arrows stand in the column before their amounts and read as words; a line with no arrow carries none.
        listOf(1 to spoken.first, 2 to spoken.second).forEach { (position, words) ->
            val arrow = arrowBounds(position)
            assertTrue("the arrow of line $position stands in the column: $arrow", arrow.left >= start - 0.5f && arrow.right <= columnEnd + 0.5f)
            assertTrue("the arrow of line $position stands before its amount", arrow.right <= quantityGlyphs(position).left + 0.5f)
            assertEquals(words, text(unmerged(IngredientTags.rounding(position))))
            assertEquals("the line reads its name, its amount, then the arrow's words",
                listOf("${lines[position].name} — ${lines[position].note}", lines[position].quantity, words), compose.textsOf(CardTags.ingredient(position)))
        }
        assertTrue(compose.onAllNodes(androidx.compose.ui.test.hasTestTag(IngredientTags.rounding(3)), useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
    }

    @Test
    @Config(qualifiers = "en-w360dp-h640dp")
    fun onViewTheAmountsStandInAColumnOfTheirOwnInTheWorkingFace() {
        show(english)
        assertColumn(english, "rounded down, exact value was higher" to "rounded up, exact value was lower")
    }

    @Test
    @Config(qualifiers = "ru-w360dp-h640dp")
    fun onViewTheAmountsStandInAColumnOfTheirOwnInRussian() {
        show(russian, language = InterfaceLanguage.RUSSIAN)
        assertColumn(russian, "округлено вниз, точное значение больше" to "округлено вверх, точное значение меньше")
    }

    @Test
    @Config(qualifiers = "en-w360dp-h640dp")
    fun onViewAGroupSubheadingStandsInSmallCapitalsOfTheWorkingFaceReadAsWritten() {
        show(english, groups = listOf(IngredientGroup("For the sauce", 0), IngredientGroup("On top", 3)))
        listOf("For the sauce", "On top").forEachIndexed { index, name ->
            val heading = unmerged(IngredientTags.group(index))
            assertEquals("set in capitals", name.uppercase(), text(heading))
            assertEquals("read as written", listOf(name), heading.config.getOrNull(SemanticsProperties.ContentDescription))
            assertTrue("$name is a heading", heading.config.getOrNull(SemanticsProperties.Heading) != null)
            val style = compose.textLayoutsWithin(IngredientTags.group(index)).single().layoutInput.style
            assertEquals("in Golos Text", WorkingFamily, style.fontFamily)
            assertEquals("spaced as small capitals", GROUP_TRACKING, style.letterSpacing)
            assertEquals("muted", muted, style.color)
        }
    }

    private fun assertAbove(lines: List<IngredientLine>) {
        val start = px(16.dp)
        lines.indices.forEach { position ->
            val quantity = unmerged(IngredientTags.quantity(position)).boundsInRoot
            val name = nameBounds(position)
            assertEquals("the amount of line $position starts the line", start, quantity.left, 0.5f)
            assertEquals("the name of line $position starts the line", start, name.left, 0.5f)
            assertTrue("the amount of line $position stands above its name", quantity.bottom <= name.top + 0.5f)
            compose.assertWhole(CardTags.ingredient(position))
        }
        // The arrows still stand before their amounts and read as words.
        assertTrue(arrowBounds(1).right <= quantityGlyphs(1).left + 0.5f)
        assertTrue(text(unmerged(IngredientTags.rounding(1))).isNotBlank())
    }

    @Test
    @Config(qualifiers = "ru-w320dp-h640dp")
    fun atTwiceTheFontScaleOnANarrowPhoneTheColumnStandsAboveTheNamesNothingCut() {
        show(russian, width = 320, fontScale = 2f, language = InterfaceLanguage.RUSSIAN)
        assertAbove(russian)
    }

    @Test
    @Config(qualifiers = "en-w320dp-h640dp")
    fun atLargeTextTheColumnStandsAboveTheNamesInEnglish() {
        show(english, width = 320, fontScale = 1.5f)
        assertAbove(english)
    }

    @Test
    @Config(qualifiers = "en-w360dp-h640dp")
    fun anAmountTooLongForTheColumnTakesTheWholeListAboveTheNames() {
        val lines = english + IngredientLine("Flour", "12 ½ heaped tablespoons")
        show(lines)
        assertAbove(lines)
    }

    @Test
    @Config(qualifiers = "en-w360dp-h640dp")
    fun cookingKeepsItsOwnPresentation() {
        show(english, mode = CardMode.COOKING, groups = listOf(IngredientGroup("For the sauce", 0)))
        // The amount in the line's own reading style, the arrow after it.
        val style = quantityLayout(1).layoutInput.style
        assertEquals(ReadingFamily, style.fontFamily)
        assertTrue("the arrow follows the amount", arrowBounds(1).left >= quantityGlyphs(1).right - 0.5f)
        val heading = unmerged(IngredientTags.group(0))
        assertEquals("For the sauce", text(heading))
        assertNotEquals(GROUP_TRACKING, compose.textLayoutsWithin(IngredientTags.group(0)).single().layoutInput.style.letterSpacing)
    }
}
