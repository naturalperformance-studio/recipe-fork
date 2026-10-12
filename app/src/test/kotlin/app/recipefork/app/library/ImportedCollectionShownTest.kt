package app.recipefork.app.library

import android.content.Context
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.MainActivity
import app.recipefork.app.Mode
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.card.CardEnvironment
import app.recipefork.app.collections.CollectionRepository
import app.recipefork.app.collections.CollectionScreenTags
import app.recipefork.app.editor.EditorEnvironment
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.editor.SAMPLE_DICTIONARIES
import app.recipefork.app.editor.TestDevice
import app.recipefork.app.file.CollectionImport
import app.recipefork.app.file.ImportOutcome
import app.recipefork.app.file.RecipeImport
import app.recipefork.app.file.TestRecipeFiles
import app.recipefork.app.file.awaitFileNotice
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.ui.AccessibilityChecks
import app.recipefork.app.ui.AppNavHost
import app.recipefork.core.file.CollectionFile
import app.recipefork.core.file.CollectionFileContent
import app.recipefork.core.file.RecipeFile
import app.recipefork.core.recipe.CollectionId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant

/**
 * Scenario 5 on the screen (v0.36, T-256): a collection file opened from another application is said in the book's
 * snackbar — "“New Year's menu” added: 2 recipes (1 was already in the book)" — with the Imported view shown and the
 * collection standing above its recipes; the snackbar's Open opens the collection.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class ImportedCollectionShownTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val device = TestDevice(context)
    private val active = ActiveBook(BookName.Pool, book.database)
    private val recipes = RecipeRepository(active)
    private val collections = CollectionRepository(active)
    private val session = LibrarySession(SavedStateHandle())
    private val now = Instant.parse("2026-10-13T12:00:00Z")
    private val files = File(context.cacheDir, "imported-collection-files").also { it.deleteRecursively() }
    private val importer = RecipeImport(recipes, files, clock = { now })
    private val collectionImporter = CollectionImport(active, files, clock = { now })
    private val incoming = TestRecipeFiles.incoming()
    private val menu = CollectionId("menu-1")

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
        incoming.deleteRecursively()
    }

    @Test
    fun anImportedCollectionIsSaidAndShownAboveTheImportedRecipes() {
        val salad = TestRecipeFiles.soup(id = "r-salad").copy(title = "Olivier")
        val duck = TestRecipeFiles.soup(id = "r-duck").copy(title = "Duck with apples")
        assertTrue(runBlocking { importer.import { TestRecipeFiles.file(salad).inputStream() } } is ImportOutcome.Added)
        val bytes = TestRecipeFiles.zip(
            CollectionFile.DOCUMENT to CollectionFile.encode(CollectionFileContent(menu, "New Year's menu", "anna", listOf(duck.id, salad.id))).toByteArray(),
            CollectionFile.recipeFolder(0) + RecipeFile.DOCUMENT to TestRecipeFiles.document(duck),
            CollectionFile.recipeFolder(1) + RecipeFile.DOCUMENT to TestRecipeFiles.document(salad),
        )
        val uri = Uri.fromFile(TestRecipeFiles.write(incoming, "New Year's menu.recipefork-collection", bytes))
        session.choose(LibraryView.OWN)
        val editor = EditorEnvironment(active, device.catalog(context)) { now }
        val card = CardEnvironment { device.database.dictionaryDao().ingredients() }
        compose.setContent {
            MaterialTheme { AppNavHost(session, recipes, Mode.Serverless, editor = editor, card = card, files = importer, collections = collections) }
        }
        compose.waitForIdle()

        compose.runOnIdle { MainActivity.openCollectionFile(context, Mode.Serverless, collectionImporter, session, uri) }
        compose.awaitFileNotice("“New Year's menu” added: 2 recipes (1 was already in the book)")
        compose.onNodeWithTag(LibraryTags.tab(LibraryView.MARKED)).assertIsSelected()
        val name = hasTestTag(CollectionScreenTags.cardName(menu.value)) and hasText("New Year's menu")
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(name, useUnmergedTree = true).fetchSemanticsNodes().size == 1 }
        AccessibilityChecks.assertTouchTargets(compose)

        val open = hasText(context.getString(app.recipefork.app.R.string.file_open)) and hasAnyAncestor(hasTestTag(LibraryTags.SNACKBAR))
        compose.onNode(open, useUnmergedTree = true).performClick()
        compose.waitUntil(PATIENCE_MS) { session.collection.value == menu.value }
        compose.waitUntilExactlyOneExists(hasTestTag(CollectionScreenTags.SCREEN), PATIENCE_MS)
        assertEquals(null, session.message.value)
    }
}
