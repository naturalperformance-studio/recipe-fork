package app.recipefork.app.editor

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.file.FileProviderCache
import app.recipefork.app.media.PhotoReencoder
import app.recipefork.app.media.PhotoStore
import app.recipefork.app.media.TestPhotos
import app.recipefork.app.media.crop.CropTags
import app.recipefork.app.media.cover
import app.recipefork.app.media.stepPhoto
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.Step
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.InputStream
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * That a photograph is being prepared, for the cover and for a step: a small indicator on the place, told to the screen
 * reader as it appears, gone once the photograph is attached or refused. The preparation is held back until the test
 * lets it go, so the indicator is seen whatever the timing.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoWorkingTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val device = TestDevice(context)
    private val recipes = RecipeRepository(ActiveBook(BookName.Pool, book.database))
    private val saving = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val now = Instant.parse("2026-10-02T12:00:00Z")
    private val incoming = File(context.externalCacheDir ?: context.cacheDir, "incoming").apply { mkdirs() }

    /** Re-encodes only once let go. */
    private val gate = CountDownLatch(1)
    private val held = object : PhotoReencoder() {
        override fun reencode(open: () -> InputStream): ByteArray {
            gate.await(PATIENCE_MS, TimeUnit.MILLISECONDS)
            return super.reencode(open)
        }
    }

    @Before
    fun setUp() {
        FileProviderCache.clear()
        compose.registerIdlingResource(book.idling)
        compose.registerIdlingResource(device.idling)
        // Not the photographs' idling: the screen is looked at while one is held back.
    }

    @After
    fun close() {
        gate.countDown()
        saving.cancel()
        compose.unregisterIdlingResource(book.idling)
        compose.unregisterIdlingResource(device.idling)
        book.close()
        device.close()
    }

    private fun open(recipe: Recipe): RecipeId {
        runBlocking { recipes.save(recipe) }
        val environment = EditorEnvironment(
            ActiveBook(BookName.Pool, book.database),
            device.catalog(context),
            clock = { now },
            photoStore = { PhotoStore(it, held) },
        )
        compose.setContent { MaterialTheme { EditorScreen(recipe.id, recipes, environment, saving, onClose = {}) } }
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.FORM), PATIENCE_MS)
        return recipe.id
    }

    /** Whether the node tagged [tag] is there — for a part of a step's sheet, in that step's sheet, opened for it. */
    private fun exists(tag: String): Boolean {
        compose.reachStep(tag)
        return compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    }

    private fun onRow(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun stored(id: RecipeId): Recipe = runBlocking { recipes.get(id) }!!

    /** Hands [file] over from the photo picker just opened, through the crop when [crop]. */
    private fun pick(file: File, crop: Boolean) {
        val activity = shadowOf(compose.activity)
        compose.waitUntil(PATIENCE_MS) { activity.peekNextStartedActivityForResult() != null }
        val intent = activity.nextStartedActivityForResult.intent
        compose.runOnUiThread { activity.receiveResult(intent, Activity.RESULT_OK, Intent().setData(Uri.fromFile(file))) }
        if (crop) {
            compose.waitUntilExactlyOneExists(hasTestTag(CropTags.FRAME), PATIENCE_MS)
            compose.onNodeWithTag(CropTags.DONE).performClick()
            compose.waitUntil(PATIENCE_MS) { !exists(CropTags.SCREEN) }
        }
    }

    private fun photo() = TestPhotos.jpeg(File(incoming, "picked.jpg"), 800, 600)

    private fun unreadable() = File(incoming, "photo.jpg").apply { writeText("not a photo at all") }

    private fun chooseForCover(file: File, crop: Boolean = true) {
        val opener = if (compose.onAllNodes(hasTestTag(CoverTags.PLACEHOLDER)).fetchSemanticsNodes().isEmpty()) CoverTags.CHANGE else CoverTags.PLACEHOLDER
        compose.onNodeWithTag(opener).performScrollTo().performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(CoverTags.CHOOSE), PATIENCE_MS)
        compose.onNodeWithTag(CoverTags.CHOOSE).performClick()
        pick(file, crop)
    }

    private fun chooseForStep(position: Int, file: File, crop: Boolean = true) {
        compose.inStep(position).onNodeWithTag(EditorTags.stepPhoto(position)).performScrollTo().performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.stepPhotoChoose(position)), PATIENCE_MS)
        compose.inStep(position).onNodeWithTag(EditorTags.stepPhotoChoose(position)).performClick()
        pick(file, crop)
    }

    /**
     * The indicator tagged [tag] shows while the photograph is held back; the screen reader is told of it in a polite
     * live region; it is no text under the photograph.
     */
    private fun awaitWorking(tag: String) {
        compose.waitUntil(PATIENCE_MS) { exists(tag) }
        val node = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
        assertEquals(LiveRegionMode.Polite, node.config.getOrNull(SemanticsProperties.LiveRegion))
        assertEquals(listOf(WORKING), node.config.getOrNull(SemanticsProperties.ContentDescription))
        assertTrue(SemanticsProperties.InvisibleToUser !in node.config)
        assertTrue(compose.onAllNodes(hasText(WORKING), useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
    }

    private fun letGo() = gate.countDown()

    @Test
    fun theCoverShowsItIsPreparedUntilAttached() {
        val id = open(Recipe.create(now, null))
        chooseForCover(photo())

        awaitWorking(CoverTags.WORKING)
        assertNull(stored(id).cover)

        letGo()
        compose.awaitStatement(PROCESSED)
        compose.waitUntil(PATIENCE_MS) { !exists(CoverTags.WORKING) && exists(CoverTags.IMAGE) }
        assertTrue(stored(id).cover != null)
    }

    @Test
    fun theCoverShowsItIsPreparedUntilRefused() {
        val id = open(Recipe.create(now, null))
        chooseForCover(unreadable(), crop = false)

        awaitWorking(CoverTags.WORKING)

        letGo()
        compose.awaitStatement(UNREADABLE)
        compose.waitUntil(PATIENCE_MS) { !exists(CoverTags.WORKING) }
        assertTrue(stored(id).media.isEmpty())
    }

    @Test
    fun aStepShowsItsPhotoIsPreparedUntilAttached() {
        val id = open(Recipe.create(now, null).copy(steps = listOf(Step(0, "Boil."), Step(1, "Fry."))))
        chooseForStep(1, photo())

        awaitWorking(EditorTags.stepPhotoWorking(1))
        assertTrue(!exists(EditorTags.stepPhotoWorking(0)) && !exists(CoverTags.WORKING))

        letGo()
        compose.awaitStatement(PROCESSED)
        compose.waitUntil(PATIENCE_MS) { !exists(EditorTags.stepPhotoWorking(1)) && exists(EditorTags.stepPhotoImage(1)) }
        assertTrue(stored(id).stepPhoto(1) != null)
    }

    @Test
    fun aStepShowsItsPhotoIsPreparedUntilRefused() {
        val id = open(Recipe.create(now, null).copy(steps = listOf(Step(0, "Boil."))))
        chooseForStep(0, unreadable(), crop = false)

        awaitWorking(EditorTags.stepPhotoWorking(0))

        letGo()
        compose.awaitStatement(UNREADABLE)
        compose.waitUntil(PATIENCE_MS) { !exists(EditorTags.stepPhotoWorking(0)) }
        assertTrue(stored(id).media.isEmpty())
    }

    private companion object {
        const val WORKING = "Preparing the photo…"
        const val PROCESSED =
            "Photo attached. It was shrunk on this phone to at most 1 MB and 1600 pixels, and all its data was removed, location included."
        const val UNREADABLE = "This file could not be read as a photo, so nothing was attached."
    }
}
