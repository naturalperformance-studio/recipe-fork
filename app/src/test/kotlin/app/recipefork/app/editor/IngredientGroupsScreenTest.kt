package app.recipefork.app.editor

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import app.recipefork.app.ui.AccessibilityChecks
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeTimeoutException
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
 * DOM-022 in the editor: a heading reads as its name and opens in a sheet of its name and actions; "Add group" after the
 * list and in a row's sheet puts a heading whose sheet opens with the name focused, saved once named; a heading is
 * removed with Undo, moved with its rows by its sheet or its handle; a row's sheet moves it into or out of a group; a
 * long name is marked on leaving it; a frozen recipe shows its headings as headings.
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

    private fun awaitShape(recipe: Recipe, shape: String) {
        try {
            compose.waitUntil(PATIENCE_MS) { stored(recipe).shape() == shape }
        } catch (timedOut: ComposeTimeoutException) {
            assertEquals(shape, stored(recipe).shape())
            throw timedOut
        }
    }

    private fun node(tag: String): SemanticsNode = compose.onNodeWithTag(tag).fetchSemanticsNode()

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    private fun focused(tag: String) = exists(tag) && node(tag).config.getOrElse(SemanticsProperties.Focused) { false }

    private fun statementShown() = compose.onAllNodes(hasTestTag(EditorTags.STATEMENT), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    // "Add group" puts a heading after the last row and opens its sheet with the name focused; it is saved once named, and
    // the rows added next belong to it. With no rows under it yet, its keyboard action is Done, which closes the sheet;
    // the heading then reads as its name.
    @Test
    fun addGroupPutsAHeadingAfterTheListThatTheNextRowsFill() {
        val recipe = open(recipe())
        compose.onNodeWithTag(EditorTags.GROUP_ADD).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { compose.groupSheetOpen() && focused(EditorTags.groupName(0)) }
        assertEquals(ImeAction.Done, node(EditorTags.groupName(0)).config[SemanticsProperties.ImeAction])
        compose.waitForIdle()
        assertEquals(emptyList<IngredientGroup>(), stored(recipe).ingredientGroups)
        // Not named yet, it reads as such in the list.
        assertEquals(context.getString(R.string.editor_group_unnamed), compose.groupLine(0))

        compose.onNodeWithTag(EditorTags.groupName(0)).performTextInput("Glaze")
        awaitShape(recipe, "salt flour water sugar butter Glaze:")
        compose.onNodeWithTag(EditorTags.groupName(0)).performImeAction()
        compose.waitUntil(PATIENCE_MS) { !compose.groupSheetOpen() }
        assertEquals("Glaze", compose.groupLine(0))
        // With no rows yet, the heading cannot move: it has no handle.
        assertTrue(!exists(EditorTags.groupHandle(0)))
        compose.onNodeWithTag(EditorTags.INGREDIENT_ADD).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { focused(EditorTags.ingredientName(5)) }
        compose.onNodeWithTag(EditorTags.ingredientName(5)).performTextReplacement("Grandma's glaze")
        awaitShape(recipe, "salt flour water sugar butter Glaze: Grandma's glaze")
        compose.closeIngredient()
        compose.openGroup(0)
        assertEquals(ImeAction.Next, node(EditorTags.groupName(0)).config[SemanticsProperties.ImeAction])
        compose.closeGroup()
        // "Add group" again, with a heading over no rows at the end, opens that one's sheet rather than a second heading.
        compose.onNodeWithTag(EditorTags.GROUP_ADD).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { compose.groupSheetOpen() && focused(EditorTags.groupName(1)) }
        compose.closeGroup()
        compose.waitUntil(PATIENCE_MS) { !exists(EditorTags.group(1)) }
        compose.onNodeWithTag(EditorTags.GROUP_ADD).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { compose.groupSheetOpen() && focused(EditorTags.groupName(1)) }
        compose.onNodeWithTag(EditorTags.groupName(1)).performTextInput("Topping")
        compose.closeGroup()
        compose.onNodeWithTag(EditorTags.GROUP_ADD).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { compose.groupSheetOpen() && focused(EditorTags.groupName(1)) }
        assertEquals("Topping", compose.textsOf(EditorTags.groupName(1)).last())
        compose.closeGroup()
        awaitShape(recipe, "salt flour water sugar butter Glaze: Grandma's glaze Topping:")
    }

    // A row's sheet puts a heading above the row and opens the heading's sheet with its name focused; the row then starts
    // the group and offers it no more. A heading never named, closed empty, goes; one named and emptied takes its name
    // back.
    @Test
    fun aRowsSheetPutsAHeadingAboveItAndANameLeftBlankRemovesIt() {
        val recipe = open(recipe())
        compose.onIngredient(3, RowAction.GROUP_ABOVE)
        compose.waitUntil(PATIENCE_MS) { compose.groupSheetOpen() && focused(EditorTags.groupName(0)) }
        assertTrue(!compose.ingredientSheetOpen())
        compose.onNodeWithTag(EditorTags.groupName(0)).performTextInput("Glaze")
        awaitShape(recipe, "salt flour water Glaze: sugar butter")
        compose.closeGroup()
        compose.openIngredient(3)
        assertTrue(!exists(EditorTags.ingredientAction(3, RowAction.GROUP_ABOVE)))
        compose.chooseInSheet(EditorTags.ingredientAction(3, RowAction.INSERT_ABOVE))
        // Inserted above the first row of a group, the row joins it.
        awaitShape(recipe, "salt flour water Glaze: ? sugar butter")
        // Closed holding nothing, it goes again, with nothing to undo.
        compose.closeIngredient()
        awaitShape(recipe, "salt flour water Glaze: sugar butter")
        compose.waitForIdle()
        assertTrue(!statementShown())

        compose.onIngredient(1, RowAction.GROUP_ABOVE)
        compose.waitUntil(PATIENCE_MS) { focused(EditorTags.groupName(0)) }
        compose.closeGroup()
        compose.waitUntil(PATIENCE_MS) { !exists(EditorTags.group(1)) }
        awaitShape(recipe, "salt flour water Glaze: sugar butter")

        compose.openGroup(0)
        compose.onNodeWithTag(EditorTags.groupName(0)).performTextReplacement("")
        compose.closeGroup()
        awaitShape(recipe, "salt flour water Glaze: sugar butter")
        assertEquals("Glaze", compose.groupLine(0))
    }

    // Removing a heading — the last of its sheet's actions, in the error colour — closes the sheet, keeps its rows, now in
    // the group above, and says so with Undo, which puts it back.
    @Test
    fun aHeadingRemovedIsOfferedForUndo() {
        val recipe = open(grouped)
        compose.openGroup(1)
        assertEquals(listOf(context.getString(R.string.editor_group_remove)), compose.textsOf(EditorTags.groupRemove(1)))
        assertEquals(lightColorScheme().error, compose.textColourOf(EditorTags.groupRemove(1)))
        compose.removeGroup(1)
        compose.awaitStatement("“Glaze” removed")
        awaitShape(recipe, "salt Dough: flour water sugar butter")
        compose.onNode(
            hasText(context.getString(R.string.editor_undo)) and hasAnyAncestor(hasTestTag(EditorTags.STATEMENT)),
            useUnmergedTree = true,
        ).performClick()
        awaitShape(recipe, "salt Dough: flour water Glaze: sugar butter")
        compose.waitUntil(PATIENCE_MS) { exists(GroupSheetTags.line(1)) }
        assertEquals("Glaze", compose.groupLine(1))
    }

    // Remove tapped twice while a heading's sheet goes down removes that heading alone, once, and its Undo puts it back;
    // a Move tapped meanwhile does nothing.
    @Test
    fun twoQuickRemovesRemoveThatHeadingOnce() {
        val recipe = open(grouped)
        compose.openGroup(0)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag(EditorTags.groupRemove(0)).performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithTag(EditorTags.groupAction(0, RowAction.MOVE_DOWN)).performSemanticsAction(SemanticsActions.OnClick)
        compose.mainClock.advanceTimeBy(120)
        compose.onNodeWithTag(EditorTags.groupRemove(0)).performSemanticsAction(SemanticsActions.OnClick)
        compose.mainClock.autoAdvance = true
        compose.waitUntil(PATIENCE_MS) { !compose.groupSheetOpen() }
        compose.awaitStatement("“Dough” removed")
        awaitShape(recipe, "salt flour water Glaze: sugar butter")
        compose.onNode(
            hasText(context.getString(R.string.editor_undo)) and hasAnyAncestor(hasTestTag(EditorTags.STATEMENT)),
            useUnmergedTree = true,
        ).performClick()
        awaitShape(recipe, "salt Dough: flour water Glaze: sugar butter")
    }

    // A heading reads as its name, a heading for the screen reader, whose tap — named "Edit group", a target at least
    // 48 dp tall — opens its sheet: the name, then its moves and its removal. Its sheet moves the group, rows and all,
    // past the group below — the first offers no "Move up" above the rows no group owns — and follows it; a row's sheet
    // moves a row past a heading, out of its group or into the next, and offers no move that would empty a group.
    @Test
    fun sheetsMoveAGroupWithItsRowsAndARowAcrossAHeading() {
        val recipe = open(grouped)
        assertEquals("Dough", compose.groupLine(0))
        val line = node(GroupSheetTags.line(0))
        assertTrue(SemanticsProperties.Heading in line.config)
        assertEquals(context.getString(R.string.editor_group_edit), line.config[SemanticsActions.OnClick].label)
        assertTrue(line.size.height >= 48 * compose.density.density - 0.5f)
        compose.openGroup(0)
        // A named heading opens with nothing focused.
        assertTrue(!focused(EditorTags.groupName(0)))
        val inSheet = hasAnyAncestor(hasTestTag(GroupSheetTags.SHEET))
        assertEquals(
            SHEET_HANDLE + listOf(GroupSheetTags.DONE, EditorTags.groupName(0), EditorTags.groupAction(0, RowAction.MOVE_DOWN), EditorTags.groupRemove(0)),
            compose.onAllNodes(inSheet and AccessibilityChecks.isInteractive).fetchSemanticsNodes().map(::controlName),
        )
        compose.chooseInSheet(EditorTags.groupAction(0, RowAction.MOVE_DOWN))
        awaitShape(recipe, "salt Glaze: sugar butter Dough: flour water")
        // The sheet follows its heading, now the last: it moves up, and no further down.
        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.groupAction(1, RowAction.MOVE_UP)) }
        assertEquals("Dough", compose.textsOf(EditorTags.groupName(1)).last())
        assertTrue(!exists(EditorTags.groupAction(1, RowAction.MOVE_DOWN)))
        compose.closeGroup()

        compose.onIngredient(1, RowAction.MOVE_UP)
        awaitShape(recipe, "salt sugar Glaze: butter Dough: flour water")
        // Butter is now Glaze's only row: no move that would empty the group is offered, nor a handle to drag it by.
        compose.openIngredient(2)
        assertTrue(exists(EditorTags.ingredientAction(2, RowAction.INSERT_ABOVE)))
        assertTrue(!exists(EditorTags.ingredientAction(2, RowAction.MOVE_UP)) && !exists(EditorTags.ingredientAction(2, RowAction.MOVE_DOWN)))
        compose.closeIngredient()
        assertTrue(!exists(EditorTags.ingredientHandle(2)) && exists(EditorTags.ingredientHandle(3)))
    }

    // The keyboard's Next follows the list as shown: from the finished weight into the first heading's sheet, and from a
    // heading to its group's first row, whose sheet opens with its name focused. A row's sheet ends the chain: its unit,
    // picked by Next, goes on to its note (T-218), whose Done closes it. The heading's name is settled on the way — trimmed — and the field left gives up its focus.
    @Test
    fun nextGoesThroughTheHeadingsInTheOrderShown() {
        val recipe = open(recipe(IngredientGroup("Dough", 0), IngredientGroup("Glaze", 3)))
        compose.onNodeWithTag(EditorTags.FINISHED_WEIGHT).performScrollTo().performClick()
        compose.onNodeWithTag(EditorTags.FINISHED_WEIGHT).performImeAction()
        compose.waitUntil(PATIENCE_MS) { compose.groupSheetOpen() && focused(EditorTags.groupName(0)) }
        assertTrue(!focused(EditorTags.FINISHED_WEIGHT))
        compose.onNodeWithTag(EditorTags.groupName(0)).performImeAction()
        compose.waitUntil(PATIENCE_MS) { focused(EditorTags.ingredientName(0)) }
        assertTrue(!compose.groupSheetOpen())
        compose.onNodeWithTag(EditorTags.ingredientName(0)).performImeAction()
        compose.waitUntil(PATIENCE_MS) { focused(EditorTags.ingredientQuantity(0)) }
        compose.onNodeWithTag(EditorTags.ingredientQuantity(0)).performImeAction()
        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.unitOption(QuantityUnit.GRAM)) }
        compose.onNodeWithTag(EditorTags.unitOption(QuantityUnit.GRAM)).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(PATIENCE_MS) { focused(IngredientNoteTags.field(0)) }
        compose.onNodeWithTag(IngredientNoteTags.field(0)).performImeAction()
        compose.waitUntil(PATIENCE_MS) { !compose.ingredientSheetOpen() }

        compose.openGroup(1)
        compose.onNodeWithTag(EditorTags.groupName(1)).performTextReplacement("  Icing ")
        compose.onNodeWithTag(EditorTags.groupName(1)).performImeAction()
        compose.waitUntil(PATIENCE_MS) { focused(EditorTags.ingredientName(3)) }
        assertEquals("Icing", stored(recipe).ingredientGroups[1].name)
        compose.closeIngredient()
        assertTrue(!focused(EditorTags.FINISHED_WEIGHT) && !focused(EditorTags.groupName(1)))
        assertEquals("Icing", compose.groupLine(1))
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

    // A name over the limit is kept and marked once the author leaves it — in the sheet, and on the heading once the sheet
    // is closed.
    @Test
    fun aLongNameIsMarkedOnLeaving() {
        val recipe = open(grouped)
        val long = "g".repeat(RecipeBounds.MAX_GROUP_NAME + 1)
        compose.openGroup(0)
        compose.onNodeWithTag(EditorTags.groupName(0)).performTextReplacement(long)
        compose.waitForIdle()
        assertTrue(!exists(GroupSheetTags.nameMark(0)) && !exists(EditorTags.groupMark(0)))
        compose.onNodeWithTag(EditorTags.groupName(0)).performSemanticsAction(SemanticsActions.RequestFocus)
        compose.closeGroup()
        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.groupMark(0)) }
        assertEquals(long, stored(recipe).ingredientGroups[0].name)
        compose.openGroup(0)
        assertTrue(exists(GroupSheetTags.nameMark(0)))
        compose.closeGroup()
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
