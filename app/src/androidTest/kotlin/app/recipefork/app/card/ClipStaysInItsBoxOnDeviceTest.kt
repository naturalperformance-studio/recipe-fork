package app.recipefork.app.card

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.view.Surface
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.recipefork.app.media.TestClips
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.Step
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/**
 * MEDIA-004 on a real device, by its real player and codecs: a clip shot upright — landscape pixels marked as turned by
 * 90 degrees, as a converted clip of a phone's camera is — on the card's steps never draws outside its box. The screen
 * is taken twice: once with the clip playing, once with a player that draws nothing but reports the same size; the
 * clip's pixels are there in its box, and every pixel outside it — the step's text above, the next step's below — is
 * the same in both.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class ClipStaysInItsBoxOnDeviceTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext.applicationContext
    private val file = File(TestClips.directory(context), "clip-box/portrait.mp4")

    /** Draws nothing; reports the clip's stored size and turn as the real player does. */
    private class Blank : ClipPlayer {
        override suspend fun open(file: File, listener: ClipPlayer.Listener) = listener.ready(WIDTH, HEIGHT, ROTATION)
        override fun surface(surface: Surface?) = Unit
        override fun play() = Unit
        override fun pause() = Unit
        override fun release() = Unit
    }

    private var blank by mutableStateOf(false)

    @After
    fun clean() {
        file.parentFile?.deleteRecursively()
    }

    private val steps = listOf(
        Step(0, "Boil the beets whole, skins on."),
        Step(1, "Fry the onion and the carrot until soft and golden."),
        Step(2, "Serve with sour cream and dill."),
    )

    /** Where the node tagged [tag] is on the screen, in whole pixels about it. */
    private fun onScreen(tag: String): Rect {
        val root = IntArray(2).also { compose.activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0).getLocationOnScreen(it) }
        val bounds = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        return Rect(
            floor(bounds.left).toInt() + root[0],
            floor(bounds.top).toInt() + root[1],
            ceil(bounds.right).toInt() + root[0],
            ceil(bounds.bottom).toInt() + root[1],
        )
    }

    private fun screen(): Bitmap = instrumentation.uiAutomation.takeScreenshot()!!.let { taken ->
        taken.copy(Bitmap.Config.ARGB_8888, false).also { if (it !== taken) taken.recycle() }
    }

    private fun near(a: Int, b: Int) =
        abs(Color.red(a) - Color.red(b)) <= 8 && abs(Color.green(a) - Color.green(b)) <= 8 && abs(Color.blue(a) - Color.blue(b)) <= 8

    /** The share of [box]'s pixels on [shot] that are not the white background: the clip's picture. */
    private fun drawn(shot: Bitmap, box: Rect): Float {
        var count = 0
        var total = 0
        for (y in box.top + 2 until box.bottom - 2 step 4) {
            for (x in box.left + 2 until box.right - 2 step 4) {
                total++
                if (!near(shot.getPixel(x, y), Color.WHITE)) count++
            }
        }
        return count.toFloat() / total
    }

    @Test
    fun anUprightClipNeverDrawsOverTheStepsText() {
        TestClips.write(file, WIDTH, HEIGHT, seconds = 3, sound = false, rotation = ROTATION)
        compose.setContent {
            MaterialTheme {
                // Each showing is made anew, its picture's view with it.
                key(blank) {
                    CompositionLocalProvider(LocalClipPlayers provides if (blank) ClipPlayers { Blank() } else ClipPlayers { PlatformClipPlayer() }) {
                        Column(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.White).padding(16.dp)) {
                            StepsList(
                                steps,
                                RecipeId("clip-box"),
                                media = StepMediaOnCard { position -> if (position == 1) StepMediaItem.Clip(file) else null },
                            )
                        }
                    }
                }
            }
        }
        compose.waitUntilExactlyOneExists(hasTestTag(CardTags.stepVideo(1)), PATIENCE_MS)
        // The real player has read the clip, and its picture is on the screen in its box.
        compose.waitUntil(PATIENCE_MS) {
            val box = onScreen(CardTags.stepVideo(1))
            box.width() > 0 && abs(box.width() - box.height()) <= 2 && screen().let { shot -> drawn(shot, box).also { shot.recycle() } } > 0.5f
        }
        val box = onScreen(CardTags.stepVideo(1))
        val section = onScreen(CardTags.STEPS)
        val texts = listOf(0, 1, 2).map { onScreen(CardTags.step(it)) }
        val playing = screen()

        compose.runOnIdle { blank = true }
        compose.waitUntilExactlyOneExists(hasTestTag(CardTags.stepVideo(1)), PATIENCE_MS)
        compose.waitUntil(PATIENCE_MS) { onScreen(CardTags.stepVideo(1)) == box }
        compose.waitForIdle()
        // Nothing of a picture is left: only the control's scrim is drawn in the box.
        compose.waitUntil(PATIENCE_MS) { screen().let { shot -> drawn(shot, box).also { shot.recycle() } } < 0.2f }
        val without = screen()
        assertEquals(texts, listOf(0, 1, 2).map { onScreen(CardTags.step(it)) })

        // The step texts' places: the clip's pixels are in none of them.
        texts.forEachIndexed { index, text ->
            assertEquals("step ${index + 1}'s text covered", 0, differing(playing, without, text, except = null))
        }
        // Nor anywhere about the box, from the steps' heading down to the foot of the screen, edge to edge.
        val around = Rect(0, section.top, playing.width, playing.height)
        assertEquals("drawn outside its box", 0, differing(playing, without, around, except = Rect(box.left - 2, box.top - 2, box.right + 2, box.bottom + 2)))
        playing.recycle()
        without.recycle()
    }

    /** How many of [area]'s pixels differ between [a] and [b], leaving out [except]. */
    private fun differing(a: Bitmap, b: Bitmap, area: Rect, except: Rect?): Int {
        var count = 0
        for (y in maxOf(area.top, 0) until minOf(area.bottom, a.height)) {
            for (x in maxOf(area.left, 0) until minOf(area.right, a.width)) {
                if (except != null && except.contains(x, y)) continue
                if (!near(a.getPixel(x, y), b.getPixel(x, y))) count++
            }
        }
        return count
    }

    private companion object {
        const val PATIENCE_MS = 60_000L
        const val WIDTH = 1280
        const val HEIGHT = 720
        const val ROTATION = 90
    }
}
