package app.recipefork.app.library

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import app.recipefork.app.editor.PATIENCE_MS
import org.junit.Assert.assertEquals

/** Whether the book's snackbar shows a notice. */
fun ComposeTestRule.noticeShown(): Boolean =
    onAllNodes(hasTestTag(LibraryTags.NOTICE), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

/**
 * Waits until the book's snackbar shows one notice reading [text] with the action [action], and that no dialog asks
 * anything meanwhile.
 */
fun ComposeTestRule.awaitRemovalNotice(text: String, action: String) {
    waitUntil(PATIENCE_MS) {
        val inNotice = hasAnyAncestor(hasTestTag(LibraryTags.NOTICE))
        onAllNodes(hasTestTag(LibraryTags.NOTICE), useUnmergedTree = true).fetchSemanticsNodes().size == 1 &&
            onAllNodes(hasText(text) and inNotice, useUnmergedTree = true).fetchSemanticsNodes().size == 1 &&
            onAllNodes(hasText(action) and inNotice, useUnmergedTree = true).fetchSemanticsNodes().size == 1
    }
    assertEquals("a dialog asks something", 0, onAllNodes(isDialog()).fetchSemanticsNodes().size)
}

/** Takes the action [action] — Undo — on the notice the book's snackbar shows, and waits until the notice is gone. */
fun ComposeTestRule.takeNoticeAction(action: String) {
    onNode(hasText(action) and hasAnyAncestor(hasTestTag(LibraryTags.NOTICE)), useUnmergedTree = true).performClick()
    waitUntil(PATIENCE_MS) { !noticeShown() }
}

/** Swipes away the notice the book's snackbar shows, and waits until it is gone. */
fun ComposeTestRule.swipeAwayNotice() {
    onNodeWithTag(LibraryTags.NOTICE, useUnmergedTree = true).performTouchInput { swipeRight() }
    waitUntil(PATIENCE_MS) { !noticeShown() }
}
