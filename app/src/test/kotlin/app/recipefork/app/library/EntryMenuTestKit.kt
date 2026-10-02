package app.recipefork.app.library

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import app.recipefork.app.editor.PATIENCE_MS

/** Opens the menu of the entry of [recipeId], and waits until its menu shows. */
fun ComposeTestRule.openEntryMenu(recipeId: String) {
    onNodeWithTag(LibraryTags.actions(recipeId)).performClick()
    waitUntil(PATIENCE_MS) {
        onAllNodes(hasTestTag(LibraryTags.actions(recipeId))).fetchSemanticsNodes().isNotEmpty() &&
            listOf(LibraryTags.export(recipeId), LibraryTags.delete(recipeId), LibraryTags.remove(recipeId))
                .any { onAllNodes(hasTestTag(it)).fetchSemanticsNodes().isNotEmpty() }
    }
}

/** Closes the open menu of the entry of [recipeId] without choosing anything. */
fun ComposeTestRule.closeEntryMenu(recipeId: String) {
    onNodeWithTag(LibraryTags.actions(recipeId)).performClick()
    waitUntil(PATIENCE_MS) {
        listOf(LibraryTags.export(recipeId), LibraryTags.delete(recipeId), LibraryTags.remove(recipeId))
            .none { onAllNodes(hasTestTag(it)).fetchSemanticsNodes().isNotEmpty() }
    }
}

/** Chooses the item tagged [item] in the menu of the entry of [recipeId], opening the menu first. */
fun ComposeTestRule.chooseEntryAction(recipeId: String, item: String) {
    openEntryMenu(recipeId)
    waitUntil(PATIENCE_MS) { onAllNodes(hasTestTag(item)).fetchSemanticsNodes().isNotEmpty() }
    onNodeWithTag(item).performClick()
}
