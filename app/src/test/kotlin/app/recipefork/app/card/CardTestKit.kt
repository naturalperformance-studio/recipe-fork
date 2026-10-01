package app.recipefork.app.card

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
