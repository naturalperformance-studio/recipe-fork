package app.recipefork.app.editor

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import app.recipefork.app.card.CardTarget
import app.recipefork.app.card.FakeClipPlayer
import app.recipefork.app.card.FakeClipPlayers
import app.recipefork.app.card.LocalClipPlayers
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
import androidx.compose.ui.test.performTextReplacement
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

    /** The players the editor's clips are played by: ones that play nothing and remember what they were asked. */
    private val players = FakeClipPlayers()

    /** Whether the editor is shown; set false, it closes. */
    private var shown by mutableStateOf(true)

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
            MaterialTheme {
                CompositionLocalProvider(LocalClipPlayers provides players) {
                    if (shown) EditorScreen(recipe.id, recipes, environment, saving, onClose = {})
                }
            }
        }
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.FORM), PATIENCE_MS)
        return recipe.id
    }

    /** Whether the node tagged [tag] is there — for a part of a step's sheet, in that step's sheet, opened for it. */
    private fun exists(tag: String): Boolean {
        compose.reachStep(tag)
        return compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    }

    private fun onRow(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

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
        compose.inStep(position).onNodeWithTag(EditorTags.stepPhoto(position)).performScrollTo().performClick()
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
        compose.awaitStatementOnceSheetCloses(text)
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
            // In the step's own sheet, and nowhere on its row.
            assertFalse(onRow(EditorTags.stepPhoto(position)))
            compose.openStep(position)
            compose.onNode(hasTestTag(EditorTags.stepPhoto(position)) and hasAnyAncestor(hasTestTag(StepSheetTags.SHEET))).assertExists()
        }
        compose.openStep(1)
        assertEquals(listOf("Add photo or clip"), compose.textsOf(EditorTags.stepPhoto(1)))
        compose.inStep(1).onNodeWithTag(EditorTags.stepPhoto(1)).performScrollTo().performClick()
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
        assertTrue(exists(EditorTags.stepClipControl(1)))
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

        // Said in the step's sheet, in its clip's part, while the sheet is open (T-212).
        val tooLong = "This clip is longer than 15 seconds, so nothing was attached."
        compose.waitUntil(PATIENCE_MS) { exists(StepSheetTags.mediaFailure(2)) }
        assertEquals(listOf(tooLong), compose.textsOf(StepSheetTags.mediaFailure(2)))
        assertTrue(converter.given.isEmpty())
        assertTrue(stored(id).media.isEmpty())
        assertEquals(emptyList<String>(), held())

        // Another clip offered in the same sheet clears it; attached, nothing is said wrong there.
        choose(2, "large.mp4")

        compose.waitUntil(PATIENCE_MS) { stored(id).stepMedia(2)?.kind == MediaKind.VIDEO }
        assertTrue(compose.stepSheetOpen())
        assertFalse(exists(StepSheetTags.mediaFailure(2)))
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
        val heavy = "This clip could not be made smaller than 6 MB, so nothing was attached."
        compose.waitUntil(PATIENCE_MS) { exists(StepSheetTags.mediaFailure(1)) }
        assertEquals(listOf(heavy), compose.textsOf(StepSheetTags.mediaFailure(1)))
        awaitNotice(1, heavy)
        // Opened again, the sheet does not repeat what was said.
        assertFalse(exists(StepSheetTags.mediaFailure(1)))

        choose(1, "broken.mp4")
        val broken = "This file could not be read or converted as a clip, so nothing was attached."
        compose.waitUntil(PATIENCE_MS) { exists(StepSheetTags.mediaFailure(1)) }
        assertEquals(listOf(broken), compose.textsOf(StepSheetTags.mediaFailure(1)))
        awaitNotice(1, broken)

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
        compose.onNode(hasText("Converting the clip…") and hasAnyAncestor(hasTestTag(StepSheetTags.SHEET)), useUnmergedTree = true)
            .assertExists()
        val converting = bounds(EditorTags.stepClipConverting(0))
        val block = bounds(StepSheetTags.SHEET)
        assertTrue(converting.top >= block.top && converting.bottom <= block.bottom)
        assertEquals(listOf("Cancel converting the clip of step 1"), compose.descriptionOf(EditorTags.stepClipCancel(0)))

        compose.inStep(0).onNodeWithTag(EditorTags.stepClipCancel(0)).performScrollTo().performClick()

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
        compose.inStep(0).onNodeWithTag(EditorTags.stepPhoto(0)).performScrollTo().performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.stepPhotoRemove(0)), PATIENCE_MS)
        assertEquals(listOf("Remove clip"), compose.textsOf(EditorTags.stepPhotoRemove(0)))
        assertEquals(lightColorScheme().error, compose.textColourOf(EditorTags.stepPhotoRemove(0)))
        compose.inStep(0).onNodeWithTag(EditorTags.stepPhotoRemove(0)).performClick()

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
    // video — nothing else; its control is named as the card's is.
    @Test
    fun theClipShowsItsFirstFrameAndIsCalledTheStepsVideo() {
        // In its step's sheet the clip is in sight at once, and its player reads its size: the clip's own, as its frame's.
        players.width = 400
        players.height = 300
        open(threeSteps().copy(media = listOf(keptClip("framed", 1))))
        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.stepClipImage(1)) }
        compose.inStep(1).onNodeWithTag(EditorTags.stepClipImage(1), useUnmergedTree = true).performScrollTo()

        // The frame is read off the main thread, and handed back on it.
        compose.waitUntilWithMainThread {
            exists(EditorTags.stepClipImage(1)) && bounds(EditorTags.stepClipImage(1)).let { it.width / it.height in 1.32f..1.35f }
        }
        val node = compose.inStep(1).onNodeWithTag(EditorTags.stepClipImage(1), useUnmergedTree = true).fetchSemanticsNode()
        assertEquals(listOf("Video of step 2"), node.config[SemanticsProperties.ContentDescription])
        assertEquals(Role.Image, node.config[SemanticsProperties.Role])
        assertEquals(listOf("Play video"), compose.descriptionOf(EditorTags.stepClipControl(1)))
        assertEquals(listOf("Video of step 2"), compose.descriptionOf(EditorTags.stepClipImage(1)))
    }

    @Test
    @Config(qualifiers = "ru")
    fun theClipIsCalledTheStepsVideoInRussian() {
        open(threeSteps().copy(media = listOf(keptClip("ru-framed", 2))))

        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.stepClipImage(2)) }
        assertEquals(listOf("Видео шага 3"), compose.descriptionOf(EditorTags.stepClipImage(2)))
        assertEquals(listOf("Воспроизвести видео"), compose.descriptionOf(EditorTags.stepClipControl(2)))
        compose.inStep(2).onNodeWithTag(EditorTags.stepPhoto(2)).performScrollTo().performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.stepClipRecord(2)), PATIENCE_MS)
        assertEquals(listOf("Снять видео"), compose.textsOf(EditorTags.stepClipRecord(2)))
        assertEquals(listOf("Выбрать видео"), compose.textsOf(EditorTags.stepClipChoose(2)))
        assertEquals(listOf("Убрать видео"), compose.textsOf(EditorTags.stepPhotoRemove(2)))
    }

    // A step just added whose clip is still being converted holds it: closed, its sheet does not drop it, and the clip
    // reaches it once converted.
    @Test
    fun aNewStepClosedWhileItsClipConvertsIsKept() {
        val id = open(threeSteps())
        holdClips()
        compose.addStepAt(3)
        choose(3, "new.mp4")
        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.stepClipConverting(3)) }
        compose.closeStep()
        compose.waitForIdle()
        assertEquals(4, stored(id).steps.size)
        converter.gate!!.complete(Unit)
        compose.waitUntilWithMainThread { stored(id).stepMedia(3)?.kind == MediaKind.VIDEO }
        assertEquals(4, stored(id).steps.size)
        compose.waitUntil(PATIENCE_MS) { onRow(StepSheetTags.thumbnail(3)) }
    }

    // T-212: a step just added, kept because its sheet closed while its clip converted, goes once the conversion ends
    // with nothing attached, as it holds nothing else; what went wrong is still said.
    @Test
    fun aNewStepKeptForItsClipGoesWhenTheClipIsNotAttached() {
        val id = open(threeSteps())
        holdClips()
        compose.addStepAt(3)
        choose(3, "broken.mp4")
        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.stepClipConverting(3)) }
        compose.closeStep()
        compose.waitForIdle()
        assertEquals(4, stored(id).steps.size)
        converter.gate!!.complete(Unit)
        compose.waitUntilWithMainThread { stored(id).steps.size == 3 }
        assertEquals(listOf("Boil.", "Fry.", "Serve."), stored(id).steps.map { it.text })
        assertFalse(onRow(EditorTags.step(3)))
        compose.awaitStatement("This file could not be read or converted as a clip, so nothing was attached.")
    }

    // A step kept for its clip and opened again is a new step once more: its conversion cancelled there and the sheet
    // closed with nothing in it, it goes; one given a note meanwhile stays when its clip is not attached.
    @Test
    fun aNewStepKeptForItsClipAndOpenedAgainGoesOnlyWhenEmpty() {
        val id = open(threeSteps())
        holdClips()
        compose.addStepAt(3)
        choose(3, "first.mp4")
        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.stepClipConverting(3)) }
        compose.closeStep()
        compose.openStep(3)
        compose.onNodeWithTag(EditorTags.stepClipCancel(3)).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { !exists(EditorTags.stepClipConverting(3)) }
        assertEquals(4, stored(id).steps.size)
        compose.closeStep()
        compose.waitUntil(PATIENCE_MS) { stored(id).steps.size == 3 }

        compose.addStepAt(3)
        choose(3, "broken.mp4")
        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.stepClipConverting(3)) }
        compose.closeStep()
        compose.openStep(3)
        compose.onNodeWithTag(StepNoteTags.field(3)).performScrollTo().performTextReplacement("Optional.")
        compose.closeStep()
        converter.gate!!.complete(Unit)
        compose.awaitStatement("This file could not be read or converted as a clip, so nothing was attached.")
        assertEquals(4, stored(id).steps.size)
        assertEquals("Optional.", stored(id).steps[3].note)
    }

    // A frozen recipe shows its steps' clips and offers nothing to change them.
    @Test
    fun aFrozenRecipeShowsItsStepClipsReadOnly() {
        open(threeSteps().copy(media = listOf(keptClip("frozen-clip", 0))).inState(RecipeState.PUBLISHED))

        // T-211: its row reads as text, with a small picture of the clip's first frame, marked as a clip and named as the
        // step's video; it opens nothing — the clip is watched on the card.
        compose.waitUntil(PATIENCE_MS) { onRow(StepSheetTags.thumbnail(0)) }
        assertEquals(listOf("Video of step 1"), compose.onNodeWithTag(StepSheetTags.thumbnail(0), useUnmergedTree = true).fetchSemanticsNode().config[SemanticsProperties.ContentDescription])
        assertFalse(onRow(EditorTags.stepPhoto(0)) || onRow(EditorTags.stepClipRecord(0)) || onRow(EditorTags.stepClipControl(0)))
        compose.onNodeWithTag(StepSheetTags.line(0), useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertFalse(compose.stepSheetOpen())
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
        compose.inStep(1).onNodeWithTag(EditorTags.stepClipCancel(1)).performScrollTo()
        val screen = bounds(EditorTags.SCREEN)
        val block = bounds(StepSheetTags.SHEET)
        val control = bounds(EditorTags.stepClipControl(1))
        val least = with(compose.density) { 48.dp.toPx() }
        assertTrue("the control is at least 48 dp", control.width >= least - 0.5f && control.height >= least - 0.5f)
        listOf(EditorTags.stepClipImage(1), EditorTags.stepClipControl(1), EditorTags.stepClipConverting(1), EditorTags.stepClipCancel(1)).forEach { tag ->
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

        compose.inStep(1).onNodeWithTag(EditorTags.stepClipCancel(1)).performClick()
        compose.waitUntil(PATIENCE_MS) { !exists(EditorTags.stepClipConverting(1)) }
        // Cancelled, the step keeps the clip it had.
        assertTrue(exists(EditorTags.stepClipImage(1)))
    }

    /** Scrolls the clip at the step at [position] into view and waits until it holds a player. */
    private fun reveal(position: Int) {
        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.stepClipImage(position)) }
        compose.inStep(position).onNodeWithTag(EditorTags.stepClipImage(position), useUnmergedTree = true).performScrollTo()
        compose.waitUntil(PATIENCE_MS) { live() != null && exists(EditorTags.stepClipControl(position)) }
    }

    /** The player the editor's clip holds now, if any. */
    private fun live(): FakeClipPlayer? = players.made.lastOrNull()?.takeUnless { it.released }

    /** Waits until a live player plays the clip, or until none plays it. */
    private fun awaitPlaying(playing: Boolean) = compose.waitUntil(PATIENCE_MS) {
        if (playing) live()?.playing == true else players.made.none { it.playing }
    }

    private fun steps(count: Int): Recipe =
        Recipe.create(now, null).copy(title = "Borscht", steps = (0 until count).map { Step(it, "Step text number $it.") })

    // MEDIA-004: in the editor a step's clip shows its first frame with the card's round control offering to play; it
    // never starts by itself.
    @Test
    @Config(qualifiers = "w360dp-h640dp")
    fun theClipShowsItsFirstFrameAndDoesNotPlayByItself() {
        val red = keptClip("still", 0, 400, 300)
        // The player reads the clip at the frame's own size, as the device's does.
        players.width = 400
        players.height = 300
        ShadowMediaMetadataRetriever.addFrame(
            DataSource.toDataSource(store.file(BookName.Pool, "still").path),
            0,
            Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.RED) },
        )
        open(threeSteps().copy(media = listOf(red)))
        reveal(0)
        compose.waitUntil(PATIENCE_MS) {
            live() != null && exists(EditorTags.stepClipImage(0)) && bounds(EditorTags.stepClipImage(0)).let { it.width / it.height in 1.32f..1.35f }
        }
        compose.waitForIdle()
        assertEquals(store.file(BookName.Pool, "still"), live()!!.file)
        assertFalse(live()!!.playing)
        assertEquals(listOf("Play video"), compose.descriptionOf(EditorTags.stepClipControl(0)))
        val control = bounds(EditorTags.stepClipControl(0))
        val box = bounds(EditorTags.stepClipImage(0))
        assertTrue("the control sits in the clip's bottom-end corner", control.right <= box.right && control.bottom <= box.bottom && control.left > box.center.x)
        // The card's own control: the same round 56 dp button.
        val card = with(compose.density) { CardTarget.toPx() }
        assertEquals(card, control.width, 0.5f)
        assertEquals(card, control.height, 0.5f)
        // The first frame is drawn in the box, away from the control.
        val shot = compose.onRoot().captureToImage().toPixelMap()
        val pixel = shot[(box.left + box.width / 4).toInt(), (box.top + box.height / 4).toInt()]
        assertTrue("the first frame is shown: $pixel", pixel.red > 0.9f && pixel.green < 0.1f && pixel.blue < 0.1f)
        // Still not playing once all is settled.
        compose.waitForIdle()
        assertTrue(players.made.none { it.playing })
    }

    // MEDIA-004, A11Y-007: a tap on the control plays it, another pauses it; a tap on the clip does the same.
    @Test
    fun aTapPlaysTheClipAndAnotherPausesIt() {
        open(threeSteps().copy(media = listOf(keptClip("tapped", 1))))
        reveal(1)

        compose.inStep(1).onNodeWithTag(EditorTags.stepClipControl(1)).performScrollTo().performClick()
        awaitPlaying(true)
        assertEquals(listOf("Pause video"), compose.descriptionOf(EditorTags.stepClipControl(1)))
        compose.inStep(1).onNodeWithTag(EditorTags.stepClipControl(1)).performClick()
        awaitPlaying(false)
        assertEquals(listOf("Play video"), compose.descriptionOf(EditorTags.stepClipControl(1)))

        // A tap anywhere on the clip does as the control does.
        compose.inStep(1).onNodeWithTag(EditorTags.stepClipImage(1)).performScrollTo()
        compose.waitForIdle()
        compose.inStep(1).onNodeWithTag(EditorTags.stepClipImage(1)).performClick()
        compose.waitForIdle()
        awaitPlaying(true)
        compose.inStep(1).onNodeWithTag(EditorTags.stepClipImage(1)).performClick()
        compose.waitForIdle()
        awaitPlaying(false)
        assertEquals(1, players.made.size)
    }

    // MEDIA-004: the editor holds a player only while the clip is on screen; scrolled away it is let go of, scrolled
    // back a new one shows the clip, again not playing by itself.
    // The clip leaving the screen — its step's sheet closed — releases its player; back in sight, it gets a new one.
    @Test
    @Config(qualifiers = "w360dp-h640dp")
    fun theClipLeavingTheScreenReleasesItsPlayer() {
        open(steps(30).copy(media = listOf(keptClip("scrolled", 0))))
        reveal(0)
        compose.inStep(0).onNodeWithTag(EditorTags.stepClipControl(0)).performScrollTo().performClick()
        awaitPlaying(true)

        compose.closeStep()
        compose.waitUntil(PATIENCE_MS) { players.made.all { it.released } }
        assertFalse(players.made.any { it.playing })

        val made = players.made.size
        compose.inStep(0).onNodeWithTag(EditorTags.stepClipImage(0)).performScrollTo()
        compose.waitUntil(PATIENCE_MS) { players.made.size > made && live() != null }
    }

    // A vertical drag that starts on the clip, in its step's sheet, does not toggle the clip.
    @Test
    @Config(qualifiers = "w360dp-h640dp")
    fun aDragStartingOnTheClipDoesNotToggleIt() {
        open(steps(12).copy(media = listOf(keptClip("dragged", 1))))
        reveal(1)
        compose.waitForIdle()
        compose.inStep(1).onNodeWithTag(EditorTags.stepClipImage(1)).performTouchInput {
            swipe(Offset(width / 4f, height * 0.8f), Offset(width / 4f, height * 0.1f), durationMillis = 300)
        }
        compose.waitForIdle()
        assertTrue(players.made.none { it.playing })
    }

    // The step's media menu stays reachable beside the previewed clip and is not the clip's control.
    @Test
    fun theMediaMenuStillOpensBesideThePreview() {
        open(threeSteps().copy(media = listOf(keptClip("menu", 1))))
        reveal(1)
        val menu = bounds(EditorTags.stepPhoto(1))
        val clip = bounds(EditorTags.stepClipImage(1))
        assertFalse("the menu's control is not over the clip", menu.overlaps(clip))
        compose.inStep(1).onNodeWithTag(EditorTags.stepPhoto(1)).performScrollTo().performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.stepPhotoRemove(1)), PATIENCE_MS)
        assertTrue(exists(EditorTags.stepClipRecord(1)) && exists(EditorTags.stepClipChoose(1)))
        assertTrue(players.made.none { it.playing })
    }

    // Replaced by another clip, the clip's player is let go of and the new clip gets its own, not playing.
    @Test
    fun replacingTheClipReleasesItsPlayer() {
        val id = open(threeSteps().copy(media = listOf(keptClip("first", 1))))
        reveal(1)
        compose.inStep(1).onNodeWithTag(EditorTags.stepClipControl(1)).performScrollTo().performClick()
        awaitPlaying(true)
        val first = live()!!

        choose(1, "second.mp4")

        compose.waitUntil(PATIENCE_MS) { stored(id).stepMedia(1)?.mediaId.let { it != null && it != "first" } }
        val second = store.file(BookName.Pool, stored(id).stepMedia(1)!!.mediaId)
        compose.waitUntil(PATIENCE_MS) { first.released && live()?.file == second }
        compose.waitForIdle()
        assertTrue(players.made.filter { it !== live() }.all { it.released })
        assertFalse(live()!!.playing)
    }

    // Removed, the clip's player is let go of; the editor closed, so is every player it held.
    @Test
    @Config(qualifiers = "w360dp-h2000dp")
    fun removingTheClipOrClosingTheEditorReleasesItsPlayer() {
        val id = open(threeSteps().copy(media = listOf(keptClip("gone", 0), keptClip("kept", 2))))
        // A clip holds a player while it is in sight, in its step's sheet.
        reveal(0)
        compose.inStep(0).onNodeWithTag(EditorTags.stepClipControl(0)).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { players.made.any { it.playing } }
        val removed = players.made.single { it.file!!.name == "gone" && !it.released }

        compose.inStep(0).onNodeWithTag(EditorTags.stepPhoto(0)).performScrollTo().performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.stepPhotoRemove(0)), PATIENCE_MS)
        compose.inStep(0).onNodeWithTag(EditorTags.stepPhotoRemove(0)).performClick()
        compose.waitUntil(PATIENCE_MS) { removed.released && stored(id).stepMedia(0) == null }
        assertTrue(players.made.filter { it.file!!.name == "gone" }.all { it.released })
        reveal(2)
        assertTrue(players.made.any { it.file!!.name == "kept" && !it.released })

        shown = false
        compose.waitForIdle()
        compose.waitUntil(PATIENCE_MS) { players.made.all { it.released } }
        assertFalse(players.made.any { it.playing })
    }

    // A clip the device cannot play is still shown by its first frame, with the mark, named as the step's video.
    @Test
    fun aClipThatCannotBePlayedShowsItsFirstFrameWithTheMark() {
        players.fails = true
        open(threeSteps().copy(media = listOf(keptClip("unplayable", 1))))
        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.stepClipImage(1)) }
        compose.inStep(1).onNodeWithTag(EditorTags.stepClipImage(1)).performScrollTo()
        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.stepClipMark(1)) }
        assertFalse(exists(EditorTags.stepClipControl(1)))
        assertEquals(listOf("Video of step 2"), compose.descriptionOf(EditorTags.stepClipImage(1)))
        assertTrue(bounds(EditorTags.stepClipImage(1)).let { it.width / it.height in 1.32f..1.35f })
        shown = false
        compose.waitForIdle()
        compose.waitUntil(PATIENCE_MS) { players.made.all { it.released } }
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
