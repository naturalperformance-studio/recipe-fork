package app.recipefork.app.media.camera

import android.view.Surface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The camera follows how the phone is held (FLOW-026): upright, sideways either way, and upside down — which turns no
 * screen, and so was missed when only the screen was asked — each sets the capture's rotation, so that the picture
 * comes out the right way up.
 */
class DeviceTurnTest {
    @Test
    fun eachWayThePhoneIsHeldSetsTheCapturesRotation() {
        listOf(0, 10, 44, 315, 359).forEach { assertEquals("$it°", Surface.ROTATION_0, DeviceTurn.targetRotation(it)) }
        listOf(45, 90, 134).forEach { assertEquals("$it°", Surface.ROTATION_270, DeviceTurn.targetRotation(it)) }
        listOf(135, 180, 224).forEach { assertEquals("$it°", Surface.ROTATION_180, DeviceTurn.targetRotation(it)) }
        listOf(225, 270, 314).forEach { assertEquals("$it°", Surface.ROTATION_90, DeviceTurn.targetRotation(it)) }
    }

    @Test
    fun aPhoneLyingFlatChangesNothing() {
        assertNull(DeviceTurn.targetRotation(-1))
    }
}
