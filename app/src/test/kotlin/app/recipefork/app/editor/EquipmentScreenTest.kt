package app.recipefork.app.editor

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
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
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.R
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.recipe.EquipmentItem
import app.recipefork.core.recipe.Ingredient
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * The equipment in the editor (T-203's offers in [EquipmentOfferScreenTest]): a section between the serving count and the ingredients whose "Add" puts an item that
 * takes the focus; an item is a one-line field with a hint, removed with Undo, moved by its menu, marked over 40
 * characters on leaving it and dropped when left blank; a sixteenth "Add" says why it adds nothing; Next walks the
 * serving count, the items and the first ingredient row; a frozen recipe shows its items as text.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class EquipmentScreenTest {
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

    private fun recipe(vararg equipment: String) = Recipe.create(now, null).copy(
        title = "Pancakes",
        servings = 4,
        equipment = equipment.map(EquipmentItem::free),
        ingredients = listOf(Ingredient(0, null, null, "flour", UnitType.EXACT, QuantityUnit.GRAM, Rational.of(200))),
        steps = listOf(Step(0, "Fry.")),
    )

    private fun open(recipe: Recipe): Recipe {
        runBlocking { recipes.save(recipe) }
        val environment = EditorEnvironment(ActiveBook(BookName.Pool, book.database), device.catalog(context)) { now }
        compose.setContent { MaterialTheme { EditorScreen(recipe.id, recipes, environment, saving, onClose = {}) } }
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.FORM), PATIENCE_MS)
        return recipe
    }

    private fun stored(recipe: Recipe): Recipe = runBlocking { recipes.get(recipe.id) }!!

    private fun awaitStored(recipe: Recipe, equipment: List<String>) =
        compose.waitUntil(PATIENCE_MS) { stored(recipe).equipment == equipment.map(EquipmentItem::free) }

    private fun node(tag: String): SemanticsNode = compose.onNodeWithTag(tag).fetchSemanticsNode()

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    private fun focused(tag: String) = exists(tag) && node(tag).config.getOrElse(SemanticsProperties.Focused) { false }

    private fun field(index: Int): String = EditorTags.equipmentName(index)

    private fun text(tag: String): String = node(tag).config.getOrNull(SemanticsProperties.EditableText)?.text.orEmpty()

    /** Takes the focus away from where it is, to the title. */
    private fun leaveField() {
        compose.onNodeWithTag(EditorTags.TITLE).performScrollTo().performClick()
        compose.waitForIdle()
    }

    // The section stands between the serving count and the ingredients, headed "Equipment".
    @Test
    fun theSectionStandsBetweenTheServingsAndTheIngredients() {
        open(recipe("Frying pan 26 cm"))
        val servings = node(EditorTags.SERVINGS).boundsInRoot
        val equipment = node(EditorTags.EQUIPMENT).boundsInRoot
        val ingredients = node(EditorTags.INGREDIENTS).boundsInRoot
        assertTrue(servings.bottom <= equipment.top)
        assertTrue(equipment.bottom <= ingredients.top)
        compose.onNode(hasText(context.getString(R.string.editor_equipment)) and hasAnyAncestor(hasTestTag(EditorTags.EQUIPMENT))).assertExistsHeading()
        assertEquals("Frying pan 26 cm", text(field(0)))
    }

    // "Add" puts an empty item that takes the focus; it is a one-line field that says what it is for and gives an
    // example, and what is typed into it is kept.
    @Test
    fun addPutsAnItemThatTakesTheFocus() {
        val recipe = open(recipe("Oven"))
        compose.onNodeWithTag(EditorTags.EQUIPMENT_ADD).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { focused(field(1)) }
        val item = node(field(1)).config
        assertTrue(item.contains(SemanticsProperties.ImeAction))
        val shown = compose.onAllNodes(hasAnyAncestor(hasTestTag(field(1))) or hasTestTag(field(1)), useUnmergedTree = true)
            .fetchSemanticsNodes().flatMap { n -> n.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } }
        assertTrue(shown.toString(), context.getString(R.string.editor_equipment_item, 2) in shown)
        assertTrue(shown.toString(), context.getString(R.string.editor_equipment_example) in shown)
        assertEquals("e.g. Oven", context.getString(R.string.editor_equipment_example))
        compose.onNodeWithTag(field(1)).performTextInput("Frying pan 26 cm")
        awaitStored(recipe, listOf("Oven", "Frying pan 26 cm"))
        // One line: the keyboard's action key, not a new line.
        assertFalse(text(field(1)).contains('\n'))
    }

    // Up to fifteen: a sixteenth "Add" adds nothing and says why, until the author's next action (GUIDE-007).
    @Test
    fun aSixteenthAddExplainsItself() {
        val fifteen = (1..RecipeBounds.MAX_EQUIPMENT).map { "Item $it" }
        val recipe = open(recipe(*fifteen.toTypedArray()))
        assertFalse(exists(EditorTags.EQUIPMENT_STATEMENT))
        compose.onNodeWithTag(EditorTags.EQUIPMENT_ADD).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.EQUIPMENT_STATEMENT) }
        assertEquals("A recipe can list up to 15 items of equipment.", context.getString(R.string.editor_equipment_limit, RecipeBounds.MAX_EQUIPMENT))
        compose.onNode(hasTestTag(EditorTags.EQUIPMENT_STATEMENT) and hasText("A recipe can list up to 15 items of equipment.")).assertExists()
        assertFalse(exists(field(15)))
        assertEquals(fifteen.map(EquipmentItem::free), stored(recipe).equipment)
        // The author's next action ends it.
        compose.onNodeWithTag(field(0)).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { !exists(EditorTags.EQUIPMENT_STATEMENT) }
    }

    // An item over 40 characters is kept and marked once the author leaves it, and the mark goes once it is shortened.
    @Test
    fun aLongItemIsMarkedOnLeavingIt() {
        val recipe = open(recipe("Oven"))
        val long = "Cast-iron frying pan with a lid, 28 cm wide"
        compose.onNodeWithTag(field(0)).performScrollTo().performClick()
        compose.onNodeWithTag(field(0)).performTextReplacement(long)
        compose.waitForIdle()
        assertFalse(exists(EditorTags.equipmentMark(0)))
        leaveField()
        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.equipmentMark(0)) }
        val said = context.getString(R.string.editor_mark_characters_over, "40", long.length.toString())
        compose.onNode(hasTestTag(EditorTags.equipmentMark(0)) and hasText(said)).assertExists()
        assertEquals(listOf(EquipmentItem.free(long)), stored(recipe).equipment)
        compose.onNodeWithTag(field(0)).performScrollTo().performTextReplacement("Cast-iron pan 28 cm")
        compose.waitUntil(PATIENCE_MS) { !exists(EditorTags.equipmentMark(0)) }
    }

    // An item left blank goes; one left with spaces around it is held trimmed.
    @Test
    fun anItemLeftBlankIsDropped() {
        val recipe = open(recipe("Oven", "Whisk"))
        compose.onNodeWithTag(EditorTags.EQUIPMENT_ADD).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { focused(field(2)) }
        leaveField()
        compose.waitUntil(PATIENCE_MS) { !exists(field(2)) }
        compose.onNodeWithTag(field(0)).performScrollTo().performClick()
        compose.onNodeWithTag(field(0)).performTextReplacement("   ")
        leaveField()
        compose.waitUntil(PATIENCE_MS) { !exists(field(1)) }
        assertEquals("Whisk", text(field(0)))
        compose.onNodeWithTag(field(0)).performScrollTo().performClick()
        compose.onNodeWithTag(field(0)).performTextReplacement("  Balloon whisk ")
        leaveField()
        compose.waitUntil(PATIENCE_MS) { text(field(0)) == "Balloon whisk" }
        awaitStored(recipe, listOf("Balloon whisk"))
        // Nothing was removed by the author's hand, so nothing offers Undo.
        assertFalse(exists(EditorTags.STATEMENT))
    }

    // Removing an item says so with Undo, which puts it back at its place.
    @Test
    fun aRemovedItemComesBackWithUndo() {
        val recipe = open(recipe("Bowl", "Whisk", "Frying pan"))
        compose.onNodeWithTag(EditorTags.equipmentRemove(1)).performScrollTo().performClick()
        compose.awaitStatement(context.getString(R.string.editor_equipment_removed, 2))
        assertEquals("Equipment item 2 removed", context.getString(R.string.editor_equipment_removed, 2))
        awaitStored(recipe, listOf("Bowl", "Frying pan"))
        compose.onNode(
            hasText(context.getString(R.string.editor_undo)) and hasAnyAncestor(hasTestTag(EditorTags.STATEMENT)),
            useUnmergedTree = true,
        ).performClick()
        awaitStored(recipe, listOf("Bowl", "Whisk", "Frying pan"))
        compose.waitUntil(PATIENCE_MS) { exists(field(2)) && text(field(1)) == "Whisk" }
    }

    // An item's menu moves it one place up or down: the first offers no "Move up", the last no "Move down", and an only
    // item has no menu.
    @Test
    fun anItemsMenuMovesItUpAndDown() {
        val recipe = open(recipe("Bowl", "Whisk", "Frying pan"))
        compose.onNodeWithTag(EditorTags.equipmentMenu(0)).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.equipmentAction(0, RowAction.MOVE_DOWN)) }
        assertFalse(exists(EditorTags.equipmentAction(0, RowAction.MOVE_UP)))
        compose.onNodeWithTag(EditorTags.equipmentAction(0, RowAction.MOVE_DOWN)).performSemanticsAction(SemanticsActions.OnClick)
        awaitStored(recipe, listOf("Whisk", "Bowl", "Frying pan"))

        compose.waitUntil(PATIENCE_MS) { !exists(EditorTags.equipmentAction(0, RowAction.MOVE_DOWN)) }
        compose.onNodeWithTag(EditorTags.equipmentMenu(2)).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.equipmentAction(2, RowAction.MOVE_UP)) }
        assertFalse(exists(EditorTags.equipmentAction(2, RowAction.MOVE_DOWN)))
        compose.onNodeWithTag(EditorTags.equipmentAction(2, RowAction.MOVE_UP)).performSemanticsAction(SemanticsActions.OnClick)
        awaitStored(recipe, listOf("Whisk", "Frying pan", "Bowl"))
        compose.waitUntil(PATIENCE_MS) { text(field(1)) == "Frying pan" }

        compose.onNodeWithTag(EditorTags.equipmentRemove(2)).performScrollTo().performClick()
        compose.onNodeWithTag(EditorTags.equipmentRemove(1)).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { !exists(field(1)) }
        assertFalse(exists(EditorTags.equipmentMenu(0)))
    }

    // The keyboard's Next goes from the serving count through every item to the first ingredient row.
    @Test
    fun nextGoesFromTheServingsThroughTheItemsToTheIngredients() {
        open(recipe("Bowl", "Whisk"))
        compose.onNodeWithTag(EditorTags.SERVINGS).performScrollTo().performClick()
        compose.onNodeWithTag(EditorTags.SERVINGS).performImeAction()
        compose.waitUntil(PATIENCE_MS) { focused(field(0)) }
        assertEquals(androidx.compose.ui.text.input.ImeAction.Next, node(field(0)).config[SemanticsProperties.ImeAction])
        compose.onNodeWithTag(field(0)).performImeAction()
        compose.waitUntil(PATIENCE_MS) { focused(field(1)) }
        assertEquals(androidx.compose.ui.text.input.ImeAction.Next, node(field(1)).config[SemanticsProperties.ImeAction])
        compose.onNodeWithTag(field(1)).performImeAction()
        compose.waitUntil(PATIENCE_MS) { focused(EditorTags.ingredientName(0)) }
    }

    // With no equipment, Next goes from the serving count straight to the first ingredient row; with no ingredients
    // either, the last item says Done.
    @Test
    fun withNoItemsNextSkipsTheSectionAndWithNoIngredientsTheLastItemSaysDone() {
        val recipe = open(recipe())
        compose.onNodeWithTag(EditorTags.SERVINGS).performScrollTo().performClick()
        compose.onNodeWithTag(EditorTags.SERVINGS).performImeAction()
        compose.waitUntil(PATIENCE_MS) { focused(EditorTags.ingredientName(0)) }
        compose.onNodeWithTag(EditorTags.ingredientRemove(0)).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { stored(recipe).ingredients.isEmpty() }
        compose.onNodeWithTag(EditorTags.EQUIPMENT_ADD).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { focused(field(0)) }
        assertEquals(androidx.compose.ui.text.input.ImeAction.Done, node(field(0)).config[SemanticsProperties.ImeAction])
    }

    // A frozen recipe shows its items as text, with nothing to change them by.
    @Test
    fun aFrozenRecipeShowsItsItemsReadOnly() {
        open(recipe("Oven", "Pie dish 24 cm").inState(RecipeState.PUBLISHED))
        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.FROZEN) }
        compose.onNode(hasTestTag(EditorTags.equipment(0)) and hasText("Oven")).assertExists()
        compose.onNode(hasTestTag(EditorTags.equipment(1)) and hasText("Pie dish 24 cm")).assertExists()
        assertTrue(compose.onAllNodes(hasSetTextAction() and hasAnyAncestor(hasTestTag(EditorTags.EQUIPMENT))).fetchSemanticsNodes().isEmpty())
        assertFalse(exists(EditorTags.EQUIPMENT_ADD))
        assertFalse(exists(EditorTags.equipmentRemove(0)))
        assertFalse(exists(EditorTags.equipmentMenu(0)))
    }

    // An item an entry names shows as the entry's label in the interface language with its qualifier, editable or
    // frozen; leaving it untouched keeps the entry, typing past its label changes its qualifier, and typing inside the
    // label makes it the text typed.
    @Test
    fun anEntrysItemShowsItsLabelAndQualifier() {
        val recipe = open(recipe("Whisk").copy(equipment = listOf(EquipmentItem.entry(10001, "26 cm"), EquipmentItem.free("Whisk"))))
        compose.waitUntil(PATIENCE_MS) { text(field(0)) == "Frying pan 26 cm" }
        compose.onNodeWithTag(field(0)).performScrollTo().performClick()
        leaveField()
        compose.waitForIdle()
        assertEquals(listOf(EquipmentItem.entry(10001, "26 cm"), EquipmentItem.free("Whisk")), stored(recipe).equipment)
        compose.onNodeWithTag(field(0)).performScrollTo().performClick()
        compose.onNodeWithTag(field(0)).performTextReplacement("Frying pan 28 cm")
        leaveField()
        compose.waitUntil(PATIENCE_MS) { stored(recipe).equipment == listOf(EquipmentItem.entry(10001, "28 cm"), EquipmentItem.free("Whisk")) }
        compose.onNodeWithTag(field(0)).performScrollTo().performClick()
        compose.onNodeWithTag(field(0)).performTextReplacement("Frying pin 28 cm")
        leaveField()
        awaitStored(recipe, listOf("Frying pin 28 cm", "Whisk"))
    }

    @Test
    @Config(qualifiers = "ru")
    fun aFrozenRecipeShowsAnEntrysItemInRussian() {
        open(recipe().copy(equipment = listOf(EquipmentItem.entry(10202), EquipmentItem.entry(10001, "26 см"))).inState(RecipeState.PUBLISHED))
        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.FROZEN) }
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(hasTestTag(EditorTags.equipment(1)) and hasText("Сковорода 26 см")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasTestTag(EditorTags.equipment(0)) and hasText("Духовка")).assertExists()
    }

    // A frozen recipe with no equipment shows no section.
    @Test
    fun aFrozenRecipeWithNoEquipmentShowsNoSection() {
        open(recipe().inState(RecipeState.PUBLISHED))
        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.FROZEN) }
        assertFalse(exists(EditorTags.EQUIPMENT))
    }

    // In Russian: the heading, the hint and the limit's statement.
    @Test
    @Config(qualifiers = "ru")
    fun inRussian() {
        open(recipe())
        compose.onNode(hasText("Посуда и техника") and hasAnyAncestor(hasTestTag(EditorTags.EQUIPMENT))).assertExists()
        assertEquals("например, Духовка", context.getString(R.string.editor_equipment_example))
        assertEquals("В рецепте можно указать до 15 предметов посуды и техники.", context.getString(R.string.editor_equipment_limit, 15))
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertExistsHeading() {
        assertTrue(fetchSemanticsNode().config.contains(SemanticsProperties.Heading))
    }
}
