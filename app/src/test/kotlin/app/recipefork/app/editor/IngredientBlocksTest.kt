package app.recipefork.app.editor

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.toSize
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.R
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.library.LibrarySession
import app.recipefork.app.library.LibraryTags
import app.recipefork.app.library.LibraryView
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.RecipeState
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.Rational
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import kotlin.math.abs

/**
 * Each ingredient row as one bounded block on a 360 dp phone, at font scale 1.0 and 2.0: every part of a row inside
 * its block, blocks apart from each other with an outline drawn round each, the handle in a gutter of its own and
 * the name above the quantity at the same left edge; the free-text warning as an icon beside the name whose text the
 * screen reader reads and a tap or a long press shows; a dragged row moving whole; a frozen recipe's rows as blocks.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IngredientBlocksTest {
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
        val environment = EditorEnvironment(active, device.catalog(context)) { created.plusSeconds(60) }
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
    // and the warning included — inside it; the handle in its own gutter, the name above the quantity at one edge.
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
            val handle = bounds(EditorTags.ingredientHandle(position))
            val name = bounds(EditorTags.ingredientName(position))
            val unit = bounds(EditorTags.ingredientUnit(position))
            val below = if (exists(EditorTags.ingredientQuantity(position))) bounds(EditorTags.ingredientQuantity(position)) else unit
            // The name and the field below it start at one edge; the handle sits in the gutter before both.
            assertEquals("row $position: the name and the field below it share their left edge", name.left, below.left, 0.5f)
            assertTrue("row $position: the handle stands before the name", handle.right <= name.left + 0.5f)
            assertTrue("row $position: the handle is on the name's line", handle.top < name.bottom && handle.bottom > name.top)
            assertTrue("row $position: the quantity and unit are below the name", below.top >= name.bottom - 0.5f)
            // The menu and the unit beside a quantity end at one right edge.
            if (below != unit) assertEquals("row $position: the menu and the unit end together", bounds(EditorTags.ingredientMenu(position)).right, unit.right, 0.5f)
        }
        // The divisibility question of the free-text piece stays inside its block, below its fields.
        assertTrue(exists(EditorTags.divisibility(0)))
        assertTrue(bounds(EditorTags.divisibility(0)).top >= bounds(EditorTags.ingredientUnit(0)).bottom - 0.5f)
        // Nothing of a row leaves the screen's width.
        val screen = bounds(EditorTags.FORM)
        (0 until count).forEach { assertTrue(bounds(EditorTags.ingredient(it)).right <= screen.right + 0.5f) }
        assertSeparateOutlinedBlocks(count)
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

    // FLOW-025: a dragged row moves as one block — every part of it travels with it, by the same distance.
    @Test
    @Config(qualifiers = "ru-w360dp-h4000dp")
    fun aDraggedRowMovesAsOneBlock() {
        open(1f)
        val tags = listOf(EditorTags.ingredient(0)) + parts(0)
        val before = tags.associateWith { bounds(it).top }
        val handle = EditorTags.ingredientHandle(0)
        val distance = bounds(EditorTags.ingredient(1)).bottom - bounds(EditorTags.ingredient(0)).bottom
        compose.onNodeWithTag(handle, useUnmergedTree = true).performTouchInput {
            down(center)
            repeat(10) { moveBy(Offset(0f, distance / 20)) }
        }
        compose.waitForIdle()
        val moved = bounds(EditorTags.ingredient(0)).top - before.getValue(EditorTags.ingredient(0))
        assertTrue("the row follows the finger ($moved px)", moved > px(8f))
        tags.forEach { tag -> assertEquals("$tag moves with its row", moved, bounds(tag).top - before.getValue(tag), 1f) }
        compose.onNodeWithTag(handle, useUnmergedTree = true).performTouchInput {
            repeat(10) { moveBy(Offset(0f, distance / 20)) }
            up()
        }
        compose.waitForIdle()
        // Dropped past the second row's middle, it lands second, as the menu's "Move down" would put it.
        compose.waitUntil(PATIENCE_MS) { fieldText(EditorTags.ingredientName(1)) == "черемша" }
        assertEquals("курица", fieldText(EditorTags.ingredientName(0)))
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
        assertFalse(exists(EditorTags.ingredientHandle(0)))
        assertSeparateOutlinedBlocks(rows().size)
    }
}
