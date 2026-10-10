package app.recipefork.app.editor

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.data.HeldDragHandleSide
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.IngredientGroup
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.Rational
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.time.Instant

/**
 * Blocks that cannot move — a group's only row, a heading with no rows yet — have no handle but keep the drag column's
 * width, so every row's and heading's name field starts at one edge, and every row's name field ends at one edge, with
 * the column on either side.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IngredientBlocksAlignTest(private val handleRight: Boolean) {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val device = TestDevice(context)
    private val recipes = RecipeRepository(ActiveBook(BookName.Pool, book.database))
    private val saving = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val now = Instant.parse("2026-10-09T12:00:00Z")

    @Before
    fun setUp() {
        compose.registerIdlingResource(book.idling)
        compose.registerIdlingResource(device.idling)
    }

    @After
    fun close() {
        saving.cancel()
        compose.unregisterIdlingResource(book.idling)
        compose.unregisterIdlingResource(device.idling)
        book.close()
        device.close()
    }

    private fun row(position: Int, name: String) =
        Ingredient(position, null, null, name, UnitType.EXACT, QuantityUnit.GRAM, Rational.of(position + 1L))

    private fun node(tag: String): SemanticsNode = compose.onNodeWithTag(tag).fetchSemanticsNode()

    private fun left(tag: String) = node(tag).positionInRoot.x

    private fun right(tag: String) = node(tag).let { it.positionInRoot.x + it.size.width }

    @Test
    fun everyLineSharesOneEdgeWhetherItsBlockMovesOrNot() {
        // Dough holds flour, water and sugar; Glaze holds butter alone, which therefore cannot move; a third heading is
        // added after the list, with no rows, which cannot move either.
        val recipe = Recipe.create(now, null).copy(
            title = "Buns",
            ingredients = listOf("salt", "flour", "water", "sugar", "butter").mapIndexed { i, name -> row(i, name) },
            ingredientGroups = listOf(IngredientGroup("Dough", 1), IngredientGroup("Glaze", 4)),
            steps = listOf(Step(0, "Bake.")),
        )
        runBlocking { recipes.save(recipe) }
        val environment = EditorEnvironment(ActiveBook(BookName.Pool, book.database), device.catalog(context), dragHandleSide = HeldDragHandleSide(handleRight)) { now }
        compose.setContent { MaterialTheme { EditorScreen(recipe.id, recipes, environment, saving, onClose = {}) } }
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.FORM), PATIENCE_MS)
        compose.onNodeWithTag(EditorTags.GROUP_ADD).performScrollTo().performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.groupName(2)), PATIENCE_MS)
        compose.onNodeWithTag(EditorTags.groupName(2)).performTextInput("To serve")
        compose.waitForIdle()

        // The blocks that cannot move have no handle.
        assertTrue(compose.onAllNodes(hasTestTag(EditorTags.ingredientHandle(4))).fetchSemanticsNodes().isEmpty())
        assertTrue(compose.onAllNodes(hasTestTag(EditorTags.groupHandle(2))).fetchSemanticsNodes().isEmpty())

        // Every row's line starts at one edge, whether its block moves or not, and ends at one edge before its icons;
        // every heading's name starts at one edge, 8 dp in from where the lines start, as its field is padded.
        val rows = (0..4).map { IngredientSheetTags.line(it) }
        val headings = (0..2).map { EditorTags.groupName(it) }
        val start = left(rows[0])
        rows.forEach { assertEquals("$it starts with the others", start, left(it), 0.5f) }
        headings.forEach { assertEquals("$it starts with the others", start + 8 * compose.density.density, left(it), 0.5f) }
        val end = right(rows[0])
        rows.forEach { assertEquals("$it ends with the others", end, right(it), 0.5f) }
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "handle on the right: {0}")
        fun sides(): List<Array<Any>> = listOf(arrayOf(false), arrayOf(true))
    }
}
