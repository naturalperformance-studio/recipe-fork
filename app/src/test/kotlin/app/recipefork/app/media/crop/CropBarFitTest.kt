package app.recipefork.app.media.crop

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.card.assertWhole
import app.recipefork.app.card.textLayoutsWithin
import app.recipefork.app.ui.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayInputStream

/**
 * After the camera, the crop's Cancel, Retake and Done each read whole on a 320 dp-wide phone at twice the font scale,
 * in Russian and in English: where one line has no room for the three, they wrap — Done last, at the end — rather
 * than cut or squeeze a label; at the usual font scale they keep one line.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CropBarFitTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val cameraActions = listOf(CropTags.CANCEL, CropTags.RETAKE, CropTags.DONE)
    private val galleryActions = listOf(CropTags.CANCEL, CropTags.DONE)
    private var actions = cameraActions

    private fun showCameraCrop() = showCrop(retake = true)

    /** The crop opened on a photo from the gallery: no Retake, only Cancel and Done. */
    private fun showGalleryCrop() {
        actions = galleryActions
        showCrop(retake = false)
        compose.onNodeWithTag(CropTags.RETAKE).assertDoesNotExist()
    }

    private fun showCrop(retake: Boolean) {
        val photo = PhotoToCrop(open = { ByteArrayInputStream(ByteArray(0)) }, orientation = null, retake = retake)
        compose.setContent { AppTheme { CropScreen(photo, onDone = {}, onRetake = {}, onCancel = {}, onUnreadable = {}) } }
        compose.onNodeWithTag(CropTags.DONE).assertExists()
        compose.waitForIdle()
    }

    private fun bounds(tag: String): Rect = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun assertActionsReadWhole() {
        val bar = bounds(CropTags.BAR)
        actions.forEach { tag ->
            compose.assertWhole(tag)
            compose.textLayoutsWithin(tag).forEach { assertEquals("$tag on one line", 1, it.lineCount) }
            val action = bounds(tag)
            assertTrue("$tag $action within the bar $bar", action.left >= bar.left - 0.5f && action.right <= bar.right + 0.5f)
            val density = compose.onNodeWithTag(tag).fetchSemanticsNode().layoutInfo.density.density
            assertTrue("$tag at least 48 dp high", action.height / density + 0.5f >= 48f)
        }
        actions.forEachIndexed { i, a ->
            actions.drop(i + 1).forEach { b -> assertFalse("$a and $b overlap", bounds(a).overlaps(bounds(b))) }
        }
        // Done comes last: at the end of the last line.
        val done = bounds(CropTags.DONE)
        actions.dropLast(1).forEach { assertTrue("$it before Done", bounds(it).top <= done.top) }
    }

    private fun assertOneLine() {
        val tops = actions.map { bounds(it).center.y }
        assertTrue("one line: $tops", tops.all { kotlin.math.abs(it - tops.first()) < 1f })
        // Cancel at the start, Done at the end.
        val bar = bounds(CropTags.BAR)
        assertEquals(bar.left, bounds(CropTags.CANCEL).left, 1f)
        assertEquals(bar.right, bounds(CropTags.DONE).right, 1f)
    }

    @Test
    @Config(qualifiers = "ru-w320dp-h640dp", fontScale = 2f)
    fun theActionsReadWholeInRussianAtTwiceTheFont() {
        showCameraCrop()
        assertActionsReadWhole()
        // Three labels this size do not fit 288 dp side by side: they wrap.
        assertTrue("wrapped", bounds(CropTags.DONE).top > bounds(CropTags.CANCEL).bottom)
    }

    @Test
    @Config(qualifiers = "en-w320dp-h640dp", fontScale = 2f)
    fun theActionsReadWholeInEnglishAtTwiceTheFont() {
        showCameraCrop()
        assertActionsReadWhole()
    }

    @Test
    @Config(qualifiers = "ru-w320dp-h640dp")
    fun theActionsKeepOneLineInRussianAtTheUsualFont() {
        showCameraCrop()
        assertActionsReadWhole()
        assertOneLine()
    }

    @Test
    @Config(qualifiers = "en-w320dp-h640dp")
    fun theActionsKeepOneLineInEnglishAtTheUsualFont() {
        showCameraCrop()
        assertActionsReadWhole()
        assertOneLine()
    }

    @Test
    @Config(qualifiers = "ru-w320dp-h640dp", fontScale = 2f)
    fun theGalleryCropsActionsReadWholeInRussianAtTwiceTheFont() {
        showGalleryCrop()
        assertActionsReadWhole()
        // Two labels this size may share the line or wrap; either way Cancel starts the bar and Done ends its last line.
        val bar = bounds(CropTags.BAR)
        assertEquals(bar.left, bounds(CropTags.CANCEL).left, 1f)
        assertEquals(bar.right, bounds(CropTags.DONE).right, 1f)
    }

    @Test
    @Config(qualifiers = "ru-w320dp-h640dp")
    fun theGalleryCropKeepsCancelAtTheStartAndDoneAtTheEndInRussianAtTheUsualFont() {
        showGalleryCrop()
        assertActionsReadWhole()
        assertOneLine()
    }

    @Test
    @Config(qualifiers = "en-w320dp-h640dp")
    fun theGalleryCropKeepsCancelAtTheStartAndDoneAtTheEndInEnglishAtTheUsualFont() {
        showGalleryCrop()
        assertActionsReadWhole()
        assertOneLine()
    }
}
