package app.recipefork.app.editor

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookDatabase
import app.recipefork.app.book.BookName
import app.recipefork.app.file.TestRecipeFiles
import app.recipefork.app.media.PhotoOffer
import app.recipefork.app.media.PhotoStore
import app.recipefork.app.media.TestPhotos
import app.recipefork.app.media.stepMedia
import app.recipefork.app.media.stepPhoto
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.recipe.Media
import app.recipefork.core.recipe.MediaAttachment
import app.recipefork.core.recipe.MediaKind
import app.recipefork.core.recipe.MediaVisibility
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeState
import app.recipefork.core.recipe.Step
import kotlinx.coroutines.CompletableDeferred
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
import java.io.File
import java.time.Instant

/**
 * A step holds one item of either kind (MEDIA-001, MEDIA-009): a clip set on a step replaces its photograph, a
 * photograph replaces its clip — through the editor's own path for photographs as well — and the file of the item
 * replaced is deleted once the book no longer holds it (MEDIA-012). Each change is read back from disk.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StepMediaOnDiskTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = BookName.OfAccount("step-media-on-disk")
    private val now = Instant.parse("2026-10-02T12:00:00Z")
    private val store = PhotoStore(context.filesDir)
    private val scope = CoroutineScope(Dispatchers.Unconfined)
    private val photos = StepPhotos(store, book, scope)
    private var database = BookDatabase.open(context, book)
    private var recipes = RecipeRepository(ActiveBook(book, database))

    @After
    fun delete() {
        database.close()
        context.deleteDatabase(book.fileName)
        store.directory(book).deleteRecursively()
    }

    private fun clip(mediaId: String, position: Int): Media {
        store.file(book, mediaId).apply { parentFile!!.mkdirs() }.writeBytes(TestRecipeFiles.mp4())
        return Media(mediaId, null, MediaKind.VIDEO, MediaAttachment.ToStep(position), MediaVisibility.PRIVATE)
    }

    private fun photo(name: String): PhotoOffer {
        val file = TestPhotos.tag(TestPhotos.jpeg(File(context.cacheDir, name), 1600, 1200))
        return PhotoOffer(open = { file.inputStream() }, release = { file.delete() })
    }

    private fun recipe(vararg media: Media) =
        Recipe.create(now, null).copy(steps = listOf(Step(0, "Boil."), Step(1, "Fry.")), media = media.toList())

    private fun opened(recipe: Recipe): RecipeEditor = runBlocking {
        recipes.save(recipe)
        RecipeEditor(recipes, recipe.id, { now }, scope).also { it.load() }
    }

    private fun reopened(recipe: Recipe): Recipe = runBlocking {
        RecipeEditor(recipes, recipe.id, { now }, scope).load()
        database.close()
        database = BookDatabase.open(context, book)
        recipes = RecipeRepository(ActiveBook(book, database))
        recipes.get(recipe.id)!!
    }

    private fun held(): List<String> = store.directory(book).list()?.sorted().orEmpty()

    /** Sets [mediaId] of [kind] on the step at [position] and waits until the item it replaced is released. */
    private fun RecipeEditor.set(position: Int, mediaId: String, kind: MediaKind): String? = runBlocking {
        val released = CompletableDeferred<String?>()
        assertTrue(setStepMedia(position, mediaId, kind) { replaced -> replaced?.let { store.delete(book, it) }; released.complete(replaced) })
        released.await()
    }

    // MEDIA-001, MEDIA-009: a clip set on a step carrying a photograph replaces it; the photograph's file is deleted.
    @Test
    fun aClipReplacesAStepsPhoto() = runBlocking {
        val recipe = recipe(clip("other", 1))
        val editor = opened(recipe)
        StepPhotoAttaching(editor, photos, editor.stepKey(0)).attach(photo("camera.jpg")).join()
        val photo = editor.stepPhoto(0)!!.mediaId
        val clip = clip("clip", 0).mediaId

        assertEquals(photo, editor.set(0, clip, MediaKind.VIDEO))

        val read = reopened(recipe)
        assertEquals(Media(clip, null, MediaKind.VIDEO, MediaAttachment.ToStep(0), MediaVisibility.PRIVATE), read.stepMedia(0))
        assertNull(read.stepPhoto(0))
        assertEquals(setOf("clip", "other"), read.media.map { it.mediaId }.toSet())
        assertEquals(listOf("clip", "other"), held())
    }

    // MEDIA-001, MEDIA-009: a photograph attached, the way the editor attaches one, at a step carrying a clip — one a
    // fork of an imported recipe holds — replaces the clip, so the step never holds two items; the clip's file goes.
    @Test
    fun aPhotoReplacesAStepsClip() = runBlocking {
        val recipe = recipe(clip("clip", 0))
        val editor = opened(recipe)

        StepPhotoAttaching(editor, photos, editor.stepKey(0)).attach(photo("camera.jpg")).join()

        val read = reopened(recipe)
        val photo = read.stepMedia(0)!!
        assertEquals(MediaKind.PHOTO, photo.kind)
        assertEquals(listOf(photo), read.media)
        assertEquals(listOf(photo.mediaId), held())
    }

    // MEDIA-001: replacing back and forth leaves one item each time, and only its file.
    @Test
    fun aStepHoldsOneItemEachTime() = runBlocking {
        val recipe = recipe()
        val editor = opened(recipe)

        assertNull(editor.set(1, clip("c1", 1).mediaId, MediaKind.VIDEO))
        assertEquals("c1", editor.set(1, "p1".also { store.file(book, it).writeBytes(TestRecipeFiles.jpeg()) }, MediaKind.PHOTO))
        assertEquals("p1", editor.set(1, clip("c2", 1).mediaId, MediaKind.VIDEO))

        val read = reopened(recipe)
        assertEquals(listOf("c2"), read.media.map { it.mediaId })
        assertEquals(MediaKind.VIDEO, read.stepMedia(1)!!.kind)
        assertEquals(listOf("c2"), held())
    }

    // A step's clip can be taken off, its file then the caller's to delete; a photograph-only removal leaves a clip.
    @Test
    fun aStepsClipCanBeRemoved() = runBlocking {
        val recipe = recipe(clip("clip", 0))
        val editor = opened(recipe)
        assertFalse(editor.removeStepPhoto(0))

        val removed = CompletableDeferred<String>()
        assertTrue(editor.removeStepMedia(0) { store.delete(book, it); removed.complete(it) })

        assertEquals("clip", removed.await())
        assertTrue(reopened(recipe).media.isEmpty())
        assertEquals(emptyList<String>(), held())
        assertFalse(opened(reopened(recipe)).removeStepMedia(0))
    }

    // FLOW-002: a frozen recipe takes no item, and a step that is not there carries none.
    @Test
    fun aFrozenRecipeOrAMissingStepTakesNoItem() = runBlocking {
        val frozen = recipe(clip("clip", 0)).inState(RecipeState.PUBLISHED)
        val editor = opened(frozen)
        assertFalse(editor.setStepMedia(0, "other", MediaKind.PHOTO))
        assertFalse(editor.removeStepMedia(0))
        assertEquals(listOf("clip"), reopened(frozen).media.map { it.mediaId })

        val bare = recipe()
        val own = opened(bare)
        assertFalse(own.setStepMedia(2, "other", MediaKind.VIDEO))
        assertTrue(reopened(bare).media.isEmpty())
    }
}
