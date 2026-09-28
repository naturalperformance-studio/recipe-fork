package app.recipefork.app.library

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookDatabase
import app.recipefork.app.book.BookName
import app.recipefork.app.book.FavouriteEntity
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.ui.LocalizationChecks
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeState
import app.recipefork.core.recipe.SentStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant

/** FLOW-034 (8) and L10N-001 (1) on the recipe book, in each interface language. */
@RunWith(AndroidJUnit4::class)
class LibraryLanguageTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val database = Room.inMemoryDatabaseBuilder(context, BookDatabase::class.java).build()
    private val recipes = RecipeRepository(ActiveBook(BookName.Pool, database))
    private val titles = mutableSetOf<String>()
    private var ownCount = 0

    @After
    fun close() = database.close()

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
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasTestTag(LibraryTags.entry("m")), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        LocalizationChecks.assertScreenTextFromResources(compose, context, titles)
        compose.onNodeWithTag(LibraryTags.tab(LibraryView.OWN)).performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasTestTag(LibraryTags.OWN_LIST)).fetchSemanticsNodes().isNotEmpty() &&
                compose.onAllNodes(hasTestTag(LibraryTags.entry("m")), useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
        }
        compose.waitForIdle()
        // A lazy list composes only what fits on the screen: every entry, and so every state's and every
        // mark's text, is brought on screen and checked in turn.
        for (index in 0 until ownCount) {
            compose.onNodeWithTag(LibraryTags.OWN_LIST).performScrollToIndex(index)
            compose.waitForIdle()
            LocalizationChecks.assertScreenTextFromResources(compose, context, titles)
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
}
