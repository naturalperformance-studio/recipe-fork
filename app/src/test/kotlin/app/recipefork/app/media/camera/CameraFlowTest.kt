package app.recipefork.app.media.camera

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
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipeRight
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.media.PhotoSources
import app.recipefork.app.media.TestPhotos
import app.recipefork.app.media.crop.CropTags
import app.recipefork.app.media.rememberPhotoSources
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowActivity
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * "Take a photo" through the application's own camera (FLOW-026): the camera permission asked for on the first one
 * and never earlier, and asked for once however often the button is pressed meanwhile; refused, explained, with the
 * settings a tap away and the gallery still working; refused for good, explained straight away; granted, a viewfinder
 * filling the screen whose shutter takes the whole frame and opens the crop; the crop's frame moved and zoomed, Done
 * handing the framed part over the right way up, Retake going back to the camera, back leaving with nothing; a gallery
 * photo going through the same crop; closed or backed out of, nothing; a phone without a back camera told so; and the
 * camera released whenever the screen leaves.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CameraFlowTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val incoming = File(context.cacheDir, "camera-flow").apply { mkdirs() }
    private val shot = TestPhotos.jpeg(File(incoming, "shot.jpg"), 800, 600).readBytes()
    private val camera = FakeCamera(picture = { shot })
    private val offers = mutableListOf<ByteArray>()
    private var unavailable = 0
    private var shown by mutableStateOf(true)

    private val activity: ShadowActivity get() = shadowOf(compose.activity)

    private fun show() {
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalCameraAccess provides camera) { if (shown) Host() }
            }
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

    private fun take() {
        compose.onNodeWithTag(TAKE).performClick()
        compose.waitForIdle()
    }

    private fun grant() = shadowOf(compose.activity.application).grantPermissions(Manifest.permission.CAMERA)

    private fun requested(): String? = activity.lastRequestedPermission?.requestedPermissions?.single()

    /** The author answers the platform's request for the camera. */
    private fun answer(granted: Boolean) {
        val request = activity.lastRequestedPermission!!
        if (granted) grant()
        compose.runOnUiThread {
            // The platform clears this as it hands an answer back; delivered by hand, the next request would be
            // answered at once with nothing.
            Activity::class.java.getDeclaredField("mHasCurrentPermissionsRequest").apply { isAccessible = true }
                .setBoolean(compose.activity, false)
            @Suppress("DEPRECATION")
            compose.activity.onRequestPermissionsResult(
                request.requestCode,
                request.requestedPermissions,
                intArrayOf(if (granted) PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED),
            )
        }
        compose.waitForIdle()
    }

    private fun awaitCamera() {
        compose.waitUntilExactlyOneExists(hasTestTag(CameraTags.VIEWFINDER), PATIENCE_MS)
        compose.waitUntil(PATIENCE_MS) { camera.isOpen }
    }

    private fun awaitGone() = compose.waitUntil(PATIENCE_MS) { !exists(CameraTags.SCREEN) }

    /** Waits for the crop to show its picture under the frame, ready for Done. */
    private fun awaitCrop() {
        compose.waitUntilExactlyOneExists(hasTestTag(CropTags.FRAME), PATIENCE_MS)
        compose.waitUntilExactlyOneExists(hasTestTag(CropTags.DONE), PATIENCE_MS)
    }

    /** The shutter pressed with the camera open: the crop opens on the picture taken. */
    private fun shoot() {
        compose.onNodeWithTag(CameraTags.SHUTTER).performClick()
        awaitCrop()
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

    /** [bitmap] written as a JPEG turned by [degrees] clockwise. */
    private fun turned(file: File, degrees: Float): ByteArray {
        val bitmap = BitmapFactory.decodeFile(file.path)
        val turned = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(degrees) }, true)
        return ByteArrayOutputStream().also { turned.compress(Bitmap.CompressFormat.JPEG, 95, it) }.toByteArray()
    }

    /** A JPEG of [width] by [height], its left half red and its right half blue. */
    private fun leftAndRight(width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.RED)
            drawRect(width / 2f, 0f, width.toFloat(), height.toFloat(), Paint().apply { color = Color.BLUE })
        }
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }.toByteArray()
    }

    /** The system back, pressed while the camera or the crop is in front: it goes to its own window, the topmost. */
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

    // FLOW-026 (4): the camera is the one permission added; nothing is asked as the screen opens.
    @Test
    fun nothingIsAskedBeforeTheFirstPhoto() {
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS or PackageManager.GET_CONFIGURATIONS)
        val declared = info.requestedPermissions.orEmpty().toSet()
        assertTrue(Manifest.permission.CAMERA in declared)
        listOf(
            Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.ACCESS_MEDIA_LOCATION,
            Manifest.permission.RECORD_AUDIO,
        ).forEach { assertFalse(it, it in declared) }

        show()
        compose.waitForIdle()

        assertNull(activity.lastRequestedPermission)
        assertEquals(0, camera.opened)
    }

    // FLOW-026 (4): the first "Take a photo" asks for the camera; granted, the camera opens.
    @Test
    fun theFirstPhotoAsksForTheCamera() {
        show()

        take()

        assertEquals(Manifest.permission.CAMERA, requested())
        assertEquals(0, camera.opened)
        answer(granted = true)
        awaitCamera()
    }

    // Granted already, the camera opens with nothing asked.
    @Test
    fun aGrantedCameraOpensWithoutAsking() {
        grant()
        show()

        take()

        awaitCamera()
        assertNull(activity.lastRequestedPermission)
    }

    // FLOW-026 (3): the viewfinder fills the screen — no frame masked out of it — with the way out at the top and the
    // shutter at the bottom over it.
    @Test
    fun theViewfinderFillsTheScreen() {
        grant()
        show()
        val screen = compose.activity.resources.displayMetrics

        take()
        awaitCamera()

        val viewfinder = compose.onNodeWithTag(CameraTags.VIEWFINDER).fetchSemanticsNode().boundsInWindow
        assertEquals(screen.widthPixels.toFloat(), viewfinder.width, 1f)
        assertTrue("${viewfinder.height} of ${screen.heightPixels}", viewfinder.height >= screen.heightPixels * 0.9f)
        val close = compose.onNodeWithTag(CameraTags.CLOSE).fetchSemanticsNode().boundsInWindow
        val shutter = compose.onNodeWithTag(CameraTags.SHUTTER).fetchSemanticsNode().boundsInWindow
        assertTrue("close over the top", close.top >= viewfinder.top && close.bottom < viewfinder.center.y)
        assertTrue("shutter over the bottom", shutter.top > viewfinder.center.y && shutter.bottom <= viewfinder.bottom)
    }

    // FLOW-026 (3): the shutter takes the whole frame and opens the crop, releasing the camera; Done hands over the
    // framed part — 4:3, wider than tall, the right way up — once, and the crop leaves.
    @Test
    fun theShutterOpensTheCropAndDoneHandsTheFrameOver() {
        grant()
        show()
        take()
        awaitCamera()

        shoot()

        assertTrue(offers.isEmpty())
        assertFalse(exists(CameraTags.SCREEN))
        compose.waitUntil(PATIENCE_MS) { !camera.isOpen }
        assertTrue("a shot from the camera can be retaken", exists(CropTags.RETAKE) && !exists(CropTags.CANCEL))
        val handed = done()
        assertEquals(800, handed.width)
        assertEquals(600, handed.height)
        assertUpright(handed)
        assertEquals(1, offers.size)
        assertEquals(1, camera.taken)
        assertFalse(exists(CameraTags.SCREEN))
    }

    // FLOW-026 (3): Retake goes back to the camera with nothing handed over; the next shot is cropped and handed over.
    @Test
    fun retakeGoesBackToTheCamera() {
        grant()
        show()
        take()
        awaitCamera()
        shoot()

        compose.onNodeWithTag(CropTags.RETAKE).performClick()

        compose.waitUntil(PATIENCE_MS) { !exists(CropTags.SCREEN) }
        awaitCamera()
        assertEquals(2, camera.opened)
        assertTrue(offers.isEmpty())
        shoot()
        done()
        assertEquals(1, offers.size)
        assertEquals(2, camera.taken)
    }

    // Back in the crop leaves it with nothing handed over.
    @Test
    fun backInTheCropLeavesWithNothing() {
        grant()
        show()
        take()
        awaitCamera()
        shoot()

        pressBack()

        compose.waitUntil(PATIENCE_MS) { !exists(CropTags.SCREEN) }
        compose.waitForIdle()
        assertTrue(offers.isEmpty())
        assertFalse(exists(CameraTags.SCREEN))
        assertFalse(camera.isOpen)
        assertTrue(exists(TAKE))
    }

    // The frame is moved by dragging the photograph under it, and never leaves it: dragged far right, the frame shows
    // the photograph's left edge.
    @Test
    fun aDragMovesThePhotographUnderTheFrame() {
        camera.picture = { leftAndRight(3200, 1800) }
        grant()
        show()
        take()
        awaitCamera()
        shoot()

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
        camera.picture = { leftAndRight(3200, 1800) }
        grant()
        show()
        take()
        awaitCamera()
        shoot()

        repeat(3) {
            compose.onNodeWithTag(CropTags.FRAME).performTouchInput {
                pinch(center - Offset(10f, 0f), centerLeft + Offset(4f, 0f), center + Offset(10f, 0f), centerRight - Offset(4f, 0f))
            }
        }

        val handed = done()
        assertEquals(1600, handed.width)
        assertEquals(1200, handed.height)
    }

    // A shot taken with the phone upside down — the camera saying it turns half a turn — is handed over upright.
    @Test
    fun aShotTakenUpsideDownIsHandedOverUpright() {
        camera.picture = { turned(File(incoming, "shot.jpg"), 180f) }
        camera.rotationDegrees = 180
        grant()
        show()
        take()
        awaitCamera()
        shoot()

        assertUpright(done())
    }

    // A shot taken with the phone sideways — the sensor's picture a quarter turned — is handed over upright, 4:3 wide.
    @Test
    fun aShotTakenSidewaysIsHandedOverUpright() {
        val tall = TestPhotos.jpeg(File(incoming, "tall.jpg"), 600, 800)
        camera.picture = { turned(tall, -90f) }
        camera.rotationDegrees = 90
        grant()
        show()
        take()
        awaitCamera()
        shoot()

        val handed = done()
        assertEquals(600, handed.width)
        assertEquals(450, handed.height)
        assertTrue(isRed(handed.getPixel(300, 20)) && isBlue(handed.getPixel(300, 430)))
    }

    // FLOW-026: a photo from the gallery goes through the same crop — Cancel in place of Retake — before it is handed
    // over, turned upright by its EXIF.
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
        assertEquals(0, camera.opened)
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

        // Read through the screen, which lets the crop come and go.
        compose.waitUntil(PATIENCE_MS) { !exists(CropTags.SCREEN) && offers.size == 1 }
        assertArrayEquals(file.readBytes(), offers.single())
        compose.waitUntil(PATIENCE_MS) { !exists(CropTags.SCREEN) }
    }

    // A second "Take a photo" while the platform's request is up is ignored — it is no refusal; the answer to the one
    // request then opens the camera.
    @Test
    fun aSecondTapWhileAskingIsIgnored() {
        show()
        take()
        val asked = activity.lastRequestedPermission
        assertNotNull(asked)

        take()
        take()

        assertSame("asked once", asked, activity.lastRequestedPermission)
        assertFalse(exists(CameraTags.PERMISSION))
        answer(granted = true)
        awaitCamera()
        assertFalse(exists(CameraTags.PERMISSION))
    }

    // A shot that fails says so and leaves the camera open to try again; nothing is handed over.
    @Test
    fun aFailedShotSaysSoAndStaysOpen() {
        camera.picture = { null }
        grant()
        show()
        take()
        awaitCamera()

        compose.onNodeWithTag(CameraTags.SHUTTER).performClick()

        compose.waitUntil(PATIENCE_MS) { exists(CameraTags.MESSAGE) && text(CameraTags.MESSAGE) == "The photo could not be taken. Try again." }
        assertTrue(offers.isEmpty())
        assertTrue(camera.isOpen && exists(CameraTags.VIEWFINDER))
    }

    // Close leaves the camera with nothing attached, and the camera is released.
    @Test
    fun closeLeavesWithNothingAndReleasesTheCamera() {
        grant()
        show()
        take()
        awaitCamera()

        compose.onNodeWithTag(CameraTags.CLOSE).performClick()

        awaitGone()
        compose.waitUntil(PATIENCE_MS) { !camera.isOpen }
        assertEquals(0, camera.taken)
        assertTrue(offers.isEmpty())
    }

    // Back leaves the camera with nothing attached, and the camera is released.
    @Test
    fun backLeavesWithNothingAndReleasesTheCamera() {
        grant()
        show()
        take()
        awaitCamera()

        pressBack()

        awaitGone()
        compose.waitUntil(PATIENCE_MS) { !camera.isOpen }
        assertTrue(offers.isEmpty())
        assertTrue(exists(TAKE))
    }

    // The camera is released when its screen leaves for any reason — here the place it was opened from goes away.
    @Test
    fun theCameraIsReleasedWhenTheScreenLeaves() {
        grant()
        show()
        take()
        awaitCamera()

        compose.runOnIdle { shown = false }

        awaitGone()
        compose.waitUntil(PATIENCE_MS) { !camera.isOpen }
        assertEquals(1, camera.closed)
    }

    // FLOW-026 (4): refused, the explanation says the camera needs the permission and offers the application's settings.
    @Test
    fun aRefusalIsExplainedWithTheSettingsATapAway() {
        shadowOf(context.packageManager).setShouldShowRequestPermissionRationale(Manifest.permission.CAMERA, true)
        show()
        take()

        answer(granted = false)

        compose.waitUntilExactlyOneExists(hasTestTag(CameraTags.PERMISSION_TEXT), PATIENCE_MS)
        assertEquals(
            "To take a photo here, Recipe Fork needs permission to use the camera. You can allow it in the app's settings, or choose a photo from the gallery instead.",
            text(CameraTags.PERMISSION_TEXT),
        )
        assertEquals(0, camera.opened)
        assertFalse(exists(CameraTags.SCREEN))

        compose.onNodeWithTag(CameraTags.PERMISSION_SETTINGS).performClick()

        val settings = activity.nextStartedActivity
        assertNotNull(settings)
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, settings.action)
        assertEquals(Uri.fromParts("package", context.packageName, null), settings.data)
        compose.waitUntil(PATIENCE_MS) { !exists(CameraTags.PERMISSION) }
        assertTrue(offers.isEmpty())
    }

    // FLOW-026 (4): refused, "Choose from gallery" still hands a picture over, with no permission asked for it.
    @Test
    fun theGalleryStillWorksAfterARefusal() {
        show()
        take()
        answer(granted = false)
        compose.waitUntilExactlyOneExists(hasTestTag(CameraTags.PERMISSION), PATIENCE_MS)
        compose.onNodeWithTag(CameraTags.PERMISSION_DISMISS).performClick()
        compose.waitUntil(PATIENCE_MS) { !exists(CameraTags.PERMISSION) }
        val asked = activity.lastRequestedPermission

        choose(TestPhotos.jpeg(File(incoming, "picked.jpg"), 800, 600))

        awaitCrop()
        val handed = done()
        assertEquals(800, handed.width)
        assertSame(asked, activity.lastRequestedPermission)
        assertEquals(0, camera.opened)
    }

    // FLOW-026 (4): refused for good — the platform would not ask again — the next "Take a photo" goes straight to the
    // explanation and its settings, asking nothing.
    @Test
    fun aRefusalForGoodGoesStraightToTheExplanation() {
        show()
        take()
        answer(granted = false)
        compose.waitUntilExactlyOneExists(hasTestTag(CameraTags.PERMISSION), PATIENCE_MS)
        compose.onNodeWithTag(CameraTags.PERMISSION_DISMISS).performClick()
        compose.waitUntil(PATIENCE_MS) { !exists(CameraTags.PERMISSION) }
        val asked = activity.lastRequestedPermission
        shadowOf(context.packageManager).setShouldShowRequestPermissionRationale(Manifest.permission.CAMERA, false)

        take()

        compose.waitUntilExactlyOneExists(hasTestTag(CameraTags.PERMISSION), PATIENCE_MS)
        assertSame("nothing asked again", asked, activity.lastRequestedPermission)
        compose.onNodeWithTag(CameraTags.PERMISSION_SETTINGS).performClick()
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, activity.nextStartedActivity.action)
        assertEquals(0, camera.opened)
    }

    // Refused once but not for good, the next "Take a photo" asks again.
    @Test
    fun aRefusalNotForGoodAsksAgain() {
        shadowOf(context.packageManager).setShouldShowRequestPermissionRationale(Manifest.permission.CAMERA, true)
        show()
        take()
        answer(granted = false)
        compose.onNodeWithTag(CameraTags.PERMISSION_DISMISS).performClick()
        compose.waitUntil(PATIENCE_MS) { !exists(CameraTags.PERMISSION) }
        val asked = activity.lastRequestedPermission

        take()

        compose.waitUntil(PATIENCE_MS) { activity.lastRequestedPermission !== asked }
        answer(granted = true)
        awaitCamera()
    }

    // A phone with no back camera says so and hands nothing over.
    @Test
    fun aPhoneWithoutABackCameraSaysSo() {
        camera.present = false
        grant()
        show()

        take()

        compose.waitUntil(PATIENCE_MS) { exists(CameraTags.MESSAGE) }
        assertEquals(
            "This phone has no back camera, so no photo can be taken here. You can choose one from the gallery instead.",
            text(CameraTags.MESSAGE),
        )
        assertFalse(exists(CameraTags.VIEWFINDER) || exists(CameraTags.SHUTTER))
        compose.onNodeWithTag(CameraTags.CLOSE).performClick()
        awaitGone()
        assertTrue(offers.isEmpty())
        assertEquals(0, camera.opened)
    }

    private companion object {
        const val TAKE = "test-take"
        const val CHOOSE = "test-choose"
        const val PATIENCE_MS = 60_000L
    }
}
