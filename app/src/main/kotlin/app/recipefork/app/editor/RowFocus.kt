package app.recipefork.app.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Which row of a list — an ingredient row, a step — is to take the focus in its first field, with the keyboard shown:
 * a row just added, or the next row the keyboard's Next goes to. The row takes it once it is on screen and this window
 * has its focus back from any menu that was open.
 */
@Stable
internal class RowFocus {
    /** The position of the row asked to take the focus; null when none is. */
    var asked by mutableStateOf<Int?>(null)
        private set

    fun ask(position: Int) {
        asked = position
    }

    /** The row at [position] has taken what it was asked: none is asked any more. */
    fun taken(position: Int) {
        if (asked == position) asked = null
    }

    /** Called by the row at [position]: where it is the one asked, its first field, [requester], takes the focus. */
    @Composable
    fun TakenBy(position: Int, requester: FocusRequester) {
        if (asked != position) return
        val keyboard = LocalSoftwareKeyboardController.current
        val window = LocalWindowInfo.current
        LaunchedEffect(this, position) {
            snapshotFlow { window.isWindowFocused }.first { it }
            runCatching { requester.requestFocus() }
            keyboard?.show()
            asked = null
        }
    }

    /**
     * Called inside a sheet that holds the row at [position]: where it is the one asked, its first field, [requester],
     * takes the focus as soon as the sheet is laid out, with the keyboard up.
     */
    @Composable
    fun TakenInSheet(position: Int, requester: FocusRequester) {
        val keyboard = LocalSoftwareKeyboardController.current
        val window = LocalWindowInfo.current
        // The sheet's own scope, so that the request outlives the ask it answers.
        val focusing = rememberCoroutineScope()
        if (asked != position) return
        LaunchedEffect(this, position) {
            taken(position)
            focusing.launch {
                withFrameNanos {}
                runCatching { requester.requestFocus() }
                // Asked once the field has started its input, and again once the sheet's window has the focus, should
                // that come after.
                withFrameNanos {}
                keyboard?.show()
                snapshotFlow { window.isWindowFocused }.first { it }
                keyboard?.show()
            }
        }
    }
}
