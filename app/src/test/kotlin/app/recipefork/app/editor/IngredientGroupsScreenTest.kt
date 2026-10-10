package app.recipefork.app.editor

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.input.ImeAction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.R
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.IngredientGroup
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeBounds
import app.recipefork.core.recipe.RecipeState
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
import org.robolectric.annotation.GraphicsMode
import java.time.Instant

/**
 * DOM-022 in the editor: "Add group" after the list and in a row's menu puts a heading that takes the focus and is
 * saved once named; a heading is removed with Undo, moved with its rows by its menu or its handle; a row's menu moves it
 * into or out of a group; a long name is marked on leaving it; a frozen recipe shows its headings as headings.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IngredientGroupsScreenTest {
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

    private fun recipe(vararg groups: IngredientGroup) = Recipe.create(now, null).copy(
        title = "Buns",
        ingredients = listOf("salt", "flour", "water", "sugar", "butter").mapIndexed { i, name -> row(i, name) },
        ingredientGroups = groups.toList(),
        steps = listOf(Step(0, "Bake.")),
    )

    private val grouped = recipe(IngredientGroup("Dough", 1), IngredientGroup("Glaze", 3))

    private fun open(recipe: Recipe): Recipe {
        runBlocking { recipes.save(recipe) }
        val environment = EditorEnvironment(ActiveBook(BookName.Pool, book.database), device.catalog(context)) { now }
        compose.setContent { MaterialTheme { EditorScreen(recipe.id, recipes, environment, saving, onClose = {}) } }
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.FORM), PATIENCE_MS)
        return recipe
    }

    private fun stored(recipe: Recipe): Recipe = runBlocking { recipes.get(recipe.id) }!!

    /** The list as it reads: each heading's name and a colon before its rows. */
    private fun Recipe.shape(): String = buildList {
        ingredients.forEachIndexed { position, row ->
            ingredientGroups.filter { it.firstPosition == position }.forEach { add("${it.name}:") }
            add(row.freeTextName ?: "?")
        }
        ingredientGroups.filter { it.firstPosition >= ingredients.size }.forEach { add("${it.name}:") }
    }.joinToString(" ")

    private fun awaitShape(recipe: Recipe, shape: String) = compose.waitUntil(PATIENCE_MS) { stored(recipe).shape() == shape }

    private fun node(tag: String): SemanticsNode = compose.onNodeWithTag(tag).fetchSemanticsNode()

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    private fun focused(tag: String) = exists(tag) && node(tag).config.getOrElse(SemanticsProperties.Focused) { false }

    private fun menu(menu: String, action: String) {
        compose.onNodeWithTag(menu).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { exists(action) }
        compose.onNodeWithTag(action).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntilDoesNotExist(hasTestTag(action), PATIENCE_MS)
    }

    /** Takes the focus away from where it is, to the title. */
    private fun leaveField() {
        compose.onNodeWithTag(EditorTags.TITLE).performScrollTo().performClick()
        compose.waitForIdle()
    }

    // "Add group" puts a heading after the last row, which takes the focus; it is saved once named, and the rows added
    // next belong to it. With no rows under it yet, its keyboard action is Done.
    @Test
    fun addGroupPutsAHeadingAfterTheListThatTheNextRowsFill() {
        val recipe = open(recipe())
        compose.onNodeWithTag(EditorTags.GROUP_ADD).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { focused(EditorTags.groupName(0)) }
        assertEquals(ImeAction.Done, node(EditorTags.groupName(0)).config[SemanticsProperties.ImeAction])
        compose.waitForIdle()
        assertEquals(emptyList<IngredientGroup>(), stored(recipe).ingredientGroups)

        compose.onNodeWithTag(EditorTags.groupName(0)).performTextInput("Glaze")
        awaitShape(recipe, "salt flour water sugar butter Glaze:")
        // With no rows yet, the heading cannot move: it has no handle.
        assertTrue(!exists(EditorTags.groupHandle(0)))
        compose.onNodeWithTag(EditorTags.INGREDIENT_ADD).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { focused(EditorTags.ingredientName(5)) }
        compose.onNodeWithTag(EditorTags.ingredientName(5)).performTextReplacement("egg")
        awaitShape(recipe, "salt flour water sugar butter Glaze: egg")
        assertEquals(ImeAction.Next, node(EditorTags.groupName(0)).config[SemanticsProperties.ImeAction])
    }

    // A row's menu puts a heading above the row, which then starts the group and offers it no more; a heading left
    // without a name goes.
    @Test
    fun aRowsMenuPutsAHeadingAboveItAndANameLeftBlankRemovesIt() {
        val recipe = open(recipe())
        menu(EditorTags.ingredientMenu(3), EditorTags.ingredientAction(3, RowAction.GROUP_ABOVE))
        compose.waitUntil(PATIENCE_MS) { focused(EditorTags.groupName(0)) }
        compose.onNodeWithTag(EditorTags.groupName(0)).performTextInput("Glaze")
        awaitShape(recipe, "salt flour water Glaze: sugar butter")
        compose.onNodeWithTag(EditorTags.ingredientMenu(3)).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.ingredientAction(3, RowAction.INSERT_ABOVE)) }
        assertTrue(!exists(EditorTags.ingredientAction(3, RowAction.GROUP_ABOVE)))
        compose.onNodeWithTag(EditorTags.ingredientAction(3, RowAction.INSERT_ABOVE)).performSemanticsAction(SemanticsActions.OnClick)
        // Inserted above the first row of a group, the row joins it.
        awaitShape(recipe, "salt flour water Glaze: ? sugar butter")

        menu(EditorTags.ingredientMenu(1), EditorTags.ingredientAction(1, RowAction.GROUP_ABOVE))
        compose.waitUntil(PATIENCE_MS) { focused(EditorTags.groupName(0)) }
        leaveField()
        compose.waitUntil(PATIENCE_MS) { !exists(EditorTags.group(1)) }
        awaitShape(recipe, "salt flour water Glaze: ? sugar butter")
    }

    // Removing a heading keeps its rows, now in the group above, and says so with Undo, which puts it back.
    @Test
    fun aHeadingRemovedIsOfferedForUndo() {
        val recipe = open(grouped)
        compose.onNodeWithTag(EditorTags.groupRemove(1)).performScrollTo().performClick()
        compose.awaitStatement(context.getString(R.string.editor_group_removed, 2))
        awaitShape(recipe, "salt Dough: flour water sugar butter")
        compose.onNode(
            hasText(context.getString(R.string.editor_undo)) and hasAnyAncestor(hasTestTag(EditorTags.STATEMENT)),
            useUnmergedTree = true,
        ).performClick()
        awaitShape(recipe, "salt Dough: flour water Glaze: sugar butter")
        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.groupName(1)) }
    }

    // A heading's menu moves the group, rows and all, past the group below — the first offers no "Move up" above the
    // rows no group owns; a row's menu moves a row past a heading, out of its group or into the next, and a group left
    // with no rows goes.
    @Test
    fun menusMoveAGroupWithItsRowsAndARowAcrossAHeading() {
        val recipe = open(grouped)
        compose.onNodeWithTag(EditorTags.groupMenu(0)).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.groupAction(0, RowAction.MOVE_DOWN)) }
        assertTrue(!exists(EditorTags.groupAction(0, RowAction.MOVE_UP)))
        compose.onNodeWithTag(EditorTags.groupAction(0, RowAction.MOVE_DOWN)).performSemanticsAction(SemanticsActions.OnClick)
        awaitShape(recipe, "salt Glaze: sugar butter Dough: flour water")

        menu(EditorTags.ingredientMenu(1), EditorTags.ingredientAction(1, RowAction.MOVE_UP))
        awaitShape(recipe, "salt sugar Glaze: butter Dough: flour water")
        // Butter is now Glaze's only row: no move that would empty the group is offered, nor a handle to drag it by.
        compose.onNodeWithTag(EditorTags.ingredientMenu(2)).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.ingredientAction(2, RowAction.INSERT_ABOVE)) }
        assertTrue(!exists(EditorTags.ingredientAction(2, RowAction.MOVE_UP)) && !exists(EditorTags.ingredientAction(2, RowAction.MOVE_DOWN)))
        compose.onNodeWithTag(EditorTags.ingredientMenu(2)).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntilDoesNotExist(hasTestTag(EditorTags.ingredientAction(2, RowAction.INSERT_ABOVE)), PATIENCE_MS)
        assertTrue(!exists(EditorTags.ingredientHandle(2)) && exists(EditorTags.ingredientHandle(3)))
    }

    // The keyboard's Next follows the list as shown: from the serving count into the first heading, and from a heading to
    // its group's first row, whose sheet opens with its name focused. A row's sheet ends the chain: its unit, picked by
    // Next, closes it.
    @Test
    fun nextGoesThroughTheHeadingsInTheOrderShown() {
        open(recipe(IngredientGroup("Dough", 0), IngredientGroup("Glaze", 3)))
        compose.onNodeWithTag(EditorTags.SERVINGS).performScrollTo().performClick()
        compose.onNodeWithTag(EditorTags.SERVINGS).performImeAction()
        compose.waitUntil(PATIENCE_MS) { focused(EditorTags.groupName(0)) }
        compose.onNodeWithTag(EditorTags.groupName(0)).performImeAction()
        compose.waitUntil(PATIENCE_MS) { focused(EditorTags.ingredientName(0)) }
        compose.onNodeWithTag(EditorTags.ingredientName(0)).performImeAction()
        compose.waitUntil(PATIENCE_MS) { focused(IngredientNoteTags.field(0)) }
        compose.onNodeWithTag(IngredientNoteTags.field(0)).performImeAction()
        compose.waitUntil(PATIENCE_MS) { focused(EditorTags.ingredientQuantity(0)) }
        compose.onNodeWithTag(EditorTags.ingredientQuantity(0)).performImeAction()
        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.unitOption(QuantityUnit.GRAM)) }
        compose.onNodeWithTag(EditorTags.unitOption(QuantityUnit.GRAM)).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(PATIENCE_MS) { !compose.ingredientSheetOpen() }

        compose.onNodeWithTag(EditorTags.groupName(1)).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { focused(EditorTags.groupName(1)) }
        compose.onNodeWithTag(EditorTags.groupName(1)).performImeAction()
        compose.waitUntil(PATIENCE_MS) { focused(EditorTags.ingredientName(3)) }
        compose.closeIngredient()
    }

    // Dragged by its handle onto the heading above, a group lands there with every row it owns.
    @Test
    fun aHeadingDraggedCarriesItsRows() {
        val recipe = open(grouped)
        val handle = EditorTags.groupHandle(1)
        compose.onNodeWithTag(handle).performScrollTo()
        compose.waitForIdle()
        val distance = node(EditorTags.group(0)).positionInRoot.y - node(EditorTags.group(1)).positionInRoot.y
        compose.onNodeWithTag(handle).performTouchInput {
            down(center)
            repeat(20) { moveBy(Offset(0f, distance / 20)) }
            up()
        }
        awaitShape(recipe, "salt Glaze: sugar butter Dough: flour water")
    }

    // A name over the limit is kept and marked once the author leaves it.
    @Test
    fun aLongNameIsMarkedOnLeaving() {
        val recipe = open(grouped)
        val long = "g".repeat(RecipeBounds.MAX_GROUP_NAME + 1)
        compose.onNodeWithTag(EditorTags.groupName(0)).performScrollTo().performTextReplacement(long)
        compose.waitForIdle()
        assertTrue(!exists(EditorTags.groupMark(0)))
        leaveField()
        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.groupMark(0)) }
        assertEquals(long, stored(recipe).ingredientGroups[0].name)
    }

    // A frozen recipe shows its headings as headings for the screen reader, with nothing to change them by.
    @Test
    fun aFrozenRecipeShowsItsHeadings() {
        open(grouped.copy(state = RecipeState.PENDING))
        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.group(1)) }
        assertTrue(SemanticsProperties.Heading in node(EditorTags.group(1)).config)
        assertEquals(listOf("Glaze"), compose.textsOf(EditorTags.group(1)))
        assertTrue(!exists(EditorTags.groupName(1)) && !exists(EditorTags.GROUP_ADD))
    }
}
