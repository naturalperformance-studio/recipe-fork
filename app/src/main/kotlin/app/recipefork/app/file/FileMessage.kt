package app.recipefork.app.file

import android.content.res.Resources
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.recipefork.app.R
import app.recipefork.core.recipe.VideoLimits

/**
 * What the person is told about a file: what became of one they opened for import (FLOW-064), that an export
 * failed (FLOW-063) — a recipe's or a collection's — or that a saved book opened from another application waits, as another saving or restore runs
 * (FLOW-073). A recipe now in the book ([isNotice]) is said in the book's snackbar, which offers to open the
 * recipe [recipeId]; a failure is said whole, in a dialog closed by its OK. Never in a toast that cuts it short; it is
 * kept as plain data so that it outlives a rotation and the end of the process while it is shown.
 */
data class FileMessage(val kind: Kind, val title: String = "", val recipeId: String = "") {
    enum class Kind { ADDED, ALREADY_IN_BOOK, CANNOT_OPEN, NOT_A_RECIPE_FILE, LATER_VERSION, DAMAGED, BREAKS_RULES, CLIP_TOO_LONG, EXPORT_FAILED, BOOK_BUSY, COLLECTION_SHARE_FAILED }

    /** Whether the message names a recipe the book holds, said in a snackbar that opens it, rather than a failure. */
    val isNotice: Boolean get() = kind == Kind.ADDED || kind == Kind.ALREADY_IN_BOOK

    /** The message in the language of [resources]; a recipe's title is named, shortened when long. */
    fun text(resources: Resources): String {
        val title = shortened(title.ifBlank { resources.getString(R.string.common_untitled_recipe) })
        return when (kind) {
            Kind.ADDED -> resources.getString(R.string.file_imported, title)
            Kind.ALREADY_IN_BOOK -> resources.getString(R.string.file_import_already, title)
            Kind.CANNOT_OPEN -> resources.getString(R.string.file_import_cannot_open)
            Kind.NOT_A_RECIPE_FILE -> resources.getString(R.string.file_import_not_a_recipe)
            Kind.LATER_VERSION -> resources.getString(R.string.file_import_later)
            Kind.DAMAGED -> resources.getString(R.string.file_import_damaged)
            Kind.BREAKS_RULES -> resources.getString(R.string.file_import_breaks_rules)
            Kind.CLIP_TOO_LONG -> resources.getString(R.string.file_import_clip_too_long, VideoLimits.MAX_DURATION.inWholeSeconds.toInt())
            Kind.EXPORT_FAILED -> resources.getString(R.string.file_export_failed)
            Kind.BOOK_BUSY -> resources.getString(R.string.backup_opened_busy)
            Kind.COLLECTION_SHARE_FAILED -> resources.getString(R.string.collection_share_failed)
        }
    }

    /** The message as saved state holds it. */
    fun encoded(): String = kind.name + SEPARATOR + recipeId + SEPARATOR + title

    companion object {
        /** The longest title named whole in a message; a longer one is cut there and ends in "…". */
        const val TITLE_LENGTH = 40

        private const val SEPARATOR = '\n'

        fun decoded(value: String?): FileMessage? {
            val parts = value?.split(SEPARATOR, limit = 3)?.takeIf { it.size == 3 } ?: return null
            val (kind, recipeId, title) = parts
            return Kind.entries.firstOrNull { it.name == kind }?.let { FileMessage(it, title, recipeId) }
        }

        /** [title] as a message names it: whole up to [TITLE_LENGTH] characters, and cut there otherwise. */
        fun shortened(title: String): String {
            val trimmed = title.trim()
            if (trimmed.length <= TITLE_LENGTH) return trimmed
            var end = TITLE_LENGTH
            // A character outside the basic plane is kept whole.
            if (Character.isHighSurrogate(trimmed[end - 1])) end -= 1
            return trimmed.substring(0, end).trimEnd() + "…"
        }
    }
}

/** What the person is told of [this] outcome. */
fun ImportOutcome.message(): FileMessage = when (this) {
    is ImportOutcome.Added -> FileMessage(FileMessage.Kind.ADDED, title, id.value)
    is ImportOutcome.AlreadyInBook -> FileMessage(FileMessage.Kind.ALREADY_IN_BOOK, title, id.value)
    is ImportOutcome.Refused -> FileMessage(
        when (refusal) {
            Refusal.CANNOT_OPEN -> FileMessage.Kind.CANNOT_OPEN
            Refusal.NOT_A_RECIPE_FILE -> FileMessage.Kind.NOT_A_RECIPE_FILE
            Refusal.LATER_VERSION -> FileMessage.Kind.LATER_VERSION
            Refusal.DAMAGED -> FileMessage.Kind.DAMAGED
            Refusal.BREAKS_RULES -> FileMessage.Kind.BREAKS_RULES
            Refusal.CLIP_TOO_LONG -> FileMessage.Kind.CLIP_TOO_LONG
        },
    )
}

object FileMessageTags {
    const val DIALOG = "file_message"
    const val TEXT = "file_message_text"
    const val OK = "file_message_ok"
}

/**
 * Says [message] whole: a dialog whose text wraps over as many lines as it needs, scrolling should the screen be too
 * short for it, read by the screen reader as the dialog opens, and closed by its OK, or by back, through [onDone].
 */
@Composable
fun FileMessageDialog(message: FileMessage, onDone: () -> Unit) {
    val text = message.text(LocalContext.current.resources)
    AlertDialog(
        onDismissRequest = onDone,
        modifier = Modifier.testTag(FileMessageTags.DIALOG),
        text = {
            Text(text, modifier = Modifier.verticalScroll(rememberScrollState()).testTag(FileMessageTags.TEXT))
        },
        confirmButton = {
            TextButton(
                onClick = onDone,
                modifier = Modifier.heightIn(min = 48.dp).testTag(FileMessageTags.OK),
            ) { Text(stringResource(R.string.file_message_ok)) }
        },
    )
}
