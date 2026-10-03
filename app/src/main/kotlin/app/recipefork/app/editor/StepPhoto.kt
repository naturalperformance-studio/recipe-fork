package app.recipefork.app.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.recipefork.app.R
import app.recipefork.app.book.BookName
import app.recipefork.app.media.PhotoAttaching
import app.recipefork.app.media.PhotoImage
import app.recipefork.app.media.PhotoNotice
import app.recipefork.app.media.PhotoSources
import app.recipefork.app.media.PhotoStore
import app.recipefork.app.media.rememberPhotoSources
import app.recipefork.core.recipe.Media
import kotlinx.coroutines.CoroutineScope

/** Where the steps' photographs are kept, and the scope their work runs in, which outlives the screen. */
internal class StepPhotos(val store: PhotoStore, val book: BookName, val saving: CoroutineScope)

@Composable
internal fun rememberStepPhotos(book: BookName, saving: CoroutineScope): StepPhotos {
    val context = LocalContext.current
    return remember(context, book, saving) { StepPhotos(PhotoStore(context.applicationContext.filesDir), book, saving) }
}

/**
 * Attaches a photograph to the step whose key is [key], wherever that step stands once the photograph is ready; a step
 * deleted meanwhile takes none, and nothing of it is kept.
 */
internal fun StepPhotoAttaching(editor: RecipeEditor, photos: StepPhotos, key: Long): PhotoAttaching = PhotoAttaching(
    photos.store, photos.book, photos.saving, PhotoNotice.STEP_REMOVED,
    set = { mediaId, written -> editor.stepPosition(key)?.let { editor.setStepPhoto(it, mediaId, written) } ?: false },
    unset = { written -> editor.stepPosition(key)?.let { editor.removeStepPhoto(it, written) } ?: false },
)

/** The photograph work of one editable step: attaching what the camera or the gallery gives, and their launchers. */
internal class StepPhotoControl(val attaching: PhotoAttaching, val sources: PhotoSources)

@Composable
internal fun rememberStepPhotoControl(position: Int, editor: RecipeEditor, photos: StepPhotos): StepPhotoControl {
    val key = editor.stepKey(position)
    val attaching = remember(editor, photos, key) { StepPhotoAttaching(editor, photos, key) }
    val sources = rememberPhotoSources(
        key = STEP_KEY + key,
        onPhoto = { attaching.attach(it) },
        onUnavailable = { attaching.tell(PhotoNotice.UNAVAILABLE) },
    )
    SaysPhotoNotices(attaching)
    return StepPhotoControl(attaching, sources)
}

/**
 * The photograph control of the editable step at [position] (FLOW-026 (2)), an icon in the step's row of actions: it
 * opens taking one with the camera, choosing one from the gallery and — once the step carries one — removing it.
 */
@Composable
internal fun StepPhotoAction(position: Int, photo: Media?, control: StepPhotoControl) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        BlockAction(
            icon = R.drawable.ic_step_photo,
            label = stringResource(R.string.media_step_photo_label, position + 1),
            tag = EditorTags.stepPhoto(position),
            action = stringResource(R.string.media_step_photo_action, position + 1),
            onClick = { expanded = !expanded },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.media_step_take)) },
                onClick = {
                    expanded = false
                    control.sources.takePhoto()
                },
                modifier = Modifier.testTag(EditorTags.stepPhotoTake(position)),
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.media_step_choose)) },
                onClick = {
                    expanded = false
                    control.sources.choosePhoto()
                },
                modifier = Modifier.testTag(EditorTags.stepPhotoChoose(position)),
            )
            if (photo != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.media_step_remove)) },
                    onClick = {
                        expanded = false
                        control.attaching.remove()
                    },
                    modifier = Modifier.testTag(EditorTags.stepPhotoRemove(position)),
                )
            }
        }
    }
}

/**
 * Under the step's row of actions, only when there is something to show: its photograph, with a small indicator on it
 * while one offered is being prepared — or the indicator alone, where the step has none yet. What the author is told
 * about it is said at the bottom of the editor, not here.
 */
@Composable
internal fun StepPhotoShown(position: Int, photo: Media?, photos: StepPhotos, control: StepPhotoControl) {
    val working by control.attaching.working
    if (photo != null) {
        StepPhotoImage(position, photo, photos, working)
    } else if (working) {
        PhotoWorking(Modifier.padding(horizontal = 12.dp, vertical = 4.dp).testTag(EditorTags.stepPhotoWorking(position)))
    }
}

/** The step's photograph, named for the screen reader by the step's number; [working] while another is prepared. */
@Composable
internal fun StepPhotoImage(position: Int, photo: Media, photos: StepPhotos, working: Boolean = false) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.fillMaxWidth()) {
            PhotoImage(
                photos.store.file(photos.book, photo.mediaId),
                stringResource(R.string.media_step_description, position + 1),
                Modifier.testTag(EditorTags.stepPhotoImage(position)),
            )
            if (working) {
                PhotoWorking(Modifier.align(Alignment.TopEnd).padding(8.dp).testTag(EditorTags.stepPhotoWorking(position)))
            }
        }
    }
}

/** The prefix of the key a step's camera file is saved under while the camera is in front; the step's key follows. */
private const val STEP_KEY = "editor.step."
