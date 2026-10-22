package app.recipefork.app.editor

import android.content.Context
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.library.LibrarySession
import app.recipefork.app.library.LibraryTags
import app.recipefork.app.library.LibraryView
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.ui.assertChangesGradually
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeId
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
import java.time.Instant

/**
 * Typing a recipe on the phone (T-143): what the editor's fields ask of the keyboard — sentence capitals, the Next that
 * goes through the form, the new-line key of the fields over several lines — and the row or step just added taking
 * the focus with the keyboard up.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class EditorKeyboardTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val device = TestDevice(context)
    private val active = ActiveBook(BookName.Pool, book.database)
    private val recipes = RecipeRepository(active)
    private val session = LibrarySession(SavedStateHandle())
    private val created = Instant.parse("2026-10-04T12:00:00Z")
    private val opened = mutableStateOf<RecipeId?>(null)

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

    private fun writeANewRecipe(): RecipeId {
        val environment = EditorEnvironment(active, device.catalog(context)) { created.plusSeconds(60) }
        compose.setContent { MaterialTheme { LibraryOrEditor(session, recipes, environment, opened) } }
        compose.onNodeWithTag(LibraryTags.tab(LibraryView.OWN)).performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.WRITE), PATIENCE_MS)
        compose.onNodeWithTag(LibraryTags.WRITE).performScrollTo().performClick()
        compose.onNodeWithTag(LibraryTags.FROM_SCRATCH).performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.FORM), PATIENCE_MS)
        return RecipeId(requireNotNull(session.editing.value))
    }

    private fun stored(id: RecipeId): Recipe = runBlocking { recipes.get(id) }!!

    private fun exists(tag: String): Boolean = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    private fun isFocused(tag: String): Boolean =
        compose.onNodeWithTag(tag).fetchSemanticsNode().config.getOrElse(SemanticsProperties.Focused) { false }

    private fun awaitFocused(tag: String) = compose.waitUntil(PATIENCE_MS) { exists(tag) && isFocused(tag) }

    private fun keyboard() = shadowOf(context.getSystemService(InputMethodManager::class.java))

    private fun keyboardShown(): Boolean = keyboard().isSoftInputVisible

    /**
     * The keyboard is up for the focused field: shown, as the shadow keyboard says for the editor's own window; for a
     * row's or a step's sheet, whose window the shadow does not follow, the sheet's field has started its input, which brings the
     * keyboard on a phone.
     */
    private fun keyboardUp(): Boolean {
        if (keyboardShown()) return true
        if (!compose.ingredientSheetOpen() && !compose.stepSheetOpen()) return false
        var taking = false
        compose.runOnUiThread { taking = windowRoots().last().let { it.hasFocus() && it.findFocus()?.onCheckIsTextEditor() == true } }
        return taking
    }

    /** The keyboard put away, as the system's back does, whatever field holds the focus. */
    private fun hideKeyboard() {
        compose.runOnUiThread {
            context.getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(compose.activity.window.decorView.windowToken, 0)
        }
        compose.waitUntil(PATIENCE_MS) { !keyboardShown() }
    }

    /** What the focused field asks the keyboard for: its input type and its action — in the editor or in a sheet. */
    private fun keyboardAskedFor(): EditorInfo = compose.keyboardAskedFor()

    private fun action(info: EditorInfo): Int = info.imeOptions and EditorInfo.IME_MASK_ACTION

    private fun capitalisesSentences(info: EditorInfo): Boolean = info.inputType and InputType.TYPE_TEXT_FLAG_CAP_SENTENCES != 0

    /** Taps the field [tag], scrolled to first, and waits for it to take the focus. */
    private fun enter(tag: String) {
        compose.reach(tag)
        compose.onNodeWithTag(tag).performScrollTo().performTouchInput { click() }
        awaitFocused(tag)
    }

    /**
     * Adds a row by "Add ingredient", which opens its sheet — any sheet open before is closed first — and names it there,
     * so that closing its sheet keeps it.
     */
    private fun addIngredient(): Int {
        compose.closeIngredient()
        val position = stored(RecipeId(requireNotNull(session.editing.value))).ingredients.size
        compose.onNodeWithTag(EditorTags.INGREDIENT_ADD).performScrollTo().performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.ingredientName(position)), PATIENCE_MS)
        awaitFocused(EditorTags.ingredientName(position))
        compose.onNodeWithTag(EditorTags.ingredientName(position)).performTextInput("Row ${position + 1}")
        compose.waitForIdle()
        return position
    }

    private fun addStep(): Int {
        compose.closeIngredient()
        val position = stored(RecipeId(requireNotNull(session.editing.value))).steps.size
        compose.closeStep()
        compose.onNodeWithTag(EditorTags.STEP_ADD).performScrollTo().performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.stepText(position)), PATIENCE_MS)
        return position
    }

    // Title, description, step text, ingredient names and timer names start a sentence with a capital letter.
    @Test
    fun theWrittenFieldsCapitaliseSentences() {
        writeANewRecipe()
        enter(EditorTags.TITLE)
        assertTrue("title", capitalisesSentences(keyboardAskedFor()))
        addIngredient()
        awaitFocused(EditorTags.ingredientName(0))
        assertTrue("ingredient name", capitalisesSentences(keyboardAskedFor()))
        addStep()
        awaitFocused(EditorTags.stepText(0))
        assertTrue("step text", capitalisesSentences(keyboardAskedFor()))
        // A timer, so that its name field shows.
        compose.inStep(0).onNodeWithTag(EditorTags.timerAttach(0)).performScrollTo().performTouchInput { click() }
        compose.openStep(0)
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.timerMinutes(0)), PATIENCE_MS)
        compose.inStep(0).onNodeWithTag(EditorTags.timerMinutes(0)).performScrollTo().performTextReplacement("5")
        compose.openStep(0)
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.timerName(0)), PATIENCE_MS)
        enter(EditorTags.timerName(0))
        assertTrue("timer name", capitalisesSentences(keyboardAskedFor()))
        enter(EditorTags.DESCRIPTION)
        assertTrue("description", capitalisesSentences(keyboardAskedFor()))
        // Numbers are not sentences.
        enter(EditorTags.SERVINGS)
        assertFalse("servings", capitalisesSentences(keyboardAskedFor()))
    }

    // The step's text and the description, over several lines, keep the keyboard's new-line key: no action replaces it.
    @Test
    fun theFieldsOverSeveralLinesKeepTheNewLineKey() {
        writeANewRecipe()
        addStep()
        awaitFocused(EditorTags.stepText(0))
        listOf(EditorTags.stepText(0), EditorTags.DESCRIPTION).forEach { tag ->
            enter(tag)
            val info = keyboardAskedFor()
            assertTrue(tag, info.inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0)
            assertTrue(tag, info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0)
        }
    }

    // Next goes title → serving count → finished weight (v0.44) → the first row, whose sheet opens with its name focused →
    // quantity → the unit's menu; a unit picked there goes on to the note (T-218), the last field, whose Done closes the
    // sheet. With no rows, the finished weight says Done, which closes the keyboard and adds no row. From the title it passes over the tags and the description between it and the serving
    // count (T-208): each keeps a key of its own — the tags' Done, the description's new line.
    @Test
    fun nextGoesThroughTheFirstRowsSheetAndTheNoteEndsIt() {
        val id = writeANewRecipe()
        // With no rows, the finished weight is the last field: Done; the serving count goes on to it.
        enter(EditorTags.SERVINGS)
        assertEquals(EditorInfo.IME_ACTION_NEXT, action(keyboardAskedFor()))
        enter(EditorTags.FINISHED_WEIGHT)
        assertEquals(EditorInfo.IME_ACTION_DONE, action(keyboardAskedFor()))
        compose.onNodeWithTag(EditorTags.FINISHED_WEIGHT).performImeAction()
        compose.waitUntil(PATIENCE_MS) { !isFocused(EditorTags.FINISHED_WEIGHT) }
        assertEquals(0, stored(id).ingredients.size)
        addIngredient()
        addIngredient()
        compose.closeIngredient()

        enter(EditorTags.TITLE)
        assertEquals(EditorInfo.IME_ACTION_NEXT, action(keyboardAskedFor()))
        compose.onNodeWithTag(EditorTags.TITLE).performImeAction()
        awaitFocused(EditorTags.SERVINGS)
        assertEquals(EditorInfo.IME_ACTION_NEXT, action(keyboardAskedFor()))
        compose.onNodeWithTag(EditorTags.SERVINGS).performImeAction()
        awaitFocused(EditorTags.FINISHED_WEIGHT)
        assertEquals(EditorInfo.IME_ACTION_NEXT, action(keyboardAskedFor()))
        hideKeyboard()
        compose.onNodeWithTag(EditorTags.FINISHED_WEIGHT).performImeAction()
        awaitFocused(EditorTags.ingredientName(0))
        compose.waitUntil(PATIENCE_MS) { keyboardUp() }
        assertTrue(compose.ingredientSheetOpen())
        assertEquals(EditorInfo.IME_ACTION_NEXT, action(keyboardAskedFor()))
        compose.onNodeWithTag(EditorTags.ingredientName(0)).performImeAction()
        awaitFocused(EditorTags.ingredientQuantity(0))
        assertEquals(EditorInfo.IME_ACTION_NEXT, action(keyboardAskedFor()))
        // The unit is chosen, not typed: Next opens its menu, and no field keeps the keyboard meanwhile.
        compose.onNodeWithTag(EditorTags.ingredientQuantity(0)).performImeAction()
        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.unitOption(QuantityUnit.KILOGRAM)) }
        assertFalse(isFocused(EditorTags.ingredientQuantity(0)))
        // A unit picked goes on to the note, with the keyboard up; the note says Done, which ends the sheet: it
        // closes, and no other row opens.
        compose.onNodeWithTag(EditorTags.unitOption(QuantityUnit.KILOGRAM)).performSemanticsAction(SemanticsActions.OnClick)
        awaitFocused(IngredientNoteTags.field(0))
        compose.waitUntil(PATIENCE_MS) { keyboardUp() }
        assertEquals(EditorInfo.IME_ACTION_DONE, action(keyboardAskedFor()))
        compose.onNodeWithTag(IngredientNoteTags.field(0)).performImeAction()
        compose.waitUntil(PATIENCE_MS) { !compose.ingredientSheetOpen() }
        compose.waitForIdle()
        assertEquals(QuantityUnit.KILOGRAM, stored(id).ingredients[0].unit)
        assertEquals(2, stored(id).ingredients.size)
        assertFalse(exists(EditorTags.ingredientName(1)))
        assertFalse(exists(EditorTags.unitOption(QuantityUnit.KILOGRAM)))
    }

    // "To taste" withdraws the quantity: in its sheet, Next goes from the name to the unit, whose pick goes on to the
    // note, whose Done closes the sheet.
    @Test
    fun aRowToTasteGoesFromItsNameToItsUnit() {
        val id = writeANewRecipe()
        addIngredient()
        compose.onNodeWithTag(EditorTags.ingredientUnit(0)).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.unitOption(QuantityUnit.TO_TASTE)) }
        compose.onNodeWithTag(EditorTags.unitOption(QuantityUnit.TO_TASTE)).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(PATIENCE_MS) { !exists(EditorTags.unitOption(QuantityUnit.TO_TASTE)) }
        // Picked by a tap, the unit leaves the sheet open.
        compose.waitUntil(PATIENCE_MS) { !exists(EditorTags.ingredientQuantity(0)) }
        assertTrue(compose.ingredientSheetOpen())
        enter(EditorTags.ingredientName(0))
        assertEquals(EditorInfo.IME_ACTION_NEXT, action(keyboardAskedFor()))
        compose.onNodeWithTag(EditorTags.ingredientName(0)).performImeAction()
        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.unitOption(QuantityUnit.TO_TASTE)) }
        compose.onNodeWithTag(EditorTags.unitOption(QuantityUnit.TO_TASTE)).performSemanticsAction(SemanticsActions.OnClick)
        awaitFocused(IngredientNoteTags.field(0))
        assertEquals(EditorInfo.IME_ACTION_DONE, action(keyboardAskedFor()))
        compose.onNodeWithTag(IngredientNoteTags.field(0)).performImeAction()
        compose.waitUntil(PATIENCE_MS) { !compose.ingredientSheetOpen() }
        compose.waitForIdle()
        assertEquals(1, stored(id).ingredients.size)
    }

    // Adding an ingredient opens its sheet with the name focused, and adding a step focuses its text; the keyboard shows.
    @Test
    fun aRowOrStepAddedTakesTheFocusWithTheKeyboard() {
        writeANewRecipe()
        assertFalse(keyboardShown())
        addIngredient()
        awaitFocused(EditorTags.ingredientName(0))
        compose.waitUntil(PATIENCE_MS) { keyboardUp() }

        hideKeyboard()
        addIngredient()
        awaitFocused(EditorTags.ingredientName(1))
        assertFalse(exists(EditorTags.ingredientName(0)))
        compose.waitUntil(PATIENCE_MS) { keyboardUp() }

        hideKeyboard()
        addStep()
        awaitFocused(EditorTags.stepText(0))
        compose.waitUntil(PATIENCE_MS) { keyboardUp() }

        hideKeyboard()
        // Written, so that it stays as the next is added.
        compose.onNodeWithTag(EditorTags.stepText(0)).performTextInput("Mix.")
        addStep()
        awaitFocused(EditorTags.stepText(1))
        // The first step's sheet closed as the second was added.
        assertFalse(exists(EditorTags.stepText(0)))
        compose.waitUntil(PATIENCE_MS) { keyboardUp() }
    }

    // T-159: a row or step added unfolds into its place — the Add under it moves away from the row before it gradually —
    // and still takes the focus with the keyboard up; one removed folds away, the Add closing up gradually.
    @Test
    fun aRowOrStepAddedUnfoldsWithTheFocusAndOneRemovedFoldsAway() {
        writeANewRecipe()
        val parts = listOf(
            listOf(EditorTags.INGREDIENT_ADD, EditorTags.ingredient(0), EditorTags.ingredientName(0), EditorTags.ingredientName(1), EditorTags.ingredientRemove(1)),
            listOf(EditorTags.STEP_ADD, EditorTags.step(0), EditorTags.stepText(0), EditorTags.stepText(1), EditorTags.stepRemove(1)),
        )
        for ((add, first, firstField, added, remove) in parts) {
            val steps = add == EditorTags.STEP_ADD
            val ingredient = add == EditorTags.INGREDIENT_ADD
            compose.onNodeWithTag(add).performScrollTo().performClick()
            awaitFocused(firstField)
            // A row or a step added and closed holding nothing goes again: it is written first.
            if (steps) compose.onNodeWithTag(firstField).performTextInput("Mix.")
            if (ingredient) compose.onNodeWithTag(firstField).performTextInput("Flour")
            hideKeyboard()
            // The sheet closed, so the list is in sight.
            compose.closeIngredient()
            compose.closeStep()
            // How far the Add stands below the first row: the room the rows after it take.
            fun after(): Float = compose.onNodeWithTag(add).fetchSemanticsNode().positionInRoot.y -
                compose.onNodeWithTag(first).fetchSemanticsNode().let { it.positionInRoot.y + it.size.height }
            compose.onNodeWithTag(add).performScrollTo()
            compose.assertChangesGradually("$add: a row added", ::after) { compose.onNodeWithTag(add).performClick() }
            awaitFocused(added)
            assertFalse(exists(firstField) && isFocused(firstField))
            compose.waitUntil(PATIENCE_MS) { keyboardUp() }
            if (steps) compose.onNodeWithTag(added).performTextInput("Serve.")
            if (ingredient) compose.onNodeWithTag(added).performTextInput("Salt")
            hideKeyboard()
            compose.closeIngredient()
            compose.closeStep()

            // A row's or a step's removal is in its sheet.
            if (steps) compose.openStep(1)
            if (ingredient) compose.openIngredient(1)
            compose.onNodeWithTag(remove).performScrollTo()
            compose.assertChangesGradually("$add: a row removed", ::after) { compose.onNodeWithTag(remove).performClick() }
            // Its Undo put away, so that it covers nothing tapped next.
            compose.swipeAwayStatement()
        }
    }
}
