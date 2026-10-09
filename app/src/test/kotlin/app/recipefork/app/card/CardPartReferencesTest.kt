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
import app.recipefork.core.recipe.IngredientShare
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.InterfaceLanguage
import app.recipefork.core.scaling.Rational
import app.recipefork.core.scaling.Rounding
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * A step's reference to a part of an ingredient on the card: the part scaled by the card's factor and rounded on its
 * own grid — not taken from the list's line — with the same arrows and words as a whole reference, the list unchanged.
 */
@RunWith(AndroidJUnit4::class)
class CardPartReferencesTest {
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

    private val garlic = Ingredient(0, null, freeTextName = "Чеснок", unitType = UnitType.PIECE, unit = QuantityUnit.PIECE, quantity = Rational.of(4), divisible = false)
    private fun half(offset: Int) = IngredientReference(0, offset, IngredientShare(Rational.of(2), QuantityUnit.PIECE))

    /** Scenario 3: garlic for 4, half of it into the marinade, half into the sauce, all of it named in a third step. */
    private val recipe = Recipe.create(now, null).copy(
        title = "Курица",
        servings = 4,
        ingredients = listOf(garlic),
        steps = listOf(
            Step(0, "В маринад: ", listOf(half(11))),
            Step(1, "В соус: ", listOf(half(8))),
            Step(2, "Всего: ", listOf(IngredientReference(0, 7))),
        ),
    )

    private fun open() {
        runBlocking { recipes.save(recipe) }
        compose.setContent { MaterialTheme { CardScreen(recipe.id, recipes, environment, onBack = {}, onEdit = {}) } }
        compose.awaitCard()
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(hasTestTag(CardTags.step(2))).fetchSemanticsNodes().size == 1 }
    }

    private fun shown(position: Int): String =
        compose.onNodeWithTag(StepTags.text(position), useUnmergedTree = true).fetchSemanticsNode().config[ShownText]

    private fun spoken(position: Int): List<String> = compose.textsOf(CardTags.step(position))
    private fun listQuantity(): String? = compose.textsOf(CardTags.ingredient(0)).getOrNull(1)
    private fun servings() = compose.textsOf(CardTags.SERVINGS).single()
    private fun more(times: Int) = repeat(times) { compose.onNodeWithTag(CardTags.SERVINGS_MORE).performScrollTo().performClick() }
    private fun fewer(times: Int) = repeat(times) { compose.onNodeWithTag(CardTags.SERVINGS_FEWER).performScrollTo().performClick() }

    // Scenario 3: 2 and 2 of 4 cloves; at double servings 4 and 4, while the list shows 8.
    @Test
    @Config(qualifiers = "ru")
    fun partsReadTheirShareAtTheCardsScale() {
        open()
        assertEquals("В маринад: 2 шт", shown(0))
        assertEquals("В соус: 2 шт", shown(1))
        assertEquals("Всего: 4 шт", shown(2))
        assertEquals("4 шт", listQuantity())
        more(4)
        assertEquals("Порций: 8", servings())
        assertEquals("В маринад: 4 шт", shown(0))
        assertEquals("В соус: 4 шт", shown(1))
        assertEquals("Всего: 8 шт", shown(2))
        assertEquals("8 шт", listQuantity())
    }

    // A part is rounded on its own: at 3 servings the whole is exactly 3, while 1½ of a part reads 2, rounded up,
    // with the arrow and its words as a whole reference carries them.
    @Test
    fun aPartIsRoundedOnItsOwnWithTheSameMarks() {
        open()
        fewer(1)
        assertEquals("Servings: 3", servings())
        assertEquals("3 pc", listQuantity())
        assertEquals("Всего: 3 pc", shown(2))
        assertEquals("В маринад: 2 pc\u202F↑", shown(0))
        assertEquals(listOf("1.", "В маринад: 2 pc (rounded up, exact value was lower)"), spoken(0))
    }

    // The references read as a timer names them: the part's text and rounding, and the whole's beside it.
    @Test
    fun theReferencesOfACardTellAPartFromTheWhole() {
        val list = ingredientList(listOf(garlic), emptyMap(), InterfaceLanguage.ENGLISH, 4, Rational.of(3))
        val references = CardReferences.of(listOf(garlic), list, InterfaceLanguage.ENGLISH, emptyMap())
        assertEquals("2 pc", references.quantity(half(0)))
        assertEquals(Rounding.UP, references.rounding(half(0)))
        assertEquals("3 pc", references.quantity(IngredientReference(0, 0)))
        assertEquals(Rounding.NONE, references.rounding(IngredientReference(0, 0)))
        // A part of a row the card cannot scale reads as the editor writes it.
        val unfinished = garlic.copy(divisible = null)
        val unscaled = CardReferences.of(listOf(unfinished), ingredientList(listOf(unfinished), emptyMap(), InterfaceLanguage.ENGLISH, 4, Rational.of(8)), InterfaceLanguage.ENGLISH, emptyMap())
        assertEquals("2 pc", unscaled.quantity(half(0)))
        assertEquals(Rounding.NONE, unscaled.rounding(half(0)))
    }

    // SCALE-002: while an ingredient is fixed, a part is scaled by the factor the fixing sets, not the count's: 400 g
    // of flour fixed at 200 g makes a 100 g part read 50 g, and a part of another row follows the same factor.
    @Test
    fun aPartFollowsTheFactorAFixingSets() {
        val flour = Ingredient(0, null, freeTextName = "Flour", unitType = UnitType.EXACT, unit = QuantityUnit.GRAM, quantity = Rational.of(400))
        val cloves = garlic.copy(position = 1)
        val bread = Recipe.create(now, null).copy(
            title = "Bread",
            servings = 4,
            ingredients = listOf(flour, cloves),
            steps = listOf(
                Step(0, "Dust with ", listOf(IngredientReference(0, 10, IngredientShare(Rational.of(100), QuantityUnit.GRAM)))),
                Step(1, "Rub with ", listOf(IngredientReference(1, 9, IngredientShare(Rational.of(2), QuantityUnit.PIECE)))),
            ),
        )
        runBlocking { recipes.save(bread) }
        compose.setContent { MaterialTheme { CardScreen(bread.id, recipes, environment, onBack = {}, onEdit = {}) } }
        compose.awaitCard()
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(hasTestTag(CardTags.step(1))).fetchSemanticsNodes().size == 1 }
        assertEquals("Dust with 100 g", shown(0))
        assertEquals("Rub with 2 pc", shown(1))
        compose.onNodeWithTag(CardTags.fixAmount(0), useUnmergedTree = true).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(hasTestTag(CardTags.FIX)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag(CardTags.FIX_FIELD).performTextReplacement("200")
        compose.waitForIdle()
        compose.onNodeWithTag(CardTags.FIX_CONFIRM).performScrollTo().assertIsEnabled().performClick()
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(hasTestTag(CardTags.FIX)).fetchSemanticsNodes().isEmpty() }
        assertEquals("Servings: ≈2", servings())
        assertEquals("Dust with 50 g", shown(0))
        assertEquals("Rub with 1 pc", shown(1))
    }
}
