package app.recipefork.app.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.text.TextLayoutResult
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

/**
 * A11Y-004 at large text: every text a part of the screen lays out breaks, if at all, between words — after a space or
 * a dash, or before a space — read from each text's own [TextLayoutResult], and none is cut.
 */
object WholeWords {
    private val DASHES = setOf('-', '‐', '–', '—')

    private val laidOutText = SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult) and
        !SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText)

    /** The layout of every text, not a field's, that [within] matches in the unmerged tree. */
    fun layouts(screen: SemanticsNodeInteractionsProvider, within: SemanticsMatcher): List<TextLayoutResult> =
        screen.onAllNodes(within and laidOutText, useUnmergedTree = true).fetchSemanticsNodes().flatMap { node ->
            mutableListOf<TextLayoutResult>().also { node.config[SemanticsActions.GetTextLayoutResult].action!!(it) }
        }

    /**
     * No text that [within] matches breaks inside a word or is cut — none ends in an ellipsis or runs past its
     * bottom; [where] names the part in the message. Returns the texts checked, by their words.
     */
    fun assertWhole(screen: SemanticsNodeInteractionsProvider, within: SemanticsMatcher, where: String): List<String> {
        val layouts = layouts(screen, within)
        assertTrue("$where shows texts", layouts.isNotEmpty())
        layouts.forEach { layout ->
            val text = layout.layoutInput.text.text
            for (line in 0 until layout.lineCount - 1) {
                val end = layout.getLineEnd(line)
                if (end <= 0 || end >= text.length) continue
                val before = text[end - 1]
                val between = before.isWhitespace() || text[end].isWhitespace() || before in DASHES
                assertTrue("$where: “$text” breaks inside a word after line $line (“${text.substring(0, end)}|”)", between)
            }
            assertFalse("$where: “$text” is cut", layout.didOverflowHeight || (0 until layout.lineCount).any(layout::isLineEllipsized))
        }
        return layouts.map { it.layoutInput.text.text }
    }
}
