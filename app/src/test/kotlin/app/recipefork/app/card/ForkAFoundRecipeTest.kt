package app.recipefork.app.card

import app.recipefork.app.ui.AppNavHost
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
import app.recipefork.app.library.LibrarySession
import app.recipefork.app.library.LibraryTags
import app.recipefork.app.library.chooseEntryAction
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
            MaterialTheme { AppNavHost(session, recipes, Mode.Serverless, editor = editor, card = card, files = importer) }
        }
        compose.waitForIdle()
    }

    private fun stored(id: RecipeId) = runBlocking { recipes.get(id) }

    /** The provenance line reads [texts]: its words, then the author the file states where the line names one. */
    private fun awaitProvenance(vararg texts: String) =
        compose.waitUntil(PATIENCE_MS) { compose.textsOf(CardTags.PROVENANCE) == texts.toList() }

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
        compose.forkFromTheCard()
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

        // The fork's card names the found recipe, without its author, and opens it; the found recipe's own card names
        // its author (FLOW-065 (3)).
        compose.onNodeWithTag(EditorTags.BACK).performClick()
        compose.awaitCard()
        compose.onNodeWithTag(CardTags.DESCRIPTION).assertDoesNotExist()
        awaitProvenance("after “Bean soup”")
        assertTrue(opens())
        compose.onNodeWithTag(ProvenanceTags.AUTHOR, useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag(CardTags.STATED_AUTHOR).assertDoesNotExist()
        compose.onNodeWithTag(CardTags.PROVENANCE).performClick()
        compose.waitUntil(PATIENCE_MS) { compose.textsOf(CardTags.TITLE) == listOf("Bean soup") }
        assertEquals(listOf("Author: anna (stated in the file, not verified)"), compose.textsOf(CardTags.STATED_AUTHOR))
        assertEquals(id.value, session.viewing.value)

        // The found recipe is removed from the book: the fork names it by its title as unavailable, with the author the
        // file states under it, marked as not verified, and opens nothing.
        compose.runOnIdle {
            session.closeCard()
            session.choose(LibraryView.MARKED)
        }
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.actions(id.value)), PATIENCE_MS)
        compose.chooseEntryAction(id.value, LibraryTags.remove(id.value))
        compose.waitUntil(PATIENCE_MS) { stored(id) == null }
        compose.runOnIdle { session.openCard(forkId.value, LibraryView.OWN) }
        compose.awaitCard()
        awaitProvenance("after “Bean soup”, which is not in this book", "anna · author from the file, not verified")
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
        awaitProvenance("after “Bean soup”, which is not in this book")
        compose.onNodeWithTag(ProvenanceTags.AUTHOR, useUnmergedTree = true).assertDoesNotExist()
    }

    // FLOW-065 (5): a found recipe's file names a parent the book does not hold — named by the file's title and
    // author, the author marked as not verified, and unavailable.
    @Test
    fun aStatedParentTheBookDoesNotHoldIsNamedAsTheFileStatesItAndUnavailable() {
        val id = found()
        session.openCard(id.value, LibraryView.MARKED)
        show()
        compose.awaitCard()
        awaitProvenance("after “Soup”, which is not in this book", "boris · author from the file, not verified")
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
        awaitProvenance("after “My soup”")
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
        awaitProvenance("after a recipe not in this book")
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
        awaitProvenance("по рецепту «Soup», которого нет в книге", "boris · автор из файла, не проверен")
        compose.runOnIdle { session.openCard(forkId.value, LibraryView.OWN) }
        compose.awaitCard()
        awaitProvenance("по рецепту «Bean soup»")
        assertTrue(runBlocking { importer.remove(id) })
        awaitProvenance("по рецепту «Bean soup», которого нет в книге", "anna · автор из файла, не проверен")
    }
}
