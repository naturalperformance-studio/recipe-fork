package app.recipefork.app.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.recipefork.app.R
import app.recipefork.app.book.BookName
import app.recipefork.app.media.AnimatedImages
import app.recipefork.app.media.ClipSources
import app.recipefork.app.media.ClipStore
import app.recipefork.app.media.PhotoAttaching
import app.recipefork.app.media.PhotoImage
import app.recipefork.app.media.PhotoNotice
import app.recipefork.app.media.PhotoSources
import app.recipefork.app.media.PhotoStore
import app.recipefork.app.media.SourceItem
import app.recipefork.app.media.SourceSheet
import app.recipefork.app.media.rememberClipSources
import app.recipefork.app.media.rememberPhotoSources
import app.recipefork.core.recipe.Media
import app.recipefork.core.recipe.MediaKind
import kotlinx.coroutines.CoroutineScope

/**
 * Where the steps' photographs and clips are kept — [clips] converts the clips, where clips are offered — and the
 * scope their work runs in, which outlives the screen.
 */
internal class StepPhotos(
    val store: PhotoStore,
    val book: BookName,
    val saving: CoroutineScope,
    val clips: ClipStore? = null,
    /** Where each step's work is held past the activity being recreated; none, and it is the screen's alone. */
    val work: StepMediaWork? = null,
    /** Tells an animated GIF or WebP picked for a step (scenario 3); none, and every picture picked is cropped. */
    val animated: AnimatedImages? = null,
    /** Turns a step's animation into a silent clip and keeps it, as [clips] does a clip. */
    val animations: ClipStore? = null,
)

@Composable
internal fun rememberStepPhotos(
    store: PhotoStore,
    book: BookName,
    saving: CoroutineScope,
    clips: ClipStore? = null,
    work: StepMediaWork? = null,
    animated: AnimatedImages? = null,
    animations: ClipStore? = null,
): StepPhotos = remember(store, book, saving, clips, work, animated, animations) {
    StepPhotos(store, book, saving, clips, work, animated, animations)
}

/**
 * Attaches a photograph or a clip to the step whose key is [key], wherever that step stands once it is ready; a step
 * deleted meanwhile takes none, and nothing of it is kept. Either replaces whatever item the step held (MEDIA-001).
 */
internal fun StepPhotoAttaching(editor: RecipeEditor, photos: StepPhotos, key: Long): PhotoAttaching =
    StepPhotoAttaching({ editor }, photos, key)

/** As above, placing through whichever editor [editor] gives when the item is ready, once [ready] has returned. */
internal fun StepPhotoAttaching(
    editor: () -> RecipeEditor?,
    photos: StepPhotos,
    key: Long,
    ready: suspend () -> Boolean = { true },
): PhotoAttaching {
    fun place(kind: MediaKind, mediaId: String, written: suspend (String?) -> Unit): Boolean {
        val shown = editor() ?: return false
        return shown.stepPosition(key)?.let { shown.setStepMedia(it, mediaId, kind, written) } ?: false
    }
    return PhotoAttaching(
        photos.store, photos.book, photos.saving, PhotoNotice.STEP_REMOVED,
        set = { mediaId, written -> place(MediaKind.PHOTO, mediaId, written) },
        unset = { written -> editor()?.let { shown -> shown.stepPosition(key)?.let { shown.removeStepMedia(it, written) } } ?: false },
        clips = photos.clips,
        setClip = { mediaId, written -> place(MediaKind.VIDEO, mediaId, written) },
        ready = ready,
        animations = photos.animations,
    )
}

/**
 * The media work of one editable step: attaching what the camera or the gallery gives — a photograph or a clip — and
 * their launchers.
 */
internal class StepPhotoControl(val attaching: PhotoAttaching, val sources: PhotoSources, val clipSources: ClipSources)

@Composable
internal fun rememberStepPhotoControl(position: Int, editor: RecipeEditor, photos: StepPhotos): StepPhotoControl {
    val key = editor.stepKey(position)
    val attaching = remember(editor, photos, key) { photos.work?.attaching(key, photos) ?: StepPhotoAttaching(editor, photos, key) }
    val sources = rememberPhotoSources(
        key = STEP_KEY + key,
        onPhoto = { attaching.attach(it) },
        onUnavailable = { attaching.tell(PhotoNotice.UNAVAILABLE) },
        // An animated GIF or WebP picked from the gallery is attached as a silent clip, with no crop (scenario 3).
        animations = photos.animated,
        onAnimation = { attaching.attachAnimation(it) },
    )
    val clipSources = rememberClipSources(
        key = STEP_CLIP_KEY + key,
        onClip = { attaching.attachClip(it) },
        onUnavailable = { attaching.tell(it) },
    )
    // Where the work is held, the steps say what it tells all together — a deleted step's among them.
    if (photos.work == null) SaysPhotoNotices(attaching)
    return StepPhotoControl(attaching, sources, clipSources)
}

/**
 * The media control of the editable step at [position] (FLOW-026 (2)), an icon in the step's row of actions: it opens
 * the media sheet ([SourceSheet]) of taking a photograph with the camera, choosing one from the gallery, recording a clip with the camera,
 * choosing one from the gallery and — once the step carries an item, [media] — removing it.
 */
@Composable
internal fun StepPhotoAction(position: Int, media: Media?, control: StepPhotoControl) {
    var sheet by rememberSaveable { mutableStateOf(false) }
    BlockAction(
        icon = R.drawable.ic_step_photo,
        label = stringResource(R.string.media_step_photo_label, position + 1),
        tag = EditorTags.stepPhoto(position),
        action = stringResource(R.string.media_step_photo_action, position + 1),
        onClick = { sheet = true },
    )
    if (!sheet) return
    val items = listOfNotNull(
        SourceItem(stringResource(R.string.media_step_take), EditorTags.stepPhotoTake(position)) { control.sources.takePhoto() },
        SourceItem(stringResource(R.string.media_step_choose), EditorTags.stepPhotoChoose(position)) { control.sources.choosePhoto() },
        SourceItem(stringResource(R.string.media_step_clip_record), EditorTags.stepClipRecord(position)) { control.clipSources.recordClip() },
        SourceItem(stringResource(R.string.media_step_clip_choose), EditorTags.stepClipChoose(position)) { control.clipSources.chooseClip() },
        when (media?.kind) {
            null -> null
            MediaKind.VIDEO -> SourceItem(stringResource(R.string.media_step_clip_remove), EditorTags.stepPhotoRemove(position), destructive = true) {
                control.attaching.remove(PhotoNotice.STEP_CLIP_REMOVED)
            }
            else -> SourceItem(stringResource(R.string.media_step_remove), EditorTags.stepPhotoRemove(position), destructive = true) { control.attaching.remove() }
        },
    )
    SourceSheet(stringResource(R.string.media_step_photo_label, position + 1), items, onDismiss = { sheet = false })
}

/**
 * Under the step's row of actions, only when there is something to show: its photograph or its clip's first frame,
 * with a small indicator on it while a photograph offered is being prepared — or the indicator alone, where the step
 * has none yet — and, while a clip is being converted, that it is, with its cancel. What the author is told about it
 * is said at the bottom of the editor, not here.
 */
@Composable
internal fun StepPhotoShown(position: Int, media: Media?, photos: StepPhotos, control: StepPhotoControl) {
    val working by control.attaching.working
    val converting by control.attaching.converting
    val indicator = working && !converting
    if (media != null) {
        StepMediaShown(position, media, photos, indicator)
    } else if (indicator) {
        PhotoWorking(Modifier.padding(horizontal = 12.dp, vertical = 4.dp).testTag(EditorTags.stepPhotoWorking(position)))
    }
    if (converting) ClipConverting(position, onCancel = control.attaching::cancelClip)
}

/** The step's item as it is shown: its photograph, or its clip's first frame with the clip's mark. */
@Composable
internal fun StepMediaShown(position: Int, media: Media, photos: StepPhotos, working: Boolean = false) {
    if (media.kind == MediaKind.VIDEO) {
        StepClipFrame(position, photos.store.file(photos.book, media.mediaId), working)
    } else {
        StepPhotoImage(position, media, photos, working)
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

/** As [STEP_KEY], for the file a step's clip is recorded into. */
private const val STEP_CLIP_KEY = "editor.step.clip."
