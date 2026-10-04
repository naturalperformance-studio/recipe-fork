package app.recipefork.app.library

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.MainActivity
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.file.FileMessage
import app.recipefork.app.file.FileMessageTags
import app.recipefork.app.file.awaitFileMessage
import app.recipefork.app.file.closeFileMessage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A file's message shown when the screen turns is shown again after it, once — never twice — and one closed before
 * it is not shown again.
 */
@RunWith(AndroidJUnit4::class)
class FileMessageRecreationTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun session() = ViewModelProvider(compose.activity)[LibrarySession::class.java]

    private fun dialogs() = compose.onAllNodes(hasTestTag(FileMessageTags.DIALOG)).fetchSemanticsNodes().size

    @Test
    fun aMessageSurvivesARotationOnceAndAClosedOneIsGone() {
        val message = FileMessage(FileMessage.Kind.DAMAGED)
        val text = "This recipe file is damaged, so nothing was added"
        compose.runOnIdle { session().tell(message) }
        compose.awaitFileMessage(text)
        compose.activityRule.scenario.recreate()
        compose.awaitFileMessage(text)
        compose.waitForIdle()
        assertEquals("shown once after the rotation", 1, dialogs())
        compose.closeFileMessage()
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(hasTestTag(LibraryTags.tab(LibraryView.OWN))).fetchSemanticsNodes().isNotEmpty() }
        assertEquals("a closed message is not shown again", 0, dialogs())
    }
}
