package app.recipefork.app.media.camera

import android.Manifest
import android.content.Context
import android.graphics.BitmapFactory
import android.media.ExifInterface
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.recipefork.app.media.Orientation
import app.recipefork.app.media.PhotoReencoder
import app.recipefork.app.media.crop.CropGeometry
import app.recipefork.app.media.crop.CropPicture
import app.recipefork.app.media.crop.PhotoToCrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.File

/**
 * The real camera, through CameraX, on a device (FLOW-026): the picture taken is the sensor's whole frame; cropped to
 * the recipe's frame and processed, it is a 4:3 landscape photograph within the limits and holding no metadata; and
 * the phone turned upside down turns the picture with it. A device with no back camera skips it.
 */
@RunWith(AndroidJUnit4::class)
class CameraXFrameTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private class Started : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
    }

    /** Runs [use] with the back camera opened through CameraX, the permission granted, and closes it after. */
    private fun withCamera(use: suspend (OpenCamera) -> Unit) {
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .grantRuntimePermission(context.packageName, Manifest.permission.CAMERA)
        val owner = Started()
        runBlocking {
            val camera = withContext(Dispatchers.Main) {
                owner.registry.currentState = Lifecycle.State.RESUMED
                CameraXAccess.open(context, owner)
            }
            assumeTrue("the device has a back camera", camera != null)
            try {
                use(camera!!)
            } finally {
                withContext(Dispatchers.Main) {
                    camera!!.close()
                    owner.registry.currentState = Lifecycle.State.DESTROYED
                }
            }
        }
    }

    private suspend fun OpenCamera.shot(): Shot = withTimeout(60_000) { takePicture() }

    // The shutter takes the sensor's whole frame — 4:3 one way or the other — with no part of it cut away.
    @Test
    fun aPictureIsTheWholeFrame() = withCamera { camera ->
        val shot = camera.shot()
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(shot.jpeg, 0, shot.jpeg.size, bounds)
        val longer = maxOf(bounds.outWidth, bounds.outHeight).toDouble()
        val shorter = minOf(bounds.outWidth, bounds.outHeight).toDouble()
        assertEquals("${bounds.outWidth} x ${bounds.outHeight}", 4.0 / 3.0, longer / shorter, 0.02)
    }

    // The camera path end to end: the shot, cropped to the frame the crop opens with and processed as "Done" hands it
    // over, is a 4:3 landscape photograph of at most 1600 pixels and 1 MB, holding no metadata.
    @Test
    fun aShotCroppedAndProcessedIsTheLandscapeFrame() = withCamera { camera ->
        val shot = camera.shot()
        val photo = PhotoToCrop({ ByteArrayInputStream(shot.jpeg) }, Orientation(Math.floorMod(shot.rotationDegrees, 360), false), retake = true)
        val picture = CropPicture.open(photo)
        assertNotNull("the shot reads as a picture", picture)
        val rect = CropGeometry.pixels(CropGeometry.initial(picture!!.upright), picture.upright)
        val cut = CropPicture.cut(photo, picture.raw, picture.orientation, rect)
        val stored = PhotoReencoder().reencode { ByteArrayInputStream(cut) }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(stored, 0, stored.size, bounds)
        assertTrue("${bounds.outWidth} x ${bounds.outHeight}", bounds.outWidth > bounds.outHeight && bounds.outWidth <= 1600)
        assertEquals(4.0 / 3.0, bounds.outWidth.toDouble() / bounds.outHeight, 0.02)
        assertTrue(stored.size <= 1_000_000)
        val file = File(context.cacheDir, "camera-x-stored.jpg").apply { writeBytes(stored) }
        val exif = ExifInterface(file.path)
        listOf(ExifInterface.TAG_MAKE, ExifInterface.TAG_MODEL, ExifInterface.TAG_DATETIME, ExifInterface.TAG_GPS_LATITUDE)
            .forEach { assertNull(it, exif.getAttribute(it)) }
        file.delete()
    }

    // The phone turned upside down — which turns no screen — turns the picture half a turn with it, so that it comes
    // out the right way up.
    @Test
    fun aPhoneTurnedUpsideDownTurnsThePicture() = withCamera { camera ->
        val turning = camera as CameraXCamera
        withContext(Dispatchers.Main) { turning.turned(0) }
        val upright = camera.shot().rotationDegrees
        withContext(Dispatchers.Main) { turning.turned(180) }
        val upsideDown = camera.shot().rotationDegrees
        assertEquals("upright $upright, upside down $upsideDown", 180, Math.floorMod(upsideDown - upright, 360))
    }
}
