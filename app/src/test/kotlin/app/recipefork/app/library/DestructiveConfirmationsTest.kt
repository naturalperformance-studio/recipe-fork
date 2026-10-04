package app.recipefork.app.library

import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.Mode
import app.recipefork.app.ui.AppColors
import app.recipefork.app.ui.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Every statement before data is destroyed — deleting an own recipe (FLOW-053 (2)), removing a found one (FLOW-064
 * (6)), clearing saved media (FLOW-060) — names the action on its confirm button and draws it in the theme's error
 * colour; its decline stays in the ordinary colour of a dialog's buttons.
 */
@RunWith(AndroidJUnit4::class)
class DestructiveConfirmationsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun shows(confirm: String, decline: String, label: String, statement: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent { AppTheme { statement() } }
        compose.waitForIdle()
        compose.onNode(hasTestTag(confirm) and hasText(label)).assertExists()
        assertConfirmInErrorColour(compose, confirm, decline)
    }

    @Test
    fun deletingAnOwnRecipe() = shows(LibraryTags.DELETE_CONFIRM, LibraryTags.DELETE_DECLINE, "Delete") {
        DeleteStatement("Soup", onConfirm = {}, onDecline = {})
    }

    @Test
    fun removingAFoundRecipe() = shows(LibraryTags.REMOVE_CONFIRM, LibraryTags.REMOVE_DECLINE, "Remove") {
        RemoveStatement("Soup", onConfirm = {}, onDecline = {})
    }

    @Test
    fun clearingSavedMedia() = shows(LibraryTags.CLEAR_MEDIA_CONFIRM, LibraryTags.CLEAR_MEDIA_DECLINE, "Clear") {
        ClearMediaStatement(Mode.Standard, onConfirm = {}, onDecline = {})
    }
}

/** The colour the text inside the button tagged [tag] is drawn in. */
private fun SemanticsNodeInteractionsProvider.labelColour(tag: String): Color {
    val layouts = mutableListOf<TextLayoutResult>()
    onNode(hasAnyAncestor(hasTestTag(tag)) and hasText("", substring = true), useUnmergedTree = true).fetchSemanticsNode()
        .config[SemanticsActions.GetTextLayoutResult].action!!(layouts)
    return layouts.single().layoutInput.style.color
}

/** The confirm tagged [confirm] is drawn in the theme's error colour, the decline tagged [decline] in its primary. */
fun assertConfirmInErrorColour(compose: SemanticsNodeInteractionsProvider, confirm: String, decline: String) {
    assertEquals("the confirm's colour", AppColors.error, compose.labelColour(confirm))
    assertEquals("the decline's colour", AppColors.primary, compose.labelColour(decline))
}
