package app.recipefork.app.library

import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.recipefork.app.book.BookName
import app.recipefork.app.media.PhotoStore
import app.recipefork.app.media.decodeThumbnail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** The side of the square a cover is shown in on a book's entry. */
val THUMBNAIL_SIZE = 56.dp

/**
 * The covers the entries of the book show, each decoded once, off the main thread and no larger than a thumbnail,
 * and kept while the screen is, so that a list scrolled back and forth reads no file twice. A cover that cannot be
 * read is not kept, and is tried again when its entry next shows.
 */
class CoverThumbnails(
    /** Decodes a file to a picture of at least the given side; replaced in a test to watch it. */
    private val decode: (File, Int) -> ImageBitmap? = ::decodeThumbnail,
    maxBytes: Int = (Runtime.getRuntime().maxMemory() / 32).coerceIn(1L, Int.MAX_VALUE.toLong()).toInt(),
) {
    private val kept = object : LruCache<String, ImageBitmap>(maxBytes) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.asAndroidBitmap().byteCount
    }

    /** The picture of [file] already decoded, if it was. */
    fun cached(file: File): ImageBitmap? = kept.get(file.path)

    /** The picture of [file] at least [size] pixels on its shorter side, decoded off the main thread once. */
    suspend fun load(file: File, size: Int): ImageBitmap? {
        kept.get(file.path)?.let { return it }
        val image = withContext(Dispatchers.IO) { decode(file, size) } ?: return null
        kept.put(file.path, image)
        return image
    }
}

/** The covers of the book's entries, kept for as long as the composition that remembers them. */
@Composable
fun rememberCoverThumbnails(): CoverThumbnails = remember { CoverThumbnails() }

/**
 * The cover [mediaId] of [book] as a small rounded square at an entry's start, cropped about its centre. It is
 * decorative — the entry already reads its title. A cover whose file is there holds its square from the first
 * frame, tinted until the picture is read, so that the entry's text never moves; with no cover, or its file
 * missing, nothing takes its place.
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
    val size = with(LocalDensity.current) { THUMBNAIL_SIZE.roundToPx() }.coerceAtLeast(1)
    val image by produceState(thumbnails.cached(file), file, size) {
        if (value == null) value = thumbnails.load(file, size)
    }
    Box(
        Modifier
            .padding(end = 12.dp)
            .size(THUMBNAIL_SIZE)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .testTag(LibraryTags.coverSlot(tag)),
    ) {
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
