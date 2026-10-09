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
        compose.onAllNodes(hasTestTagMatching(Regex("editor-ingredient-\\d+-name"))).fetchSemanticsNodes().size

    private fun addStep(text: String) {
        val position = stepCount()
        compose.onNodeWithTag(EditorTags.STEP_ADD).performScrollTo().performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.stepText(position)), PATIENCE_MS)
        compose.onNodeWithTag(EditorTags.stepText(position)).performScrollTo().performTextReplacement(text)
        compose.waitForIdle()
    }

    private fun addIngredient(name: String) {
        val position = ingredientCount()
        compose.onNodeWithTag(EditorTags.INGREDIENT_ADD).performScrollTo().performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.ingredientName(position)), PATIENCE_MS)
        if (name.isNotEmpty()) {
            compose.onNodeWithTag(EditorTags.ingredientName(position)).performScrollTo().performTextReplacement(name)
            compose.waitForIdle()
        }
    }

    private fun stepTexts(): List<String> = (0 until stepCount()).map { fieldText(EditorTags.stepText(it)) }

    private fun stepLabels(): List<String> = (0 until stepCount()).map { compose.textsOf(EditorTags.stepText(it)).first() }

    private fun ingredientNames(): List<String> = (0 until ingredientCount()).map { fieldText(EditorTags.ingredientName(it)) }

    /** Opens the menu tagged [menu] and returns the actions it offers, in order, by tag. */
    private fun openMenu(menu: String, actionTag: (RowAction) -> String): List<RowAction> {
        compose.onNodeWithTag(menu).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { exists(actionTag(RowAction.INSERT_ABOVE)) }
        return RowAction.entries.filter { exists(actionTag(it)) }
    }

    private fun choose(menu: String, actionTag: (RowAction) -> String, action: RowAction) {
        openMenu(menu, actionTag)
        compose.onNodeWithTag(actionTag(action)).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntilDoesNotExist(hasTestTag(actionTag(RowAction.INSERT_ABOVE)), PATIENCE_MS)
        compose.waitForIdle()
    }

    private fun onStep(position: Int, action: RowAction) =
        choose(EditorTags.stepMenu(position), { EditorTags.stepAction(position, it) }, action)

    private fun onIngredient(position: Int, action: RowAction) =
        choose(EditorTags.ingredientMenu(position), { EditorTags.ingredientAction(position, it) }, action)

    /** Closes the open menu tagged [menu] by its control, as the author would without choosing anything. */
    private fun closeMenu(menu: String) {
        compose.onNodeWithTag(menu).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(hasTestTagMatching(Regex(".*-insert-above"))).fetchSemanticsNodes().isEmpty() }
    }

    // FLOW-025 (1, 2, 6), A11Y-006: every editable step and row carries a 48 dp menu named for its row, with "Insert
    // above", "Insert below", "Move up" and "Move down" — none to move the first up or the last down; an ingredient
    // row's also puts a group's heading above it (DOM-022).
    @Test
    fun everyRowCarriesAMenuNamedForItsRowInEnglish() = everyRowCarriesAMenuNamedForItsRow(
        listOf("Insert above", "Insert below", "Move up", "Move down", "Add group above"), "Step 2 actions", "Ingredient row 3 actions",
    )

    @Test
    @Config(qualifiers = "ru")
    fun everyRowCarriesAMenuNamedForItsRowInRussian() = everyRowCarriesAMenuNamedForItsRow(
        listOf("Вставить выше", "Вставить ниже", "Переместить вверх", "Переместить вниз", "Добавить группу выше"),
        "Действия с шагом 2",
        "Действия со строкой ингредиента 3",
    )

    private fun everyRowCarriesAMenuNamedForItsRow(labels: List<String>, secondStep: String, thirdRow: String) {
        writeANewRecipe()
        // A single row can only have rows put beside it.
        addStep("one")
        addIngredient("salt")
        assertEquals(listOf(RowAction.INSERT_ABOVE, RowAction.INSERT_BELOW), openMenu(EditorTags.stepMenu(0)) { EditorTags.stepAction(0, it) })
        closeMenu(EditorTags.stepMenu(0))
        assertEquals(
            listOf(RowAction.INSERT_ABOVE, RowAction.INSERT_BELOW, RowAction.GROUP_ABOVE),
            openMenu(EditorTags.ingredientMenu(0)) { EditorTags.ingredientAction(0, it) },
        )
        closeMenu(EditorTags.ingredientMenu(0))
        listOf("two", "three").forEach(::addStep)
        listOf("flour", "water").forEach(::addIngredient)

        val all = RowAction.entries - RowAction.GROUP_ABOVE
        val expected = listOf(all - RowAction.MOVE_UP, all, all - RowAction.MOVE_DOWN)
        val ingredientExpected = expected.map { it + RowAction.GROUP_ABOVE }
        (0..2).forEach { position ->
            val step = node(EditorTags.stepMenu(position))
            assertEquals(listOf(string(R.string.editor_step_menu_action, position + 1)), step.config[SemanticsProperties.ContentDescription])
            assertTrue(step.size.width >= step.layoutInfo.density.run { 48.0f * density } - 0.5f)
            assertTrue(step.size.height >= step.layoutInfo.density.run { 48.0f * density } - 0.5f)
            assertEquals("step $position", expected[position], openMenu(EditorTags.stepMenu(position)) { EditorTags.stepAction(position, it) })
            assertEquals(
                expected[position].map { labels[it.ordinal] },
                expected[position].map { compose.textsOf(EditorTags.stepAction(position, it)).joinToString("") },
            )
            // A11Y-003, A11Y-005 with the menu open, its actions included.
            AccessibilityChecks.assertTouchTargets(compose, expectAtLeast = expected[position].size)
            AccessibilityChecks.assertInteractiveElementsLabelled(compose, expectAtLeast = expected[position].size)
            closeMenu(EditorTags.stepMenu(position))

            val row = node(EditorTags.ingredientMenu(position))
            assertEquals(listOf(string(R.string.editor_ingredient_menu_action, position + 1)), row.config[SemanticsProperties.ContentDescription])
            assertEquals(
                "row $position",
                ingredientExpected[position],
                openMenu(EditorTags.ingredientMenu(position)) { EditorTags.ingredientAction(position, it) },
            )
            assertEquals(
                ingredientExpected[position].map { labels[it.ordinal] },
                ingredientExpected[position].map { compose.textsOf(EditorTags.ingredientAction(position, it)).joinToString("") },
            )
            AccessibilityChecks.assertTouchTargets(compose, expectAtLeast = ingredientExpected[position].size)
            closeMenu(EditorTags.ingredientMenu(position))
        }
        assertEquals(listOf(secondStep), node(EditorTags.stepMenu(1)).config[SemanticsProperties.ContentDescription])
        assertEquals(listOf(thirdRow), node(EditorTags.ingredientMenu(2)).config[SemanticsProperties.ContentDescription])
        AccessibilityChecks.assertTouchTargets(compose, expectAtLeast = 20)
        AccessibilityChecks.assertInteractiveElementsLabelled(compose, expectAtLeast = 20)
    }

    // FLOW-025 (1): a new empty step, or a new row exactly like an appended one, at the place chosen; the ones after
    // it move down.
    @Test
    fun insertingPutsANewRowAtThatPlace() {
        val id = writeANewRecipe()
        listOf("one", "two", "three", "four", "five").forEach(::addStep)
        // Between the second and the third.
        onStep(1, RowAction.INSERT_BELOW)
        assertEquals(listOf("one", "two", "", "three", "four", "five"), stored(id).steps.map { it.text })
        assertEquals((0..5).toList(), stored(id).steps.map { it.position })
        assertEquals(listOf("one", "two", "", "three", "four", "five"), stepTexts())
        assertEquals((1..6).map { "Step $it" }, stepLabels())
        // Above the first.
        onStep(0, RowAction.INSERT_ABOVE)
        assertEquals(listOf("", "one", "two", "", "three", "four", "five"), stored(id).steps.map { it.text })
        assertEquals(listOf("", "one", "two", "", "three", "four", "five"), stepTexts())
        // Below the last.
        onStep(6, RowAction.INSERT_BELOW)
        assertEquals(listOf("", "one", "two", "", "three", "four", "five", ""), stepTexts())
        assertEquals(Step(7, ""), stored(id).steps.last())

        addIngredient("salt")
        addIngredient("flour")
        onIngredient(1, RowAction.INSERT_ABOVE)
        onIngredient(0, RowAction.INSERT_BELOW)
        addIngredient("")
        val rows = stored(id).ingredients
        assertEquals(listOf("salt", null, null, "flour", null), rows.map { it.freeTextName })
        assertEquals((0..4).toList(), rows.map { it.position })
        val appended = rows.last()
        assertEquals(appended.copy(position = 1), rows[1])
        assertEquals(appended.copy(position = 2), rows[2])
        assertEquals(listOf("salt", "", "", "flour", ""), ingredientNames())
        assertEquals(stepTexts(), stored(id).steps.map { it.text })
    }

    // Scenario 3: the fourth step above the second and the same with ingredients, by the menu's moves; the order is
    // as set, timers stay with their steps, rows keep what they hold, and the card and the reopened editor show it.
    @Test
    fun movingByTheMenuChangesTheOrderAndTheCardShowsIt() {
        val id = writeANewRecipe()
        compose.onNodeWithTag(EditorTags.TITLE).performTextInput("Bread")
        listOf("Mix.", "Rest.", "Knead.", "Shape.", "Bake.").forEach(::addStep)
        // A timer on the fourth step, entered on the screen.
        compose.onNodeWithTag(EditorTags.timerAttach(3)).performScrollTo().performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.timerMinutes(3)), PATIENCE_MS)
        compose.onNodeWithTag(EditorTags.timerMinutes(3)).performScrollTo().performTextInput("5")
        compose.waitForIdle()
        listOf("salt", "flour", "water", "yeast").forEach(::addIngredient)
        compose.onNodeWithTag(EditorTags.ingredientQuantity(3)).performScrollTo().performTextReplacement("7")
        compose.waitForIdle()
        val before = stored(id)

        onStep(3, RowAction.MOVE_UP)
        onStep(2, RowAction.MOVE_UP)
        val order = listOf(0, 3, 1, 2, 4)
        assertEquals(order.map { before.steps[it].text }, stored(id).steps.map { it.text })
        assertEquals(order.map { before.steps[it].copy(position = 0) }, stored(id).steps.map { it.copy(position = 0) })
        assertEquals(listOf(null, Timer(5.minutes), null, null, null), stored(id).steps.map { it.timer })
        assertEquals(listOf("Mix.", "Shape.", "Rest.", "Knead.", "Bake."), stepTexts())
        assertEquals("5", fieldText(EditorTags.timerMinutes(1)))
        assertTrue(!exists(EditorTags.timerMinutes(3)))
        // And one down again, then back.
        onStep(1, RowAction.MOVE_DOWN)
        assertEquals(listOf("Mix.", "Rest.", "Shape.", "Knead.", "Bake."), stepTexts())
        assertEquals("5", fieldText(EditorTags.timerMinutes(2)))
        onStep(2, RowAction.MOVE_UP)
        assertEquals(listOf("Mix.", "Shape.", "Rest.", "Knead.", "Bake."), stepTexts())

        onIngredient(3, RowAction.MOVE_UP)
        onIngredient(2, RowAction.MOVE_UP)
        val rowOrder = listOf(0, 3, 1, 2)
        assertEquals(rowOrder.map { before.ingredients[it].copy(position = 0) }, stored(id).ingredients.map { it.copy(position = 0) })
        assertEquals((0..3).toList(), stored(id).ingredients.map { it.position })
        assertEquals(listOf("salt", "yeast", "flour", "water"), ingredientNames())
        assertEquals("7", fieldText(EditorTags.ingredientQuantity(1)))
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
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.stepText(4)), PATIENCE_MS)
        assertEquals(listOf("Mix.", "Shape.", "Rest.", "Knead.", "Bake."), stepTexts())
        assertEquals("5", fieldText(EditorTags.timerMinutes(1)))
        assertEquals(listOf("salt", "yeast", "flour", "water"), ingredientNames())
        assertEquals(after, stored(id))
    }

    // What the screen holds for a step — focus, a timer being attached — stays with it through an insert, a move and
    // a delete above it.
    @Test
    fun whatTheScreenHoldsForAStepStaysWithIt() {
        val id = writeANewRecipe()
        listOf("one", "two", "three", "four").forEach(::addStep)
        // A timer opened on "three", with no duration yet, so nothing of it is in the recipe.
        compose.onNodeWithTag(EditorTags.timerAttach(2)).performScrollTo().performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.timerHours(2)), PATIENCE_MS)
        assertEquals(null, stored(id).steps[2].timer)
        // The author is in "four".
        compose.onNodeWithTag(EditorTags.stepText(3)).performScrollTo().performTextInput(" more")
        compose.waitForIdle()
        compose.onNodeWithTag(EditorTags.stepText(3)).assertIsFocused()

        onStep(0, RowAction.INSERT_ABOVE)
        assertEquals(listOf("", "one", "two", "three", "four more"), stepTexts())
        compose.onNodeWithTag(EditorTags.stepText(4)).assertIsFocused()
        compose.onNodeWithTag(EditorTags.stepText(3)).assertIsNotFocused()
        assertTrue(exists(EditorTags.timerHours(3)))
        assertTrue(!exists(EditorTags.timerHours(2)))

        // "three", its timer open, moves above "one".
        onStep(3, RowAction.MOVE_UP)
        onStep(2, RowAction.MOVE_UP)
        assertEquals(listOf("", "three", "one", "two", "four more"), stepTexts())
        assertTrue(exists(EditorTags.timerHours(1)))
        assertEquals(1, compose.onAllNodes(hasTestTagMatching(Regex("editor-step-\\d+-timer-hours"))).fetchSemanticsNodes().size)
        compose.onNodeWithTag(EditorTags.stepText(4)).assertIsFocused()

        // The focused step itself moves.
        onStep(4, RowAction.MOVE_UP)
        assertEquals(listOf("", "three", "one", "four more", "two"), stepTexts())
        compose.onNodeWithTag(EditorTags.stepText(3)).assertIsFocused()

        // Deleted above.
        compose.onNodeWithTag(EditorTags.stepRemove(0)).performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(listOf("three", "one", "four more", "two"), stepTexts())
        assertTrue(exists(EditorTags.timerHours(0)))
        compose.onNodeWithTag(EditorTags.stepText(2)).assertIsFocused()
        // Still typing into the same step.
        compose.onNodeWithTag(EditorTags.stepText(2)).performTextInput("!")
        compose.waitForIdle()
        assertEquals(listOf("three", "one", "four more!", "two"), stored(id).steps.map { it.text })
    }

    // What the screen holds for an ingredient row — focus, an open dictionary offer, a typed value not held, an error
    // mark — stays with that row through an insert, a move and a delete above it.
    @Test
    fun whatTheScreenHoldsForAnIngredientRowStaysWithIt() {
        val id = writeANewRecipe()
        addIngredient("salt")
        // "2.50" g: a quantity the field does not hold (whole grams), marked, shown as typed.
        addIngredient("flour")
        compose.onNodeWithTag(EditorTags.ingredientQuantity(1)).performScrollTo().performTextReplacement("2.50")
        compose.waitForIdle()
        assertEquals("2.50", fieldText(EditorTags.ingredientQuantity(1)))
        assertTrue(exists(EditorTags.ingredientError(1)))
        // The author is typing "chick" into the third row, and the dictionary offers entries for it.
        addIngredient("")
        compose.onNodeWithTag(EditorTags.ingredientName(2)).performScrollTo().performTextInput("chick")
        compose.waitUntilExactlyOneExists(hasTestTag(IngredientNameTags.offer(2)), PATIENCE_MS)
        compose.onNodeWithTag(EditorTags.ingredientName(2)).assertIsFocused()

        onIngredient(0, RowAction.INSERT_ABOVE)
        assertEquals(listOf("", "salt", "flour", "chick"), ingredientNames())
        assertOffered(3)
        compose.onNodeWithTag(EditorTags.ingredientName(3)).assertIsFocused()
        assertMarked(2, "2.50")

        // The marked row moves above "salt".
        onIngredient(2, RowAction.MOVE_UP)
        assertEquals(listOf("", "flour", "salt", "chick"), ingredientNames())
        assertMarked(1, "2.50")
        assertEquals(QuantityUnit.GRAM, stored(id).ingredients[1].unit)
        assertOffered(3)
        compose.onNodeWithTag(EditorTags.ingredientName(3)).assertIsFocused()

        // The focused row itself moves.
        onIngredient(3, RowAction.MOVE_UP)
        assertEquals(listOf("", "flour", "chick", "salt"), ingredientNames())
        assertOffered(2)
        compose.onNodeWithTag(EditorTags.ingredientName(2)).assertIsFocused()

        // Deleted above.
        compose.onNodeWithTag(EditorTags.ingredientRemove(0)).performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(listOf("flour", "chick", "salt"), ingredientNames())
        assertMarked(0, "2.50")
        assertOffered(1)
        compose.onNodeWithTag(EditorTags.ingredientName(1)).assertIsFocused()
        // The offer is still that row's: picking from it names that row, once the statement of the deletion, which
        // covers the bottom of the screen, is swiped away.
        compose.swipeAwayStatement()
        compose.onNodeWithTag(IngredientNameTags.entry(1, 1)).performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(listOf(null, 1L, null), stored(id).ingredients.map { it.dictionaryEntry })
        assertEquals(listOf("flour", null, "salt"), stored(id).ingredients.map { it.freeTextName })
    }

    private fun assertOffered(position: Int) {
        assertEquals(
            listOf(position),
            (0 until ingredientCount()).filter { exists(IngredientNameTags.offer(it)) },
        )
    }

    private fun assertMarked(position: Int, typed: String) {
        assertEquals(typed, fieldText(EditorTags.ingredientQuantity(position)))
        assertEquals(listOf(position), (0 until ingredientCount()).filter { exists(EditorTags.ingredientError(it)) })
        assertTrue(SemanticsProperties.Error in node(EditorTags.ingredientQuantity(position)).config)
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
