package app.recipefork.app.media

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.file.FileProviderCache
import app.recipefork.app.media.crop.CropTags
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowActivity
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * "Take a photo" through the phone's own camera application (FLOW-026): no camera permission declared or asked for;
 * the camera application handed one new file of the application's cache to write the shot into, and that file alone;
 * the shot cropped next, turned upright by its EXIF, Done handing the framed part over, Retake launching the camera
 * again, back leaving with nothing; a camera cancelled attaching nothing; the shot's file deleted once its crop is
 * done with, or the camera comes back without one; a phone with no camera application told so and offered the
 * gallery; and a gallery photo going through the same crop.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CameraFlowTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val incoming = File(context.filesDir, "camera-flow").apply { mkdirs() }
    private val offers = mutableListOf<ByteArray>()
    private var unavailable = 0

    private val activity: ShadowActivity get() = shadowOf(compose.activity)

    @Before
    fun setUp() {
        FileProviderCache.clear()
    }

    private fun show() {
        compose.setContent {
            MaterialTheme { Host() }
        }
        compose.waitUntilExactlyOneExists(hasTestTag(TAKE), PATIENCE_MS)
    }

    @Composable
    private fun Host() {
        val sources: PhotoSources = rememberPhotoSources(
            key = "test",
            onPhoto = { offer ->
                offers += offer.open().use { it.readBytes() }
                offer.release()
            },
            onUnavailable = { unavailable++ },
        )
        Column {
            Text("Take a photo", Modifier.clickable { sources.takePhoto() }.testTag(TAKE))
            Text("Choose from gallery", Modifier.clickable { sources.choosePhoto() }.testTag(CHOOSE))
        }
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    private fun text(tag: String): String = compose.onNodeWithTag(tag).fetchSemanticsNode()
        .config[SemanticsProperties.Text].joinToString("") { it.text }

    /** "Take a photo": the camera application is launched; its intent is given back. */
    private fun take(): Intent {
        compose.onNodeWithTag(TAKE).performClick()
        compose.waitUntil(PATIENCE_MS) { TestCamera.launched(activity) }
        return TestCamera.take(activity)
    }

    /** The camera application writes [shot] and comes back with it saved. */
    private fun shoot(intent: Intent, shot: ByteArray = picture()) {
        TestCamera.write(context, intent, shot)
        compose.runOnUiThread { TestCamera.answer(activity, intent, saved = true) }
    }

    /** Waits for the crop to show its picture under the frame, ready for Done. */
    private fun awaitCrop() {
        compose.waitUntilExactlyOneExists(hasTestTag(CropTags.FRAME), PATIENCE_MS)
        compose.waitUntilExactlyOneExists(hasTestTag(CropTags.DONE), PATIENCE_MS)
    }

    /** Done pressed in the crop: one picture is handed over, and the crop leaves. */
    private fun done(): Bitmap {
        val before = offers.size
        compose.onNodeWithTag(CropTags.DONE).performClick()
        compose.waitUntil(PATIENCE_MS) { offers.size == before + 1 }
        compose.waitUntil(PATIENCE_MS) { !exists(CropTags.SCREEN) }
        return offers.last().let { BitmapFactory.decodeByteArray(it, 0, it.size) }
    }

    /** Picks [file] in the system photo picker. */
    private fun choose(file: File) {
        compose.onNodeWithTag(CHOOSE).performClick()
        compose.waitUntil(PATIENCE_MS) { activity.peekNextStartedActivityForResult() != null }
        val intent = activity.nextStartedActivityForResult.intent
        assertTrue(intent.action, intent.action == MediaStore.ACTION_PICK_IMAGES || intent.action == Intent.ACTION_OPEN_DOCUMENT)
        compose.runOnUiThread { activity.receiveResult(intent, Activity.RESULT_OK, Intent().setData(Uri.fromFile(file))) }
    }

    private fun isRed(color: Int) = Color.red(color) > 150 && Color.green(color) < 100 && Color.blue(color) < 100
    private fun isBlue(color: Int) = Color.blue(color) > 150 && Color.red(color) < 100 && Color.green(color) < 100

    /** Upright, a test photo's top is red and its bottom blue. */
    private fun assertUpright(bitmap: Bitmap) {
        assertTrue("top", isRed(bitmap.getPixel(bitmap.width / 2, bitmap.height / 8)))
        assertTrue("bottom", isBlue(bitmap.getPixel(bitmap.width / 2, bitmap.height * 7 / 8)))
    }

    /** An upright test photo of [width] by [height], as a JPEG. */
    private fun picture(width: Int = 800, height: Int = 600): ByteArray =
        TestPhotos.jpeg(File(incoming, "shot.jpg"), width, height).readBytes()

    /**
     * A shot as a phone's camera writes one taken held upright: the pixels of a [width] by [height] upright picture
     * stored a quarter turned back, and EXIF saying to turn them a quarter clockwise; tagged with place and device too.
     */
    private fun sidewaysShot(width: Int, height: Int): ByteArray {
        val upright = BitmapFactory.decodeFile(TestPhotos.jpeg(File(incoming, "upright.jpg"), width, height).path)
        val stored = Bitmap.createBitmap(upright, 0, 0, upright.width, upright.height, Matrix().apply { postRotate(-90f) }, true)
        val file = File(incoming, "sideways.jpg")
        file.outputStream().use { stored.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        return TestPhotos.tag(file, ExifInterface.ORIENTATION_ROTATE_90).readBytes()
    }

    /** A JPEG of [width] by [height], its left half red and its right half blue. */
    private fun leftAndRight(width: Int, height: Int): File {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.RED)
            drawRect(width / 2f, 0f, width.toFloat(), height.toFloat(), Paint().apply { color = Color.BLUE })
        }
        val file = File(incoming, "left-and-right.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        return file
    }

    /** [file]'s picture written as a JPEG turned by [degrees] clockwise. */
    private fun turned(file: File, degrees: Float): ByteArray {
        val bitmap = BitmapFactory.decodeFile(file.path)
        val turned = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(degrees) }, true)
        return ByteArrayOutputStream().also { turned.compress(Bitmap.CompressFormat.JPEG, 95, it) }.toByteArray()
    }

    /** The system back, pressed while the crop is in front: it goes to its own window, the topmost. */
    private fun pressBack() {
        val global = Class.forName("android.view.WindowManagerGlobal").getMethod("getInstance").invoke(null)
        @Suppress("UNCHECKED_CAST")
        val views = global.javaClass.getDeclaredField("mViews").apply { isAccessible = true }.get(global) as List<android.view.View>
        val window = views.last()
        compose.runOnUiThread {
            window.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_BACK))
            window.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_BACK))
        }
    }

    // FLOW-026 (4): no camera permission is declared — declared and not granted, the platform would refuse the camera
    // application — none to read the phone's storage or media, and nothing is asked when a photo is taken.
    @Test
    fun noPermissionIsDeclaredOrAskedFor() {
        val declared = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions.orEmpty().toSet()
        listOf(
            Manifest.permission.CAMERA,
            Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.ACCESS_MEDIA_LOCATION,
            Manifest.permission.RECORD_AUDIO,
        ).forEach { assertFalse(it, it in declared) }
        show()

        take()
        compose.waitForIdle()

        assertNull(activity.lastRequestedPermission)
    }

    // FLOW-026 (1): "Take a photo" launches the phone's camera application into a new, empty file of the application's
    // cache, through a content address granted for that capture alone.
    @Test
    fun takeAPhotoLaunchesTheCameraApplicationIntoAFileOfItsOwn() {
        show()

        val intent = take()

        val output = TestCamera.output(intent)
        assertEquals("content", output.scheme)
        assertEquals(context.packageName + ".capture", output.authority)
        val file = TestCamera.file(context, intent)
        assertTrue(file.isFile && file.length() == 0L)
        assertEquals(Captures.directory(context).canonicalFile, file.canonicalFile.parentFile)
        assertTrue("write granted", intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0)
        assertNoWiderGrant(intent)
        assertEquals(1, intent.clipData!!.itemCount)
        assertEquals("granted for the shot's address alone", output, intent.clipData!!.getItemAt(0).uri)
        // The provider shares the shots' directory and no other file of the application.
        val book = File(context.filesDir, "book.db").apply { writeText("x") }
        val shared = runCatching { FileProvider.getUriForFile(context, Captures.authority(context), book) }
        assertTrue("nothing outside the shots' directory is shared", shared.isFailure)
    }

    // The intent the application builds itself — before the platform adds its own grant of EXTRA_OUTPUT — grants
    // writing and reading the shot's address, that address alone, and nothing that outlives the capture.
    @Test
    fun theCaptureIntentGrantsTheShotsAddressAlone() {
        val output = Uri.parse("content://${context.packageName}.capture/capture/shot-1.jpg")

        val intent = TakeOnePicture().createIntent(context, output)

        assertEquals(MediaStore.ACTION_IMAGE_CAPTURE, intent.action)
        assertTrue("write granted", intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0)
        assertTrue("read granted", intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertNoWiderGrant(intent)
        assertEquals(1, intent.clipData!!.itemCount)
        assertEquals(output, intent.clipData!!.getItemAt(0).uri)
    }

    /** No grant of every address under the shot's, nor one that outlives the capture. */
    private fun assertNoWiderGrant(intent: Intent) {
        assertEquals("no prefix grant", 0, intent.flags and Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
        assertEquals("no persistable grant", 0, intent.flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
    }

    // FLOW-026 (3): the shot opens the crop — Retake beside Cancel (T-147) — and Done hands over the framed part, 4:3
    // wide and the right way up as its EXIF says; the shot's file is deleted once the cut has been read from it.
    @Test
    fun theShotOpensTheCropAndDoneHandsTheFrameOverUpright() {
        show()
        val intent = take()
        val file = TestCamera.file(context, intent)

        shoot(intent, sidewaysShot(600, 800))

        awaitCrop()
        assertTrue(offers.isEmpty())
        assertTrue("a shot from the camera can be retaken or cancelled", exists(CropTags.RETAKE) && exists(CropTags.CANCEL))
        val handed = done()
        assertEquals(600, handed.width)
        assertEquals(450, handed.height)
        assertTrue(isRed(handed.getPixel(300, 20)) && isBlue(handed.getPixel(300, 430)))
        assertEquals(1, offers.size)
        assertFalse(file.exists())
        assertEquals(emptyList<String>(), TestCamera.left(context))
    }

    // FLOW-026 (3): Retake launches the camera application again, into a new file, the first one deleted; the next
    // shot is cropped and handed over.
    @Test
    fun retakeLaunchesTheCameraAgain() {
        show()
        val first = take()
        shoot(first)
        awaitCrop()

        compose.onNodeWithTag(CropTags.RETAKE).performClick()

        compose.waitUntil(PATIENCE_MS) { TestCamera.launched(activity) }
        val second = TestCamera.take(activity)
        compose.waitUntil(PATIENCE_MS) { !exists(CropTags.SCREEN) }
        assertTrue(offers.isEmpty())
        assertNotEquals(TestCamera.output(first), TestCamera.output(second))
        assertFalse(TestCamera.file(context, first).exists())
        shoot(second)
        awaitCrop()
        done()
        assertEquals(1, offers.size)
        assertEquals(emptyList<String>(), TestCamera.left(context))
    }

    // A camera cancelled hands nothing over, opens no crop and leaves no file.
    @Test
    fun aCancelledCameraAttachesNothing() {
        show()
        val intent = take()

        compose.runOnUiThread { TestCamera.answer(activity, intent, saved = false) }
        compose.waitForIdle()

        assertFalse(exists(CropTags.SCREEN))
        assertTrue(offers.isEmpty())
        assertEquals(emptyList<String>(), TestCamera.left(context))
    }

    // A camera that says it saved a shot but wrote nothing is taken as cancelled.
    @Test
    fun aCameraThatWroteNothingAttachesNothing() {
        show()
        val intent = take()

        compose.runOnUiThread { TestCamera.answer(activity, intent, saved = true) }
        compose.waitForIdle()

        assertFalse(exists(CropTags.SCREEN))
        assertTrue(offers.isEmpty())
        assertEquals(emptyList<String>(), TestCamera.left(context))
    }

    // Back in the crop of a shot leaves it with nothing handed over, and the shot's file is deleted.
    @Test
    fun backInTheCropLeavesWithNothing() {
        show()
        val intent = take()
        shoot(intent)
        awaitCrop()

        pressBack()

        compose.waitUntil(PATIENCE_MS) { !exists(CropTags.SCREEN) }
        compose.waitForIdle()
        assertTrue(offers.isEmpty())
        assertEquals(emptyList<String>(), TestCamera.left(context))
        assertTrue(exists(TAKE))
    }

    // MEDIA-008: a shot that is no picture is handed over as it is, for processing to refuse and say why; its file is
    // deleted once processing lets it go.
    @Test
    fun aShotThatIsNoPictureIsHandedOverForProcessingToRefuse() {
        show()
        val intent = take()
        val bytes = "not a photo at all".toByteArray()

        shoot(intent, bytes)

        compose.waitUntil(PATIENCE_MS) { !exists(CropTags.SCREEN) && offers.size == 1 }
        assertArrayEquals(bytes, offers.single())
        assertEquals(emptyList<String>(), TestCamera.left(context))
    }

    // A phone with no camera application says so — no file left — and offers the gallery, which opens on a tap.
    @Test
    fun aPhoneWithoutACameraApplicationSaysSoAndOffersTheGallery() {
        shadowOf(compose.activity.application).checkActivities(true)
        show()

        compose.onNodeWithTag(TAKE).performClick()

        compose.waitUntilExactlyOneExists(hasTestTag(PhotoSourceTags.CAMERA_UNAVAILABLE_TEXT), PATIENCE_MS)
        assertEquals(
            "No camera app was found on this phone, so no photo can be taken here. You can choose one from the gallery instead.",
            text(PhotoSourceTags.CAMERA_UNAVAILABLE_TEXT),
        )
        assertEquals(emptyList<String>(), TestCamera.left(context))
        assertFalse(exists(CropTags.SCREEN))
        shadowOf(compose.activity.application).checkActivities(false)

        compose.onNodeWithTag(PhotoSourceTags.CAMERA_UNAVAILABLE_GALLERY).performClick()

        compose.waitUntil(PATIENCE_MS) { activity.peekNextStartedActivityForResult() != null }
        val picker = activity.nextStartedActivityForResult.intent
        assertTrue(picker.action, picker.action == MediaStore.ACTION_PICK_IMAGES || picker.action == Intent.ACTION_OPEN_DOCUMENT)
        compose.waitUntil(PATIENCE_MS) { !exists(PhotoSourceTags.CAMERA_UNAVAILABLE) }
    }

    // Told there is no camera application, "Not now" closes the message with nothing handed over.
    @Test
    fun theNoCameraMessageCanBeDismissed() {
        shadowOf(compose.activity.application).checkActivities(true)
        show()
        compose.onNodeWithTag(TAKE).performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(PhotoSourceTags.CAMERA_UNAVAILABLE), PATIENCE_MS)

        compose.onNodeWithTag(PhotoSourceTags.CAMERA_UNAVAILABLE_DISMISS).performClick()

        compose.waitUntil(PATIENCE_MS) { !exists(PhotoSourceTags.CAMERA_UNAVAILABLE) }
        assertTrue(offers.isEmpty())
        assertEquals(0, unavailable)
    }

    // FLOW-026: a photo from the gallery goes through the same crop — Cancel in place of Retake — before it is handed
    // over, turned upright by its EXIF; the picked file itself is not touched.
    @Test
    fun aGalleryPhotoGoesThroughTheCropUpright() {
        show()
        val upright = TestPhotos.jpeg(File(incoming, "picked-upright.jpg"), 800, 600)
        val picked = File(incoming, "picked.jpg").apply { writeBytes(turned(upright, -90f)) }
        TestPhotos.tag(picked, ExifInterface.ORIENTATION_ROTATE_90)

        choose(picked)

        awaitCrop()
        assertTrue(offers.isEmpty())
        assertTrue(exists(CropTags.CANCEL) && !exists(CropTags.RETAKE))
        val handed = done()
        assertEquals(800, handed.width)
        assertEquals(600, handed.height)
        assertUpright(handed)
        assertTrue(picked.exists())
    }

    // T-147: the crop after the camera offers Cancel too; it hands nothing over and leaves no file of the shot on disk.
    @Test
    fun cancelInTheCamerasCropHandsNothingOverAndLeavesNoFile() {
        show()
        val intent = take()
        val file = TestCamera.file(context, intent)
        shoot(intent)
        awaitCrop()
        assertTrue(file.exists())

        compose.onNodeWithTag(CropTags.CANCEL).assertHeightIsAtLeast(48.dp).performClick()

        compose.waitUntil(PATIENCE_MS) { !exists(CropTags.SCREEN) }
        compose.waitForIdle()
        assertTrue(offers.isEmpty())
        assertFalse(file.exists())
        assertEquals(emptyList<String>(), TestCamera.left(context))
    }

    // Cancel in the gallery's crop hands nothing over.
    @Test
    fun cancelInTheGallerysCropHandsNothingOver() {
        show()
        choose(TestPhotos.jpeg(File(incoming, "picked.jpg"), 800, 600))
        awaitCrop()

        compose.onNodeWithTag(CropTags.CANCEL).performClick()

        compose.waitUntil(PATIENCE_MS) { !exists(CropTags.SCREEN) }
        compose.waitForIdle()
        assertTrue(offers.isEmpty())
    }

    // MEDIA-008: a file from the gallery that is no picture is handed over as it is, for processing to refuse and say why.
    @Test
    fun aFileThatIsNoPictureIsHandedOverForProcessingToRefuse() {
        show()
        val file = File(incoming, "photo.jpg").apply { writeText("not a photo at all") }

        choose(file)

        compose.waitUntil(PATIENCE_MS) { !exists(CropTags.SCREEN) && offers.size == 1 }
        assertArrayEquals(file.readBytes(), offers.single())
    }

    // The frame is moved by dragging the photograph under it, and never leaves it: dragged far right, the frame shows
    // the photograph's left edge.
    @Test
    fun aDragMovesThePhotographUnderTheFrame() {
        show()
        choose(leftAndRight(3200, 1800))
        awaitCrop()

        compose.onNodeWithTag(CropTags.FRAME).performTouchInput { swipeRight(startX = left + 4f, endX = right - 4f) }
        compose.onNodeWithTag(CropTags.FRAME).performTouchInput { swipeRight(startX = left + 4f, endX = right - 4f) }

        val handed = done()
        assertEquals(2400, handed.width)
        assertEquals(1800, handed.height)
        // At the widest, centred, the halves would meet in the middle; at the left edge, two thirds are red.
        assertTrue(isRed(handed.getPixel(handed.width * 6 / 10, handed.height / 2)))
        assertTrue(isBlue(handed.getPixel(handed.width * 9 / 10, handed.height / 2)))
    }

    // The frame is zoomed by pinching, down to no narrower than 1600 of the photograph's pixels.
    @Test
    fun aPinchZoomsDownToNoNarrowerThan1600() {
        show()
        choose(leftAndRight(3200, 1800))
        awaitCrop()

        repeat(3) {
            compose.onNodeWithTag(CropTags.FRAME).performTouchInput {
                pinch(center - Offset(10f, 0f), centerLeft + Offset(4f, 0f), center + Offset(10f, 0f), centerRight - Offset(4f, 0f))
            }
        }

        val handed = done()
        assertEquals(1600, handed.width)
        assertEquals(1200, handed.height)
    }

    private companion object {
        const val TAKE = "test-take"
        const val CHOOSE = "test-choose"
        const val PATIENCE_MS = 60_000L
    }
}
