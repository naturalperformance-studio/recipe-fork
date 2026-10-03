package app.recipefork.app.editor

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * "Drag handle on the right" means the physical right in either layout direction — a left hand is on the left of the
 * screen whatever the language; left at its default, the column takes the block's start, as it always has.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp")
class DragColumnDirectionTest(private val direction: LayoutDirection) {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun show(onRight: Boolean) {
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides direction, LocalDragColumnOnRight provides onRight) {
                MaterialTheme {
                    val scroll = rememberScrollState()
                    val drag = rememberRowDrag(remember(scroll) { EditorScroll(scroll) }, 8.dp) { _, _ -> }
                    Box(Modifier.fillMaxWidth().testTag(BLOCK)) {
                        DragColumnBlock(drag, 0, 1, COLUMN, ICON) {
                            Box(Modifier.fillMaxWidth().height(96.dp).testTag(BODY))
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun bounds(tag: String): Rect =
        compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().let { Rect(it.positionInRoot, it.size.toSize()) }

    @Test
    fun onTheRightTheColumnIsAtThePhysicalRight() {
        show(onRight = true)
        val block = bounds(BLOCK)
        val column = bounds(COLUMN)
        val body = bounds(BODY)
        assertEquals("$direction: the column ends at the block's right edge", block.right, column.right, 0.5f)
        assertEquals("$direction: the content starts at the block's left edge", block.left, body.left, 0.5f)
        assertEquals("$direction: the content ends where the column starts", column.left, body.right, 0.5f)
    }

    @Test
    fun byDefaultTheColumnIsAtTheStart() {
        show(onRight = false)
        val block = bounds(BLOCK)
        val column = bounds(COLUMN)
        if (direction == LayoutDirection.Rtl) {
            assertEquals("Rtl: the column stands at the start, the right", block.right, column.right, 0.5f)
        } else {
            assertEquals("Ltr: the column stands at the start, the left", block.left, column.left, 0.5f)
        }
    }

    companion object {
        private const val BLOCK = "block"
        private const val COLUMN = "column"
        private const val ICON = "column-icon"
        private const val BODY = "body"

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun directions(): List<Array<Any>> = listOf(arrayOf(LayoutDirection.Ltr), arrayOf(LayoutDirection.Rtl))
    }
}
