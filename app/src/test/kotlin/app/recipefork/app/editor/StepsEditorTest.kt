package app.recipefork.app.editor

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.toSize
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.R
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.card.CardTags
import app.recipefork.app.card.awaitCard
import app.recipefork.app.card.backThroughTheCard
import app.recipefork.app.library.LibrarySession
import app.recipefork.app.library.LibraryTags
import app.recipefork.app.library.LibraryView
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.ui.AccessibilityChecks
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.IngredientReference
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.RecipeState
import app.recipefork.core.recipe.SentStatus
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.Timer
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.Rational
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
import kotlin.time.Duration.Companion.minutes

/**
 * The steps and the description of the editor (FLOW-024), on a new recipe written through the screen, and on seeded
 * recipes opened directly.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class StepsEditorTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val device = TestDevice(context)
    private val active = ActiveBook(BookName.Pool, book.database)
    private val recipes = RecipeRepository(active)
    private val session = LibrarySession(SavedStateHandle())
    private val created = Instant.parse("2026-09-29T12:00:00Z")
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
        val environment = EditorEnvironment(active, device.catalog(context)) { created.plusSeconds(60) }
        compose.setContent { MaterialTheme { LibraryOrEditor(session, recipes, environment, opened) } }
        compose.onNodeWithTag(LibraryTags.tab(LibraryView.OWN)).performClick()
        compose.waitForIdle()
    }

    private fun writeANewRecipe(): RecipeId {
        show()
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.WRITE), PATIENCE_MS)
        compose.onNodeWithTag(LibraryTags.WRITE).performScrollTo().performClick()
        compose.onNodeWithTag(LibraryTags.FROM_SCRATCH).performClick()
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

    private fun node(tag: String, unmerged: Boolean = false): SemanticsNode =
        compose.onNodeWithTag(tag, useUnmergedTree = unmerged).fetchSemanticsNode()

    private fun bounds(tag: String): Rect = node(tag).let { Rect(it.positionInRoot, it.size.toSize()) }

    private fun fieldText(tag: String): String = node(tag).config[SemanticsProperties.EditableText].text

    /** The label a step's field shows, in its open sheet: its number. */
    private fun stepLabel(position: Int): String = compose.textsOf(EditorTags.stepText(position)).first()

    /** What the row of the step at [position] is read as first: its number, as the step's name. */
    private fun rowLabel(position: Int): String = compose.textsOf(StepSheetTags.line(position)).first()

    private fun hasSetTextAction() = SemanticsMatcher.keyIsDefined(SemanticsActions.SetText)

    private fun stepCount(): Int = compose.onAllNodes(hasTestTagMatching(Regex("editor-step-\\d+"))).fetchSemanticsNodes().size

    private fun hasTestTagMatching(pattern: Regex) = SemanticsMatcher("tag matches $pattern") {
        it.config.getOrElseNullable(SemanticsProperties.TestTag) { null }?.matches(pattern) == true
    }

    /** Appends a step through the screen, which opens in its sheet, and returns its position. */
    private fun addStep(): Int {
        compose.closeStep()
        val position = stepCount()
        compose.addStepAt(position)
        return position
    }

    /** Writes [text] into the step at [position] in its sheet, opening it first; the sheet stays open. */
    private fun typeStep(position: Int, text: String) {
        compose.openStep(position)
        compose.onNodeWithTag(EditorTags.stepText(position)).performScrollTo().performTextReplacement(text)
        compose.waitForIdle()
    }

    private fun layoutOf(tag: String, unmerged: Boolean = false): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        node(tag, unmerged).config[SemanticsActions.GetTextLayoutResult].action!!.invoke(results)
        return results.single()
    }

    /** The text tagged [tag] is laid out whole: nothing cut off or ellipsized, and free to wrap onto as many lines as it takes. */
    private fun assertWhole(tag: String, text: String, unmerged: Boolean = false) {
        val layout = layoutOf(tag, unmerged)
        assertEquals(tag, text, layout.layoutInput.text.text)
        assertFalse("$tag overflows", layout.hasVisualOverflow)
        // However many lines it takes: wrapped, with no cap on the lines and no ellipsis.
        assertTrue("$tag does not wrap", layout.layoutInput.softWrap)
        assertEquals("$tag caps its lines", Int.MAX_VALUE, layout.layoutInput.maxLines)
        assertTrue("$tag ellipsizes", layout.layoutInput.overflow != TextOverflow.Ellipsis)
        assertFalse("$tag is ellipsized", (0 until layout.lineCount).any { layout.isLineEllipsized(it) })
        // The node is as tall as its text: no line is clipped by the field's own bounds.
        assertTrue("$tag is shorter than its text", node(tag, unmerged).size.height >= layout.size.height)
    }

    // FLOW-024 (1) as T-208 orders it: the title, the tags, then the description, before the serving count; after the
    // ingredients, the steps, the form's last part.
    @Test
    fun theDescriptionSitsUnderTheTitleAndTagsAndTheStepsFollowTheIngredients() {
        writeANewRecipe()
        val order = listOf(EditorTags.TITLE, EditorTags.TAGS, EditorTags.DESCRIPTION, EditorTags.SERVINGS, EditorTags.INGREDIENTS, EditorTags.STEPS)
        order.zipWithNext().forEach { (above, below) -> assertTrue("$above above $below", bounds(above).bottom <= bounds(below).top + 0.5f) }
        // A step stays inside the steps, after the ingredients.
        typeStep(addStep(), "Mix.")
        compose.closeStep()
        assertTrue(bounds(EditorTags.step(0)).top >= bounds(EditorTags.INGREDIENTS).bottom)
        assertTrue(bounds(EditorTags.STEP_ADD).top >= bounds(EditorTags.step(0)).bottom)
        assertTrue(bounds(EditorTags.DESCRIPTION).bottom <= bounds(EditorTags.SERVINGS).top)
    }

    // "Add step" appends a numbered step of plain text, which opens in its sheet; its text is edited there, and its row
    // reads it.
    @Test
    fun addStepAppendsANumberedStepEditedInItsSheet() {
        val id = writeANewRecipe()
        compose.onNodeWithTag(EditorTags.step(0)).assertDoesNotExist()
        assertEquals(string(R.string.editor_step_add_action), AccessibilityChecks.clickActionLabel(node(EditorTags.STEP_ADD)))
        listOf("Boil the water.", "Add the pasta.", "Drain.").forEachIndexed { expected, text ->
            val position = addStep()
            assertEquals(expected, position)
            assertEquals("", fieldText(EditorTags.stepText(position)))
            assertEquals("Step ${position + 1}", stepLabel(position))
            typeStep(position, text)
        }
        compose.closeStep()
        assertEquals(listOf("Boil the water.", "Add the pasta.", "Drain."), stored(id).steps.map { it.text })
        assertEquals(listOf(0, 1, 2), stored(id).steps.map { it.position })
        // A step's row is one target, which opens its sheet; nothing of the step is edited on the row.
        val inStep = hasAnyAncestor(hasTestTag(EditorTags.step(1)))
        val onRow = compose.onAllNodes(inStep and AccessibilityChecks.isInteractive).fetchSemanticsNodes().map { it.config[SemanticsProperties.TestTag] }
        assertEquals(listOf(StepSheetTags.line(1)), onRow)
        // Its sheet holds its text field, its removal and its moves (FLOW-025), the control that inserts an ingredient's
        // quantity (FLOW-031), the one that attaches its timer and the one that attaches its photo (FLOW-026 (2)), and
        // its note.
        compose.openStep(1)
        val inSheet = hasAnyAncestor(hasTestTag(StepSheetTags.SHEET))
        val actions = compose.onAllNodes(inSheet and AccessibilityChecks.isInteractive).fetchSemanticsNodes()
            .map(::controlName).toSet()
        assertEquals(
            SHEET_HANDLE.toSet() + setOf(
                StepSheetTags.DONE, EditorTags.stepText(1), EditorTags.stepRemove(1), EditorTags.stepInsert(1), EditorTags.timerAttach(1),
                EditorTags.stepPhoto(1), StepNoteTags.field(1),
            ) + RowAction.entries.filter { it != RowAction.GROUP_ABOVE }.map { EditorTags.stepAction(1, it) },
            actions,
        )

        // Edited in its sheet: the middle step changes, and the others and the numbering stay.
        compose.onNodeWithTag(EditorTags.stepText(1)).performScrollTo().performTextInput(" Stir once.")
        compose.waitForIdle()
        assertEquals("Add the pasta. Stir once.", fieldText(EditorTags.stepText(1)))
        assertEquals(listOf("Boil the water.", "Add the pasta. Stir once.", "Drain."), stored(id).steps.map { it.text })
        compose.closeStep()
        assertEquals(listOf("Boil the water.", "Add the pasta. Stir once.", "Drain."), (0..2).map { compose.stepLine(it) })
        assertEquals(listOf("Step 1", "Step 2", "Step 3"), (0..2).map { rowLabel(it) })
    }

    // A step can be deleted from its sheet, and the numbers after it close up.
    @Test
    fun aStepCanBeDeletedAndTheNumbersAfterItCloseUp() {
        val id = writeANewRecipe()
        listOf("one", "two", "three", "four").forEach { typeStep(addStep(), it) }
        compose.openStep(1)
        assertEquals(listOf(string(R.string.editor_step_remove)), compose.textsOf(EditorTags.stepRemove(1)))
        compose.removeStep(1)
        assertEquals(listOf("one", "three", "four"), stored(id).steps.map { it.text })
        assertEquals(listOf(0, 1, 2), stored(id).steps.map { it.position })
        assertEquals(listOf("one", "three", "four"), (0..2).map { compose.stepLine(it) })
        assertEquals(listOf("Step 1", "Step 2", "Step 3"), (0..2).map { rowLabel(it) })
        compose.openStep(1)
        assertEquals(listOf(string(R.string.editor_step_remove)), compose.textsOf(EditorTags.stepRemove(1)))
        compose.closeStep()
        compose.onNodeWithTag(EditorTags.step(3)).assertDoesNotExist()
        // The last, then the first, then the only one left.
        compose.removeStep(2)
        compose.removeStep(0)
        assertEquals(listOf("three"), stored(id).steps.map { it.text })
        assertEquals("Step 1", rowLabel(0))
        compose.removeStep(0)
        assertEquals(emptyList<Step>(), stored(id).steps)
        compose.onNodeWithTag(EditorTags.step(0)).assertDoesNotExist()
    }

    // Editing a step's text keeps its timer and references; deleting a step leaves every other step's references
    // resolving to the same ingredient (DOM-007).
    @Test
    fun editingAndDeletingStepsKeepTimersAndReferencesResolving() {
        val salt = Ingredient(0, null, null, "salt", UnitType.EXACT, QuantityUnit.GRAM, Rational.of(5))
        val flour = Ingredient(1, null, null, "flour", UnitType.EXACT, QuantityUnit.GRAM, Rational.of(500))
        val recipe = seed(
            Recipe.create(created, null).copy(
                title = "Bread",
                ingredients = listOf(salt, flour),
                steps = listOf(
                    Step(0, "Mix salt in.", listOf(IngredientReference(0, 4))),
                    Step(1, "Add flour and knead.", listOf(IngredientReference(1, 4)), Timer(10.minutes)),
                    Step(2, "Bake with the rest of the flour.", listOf(IngredientReference(1, 21)), Timer(40.minutes, "Oven")),
                ),
            ),
        )
        openDirectly(recipe.id)
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.step(2)), PATIENCE_MS)
        // Typed at the end of the step: the reference before it stays where it is.
        compose.openStep(1)
        compose.onNodeWithTag(EditorTags.stepText(1)).performScrollTo().performTextInput(" Rest.")
        compose.waitForIdle()
        val edited = stored(recipe.id).steps[1]
        assertEquals("Add flour and knead. Rest.", edited.text)
        assertEquals(Timer(10.minutes), edited.timer)
        assertEquals(listOf(IngredientReference(1, 4)), edited.ingredientReferences)

        compose.removeStep(0)
        val steps = stored(recipe.id).steps
        assertEquals(listOf("Add flour and knead. Rest.", "Bake with the rest of the flour."), steps.map { it.text })
        assertEquals(listOf(Timer(10.minutes), Timer(40.minutes, "Oven")), steps.map { it.timer })
        val ingredients = stored(recipe.id).ingredients
        steps.forEach { step ->
            step.ingredientReferences.forEach { assertEquals("flour", ingredients[it.ingredientPosition].freeTextName) }
        }
        assertEquals(listOf(IngredientReference(1, 4)), steps[0].ingredientReferences)
        assertEquals(listOf(IngredientReference(1, 21)), steps[1].ingredientReferences)
    }

    // FLOW-024 (2): with no serving count and no ingredient, the steps accept input.
    @Test
    fun theStepsAcceptInputWithNoServingCountAndNoIngredient() {
        val id = writeANewRecipe()
        assertEquals(null, stored(id).servings)
        assertEquals(emptyList<Ingredient>(), stored(id).ingredients)
        compose.onNodeWithTag(EditorTags.ingredient(0)).assertDoesNotExist()
        typeStep(addStep(), "Whisk everything together.")
        assertEquals(listOf("Whisk everything together."), stored(id).steps.map { it.text })
        assertEquals("Whisk everything together.", fieldText(EditorTags.stepText(0)))
        assertEquals(null, stored(id).servings)
        assertEquals(emptyList<Ingredient>(), stored(id).ingredients)
    }

    // The description is one optional field that takes several lines.
    @Test
    fun theDescriptionIsOneOptionalMultiLineField() {
        val id = writeANewRecipe()
        assertEquals("", stored(id).description)
        assertEquals("", fieldText(EditorTags.DESCRIPTION))
        assertEquals(string(R.string.editor_description_optional), compose.textsOf(EditorTags.DESCRIPTION).first())
        // One field, alone in its part of the screen.
        val fields = compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().map { it.config[SemanticsProperties.TestTag] }
        assertEquals(1, fields.count { it == EditorTags.DESCRIPTION })
        // Several lines, kept as typed.
        val text = "A summer soup.\nServe cold.\n\nBest on the second day."
        compose.onNodeWithTag(EditorTags.DESCRIPTION).performScrollTo().performTextReplacement(text)
        compose.waitForIdle()
        assertEquals(text, stored(id).description)
        assertEquals(text, fieldText(EditorTags.DESCRIPTION))
        assertTrue(layoutOf(EditorTags.DESCRIPTION).lineCount >= 4)
        // Optional: emptied again, it holds nothing and nothing else is asked for.
        compose.onNodeWithTag(EditorTags.DESCRIPTION).performTextReplacement("")
        compose.waitForIdle()
        assertEquals("", stored(id).description)
    }

    // No text is truncated, however long: in a step and in the description, while editable and while frozen.
    @Test
    fun noTextIsTruncatedHoweverLong() {
        val longStep = (1..60).joinToString(" ") { "stir the sauce slowly for word $it" }
        val longDescription = (1..60).joinToString(" ") { "a description that goes on and on, part $it" }
        val id = writeANewRecipe()
        typeStep(addStep(), longStep)
        assertWhole(EditorTags.stepText(0), longStep)
        compose.closeStep()
        compose.onNodeWithTag(EditorTags.DESCRIPTION).performScrollTo().performTextReplacement(longDescription)
        compose.waitForIdle()
        assertEquals(longStep, stored(id).steps.single().text)
        assertEquals(longDescription, stored(id).description)
        // The row reads it whole too.
        assertWhole(StepSheetTags.text(0), longStep, unmerged = true)
        assertWhole(EditorTags.DESCRIPTION, longDescription)

        val frozen = seed(
            Recipe.create(created, null).copy(steps = listOf(Step(0, longStep)), description = longDescription, sent = SentStatus.SENT)
                .inState(RecipeState.PUBLISHED),
        )
        compose.runOnIdle { opened.value = frozen.id }
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.FROZEN), PATIENCE_MS)
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.step(0)), PATIENCE_MS)
        assertWhole(StepSheetTags.text(0), longStep, unmerged = true)
        assertWhole(EditorTags.DESCRIPTION_TEXT, longDescription, unmerged = true)
    }

    // FLOW-024 (6): steps and description are in the book after leaving the editor, and back in the editor.
    @Test
    @Config(qualifiers = "ru")
    fun theStepsAndTheDescriptionAreThereAfterLeavingTheEditor() {
        val id = writeANewRecipe()
        compose.onNodeWithTag(EditorTags.TITLE).performTextInput("Окрошка")
        val steps = listOf("Нарезать овощи.", "", "Залить квасом.\nПодавать холодной.")
        // An empty step is one written and emptied: a step added and closed empty goes again.
        steps.forEach { typeStep(addStep(), it.ifEmpty { "x" }) }
        typeStep(1, "")
        compose.closeStep()
        compose.onNodeWithTag(EditorTags.DESCRIPTION).performScrollTo().performTextReplacement("Летний суп.")
        compose.waitForIdle()
        val before = stored(id)
        compose.onNodeWithTag(EditorTags.BACK).performClick()
        // The card shows the steps that hold text, numbered, and the description.
        compose.awaitCard()
        compose.onNodeWithTag(CardTags.STEPS).assertExists()
        compose.onNodeWithTag(CardTags.DESCRIPTION).assertExists()
        compose.backThroughTheCard()
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.entry(id.value)), PATIENCE_MS)
        assertEquals(before, stored(id))

        openDirectly(id)
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.step(2)), PATIENCE_MS)
        assertEquals(listOf("Нарезать овощи.", "Новый шаг", "Залить квасом.\nПодавать холодной."), (0..2).map { compose.stepLine(it) })
        assertEquals(listOf("Шаг 1", "Шаг 2", "Шаг 3"), (0..2).map { rowLabel(it) })
        assertEquals(steps, (0..2).map { compose.openStep(it); fieldText(EditorTags.stepText(it)) })
        compose.closeStep()
        assertEquals("Летний суп.", fieldText(EditorTags.DESCRIPTION))
        assertEquals(before, stored(id))
        assertEquals(steps, stored(id).steps.map { it.text })
        assertEquals("Летний суп.", stored(id).description)
    }

    // A frozen recipe shows the steps and the description, and nothing changes them.
    @Test
    @Config(qualifiers = "ru")
    fun aFrozenRecipeShowsTheStepsAndTheDescriptionReadOnly() {
        listOf(RecipeState.PENDING, RecipeState.LINK_SHARED, RecipeState.PUBLISHED, RecipeState.REPORTED_HIDDEN, RecipeState.REMOVED)
            .forEach { state ->
                val recipe = seed(
                    Recipe.create(created, null).copy(
                        title = "Блины",
                        steps = listOf(Step(0, "Смешать тесто."), Step(1, "Жарить с двух сторон.", timer = Timer(2.minutes))),
                        description = "Тонкие блины.\nНа молоке.",
                        sent = SentStatus.SENT,
                    ).inState(state),
                )
                openDirectly(recipe.id)
                compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.step(1)), PATIENCE_MS)
                assertEquals("$state", listOf("Шаг 1", "Смешать тесто."), compose.textsOf(StepSheetTags.line(0)))
                assertEquals(
                    "$state",
                    listOf("Шаг 2", "Жарить с двух сторон.", "Жарить с двух сторон. · 2 мин"),
                    compose.textsOf(StepSheetTags.line(1)),
                )
                assertEquals("$state", listOf("Описание", "Тонкие блины.\nНа молоке."), compose.textsOf(EditorTags.DESCRIPTION))
                assertEquals("$state", 0, compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size)
                val actions = compose.onAllNodes(AccessibilityChecks.isInteractive).fetchSemanticsNodes()
                assertEquals("$state", listOf(EditorTags.BACK, EditorTags.FORK_NOTE, ChecklistTags.CONTROL), actions.map { it.config[SemanticsProperties.TestTag] })
                compose.onNodeWithTag(EditorTags.STEP_ADD).assertDoesNotExist()
                compose.onNodeWithTag(EditorTags.stepRemove(0)).assertDoesNotExist()
                assertEquals("$state", recipe, stored(recipe.id))
                compose.onNodeWithTag(EditorTags.BACK).performClick()
                compose.waitForIdle()
            }
    }

    // A frozen recipe with no steps says so; one with no description shows no empty part for it (T-271).
    @Test
    fun aFrozenRecipeWithNoStepsSaysSoAndShowsNoEmptyDescription() {
        val recipe = seed(Recipe.create(created, null).copy(title = "Plum cake").inState(RecipeState.PUBLISHED))
        openDirectly(recipe.id)
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.STEPS_NONE), PATIENCE_MS)
        assertEquals(listOf("No steps"), compose.textsOf(EditorTags.STEPS_NONE))
        compose.onNodeWithTag(EditorTags.DESCRIPTION).assertDoesNotExist()
    }

    // FLOW-002 (4) at the editor's boundary: no change to the steps or the description reaches a frozen recipe.
    @Test
    fun theEditorRefusesEveryChangeOfTheStepsAndTheDescriptionToAFrozenRecipe() {
        val recipe = seed(
            Recipe.create(created, null).copy(steps = listOf(Step(0, "Mix.")), description = "Old.").inState(RecipeState.PUBLISHED),
        )
        val editor = RecipeEditor(recipes, recipe.id, { created }, CoroutineScope(Dispatchers.Unconfined))
        runBlocking { editor.load() }
        assertFalse(editor.addStep())
        assertFalse(editor.setStepText(0, "Stir."))
        assertFalse(editor.removeStep(0))
        assertFalse(editor.setDescription("New."))
        assertEquals(recipe, editor.recipe.value)
        assertEquals(recipe, stored(recipe.id))
    }

    // A11Y-003, A11Y-005 over the new parts: every target is 48 dp and every action is named, in both languages.
    @Test
    fun theStepsAreReachableAndNamedInEnglish() = reachableAndNamed()

    @Test
    @Config(qualifiers = "ru")
    fun theStepsAreReachableAndNamedInRussian() = reachableAndNamed()

    private fun reachableAndNamed() {
        writeANewRecipe()
        typeStep(addStep(), "Mix.")
        typeStep(addStep(), "Serve.")
        // The sheet open over the editor, then the editor's rows.
        AccessibilityChecks.assertTouchTargets(compose, expectAtLeast = 5)
        AccessibilityChecks.assertInteractiveElementsLabelled(compose, expectAtLeast = 5)
        assertEquals(string(R.string.editor_step, 2), stepLabel(1))
        assertEquals(listOf(string(R.string.editor_step_remove)), compose.textsOf(EditorTags.stepRemove(1)))
        compose.closeStep()
        AccessibilityChecks.assertTouchTargets(compose, expectAtLeast = 3)
        AccessibilityChecks.assertInteractiveElementsLabelled(compose, expectAtLeast = 3)
        assertEquals(string(R.string.editor_step, 2), rowLabel(1))
        assertEquals(string(R.string.editor_step_edit), AccessibilityChecks.clickActionLabel(node(StepSheetTags.line(1))))
    }
}
