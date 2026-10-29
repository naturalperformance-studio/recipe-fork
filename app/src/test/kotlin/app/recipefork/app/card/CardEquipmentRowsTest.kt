package app.recipefork.app.card

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.editor.textsOf
import app.recipefork.app.ui.AccessibilityChecks
import app.recipefork.app.ui.AppTheme
import app.recipefork.app.ui.appColorScheme
import app.recipefork.app.ui.appTypography
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.abs

/**
 * T-317, scenario 7 of v0.43: on View an item of equipment the kitchen has stands after a filled square, one it lacks
 * after an empty square with the words "Not in your kitchen" — shape and words, not colour alone — and the screen
 * reader says which; a lacking row still opens My kitchen, a target the card's 56 dp; a dotted rule runs under each
 * row; at twice the font scale on 320 dp nothing is cut; light and dark draw in the theme's colours only. An item the
 * app cannot tell about has no square, keeps its indent and says nothing of the kitchen (the owner, 2026-10-29).
 * Cooking keeps its own lines.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w360dp-h800dp-xxhdpi")
class CardEquipmentRowsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()

    /** The frying pan the kitchen has, and the saucepan (entry 10002) it lacks. */
    private val equipment = listOf(ShownEquipment("Frying pan", had = true), ShownEquipment("Saucepan 3 l", lackedEntry = 10002))

    private var dark by mutableStateOf(false)
    private var tab by mutableStateOf(CardMode.VIEW)
    private val opened = mutableListOf<Long>()

    private fun show(items: List<ShownEquipment> = equipment, mode: CardMode = CardMode.VIEW, opens: Boolean = true) {
        tab = mode
        compose.setContent {
            AppTheme(dark = dark) {
                Column(Modifier.padding(horizontal = GUTTER)) {
                    EquipmentList(items, marks = null, mode = tab, onOpenKitchen = if (opens) ({ opened += it }) else null)
                }
            }
        }
        compose.waitForIdle()
    }

    private fun row(index: Int) = CardTags.equipment(index)

    private fun node(tag: String, unmerged: Boolean = false): SemanticsNode =
        compose.onNodeWithTag(tag, useUnmergedTree = unmerged).fetchSemanticsNode()

    private fun bounds(tag: String): Rect = node(tag, unmerged = true).boundsInRoot

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun state(index: Int): String? = node(row(index)).config.getOrNull(SemanticsProperties.StateDescription)

    private fun image(tag: String): ImageBitmap = compose.onNodeWithTag(tag, useUnmergedTree = true).captureToImage()

    private fun close(expected: Color, shown: Color) =
        listOf(shown.red - expected.red, shown.green - expected.green, shown.blue - expected.blue).all { abs(it) < 0.03f }

    private fun assertColour(message: String, expected: Color, shown: Color) = assertTrue("$message: $shown, not $expected", close(expected, shown))

    // The kitchen has it: a filled square and its name, read as in the kitchen. It lacks it: an empty square, the name
    // and "Not in your kitchen" in words, read with the line.
    @Test
    fun aSquareAndWordsSayWhetherTheKitchenHasAnItem() {
        show()
        assertEquals(listOf("Frying pan"), compose.textsOf(row(0)))
        assertEquals("In your kitchen", state(0))
        assertEquals(listOf("Saucepan 3 l", "Not in your kitchen"), compose.textsOf(row(1)))
        assertNull("the lacking item's words are read with it, no state contradicts them", state(1))
        // The square stands before the name, in each row.
        equipment.indices.forEach { index ->
            val square = bounds(EquipmentTags.square(index))
            assertEquals(SQUARE_SIZE.value, square.width / density(), 0.5f)
            assertTrue("the square stands at the row's start", abs(square.left - bounds(row(index)).left) < 1f)
            assertTrue("the square stands in the row's middle", abs(square.center.y - bounds(row(index)).center.y) < 1f)
        }
        assertTrue("the words at the row's end", abs(bounds(EquipmentTags.lacking(1)).right - bounds(row(1)).right) < CHEVRON_ROOM * density())
    }

    // Shape, not colour alone: the square of what the kitchen has is filled, the one of what it lacks shows the tint
    // inside its outline; a dotted rule in the outline variant runs under each row. In the light and the dark theme,
    // each in the theme's own colours.
    @Test
    fun theSquaresAndRulesAreDrawnInTheThemesColoursLightAndDark() {
        show()
        listOf(false, true).forEach { isDark ->
            dark = isDark
            compose.waitForIdle()
            val scheme = appColorScheme(context.resources, isDark)
            val theme = if (isDark) "dark" else "light"
            val filled = image(EquipmentTags.square(0)).toPixelMap()
            val empty = image(EquipmentTags.square(1)).toPixelMap()
            val middle = filled.width / 2
            assertColour("$theme: the filled square's middle", scheme.onSurfaceVariant, filled[middle, middle])
            assertColour("$theme: the empty square's middle shows the tint", scheme.surfaceContainerLow, empty[middle, middle])
            assertColour("$theme: the empty square's outline", scheme.onSurfaceVariant, empty[2, middle])
            equipment.indices.forEach { index ->
                val shown = image(row(index)).toPixelMap()
                val bottom = (0 until shown.width).map { shown[it, shown.height - 1] }
                assertTrue("$theme: a dot of the rule under row $index", bottom.any { close(scheme.outlineVariant, it) })
                assertTrue("$theme: a gap of the rule under row $index", bottom.any { close(scheme.surfaceContainerLow, it) })
            }
        }
    }

    /** The kitchen has the pan, cannot tell of the ladle (free text, a consumable, an empty kitchen), lacks the saucepan. */
    private val three = listOf(ShownEquipment("Frying pan", had = true), ShownEquipment("Ladle"), ShownEquipment("Saucepan 3 l", lackedEntry = 10002))

    /** Where the name of the item at [index] starts. */
    private fun nameLeft(index: Int, name: String): Float =
        compose.onNode(hasText(name) and hasAnyAncestor(hasTestTag(row(index))), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.left

    // An item the app cannot tell about: no square, its name where the others' stand, nothing of the kitchen read.
    @Test
    fun anItemTheAppCannotTellAboutHasNoSquareButKeepsItsIndent() {
        show(three)
        assertTrue(exists(EquipmentTags.square(0)))
        assertFalse("no square for what the app cannot tell", exists(EquipmentTags.square(1)))
        assertTrue(exists(EquipmentTags.square(2)))
        assertEquals(listOf("Ladle"), compose.textsOf(row(1)))
        assertNull("nothing said of the kitchen", state(1))
        assertEquals("In your kitchen", state(0))
        val indent = (SQUARE_SIZE.value + SQUARE_GAP.value) * density()
        assertEquals("the name keeps the square's indent", bounds(row(1)).left + indent, nameLeft(1, "Ladle"), 1f)
        assertEquals(nameLeft(0, "Frying pan"), nameLeft(1, "Ladle"), 0.5f)
        assertEquals(nameLeft(2, "Saucepan 3 l"), nameLeft(1, "Ladle"), 0.5f)
        assertNull(node(row(1)).config.getOrNull(SemanticsActions.OnClick))
    }

    @Test
    @Config(qualifiers = "ru-w360dp-h800dp-xxhdpi")
    fun inRussianAnItemTheAppCannotTellAboutSaysNothingOfTheKitchen() {
        show(three)
        assertFalse(exists(EquipmentTags.square(1)))
        assertNull("not «Есть на кухне»", state(1))
        assertEquals("Есть на кухне", state(0))
        assertEquals(listOf("Ladle"), compose.textsOf(row(1)))
        assertEquals(nameLeft(0, "Frying pan"), nameLeft(1, "Ladle"), 0.5f)
    }

    // The theme's colours only: the rows name no colour of their own.
    @Test
    fun theRowsNameNoColourOfTheirOwn() {
        val source = File("src/main/kotlin/app/recipefork/app/card/EquipmentList.kt").readText()
        assertFalse("no colour literal", Regex("""Color\s*\(|Color\.\w""").containsMatchIn(source))
        assertTrue("the square and the words in the muted ink", "colorScheme.onSurfaceVariant" in source)
        assertTrue("the rule in the outline variant", "colorScheme.outlineVariant" in source)
    }

    // The lacking row still opens My kitchen at its entry, a button the card's 56 dp; a row the kitchen does not lack
    // answers no tap and stands 48 dp, as the page's rows.
    @Test
    fun aLackingRowOpensMyKitchenAndIsTheCardsTarget() {
        show()
        compose.assertCardTarget(row(1))
        val lacking = node(row(1))
        assertEquals("Open My kitchen", AccessibilityChecks.clickActionLabel(lacking))
        assertEquals(Role.Button, lacking.config.getOrNull(SemanticsProperties.Role))
        compose.onNodeWithTag(row(1)).performClick()
        compose.waitForIdle()
        assertEquals(listOf(10002L), opened)
        assertNull(node(row(0)).config.getOrNull(SemanticsActions.OnClick))
        assertEquals(VIEW_ROW_HEIGHT.value, bounds(row(0)).height / density(), 1f)
        AccessibilityChecks.assertTouchTargets(compose, min = CardTarget)
    }

    @Test
    @Config(qualifiers = "ru-w360dp-h800dp-xxhdpi")
    fun theRowsReadInRussian() {
        show()
        assertEquals("Есть на кухне", state(0))
        assertEquals("Нет на кухне", compose.textsOf(row(1)).last())
        assertEquals("Открыть «Мою кухню»", AccessibilityChecks.clickActionLabel(node(row(1))))
    }

    /** Names long enough to wrap at twice the font scale on 320 dp. */
    private val long = listOf(
        ShownEquipment("Heavy cast-iron frying pan with a lid", had = true),
        ShownEquipment("Large enamelled saucepan of at least five litres", lackedEntry = 10002),
    )

    // At twice the font scale on 320 dp every row reads whole: the words move under the name, at the row's end, and the
    // lacking row stays the card's target.
    @Test
    @Config(qualifiers = "en-w320dp-h640dp", fontScale = 2f)
    fun atTwiceTheFontScaleOn320dpNothingIsCut() = assertWholeAtLargeText()

    @Test
    @Config(qualifiers = "ru-w320dp-h640dp", fontScale = 2f)
    fun inRussianAtTwiceTheFontScaleOn320dpNothingIsCut() = assertWholeAtLargeText()

    private fun assertWholeAtLargeText() {
        show(long)
        long.indices.forEach { compose.assertWhole(row(it)) }
        val name = compose.textLayoutsWithin(row(1)).first { it.layoutInput.text.text == long[1].text }
        assertTrue("the name wraps", name.lineCount > 1)
        val words = bounds(EquipmentTags.lacking(1))
        assertTrue("the words stand under the name", words.top >= bounds(row(1)).top + name.size.height - 0.5f)
        assertTrue("the words lie within the row", words.bottom <= bounds(row(1)).bottom + 0.5f)
        compose.assertCardTarget(row(1))
    }

    // F-2 (the owner, 2026-10-29): a name wrapping to several lines keeps its square level with its first line, centred
    // on that line's height, not on the whole block; a one-line row keeps its square in the row's middle.
    @Test
    @Config(qualifiers = "en-w320dp-h640dp", fontScale = 2f)
    fun atTwiceTheFontScaleOn320dpTheSquareStandsLevelWithTheNamesFirstLine() {
        val items = long + ShownEquipment("Pan", had = true)
        show(items)
        long.indices.forEach { index ->
            val name = compose.textLayoutsWithin(row(index)).first { it.layoutInput.text.text == long[index].text }
            assertTrue("the name of row $index wraps to several lines", name.lineCount > 2)
            val top = compose.onNode(hasText(long[index].text) and hasAnyAncestor(hasTestTag(row(index))), useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot.top
            val firstLine = top + (name.getLineTop(0) + name.getLineBottom(0)) / 2
            val square = bounds(EquipmentTags.square(index))
            assertEquals("row $index: the square level with the first line", firstLine, square.center.y, 1f)
            assertTrue("row $index: not in the block's middle", square.center.y < bounds(row(index)).center.y - name.size.height / 4)
        }
        val one = bounds(EquipmentTags.square(2))
        assertEquals("a one-line row: the square in the row's middle", bounds(row(2)).center.y, one.center.y, 1f)
    }

    // Cooking keeps its own lines: no square, the words under the name, no kitchen state.
    @Test
    fun cookingKeepsItsOwnLines() {
        show(mode = CardMode.COOKING)
        equipment.indices.forEach { assertFalse("no square on Cooking", exists(EquipmentTags.square(it))) }
        val name = compose.textLayoutsWithin(row(1)).first { it.layoutInput.text.text == "Saucepan 3 l" }
        assertTrue("the words under the name", bounds(EquipmentTags.lacking(1)).top >= bounds(row(1)).top + name.size.height)
        assertNull("no kitchen state on Cooking", state(0))
        // Cooking's words in the reading face's bodyMedium, as before the redesign; View's in the working face's labelLarge.
        assertWordsIn("Cooking", appTypography(context.resources).bodyMedium)
        tab = CardMode.VIEW
        compose.waitForIdle()
        assertWordsIn("View", appTypography(context.resources).labelLarge)
    }

    /** The lacking item's words are set in [expected]'s family, weight and size. */
    private fun assertWordsIn(tab: String, expected: TextStyle) {
        val words = compose.textLayoutsWithin(EquipmentTags.lacking(1)).single().layoutInput.style
        assertEquals("$tab: the words' family", expected.fontFamily, words.fontFamily)
        assertEquals("$tab: the words' weight", expected.fontWeight, words.fontWeight)
        assertEquals("$tab: the words' size", expected.fontSize, words.fontSize)
    }

    private fun density() = compose.density.density

    private companion object {
        /** The card's side gutter, which the tonal surface reaches into. */
        val GUTTER = 16.dp

        /** Room for the chevron and its gap after the words, in dp. */
        const val CHEVRON_ROOM = 26f
    }
}
