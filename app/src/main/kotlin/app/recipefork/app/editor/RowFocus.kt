package app.recipefork.app.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import kotlinx.coroutines.flow.first

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
}
