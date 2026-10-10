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
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
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
 * block — or the right edge, with the handle on the right — with the handle icon at its centre, the row's line beside it
 * with room to be read and its marks under it; in the row's sheet, the inputs at one left edge and of one height and the
 * free-text warning as a hint under the name; a row dragged from anywhere in its column moving whole; a frozen recipe's
 * rows as blocks, with no column.
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
        // The dictionary is read: the entry's row reads its label.
        compose.waitUntil(PATIENCE_MS) { listOf("курица", "chicken").any { it in compose.ingredientLine(1) } }
        return recipe.id
    }

    private fun node(tag: String): SemanticsNode = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()

    private fun exists(tag: String): Boolean = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun bounds(tag: String): Rect = node(tag).let { Rect(it.positionInRoot, it.size.toSize()) }

    private fun fieldText(tag: String): String = compose.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.EditableText].text

    private fun px(dp: Float): Float = dp * compose.density.density

    private fun string(id: Int): String = context.getString(id)

    /** How much the free-text hint under the name of the open row [position] adds to its field: the hint and the 4 dp above it. */
    private fun hintHeight(position: Int): Float =
        if (exists(EditorTags.ingredientWarning(position))) bounds(EditorTags.ingredientWarning(position)).height + px(4f) else 0f

    /** Every part of the editable row at [position] that is on the screen. */
    private fun parts(position: Int): List<String> = listOf(
        EditorTags.ingredientHandle(position),
        EditorTags.ingredientHandleIcon(position),
        IngredientSheetTags.line(position),
        IngredientSheetTags.freeText(position),
        IngredientSheetTags.problem(position),
    ).filter(::exists)

    /**
     * The blocks of [count] rows: each apart from the next, with a background gap between them, and each with an
     * outline drawn at its top, bottom and sides — something other than the background just inside its edges.
     */
    private fun assertSeparateOutlinedBlocks(count: Int) {
        // The list in sight whole, the parts above it as tall as they are at the font scale.
        compose.onNodeWithTag(EditorTags.INGREDIENTS).performScrollTo()
        compose.waitForIdle()
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

    // Each editable row is one bounded block, separated from the next; every part of it — its line and its marks —
    // inside it; the drag column down its left edge, and the line beside it.
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
            val line = bounds(IngredientSheetTags.line(position))
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
            // Nothing that drags stands among the rest: every other part of the row is beside the column.
            (parts(position) - setOf(EditorTags.ingredientHandle(position), EditorTags.ingredientHandleIcon(position))).forEach {
                val b = bounds(it)
                if (handleRight) assertTrue("row $position: $it is left of the column", b.right <= column.left + 0.5f)
                else assertTrue("row $position: $it is right of the column", b.left >= column.right - 0.5f)
            }
            if (handleRight) assertTrue("row $position: the line starts by the block's left edge", line.left - block.left <= px(16f))
            // The line is the target: at least 48 dp tall.
            assertTrue("row $position: the line is ${line.height / compose.density.density} dp tall", line.height >= px(48f) - 0.5f)
            // Its marks stand under its text, inside it.
            listOf(IngredientSheetTags.freeText(position), IngredientSheetTags.problem(position)).filter(::exists).forEach {
                val b = bounds(it)
                assertTrue("row $position: $it is inside the line", b.left >= line.left - 0.5f && b.bottom <= line.bottom + 0.5f)
            }
        }
        // The free-text names are marked, the dictionary's entry is not.
        assertEquals(listOf(true, false, true, true), (0 until count).map { exists(IngredientSheetTags.freeText(it)) })
        // Nothing of a row leaves the screen's width.
        val screen = bounds(EditorTags.FORM)
        (0 until count).forEach { assertTrue(bounds(EditorTags.ingredient(it)).right <= screen.right + 0.5f) }
        assertSeparateOutlinedBlocks(count)
        // The divisibility question of the free-text piece stands in its sheet, below its fields.
        compose.openIngredient(0)
        assertTrue(exists(EditorTags.divisibility(0)))
        assertTrue(bounds(EditorTags.divisibility(0)).top >= bounds(EditorTags.ingredientUnit(0)).bottom - 0.5f)
    }

    /** The sheet of row [position] is laid out at [fontScale] — its own window takes the scale from the configuration. */
    private fun assertSheetAt(fontScale: Float, position: Int) =
        assertEquals("the sheet's font scale", fontScale, layoutOf(EditorTags.ingredientName(position)).layoutInput.density.fontScale, 0.01f)

    // In the sheet, the name field, the quantity field and the unit select are of one height, and the quantity and the
    // unit stand level on their line — at font scale 1.0 and 2.0.
    @Test
    @Config(qualifiers = "ru-w360dp-h4000dp")
    fun theInputsOfARowAreOfOneHeight() = inputsAreOfOneHeight(1f)

    @Test
    @Config(qualifiers = "ru-w360dp-h4000dp", fontScale = 2f)
    fun theInputsOfARowAreOfOneHeightAtDoubleFontScale() = inputsAreOfOneHeight(2f)

    @Test
    @Config(qualifiers = "en-w360dp-h4000dp", fontScale = 2f)
    fun theInputsOfARowAreOfOneHeightInEnglishAtDoubleFontScale() = inputsAreOfOneHeight(2f)

    private fun inputsAreOfOneHeight(fontScale: Float) {
        open(fontScale)
        (0 until rows().size).forEach { position ->
            compose.openIngredient(position)
            assertSheetAt(fontScale, position)
            // The name's field without the free-text hint under it.
            val name = bounds(EditorTags.ingredientName(position)).let { it.copy(bottom = it.bottom - hintHeight(position)) }
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

    // In the sheet, the quantity field takes the line from the name's left edge up to the unit.
    @Test
    @Config(qualifiers = "ru-w360dp-h4000dp")
    fun theQuantityTakesTheWholeLineBeforeTheUnit() {
        open(1f)
        compose.openIngredient(0)
        val quantity = bounds(EditorTags.ingredientQuantity(0))
        val unit = bounds(EditorTags.ingredientUnit(0))
        val name = bounds(EditorTags.ingredientName(0))
        assertEquals(name.left, quantity.left, 0.5f)
        assertEquals("only the 8 dp gap between the quantity and the unit", px(8f), unit.left - quantity.right, 0.5f)
        assertTrue("the quantity is ${quantity.width / compose.density.density} dp wide", quantity.width >= px(120f))
        assertEquals("the unit ends with the name", name.right, unit.right, 0.5f)
    }

    // A11Y-004: at font scale 2.0 the quantity's label is not wrapped onto the name above it — where it cannot have one
    // line beside the unit, the quantity takes the whole line and the unit the line below, both from one left edge.
    @Test
    @Config(qualifiers = "ru-w360dp-h4000dp", fontScale = 2f)
    fun theQuantityLabelHasOneLineAtDoubleFontScale() = quantityLabelHasOneLine()

    @Test
    @Config(qualifiers = "en-w360dp-h4000dp", fontScale = 2f)
    fun theQuantityLabelHasOneLineInEnglishAtDoubleFontScale() = quantityLabelHasOneLine()

    private fun quantityLabelHasOneLine() {
        open(2f)
        val label = widthOf(string(R.string.editor_ingredient_quantity), 2f, Typography().bodySmall)
        listOf(0, 1, 3).forEach { position ->
            compose.openIngredient(position)
            assertSheetAt(2f, position)
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
            val line = bounds(IngredientSheetTags.line(position))
            // The block's own colour: in the padding under the line's text, inside the block.
            val body = at(line.center.x, line.bottom - px(2f))
            val tint = at(column.center.x, column.top + px(12f))
            assertTrue("row $position: the column is tinted ($tint against $body)", differs(tint, body))
            assertFalse("row $position: the column is one tint to its bottom", differs(tint, at(column.center.x, column.bottom - px(12f))))
            assertTrue("row $position: the column carries its icon", exists(EditorTags.ingredientHandleIcon(position)))
        }
    }

    // FLOW-022 (2): on the row, a name in the author's own words is marked in muted words under the line, never as an
    // error; in the sheet, the warning is a hint in the name field's own supporting text — under the name, inside its
    // field — never an error and never an icon beside it; the screen reader reads it with the field.
    @Test
    @Config(qualifiers = "ru-w360dp-h4000dp")
    fun theFreeTextWarningIsAHintUnderTheNameInRussian() = theFreeTextWarningIsAHint()

    @Test
    @Config(qualifiers = "en-w360dp-h4000dp")
    fun theFreeTextWarningIsAHintUnderTheName() = theFreeTextWarningIsAHint()

    private fun theFreeTextWarningIsAHint() {
        open(1f)
        val text = string(R.string.editor_ingredient_free_text_warning)
        listOf(0, 2, 3).forEach { position ->
            assertEquals(listOf(string(R.string.editor_ingredient_free_text_mark)), compose.textsOf(IngredientSheetTags.freeText(position)))
            assertEquals(Typography().bodyMedium.fontSize, layoutOf(IngredientSheetTags.freeText(position)).layoutInput.style.fontSize)
            assertEquals(lightColorScheme().onSurfaceVariant, layoutOf(IngredientSheetTags.freeText(position)).layoutInput.style.color)
        }
        // Free text warns; the dictionary entry does not.
        assertEquals(listOf(true, false, true, true), (0..3).map { compose.openIngredient(it); exists(EditorTags.ingredientWarning(it)) })
        listOf(0, 2, 3).forEach { position ->
            compose.openIngredient(position)
            val field = node(EditorTags.ingredientName(position))
            val name = bounds(EditorTags.ingredientName(position))
            val b = bounds(EditorTags.ingredientWarning(position))
            assertTrue("inside the name's field, under its text ($b in $name)", b.left >= name.left && b.right <= name.right + 0.5f && b.bottom <= name.bottom + 0.5f && b.top > name.top + px(40f))
            assertEquals(listOf(text), compose.textsOf(EditorTags.ingredientWarning(position)))
            // A hint, not an error: the field is not marked as one.
            assertFalse("row $position is marked as an error", field.config.contains(SemanticsProperties.Error))
            val read = compose.onNodeWithTag(EditorTags.ingredientName(position)).fetchSemanticsNode().config
            assertTrue("row $position: the screen reader does not read the hint with the name", text in read.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text })
        }
    }

    private fun layoutOf(tag: String): TextLayoutResult =
        mutableListOf<TextLayoutResult>().also { node(tag).config[SemanticsActions.GetTextLayoutResult].action!!(it) }.single()

    /** The width the name field of the open row [position] leaves for its text: 16 dp of padding at both ends. */
    private fun nameTextWidth(position: Int): Float =
        bounds(EditorTags.ingredientName(position)).width - px(32f)

    /** How wide [text] is laid out in the name field's style at [fontScale]. */
    private fun widthOf(text: String, fontScale: Float, style: TextStyle = Typography().bodyLarge): Float {
        val measurer = TextMeasurer(createFontFamilyResolver(context), Density(compose.density.density, fontScale), LayoutDirection.Ltr)
        return measurer.measure(text, style).size.width.toFloat()
    }

    /** The width the line of row [position] leaves for its text: 12 dp before it and after it. */
    private fun lineTextWidth(position: Int): Float = bounds(IngredientSheetTags.line(position)).width - px(24f)

    // Criterion 1 at large text: on a 360 dp phone at font scale 2.0 a row's line keeps room for about eight Cyrillic
    // letters, so "черемша" reads whole; its sheet's name field more than that.
    @Test
    @Config(qualifiers = "ru-w360dp-h4000dp", fontScale = 2f)
    fun aFreeTextNameKeepsRoomForItsTextAtDoubleFontScale() {
        open(2f)
        val eight = widthOf("морковка", 2f)
        val line = lineTextWidth(0)
        assertTrue("the line leaves ${line / compose.density.density} dp for its text, eight letters take ${eight / compose.density.density} dp", line >= eight)
        compose.openIngredient(0)
        assertSheetAt(2f, 0)
        val room = nameTextWidth(0)
        assertTrue("the name leaves ${room / compose.density.density} dp for its text, eight letters take ${eight / compose.density.density} dp", room >= eight)
    }

    // On a 320 dp phone at font scale 2.0 the text of a free-text name still has room of its own, on the row and in the
    // sheet.
    @Test
    @Config(qualifiers = "ru-w320dp-h4000dp", fontScale = 2f)
    fun aFreeTextNameKeepsUsableRoomOnANarrowPhoneAtDoubleFontScale() {
        open(2f)
        val four = widthOf("черм", 2f)
        assertTrue(lineTextWidth(0) >= four)
        compose.openIngredient(0)
        assertSheetAt(2f, 0)
        val room = nameTextWidth(0)
        assertTrue("the name leaves ${room / compose.density.density} dp for its text, four letters take ${four / compose.density.density} dp", room >= four)
    }

    private fun isFocused(tag: String): Boolean = node(tag).config.getOrNull(SemanticsProperties.Focused) == true

    // A row with no name opens with its name focused; a free-text name typed letter by letter keeps its focus and its
    // text, and the warning does not spring up under it while it is typed: it comes once the field is left.
    @Test
    @Config(qualifiers = "ru-w360dp-h4000dp")
    fun theFreeTextWarningComesOnceTheNameIsLeft() {
        open(1f, ingredients = rows() + Ingredient(4, null, null, null, UnitType.EXACT, QuantityUnit.GRAM, null))
        val name = EditorTags.ingredientName(4)
        compose.openIngredient(4)
        compose.waitUntil(PATIENCE_MS) { isFocused(name) }
        assertFalse(exists(EditorTags.ingredientWarning(4)))
        var typed = ""
        "щавель".forEach { letter ->
            compose.onNodeWithTag(name).performTextInput(letter.toString())
            typed += letter
            compose.waitForIdle()
            assertEquals(typed, fieldText(name))
            assertTrue("the field keeps the focus after \"$typed\"", isFocused(name))
            assertFalse("the warning shows while \"$typed\" is typed", exists(EditorTags.ingredientWarning(4)))
        }
        // Left, the field says it as a hint.
        compose.onNodeWithTag(name).performImeAction()
        compose.waitUntil(PATIENCE_MS) { !isFocused(name) }
        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.ingredientWarning(4)) }
        assertEquals("щавель", fieldText(name))
        assertFalse(node(name).config.contains(SemanticsProperties.Error))
        // Back in the field, it goes again while the author types.
        compose.onNodeWithTag(name).performClick()
        compose.waitUntil(PATIENCE_MS) { isFocused(name) }
        compose.waitUntil(PATIENCE_MS) { !exists(EditorTags.ingredientWarning(4)) }
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
            compose.waitUntil(PATIENCE_MS) { compose.ingredientLine(1).endsWith(second) }
            assertTrue("dragged from $where", compose.ingredientLine(0).endsWith(first))
            // A drag opens no sheet.
            assertFalse(compose.ingredientSheetOpen())
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

    private fun dp(px: Float) = px / compose.density.density

    // The block reads as its line: it is no taller than its line — the line's text and marks — and at 360 dp and font
    // scale 1.0 a row with no mark is one line of 48 dp, a third of the 144 dp a row of fields took.
    @Test
    @Config(qualifiers = "ru-w360dp-h4000dp")
    fun theBlockIsAShortLine() = blockIsAShortLine()

    @Test
    @Config(qualifiers = "en-w360dp-h4000dp")
    fun theBlockIsAShortLineInEnglish() = blockIsAShortLine()

    private fun blockIsAShortLine() {
        open(1f)
        (0 until rows().size).forEach { position ->
            val block = bounds(EditorTags.ingredient(position))
            val line = bounds(IngredientSheetTags.line(position))
            // The block is its line, within its outline.
            assertEquals("row $position: the block is as tall as its line", line.height, block.height, px(2f))
        }
        assertTrue("an unmarked row is ${dp(bounds(EditorTags.ingredient(1)).height)} dp tall", dp(bounds(EditorTags.ingredient(1)).height) <= 50.5f)
    }

    // The row holds its line and its drag column, and no action of its own — no icon, no button: its actions are in its
    // sheet. The line takes the whole block beside the column, and keeps room for a word at any font scale.
    @Test
    @Config(qualifiers = "ru-w360dp-h4000dp")
    fun theRowHoldsOnlyItsLine() = rowHoldsOnlyItsLine(1f)

    @Test
    @Config(qualifiers = "ru-w360dp-h4000dp")
    fun theRowHoldsOnlyItsLineAtDoubleFontScale() = rowHoldsOnlyItsLine(2f)

    @Test
    @Config(qualifiers = "en-w360dp-h4000dp")
    fun theRowHoldsOnlyItsLineInEnglishAtDoubleFontScale() = rowHoldsOnlyItsLine(2f)

    private fun rowHoldsOnlyItsLine(fontScale: Float) {
        open(fontScale)
        (0 until rows().size).forEach { position ->
            val block = bounds(EditorTags.ingredient(position))
            val line = bounds(IngredientSheetTags.line(position))
            assertFalse(exists(EditorTags.ingredientRemove(position)))
            val inRow = hasAnyAncestor(hasTestTag(EditorTags.ingredient(position)))
            assertEquals(
                "row $position: the line is its one target",
                listOf(IngredientSheetTags.line(position)),
                compose.onAllNodes(inRow and AccessibilityChecks.isInteractive, useUnmergedTree = true).fetchSemanticsNodes()
                    .map { it.config[SemanticsProperties.TestTag] },
            )
            assertEquals("row $position: the line takes the block beside its column", block.width - px(48f), line.width, 1f)
            assertTrue("row $position: the line keeps room for a word", lineTextWidth(position) >= widthOf("00000000", fontScale) - 0.5f)
        }
        AccessibilityChecks.assertTouchTargets(compose)
        AccessibilityChecks.assertInteractiveElementsLabelled(compose)
    }

    // The row's actions stand in its sheet, each a whole-width item at least 56 dp tall with its words whole, at font
    // scale 2.0 on a 360 dp phone — the sheet's own window taking the scale from the configuration.
    @Test
    @Config(qualifiers = "ru-w360dp-h4000dp", fontScale = 2f)
    fun theSheetsActionsAreWholeAtDoubleFontScale() = sheetsActionsAreWhole()

    @Test
    @Config(qualifiers = "en-w360dp-h4000dp", fontScale = 2f)
    fun theSheetsActionsAreWholeInEnglishAtDoubleFontScale() = sheetsActionsAreWhole()

    private fun sheetsActionsAreWhole() {
        open(2f)
        compose.openIngredient(1)
        val tags = listOf(RowAction.MOVE_UP, RowAction.MOVE_DOWN, RowAction.INSERT_ABOVE, RowAction.INSERT_BELOW, RowAction.GROUP_ABOVE)
            .map { EditorTags.ingredientAction(1, it) } + EditorTags.ingredientRemove(1)
        val sheet = bounds(IngredientSheetTags.SHEET)
        tags.forEach { tag ->
            compose.onNodeWithTag(tag).performScrollTo()
            val b = bounds(tag)
            assertTrue("$tag is ${dp(b.height)} dp tall", b.height >= px(56f) - 0.5f)
            assertEquals("$tag spans the sheet", sheet.width, b.width, 1f)
            val text = compose.onAllNodes(hasAnyAncestor(hasTestTag(tag)) and hasText("", substring = true), useUnmergedTree = true)
                .fetchSemanticsNodes().first()
            val layout = mutableListOf<TextLayoutResult>().also { text.config[SemanticsActions.GetTextLayoutResult].action!!(it) }.single()
            // Laid out at twice the size, and nothing of it cut: no line lost, every line within the item.
            assertTrue("$tag at ${layout.layoutInput.density.fontScale}", layout.layoutInput.density.fontScale >= 2f)
            assertFalse("$tag is cut", layout.didOverflowHeight || layout.multiParagraph.didExceedMaxLines)
            val t = Rect(text.positionInRoot, text.size.toSize())
            assertTrue("$tag: its text lies inside it", t.left >= b.left - 0.5f && t.right <= b.right + 0.5f && t.top >= b.top - 0.5f && t.bottom <= b.bottom + 0.5f)
            assertTrue("$tag: its text is as tall as its lines", text.size.height >= layout.multiParagraph.height - 0.5f)
        }
    }

    // A frozen recipe's ingredient block shows no action icons, and nothing in it is to be pressed.
    @Test
    @Config(qualifiers = "en-w360dp-h4000dp")
    fun aFrozenRecipesRowsShowNoActionIcons() {
        open(1f, state = RecipeState.PUBLISHED)
        (0 until rows().size).forEach { position ->
            assertTrue(exists(EditorTags.ingredient(position)))
            assertFalse("frozen row $position shows a removal", exists(EditorTags.ingredientRemove(position)))
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
