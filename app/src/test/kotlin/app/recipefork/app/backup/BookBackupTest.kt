package app.recipefork.app.backup

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.Mode
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookDatabase
import app.recipefork.app.book.BookName
import app.recipefork.app.card.CardEnvironment
import app.recipefork.app.card.IngredientEntries
import app.recipefork.app.data.HeldThemeSetting
import app.recipefork.app.data.HeldUsualServings
import app.recipefork.app.dictionary.TagEntity
import app.recipefork.app.dictionary.TagSource
import app.recipefork.app.editor.EditorEnvironment
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.editor.textsOf
import app.recipefork.app.kitchen.HeldKitchen
import app.recipefork.app.library.LibrarySession
import app.recipefork.app.library.LibraryTags
import app.recipefork.app.library.PendingRemoval
import app.recipefork.app.media.PhotoStore
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.settings.SettingsTags
import app.recipefork.app.shopping.HeldShopping
import app.recipefork.app.shopping.ListedRecipe
import app.recipefork.app.shopping.OwnLine
import app.recipefork.app.shopping.ShoppingEntry
import app.recipefork.app.shopping.ShoppingList
import app.recipefork.app.shopping.shoppingEntries
import app.recipefork.app.timer.TestTimers
import app.recipefork.app.ui.AccessibilityChecks
import app.recipefork.app.ui.AppNavHost
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.scaling.Rational
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FilterInputStream
import java.io.FilterOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * T-241: the book saved and restored from the serverless build's settings (FLOW-073) — the pickers, the question and
 * its confirmation, the notices, a refusal, a cancel at each point, the operation kept through a turned screen and a
 * left one, and the shopping list after a Replace.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class BookBackupTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val now = Instant.parse("2026-10-12T12:00:00Z")
    private val documentsDir = File(context.cacheDir, "backup-test-documents").apply { mkdirs() }

    private val sourceName = BookName.OfAccount("backup-source")
    private val sourceDatabase = BookDatabase.open(context, sourceName)
    private val sourceRecipes = RecipeRepository(ActiveBook(sourceName, sourceDatabase), context.filesDir)
    private val source = ActiveBook(sourceName, sourceDatabase)

    private val bookName = BookName.OfAccount("backup-book")
    private val bookDatabase = BookDatabase.open(context, bookName)
    private val book = ActiveBook(bookName, bookDatabase)
    private val recipes = RecipeRepository(book, context.filesDir)

    private val kitchen = HeldKitchen()
    private val documents = FileDocuments()

    /** Holds the restore inside its transaction while set. */
    private var inTransaction: Gate? = null
    private val backup = BookBackup(
        book, kitchen, documents, BookFileWriter(context), BookFileReader(context),
        BookRestore(context.filesDir, kitchen) { inTransaction?.pass() },
    )

    private val session = LibrarySession(SavedStateHandle())
    private val timers = TestTimers(context)

    /** What each picker was launched with, and the document it answers with. */
    private val launched = mutableListOf<Intent>()
    private var picked: Uri? = null
    private val registry = object : ActivityResultRegistry() {
        override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
            launched += contract.createIntent(context, input)
            dispatchResult(requestCode, Activity.RESULT_OK, Intent().setData(picked))
        }
    }

    @After
    fun delete() {
        listOf(sourceDatabase to sourceName, bookDatabase to bookName).forEach { (database, name) ->
            database.close()
            context.deleteDatabase(name.fileName)
            PhotoStore(context.filesDir).directory(name).deleteRecursively()
        }
        documentsDir.deleteRecursively()
        timers.close()
    }

    private var mode by mutableStateOf(Mode.Serverless)

    private fun show(): StateRestorationTester {
        session.openSettings()
        val tester = StateRestorationTester(compose)
        val owner = object : ActivityResultRegistryOwner {
            override val activityResultRegistry = registry
        }
        tester.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                MaterialTheme {
                    AppNavHost(
                        session, recipes, mode,
                        card = CardEnvironment(HeldUsualServings(), ingredients = IngredientEntries { emptyList() }),
                        editor = EditorEnvironment(book, NoTags) { now },
                        themeSetting = HeldThemeSetting(),
                        backup = backup,
                    )
                }
            }
        }
        await(SettingsTags.CLEAR_MEDIA)
        awaitCounted()
        return tester
    }

    /**
     * Waits until the saved media's item has counted what clearing would delete: its query reads the book, which the
     * test closes as it ends.
     */
    private fun awaitCounted() = compose.waitUntil(PATIENCE_MS) {
        exists(SettingsTags.CLEAR_MEDIA) &&
            compose.onAllNodes(hasTestTag(SettingsTags.CLEAR_MEDIA) and hasText("Counting…", substring = true)).fetchSemanticsNodes().isEmpty()
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
    private fun shows(text: String) = compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
    private fun await(tag: String) = compose.waitUntil(PATIENCE_MS) { exists(tag) }
    private fun awaitText(text: String) = compose.waitUntil(PATIENCE_MS) { shows(text) }
    private fun tap(tag: String) = compose.onNodeWithTag(tag).performScrollTo().performClick()
    private fun press(tag: String) = compose.onNodeWithTag(tag).performClick()
    private fun awaitState(test: (BackupState) -> Boolean) = compose.waitUntil(PATIENCE_MS) { test(backup.state.value) }

    private fun recipe(title: String) = Recipe.create(now, null).copy(title = title)

    /** A book file of [held] saved in a source book, to be picked. */
    private fun bookFile(vararg held: Recipe): Uri = runBlocking {
        held.forEach { sourceRecipes.save(it) }
        val file = File(documentsDir, "saved.recipefork-book")
        file.outputStream().use { BookFileWriter(context).write(source.open.value, setOf(5L), it) }
        Uri.fromFile(file)
    }

    private fun holding(vararg held: Recipe) = runBlocking { held.forEach { recipes.save(it) } }
    private fun titles(): Set<String> = runBlocking { bookDatabase.recipes().ids().map { recipes.get(RecipeId(it))!!.title }.toSet() }
    private fun noneOpened() = File(context.cacheDir, "opened-books").listFiles().orEmpty().isEmpty()

    @Test
    fun theItemsAreInTheServerlessBuildOnly() {
        mode = Mode.Standard
        show()
        assertFalse(exists(SettingsTags.BACKUP_SAVE) || exists(SettingsTags.BACKUP_RESTORE) || shows("Backup"))
        mode = Mode.Serverless
        await(SettingsTags.BACKUP_RESTORE)
        assertTrue(shows("Backup") && shows("Save the book to a file") && shows("Restore from a file"))
        AccessibilityChecks.assertTouchTargets(compose)
        // The screen shown anew counts its saved media again: done before the test lets go of the book.
        awaitCounted()
    }

    @Test
    fun savingOffersTheSuggestedNameAndSaysHowManyRecipes() {
        holding(recipe("Soup"), recipe("Pie"))
        val saved = File(documentsDir, "chosen")
        picked = Uri.fromFile(saved)
        show()
        tap(SettingsTags.BACKUP_SAVE)
        awaitText("Book saved: 2 recipes")
        val intent = launched.single()
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, intent.action)
        assertEquals("application/octet-stream", intent.type)
        assertEquals("Recipe book ${LocalDate.now()}.recipefork-book", intent.getStringExtra(Intent.EXTRA_TITLE))
        val opened = runBlocking { BookFileReader(context).open(saved.inputStream()) } as BookFileOpening.Opened
        opened.book.use { assertEquals(2, it.manifest.recipes) }
    }

    @Test
    fun anEmptyBookIsRestoredIntoWithoutAsking() {
        picked = bookFile(recipe("Soup"), recipe("Pie"))
        show()
        tap(SettingsTags.BACKUP_RESTORE)
        awaitText("Restored 2 recipes")
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, launched.single().action)
        assertFalse(exists(SettingsTags.BACKUP_QUESTION))
        assertEquals(setOf("Soup", "Pie"), titles())
        assertEquals(setOf(5L), kitchen.held.value)
    }

    @Test
    fun aBookHoldingRecipesIsAskedFirstAndAddKeepsWhatItHolds() {
        val soup = recipe("Soup")
        picked = bookFile(soup, recipe("Pie"))
        holding(soup.copy(title = "My soup"), recipe("Stew"))
        show()
        tap(SettingsTags.BACKUP_RESTORE)
        await(SettingsTags.BACKUP_QUESTION)
        assertTrue(shows("The book already holds 2 recipes. Add the file's recipes it doesn't hold, or replace the whole book with the file?"))
        listOf(SettingsTags.BACKUP_ADD to "Add to the book", SettingsTags.BACKUP_REPLACE to "Replace the book", SettingsTags.BACKUP_QUESTION_CANCEL to "Cancel")
            .forEach { (tag, label) -> assertEquals(listOf(label), compose.textsOf(tag)) }
        AccessibilityChecks.assertTouchTargets(compose)
        press(SettingsTags.BACKUP_ADD)
        awaitText("Added 1 recipe; 1 was already in the book")
        assertEquals(setOf("My soup", "Stew", "Pie"), titles())
    }

    // The shopping list, open meanwhile: a listed recipe the file also holds stays at its count, read as restored; one
    // the replaced book no longer holds leaves it; the reader's own line stays.
    @Test
    fun replaceIsConfirmedNamingWhatGoesAndTheShoppingListFollows() {
        val soup = recipe("Soup").copy(servings = 2)
        picked = bookFile(soup, recipe("Pie"))
        val stew = recipe("Stew")
        holding(soup.copy(title = "My soup"), stew)
        val milk = OwnLine(bookName.fileName, "1", "milk")
        val listedSoup = ListedRecipe(bookName.fileName, soup.id, Rational.of(6))
        val store = HeldShopping(ShoppingList(listOf(listedSoup, ListedRecipe(bookName.fileName, stew.id, null)), own = listOf(milk)))
        val entries = MutableStateFlow<List<ShoppingEntry>?>(null)
        val watching = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        watching.launch { shoppingEntries(store, recipes).collect { entries.value = it } }
        try {
            compose.waitUntil(PATIENCE_MS) { entries.value?.size == 2 }
            show()
            tap(SettingsTags.BACKUP_RESTORE)
            await(SettingsTags.BACKUP_QUESTION)
            press(SettingsTags.BACKUP_REPLACE)
            await(SettingsTags.BACKUP_CONFIRMATION)
            assertFalse(exists(SettingsTags.BACKUP_QUESTION))
            assertTrue(shows("All 2 recipes of the book will be removed, with their photos and clips, and the file's recipes put in their place. This can't be undone."))
            AccessibilityChecks.assertTouchTargets(compose)
            press(SettingsTags.BACKUP_REPLACE_CONFIRM)
            awaitText("Restored 2 recipes")
            assertEquals(setOf("Soup", "Pie"), titles())
            compose.waitUntil(PATIENCE_MS) { entries.value?.map { it.recipe.title } == listOf("Soup") }
            assertEquals(Rational.of(6), entries.value!!.single().servings)
            assertEquals(listOf(listedSoup), store.list.value.recipes)
            assertEquals(listOf(milk), store.list.value.own)
        } finally {
            watching.cancel()
        }
    }

    @Test
    fun cancellingTheQuestionOrTheConfirmationChangesNothing() {
        picked = bookFile(recipe("Soup"))
        holding(recipe("Stew"))
        show()
        tap(SettingsTags.BACKUP_RESTORE)
        await(SettingsTags.BACKUP_QUESTION)
        press(SettingsTags.BACKUP_QUESTION_CANCEL)
        tap(SettingsTags.BACKUP_RESTORE)
        await(SettingsTags.BACKUP_QUESTION)
        press(SettingsTags.BACKUP_REPLACE)
        await(SettingsTags.BACKUP_CONFIRMATION)
        press(SettingsTags.BACKUP_CONFIRMATION_CANCEL)
        awaitState { it == BackupState.Idle }
        compose.waitUntil(PATIENCE_MS) { noneOpened() }
        assertFalse(exists(SettingsTags.BACKUP_CONFIRMATION) || exists(SettingsTags.BACKUP_QUESTION))
        assertEquals(setOf("Stew"), titles())
        assertEquals(2, launched.size)
    }

    @Test
    fun aFileThatIsNoBookIsRefusedSayingWhy() {
        val file = File(documentsDir, "notes.txt").apply { writeText("shopping: milk") }
        picked = Uri.fromFile(file)
        holding(recipe("Stew"))
        show()
        tap(SettingsTags.BACKUP_RESTORE)
        awaitText("This file can't be restored: it is not a saved recipe book")
        assertEquals(setOf("Stew"), titles())
    }

    @Test
    fun cancellingWhileTheFileIsReadChangesNothing() {
        picked = bookFile(recipe("Soup"))
        val reading = Gate().also { documents.reading = it }
        show()
        tap(SettingsTags.BACKUP_RESTORE)
        assertTrue(reading.reached.await(PATIENCE_MS, TimeUnit.MILLISECONDS))
        await(SettingsTags.BACKUP_PROGRESS)
        assertTrue(shows("Reading the file…"))
        tap(SettingsTags.BACKUP_CANCEL)
        reading.released.countDown()
        awaitState { it == BackupState.Idle }
        compose.waitUntil(PATIENCE_MS) { noneOpened() }
        assertEquals(emptySet<String>(), titles())
    }

    @Test
    fun cancellingASaveLeavesNoFileBehind() {
        holding(recipe("Soup"))
        val saved = File(documentsDir, "chosen")
        picked = Uri.fromFile(saved)
        val writing = Gate().also { documents.writing = it }
        show()
        tap(SettingsTags.BACKUP_SAVE)
        assertTrue(writing.reached.await(PATIENCE_MS, TimeUnit.MILLISECONDS))
        tap(SettingsTags.BACKUP_CANCEL)
        writing.released.countDown()
        awaitState { it == BackupState.Idle }
        assertEquals(listOf(picked), documents.deleted)
        assertFalse(saved.exists())
    }

    // Turned while it asks, the question is asked again, once; left while it reads, the restore goes on, is not
    // started twice, and is said once the settings are shown again.
    @Test
    fun aTurnedOrLeftScreenKeepsTheOneOperation() {
        val uri = bookFile(recipe("Soup"), recipe("Pie"))
        picked = uri
        holding(recipe("Stew"))
        val tester = show()
        tap(SettingsTags.BACKUP_RESTORE)
        await(SettingsTags.BACKUP_QUESTION)
        tester.emulateSavedInstanceStateRestore()
        await(SettingsTags.BACKUP_QUESTION)
        assertFalse(backup.restoreFrom(uri))
        press(SettingsTags.BACKUP_ADD)
        awaitText("Added 2 recipes")
        compose.waitUntil(PATIENCE_MS) { backup.state.value == BackupState.Idle }

        runBlocking { recipes.delete(bookDatabase.recipes().ids().map(::RecipeId).first { recipes.get(it)!!.title == "Pie" }) }
        val reading = Gate().also { documents.reading = it }
        tap(SettingsTags.BACKUP_RESTORE)
        assertTrue(reading.reached.await(PATIENCE_MS, TimeUnit.MILLISECONDS))
        compose.runOnIdle { session.closeSettings() }
        compose.waitUntil(PATIENCE_MS) { exists(LibraryTags.PAGER) && !exists(SettingsTags.SCREEN) }
        reading.released.countDown()
        awaitState { it is BackupState.Asking }
        compose.runOnIdle { session.openSettings() }
        await(SettingsTags.BACKUP_QUESTION)
        press(SettingsTags.BACKUP_ADD)
        awaitText("Added 1 recipe; 1 was already in the book")
        assertEquals(setOf("Stew", "Soup", "Pie"), titles())
        assertEquals(2, launched.size)
    }

    // A cancel that comes once the book is being written finishes it, and the restore is said.
    @Test
    fun aCancelDuringTheCommitStillSaysTheBookWasRestored() {
        val uri = bookFile(recipe("Soup"), recipe("Pie"))
        val writing = Gate().also { inTransaction = it }
        assertTrue(backup.restoreFrom(uri))
        assertTrue(writing.reached.await(PATIENCE_MS, TimeUnit.MILLISECONDS))
        backup.cancel()
        writing.released.countDown()
        awaitState { it == BackupState.Ended(BackupNotice.Restored(2)) }
        assertEquals(setOf("Soup", "Pie"), titles())
    }

    // A recipe whose deletion could still be undone is final once a Replace runs: its Undo would put it into the
    // replaced book.
    @Test
    fun aPendingUndoEndsWhenAReplaceStarts() {
        picked = bookFile(recipe("Soup"))
        val stew = recipe("Stew")
        holding(stew, recipe("Pie"))
        show()
        tap(SettingsTags.BACKUP_RESTORE)
        await(SettingsTags.BACKUP_QUESTION)
        press(SettingsTags.BACKUP_REPLACE)
        val writing = Gate().also { inTransaction = it }
        val taken = runBlocking { recipes.take(stew.id) }!!
        press(SettingsTags.BACKUP_REPLACE_CONFIRM)
        assertTrue(writing.reached.await(PATIENCE_MS, TimeUnit.MILLISECONDS))
        val removal = PendingRemoval(taken, timers.card)
        compose.runOnIdle { session.removed(removal) }
        compose.waitUntil(PATIENCE_MS) { session.removal.value == null }
        writing.released.countDown()
        awaitText("Restored 1 recipe")
        session.undoRemoval(removal)
        compose.waitForIdle()
        assertEquals(setOf("Soup"), titles())
        assertNull(session.removal.value)
    }

    /** Stops the first read or write it is passed by until released. */
    private class Gate {
        val reached = CountDownLatch(1)
        val released = CountDownLatch(1)

        fun pass() {
            if (reached.count == 0L) return
            reached.countDown()
            released.await(PATIENCE_MS, TimeUnit.MILLISECONDS)
        }
    }

    /** Documents as files of the test's own, read and written through [reading] and [writing] when set. */
    private class FileDocuments : BookDocuments {
        var reading: Gate? = null
        var writing: Gate? = null
        val deleted = mutableListOf<Uri>()

        override fun output(uri: Uri): OutputStream = object : FilterOutputStream(File(uri.path!!).outputStream()) {
            override fun write(b: ByteArray, off: Int, len: Int) {
                writing?.pass()
                out.write(b, off, len)
            }
        }

        override fun input(uri: Uri): InputStream = object : FilterInputStream(File(uri.path!!).inputStream()) {
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                reading?.pass()
                return super.read(b, off, len)
            }
        }

        override fun size(uri: Uri): Long = File(uri.path!!).length()

        override fun delete(uri: Uri) {
            deleted += uri
            File(uri.path!!).delete()
        }
    }

    private object NoTags : TagSource {
        override suspend fun tags() = emptyList<TagEntity>()
    }
}
