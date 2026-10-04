package app.recipefork.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Collections

/**
 * The device's feedback each haptic plays. A tick — servings −/+, a dragged row passing to a new place — is a
 * virtual-key click every phone plays, not the segment tick phones without that effect leave silent. It goes through
 * the composition's feedback, which the view gives under the system's touch-feedback setting.
 */
@RunWith(AndroidJUnit4::class)
class HapticsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun aTickPlaysAVirtualKeyClickAndAConfirmationAConfirm() {
        assertEquals(HapticFeedbackType.VirtualKey, Haptic.TICK.feedbackType())
        assertEquals(HapticFeedbackType.Confirm, Haptic.CONFIRM.feedbackType())
    }

    @Test
    fun theDevicesOwnFeedbackIsAskedForTheMappedType() {
        val asked = Collections.synchronizedList(mutableListOf<HapticFeedbackType>())
        val recording = object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                asked += hapticFeedbackType
            }
        }
        var haptics: Haptics? = null
        compose.setContent {
            CompositionLocalProvider(LocalHapticFeedback provides recording) { haptics = rememberHaptics() }
        }
        compose.runOnIdle {
            haptics!!.perform(Haptic.TICK)
            haptics!!.perform(Haptic.CONFIRM)
        }
        assertEquals(listOf(HapticFeedbackType.VirtualKey, HapticFeedbackType.Confirm), asked.toList())
    }
}
