package app.recipefork.app.file

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.MainActivity
import app.recipefork.app.Mode
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.collections.CollectionRepository
import app.recipefork.app.library.LibrarySession
import app.recipefork.app.library.LibraryView
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.file.CollectionFile
import app.recipefork.core.file.CollectionFileContent
import app.recipefork.core.file.RecipeFile
import app.recipefork.core.recipe.CollectionId
import app.recipefork.core.recipe.CollectionOrigin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowToast
import java.io.File
import java.time.Instant

/**
 * A collection file opened from another application (v0.36, T-256): imported in serverless mode only, as every file
 * feature is (FLOW-061); the person told what became of it, and the collection shown where it stands — above the
 * imported recipes, or the reader's own above the own ones.
 */
@RunWith(AndroidJUnit4::class)
class OpenCollectionFileTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val active = ActiveBook(BookName.Pool, book.database)
    private val recipes = RecipeRepository(active)
    private val collections = CollectionRepository(active)
    private val files = File(context.cacheDir, "open-collection-files").also { it.deleteRecursively() }
    private val importer = CollectionImport(active, files, clock = { Instant.parse("2026-10-13T12:00:00Z") })
    private val session = LibrarySession(SavedStateHandle())
    private val incoming = TestRecipeFiles.incoming()
    private val menu = CollectionId("menu-1")

    @After
    fun close() {
        book.close()
        files.deleteRecursively()
        incoming.deleteRecursively()
    }

    private fun collectionFile(id: CollectionId = menu): Uri {
        val soup = TestRecipeFiles.soup()
        val bytes = TestRecipeFiles.zip(
            CollectionFile.DOCUMENT to CollectionFile.encode(CollectionFileContent(id, "New Year's menu", null, listOf(soup.id))).toByteArray(),
            CollectionFile.recipeFolder(0) + RecipeFile.DOCUMENT to TestRecipeFiles.document(soup),
        )
        return Uri.fromFile(TestRecipeFiles.write(incoming, "New Year's menu.recipefork-collection", bytes))
    }

    private fun open(mode: Mode, uri: Uri) {
        val work = MainActivity.openCollectionFile(context, mode, importer, session, uri)
        // The import runs on the session's scope; the main looper and the book's threads are let run to its end.
        while (work != null && !work.isCompleted) {
            ShadowLooper.idleMainLooper()
            Thread.sleep(5)
        }
        ShadowLooper.idleMainLooper()
    }

    private fun told(): String? {
        assertNull(ShadowToast.getTextOfLatestToast())
        return session.message.value?.text(context.resources)
    }

    // Scenario 5: in serverless mode the file is imported, said to be, and the imported recipes shown — unnarrowed, so
    // that the collection stands above them — in place of whatever was open.
    @Test
    fun inServerlessModeAnOpenedCollectionIsImported() = runBlocking {
        session.choose(LibraryView.OWN)
        session.search(LibraryView.MARKED, "duck")
        session.openCard("something")
        open(Mode.Serverless, collectionFile())
        assertEquals(CollectionOrigin.IMPORTED, collections.get(menu)!!.origin)
        assertEquals(1, recipes.marked().first().size)
        assertEquals("“New Year's menu” added: 1 recipe", told())
        assertEquals(menu.value, session.message.value!!.collectionId)
        assertEquals(LibraryView.MARKED, session.view.value)
        assertEquals("", session.query(LibraryView.MARKED).value)
        assertNull(session.viewing.value)
        // Damaged: said in its dialog, nothing more added.
        open(Mode.Serverless, Uri.fromFile(TestRecipeFiles.write(incoming, "broken.recipefork-collection", "garbage".toByteArray())))
        assertEquals("This file is not a collection file, so nothing was added", told())
        assertEquals(1, recipes.count())
    }

    // The reader's own collection come back: its notice, the own recipes shown, nothing changed.
    @Test
    fun anOwnCollectionComeBackIsSaidAndShown() = runBlocking {
        val own = collections.create("Our menu")!!
        open(Mode.Serverless, collectionFile(own))
        assertEquals("You already have this collection", told())
        assertEquals(LibraryView.OWN, session.view.value)
        assertEquals(0, recipes.count())
        assertEquals("Our menu", collections.get(own)!!.name)
    }

    // FLOW-061: in standard mode a collection file imports nothing and is not even read.
    @Test
    fun inStandardModeAnOpenedCollectionImportsNothing() = runBlocking {
        session.choose(LibraryView.OWN)
        assertNull(MainActivity.openCollectionFile(context, Mode.Standard, importer, session, collectionFile()))
        ShadowLooper.idleMainLooper()
        assertEquals(0, recipes.count())
        assertNull(collections.get(menu))
        assertNull(told())
        assertEquals(LibraryView.OWN, session.view.value)
    }
}
