package app.recipefork.app.file

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.library.LibraryTags
import org.junit.Assert.assertNull
import org.robolectric.shadows.ShadowToast

/** Waits until [text] is said whole in the file message's dialog — and checks that no toast said anything. */
fun ComposeTestRule.awaitFileMessage(text: String) {
    waitUntil(PATIENCE_MS) {
        onAllNodes(hasTestTag(FileMessageTags.TEXT) and hasText(text) and hasAnyAncestor(isDialog()))
            .fetchSemanticsNodes().size == 1
    }
    assertNull(ShadowToast.getTextOfLatestToast())
}

/**
 * Waits until [text] is said whole in the book's snackbar — no dialog open, and no toast — and returns the node of its
 * text.
 */
fun ComposeTestRule.awaitFileNotice(text: String): androidx.compose.ui.semantics.SemanticsNode {
    val said = hasText(text) and hasAnyAncestor(hasTestTag(LibraryTags.SNACKBAR))
    waitUntil(PATIENCE_MS) { onAllNodes(said, useUnmergedTree = true).fetchSemanticsNodes().size == 1 }
    assertNull(ShadowToast.getTextOfLatestToast())
    org.junit.Assert.assertEquals("no dialog", 0, onAllNodes(isDialog()).fetchSemanticsNodes().size)
    return onAllNodes(said, useUnmergedTree = true).fetchSemanticsNodes().single()
}

/** Closes the file message's dialog by its OK and waits until it is gone. */
fun ComposeTestRule.closeFileMessage() {
    onNodeWithTag(FileMessageTags.OK).performClick()
    waitUntil(PATIENCE_MS) { onAllNodes(hasTestTag(FileMessageTags.DIALOG)).fetchSemanticsNodes().isEmpty() }
}
