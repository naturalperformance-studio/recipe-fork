package app.recipefork.app.library

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.recipefork.app.R

/**
 * The statement shown before anything is destroyed (FLOW-053 (2)): the recipe titled [title] goes at once, with its
 * photographs and video, and cannot be brought back. Nothing is deleted until it is confirmed; declining it changes
 * nothing. Shown alike from the book's entry and from the card, whose buttons are [target] tall.
 */
@Composable
internal fun DeleteStatement(title: String, onConfirm: () -> Unit, onDecline: () -> Unit, target: Dp = 48.dp) {
    AlertDialog(
        onDismissRequest = onDecline,
        modifier = Modifier.testTag(LibraryTags.DELETE_STATEMENT),
        title = { Text(stringResource(R.string.library_delete_title, title.ifBlank { stringResource(R.string.common_untitled_recipe) })) },
        text = { Text(stringResource(R.string.library_delete_statement)) },
        confirmButton = { DestructiveConfirm(R.string.library_delete_confirm, LibraryTags.DELETE_CONFIRM, target, onConfirm) },
        dismissButton = { Decline(R.string.library_delete_decline, LibraryTags.DELETE_DECLINE, target, onDecline) },
    )
}

/**
 * The statement shown before the found recipe titled [title] leaves the book with its content (FLOW-009 (4),
 * FLOW-064 (6)), from the book's entry or from the card.
 */
@Composable
internal fun RemoveStatement(title: String, onConfirm: () -> Unit, onDecline: () -> Unit, target: Dp = 48.dp) {
    AlertDialog(
        onDismissRequest = onDecline,
        modifier = Modifier.testTag(LibraryTags.REMOVE_STATEMENT),
        title = { Text(stringResource(R.string.library_remove_title, title.ifBlank { stringResource(R.string.common_untitled_recipe) })) },
        text = { Text(stringResource(R.string.library_remove_statement)) },
        confirmButton = { DestructiveConfirm(R.string.library_remove_confirm, LibraryTags.REMOVE_CONFIRM, target, onConfirm) },
        dismissButton = { Decline(R.string.library_remove_decline, LibraryTags.REMOVE_DECLINE, target, onDecline) },
    )
}

/**
 * The confirm button of a statement before data is destroyed: it names the action, and is drawn in the theme's error
 * colour so that it reads as destructive.
 */
@Composable
internal fun DestructiveConfirm(@StringRes label: Int, tag: String, target: Dp = 48.dp, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
        modifier = Modifier.heightIn(min = target).widthIn(min = target).testTag(tag),
    ) { Text(stringResource(label)) }
}

/** The button that declines a statement, changing nothing. */
@Composable
private fun Decline(@StringRes label: Int, tag: String, target: Dp, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.heightIn(min = target).widthIn(min = target).testTag(tag),
    ) { Text(stringResource(label)) }
}
