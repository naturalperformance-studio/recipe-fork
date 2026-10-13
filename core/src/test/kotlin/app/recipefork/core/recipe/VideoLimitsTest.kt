package app.recipefork.core.recipe

import app.recipefork.core.file.RecipeFileCheck
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** MEDIA-004's limits on a clip, held once, and MEDIA-007's rule on its length as an importer reads it. */
class VideoLimitsTest {
    @Test
    fun theLimitsAreMedia004s() {
        assertEquals(15.seconds, VideoLimits.MAX_DURATION)
        assertEquals(6L * 1024 * 1024, VideoLimits.MAX_BYTES)
        assertEquals(1280, VideoLimits.MAX_LONGER_EDGE)
    }

    // MEDIA-007: at most fifteen seconds; a clip of no length, or whose length cannot be read, is not accepted.
    @Test
    fun aClipIsAcceptedUpToItsLimit() {
        assertTrue(VideoLimits.accepts(10.seconds))
        assertTrue(VideoLimits.accepts(15.seconds))
        assertFalse(VideoLimits.accepts(16.seconds))
        assertFalse(VideoLimits.accepts(40.seconds))
        assertFalse(VideoLimits.accepts(Duration.ZERO))
        assertFalse(VideoLimits.accepts(null))
    }

    // T-243 on the owner's phone: a camera clip of fifteen seconds whose sound track runs a little past its picture
    // states the longer track's length; it is accepted, and one clearly over is still refused.
    @Test
    fun aFifteenSecondCameraClipWhoseSoundRunsPastItIsAccepted() {
        assertTrue(VideoLimits.accepts(15.seconds + 64.milliseconds))
        assertTrue(VideoLimits.accepts(15.seconds + 300.milliseconds))
        assertTrue(VideoLimits.accepts(VideoLimits.MAX_DURATION + VideoLimits.TOLERANCE))
        assertFalse(VideoLimits.accepts(VideoLimits.MAX_DURATION + VideoLimits.TOLERANCE + 1.milliseconds))
        assertFalse(VideoLimits.accepts(15.seconds + 900.milliseconds))
        assertNull(RecipeFileCheck.clipProblem(15.seconds + 300.milliseconds))
    }

    @Test
    fun aRecipeFilesClipIsCheckedByTheSameRule() {
        assertNull(RecipeFileCheck.clipProblem(15.seconds))
        assertNotNull(RecipeFileCheck.clipProblem(16.seconds))
        assertNotNull(RecipeFileCheck.clipProblem(null))
        assertNotNull(RecipeFileCheck.clipProblem(Duration.ZERO))
    }
}
