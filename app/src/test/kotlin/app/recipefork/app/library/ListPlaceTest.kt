package app.recipefork.app.library

import app.recipefork.app.ui.AppNavHost
import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.card.CardEnvironment
import app.recipefork.app.card.IngredientEntries
import app.recipefork.app.dictionary.TagEntity
import app.recipefork.app.dictionary.TagSource
import app.recipefork.app.editor.EditorEnvironment
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.file.RecipeImport
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.search.BookOrder
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * FLOW-041 (4), FLOW-058: a list restored — the process ended and the reader returned — keeps its place; only a query,
 * an order or a filter that changes from one already listed brings it back to its top.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class ListPlaceTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val active = ActiveBook(BookName.Pool, book.database)
    private val recipes = RecipeRepository(active)
    private val session = LibrarySession(SavedStateHandle())
    private val now = Instant.parse("2026-10-01T12:00:00Z")
    private val italian = TagEntity(10, "итальянская", "Italian")
    private val card = CardEnvironment(ingredients = IngredientEntries { emptyList() })
    private val editor = EditorEnvironment(
        active,
        object : TagSource {
            override suspend fun tags() = listOf(italian)
        },
    ) { now }

    @Before
    fun waitForTheBook() = compose.registerIdlingResource(book.idling)

    @After
    fun close() {
        compose.unregisterIdlingResource(book.idling)
        book.close()
    }

    /** Forty recipes of [view], "Recipe 0" the newest; every one carries the tag. */
    private fun fortyRecipes(view: LibraryView): List<Recipe> = (0 until 40).map { n ->
        val recipe = Recipe.create(now.minusSeconds(n * 60L), owner = null)
            .copy(title = "Recipe $n", tags = setOf(italian.id))
        runBlocking {
            if (view == LibraryView.OWN) {
                recipes.save(recipe)
            } else {
                recipes.addFound(recipe.inState(RecipeImport.FOUND_STATE), now.minusSeconds(n * 60L))
            }
        }
        recipe
    }

    private fun show(view: LibraryView): StateRestorationTester {
        session.choose(view)
        val tester = StateRestorationTester(compose)
        tester.setContent { MaterialTheme { AppNavHost(session, recipes, card = card, editor = editor) } }
        compose.waitForIdle()
        return tester
    }

    private fun list(view: LibraryView) = if (view == LibraryView.OWN) LibraryTags.OWN_LIST else LibraryTags.MARKED_LIST

    private fun shown(recipe: Recipe) = compose.onAllNodes(hasTestTag(LibraryTags.entry(recipe.id.value))).fetchSemanticsNodes().isNotEmpty()

    private fun awaitShown(recipe: Recipe) = compose.waitUntil(PATIENCE_MS) { shown(recipe) }

    private fun keepsItsPlace(view: LibraryView) {
        val forty = fortyRecipes(view)
        val tester = show(view)
        awaitShown(forty[0])
        compose.onNodeWithTag(list(view)).performScrollToIndex(25)
        awaitShown(forty[25])
        check(!shown(forty[0]))
        // The process ends while away and the reader returns: the list is where it was left.
        tester.emulateSavedInstanceStateRestore()
        awaitShown(forty[25])
        check(!shown(forty[0])) { "the restored list jumped to its top" }
        // A filter that changes brings it to the top; every recipe carries the tag, so the same forty are listed.
        compose.onNodeWithTag(LibraryTags.FILTER_ADD).scrolledTo().performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.TAG_FIELD), PATIENCE_MS)
        compose.onNodeWithTag(LibraryTags.TAG_FIELD).scrolledTo().performTextReplacement("ital")
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.tagChoice(italian.id)), PATIENCE_MS)
        compose.onNodeWithTag(LibraryTags.tagChoice(italian.id)).scrolledTo().performClick()
        compose.onNodeWithTag(LibraryTags.FILTERS_SHEET_DONE).performClick()
        compose.waitUntilDoesNotExist(hasTestTag(LibraryTags.FILTERS_SHEET), PATIENCE_MS)
        awaitShown(forty[0])
        // So does a query or an order.
        compose.onNodeWithTag(list(view)).performScrollToIndex(25)
        awaitShown(forty[25])
        compose.onNodeWithTag(LibraryTags.SEARCH).performTextReplacement("recipe")
        awaitShown(forty[0])
        compose.onNodeWithTag(list(view)).performScrollToIndex(25)
        awaitShown(forty[25])
        session.orderBy(view, BookOrder.OLDEST)
        awaitShown(forty[39])
    }

    @Test
    fun theOwnListKeepsItsPlaceWhenRestored() = keepsItsPlace(LibraryView.OWN)

    @Test
    fun theMarkedListKeepsItsPlaceWhenRestored() = keepsItsPlace(LibraryView.MARKED)
}
