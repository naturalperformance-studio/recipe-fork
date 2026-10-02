package app.recipefork.app.file

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import app.recipefork.app.editor.PATIENCE_MS
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

/** Closes the file message's dialog by its OK and waits until it is gone. */
fun ComposeTestRule.closeFileMessage() {
    onNodeWithTag(FileMessageTags.OK).performClick()
    waitUntil(PATIENCE_MS) { onAllNodes(hasTestTag(FileMessageTags.DIALOG)).fetchSemanticsNodes().isEmpty() }
}
