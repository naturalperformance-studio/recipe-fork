package app.recipefork.app.editor

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookDatabase
import app.recipefork.app.book.BookName
import app.recipefork.app.media.PhotoAttaching
import app.recipefork.app.media.PhotoLimits
import app.recipefork.app.media.PhotoNotice
import app.recipefork.app.media.PhotoReencoder
import app.recipefork.app.media.PhotoOffer
import app.recipefork.app.media.PhotoStore
import app.recipefork.app.media.TestPhotos
import app.recipefork.app.media.cover
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.recipe.Media
import app.recipefork.core.recipe.MediaAttachment
import app.recipefork.core.recipe.MediaKind
import app.recipefork.core.recipe.MediaVisibility
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayInputStream
import java.io.File
import java.time.Instant

/**
 * The cover in the book file (FLOW-026, MEDIA-001, MEDIA-009): set, replaced and removed through the editor, each
 * change read back once the book's database is closed and opened again from disk, with the files that go with it.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CoverOnDiskTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = BookName.OfAccount("cover-on-disk")
    private val now = Instant.parse("2026-10-02T12:00:00Z")
    private val store = PhotoStore(context.filesDir)
    private val scope = CoroutineScope(Dispatchers.Unconfined)
    private var database = BookDatabase.open(context, book)
    private var recipes = RecipeRepository(ActiveBook(book, database))

    @After
    fun delete() {
        database.close()
        context.deleteDatabase(book.fileName)
        store.directory(book).deleteRecursively()
    }

    private fun photo(name: String, width: Int = 2000, height: Int = 1500): PhotoOffer {
        val file = TestPhotos.tag(TestPhotos.jpeg(File(context.cacheDir, name), width, height))
        return PhotoOffer(open = { file.inputStream() }, release = { file.delete() })
    }

    private fun opened(recipe: Recipe): Pair<RecipeEditor, PhotoAttaching> = runBlocking {
        recipes.save(recipe)
        val editor = RecipeEditor(recipes, recipe.id, { now }, scope)
        editor.load()
        editor to CoverAttaching(editor, store, book, scope)
    }

    /** The recipe as the book holds it once its database is closed and opened again. */
    private fun reopened(recipe: Recipe): Recipe = runBlocking {
        // A second editor reads only once every write of the first is in the book.
        RecipeEditor(recipes, recipe.id, { now }, scope).load()
        database.close()
        database = BookDatabase.open(context, book)
        recipes = RecipeRepository(ActiveBook(book, database))
        recipes.get(recipe.id)!!
    }

    private fun held(): List<String> = store.directory(book).list()?.sorted().orEmpty()

    // FLOW-026 (1, 3), MEDIA-001: a photograph attached is the recipe's one cover, held on disk.
    @Test
    fun theCoverSurvivesReopeningTheBook() = runBlocking {
        val recipe = Recipe.create(now, null).copy(title = "Borscht")
        val (editor, attaching) = opened(recipe)
        val offered = photo("camera.jpg")

        attaching.attach(offered).join()

        assertEquals(PhotoNotice.PROCESSED, attaching.notice.value)
        val cover = reopened(recipe).cover!!
        assertEquals(Media(cover.mediaId, null, MediaKind.PHOTO, MediaAttachment.Cover, MediaVisibility.PRIVATE), cover)
        assertEquals(cover, editor.cover)
        assertEquals(listOf(cover.mediaId), held())
        assertTrue(store.file(book, cover.mediaId).length() in 1..1_000_000)
        // What was held only for the photograph's processing — the camera's file — is gone.
        assertFalse(File(context.cacheDir, "camera.jpg").exists())
    }

    // MEDIA-009 (2, 3): a second cover replaces the first, whose file is deleted.
    @Test
    fun aSecondCoverReplacesTheFirstAndDeletesItsFile() = runBlocking {
        val recipe = Recipe.create(now, null)
        val (_, attaching) = opened(recipe)
        attaching.attach(photo("first.jpg")).join()
        val first = held().single()

        attaching.attach(photo("second.jpg", 1200, 1600)).join()

        val read = reopened(recipe)
        val second = read.cover!!.mediaId
        assertEquals(1, read.media.size)
        assertTrue(second != first)
        assertEquals(listOf(second), held())
        assertFalse(store.file(book, first).exists())
    }

    // The cover can be removed, its file with it.
    @Test
    fun theCoverCanBeRemovedWithItsFile() = runBlocking {
        val recipe = Recipe.create(now, null)
        val (editor, attaching) = opened(recipe)
        attaching.attach(photo("camera.jpg")).join()

        attaching.remove()

        assertEquals(PhotoNotice.REMOVED, attaching.notice.value)
        assertNull(editor.cover)
        assertNull(reopened(recipe).cover)
        assertEquals(emptyList<String>(), held())
        // Nothing to remove is no change.
        assertFalse(editor.removeCover())
    }

    // MEDIA-006 (3), MEDIA-008 (1–3): a refused file attaches nothing, and the cover the recipe had is untouched.
    @Test
    fun aRefusedFileLeavesTheCoverAsItWas() = runBlocking {
        val recipe = Recipe.create(now, null)
        val (_, attaching) = opened(recipe)
        attaching.attach(photo("camera.jpg")).join()
        val before = held().single()

        attaching.attach(PhotoOffer(open = { ByteArrayInputStream("garbage".toByteArray()) }, release = {})).join()

        assertEquals(PhotoNotice.UNREADABLE, attaching.notice.value)
        assertEquals(before, reopened(recipe).cover!!.mediaId)
        assertEquals(listOf(before), held())
    }

    // MEDIA-006 (1, 3): a photograph that cannot be brought under its limit is refused as too large — said so, not
    // as unreadable — the recipe keeps its cover, and nothing of the refused one is kept.
    @Test
    fun aPhotographThatDoesNotFitIsRefusedAsTooLarge() = runBlocking {
        val recipe = Recipe.create(now, null)
        val (editor, attaching) = opened(recipe)
        attaching.attach(photo("camera.jpg")).join()
        val before = held().single()
        val tiny = CoverAttaching(editor, PhotoStore(context.filesDir, PhotoReencoder(PhotoLimits(maxBytes = 2_000))), book, scope)

        tiny.attach(photo("big.jpg")).join()

        assertEquals(PhotoNotice.TOO_LARGE, tiny.notice.value)
        assertEquals(before, reopened(recipe).cover!!.mediaId)
        assertEquals(listOf(before), held())
    }

    // FLOW-002: a frozen recipe takes no cover, and the photograph processed for it is not kept.
    @Test
    fun aFrozenRecipeTakesNoCoverAndKeepsNoFile() = runBlocking {
        val recipe = Recipe.create(now, null).inState(RecipeState.PUBLISHED)
        val (editor, attaching) = opened(recipe)

        attaching.attach(photo("camera.jpg")).join()

        assertNull(attaching.notice.value)
        assertNull(editor.cover)
        assertNull(reopened(recipe).cover)
        assertEquals(emptyList<String>(), held())
        assertFalse(editor.setCover("anything"))
    }
}
