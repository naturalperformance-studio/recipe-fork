package app.recipefork.app.card

import android.graphics.RectF
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.IntSize
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Where a step's clip is drawn in its box (MEDIA-004): the turn a file states is applied to its size; the box is the
 * clip's own proportions when landscape or square, a square when taller (PhotoImage's rule, within 4:5); the picture
 * covers the box, centred, keeping its proportions, and once clipped to the box is never larger than it. The control's
 * icon reads on its scrim over any frame (≥ 3:1).
 */
@RunWith(AndroidJUnit4::class)
class ClipFrameTest {
    private val clips = listOf(
        "portrait by its turn" to ClipFrame.shown(1280, 720, 90),
        "portrait by its pixels" to ClipFrame.shown(720, 1280, 0),
        "landscape" to ClipFrame.shown(1280, 720, 0),
        "landscape by a turn of 270" to ClipFrame.shown(720, 1280, 270),
        "square" to ClipFrame.shown(720, 720, 180),
        "very tall" to ClipFrame.shown(100, 1000, 0),
        "very wide" to ClipFrame.shown(1000, 100, 0),
    )

    @Test
    fun theTurnIsAppliedToTheSize() {
        assertEquals(IntSize(720, 1280), ClipFrame.shown(1280, 720, 90))
        assertEquals(IntSize(720, 1280), ClipFrame.shown(1280, 720, 270))
        assertEquals(IntSize(720, 1280), ClipFrame.shown(1280, 720, -90))
        assertEquals(IntSize(1280, 720), ClipFrame.shown(1280, 720, 180))
        assertEquals(IntSize(1280, 720), ClipFrame.shown(1280, 720, 0))
    }

    @Test
    fun theBoxIsTheClipsOwnProportionsOrASquare() {
        assertEquals(1f, ClipFrame.boxRatio(ClipFrame.shown(1280, 720, 90)), 0.001f)
        assertEquals(1f, ClipFrame.boxRatio(IntSize(720, 1280)), 0.001f)
        assertEquals(16f / 9f, ClipFrame.boxRatio(IntSize(1280, 720)), 0.001f)
        assertEquals(1f, ClipFrame.boxRatio(IntSize(720, 720)), 0.001f)
        assertEquals(ClipFrame.UNKNOWN_RATIO, ClipFrame.boxRatio(null), 0f)
        assertEquals(ClipFrame.UNKNOWN_RATIO, ClipFrame.boxRatio(IntSize(0, 0)), 0f)
        // Never taller than 4:5 (height at most 1.25 × width), whatever the clip.
        clips.forEach { (name, shown) -> assertTrue(name, 1f / ClipFrame.boxRatio(shown) <= 1.25f) }
    }

    @Test
    fun thePictureCoversTheBoxCentredAndKeepsItsProportions() {
        val boxWidth = 984f
        clips.forEach { (name, shown) ->
            val boxHeight = boxWidth / ClipFrame.boxRatio(shown)
            val (left, top, right, bottom) = ClipFrame.content(boxWidth, boxHeight, shown)
            // Covers it.
            assertTrue(name, left <= 0.01f && top <= 0.01f && right >= boxWidth - 0.01f && bottom >= boxHeight - 0.01f)
            // Fills it exactly along one side.
            assertTrue(name, (right - left - boxWidth) < 0.01f || (bottom - top - boxHeight) < 0.01f)
            // Centred.
            assertEquals(name, boxWidth / 2f, (left + right) / 2f, 0.01f)
            assertEquals(name, boxHeight / 2f, (top + bottom) / 2f, 0.01f)
            // Its proportions kept.
            assertEquals(name, shown.width.toFloat() / shown.height, (right - left) / (bottom - top), 0.001f)
            // A landscape or square clip is shown whole.
            if (shown.width >= shown.height) assertEquals(name, boxWidth, right - left, 0.01f)
        }
    }

    // The TextureView stretches the picture over its own bounds; the transform takes it to the place above, and the
    // clip of the box leaves no more than the box.
    @Test
    fun theTransformTakesTheStretchedPictureToItsPlaceAndClippedIsTheBox() {
        val boxWidth = 984f
        clips.forEach { (name, shown) ->
            val boxHeight = boxWidth / ClipFrame.boxRatio(shown)
            val drawn = RectF(0f, 0f, boxWidth, boxHeight)
            ClipFrame.transform(boxWidth, boxHeight, shown).mapRect(drawn)
            val (left, top, right, bottom) = ClipFrame.content(boxWidth, boxHeight, shown)
            assertEquals(name, left, drawn.left, 0.5f)
            assertEquals(name, top, drawn.top, 0.5f)
            assertEquals(name, right, drawn.right, 0.5f)
            assertEquals(name, bottom, drawn.bottom, 0.5f)
            val box = RectF(0f, 0f, boxWidth, boxHeight)
            assertTrue(name, drawn.intersect(box))
            assertEquals(name, box, drawn)
        }
    }

    // The portrait clip the owner saw: a square box; uncut, its picture would reach 39 % of the width beyond the box
    // above and below, over the step's text — which the clip to the box removes.
    @Test
    fun aPortraitClipsPictureReachesBeyondItsBoxOnlyToBeClipped() {
        val shown = ClipFrame.shown(1280, 720, 90)
        val (_, top, _, bottom) = ClipFrame.content(1000f, 1000f, shown)
        assertEquals(-388.9f, top, 0.1f)
        assertEquals(1388.9f, bottom, 0.1f)
    }

    // A11Y-007 / WCAG 1.4.11: the white icon on the dark scrim reads at 3:1 or more over any frame, white to black.
    @Test
    fun theControlsIconReadsOnItsScrimOverAnyFrame() {
        for (frame in listOf(Color.White, Color.Black, Color(0xFFFFFF00), Color(0xFF00FFFF), Color(0xFF808080), Color(0xFFFFC0CB))) {
            val scrim = ClipControlScrim.compositeOver(frame)
            assertTrue("over $frame: ${ratio(ClipControlIcon, scrim)}", ratio(ClipControlIcon, scrim) >= 3.0)
        }
    }

    private fun ratio(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    /** The relative luminance of an opaque sRGB colour (WCAG 2.2), as ContrastTest measures it. */
    private fun luminance(color: Color): Double {
        require(color.alpha == 1f) { "$color is not opaque" }
        fun channel(v: Float): Double = if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
    }
}
