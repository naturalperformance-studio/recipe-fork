package app.recipefork.app.card

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsEnabled
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
import app.recipefork.app.editor.SAMPLE_DICTIONARIES
import app.recipefork.app.editor.TestDevice
import app.recipefork.app.editor.textsOf
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.IngredientReference
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.Rational
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * Ingredient references read at the reader's scale on the card (FLOW-032, SCALE-007, DOM-007): each one exactly as its
 * ingredient's line in the list reads — quantity, rounding arrow and the arrow's words — at every count and while an
 * ingredient is fixed, the author's words around it untouched.
 */
@RunWith(AndroidJUnit4::class)
class CardStepReferencesTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val device = TestDevice(context)
    private val recipes = RecipeRepository(ActiveBook(BookName.Pool, book.database))
    private val environment = CardEnvironment { device.database.dictionaryDao().ingredients() }
    private val now = Instant.parse("2026-10-02T12:00:00Z")

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

    /** The rows referenced, by position: what scales, a row to taste, and rows the card shows unscaled. */
    private val ingredients = listOf(
        Ingredient(0, null, freeTextName = "Flour", unitType = UnitType.EXACT, unit = QuantityUnit.GRAM, quantity = Rational.of(200)),
        Ingredient(1, null, freeTextName = "Eggs", unitType = UnitType.PIECE, unit = QuantityUnit.PIECE, quantity = Rational.of(3), divisible = false),
        Ingredient(2, null, freeTextName = "Salt", unitType = UnitType.HOUSEHOLD, unit = QuantityUnit.TEASPOON, quantity = Rational.ONE),
        Ingredient(3, null, freeTextName = "Pepper", unitType = UnitType.TASTE_UNMEASURED, unit = null, quantity = null),
        // Unfinished: a unit and no quantity.
        Ingredient(4, null, freeTextName = "Sugar", unitType = UnitType.EXACT, unit = QuantityUnit.GRAM, quantity = null),
        // Off its grid: half a gram.
        Ingredient(5, null, freeTextName = "Butter", unitType = UnitType.EXACT, unit = QuantityUnit.GRAM, quantity = Rational.of(25, 2)),
        // Nothing to show at all.
        Ingredient(6, null, freeTextName = "Water", unitType = UnitType.EXACT, unit = null, quantity = null),
    )

    /** For 4: the first step is the scenario's, then one step "Take <reference>." per row. */
    private fun recipe(first: String, offset: Int) = Recipe.create(now, null).copy(
        title = "Bread",
        servings = 4,
        ingredients = ingredients,
        steps = listOf(Step(0, first, listOf(IngredientReference(0, offset)))) +
            ingredients.map { Step(it.position + 1, "Take .", listOf(IngredientReference(it.position, 5))) },
    )

    private val english = recipe("Add  flour", 4)

    private fun open(recipe: Recipe = english) {
        runBlocking { recipes.save(recipe) }
        compose.setContent { MaterialTheme { CardScreen(recipe.id, recipes, environment, onBack = {}, onEdit = {}) } }
        compose.awaitCard()
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(hasTestTag(CardTags.step(0))).fetchSemanticsNodes().size == 1 }
    }

    private fun servings() = compose.textsOf(CardTags.SERVINGS).single()
    private fun more(times: Int = 1) = repeat(times) { compose.onNodeWithTag(CardTags.SERVINGS_MORE).performScrollTo().performClick() }
    private fun fewer(times: Int = 1) = repeat(times) { compose.onNodeWithTag(CardTags.SERVINGS_FEWER).performScrollTo().performClick() }

    /** The step at [position] as a sighted reader sees it. */
    private fun shown(position: Int): String =
        compose.onNodeWithTag(StepTags.text(position), useUnmergedTree = true).fetchSemanticsNode().config[ShownText]

    /** The step at [position] as the screen reader reads it: its number, then its one sentence. */
    private fun spoken(position: Int): List<String> = compose.textsOf(CardTags.step(position))

    /** The quantity the list shows for the row at [position], or null where it shows none. */
    private fun listQuantity(position: Int): String? = compose.textsOf(CardTags.ingredient(position)).getOrNull(1)

    /** The arrow the list shows after the row's quantity, or null. */
    private fun listArrow(position: Int): String? =
        compose.onAllNodes(hasTestTag(IngredientTags.rounding(position)), useUnmergedTree = true).fetchSemanticsNodes()
            .singleOrNull()?.config?.get(ShownText)

    /** The list's words for the row's arrow, read right after its quantity, or null where it carries none. */
    private fun listWords(position: Int): String? =
        listArrow(position)?.let { compose.textsOf(CardTags.ingredient(position))[2] }

    /** Every reference step reads its row exactly as the list does: quantity, arrow and its words. */
    private fun assertStepsReadAsTheList() {
        ingredients.forEach { ingredient ->
            val p = ingredient.position
            // A row with no quantity yet reads as the editor writes it: the placeholder, then the unit the list shows.
            val unfinished = ingredient.quantity == null && ingredient.unitType != UnitType.TASTE_UNMEASURED
            val quantity = if (unfinished) listOfNotNull("___", listQuantity(p)).joinToString(" ") else listQuantity(p)!!
            val arrow = listArrow(p)?.let { " $it" } ?: ""
            assertEquals("${ingredient.freeTextName} at ${servings()}", "Take $quantity$arrow.", shown(p + 1))
            val words = listWords(p)?.let { " ($it)" } ?: ""
            assertEquals("${ingredient.freeTextName} at ${servings()}", listOf("${p + 2}.", "Take $quantity$words."), spoken(p + 1))
        }
    }

    private val up = "rounded up, exact value was lower"
    private val down = "rounded down, exact value was higher"

    // FLOW-032 (1), scenarios 1–2: at 4 the step reads 200 g; at 8, 400 g, exactly the list's; the words untouched.
    @Test
    @Config(qualifiers = "ru")
    fun theScenarioStepReadsTheScaledQuantity() {
        open(recipe("Всыпьте  муки", 8))
        assertEquals("Всыпьте 200 г муки", shown(0))
        more(4)
        assertEquals("Порций: 8", servings())
        assertEquals("Всыпьте 400 г муки", shown(0))
        assertEquals("400 г", listQuantity(0))
        assertEquals(listOf("1.", "Всыпьте 400 г муки"), spoken(0))
        // A "to taste" reference reads as the list writes it, unmarked.
        assertEquals("Take по вкусу.", shown(4))
    }

    // SCALE-007, DOM-007, FLOW-032 (3): at three counts every reference is its list line, arrows and words included;
    // at the authored count none is marked.
    @Test
    fun atEveryCountAReferenceReadsAsItsLine() {
        open()
        assertEquals("Servings: 4", servings())
        assertStepsReadAsTheList()
        (1..7).forEach { assertFalse(shown(it), shown(it).contains('↑') || shown(it).contains('↓')) }
        more(4)
        assertEquals("Servings: 8", servings())
        assertStepsReadAsTheList()
        assertEquals("Add 400 g flour", shown(0))
        fewer(3)
        assertEquals("Servings: 5", servings())
        assertStepsReadAsTheList()
        // 3.75 eggs shown as 4, rounded up; 1¼ tsp of salt exact.
        assertEquals("Take 4 pc ↑.", shown(2))
        assertEquals(listOf("3.", "Take 4 pc ($up)."), spoken(2))
        fewer(2)
        assertEquals("Servings: 3", servings())
        assertStepsReadAsTheList()
        // 2.25 eggs shown as 2, rounded down.
        assertEquals("Take 2 pc ↓.", shown(2))
        assertEquals(listOf("3.", "Take 2 pc ($down)."), spoken(2))
    }

    // SCALE-007 while an ingredient is fixed: 300 g of flour sets 3/2 — every reference follows the list.
    @Test
    fun whileAnIngredientIsFixedAReferenceReadsAsItsLine() {
        open()
        compose.onNodeWithTag(CardTags.fixAmount(0), useUnmergedTree = true).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(hasTestTag(CardTags.FIX)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag(CardTags.FIX_FIELD).performTextReplacement("300")
        compose.waitForIdle()
        compose.onNodeWithTag(CardTags.FIX_CONFIRM).performScrollTo().assertIsEnabled().performClick()
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(hasTestTag(CardTags.FIX)).fetchSemanticsNodes().isEmpty() }
        assertEquals("Servings: ≈6", servings())
        assertStepsReadAsTheList()
        assertEquals("Add 300 g flour", shown(0))
        // 4.5 eggs shown as 5, rounded up.
        assertEquals("Take 5 pc ↑.", shown(2))
    }

    // FLOW-032 (4), SCALE-005: a value held at its floor reads the floor, with the list's ↑.
    @Test
    fun aValueHeldAtItsFloorReadsTheFloor() {
        open()
        fewer(8)
        assertEquals("Servings: ¼", servings())
        assertStepsReadAsTheList()
        assertEquals("Take 1 pc ↑.", shown(2))
        assertEquals("Take ¼ tsp ↑.", shown(3))
        assertEquals(listOf("4.", "Take ¼ tsp ($up)."), spoken(3))
    }

    // FLOW-032 (6): "to taste" reads as such and is never marked; unscaled rows read as their lines read.
    @Test
    fun toTasteAndUnscaledRowsReadAsTheirLines() {
        open()
        more(4)
        assertEquals("Take to taste.", shown(4))
        assertEquals(listOf("5.", "Take to taste."), spoken(4))
        // FLOW-032: a row with no quantity yet reads as in the editor, the empty placeholder then its unit; with no
        // unit either, the placeholder alone. An off-grid row reads as its line, its unit alone, unscaled.
        assertEquals("Take ___ g.", shown(5))
        assertEquals("Take g.", shown(6))
        assertEquals("g", listQuantity(5))
        assertEquals("Take ___.", shown(7))
        assertStepsReadAsTheList()
    }

    private val flour = ingredients[0]

    private fun fixFlour(amount: String) {
        compose.onNodeWithTag(CardTags.fixAmount(0), useUnmergedTree = true).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(hasTestTag(CardTags.FIX)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag(CardTags.FIX_FIELD).performTextReplacement(amount)
        compose.waitForIdle()
        compose.onNodeWithTag(CardTags.FIX_CONFIRM).performScrollTo().assertIsEnabled().performClick()
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(hasTestTag(CardTags.FIX)).fetchSemanticsNodes().isEmpty() }
    }

    // A reference to an ingredient since deleted is gone: the step reads as typed. A step that is only a reference is
    // shown, numbered, and reads its quantity.
    @Test
    fun aDeletedIngredientsReferenceIsGoneAndAReferenceAloneIsAStep() {
        open(
            Recipe.create(now, null).copy(
                title = "Bread",
                servings = 4,
                ingredients = listOf(flour),
                steps = listOf(
                    Step(0, "Stir well", listOf(IngredientReference(9, 4))),
                    Step(1, "", listOf(IngredientReference(0, 0))),
                ),
            ),
        )
        assertEquals("Stir well", shown(0))
        assertEquals(listOf("1.", "Stir well"), spoken(0))
        assertEquals("200 g", shown(1))
        assertEquals(listOf("2.", "200 g"), spoken(1))
        more(4)
        assertEquals("Stir well", shown(0))
        assertEquals("400 g", shown(1))
    }

    // A recipe with no serving count shows none, and is scaled by a fixing all the same: the references follow.
    @Test
    fun aRecipeWithNoCountIsScaledByAFixingAndItsReferencesFollow() {
        open(
            Recipe.create(now, null).copy(
                title = "Bread",
                servings = null,
                ingredients = ingredients.take(3),
                steps = listOf(Step(0, "Add  flour", listOf(IngredientReference(0, 4)))) +
                    ingredients.take(3).map { Step(it.position + 1, "Take .", listOf(IngredientReference(it.position, 5))) },
            ),
        )
        assertEquals("Add 200 g flour", shown(0))
        fixFlour("100")
        assertEquals("Add 100 g flour", shown(0))
        // 1.5 eggs shown as 2, rounded up, as the list shows it.
        assertEquals("2 pc", listQuantity(1))
        assertEquals("Take 2 pc\u202F↑.", shown(2))
        assertEquals(listOf("3.", "Take 2 pc ($up)."), spoken(2))
        assertEquals("Take ½ tsp.", shown(3))
        assertEquals("½ tsp", listQuantity(2))
    }
}
