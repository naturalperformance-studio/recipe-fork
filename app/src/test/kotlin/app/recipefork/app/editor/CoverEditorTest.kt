package app.recipefork.app.editor

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.ExifInterface
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
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
import app.recipefork.app.media.PhotoStore
import app.recipefork.app.media.TestPhotos
import app.recipefork.app.media.TestCamera
import app.recipefork.app.media.crop.CropTags
import app.recipefork.app.media.cover
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.recipe.Media
import app.recipefork.core.recipe.MediaAttachment
import app.recipefork.core.recipe.MediaKind
import app.recipefork.core.recipe.MediaVisibility
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.RecipeState
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
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowActivity
import java.io.File
import java.time.Instant

/**
 * The cover in the editor (FLOW-026, MEDIA-005, MEDIA-006, MEDIA-008, MEDIA-009): offered at the title as optional,
 * from the phone's own camera application or the system photo picker, neither asking for any permission, said to be shrunk and stripped once attached, refused with a reason when it cannot be, replaced and removed — and shown, not offered, on a frozen recipe.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CoverEditorTest {
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

    private fun open(recipe: Recipe): RecipeId {
        runBlocking { recipes.save(recipe) }
        val environment = EditorEnvironment(ActiveBook(BookName.Pool, book.database), device.catalog(context)) { now }
        compose.setContent {
            MaterialTheme {
                if (shown) EditorScreen(recipe.id, recipes, environment, saving, onClose = {})
            }
        }
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.FORM), PATIENCE_MS)
        return recipe.id
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    private fun stored(id: RecipeId): Recipe = runBlocking { recipes.get(id) }!!

    private fun held(): List<String> = store.directory(BookName.Pool).list()?.sorted().orEmpty()

    private fun awaitNotice(text: String) = compose.waitUntil(PATIENCE_MS) {
        exists(CoverTags.NOTICE) && compose.textsOf(CoverTags.NOTICE) == listOf(text)
    }

    private val activity: ShadowActivity get() = shadowOf(compose.activity)

    private fun started(): Intent {
        compose.waitUntil(PATIENCE_MS) { activity.peekNextStartedActivityForResult() != null }
        return activity.nextStartedActivityForResult.intent
    }

    /** Launches the phone's camera application from the cover; its intent is given back. */
    private fun openCamera(): Intent {
        compose.onNodeWithTag(CoverTags.TAKE).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { TestCamera.launched(activity) }
        return TestCamera.take(activity)
    }

    /** Takes a photograph of [width] by [height] with the phone's camera application, tagged as a camera tags it. */
    private fun takePhoto(width: Int = 3000, height: Int = 2000) {
        val shot = TestPhotos.tag(TestPhotos.jpeg(File(incoming, "shot.jpg"), width, height)).readBytes()
        val intent = openCamera()
        TestCamera.write(context, intent, shot)
        compose.runOnUiThread { TestCamera.answer(activity, intent, saved = true) }
        cropDone()
    }

    /** Picks [file] in the system photo picker. */
    private fun choose(file: File, crop: Boolean = true) {
        compose.onNodeWithTag(CoverTags.CHOOSE).performScrollTo().performClick()
        val intent = started()
        assertTrue(intent.action, intent.action == MediaStore.ACTION_PICK_IMAGES || intent.action == Intent.ACTION_OPEN_DOCUMENT)
        assertEquals("image/*", intent.type)
        compose.runOnUiThread { activity.receiveResult(intent, Activity.RESULT_OK, Intent().setData(Uri.fromFile(file))) }
        if (crop) cropDone()
    }

    /** The crop, opened on the picture handed over, framed as it opens: Done. */
    private fun cropDone() {
        compose.waitUntilExactlyOneExists(hasTestTag(CropTags.FRAME), PATIENCE_MS)
        compose.onNodeWithTag(CropTags.DONE).performClick()
        compose.waitUntil(PATIENCE_MS) { !exists(CropTags.SCREEN) }
    }

    private val processed =
        "Photo attached. It was shrunk on this phone to at most 1 MB and 1600 pixels, and all its data was removed, location included."

    // FLOW-026 (1, 3): the cover is offered at the title, described as optional; a recipe without one is complete.
    @Test
    fun theCoverIsOfferedAtTheTitleAsOptional() {
        open(Recipe.create(now, null).copy(title = "Borscht"))

        compose.waitUntilExactlyOneExists(hasTestTag(CoverTags.COVER), PATIENCE_MS)
        compose.onNode(hasText("Cover photo (optional)") and hasAnyAncestor(hasTestTag(CoverTags.COVER))).assertExists()
        assertTrue(exists(CoverTags.TAKE) && exists(CoverTags.CHOOSE))
        assertFalse(exists(CoverTags.REMOVE) || exists(CoverTags.IMAGE))
        // Directly under the title, before the tags.
        val title = compose.onNodeWithTag(EditorTags.TITLE).fetchSemanticsNode().boundsInRoot
        val cover = compose.onNodeWithTag(CoverTags.COVER).fetchSemanticsNode().boundsInRoot
        val tags = compose.onNodeWithTag(EditorTags.TAGS).fetchSemanticsNode().boundsInRoot
        assertTrue(cover.top >= title.bottom && cover.bottom <= tags.top)
    }

    // FLOW-026 (1, 4): neither the camera nor the gallery asks for a permission, and none is declared — CAMERA least of
    // all, which declared and not granted would make the platform refuse the camera application.
    @Test
    fun nothingIsAskedForAPhoto() {
        val requested = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions.orEmpty().toSet()
        listOf(
            Manifest.permission.CAMERA, Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.ACCESS_MEDIA_LOCATION,
        ).forEach { assertFalse(it, it in requested) }
        val id = open(Recipe.create(now, null))

        choose(TestPhotos.jpeg(File(incoming, "picked.jpg"), 800, 600))
        compose.waitForIdle()
        compose.waitUntil(PATIENCE_MS) { stored(id).media.size == 1 && held().size == 1 }
        awaitNotice(processed)
        val picked = stored(id).cover!!.mediaId
        takePhoto()
        compose.waitUntil(PATIENCE_MS) { stored(id).cover.let { it != null && it.mediaId != picked } && held().size == 1 }

        assertNull(activity.lastRequestedPermission)
    }

    // FLOW-026 (1), MEDIA-005: a photograph taken with the camera is attached, said to be shrunk and stripped, shown as
    // the cover, and the camera's file is not kept.
    @Test
    fun aPhotographTakenWithTheCameraBecomesTheCover() {
        val id = open(Recipe.create(now, null).copy(title = "Borscht"))

        takePhoto()

        awaitNotice(processed)
        compose.waitUntilExactlyOneExists(hasTestTag(CoverTags.IMAGE), PATIENCE_MS)
        assertEquals(listOf("Cover photo of “Borscht”"), compose.descriptionOf(CoverTags.IMAGE))
        val cover = stored(id).cover!!
        assertEquals(Media(cover.mediaId, null, MediaKind.PHOTO, MediaAttachment.Cover, MediaVisibility.PRIVATE), cover)
        assertEquals(listOf(cover.mediaId), held())
        // MEDIA-003: the camera's shot carried place, device and time; the stored photograph carries none of it, and
        // the shot's own file is gone.
        val exif = ExifInterface(store.file(BookName.Pool, cover.mediaId).path)
        TestPhotos.IDENTIFYING.keys.forEach { assertNull(it, exif.getAttribute(it)) }
        assertTrue(TestCamera.left(context).isEmpty())
        assertTrue(exists(CoverTags.REMOVE))
    }

    // A camera cancelled without a photograph attaches nothing and keeps nothing.
    @Test
    fun aCancelledCameraAttachesNothing() {
        val id = open(Recipe.create(now, null))
        val intent = openCamera()

        compose.runOnUiThread { TestCamera.answer(activity, intent, saved = false) }

        compose.waitForIdle()
        assertTrue(TestCamera.left(context).isEmpty())
        assertFalse(exists(CropTags.SCREEN))
        assertTrue(stored(id).media.isEmpty())
        assertTrue(held().isEmpty())
        assertFalse(exists(CoverTags.NOTICE))
    }

    // FLOW-026 (1), MEDIA-009: one picked from the gallery replaces the cover, whose file is deleted.
    @Test
    fun aPhotographChosenFromTheGalleryReplacesTheCover() {
        val id = open(Recipe.create(now, null))
        takePhoto()
        awaitNotice(processed)
        compose.waitUntil(PATIENCE_MS) { stored(id).cover != null }
        val first = stored(id).cover!!.mediaId

        choose(TestPhotos.tag(TestPhotos.jpeg(File(incoming, "picked.jpg"), 1200, 1600)))

        // Waits for the photograph's processing, then for the files its replacement deletes.
        compose.waitForIdle()
        compose.waitUntil(PATIENCE_MS) { stored(id).cover?.mediaId.let { it != null && it != first } && held().size == 1 }
        val second = stored(id).cover!!.mediaId
        assertEquals(1, stored(id).media.size)
        assertEquals(listOf(second), held())
        awaitNotice(processed)
    }

    // MEDIA-008 (1–4): a file that cannot be read attaches nothing, says why, and leaves the editor usable.
    @Test
    fun anUnreadableFileIsRefusedWithAReason() {
        val id = open(Recipe.create(now, null))
        takePhoto()
        awaitNotice(processed)
        compose.waitUntil(PATIENCE_MS) { stored(id).cover != null }
        val before = stored(id).cover!!.mediaId

        choose(File(incoming, "photo.jpg").apply { writeText("not a photo at all") }, crop = false)

        awaitNotice("This file could not be read as a photo, so nothing was attached.")
        assertEquals(before, stored(id).cover!!.mediaId)
        assertEquals(listOf(before), held())
        assertTrue(exists(CoverTags.IMAGE) && exists(CoverTags.TAKE))
    }

    // The cover can be removed; its file is deleted and nothing shows in its place.
    @Test
    fun theCoverCanBeRemoved() {
        val id = open(Recipe.create(now, null))
        takePhoto()
        compose.waitUntilExactlyOneExists(hasTestTag(CoverTags.IMAGE), PATIENCE_MS)

        compose.onNodeWithTag(CoverTags.REMOVE).performScrollTo().performClick()

        compose.waitUntil(PATIENCE_MS) { !exists(CoverTags.IMAGE) && stored(id).media.isEmpty() && held().isEmpty() }
        awaitNotice("Cover photo removed.")
        assertFalse(exists(CoverTags.REMOVE))
    }

    // The cover survives leaving the editor: opened again, it is shown.
    @Test
    fun theCoverSurvivesLeavingTheEditor() {
        open(Recipe.create(now, null).copy(title = "Borscht"))
        takePhoto()
        compose.waitUntilExactlyOneExists(hasTestTag(CoverTags.IMAGE), PATIENCE_MS)

        compose.runOnIdle { shown = false }
        compose.waitUntil(PATIENCE_MS) { !exists(EditorTags.FORM) }
        compose.runOnIdle { shown = true }

        compose.waitUntilExactlyOneExists(hasTestTag(CoverTags.IMAGE), PATIENCE_MS)
        assertFalse(exists(CoverTags.NOTICE))
    }

    // A frozen recipe shows its cover and offers nothing to change it.
    @Test
    fun aFrozenRecipeShowsItsCoverReadOnly() {
        val mediaId = "frozen-cover"
        TestPhotos.jpeg(store.file(BookName.Pool, mediaId).apply { parentFile!!.mkdirs() }, 800, 600)
        val cover = Media(mediaId, null, MediaKind.PHOTO, MediaAttachment.Cover, MediaVisibility.PRIVATE)
        open(Recipe.create(now, null).copy(title = "Borscht", media = listOf(cover)).inState(RecipeState.PUBLISHED))

        compose.waitUntilExactlyOneExists(hasTestTag(CoverTags.IMAGE), PATIENCE_MS)
        assertEquals(listOf("Cover photo of “Borscht”"), compose.descriptionOf(CoverTags.IMAGE))
        listOf(CoverTags.TAKE, CoverTags.CHOOSE, CoverTags.REMOVE).forEach { assertFalse(it, exists(it)) }
    }

    @Test
    fun aFrozenRecipeWithoutACoverShowsNothingOfIt() {
        open(Recipe.create(now, null).inState(RecipeState.PUBLISHED))
        compose.waitForIdle()
        assertFalse(exists(CoverTags.COVER))
    }
}
