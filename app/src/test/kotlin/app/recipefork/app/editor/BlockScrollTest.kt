package app.recipefork.app.editor

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.toSize
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.data.HeldDragHandleSide
import app.recipefork.app.library.LibrarySession
import app.recipefork.app.library.LibraryTags
import app.recipefork.app.library.LibraryView
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeId
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

/**
 * FLOW-025: only the drag column drags. A finger that starts on a block's inputs, on their labels or on a blank part
 * of the block and moves up or down scrolls the editor — the block is never picked up, and nothing is reordered.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(qualifiers = "ru-w360dp-h640dp")
class BlockScrollTest(private val handleRight: Boolean) {
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

    private fun recipe(): Recipe = Recipe.create(created, null).copy(
        title = "Bread",
        servings = 2,
        ingredients = listOf(
            Ingredient(0, null, null, "flour", UnitType.EXACT, QuantityUnit.GRAM, Rational.of(500)),
            Ingredient(1, null, null, "water", UnitType.EXACT, QuantityUnit.MILLILITRE, Rational.of(300)),
            Ingredient(2, null, null, "salt", UnitType.EXACT, QuantityUnit.GRAM, Rational.of(10)),
            Ingredient(3, null, null, "yeast", UnitType.EXACT, QuantityUnit.GRAM, Rational.of(7)),
        ),
        steps = (0 until 8).map { Step(it, "Step $it of the bread.", timer = if (it == 2) Timer(30.minutes) else null) },
    )

    private fun open(): RecipeId {
        val recipe = recipe()
        runBlocking { recipes.save(recipe) }
        val environment = EditorEnvironment(active, device.catalog(context), dragHandleSide = HeldDragHandleSide(handleRight)) { created.plusSeconds(60) }
        compose.setContent { MaterialTheme { LibraryOrEditor(session, recipes, environment, opened) } }
        compose.onNodeWithTag(LibraryTags.tab(LibraryView.OWN)).performClick()
        compose.waitForIdle()
        compose.runOnIdle { opened.value = recipe.id }
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.step(7)), PATIENCE_MS)
        return recipe.id
    }

    private fun node(tag: String): SemanticsNode = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()

    private fun bounds(tag: String): Rect = node(tag).let { Rect(it.positionInRoot, it.size.toSize()) }

    private fun px(dp: Float): Float = dp * compose.density.density

    private fun scrolled(): Float = node(EditorTags.FORM).config[SemanticsProperties.VerticalScrollAxisRange].value()

    private fun stored(id: RecipeId): Recipe = runBlocking { recipes.get(id) }!!

    /**
     * Brings [tag] onto the screen, puts a finger down at the point [at] gives within its bounds, and moves it 160 dp
     * up or down, whichever the screen has room for: the editor scrolls with it, and the block [block] keeps its place
     * among its neighbours [neighbour] while the finger moves and after it lifts.
     */
    private fun assertScrollsFrom(where: String, tag: String, block: String, neighbour: String, at: (Rect) -> Offset) {
        compose.onNodeWithTag(tag, useUnmergedTree = true).performScrollTo()
        compose.waitForIdle()
        val form = bounds(EditorTags.FORM)
        val start = at(bounds(tag))
        assertTrue("the point $where ($start) lies in the scrolling form ($form)", form.contains(start))
        val distance = px(160f)
        val up = start.y - distance > form.top + px(8f)
        val move = if (up) -distance else distance
        val gap = bounds(neighbour).top - bounds(block).top
        val before = scrolled()
        compose.onNodeWithTag(EditorTags.FORM).performTouchInput {
            down(start - form.topLeft)
            repeat(16) { moveBy(Offset(0f, move / 16)) }
        }
        compose.waitForIdle()
        val during = scrolled() - before
        assertTrue("from $where, the editor scrolls with the finger (${during / compose.density.density} dp)", abs(during) > px(100f))
        assertTrue("from $where, it scrolls the way the finger goes", (during > 0) == up)
        assertEquals("from $where, the block is not picked up", gap, bounds(neighbour).top - bounds(block).top, 1f)
        compose.onNodeWithTag(EditorTags.FORM).performTouchInput { up() }
        compose.waitForIdle()
        assertEquals("from $where, the block stays in its place", gap, bounds(neighbour).top - bounds(block).top, 1f)
    }

    @Test
    fun aFingerOnAnIngredientBlockOutsideItsColumnScrollsAndNeverDrags() {
        val id = open()
        val block = EditorTags.ingredient(1)
        val next = EditorTags.ingredient(2)
        assertScrollsFrom("the line", IngredientSheetTags.line(1), block, next) { it.center }
        assertScrollsFrom("the line's start", IngredientSheetTags.line(1), block, next) { Offset(it.left + px(16f), it.top + px(16f)) }
        assertScrollsFrom("the line's end", IngredientSheetTags.line(1), block, next) { Offset(it.right - px(8f), it.center.y) }
        // A finger that scrolls opens no sheet.
        assertFalse(compose.ingredientSheetOpen())
        assertScrollsFrom("the blank bottom of the block", block, block, next) { Offset(it.center.x, it.bottom - px(3f)) }
        assertEquals(recipe().ingredients, stored(id).ingredients)
    }

    @Test
    fun aFingerOnAStepBlockOutsideItsColumnScrollsAndNeverDrags() {
        val id = open()
        val block = EditorTags.step(2)
        val next = EditorTags.step(3)
        assertScrollsFrom("the text", StepSheetTags.line(2), block, next) { it.center }
        assertScrollsFrom("the number", StepSheetTags.line(2), block, next) { Offset(it.left + px(16f), it.top + px(16f)) }
        assertScrollsFrom("the row's end", StepSheetTags.line(2), block, next) { Offset(it.right - px(8f), it.center.y) }
        assertScrollsFrom("the timer's line", StepSheetTags.timer(2, 0), block, next) { it.center }
        // A finger that scrolls opens no sheet.
        assertFalse(compose.stepSheetOpen())
        assertScrollsFrom("the blank bottom of the block", block, block, next) { Offset(it.center.x, it.bottom - px(3f)) }
        assertEquals(recipe().steps, stored(id).steps)
    }

    // By contrast, the same move started in the column picks the block up.
    @Test
    fun theSameMoveInTheColumnDrags() {
        open()
        val block = EditorTags.step(2)
        val next = EditorTags.step(3)
        val column = EditorTags.stepHandle(2)
        compose.onNodeWithTag(column, useUnmergedTree = true).performScrollTo()
        compose.waitForIdle()
        val gap = bounds(next).top - bounds(block).top
        val before = scrolled()
        // Away from the nearer edge of the screen, so the editor has no reason to scroll under the dragged block.
        val form = bounds(EditorTags.FORM)
        val move = if (bounds(column).center.y > form.center.y) -px(40f) else px(40f)
        compose.onNodeWithTag(column, useUnmergedTree = true).performTouchInput {
            down(center)
            repeat(8) { moveBy(Offset(0f, move / 8)) }
        }
        compose.waitForIdle()
        assertEquals("the editor does not scroll", before, scrolled(), 1f)
        assertTrue("the block is picked up and follows the finger", abs(bounds(next).top - bounds(block).top - gap) > px(20f))
        compose.onNodeWithTag(column, useUnmergedTree = true).performTouchInput { up() }
        compose.waitForIdle()
    }

    companion object {
        /** Every test runs with the drag column on the left, as by default, and on the right, for a left hand. */
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "handle on the right: {0}")
        fun sides(): List<Array<Any>> = listOf(arrayOf(false), arrayOf(true))
    }
}
