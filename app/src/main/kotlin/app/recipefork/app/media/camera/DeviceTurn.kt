package app.recipefork.app.media.camera

import android.view.OrientationEventListener
import android.view.Surface

/**
 * How the phone is held, as the camera is told it so that a picture comes out the right way up however the phone is
 * turned — upside down included, which turns no screen.
 */
object DeviceTurn {
    /**
     * The [Surface] rotation a camera's capture is set to for the phone turned [degrees] clockwise from its natural
     * way up, as an [OrientationEventListener] reports it; null where the phone lies flat and no turn can be told.
     */
    fun targetRotation(degrees: Int): Int? = when {
        degrees == OrientationEventListener.ORIENTATION_UNKNOWN || degrees < 0 -> null
        Math.floorMod(degrees, 360) in 45 until 135 -> Surface.ROTATION_270
        Math.floorMod(degrees, 360) in 135 until 225 -> Surface.ROTATION_180
        Math.floorMod(degrees, 360) in 225 until 315 -> Surface.ROTATION_90
        else -> Surface.ROTATION_0
    }
}
