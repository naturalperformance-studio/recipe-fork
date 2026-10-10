package app.recipefork.app.editor

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import app.recipefork.app.R
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.data.HeldDragHandleSide
import app.recipefork.app.card.CardTags
import app.recipefork.app.card.awaitCard
import app.recipefork.app.card.backThroughTheCard
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
import app.recipefork.core.recipe.SentStatus
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.Timer
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.Rational
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import kotlin.time.Duration.Companion.minutes

/**
 * The row menu of FLOW-025 on the screen: on every editable step and ingredient row, named for its row, inserting
 * and moving one place (A11Y-006 — the alternative to the dragging that comes later); what the screen holds for a
 * row staying with it; and the order reaching the card and the book.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(ParameterizedRobolectricTestRunner::class)
class RowMenuEditorTest(private val handleRight: Boolean) {
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
    private var shown = false

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

    private fun show() {
        if (shown) return
        shown = true
        val environment = EditorEnvironment(active, device.catalog(context), dragHandleSide = HeldDragHandleSide(handleRight)) { created.plusSeconds(60) }
        compose.setContent { MaterialTheme { LibraryOrEditor(session, recipes, environment, opened) } }
        compose.onNodeWithTag(LibraryTags.tab(LibraryView.OWN)).performClick()
        compose.waitForIdle()
    }

    private fun writeANewRecipe(): RecipeId {
        show()
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.WRITE), PATIENCE_MS)
        compose.onNodeWithTag(LibraryTags.WRITE).performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.FORM), PATIENCE_MS)
        return RecipeId(requireNotNull(session.editing.value))
    }

    /** Opens the editor directly on the recipe [id], as a test fixture: the application routes there through the card. */
    private fun openDirectly(id: RecipeId) {
        show()
        compose.runOnIdle { opened.value = id }
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.FORM), PATIENCE_MS)
    }

    private fun seed(recipe: Recipe): Recipe = recipe.also { runBlocking { recipes.save(it) } }

    private fun stored(id: RecipeId): Recipe = runBlocking { recipes.get(id) }!!

    private fun string(id: Int, vararg args: Any): String = context.getString(id, *args)

    private fun node(tag: String): SemanticsNode = compose.onNodeWithTag(tag).fetchSemanticsNode()

    private fun exists(tag: String): Boolean = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun fieldText(tag: String): String = node(tag).config[SemanticsProperties.EditableText].text

    private fun hasTestTagMatching(pattern: Regex) = SemanticsMatcher("tag matches $pattern") {
        it.config.getOrElseNullable(SemanticsProperties.TestTag) { null }?.matches(pattern) == true
    }

    private fun stepCount(): Int = compose.onAllNodes(hasTestTagMatching(Regex("editor-step-\\d+"))).fetchSemanticsNodes().size

    private fun ingredientCount(): Int =
        compose.onAllNodes(hasTestTagMatching(Regex("editor-ingredient-\\d+-line"))).fetchSemanticsNodes().size

    /** Adds a step by "Add step", which opens its sheet, writes [text] in it and closes it. */
    private fun addStep(text: String) {
        compose.addStepAt(stepCount())
        writeOpenStep(stepCount() - 1, text)
    }

    /** Writes [text] into the step at [position], whose sheet is open, and closes the sheet. */
    private fun writeOpenStep(position: Int, text: String) {
        compose.onNodeWithTag(EditorTags.stepText(position)).performTextReplacement(text)
        compose.waitForIdle()
        compose.closeStep()
    }

    /** Adds a row by "Add ingredient", which opens its sheet — closing any open before — and names it [name] there. */
    private fun addIngredient(name: String) {
        compose.closeIngredient()
        val position = ingredientCount()
        compose.onNodeWithTag(EditorTags.INGREDIENT_ADD).performScrollTo().performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.ingredientName(position)), PATIENCE_MS)
        if (name.isNotEmpty()) {
            compose.onNodeWithTag(EditorTags.ingredientName(position)).performTextReplacement(name)
            compose.waitForIdle()
        }
    }

    /** The steps' texts as their rows read them; "" for a step holding none. */
    private fun stepTexts(): List<String> =
        (0 until stepCount()).map { compose.stepLine(it).let { line -> if (line == string(R.string.editor_step_new)) "" else line } }

    private fun stepLabels(): List<String> = (0 until stepCount()).map { compose.textsOf(StepSheetTags.line(it)).first() }

    /** The rows' names as their lines end with them; "" for a row holding nothing. */
    private fun ingredientNames(): List<String> = (0 until ingredientCount()).map { position ->
        compose.ingredientLine(position).let { if (it == context.getString(R.string.editor_ingredient_new)) "" else it.substringAfterLast(' ') }
    }

    /** Opens the menu tagged [menu] and returns the actions it offers, in order, by tag. */
    private fun openMenu(menu: String, actionTag: (RowAction) -> String): List<RowAction> {
        // Opened by its click, as a screen reader would, so that a row's sheet open in front leaves it in reach.
        compose.onNodeWithTag(menu).performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(PATIENCE_MS) { exists(actionTag(RowAction.INSERT_ABOVE)) }
        return RowAction.entries.filter { exists(actionTag(it)) }
    }

    private fun choose(menu: String, actionTag: (RowAction) -> String, action: RowAction) {
        openMenu(menu, actionTag)
        compose.onNodeWithTag(actionTag(action)).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntilDoesNotExist(hasTestTag(actionTag(RowAction.INSERT_ABOVE)), PATIENCE_MS)
        compose.waitForIdle()
    }

    /** Chooses [action] in the sheet of the step at [position]: it stays open on its step, or opens on the one inserted. */
    private fun onStep(position: Int, action: RowAction) = compose.stepAction(position, action)

    /** The actions the sheet of the step at [position] offers, in order. */
    private fun stepActions(position: Int): List<RowAction> {
        compose.openStep(position)
        return RowAction.entries.filter { exists(EditorTags.stepAction(position, it)) }
    }

    /** Opens the sheet of ingredient row [position], chooses its [action] there and closes the sheet. */
    private fun onIngredient(position: Int, action: RowAction) {
        compose.onIngredient(position, action)
        compose.closeIngredient()
    }

    /** Opens the sheet of ingredient row [position] and returns the row actions it offers, in [RowAction]'s order. */
    private fun sheetActions(position: Int): List<RowAction> {
        compose.openIngredient(position)
        return RowAction.entries.filter { exists(EditorTags.ingredientAction(position, it)) }
    }

    private fun focused(tag: String) = exists(tag) && node(tag).config.getOrElse(SemanticsProperties.Focused) { false }

    /** Closes the open menu tagged [menu] by its control, as the author would without choosing anything. */
    private fun closeMenu(menu: String) {
        compose.onNodeWithTag(menu).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(hasTestTagMatching(Regex(".*-insert-above"))).fetchSemanticsNodes().isEmpty() }
    }

    // FLOW-025 (1, 2, 6), A11Y-006: every editable step carries a 48 dp menu named for its row, and every ingredient
    // row's sheet the same actions, with "Insert above", "Insert below", "Move up" and "Move down" — none to move the
    // first up or the last down; an ingredient row's also puts a group's heading above it (DOM-022).
    @Test
    fun everyRowCarriesAMenuNamedForItsRowInEnglish() = everyRowCarriesAMenuNamedForItsRow(
        listOf("Insert above", "Insert below", "Move up", "Move down", "Add group above"), "Step 2",
    )

    @Test
    @Config(qualifiers = "ru")
    fun everyRowCarriesAMenuNamedForItsRowInRussian() = everyRowCarriesAMenuNamedForItsRow(
        listOf("Вставить выше", "Вставить ниже", "Переместить вверх", "Переместить вниз", "Добавить группу выше"),
        "Шаг 2",
    )

    private fun everyRowCarriesAMenuNamedForItsRow(labels: List<String>, secondStep: String) {
        writeANewRecipe()
        // A single row can only have rows put beside it.
        addStep("one")
        addIngredient("salt")
        compose.closeIngredient()
        assertEquals(listOf(RowAction.INSERT_ABOVE, RowAction.INSERT_BELOW), stepActions(0))
        compose.closeStep()
        assertEquals(listOf(RowAction.INSERT_ABOVE, RowAction.INSERT_BELOW, RowAction.GROUP_ABOVE), sheetActions(0))
        compose.closeIngredient()
        listOf("two", "three").forEach(::addStep)
        listOf("flour", "water").forEach(::addIngredient)
        compose.closeIngredient()

        val all = RowAction.entries - RowAction.GROUP_ABOVE
        val expected = listOf(all - RowAction.MOVE_UP, all, all - RowAction.MOVE_DOWN)
        val ingredientExpected = expected.map { it + RowAction.GROUP_ABOVE }
        (0..2).forEach { position ->
            // A step's actions are in its sheet, named by the step it is titled with.
            assertEquals("step $position", expected[position], stepActions(position))
            expected[position].forEach { action ->
                val node = node(EditorTags.stepAction(position, action))
                assertTrue(node.size.height >= node.layoutInfo.density.run { 48.0f * density } - 0.5f)
            }
            assertEquals(
                expected[position].map { labels[it.ordinal] },
                expected[position].map { compose.textsOf(EditorTags.stepAction(position, it)).joinToString("") },
            )
            // A11Y-003, A11Y-005 with the sheet open, its actions included.
            AccessibilityChecks.assertTouchTargets(compose, expectAtLeast = expected[position].size)
            AccessibilityChecks.assertInteractiveElementsLabelled(compose, expectAtLeast = expected[position].size)
            if (position == 1) {
                val title = hasAnyAncestor(hasTestTag(StepSheetTags.SHEET)) and hasText(secondStep) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)
                assertEquals(1, compose.onAllNodes(title, useUnmergedTree = true).fetchSemanticsNodes().size)
            }
            compose.closeStep()

            // The row's actions in its sheet, each in words.
            assertEquals("row $position", ingredientExpected[position], sheetActions(position))
            assertEquals(
                ingredientExpected[position].map { labels[it.ordinal] },
                ingredientExpected[position].map { compose.textsOf(EditorTags.ingredientAction(position, it)).joinToString("") },
            )
            AccessibilityChecks.assertTouchTargets(compose, expectAtLeast = ingredientExpected[position].size)
            AccessibilityChecks.assertInteractiveElementsLabelled(compose, expectAtLeast = ingredientExpected[position].size)
            compose.closeIngredient()
        }
        // With every sheet closed, each step and each row is one labelled target of 48 dp.
        AccessibilityChecks.assertTouchTargets(compose, expectAtLeast = 6)
        AccessibilityChecks.assertInteractiveElementsLabelled(compose, expectAtLeast = 6)
    }

    // FLOW-025 (1): a new empty step, or a new row exactly like an appended one, at the place chosen; the ones after
    // it move down.
    @Test
    fun insertingPutsANewRowAtThatPlace() {
        val id = writeANewRecipe()
        listOf("one", "two", "three", "four", "five").forEach(::addStep)
        // Between the second and the third: an empty step, whose sheet opens on it.
        onStep(1, RowAction.INSERT_BELOW)
        assertEquals(listOf("one", "two", "", "three", "four", "five"), stored(id).steps.map { it.text })
        assertEquals((0..5).toList(), stored(id).steps.map { it.position })
        assertTrue(exists(EditorTags.stepText(2)))
        assertEquals("", fieldText(EditorTags.stepText(2)))
        writeOpenStep(2, "2b")
        assertEquals(listOf("one", "two", "2b", "three", "four", "five"), stepTexts())
        assertEquals((1..6).map { "Step $it" }, stepLabels())
        // Above the first.
        onStep(0, RowAction.INSERT_ABOVE)
        assertEquals(listOf("", "one", "two", "2b", "three", "four", "five"), stored(id).steps.map { it.text })
        writeOpenStep(0, "zero")
        assertEquals(listOf("zero", "one", "two", "2b", "three", "four", "five"), stepTexts())
        // Below the last.
        onStep(6, RowAction.INSERT_BELOW)
        assertEquals(Step(7, ""), stored(id).steps.last())
        writeOpenStep(7, "last")
        assertEquals(listOf("zero", "one", "two", "2b", "three", "four", "five", "last"), stepTexts())

        addIngredient("salt")
        addIngredient("flour")
        addIngredient("")
        val appended = stored(id).ingredients.last()
        // Closed holding nothing, the row added goes again.
        compose.closeIngredient()
        compose.waitUntil(PATIENCE_MS) { stored(id).ingredients.size == 2 }
        // Put above a row from its sheet: a row exactly like an appended one, whose sheet opens in place of the first,
        // its name focused.
        compose.onIngredient(1, RowAction.INSERT_ABOVE)
        compose.waitUntil(PATIENCE_MS) { focused(EditorTags.ingredientName(1)) }
        assertEquals(listOf("salt", null, "flour"), stored(id).ingredients.map { it.freeTextName })
        assertEquals(appended.copy(position = 1), stored(id).ingredients[1])
        compose.onNodeWithTag(EditorTags.ingredientName(1)).performTextReplacement("sugar")
        compose.waitForIdle()
        // And below one, from the sheet now open.
        compose.chooseInSheet(EditorTags.ingredientAction(1, RowAction.INSERT_BELOW))
        compose.waitUntil(PATIENCE_MS) { focused(EditorTags.ingredientName(2)) }
        assertEquals(appended.copy(position = 2), stored(id).ingredients[2])
        compose.onNodeWithTag(EditorTags.ingredientName(2)).performTextReplacement("yeast")
        compose.waitForIdle()
        compose.closeIngredient()
        val rows = stored(id).ingredients
        assertEquals(listOf("salt", "sugar", "yeast", "flour"), rows.map { it.freeTextName })
        assertEquals((0..3).toList(), rows.map { it.position })
        assertEquals(listOf("salt", "sugar", "yeast", "flour"), ingredientNames())
        assertEquals(stepTexts(), stored(id).steps.map { it.text })
    }

    // Scenario 3: the fourth step above the second and the same with ingredients, by the menu's moves; the order is
    // as set, timers stay with their steps, rows keep what they hold, and the card and the reopened editor show it.
    @Test
    fun movingByTheMenuChangesTheOrderAndTheCardShowsIt() {
        val id = writeANewRecipe()
        compose.onNodeWithTag(EditorTags.TITLE).performTextInput("Bread")
        listOf("Mix.", "Rest.", "Knead.", "Shape.", "Bake.").forEach(::addStep)
        // A timer on the fourth step, entered in its sheet.
        compose.openStep(3)
        compose.onNodeWithTag(EditorTags.timerAttach(3)).performScrollTo().performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.timerMinutes(3)), PATIENCE_MS)
        compose.onNodeWithTag(EditorTags.timerMinutes(3)).performScrollTo().performTextInput("5")
        compose.waitForIdle()
        compose.closeStep()
        listOf("salt", "flour", "water", "yeast").forEach(::addIngredient)
        compose.onNodeWithTag(EditorTags.ingredientQuantity(3)).performTextReplacement("7")
        compose.waitForIdle()
        compose.closeIngredient()
        val before = stored(id)

        onStep(3, RowAction.MOVE_UP)
        onStep(2, RowAction.MOVE_UP)
        val order = listOf(0, 3, 1, 2, 4)
        assertEquals(order.map { before.steps[it].text }, stored(id).steps.map { it.text })
        assertEquals(order.map { before.steps[it].copy(position = 0) }, stored(id).steps.map { it.copy(position = 0) })
        assertEquals(listOf(null, Timer(5.minutes), null, null, null), stored(id).steps.map { it.timer })
        compose.closeStep()
        assertEquals(listOf("Mix.", "Shape.", "Rest.", "Knead.", "Bake."), stepTexts())
        assertEquals("Shape. · 5 min", compose.rowText(StepSheetTags.timer(1, 0)))
        assertTrue(!exists(StepSheetTags.timer(3, 0)))
        // And one down again, then back.
        onStep(1, RowAction.MOVE_DOWN)
        compose.closeStep()
        assertEquals(listOf("Mix.", "Rest.", "Shape.", "Knead.", "Bake."), stepTexts())
        assertTrue(exists(StepSheetTags.timer(2, 0)))
        onStep(2, RowAction.MOVE_UP)
        compose.closeStep()
        assertEquals(listOf("Mix.", "Shape.", "Rest.", "Knead.", "Bake."), stepTexts())

        onIngredient(3, RowAction.MOVE_UP)
        onIngredient(2, RowAction.MOVE_UP)
        val rowOrder = listOf(0, 3, 1, 2)
        assertEquals(rowOrder.map { before.ingredients[it].copy(position = 0) }, stored(id).ingredients.map { it.copy(position = 0) })
        assertEquals((0..3).toList(), stored(id).ingredients.map { it.position })
        assertEquals(listOf("salt", "yeast", "flour", "water"), ingredientNames())
        assertEquals("7 g yeast", compose.ingredientLine(1))
        assertEquals(Rational.of(7), stored(id).ingredients[1].quantity)
        val after = stored(id)

        // The card shows the new order.
        compose.onNodeWithTag(EditorTags.BACK).performClick()
        compose.awaitCard()
        compose.waitUntilExactlyOneExists(hasTestTag(CardTags.step(4)), PATIENCE_MS)
        assertEquals(listOf("Mix.", "Shape.", "Rest.", "Knead.", "Bake."), (0..4).map { compose.textsOf(CardTags.step(it)).last() })
        assertEquals(listOf("salt", "yeast", "flour", "water"), (0..3).map { compose.textsOf(CardTags.ingredient(it)).first() })
        compose.backThroughTheCard()
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.entry(id.value)), PATIENCE_MS)
        assertEquals(after, stored(id))

        // And so does the editor opened again.
        openDirectly(id)
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.step(4)), PATIENCE_MS)
        assertEquals(listOf("Mix.", "Shape.", "Rest.", "Knead.", "Bake."), stepTexts())
        compose.openStep(1)
        assertEquals("5", fieldText(EditorTags.timerMinutes(1)))
        compose.closeStep()
        assertEquals(listOf("salt", "yeast", "flour", "water"), ingredientNames())
        assertEquals(after, stored(id))
    }

    // What the screen holds for a step — a timer being attached, kept by its row while its sheet is closed — stays with
    // it through an insert, a move and a delete above it; the sheet itself follows its step as the step moves.
    @Test
    fun whatTheScreenHoldsForAStepStaysWithIt() {
        val id = writeANewRecipe()
        listOf("one", "two", "three", "four").forEach(::addStep)
        // A timer opened on "three", with no duration yet, so nothing of it is in the recipe.
        compose.openStep(2)
        compose.onNodeWithTag(EditorTags.timerAttach(2)).performScrollTo().performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.timerHours(2)), PATIENCE_MS)
        assertEquals(null, stored(id).steps[2].timer)
        compose.closeStep()
        fun timerOpenAt(): List<Int> = (0 until stepCount()).filter { position ->
            compose.openStep(position)
            exists(EditorTags.timerHours(position)).also { compose.closeStep() }
        }

        onStep(0, RowAction.INSERT_ABOVE)
        writeOpenStep(0, "zero")
        assertEquals(listOf("zero", "one", "two", "three", "four"), stepTexts())
        assertEquals(listOf(3), timerOpenAt())

        // "three", its timer open, moves above "one"; its sheet goes with it.
        onStep(3, RowAction.MOVE_UP)
        assertTrue(exists(EditorTags.timerHours(2)))
        onStep(2, RowAction.MOVE_UP)
        assertTrue(exists(EditorTags.timerHours(1)))
        assertEquals("three", fieldText(EditorTags.stepText(1)))
        compose.closeStep()
        assertEquals(listOf("zero", "three", "one", "two", "four"), stepTexts())
        assertEquals(listOf(1), timerOpenAt())

        // Deleted above.
        compose.removeStep(0)
        assertEquals(listOf("three", "one", "two", "four"), stepTexts())
        assertEquals(listOf(0), timerOpenAt())
        // Still the same step's timer: a duration entered there is that step's.
        compose.openStep(0)
        compose.onNodeWithTag(EditorTags.timerMinutes(0)).performScrollTo().performTextInput("3")
        compose.waitForIdle()
        compose.closeStep()
        assertEquals(listOf(Timer(3.minutes), null, null, null), stored(id).steps.map { it.timer })
    }

    // What the screen holds for an ingredient row — its open sheet, with the focus, an open dictionary offer — stays with
    // that row through its own moves from that sheet; a quantity the field does not hold stays marked on its row through
    // moves, an insert and a delete above it.
    @Test
    fun whatTheScreenHoldsForAnIngredientRowStaysWithIt() {
        val id = writeANewRecipe()
        addIngredient("salt")
        // "2.50" g: a quantity the field does not hold (whole grams), marked.
        addIngredient("flour")
        compose.onNodeWithTag(EditorTags.ingredientQuantity(1)).performTextReplacement("2.50")
        compose.waitForIdle()
        assertEquals("2.50", fieldText(EditorTags.ingredientQuantity(1)))
        assertTrue(exists(EditorTags.ingredientError(1)))
        // The author is typing "chick" into the third row, and the dictionary offers entries for it.
        addIngredient("")
        compose.onNodeWithTag(EditorTags.ingredientName(2)).performTextInput("chick")
        compose.waitUntilExactlyOneExists(hasTestTag(IngredientNameTags.offer(2)), PATIENCE_MS)
        compose.onNodeWithTag(EditorTags.ingredientName(2)).assertIsFocused()
        assertMarked(1)

        // The focused row moves above the marked one, from its own sheet, which goes with it.
        compose.chooseInSheet(EditorTags.ingredientAction(2, RowAction.MOVE_UP))
        assertEquals(listOf("salt", "chick", "flour"), ingredientNames())
        assertOffered(1)
        compose.onNodeWithTag(EditorTags.ingredientName(1)).assertIsFocused()
        assertMarked(2)
        assertEquals(QuantityUnit.GRAM, stored(id).ingredients[2].unit)
        compose.chooseInSheet(EditorTags.ingredientAction(1, RowAction.MOVE_UP))
        assertEquals(listOf("chick", "salt", "flour"), ingredientNames())
        assertOffered(0)
        compose.onNodeWithTag(EditorTags.ingredientName(0)).assertIsFocused()
        // The offer is still that row's: picking from it names that row.
        compose.onNodeWithTag(IngredientNameTags.entry(0, 1)).performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(listOf(1L, null, null), stored(id).ingredients.map { it.dictionaryEntry })
        assertEquals(listOf(null, "salt", "flour"), stored(id).ingredients.map { it.freeTextName })
        compose.closeIngredient()

        // A row put above, named, and one deleted above: the mark stays on its row.
        compose.onIngredient(0, RowAction.INSERT_ABOVE)
        compose.onNodeWithTag(EditorTags.ingredientName(0)).performTextReplacement("water")
        compose.waitForIdle()
        compose.closeIngredient()
        assertMarked(3)
        compose.removeIngredient(1)
        assertEquals(listOf("water", "salt", "flour"), ingredientNames())
        assertMarked(2)
        // The marked row itself moves.
        onIngredient(2, RowAction.MOVE_UP)
        assertEquals(listOf("water", "flour", "salt"), ingredientNames())
        assertMarked(1)
    }

    private fun assertOffered(position: Int) {
        assertEquals(
            listOf(position),
            (0 until ingredientCount()).filter { exists(IngredientNameTags.offer(it)) },
        )
    }

    /** The row at [position], and no other, is marked for a quantity its field does not hold. */
    private fun assertMarked(position: Int) {
        assertEquals(listOf(position), (0 until ingredientCount()).filter { exists(IngredientSheetTags.problem(it)) })
        assertEquals("2.5 g flour", compose.ingredientLine(position))
    }

    // A frozen recipe shows no row menu.
    @Test
    fun aFrozenRecipeShowsNoRowMenu() {
        listOf(RecipeState.PENDING, RecipeState.LINK_SHARED, RecipeState.PUBLISHED, RecipeState.REPORTED_HIDDEN, RecipeState.REMOVED)
            .forEach { state ->
                val recipe = seed(
                    Recipe.create(created, null).copy(
                        title = "Bread",
                        ingredients = listOf(
                            Ingredient(0, null, null, "salt", UnitType.EXACT, QuantityUnit.GRAM, Rational.of(5)),
                            Ingredient(1, null, null, "flour", UnitType.EXACT, QuantityUnit.GRAM, Rational.of(500)),
                        ),
                        steps = listOf(Step(0, "Mix."), Step(1, "Bake.", timer = Timer(40.minutes))),
                        sent = SentStatus.SENT,
                    ).inState(state),
                )
                openDirectly(recipe.id)
                compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.step(1)), PATIENCE_MS)
                compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.ingredient(1)), PATIENCE_MS)
                assertEquals("$state", 0, compose.onAllNodes(hasTestTagMatching(Regex("editor-.*-menu"))).fetchSemanticsNodes().size)
                assertEquals("$state", 0, compose.onAllNodes(hasTestTagMatching(Regex("editor-.*-(insert|move)-.*"))).fetchSemanticsNodes().size)
                assertEquals("$state", recipe, stored(recipe.id))
                compose.onNodeWithTag(EditorTags.BACK).performClick()
                compose.waitForIdle()
            }
    }

    companion object {
        /** Every test runs with the drag column on the left, as by default, and on the right, for a left hand. */
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "handle on the right: {0}")
        fun sides(): List<Array<Any>> = listOf(arrayOf(false), arrayOf(true))
    }
}
