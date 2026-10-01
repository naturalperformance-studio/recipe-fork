package app.recipefork.app.ui

import androidx.compose.ui.semantics.AccessibilityAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsPropertyKey
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
 *
 * Both checks see what the semantics tree sees, and so what the screen reader sees. A tap handled only by
 * `pointerInput` (for example `detectTapGestures`) with no semantics action is invisible to these checks and to
 * the screen reader alike; what keeps such an element out of the product is A11Y-005 — an element announces what
 * it does — which it cannot meet without a semantics action. The touch-target check does not catch it on its own.
 */
object AccessibilityChecks {
    /**
     * Semantics actions that carry an [AccessibilityAction] but are not something the reader performs on an
     * element: queries the framework makes, the system's text translation on every text node, focus movement,
     * and scrolling.
     *
     * Scrolling is left out on purpose: a scroll container is not taken to be an interactive element in A11Y-003's
     * sense — the reader acts on what it holds, each of which is measured on its own — and a scroll container
     * reached only by scrolling is not selected.
     */
    private val notPerformedByTheReader: Set<SemanticsPropertyKey<*>> = setOf(
        SemanticsActions.GetTextLayoutResult,
        SemanticsActions.GetScrollViewportLength,
        SemanticsActions.SetTextSubstitution,
        SemanticsActions.ShowTextSubstitution,
        SemanticsActions.ClearTextSubstitution,
        SemanticsActions.RequestFocus,
        SemanticsActions.ScrollBy,
        SemanticsActions.ScrollByOffset,
        SemanticsActions.ScrollToIndex,
        SemanticsActions.PageUp,
        SemanticsActions.PageDown,
        SemanticsActions.PageLeft,
        SemanticsActions.PageRight,
    )

    /**
     * An element the reader acts on: any node that offers a semantics action the reader performs — click,
     * long-click, text entry, progress, expand, collapse, dismiss, an IME action, a custom action and any other —
     * or that is toggleable. Selection is by the kind of key, not a fixed list: every [AccessibilityAction] and
     * every custom-action list counts, except those in [notPerformedByTheReader].
     */
    val isInteractive = SemanticsMatcher("is interactive") { node -> isInteractive(node) }

    private fun isInteractive(node: SemanticsNode): Boolean =
        SemanticsProperties.ToggleableState in node.config ||
            node.config.any { (key, value) ->
                key !in notPerformedByTheReader &&
                    (value is AccessibilityAction<*> || (key == SemanticsActions.CustomActions && value is List<*> && value.isNotEmpty()))
            }

    /**
     * A11Y-003 (1): every interactive element is at least [min] by [min] as laid out.
     *
     * The size read is the node's own layout size, not its touch bounds: Compose widens the touch bounds of any
     * clickable to the platform's minimum touch target, so a check on them could never fail.
     *
     * The nodes are read from the unmerged tree. On the merged tree an element that is interactive only through a
     * semantics action, and does not merge its descendants itself, folds into a merging parent's node, and only
     * the parent would be measured.
     *
     * Each element too small is named as the reader can find it on screen: see [nameOf].
     */
    fun assertTouchTargets(screen: SemanticsNodeInteractionsProvider, min: Dp = 48.dp, expectAtLeast: Int = 1) {
        val nodes = screen.onAllNodes(isInteractive, useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue("expected at least $expectAtLeast interactive elements, found ${nodes.size}", nodes.size >= expectAtLeast)
        val small = nodes.filter { node ->
            val minPx = with(node.layoutInfo.density) { min.toPx() }
            node.size.width + 0.5f < minPx || node.size.height + 0.5f < minPx
        }.map { node ->
            val density = node.layoutInfo.density.density
            "${nameOf(node)}: ${node.size.width / density} x ${node.size.height / density} dp laid out"
        }
        if (small.isNotEmpty()) fail("interactive elements smaller than $min by $min: $small")
    }

    /**
     * A name for [node] the reader can find on screen, taken from the unmerged tree in this order: its own text or
     * content description, or its descendants'; else its click or long-click action label; else its test tag;
     * else the text of the nearest merging ancestor that has one, as `inside "…"`. Only when none exists is the
     * internal semantics id given, and the name says so.
     */
    fun nameOf(node: SemanticsNode): String {
        labelWithin(node).takeIf { it.isNotBlank() }?.let { return "\"$it\"" }
        val actionLabel = node.config.getOrNull(SemanticsActions.OnClick)?.label?.takeIf { it.isNotBlank() }
            ?: node.config.getOrNull(SemanticsActions.OnLongClick)?.label?.takeIf { it.isNotBlank() }
        actionLabel?.let { return "\"$it\" (action label)" }
        node.config.getOrNull(SemanticsProperties.TestTag)?.takeIf { it.isNotBlank() }?.let { return "tag \"$it\"" }
        generateSequence(node.parent) { it.parent }
            .filter { it.config.isMergingSemanticsOfDescendants }
            .map { labelWithin(it) }
            .firstOrNull { it.isNotBlank() }
            ?.let { return "unlabelled element inside \"$it\"" }
        return "node ${node.id} (no text, content description, action label, test tag or labelled merging ancestor)"
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

    /** [node]'s own label or, on the unmerged tree where its text sits in its children, theirs. */
    private fun labelWithin(node: SemanticsNode): String =
        announcedLabel(node).ifBlank { node.children.joinToString(" ") { labelWithin(it) }.trim() }

    /** The label the screen reader speaks for [node]'s click action ("double tap to …"), if it has one. */
    fun clickActionLabel(node: SemanticsNode): String? = node.config.getOrNull(SemanticsActions.OnClick)?.label
}
