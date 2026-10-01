package app.recipefork.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.onImeAction
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
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

/** A11Y-003 (1): the touch-target check measures each interactive node as laid out, nested ones included. */
@RunWith(AndroidJUnit4::class)
class AccessibilityChecksTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show(width: Dp, height: Dp) = compose.setContent {
        Box(Modifier.size(width, height).clickable(onClickLabel = "act") {}.testTag("target"))
    }

    private fun assertCheckFailsOnSize(): String {
        val error = assertThrows(AssertionError::class.java) { AccessibilityChecks.assertTouchTargets(compose) }
        assertTrue(error.message.orEmpty(), error.message.orEmpty().contains("smaller than 48.0.dp"))
        return error.message.orEmpty()
    }

    /** The check fails on size and names the small element [name], not by its semantics id. */
    private fun assertFailsNaming(name: String) {
        val message = assertCheckFailsOnSize()
        assertTrue(message, message.contains("$name: 24.0 x 24.0 dp laid out"))
        assertTrue(message, !message.contains("node "))
    }

    /** A 24 dp node acted on only through [action], next to a 200 dp clickable. */
    private fun showSmallBeside(action: SemanticsPropertyReceiver.() -> Unit) = compose.setContent {
        Row {
            Box(Modifier.size(200.dp, 200.dp).clickable(onClickLabel = "big") {})
            Box(Modifier.size(24.dp, 24.dp).semantics { contentDescription = "small"; action() })
        }
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

    // A11Y-003 (1): an element interactive only through a semantics action does not hide inside a merging parent.
    @Test
    fun aSmallSemanticsActionInsideAClickableParentFails() {
        compose.setContent {
            Box(Modifier.size(200.dp, 200.dp).clickable(onClickLabel = "outer") {}) {
                Box(Modifier.size(24.dp, 24.dp).semantics { onClick(label = "inner") { true } })
            }
        }
        assertCheckFailsOnSize()
    }

    // A11Y-003 (1): a small toggleable inside a merging parent is measured on its own and fails.
    @Test
    fun aSmallToggleableInsideAMergingParentFails() {
        compose.setContent {
            Box(Modifier.size(200.dp, 200.dp).semantics(mergeDescendants = true) { contentDescription = "row" }) {
                Box(Modifier.size(24.dp, 24.dp).toggleable(value = false) {})
            }
        }
        assertCheckFailsOnSize()
    }

    // A11Y-003 (1): a nested element with no label of its own is named by the merging ancestor the reader hears.
    @Test
    fun anUnlabelledActionInsideALabelledMergingControlIsNamedByIt() {
        compose.setContent {
            Box(Modifier.size(200.dp, 200.dp).semantics(mergeDescendants = true) {}.clickable(onClickLabel = "change") {}) {
                Text("English")
                Box(Modifier.size(24.dp, 24.dp).semantics { onClick { true } })
            }
        }
        assertFailsNaming("unlabelled element inside \"English\"")
    }

    // A11Y-003 (1): a small switch in a merged row is named by the row's text.
    @Test
    fun aSmallSwitchInAMergedRowIsNamedByTheRow() {
        compose.setContent {
            Row(Modifier.size(300.dp, 64.dp).semantics(mergeDescendants = true) {}) {
                Text("Wi-Fi")
                Switch(checked = false, onCheckedChange = {}, modifier = Modifier.size(24.dp, 24.dp))
            }
        }
        val message = assertCheckFailsOnSize()
        assertTrue(message, message.contains("unlabelled element inside \"Wi-Fi\""))
        assertTrue(message, !message.contains("node "))
    }

    // A11Y-003 (1): a small clickable with only a click-action label is named by that label.
    @Test
    fun aSmallClickableIsNamedByItsActionLabel() {
        compose.setContent { Box(Modifier.size(24.dp, 24.dp).clickable(onClickLabel = "Open settings") {}) }
        assertFailsNaming("\"Open settings\" (action label)")
    }

    // A11Y-003 (1): a small clickable with only a test tag is named by the tag.
    @Test
    fun aSmallClickableIsNamedByItsTestTag() {
        compose.setContent { Box(Modifier.size(24.dp, 24.dp).clickable {}.testTag("settings")) }
        assertFailsNaming("tag \"settings\"")
    }

    // A11Y-003 (1): an element acted on only through custom actions is selected and measured.
    @Test
    fun aSmallElementWithOnlyCustomActionsFails() {
        showSmallBeside { customActions = listOf(CustomAccessibilityAction("archive") { true }) }
        assertFailsNaming("\"small\"")
    }

    // A11Y-003 (1): an element acted on only by expanding is selected and measured.
    @Test
    fun aSmallElementWithOnlyExpandFails() {
        showSmallBeside { expand { true } }
        assertFailsNaming("\"small\"")
    }

    // A11Y-003 (1): an element acted on only by dismissing is selected and measured.
    @Test
    fun aSmallElementWithOnlyDismissFails() {
        showSmallBeside { dismiss { true } }
        assertFailsNaming("\"small\"")
    }

    // A11Y-003 (1): an element acted on only through an IME action is selected and measured.
    @Test
    fun aSmallElementWithOnlyAnImeActionFails() {
        showSmallBeside { onImeAction(ImeAction.Done) { true } }
        assertFailsNaming("\"small\"")
    }
}
