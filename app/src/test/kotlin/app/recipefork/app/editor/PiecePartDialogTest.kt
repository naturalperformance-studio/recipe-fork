package app.recipefork.app.editor

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.R
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.library.LibrarySession
import app.recipefork.app.library.LibraryTags
import app.recipefork.app.library.LibraryView
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.api.IngredientEntryDto
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.IngredientReference
import app.recipefork.core.recipe.IngredientShare
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.Step
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
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * The part dialog at a phone's width (360 dp), in both languages, at 1× and 2× text: once open, the screen settles — it
 * did not, the dialog relaid every frame — and a `piece` row whose halving is not answered offers no part until it is,
 * so its part is never read on a grid it does not have.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class PiecePartDialogTest {
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

    // Free text, so that no dictionary entry answers the halving for the author.
    private val chicken = Ingredient(0, null, null, "Chicken", UnitType.PIECE, QuantityUnit.PIECE, Rational.of(3), divisible = null)
    private val flour = Ingredient(1, null, null, "Flour", UnitType.EXACT, QuantityUnit.GRAM, Rational.of(200))

    @Before
    fun setUp() {
        compose.registerIdlingResource(book.idling)
        compose.registerIdlingResource(device.idling)
        // An entry that answers the halving itself (FLOW-023 (11)).
        val egg = IngredientEntryDto(EGG, "яйцо", "egg", divisible = false)
        runBlocking { device.database.dictionaryDao().replace(SAMPLE_DICTIONARIES.copy(ingredients = SAMPLE_DICTIONARIES.ingredients + egg)) }
    }

    @After
    fun close() {
        compose.unregisterIdlingResource(book.idling)
        compose.unregisterIdlingResource(device.idling)
        book.close()
        device.close()
    }

    @Test
    @Config(qualifiers = "en-w360dp-h640dp")
    fun inEnglish() = partsOfAPieceRowAndAGramRow(flourWhole = "200 g", chickenWhole = "3 pc")

    @Test
    @Config(qualifiers = "ru-w360dp-h640dp")
    fun inRussian() = partsOfAPieceRowAndAGramRow(flourWhole = "200 г", chickenWhole = "3 шт")

    @Test
    @Config(qualifiers = "en-w360dp-h640dp", fontScale = 2f)
    fun inEnglishAtTwiceTheText() = partsOfAPieceRowAndAGramRow(flourWhole = "200 g", chickenWhole = "3 pc")

    @Test
    @Config(qualifiers = "ru-w360dp-h640dp", fontScale = 2f)
    fun inRussianAtTwiceTheText() = partsOfAPieceRowAndAGramRow(flourWhole = "200 г", chickenWhole = "3 шт")

    // A row naming an entry that gives its divisibility is never asked it: its part is offered at once, on the entry's
    // grid, though the row was stored before it held the entry's answer.
    @Test
    @Config(qualifiers = "en-w360dp-h640dp")
    fun anEntryAnswersTheHalving() {
        val eggs = Ingredient(0, EGG, null, null, UnitType.PIECE, QuantityUnit.PIECE, Rational.of(2), divisible = null)
        val recipe = Recipe.create(created, null)
            .copy(title = "Eggs", servings = 2, ingredients = listOf(eggs), steps = listOf(Step(0, "Beat ")))
            .also { runBlocking { recipes.save(it) } }
        open(recipe.id)
        openOffer()
        assertTrue(exists(EditorTags.stepOfferPart(0, 0)))
        askPart(0)
        typePart("0.5")
        assertEquals(string(R.string.editor_quantity_accepts_whole_pieces), problem())
        typePart("1")
        insertPart()
        assertEquals(listOf(IngredientReference(0, 5, IngredientShare(Rational.of(1), QuantityUnit.PIECE))), references(recipe.id))
    }

    private fun partsOfAPieceRowAndAGramRow(flourWhole: String, chickenWhole: String) {
        val recipe = Recipe.create(created, null)
            .copy(title = "Chicken", servings = 4, ingredients = listOf(chicken, flour), steps = listOf(Step(0, "Fry ")))
            .also { runBlocking { recipes.save(it) } }
        open(recipe.id)

        // The piece row's halving is not answered: its whole is offered, its part is not; the gram row offers both.
        openOffer()
        assertTrue(exists(EditorTags.stepOffer(0, 0)))
        assertFalse(exists(EditorTags.stepOfferPart(0, 0)))
        assertTrue(exists(EditorTags.stepOffer(0, 1)))
        assertTrue(exists(EditorTags.stepOfferPart(0, 1)))

        // The gram row's part: the screen settles with the dialog open, which says what it is a part of, and inserts.
        askPart(1)
        assertEquals(string(R.string.editor_part_of_whole, flourWhole), problem())
        assertFits()
        typePart("50")
        insertPart()
        assertEquals(listOf(IngredientReference(1, 4, IngredientShare(Rational.of(50), QuantityUnit.GRAM))), references(recipe.id))

        // Answered "can be halved", the piece row offers its part, read on halves; the screen settles here too.
        compose.openIngredient(0)
        compose.onNodeWithTag(EditorTags.divisible(0, true)).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { stored(recipe.id).ingredients[0].divisible == true }
        compose.closeIngredient()
        openOffer()
        assertTrue(exists(EditorTags.stepOfferPart(0, 0)))
        askPart(0)
        assertEquals(string(R.string.editor_part_of_whole, chickenWhole), problem())
        assertFits()
        typePart("1,3")
        assertEquals(string(R.string.editor_quantity_accepts_halves), problem())
        typePart("1.5")
        insertPart()
        assertEquals(IngredientShare(Rational.of(3, 2), QuantityUnit.PIECE), references(recipe.id).single { it.ingredientPosition == 0 }.share)
    }

    private companion object {
        const val EGG = 3L
    }

    private fun open(id: RecipeId) {
        val environment = EditorEnvironment(active, device.catalog(context)) { created.plusSeconds(60) }
        compose.setContent { MaterialTheme { LibraryOrEditor(session, recipes, environment, opened) } }
        compose.onNodeWithTag(LibraryTags.tab(LibraryView.OWN)).performClick()
        compose.waitForIdle()
        compose.runOnIdle { opened.value = id }
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.FORM), PATIENCE_MS)
    }

    private fun stored(id: RecipeId): Recipe = runBlocking { recipes.get(id) }!!

    private fun references(id: RecipeId): List<IngredientReference> = stored(id).steps.single().ingredientReferences

    private fun string(id: Int, vararg args: Any): String = context.getString(id, *args)

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun openOffer() {
        compose.inStep(0).onNodeWithTag(EditorTags.stepInsert(0)).performScrollTo().performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.stepOffer(0, 0)), PATIENCE_MS)
        compose.waitForIdle()
    }

    /** Asks for a part of the row at [row], and waits until the screen is idle with the dialog open. */
    private fun askPart(row: Int) {
        compose.onNodeWithTag(EditorTags.stepOfferPart(0, row)).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.PART_FIELD), PATIENCE_MS)
        compose.waitForIdle()
    }

    private fun problem(): String = compose.onNodeWithTag(EditorTags.PART_PROBLEM, useUnmergedTree = true).fetchSemanticsNode()
        .config[SemanticsProperties.Text].joinToString("") { it.text }

    /** The dialog stands inside the screen with a gutter each side, its field and Insert in sight. */
    private fun assertFits() {
        // The dialog's window is the screen's width, so its root's bounds are the screen's.
        val screen = context.resources.configuration.screenWidthDp.dp
        val dialog = compose.onNodeWithTag(EditorTags.PART_DIALOG).getBoundsInRoot()
        assertTrue("$dialog in $screen", dialog.left >= 16.dp && dialog.right <= screen - 16.dp)
        compose.onNodeWithTag(EditorTags.PART_FIELD).assertIsDisplayed()
        compose.onNodeWithTag(EditorTags.PART_INSERT).assertIsDisplayed()
    }

    private fun typePart(text: String) {
        compose.onNodeWithTag(EditorTags.PART_FIELD).performTextReplacement(text)
        compose.waitForIdle()
    }

    private fun insertPart() {
        compose.onNodeWithTag(EditorTags.PART_INSERT).assertIsEnabled().performClick()
        compose.waitUntilDoesNotExist(hasTestTag(EditorTags.PART_FIELD), PATIENCE_MS)
        compose.waitForIdle()
    }
}
