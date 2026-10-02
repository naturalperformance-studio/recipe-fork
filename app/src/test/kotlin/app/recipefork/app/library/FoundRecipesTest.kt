package app.recipefork.app.library

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
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
import app.recipefork.app.card.CardEnvironment
import app.recipefork.app.card.CardTags
import app.recipefork.app.card.awaitCard
import app.recipefork.app.card.backThroughTheCard
import app.recipefork.app.editor.EditorEnvironment
import app.recipefork.app.editor.EditorTags
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.editor.SAMPLE_DICTIONARIES
import app.recipefork.app.editor.TestDevice
import app.recipefork.app.editor.textsOf
import app.recipefork.app.file.ImportOutcome
import app.recipefork.app.file.RecipeImport
import app.recipefork.app.file.TestRecipeFiles
import app.recipefork.app.file.awaitFileMessage
import app.recipefork.app.file.closeFileMessage
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.ui.AccessibilityChecks
import app.recipefork.core.file.RecipeFileContent
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.RecipeState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.time.Instant

/**
 * Scenario 2 of the increment (FLOW-064 (1–3, 6)): an imported recipe is in the view of marked recipes, its stated
 * author marked as not verified; it opens as a card that scales, has no Edit and has Fork; it can be removed from its
 * entry. In serverless mode the view offers the import of a file through the document picker; in standard mode it does
 * not. Each test of a mode-dependent offer passes the mode.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class FoundRecipesTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val device = TestDevice(context)
    private val active = ActiveBook(BookName.Pool, book.database)
    private val recipes = RecipeRepository(active)
    private val session = LibrarySession(SavedStateHandle())
    private val now = Instant.parse("2026-10-02T12:00:00Z")
    private val files = File(context.cacheDir, "found-test-files").also { it.deleteRecursively() }
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
        val outcome = runBlocking { importer.import { TestRecipeFiles.file(content).inputStream() } }
        assertTrue("$outcome", outcome is ImportOutcome.Added)
        return content.id
    }

    private fun show(mode: Mode) {
        val editor = EditorEnvironment(active, device.catalog(context)) { now }
        val card = CardEnvironment { device.database.dictionaryDao().ingredients() }
        compose.setContent { MaterialTheme { LibraryScreen(session, recipes, mode, editor = editor, card = card, files = importer) } }
        compose.waitForIdle()
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    private fun awaitEntry(id: RecipeId) =
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.entry(id.value)), PATIENCE_MS)

    private fun openEntry(id: RecipeId) {
        awaitEntry(id)
        compose.onNodeWithTag(LibraryTags.entry(id.value)).performClick()
        compose.awaitCard()
    }

    // FLOW-064 (1, 3): listed among the marked recipes with its stated author marked as not verified, and not among
    // the own recipes.
    @Test
    fun aFoundRecipeIsListedAmongTheMarkedWithItsStatedAuthor() {
        val id = found()
        show(Mode.Serverless)
        awaitEntry(id)
        assertEquals(
            listOf("Bean soup", "Author: anna (stated in the file, not verified)"),
            compose.textsOf(LibraryTags.entry(id.value)),
        )
        compose.onNodeWithTag(LibraryTags.tab(LibraryView.OWN)).performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.OWN_EMPTY), PATIENCE_MS)
        assertTrue(!exists(LibraryTags.entry(id.value)))
    }

    @Test
    @Config(qualifiers = "ru")
    fun theStatedAuthorIsMarkedInRussian() {
        val id = found()
        show(Mode.Serverless)
        awaitEntry(id)
        assertEquals(listOf("Bean soup", "Автор: anna (указан в файле, не проверен)"), compose.textsOf(LibraryTags.entry(id.value)))
        openEntry(id)
        assertEquals("Автор: anna (указан в файле, не проверен)", compose.textsOf(CardTags.STATED_AUTHOR).single())
    }

    // FLOW-064 (1, 2, 3): it opens in full as a card that scales, marks the stated author, has Fork and no Edit; back
    // returns to the marked recipes.
    @Test
    fun aFoundRecipeOpensAsACardThatScalesWithForkAndNoEdit() {
        val id = found()
        show(Mode.Serverless)
        openEntry(id)
        assertEquals("Bean soup", compose.textsOf(CardTags.TITLE).single())
        assertEquals("Author: anna (stated in the file, not verified)", compose.textsOf(CardTags.STATED_AUTHOR).single())
        assertTrue(exists(CardTags.FORK))
        assertTrue(!exists(CardTags.EDIT))
        assertEquals("Servings: 2", compose.textsOf(CardTags.SERVINGS).single())
        assertEquals("200 g", compose.textsOf(CardTags.ingredient(0))[1])
        compose.onNodeWithTag(CardTags.SERVINGS_MORE).performClick()
        compose.waitForIdle()
        assertEquals("Servings: 3", compose.textsOf(CardTags.SERVINGS).single())
        assertEquals("300 g", compose.textsOf(CardTags.ingredient(0))[1])
        compose.backThroughTheCard()
        compose.onNodeWithTag(LibraryTags.tab(LibraryView.MARKED)).assertIsSelected()
        awaitEntry(id)
    }

    // FLOW-064 (3): a file naming no author shows none, on the entry or on the card.
    @Test
    fun aFileNamingNoAuthorShowsNone() {
        val id = found(TestRecipeFiles.soup(author = null))
        show(Mode.Serverless)
        awaitEntry(id)
        assertEquals(listOf("Bean soup"), compose.textsOf(LibraryTags.entry(id.value)))
        assertEquals(0, compose.onAllNodes(hasTestTag(LibraryTags.statedAuthor(id.value)), useUnmergedTree = true).fetchSemanticsNodes().size)
        openEntry(id)
        assertEquals(0, compose.onAllNodes(hasTestTag(CardTags.STATED_AUTHOR), useUnmergedTree = true).fetchSemanticsNodes().size)
        val author = hasText("Author", substring = true) or hasContentDescription("Author", substring = true)
        assertEquals(0, compose.onAllNodes(author, useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    // FLOW-064 (2): the fork works as it does for an own recipe — the editor opens on an editable fork naming the
    // found recipe as its parent, and leaving it shows the fork's card, among the own recipes.
    @Test
    fun aFoundRecipeForks() {
        val id = found()
        show(Mode.Serverless)
        openEntry(id)
        compose.onNodeWithTag(CardTags.FORK).performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.FORM), PATIENCE_MS)
        val forkId = RecipeId(requireNotNull(session.editing.value))
        assertNotEquals(id, forkId)
        val fork = requireNotNull(runBlocking { recipes.get(forkId) })
        assertEquals(id, fork.parent)
        assertEquals(RecipeState.LOCAL, fork.state)
        assertTrue(fork.state.editable)
        assertNull(fork.statedAuthor)
        compose.onNodeWithTag(EditorTags.BACK).performClick()
        compose.backThroughTheCard()
        compose.onNodeWithTag(LibraryTags.tab(LibraryView.OWN)).assertIsSelected()
        awaitEntry(forkId)
        // The found recipe is unchanged, and still found.
        assertEquals(RecipeState.LINK_SHARED, runBlocking { recipes.get(id) }!!.state)
        assertEquals(listOf(id.value), runBlocking { recipes.marked().first() }.map { it.recipeId })
    }

    // FLOW-064 (6): removed from its entry, after a statement, it leaves the book with its content; declining keeps it.
    @Test
    fun aFoundRecipeIsRemovedFromItsEntry() {
        val id = found()
        show(Mode.Serverless)
        awaitEntry(id)
        compose.onNodeWithTag(LibraryTags.remove(id.value)).performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.REMOVE_STATEMENT), PATIENCE_MS)
        compose.onNodeWithTag(LibraryTags.REMOVE_DECLINE).performClick()
        compose.waitUntilDoesNotExist(hasTestTag(LibraryTags.REMOVE_STATEMENT), PATIENCE_MS)
        assertNotNull(runBlocking { recipes.get(id) })
        compose.onNodeWithTag(LibraryTags.remove(id.value)).performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.REMOVE_STATEMENT), PATIENCE_MS)
        compose.onNodeWithTag(LibraryTags.REMOVE_CONFIRM).performClick()
        compose.waitUntilDoesNotExist(hasTestTag(LibraryTags.entry(id.value)), PATIENCE_MS)
        compose.waitUntil(PATIENCE_MS) { runBlocking { recipes.get(id) } == null }
        assertEquals(emptyList<Any>(), runBlocking { recipes.marked().first() })
        assertEquals(0, runBlocking { recipes.count() })
    }

    // A11Y-003, A11Y-005: the entry, its removal and the import are large enough and say what they do.
    @Test
    fun theOffersAreLargeEnoughAndSayWhatTheyDo() {
        found()
        show(Mode.Serverless)
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.IMPORT), PATIENCE_MS)
        AccessibilityChecks.assertTouchTargets(compose, expectAtLeast = 3)
        AccessibilityChecks.assertInteractiveElementsLabelled(compose, expectAtLeast = 3)
    }

    // FLOW-064 (1), FLOW-042: in serverless mode the marked view offers the import; with nothing marked it says so.
    @Test
    fun inServerlessModeTheMarkedViewOffersTheImport() {
        show(Mode.Serverless)
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.MARKED_EMPTY), PATIENCE_MS)
        assertEquals("No recipes are marked in this book yet.", compose.textsOf(LibraryTags.MARKED_EMPTY).single())
        assertEquals(listOf("Import a recipe file"), compose.textsOf(LibraryTags.IMPORT))
    }

    // FLOW-062 (8), FLOW-064 (7): in standard mode no import exists, in either view.
    @Test
    fun inStandardModeNoImportExists() {
        found()
        show(Mode.Standard)
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.entry(TestRecipeFiles.soup().id.value)), PATIENCE_MS)
        val import = hasText("Import", substring = true, ignoreCase = true) or
            hasContentDescription("Import", substring = true, ignoreCase = true)
        assertTrue(!exists(LibraryTags.IMPORT))
        assertEquals(0, compose.onAllNodes(import, useUnmergedTree = true).fetchSemanticsNodes().size)
        compose.onNodeWithTag(LibraryTags.tab(LibraryView.OWN)).performClick()
        compose.waitForIdle()
        assertEquals(0, compose.onAllNodes(import, useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    // FLOW-064 (1): the import action opens the system's document picker on any file; the file picked is imported,
    // said to be, and listed.
    @Test
    fun theImportActionImportsThePickedFile() {
        show(Mode.Serverless)
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.IMPORT), PATIENCE_MS)
        compose.onNodeWithTag(LibraryTags.IMPORT).performClick()
        val activity = shadowOf(compose.activity)
        compose.waitUntil(PATIENCE_MS) { activity.peekNextStartedActivityForResult() != null }
        val started = activity.nextStartedActivityForResult
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, started.intent.action)
        // Picked from outside the application's own data, where the importer reads nothing.
        val picked = TestRecipeFiles.incoming()
        val file = TestRecipeFiles.write(picked, "Bean soup.recipefork", TestRecipeFiles.file(TestRecipeFiles.soup()))
        compose.runOnUiThread {
            activity.receiveResult(started.intent, Activity.RESULT_OK, Intent().setData(Uri.fromFile(file)))
        }
        val id = TestRecipeFiles.soup().id
        awaitEntry(id)
        compose.awaitFileMessage("“Bean soup” is in your recipe book now")
        assertTrue(!exists(LibraryTags.MARKED_EMPTY))
        compose.closeFileMessage()
        // A picked file that is no recipe file is refused and said to be; nothing more is listed.
        compose.onNodeWithTag(LibraryTags.IMPORT).performClick()
        compose.waitUntil(PATIENCE_MS) { activity.peekNextStartedActivityForResult() != null }
        val again = activity.nextStartedActivityForResult
        val other = TestRecipeFiles.write(picked, "photo.jpg", TestRecipeFiles.jpeg())
        compose.runOnUiThread { activity.receiveResult(again.intent, Activity.RESULT_OK, Intent().setData(Uri.fromFile(other))) }
        compose.awaitFileMessage("This file is not a recipe file, so nothing was added")
        assertEquals(1, runBlocking { recipes.marked().first() }.size)
    }

    // FLOW-063 (1), FLOW-024 (6): an imported draft shows its unfinished and off-grid rows as written, unscaled, and
    // scales the rest.
    @Test
    fun anImportedDraftShowsItsUnfinishedRowsAsWrittenAndScalesTheRest() {
        val id = found(TestRecipeFiles.draft())
        show(Mode.Serverless)
        openEntry(id)
        fun quantities() = (0..2).map { compose.textsOf(CardTags.ingredient(it)).getOrNull(1) }
        val before = quantities()
        assertEquals("200 g", before[0])
        compose.onNodeWithTag(CardTags.SERVINGS_MORE).performClick()
        compose.waitForIdle()
        assertEquals("Servings: 3", compose.textsOf(CardTags.SERVINGS).single())
        val after = quantities()
        assertEquals("300 g", after[0])
        // The unfinished and the off-grid rows read as they were written, at either count.
        assertEquals(before[1], after[1])
        assertEquals(before[2], after[2])
        // As an own draft's are (CardServingsTest): the row carries its unit and no value it does not hold.
        assertEquals(listOf("200 g", "ml", "g"), before)
        assertEquals(listOf("300 g", "ml", "g"), after)
    }
}
