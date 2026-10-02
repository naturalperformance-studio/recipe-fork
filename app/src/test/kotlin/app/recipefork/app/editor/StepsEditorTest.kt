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

    private fun node(tag: String, unmerged: Boolean = false): SemanticsNode =
        compose.onNodeWithTag(tag, useUnmergedTree = unmerged).fetchSemanticsNode()

    private fun bounds(tag: String): Rect = node(tag).let { Rect(it.positionInRoot, it.size.toSize()) }

    private fun fieldText(tag: String): String = node(tag).config[SemanticsProperties.EditableText].text

    /** The label a step's field shows: its number. */
    private fun stepLabel(position: Int): String = compose.textsOf(EditorTags.stepText(position)).first()

    private fun hasSetTextAction() = SemanticsMatcher.keyIsDefined(SemanticsActions.SetText)

    private fun stepCount(): Int = compose.onAllNodes(hasTestTagMatching(Regex("editor-step-\\d+"))).fetchSemanticsNodes().size

    private fun hasTestTagMatching(pattern: Regex) = SemanticsMatcher("tag matches $pattern") {
        it.config.getOrElseNullable(SemanticsProperties.TestTag) { null }?.matches(pattern) == true
    }

    /** Appends a step through the screen and returns its position. */
    private fun addStep(): Int {
        val position = stepCount()
        compose.onNodeWithTag(EditorTags.STEP_ADD).performScrollTo().performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.stepText(position)), PATIENCE_MS)
        return position
    }

    private fun typeStep(position: Int, text: String) {
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

    // FLOW-024 (1): after the ingredients, the steps and then the description.
    @Test
    fun theStepsAndThenTheDescriptionFollowTheIngredients() {
        writeANewRecipe()
        val ingredients = bounds(EditorTags.INGREDIENTS)
        val steps = bounds(EditorTags.STEPS)
        val description = bounds(EditorTags.DESCRIPTION)
        assertTrue("the steps are not after the ingredients", steps.top >= ingredients.bottom)
        assertTrue("the description is not after the steps", description.top >= steps.bottom)
        // A step stays inside the steps, before the description.
        addStep()
        assertTrue(bounds(EditorTags.step(0)).top >= bounds(EditorTags.INGREDIENTS).bottom)
        assertTrue(bounds(EditorTags.STEP_ADD).top >= bounds(EditorTags.step(0)).bottom)
        assertTrue(bounds(EditorTags.DESCRIPTION).top >= bounds(EditorTags.STEPS).bottom)
    }

    // "Add step" appends a numbered step of plain text, whose text is edited where it stands.
    @Test
    fun addStepAppendsANumberedStepEditedWhereItStands() {
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
        assertEquals(listOf("Boil the water.", "Add the pasta.", "Drain."), stored(id).steps.map { it.text })
        assertEquals(listOf(0, 1, 2), stored(id).steps.map { it.position })
        // A step is one text field and its removal; nothing else.
        val inStep = hasAnyAncestor(hasTestTag(EditorTags.step(1)))
        val actions = compose.onAllNodes(inStep and AccessibilityChecks.isInteractive).fetchSemanticsNodes()
            .map { it.config[SemanticsProperties.TestTag] }.toSet()
        assertEquals(setOf(EditorTags.stepText(1), EditorTags.stepRemove(1)), actions)

        // Edited in place: the middle step changes, and the others and the numbering stay.
        compose.onNodeWithTag(EditorTags.stepText(1)).performScrollTo().performTextInput(" Stir once.")
        compose.waitForIdle()
        assertEquals("Add the pasta. Stir once.", fieldText(EditorTags.stepText(1)))
        assertEquals(listOf("Boil the water.", "Add the pasta. Stir once.", "Drain."), stored(id).steps.map { it.text })
        assertEquals(listOf("Step 1", "Step 2", "Step 3"), (0..2).map { stepLabel(it) })
    }

    // A step can be deleted, and the numbers after it close up.
    @Test
    fun aStepCanBeDeletedAndTheNumbersAfterItCloseUp() {
        val id = writeANewRecipe()
        listOf("one", "two", "three", "four").forEach { typeStep(addStep(), it) }
        assertEquals(string(R.string.editor_step_remove_action, 2), AccessibilityChecks.clickActionLabel(node(EditorTags.stepRemove(1))))
        compose.onNodeWithTag(EditorTags.stepRemove(1)).performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(listOf("one", "three", "four"), stored(id).steps.map { it.text })
        assertEquals(listOf(0, 1, 2), stored(id).steps.map { it.position })
        assertEquals(listOf("one", "three", "four"), (0..2).map { fieldText(EditorTags.stepText(it)) })
        assertEquals(listOf("Step 1", "Step 2", "Step 3"), (0..2).map { stepLabel(it) })
        assertEquals(string(R.string.editor_step_remove_action, 2), AccessibilityChecks.clickActionLabel(node(EditorTags.stepRemove(1))))
        compose.onNodeWithTag(EditorTags.step(3)).assertDoesNotExist()
        // The last, then the first, then the only one left.
        compose.onNodeWithTag(EditorTags.stepRemove(2)).performScrollTo().performClick()
        compose.onNodeWithTag(EditorTags.stepRemove(0)).performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(listOf("three"), stored(id).steps.map { it.text })
        assertEquals("Step 1", stepLabel(0))
        compose.onNodeWithTag(EditorTags.stepRemove(0)).performScrollTo().performClick()
        compose.waitForIdle()
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
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.stepText(2)), PATIENCE_MS)
        typeStep(1, "Add flour and knead well.")
        val edited = stored(recipe.id).steps[1]
        assertEquals("Add flour and knead well.", edited.text)
        assertEquals(Timer(10.minutes), edited.timer)
        assertEquals(listOf(IngredientReference(1, 4)), edited.ingredientReferences)

        compose.onNodeWithTag(EditorTags.stepRemove(0)).performScrollTo().performClick()
        compose.waitForIdle()
        val steps = stored(recipe.id).steps
        assertEquals(listOf("Add flour and knead well.", "Bake with the rest of the flour."), steps.map { it.text })
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
        compose.onNodeWithTag(EditorTags.DESCRIPTION).performScrollTo().performTextReplacement(longDescription)
        compose.waitForIdle()
        assertEquals(longStep, stored(id).steps.single().text)
        assertEquals(longDescription, stored(id).description)
        assertWhole(EditorTags.stepText(0), longStep)
        assertWhole(EditorTags.DESCRIPTION, longDescription)

        val frozen = seed(
            Recipe.create(created, null).copy(steps = listOf(Step(0, longStep)), description = longDescription, sent = SentStatus.SENT)
                .inState(RecipeState.PUBLISHED),
        )
        compose.runOnIdle { opened.value = frozen.id }
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.FROZEN), PATIENCE_MS)
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.step(0)), PATIENCE_MS)
        assertWhole(EditorTags.stepText(0), longStep, unmerged = true)
        assertWhole(EditorTags.DESCRIPTION_TEXT, longDescription, unmerged = true)
    }

    // FLOW-024 (6): steps and description are in the book after leaving the editor, and back in the editor.
    @Test
    @Config(qualifiers = "ru")
    fun theStepsAndTheDescriptionAreThereAfterLeavingTheEditor() {
        val id = writeANewRecipe()
        compose.onNodeWithTag(EditorTags.TITLE).performTextInput("Окрошка")
        val steps = listOf("Нарезать овощи.", "", "Залить квасом.\nПодавать холодной.")
        steps.forEach { typeStep(addStep(), it) }
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
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.stepText(2)), PATIENCE_MS)
        assertEquals(steps, (0..2).map { fieldText(EditorTags.stepText(it)) })
        assertEquals(listOf("Шаг 1", "Шаг 2", "Шаг 3"), (0..2).map { stepLabel(it) })
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
                assertEquals("$state", listOf("Шаг 1", "Смешать тесто."), compose.textsOf(EditorTags.step(0)))
                assertEquals("$state", listOf("Шаг 2", "Жарить с двух сторон."), compose.textsOf(EditorTags.step(1)))
                assertEquals("$state", listOf("Описание", "Тонкие блины.\nНа молоке."), compose.textsOf(EditorTags.DESCRIPTION))
                assertEquals("$state", 0, compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size)
                val actions = compose.onAllNodes(AccessibilityChecks.isInteractive).fetchSemanticsNodes()
                assertEquals("$state", listOf(EditorTags.BACK), actions.map { it.config[SemanticsProperties.TestTag] })
                compose.onNodeWithTag(EditorTags.STEP_ADD).assertDoesNotExist()
                compose.onNodeWithTag(EditorTags.stepRemove(0)).assertDoesNotExist()
                assertEquals("$state", recipe, stored(recipe.id))
                compose.onNodeWithTag(EditorTags.BACK).performClick()
                compose.waitForIdle()
            }
    }

    // A frozen recipe with neither steps nor a description says so.
    @Test
    fun aFrozenRecipeWithNoStepsAndNoDescriptionSaysSo() {
        val recipe = seed(Recipe.create(created, null).copy(title = "Plum cake").inState(RecipeState.PUBLISHED))
        openDirectly(recipe.id)
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.STEPS_NONE), PATIENCE_MS)
        assertEquals(listOf("No steps"), compose.textsOf(EditorTags.STEPS_NONE))
        assertEquals(listOf("Description", "No description"), compose.textsOf(EditorTags.DESCRIPTION))
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
        addStep()
        AccessibilityChecks.assertTouchTargets(compose, expectAtLeast = 5)
        AccessibilityChecks.assertInteractiveElementsLabelled(compose, expectAtLeast = 5)
        assertEquals(string(R.string.editor_step, 2), stepLabel(1))
        assertEquals(string(R.string.editor_step_remove_action, 2), AccessibilityChecks.clickActionLabel(node(EditorTags.stepRemove(1))))
    }
}
