package app.recipefork.app.editor

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
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
import app.recipefork.app.ui.AccessibilityChecks
import app.recipefork.app.ui.LocalizationChecks
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.RecipeState
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.Rational
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * L10N-001 (1), A11Y-003 (1) and A11Y-005 (1) on the serving count and the ingredient rows in every state this task
 * adds — a free-text row with its warning, a piece asking for divisibility, a marked quantity, "to taste", the unit
 * list open, and the read-only rows of a frozen recipe — in each interface language.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class IngredientsLanguageAndAccessibilityTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val device = TestDevice(context)
    private val active = ActiveBook(BookName.Pool, book.database)
    private val recipes = RecipeRepository(active)
    private val session = LibrarySession(SavedStateHandle())
    private val opened = mutableStateOf<RecipeId?>(null)

    /** The author's own content on the screen: tag labels, names, numbers typed and the frozen recipe's title. */
    private val content = tagLabels(SAMPLE_DICTIONARIES) + setOf("Pancakes", "черемша", "4", "¼", "1 500", "500", "½", "2")

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

    private fun checkTheScreen(expectInteractive: Int) {
        compose.waitForIdle()
        LocalizationChecks.assertScreenTextFromResources(compose, context, content)
        LocalizationChecks.assertScreenTextWrittenInItsLanguage(compose, context, content)
        AccessibilityChecks.assertTouchTargets(compose, expectAtLeast = expectInteractive)
        AccessibilityChecks.assertInteractiveElementsLabelled(compose, expectAtLeast = expectInteractive)
    }

    private fun chooseUnit(position: Int, unit: QuantityUnit) {
        compose.onNodeWithTag(EditorTags.ingredientUnit(position)).performScrollTo().performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.unitOption(unit)), PATIENCE_MS)
        compose.onNodeWithTag(EditorTags.unitOption(unit)).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntilDoesNotExist(hasTestTag(EditorTags.unitOption(unit)), PATIENCE_MS)
    }

    private fun addIngredient(position: Int) {
        compose.onNodeWithTag(EditorTags.INGREDIENT_ADD).performScrollTo().performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.ingredientName(position)), PATIENCE_MS)
    }

    @Test
    fun inEnglish() = everyState()

    @Test
    @Config(qualifiers = "ru")
    fun inRussian() = everyState()

    private fun everyState() {
        compose.setContent {
            MaterialTheme { LibraryOrEditor(session, recipes, EditorEnvironment(active, device.catalog(context)), opened) }
        }
        compose.onNodeWithTag(LibraryTags.tab(LibraryView.OWN)).performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.WRITE), PATIENCE_MS)
        compose.onNodeWithTag(LibraryTags.WRITE).performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.offered(10)), PATIENCE_MS)

        // A serving count; a free-text piece asking for divisibility, holding a quarter it does not accept; a row
        // with separators; and a row to taste.
        compose.onNodeWithTag(EditorTags.SERVINGS).performTextInput("4")
        addIngredient(0)
        compose.onNodeWithTag(EditorTags.ingredientName(0)).performTextInput("черемша")
        chooseUnit(0, QuantityUnit.PIECE)
        compose.onNodeWithTag(EditorTags.ingredientQuantity(0)).performScrollTo().performTextInput("¼")
        addIngredient(1)
        compose.onNodeWithTag(EditorTags.ingredientQuantity(1)).performScrollTo().performTextInput("1 500")
        addIngredient(2)
        chooseUnit(2, QuantityUnit.TO_TASTE)
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.ingredientError(1)), PATIENCE_MS)
        // back, title, language, clear, search, three tags, servings, and per row its fields and actions
        checkTheScreen(expectInteractive = 20)

        // The unit list open.
        compose.onNodeWithTag(EditorTags.ingredientUnit(1)).performScrollTo().performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.unitOption(QuantityUnit.CUP)), PATIENCE_MS)
        checkTheScreen(expectInteractive = 12)
        compose.onNodeWithTag(EditorTags.unitOption(QuantityUnit.GRAM)).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntilDoesNotExist(hasTestTag(EditorTags.unitOption(QuantityUnit.CUP)), PATIENCE_MS)

        // A frozen recipe's rows, read only.
        compose.onNodeWithTag(EditorTags.BACK).performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.OWN_LIST), PATIENCE_MS)
        val frozen = Recipe.create(Instant.parse("2026-10-02T12:00:00Z"), null).copy(
            title = "Pancakes",
            servings = 4,
            ingredients = listOf(
                Ingredient(0, null, null, "черемша", UnitType.EXACT, QuantityUnit.GRAM, Rational.of(500)),
                Ingredient(1, null, null, null, UnitType.HOUSEHOLD, QuantityUnit.TEASPOON, Rational.of(1, 2)),
                Ingredient(2, null, null, "черемша", UnitType.TASTE_UNMEASURED, null, null),
                Ingredient(3, null, null, "черемша", UnitType.PIECE, QuantityUnit.PIECE, Rational.of(2)),
            ),
        ).inState(RecipeState.PUBLISHED)
        runBlocking { recipes.save(frozen) }
        compose.runOnIdle { opened.value = frozen.id }
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.ingredient(3)), PATIENCE_MS)
        checkTheScreen(expectInteractive = 1)
    }
}
