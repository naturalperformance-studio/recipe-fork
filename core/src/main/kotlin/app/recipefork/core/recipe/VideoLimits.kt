package app.recipefork.core.recipe

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The limits of MEDIA-004 on a step's clip, held once: what the device converts a clip to, what an importer accepts
 * of a recipe file, and what MEDIA-007 refuses before anything else. The limits are inclusive.
 */
object VideoLimits {
    /** The longest clip accepted (MEDIA-004, MEDIA-007). */
    val MAX_DURATION: Duration = 15.seconds

    /** The most a converted clip weighs (MEDIA-004). */
    const val MAX_BYTES: Long = 6L * 1024 * 1024

    /** The longest edge of a converted clip, in pixels, in whichever orientation it was shot (MEDIA-004). */
    const val MAX_LONGER_EDGE: Int = 1280

    /**
     * Whether a clip lasting [duration] is accepted (MEDIA-007): one whose duration could not be read, null here, is
     * not, nor is one of no length.
     */
    fun accepts(duration: Duration?): Boolean = duration != null && duration.isPositive() && duration <= MAX_DURATION
}
