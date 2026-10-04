package app.recipefork.app.library

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.performScrollTo

/** The node, scrolled into view first when it stands in something that scrolls — the row of quick filters, the sheet. */
internal fun SemanticsNodeInteraction.scrolledTo(): SemanticsNodeInteraction {
    var parent = fetchSemanticsNode().parent
    while (parent != null) {
        if (SemanticsActions.ScrollBy in parent.config) return performScrollTo()
        parent = parent.parent
    }
    return this
}
