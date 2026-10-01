package app.recipefork.app.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Assert.fail

/**
 * Checks every screen repeats: A11Y-003 (touch targets) and A11Y-005 (every interactive element announces
 * itself). Run them on each state of a screen a test puts it in.
 */
object AccessibilityChecks {
    /** An element the reader acts on: clicked, long-pressed, typed into, toggled or adjusted. */
    val isInteractive = SemanticsMatcher("is interactive") { node ->
        val config = node.config
        SemanticsActions.OnClick in config || SemanticsActions.OnLongClick in config ||
            SemanticsActions.SetText in config || SemanticsActions.SetProgress in config ||
            SemanticsProperties.ToggleableState in config
    }

    /**
     * A11Y-003 (1): every interactive element is at least [min] by [min] as laid out.
     *
     * The size read is the node's own layout size, not its touch bounds: Compose widens the touch bounds of any
     * clickable to the platform's minimum touch target, so a check on them could never fail.
     */
    fun assertTouchTargets(screen: SemanticsNodeInteractionsProvider, min: Dp = 48.dp, expectAtLeast: Int = 1) {
        val nodes = screen.onAllNodes(isInteractive).fetchSemanticsNodes()
        assertTrue("expected at least $expectAtLeast interactive elements, found ${nodes.size}", nodes.size >= expectAtLeast)
        val small = nodes.filter { node ->
            val minPx = with(node.layoutInfo.density) { min.toPx() }
            node.size.width + 0.5f < minPx || node.size.height + 0.5f < minPx
        }.map { node ->
            val density = node.layoutInfo.density.density
            "${announcedLabel(node).ifBlank { "node ${node.id}" }}: " +
                "${node.size.width / density} x ${node.size.height / density} dp laid out"
        }
        if (small.isNotEmpty()) fail("interactive elements smaller than $min by $min: $small")
    }

    /**
     * A11Y-005 (1): every interactive element carries a label the screen reader speaks — its visible text or a
     * content description — and each click action names what it does, either through that label or through an
     * action label of its own.
     */
    fun assertInteractiveElementsLabelled(screen: SemanticsNodeInteractionsProvider, expectAtLeast: Int = 1) {
        val nodes = screen.onAllNodes(isInteractive).fetchSemanticsNodes()
        assertTrue("expected at least $expectAtLeast interactive elements, found ${nodes.size}", nodes.size >= expectAtLeast)
        nodes.forEach { node ->
            if (announcedLabel(node).isBlank()) fail("interactive element ${node.id} announces nothing: ${node.config}")
        }
    }

    /** What the screen reader speaks for [node]: its content description or its text. */
    fun announcedLabel(node: SemanticsNode): String {
        val description = node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
        val text = node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
        return (description + text).joinToString(" ").trim()
    }

    /** The label the screen reader speaks for [node]'s click action ("double tap to …"), if it has one. */
    fun clickActionLabel(node: SemanticsNode): String? = node.config.getOrNull(SemanticsActions.OnClick)?.label
}
