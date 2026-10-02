package app.recipefork.app.editor

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.toSize
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.file.FileProviderCache
import app.recipefork.app.media.PhotoSources
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
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.RecipeState
import app.recipefork.core.recipe.Step
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowActivity
import java.io.File
import java.time.Instant

/**
 * A photograph at a step in the editor (FLOW-026 (2), MEDIA-001, MEDIA-005, MEDIA-008, MEDIA-009): offered at each
 * step and nowhere else, from the camera or the gallery, said to be shrunk and stripped as the cover's is, refused with
 * a reason, replaced, removed, carried along when its step moves, deleted with its step — and shown, not offered, on a
 * frozen recipe.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StepPhotoEditorTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val device = TestDevice(context)
    private val recipes = RecipeRepository(ActiveBook(BookName.Pool, book.database))
    private val store = PhotoStore(context.filesDir)
    private val saving = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val now = Instant.parse("2026-10-02T12:00:00Z")
    private val incoming = File(context.externalCacheDir ?: context.cacheDir, "incoming").apply { mkdirs() }
    private var shown by mutableStateOf(true)

    @Before
    fun setUp() {
        FileProviderCache.clear()
        compose.registerIdlingResource(book.idling)
        compose.registerIdlingResource(device.idling)
        compose.registerIdlingResource(TestPhotos.idling)
    }

    @After
    fun close() {
        saving.cancel()
        compose.unregisterIdlingResource(book.idling)
        compose.unregisterIdlingResource(device.idling)
        compose.unregisterIdlingResource(TestPhotos.idling)
        book.close()
        device.close()
    }

    private fun threeSteps(): Recipe =
        Recipe.create(now, null).copy(title = "Borscht", steps = listOf(Step(0, "Boil."), Step(1, "Fry."), Step(2, "Serve.")))

    private fun open(recipe: Recipe): RecipeId {
        runBlocking { recipes.save(recipe) }
        val environment = EditorEnvironment(ActiveBook(BookName.Pool, book.database), device.catalog(context)) { now }
        compose.setContent {
            MaterialTheme { if (shown) EditorScreen(recipe.id, recipes, environment, saving, onClose = {}) }
        }
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.FORM), PATIENCE_MS)
        return recipe.id
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun bounds(tag: String): Rect =
        compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().let { Rect(it.positionInRoot, it.size.toSize()) }

    private fun unmergedDescriptionOf(tag: String): List<String> = compose.onNodeWithTag(tag, useUnmergedTree = true)
        .fetchSemanticsNode().config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }

    private fun stored(id: RecipeId): Recipe = runBlocking { recipes.get(id) }!!

    private fun held(): List<String> = store.directory(BookName.Pool).list()?.sorted().orEmpty()

    private fun awaitNotice(position: Int, text: String) = compose.waitUntil(PATIENCE_MS) {
        exists(EditorTags.stepPhotoNotice(position)) && compose.textsOf(EditorTags.stepPhotoNotice(position)) == listOf(text)
    }

    private val activity: ShadowActivity get() = shadowOf(compose.activity)

    private fun started(): Intent {
        compose.waitUntil(PATIENCE_MS) { activity.peekNextStartedActivityForResult() != null }
        return activity.nextStartedActivityForResult.intent
    }

    /** Opens the photo menu of the step at [position] and chooses [item] in it. */
    private fun menu(position: Int, item: String) {
        compose.onNodeWithTag(EditorTags.stepPhoto(position)).performScrollTo().performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(item), PATIENCE_MS)
        compose.onNodeWithTag(item).performClick()
    }

    /** Takes a photograph at the step at [position] with the system camera. */
    private fun takePhoto(position: Int, width: Int = 3000, height: Int = 2000) {
        menu(position, EditorTags.stepPhotoTake(position))
        val intent = started()
        assertEquals(MediaStore.ACTION_IMAGE_CAPTURE, intent.action)
        val file = PhotoSources.cameraDirectory(context).listFiles()!!.single()
        TestPhotos.tag(TestPhotos.jpeg(file, width, height))
        compose.runOnUiThread { activity.receiveResult(intent, Activity.RESULT_OK, Intent()) }
    }

    /** Picks [file] in the system photo picker at the step at [position]. */
    private fun choose(position: Int, file: File) {
        menu(position, EditorTags.stepPhotoChoose(position))
        val intent = started()
        assertTrue(intent.action, intent.action == MediaStore.ACTION_PICK_IMAGES || intent.action == Intent.ACTION_OPEN_DOCUMENT)
        compose.runOnUiThread { activity.receiveResult(intent, Activity.RESULT_OK, Intent().setData(Uri.fromFile(file))) }
    }

    private val processed =
        "Photo attached. It was shrunk on this phone to at most 1 MB and 1600 pixels, and all its data was removed, location included."

    // FLOW-026 (2): every editable step offers a photograph — from the camera or the gallery — inside its own block.
    @Test
    fun eachStepOffersAPhotoAtThatStep() {
        open(threeSteps())

        (0..2).forEach { position ->
            val control = bounds(EditorTags.stepPhoto(position))
            val block = bounds(EditorTags.step(position))
            assertTrue("step $position offers its photo inside its block", control.top >= block.top && control.bottom <= block.bottom)
        }
        assertEquals(listOf("Photo"), compose.textsOf(EditorTags.stepPhoto(1)))
        compose.onNodeWithTag(EditorTags.stepPhoto(1)).performScrollTo().performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.stepPhotoTake(1)), PATIENCE_MS)
        assertTrue(exists(EditorTags.stepPhotoChoose(1)))
        // Nothing to remove yet.
        assertFalse(exists(EditorTags.stepPhotoRemove(1)))
    }

    // FLOW-026 (2), MEDIA-005: a photograph taken at a step is that step's — said to be shrunk and stripped, shown in
    // its block and named by it — and no other step's, nor the cover.
    @Test
    fun aPhotographTakenAtAStepShowsAtThatStep() {
        val id = open(threeSteps())

        takePhoto(1)

        awaitNotice(1, processed)
        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.stepPhotoImage(1)) }
        assertEquals(listOf("Photo of step 2"), compose.descriptionOf(EditorTags.stepPhotoImage(1)))
        val image = bounds(EditorTags.stepPhotoImage(1))
        val block = bounds(EditorTags.step(1))
        assertTrue(image.top >= block.top && image.bottom <= block.bottom)
        val photo = stored(id).stepPhoto(1)!!
        assertEquals(Media(photo.mediaId, null, MediaKind.PHOTO, MediaAttachment.ToStep(1), MediaVisibility.PRIVATE), photo)
        assertEquals(listOf(photo), stored(id).media)
        assertNull(stored(id).cover)
        assertEquals(listOf(photo.mediaId), held())
        assertEquals(emptyList<String>(), PhotoSources.cameraDirectory(context).list()!!.toList())
        listOf(0, 2).forEach { assertFalse(exists(EditorTags.stepPhotoImage(it)) || exists(EditorTags.stepPhotoNotice(it))) }
        assertFalse(exists(CoverTags.IMAGE) || exists(CoverTags.NOTICE))
    }

    // MEDIA-001, MEDIA-009: a second photograph at the step replaces the first, whose file is deleted.
    @Test
    fun aSecondPhotographReplacesTheFirst() {
        val id = open(threeSteps())
        takePhoto(1)
        awaitNotice(1, processed)
        compose.waitUntil(PATIENCE_MS) { stored(id).stepPhoto(1) != null }
        val first = stored(id).stepPhoto(1)!!.mediaId

        choose(1, TestPhotos.tag(TestPhotos.jpeg(File(incoming, "picked.jpg"), 1200, 1600)))

        compose.waitForIdle()
        compose.waitUntil(PATIENCE_MS) { stored(id).stepPhoto(1)?.mediaId.let { it != null && it != first } && held().size == 1 }
        assertEquals(1, stored(id).media.size)
        assertEquals(listOf(stored(id).stepPhoto(1)!!.mediaId), held())
        awaitNotice(1, processed)
    }

    // MEDIA-008: a file that cannot be read attaches nothing at the step, and says why there.
    @Test
    fun anUnreadableFileIsRefusedAtTheStepWithAReason() {
        val id = open(threeSteps())

        choose(2, File(incoming, "photo.jpg").apply { writeText("not a photo at all") })

        awaitNotice(2, "This file could not be read as a photo, so nothing was attached.")
        assertTrue(stored(id).media.isEmpty())
        assertEquals(emptyList<String>(), held())
        assertFalse(exists(EditorTags.stepPhotoImage(2)))
    }

    // A step's photograph can be removed; its file is deleted and nothing shows in its place.
    @Test
    fun aStepPhotoCanBeRemoved() {
        val id = open(threeSteps())
        takePhoto(0)
        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.stepPhotoImage(0)) }

        menu(0, EditorTags.stepPhotoRemove(0))

        compose.waitForIdle()
        compose.waitUntil(PATIENCE_MS) { !exists(EditorTags.stepPhotoImage(0)) && stored(id).media.isEmpty() && held().isEmpty() }
        awaitNotice(0, "Step photo removed.")
    }

    // FLOW-025: a step moved carries its photograph; one deleted takes its photograph's file with it.
    @Test
    fun aPhotographMovesAndGoesWithItsStep() {
        val id = open(threeSteps())
        takePhoto(0)
        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.stepPhotoImage(0)) && stored(id).stepPhoto(0) != null }
        val photo = stored(id).stepPhoto(0)!!.mediaId

        compose.onNodeWithTag(EditorTags.stepMenu(0)).performScrollTo().performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.stepAction(0, RowAction.MOVE_DOWN)), PATIENCE_MS)
        compose.onNodeWithTag(EditorTags.stepAction(0, RowAction.MOVE_DOWN)).performClick()

        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.stepPhotoImage(1)) && !exists(EditorTags.stepPhotoImage(0)) }
        assertEquals(listOf("Photo of step 2"), compose.descriptionOf(EditorTags.stepPhotoImage(1)))
        compose.waitUntil(PATIENCE_MS) { stored(id).steps.single { it.position == 1 }.text == "Boil." }
        assertEquals(photo, stored(id).stepPhoto(1)!!.mediaId)

        compose.onNodeWithTag(EditorTags.stepRemove(1)).performScrollTo().performClick()

        compose.waitForIdle()
        compose.waitUntil(PATIENCE_MS) { stored(id).steps.size == 2 && held().isEmpty() }
        assertTrue(stored(id).media.isEmpty())
        assertFalse(exists(EditorTags.stepPhotoImage(0)) || exists(EditorTags.stepPhotoImage(1)))
    }

    // A step's photograph survives leaving the editor: opened again, it is shown at its step.
    @Test
    fun aStepPhotoSurvivesLeavingTheEditor() {
        open(threeSteps())
        takePhoto(2)
        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.stepPhotoImage(2)) }

        compose.runOnIdle { shown = false }
        compose.waitUntil(PATIENCE_MS) { !exists(EditorTags.FORM) }
        compose.runOnIdle { shown = true }

        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.stepPhotoImage(2)) }
        assertFalse(exists(EditorTags.stepPhotoNotice(2)))
    }

    // A frozen recipe shows its steps' photographs and offers nothing to change them.
    @Test
    fun aFrozenRecipeShowsItsStepPhotosReadOnly() {
        val mediaId = "frozen-step"
        TestPhotos.jpeg(store.file(BookName.Pool, mediaId).apply { parentFile!!.mkdirs() }, 800, 600)
        val photo = Media(mediaId, null, MediaKind.PHOTO, MediaAttachment.ToStep(1), MediaVisibility.PRIVATE)
        open(threeSteps().copy(media = listOf(photo)).inState(RecipeState.PUBLISHED))

        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.stepPhotoImage(1)) }
        assertEquals(listOf("Photo of step 2"), unmergedDescriptionOf(EditorTags.stepPhotoImage(1)))
        (0..2).forEach { position ->
            listOf(EditorTags.stepPhoto(position), EditorTags.stepPhotoTake(position), EditorTags.stepPhotoRemove(position))
                .forEach { assertFalse(it, exists(it)) }
        }
    }

    @Test
    @Config(qualifiers = "ru")
    fun theControlAndTheTextAlternativeAreInTheInterfaceLanguage() {
        val mediaId = "ru-step"
        TestPhotos.jpeg(store.file(BookName.Pool, mediaId).apply { parentFile!!.mkdirs() }, 800, 600)
        val photo = Media(mediaId, null, MediaKind.PHOTO, MediaAttachment.ToStep(2), MediaVisibility.PRIVATE)
        open(threeSteps().copy(media = listOf(photo)))

        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.stepPhotoImage(2)) }
        assertEquals(listOf("Фото шага 3"), compose.descriptionOf(EditorTags.stepPhotoImage(2)))
        assertEquals(listOf("Фото"), compose.textsOf(EditorTags.stepPhoto(2)))
    }
}
