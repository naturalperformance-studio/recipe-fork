package app.recipefork.app.library

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.Mode
import app.recipefork.app.settings.ClearMediaStatement
import app.recipefork.app.settings.SettingsTags
import app.recipefork.app.ui.appColorScheme
import app.recipefork.app.ui.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Every statement before data is destroyed for good — clearing saved media (FLOW-060) — names the action on its
 * confirm button and draws it in the theme's error colour; its decline stays in the ordinary colour of a dialog's
 * buttons. Deleting an own recipe or removing a found one asks nothing: it is undone instead (FLOW-053, RecipeUndoTest).
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
    fun clearingSavedMedia() = shows(SettingsTags.CLEAR_MEDIA_CONFIRM, SettingsTags.CLEAR_MEDIA_DECLINE, "Clear") {
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
    val colours = appColorScheme(ApplicationProvider.getApplicationContext<Context>().resources, dark = false)
    assertEquals("the confirm's colour", colours.error, compose.labelColour(confirm))
    assertEquals("the decline's colour", colours.primary, compose.labelColour(decline))
}
