package app.recipefork.app.library

import android.content.Context
import android.os.Looper
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.Mode
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.card.CardEnvironment
import app.recipefork.app.card.IngredientEntries
import app.recipefork.app.dictionary.IngredientEntryEntity
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.editor.textsOf
import app.recipefork.app.file.RecipeImport
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeState
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.Rational
import app.recipefork.core.search.BookOrder
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList

/**
 * FLOW-035 (3, 4): the search field and the order at the top of each view of the recipe book. The screen is tall
 * enough for every entry of a test to be laid out at once, so that what a view lists is read whole, in its order.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "h2000dp")
class LibrarySearchTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val recipes = RecipeRepository(ActiveBook(BookName.Pool, book.database))
    private val session = LibrarySession(SavedStateHandle())
    private val now = Instant.parse("2026-10-01T12:00:00Z")

    private val chicken = IngredientEntryEntity(1, "Курица", "Chicken", null)
    private val rice = IngredientEntryEntity(2, "Рис", "Rice", null)
    /** Whether each reading of the dictionary, which the search does as it builds its index, was off the main thread. */
    private val dictionaryReads = CopyOnWriteArrayList<Boolean>()
    private val card = CardEnvironment(
        ingredients = IngredientEntries {
            dictionaryReads += Looper.myLooper() != Looper.getMainLooper()
            listOf(chicken, rice)
        },
    )

    @Before
    fun waitForTheBook() = compose.registerIdlingResource(book.idling)

    @After
    fun close() {
        compose.unregisterIdlingResource(book.idling)
        book.close()
    }

    private fun ingredient(position: Int, entry: IngredientEntryEntity?, qualifier: String? = null, free: String? = null) =
        Ingredient(position, entry?.id, qualifier, free, UnitType.EXACT, QuantityUnit.GRAM, Rational.of(100), null, null)

    /** An own recipe, last changed [minutesAgo] minutes before now. */
    private fun own(title: String, vararg ingredients: Ingredient, minutesAgo: Long = 0): Recipe {
        val recipe = Recipe.create(now.minusSeconds(minutesAgo * 60), owner = null)
            .copy(title = title, ingredients = ingredients.toList())
        runBlocking { recipes.save(recipe) }
        return recipe
    }

    /** A found recipe, marked [minutesAgo] minutes before now. */
    private fun found(title: String, vararg ingredients: Ingredient, minutesAgo: Long = 0): Recipe {
        val recipe = Recipe.create(now, owner = null).copy(title = title, ingredients = ingredients.toList())
            .inState(RecipeImport.FOUND_STATE)
        runBlocking { recipes.addFound(recipe, now.minusSeconds(minutesAgo * 60)) }
        return recipe
    }

    private fun show(view: LibraryView, mode: Mode = Mode.Standard) {
        session.choose(view)
        compose.setContent { MaterialTheme { LibraryScreen(session, recipes, mode, card = card) } }
        compose.waitForIdle()
    }

    private fun choose(view: LibraryView) {
        compose.onNodeWithTag(LibraryTags.tab(view)).performClick()
        compose.waitForIdle()
    }

    private val anEntryTitle = SemanticsMatcher("an entry's title") {
        it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("library-entry-title-") == true
    }

    /** The titles the view lists, in its order. */
    private fun listed(): List<String> = compose.onAllNodes(anEntryTitle, useUnmergedTree = true).fetchSemanticsNodes()
        .map { node -> node.config[SemanticsProperties.Text].joinToString("") { it.text } }

    /** Waits until the view lists [titles], in that order, and fails naming what it lists if it never does. */
    private fun assertListed(vararg titles: String) {
        runCatching { compose.waitUntil(PATIENCE_MS) { listed() == titles.toList() } }
        assertEquals(titles.toList(), listed())
    }

    private fun search(query: String) {
        compose.onNodeWithTag(LibraryTags.SEARCH).performTextReplacement(query)
        compose.waitForIdle()
    }

    private fun typed(): String =
        compose.onNodeWithTag(LibraryTags.SEARCH).fetchSemanticsNode().config.getOrNull(SemanticsProperties.EditableText)
            ?.text.orEmpty()

    private fun orderBy(order: BookOrder) {
        compose.onNodeWithTag(LibraryTags.ORDER).performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.order(order)), PATIENCE_MS)
        compose.onNodeWithTag(LibraryTags.order(order)).performClick()
        compose.waitForIdle()
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    /** The book of the scenario: chicken by its title, chicken among its ingredients only, and neither. */
    private fun chickenBook() {
        own("Курица с рисом", ingredient(0, rice), minutesAgo = 1)
        own("Плов по-домашнему", ingredient(0, rice), ingredient(1, chicken, qualifier = "бедро"), minutesAgo = 2)
        own("Борщ", ingredient(0, null, free = "Свёкла"), minutesAgo = 3)
        own("Окурок бабушкин", minutesAgo = 4)
    }

    // FLOW-035 (4), scenario 2: "кур" finds the title and the recipe with chicken among its ingredients only.
    @Test
    fun aQueryFindsATitleOrAnIngredientWithAWordBeginningWithIt() {
        chickenBook()
        show(LibraryView.OWN)
        assertListed("Курица с рисом", "Плов по-домашнему", "Борщ", "Окурок бабушкин")
        search("кур")
        assertListed("Курица с рисом", "Плов по-домашнему")
        // The dictionary entry is named in both languages, with its qualifier.
        search("chick")
        assertListed("Плов по-домашнему")
        search("курица бедр")
        assertListed("Плов по-домашнему")
        // A row's free text.
        search("свекл")
        assertListed("Борщ")
        // Every word must begin a word of the title or of one ingredient's name.
        search("рис кур")
        assertListed("Курица с рисом")
    }

    // FLOW-035 (4): case and ё/е do not matter, on either side.
    @Test
    fun caseAndYoDoNotMatter() {
        chickenBook()
        show(LibraryView.OWN)
        search("КУР")
        assertListed("Курица с рисом", "Плов по-домашнему")
        search("СВЁК")
        assertListed("Борщ")
        search("бОрЩ")
        assertListed("Борщ")
    }

    // FLOW-035 (4): a query searches the open view only, and each view keeps its own.
    @Test
    fun aQuerySearchesTheOpenViewOnly() {
        own("Курица с рисом", minutesAgo = 1)
        found("Куриный суп", minutesAgo = 1)
        found("Щи", ingredient(0, chicken), minutesAgo = 2)
        found("Блины", minutesAgo = 3)
        show(LibraryView.OWN)
        search("кур")
        assertListed("Курица с рисом")
        choose(LibraryView.MARKED)
        // The marked view has its own query, empty as yet.
        assertEquals("", typed())
        assertListed("Куриный суп", "Щи", "Блины")
        search("кур")
        assertListed("Куриный суп", "Щи")
        search("блин")
        assertListed("Блины")
        choose(LibraryView.OWN)
        assertEquals("кур", typed())
        assertListed("Курица с рисом")
    }

    // FLOW-035 (4), in English: an empty result says so and offers to clear the search.
    @Test
    fun anEmptyResultSaysSoAndOffersToClearTheSearch() = emptyResult("Nothing found for “zzz”", "Clear the search")

    // The same, in Russian.
    @Test
    @Config(qualifiers = "ru-h2000dp")
    fun anEmptyResultSaysSoAndOffersToClearTheSearchInRussian() =
        emptyResult("По запросу «zzz» ничего не найдено", "Очистить поиск")

    private fun emptyResult(said: String, clear: String) {
        chickenBook()
        show(LibraryView.OWN)
        search("  zzz ")
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.NOTHING_FOUND), PATIENCE_MS)
        assertEquals(listOf(said), compose.textsOf(LibraryTags.NOTHING_FOUND))
        assertEquals(listOf(clear), compose.textsOf(LibraryTags.NOTHING_FOUND_CLEAR))
        assertListed()
        // The book still offers to write a recipe: it is not empty, only the search found nothing.
        assertEquals(false, exists(LibraryTags.OWN_EMPTY))
        compose.onNodeWithTag(LibraryTags.NOTHING_FOUND_CLEAR).performClick()
        compose.waitForIdle()
        assertEquals("", typed())
        assertListed("Курица с рисом", "Плов по-домашнему", "Борщ", "Окурок бабушкин")
        assertEquals(false, exists(LibraryTags.NOTHING_FOUND))
        // The same of the marked view, and the field's own clearing control.
        found("Щи")
        choose(LibraryView.MARKED)
        search("zzz")
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.NOTHING_FOUND), PATIENCE_MS)
        assertEquals(listOf(said), compose.textsOf(LibraryTags.NOTHING_FOUND))
        assertEquals(listOf(clear), compose.textsOf(LibraryTags.NOTHING_FOUND_CLEAR))
        compose.onNodeWithTag(LibraryTags.SEARCH_CLEAR).performClick()
        compose.waitForIdle()
        assertListed("Щи")
    }

    // An empty view with no query keeps its existing empty state, with no header to search nothing with.
    @Test
    fun anEmptyViewWithNoQueryKeepsItsEmptyState() {
        show(LibraryView.OWN, Mode.Serverless)
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.OWN_EMPTY), PATIENCE_MS)
        assertEquals(false, exists(LibraryTags.HEADER))
        choose(LibraryView.MARKED)
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.MARKED_EMPTY), PATIENCE_MS)
        assertEquals(false, exists(LibraryTags.HEADER))
        assertEquals(false, exists(LibraryTags.NOTHING_FOUND))
    }

    // FLOW-035 (3): the own view, newest first by default — by its last change of content — oldest first, or by
    // title, alphabetical in English.
    @Test
    fun theOwnViewIsOrderedNewestOldestOrByTitle() {
        own("banana bread", minutesAgo = 1)
        own("Apple pie", minutesAgo = 2)
        own("cherry jam", minutesAgo = 3)
        show(LibraryView.OWN)
        assertListed("banana bread", "Apple pie", "cherry jam")
        orderBy(BookOrder.OLDEST)
        assertListed("cherry jam", "Apple pie", "banana bread")
        orderBy(BookOrder.TITLE)
        assertListed("Apple pie", "banana bread", "cherry jam")
        orderBy(BookOrder.NEWEST)
        assertListed("banana bread", "Apple pie", "cherry jam")
    }

    // FLOW-035 (3): by title in the Russian collation, ё read as е; the order holds under a query too.
    @Test
    @Config(qualifiers = "ru-h2000dp")
    fun byTitleIsAlphabeticalInTheInterfaceLanguageWithYoAsYe() {
        own("Ёжики", minutesAgo = 1)
        own("Яблочный пирог", minutesAgo = 2)
        own("Ежевичный морс", minutesAgo = 3)
        own("Арбузный салат", minutesAgo = 4)
        own("Жаркое", minutesAgo = 5)
        show(LibraryView.OWN)
        orderBy(BookOrder.TITLE)
        assertListed("Арбузный салат", "Ежевичный морс", "Ёжики", "Жаркое", "Яблочный пирог")
        search("е")
        assertListed("Ежевичный морс", "Ёжики")
    }

    // FLOW-035 (3): each view keeps its own order.
    @Test
    fun eachViewKeepsItsOwnOrder() {
        own("Бефстроганов", minutesAgo = 1)
        own("Азу", minutesAgo = 2)
        found("Щи", minutesAgo = 1)
        found("Борщ", minutesAgo = 2)
        show(LibraryView.OWN)
        orderBy(BookOrder.TITLE)
        assertListed("Азу", "Бефстроганов")
        choose(LibraryView.MARKED)
        // The marked view, the last marked first, as it was.
        assertListed("Щи", "Борщ")
        compose.onNodeWithTag(LibraryTags.ORDER).performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.order(BookOrder.NEWEST)), PATIENCE_MS)
        compose.onNodeWithTag(LibraryTags.order(BookOrder.NEWEST)).assertIsSelected()
        compose.onNodeWithTag(LibraryTags.order(BookOrder.TITLE)).assertIsNotSelected()
        compose.onNodeWithTag(LibraryTags.order(BookOrder.OLDEST)).performClick()
        compose.waitForIdle()
        assertListed("Борщ", "Щи")
        choose(LibraryView.OWN)
        assertListed("Азу", "Бефстроганов")
        assertEquals(BookOrder.TITLE, session.order(LibraryView.OWN).value)
        assertEquals(BookOrder.OLDEST, session.order(LibraryView.MARKED).value)
    }

    // The search runs off the main thread, where the book's queries run: the dictionary it names ingredients by is read
    // there, once, however many queries follow.
    @Test
    fun theSearchRunsOffTheMainThread() {
        chickenBook()
        show(LibraryView.OWN)
        search("кур")
        assertListed("Курица с рисом", "Плов по-домашнему")
        search("рис")
        assertListed("Курица с рисом", "Плов по-домашнему")
        assertEquals(listOf(true), dictionaryReads)
    }

    // The list updates as one types, a letter at a time.
    @Test
    fun theListFollowsTheQueryAsItIsTyped() {
        chickenBook()
        show(LibraryView.OWN)
        compose.onNodeWithTag(LibraryTags.SEARCH).performTextInput("к")
        assertListed("Курица с рисом", "Плов по-домашнему")
        compose.onNodeWithTag(LibraryTags.SEARCH).performTextInput("у")
        assertListed("Курица с рисом", "Плов по-домашнему")
        compose.onNodeWithTag(LibraryTags.SEARCH).performTextInput("рица с")
        assertListed("Курица с рисом")
        assertEquals("курица с", session.query(LibraryView.OWN).value.lowercase())
    }
}
