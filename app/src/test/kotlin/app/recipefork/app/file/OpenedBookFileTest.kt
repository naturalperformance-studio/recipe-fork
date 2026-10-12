package app.recipefork.app.file

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.MainActivity
import app.recipefork.app.Mode
import app.recipefork.app.RecipeForkApplication
import app.recipefork.app.ServerlessTestApplication
import app.recipefork.app.TestRecipeForkApplication
import app.recipefork.app.backup.BackupNotice
import app.recipefork.app.backup.BackupState
import app.recipefork.app.backup.BookBackup
import app.recipefork.app.backup.BookDocuments
import app.recipefork.app.backup.BookFileReader
import app.recipefork.app.backup.BookFileWriter
import app.recipefork.app.backup.BookRestore
import app.recipefork.app.backup.BookFileRefusal
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookDatabase
import app.recipefork.app.book.BookName
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.kitchen.HeldKitchen
import app.recipefork.app.library.LibrarySession
import app.recipefork.app.media.PhotoStore
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.settings.SettingsTags
import app.recipefork.core.recipe.Recipe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

/**
 * T-242, FLOW-073 scenario 4: a saved book opened from another application passes through [OpenRecipeFileActivity] to
 * [MainActivity], which starts its restore — the settings' own, with the same question, progress and notices — and
 * says so when another saving or restore runs; the recipe import refuses a book.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = ServerlessTestApplication::class)
class OpenedBookFileTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val application = ApplicationProvider.getApplicationContext<RecipeForkApplication>()
    private val container = application.container
    private val incoming = TestRecipeFiles.incoming()
    private var opened: ActivityController<MainActivity>? = null

    private val sourceName = BookName.OfAccount("opened-book-source")
    private val sourceDatabase = BookDatabase.open(application, sourceName)
    private val source = ActiveBook(sourceName, sourceDatabase)
    private val sourceRecipes = RecipeRepository(source, application.filesDir)
    private val now = Instant.parse("2026-10-12T12:00:00Z")

    @After
    fun close() {
        opened?.pause()?.stop()?.destroy()
        container.backup.cancel()
        sourceDatabase.close()
        application.deleteDatabase(sourceName.fileName)
        PhotoStore(application.filesDir).directory(sourceName).deleteRecursively()
        incoming.deleteRecursively()
    }

    /** A book file of the source book, once [titles] are saved in it, under [name], as a messenger keeps it. */
    private fun bookFile(name: String, vararg titles: String): Uri = runBlocking {
        titles.forEach { sourceRecipes.save(Recipe.create(now, null).copy(title = it)) }
        val file = File(incoming, name)
        file.outputStream().use { BookFileWriter(application).write(source.open.value, emptySet(), it) }
        Uri.fromFile(file)
    }

    /** What the opener hands on to MainActivity for [intent], as the platform would deliver it. */
    private fun handedOn(intent: Intent): Intent {
        val opener = Robolectric.buildActivity(OpenRecipeFileActivity::class.java, intent).create().get()
        return shadowOf(opener).nextStartedActivity!!
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
    private fun await(tag: String) = compose.waitUntil(PATIENCE_MS) { exists(tag) }
    private fun awaitText(text: String) =
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }

    private fun inBook(): Int = runBlocking { container.recipes.count() }

    // Tapped in the messenger, a book opens the application on its restore in the settings: into an empty book it is
    // restored at once; opened again into a book that holds recipes, the settings' question is asked, and a third file
    // opened meanwhile is not taken — the person is told so — while the question stays.
    @Test
    fun anOpenedBookIsRestoredAsFromTheSettings() {
        val first = bookFile("Recipe book 2026-10-12.recipefork-book", "Soup", "Pie")
        opened = Robolectric.buildActivity(MainActivity::class.java, handedOn(
            Intent(Intent.ACTION_VIEW).setDataAndType(first, "text/plain"),
        )).setup()
        await(SettingsTags.BACKUP_RESTORE)
        awaitText("Restored 2 recipes")
        assertEquals(2, inBook())
        assertFalse(exists(FileMessageTags.DIALOG))

        val second = bookFile("Recipe book 2026-10-13.recipefork-book", "Stew")
        opened!!.newIntent(handedOn(Intent(Intent.ACTION_VIEW).setDataAndType(second, "application/octet-stream")))
        await(SettingsTags.BACKUP_QUESTION)

        opened!!.newIntent(handedOn(Intent(Intent.ACTION_VIEW).setDataAndType(first, null)))
        awaitText("The book is already being saved or restored, so this file was not opened. Open it again once that is done.")
        compose.onNodeWithTag(FileMessageTags.OK).performClick()
        assertTrue(container.backup.state.value is BackupState.Asking)

        compose.onNodeWithTag(SettingsTags.BACKUP_ADD).performClick()
        awaitText("Added 1 recipe; 2 were already in the book")
        assertEquals(3, inBook())
    }

    // The hand-over shows the settings in place of whatever was open, and starts the restore with that very file.
    @Test
    fun theHandOverStartsTheRestoreWithThatFile() {
        val file = bookFile("book.recipefork-book", "Soup")
        val session = LibrarySession(SavedStateHandle())
        session.openCard("some-recipe")
        assertTrue(MainActivity.openBookFile(Mode.Serverless, container.backup, session, file))
        assertNull(session.viewing.value)
        assertTrue(session.settings.value)
        compose.waitUntil(PATIENCE_MS) { container.backup.state.value is BackupState.Ended }
        assertEquals(BackupState.Ended(BackupNotice.Restored(1)), container.backup.state.value)
        assertEquals(1, inBook())
    }

    // The import of a recipe file refuses a book, should one reach it some other way (the in-app Import's picker).
    @Test
    fun theRecipeImportRefusesABook() = runBlocking {
        val file = bookFile("book.recipefork-book", "Soup")
        val outcome = RecipeImport(container.recipes, File(application.cacheDir, "opened-book-import"))
            .importSafely(application, file)
        assertEquals(ImportOutcome.Refused(Refusal.NOT_A_RECIPE_FILE), outcome)
        assertEquals(0, inBook())
    }

    // The standard build has no backup (FLOW-073 is serverless): a book opened there starts it and is neither read nor
    // said anything of.
    @Test
    @Config(application = TestRecipeForkApplication::class)
    fun theStandardBuildOpensABookAndRestoresNothing() {
        val file = bookFile("book.recipefork-book", "Soup")
        assertEquals(Mode.Standard, container.mode)
        val session = LibrarySession(SavedStateHandle())
        assertFalse(MainActivity.openBookFile(Mode.Standard, container.backup, session, file))
        assertFalse(session.settings.value)
        opened = Robolectric.buildActivity(MainActivity::class.java, handedOn(
            Intent(Intent.ACTION_VIEW).setDataAndType(file, "text/plain"),
        )).setup()
        compose.waitForIdle()
        assertEquals(BackupState.Idle, container.backup.state.value)
        assertEquals(0, inBook())
        assertFalse(exists(FileMessageTags.DIALOG))
    }

    // The reading of a handed-over file is granted only while the activity it was handed to lives; the restore reads on
    // its own thread, later. Opened at the hand-over, the file is read whole even when the grant is gone by the time
    // the restore runs — and the same file not opened then cannot be read at all.
    @Test
    fun aHandedOverBookIsReadAfterItsGrantIsGone() {
        val file = bookFile("book.recipefork-book", "Soup", "Pie")
        val documents = GrantedDocuments()
        val held = CountDownLatch(1)
        val worker = Executors.newSingleThreadExecutor().apply { execute { held.await() } }
        val kitchen = HeldKitchen()
        val backup = BookBackup(
            container.activeBook, kitchen, documents, BookFileWriter(application), BookFileReader(application),
            BookRestore(application.filesDir, kitchen), CoroutineScope(SupervisorJob() + worker.asCoroutineDispatcher()),
        )
        try {
            val session = LibrarySession(SavedStateHandle())
            assertTrue(MainActivity.openBookFile(Mode.Serverless, backup, session, file))
            documents.granted = false
            held.countDown()
            compose.waitUntil(PATIENCE_MS) { backup.state.value is BackupState.Ended }
            assertEquals(BackupState.Ended(BackupNotice.Restored(2)), backup.state.value)
            assertEquals(2, inBook())

            val again = CountDownLatch(1)
            worker.execute { again.await() }
            documents.granted = true
            assertTrue(backup.restoreFrom(file))
            documents.granted = false
            again.countDown()
            compose.waitUntil(PATIENCE_MS) { backup.state.value is BackupState.Ended }
            assertEquals(BackupState.Ended(BackupNotice.Refused(BookFileRefusal.CANNOT_READ)), backup.state.value)
        } finally {
            worker.shutdownNow()
        }
    }

    /** Documents readable only while [granted], as a handed-over address is while its receiving activity lives. */
    private class GrantedDocuments : BookDocuments {
        @Volatile var granted = true

        private fun check(uri: Uri) = if (!granted) throw SecurityException("no grant for $uri") else Unit
        override fun input(uri: Uri): InputStream = check(uri).let { File(uri.path!!).inputStream() }
        override fun size(uri: Uri): Long = check(uri).let { File(uri.path!!).length() }
        override fun output(uri: Uri): OutputStream = throw UnsupportedOperationException()
        override fun delete(uri: Uri) = Unit
    }
}
