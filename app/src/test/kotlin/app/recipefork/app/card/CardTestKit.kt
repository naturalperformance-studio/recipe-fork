package app.recipefork.app.card

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import app.recipefork.app.editor.PATIENCE_MS
import org.junit.Assert.assertTrue

/** Waits until the card is open, then takes its back action to the recipe book. */
fun ComposeTestRule.backThroughTheCard() {
    awaitCard()
    onNodeWithTag(CardTags.BACK).performClick()
    waitForIdle()
}

/** The element tagged [tag] is laid out at least the card's [CardTarget] in each direction (A11Y-003 (2), A11Y-007). */
fun SemanticsNodeInteractionsProvider.assertCardTarget(tag: String) {
    val node = onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
    val density = node.layoutInfo.density.density
    val width = node.size.width / density
    val height = node.size.height / density
    assertTrue("$tag is $width x $height dp, less than $CardTarget", width + 0.5f >= CardTarget.value && height + 0.5f >= CardTarget.value)
}

/** Every text laid out within the element tagged [tag], itself included, as it is laid out now. */
fun SemanticsNodeInteractionsProvider.textLayoutsWithin(tag: String): List<TextLayoutResult> =
    onAllNodes(hasTestTag(tag) or hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true).fetchSemanticsNodes()
        .mapNotNull { node ->
            node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.let { action ->
                mutableListOf<TextLayoutResult>().also { action(it) }.firstOrNull()
            }
        }

/**
 * Every text within the element tagged [tag] reads whole (A11Y-004): given room, nothing of it cut off, and broken
 * only between words, never inside one; and the element lies within the screen's width.
 */
fun SemanticsNodeInteractionsProvider.assertWhole(tag: String) {
    val node = onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
    val screen = node.layoutInfo.coordinates.let { generateSequence(it) { c -> c.parentCoordinates }.last() }.size.width
    assertTrue("$tag lies within the screen: ${node.boundsInRoot} in $screen px",
        node.boundsInRoot.left >= -0.5f && node.boundsInRoot.right <= screen + 0.5f)
    val layouts = textLayoutsWithin(tag)
    assertTrue("$tag shows text", layouts.isNotEmpty())
    layouts.forEach { layout ->
        val text = layout.layoutInput.text.text
        assertTrue("\"$text\" in $tag has room (${layout.size.width} px)", layout.size.width > 0)
        assertTrue("\"$text\" in $tag is not cut off",
            !layout.didOverflowHeight && !layout.multiParagraph.didExceedMaxLines &&
                layout.getLineEnd(layout.lineCount - 1) == text.length &&
                (0 until layout.lineCount).all { layout.getLineRight(it) <= layout.size.width + 0.5f })
        (0 until layout.lineCount - 1).forEach { line ->
            val end = layout.getLineEnd(line)
            assertTrue("\"$text\" in $tag breaks only between words, not after \"${text.substring(layout.getLineStart(line), end)}\"",
                text[end - 1].isWhitespace() || text.getOrNull(end)?.isWhitespace() == true)
        }
    }
}

/** Waits until the card is open and shows its content or its message. */
fun ComposeTestRule.awaitCard() = waitUntil(PATIENCE_MS) {
    listOf(CardTags.CONTENT, CardTags.MISSING).any { onAllNodes(hasTestTag(it)).fetchSemanticsNodes().size == 1 }
}

/**
 * The tag of the ingredient line or step first at the top of the card's scrolling content: the first whose bottom is
 * below the top of the screen. Measured unclipped, so that a part scrolled out of sight is never taken for one in it.
 */
fun ComposeTestRule.firstAtTheTop(): String {
    val top = onNodeWithTag(CardTags.CONTENT).fetchSemanticsNode().positionInRoot.y
    val part = Regex("card-(ingredient|step)-\\d+")
    return onAllNodes(SemanticsMatcher("an ingredient line or a step") { node ->
        node.config.getOrNull(SemanticsProperties.TestTag)?.matches(part) == true
    }).fetchSemanticsNodes()
        .filter { it.positionInRoot.y + it.size.height > top + 0.5f }
        .minBy { it.positionInRoot.y }
        .config[SemanticsProperties.TestTag]
}
