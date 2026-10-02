package app.recipefork.app.media.camera

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.LifecycleOwner
import java.io.IOException

/**
 * A camera for a test, in place of CameraX, which cannot run on the JVM: [present] says whether the phone has a back
 * camera, [picture] is what the shutter takes (null: the shot fails), and what was opened and closed is counted.
 */
class FakeCamera(var present: Boolean = true, var picture: () -> ByteArray? = { ByteArray(0) }) : CameraAccess {
    @Volatile var opened = 0
        private set

    @Volatile var closed = 0
        private set

    @Volatile var taken = 0
        private set

    /** Whether a camera opened has not been released. */
    val isOpen: Boolean get() = opened > closed

    override suspend fun open(context: Context, owner: LifecycleOwner): OpenCamera? {
        if (!present) return null
        opened++
        return object : OpenCamera {
            @Composable
            override fun Viewfinder(modifier: Modifier) = Box(modifier)

            override suspend fun takePicture(): ByteArray {
                taken++
                return picture() ?: throw IOException("the shot failed")
            }

            override fun close() {
                closed++
            }
        }
    }
}
