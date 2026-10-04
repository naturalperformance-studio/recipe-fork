package app.recipefork.app.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically

/**
 * The app's motion, in Material 3's durations and easings: what appears comes in over a medium duration, decelerating;
 * what goes leaves over a short one, accelerating. Every animation runs on Compose's clock, so with the system's
 * animator duration scale at 0 nothing moves.
 */
internal object Motion {
    /** M3 `medium2`. */
    const val APPEAR_MS = 300

    /** M3 `short4`. */
    const val GO_MS = 200

    /** M3 emphasized decelerate. */
    val Decelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** M3 emphasized accelerate. */
    val Accelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    fun <T> appearing(): FiniteAnimationSpec<T> = tween(APPEAR_MS, easing = Decelerate)
    fun <T> going(): FiniteAnimationSpec<T> = tween(GO_MS, easing = Accelerate)

    /** A block opening in a column: it unfolds downwards and fades in. */
    val open: EnterTransition = expandVertically(appearing()) + fadeIn(appearing())

    /** A block closing in a column: it folds up and fades out. */
    val close: ExitTransition = shrinkVertically(going()) + fadeOut(going())

    /** A bar at the bottom of the screen: it rises from the edge. */
    val rise: EnterTransition = slideInVertically(appearing()) { it } + fadeIn(appearing())

    /** A bar at the bottom of the screen: it sinks below the edge. */
    val sink: ExitTransition = slideOutVertically(going()) { it } + fadeOut(going())
}
