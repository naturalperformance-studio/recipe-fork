package app.recipefork.app.collections

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.zIndex
import app.recipefork.app.R
import app.recipefork.app.file.CollectionExport
import app.recipefork.app.file.FileMessage
import app.recipefork.app.library.LibrarySession
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.recipe.CollectionId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The sharing of a collection as a file, from its ⋮ (v0.36): [start] writes the file and opens the share and save
 * sheet on it. A tap while the file is being written starts no second one; [progress] says how far meanwhile. A
 * sharing that fails says so ([FileMessage.Kind.COLLECTION_SHARE_FAILED]) and changes nothing.
 */
@Stable
class CollectionShare internal constructor(
    private val scope: CoroutineScope,
    private val run: suspend (progress: (Float) -> Unit) -> Boolean,
    private val failed: () -> Unit,
) {
    /** Whether a file is being written. */
    var running by mutableStateOf(false)
        private set

    /** The share of the file written, while one is being written and how far is known; null otherwise. */
    var progress by mutableStateOf<Float?>(null)
        private set

    fun start() {
        if (running) return
        running = true
        progress = null
        scope.launch {
            try {
                val told = run { done ->
                    // Changing by a hundredth at least, as the book's progress does.
                    val shown = progress
                    if (shown == null || done - shown >= PROGRESS_STEP || (done >= 1f && shown < 1f)) progress = done
                }
                if (!told) failed()
            } finally {
                running = false
                progress = null
            }
        }
    }
}

/** The sharing of the collection [id] of the active book, its failure told through [session]. */
@Composable
fun rememberCollectionShare(
    id: CollectionId,
    session: LibrarySession,
    collections: CollectionRepository,
    recipes: RecipeRepository,
): CollectionShare {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return remember(id, session, collections, recipes, context, scope) {
        CollectionShare(
            scope,
            run = { progress ->
                CollectionExport.share(
                    context, recipes, collections, id,
                    sheetTitle = { name -> context.getString(R.string.collection_share_sheet, name) },
                    progress = progress,
                )
            },
            failed = { session.tell(FileMessage(FileMessage.Kind.COLLECTION_SHARE_FAILED)) },
        )
    }
}

/**
 * The progress of the collection's file being written, in place over the top of the screen's content, shifting
 * nothing: how far, once known, and that it is under way before. The screen reader names what is under way.
 */
@Composable
fun CollectionShareProgress(share: CollectionShare, modifier: Modifier = Modifier) {
    if (!share.running) return
    val label = stringResource(R.string.collection_sharing)
    val tagged = modifier.zIndex(1f).fillMaxWidth().semantics { contentDescription = label }.testTag(CollectionShareTags.PROGRESS)
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val done = share.progress
    if (done == null) LinearProgressIndicator(tagged, trackColor = track) else LinearProgressIndicator({ done }, tagged, trackColor = track)
}

private const val PROGRESS_STEP = 0.01f

object CollectionShareTags {
    const val SHARE = "collection-share"
    const val PROGRESS = "collection-share-progress"
}
