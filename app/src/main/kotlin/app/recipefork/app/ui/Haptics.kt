package app.recipefork.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

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

/**
 * The touch feedback to give: the one provided, or else the device's own through the view, which follows the system's
 * touch-feedback setting.
 */
@Composable
fun rememberHaptics(): Haptics {
    val provided = LocalHaptics.current
    val feedback = LocalHapticFeedback.current
    return remember(provided, feedback) {
        provided ?: Haptics { haptic ->
            feedback.performHapticFeedback(haptic.feedbackType())
        }
    }
}

/**
 * The device's feedback each haptic plays. A tick is a virtual-key click: every phone plays it, where the segment tick
 * is left silent by phones without that effect.
 */
internal fun Haptic.feedbackType(): HapticFeedbackType = when (this) {
    Haptic.CONFIRM -> HapticFeedbackType.Confirm
    Haptic.TICK -> HapticFeedbackType.VirtualKey
}
