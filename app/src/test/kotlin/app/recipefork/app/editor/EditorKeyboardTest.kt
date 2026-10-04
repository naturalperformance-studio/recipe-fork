package app.recipefork.app.editor

import android.content.Context
import android.text.InputType
import android.view.View
import android.view.ViewGroup
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
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.core.view.children
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
        compose.onNodeWithTag(LibraryTags.WRITE).performClick()
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

    /** The keyboard put away, as the system's back does, whatever field holds the focus. */
    private fun hideKeyboard() {
        compose.runOnUiThread {
            context.getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(compose.activity.window.decorView.windowToken, 0)
        }
        compose.waitUntil(PATIENCE_MS) { !keyboardShown() }
    }

    /** What the focused field asks the keyboard for: its input type and its action. */
    private fun keyboardAskedFor(): EditorInfo {
        fun View.all(): Sequence<View> = sequenceOf(this) + ((this as? ViewGroup)?.children?.flatMap { it.all() } ?: emptySequence())
        val editor = EditorInfo()
        compose.runOnUiThread {
            compose.activity.window.decorView.all().first { it.onCheckIsTextEditor() }.onCreateInputConnection(editor)
        }
        return editor
    }

    private fun action(info: EditorInfo): Int = info.imeOptions and EditorInfo.IME_MASK_ACTION

    private fun capitalisesSentences(info: EditorInfo): Boolean = info.inputType and InputType.TYPE_TEXT_FLAG_CAP_SENTENCES != 0

    /** Taps the field [tag], scrolled to first, and waits for it to take the focus. */
    private fun enter(tag: String) {
        compose.onNodeWithTag(tag).performScrollTo().performTouchInput { click() }
        awaitFocused(tag)
    }

    private fun addIngredient(): Int {
        val position = stored(RecipeId(requireNotNull(session.editing.value))).ingredients.size
        compose.onNodeWithTag(EditorTags.INGREDIENT_ADD).performScrollTo().performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.ingredientName(position)), PATIENCE_MS)
        return position
    }

    private fun addStep(): Int {
        val position = stored(RecipeId(requireNotNull(session.editing.value))).steps.size
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
        compose.onNodeWithTag(EditorTags.timerAttach(0)).performScrollTo().performTouchInput { click() }
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.timerMinutes(0)), PATIENCE_MS)
        compose.onNodeWithTag(EditorTags.timerMinutes(0)).performScrollTo().performTextReplacement("5")
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

    // Next goes title → serving count → name → quantity → unit → the next row's name; the last row's last field says
    // Done, which closes the keyboard and adds no row.
    @Test
    fun nextGoesThroughTheRowsAndDoneEndsTheLastOne() {
        val id = writeANewRecipe()
        // With no rows, the serving count is the last field: Done.
        enter(EditorTags.SERVINGS)
        assertEquals(EditorInfo.IME_ACTION_DONE, action(keyboardAskedFor()))
        addIngredient()
        addIngredient()

        enter(EditorTags.TITLE)
        assertEquals(EditorInfo.IME_ACTION_NEXT, action(keyboardAskedFor()))
        compose.onNodeWithTag(EditorTags.TITLE).performImeAction()
        awaitFocused(EditorTags.SERVINGS)
        assertEquals(EditorInfo.IME_ACTION_NEXT, action(keyboardAskedFor()))
        compose.onNodeWithTag(EditorTags.SERVINGS).performImeAction()
        awaitFocused(EditorTags.ingredientName(0))
        assertEquals(EditorInfo.IME_ACTION_NEXT, action(keyboardAskedFor()))
        compose.onNodeWithTag(EditorTags.ingredientName(0)).performImeAction()
        awaitFocused(EditorTags.ingredientQuantity(0))
        assertEquals(EditorInfo.IME_ACTION_NEXT, action(keyboardAskedFor()))
        // The unit is chosen, not typed: Next opens its menu, and no field keeps the keyboard meanwhile.
        compose.onNodeWithTag(EditorTags.ingredientQuantity(0)).performImeAction()
        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.unitOption(QuantityUnit.PIECE)) }
        assertFalse(isFocused(EditorTags.ingredientQuantity(0)))
        // A unit picked goes on to the next row's name, with the keyboard up.
        hideKeyboard()
        compose.onNodeWithTag(EditorTags.unitOption(QuantityUnit.PIECE)).performSemanticsAction(SemanticsActions.OnClick)
        awaitFocused(EditorTags.ingredientName(1))
        compose.waitUntil(PATIENCE_MS) { keyboardShown() }
        assertEquals(QuantityUnit.PIECE, stored(id).ingredients[0].unit)

        assertEquals(EditorInfo.IME_ACTION_NEXT, action(keyboardAskedFor()))
        compose.onNodeWithTag(EditorTags.ingredientName(1)).performImeAction()
        awaitFocused(EditorTags.ingredientQuantity(1))
        // The last row's last field: Done closes the keyboard, and no row is added.
        assertEquals(EditorInfo.IME_ACTION_DONE, action(keyboardAskedFor()))
        compose.onNodeWithTag(EditorTags.ingredientQuantity(1)).performImeAction()
        compose.waitUntil(PATIENCE_MS) { !isFocused(EditorTags.ingredientQuantity(1)) }
        compose.waitUntil(PATIENCE_MS) { !keyboardShown() }
        compose.waitForIdle()
        assertEquals(2, stored(id).ingredients.size)
        assertFalse(exists(EditorTags.ingredientName(2)))
        assertFalse(exists(EditorTags.unitOption(QuantityUnit.PIECE)))
    }

    // "To taste" withdraws the quantity: Next goes from the name to the unit, and on the last row the name says Done.
    @Test
    fun aRowToTasteGoesFromItsNameToItsUnit() {
        val id = writeANewRecipe()
        listOf(addIngredient(), addIngredient()).forEach { position ->
            compose.onNodeWithTag(EditorTags.ingredientUnit(position)).performScrollTo().performClick()
            compose.waitUntil(PATIENCE_MS) { exists(EditorTags.unitOption(QuantityUnit.TO_TASTE)) }
            compose.onNodeWithTag(EditorTags.unitOption(QuantityUnit.TO_TASTE)).performSemanticsAction(SemanticsActions.OnClick)
            compose.waitUntil(PATIENCE_MS) { !exists(EditorTags.unitOption(QuantityUnit.TO_TASTE)) }
        }
        compose.waitUntil(PATIENCE_MS) { !exists(EditorTags.ingredientQuantity(1)) }
        enter(EditorTags.ingredientName(0))
        assertEquals(EditorInfo.IME_ACTION_NEXT, action(keyboardAskedFor()))
        compose.onNodeWithTag(EditorTags.ingredientName(0)).performImeAction()
        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.unitOption(QuantityUnit.TO_TASTE)) }
        compose.onNodeWithTag(EditorTags.unitOption(QuantityUnit.TO_TASTE)).performSemanticsAction(SemanticsActions.OnClick)
        awaitFocused(EditorTags.ingredientName(1))
        assertEquals(EditorInfo.IME_ACTION_DONE, action(keyboardAskedFor()))
        compose.onNodeWithTag(EditorTags.ingredientName(1)).performImeAction()
        compose.waitUntil(PATIENCE_MS) { !isFocused(EditorTags.ingredientName(1)) }
        compose.waitForIdle()
        assertEquals(2, stored(id).ingredients.size)
    }

    // Adding an ingredient or a step focuses its first field and shows the keyboard.
    @Test
    fun aRowOrStepAddedTakesTheFocusWithTheKeyboard() {
        writeANewRecipe()
        assertFalse(keyboardShown())
        addIngredient()
        awaitFocused(EditorTags.ingredientName(0))
        compose.waitUntil(PATIENCE_MS) { keyboardShown() }

        hideKeyboard()
        addIngredient()
        awaitFocused(EditorTags.ingredientName(1))
        assertFalse(isFocused(EditorTags.ingredientName(0)))
        compose.waitUntil(PATIENCE_MS) { keyboardShown() }

        hideKeyboard()
        addStep()
        awaitFocused(EditorTags.stepText(0))
        compose.waitUntil(PATIENCE_MS) { keyboardShown() }

        hideKeyboard()
        addStep()
        awaitFocused(EditorTags.stepText(1))
        assertFalse(isFocused(EditorTags.stepText(0)))
        compose.waitUntil(PATIENCE_MS) { keyboardShown() }
    }
}
