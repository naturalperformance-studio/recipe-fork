package app.recipefork.app.card

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.Mode
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.editor.EditorEnvironment
import app.recipefork.app.editor.EditorTags
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.editor.SAMPLE_DICTIONARIES
import app.recipefork.app.editor.TestDevice
import app.recipefork.app.editor.textsOf
import app.recipefork.app.file.ImportOutcome
import app.recipefork.app.file.RecipeImport
import app.recipefork.app.file.TestRecipeFiles
import app.recipefork.app.library.LibraryScreen
import app.recipefork.app.library.LibrarySession
import app.recipefork.app.library.LibraryTags
import app.recipefork.app.library.LibraryView
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.ui.AccessibilityChecks
import app.recipefork.core.file.FileMedia
import app.recipefork.core.file.FileParent
import app.recipefork.core.file.RecipeFileContent
import app.recipefork.core.recipe.MediaAttachment
import app.recipefork.core.recipe.MediaKind
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.RecipeState
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.time.Instant

/**
 * Scenario 4 of the increment (FLOW-065, FORK-002, FORK-003): fork a found recipe → an editable copy without its
 * description; its card names the found recipe and opens it; once the found recipe is removed from the book, the fork
 * names it by its title as unavailable. A found recipe whose file names a parent shows that parent by the title and
 * the author the file states, the author marked as not verified, opening it while the book holds a recipe of that
 * identity.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class ForkAFoundRecipeTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val device = TestDevice(context)
    private val active = ActiveBook(BookName.Pool, book.database)
    private val recipes = RecipeRepository(active)
    private val session = LibrarySession(SavedStateHandle())
    private val now = Instant.parse("2026-10-02T12:00:00Z")
    private val files = File(context.cacheDir, "fork-found-test-files").also { it.deleteRecursively() }
    private val importer = RecipeImport(recipes, files, clock = { now })

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
        files.deleteRecursively()
    }

    private fun found(content: RecipeFileContent = TestRecipeFiles.soup()): RecipeId {
        val outcome = runBlocking { importer.import { TestRecipeFiles.file(content, *content.media.map { it.entry to TestRecipeFiles.jpeg() }.toTypedArray()).inputStream() } }
        assertTrue("$outcome", outcome is ImportOutcome.Added)
        return content.id
    }

    private fun show() {
        val editor = EditorEnvironment(active, device.catalog(context)) { now }
        val card = CardEnvironment { device.database.dictionaryDao().ingredients() }
        compose.setContent {
            MaterialTheme { LibraryScreen(session, recipes, Mode.Serverless, editor = editor, card = card, files = importer) }
        }
        compose.waitForIdle()
    }

    private fun stored(id: RecipeId) = runBlocking { recipes.get(id) }

    private fun awaitProvenance(text: String) =
        compose.waitUntil(PATIENCE_MS) { compose.textsOf(CardTags.PROVENANCE) == listOf(text) }

    private fun opens(): Boolean =
        AccessibilityChecks.clickActionLabel(compose.onNodeWithTag(CardTags.PROVENANCE).fetchSemanticsNode()) != null

    // FLOW-065 (1–4), FORK-002: the whole of scenario 4, from the marked view.
    @Test
    fun aForkOfAFoundRecipeNamesItOpensItAndNamesItAsUnavailableOnceRemoved() {
        val id = found(TestRecipeFiles.soup().copy(media = listOf(FileMedia("m-1", MediaKind.PHOTO, MediaAttachment.Cover, "media/cover.jpg"))))
        val parent = requireNotNull(stored(id))
        assertTrue("the found recipe holds media", parent.media.isNotEmpty())
        session.choose(LibraryView.MARKED)
        session.openCard(id.value, LibraryView.MARKED)
        show()
        compose.awaitCard()
        compose.onNodeWithTag(CardTags.FORK).performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.FORM), PATIENCE_MS)
        val forkId = RecipeId(requireNotNull(session.editing.value))
        assertNotEquals(id, forkId)
        // An editable recipe in the pool, naming the found recipe; its ingredients, steps, references and timers, and
        // none of its description and media.
        val fork = requireNotNull(stored(forkId))
        assertEquals(id, fork.parent)
        assertEquals(RecipeState.LOCAL, fork.state)
        assertTrue(fork.state.editable)
        assertNull(fork.owner)
        assertEquals(parent.ingredients, fork.ingredients)
        assertEquals(parent.steps, fork.steps)
        assertTrue(fork.steps.any { it.timer != null && it.ingredientReferences.isNotEmpty() })
        assertEquals("", fork.description)
        assertTrue(fork.media.isEmpty())
        assertEquals(parent, stored(id))

        // The fork's card names the found recipe and opens it.
        compose.onNodeWithTag(EditorTags.BACK).performClick()
        compose.awaitCard()
        compose.onNodeWithTag(CardTags.DESCRIPTION).assertDoesNotExist()
        val named = "Forked from “Bean soup” (author: anna, stated in the file, not verified)"
        awaitProvenance(named)
        assertTrue(opens())
        compose.onNodeWithTag(CardTags.PROVENANCE).performClick()
        compose.waitUntil(PATIENCE_MS) { compose.textsOf(CardTags.TITLE) == listOf("Bean soup") }
        assertEquals(id.value, session.viewing.value)

        // The found recipe is removed from the book: the fork names it by its title as unavailable, and opens nothing.
        compose.runOnIdle {
            session.closeCard()
            session.choose(LibraryView.MARKED)
        }
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.remove(id.value)), PATIENCE_MS)
        compose.onNodeWithTag(LibraryTags.remove(id.value)).performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.REMOVE_STATEMENT), PATIENCE_MS)
        compose.onNodeWithTag(LibraryTags.REMOVE_CONFIRM).performClick()
        compose.waitUntil(PATIENCE_MS) { stored(id) == null }
        compose.runOnIdle { session.openCard(forkId.value, LibraryView.OWN) }
        compose.awaitCard()
        awaitProvenance("Forked from “Bean soup” (author: anna, stated in the file, not verified), which is not in this recipe book")
        assertNull(AccessibilityChecks.clickActionLabel(compose.onNodeWithTag(CardTags.PROVENANCE).fetchSemanticsNode()))
        compose.onNodeWithTag(CardTags.PROVENANCE).performClick()
        compose.waitForIdle()
        assertEquals(forkId.value, session.viewing.value)
    }

    // FLOW-065 (4): a found recipe stating no author is named by its title alone once removed.
    @Test
    fun aRemovedParentStatingNoAuthorIsNamedByItsTitle() {
        val id = found(TestRecipeFiles.soup(author = null))
        val forkId = requireNotNull(runBlocking { forkRecipe(recipes, active, id, now) })
        assertTrue(runBlocking { importer.remove(id) })
        session.openCard(forkId.value, LibraryView.OWN)
        show()
        compose.awaitCard()
        awaitProvenance("Forked from “Bean soup”, which is not in this recipe book")
    }

    // FLOW-065 (5): a found recipe's file names a parent the book does not hold — named by the file's title and
    // author, the author marked as not verified, and unavailable.
    @Test
    fun aStatedParentTheBookDoesNotHoldIsNamedAsTheFileStatesItAndUnavailable() {
        val id = found()
        session.openCard(id.value, LibraryView.MARKED)
        show()
        compose.awaitCard()
        awaitProvenance("Forked from “Soup” (author: boris, stated in the file, not verified), which is not in this recipe book")
        assertNull(AccessibilityChecks.clickActionLabel(compose.onNodeWithTag(CardTags.PROVENANCE).fetchSemanticsNode()))
    }

    // FLOW-065 (5): a stated parent whose identity the book holds opens it; it is still named as the file states it.
    @Test
    fun aStatedParentTheBookHoldsOpens() {
        val own = Recipe.create(now, owner = null).copy(title = "My soup, renamed since")
        runBlocking { recipes.save(own) }
        val id = found(TestRecipeFiles.soup().copy(parent = FileParent(own.id, "My soup", "boris")))
        session.openCard(id.value, LibraryView.MARKED)
        show()
        compose.awaitCard()
        awaitProvenance("Forked from “My soup” (author: boris, stated in the file, not verified)")
        assertTrue(opens())
        compose.onNodeWithTag(CardTags.PROVENANCE).performClick()
        compose.waitUntil(PATIENCE_MS) { compose.textsOf(CardTags.TITLE) == listOf("My soup, renamed since") }
        assertEquals(own.id.value, session.viewing.value)
    }

    // A file stating a parent's identity and no title keeps the general wording.
    @Test
    fun aStatedParentWithNoTitleIsNamedAsNotInTheBook() {
        val id = found(TestRecipeFiles.soup().copy(parent = FileParent(RecipeId("p-2"), null, null)))
        session.openCard(id.value, LibraryView.MARKED)
        show()
        compose.awaitCard()
        awaitProvenance("Forked from a recipe that is not in this recipe book")
    }

    // L10N-001: the stated parent reads in Russian, held and not held.
    @Test
    @Config(qualifiers = "ru")
    fun theStatedParentReadsInRussian() {
        val id = found()
        val forkId = requireNotNull(runBlocking { forkRecipe(recipes, active, id, now) })
        session.openCard(id.value, LibraryView.MARKED)
        show()
        compose.awaitCard()
        awaitProvenance("Основано на рецепте «Soup» (автор: boris, указан в файле, не проверен), которого нет в этой книге рецептов")
        compose.runOnIdle { session.openCard(forkId.value, LibraryView.OWN) }
        compose.awaitCard()
        awaitProvenance("Основано на рецепте «Bean soup» (автор: anna, указан в файле, не проверен)")
        assertTrue(runBlocking { importer.remove(id) })
        awaitProvenance("Основано на рецепте «Bean soup» (автор: anna, указан в файле, не проверен), которого нет в этой книге рецептов")
    }
}
