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
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.toSize
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.data.HeldDragHandleSide
import app.recipefork.app.library.LibrarySession
import app.recipefork.app.media.PhotoStore
import app.recipefork.app.media.TestPhotos
import app.recipefork.app.library.LibraryTags
import app.recipefork.app.library.LibraryView
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.IngredientReference
import app.recipefork.core.recipe.Media
import app.recipefork.core.recipe.MediaAttachment
import app.recipefork.core.recipe.MediaKind
import app.recipefork.core.recipe.MediaVisibility
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.RecipeState
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.Timer
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.Rational
import java.time.Instant
import kotlin.math.abs
import kotlin.time.Duration.Companion.minutes
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
 * Each step as one bounded block, as an ingredient is, on a 360 dp phone at font scale 1.0 and 2.0: every part of a
 * step's row — its text, its timer's line, its photograph's picture (T-211: the step reads as text) — inside its block,
 * blocks apart from each other with an outline drawn round each, a tinted column down the left edge of each block —
 * or the right edge, with the handle on the right — with the handle icon at its centre, the inputs beside it at one left edge; a step dragged from anywhere in its column
 * moving; a frozen recipe's steps as blocks, with no column.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StepBlocksTest(private val handleRight: Boolean) {
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

    /** Three steps: one referring to an ingredient, one with a timer, one long and plain. */
    private fun steps(): List<Step> = listOf(
        Step(0, "Mix  with the water.", listOf(IngredientReference(0, 4))),
        Step(1, "Let it rest under a towel.", timer = Timer(30.minutes)),
        Step(2, "Shape the loaf, put it on the tray, score the top and bake it until it sounds hollow when tapped."),
    )

    /** The last step carries a photograph, a landscape one, kept where the editor keeps its own. */
    private fun stepPhoto(): Media {
        val mediaId = "step-photo"
        TestPhotos.jpeg(PhotoStore(context.filesDir).file(BookName.Pool, mediaId).apply { parentFile!!.mkdirs() }, 1600, 1200)
        return Media(mediaId, null, MediaKind.PHOTO, MediaAttachment.ToStep(PHOTO_STEP), MediaVisibility.PRIVATE)
    }

    private val PHOTO_STEP = 2

    /** Opens the editor at [fontScale] directly on a seeded recipe holding [steps()], frozen if [state] says so. */
    private fun open(fontScale: Float, state: RecipeState? = null): RecipeId {
        var recipe = Recipe.create(created, null).copy(
            title = "Bread",
            ingredients = listOf(Ingredient(0, null, null, "flour", UnitType.EXACT, QuantityUnit.GRAM, Rational.of(500))),
            steps = steps(),
            media = listOf(stepPhoto()),
        )
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
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.step(steps().lastIndex)), PATIENCE_MS)
        compose.waitUntil(PATIENCE_MS) { exists(StepSheetTags.thumbnail(PHOTO_STEP)) }
        return recipe.id
    }

    private fun node(tag: String): SemanticsNode = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()

    private fun exists(tag: String): Boolean = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun bounds(tag: String): Rect = node(tag).let { Rect(it.positionInRoot, it.size.toSize()) }

    private fun px(dp: Float): Float = dp * compose.density.density

    private fun stored(id: RecipeId): Recipe = runBlocking { recipes.get(id) }!!

    /** Every part of the editable step at [position] that is on the screen. */
    private fun parts(position: Int): List<String> = listOf(
        EditorTags.stepHandle(position),
        EditorTags.stepHandleIcon(position),
        StepSheetTags.line(position),
        StepSheetTags.text(position),
        StepSheetTags.timer(position, 0),
        StepSheetTags.thumbnail(position),
    ).filter(::exists)

    /** What starts a line of the row of the step at [position]: the row itself, and its timer's line. */
    private fun lineStarts(position: Int): List<String> = listOf(
        StepSheetTags.line(position),
        StepSheetTags.timer(position, 0),
    ).filter(::exists)

    /**
     * The blocks of [count] steps: each apart from the next, with a background gap between them, and each with an
     * outline drawn at its top, bottom and sides — something other than the background just inside its edges.
     */
    private fun assertSeparateOutlinedBlocks(count: Int) {
        val list = bounds(EditorTags.STEPS)
        val image = compose.onNodeWithTag(EditorTags.STEPS).captureToImage().toPixelMap()
        fun at(x: Float, y: Float): Color = image[(x - list.left).toInt().coerceIn(0, image.width - 1), (y - list.top).toInt().coerceIn(0, image.height - 1)]
        fun differs(a: Color, b: Color) = abs(a.red - b.red) + abs(a.green - b.green) + abs(a.blue - b.blue) > 0.05f
        val blocks = (0 until count).map { bounds(EditorTags.step(it)) }
        blocks.zipWithNext().forEachIndexed { i, (upper, lower) ->
            assertTrue("step $i ends ${lower.top - upper.bottom} px before step ${i + 1} begins", lower.top - upper.bottom >= px(4f) - 0.5f)
        }
        val background = at(blocks[0].center.x, (blocks[0].bottom + blocks[1].top) / 2)
        blocks.forEachIndexed { i, block ->
            fun outlined(side: String, points: List<Offset>) =
                assertTrue("step $i has its outline at its $side", points.any { differs(at(it.x, it.y), background) })
            outlined("top", (0..2).map { Offset(block.center.x, block.top + it) })
            outlined("bottom", (1..3).map { Offset(block.center.x, block.bottom - it) })
            outlined("left", (0..2).map { Offset(block.left + it, block.center.y) })
            outlined("right", (1..3).map { Offset(block.right - it, block.center.y) })
            if (i < count - 1) assertFalse("step $i runs into step ${i + 1}", differs(at(block.center.x, block.bottom + px(3f)), background))
        }
    }

    // Each editable step is one bounded block, separated from the next; its text, the insert-ingredient control, its
    // timer, its removal and its menu inside it; the drag column down its left edge, the full height of the block, and
    // what the step holds beside it at one left edge.
    @Test
    @Config(qualifiers = "ru-w360dp-h4000dp")
    fun everyStepIsOneBlock() = everyStepIsOneBlock(1f)

    @Test
    @Config(qualifiers = "ru-w360dp-h4000dp")
    fun everyStepIsOneBlockAtDoubleFontScale() = everyStepIsOneBlock(2f)

    @Test
    @Config(qualifiers = "en-w360dp-h4000dp")
    fun everyStepIsOneBlockInEnglishAtDoubleFontScale() = everyStepIsOneBlock(2f)

    private fun everyStepIsOneBlock(fontScale: Float) {
        open(fontScale)
        val screen = bounds(EditorTags.FORM)
        steps().indices.forEach { position ->
            val block = bounds(EditorTags.step(position))
            listOf(StepSheetTags.line(position), StepSheetTags.text(position)).forEach { assertTrue("step $position shows $it", exists(it)) }
            // Nothing of the step is edited on its row.
            listOf(EditorTags.stepText(position), EditorTags.stepInsert(position), EditorTags.stepRemove(position), EditorTags.timerAttach(position), EditorTags.stepPhoto(position))
                .forEach { assertFalse("step $position shows $it", exists(it)) }
            parts(position).forEach { part ->
                val b = bounds(part)
                assertTrue(
                    "$part ($b) lies inside its step ($block)",
                    b.left >= block.left - 0.5f && b.right <= block.right + 0.5f && b.top >= block.top - 0.5f && b.bottom <= block.bottom + 0.5f,
                )
            }
            val column = bounds(EditorTags.stepHandle(position))
            val icon = bounds(EditorTags.stepHandleIcon(position))
            if (handleRight) {
                assertEquals("step $position: the column ends at the block's right edge", block.right, column.right, 0.5f)
            } else {
                assertEquals("step $position: the column starts at the block's left edge", block.left, column.left, 0.5f)
            }
            assertEquals("step $position: the column starts at the block's top", block.top, column.top, 0.5f)
            assertEquals("step $position: the column ends at the block's bottom", block.bottom, column.bottom, 0.5f)
            assertEquals("step $position: the column is ${column.width / compose.density.density} dp wide", px(48f), column.width, 0.5f)
            assertEquals("step $position: the icon is centred across the column", column.center.x, icon.center.x, 1f)
            assertEquals("step $position: the icon is centred down the column", column.center.y, icon.center.y, 1f)
            // One left edge, beside the column: nothing that drags stands among what the row reads. The timer's line
            // starts under the step's number, its clock (18 dp and a gap of 8) before its text; the row's own padding is 12 dp.
            val starts = lineStarts(position).map { if (it == StepSheetTags.line(position)) bounds(it).left + px(12f) else bounds(it).left - px(26f) }
            starts.forEach { assertEquals("step $position: its lines start at one left edge", starts.first(), it, 0.5f) }
            val inputs = parts(position) - setOf(EditorTags.stepHandle(position), EditorTags.stepHandleIcon(position))
            inputs.forEach {
                val b = bounds(it)
                if (handleRight) assertTrue("step $position: $it is left of the column", b.right <= column.left + 0.5f)
                else assertTrue("step $position: $it is right of the column", b.left >= column.right - 0.5f)
            }
            if (handleRight) assertTrue("step $position: its lines start by the block's left edge", starts.first() - block.left <= px(16f))
            assertTrue(block.right <= screen.right + 0.5f)
        }
        // The timer's line is there for the step that has one, and the photograph's picture for the step that carries one.
        assertTrue(exists(StepSheetTags.timer(1, 0)))
        assertTrue(exists(StepSheetTags.thumbnail(PHOTO_STEP)))
        assertSeparateOutlinedBlocks(steps().size)
    }

    // The column is tinted, so the area to drag is seen, one tint from top to bottom, and its icon marks it.
    @Test
    @Config(qualifiers = "ru-w360dp-h4000dp")
    fun theColumnIsTintedAndMarkedByItsIcon() {
        open(1f)
        val list = bounds(EditorTags.STEPS)
        val image = compose.onNodeWithTag(EditorTags.STEPS).captureToImage().toPixelMap()
        fun at(x: Float, y: Float): Color = image[(x - list.left).toInt().coerceIn(0, image.width - 1), (y - list.top).toInt().coerceIn(0, image.height - 1)]
        fun differs(a: Color, b: Color) = abs(a.red - b.red) + abs(a.green - b.green) + abs(a.blue - b.blue) > 0.02f
        steps().indices.forEach { position ->
            val column = bounds(EditorTags.stepHandle(position))
            val line = bounds(StepSheetTags.line(position))
            // The block's own colour: inside the row's padding, before its number.
            val body = at(line.left + px(4f), line.top + px(4f))
            val tint = at(column.center.x, column.top + px(12f))
            assertTrue("step $position: the column is tinted ($tint against $body)", differs(tint, body))
            assertFalse("step $position: the column is one tint to its bottom", differs(tint, at(column.center.x, column.bottom - px(12f))))
            assertTrue(exists(EditorTags.stepHandleIcon(position)))
        }
    }

    // FLOW-025: a step dragged from the top or the bottom of its column lands where it is dropped.
    @Test
    @Config(qualifiers = "ru-w360dp-h4000dp")
    fun aStepDraggedFromAnywhereInItsColumnMoves() {
        val id = open(1f)
        val texts = steps().map { it.text }
        listOf(0.03f, 0.97f).forEach { across ->
            val before = stored(id).steps.map { it.text }
            val distance = bounds(EditorTags.step(1)).bottom - bounds(EditorTags.step(0)).bottom
            compose.onNodeWithTag(EditorTags.stepHandle(0), useUnmergedTree = true).performTouchInput {
                down(Offset(centerX, height * across))
                repeat(20) { moveBy(Offset(0f, distance / 20)) }
                up()
            }
            compose.waitUntil(PATIENCE_MS) { stored(id).steps.map { it.text } == listOf(before[1], before[0], before[2]) }
        }
        assertEquals(texts, stored(id).steps.map { it.text })
    }

    // A frozen recipe's steps are blocks too, with no column.
    @Test
    @Config(qualifiers = "ru-w360dp-h4000dp")
    fun aFrozenRecipesStepsAreBlocks() = frozenStepsAreBlocks(1f)

    @Test
    @Config(qualifiers = "ru-w360dp-h4000dp")
    fun aFrozenRecipesStepsAreBlocksAtDoubleFontScale() = frozenStepsAreBlocks(2f)

    private fun frozenStepsAreBlocks(fontScale: Float) {
        open(fontScale, state = RecipeState.PUBLISHED)
        steps().indices.forEach { position ->
            assertFalse(exists(EditorTags.stepHandle(position)))
            assertFalse(exists(EditorTags.stepHandleIcon(position)))
            val block = bounds(EditorTags.step(position))
            val text = bounds(StepSheetTags.text(position))
            assertTrue("step $position: its text is inside its block", text.left >= block.left && text.right <= block.right && text.top >= block.top && text.bottom <= block.bottom)
            assertFalse(exists(EditorTags.stepPhoto(position)))
        }
        // The photograph's picture shows, read-only, inside its step's block.
        val photo = bounds(StepSheetTags.thumbnail(PHOTO_STEP))
        val block = bounds(EditorTags.step(PHOTO_STEP))
        assertTrue(photo.left >= block.left - 0.5f && photo.right <= block.right + 0.5f && photo.top >= block.top - 0.5f && photo.bottom <= block.bottom + 0.5f)
        assertSeparateOutlinedBlocks(steps().size)
    }

    companion object {
        /** Every test runs with the drag column on the left, as by default, and on the right, for a left hand. */
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "handle on the right: {0}")
        fun sides(): List<Array<Any>> = listOf(arrayOf(false), arrayOf(true))
    }
}
