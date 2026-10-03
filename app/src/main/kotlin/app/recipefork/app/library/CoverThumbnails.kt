package app.recipefork.app.library

import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import app.recipefork.app.book.BookName
import app.recipefork.app.media.PhotoStore
import app.recipefork.app.media.decodeFilling
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** The frame every cover is shown in: 4:3 landscape, as every photograph is cropped to (MEDIA-014). */
const val COVER_RATIO = 4f / 3f

/**
 * The covers the entries of the book show, each decoded once, off the main thread and no larger than it is shown,
 * and kept while the screen is, so that a list scrolled back and forth reads no file twice. A cover that cannot be
 * read is not kept, and is tried again when its entry next shows.
 */
class CoverThumbnails(
    /** Decodes a file to a picture filling a frame of the given size; replaced in a test to watch it. */
    private val decode: (File, IntSize) -> ImageBitmap? = { file, size -> decodeFilling(file, size.width, size.height) },
    maxBytes: Int = (Runtime.getRuntime().maxMemory() / 8).coerceIn(1L, Int.MAX_VALUE.toLong()).toInt(),
) {
    private val kept = object : LruCache<Key, ImageBitmap>(maxBytes) {
        override fun sizeOf(key: Key, value: ImageBitmap) = value.asAndroidBitmap().byteCount
    }

    /** The picture of [file] already decoded for a frame of [size], if it was. */
    fun cached(file: File, size: IntSize): ImageBitmap? = kept.get(Key(file.path, size))

    /** The picture of [file] filling a frame of [size] pixels, decoded off the main thread once. */
    suspend fun load(file: File, size: IntSize): ImageBitmap? {
        kept.get(Key(file.path, size))?.let { return it }
        val image = withContext(Dispatchers.IO) { decode(file, size) } ?: return null
        kept.put(Key(file.path, size), image)
        return image
    }

    /** A picture kept: its file, and the frame it was decoded for. */
    private data class Key(val path: String, val size: IntSize)
}

/** The covers of the book's entries, kept for as long as the composition that remembers them. */
@Composable
fun rememberCoverThumbnails(): CoverThumbnails = remember { CoverThumbnails() }

/**
 * The cover [mediaId] of [book] across the whole width of an entry, in the 4:3 frame, cropped about its centre. It is
 * decorative — the entry already reads its title. A cover whose file is there holds its frame from the first frame,
 * tinted until the picture is read — and stays tinted when it cannot be read — so that the entry's text never moves;
 * with no cover, or its file missing, nothing takes its place.
 */
@Composable
internal fun CoverThumbnail(
    mediaId: String?,
    book: BookName,
    filesDirectory: File?,
    thumbnails: CoverThumbnails,
    tag: String,
) {
    if (mediaId == null) return
    val context = LocalContext.current
    // Whether the file is there is decided once per cover, not on every recomposition.
    val file = remember(book, mediaId, filesDirectory) {
        PhotoStore(filesDirectory ?: context.applicationContext.filesDir).file(book, mediaId).takeIf { it.isFile }
    } ?: return
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .aspectRatio(COVER_RATIO)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .testTag(LibraryTags.coverSlot(tag)),
    ) {
        // Decoded at the size it is shown at, never larger.
        val size = with(LocalDensity.current) {
            IntSize(maxWidth.roundToPx().coerceAtLeast(1), maxHeight.roundToPx().coerceAtLeast(1))
        }
        val image by produceState(thumbnails.cached(file, size), file, size) {
            if (value == null) value = thumbnails.load(file, size)
        }
        image?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().testTag(tag),
            )
        }
    }
}
