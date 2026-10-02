package app.recipefork.app.card

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/** How long the card keeps the screen on without a touch before leaving it to the device's own setting (FLOW-014). */
val AWAKE_WITHOUT_A_TOUCH: Duration = 15.minutes

/** The time the card's keep-awake period is measured in: the real one on a device, one the tests move by hand. */
fun interface AwakeClock {
    /** Suspends until [duration] has passed. */
    suspend fun wait(duration: Duration)
}

/** The device's own time. */
val RealAwakeClock = AwakeClock { delay(it) }

/** The clock the keep-awake period is measured by; tests provide their own. */
val LocalAwakeClock = staticCompositionLocalOf { RealAwakeClock }

/**
 * Keeps the screen on while the element this modifies is shown, for [AWAKE_WITHOUT_A_TOUCH] after the last touch on
 * it, then leaves the screen to the device's own setting until the next touch (FLOW-014 (1–3)). Coming back to the
 * application counts as a touch. Once the element is no longer shown the screen is left to the device's setting at
 * once (FLOW-014 (4)).
 */
@Composable
fun Modifier.keepScreenAwake(): Modifier {
    val clock = LocalAwakeClock.current
    val view = LocalView.current
    var touches by remember { mutableIntStateOf(0) }
    LaunchedEffect(touches, clock, view) {
        view.keepScreenOn = true
        clock.wait(AWAKE_WITHOUT_A_TOUCH)
        view.keepScreenOn = false
    }
    LifecycleResumeEffect(Unit) {
        touches++
        onPauseOrDispose {}
    }
    DisposableEffect(view) {
        onDispose { view.keepScreenOn = false }
    }
    return pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                // Seen on the way down to the card and never consumed: the card handles the touch as it would anyway.
                if (awaitPointerEvent(PointerEventPass.Initial).type == PointerEventType.Press) touches++
            }
        }
    }
}
