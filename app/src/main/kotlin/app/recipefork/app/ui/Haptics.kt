package app.recipefork.app.ui

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/** The kinds of touch feedback the application gives. */
enum class Haptic {
    /** An action done: a timer started, stopped or cancelled. */
    CONFIRM,

    /** A light step: the serving count walked by one, a dragged row passing to a new place. */
    TICK,
}

/** Gives touch feedback. */
fun interface Haptics {
    fun perform(haptic: Haptic)
}

/** The touch feedback provided to the composition; tests provide one that records. Null: the device's own. */
val LocalHaptics = staticCompositionLocalOf<Haptics?> { null }

/** The touch feedback to give: the one provided, or else the device's own vibrator ([VibratorHaptics]). */
@Composable
fun rememberHaptics(): Haptics {
    val provided = LocalHaptics.current
    val context = LocalContext.current
    return remember(provided, context) { provided ?: VibratorHaptics(context) }
}

/**
 * Touch feedback played on the device's default vibrator as touch vibrations. The vibrator is asked directly because
 * some phones leave the view's haptic feedback silent. From API 33 the system silences touch vibrations itself when
 * touch feedback is off; below it the system only scales them by the touch intensity, so the switch
 * ([Settings.System.HAPTIC_FEEDBACK_ENABLED]) is read here at each play, and off plays nothing. A device without a
 * vibrator (none offered, or one that has none) feels nothing. Only the application's context is held, so the
 * feedback never keeps a screen's activity alive.
 */
class VibratorHaptics(context: Context) : Haptics {
    private val context: Context = context.applicationContext
    private val vibrator: Vibrator? = this.context.vibrator()

    override fun perform(haptic: Haptic) {
        val vibrator = vibrator?.takeIf { it.hasVibrator() } ?: return
        val effect = VibrationEffect.createPredefined(haptic.effectId())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH))
        } else if (touchFeedbackOn()) {
            vibrator.vibrate(effect, touchAudioAttributes())
        }
    }

    /** Whether the system's touch-feedback switch is on; a device that never set it counts as on. */
    private fun touchFeedbackOn(): Boolean =
        Settings.System.getInt(context.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) != 0
}

/** The predefined vibration each haptic plays: a tick is the light tick, a confirmation a click. */
internal fun Haptic.effectId(): Int = when (this) {
    Haptic.CONFIRM -> VibrationEffect.EFFECT_CLICK
    Haptic.TICK -> VibrationEffect.EFFECT_TICK
}

/**
 * Before API 33, the audio usage the system plays as touch feedback: scaled, or silenced, by the touch intensity
 * setting. The touch-feedback switch is not applied to it there; [VibratorHaptics] checks that itself.
 */
internal fun touchAudioAttributes(): AudioAttributes =
    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).build()

/** The device's default vibrator, or null when the device offers none. */
internal fun Context.vibrator(): Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    getSystemService(VibratorManager::class.java)?.defaultVibrator
} else {
    getSystemService(Vibrator::class.java)
}
