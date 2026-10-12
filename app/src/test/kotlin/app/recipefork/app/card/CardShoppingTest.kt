package app.recipefork.app.card

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.data.HeldLastCardTab
import app.recipefork.app.data.HeldThemeSetting
import app.recipefork.app.data.HeldUsualServings
import app.recipefork.app.dictionary.TagEntity
import app.recipefork.app.dictionary.TagSource
import app.recipefork.app.editor.EditorEnvironment
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.editor.textsOf
import app.recipefork.app.library.LibrarySession
import app.recipefork.app.library.LibraryTags
import app.recipefork.app.library.LibraryView
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.shopping.HeldShopping
import app.recipefork.app.shopping.ListedRecipe
import app.recipefork.app.shopping.ShoppingEnvironment
import app.recipefork.app.shopping.ShoppingList
import app.recipefork.app.shopping.ShoppingTags
import app.recipefork.app.ui.AppNavHost
import app.recipefork.core.recipe.Ingredient
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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * v0.32, T-230 — adding a recipe to the shopping list from its card (FLOW-074, scenarios 1 and 6):
 *
 * - The card's ⋮, on either tab, always offers "Add to shopping list", or "Update in shopping list" once the recipe is
 *   on it; it adds the recipe at the count the card shows — the count a fixed amount implies while one is fixed — and a
 *   recipe with no count with none.
 * - The card's snackbar says "Added to shopping list" or "Shopping list updated", with Open, which opens the list; adding
 *   again sets the recipe's count where it stands on the list, never adding it twice.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "en-w360dp-h640dp")
class CardShoppingTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val active = ActiveBook(BookName.Pool, book.database)
    private val recipes = RecipeRepository(active)
    private val now = Instant.parse("2026-10-12T12:00:00Z")
    private val pool = BookName.Pool.fileName
    private var store = HeldShopping()
    private var opened = 0

    /** Soup for 4: 300 g of flour and 100 g of butter. */
    private val soup = soup(servings = 4)

    private fun soup(servings: Int?) = Recipe.create(now, null).copy(
        title = "Soup",
        servings = servings,
        ingredients = listOf(
            Ingredient(0, null, freeTextName = "Flour", unitType = UnitType.EXACT, unit = QuantityUnit.GRAM, quantity = Rational.of(300)),
            Ingredient(1, null, freeTextName = "Butter", unitType = UnitType.EXACT, unit = QuantityUnit.GRAM, quantity = Rational.of(100)),
        ),
        steps = listOf(Step(0, "Cook it.")),
    )

    @Before
    fun setUp() = compose.registerIdlingResource(book.idling)

    @After
    fun close() {
        compose.unregisterIdlingResource(book.idling)
        book.close()
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    /** Opens the card of [recipe] on [tab], its scale as the [session] holds it. */
    private fun open(recipe: Recipe, tab: CardMode = CardMode.VIEW, session: CardSession = TextCardSession.inMemory()) {
        runBlocking { recipes.save(recipe) }
        val environment = CardEnvironment(lastTab = HeldLastCardTab(tab), shopping = store, ingredients = IngredientEntries { emptyList() })
        compose.setContent {
            MaterialTheme {
                CardScreen(recipe.id, recipes, environment, onBack = {}, onEdit = {}, session = session, onOpenShopping = { opened++ })
            }
        }
        compose.awaitCard()
        compose.waitUntil(PATIENCE_MS) { exists(CardTags.step(0)) }
        compose.waitForIdle()
    }

    private fun tap(tag: String) {
        compose.onNodeWithTag(tag).performClick()
        compose.waitForIdle()
    }

    private fun servingsShown(text: String) = compose.waitUntil(PATIENCE_MS) { compose.textsOf(CardTags.SERVINGS) == listOf(text) }

    /** The ⋮'s shopping-list action as it reads, the menu closed again. */
    private fun shoppingItem(): String {
        compose.openTheCardMenu()
        val text = compose.textsOf(CardTags.SHOPPING).single()
        compose.closeTheCardMenu()
        return text
    }

    /** Takes the ⋮'s shopping-list action, and waits for the list to hold what [until] waits for. */
    private fun addFromTheMenu(until: (ShoppingList) -> Boolean) {
        compose.openTheCardMenu()
        compose.onNodeWithTag(CardTags.SHOPPING).performClick()
        compose.waitUntil(PATIENCE_MS) { until(store.list.value) }
    }

    private fun inSnackbar(text: String) = hasText(text) and hasAnyAncestor(hasTestTag(CardTags.SNACKBAR))

    private fun awaitStatement(text: String, action: String) = compose.waitUntil(PATIENCE_MS) {
        compose.onAllNodes(inSnackbar(text), useUnmergedTree = true).fetchSemanticsNodes().size == 1 &&
            compose.onAllNodes(inSnackbar(action), useUnmergedTree = true).fetchSemanticsNodes().size == 1
    }

    // Scenario 1: set to six servings, the ⋮'s "Add to shopping list" puts the recipe on the list at six, and the card
    // says so with Open, which opens the list.
    @Test
    fun theMenuAddsTheRecipeAtTheCountShownAndOffersToOpenTheList() {
        open(soup)
        servingsShown("Servings: 4")
        tap(CardTags.SERVINGS_MORE)
        tap(CardTags.SERVINGS_MORE)
        servingsShown("Servings: 6")
        compose.openTheCardMenu()
        assertEquals(listOf("Add to shopping list"), compose.textsOf(CardTags.SHOPPING))
        compose.assertCardTarget(CardTags.SHOPPING)
        compose.closeTheCardMenu()

        addFromTheMenu { it.recipes.isNotEmpty() }
        assertEquals(listOf(ListedRecipe(pool, soup.id, Rational.of(6))), store.list.value.recipes)
        awaitStatement("Added to shopping list", "Open")
        compose.onNode(inSnackbar("Open"), useUnmergedTree = true).performClick()
        compose.waitUntil(PATIENCE_MS) { opened == 1 }
    }

    // Scenario 6 (part): on the list already — beside another recipe — the ⋮ offers to update it; doing so sets its
    // count where it stands, never adding it twice, and says so. Cooking's ⋮ offers it as View's does.
    @Test
    fun addingAgainUpdatesTheCountRatherThanDoublingIt() {
        val other = ListedRecipe(pool, RecipeId("another"), Rational.of(2))
        store = HeldShopping(ShoppingList(listOf(other)))
        open(soup, CardMode.COOKING)
        assertEquals("Add to shopping list", shoppingItem())
        addFromTheMenu { it.recipes.size == 2 }
        assertEquals(listOf(other, ListedRecipe(pool, soup.id, Rational.of(4))), store.list.value.recipes)
        awaitStatement("Added to shopping list", "Open")

        compose.waitForIdle()
        assertEquals("Update in shopping list", shoppingItem())
        tap(CardTags.SERVINGS_FEWER)
        servingsShown("Servings: 3")
        addFromTheMenu { list -> list.recipes.any { it.recipe == soup.id && it.servings == Rational.of(3) } }
        assertEquals(listOf(other, ListedRecipe(pool, soup.id, Rational.of(3))), store.list.value.recipes)
        awaitStatement("Shopping list updated", "Open")
        // The same count again changes nothing, and still says the list is updated.
        addFromTheMenu { it.recipes.size == 2 }
        assertEquals(listOf(other, ListedRecipe(pool, soup.id, Rational.of(3))), store.list.value.recipes)
        assertEquals("Update in shopping list", shoppingItem())
        assertEquals(0, opened)
    }

    // While an amount is fixed, the recipe goes on the list at the count that amount implies, as the card shows it.
    @Test
    fun aFixedAmountAddsTheCountItImplies() {
        val session = TextCardSession.inMemory()
        // 600 g of the flour the soup takes 300 g of for four: eight servings.
        val fixed = CardScale(Rational.of(4)).fix(CardFixing(0, Rational.of(600), QuantityUnit.GRAM))
        session.holdScale(soup.id, HeldScale.of(fixed, soup.ingredients))
        open(soup, session = session)
        servingsShown("Servings: ≈8")
        addFromTheMenu { it.recipes.isNotEmpty() }
        assertEquals(listOf(ListedRecipe(pool, soup.id, Rational.of(8))), store.list.value.recipes)
    }

    // A recipe with no serving count goes on the list with none, which the list takes as authored.
    @Test
    fun aRecipeWithNoCountIsAddedWithNone() {
        val uncounted = soup(servings = null)
        open(uncounted)
        assertFalse(exists(CardTags.SERVINGS))
        addFromTheMenu { it.recipes.isNotEmpty() }
        val listed = store.list.value.recipes.single()
        assertEquals(uncounted.id, listed.recipe)
        assertNull(listed.servings)
        awaitStatement("Added to shopping list", "Open")
    }

    @Test
    @Config(qualifiers = "ru-w360dp-h640dp")
    fun inRussianTheMenuAndTheNoticeSaySo() {
        open(soup)
        assertEquals("Добавить в список покупок", shoppingItem())
        addFromTheMenu { it.recipes.isNotEmpty() }
        awaitStatement("Добавлено в список покупок", "Открыть")
        compose.waitForIdle()
        assertEquals("Обновить в списке покупок", shoppingItem())
        tap(CardTags.SERVINGS_MORE)
        addFromTheMenu { list -> list.recipes.single().servings == Rational.of(5) }
        awaitStatement("Список покупок обновлён", "Открыть")
    }

    // Scenario 1 through the application: Open shows the list, holding the six-serving quantities, in place of the
    // card; back from it returns to the book, as the list's back says.
    @Test
    fun openShowsTheListInPlaceOfTheCardAndBackReturnsToTheBook() {
        runBlocking { recipes.save(soup) }
        val session = LibrarySession(SavedStateHandle())
        session.choose(LibraryView.OWN)
        val card = CardEnvironment(HeldUsualServings(), shopping = store, ingredients = IngredientEntries { emptyList() })
        val editor = EditorEnvironment(
            active,
            object : TagSource {
                override suspend fun tags() = emptyList<TagEntity>()
            },
        ) { now }
        val shopping = ShoppingEnvironment(store, ingredients = IngredientEntries { emptyList() })
        compose.setContent {
            MaterialTheme {
                AppNavHost(session, recipes, card = card, editor = editor, themeSetting = HeldThemeSetting(), shopping = shopping)
            }
        }
        compose.waitUntil(PATIENCE_MS) { exists(LibraryTags.TOP_BAR) }
        compose.runOnUiThread { session.openCard(soup.id.value, LibraryView.OWN) }
        compose.awaitCard()
        compose.waitUntil(PATIENCE_MS) { exists(CardTags.SERVINGS) }
        tap(CardTags.SERVINGS_MORE)
        tap(CardTags.SERVINGS_MORE)
        servingsShown("Servings: 6")
        addFromTheMenu { it.recipes.isNotEmpty() }
        awaitStatement("Added to shopping list", "Open")
        compose.onNode(inSnackbar("Open"), useUnmergedTree = true).performClick()
        compose.waitUntil(PATIENCE_MS) { exists(ShoppingTags.SCREEN) && !exists(CardTags.SCREEN) && !exists(ShoppingTags.LOADING) }
        assertEquals(true, session.shopping.value)
        assertNull(session.viewing.value)
        // 300 g of flour and 100 g of butter for four, for six.
        compose.waitUntil(PATIENCE_MS) {
            listOf("450 g", "150 g").all { compose.onAllNodes(hasText(it, substring = true)).fetchSemanticsNodes().isNotEmpty() }
        }
        compose.onNodeWithTag(ShoppingTags.BACK).performClick()
        compose.waitUntil(PATIENCE_MS) { exists(LibraryTags.TOP_BAR) && !exists(ShoppingTags.SCREEN) }
        assertFalse(session.shopping.value)
        assertFalse(exists(CardTags.SCREEN))
    }
}
