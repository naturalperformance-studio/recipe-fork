package app.recipefork.app.library

import app.recipefork.app.ui.AppNavHost
import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.editor.EditorEnvironment
import app.recipefork.app.editor.EditorTags
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.editor.TestDevice
import app.recipefork.app.editor.textsOf
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.ui.AccessibilityChecks
import app.recipefork.core.recipe.AccountId
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.RecipeLanguage
import app.recipefork.core.recipe.RecipeState
import app.recipefork.core.recipe.SentStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * FLOW-042 (4): the own-recipes view of an active book in which nothing has been written — an account's book, or
 * the pool when no account is active — says so, offers to write a recipe, and the offer opens the editor on a new
 * recipe in that book.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class NothingWrittenTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val pool = TestBook(context)
    private val account = TestBook(context)
    private val device = TestDevice(context)
    private val session = LibrarySession(SavedStateHandle())
    private val now = Instant.parse("2026-09-29T12:00:00Z")

    @Before
    fun waitForTheBooks() {
        listOf(pool.idling, account.idling, device.idling).forEach(compose::registerIdlingResource)
    }

    @After
    fun close() {
        listOf(pool.idling, account.idling, device.idling).forEach(compose::unregisterIdlingResource)
        pool.close()
        account.close()
        device.close()
    }

    private fun show(active: ActiveBook) {
        val recipes = RecipeRepository(active)
        val environment = EditorEnvironment(active, device.catalog(context)) { now }
        compose.setContent { MaterialTheme { AppNavHost(session, recipes, editor = environment) } }
        compose.onNodeWithTag(LibraryTags.tab(LibraryView.OWN)).performClick()
        compose.waitForIdle()
    }

    private fun written(book: TestBook) = runBlocking { book.database.recipes().newest(100).first() }

    /** The view states that nothing is written, holds one offer, and the offer reads "New recipe". */
    private fun assertStatesItAndOffersToWrite(statement: String, offer: String) {
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.OWN_EMPTY), PATIENCE_MS)
        compose.onNode(hasText(statement) and hasAnyAncestor(hasTestTag(LibraryTags.OWN_EMPTY))).assertIsDisplayed()
        assertEquals(listOf(offer), compose.textsOf(LibraryTags.WRITE))
        // One offer and nothing else to act on in the view below the switch; above it, the shopping list's and the
        // settings' actions.
        val actions = compose.onAllNodes(AccessibilityChecks.isInteractive).fetchSemanticsNodes()
            .filter { SemanticsActions.OnClick in it.config }
            .filterNot { it.config.getOrNull(SemanticsProperties.TestTag) in setOf(LibraryTags.SHOPPING, LibraryTags.SETTINGS) }
        assertEquals(3, actions.size) // the two tabs and the offer
    }

    /** Takes the offer and returns the recipe the editor opened on. */
    private fun takeTheOffer(): String {
        compose.onNodeWithTag(LibraryTags.WRITE).performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.FORM), PATIENCE_MS)
        return requireNotNull(session.editing.value)
    }

    private fun assertANewRecipeInThisBook(book: TestBook, opened: String, owner: AccountId?) {
        val rows = written(book)
        assertEquals(1, rows.size)
        val recipe = runBlocking { RecipeRepository(ActiveBook(BookName.Pool, book.database)).get(RecipeId(opened)) }!!
        assertEquals(opened, rows.single().recipeId)
        assertEquals(RecipeState.LOCAL, recipe.state)
        assertEquals(SentStatus.NEVER, recipe.sent)
        assertEquals(owner, recipe.owner)
        assertEquals("", recipe.title)
        assertEquals(emptySet<Long>(), recipe.tags)
    }

    // FLOW-042 (4), the pool of AUTH-026 when no account is active.
    @Test
    fun thePoolWithNothingWrittenSaysSoAndItsOfferOpensTheEditorOnANewRecipeInThePool() {
        show(ActiveBook(BookName.Pool, pool.database))
        assertStatesItAndOffersToWrite("No recipes of yours yet", "New recipe")
        val opened = takeTheOffer()
        assertANewRecipeInThisBook(pool, opened, owner = null)
        assertEquals(emptyList<Any>(), written(account))
    }

    // FLOW-042 (4), an account's book: a book other than the pool is active and empty.
    @Test
    fun anAccountsBookWithNothingWrittenSaysSoAndItsOfferOpensTheEditorOnANewRecipeInThatBook() {
        show(ActiveBook(BookName.OfAccount("account-1"), account.database))
        assertStatesItAndOffersToWrite("No recipes of yours yet", "New recipe")
        val opened = takeTheOffer()
        assertANewRecipeInThisBook(account, opened, owner = AccountId("account-1"))
        // Nothing reached the pool, which is not the active book.
        assertEquals(emptyList<Any>(), written(pool))
    }

    // FLOW-042 (4), in Russian.
    @Test
    @Config(qualifiers = "ru")
    fun inRussianTheEmptyViewSaysSoAndOffersToWrite() {
        show(ActiveBook(BookName.Pool, pool.database))
        assertStatesItAndOffersToWrite("Своих рецептов пока нет", "Новый рецепт")
        val opened = takeTheOffer()
        assertANewRecipeInThisBook(pool, opened, owner = null)
    }

    // FLOW-042 (4): the statement belongs to a book with nothing written; a book with a recipe lists it instead.
    @Test
    fun aBookWithARecipeWrittenShowsNoStatementAndNoOffer() {
        val recipe = Recipe.create(now, null).copy(title = "Soup", language = RecipeLanguage.EN)
        runBlocking { RecipeRepository(ActiveBook(BookName.Pool, pool.database)).save(recipe) }
        show(ActiveBook(BookName.Pool, pool.database))
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.entry(recipe.id.value)), PATIENCE_MS)
        compose.onNodeWithTag(LibraryTags.OWN_EMPTY).assertDoesNotExist()
        compose.onNodeWithTag(LibraryTags.WRITE).assertDoesNotExist()
        assertNull(session.editing.value)
    }
}
