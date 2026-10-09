package app.recipefork.app.card

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.editor.TestDevice
import app.recipefork.app.editor.textsOf
import app.recipefork.app.file.RecipeImport
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.api.DictionariesDto
import app.recipefork.core.api.IngredientEntryDto
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.IngredientReference
import app.recipefork.core.recipe.IngredientShare
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeState
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.InterfaceLanguage.ENGLISH
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
import java.time.Instant

/**
 * The card scales a `piece` row by its effective divisibility — the dictionary entry's flag where it sets one, the
 * row's stored answer otherwise — in its list, in a step's part of the row, and in the fixing control; and a value
 * written before the entry's flag changed stays as written at the authored count (DOM-018).
 */
@RunWith(AndroidJUnit4::class)
class CardEntryDivisibilityTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val device = TestDevice(context)
    private val recipes = RecipeRepository(ActiveBook(BookName.Pool, book.database))
    private val environment = CardEnvironment { device.database.dictionaryDao().ingredients() }
    private val now = Instant.parse("2026-10-03T12:00:00Z")

    /** Chicken divisible, wings not: the flags the dictionary ships with. */
    private val dictionaries = DictionariesDto(
        version = "flags-1",
        ingredients = listOf(
            IngredientEntryDto(1, "курица", "chicken", divisible = true),
            IngredientEntryDto(WINGS, "крылышки", "chicken wings", 1, divisible = false),
        ),
        tags = emptyList(),
    )

    @Before
    fun setUp() {
        compose.registerIdlingResource(book.idling)
        compose.registerIdlingResource(device.idling)
        runBlocking { device.database.dictionaryDao().replace(dictionaries) }
    }

    @After
    fun close() {
        compose.unregisterIdlingResource(book.idling)
        compose.unregisterIdlingResource(device.idling)
        book.close()
        device.close()
    }

    /** Wings whose row still stores "divisible", as written before the dictionary said otherwise. */
    private fun wings(position: Int, quantity: Rational) = Ingredient(
        position, WINGS, unitType = UnitType.PIECE, unit = QuantityUnit.PIECE, quantity = quantity, divisible = true,
    )

    private fun open(recipe: Recipe) {
        runBlocking { recipes.save(recipe) }
        compose.setContent { MaterialTheme { CardScreen(recipe.id, recipes, environment, onBack = {}, onEdit = {}) } }
        compose.awaitCard()
        compose.waitUntil(PATIENCE_MS) {
            compose.onAllNodes(hasTestTag(CardTags.ingredient(recipe.ingredients.lastIndex))).fetchSemanticsNodes().size == 1
        }
    }

    private fun servings() = compose.textsOf(CardTags.SERVINGS).single()
    private fun quantity(position: Int) = compose.textsOf(CardTags.ingredient(position))[1]
    private fun recalculated() = compose.onAllNodes(hasTestTag(IngredientTags.RECALCULATED)).fetchSemanticsNodes().isNotEmpty()
    private fun shown(position: Int): String =
        compose.onNodeWithTag(StepTags.text(position), useUnmergedTree = true).fetchSemanticsNode().config[ShownText]
    private fun fewer() = compose.onNodeWithTag(CardTags.SERVINGS_FEWER).performScrollTo().performClick()
    private fun more() = compose.onNodeWithTag(CardTags.SERVINGS_MORE).performScrollTo().performClick()

    // Scenario 4: a recipe for 4 with 8 wings, at 3 servings: 6 whole wings. 7 wings at 2 servings — 3½ exactly — read
    // 4 by the entry, though the row stores "divisible"; a step's part of 1 wing reads 1 at 2 servings, not ½.
    @Test
    fun theCardScalesWingsAsWholePieces() {
        open(
            Recipe.create(now, null).copy(
                title = "Крылышки",
                servings = 4,
                ingredients = listOf(wings(0, Rational.of(8)), wings(1, Rational.of(7))),
                steps = listOf(Step(0, "Add ", listOf(IngredientReference(1, 4, IngredientShare(Rational.ONE, QuantityUnit.PIECE))))),
            ),
        )
        assertEquals("8 pc", quantity(0))
        assertEquals("Add 1 pc", shown(0))
        fewer()
        assertEquals("Servings: 3", servings())
        assertEquals("6 pc", quantity(0))
        fewer()
        assertEquals("Servings: 2", servings())
        assertEquals("4 pc", quantity(1))
        assertEquals("Add 1 pc ↑", shown(0))
    }

    // The fixing control holds what the entry's grid holds: a half wing is refused, a whole one fixes the scale.
    @Test
    fun theFixingControlHoldsWholeWingsOnly() {
        open(Recipe.create(now, null).copy(title = "Крылышки", servings = 4, ingredients = listOf(wings(0, Rational.of(8)))))
        compose.onNodeWithTag(CardTags.fixAmount(0), useUnmergedTree = true).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(hasTestTag(CardTags.FIX)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag(CardTags.FIX_FIELD).performTextReplacement("4.5")
        compose.waitForIdle()
        assertEquals(1, compose.onAllNodes(hasTestTag(CardTags.FIX_PROBLEM)).fetchSemanticsNodes().size)
        compose.onNodeWithTag(CardTags.FIX_CONFIRM).assertIsNotEnabled()
        compose.onNodeWithTag(CardTags.FIX_FIELD).performTextReplacement("6")
        compose.waitForIdle()
        compose.onNodeWithTag(CardTags.FIX_CONFIRM).performScrollTo().assertIsEnabled().performClick()
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(hasTestTag(CardTags.FIX)).fetchSemanticsNodes().isEmpty() }
        assertEquals("Servings: ≈3", servings())
        assertEquals("6 pc", quantity(0))
    }

    /**
     * DOM-018 (1, 2) for a frozen recipe — published with an account, found without one — holding 1½ wings written
     * while wings were divisible: at 4 servings 1½ as written and nothing marked recalculated; at 3 servings 1⅛,
     * computed from the written 1½ and rounded on the whole-piece grid to 1; at 8, 3.
     */
    private fun writtenBeforeTheCorrectionStaysAsWritten(state: RecipeState) {
        open(
            Recipe.create(now, null).copy(
                title = "Крылышки",
                servings = 4,
                ingredients = listOf(wings(0, Rational.of(3, 2))),
            ).inState(state),
        )
        assertEquals("Servings: 4", servings())
        assertEquals("1½ pc", quantity(0))
        assertFalse(recalculated())
        fewer()
        assertEquals("Servings: 3", servings())
        assertEquals("1 pc", quantity(0))
        assertTrue(recalculated())
        repeat(5) { more() }
        assertEquals("Servings: 8", servings())
        assertEquals("3 pc", quantity(0))
    }

    @Test
    fun aPublishedRecipeKeepsItsValueAsWritten() = writtenBeforeTheCorrectionStaysAsWritten(RecipeState.PUBLISHED)

    @Test
    fun aFoundRecipeKeepsItsValueAsWritten() = writtenBeforeTheCorrectionStaysAsWritten(RecipeImport.FOUND_STATE)

    // The same through the list a step reads: at the authored count nothing is derived.
    @Test
    fun theListAtTheAuthoredCountMarksNothing() {
        val rows = listOf(wings(0, Rational.of(3, 2)))
        val entries = runBlocking { device.database.dictionaryDao().ingredients() }.associateBy { it.id }
        val authored = ingredientList(rows, entries, ENGLISH, 4, Rational.of(4))
        assertEquals("1½ pc", authored.lines.single().quantity)
        assertFalse(authored.lines.single().derived)
        assertEquals(false, authored.lines.single().fixing?.divisible)
        // At 6 servings 2¼: 2 whole wings by the entry; read without the dictionary, the row's own answer gives 2½.
        val atSix = ingredientList(rows, entries, ENGLISH, 4, Rational.of(6))
        assertEquals("2 pc", atSix.lines.single().quantity)
        assertTrue(atSix.lines.single().derived)
        assertEquals("2½ pc", ingredientList(rows, emptyMap(), ENGLISH, 4, Rational.of(6)).lines.single().quantity)
    }

    private companion object {
        const val WINGS = 2L
    }
}
