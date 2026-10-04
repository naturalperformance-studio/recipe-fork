package app.recipefork.app.editor

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
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
import app.recipefork.app.ui.Haptic
import app.recipefork.app.ui.LocalHaptics
import app.recipefork.app.ui.RecordingHaptics
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
import java.time.Instant
import kotlin.time.Duration.Companion.minutes

/**
 * Dragging steps and ingredient rows by their handles (FLOW-025): a row lands where it is dropped, by the same move
 * the row menu makes; the editor scrolls under a row dragged against its edge; the handle is not the only way
 * (A11Y-006) and the screen reader does not see it; a frozen recipe has none.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(ParameterizedRobolectricTestRunner::class)
class RowDragEditorTest(private val handleRight: Boolean) {
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
    private val haptics = RecordingHaptics()

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
        compose.setContent {
            CompositionLocalProvider(LocalHaptics provides haptics) { MaterialTheme { LibraryOrEditor(session, recipes, environment, opened) } }
        }
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

    private fun closeDirectly() {
        compose.onNodeWithTag(EditorTags.BACK).performClick()
        compose.waitUntilDoesNotExist(hasTestTag(EditorTags.FORM), PATIENCE_MS)
    }

    private fun seed(recipe: Recipe): Recipe = recipe.also { runBlocking { recipes.save(it) } }

    private fun stored(id: RecipeId): Recipe = runBlocking { recipes.get(id) }!!

    private fun node(tag: String): SemanticsNode = compose.onNodeWithTag(tag).fetchSemanticsNode()

    private fun exists(tag: String): Boolean = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    private fun fieldText(tag: String): String = node(tag).config[SemanticsProperties.EditableText].text

    private fun hasTestTagMatching(pattern: Regex) = SemanticsMatcher("tag matches $pattern") {
        it.config.getOrElseNullable(SemanticsProperties.TestTag) { null }?.matches(pattern) == true
    }

    private fun count(pattern: String): Int = compose.onAllNodes(hasTestTagMatching(Regex(pattern))).fetchSemanticsNodes().size

    private fun stepCount(): Int = count("editor-step-\\d+")

    private fun ingredientCount(): Int = count("editor-ingredient-\\d+-name")

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
        compose.onNodeWithTag(EditorTags.ingredientName(position)).performScrollTo().performTextReplacement(name)
        compose.waitForIdle()
    }

    private fun stepTexts(): List<String> = (0 until stepCount()).map { fieldText(EditorTags.stepText(it)) }

    private fun ingredientNames(): List<String> = (0 until ingredientCount()).map { fieldText(EditorTags.ingredientName(it)) }

    private fun top(tag: String): Float = node(tag).positionInRoot.y

    private fun bottom(tag: String): Float = node(tag).let { it.positionInRoot.y + it.size.height }

    /**
     * Drags the row tagged [row] by its handle [handle] until it covers the row tagged [onto] — its top on that row's
     * top going up, its bottom on that row's bottom going down — in small moves as a finger does, and lifts the
     * finger there.
     */
    private fun drag(handle: String, row: String, onto: String) {
        compose.onNodeWithTag(handle).performScrollTo()
        compose.waitForIdle()
        val distance = if (top(onto) < top(row)) top(onto) - top(row) else bottom(onto) - bottom(row)
        compose.onNodeWithTag(handle).performTouchInput {
            down(center)
            repeat(20) { moveBy(Offset(0f, distance / 20)) }
            up()
        }
        compose.waitForIdle()
    }

    private fun dragStep(from: Int, to: Int) = drag(EditorTags.stepHandle(from), EditorTags.step(from), EditorTags.step(to))

    private fun dragIngredient(from: Int, to: Int) =
        drag(EditorTags.ingredientHandle(from), EditorTags.ingredient(from), EditorTags.ingredient(to))

    private fun onMenu(menu: String, actionTag: (RowAction) -> String, action: RowAction) {
        compose.onNodeWithTag(menu).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { exists(actionTag(RowAction.INSERT_ABOVE)) }
        compose.onNodeWithTag(actionTag(action)).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntilDoesNotExist(hasTestTag(actionTag(RowAction.INSERT_ABOVE)), PATIENCE_MS)
        compose.waitForIdle()
    }

    /** A recipe whose steps refer to its ingredients and carry timers. */
    private fun referenced(): Recipe = Recipe.create(created, null).copy(
        title = "Bread",
        ingredients = listOf(
            Ingredient(0, null, null, "salt", UnitType.EXACT, QuantityUnit.GRAM, Rational.of(5)),
            Ingredient(1, null, null, "flour", UnitType.EXACT, QuantityUnit.GRAM, Rational.of(500)),
            Ingredient(2, null, null, "water", UnitType.EXACT, QuantityUnit.MILLILITRE, Rational.of(300)),
            Ingredient(3, null, null, "yeast", UnitType.EXACT, QuantityUnit.GRAM, Rational.of(7)),
        ),
        steps = listOf(
            Step(0, "Mix  and .", listOf(IngredientReference(1, 4), IngredientReference(2, 9))),
            Step(1, "Rest.", timer = Timer(30.minutes)),
            Step(2, "Add  and knead.", listOf(IngredientReference(0, 4))),
            Step(3, "Stir in .", listOf(IngredientReference(3, 8)), timer = Timer(5.minutes)),
            Step(4, "Bake.", timer = Timer(40.minutes)),
        ),
    )

    // FLOW-025 (2), A11Y-003, A11Y-006: every editable step and row has a 48 dp handle the screen reader does not see;
    // the menu's "Move up" and "Move down" stay beside it.
    @Test
    fun everyEditableRowHasAHandleBesideItsMenu() {
        writeANewRecipe()
        listOf("one", "two", "three").forEach(::addStep)
        listOf("salt", "flour", "water").forEach(::addIngredient)
        (0..2).forEach { position ->
            listOf(EditorTags.stepHandle(position), EditorTags.ingredientHandle(position)).forEach { tag ->
                val handle = node(tag)
                val min = handle.layoutInfo.density.run { 48.0f * density } - 0.5f
                assertTrue("$tag width", handle.size.width >= min)
                assertTrue("$tag height", handle.size.height >= min)
                assertTrue("$tag hidden", SemanticsProperties.HideFromAccessibility in handle.config)
                assertFalse("$tag described", SemanticsProperties.ContentDescription in handle.config)
                assertFalse("$tag texts", SemanticsProperties.Text in handle.config)
                assertFalse("$tag acted on", AccessibilityChecks.isInteractive.matches(handle))
            }
        }
        assertEquals(3, count("editor-step-\\d+-handle"))
        assertEquals(3, count("editor-ingredient-\\d+-handle"))
        // The discrete moves are still offered.
        compose.onNodeWithTag(EditorTags.stepMenu(1)).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.stepAction(1, RowAction.MOVE_UP)) }
        assertTrue(exists(EditorTags.stepAction(1, RowAction.MOVE_DOWN)))
        compose.onNodeWithTag(EditorTags.stepMenu(1)).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntilDoesNotExist(hasTestTag(EditorTags.stepAction(1, RowAction.MOVE_UP)), PATIENCE_MS)
        compose.onNodeWithTag(EditorTags.ingredientMenu(1)).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.ingredientAction(1, RowAction.MOVE_UP)) }
        assertTrue(exists(EditorTags.ingredientAction(1, RowAction.MOVE_DOWN)))
        compose.onNodeWithTag(EditorTags.ingredientMenu(1)).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntilDoesNotExist(hasTestTag(EditorTags.ingredientAction(1, RowAction.MOVE_UP)), PATIENCE_MS)
        AccessibilityChecks.assertTouchTargets(compose, expectAtLeast = 20)
        AccessibilityChecks.assertInteractiveElementsLabelled(compose, expectAtLeast = 20)
    }

    // Scenario 3, FLOW-025 (2): the fourth step dragged above the second, and the same with ingredients; each lands
    // where it was dropped, keeps its timer and what it holds, and the card shows the new order.
    @Test
    @Config(qualifiers = "w411dp-h4000dp")
    fun aDraggedRowLandsWhereItIsDroppedAndTheCardShowsIt() {
        val id = writeANewRecipe()
        compose.onNodeWithTag(EditorTags.TITLE).performTextInput("Bread")
        listOf("Mix.", "Rest.", "Knead.", "Shape.", "Bake.").forEach(::addStep)
        compose.onNodeWithTag(EditorTags.timerAttach(3)).performScrollTo().performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.timerMinutes(3)), PATIENCE_MS)
        compose.onNodeWithTag(EditorTags.timerMinutes(3)).performScrollTo().performTextInput("5")
        compose.waitForIdle()
        listOf("salt", "flour", "water", "yeast").forEach(::addIngredient)
        compose.onNodeWithTag(EditorTags.ingredientQuantity(3)).performScrollTo().performTextReplacement("7")
        compose.waitForIdle()
        val before = stored(id)

        val secondStepTop = top(EditorTags.step(1)) - top(EditorTags.step(0))
        dragStep(3, 1)
        assertEquals(listOf("Mix.", "Shape.", "Rest.", "Knead.", "Bake."), stepTexts())
        assertEquals(listOf(0, 3, 1, 2, 4).map { before.steps[it].copy(position = 0) }, stored(id).steps.map { it.copy(position = 0) })
        assertEquals((0..4).toList(), stored(id).steps.map { it.position })
        assertEquals(listOf(null, Timer(5.minutes), null, null, null), stored(id).steps.map { it.timer })
        assertEquals("5", fieldText(EditorTags.timerMinutes(1)))
        // It stays where it was dropped, drawn in its new place and nowhere else.
        assertEquals(secondStepTop, top(EditorTags.step(1)) - top(EditorTags.step(0)), 0.5f)
        // And down again, below the fourth.
        dragStep(1, 3)
        assertEquals(listOf("Mix.", "Rest.", "Knead.", "Shape.", "Bake."), stepTexts())
        dragStep(3, 1)
        assertEquals(listOf("Mix.", "Shape.", "Rest.", "Knead.", "Bake."), stepTexts())

        val secondRowTop = top(EditorTags.ingredient(1)) - top(EditorTags.ingredient(0))
        dragIngredient(3, 1)
        assertEquals(listOf("salt", "yeast", "flour", "water"), ingredientNames())
        assertEquals(listOf(0, 3, 1, 2).map { before.ingredients[it].copy(position = 0) }, stored(id).ingredients.map { it.copy(position = 0) })
        assertEquals((0..3).toList(), stored(id).ingredients.map { it.position })
        assertEquals("7", fieldText(EditorTags.ingredientQuantity(1)))
        assertEquals(secondRowTop, top(EditorTags.ingredient(1)) - top(EditorTags.ingredient(0)), 0.5f)
        val after = stored(id)

        compose.onNodeWithTag(EditorTags.BACK).performClick()
        compose.awaitCard()
        compose.waitUntilExactlyOneExists(hasTestTag(CardTags.step(4)), PATIENCE_MS)
        assertEquals(listOf("Mix.", "Shape.", "Rest.", "Knead.", "Bake."), (0..4).map { compose.textsOf(CardTags.step(it)).last() })
        assertEquals(listOf("salt", "yeast", "flour", "water"), (0..3).map { compose.textsOf(CardTags.ingredient(it)).first() })
        compose.backThroughTheCard()
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.entry(id.value)), PATIENCE_MS)
        assertEquals(after, stored(id))
    }

    // FLOW-025 (3, 4): a drag makes exactly what the same moves by the row menu make — every step keeps its text,
    // timer and references, and every reference still resolves to the same ingredient.
    @Test
    @Config(qualifiers = "w411dp-h4000dp")
    fun aDragMakesWhatTheMenuMakes() {
        val dragged = seed(referenced())
        val moved = seed(referenced())

        openDirectly(dragged.id)
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.step(4)), PATIENCE_MS)
        dragStep(3, 1)
        dragIngredient(3, 0)
        dragStep(0, 2)
        closeDirectly()

        openDirectly(moved.id)
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.step(4)), PATIENCE_MS)
        fun step(position: Int, action: RowAction) = onMenu(EditorTags.stepMenu(position), { EditorTags.stepAction(position, it) }, action)
        fun row(position: Int, action: RowAction) =
            onMenu(EditorTags.ingredientMenu(position), { EditorTags.ingredientAction(position, it) }, action)
        step(3, RowAction.MOVE_UP)
        step(2, RowAction.MOVE_UP)
        (3 downTo 1).forEach { row(it, RowAction.MOVE_UP) }
        step(0, RowAction.MOVE_DOWN)
        step(1, RowAction.MOVE_DOWN)
        closeDirectly()

        val byDrag = stored(dragged.id)
        val byMenu = stored(moved.id)
        assertEquals(byMenu.steps, byDrag.steps)
        assertEquals(byMenu.ingredients, byDrag.ingredients)
        assertEquals(byMenu.media, byDrag.media)
        assertEquals(listOf("Stir in .", "Rest.", "Mix  and .", "Add  and knead.", "Bake."), byDrag.steps.map { it.text })
        assertEquals(listOf("yeast", "salt", "flour", "water"), byDrag.ingredients.map { it.freeTextName })
        // Every reference names the same ingredient it named before.
        val original = referenced()
        fun named(recipe: Recipe) = recipe.steps.associate { step ->
            step.text to step.ingredientReferences.map { recipe.ingredients[it.ingredientPosition].freeTextName to it.textOffset }
        }
        assertEquals(named(original), named(byDrag))
        assertEquals(original.steps.associate { it.text to it.timer }, byDrag.steps.associate { it.text to it.timer })
    }

    // A dragged row ticks once each time it passes to a new place — not with every move of the finger — down and up.
    @Test
    @Config(qualifiers = "w411dp-h4000dp")
    fun aDraggedRowTicksAtEachNewPlace() {
        val id = seed(referenced()).id
        openDirectly(id)
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.step(4)), PATIENCE_MS)
        assertEquals(emptyList<Haptic>(), haptics.all)
        // Twenty moves of the finger, three places passed.
        dragStep(0, 3)
        assertEquals(List(3) { Haptic.TICK }, haptics.all)
        assertEquals(listOf("Rest.", "Add", "Stir", "Mix", "Bake."), stepTexts().map { it.substringBefore(' ') })
        dragIngredient(3, 1)
        assertEquals(List(5) { Haptic.TICK }, haptics.all)
        assertEquals(listOf("salt", "yeast", "flour", "water"), ingredientNames())
    }

    // Dragging against the bottom edge scrolls the editor, so a row travels the whole recipe; against the top, back.
    @Test
    fun draggingAgainstTheEdgeScrollsTheEditor() {
        val id = writeANewRecipe()
        val texts = (1..8).map { "Step text $it." }
        texts.forEach(::addStep)
        val form = node(EditorTags.FORM).boundsInRoot
        val handle = EditorTags.stepHandle(0)
        compose.onNodeWithTag(handle).performScrollTo()
        compose.waitForIdle()
        val start = scroll().value()
        val handleTop = node(handle).boundsInRoot.top
        compose.onNodeWithTag(handle).performTouchInput {
            down(center)
            repeat(10) { moveBy(Offset(0f, (form.bottom - 8f - handleTop - center.y) / 10)) }
        }
        compose.waitUntil(PATIENCE_MS) { scroll().let { it.value() >= it.maxValue() } }
        assertTrue(scroll().value() > start)
        compose.onNodeWithTag(EditorTags.FORM).performTouchInput { up() }
        compose.waitForIdle()
        assertEquals(texts.drop(1) + texts.first(), stored(id).steps.map { it.text })
        assertEquals(texts.drop(1) + texts.first(), stepTexts())

        // And back up the whole way, against the top edge.
        val last = EditorTags.stepHandle(7)
        compose.onNodeWithTag(last).performScrollTo()
        compose.waitForIdle()
        val lastTop = node(last).boundsInRoot.top
        compose.onNodeWithTag(last).performTouchInput {
            down(center)
            repeat(10) { moveBy(Offset(0f, (form.top + 8f - lastTop - center.y) / 10)) }
        }
        compose.waitUntil(PATIENCE_MS) { scroll().value() == 0f }
        compose.onNodeWithTag(EditorTags.FORM).performTouchInput { up() }
        compose.waitForIdle()
        assertEquals(texts, stored(id).steps.map { it.text })
    }

    private fun scroll() = node(EditorTags.FORM).config[SemanticsProperties.VerticalScrollAxisRange]

    // A frozen recipe has no handles.
    @Test
    fun aFrozenRecipeHasNoHandles() {
        listOf(RecipeState.PENDING, RecipeState.LINK_SHARED, RecipeState.PUBLISHED, RecipeState.REPORTED_HIDDEN, RecipeState.REMOVED)
            .forEach { state ->
                val recipe = seed(referenced().copy(sent = SentStatus.SENT).inState(state))
                openDirectly(recipe.id)
                compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.step(4)), PATIENCE_MS)
                compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.ingredient(3)), PATIENCE_MS)
                assertEquals("$state", 0, count("editor-.*-handle"))
                assertEquals("$state", recipe, stored(recipe.id))
                closeDirectly()
            }
    }

    companion object {
        /** Every test runs with the drag column on the left, as by default, and on the right, for a left hand. */
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "handle on the right: {0}")
        fun sides(): List<Array<Any>> = listOf(arrayOf(false), arrayOf(true))
    }
}
