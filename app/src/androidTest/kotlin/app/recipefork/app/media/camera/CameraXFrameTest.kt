package app.recipefork.app.media.camera

import android.Manifest
import android.content.Context
import android.graphics.BitmapFactory
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The real camera, through CameraX, on a device (FLOW-026 (3)): the picture taken with the phone upright is the 4:3
 * landscape frame the viewfinder shows. A device with no back camera skips it.
 */
@RunWith(AndroidJUnit4::class)
class CameraXFrameTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private class Started : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
    }

    @Test
    fun aPictureTakenUprightIsTheLandscapeFrame() {
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
                val jpeg = withTimeout(60_000) { camera!!.takePicture() }
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, bounds)
                assertTrue("${bounds.outWidth} x ${bounds.outHeight}", bounds.outWidth > bounds.outHeight)
                assertEquals(4.0 / 3.0, bounds.outWidth.toDouble() / bounds.outHeight, 0.02)
            } finally {
                withContext(Dispatchers.Main) {
                    camera!!.close()
                    owner.registry.currentState = Lifecycle.State.DESTROYED
                }
            }
        }
    }
}
