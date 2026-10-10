package app.recipefork.app.editor

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.R
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.card.forkRecipe
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
import app.recipefork.core.recipe.ReferenceText
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowInputMethodManager
import java.time.Instant
import kotlin.time.Duration.Companion.minutes

/**
 * Ingredient references inside a step's text, in the editor (FLOW-031, FLOW-032 (5–7)): inserted by the step's control
 * or by typing `@`, shown as the authored quantity, kept one unit, following their ingredient, and read back from the
 * book.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ru")
class StepReferencesEditorTest {
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

    private val flour = Ingredient(0, null, null, "Мука", UnitType.EXACT, QuantityUnit.GRAM, Rational.of(200))
    private val milk = Ingredient(1, null, null, "Молоко", UnitType.EXACT, QuantityUnit.MILLILITRE, null)
    private val salt = Ingredient(2, null, null, "Соль", UnitType.TASTE_UNMEASURED, null, null)

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

    private fun open(id: RecipeId) {
        show()
        compose.runOnIdle { opened.value = id }
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.FORM), PATIENCE_MS)
    }

    private fun closeEditor() {
        compose.closeIngredient()
        compose.onNodeWithTag(EditorTags.BACK).performClick()
        compose.waitUntilDoesNotExist(hasTestTag(EditorTags.FORM), PATIENCE_MS)
        compose.waitForIdle()
    }

    private fun seed(recipe: Recipe): Recipe = recipe.also { runBlocking { recipes.save(it) } }

    private fun bread(vararg steps: Step) = seed(
        Recipe.create(created, null).copy(title = "Хлеб", servings = 4, ingredients = listOf(flour, milk, salt), steps = steps.toList()),
    )

    private fun stored(id: RecipeId): Recipe = runBlocking { recipes.get(id) }!!

    private fun string(id: Int, vararg args: Any): String = context.getString(id, *args)

    private fun node(tag: String): SemanticsNode = compose.onNodeWithTag(tag).fetchSemanticsNode()

    private fun fieldText(tag: String): String = node(tag).config[SemanticsProperties.EditableText].text

    private fun selection(position: Int): TextRange = node(EditorTags.stepText(position)).config[SemanticsProperties.TextSelectionRange]

    private fun stepShown(position: Int) = fieldText(EditorTags.stepText(position))

    /** The text as laid out on screen under [tag], with its styles. */
    private fun laidOut(tag: String, unmerged: Boolean = false): AnnotatedString {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag(tag, useUnmergedTree = unmerged).fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult].action!!.invoke(results)
        return results.single().layoutInput.text
    }

    /** Characters [start] to [end] of [text] are bold and underlined, and the characters of [typed] are neither. */
    private fun assertSetApart(text: AnnotatedString, start: Int, end: Int, typed: List<Int>) {
        fun styles(at: Int) = text.spanStyles.filter { it.start <= at && at < it.end }.map { it.item }
        (start until end).forEach { at ->
            assertTrue("bold at $at", styles(at).any { it.fontWeight == FontWeight.Bold })
            assertTrue("underlined at $at", styles(at).any { it.textDecoration == TextDecoration.Underline })
        }
        typed.forEach { at ->
            assertTrue("plain at $at", styles(at).none { it.fontWeight == FontWeight.Bold || it.textDecoration == TextDecoration.Underline })
        }
    }

    private fun placeCursor(position: Int, at: Int) {
        compose.onNodeWithTag(EditorTags.stepText(position)).performScrollTo().performTextInputSelection(TextRange(at))
        compose.waitForIdle()
    }

    private fun openOffer(position: Int) {
        compose.onNodeWithTag(EditorTags.stepInsert(position)).performScrollTo().performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.stepOffer(position, 0)), PATIENCE_MS)
    }

    private fun pick(position: Int, row: Int) {
        compose.onNodeWithTag(EditorTags.stepOffer(position, row)).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntilDoesNotExist(hasTestTag(EditorTags.stepOffer(position, row)), PATIENCE_MS)
        compose.waitForIdle()
    }

    /** The tags of the rows offered for the step at [position], in order. */
    private fun offered(position: Int): List<String> =
        compose.onAllNodesWithTagPrefix("editor-step-$position-offer-").map { it.config[SemanticsProperties.TestTag] }

    private fun androidx.compose.ui.test.junit4.ComposeTestRule.onAllNodesWithTagPrefix(prefix: String): List<SemanticsNode> =
        onAllNodes(androidx.compose.ui.test.SemanticsMatcher("tag starts with $prefix") {
            it.config.getOrElseNullable(SemanticsProperties.TestTag) { null }?.startsWith(prefix) == true
        }).fetchSemanticsNodes()

    // Scenario 1, FLOW-031 (2, 3), FLOW-032 (5): the step's control offers this recipe's rows — one with no quantity
    // and one "to taste" included, none of another recipe's — and the chosen one's quantity appears at the cursor.
    @Test
    fun theStepsControlInsertsAReferenceAtTheCursor() {
        seed(Recipe.create(created, null).copy(title = "Другой", ingredients = listOf(flour.copy(freeTextName = "Сахар"))))
        val recipe = bread(Step(0, "Всыпьте  муки"))
        open(recipe.id)
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.stepInsert(0)), PATIENCE_MS)
        val control = node(EditorTags.stepInsert(0))
        assertTrue(control.size.height >= with(compose.density) { 48.dp.roundToPx() })
        assertTrue(control.size.width >= with(compose.density) { 48.dp.roundToPx() })
        assertEquals(string(R.string.editor_step_insert_ingredient_action, 1), AccessibilityChecks.clickActionLabel(control))
        assertEquals("Вставить ингредиент в шаг 1", compose.descriptionOf(EditorTags.stepInsert(0)).single())

        placeCursor(0, 8)
        openOffer(0)
        assertEquals((0..2).map { EditorTags.stepOffer(0, it) }, offered(0))
        assertEquals(listOf("Мука — 200 г"), compose.textsOf(EditorTags.stepOffer(0, 0)))
        assertEquals(listOf("Молоко — ${ReferenceText.PLACEHOLDER} мл"), compose.textsOf(EditorTags.stepOffer(0, 1)))
        assertEquals(listOf("Соль — по вкусу"), compose.textsOf(EditorTags.stepOffer(0, 2)))
        pick(0, 0)

        assertEquals("Всыпьте 200 г муки", stepShown(0))
        assertEquals(TextRange(13), selection(0))
        // Set apart on screen by weight and an underline, not by colour alone.
        val shown = laidOut(EditorTags.stepText(0))
        assertEquals("Всыпьте 200 г муки", shown.text)
        assertSetApart(shown, 8, 13, typed = listOf(0, 7, 13, 17))
        assertEquals(Step(0, "Всыпьте  муки", listOf(IngredientReference(0, 8))), stored(recipe.id).steps.single())
    }

    // FLOW-032 (6, 7): a "to taste" row reads "по вкусу"; a row with no quantity, a visible placeholder and its unit.
    @Test
    fun aToTasteRowAndARowWithNoQuantityAreReferencedToo() {
        val recipe = bread(Step(0, "Добавьте  и "))
        open(recipe.id)
        placeCursor(0, 9)
        openOffer(0)
        pick(0, 2)
        compose.onNodeWithTag(EditorTags.stepText(0)).performTextInput(" соли, затем ")
        compose.waitForIdle()
        openOffer(0)
        pick(0, 1)
        compose.onNodeWithTag(EditorTags.stepText(0)).performTextInput(" молока")
        compose.waitForIdle()
        assertEquals("Добавьте по вкусу соли, затем ${ReferenceText.PLACEHOLDER} мл молока и ", stepShown(0))
        val step = stored(recipe.id).steps.single()
        assertEquals("Добавьте  соли, затем  молока и ", step.text)
        assertEquals(listOf(IngredientReference(2, 9), IngredientReference(1, 22)), step.ingredientReferences)
    }

    // FLOW-031 (1): typing `@` offers the same rows; choosing one replaces the `@`, dismissing leaves it as typed.
    @Test
    fun typingTheTriggerOffersTheSameRows() {
        val recipe = bread(Step(0, "Всыпьте "))
        open(recipe.id)
        placeCursor(0, 8)
        compose.onNodeWithTag(EditorTags.stepText(0)).performTextInput("@")
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.stepOffer(0, 0)), PATIENCE_MS)
        assertEquals((0..2).map { EditorTags.stepOffer(0, it) }, offered(0))
        pick(0, 0)
        assertEquals("Всыпьте 200 г", stepShown(0))
        assertEquals(Step(0, "Всыпьте ", listOf(IngredientReference(0, 8))), stored(recipe.id).steps.single())

        compose.onNodeWithTag(EditorTags.stepText(0)).performTextInput(" муки, @")
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.stepOffer(0, 0)), PATIENCE_MS)
        pressBackOnTheOffer()
        compose.waitUntilDoesNotExist(hasTestTag(EditorTags.stepOffer(0, 0)), PATIENCE_MS)
        compose.waitForIdle()
        assertEquals("Всыпьте 200 г муки, @", stepShown(0))
        assertEquals(Step(0, "Всыпьте  муки, @", listOf(IngredientReference(0, 8))), stored(recipe.id).steps.single())
    }

    // FLOW-031 (4): the cursor does not land inside a reference, and deleting into it removes it whole, leaving the
    // text around it as typed.
    @Test
    fun aReferenceIsOneUnit() {
        val recipe = bread(Step(0, "Всыпьте  муки", listOf(IngredientReference(0, 8))))
        open(recipe.id)
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.stepText(0)), PATIENCE_MS)
        assertEquals("Всыпьте 200 г муки", stepShown(0))
        placeCursor(0, 9)
        assertEquals(TextRange(8), selection(0))
        placeCursor(0, 12)
        assertEquals(TextRange(13), selection(0))

        // Typing before and after it keeps it at its place in the sentence.
        placeCursor(0, 0)
        compose.onNodeWithTag(EditorTags.stepText(0)).performTextInput("Теперь ")
        compose.waitForIdle()
        compose.onNodeWithTag(EditorTags.stepText(0)).performTextInputSelection(TextRange(stepShown(0).length))
        compose.onNodeWithTag(EditorTags.stepText(0)).performTextInput(" высшего сорта")
        compose.waitForIdle()
        assertEquals("Теперь Всыпьте 200 г муки высшего сорта", stepShown(0))
        assertEquals(
            Step(0, "Теперь Всыпьте  муки высшего сорта", listOf(IngredientReference(0, 15))),
            stored(recipe.id).steps.single(),
        )

        // A backspace right after it takes the whole reference, and nothing else.
        compose.onNodeWithTag(EditorTags.stepText(0)).performTextInputSelection(TextRange(20))
        compose.onNodeWithTag(EditorTags.stepText(0)).performKeyInput { pressKey(Key.Backspace) }
        compose.waitForIdle()
        // Single spacing is left where it stood (T-074).
        assertEquals("Теперь Всыпьте муки высшего сорта", stepShown(0))
        assertEquals(Step(0, "Теперь Всыпьте муки высшего сорта"), stored(recipe.id).steps.single())
        assertEquals(TextRange(15), selection(0))
    }

    // FLOW-031 (5, 6): a referenced row's quantity and unit change what its references show; "to taste" and back
    // keeps every reference; deleting the row drops its references and leaves the text.
    @Test
    fun referencesFollowTheirIngredient() {
        val recipe = bread(
            Step(0, "Всыпьте  муки", listOf(IngredientReference(0, 8))),
            Step(1, "Остаток муки:  и соль ", listOf(IngredientReference(0, 14), IngredientReference(2, 22))),
        )
        open(recipe.id)
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.stepText(1)), PATIENCE_MS)
        compose.openIngredient(0)
        compose.onNodeWithTag(EditorTags.ingredientQuantity(0)).performScrollTo().performTextReplacement("1500")
        compose.waitForIdle()
        assertEquals("Всыпьте 1,5 кг муки", stepShown(0))
        assertEquals("Остаток муки: 1,5 кг и соль по вкусу", stepShown(1))

        chooseUnit(0, QuantityUnit.TO_TASTE)
        assertEquals("Всыпьте по вкусу муки", stepShown(0))
        chooseUnit(0, QuantityUnit.KILOGRAM)
        assertEquals("Всыпьте ${ReferenceText.PLACEHOLDER} кг муки", stepShown(0))
        compose.openIngredient(0)
        compose.onNodeWithTag(EditorTags.ingredientQuantity(0)).performScrollTo().performTextReplacement("2")
        compose.waitForIdle()
        assertEquals("Всыпьте 2 кг муки", stepShown(0))
        assertEquals(listOf(IngredientReference(0, 8)), stored(recipe.id).steps[0].ingredientReferences)

        compose.chooseInSheet(EditorTags.ingredientRemove(0))
        compose.waitUntil(PATIENCE_MS) { !compose.ingredientSheetOpen() }
        compose.waitForIdle()
        // Single spacing is left where each stood (T-074).
        assertEquals("Всыпьте муки", stepShown(0))
        assertEquals("Остаток муки: и соль по вкусу", stepShown(1))
        val steps = stored(recipe.id).steps
        assertEquals(listOf(Step(0, "Всыпьте муки"), Step(1, "Остаток муки: и соль ", listOf(IngredientReference(1, 21)))), steps)
    }

    /** The system back, pressed while the offer is open: it goes to the offer's own window, the topmost. */
    private fun pressBackOnTheOffer() {
        val global = Class.forName("android.view.WindowManagerGlobal").getMethod("getInstance").invoke(null)
        @Suppress("UNCHECKED_CAST")
        val views = global.javaClass.getDeclaredField("mViews").apply { isAccessible = true }.get(global) as List<android.view.View>
        val popup = views.last()
        compose.runOnUiThread {
            popup.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_BACK))
            popup.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_BACK))
        }
    }

    private fun chooseUnit(position: Int, unit: QuantityUnit) {
        compose.openIngredient(position)
        compose.onNodeWithTag(EditorTags.ingredientUnit(position)).performScrollTo().performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.unitOption(unit)), PATIENCE_MS)
        compose.onNodeWithTag(EditorTags.unitOption(unit)).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntilDoesNotExist(hasTestTag(EditorTags.unitOption(unit)), PATIENCE_MS)
        compose.waitForIdle()
    }

    // Moving and inserting rows keeps each reference on its ingredient (DOM-007, FLOW-025 (3)), as the field shows.
    @Test
    fun movingAndInsertingRowsKeepsEachReferenceOnItsIngredient() {
        val recipe = bread(Step(0, "Всыпьте , добавьте ", listOf(IngredientReference(0, 8), IngredientReference(2, 19))))
        open(recipe.id)
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.stepText(0)), PATIENCE_MS)
        compose.closeIngredient()
        compose.onIngredient(0, RowAction.INSERT_ABOVE)
        // The row put above opens in the sheet; named there, it stays once the sheet is closed.
        compose.onNodeWithTag(EditorTags.ingredientName(0)).performTextReplacement("изюм")
        compose.waitForIdle()
        compose.closeIngredient()
        compose.onIngredient(3, RowAction.MOVE_UP)
        compose.closeIngredient()
        assertEquals("Всыпьте 200 г, добавьте по вкусу", stepShown(0))
        assertEquals(listOf(IngredientReference(1, 8), IngredientReference(2, 19)), stored(recipe.id).steps.single().ingredientReferences)
    }

    // References survive leaving the editor and the book being read again; a fork's point at its own ingredients.
    @Test
    fun referencesAreReadBackAndAForksPointAtItsOwnIngredients() {
        val recipe = bread(Step(0, "Всыпьте  муки"))
        open(recipe.id)
        placeCursor(0, 8)
        openOffer(0)
        pick(0, 0)
        compose.closeIngredient()
        closeEditor()
        val read = stored(recipe.id)
        assertEquals(listOf(IngredientReference(0, 8)), read.steps.single().ingredientReferences)
        open(recipe.id)
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.stepText(0)), PATIENCE_MS)
        assertEquals("Всыпьте 200 г муки", stepShown(0))
        compose.closeIngredient()
        closeEditor()

        val fork = runBlocking { forkRecipe(recipes, active, recipe.id, created.plusSeconds(30)) }!!
        open(fork)
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.stepText(0)), PATIENCE_MS)
        assertEquals("Всыпьте 200 г муки", stepShown(0))
        compose.openIngredient(0)
        compose.onNodeWithTag(EditorTags.ingredientQuantity(0)).performScrollTo().performTextReplacement("250")
        compose.waitForIdle()
        assertEquals("Всыпьте 250 г муки", stepShown(0))
        compose.closeIngredient()
        closeEditor()
        // The parent's step still reads its own flour.
        assertEquals(Rational.of(200), stored(recipe.id).ingredients[0].quantity)
        assertEquals(listOf(IngredientReference(0, 8)), stored(fork).steps.single().ingredientReferences)
        assertEquals(Rational.of(250), stored(fork).ingredients[0].quantity)
    }

    // A frozen recipe shows its steps with references rendered, and nothing to change them with.
    @Test
    fun aFrozenRecipeShowsItsReferencesReadOnly() {
        val recipe = seed(
            Recipe.create(created, null).copy(
                ingredients = listOf(flour, milk, salt),
                steps = listOf(Step(0, "Всыпьте  муки,  молока и ", listOf(IngredientReference(0, 8), IngredientReference(1, 15), IngredientReference(2, 25)), Timer(5.minutes))),
            ).inState(RecipeState.PUBLISHED),
        )
        open(recipe.id)
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.step(0)), PATIENCE_MS)
        val text = "Всыпьте 200 г муки, ${ReferenceText.PLACEHOLDER} мл молока и по вкусу"
        val shownText = compose.onNodeWithTag(EditorTags.stepText(0), useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsProperties.Text].map { it.text }
        assertEquals(listOf(text), shownText)
        // Set apart as the field sets them: all three references, the text between them plain.
        val laid = laidOut(EditorTags.stepText(0), unmerged = true)
        assertEquals(text, laid.text)
        assertSetApart(laid, 8, 13, typed = listOf(7, 13, 18))
        assertSetApart(laid, 20, 26, typed = listOf(19, 26))
        assertSetApart(laid, text.length - 8, text.length, typed = listOf(text.length - 9))
        compose.onNodeWithTag(EditorTags.stepInsert(0)).assertDoesNotExist()
        assertFalse(compose.onNodeWithTag(EditorTags.stepText(0), useUnmergedTree = true).fetchSemanticsNode().config.contains(SemanticsActions.SetText))
        val timerName = compose.onNodeWithTag(EditorTags.timerName(0), useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsProperties.Text].single().text
        // The name holds each reference together with no-break spaces, as the card shows it; the field, plain ones.
        assertTrue(timerName, timerName.replace('\u00A0', ' ').contains(text))
    }

    // FLOW-016 (8) at the authored scale: the derived timer name reads a reference as the editor shows it.
    @Test
    fun theDerivedTimerNameReadsAReferenceAsTheEditorShowsIt() {
        val recipe = bread(Step(0, "Всыпьте  муки", listOf(IngredientReference(0, 8)), Timer(5.minutes)))
        open(recipe.id)
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.timerName(0)), PATIENCE_MS)
        assertEquals("Всыпьте 200\u00A0г муки", fieldText(EditorTags.timerName(0)))
        compose.openIngredient(0)
        compose.onNodeWithTag(EditorTags.ingredientQuantity(0)).performScrollTo().performTextReplacement("300")
        compose.waitForIdle()
        assertEquals("Всыпьте 300\u00A0г муки", fieldText(EditorTags.timerName(0)))
    }

    // T-191: a derived name holds its references together with no-break spaces, but a name the author gives from it
    // keeps ordinary ones — stored, and so exported, as typed text always is.
    @Test
    fun aNameGivenFromTheDerivedOneKeepsOrdinarySpaces() {
        val recipe = bread(Step(0, "Всыпьте  муки", listOf(IngredientReference(0, 8)), Timer(5.minutes)))
        open(recipe.id)
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.timerName(0)), PATIENCE_MS)
        compose.onNodeWithTag(EditorTags.timerName(0)).performScrollTo().performClick()
        compose.onNodeWithTag(EditorTags.timerName(0)).performTextInput("!")
        compose.waitUntil(PATIENCE_MS) { stored(recipe.id).steps.single().timers.single().label != null }
        val label = stored(recipe.id).steps.single().timers.single().label!!
        assertFalse(label, '\u00A0' in label)
        assertEquals("Всыпьте 200 г муки", label.replace("!", ""))
        assertFalse('\u00A0' in fieldText(EditorTags.timerName(0)))
    }

    // A recipe with no ingredient yet: the control is there, and says there is nothing to insert.
    @Test
    fun aRecipeWithNoIngredientsOffersNothing() {
        val recipe = seed(Recipe.create(created, null).copy(steps = listOf(Step(0, "Смешайте."))))
        open(recipe.id)
        compose.onNodeWithTag(EditorTags.stepInsert(0)).performScrollTo().performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.stepOfferNone(0)), PATIENCE_MS)
        assertEquals(listOf(string(R.string.editor_step_insert_ingredient_none)), compose.textsOf(EditorTags.stepOfferNone(0)))
    }

    // Undo never brings a removed reference back as typed text: the step's field takes no undo.
    @Test
    fun undoNeverBringsAReferenceBackAsTypedText() {
        val recipe = bread(Step(0, "Всыпьте  муки", listOf(IngredientReference(0, 8))))
        open(recipe.id)
        placeCursor(0, 13)
        compose.onNodeWithTag(EditorTags.stepText(0)).performKeyInput { pressKey(Key.Backspace) }
        compose.waitForIdle()
        assertEquals("Всыпьте муки", stepShown(0))
        compose.onNodeWithTag(EditorTags.stepText(0)).performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.Z) } }
        compose.waitForIdle()
        compose.onNodeWithTag(EditorTags.stepText(0)).performKeyInput { withKeyDown(Key.CtrlLeft) { withKeyDown(Key.ShiftLeft) { pressKey(Key.Z) } } }
        compose.waitForIdle()
        assertEquals("Всыпьте муки", stepShown(0))
        assertEquals(Step(0, "Всыпьте муки"), stored(recipe.id).steps.single())
    }

    // A removed reference's quantity typed back as plain text after the screen redrew: every character is kept.
    @Test
    fun theQuantityTypedBackWhereAReferenceWasRemovedIsKeptWhole() {
        val recipe = bread(Step(0, "Всыпьте  муки", listOf(IngredientReference(0, 8))))
        open(recipe.id)
        placeCursor(0, 13)
        compose.onNodeWithTag(EditorTags.stepText(0)).performKeyInput { pressKey(Key.Backspace) }
        compose.waitForIdle()
        assertEquals("Всыпьте муки", stepShown(0))
        placeCursor(0, 8)
        compose.onNodeWithTag(EditorTags.stepText(0)).performTextInput("200 г ")
        compose.waitForIdle()
        assertEquals("Всыпьте 200 г муки", stepShown(0))
        assertEquals(Step(0, "Всыпьте 200 г муки"), stored(recipe.id).steps.single())
    }

    // Backspacing the only space between two references removes the one before the cursor; the keyboard is not ignored.
    @Test
    fun backspacingTheSpaceBetweenTwoReferences() {
        val recipe = bread(Step(0, "Всыпьте   и ", listOf(IngredientReference(0, 8), IngredientReference(2, 9))))
        open(recipe.id)
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.stepText(0)), PATIENCE_MS)
        assertEquals("Всыпьте 200 г по вкусу и ", stepShown(0))
        placeCursor(0, 14)
        compose.onNodeWithTag(EditorTags.stepText(0)).performKeyInput { pressKey(Key.Backspace) }
        compose.waitForIdle()
        assertEquals("Всыпьте по вкусу и ", stepShown(0))
        assertEquals(Step(0, "Всыпьте  и ", listOf(IngredientReference(2, 8))), stored(recipe.id).steps.single())
        assertEquals(TextRange(8), selection(0))
    }

    private val keyboard: ShadowInputMethodManager
        get() = shadowOf(context.getSystemService(InputMethodManager::class.java))

    /** The soft keyboard put away, as the offer's window taking focus puts it away on a device. */
    private fun hideKeyboard() {
        compose.runOnUiThread {
            context.getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(null, 0)
        }
        assertFalse(keyboard.isSoftInputVisible)
    }

    /** The step's field has focus again, the keyboard shown, the cursor at [cursor]. */
    private fun assertTypingGoesOn(position: Int, cursor: Int) {
        compose.waitUntil(PATIENCE_MS) { node(EditorTags.stepText(position)).config[SemanticsProperties.Focused] }
        compose.onNodeWithTag(EditorTags.stepText(position)).assertIsFocused()
        compose.waitUntil(PATIENCE_MS) { keyboard.isSoftInputVisible }
        assertEquals(TextRange(cursor), selection(position))
    }

    // T-074: choosing from the offer the step's control opened leaves the field focused, the keyboard open and the
    // cursor after the reference and the space typed with it; typing goes on there.
    @Test
    fun choosingFromTheControlKeepsTypingGoingAfterTheReference() {
        val recipe = bread(Step(0, "Всыпьтемуки"))
        open(recipe.id)
        placeCursor(0, 7)
        openOffer(0)
        hideKeyboard()
        pick(0, 0)
        assertEquals("Всыпьте 200 г муки", stepShown(0))
        assertEquals(Step(0, "Всыпьте  муки", listOf(IngredientReference(0, 8))), stored(recipe.id).steps.single())
        assertTypingGoesOn(0, 14)
        compose.onNodeWithTag(EditorTags.stepText(0)).performTextInput("пшеничной ")
        compose.waitForIdle()
        assertEquals("Всыпьте 200 г пшеничной муки", stepShown(0))
    }

    // T-074: the same after `@`, and dismissing the offer `@` opened returns focus and keyboard to the field.
    @Test
    fun choosingOrDismissingAfterTheTriggerKeepsTypingGoing() {
        val recipe = bread(Step(0, "Всыпьте"))
        open(recipe.id)
        placeCursor(0, 7)
        compose.onNodeWithTag(EditorTags.stepText(0)).performTextInput("@")
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.stepOffer(0, 0)), PATIENCE_MS)
        hideKeyboard()
        pick(0, 0)
        assertEquals("Всыпьте 200 г", stepShown(0))
        assertEquals(Step(0, "Всыпьте ", listOf(IngredientReference(0, 8))), stored(recipe.id).steps.single())
        assertTypingGoesOn(0, 13)

        compose.onNodeWithTag(EditorTags.stepText(0)).performTextInput(" муки, @")
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.stepOffer(0, 0)), PATIENCE_MS)
        hideKeyboard()
        pressBackOnTheOffer()
        compose.waitUntilDoesNotExist(hasTestTag(EditorTags.stepOffer(0, 0)), PATIENCE_MS)
        assertTypingGoesOn(0, "Всыпьте 200 г муки, @".length)
    }

    // T-074: a reference deleted into by the keyboard leaves single spacing — before punctuation, the space before it
    // goes; at the end of the text, its neighbouring space.
    @Test
    fun deletingIntoAReferenceLeavesSingleSpacing() {
        val recipe = bread(
            Step(0, "Всыпьте  муки", listOf(IngredientReference(0, 8))),
            Step(1, "добавьте .", listOf(IngredientReference(2, 9))),
            Step(2, "Посолите ", listOf(IngredientReference(2, 9))),
        )
        open(recipe.id)
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.stepText(2)), PATIENCE_MS)
        placeCursor(0, 13)
        compose.onNodeWithTag(EditorTags.stepText(0)).performKeyInput { pressKey(Key.Backspace) }
        compose.waitForIdle()
        assertEquals("Всыпьте муки", stepShown(0))
        assertEquals(TextRange(8), selection(0))
        placeCursor(1, 9)
        compose.onNodeWithTag(EditorTags.stepText(1)).performKeyInput { pressKey(Key.Delete) }
        compose.waitForIdle()
        assertEquals("добавьте.", stepShown(1))
        assertEquals(TextRange(8), selection(1))
        placeCursor(2, 17)
        compose.onNodeWithTag(EditorTags.stepText(2)).performKeyInput { pressKey(Key.Backspace) }
        compose.waitForIdle()
        assertEquals("Посолите", stepShown(2))
        assertEquals(
            listOf(Step(0, "Всыпьте муки"), Step(1, "добавьте."), Step(2, "Посолите")),
            stored(recipe.id).steps,
        )
    }
}
