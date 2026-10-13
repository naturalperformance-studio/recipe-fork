package app.recipefork.app.library

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import app.recipefork.app.editor.PATIENCE_MS

/**
 * Opens the menu of the entry of [recipeId], brought into view first, and waits until its menu shows. The ⋮ is acted
 * on as a screen reader does: brought just into view, it may stand under the view's floating action.
 */
fun ComposeTestRule.openEntryMenu(recipeId: String) {
    onNodeWithTag(LibraryTags.actions(recipeId)).performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
    waitUntil(PATIENCE_MS) {
        onAllNodes(hasTestTag(LibraryTags.actions(recipeId))).fetchSemanticsNodes().isNotEmpty() &&
            menuItems(recipeId).any { onAllNodes(hasTestTag(it)).fetchSemanticsNodes().isNotEmpty() }
    }
}

/** Every item an entry's menu may hold. */
private fun menuItems(recipeId: String) = listOf(
    LibraryTags.addToCollection(recipeId), LibraryTags.addToShopping(recipeId),
    LibraryTags.print(recipeId), LibraryTags.sharePdf(recipeId), LibraryTags.export(recipeId), LibraryTags.delete(recipeId), LibraryTags.remove(recipeId),
)

/** Closes the open menu of the entry of [recipeId] without choosing anything. */
fun ComposeTestRule.closeEntryMenu(recipeId: String) {
    onNodeWithTag(LibraryTags.actions(recipeId)).performClick()
    waitUntil(PATIENCE_MS) {
        menuItems(recipeId).none { onAllNodes(hasTestTag(it)).fetchSemanticsNodes().isNotEmpty() }
    }
}

/** Chooses the item tagged [item] in the menu of the entry of [recipeId], opening the menu first. */
fun ComposeTestRule.chooseEntryAction(recipeId: String, item: String) {
    openEntryMenu(recipeId)
    waitUntil(PATIENCE_MS) { onAllNodes(hasTestTag(item)).fetchSemanticsNodes().isNotEmpty() }
    onNodeWithTag(item).performClick()
}
