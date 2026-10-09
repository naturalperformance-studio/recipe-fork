package app.recipefork.app.editor

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Looper
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
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
import app.recipefork.app.card.FakeClipPlayers
import app.recipefork.app.card.LocalClipPlayers
import app.recipefork.app.file.FileProviderCache
import app.recipefork.app.media.AnimatedImage
import app.recipefork.app.media.AnimatedImages
import app.recipefork.app.media.AnimationClips
import app.recipefork.app.media.ClipConverter
import app.recipefork.app.media.ClipStore
import app.recipefork.app.media.HeldAnimatedImages
import app.recipefork.app.media.ImageKind
import app.recipefork.app.media.PhotoStore
import app.recipefork.app.media.ResolverAnimatedImages
import app.recipefork.app.media.TestCamera
import app.recipefork.app.media.TestPhotos
import app.recipefork.app.media.crop.CropTags
import app.recipefork.app.media.stepMedia
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.recipe.Media
import app.recipefork.core.recipe.MediaAttachment
import app.recipefork.core.recipe.MediaKind
import app.recipefork.core.recipe.MediaVisibility
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeId
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
import java.io.File
import java.time.Instant
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds

/**
 * An animated GIF or WebP chosen for a step (scenario 3): it skips the crop and is attached as a silent clip, exactly as
 * a clip chosen is — its conversion shown with a Cancel, said to be converted, refused as a clip is, the last item
 * offered winning — while a still picture, a still GIF or WebP among them, goes through the crop, and the cover's
 * picker never takes the animation's way. The detection is the application's own, reading the fixtures; the encoding
 * is the device's (AnimationEncoder's device tests), so an encoder stands in for it here.
 */
/**
 * The application's own detection, holding what it told for the conversion as the application's does, with what the
 * tests watch: the pictures it was asked about, whether it was ever asked on the main thread, the pictures read for a
 * conversion, and how many times a file was parsed. While [gate] is set, telling waits for it.
 */
class WatchedAnimatedImages(context: Context) : AnimatedImages {
    private val real = ResolverAnimatedImages(context)
    val parses = AtomicInteger(0)
    private val held = HeldAnimatedImages(object : AnimatedImages {
        override fun read(source: Uri): AnimatedImage? = real.read(source).also { parses.incrementAndGet() }

        override fun examine(source: Uri): ImageKind = real.examine(source).also { parses.incrementAndGet() }
    })
    val asked: MutableList<Uri> = Collections.synchronizedList(mutableListOf())
    val converted: MutableList<Uri> = Collections.synchronizedList(mutableListOf())
    val askedOnMain = AtomicBoolean(false)

    @Volatile
    var gate: CompletableDeferred<Unit>? = null

    override fun examine(source: Uri): ImageKind {
        asked += source
        if (Looper.myLooper() == Looper.getMainLooper()) askedOnMain.set(true)
        gate?.let { runBlocking { it.await() } }
        return held.examine(source)
    }

    override fun read(source: Uri): AnimatedImage? = held.read(source).also { converted += source }
}

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StepAnimationEditorTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val device = TestDevice(context)
    private val recipes = RecipeRepository(ActiveBook(BookName.Pool, book.database))
    private val store = PhotoStore(context.filesDir)
    private val saving = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val now = Instant.parse("2026-10-09T12:00:00Z")
    private val incoming = File(context.externalCacheDir ?: context.cacheDir, "incoming").apply { mkdirs() }
    private val players = FakeClipPlayers()

    private val images = WatchedAnimatedImages(context)

    /** Stands in for the device's encoder: what it encoded; while [gate] is set it waits for it; [failing], it fails. */
    private val encoded: MutableList<AnimatedImage> = Collections.synchronizedList(mutableListOf())

    @Volatile
    private var gate: CompletableDeferred<Unit>? = null

    @Volatile
    private var failing = false

    private suspend fun encode(animation: AnimatedImage, target: File) {
        encoded += animation
        gate?.await()
        if (failing) throw IllegalStateException("codec")
        target.writeBytes(ByteArray(ANIMATION_BYTES) { 5 })
    }

    /** Stands in for the device's clip converter: what it was given; while [clipGate] is set it waits for it. */
    private val clipsGiven: MutableList<Uri> = Collections.synchronizedList(mutableListOf())

    @Volatile
    private var clipGate: CompletableDeferred<Unit>? = null

    private val clipConverter = ClipConverter { source, target ->
        clipsGiven += source
        clipGate?.await()
        target.writeBytes(ByteArray(CLIP_BYTES) { 2 })
    }

    @Before
    fun setUp() {
        FileProviderCache.clear()
        compose.registerIdlingResource(book.idling)
        compose.registerIdlingResource(device.idling)
        compose.registerIdlingResource(TestPhotos.idling)
    }

    @After
    fun close() {
        images.gate?.cancel()
        gate?.cancel()
        clipGate?.cancel()
        saving.cancel()
        compose.unregisterIdlingResource(book.idling)
        compose.unregisterIdlingResource(device.idling)
        compose.unregisterIdlingResource(TestPhotos.idling)
        book.close()
        device.close()
        store.directory(BookName.Pool).deleteRecursively()
    }

    /** The media work is held from now on, so it is no longer waited for. */
    private fun holdWork() {
        compose.unregisterIdlingResource(TestPhotos.idling)
    }

    private fun threeSteps(): Recipe =
        Recipe.create(now, null).copy(title = "Borscht", steps = listOf(Step(0, "Boil."), Step(1, "Fry."), Step(2, "Serve.")))

    private fun open(recipe: Recipe): RecipeId {
        runBlocking { recipes.save(recipe) }
        val environment = EditorEnvironment(
            ActiveBook(BookName.Pool, book.database),
            device.catalog(context),
            clipStore = { ClipStore(it.filesDir, clipConverter, { 10.seconds }) },
            animatedImages = { images },
            animationStore = { context, shared -> AnimationClips(shared, ::encode).let { ClipStore(context.filesDir, it, it) } },
        ) { now }
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalClipPlayers provides players) {
                    EditorScreen(recipe.id, recipes, environment, saving, onClose = {})
                }
            }
        }
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.FORM), PATIENCE_MS)
        return recipe.id
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

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

    /** The fixture [fixture] as a file the picker hands over, named [name]. */
    private fun picture(fixture: String, name: String = fixture): File =
        File(incoming, name).apply { writeBytes(checkNotNull(StepAnimationEditorTest::class.java.getResourceAsStream("/animation/$fixture")).use { it.readBytes() }) }

    /** Opens the media menu of the step at [position] and chooses [item] in it. */
    private fun menu(position: Int, item: String) {
        compose.swipeAwayStatement()
        compose.onNodeWithTag(EditorTags.stepPhoto(position)).performScrollTo().performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(item), PATIENCE_MS)
        compose.onNodeWithTag(item).performClick()
    }

    /** Hands [file] over through the system picker [intent] opened. */
    private fun pickIn(intent: Intent, file: File) {
        compose.runOnUiThread { activity.receiveResult(intent, Activity.RESULT_OK, Intent().setData(Uri.fromFile(file))) }
    }

    /** Chooses [file] with the step's "Choose a photo" at [position], the picker offering images. */
    private fun choosePhoto(position: Int, file: File) {
        menu(position, EditorTags.stepPhotoChoose(position))
        val intent = started()
        assertTrue(intent.action, intent.action == MediaStore.ACTION_PICK_IMAGES || intent.action == Intent.ACTION_OPEN_DOCUMENT)
        assertEquals("image/*", intent.type)
        pickIn(intent, file)
    }

    /** Chooses a clip named [name] with the step's "Choose a clip" at [position]. */
    private fun chooseClip(position: Int, name: String) {
        val file = File(incoming, name).apply { writeBytes(ByteArray(3000) { 2 }) }
        menu(position, EditorTags.stepClipChoose(position))
        pickIn(started(), file)
    }

    /** Waits until [text] is said at the bottom of the editor and checks it is not written in the step's block. */
    private fun awaitNotice(position: Int, text: String) {
        compose.awaitStatement(text)
        compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag(EditorTags.step(position))), useUnmergedTree = true)
            .assertDoesNotExist()
    }

    private val converted =
        "Clip attached. It was converted on this phone to at most 1280 pixels and 6 MB, and its sound was removed."

    private fun clipAt(id: RecipeId, position: Int): Media? = stored(id).stepMedia(position)?.takeIf { it.kind == MediaKind.VIDEO }

    // Scenario 3: an animated GIF chosen as a step's photo opens no crop; it is converted as a clip is, attached to that
    // step as its clip — the media the card plays — and said as a clip is; the detection ran off the main thread.
    @Test
    fun anAnimatedGifChosenForAStepIsAttachedAsAClipWithoutTheCrop() {
        val id = open(threeSteps())

        choosePhoto(1, picture("animated.gif"))

        awaitNotice(1, converted)
        compose.waitUntil(PATIENCE_MS) { clipAt(id, 1) != null && exists(EditorTags.stepClipImage(1)) }
        val clip = stored(id).stepMedia(1)!!
        assertEquals(Media(clip.mediaId, null, MediaKind.VIDEO, MediaAttachment.ToStep(1), MediaVisibility.PRIVATE), clip)
        assertEquals(listOf(clip), stored(id).media)
        assertEquals(listOf(clip.mediaId), held())
        assertEquals(ANIMATION_BYTES.toLong(), store.file(BookName.Pool, clip.mediaId).length())
        assertEquals(1, encoded.size)
        assertTrue(clipsGiven.isEmpty())
        assertFalse(exists(CropTags.SCREEN))
        assertEquals(1, images.asked.size)
        assertFalse("the detection ran on the main thread", images.askedOnMain.get())
        assertEquals("the animation told is the one converted, parsed once", 1, images.parses.get())
        assertEquals(images.asked, images.converted)
    }

    // Scenario 3: an animated WebP is attached the same way.
    @Test
    fun anAnimatedWebpChosenForAStepIsAttachedAsAClipWithoutTheCrop() {
        val id = open(threeSteps())

        choosePhoto(2, picture("animated.webp"))

        awaitNotice(2, converted)
        compose.waitUntil(PATIENCE_MS) { clipAt(id, 2) != null }
        assertEquals(1, encoded.size)
        assertFalse(exists(CropTags.SCREEN))
        assertFalse(images.askedOnMain.get())
    }

    // Scenario 3: a still picture — a JPEG, a PNG, a still GIF, a still WebP — goes through the crop as before, and is
    // attached as a photograph; nothing is converted. The detection ran off the main thread.
    @Test
    fun aStillPictureChosenForAStepGoesThroughTheCrop() {
        val id = open(threeSteps())

        listOf("still.jpg", "still.png", "still.gif", "still.webp").forEach { name ->
            choosePhoto(0, picture(name))
            compose.waitUntilExactlyOneExists(hasTestTag(CropTags.SCREEN), PATIENCE_MS)
            compose.onNodeWithTag(CropTags.CANCEL).performClick()
            compose.waitUntil(PATIENCE_MS) { !exists(CropTags.SCREEN) }
        }

        choosePhoto(0, picture("still.png", "kept.png"))
        compose.waitUntilExactlyOneExists(hasTestTag(CropTags.FRAME), PATIENCE_MS)
        compose.onNodeWithTag(CropTags.DONE).performClick()
        compose.waitUntil(PATIENCE_MS) { stored(id).stepMedia(0)?.kind == MediaKind.PHOTO }
        assertTrue(encoded.isEmpty())
        assertTrue(clipsGiven.isEmpty())
        assertEquals(5, images.asked.size)
        assertFalse("the detection ran on the main thread", images.askedOnMain.get())
    }

    // Scenario 3: while the animation is converted the step shows it with the clip's Cancel; cancelled, nothing is
    // attached or kept, and nothing is said.
    @Test
    fun anAnimationsConversionShowsAtTheStepAndCanBeCancelled() {
        val id = open(threeSteps())
        holdWork()
        gate = CompletableDeferred()

        choosePhoto(0, picture("animated.gif"))

        compose.waitUntil(PATIENCE_MS) { exists(EditorTags.stepClipConverting(0)) && encoded.isNotEmpty() }
        compose.onNode(hasText("Converting the clip…") and hasAnyAncestor(hasTestTag(EditorTags.step(0))), useUnmergedTree = true)
            .assertExists()
        compose.onNodeWithTag(EditorTags.stepClipCancel(0)).performScrollTo().performClick()

        compose.waitUntil(PATIENCE_MS) { !exists(EditorTags.stepClipConverting(0)) }
        compose.waitUntil(PATIENCE_MS) { store.directory(BookName.Pool).list().isNullOrEmpty() }
        assertTrue(stored(id).media.isEmpty())
        assertFalse(exists(EditorTags.stepClipImage(0)))
        assertFalse(exists(EditorTags.STATEMENT))
    }

    // The last item offered wins: an animation offered while a clip converts stops the clip, and a clip offered while
    // an animation converts stops the animation; one item is attached each time, nothing else kept.
    @Test
    fun theLastItemOfferedWinsBetweenAnAnimationAndAClip() {
        val id = open(threeSteps())
        holdWork()

        clipGate = CompletableDeferred()
        chooseClip(1, "first.mp4")
        compose.waitUntil(PATIENCE_MS) { clipsGiven.isNotEmpty() && exists(EditorTags.stepClipConverting(1)) }
        choosePhoto(1, picture("animated.gif"))

        compose.waitUntil(PATIENCE_MS) { clipAt(id, 1) != null && !exists(EditorTags.stepClipConverting(1)) }
        val animation = clipAt(id, 1)!!.mediaId
        assertEquals(ANIMATION_BYTES.toLong(), store.file(BookName.Pool, animation).length())
        compose.waitUntil(PATIENCE_MS) { held() == listOf(animation) }

        gate = CompletableDeferred()
        choosePhoto(1, picture("animated.webp"))
        awaitWork { encoded.size == 2 && exists(EditorTags.stepClipConverting(1)) }
        clipGate = null
        chooseClip(1, "second.mp4")

        compose.waitUntil(PATIENCE_MS) { clipAt(id, 1)?.mediaId?.let { it != animation } == true && !exists(EditorTags.stepClipConverting(1)) }
        val clip = clipAt(id, 1)!!.mediaId
        assertEquals(CLIP_BYTES.toLong(), store.file(BookName.Pool, clip).length())
        compose.waitUntil(PATIENCE_MS) { held() == listOf(clip) }
        assertEquals(1, stored(id).media.size)
    }

    // MEDIA-007: an animation longer than 15 seconds is refused with the clip's words, before anything is encoded.
    @Test
    fun anAnimationTooLongIsRefusedAsAClipIs() {
        val id = open(threeSteps())

        choosePhoto(2, picture("long.gif"))

        awaitNotice(2, "This clip is longer than 15 seconds, so nothing was attached.")
        assertTrue(encoded.isEmpty())
        assertTrue(stored(id).media.isEmpty())
        assertEquals(emptyList<String>(), held())
        assertFalse(exists(CropTags.SCREEN))
    }

    // MEDIA-008: an animation that cannot be converted is refused with the clip's words; nothing is kept.
    @Test
    fun anAnimationThatCannotBeConvertedIsRefusedAsAClipIs() {
        val id = open(threeSteps())
        failing = true

        choosePhoto(1, picture("animated.webp"))

        awaitNotice(1, "This file could not be read or converted as a clip, so nothing was attached.")
        assertTrue(stored(id).media.isEmpty())
        assertEquals(emptyList<String>(), held())
    }

    @Test
    @Config(qualifiers = "ru")
    fun anAnimationIsSaidAsAClipInRussian() {
        val id = open(threeSteps())

        choosePhoto(0, picture("animated.gif"))

        compose.awaitStatement("Видео прикреплено. Оно сжато на этом телефоне до 1280 пикселей и 6 МБ, звук удалён.")
        compose.waitUntil(PATIENCE_MS) { clipAt(id, 0) != null }
    }

    // A cover stays a still photograph: an animated GIF chosen for it goes through the crop, and nothing asks whether
    // it is animated or converts it.
    @Test
    fun anAnimatedGifChosenForTheCoverGoesThroughTheCrop() {
        val id = open(threeSteps())

        compose.swipeAwayStatement()
        compose.onNodeWithTag(CoverTags.PLACEHOLDER).performScrollTo().performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(CoverTags.CHOOSE), PATIENCE_MS)
        compose.onNodeWithTag(CoverTags.CHOOSE).performClick()
        pickIn(started(), picture("animated.gif"))

        compose.waitUntilExactlyOneExists(hasTestTag(CropTags.SCREEN), PATIENCE_MS)
        assertTrue(images.asked.isEmpty())
        assertTrue(encoded.isEmpty())
        assertFalse(exists(EditorTags.stepClipConverting(0)))
        assertTrue(stored(id).media.none { it.kind == MediaKind.VIDEO })
    }

    // A shot from the camera is never asked about: it is a photograph, cropped.
    @Test
    fun aCameraShotForAStepIsNeverAnAnimation() {
        val id = open(threeSteps())
        val shot = TestPhotos.jpeg(File(incoming, "shot.jpg"), 1600, 1200).readBytes()

        menu(1, EditorTags.stepPhotoTake(1))
        compose.waitUntil(PATIENCE_MS) { TestCamera.launched(activity) }
        val intent = TestCamera.take(activity)
        TestCamera.write(context, intent, shot)
        compose.runOnUiThread { TestCamera.answer(activity, intent, saved = true) }
        compose.waitUntilExactlyOneExists(hasTestTag(CropTags.FRAME), PATIENCE_MS)
        compose.onNodeWithTag(CropTags.DONE).performClick()

        compose.waitUntil(PATIENCE_MS) { stored(id).stepMedia(1)?.kind == MediaKind.PHOTO }
        assertTrue(images.asked.isEmpty())
        assertTrue(encoded.isEmpty())
    }

    /** Waits until [condition] holds, letting the screen's work run between looks. */
    private fun awaitWork(condition: () -> Boolean) = compose.waitUntil(PATIENCE_MS) {
        compose.waitForIdle()
        condition()
    }

    /** The pictures whose reading the application holds past its activity. */
    private fun readings(): List<Uri> = context.contentResolver.persistedUriPermissions.map { it.uri }

    // The step shows that it is working while the picture picked is told apart — before any crop or conversion.
    @Test
    fun theStepShowsItIsWorkingWhileThePictureIsToldApart() {
        open(threeSteps())
        holdWork()
        images.gate = CompletableDeferred()

        choosePhoto(1, picture("still.png"))

        awaitWork { images.asked.isNotEmpty() && exists(EditorTags.stepPhotoWorking(1)) }
        assertFalse(exists(CropTags.SCREEN) || exists(EditorTags.stepClipConverting(1)))
        images.gate!!.complete(Unit)
        compose.waitUntilExactlyOneExists(hasTestTag(CropTags.SCREEN), PATIENCE_MS)
        assertFalse(exists(EditorTags.stepPhotoWorking(1)))
    }

    // Two picks overlapping while the first is told apart end with the later: a still picture after an animation
    // opens the crop and nothing is converted.
    @Test
    fun aStillPickedAfterAnAnimationEndsInTheCrop() {
        val id = open(threeSteps())
        holdWork()
        images.gate = CompletableDeferred()

        choosePhoto(0, picture("animated.gif"))
        awaitWork { images.asked.size == 1 }
        choosePhoto(0, picture("still.png"))
        awaitWork { images.asked.size == 2 }
        images.gate!!.complete(Unit)

        compose.waitUntilExactlyOneExists(hasTestTag(CropTags.FRAME), PATIENCE_MS)
        compose.onNodeWithTag(CropTags.DONE).performClick()
        compose.waitUntil(PATIENCE_MS) { stored(id).stepMedia(0)?.kind == MediaKind.PHOTO && !exists(EditorTags.stepPhotoWorking(0)) }
        assertTrue(encoded.isEmpty())
        assertTrue(images.converted.isEmpty())
        assertEquals(1, stored(id).media.size)
    }

    // ... and an animation picked after a still picture is attached as a clip, with no crop.
    @Test
    fun anAnimationPickedAfterAStillEndsAsAClip() {
        val id = open(threeSteps())
        holdWork()
        images.gate = CompletableDeferred()

        choosePhoto(0, picture("still.png"))
        awaitWork { images.asked.size == 1 }
        val later = picture("animated.webp")
        choosePhoto(0, later)
        awaitWork { images.asked.size == 2 }
        images.gate!!.complete(Unit)

        compose.waitUntil(PATIENCE_MS) { clipAt(id, 0) != null && !exists(EditorTags.stepPhotoWorking(0)) }
        assertEquals(listOf(Uri.fromFile(later)), images.converted)
        assertFalse(exists(CropTags.SCREEN))
        assertEquals(1, stored(id).media.size)
    }

    // The reading of an animation picked is held past the activity while it is converted, and let go once converted.
    @Test
    fun theReadingIsHeldWhileConvertedAndLetGoAfter() {
        val id = open(threeSteps())
        holdWork()
        gate = CompletableDeferred()
        val file = picture("animated.gif")

        choosePhoto(1, file)

        awaitWork { encoded.isNotEmpty() }
        assertEquals(listOf(Uri.fromFile(file)), readings())
        gate!!.complete(Unit)
        compose.waitUntil(PATIENCE_MS) { clipAt(id, 1) != null }
        compose.waitUntil(PATIENCE_MS) { readings().isEmpty() }
    }

    // ... let go once refused: held while it is told apart, gone once it is refused as too long.
    @Test
    fun theReadingIsLetGoOnceRefused() {
        open(threeSteps())
        holdWork()
        images.gate = CompletableDeferred()
        val file = picture("long.gif")

        choosePhoto(2, file)

        awaitWork { images.asked.isNotEmpty() }
        assertEquals(listOf(Uri.fromFile(file)), readings())
        images.gate!!.complete(Unit)
        awaitNotice(2, "This clip is longer than 15 seconds, so nothing was attached.")
        compose.waitUntil(PATIENCE_MS) { readings().isEmpty() }
    }

    // ... and let go once cancelled.
    @Test
    fun theReadingIsLetGoOnceCancelled() {
        val id = open(threeSteps())
        holdWork()
        gate = CompletableDeferred()
        val file = picture("animated.webp")

        choosePhoto(0, file)

        awaitWork { encoded.isNotEmpty() && exists(EditorTags.stepClipCancel(0)) }
        assertEquals(listOf(Uri.fromFile(file)), readings())
        compose.onNodeWithTag(EditorTags.stepClipCancel(0)).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { !exists(EditorTags.stepClipConverting(0)) }
        compose.waitUntil(PATIENCE_MS) { readings().isEmpty() }
        assertTrue(stored(id).media.isEmpty())
    }

    // An animated GIF over 32 MB is refused as a clip too large, with the clip's words — its first frame is not cropped.
    @Test
    fun aGifTooLargeToReadIsRefusedAsAClipTooLarge() {
        val id = open(threeSteps())
        val huge = File(incoming, "huge.gif")
        huge.outputStream().use { out ->
            out.write(checkNotNull(StepAnimationEditorTest::class.java.getResourceAsStream("/animation/animated.gif")).use { it.readBytes() })
            val block = ByteArray(1024 * 1024)
            repeat(33) { out.write(block) }
        }

        choosePhoto(1, huge)

        awaitNotice(1, "This clip could not be made smaller than 6 MB, so nothing was attached.")
        assertFalse(exists(CropTags.SCREEN))
        assertTrue(encoded.isEmpty())
        assertTrue(stored(id).media.isEmpty())
        compose.waitUntil(PATIENCE_MS) { readings().isEmpty() }
        huge.delete()
    }

    private companion object {
        const val ANIMATION_BYTES = 1500
        const val CLIP_BYTES = 2500
    }
}
