package app.recipefork.app.library

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.FavouriteEntity
import app.recipefork.app.book.TestBook
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.ui.LocalizationChecks
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeState
import app.recipefork.core.recipe.SentStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant

/** FLOW-034 (8) and L10N-001 (1) on the recipe book, in each interface language. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class LibraryLanguageTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val database = book.database
    private val recipes = RecipeRepository(ActiveBook(BookName.Pool, database))
    private val titles = mutableSetOf<String>()
    private var ownCount = 0

    @Before
    fun waitForTheBook() = compose.registerIdlingResource(book.idling)

    @After
    fun close() {
        compose.unregisterIdlingResource(book.idling)
        book.close()
    }

    /** Every text on screen is this language's own resource, and is written in this language's script. */
    private fun assertEveryTextInTheLanguageInForce() {
        LocalizationChecks.assertScreenTextFromResources(compose, context, titles)
        LocalizationChecks.assertScreenTextWrittenInItsLanguage(compose, context, titles)
    }

    private fun showBothViewsAndCheckTheirText() {
        val now = Instant.parse("2026-09-29T12:00:00Z")
        runBlocking {
            RecipeState.entries.forEachIndexed { i, state ->
                SentStatus.entries.forEach { sent ->
                    val title = "Recipe $state $sent"
                    titles += title
                    recipes.save(Recipe.create(now.minusSeconds(i * 10L), null).copy(title = title, sent = sent).inState(state))
                    ownCount++
                }
            }
            recipes.save(Recipe.create(now.minusSeconds(1_000), null)) // untitled
            ownCount++
            database.favourites().put(FavouriteEntity("m", "Marked pie", now))
            titles += "Marked pie"
        }
        compose.setContent { MaterialTheme { LibraryScreen(LibrarySession(SavedStateHandle()), recipes) } }
        // The rule waits for the book as for the screen; the time limit only turns a missing entry into a failure.
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.entry("m")), PATIENCE_MS)
        assertEveryTextInTheLanguageInForce()
        compose.onNodeWithTag(LibraryTags.tab(LibraryView.OWN)).performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.OWN_LIST), PATIENCE_MS)
        compose.waitUntilDoesNotExist(hasTestTag(LibraryTags.entry("m")), PATIENCE_MS)
        compose.waitForIdle()
        // A lazy list composes only what fits on the screen: every entry, and so every state's and every
        // mark's text, is brought on screen and checked in turn.
        for (index in 0 until ownCount) {
            compose.onNodeWithTag(LibraryTags.OWN_LIST).performScrollToIndex(index)
            compose.waitForIdle()
            assertEveryTextInTheLanguageInForce()
        }
    }

    // FLOW-034 (8), L10N-001 (1): English.
    @Test
    fun inEnglishTheSectionIsMyRecipeBookAndEveryTextIsEnglish() {
        showBothViewsAndCheckTheirText()
        compose.onNodeWithTag(LibraryTags.TITLE).assertTextEquals("My recipe book")
    }

    // FLOW-034 (8), L10N-001 (1): Russian.
    @Test
    @Config(qualifiers = "ru")
    fun inRussianTheSectionIsMoyaKnigaReceptovAndEveryTextIsRussian() {
        showBothViewsAndCheckTheirText()
        compose.onNodeWithTag(LibraryTags.TITLE).assertTextEquals("Моя книга рецептов")
    }

    private companion object {
        const val PATIENCE_MS = 60_000L
    }
}
