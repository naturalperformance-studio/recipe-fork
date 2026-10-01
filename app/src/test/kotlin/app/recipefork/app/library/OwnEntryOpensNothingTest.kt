package app.recipefork.app.library

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.editor.EditorEnvironment
import app.recipefork.app.editor.EditorTags
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.editor.SAMPLE_DICTIONARIES
import app.recipefork.app.editor.TestDevice
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeState
import app.recipefork.core.recipe.SentStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * The own-recipes view offers no way into the editor for a recipe already written, in an editable state or a frozen
 * one: an entry has no click action, and clicking it shows no editor. A saved recipe opens as its card (FLOW-029),
 * and a frozen one offers no way into the editor at all (FLOW-029 (4)). A guard against that route coming back.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class OwnEntryOpensNothingTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val device = TestDevice(context)
    private val active = ActiveBook(BookName.Pool, book.database)
    private val recipes = RecipeRepository(active)
    private val session = LibrarySession(SavedStateHandle())
    private val now = Instant.parse("2026-09-29T12:00:00Z")

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

    @Test
    fun anEntryInAnEditableOrAFrozenStateOpensNoEditor() {
        val written = RecipeState.entries.mapIndexed { i, state ->
            val sent = if (state == RecipeState.LOCAL) SentStatus.NEVER else SentStatus.SENT
            Recipe.create(now.minusSeconds(i * 60L), owner = null).copy(title = "Soup $i", sent = sent).inState(state)
        }
        assertTrue(written.any { it.state.editable } && written.any { !it.state.editable })
        runBlocking { written.forEach { recipes.save(it) } }
        compose.setContent {
            MaterialTheme { LibraryScreen(session, recipes, editor = EditorEnvironment(active, device.catalog(context))) }
        }
        compose.onNodeWithTag(LibraryTags.tab(LibraryView.OWN)).performClick()

        written.forEach { recipe ->
            val entry = LibraryTags.entry(recipe.id.value)
            val state = "${recipe.state}"
            // The list composes only what fits on screen: the entry is scrolled to once the book has listed it.
            compose.waitUntil(PATIENCE_MS) {
                try {
                    compose.onNodeWithTag(LibraryTags.OWN_LIST).performScrollToNode(hasTestTag(entry))
                    true
                } catch (e: AssertionError) {
                    false
                }
            }
            // No node of the entry, merged as the screen reader reads it or not, carries a click action.
            val inEntry = hasTestTag(entry) or hasAnyAncestor(hasTestTag(entry))
            assertTrue(state, compose.onAllNodes(hasTestTag(entry)).fetchSemanticsNodes().size == 1)
            assertEquals(state, 0, compose.onAllNodes(inEntry and hasClickAction()).fetchSemanticsNodes().size)
            assertEquals(state, 0, compose.onAllNodes(inEntry and hasClickAction(), useUnmergedTree = true).fetchSemanticsNodes().size)
            // Clicking it shows no editor: the own-recipes view stays as it was.
            compose.onNodeWithTag(entry).performClick()
            compose.waitForIdle()
            compose.onNodeWithTag(EditorTags.SCREEN).assertDoesNotExist()
            compose.onNodeWithTag(LibraryTags.OWN_LIST).assertExists()
            assertNull(state, session.editing.value)
        }
    }
}
