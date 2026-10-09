package app.recipefork.app.ui

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.SnackbarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A snackbar's action — the book's Undo and Open, the editor's Undo — in the snackbar's action colour, laid out 48 dp
 * tall at least, not the 40 dp Material's button draws by default (A11Y-003).
 */
@Composable
fun SnackbarAction(label: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        colors = ButtonDefaults.textButtonColors(contentColor = SnackbarDefaults.actionColor),
        modifier = Modifier.heightIn(min = 48.dp),
    ) { Text(label) }
}
