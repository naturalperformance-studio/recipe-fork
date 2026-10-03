package app.recipefork.app.editor

import android.content.Context
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.text.TextLayoutResult
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.BookName
import app.recipefork.app.media.PhotoAttaching
import app.recipefork.app.media.PhotoNotice
import app.recipefork.app.media.PhotoStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * What the editor says about a photograph — attached, refused, removed, for the cover or a step — said in the editor's
 * one statement host at its bottom: every notice there, whole at the narrowest width and the largest text, read by the
 * screen reader, one at a time, above the keyboard, gone by itself after a while or swiped away.
 */
@RunWith(AndroidJUnit4::class)
class EditorStatementsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val work = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val attaching = PhotoAttaching(
        PhotoStore(context.filesDir), BookName.Pool, work, PhotoNotice.REMOVED,
        set = { _, _ -> false },
        unset = { false },
    )

    @After
    fun close() = work.cancel()

    private fun show() {
        compose.setContent {
            MaterialTheme {
                val host = remember { SnackbarHostState() }
                val scope = rememberCoroutineScope()
                val statements = remember { EditorStatements(host, scope) }
                Box(Modifier.fillMaxSize().testTag(ROOT)) {
                    CompositionLocalProvider(LocalEditorStatements provides statements) { SaysPhotoNotices(attaching) }
                    EditorStatementHost(host, Modifier.align(Alignment.BottomCenter))
                }
            }
        }
        compose.waitForIdle()
    }

    private fun text(notice: PhotoNotice) = notice.said(context.resources)

    private fun tell(notice: PhotoNotice) = compose.runOnIdle { attaching.tell(notice) }

    private fun statements() = compose.onAllNodes(hasTestTag(EditorTags.STATEMENT), useUnmergedTree = true).fetchSemanticsNodes()

    private fun textNode(text: String): SemanticsNode = compose.onNode(
        hasText(text) and hasAnyAncestor(hasTestTag(EditorTags.STATEMENT)),
        useUnmergedTree = true,
    ).fetchSemanticsNode()

    // Every photo notice — attached, each refusal, no picker, removed for the cover and for a step — is said in the
    // statement host; a new one replaces the one shown, so one shows at a time.
    @Test
    fun everyNoticeIsSaidAtTheBottomOneAtATime() {
        show()
        PhotoNotice.entries.forEach { notice ->
            tell(notice)
            compose.awaitStatement(text(notice))
            assertEquals(notice.name, 1, statements().size)
        }
        // The same notice twice is said twice.
        tell(PhotoNotice.PROCESSED)
        compose.awaitStatement(text(PhotoNotice.PROCESSED))
        compose.runOnIdle { attaching.tell(PhotoNotice.UNREADABLE); attaching.tell(PhotoNotice.PROCESSED) }
        compose.awaitStatement(text(PhotoNotice.PROCESSED))
        assertEquals(1, statements().size)
    }

    // The screen reader announces it: its text sits in a polite live region.
    @Test
    fun theScreenReaderAnnouncesIt() {
        show()
        tell(PhotoNotice.UNREADABLE)
        val text = text(PhotoNotice.UNREADABLE)
        compose.awaitStatement(text)
        val live = SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)
        compose.onNode(live and hasAnyDescendant(hasText(text)), useUnmergedTree = true).assertExists()
        val node = textNode(text)
        assertTrue(SemanticsProperties.InvisibleToUser !in node.config)
        assertTrue(SemanticsProperties.HideFromAccessibility !in node.config)
    }

    // At 360 dp and twice the font size, every notice is laid out whole: nothing ellipsized, cut or clipped.
    @Test
    @Config(qualifiers = "en-w360dp-h640dp", fontScale = 2f)
    fun everyNoticeIsWholeAtTheNarrowestWidthAndLargestText() = saidWhole()

    @Test
    @Config(qualifiers = "ru-w360dp-h640dp", fontScale = 2f)
    fun everyNoticeIsWholeAtTheNarrowestWidthAndLargestTextInRussian() = saidWhole()

    private fun saidWhole() {
        show()
        PhotoNotice.entries.forEach { notice ->
            val text = text(notice)
            tell(notice)
            compose.awaitStatement(text)
            compose.waitForIdle()
            val node = textNode(text)
            val layout = layoutOf(node)
            assertEquals(text, layout.layoutInput.text.text)
            assertEquals("measured at twice the font size", 2f, layout.layoutInput.density.fontScale, 0.001f)
            assertFalse("“$text” is cut in height", layout.didOverflowHeight)
            (0 until layout.lineCount).forEach { line ->
                assertFalse("line $line of “$text” is ellipsized", layout.isLineEllipsized(line))
                assertTrue("line $line of “$text” runs past its room", layout.getLineRight(line) <= layout.size.width + 0.5f)
            }
            assertEquals("“$text” ends on its last line", text.length, layout.getLineEnd(layout.lineCount - 1))
            val shown = node.boundsInRoot
            assertEquals("“$text” is shown in full height", node.size.height.toFloat(), shown.height, 1f)
            assertEquals("“$text” is shown in full width", node.size.width.toFloat(), shown.width, 1f)
        }
    }

    private fun layoutOf(node: SemanticsNode): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action!!(layouts)
        return layouts.single()
    }

    // A statement goes by itself after a few seconds, driven by the test clock: still there after five, gone after
    // the long duration.
    @Test
    fun aStatementGoesByItself() {
        show()
        tell(PhotoNotice.PROCESSED)
        compose.awaitStatement(text(PhotoNotice.PROCESSED))
        compose.mainClock.autoAdvance = false
        val shownAt = compose.mainClock.currentTime
        compose.mainClock.advanceTimeBy(5_000)
        assertEquals("still there after five seconds", 1, statements().size)
        compose.mainClock.advanceTimeBy(6_000)
        compose.waitUntil(PATIENCE_MS) {
            compose.mainClock.advanceTimeByFrame()
            statements().isEmpty()
        }
        // Gone with the long duration and its fading, not long after.
        assertTrue(compose.mainClock.currentTime - shownAt <= 12_000)
    }

    // A statement swiped away, either way, goes at once.
    @Test
    fun aStatementSwipedAwayGoes() {
        show()
        tell(PhotoNotice.REMOVED)
        compose.awaitStatement(text(PhotoNotice.REMOVED))
        compose.onNodeWithTag(EditorTags.STATEMENT, useUnmergedTree = true).performTouchInput { swipeRight() }
        compose.waitUntil(PATIENCE_MS) { statements().isEmpty() }

        tell(PhotoNotice.STEP_REMOVED)
        compose.awaitStatement(text(PhotoNotice.STEP_REMOVED))
        compose.onNodeWithTag(EditorTags.STATEMENT, useUnmergedTree = true).performTouchInput { swipeLeft() }
        compose.waitUntil(PATIENCE_MS) { statements().isEmpty() }
    }

    // While the keyboard is open, the statement sits above it.
    @Test
    @Config(qualifiers = "w360dp-h640dp")
    fun aStatementSitsAboveAnOpenKeyboard() {
        show()
        tell(PhotoNotice.PROCESSED)
        compose.awaitStatement(text(PhotoNotice.PROCESSED))
        val root = compose.onNodeWithTag(ROOT).fetchSemanticsNode().boundsInRoot
        val before = compose.onNodeWithTag(EditorTags.STATEMENT, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(before.bottom > root.bottom - KEYBOARD_PX)

        compose.runOnUiThread {
            val content = compose.activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
            val keyboard = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, KEYBOARD_PX))
                .setVisible(WindowInsetsCompat.Type.ime(), true)
                .build()
            ViewCompat.dispatchApplyWindowInsets(content, keyboard)
        }

        compose.waitUntil(PATIENCE_MS) {
            val shown = compose.onAllNodes(hasTestTag(EditorTags.STATEMENT), useUnmergedTree = true).fetchSemanticsNodes()
            shown.size == 1 && shown.single().boundsInRoot.bottom <= root.bottom - KEYBOARD_PX + 0.5f
        }
    }

    private companion object {
        const val ROOT = "statements-root"
        const val KEYBOARD_PX = 400
    }
}
