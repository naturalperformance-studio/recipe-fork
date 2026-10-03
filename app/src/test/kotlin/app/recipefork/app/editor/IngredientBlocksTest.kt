package app.recipefork.app.editor

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.toSize
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import app.recipefork.app.R
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.data.HeldDragHandleSide
import app.recipefork.app.library.LibrarySession
import app.recipefork.app.library.LibraryTags
import app.recipefork.app.library.LibraryView
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.ui.AccessibilityChecks
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.RecipeState
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.Rational
import java.time.Instant
import kotlin.math.abs
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Each ingredient row as one bounded block on a 360 dp phone, at font scale 1.0 and 2.0: every part of a row inside
 * its block, blocks apart from each other with an outline drawn round each, a tinted column down the left edge of each
 * block — or the right edge, with the handle on the right — with the handle icon at its centre, the inputs beside it at one left edge and of one height, and the name's text
 * with room to be read; the free-text warning as an icon beside the name whose text the screen reader reads and a tap
 * or a long press shows; a row dragged from anywhere in its column moving whole; a frozen recipe's rows as blocks,
 * with no column.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IngredientBlocksTest(private val handleRight: Boolean) {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val device = TestDevice(context)
    private val active = ActiveBook(BookName.Pool, book.database)
    private val recipes = RecipeRepository(active)
    private val session = LibrarySession(SavedStateHandle())
    private val created = Instant.parse("2026-10-02T12:00:00Z")
    private val opened = mutableStateOf<RecipeId?>(null)

    @Before
    fun setUp() {
        compose.registerIdlingResource(book.idling)
        compose.registerIdlingResource(device.idling)
        runBlocking { device.database.dictionaryDao().replace(SAMPLE_DICTIONARIES) }
    }

    @After
    fun close() {
        compose.unregisterIdlingResource(book.idling)
        compose.unregisterIdlingResource(device.idling)
        book.close()
        device.close()
    }

    /** Four rows: a free-text piece (warning and divisibility), a dictionary entry, a free text to taste, a long name. */
    private fun rows(): List<Ingredient> = listOf(
        Ingredient(0, null, null, "черемша", UnitType.PIECE, QuantityUnit.PIECE, Rational.of(2)),
        Ingredient(1, 1, null, null, UnitType.EXACT, QuantityUnit.GRAM, Rational.of(500)),
        Ingredient(2, null, null, "соль морская мелкая", UnitType.TASTE_UNMEASURED, null, null),
        Ingredient(3, null, null, "мука пшеничная высшего сорта", UnitType.HOUSEHOLD, QuantityUnit.TABLESPOON, Rational.of(1, 2)),
    )

    /** Opens the editor at [fontScale] directly on a seeded recipe holding [ingredients], frozen if [state] says so. */
    private fun open(fontScale: Float, ingredients: List<Ingredient> = rows(), state: RecipeState? = null): RecipeId {
        var recipe = Recipe.create(created, null).copy(title = "Pancakes", servings = 4, ingredients = ingredients)
        if (state != null) recipe = recipe.inState(state)
        runBlocking { recipes.save(recipe) }
        val environment = EditorEnvironment(active, device.catalog(context), dragHandleSide = HeldDragHandleSide(handleRight)) { created.plusSeconds(60) }
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                MaterialTheme { LibraryOrEditor(session, recipes, environment, opened) }
            }
        }
        compose.onNodeWithTag(LibraryTags.tab(LibraryView.OWN)).performClick()
        compose.waitForIdle()
        compose.runOnIdle { opened.value = recipe.id }
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.ingredient(ingredients.lastIndex)), PATIENCE_MS)
        // The dictionary is read: the entry's row shows its label.
        if (state == null) compose.waitUntil(PATIENCE_MS) { fieldText(EditorTags.ingredientName(1)) in setOf("курица", "chicken") }
        return recipe.id
    }

    private fun node(tag: String): SemanticsNode = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()

    private fun exists(tag: String): Boolean = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun bounds(tag: String): Rect = node(tag).let { Rect(it.positionInRoot, it.size.toSize()) }

    private fun fieldText(tag: String): String = compose.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.EditableText].text

    private fun px(dp: Float): Float = dp * compose.density.density

    private fun string(id: Int): String = context.getString(id)

    /** Every part of the editable row at [position] that is on the screen. */
    private fun parts(position: Int): List<String> = listOf(
        EditorTags.ingredientHandle(position),
        EditorTags.ingredientHandleIcon(position),
        EditorTags.ingredientName(position),
        EditorTags.ingredientRemove(position),
        EditorTags.ingredientMenu(position),
        EditorTags.ingredientQuantity(position),
        EditorTags.ingredientUnit(position),
        EditorTags.ingredientWarning(position),
        EditorTags.divisibility(position),
    ).filter(::exists)

    /**
     * The blocks of [count] rows: each apart from the next, with a background gap between them, and each with an
     * outline drawn at its top, bottom and sides — something other than the background just inside its edges.
     */
    private fun assertSeparateOutlinedBlocks(count: Int) {
        val list = bounds(EditorTags.INGREDIENTS)
        val image = compose.onNodeWithTag(EditorTags.INGREDIENTS).captureToImage().toPixelMap()
        fun at(x: Float, y: Float): Color = image[(x - list.left).toInt().coerceIn(0, image.width - 1), (y - list.top).toInt().coerceIn(0, image.height - 1)]
        fun differs(a: Color, b: Color) = abs(a.red - b.red) + abs(a.green - b.green) + abs(a.blue - b.blue) > 0.05f
        val blocks = (0 until count).map { bounds(EditorTags.ingredient(it)) }
        blocks.zipWithNext().forEachIndexed { i, (upper, lower) ->
            assertTrue("row $i ends ${lower.top - upper.bottom} px before row ${i + 1} begins", lower.top - upper.bottom >= px(4f) - 0.5f)
        }
        val background = at(blocks[0].center.x, (blocks[0].bottom + blocks[1].top) / 2)
        blocks.forEachIndexed { i, block ->
            val inset = px(2f)
            fun outlined(side: String, points: List<Offset>) =
                assertTrue("row $i has its outline at its $side", points.any { differs(at(it.x, it.y), background) })
            outlined("top", (0..2).map { Offset(block.center.x, block.top + it) })
            outlined("bottom", (1..3).map { Offset(block.center.x, block.bottom - it) })
            outlined("left", (0..2).map { Offset(block.left + it, block.center.y) })
            outlined("right", (1..3).map { Offset(block.right - it, block.center.y) })
            // And background just outside it, between it and the next.
            if (i < count - 1) assertFalse("row $i runs into row ${i + 1}", differs(at(block.center.x, block.bottom + px(1f) + inset), background))
        }
    }

    // Each editable row is one bounded block, separated from the next; every part of it — the divisibility question
    // and the warning included — inside it; the drag column down its left edge, and the inputs beside it at one left edge.
    @Test
    @Config(qualifiers = "ru-w360dp-h4000dp")
    fun everyRowIsOneBlockInRussian() = everyRowIsOneBlock(1f)

    @Test
    @Config(qualifiers = "ru-w360dp-h4000dp")
    fun everyRowIsOneBlockInRussianAtDoubleFontScale() = everyRowIsOneBlock(2f)

    @Test
    @Config(qualifiers = "en-w360dp-h4000dp")
    fun everyRowIsOneBlockInEnglishAtDoubleFontScale() = everyRowIsOneBlock(2f)

    private fun everyRowIsOneBlock(fontScale: Float) {
        open(fontScale)
        val count = rows().size
        (0 until count).forEach { position ->
            val block = bounds(EditorTags.ingredient(position))
            parts(position).forEach { part ->
                val b = bounds(part)
                assertTrue(
                    "$part ($b) lies inside its row ($block)",
                    b.left >= block.left - 0.5f && b.right <= block.right + 0.5f && b.top >= block.top - 0.5f && b.bottom <= block.bottom + 0.5f,
                )
            }
            val column = bounds(EditorTags.ingredientHandle(position))
            val icon = bounds(EditorTags.ingredientHandleIcon(position))
            val name = bounds(EditorTags.ingredientName(position))
            val unit = bounds(EditorTags.ingredientUnit(position))
            val below = if (exists(EditorTags.ingredientQuantity(position))) bounds(EditorTags.ingredientQuantity(position)) else unit
            // The column runs down the block's left edge, its full height, 48 dp wide and no wider, the icon at its centre.
            if (handleRight) {
                assertEquals("row $position: the column ends at the block's right edge", block.right, column.right, 0.5f)
            } else {
                assertEquals("row $position: the column starts at the block's left edge", block.left, column.left, 0.5f)
            }
            assertEquals("row $position: the column starts at the block's top", block.top, column.top, 0.5f)
            assertEquals("row $position: the column ends at the block's bottom", block.bottom, column.bottom, 0.5f)
            assertEquals("row $position: the column is ${column.width / compose.density.density} dp wide", px(48f), column.width, 0.5f)
            assertEquals("row $position: the icon is centred across the column", column.center.x, icon.center.x, 1f)
            assertEquals("row $position: the icon is centred down the column", column.center.y, icon.center.y, 1f)
            // Nothing that drags stands among the inputs: every other part of the row is beside the column.
            (parts(position) - setOf(EditorTags.ingredientHandle(position), EditorTags.ingredientHandleIcon(position))).forEach {
                val b = bounds(it)
                if (handleRight) assertTrue("row $position: $it is left of the column", b.right <= column.left + 0.5f)
                else assertTrue("row $position: $it is right of the column", b.left >= column.right - 0.5f)
            }
            if (handleRight) assertTrue("row $position: the inputs start by the block's left edge", name.left - block.left <= px(16f))
            // One left edge: the name starts the first line, the quantity (or the unit, with no quantity) the second.
            assertEquals("row $position: the name and the field below it share their left edge", name.left, below.left, 0.5f)
            assertTrue("row $position: the quantity and unit are below the name", below.top >= name.bottom - 0.5f)
            // The menu beside the name and the unit beside a quantity end at one right edge.
            val menu = bounds(EditorTags.ingredientMenu(position))
            if (below != unit && menu.top < name.bottom) assertEquals("row $position: the menu and the unit end together", menu.right, unit.right, 0.5f)
        }
        // The divisibility question of the free-text piece stays inside its block, below its fields.
        assertTrue(exists(EditorTags.divisibility(0)))
        assertTrue(bounds(EditorTags.divisibility(0)).top >= bounds(EditorTags.ingredientUnit(0)).bottom - 0.5f)
        // Nothing of a row leaves the screen's width.
        val screen = bounds(EditorTags.FORM)
        (0 until count).forEach { assertTrue(bounds(EditorTags.ingredient(it)).right <= screen.right + 0.5f) }
        assertSeparateOutlinedBlocks(count)
    }

    // The name field, the quantity field and the unit select of a row are of one height, and the quantity and the
    // unit stand level on their line — at font scale 1.0 and 2.0.
    @Test
    @Config(qualifiers = "ru-w360dp-h4000dp")
    fun theInputsOfARowAreOfOneHeight() = inputsAreOfOneHeight(1f)

    @Test
    @Config(qualifiers = "ru-w360dp-h4000dp")
    fun theInputsOfARowAreOfOneHeightAtDoubleFontScale() = inputsAreOfOneHeight(2f)

    @Test
    @Config(qualifiers = "en-w360dp-h4000dp")
    fun theInputsOfARowAreOfOneHeightInEnglishAtDoubleFontScale() = inputsAreOfOneHeight(2f)

    private fun inputsAreOfOneHeight(fontScale: Float) {
        open(fontScale)
        (0 until rows().size).forEach { position ->
            val name = bounds(EditorTags.ingredientName(position))
            val unit = bounds(EditorTags.ingredientUnit(position))
            fun dp(px: Float) = px / compose.density.density
            assertEquals("row $position: the unit (${dp(unit.height)} dp) is as tall as the name (${dp(name.height)} dp)", name.height, unit.height, 0.5f)
            if (exists(EditorTags.ingredientQuantity(position))) {
                val quantity = bounds(EditorTags.ingredientQuantity(position))
                assertEquals("row $position: the quantity is as tall as the name", name.height, quantity.height, 0.5f)
                // Side by side, they stand level; stacked, the unit is below the quantity.
                if (unit.left > quantity.left) {
                    assertEquals("row $position: the quantity and the unit stand level", quantity.top, unit.top, 0.5f)
                } else {
                    assertTrue("row $position: the unit is below the quantity", unit.top >= quantity.bottom - 0.5f)
                }
            }
        }
    }

    // The quantity field takes the line from the block's left edge up to the unit: nothing stands before it any more.
    @Test
    @Config(qualifiers = "ru-w360dp-h4000dp")
    fun theQuantityTakesTheWholeLineBeforeTheUnit() {
        open(1f)
        val quantity = bounds(EditorTags.ingredientQuantity(0))
        val unit = bounds(EditorTags.ingredientUnit(0))
        val name = bounds(EditorTags.ingredientName(0))
        assertEquals(name.left, quantity.left, 0.5f)
        assertEquals("only the 8 dp gap between the quantity and the unit", px(8f), unit.left - quantity.right, 0.5f)
        assertTrue("the quantity is ${quantity.width / compose.density.density} dp wide", quantity.width >= px(120f))
    }

    // A11Y-004: at font scale 2.0 the quantity's label is not wrapped onto the name above it — where it cannot have one
    // line beside the unit, the quantity takes the whole line and the unit the line below, both from one left edge.
    @Test
    @Config(qualifiers = "ru-w360dp-h4000dp")
    fun theQuantityLabelHasOneLineAtDoubleFontScale() = quantityLabelHasOneLine()

    @Test
    @Config(qualifiers = "en-w360dp-h4000dp")
    fun theQuantityLabelHasOneLineInEnglishAtDoubleFontScale() = quantityLabelHasOneLine()

    private fun quantityLabelHasOneLine() {
        open(2f)
        val label = widthOf(string(R.string.editor_ingredient_quantity), 2f, Typography().bodySmall)
        listOf(0, 1, 3).forEach { position ->
            val quantity = bounds(EditorTags.ingredientQuantity(position))
            val unit = bounds(EditorTags.ingredientUnit(position))
            assertTrue("row $position: the quantity (${quantity.width} px) has room for its label ($label px) on one line", quantity.width - px(32f) >= label)
            assertEquals("row $position: the unit starts where the quantity does, or beside it", true, unit.left > quantity.right || abs(unit.left - quantity.left) < 0.5f)
            assertTrue("row $position: the quantity is below the name", quantity.top >= bounds(EditorTags.ingredientName(position)).bottom - 0.5f)
        }
    }

    // The column is tinted, so the area to drag is seen: its colour differs from the block's own down its centre line,
    // near both its ends — and its icon marks it besides the colour.
    @Test
    @Config(qualifiers = "ru-w360dp-h4000dp")
    fun theColumnIsTintedAndMarkedByItsIcon() {
        open(1f)
        val list = bounds(EditorTags.INGREDIENTS)
        val image = compose.onNodeWithTag(EditorTags.INGREDIENTS).captureToImage().toPixelMap()
        fun at(x: Float, y: Float): Color = image[(x - list.left).toInt().coerceIn(0, image.width - 1), (y - list.top).toInt().coerceIn(0, image.height - 1)]
        fun differs(a: Color, b: Color) = abs(a.red - b.red) + abs(a.green - b.green) + abs(a.blue - b.blue) > 0.02f
        (0 until rows().size).forEach { position ->
            val column = bounds(EditorTags.ingredientHandle(position))
            val unit = bounds(EditorTags.ingredientUnit(position))
            // The block's own colour: just below the unit select, inside the block.
            val body = at(unit.center.x, unit.bottom + px(2f))
            val tint = at(column.center.x, column.top + px(12f))
            assertTrue("row $position: the column is tinted ($tint against $body)", differs(tint, body))
            assertFalse("row $position: the column is one tint to its bottom", differs(tint, at(column.center.x, column.bottom - px(12f))))
            assertTrue("row $position: the column carries its icon", exists(EditorTags.ingredientHandleIcon(position)))
        }
    }

    // FLOW-022 (2): the warning is an icon beside the name, not a paragraph under the row; the screen reader reads its
    // full text without anything being opened; a tap shows the text in a tooltip, and so does a long press.
    @Test
    @Config(qualifiers = "ru-w360dp-h4000dp")
    fun theFreeTextWarningIsAnIconWithItsTextInATooltipInRussian() = theFreeTextWarningIsAnIcon()

    @Test
    @Config(qualifiers = "en-w360dp-h4000dp")
    fun theFreeTextWarningIsAnIconWithItsTextInATooltip() = theFreeTextWarningIsAnIcon()

    private fun theFreeTextWarningIsAnIcon() {
        open(1f)
        val text = string(R.string.editor_ingredient_free_text_warning)
        // Free text warns; the dictionary entry does not.
        assertEquals(listOf(true, false, true, true), (0..3).map { exists(EditorTags.ingredientWarning(it)) })
        listOf(0, 2, 3).forEach { position ->
            val icon = node(EditorTags.ingredientWarning(position))
            val name = bounds(EditorTags.ingredientName(position))
            val b = bounds(EditorTags.ingredientWarning(position))
            assertTrue("a 48 dp target (${icon.size})", icon.size.width >= px(48f) - 0.5f && icon.size.height >= px(48f) - 0.5f)
            assertTrue("beside the name, on its line ($b in $name)", b.left >= name.left && b.right <= name.right + 0.5f && b.top < name.bottom && b.bottom > name.top)
            // The full text, read by the screen reader on the row as it stands — a shape and words, not a colour.
            assertEquals(listOf(text), compose.descriptionOf(EditorTags.ingredientWarning(position)))
        }
        // Nowhere a paragraph: no text on the screen holds the warning.
        val paragraphs = compose.onAllNodes(SemanticsMatcher("any node") { true }, useUnmergedTree = true)
            .fetchSemanticsNodes().filter { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == text } }
        assertEquals(emptyList<SemanticsNode>(), paragraphs)
        assertFalse(exists(EditorTags.ingredientWarningText(0)))

        // A tap shows the text.
        compose.onNodeWithTag(EditorTags.ingredientWarning(0)).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.ingredientWarningText(0)) }
        assertTrue(shownTooltip(0).contains(text))

        // A long press shows it too.
        compose.onNodeWithTag(EditorTags.ingredientWarning(3)).performScrollTo().performTouchInput { longClick() }
        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.ingredientWarningText(3)) }
        assertTrue(shownTooltip(3).contains(text))
    }

    /** The texts inside the tooltip of the row at [position]. */
    private fun shownTooltip(position: Int): List<String> {
        val tooltip = node(EditorTags.ingredientWarningText(position))
        fun texts(node: SemanticsNode): List<String> =
            node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } + node.children.flatMap(::texts)
        return texts(tooltip)
    }

    /**
     * The width the name field of row [position] leaves for its text: from where the field's text starts — Material 3
     * pads it 16 dp from the field's edge — to the warning icon at its end.
     */
    private fun nameTextWidth(position: Int): Float =
        bounds(EditorTags.ingredientWarning(position)).left - (bounds(EditorTags.ingredientName(position)).left + px(16f))

    /** How wide [text] is laid out in the name field's style at [fontScale]. */
    private fun widthOf(text: String, fontScale: Float, style: TextStyle = Typography().bodyLarge): Float {
        val measurer = TextMeasurer(createFontFamilyResolver(context), Density(compose.density.density, fontScale), LayoutDirection.Ltr)
        return measurer.measure(text, style).size.width.toFloat()
    }

    // Criterion 1 at large text: a free-text name beside its warning icon keeps room for about eight Cyrillic letters
    // on a 360 dp phone at font scale 2.0, so "черемша" reads whole.
    @Test
    @Config(qualifiers = "ru-w360dp-h4000dp")
    fun aFreeTextNameKeepsRoomForItsTextAtDoubleFontScale() {
        open(2f)
        val room = nameTextWidth(0)
        val eight = widthOf("морковка", 2f)
        assertTrue("the name leaves ${room / compose.density.density} dp for its text, eight letters take ${eight / compose.density.density} dp", room >= eight)
    }

    // On a 320 dp phone at font scale 2.0 the text of a free-text name still has room of its own.
    @Test
    @Config(qualifiers = "ru-w320dp-h4000dp")
    fun aFreeTextNameKeepsUsableRoomOnANarrowPhoneAtDoubleFontScale() {
        open(2f)
        val room = nameTextWidth(0)
        val four = widthOf("черм", 2f)
        assertTrue("the name leaves ${room / compose.density.density} dp for its text, four letters take ${four / compose.density.density} dp", room >= four)
    }

    private fun isFocused(tag: String): Boolean = node(tag).config.getOrNull(SemanticsProperties.Focused) == true

    // A free-text name typed letter by letter keeps its focus and its text while the warning icon appears beside it;
    // tapping the icon neither takes the focus from the field nor gives it to it.
    @Test
    @Config(qualifiers = "ru-w360dp-h4000dp")
    fun typingAFreeTextNameKeepsFocusAsTheWarningAppears() {
        open(1f, ingredients = rows() + Ingredient(4, null, null, null, UnitType.EXACT, QuantityUnit.GRAM, null))
        val name = EditorTags.ingredientName(4)
        compose.onNodeWithTag(name).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { isFocused(name) }
        assertFalse(exists(EditorTags.ingredientWarning(4)))
        var typed = ""
        "щавель".forEach { letter ->
            compose.onNodeWithTag(name).performTextInput(letter.toString())
            typed += letter
            compose.waitForIdle()
            assertEquals(typed, fieldText(name))
            assertTrue("the field keeps the focus after \"$typed\"", isFocused(name))
            assertTrue("the warning shows after \"$typed\"", exists(EditorTags.ingredientWarning(4)))
        }
        // A tap on the icon while the field is focused: the field keeps it, the icon does not take it.
        compose.onNodeWithTag(EditorTags.ingredientWarning(4)).performClick()
        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.ingredientWarningText(4)) }
        assertTrue(isFocused(name))
        assertFalse(isFocused(EditorTags.ingredientWarning(4)))
        assertEquals("щавель", fieldText(name))
        // With the focus elsewhere, a tap on the icon does not give it to the field.
        compose.onNodeWithTag(name).performImeAction()
        compose.waitUntil(PATIENCE_MS) { !isFocused(name) }
        compose.onNodeWithTag(EditorTags.ingredientWarning(0)).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.ingredientWarningText(0)) }
        assertFalse(isFocused(EditorTags.ingredientName(0)))
        assertFalse(isFocused(name))
        assertFalse(isFocused(EditorTags.ingredientWarning(0)))
    }

    // FLOW-025: a row dragged from anywhere in its column — its centre, both its ends and both its sides — moves as one
    // block: every part of it travels with it, by the same distance, and it lands where it is dropped.
    @Test
    @Config(qualifiers = "ru-w360dp-h4000dp")
    fun aRowDraggedFromAnywhereInItsColumnMovesAsOneBlock() {
        open(1f)
        val names = listOf("черемша", "курица")
        listOf(
            "its centre" to Offset(0.5f, 0.5f),
            "its top" to Offset(0.5f, 0.03f),
            "its bottom" to Offset(0.5f, 0.97f),
            "its left side" to Offset(0.05f, 0.3f),
            "its right side" to Offset(0.95f, 0.7f),
        ).forEachIndexed { i, (where, at) ->
            val tags = listOf(EditorTags.ingredient(0)) + parts(0)
            val before = tags.associateWith { bounds(it).top }
            val column = EditorTags.ingredientHandle(0)
            val distance = bounds(EditorTags.ingredient(1)).bottom - bounds(EditorTags.ingredient(0)).bottom
            compose.onNodeWithTag(column, useUnmergedTree = true).performTouchInput {
                down(Offset(width * at.x, height * at.y))
                repeat(10) { moveBy(Offset(0f, distance / 20)) }
            }
            compose.waitForIdle()
            val moved = bounds(EditorTags.ingredient(0)).top - before.getValue(EditorTags.ingredient(0))
            assertTrue("dragged from $where, the row follows the finger ($moved px)", moved > px(8f))
            tags.forEach { tag -> assertEquals("dragged from $where, $tag moves with its row", moved, bounds(tag).top - before.getValue(tag), 1f) }
            compose.onNodeWithTag(column, useUnmergedTree = true).performTouchInput {
                repeat(10) { moveBy(Offset(0f, distance / 20)) }
                up()
            }
            compose.waitForIdle()
            // Dropped past the second row's middle, it lands second, as the menu's "Move down" would put it.
            val first = names[(i + 1) % 2]
            val second = names[i % 2]
            compose.waitUntil(PATIENCE_MS) { fieldText(EditorTags.ingredientName(1)) == second }
            assertEquals("dragged from $where", first, fieldText(EditorTags.ingredientName(0)))
            // Dropped, every part is back on its place.
            compose.waitUntil(PATIENCE_MS) { abs(bounds(EditorTags.ingredient(0)).top - before.getValue(EditorTags.ingredient(0))) < 1f }
        }
    }

    // A frozen recipe's rows read as blocks too.
    @Test
    @Config(qualifiers = "ru-w360dp-h4000dp")
    fun aFrozenRecipesRowsAreBlocks() = frozenRowsAreBlocks(1f)

    @Test
    @Config(qualifiers = "ru-w360dp-h4000dp")
    fun aFrozenRecipesRowsAreBlocksAtDoubleFontScale() = frozenRowsAreBlocks(2f)

    private fun frozenRowsAreBlocks(fontScale: Float) {
        open(fontScale, state = RecipeState.PUBLISHED)
        (0 until rows().size).forEach { position ->
            assertFalse(exists(EditorTags.ingredientHandle(position)))
            assertFalse(exists(EditorTags.ingredientHandleIcon(position)))
        }
        assertSeparateOutlinedBlocks(rows().size)
    }

    /** The action icons of the editable row at [position], in their order. */
    private fun actions(position: Int) = listOf(EditorTags.ingredientRemove(position), EditorTags.ingredientMenu(position))

    private fun labels(position: Int): List<String> = actions(position).map { AccessibilityChecks.announcedLabel(compose.onNodeWithTag(it).fetchSemanticsNode()) }

    private fun dp(px: Float) = px / compose.density.density

    // The block is no taller than it was before its actions became the step's icons: measured at 360 dp and font
    // scale 1.0 on the block as it stood, 144 dp for a row of name, quantity and unit, 284 dp with the divisibility
    // question. The icons stand on the name's line, so they add no line of their own.
    @Test
    @Config(qualifiers = "ru-w360dp-h4000dp")
    fun theBlockIsNoTallerThanBefore() = blockIsNoTallerThanBefore()

    @Test
    @Config(qualifiers = "en-w360dp-h4000dp")
    fun theBlockIsNoTallerThanBeforeInEnglish() = blockIsNoTallerThanBefore()

    private fun blockIsNoTallerThanBefore() {
        open(1f)
        val before = listOf(284f, 144f, 144f, 144f)
        (0 until rows().size).forEach { position ->
            val height = dp(bounds(EditorTags.ingredient(position)).height)
            assertTrue("row $position is $height dp tall, it was ${before[position]} dp", height <= before[position] + 0.5f)
            val name = bounds(EditorTags.ingredientName(position))
            actions(position).forEach {
                val b = bounds(it)
                assertTrue("row $position: $it stands on the name's line", b.top >= name.top - 0.5f && b.bottom <= name.bottom + 0.5f)
                assertTrue("row $position: $it is right of the name", b.left >= name.right - 0.5f)
            }
        }
    }

    // The row's actions are icons as a step's are: 48 dp each, side by side in one row, inside the block, and no text
    // button anywhere in the block — at font scale 1.0 beside the name, at 2.0 in a row of their own under the inputs.
    @Test
    @Config(qualifiers = "ru-w360dp-h4000dp")
    fun theActionsAreIconsInOneRow() = actionsAreIconsInOneRow(1f)

    @Test
    @Config(qualifiers = "ru-w360dp-h4000dp")
    fun theActionsAreIconsInOneRowAtDoubleFontScale() = actionsAreIconsInOneRow(2f)

    @Test
    @Config(qualifiers = "en-w360dp-h4000dp")
    fun theActionsAreIconsInOneRowInEnglishAtDoubleFontScale() = actionsAreIconsInOneRow(2f)

    private fun actionsAreIconsInOneRow(fontScale: Float) {
        open(fontScale)
        (0 until rows().size).forEach { position ->
            val block = bounds(EditorTags.ingredient(position))
            val name = bounds(EditorTags.ingredientName(position))
            val unit = bounds(EditorTags.ingredientUnit(position))
            val controls = actions(position).map { bounds(it) }
            controls.forEach { b ->
                assertEquals("row $position: an icon is 48 dp wide", 48f, dp(b.width), 0.5f)
                assertEquals("row $position: an icon is 48 dp high", 48f, dp(b.height), 0.5f)
                assertTrue("row $position: the icon lies inside the block", b.left >= block.left - 0.5f && b.right <= block.right + 0.5f && b.top >= block.top - 0.5f && b.bottom <= block.bottom + 0.5f)
            }
            val (remove, menu) = controls
            assertEquals("row $position: the icons stand in one row", remove.top, menu.top, 0.5f)
            assertTrue("row $position: remove, then the menu", menu.left >= remove.right - 0.5f)
            if (fontScale == 2f) {
                // The name has the whole line: it reaches the block's right edge, and the icons are under the inputs.
                val end = if (handleRight) bounds(EditorTags.ingredientHandle(position)).left else block.right
                assertTrue("row $position: the name reaches the block's right edge", end - name.right <= px(4f) + 0.5f)
                assertTrue("row $position: the icons are under the unit", remove.top >= unit.bottom - 0.5f)
                if (exists(EditorTags.ingredientQuantity(position))) assertTrue(remove.top >= bounds(EditorTags.ingredientQuantity(position)).bottom - 0.5f)
                assertEquals("row $position: the icons start at the inputs' left edge", name.left, remove.left, 0.5f)
                if (exists(EditorTags.divisibility(position))) {
                    assertTrue("row $position: the icons come before the divisibility question", remove.bottom <= bounds(EditorTags.divisibility(position)).top + 0.5f)
                }
            }
            // No text button: in the merged tree a button carries the text of everything inside it, so a button in the
            // block with any text is a text button; the icons carry only their spoken names.
            val inRow = hasAnyAncestor(hasTestTag(EditorTags.ingredient(position)))
            val texts = compose.onAllNodes(inRow and AccessibilityChecks.isInteractive).fetchSemanticsNodes()
                .filter { it.config.getOrNull(SemanticsProperties.EditableText) == null }
                .filter { it.config.getOrNull(SemanticsProperties.Role) == androidx.compose.ui.semantics.Role.Button }
                .filter { it.config.getOrNull(SemanticsProperties.Text).orEmpty().isNotEmpty() }
            assertEquals("row $position: no text buttons", emptyList<SemanticsNode>(), texts)
        }
        AccessibilityChecks.assertTouchTargets(compose)
        AccessibilityChecks.assertInteractiveElementsLabelled(compose)
    }

    // Each icon is named for the screen reader by its row, and a long press shows that name in a tooltip.
    @Test
    @Config(qualifiers = "en-w360dp-h4000dp")
    fun eachIconIsNamedByItsRowAndShowsATooltip() {
        open(1f)
        assertEquals(listOf("Remove ingredient row 2", "Ingredient row 2 actions"), labels(1))
        listOf(1, 3).flatMap(::actions).forEach { tag ->
            compose.onNodeWithTag(tag).performScrollTo().performTouchInput { longClick() }
            compose.waitUntil(PATIENCE_MS) { exists(EditorTags.tooltip(tag)) }
            val label = AccessibilityChecks.announcedLabel(compose.onNodeWithTag(tag).fetchSemanticsNode())
            fun texts(node: SemanticsNode): List<String> = node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } + node.children.flatMap(::texts)
            assertTrue("$tag shows \"$label\" on a long press", texts(node(EditorTags.tooltip(tag))).contains(label))
            compose.waitUntil(PATIENCE_MS) { !exists(EditorTags.tooltip(tag)) }
        }
        // A long press only shows the name: the row is still there, and no menu opened.
        assertTrue(exists(EditorTags.ingredient(3)))
        assertFalse(exists(EditorTags.ingredientAction(3, RowAction.INSERT_ABOVE)))
    }

    // The free-text warning's tooltip and an icon's tooltip never show at once: a long press on remove while the
    // warning's text is shown takes the warning's text away.
    @Test
    @Config(qualifiers = "en-w360dp-h4000dp")
    fun anIconsTooltipDismissesTheWarningsTooltip() {
        open(1f)
        compose.onNodeWithTag(EditorTags.ingredientWarning(0)).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.ingredientWarningText(0)) }
        val remove = EditorTags.ingredientRemove(0)
        compose.onNodeWithTag(remove).performTouchInput { longClick() }
        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.tooltip(remove)) }
        compose.waitUntil(PATIENCE_MS) { !exists(EditorTags.ingredientWarningText(0)) }
        assertTrue(exists(EditorTags.ingredient(0)))
    }

    @Test
    @Config(qualifiers = "ru-w360dp-h4000dp")
    fun eachIconIsNamedByItsRowInRussian() {
        open(2f)
        assertEquals(listOf("Убрать строку ингредиента 2", "Действия со строкой ингредиента 2"), labels(1))
        val tag = EditorTags.ingredientRemove(0)
        compose.onNodeWithTag(tag).performScrollTo().performTouchInput { longClick() }
        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.tooltip(tag)) }
    }

    // A frozen recipe's ingredient block shows no action icons, and nothing in it is to be pressed.
    @Test
    @Config(qualifiers = "en-w360dp-h4000dp")
    fun aFrozenRecipesRowsShowNoActionIcons() {
        open(1f, state = RecipeState.PUBLISHED)
        (0 until rows().size).forEach { position ->
            assertTrue(exists(EditorTags.ingredient(position)))
            actions(position).forEach { assertFalse("frozen row $position shows $it", exists(it)) }
            val inRow = hasAnyAncestor(hasTestTag(EditorTags.ingredient(position)))
            assertEquals(0, compose.onAllNodes(inRow and AccessibilityChecks.isInteractive, useUnmergedTree = true).fetchSemanticsNodes().size)
        }
    }

    companion object {
        /** Every test runs with the drag column on the left, as by default, and on the right, for a left hand. */
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "handle on the right: {0}")
        fun sides(): List<Array<Any>> = listOf(arrayOf(false), arrayOf(true))
    }
}
