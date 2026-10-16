package app.recipefork.app.print

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/** Whether a screen shows the progress of a document being made to print or share ([PrintProgress]). */
fun SemanticsNodeInteractionsProvider.printProgressShown(): Boolean =
    onAllNodes(hasTestTag(PrintTags.PROGRESS), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

/**
 * The progress of a document being made shows as the book's does (v0.38, T-269): a running linear progress, and under
 * it [words] drawn — not only given to the screen reader — just under the node tagged [bar] (the screen's bar, or the
 * tabs under it); the screen reader reads the words and the progress as one, as they appear.
 */
fun SemanticsNodeInteractionsProvider.assertPrintProgress(words: String, bar: String) {
    val node = onNodeWithTag(PrintTags.PROGRESS).fetchSemanticsNode()
    assertEquals(listOf(words), node.config.getOrNull(SemanticsProperties.Text)?.map { it.text })
    val running = SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo) and hasAnyAncestor(hasTestTag(PrintTags.PROGRESS))
    assertEquals("a running progress", 1, onAllNodes(running, useUnmergedTree = true).fetchSemanticsNodes().size)
    assertEquals(LiveRegionMode.Polite, node.config.getOrNull(SemanticsProperties.LiveRegion))
    onNodeWithTag(PrintTags.PROGRESS_WORDS, useUnmergedTree = true).assertIsDisplayed()
    val drawn = onNodeWithTag(PrintTags.PROGRESS_WORDS, useUnmergedTree = true).fetchSemanticsNode()
    assertEquals(listOf(words), drawn.config.getOrNull(SemanticsProperties.Text)?.map { it.text })
    val under = onNodeWithTag(bar, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
    val shown = node.boundsInRoot
    assertTrue("the progress ($shown) is not just under $bar ($under)", shown.top >= under.bottom - 0.5f && shown.top <= under.bottom + 1f)
    assertTrue("the words (${drawn.boundsInRoot}) are not under the progress", drawn.boundsInRoot.top > shown.top && drawn.boundsInRoot.height > 0f)
}
