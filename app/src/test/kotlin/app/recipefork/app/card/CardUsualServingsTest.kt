package app.recipefork.app.card

import app.recipefork.app.data.HeldLastCardTab
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
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
import app.recipefork.app.data.HeldUsualServings
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.editor.SAMPLE_DICTIONARIES
import app.recipefork.app.editor.TestDevice
import app.recipefork.app.editor.textsOf
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeId
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
import java.time.Instant

/**
 * The card opens at the reader's usual serving count (FLOW-017 (1–3, 5)): a recipe authored for another count opens
 * recalculated and marked so, one never set opens as authored, a change of count on the card leaves the setting as it
 * is, and a fixing released returns to the usual count it was taken from (SCALE-017).
 */
@RunWith(AndroidJUnit4::class)
class CardUsualServingsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val device = TestDevice(context)
    private val recipes = RecipeRepository(ActiveBook(BookName.Pool, book.database))
    private val usual = HeldUsualServings()
    private val environment = CardEnvironment(usual, lastTab = HeldLastCardTab(CardMode.COOKING)) { device.database.dictionaryDao().ingredients() }
    private val now = Instant.parse("2026-10-03T12:00:00Z")

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

    /** Chicken, indivisible eggs and salt, authored for [servings]. */
    private fun recipe(servings: Int?): Recipe = Recipe.create(now, null).copy(
        title = "Omelette",
        servings = servings,
        ingredients = listOf(
            Ingredient(0, null, freeTextName = "Chicken", unitType = UnitType.EXACT, unit = QuantityUnit.GRAM, quantity = Rational.of(500)),
            Ingredient(1, null, freeTextName = "Eggs", unitType = UnitType.PIECE, unit = QuantityUnit.PIECE, quantity = Rational.of(3), divisible = false),
            Ingredient(2, null, freeTextName = "Salt", unitType = UnitType.HOUSEHOLD, unit = QuantityUnit.TEASPOON, quantity = Rational.ONE),
        ),
    ).also { runBlocking { recipes.save(it) } }

    private var shown by mutableStateOf<RecipeId?>(null)
    private var opened by mutableStateOf(0)
    private var composed = false

    /** Opens the card on [id]; every call opens it anew, as leaving and coming back does. */
    private fun show(id: RecipeId) {
        if (!composed) {
            composed = true
            compose.setContent {
                MaterialTheme {
                    shown?.let { key(it, opened) { CardScreen(it, recipes, environment, onBack = {}, onEdit = {}) } }
                }
            }
        }
        compose.runOnIdle { shown = id; opened++ }
        compose.waitForIdle()
        compose.awaitCard()
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(hasTestTag(CardTags.ingredient(0))).fetchSemanticsNodes().size == 1 }
    }

    private fun servings() = compose.textsOf(CardTags.SERVINGS).single()
    private fun lines() = (0..2).map { compose.textsOf(CardTags.ingredient(it)) }
    private fun more(times: Int = 1) = repeat(times) { compose.onNodeWithTag(CardTags.SERVINGS_MORE).performScrollTo().performClick() }
    private fun fewer(times: Int = 1) = repeat(times) { compose.onNodeWithTag(CardTags.SERVINGS_FEWER).performScrollTo().performClick() }

    /** Whether the section line that says the quantities are recalculated is shown (SCALE-004 (1)). */
    private fun recalculated() = compose.onAllNodes(hasTestTag(IngredientTags.RECALCULATED)).fetchSemanticsNodes().isNotEmpty()

    private fun awaitRecalculated(shown: Boolean) = compose.waitUntil(PATIENCE_MS) { recalculated() == shown }

    private val up = "rounded up, exact value was lower"
    private val authoredForFour = listOf(listOf("Chicken", "500 g"), listOf("Eggs", "3 pc"), listOf("Salt", "1 tsp"))
    private val atTwo = listOf(listOf("Chicken", "250 g"), listOf("Eggs", "2 pc", up), listOf("Salt", "½ tsp"))

    // FLOW-017 (1), SCALE-004: with the setting at 2, a recipe for 4 opens at 2, recalculated and marked.
    @Test
    fun aRecipeForFourOpensAtTheUsualTwoMarkedAsRecalculated() {
        usual.set(Rational.of(2))
        show(recipe(4).id)
        assertEquals("Servings: 2", servings())
        awaitRecalculated(true)
        assertEquals(atTwo, lines())
    }

    // FLOW-017 (1): the usual count is a count as requested — a fraction too — and a recipe for 1 is scaled up to it.
    @Test
    fun anyRecipeOpensAtTheUsualCountWhateverItWasAuthoredFor() {
        usual.set(Rational.of(2))
        show(recipe(1).id)
        assertEquals("Servings: 2", servings())
        assertEquals(listOf("Chicken", "1 kg"), lines()[0])
        awaitRecalculated(true)
        usual.set(Rational.of(1, 2))
        show(recipe(4).id)
        assertEquals("Servings: ½", servings())
        awaitRecalculated(true)
    }

    // FLOW-017 (2): never set, a recipe opens at its authored count with nothing marked.
    @Test
    fun neverSetARecipeOpensAsAuthoredWithNothingMarked() {
        show(recipe(4).id)
        assertEquals("Servings: 4", servings())
        assertEquals(authoredForFour, lines())
        assertTrue(!recalculated())
    }

    // FLOW-017 (1, 2): the usual count equal to the authored one marks nothing.
    @Test
    fun theUsualCountEqualToTheAuthoredOneMarksNothing() {
        usual.set(Rational.of(4))
        show(recipe(4).id)
        assertEquals("Servings: 4", servings())
        assertEquals(authoredForFour, lines())
        assertTrue(!recalculated())
    }

    // FLOW-017 (3, 5): a change of count on the card leaves the setting; back at the authored count nothing is marked;
    // the card opened again starts at the usual count.
    @Test
    fun changingTheCountLeavesTheSettingAndTheAuthoredCountShowsNoMarks() {
        usual.set(Rational.of(2))
        val forFour = recipe(4)
        show(forFour.id)
        awaitRecalculated(true)
        more(2)
        assertEquals("Servings: 4", servings())
        awaitRecalculated(false)
        assertEquals(authoredForFour, lines())
        assertEquals(Rational.of(2), usual.count.value)
        more(3)
        fewer()
        assertEquals("Servings: 6", servings())
        assertEquals(Rational.of(2), usual.count.value)
        show(forFour.id)
        assertEquals("Servings: 2", servings())
    }

    // A recipe with no serving count ignores the setting: no count, every quantity as authored.
    @Test
    fun aRecipeWithNoServingCountIgnoresTheSetting() {
        usual.set(Rational.of(2))
        show(recipe(null).id)
        compose.onNodeWithTag(CardTags.SERVINGS).assertDoesNotExist()
        assertEquals(authoredForFour, lines())
        assertTrue(!recalculated())
    }

    // SCALE-002, SCALE-017: a fixing works from the usual count, and releasing it returns to that count.
    @Test
    fun aFixingReleasedReturnsToTheUsualCount() {
        usual.set(Rational.of(2))
        show(recipe(4).id)
        compose.onNodeWithTag(CardTags.fixAmount(0), useUnmergedTree = true).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(hasTestTag(CardTags.FIX)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag(CardTags.FIX_FIELD).performTextReplacement("1000")
        compose.waitForIdle()
        compose.onNodeWithTag(CardTags.FIX_CONFIRM).performScrollTo().assertIsEnabled().performClick()
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(hasTestTag(CardTags.FIX)).fetchSemanticsNodes().isEmpty() }
        // 1 kg of the 500 g chicken: the recipe for 8, whatever count was in force.
        assertEquals("Servings: ≈8", servings())
        assertEquals(listOf("Salt", "2 tsp"), lines()[2].take(2))
        compose.onNodeWithTag(CardTags.fixAmount(0), useUnmergedTree = true).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(hasTestTag(CardTags.FIX)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag(CardTags.FIX_RELEASE).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(hasTestTag(CardTags.FIX)).fetchSemanticsNodes().isEmpty() }
        assertEquals("Servings: 2", servings())
        assertEquals(atTwo, lines())
        assertEquals(Rational.of(2), usual.count.value)
    }

    // The section line is read in words by the screen reader as well (A11Y-005), at the usual count as at any other.
    @Test
    fun theRecalculatedLineIsReadInWords() {
        usual.set(Rational.of(2))
        show(recipe(4).id)
        awaitRecalculated(true)
        val spoken = compose.onAllNodes(hasTestTag(IngredientTags.RECALCULATED)).fetchSemanticsNodes()
            .single().config[SemanticsProperties.Text].joinToString("") { it.text }
        assertEquals(
            "Quantities are recalculated. An up arrow marks a quantity rounded up, a down arrow one rounded down.",
            spoken,
        )
    }
}
