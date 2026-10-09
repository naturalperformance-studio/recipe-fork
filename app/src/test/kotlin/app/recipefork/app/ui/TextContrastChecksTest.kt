package app.recipefork.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** A11Y-002 on a rendered screen: the text contrast check measures each text against what is drawn behind it. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TextContrastChecksTest {
    @get:Rule
    val compose = createComposeRule()

    private fun assertFails(containing: String) {
        val error = assertThrows(AssertionError::class.java) { TextContrastChecks.assertTextContrast(compose) }
        assertTrue(error.message.orEmpty(), error.message.orEmpty().contains(containing))
    }

    // Body text at 4.5:1 or better passes; light grey on white fails, and the failure names the text and its ratio.
    @Test
    fun lowContrastTextFailsAndDarkTextPasses() {
        compose.setContent {
            Column(Modifier.background(Color.White).padding(8.dp)) {
                Text("Readable", color = Color(0xFF595959))
                Text("Faint", color = Color(0xFFBBBBBB))
            }
        }
        assertFails("\"Faint\"")
        val error = assertThrows(AssertionError::class.java) { TextContrastChecks.assertTextContrast(compose) }
        assertTrue(error.message.orEmpty(), !error.message.orEmpty().contains("\"Readable\""))
    }

    // Large text needs 3:1 only.
    @Test
    fun largeTextNeedsThreeToOne() {
        compose.setContent {
            Box(Modifier.background(Color.White).padding(8.dp)) { Text("Large", color = Color(0xFF8A8A8A), fontSize = 28.sp) }
        }
        TextContrastChecks.assertTextContrast(compose)
    }

    // What is behind the text is what is drawn there: white text on a dark button passes, on white it fails.
    @Test
    fun theBackgroundIsWhatIsDrawnBehindTheText() {
        compose.setContent {
            Column(Modifier.background(Color.White).padding(8.dp)) {
                Box(Modifier.background(Color(0xFF202020)).padding(8.dp)) { Text("On dark", color = Color.White) }
                Text("On white", color = Color.White)
            }
        }
        assertFails("\"On white\"")
    }

    // A popup's text is measured against the popup's own drawing, not the screen beneath it.
    @Test
    fun aPopupsTextIsMeasuredInItsOwnWindow() {
        compose.setContent {
            Box(Modifier.background(Color.Black).padding(40.dp)) {
                Popup { Box(Modifier.background(Color.White).padding(8.dp)) { Text("In the popup", color = Color.Black) } }
            }
        }
        TextContrastChecks.assertTextContrast(compose)
    }

    // A disabled control's text is exempt, as A11Y-002 exempts it.
    @Test
    fun aDisabledControlsTextIsExempt() {
        compose.setContent { Box(Modifier.background(Color.White).padding(8.dp)) { Button(onClick = {}, enabled = false) { Text("Off") } } }
        TextContrastChecks.assertTextContrast(compose, expectAtLeast = 0)
    }
}
