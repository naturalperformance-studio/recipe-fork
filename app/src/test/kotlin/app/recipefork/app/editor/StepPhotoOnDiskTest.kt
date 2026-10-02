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
import app.recipefork.app.media.PhotoOffer
import app.recipefork.app.media.PhotoReencoder
import app.recipefork.app.media.PhotoStore
import app.recipefork.app.media.TestPhotos
import app.recipefork.app.media.cover
import app.recipefork.app.media.stepPhoto
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.recipe.Media
import app.recipefork.core.recipe.MediaAttachment
import app.recipefork.core.recipe.MediaKind
import app.recipefork.core.recipe.MediaVisibility
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeState
import app.recipefork.core.recipe.Step
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
import java.util.concurrent.CountDownLatch

/**
 * A step's photograph in the book file (FLOW-026 (2), MEDIA-001, MEDIA-009): attached, replaced and removed through
 * the editor, kept with its step as steps are inserted, moved and deleted, each change read back once the book's
 * database is closed and opened again from disk, with the files that go with it.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StepPhotoOnDiskTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = BookName.OfAccount("step-photo-on-disk")
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

    private fun photo(name: String, width: Int = 2000, height: Int = 1500): PhotoOffer {
        val file = TestPhotos.tag(TestPhotos.jpeg(File(context.cacheDir, name), width, height))
        return PhotoOffer(open = { file.inputStream() }, release = { file.delete() })
    }

    private fun recipe(vararg texts: String) =
        Recipe.create(now, null).copy(steps = texts.mapIndexed { i, text -> Step(i, text) })

    private fun opened(recipe: Recipe): RecipeEditor = runBlocking {
        recipes.save(recipe)
        RecipeEditor(recipes, recipe.id, { now }, scope).also { it.load() }
    }

    /** Attaching at the step now at [position], followed by its key wherever it goes. */
    private fun RecipeEditor.at(position: Int): PhotoAttaching = StepPhotoAttaching(this, photos, stepKey(position))

    /** The recipe as the book holds it once its database is closed and opened again. */
    private fun reopened(recipe: Recipe): Recipe = runBlocking {
        RecipeEditor(recipes, recipe.id, { now }, scope).load()
        database.close()
        database = BookDatabase.open(context, book)
        recipes = RecipeRepository(ActiveBook(book, database))
        recipes.get(recipe.id)!!
    }

    private fun held(): List<String> = store.directory(book).list()?.sorted().orEmpty()

    /** Each step's text, with the identity of the photograph it carries, if any. */
    private fun Recipe.photosByText(): Map<String, String?> =
        steps.sortedBy { it.position }.associate { it.text to stepPhoto(it.position)?.mediaId }

    // FLOW-026 (2), MEDIA-001, MEDIA-005: a photograph attached at a step is that step's, and nowhere else's.
    @Test
    fun aStepPhotoSurvivesReopeningTheBook() = runBlocking {
        val recipe = recipe("Boil.", "Fry.", "Serve.")
        val editor = opened(recipe)
        val attaching = editor.at(1)

        attaching.attach(photo("camera.jpg")).join()

        assertEquals(PhotoNotice.PROCESSED, attaching.notice.value)
        val read = reopened(recipe)
        val photo = read.stepPhoto(1)!!
        assertEquals(Media(photo.mediaId, null, MediaKind.PHOTO, MediaAttachment.ToStep(1), MediaVisibility.PRIVATE), photo)
        assertEquals(listOf(photo), read.media)
        assertNull(read.cover)
        assertEquals(listOf(photo.mediaId), held())
        assertTrue(store.file(book, photo.mediaId).length() in 1..1_000_000)
        assertFalse(File(context.cacheDir, "camera.jpg").exists())
    }

    // MEDIA-001, MEDIA-009: a step carries one photograph; a second replaces the first, whose file is deleted. Another
    // step's photograph is untouched.
    @Test
    fun aSecondPhotoReplacesTheFirstAndDeletesItsFile() = runBlocking {
        val recipe = recipe("Boil.", "Fry.")
        val editor = opened(recipe)
        editor.at(0).attach(photo("other.jpg")).join()
        val other = editor.stepPhoto(0)!!.mediaId
        val attaching = editor.at(1)
        attaching.attach(photo("first.jpg")).join()
        val first = editor.stepPhoto(1)!!.mediaId

        attaching.attach(photo("second.jpg", 1200, 1600)).join()

        val read = reopened(recipe)
        val second = read.stepPhoto(1)!!.mediaId
        assertTrue(second != first)
        assertEquals(2, read.media.size)
        assertEquals(other, read.stepPhoto(0)!!.mediaId)
        assertEquals(listOf(other, second).sorted(), held())
        assertFalse(store.file(book, first).exists())
    }

    // A step's photograph can be removed, its file with it.
    @Test
    fun aStepPhotoCanBeRemovedWithItsFile() = runBlocking {
        val recipe = recipe("Boil.", "Fry.")
        val editor = opened(recipe)
        val attaching = editor.at(1)
        attaching.attach(photo("camera.jpg")).join()

        attaching.remove()

        assertEquals(PhotoNotice.STEP_REMOVED, attaching.notice.value)
        assertNull(editor.stepPhoto(1))
        assertTrue(reopened(recipe).media.isEmpty())
        assertEquals(emptyList<String>(), held())
        assertFalse(editor.removeStepPhoto(1))
    }

    // MEDIA-006, MEDIA-008: a file that cannot be read, or not shrunk to its limit, is refused as the cover's is, and
    // the step keeps the photograph it had.
    @Test
    fun aRefusedFileLeavesTheStepPhotoAsItWas() = runBlocking {
        val recipe = recipe("Boil.")
        val editor = opened(recipe)
        val attaching = editor.at(0)
        attaching.attach(photo("camera.jpg")).join()
        val before = held().single()

        attaching.attach(PhotoOffer(open = { ByteArrayInputStream("garbage".toByteArray()) }, release = {})).join()
        assertEquals(PhotoNotice.UNREADABLE, attaching.notice.value)

        val tiny = StepPhotoAttaching(editor, StepPhotos(PhotoStore(context.filesDir, PhotoReencoder(PhotoLimits(maxBytes = 2_000))), book, scope), editor.stepKey(0))
        tiny.attach(photo("big.jpg")).join()
        assertEquals(PhotoNotice.TOO_LARGE, tiny.notice.value)

        assertEquals(before, reopened(recipe).stepPhoto(0)!!.mediaId)
        assertEquals(listOf(before), held())
    }

    // FLOW-025: inserting, moving and deleting steps keeps each photograph with its step; deleting a step deletes its
    // photograph's file once the book no longer holds it.
    @Test
    fun eachPhotoStaysWithItsStep() = runBlocking {
        val recipe = recipe("Boil.", "Fry.", "Serve.")
        val editor = opened(recipe)
        editor.at(0).attach(photo("boil.jpg")).join()
        editor.at(2).attach(photo("serve.jpg")).join()
        val boil = editor.stepPhoto(0)!!.mediaId
        val serve = editor.stepPhoto(2)!!.mediaId

        assertTrue(editor.moveStep(0, 2))
        assertTrue(editor.insertStep(0))
        editor.setStepText(0, "Wash.")
        assertEquals(mapOf("Wash." to null, "Fry." to null, "Serve." to serve, "Boil." to boil), reopened(recipe).photosByText())

        val deleted = mutableListOf<String>()
        val again = opened(reopened(recipe))
        assertTrue(again.removeStep(2) { removed -> deleted += removed; removed.forEach { store.delete(book, it) } })

        val read = reopened(recipe)
        assertEquals(mapOf("Wash." to null, "Fry." to null, "Boil." to boil), read.photosByText())
        assertEquals(listOf(serve), deleted)
        assertEquals(listOf(boil), held())
        // A step without a photograph deletes no file.
        val last = opened(read)
        var told = false
        last.removeStep(0) { told = true }
        RecipeEditor(recipes, recipe.id, { now }, scope).load()
        assertFalse(told)
    }

    // A photograph still being processed when its step moves lands on that step where it now stands; one whose step is
    // deleted meanwhile is kept nowhere.
    @Test
    fun aPhotographFollowsItsStepWhileItIsProcessed() = runBlocking {
        val recipe = recipe("Boil.", "Fry.", "Serve.")
        val editor = opened(recipe)
        val file = TestPhotos.jpeg(File(context.cacheDir, "slow.jpg"), 1200, 900)
        val gate = CountDownLatch(1)
        val slow = PhotoOffer(open = { gate.await(); file.inputStream() }, release = {})
        val job = editor.at(0).attach(slow)

        editor.moveStep(0, 2)
        gate.countDown()
        job.join()

        assertEquals("Boil.", reopened(recipe).steps.single { it.position == 2 }.text)
        assertEquals(mapOf("Fry." to null, "Serve." to null, "Boil." to held().single()), reopened(recipe).photosByText())

        val other = opened(reopened(recipe))
        val gone = CountDownLatch(1)
        val late = other.at(0).attach(PhotoOffer(open = { gone.await(); file.inputStream() }, release = {}))
        other.removeStep(0)
        gone.countDown()
        late.join()
        RecipeEditor(recipes, recipe.id, { now }, scope).load()
        assertEquals(1, held().size)
        assertEquals(1, reopened(recipe).media.size)
    }

    // FLOW-002: a frozen recipe takes no step photograph, and the one processed for it is not kept.
    @Test
    fun aFrozenRecipeTakesNoStepPhotoAndKeepsNoFile() = runBlocking {
        val recipe = recipe("Boil.").inState(RecipeState.PUBLISHED)
        val editor = opened(recipe)
        val attaching = editor.at(0)

        attaching.attach(photo("camera.jpg")).join()

        assertNull(attaching.notice.value)
        assertTrue(reopened(recipe).media.isEmpty())
        assertEquals(emptyList<String>(), held())
        assertFalse(editor.setStepPhoto(0, "anything"))
        assertFalse(editor.setStepPhoto(5, "anything"))
    }
}
