package app.recipefork.app.file

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.MainActivity
import app.recipefork.app.Mode
import app.recipefork.app.RecipeForkApplication
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.library.LibrarySession
import app.recipefork.app.library.LibraryView
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.file.RecipeFile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowToast
import java.io.File
import java.time.Instant

/**
 * FLOW-064: a recipe file opened from another application — tapped in a messenger or a file manager — reaches the
 * application through `ACTION_VIEW` (RecipeFileRoutingTest) and is imported in serverless mode only. Each test of the import passes the mode it is about.
 */
@RunWith(AndroidJUnit4::class)
class OpenRecipeFileTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val recipes = RecipeRepository(ActiveBook(BookName.Pool, book.database))
    private val files = File(context.cacheDir, "open-test-files").also { it.deleteRecursively() }
    private val importer = RecipeImport(recipes, files, clock = { Instant.parse("2026-10-02T12:00:00Z") })
    private val session = LibrarySession(SavedStateHandle())
    private val incoming = TestRecipeFiles.incoming()

    @After
    fun close() {
        book.close()
        files.deleteRecursively()
        incoming.deleteRecursively()
    }

    private fun fileOf(bytes: ByteArray, name: String = "Bean soup.recipefork"): Uri =
        Uri.fromFile(TestRecipeFiles.write(incoming, name, bytes))

    private fun open(mode: Mode, uri: Uri) {
        val work = MainActivity.openRecipeFile(context, mode, importer, session, uri)
        // The import runs on the session's scope; the main looper and the book's threads are let run to its end.
        while (work != null && !work.isCompleted) {
            ShadowLooper.idleMainLooper()
            Thread.sleep(5)
        }
        ShadowLooper.idleMainLooper()
    }

    /** What the person is told: the message the session holds, said in a dialog and never in a toast. */
    private fun told(): String? {
        assertNull(ShadowToast.getTextOfLatestToast())
        return session.message.value?.text(context.resources)
    }

    private fun view(uri: Uri, type: String?) = Intent(Intent.ACTION_VIEW).setDataAndType(uri, type)

    // FLOW-064 (1): in serverless mode, a file opened from another application is added, said to be, and the marked
    // recipes are shown, where it is listed.
    @Test
    fun inServerlessModeAnOpenedFileIsImported() = runBlocking {
        session.choose(LibraryView.OWN)
        session.openCard("something")
        val uri = fileOf(TestRecipeFiles.file(TestRecipeFiles.soup()))
        assertEquals(uri, RecipeFileIntents.fileOf(view(uri, RecipeFile.MIME_TYPE)))
        open(Mode.Serverless, uri)
        assertEquals(1, recipes.marked().first().size)
        assertEquals("“Bean soup” is in your recipe book now", told())
        assertEquals(LibraryView.MARKED, session.view.value)
        assertNull(session.viewing.value)
    }

    // FLOW-064 (4, 5): opened again, or opened damaged, the person is told why nothing was added.
    @Test
    fun anOpenedFileAlreadyHeldOrDamagedIsSaidToBe() = runBlocking {
        val uri = fileOf(TestRecipeFiles.file(TestRecipeFiles.soup()))
        open(Mode.Serverless, uri)
        session.choose(LibraryView.OWN)
        open(Mode.Serverless, uri)
        assertEquals("“Bean soup” is already in your recipe book, so nothing was added", told())
        assertEquals(LibraryView.OWN, session.view.value)
        open(Mode.Serverless, fileOf("garbage".toByteArray(), "other.recipefork"))
        assertEquals("This file is not a recipe file, so nothing was added", told())
        open(Mode.Serverless, fileOf(TestRecipeFiles.zip(RecipeFile.DOCUMENT to "{".toByteArray()), "broken.recipefork"))
        assertEquals("This recipe file is damaged, so nothing was added", told())
        val later = RecipeFile.encode(TestRecipeFiles.soup("other")).replace("\"format_version\": ${RecipeFile.FORMAT_VERSION}", "\"format_version\": 7")
        assertTrue(later.contains("\"format_version\": 7"))
        open(Mode.Serverless, fileOf(TestRecipeFiles.zip(RecipeFile.DOCUMENT to later.toByteArray()), "later.recipefork"))
        assertEquals(
            "This recipe file was made by a newer version of the app. Update the app to open it; nothing was added",
            told(),
        )
        open(Mode.Serverless, Uri.fromFile(File(incoming, "missing.recipefork")))
        assertEquals("Couldn't open the file, so nothing was added", told())
        assertEquals(1, recipes.count())
    }

    @Test
    @Config(qualifiers = "ru")
    fun theOutcomeIsSaidInRussian() = runBlocking {
        val uri = fileOf(TestRecipeFiles.file(TestRecipeFiles.soup()))
        open(Mode.Serverless, uri)
        assertEquals("«Bean soup» теперь в вашей книге рецептов", told())
        open(Mode.Serverless, fileOf("garbage".toByteArray(), "other.recipefork"))
        assertEquals("Это не файл рецепта, ничего не добавлено", told())
    }

    // FLOW-064 (7): in standard mode, a file opened imports nothing and is not even read.
    @Test
    fun inStandardModeAnOpenedFileImportsNothing() = runBlocking {
        session.choose(LibraryView.OWN)
        val uri = fileOf(TestRecipeFiles.file(TestRecipeFiles.soup()))
        assertNull(MainActivity.openRecipeFile(context, Mode.Standard, importer, session, uri))
        ShadowLooper.idleMainLooper()
        assertEquals(0, recipes.count())
        assertEquals(emptyList<Any>(), recipes.marked().first())
        assertNull(told())
        assertNull(ShadowToast.getTextOfLatestToast())
        assertEquals(LibraryView.OWN, session.view.value)
    }

    // The application as built (standard): opening a recipe file starts it and adds nothing to its book.
    @Test
    fun theStandardBuildOpensAFileAndAddsNothing() = runBlocking {
        val uri = fileOf(TestRecipeFiles.file(TestRecipeFiles.soup()))
        val intent = view(uri, RecipeFile.MIME_TYPE).setClass(context, MainActivity::class.java)
        ActivityScenario.launch<MainActivity>(intent).use {
            ShadowLooper.idleMainLooper()
            val container = (context as RecipeForkApplication).container
            assertEquals(Mode.Standard, container.mode)
            assertEquals(0, container.recipes.count())
            assertEquals(emptyList<Any>(), container.recipes.marked().first())
        }
    }

    // A timer's notification is a view of the application's own address: no file, and nothing is imported from it.
    @Test
    fun aTimersNotificationIsNoFile() {
        val notification = app.recipefork.app.timer.TimerIntents.uriOf(app.recipefork.app.timer.TimerKey("book-pool.db", "r1", 0))
        assertNull(RecipeFileIntents.fileOf(view(notification, null)))
        assertNull(RecipeFileIntents.fileOf(Intent(Intent.ACTION_SEND).setData(fileOf(ByteArray(1)))))
        val stream = fileOf(ByteArray(1))
        val shared = Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, stream)
        assertNull(RecipeFileIntents.fileOf(Intent(shared).setType("image/jpeg")))
        assertEquals(stream, RecipeFileIntents.fileOf(Intent(shared).setType(RecipeFile.MIME_TYPE)))
        assertNull(RecipeFileIntents.fileOf(null))
    }
}
