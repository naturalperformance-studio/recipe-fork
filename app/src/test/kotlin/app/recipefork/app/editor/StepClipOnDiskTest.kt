package app.recipefork.app.editor

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.R
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookDatabase
import app.recipefork.app.book.BookName
import app.recipefork.app.media.ClipConverter
import app.recipefork.app.media.ClipOffer
import app.recipefork.app.media.ClipRefusal
import app.recipefork.app.media.ClipStore
import app.recipefork.app.media.PhotoNotice
import app.recipefork.app.media.PhotoOffer
import app.recipefork.app.media.PhotoStore
import app.recipefork.app.media.TestPhotos
import app.recipefork.app.media.stepMedia
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.recipe.Media
import app.recipefork.core.recipe.MediaAttachment
import app.recipefork.core.recipe.MediaKind
import app.recipefork.core.recipe.MediaVisibility
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.Step
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowMediaMetadataRetriever
import org.robolectric.shadows.util.DataSource
import java.io.File
import java.time.Instant
import kotlin.time.Duration.Companion.seconds

/**
 * A step's clip in the book file (FLOW-026, MEDIA-001, MEDIA-005 to MEDIA-009): converted, attached in place of the
 * step's photograph and replaced by one in turn, removed, cancelled — each change read back once the book's database
 * is closed and opened again from disk, with the files that go with it and the capture's own file gone; what each
 * refusal says, in both languages; and the first frame the editor shows.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StepClipOnDiskTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = BookName.OfAccount("step-clip-on-disk")
    private val now = Instant.parse("2026-10-03T12:00:00Z")
    private val store = PhotoStore(context.filesDir)
    private val scope = CoroutineScope(Dispatchers.Unconfined)
    private var gate: CompletableDeferred<Unit>? = null
    private val converter = ClipConverter { _, target ->
        gate?.await()
        target.writeBytes(ByteArray(1500) { 5 })
    }
    private val clips = ClipStore(context.filesDir, converter, { 10.seconds })
    private val photos = StepPhotos(store, book, scope, clips)
    private var database = BookDatabase.open(context, book)
    private var recipes = RecipeRepository(ActiveBook(book, database))

    @After
    fun delete() {
        database.close()
        context.deleteDatabase(book.fileName)
        store.directory(book).deleteRecursively()
    }

    private fun recipe(vararg texts: String) = Recipe.create(now, null).copy(steps = texts.mapIndexed { i, text -> Step(i, text) })

    private fun opened(recipe: Recipe): RecipeEditor = runBlocking {
        recipes.save(recipe)
        RecipeEditor(recipes, recipe.id, { now }, scope).also { it.load() }
    }

    private fun RecipeEditor.at(position: Int) = StepPhotoAttaching(this, photos, stepKey(position))

    private fun reopened(recipe: Recipe): Recipe = runBlocking {
        RecipeEditor(recipes, recipe.id, { now }, scope).load()
        database.close()
        database = BookDatabase.open(context, book)
        recipes = RecipeRepository(ActiveBook(book, database))
        recipes.get(recipe.id)!!
    }

    private fun held(): List<String> = store.directory(book).list()?.sorted().orEmpty()

    /** A clip the camera recorded into [name]; offered, its file is deleted once it is done with. */
    private fun recorded(name: String): Pair<ClipOffer, File> {
        val file = File(context.cacheDir, name).apply { writeBytes(ByteArray(3000) { 1 }) }
        return ClipOffer(Uri.fromFile(file), release = { file.delete() }) to file
    }

    private fun photo(name: String): PhotoOffer {
        val file = TestPhotos.jpeg(File(context.cacheDir, name), 1200, 900)
        return PhotoOffer(open = { file.inputStream() }, release = { file.delete() })
    }

    // MEDIA-001, MEDIA-005, MEDIA-009: a clip replaces the step's photograph, a photograph the clip, one item each time
    // and the replaced file deleted; the book holds it once reopened; the capture's file is gone.
    @Test
    fun aClipReplacesAPhotoAndAPhotoTheClip() = runBlocking {
        val recipe = recipe("Boil.", "Fry.")
        val editor = opened(recipe)
        val attaching = editor.at(1)
        attaching.attach(photo("photo.jpg")).join()
        val photo = editor.stepMedia(1)!!.mediaId
        val (offer, capture) = recorded("clip.mp4")

        attaching.attachClip(offer).join()

        assertEquals(PhotoNotice.CLIP_PROCESSED, attaching.notice.value)
        val clip = reopened(recipe).stepMedia(1)!!
        assertEquals(Media(clip.mediaId, null, MediaKind.VIDEO, MediaAttachment.ToStep(1), MediaVisibility.PRIVATE), clip)
        assertEquals(listOf(clip), reopened(recipe).media)
        assertEquals(listOf(clip.mediaId), held())
        assertFalse(store.file(book, photo).exists())
        assertFalse(capture.exists())

        val again = opened(reopened(recipe)).at(1)
        again.attach(photo("again.jpg")).join()

        val read = reopened(recipe)
        assertEquals(MediaKind.PHOTO, read.stepMedia(1)!!.kind)
        assertEquals(1, read.media.size)
        assertEquals(listOf(read.stepMedia(1)!!.mediaId), held())
    }

    // A clip removed goes with its file, and is said to be.
    @Test
    fun aStepClipCanBeRemovedWithItsFile() = runBlocking {
        val recipe = recipe("Boil.")
        val editor = opened(recipe)
        val attaching = editor.at(0)
        attaching.attachClip(recorded("clip.mp4").first).join()

        attaching.remove(PhotoNotice.STEP_CLIP_REMOVED)

        assertEquals(PhotoNotice.STEP_CLIP_REMOVED, attaching.notice.value)
        assertTrue(reopened(recipe).media.isEmpty())
        assertEquals(emptyList<String>(), held())
    }

    // Cancelled mid-conversion: nothing attached, nothing kept, the capture's file deleted, nothing said; the step keeps
    // what it had.
    @Test
    fun aCancelledConversionLeavesNothing() = runBlocking {
        val recipe = recipe("Boil.")
        val editor = opened(recipe)
        val attaching = editor.at(0)
        attaching.attach(photo("kept.jpg")).join()
        val kept = editor.stepMedia(0)!!.mediaId
        gate = CompletableDeferred()
        val (offer, capture) = recorded("cancelled.mp4")

        val job = attaching.attachClip(offer)
        withTimeout(10_000) { while (!attaching.converting.value) Thread.sleep(10) }
        assertTrue(attaching.working.value)
        attaching.cancelClip()
        job.join()

        assertFalse(attaching.converting.value)
        assertFalse(attaching.working.value)
        assertEquals(PhotoNotice.PROCESSED, attaching.notice.value)
        assertEquals(kept, reopened(recipe).stepMedia(0)!!.mediaId)
        assertEquals(listOf(kept), held())
        assertFalse(capture.exists())
    }

    // MEDIA-008: a clip that cannot be kept on the device attaches nothing and says so.
    @Test
    fun aClipThatCannotBeKeptIsRefused() = runBlocking {
        val recipe = recipe("Boil.")
        val editor = opened(recipe)
        // The books' media live under a file, not a directory: nothing can be kept there.
        val blocked = File(context.cacheDir, "blocked").apply { writeText("a file") }
        val nowhere = StepPhotos(store, book, scope, ClipStore(blocked, converter, { 10.seconds }))

        val attaching = StepPhotoAttaching(editor, nowhere, editor.stepKey(0))
        attaching.attachClip(recorded("clip.mp4").first).join()

        assertEquals(PhotoNotice.CLIP_NOT_STORED, attaching.notice.value)
        assertTrue(reopened(recipe).media.isEmpty())
    }

    // MEDIA-005 to MEDIA-008: each refusal has its own notice, and the limits it names are VideoLimits'.
    @Test
    fun eachRefusalSaysWhyNamingTheLimits() {
        assertEquals(
            listOf(PhotoNotice.CLIP_TOO_LONG, PhotoNotice.CLIP_UNREADABLE, PhotoNotice.CLIP_TOO_LARGE, PhotoNotice.CLIP_NOT_STORED),
            ClipRefusal.entries.map { PhotoNotice.of(it) },
        )
        val resources = context.resources
        assertEquals("This clip is longer than 15 seconds, so nothing was attached.", PhotoNotice.CLIP_TOO_LONG.said(resources))
        assertEquals("This clip could not be made smaller than 6 MB, so nothing was attached.", PhotoNotice.CLIP_TOO_LARGE.said(resources))
        assertEquals("The clip could not be saved on this phone, so nothing was attached.", PhotoNotice.CLIP_NOT_STORED.said(resources))
        assertEquals(
            "Clip attached. It was converted on this phone to at most 1280 pixels and 6 MB, and its sound was removed.",
            PhotoNotice.CLIP_PROCESSED.said(resources),
        )
    }

    @Test
    @Config(qualifiers = "ru")
    fun eachRefusalSaysWhyInRussian() {
        val resources = context.resources
        assertEquals("Это видео длиннее 15 секунд, поэтому ничего не прикреплено.", PhotoNotice.CLIP_TOO_LONG.said(resources))
        assertEquals("Видео прикреплено. Оно сжато на этом телефоне до 1280 пикселей и 6 МБ, звук удалён.", PhotoNotice.CLIP_PROCESSED.said(resources))
        assertEquals("Видео шага 2", resources.getString(R.string.media_step_clip_description, 2))
    }

    // The editor reads the clip's first frame from the file itself; a file of no frame gives none, and no failure.
    @Test
    fun theFirstFrameIsReadFromTheFile() {
        val file = File(context.cacheDir, "framed.mp4").apply { writeBytes(ByteArray(10)) }
        ShadowMediaMetadataRetriever.addFrame(DataSource.toDataSource(file.path), 0, Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888))

        val frame = firstFrame(file)

        assertNotNull(frame)
        assertEquals(320, frame!!.width)
        assertNull(firstFrame(File(context.cacheDir, "missing.mp4")))
    }
}
