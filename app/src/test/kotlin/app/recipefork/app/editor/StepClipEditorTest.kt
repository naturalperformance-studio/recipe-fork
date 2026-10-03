package app.recipefork.app.editor

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.toSize
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.file.FileProviderCache
import app.recipefork.app.media.ClipConversionFailed
import app.recipefork.app.media.ClipConverter
import app.recipefork.app.media.ClipStore
import app.recipefork.app.media.PhotoStore
import app.recipefork.app.media.TestCamera
import app.recipefork.app.media.TestPhotos
import app.recipefork.app.media.crop.CropTags
import app.recipefork.app.media.stepMedia
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.ui.AccessibilityChecks
import app.recipefork.core.recipe.Media
import app.recipefork.core.recipe.MediaAttachment
import app.recipefork.core.recipe.MediaKind
import app.recipefork.core.recipe.MediaVisibility
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.RecipeState
import app.recipefork.core.recipe.Step
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowActivity
import org.robolectric.shadows.ShadowMediaMetadataRetriever
import org.robolectric.shadows.util.DataSource
import java.io.File
import java.time.Instant
import java.util.Collections
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * A clip at a step in the editor (FLOW-026, MEDIA-001, MEDIA-004 to MEDIA-009, A11Y-001): recorded with the phone's
 * camera, limited to 15 seconds, or chosen from the gallery, videos only — at a step and never at the cover; refused
 * before conversion when too long, converted otherwise with a state the author can cancel, said to be converted and
 * silenced, shown by its first frame with a mark, named as the step's video, replaced by a photograph and back,
 * removed. The conversion itself is the device's (VideoConversionTest); here a converter stands in for it.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StepClipEditorTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val device = TestDevice(context)
    private val recipes = RecipeRepository(ActiveBook(BookName.Pool, book.database))
    private val store = PhotoStore(context.filesDir)
    private val saving = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val now = Instant.parse("2026-10-03T12:00:00Z")
    private val incoming = File(context.externalCacheDir ?: context.cacheDir, "incoming").apply { mkdirs() }

    /**
     * Stands in for the device's converter: what it was given, in order; a clip named "broken" fails, one named
     * "heavy" comes out too heavy; while [gate] is set, it waits for it — or for being cancelled.
     */
    private inner class Converter : ClipConverter {
        val given: MutableList<Uri> = Collections.synchronizedList(mutableListOf())

        @Volatile
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun convert(source: Uri, target: File) {
            given += source
            gate?.await()
            val name = source.lastPathSegment.orEmpty()
            if (name.startsWith("broken")) throw ClipConversionFailed("broken")
            target.writeBytes(ByteArray(if (name.startsWith("heavy")) HEAVY else LIGHT) { 3 })
        }
    }

    private val converter = Converter()

    /** How long each clip lasts, by its file's name; one not named here lasts 12 seconds. */
    private val durations = mapOf("long.mp4" to 40.seconds, "exactly.mp4" to 15.seconds)

    private fun lasts(source: Uri): Duration = durations[source.lastPathSegment] ?: 12.seconds

    @Before
    fun setUp() {
        FileProviderCache.clear()
        compose.registerIdlingResource(book.idling)
        compose.registerIdlingResource(device.idling)
        compose.registerIdlingResource(TestPhotos.idling)
    }

    /** Holds every clip at its gate from now on; the media work is then no longer waited for, as it is held. */
    private fun holdClips() {
        compose.unregisterIdlingResource(TestPhotos.idling)
        converter.gate = CompletableDeferred()
    }

    @After
    fun close() {
        converter.gate?.cancel()
        saving.cancel()
        compose.unregisterIdlingResource(book.idling)
        compose.unregisterIdlingResource(device.idling)
        compose.unregisterIdlingResource(TestPhotos.idling)
        book.close()
        device.close()
        store.directory(BookName.Pool).deleteRecursively()
    }

    private fun threeSteps(): Recipe =
        Recipe.create(now, null).copy(title = "Borscht", steps = listOf(Step(0, "Boil."), Step(1, "Fry."), Step(2, "Serve.")))

    private fun open(recipe: Recipe): RecipeId {
        runBlocking { recipes.save(recipe) }
        val environment = EditorEnvironment(
            ActiveBook(BookName.Pool, book.database),
            device.catalog(context),
            clipStore = { ClipStore(it.filesDir, converter, ::lasts, maxBytes = MAX_BYTES) },
        ) { now }
        compose.setContent {
            MaterialTheme { EditorScreen(recipe.id, recipes, environment, saving, onClose = {}) }
        }
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.FORM), PATIENCE_MS)
        return recipe.id
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun bounds(tag: String): Rect =
        compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().let { Rect(it.positionInRoot, it.size.toSize()) }

    /** The recipe as the book holds it, once the screen has run what it was given — the work's resumption among it. */
    private fun stored(id: RecipeId): Recipe {
        compose.waitForIdle()
        return runBlocking { recipes.get(id) }!!
    }

    private fun held(): List<String> = store.directory(BookName.Pool).list()?.filterNot { it.startsWith(".") }?.sorted().orEmpty()

    private val activity: ShadowActivity get() = shadowOf(compose.activity)

    private fun started(): Intent {
        compose.waitUntil(PATIENCE_MS) { activity.peekNextStartedActivityForResult() != null }
        return activity.nextStartedActivityForResult.intent
    }

    /** Opens the media menu of the step at [position] and chooses [item] in it. */
    private fun menu(position: Int, item: String) {
        compose.swipeAwayStatement()
        compose.onNodeWithTag(EditorTags.stepPhoto(position)).performScrollTo().performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(item), PATIENCE_MS)
        compose.onNodeWithTag(item).performClick()
    }

    /** Opens the camera to record a clip at the step at [position]; its intent is given back. */
    private fun openRecorder(position: Int): Intent {
        menu(position, EditorTags.stepClipRecord(position))
        return started()
    }

    private fun output(intent: Intent): Uri {
        @Suppress("DEPRECATION")
        return intent.getParcelableExtra(MediaStore.EXTRA_OUTPUT)!!
    }

    /** Records a clip at the step at [position], as the camera application would: written, then come back. */
    private fun record(position: Int): Intent {
        val intent = openRecorder(position)
        context.contentResolver.openOutputStream(output(intent))!!.use { it.write(ByteArray(4000) { 1 }) }
        compose.runOnUiThread { activity.receiveResult(intent, Activity.RESULT_OK, null) }
        return intent
    }

    /** Picks the clip [name] in the system picker at the step at [position]. */
    private fun choose(position: Int, name: String): File {
        val file = File(incoming, name).apply { writeBytes(ByteArray(3000) { 2 }) }
        menu(position, EditorTags.stepClipChoose(position))
        val intent = started()
        compose.runOnUiThread { activity.receiveResult(intent, Activity.RESULT_OK, Intent().setData(Uri.fromFile(file))) }
        return file
    }

    /** Takes a photograph at the step at [position] with the camera, cropped as it opens. */
    private fun takePhoto(position: Int) {
        val shot = TestPhotos.jpeg(File(incoming, "shot.jpg"), 1600, 1200).readBytes()
        menu(position, EditorTags.stepPhotoTake(position))
        compose.waitUntil(PATIENCE_MS) { TestCamera.launched(activity) }
        val intent = TestCamera.take(activity)
        TestCamera.write(context, intent, shot)
        compose.runOnUiThread { TestCamera.answer(activity, intent, saved = true) }
        compose.waitUntilExactlyOneExists(hasTestTag(CropTags.FRAME), PATIENCE_MS)
        compose.onNodeWithTag(CropTags.DONE).performClick()
        compose.waitUntil(PATIENCE_MS) { !exists(CropTags.SCREEN) }
    }

    /** Waits until [text] is said at the bottom of the editor and checks it is not written in the step's block. */
    private fun awaitNotice(position: Int, text: String) {
        compose.awaitStatement(text)
        compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag(EditorTags.step(position))), useUnmergedTree = true)
            .assertDoesNotExist()
    }

    private val converted =
        "Clip attached. It was converted on this phone to at most 1280 pixels and 6 MB, and its sound was removed."

    // FLOW-026: every editable step offers recording and choosing a clip in its one media menu, beside the photo's
    // ways; the cover offers neither.
    @Test
    fun eachStepOffersAClipInItsMediaMenuAndTheCoverDoesNot() {
        open(threeSteps())

        compose.onNode(hasText("Record a clip") or hasText("Choose a clip")).assertDoesNotExist()
        (0..2).forEach { position ->
            val control = bounds(EditorTags.stepPhoto(position))
            val block = bounds(EditorTags.step(position))
            assertTrue("step $position offers its media inside its block", control.top >= block.top && control.bottom <= block.bottom)
        }
        assertEquals(listOf("Photo or clip for step 2"), compose.descriptionOf(EditorTags.stepPhoto(1)))
        compose.onNodeWithTag(EditorTags.stepPhoto(1)).performScrollTo().performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.stepClipRecord(1)), PATIENCE_MS)
        assertEquals(listOf("Record a clip"), compose.textsOf(EditorTags.stepClipRecord(1)))
        assertEquals(listOf("Choose a clip"), compose.textsOf(EditorTags.stepClipChoose(1)))
        assertTrue(exists(EditorTags.stepPhotoTake(1)) && exists(EditorTags.stepPhotoChoose(1)))
        assertFalse(exists(EditorTags.stepPhotoRemove(1)))
        AccessibilityChecks.assertTouchTargets(compose)
        compose.onNode(
            (hasText("Record a clip") or hasText("Choose a clip")) and hasAnyAncestor(hasTestTag(CoverTags.COVER)),
            useUnmergedTree = true,
        ).assertDoesNotExist()
    }

    // FLOW-026, MEDIA-005: "Record a clip" opens the phone's camera by intent, limited to 15 seconds and writing into a
    // file of the capture provider; the clip is converted, attached to that step alone, said to be converted with its
    // sound removed, shown with its mark — and the capture's file is deleted.
    @Test
    fun aRecordedClipIsLimitedConvertedAttachedAndItsCaptureDeleted() {
        val id = open(threeSteps())

        val intent = record(1)

        assertEquals(MediaStore.ACTION_VIDEO_CAPTURE, intent.action)
        assertEquals(15, intent.getIntExtra(MediaStore.EXTRA_DURATION_LIMIT, -1))
        assertEquals(context.packageName + ".capture", output(intent).authority)
        assertEquals(output(intent), intent.clipData!!.getItemAt(0).uri)
        assertTrue(intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0)
        awaitNotice(1, converted)
        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.stepClipImage(1)) && stored(id).stepMedia(1) != null }
        val clip = stored(id).stepMedia(1)!!
        assertEquals(Media(clip.mediaId, null, MediaKind.VIDEO, MediaAttachment.ToStep(1), MediaVisibility.PRIVATE), clip)
        assertEquals(listOf(clip), stored(id).media)
        assertEquals(listOf(clip.mediaId), held())
        assertEquals(1, converter.given.size)
        compose.waitUntil(PATIENCE_MS) { TestCamera.left(context).isEmpty() }
        assertTrue(exists(EditorTags.stepClipMark(1)))
        listOf(0, 2).forEach { assertFalse(exists(EditorTags.stepClipImage(it))) }
        assertFalse(exists(CoverTags.IMAGE))
    }

    // FLOW-026: "Choose a clip" opens the system picker for videos only.
    @Test
    fun choosingAClipOpensThePickerForVideosOnly() {
        val id = open(threeSteps())

        menu(0, EditorTags.stepClipChoose(0))
        val intent = started()

        assertTrue(intent.action, intent.action == MediaStore.ACTION_PICK_IMAGES || intent.action == Intent.ACTION_OPEN_DOCUMENT)
        assertEquals("video/*", intent.type)
        compose.runOnUiThread { activity.receiveResult(intent, Activity.RESULT_CANCELED, null) }
        compose.waitForIdle()
        assertTrue(stored(id).media.isEmpty())
        assertTrue(converter.given.isEmpty())
    }

    // MEDIA-007: a clip of 40 seconds is refused at once — nothing converted — and the message names 15 seconds; one
    // of 12 seconds attaches.
    @Test
    fun aClipTooLongIsRefusedBeforeConversionAndOneWithinTheLimitAttaches() {
        val id = open(threeSteps())

        choose(2, "long.mp4")

        awaitNotice(2, "This clip is longer than 15 seconds, so nothing was attached.")
        assertTrue(converter.given.isEmpty())
        assertTrue(stored(id).media.isEmpty())
        assertEquals(emptyList<String>(), held())

        choose(2, "large.mp4")

        awaitNotice(2, converted)
        compose.waitUntil(PATIENCE_MS) { stored(id).stepMedia(2)?.kind == MediaKind.VIDEO }
        assertEquals(1, converter.given.size)
        assertTrue(held().single().let { store.file(BookName.Pool, it).length() <= MAX_BYTES })
    }

    // MEDIA-006, MEDIA-008: a clip too heavy once converted, or one that cannot be converted, attaches nothing and
    // says why.
    @Test
    fun aClipTooHeavyOrUnreadableIsRefusedWithAReason() {
        val id = open(threeSteps())

        choose(1, "heavy.mp4")
        awaitNotice(1, "This clip could not be made smaller than 6 MB, so nothing was attached.")

        choose(1, "broken.mp4")
        awaitNotice(1, "This file could not be read or converted as a clip, so nothing was attached.")

        assertTrue(stored(id).media.isEmpty())
        assertEquals(emptyList<String>(), held())
        assertFalse(exists(EditorTags.stepClipImage(1)))
    }

    // While a clip is converted, the step shows it with a Cancel; cancelled, nothing is attached or kept, the capture's
    // file is deleted, and nothing is said.
    @Test
    fun aConversionShowsAtTheStepAndCanBeCancelled() {
        val id = open(threeSteps())
        holdClips()

        record(0)

        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.stepClipConverting(0)) }
        compose.onNode(hasText("Converting the clip…") and hasAnyAncestor(hasTestTag(EditorTags.step(0))), useUnmergedTree = true)
            .assertExists()
        val converting = bounds(EditorTags.stepClipConverting(0))
        val block = bounds(EditorTags.step(0))
        assertTrue(converting.top >= block.top && converting.bottom <= block.bottom)
        assertEquals(listOf("Cancel converting the clip of step 1"), compose.descriptionOf(EditorTags.stepClipCancel(0)))

        compose.onNodeWithTag(EditorTags.stepClipCancel(0)).performScrollTo().performClick()

        compose.waitUntil(PATIENCE_MS) { !exists(EditorTags.stepClipConverting(0)) }
        compose.waitUntil(PATIENCE_MS) { TestCamera.left(context).isEmpty() }
        compose.waitUntil(PATIENCE_MS) { store.directory(BookName.Pool).list().isNullOrEmpty() }
        assertTrue(stored(id).media.isEmpty())
        assertFalse(exists(EditorTags.stepClipImage(0)))
        assertFalse(exists(EditorTags.STATEMENT))
    }

    // MEDIA-001, MEDIA-009: a step's photograph replaced by a clip, then the clip by a photograph — one item each time,
    // the replaced file deleted.
    @Test
    fun aPhotoIsReplacedByAClipAndTheClipByAPhoto() {
        val id = open(threeSteps())
        takePhoto(1)
        compose.waitUntil(PATIENCE_MS) { stored(id).stepMedia(1)?.kind == MediaKind.PHOTO && exists(EditorTags.stepPhotoImage(1)) }
        val photo = stored(id).stepMedia(1)!!.mediaId

        choose(1, "replacing.mp4")

        compose.waitUntil(PATIENCE_MS) { stored(id).stepMedia(1)?.kind == MediaKind.VIDEO && held().size == 1 }
        val clip = stored(id).stepMedia(1)!!.mediaId
        assertEquals(1, stored(id).media.size)
        assertEquals(listOf(clip), held())
        assertFalse(photo == clip)
        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.stepClipImage(1)) && !exists(EditorTags.stepPhotoImage(1)) }
        awaitNotice(1, converted)

        takePhoto(1)

        compose.waitUntil(PATIENCE_MS) { stored(id).stepMedia(1)?.kind == MediaKind.PHOTO && held().size == 1 }
        assertEquals(1, stored(id).media.size)
        assertEquals(listOf(stored(id).stepMedia(1)!!.mediaId), held())
        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.stepPhotoImage(1)) && !exists(EditorTags.stepClipImage(1)) }
    }

    // A step's clip is removed as a photograph is: its file deleted, and said.
    @Test
    fun aStepClipCanBeRemoved() {
        val id = open(threeSteps())
        choose(0, "removed.mp4")
        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.stepClipImage(0)) }
        awaitNotice(0, converted)

        compose.swipeAwayStatement()
        compose.onNodeWithTag(EditorTags.stepPhoto(0)).performScrollTo().performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.stepPhotoRemove(0)), PATIENCE_MS)
        assertEquals(listOf("Remove clip"), compose.textsOf(EditorTags.stepPhotoRemove(0)))
        compose.onNodeWithTag(EditorTags.stepPhotoRemove(0)).performClick()

        compose.waitUntil(PATIENCE_MS) { !exists(EditorTags.stepClipImage(0)) && stored(id).media.isEmpty() && held().isEmpty() }
        awaitNotice(0, "Step clip removed.")
    }

    /** A clip kept at the step at [position] under [mediaId], whose first frame is [width] by [height]. */
    private fun keptClip(mediaId: String, position: Int, width: Int = 400, height: Int = 300): Media {
        val file = store.file(BookName.Pool, mediaId).apply { parentFile!!.mkdirs(); writeBytes(ByteArray(100)) }
        ShadowMediaMetadataRetriever.addFrame(DataSource.toDataSource(file.path), 0, Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888))
        return Media(mediaId, null, MediaKind.VIDEO, MediaAttachment.ToStep(position), MediaVisibility.PRIVATE)
    }

    // A11Y-001: the clip is shown by its first frame, at the frame's shape, and the screen reader calls it the step's
    // video — nothing else; its mark says nothing of its own.
    @Test
    fun theClipShowsItsFirstFrameAndIsCalledTheStepsVideo() {
        open(threeSteps().copy(media = listOf(keptClip("framed", 1))))

        compose.waitUntil(PATIENCE_MS) {
            exists(EditorTags.stepClipImage(1)) && bounds(EditorTags.stepClipImage(1)).let { it.width / it.height in 1.32f..1.35f }
        }
        val node = compose.onNodeWithTag(EditorTags.stepClipImage(1), useUnmergedTree = true).fetchSemanticsNode()
        assertEquals(listOf("Video of step 2"), node.config[SemanticsProperties.ContentDescription])
        assertEquals(Role.Image, node.config[SemanticsProperties.Role])
        val mark = compose.onNodeWithTag(EditorTags.stepClipMark(1), useUnmergedTree = true).fetchSemanticsNode()
        assertTrue(SemanticsProperties.ContentDescription !in mark.config)
        assertEquals(listOf("Video of step 2"), compose.descriptionOf(EditorTags.stepClipImage(1)))
    }

    @Test
    @Config(qualifiers = "ru")
    fun theClipIsCalledTheStepsVideoInRussian() {
        open(threeSteps().copy(media = listOf(keptClip("ru-framed", 2))))

        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.stepClipImage(2)) }
        assertEquals(listOf("Видео шага 3"), compose.descriptionOf(EditorTags.stepClipImage(2)))
        compose.onNodeWithTag(EditorTags.stepPhoto(2)).performScrollTo().performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.stepClipRecord(2)), PATIENCE_MS)
        assertEquals(listOf("Снять видео"), compose.textsOf(EditorTags.stepClipRecord(2)))
        assertEquals(listOf("Выбрать видео"), compose.textsOf(EditorTags.stepClipChoose(2)))
        assertEquals(listOf("Убрать видео"), compose.textsOf(EditorTags.stepPhotoRemove(2)))
    }

    // A frozen recipe shows its steps' clips and offers nothing to change them.
    @Test
    fun aFrozenRecipeShowsItsStepClipsReadOnly() {
        open(threeSteps().copy(media = listOf(keptClip("frozen-clip", 0))).inState(RecipeState.PUBLISHED))

        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.stepClipImage(0)) }
        assertTrue(exists(EditorTags.stepClipMark(0)))
        assertFalse(exists(EditorTags.stepPhoto(0)) || exists(EditorTags.stepClipRecord(0)))
    }

    // A11Y-001: at 360 dp and twice the font size, the clip, its conversion and its Cancel stay within the step's block
    // and the screen, the Cancel 48 dp, the text whole.
    @Test
    @Config(qualifiers = "en-w360dp-h2000dp", fontScale = 2f)
    fun theClipAndItsConversionFitAtTheNarrowestWidthAndLargestText() = fits("Converting the clip…")

    @Test
    @Config(qualifiers = "ru-w360dp-h2000dp", fontScale = 2f)
    fun theClipAndItsConversionFitAtTheNarrowestWidthAndLargestTextInRussian() = fits("Видео сжимается…")

    private fun fits(convertingText: String) {
        open(threeSteps().copy(media = listOf(keptClip("fits", 1))))
        holdClips()

        choose(1, "fits.mp4")

        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.stepClipConverting(1)) }
        compose.onNodeWithTag(EditorTags.stepClipCancel(1)).performScrollTo()
        val screen = bounds(EditorTags.SCREEN)
        val block = bounds(EditorTags.step(1))
        listOf(EditorTags.stepClipImage(1), EditorTags.stepClipConverting(1), EditorTags.stepClipCancel(1)).forEach { tag ->
            val shown = bounds(tag)
            assertTrue("$tag within its block", shown.left >= block.left - 0.5f && shown.right <= block.right + 0.5f)
            assertTrue("$tag within the screen", shown.right <= screen.right + 0.5f)
        }
        val text = compose.onNode(hasText(convertingText) and hasAnyAncestor(hasTestTag(EditorTags.stepClipConverting(1))), useUnmergedTree = true)
            .fetchSemanticsNode()
        val layout = layoutOf(text)
        assertFalse(layout.didOverflowHeight)
        (0 until layout.lineCount).forEach { assertFalse(layout.isLineEllipsized(it)) }
        AccessibilityChecks.assertTouchTargets(compose)
        AccessibilityChecks.assertInteractiveElementsLabelled(compose)

        compose.onNodeWithTag(EditorTags.stepClipCancel(1)).performClick()
        compose.waitUntil(PATIENCE_MS) { !exists(EditorTags.stepClipConverting(1)) }
        // Cancelled, the step keeps the clip it had.
        assertTrue(exists(EditorTags.stepClipImage(1)))
    }

    private fun layoutOf(node: SemanticsNode): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action!!(layouts)
        return layouts.single()
    }

    private companion object {
        const val LIGHT = 2000
        const val HEAVY = 9000
        const val MAX_BYTES = 5000L
    }
}
