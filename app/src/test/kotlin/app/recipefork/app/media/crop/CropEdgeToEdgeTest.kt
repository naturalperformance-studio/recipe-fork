package app.recipefork.app.media.crop

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.core.graphics.Insets
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowDialog
import java.io.ByteArrayInputStream

/**
 * The crop is a full-screen window drawn edge to edge (FLOW-026): its black ground runs under the system bars, which
 * its own window leaves to it, while its title and controls keep clear of them.
 */
@RunWith(AndroidJUnit4::class)
class CropEdgeToEdgeTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun theCropRunsUnderTheBarsWithItsControlsClearOfThem() {
        val photo = PhotoToCrop(open = { ByteArrayInputStream(ByteArray(0)) }, orientation = null, retake = false)
        compose.setContent { CropScreen(photo, onDone = {}, onRetake = {}, onCancel = {}, onUnreadable = {}) }
        compose.onNodeWithTag(CropTags.SCREEN).assertExists()
        val decor = ShadowDialog.getLatestDialog().window!!.decorView
        compose.runOnUiThread {
            val insets = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, STATUS, 0, 0))
                .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, NAVIGATION))
                .build()
            decor.dispatchApplyWindowInsets(insets.toWindowInsets())
        }
        compose.waitForIdle()
        // The black ground spans the whole window, under the bars: the window pads nothing itself.
        val ground = compose.onNodeWithTag(CropTags.GROUND).fetchSemanticsNode().boundsInWindow
        assertEquals(0f, ground.top)
        assertEquals(decor.height.toFloat(), ground.bottom)
        // The title and controls keep exactly the bars' room from them.
        val content = compose.onNodeWithTag(CropTags.SCREEN).fetchSemanticsNode().boundsInWindow
        assertEquals(STATUS.toFloat(), content.top)
        assertEquals((decor.height - NAVIGATION).toFloat(), content.bottom)
    }

    private companion object {
        const val STATUS = 60
        const val NAVIGATION = 90
    }
}
