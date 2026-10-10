package app.recipefork.app.editor

import android.content.Context
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.R
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.card.ingredientLine
import app.recipefork.app.library.LibrarySession
import app.recipefork.app.library.LibraryTags
import app.recipefork.app.library.LibraryView
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.ui.AccessibilityChecks
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.RecipeLanguage
import app.recipefork.core.recipe.RecipeState
import app.recipefork.core.recipe.SentStatus
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.InterfaceLanguage
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
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * An ingredient row reads as finished text and opens in a sheet (T-209): the row is one line written as the card writes
 * it, with the marks its fields would carry and its drag handle, and no fields; a tap opens a bottom sheet holding its
 * fields, whose edits apply as typed whichever way it is closed; the sheet outlives the activity being recreated, goes
 * with its row, and a frozen recipe opens nothing.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class IngredientSheetTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val device = TestDevice(context)
    private val active = ActiveBook(BookName.Pool, book.database)
    private val recipes = RecipeRepository(active)
    private val session = LibrarySession(SavedStateHandle())
    private val created = Instant.parse("2026-10-10T12:00:00Z")
    private val opened = mutableStateOf<RecipeId?>(null)
    private val restoration = StateRestorationTester(compose)

    @Before
    fun setUp() {
        compose.registerIdlingResource(book.idling)
        compose.registerIdlingResource(device.idling)
        runBlocking { device.database.dictionaryDao().replace(TEST_INGREDIENTS) }
    }

    @After
    fun close() {
        compose.unregisterIdlingResource(book.idling)
        compose.unregisterIdlingResource(device.idling)
        book.close()
        device.close()
    }

    /** Milk from the dictionary with a note, onion in the author's own words, salt to taste, and a row holding nothing. */
    private fun draft(state: RecipeState = RecipeState.LOCAL) = Recipe.create(created, owner = null).copy(
        title = "Soup",
        language = RecipeLanguage.EN,
        servings = 2,
        ingredients = listOf(
            Ingredient(0, 1, null, null, UnitType.EXACT, QuantityUnit.MILLILITRE, Rational.of(500), note = "warm"),
            Ingredient(1, null, null, "onion", UnitType.PIECE, QuantityUnit.PIECE, Rational.of(2), divisible = true),
            Ingredient(2, null, null, "salt", UnitType.TASTE_UNMEASURED, null, null),
            Ingredient(3, null, freeTextName = null, unitType = UnitType.EXACT, unit = QuantityUnit.GRAM, quantity = null),
        ),
        sent = if (state == RecipeState.LOCAL) SentStatus.NEVER else SentStatus.SENT,
    ).let { if (state == RecipeState.LOCAL) it else it.inState(state) }

    private fun open(recipe: Recipe, restorable: Boolean = false): RecipeId {
        runBlocking { recipes.save(recipe) }
        val environment = EditorEnvironment(active, device.catalog(context)) { created.plusSeconds(60) }
        val content: @androidx.compose.runtime.Composable () -> Unit = { MaterialTheme { LibraryOrEditor(session, recipes, environment, opened) } }
        if (restorable) restoration.setContent(content) else compose.setContent(content)
        compose.onNodeWithTag(LibraryTags.tab(LibraryView.OWN)).performClick()
        compose.waitForIdle()
        compose.runOnIdle { opened.value = recipe.id }
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.ingredient(recipe.ingredients.lastIndex)), PATIENCE_MS)
        // The dictionary is read: the entry's row reads its label.
        compose.waitUntil(PATIENCE_MS) { compose.ingredientLine(0).contains(context.getString(R.string.editor_ingredient_unnamed)).not() }
        return recipe.id
    }

    private fun stored(id: RecipeId): Recipe = runBlocking { recipes.get(id) }!!

    private fun string(id: Int, vararg args: Any): String = context.getString(id, *args)

    private fun exists(tag: String): Boolean = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun fieldText(tag: String): String = compose.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.EditableText].text

    private fun isFocused(tag: String): Boolean =
        compose.onNodeWithTag(tag).fetchSemanticsNode().config.getOrElse(SemanticsProperties.Focused) { false }

    private fun awaitFocused(tag: String) = compose.waitUntil(PATIENCE_MS) { exists(tag) && isFocused(tag) }

    private fun hasSetTextAction() = SemanticsMatcher.keyIsDefined(SemanticsActions.SetText)

    /** The line the card writes for [row]: its amount, then its name and note, as the card's list shows them. */
    private fun cardLine(row: Ingredient, language: InterfaceLanguage): String {
        val entries = runBlocking { device.database.dictionaryDao().ingredients() }.associateBy { it.id }
        val line = ingredientLine(row, entries, language)
        return listOfNotNull(line.quantity, line.name).joinToString(" ") + (line.note?.let { " — $it" } ?: "")
    }

    /** Hands a press of the system's back to the topmost window: the sheet's own, in front of the editor. */
    private fun pressBack() {
        val window = windowRoots().last()
        compose.runOnUiThread {
            window.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
            window.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))
        }
    }

    private fun awaitClosed() {
        compose.waitUntil(PATIENCE_MS) { !compose.ingredientSheetOpen() }
        compose.waitForIdle()
    }

    // Scenario 1: each row reads as one line, as the card writes it — amount, unit, name, note — with no fields on it; its
    // drag handle stays; an empty row reads "New ingredient".
    @Test
    fun eachRowReadsAsTheCardsLineWithNoFields() = rowsReadAsLines(InterfaceLanguage.ENGLISH, listOf("500 ml milk — warm", "2 pc onion", "to taste salt", "New ingredient"))

    @Test
    @Config(qualifiers = "ru")
    fun eachRowReadsAsTheCardsLineWithNoFieldsInRussian() = rowsReadAsLines(InterfaceLanguage.RUSSIAN, listOf("500 мл молоко — warm", "2 шт onion", "по вкусу salt", "Новый ингредиент"))

    private fun rowsReadAsLines(language: InterfaceLanguage, expected: List<String>) {
        val recipe = draft()
        open(recipe)
        assertEquals(expected, (0..3).map { compose.ingredientLine(it) })
        // The editor's line is the card's own, so the two cannot drift apart.
        (0..2).forEach { assertEquals(cardLine(recipe.ingredients[it], language), compose.ingredientLine(it)) }
        (0..3).forEach { position ->
            val inRow = hasAnyAncestor(hasTestTag(EditorTags.ingredient(position)))
            assertEquals("row $position holds a field", 0, compose.onAllNodes(inRow and hasSetTextAction(), useUnmergedTree = true).fetchSemanticsNodes().size)
            assertTrue("row $position has no handle", exists(EditorTags.ingredientHandle(position)))
            // The row's ⋮ and Remove stay for now.
            assertTrue(exists(EditorTags.ingredientRemove(position)))
            assertTrue(exists(EditorTags.ingredientMenu(position)))
        }
        assertFalse(compose.ingredientSheetOpen())
    }

    // The marks the row's fields carried still show on the row: a name in the author's own words, a quantity its field
    // does not hold, a note over its limit once left.
    @Test
    fun theRowCarriesTheMarksOfItsFields() {
        val id = open(
            draft().copy(
                ingredients = listOf(
                    Ingredient(0, 1, null, null, UnitType.EXACT, QuantityUnit.GRAM, null, enteredQuantity = "1 500"),
                    Ingredient(1, null, null, "onion", UnitType.EXACT, QuantityUnit.GRAM, Rational.of(3, 2)),
                ),
            ),
        )
        assertFalse(exists(IngredientSheetTags.freeText(0)))
        assertEquals(listOf(string(R.string.editor_ingredient_free_text_mark)), compose.textsOf(IngredientSheetTags.freeText(1)))
        // What was typed stays in sight, and the row says what the field accepts.
        assertEquals("1 500 g milk", compose.ingredientLine(0))
        assertEquals(listOf(string(R.string.editor_quantity_separators)), compose.textsOf(IngredientSheetTags.problem(0)))
        assertEquals("1.5 g onion", compose.ingredientLine(1))
        assertEquals(listOf(string(R.string.editor_quantity_accepts_grams)), compose.textsOf(IngredientSheetTags.problem(1)))
        // A note over 60 characters is marked once its field is left — in the sheet, and on the row once it is closed.
        assertFalse(exists(IngredientSheetTags.noteMark(1)))
        compose.openIngredient(1)
        compose.onNodeWithTag(IngredientNoteTags.field(1)).performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithTag(IngredientNoteTags.field(1)).performTextReplacement("x".repeat(61))
        compose.onNodeWithTag(EditorTags.ingredientName(1)).performSemanticsAction(SemanticsActions.RequestFocus)
        compose.waitUntil(PATIENCE_MS) { exists(IngredientNoteTags.mark(1)) }
        compose.closeIngredient()
        compose.waitUntil(PATIENCE_MS) { exists(IngredientSheetTags.noteMark(1)) }
        assertEquals("x".repeat(61), stored(id).ingredients[1].note)
    }

    // Scenario 2: a tap on the line — one target at least 48 dp tall, named "Edit ingredient" — opens the sheet with the
    // row's fields in the row's order; edits apply as typed, and closed by Done the row reads the new line.
    @Test
    fun aTapOpensTheSheetWithTheFieldsAndDoneKeepsTheEdits() = tapOpensTheSheet()

    @Test
    @Config(qualifiers = "ru")
    fun aTapOpensTheSheetWithTheFieldsAndDoneKeepsTheEditsInRussian() = tapOpensTheSheet()

    private fun tapOpensTheSheet() {
        val id = open(draft())
        val line = compose.onNodeWithTag(IngredientSheetTags.line(1)).fetchSemanticsNode()
        assertEquals(string(R.string.editor_ingredient_edit), line.config[SemanticsActions.OnClick].label)
        assertTrue(line.size.height >= 48 * compose.density.density - 0.5f)
        compose.openIngredient(1)
        val sheet = hasAnyAncestor(hasTestTag(IngredientSheetTags.SHEET))
        val fields = compose.onAllNodes(sheet and hasSetTextAction()).fetchSemanticsNodes().map { it.config[SemanticsProperties.TestTag] }
        assertEquals(listOf(EditorTags.ingredientName(1), IngredientNoteTags.field(1), EditorTags.ingredientQuantity(1)), fields)
        val interactive = compose.onAllNodes(sheet and AccessibilityChecks.isInteractive).fetchSemanticsNodes()
            .mapNotNull { it.config.getOrElseNullable(SemanticsProperties.TestTag) { null } }
        assertEquals(
            listOf(
                IngredientSheetTags.DONE, EditorTags.ingredientName(1), IngredientNoteTags.field(1), EditorTags.ingredientQuantity(1),
                EditorTags.ingredientUnit(1), EditorTags.divisible(1, true), EditorTags.divisible(1, false),
            ),
            interactive,
        )
        // A named row opens with nothing focused.
        assertFalse(isFocused(EditorTags.ingredientName(1)))
        compose.onNodeWithTag(IngredientNoteTags.field(1)).performTextReplacement("sliced")
        compose.onNodeWithTag(EditorTags.ingredientQuantity(1)).performTextReplacement("3")
        compose.waitForIdle()
        // Applied as typed: the book holds it with the sheet still open.
        assertEquals("sliced", stored(id).ingredients[1].note)
        assertEquals(Rational.of(3), stored(id).ingredients[1].quantity)
        compose.closeIngredient()
        assertEquals(cardLine(stored(id).ingredients[1], language()), compose.ingredientLine(1))
        assertTrue(compose.ingredientLine(1).endsWith(" — sliced"))
        assertTrue(compose.ingredientLine(1).startsWith("3 "))
    }

    private fun language() = if (context.resources.configuration.locales[0].language == "ru") InterfaceLanguage.RUSSIAN else InterfaceLanguage.ENGLISH

    // Closed by the system's back or by a swipe down, the sheet keeps what was typed, the name settled as on leaving its
    // field: exactly an entry's label names the row by the entry.
    @Test
    fun backOrASwipeDownKeepsTheEdits() {
        val id = open(draft())
        compose.openIngredient(1)
        compose.onNodeWithTag(EditorTags.ingredientName(1)).performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithTag(EditorTags.ingredientName(1)).performTextReplacement("turmeric")
        compose.waitForIdle()
        pressBack()
        awaitClosed()
        assertEquals(20L, stored(id).ingredients[1].dictionaryEntry)
        assertTrue(compose.ingredientLine(1).endsWith("turmeric"))

        compose.openIngredient(2)
        compose.onNodeWithTag(IngredientNoteTags.field(2)).performTextReplacement("flaked")
        compose.waitForIdle()
        compose.onNodeWithTag(IngredientSheetTags.SHEET).performTouchInput { swipeDown() }
        awaitClosed()
        assertEquals("flaked", stored(id).ingredients[2].note)
        assertEquals("to taste salt — flaked", compose.ingredientLine(2))
    }

    // The keyboard goes name → note → quantity → the unit's menu inside the sheet; a unit picked from there closes it.
    // A row with no name opens with its name focused and the keyboard up.
    @Test
    fun nextGoesThroughTheSheetAndTheUnitClosesIt() {
        val id = open(draft())
        compose.onNodeWithTag(IngredientSheetTags.line(3)).performScrollTo().performClick()
        awaitFocused(EditorTags.ingredientName(3))
        compose.onNodeWithTag(EditorTags.ingredientName(3)).performTextInput("Flour")
        assertEquals(EditorInfo.IME_ACTION_NEXT, compose.keyboardAskedFor().imeOptions and EditorInfo.IME_MASK_ACTION)
        compose.onNodeWithTag(EditorTags.ingredientName(3)).performImeAction()
        awaitFocused(IngredientNoteTags.field(3))
        assertEquals(EditorInfo.IME_ACTION_NEXT, compose.keyboardAskedFor().imeOptions and EditorInfo.IME_MASK_ACTION)
        compose.onNodeWithTag(IngredientNoteTags.field(3)).performImeAction()
        awaitFocused(EditorTags.ingredientQuantity(3))
        assertEquals(EditorInfo.IME_ACTION_NEXT, compose.keyboardAskedFor().imeOptions and EditorInfo.IME_MASK_ACTION)
        compose.onNodeWithTag(EditorTags.ingredientQuantity(3)).performTextInput("200")
        compose.onNodeWithTag(EditorTags.ingredientQuantity(3)).performImeAction()
        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.unitOption(QuantityUnit.KILOGRAM)) }
        compose.onNodeWithTag(EditorTags.unitOption(QuantityUnit.MILLILITRE)).performSemanticsAction(SemanticsActions.OnClick)
        awaitClosed()
        assertEquals("200 ml Flour", compose.ingredientLine(3))
        assertEquals(QuantityUnit.MILLILITRE, stored(id).ingredients[3].unit)

        // A piece picked by Next asks whether it can be halved: the sheet stays for the answer.
        compose.openIngredient(3)
        compose.onNodeWithTag(EditorTags.ingredientQuantity(3)).performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithTag(EditorTags.ingredientQuantity(3)).performImeAction()
        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.unitOption(QuantityUnit.PIECE)) }
        compose.onNodeWithTag(EditorTags.unitOption(QuantityUnit.PIECE)).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.divisibility(3)) }
        assertTrue(compose.ingredientSheetOpen())
        compose.closeIngredient()
    }

    // The sheet outlives the activity being recreated, open on its row with what was typed in it.
    @Test
    fun theOpenSheetSurvivesRecreation() {
        val id = open(draft(), restorable = true)
        compose.openIngredient(1)
        // "2.25" pieces is no value of the grid, and stays as typed.
        compose.onNodeWithTag(EditorTags.ingredientQuantity(1)).performTextReplacement("2.25")
        compose.waitForIdle()
        restoration.emulateSavedInstanceStateRestore()
        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.ingredientQuantity(1)) }
        assertTrue(compose.ingredientSheetOpen())
        assertEquals("onion", fieldText(EditorTags.ingredientName(1)))
        assertEquals("2.25", fieldText(EditorTags.ingredientQuantity(1)))
        compose.onNodeWithTag(EditorTags.ingredientQuantity(1)).performTextReplacement("4")
        compose.waitForIdle()
        assertEquals(Rational.of(4), stored(id).ingredients[1].quantity)
        compose.closeIngredient()
    }

    // A row moved while its sheet is open keeps it, at its new place; one deleted closes it, and Undo puts the row back.
    @Test
    fun aMovedRowKeepsItsSheetAndADeletedOneClosesIt() {
        val id = open(draft())
        compose.openIngredient(1)
        compose.onNodeWithTag(EditorTags.ingredientMenu(1)).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.ingredientAction(1, RowAction.MOVE_UP)) }
        compose.onNodeWithTag(EditorTags.ingredientAction(1, RowAction.MOVE_UP)).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.ingredientName(0)) }
        assertEquals("onion", fieldText(EditorTags.ingredientName(0)))
        assertFalse(exists(EditorTags.ingredientName(1)))
        compose.onNodeWithTag(IngredientNoteTags.field(0)).performTextReplacement("diced")
        compose.waitForIdle()
        assertEquals(listOf("onion", null), stored(id).ingredients.take(2).map { it.freeTextName })
        assertEquals("diced", stored(id).ingredients[0].note)

        compose.onNodeWithTag(EditorTags.ingredientRemove(0)).performSemanticsAction(SemanticsActions.OnClick)
        awaitClosed()
        assertEquals(3, stored(id).ingredients.size)
        compose.awaitStatement(string(R.string.editor_ingredient_removed, 1))
        val undo = hasAnyAncestor(hasTestTag(EditorTags.STATEMENT)) and SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick)
        compose.onNode(undo, useUnmergedTree = true).performClick()
        compose.waitUntil(PATIENCE_MS) { stored(id).ingredients.size == 4 }
        assertEquals("2 pc onion — diced", compose.ingredientLine(0))
        assertFalse(compose.ingredientSheetOpen())
    }

    // A frozen recipe's rows read as the same lines and open nothing.
    @Test
    fun aFrozenRecipesRowsReadAsTextAndOpenNothing() {
        val recipe = draft(RecipeState.PUBLISHED)
        open(recipe)
        assertEquals(listOf("500 ml milk — warm", "2 pc onion", "to taste salt"), (0..2).map { compose.ingredientLine(it) })
        (0..3).forEach { position ->
            val line = compose.onNodeWithTag(IngredientSheetTags.line(position), useUnmergedTree = true).fetchSemanticsNode()
            assertFalse("row $position opens", SemanticsActions.OnClick in line.config)
            assertFalse(exists(EditorTags.ingredientHandle(position)))
        }
        compose.onNodeWithTag(IngredientSheetTags.line(0), useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertFalse(compose.ingredientSheetOpen())
        assertEquals(0, compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size)
    }
}
