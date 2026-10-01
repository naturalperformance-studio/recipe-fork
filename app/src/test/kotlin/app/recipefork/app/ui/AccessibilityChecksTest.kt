package app.recipefork.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** A11Y-003 (1): the touch-target check measures each interactive node as laid out. */
@RunWith(AndroidJUnit4::class)
class AccessibilityChecksTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show(width: Dp, height: Dp) = compose.setContent {
        Box(Modifier.size(width, height).clickable(onClickLabel = "act") {}.testTag("target"))
    }

    private fun assertCheckFailsOnSize() {
        val error = assertThrows(AssertionError::class.java) { AccessibilityChecks.assertTouchTargets(compose) }
        assertTrue(error.message.orEmpty(), error.message.orEmpty().contains("smaller than 48.0.dp"))
    }

    // A11Y-003 (1): an element smaller than 48 dp fails, although Compose widens its touch bounds to 48 dp.
    @Test
    fun aSmallElementFailsThoughComposeWidensItsTouchBounds() {
        show(24.dp, 24.dp)
        val node = compose.onNodeWithTag("target").fetchSemanticsNode()
        val minPx = with(node.layoutInfo.density) { 48.dp.toPx() }
        assertTrue(
            "touch bounds ${node.touchBoundsInRoot.size} should be widened to $minPx px",
            node.touchBoundsInRoot.width >= minPx && node.touchBoundsInRoot.height >= minPx,
        )
        assertCheckFailsOnSize()
    }

    // A11Y-003 (1): one side short is enough to fail.
    @Test
    fun anElementShortInOneDirectionFails() {
        show(200.dp, 40.dp)
        assertCheckFailsOnSize()
    }

    // A11Y-003 (1): an element of 48 by 48 dp passes.
    @Test
    fun anElementOf48dpPasses() {
        show(48.dp, 48.dp)
        AccessibilityChecks.assertTouchTargets(compose)
    }
}
