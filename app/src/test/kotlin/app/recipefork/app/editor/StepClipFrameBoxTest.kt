package app.recipefork.app.editor

import android.content.Context
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowMediaMetadataRetriever
import org.robolectric.shadows.util.DataSource
import java.io.File

/**
 * The editor's clip (MEDIA-004): a portrait first frame — as the framework hands it, turned upright — is shown in a
 * square, cropped about its centre, and none of it is drawn outside that box, over what lies above or below.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp")
class StepClipFrameBoxTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun aPortraitFrameKeepsToItsSquareBox() {
        val file = File(context.filesDir, "portrait.mp4").apply { writeBytes(ByteArray(100)) }
        val frame = Bitmap.createBitmap(720, 1280, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.RED) }
        ShadowMediaMetadataRetriever.addFrame(DataSource.toDataSource(file.path), 0, frame)
        compose.setContent {
            MaterialTheme {
                Column(Modifier.background(Color.White)) {
                    Box(Modifier.fillMaxWidth().height(120.dp).testTag("above"))
                    StepClipFrame(1, file)
                    Box(Modifier.fillMaxWidth().height(120.dp).testTag("below"))
                }
            }
        }
        compose.waitUntil(PATIENCE_MS) {
            compose.onNodeWithTag(EditorTags.stepClipImage(1)).fetchSemanticsNode().size.let { it.width == it.height }
        }
        compose.waitForIdle()
        val box = compose.onNodeWithTag(EditorTags.stepClipImage(1)).fetchSemanticsNode().boundsInRoot
        assertEquals(box.width, box.height, 1f)
        val shot = compose.onRoot().captureToImage().toPixelMap()
        fun reds(tag: String): Int {
            val area = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
            var count = 0
            for (y in area.top.toInt() until area.bottom.toInt()) {
                for (x in area.left.toInt() until area.right.toInt()) {
                    val pixel = shot[x, y]
                    if (pixel.red > 0.8f && pixel.green < 0.2f && pixel.blue < 0.2f) count++
                }
            }
            return count
        }
        assertEquals("over what lies above", 0, reds("above"))
        assertEquals("over what lies below", 0, reds("below"))
        // The frame is drawn in its box.
        val centre = shot[box.center.x.toInt(), (box.top + box.height / 4).toInt()]
        assertEquals(1f, centre.red, 0.05f)
    }
}
