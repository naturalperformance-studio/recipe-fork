package app.recipefork.app.library

import app.recipefork.app.ui.AppNavHost
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.card.CardEnvironment
import app.recipefork.app.card.CardTags
import app.recipefork.app.card.awaitCard
import app.recipefork.app.editor.EditorEnvironment
import app.recipefork.app.editor.EditorTags
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.editor.SAMPLE_DICTIONARIES
import app.recipefork.app.editor.TestDevice
import app.recipefork.app.editor.textsOf
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.ui.AccessibilityChecks
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeId
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
 * The way from the own-recipes view to a saved recipe and back (FLOW-029, FLOW-042 (5)): an entry opens the
 * recipe's card; the card's edit action opens the editor on it; leaving the editor shows that card again; back
 * from the card returns to the own-recipes view. A frozen recipe's card offers no way into the editor.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class OwnEntryOpensCardTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val device = TestDevice(context)
    private val active = ActiveBook(BookName.Pool, book.database)
    private val recipes = RecipeRepository(active)
    private val session = LibrarySession(SavedStateHandle())
    private val now = Instant.parse("2026-10-01T12:00:00Z")

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

    private fun own(title: String, state: RecipeState = RecipeState.LOCAL, minutesAgo: Long = 0): Recipe {
        val sent = if (state == RecipeState.LOCAL) SentStatus.NEVER else SentStatus.SENT
        val recipe = Recipe.create(now.minusSeconds(minutesAgo * 60), owner = null).copy(title = title, sent = sent).inState(state)
        runBlocking { recipes.save(recipe) }
        return recipe
    }

    private fun show() {
        val editor = EditorEnvironment(active, device.catalog(context)) { now }
        val card = CardEnvironment { device.database.dictionaryDao().ingredients() }
        compose.setContent { MaterialTheme { AppNavHost(session, recipes, editor = editor, card = card) } }
        compose.onNodeWithTag(LibraryTags.tab(LibraryView.OWN)).performClick()
        compose.waitForIdle()
    }

    /** Scrolls the own list to the entry of [recipe] once the book has listed it, and taps it. */
    private fun tapEntry(recipe: Recipe) {
        val entry = LibraryTags.entry(recipe.id.value)
        compose.waitUntil(PATIENCE_MS) {
            try {
                compose.onNodeWithTag(LibraryTags.OWN_LIST).performScrollToNode(hasTestTag(entry))
                true
            } catch (e: AssertionError) {
                false
            }
        }
        compose.onNodeWithTag(entry).performClick()
        compose.awaitCard()
    }

    private fun assertTheOwnView() {
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.OWN_LIST), PATIENCE_MS)
        compose.onNodeWithTag(CardTags.SCREEN).assertDoesNotExist()
        compose.onNodeWithTag(LibraryTags.tab(LibraryView.OWN)).assertIsSelected()
        assertNull(session.viewing.value)
        assertNull(session.editing.value)
    }

    private fun stored(id: RecipeId) = runBlocking { recipes.get(id) }

    // FLOW-029 (1): an entry opens that recipe's card, in every state; back from it returns to the own view.
    @Test
    fun anEntryOpensItsRecipesCardAndBackReturnsToTheOwnView() {
        val written = RecipeState.entries.mapIndexed { i, state -> own("Soup in $state", state, i.toLong()) }
        show()
        written.forEach { recipe ->
            tapEntry(recipe)
            assertEquals(listOf("Soup in ${recipe.state}"), compose.textsOf(CardTags.TITLE))
            assertEquals(recipe.id.value, session.viewing.value)
            compose.onNodeWithTag(EditorTags.SCREEN).assertDoesNotExist()
            compose.onNodeWithTag(CardTags.BACK).performClick()
            assertTheOwnView()
        }
    }

    // FLOW-029 (1): an entry announces that tapping it opens the recipe.
    @Test
    fun anEntryAnnouncesThatItOpensTheRecipe() = entryAnnounced("Open the recipe")

    @Test
    @Config(qualifiers = "ru")
    fun anEntryAnnouncesThatItOpensTheRecipeInRussian() = entryAnnounced("Открыть рецепт")

    private fun entryAnnounced(action: String) {
        val recipe = own("Soup")
        show()
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.entry(recipe.id.value)), PATIENCE_MS)
        val node = compose.onNodeWithTag(LibraryTags.entry(recipe.id.value)).fetchSemanticsNode()
        assertEquals(action, AccessibilityChecks.clickActionLabel(node))
        AccessibilityChecks.assertTouchTargets(compose, expectAtLeast = 4) // two tabs, the new recipe, the entry
        AccessibilityChecks.assertInteractiveElementsLabelled(compose, expectAtLeast = 4)
    }

    // FLOW-029 (2): the card's edit action opens the editor on that recipe; leaving the editor shows its card,
    // holding what was just edited; back from the card returns to the own view.
    @Test
    fun editFromTheCardsBarAndLeavingTheEditorShowsTheCardOfTheRecipeJustEdited() =
        editAndLeave { compose.onNodeWithTag(EditorTags.BACK).performClick() }

    // The same, leaving the editor by "Done", which closes it once the change is in the book.
    @Test
    fun doneLeavesTheEditorForTheCardOfTheRecipeJustEdited() =
        editAndLeave { compose.onNodeWithTag(EditorTags.DONE).performClick() }

    // The same, leaving the editor by the system's back, and leaving the card by it too.
    @Test
    fun theSystemBackLeavesTheEditorForTheCardAndTheCardForTheOwnView() = editAndLeave(systemBackFromTheCard = true) {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
    }

    private fun editAndLeave(systemBackFromTheCard: Boolean = false, leave: () -> Unit) {
        val other = own("Pea soup", minutesAgo = 5)
        val recipe = own("Borscht")
        show()
        tapEntry(recipe)
        compose.onNodeWithTag(CardTags.EDIT).performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.FORM), PATIENCE_MS)
        assertEquals(recipe.id.value, session.editing.value)
        compose.onNodeWithTag(CardTags.SCREEN).assertDoesNotExist()
        compose.onNodeWithTag(EditorTags.TITLE).performTextReplacement("Borscht with beans")
        leave()
        compose.awaitCard()
        compose.onNodeWithTag(EditorTags.SCREEN).assertDoesNotExist()
        compose.waitUntil(PATIENCE_MS) { compose.textsOf(CardTags.TITLE) == listOf("Borscht with beans") }
        assertEquals(recipe.id.value, session.viewing.value)
        assertNull(session.editing.value)
        assertEquals("Borscht with beans", stored(recipe.id)?.title)
        assertEquals("Pea soup", stored(other.id)?.title)
        if (systemBackFromTheCard) {
            compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        } else {
            compose.onNodeWithTag(CardTags.BACK).performClick()
        }
        assertTheOwnView()
        compose.onNode(hasText("Borscht with beans") and hasAnyAncestor(hasTestTag(LibraryTags.OWN_LIST))).assertExists()
    }

    // FLOW-029 (4): a recipe no longer editable opens as its card with no way into the editor.
    @Test
    fun aFrozenRecipesCardOffersNoWayIntoTheEditor() {
        val frozen = RecipeState.entries.filter { !it.editable }.mapIndexed { i, state -> own("Pie in $state", state, i.toLong()) }
        show()
        frozen.forEach { recipe ->
            tapEntry(recipe)
            compose.onNodeWithTag(CardTags.EDIT).assertDoesNotExist()
            val actions = compose.onAllNodes(AccessibilityChecks.isInteractive).fetchSemanticsNodes()
            assertEquals("${recipe.state}", 5, actions.size) // back, the ⋮ (its shopping-list action), the two tabs and fork
            compose.onNodeWithTag(CardTags.BACK).performClick()
            assertTheOwnView()
        }
    }

    // FLOW-042 (5): a book that lists recipes still offers a new one, which opens the editor on a new recipe in the
    // active book, as the empty book's offer does; leaving the editor shows its card.
    @Test
    fun theOwnViewOffersANewRecipeAlsoWhenTheBookIsNotEmpty() = newRecipe("New recipe")

    @Test
    @Config(qualifiers = "ru")
    fun theOwnViewOffersANewRecipeAlsoWhenTheBookIsNotEmptyInRussian() = newRecipe("Новый рецепт")

    // A new recipe left by "Done" goes where back takes it: its card, the book beneath.
    @Test
    fun doneLeavesANewRecipeAsBackDoes() = newRecipe("New recipe", EditorTags.DONE)

    private fun newRecipe(label: String, leave: String = EditorTags.BACK) {
        val existing = own("Soup")
        show()
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.NEW), PATIENCE_MS)
        compose.onNodeWithTag(LibraryTags.NEW).assertIsDisplayed()
        assertEquals(listOf(label), compose.textsOf(LibraryTags.NEW))
        compose.onNodeWithTag(LibraryTags.OWN_EMPTY).assertDoesNotExist()
        compose.onNodeWithTag(LibraryTags.NEW).performClick()
        compose.onNodeWithTag(LibraryTags.FROM_SCRATCH).performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.FORM), PATIENCE_MS)
        val opened = RecipeId(requireNotNull(session.editing.value))
        val rows = runBlocking { book.database.recipes().newest(10).first() }
        assertEquals(setOf(existing.id.value, opened.value), rows.map { it.recipeId }.toSet())
        val created = requireNotNull(stored(opened))
        assertEquals(RecipeState.LOCAL, created.state)
        assertEquals(SentStatus.NEVER, created.sent)
        assertEquals(null, created.owner)
        assertEquals("", created.title)
        compose.onNodeWithTag(leave).performClick()
        compose.awaitCard()
        assertEquals(opened.value, session.viewing.value)
        compose.onNodeWithTag(CardTags.BACK).performClick()
        assertTheOwnView()
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.entry(opened.value)), PATIENCE_MS)
    }

    // A card open on a recipe the book no longer holds: a short message and the back action, never a crash.
    @Test
    fun aCardOfARecipeTheBookNoLongerHoldsSaysSoAndLeadsBack() {
        own("Soup")
        session.choose(LibraryView.OWN)
        session.openCard("gone-from-this-book")
        val editor = EditorEnvironment(active, device.catalog(context)) { now }
        val card = CardEnvironment { device.database.dictionaryDao().ingredients() }
        compose.setContent { MaterialTheme { AppNavHost(session, recipes, editor = editor, card = card) } }
        compose.awaitCard()
        assertEquals(listOf("This recipe is no longer in this recipe book."), compose.textsOf(LibraryTags.emptySentence(CardTags.MISSING)))
        compose.onNodeWithTag(CardTags.EDIT).assertDoesNotExist()
        compose.onNodeWithTag(CardTags.BACK).performClick()
        assertTheOwnView()
    }
}
