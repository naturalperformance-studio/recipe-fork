package app.recipefork.app.editor

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * A step's reference to a part of an ingredient, in the editor: offered beside the whole for a row holding a quantity,
 * entered in the row's own unit on its grid and no more than the whole, shown as the authored part and styled as a
 * whole reference is; kept as written when the row's quantity changes, marked where it is then more than the whole,
 * and made whole again — said once — when the row's unit changes to one the part cannot be read in.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ru")
class PartReferencesEditorTest {
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

    private val garlic = Ingredient(0, null, null, "Чеснок", UnitType.PIECE, QuantityUnit.PIECE, Rational.of(4), divisible = false)
    private val salt = Ingredient(1, null, null, "Соль", UnitType.TASTE_UNMEASURED, null, null)
    private val milk = Ingredient(2, null, null, "Молоко", UnitType.EXACT, QuantityUnit.MILLILITRE, null)
    private val two = IngredientShare(Rational.of(2), QuantityUnit.PIECE)

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

    private fun open(id: RecipeId) {
        val environment = EditorEnvironment(active, device.catalog(context)) { created.plusSeconds(60) }
        compose.setContent {
            MaterialTheme {
                warning = MaterialTheme.colorScheme.errorContainer
                LibraryOrEditor(session, recipes, environment, opened)
            }
        }
        compose.onNodeWithTag(LibraryTags.tab(LibraryView.OWN)).performClick()
        compose.waitForIdle()
        compose.runOnIdle { opened.value = id }
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.FORM), PATIENCE_MS)
    }

    private fun chicken(vararg steps: Step): Recipe = Recipe.create(created, null)
        .copy(title = "Курица", servings = 4, ingredients = listOf(garlic, salt, milk), steps = steps.toList())
        .also { runBlocking { recipes.save(it) } }

    private fun stored(id: RecipeId): Recipe = runBlocking { recipes.get(id) }!!

    private fun string(id: Int, vararg args: Any): String = context.getString(id, *args)

    private fun node(tag: String): SemanticsNode = compose.onNodeWithTag(tag).fetchSemanticsNode()

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    /** The text of the one node tagged [tag], read apart from the field or dialog it is merged into. */
    private fun textOf(tag: String): String =
        compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config[SemanticsProperties.Text].joinToString("") { it.text }

    private fun stepShown(position: Int) = node(EditorTags.stepText(position)).config[SemanticsProperties.EditableText].text

    private fun laidOut(tag: String): AnnotatedString {
        val results = mutableListOf<TextLayoutResult>()
        node(tag).config[SemanticsActions.GetTextLayoutResult].action!!.invoke(results)
        return results.single().layoutInput.text
    }

    private var warning: Color? = null

    /** The background of the step's shown character at [at], as laid out. */
    private fun background(at: Int): Color? =
        laidOut(EditorTags.stepText(0)).spanStyles.filter { it.start <= at && at < it.end }.map { it.item.background }
            .firstOrNull { it != Color.Unspecified }

    private fun bold(at: Int): Boolean = laidOut(EditorTags.stepText(0)).spanStyles
        .any { it.start <= at && at < it.end && it.item.fontWeight == FontWeight.Bold && it.item.textDecoration == TextDecoration.Underline }

    private fun placeCursor(position: Int, at: Int) {
        compose.onNodeWithTag(EditorTags.stepText(position)).performScrollTo().performTextInputSelection(TextRange(at))
        compose.waitForIdle()
    }

    private fun openOffer(position: Int) {
        compose.onNodeWithTag(EditorTags.stepInsert(position)).performScrollTo().performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.stepOffer(position, 0)), PATIENCE_MS)
    }

    private fun askPart(position: Int, row: Int) {
        compose.onNodeWithTag(EditorTags.stepOfferPart(position, row)).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.PART_FIELD), PATIENCE_MS)
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

    private fun problem(): String = textOf(EditorTags.PART_PROBLEM)

    // Scenario 3: "part" is offered beside the whole for a row holding a quantity — never for "to taste" or a row with
    // no quantity — entered in the row's own unit, read like its quantity field, on its grid and no more than the whole.
    @Test
    fun aPartIsOfferedEnteredAndInsertedAtTheCursor() {
        val recipe = chicken(Step(0, "В маринад: "), Step(1, "В соус: "))
        open(recipe.id)
        placeCursor(0, 11)
        openOffer(0)
        assertTrue(exists(EditorTags.stepOfferPart(0, 0)))
        assertTrue(!exists(EditorTags.stepOfferPart(0, 1)))
        assertTrue(!exists(EditorTags.stepOfferPart(0, 2)))
        assertEquals(listOf(string(R.string.editor_step_offered_part, "Чеснок")), compose.textsOf(EditorTags.stepOfferPart(0, 0)))
        askPart(0, 0)
        compose.onNodeWithTag(EditorTags.PART_INSERT).assertIsNotEnabled()
        assertEquals("Из 4 шт по списку.", problem())
        typePart("0")
        compose.onNodeWithTag(EditorTags.PART_INSERT).assertIsNotEnabled()
        assertEquals(string(R.string.editor_quantity_accepts_whole_pieces), problem())
        typePart("1,5")
        compose.onNodeWithTag(EditorTags.PART_INSERT).assertIsNotEnabled()
        assertEquals(string(R.string.editor_quantity_accepts_whole_pieces), problem())
        typePart("1 0")
        assertEquals(string(R.string.editor_quantity_separators), problem())
        typePart("5")
        compose.onNodeWithTag(EditorTags.PART_INSERT).assertIsNotEnabled()
        assertEquals("Больше, чем 4 шт по списку.", problem())
        typePart("2")
        insertPart()
        assertEquals("В маринад: 2 шт", stepShown(0))
        assertEquals(Step(0, "В маринад: ", listOf(IngredientReference(0, 11, two))), stored(recipe.id).steps[0])
        // Styled as a whole reference is: bold and underlined, the typed text plain.
        val shown = laidOut(EditorTags.stepText(0))
        fun styles(at: Int) = shown.spanStyles.filter { it.start <= at && at < it.end }.map { it.item }
        (11 until 15).forEach { at ->
            assertTrue(styles(at).any { it.fontWeight == FontWeight.Bold && it.textDecoration == TextDecoration.Underline })
        }
        assertTrue(styles(3).isEmpty())

        // Parts are independent: the other step takes 2 of the 4 too, or even all 4.
        placeCursor(1, 8)
        openOffer(1)
        askPart(1, 0)
        typePart("4")
        insertPart()
        assertEquals("В соус: 4 шт", stepShown(1))
        assertEquals(listOf(IngredientReference(0, 8, IngredientShare(Rational.of(4), QuantityUnit.PIECE))), stored(recipe.id).steps[1].ingredientReferences)
        // Choosing the whole still inserts the whole.
        placeCursor(1, 0)
        openOffer(1)
        compose.onNodeWithTag(EditorTags.stepOffer(1, 0)).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(PATIENCE_MS) { stored(recipe.id).steps[1].ingredientReferences.size == 2 }
        assertEquals(null, stored(recipe.id).steps[1].ingredientReferences.first().share)
    }

    // FLOW-031 (1): typing `@` offers the part too; the part takes the `@`'s place. Cancelling inserts nothing.
    @Test
    fun theTriggerOffersThePartToo() {
        val recipe = chicken(Step(0, "В соус "))
        open(recipe.id)
        placeCursor(0, 7)
        compose.onNodeWithTag(EditorTags.stepText(0)).performTextInput("@")
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.stepOfferPart(0, 0)), PATIENCE_MS)
        askPart(0, 0)
        compose.onNodeWithTag(EditorTags.PART_CANCEL).performClick()
        compose.waitUntilDoesNotExist(hasTestTag(EditorTags.PART_FIELD), PATIENCE_MS)
        compose.waitForIdle()
        assertEquals("В соус @", stepShown(0))
        assertEquals(Step(0, "В соус @"), stored(recipe.id).steps.single())
        compose.onNodeWithTag(EditorTags.stepText(0)).performTextReplacement("В соус ")
        compose.waitForIdle()
        placeCursor(0, 7)
        compose.onNodeWithTag(EditorTags.stepText(0)).performTextInput("@")
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.stepOfferPart(0, 0)), PATIENCE_MS)
        askPart(0, 0)
        typePart("2")
        insertPart()
        assertEquals("В соус 2 шт", stepShown(0))
        assertEquals(Step(0, "В соус ", listOf(IngredientReference(0, 7, two))), stored(recipe.id).steps.single())
    }

    // Editing the row's quantity keeps each part as written; one more than the new whole is marked, not refused.
    @Test
    fun aPartIsKeptAsWrittenAndMarkedWhenItExceedsTheWhole() {
        val recipe = chicken(Step(0, "В маринад: ", listOf(IngredientReference(0, 11, two))))
        open(recipe.id)
        assertEquals("В маринад: 2 шт", stepShown(0))
        assertTrue(!exists(EditorTags.stepPartWarning(0)))
        assertTrue(background(11) != null && background(11) != warning)
        compose.onNodeWithTag(EditorTags.ingredientQuantity(0)).performScrollTo().performTextReplacement("1")
        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.stepPartWarning(0)) }
        assertEquals("В маринад: 2 шт", stepShown(0))
        assertEquals("Часть 2 шт больше, чем 1 шт по списку.", textOf(EditorTags.stepPartWarning(0)))
        // The reference itself is tinted as a warning, still bold and underlined like any reference.
        compose.waitUntil(PATIENCE_MS) { background(11) == warning }
        assertTrue(bold(11))
        assertEquals(listOf(IngredientReference(0, 11, two)), stored(recipe.id).steps[0].ingredientReferences)
        compose.onNodeWithTag(EditorTags.ingredientQuantity(0)).performScrollTo().performTextReplacement("6")
        compose.waitUntil(PATIENCE_MS) { !exists(EditorTags.stepPartWarning(0)) }
        compose.waitUntil(PATIENCE_MS) { background(11) != warning }
        assertEquals("В маринад: 2 шт", stepShown(0))
        assertEquals(listOf(IngredientReference(0, 11, two)), stored(recipe.id).steps[0].ingredientReferences)
    }

    // A change of the row's unit type makes its parts whole references again, and says so once.
    @Test
    fun aChangeOfUnitTypeMakesThePartsWholeAndSaysSoOnce() {
        val recipe = chicken(
            Step(0, "В маринад: ", listOf(IngredientReference(0, 11, two))),
            Step(1, "В соус: ", listOf(IngredientReference(0, 8, two))),
        )
        open(recipe.id)
        compose.onNodeWithTag(EditorTags.ingredientUnit(0)).performScrollTo().performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.unitOption(QuantityUnit.GRAM)), PATIENCE_MS)
        compose.onNodeWithTag(EditorTags.unitOption(QuantityUnit.GRAM)).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.STATEMENT) }
        val statement = string(R.string.editor_parts_made_whole, 1)
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodesWithText(statement, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        // Said once, for both parts.
        assertEquals(1, compose.onAllNodesWithText(statement, useUnmergedTree = true).fetchSemanticsNodes().size)
        assertEquals(1, compose.onAllNodes(hasTestTag(EditorTags.STATEMENT), useUnmergedTree = true).fetchSemanticsNodes().size)
        val steps = stored(recipe.id).steps
        assertEquals(listOf(IngredientReference(0, 11)), steps[0].ingredientReferences)
        assertEquals(listOf(IngredientReference(0, 8)), steps[1].ingredientReferences)
        assertEquals("В маринад: 4 г", stepShown(0))
        assertEquals("В соус: 4 г", stepShown(1))
    }
}
