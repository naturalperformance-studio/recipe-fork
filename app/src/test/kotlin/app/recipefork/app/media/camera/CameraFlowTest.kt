package app.recipefork.app.media.camera

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.media.PhotoSources
import app.recipefork.app.media.TestPhotos
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
import java.io.File

/**
 * "Take a photo" through the application's own camera (FLOW-026): the camera permission asked for on the first one
 * and never earlier; refused, explained, with the settings a tap away and the gallery still working; refused for good,
 * explained straight away; granted, a 4:3 landscape viewfinder whose shutter hands the picture over; closed or backed
 * out of, nothing; a phone without a back camera told so; and the camera released whenever the screen leaves.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CameraFlowTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val incoming = File(context.cacheDir, "camera-flow").apply { mkdirs() }
    private val shot = TestPhotos.jpeg(File(incoming, "shot.jpg"), 400, 300).readBytes()
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

    /** The system back, pressed while the camera is in front: it goes to the camera's own window, the topmost. */
    private fun pressBackOnTheCamera() {
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

    // FLOW-026 (3): the phone held upright, the viewfinder is the recipe's frame: 4:3, wider than tall.
    @Test
    fun theViewfinderIsFourByThreeLandscapeWithThePhoneUpright() {
        grant()
        show()
        val screen = compose.activity.resources.displayMetrics
        assertTrue("the phone is held upright", screen.heightPixels > screen.widthPixels)

        take()
        awaitCamera()

        val size = compose.onNodeWithTag(CameraTags.VIEWFINDER).fetchSemanticsNode().size
        assertTrue("${size.width} x ${size.height}", size.width > size.height)
        assertEquals(4.0 / 3.0, size.width.toDouble() / size.height, 0.01)
    }

    // FLOW-026 (3): the shutter hands over the picture the camera took — the viewfinder's frame — once, closes the
    // camera screen and releases the camera.
    @Test
    fun theShutterHandsThePictureOverAndReleasesTheCamera() {
        grant()
        show()
        take()
        awaitCamera()

        compose.onNodeWithTag(CameraTags.SHUTTER).performClick()

        compose.waitUntil(PATIENCE_MS) { offers.size == 1 }
        assertArrayEquals(shot, offers.single())
        awaitGone()
        compose.waitUntil(PATIENCE_MS) { !camera.isOpen }
        assertEquals(1, camera.taken)
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

        pressBackOnTheCamera()

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

        compose.onNodeWithTag(CHOOSE).performClick()

        compose.waitUntil(PATIENCE_MS) { activity.peekNextStartedActivityForResult() != null }
        val intent = activity.nextStartedActivityForResult.intent
        assertTrue(intent.action, intent.action == MediaStore.ACTION_PICK_IMAGES || intent.action == Intent.ACTION_OPEN_DOCUMENT)
        val picked = TestPhotos.jpeg(File(incoming, "picked.jpg"), 800, 600)
        compose.runOnUiThread { activity.receiveResult(intent, Activity.RESULT_OK, Intent().setData(Uri.fromFile(picked))) }
        compose.waitUntil(PATIENCE_MS) { offers.size == 1 }
        assertArrayEquals(picked.readBytes(), offers.single())
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
