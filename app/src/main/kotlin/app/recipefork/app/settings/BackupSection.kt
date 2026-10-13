package app.recipefork.app.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.recipefork.app.R
import app.recipefork.app.backup.BackupNotice
import app.recipefork.app.backup.BackupState
import app.recipefork.app.backup.BackupWork
import app.recipefork.app.backup.BookBackup
import app.recipefork.app.backup.BookFileRefusal
import app.recipefork.app.library.DestructiveConfirm
import app.recipefork.app.ui.WordFitText
import java.time.LocalDate

/**
 * The serverless build's "Backup" section (FLOW-073): the book saved to a file through the system's create-document
 * picker, and restored from one through its open-document picker. What runs shows its progress in its item, with
 * Cancel; a restore into a book that holds recipes asks "Add to the book" or "Replace the book", and Replace is
 * confirmed once more. Each ending is said once in [snackbars]. All of it is [backup]'s, which outlives the screen.
 */
@Composable
internal fun BackupSection(backup: BookBackup, snackbars: SnackbarHostState) {
    val state by backup.state.collectAsState()
    val name = stringResource(R.string.backup_file_name, LocalDate.now().toString())
    val saveTo = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BookBackup.MIME)) { it?.let(backup::save) }
    // A provider may not know the file's type: any file is offered, and the reader refuses what is no book.
    val restoreFrom = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(backup::restoreFrom) }
    val free = state is BackupState.Idle || state is BackupState.Ended
    val working = state as? BackupState.Working
    Text(
        stringResource(R.string.backup_title),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp).semantics { heading() },
    )
    BackupItem(
        headline = stringResource(R.string.backup_save),
        hint = stringResource(R.string.backup_save_hint),
        working = working?.takeIf { it.work == BackupWork.SAVING },
        enabled = free,
        tag = SettingsTags.BACKUP_SAVE,
        onCancel = backup::cancel,
    ) { saveTo.launch(name) }
    BackupItem(
        headline = stringResource(R.string.backup_restore),
        hint = stringResource(R.string.backup_restore_hint),
        working = working?.takeIf { it.work != BackupWork.SAVING },
        enabled = free,
        tag = SettingsTags.BACKUP_RESTORE,
        onCancel = backup::cancel,
    ) { restoreFrom.launch(arrayOf("*/*")) }
    when (val asking = state) {
        is BackupState.Asking -> RestoreQuestion(asking.inBook, onAdd = backup::add, onReplace = backup::replace, onCancel = backup::cancel)
        is BackupState.ConfirmingReplace -> ReplaceConfirmation(asking.inBook, onReplace = backup::confirmReplace, onCancel = backup::cancel)
        else -> Unit
    }
    val ended = (state as? BackupState.Ended)?.notice
    val said = ended?.let { said(it) }
    LaunchedEffect(ended) {
        if (ended == null || said == null) return@LaunchedEffect
        val long = ended is BackupNotice.Refused || ended == BackupNotice.SaveFailed || ended == BackupNotice.RestoreFailed
        snackbars.showSnackbar(said, duration = if (long) SnackbarDuration.Long else SnackbarDuration.Short)
        backup.noticeSaid(ended)
    }
}

/**
 * An item of the section: its name and beneath it what it does — or, while its work runs, what runs with its progress,
 * and Cancel at its end.
 */
@Composable
private fun BackupItem(
    headline: String,
    hint: String,
    working: BackupState.Working?,
    enabled: Boolean,
    tag: String,
    onCancel: () -> Unit,
    onClick: () -> Unit,
) {
    val dimmed = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    ListItem(
        headlineContent = { WordFitText(headline) },
        supportingContent = {
            if (working == null) {
                WordFitText(hint)
            } else {
                Column {
                    WordFitText(stringResource(working.work.label()))
                    val progress = Modifier.fillMaxWidth().padding(top = 8.dp).testTag(SettingsTags.BACKUP_PROGRESS)
                    working.done?.let { LinearProgressIndicator(progress = { it }, modifier = progress) } ?: LinearProgressIndicator(progress)
                }
            }
        },
        trailingContent = working?.let {
            {
                TextButton(onClick = onCancel, modifier = Modifier.heightIn(min = 48.dp).testTag(SettingsTags.BACKUP_CANCEL)) {
                    Text(stringResource(R.string.backup_cancel))
                }
            }
        },
        colors = if (enabled || working != null) ListItemDefaults.colors() else ListItemDefaults.colors(headlineColor = dimmed),
        modifier = Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick).testTag(tag),
    )
}

private fun BackupWork.label(): Int = when (this) {
    BackupWork.SAVING -> R.string.backup_saving
    BackupWork.READING -> R.string.backup_reading
    BackupWork.ADDING, BackupWork.REPLACING -> R.string.backup_restoring
}

/**
 * Asked before a restore into a book that holds [inBook] recipes: add to it ([onAdd]), replace it ([onReplace]), or
 * neither ([onCancel]). The two ways are the dialog's own rows under the question, each one tap, the whole width its
 * target: Add, which keeps every recipe the book holds, first; Replace under it in the error colour, confirmed once
 * more ([ReplaceConfirmation]). Its one button is Cancel, so that no row of buttons stacks at a large font scale; the
 * question scrolls where the screen is too short for it.
 */
@Composable
internal fun RestoreQuestion(inBook: Int, onAdd: () -> Unit, onReplace: () -> Unit, onCancel: () -> Unit) = AlertDialog(
    onDismissRequest = onCancel,
    modifier = Modifier.testTag(SettingsTags.BACKUP_QUESTION),
    title = { WordFitText(stringResource(R.string.backup_ask_title)) },
    text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            WordFitText(pluralStringResource(R.plurals.backup_ask_text, inBook, inBook))
            Spacer(Modifier.height(8.dp))
            RestoreWay(R.drawable.ic_add_item, R.string.backup_add, SettingsTags.BACKUP_ADD, MaterialTheme.colorScheme.onSurface, onAdd)
            RestoreWay(R.drawable.ic_import_file, R.string.backup_replace, SettingsTags.BACKUP_REPLACE, MaterialTheme.colorScheme.error, onReplace)
        }
    },
    confirmButton = { DialogButton(R.string.backup_cancel, SettingsTags.BACKUP_QUESTION_CANCEL, onCancel) },
)

/** One way of [RestoreQuestion]: its [icon] and [label] in [colour] on the dialog's own colour; a tap takes it. */
@Composable
private fun RestoreWay(@DrawableRes icon: Int, @StringRes label: Int, tag: String, colour: Color, onClick: () -> Unit) = ListItem(
    headlineContent = { WordFitText(stringResource(label)) },
    // Said by the words beside it.
    leadingContent = { Icon(painterResource(icon), contentDescription = null) },
    colors = ListItemDefaults.colors(
        containerColor = AlertDialogDefaults.containerColor,
        headlineColor = colour,
        leadingIconColor = colour,
    ),
    modifier = Modifier.heightIn(min = 56.dp).clickable(role = Role.Button, onClick = onClick).testTag(tag),
)

/** Replacing confirmed once more ([onReplace]), naming the [inBook] recipes that go; it cannot be undone. */
@Composable
internal fun ReplaceConfirmation(inBook: Int, onReplace: () -> Unit, onCancel: () -> Unit) = AlertDialog(
    onDismissRequest = onCancel,
    modifier = Modifier.testTag(SettingsTags.BACKUP_CONFIRMATION),
    title = { WordFitText(stringResource(R.string.backup_replace_title)) },
    text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            WordFitText(pluralStringResource(R.plurals.backup_replace_text, inBook, inBook))
        }
    },
    confirmButton = { DestructiveConfirm(R.string.backup_replace_confirm, SettingsTags.BACKUP_REPLACE_CONFIRM, onClick = onReplace) },
    dismissButton = { DialogButton(R.string.backup_cancel, SettingsTags.BACKUP_CONFIRMATION_CANCEL, onCancel) },
)

@Composable
private fun DialogButton(label: Int, tag: String, onClick: () -> Unit) =
    TextButton(onClick = onClick, modifier = Modifier.heightIn(min = 48.dp).testTag(tag)) { Text(stringResource(label)) }

/** What [notice] says. */
@Composable
private fun said(notice: BackupNotice): String = when (notice) {
    is BackupNotice.Saved -> pluralStringResource(R.plurals.backup_saved, notice.recipes, notice.recipes)
    is BackupNotice.Restored -> pluralStringResource(R.plurals.backup_restored, notice.recipes, notice.recipes)
    is BackupNotice.Added -> {
        val added = pluralStringResource(R.plurals.backup_added, notice.added, notice.added)
        if (notice.alreadyHeld == 0) {
            added
        } else {
            stringResource(R.string.backup_added_and_held, added, pluralStringResource(R.plurals.backup_already_held, notice.alreadyHeld, notice.alreadyHeld))
        }
    }
    is BackupNotice.Refused -> stringResource(
        when (notice.refusal) {
            BookFileRefusal.NOT_A_BOOK_FILE -> R.string.backup_refused_not_a_book
            BookFileRefusal.DAMAGED -> R.string.backup_refused_damaged
            BookFileRefusal.NEWER -> R.string.backup_refused_newer
            BookFileRefusal.CANNOT_READ -> R.string.backup_refused_cannot_read
        },
    )
    BackupNotice.SaveFailed -> stringResource(R.string.backup_save_failed)
    BackupNotice.RestoreFailed -> stringResource(R.string.backup_restore_failed)
}
