package app.recipefork.app.card

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import app.recipefork.app.editor.PATIENCE_MS

/** Waits until the card is open, then takes its back action to the recipe book. */
fun ComposeTestRule.backThroughTheCard() {
    awaitCard()
    onNodeWithTag(CardTags.BACK).performClick()
    waitForIdle()
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
