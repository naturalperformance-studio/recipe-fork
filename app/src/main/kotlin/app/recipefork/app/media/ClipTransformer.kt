package app.recipefork.app.media

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Metadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.container.Mp4TimestampData
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.InAppMp4Muxer
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import app.recipefork.core.recipe.VideoLimits
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Converts a clip with Media3 Transformer (decisions/20260927-android-client-stack.md): decoded the right way up,
 * scaled down to the limits' longer edge when it is larger, its audio dropped, and encoded as H.264 at
 * [ClipPlan.BITRATE] into an MP4 written by Media3's own muxer, which is told to keep nothing of the original's
 * metadata — no place, no capture time, no device — but the orientation it writes itself (MEDIA-003, MEDIA-004).
 */
@OptIn(UnstableApi::class)
class ClipTransformer(context: Context) : ClipConverter {
    private val context = context.applicationContext

    override suspend fun convert(source: Uri, target: File) {
        val shown = withContext(Dispatchers.IO) { shownSize(source) }
            ?: throw ClipConversionFailed("The clip states no picture size")
        val size = ClipPlan.converted(shown, VideoLimits.MAX_LONGER_EDGE)
        val effects = if (size == shown) {
            Effects.EMPTY
        } else {
            Effects(emptyList(), listOf(Presentation.createForWidthAndHeight(size.width, size.height, Presentation.LAYOUT_SCALE_TO_FIT)))
        }
        val item = EditedMediaItem.Builder(MediaItem.fromUri(source))
            .setRemoveAudio(true)
            .setEffects(effects)
            .build()
        // Transformer is driven from the thread it was built on; the main one is always there to drive it.
        withContext(Dispatchers.Main) {
            val done = CompletableDeferred<Unit>()
            val transformer = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setEncoderFactory(
                    DefaultEncoderFactory.Builder(context)
                        .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(ClipPlan.BITRATE).build())
                        .build(),
                )
                .setMuxerFactory(InAppMp4Muxer.Factory(OrientationOnly))
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        done.complete(Unit)
                    }

                    override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                        done.completeExceptionally(ClipConversionFailed("The clip could not be converted", exportException))
                    }
                })
                .build()
            try {
                transformer.start(item, target.path)
            } catch (e: IllegalStateException) {
                throw ClipConversionFailed("The conversion did not start", e)
            }
            try {
                done.await()
            } catch (e: CancellationException) {
                // Returns once the conversion has stopped, so nothing is written to the file after it is deleted.
                transformer.cancel()
                throw e
            }
        }
    }

    private fun shownSize(source: Uri): PixelSize? = try {
        MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(context, source)
            fun read(key: Int) = retriever.extractMetadata(key)?.trim()?.toIntOrNull()
            val width = read(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH) ?: return@use null
            val height = read(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT) ?: return@use null
            val rotation = read(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION) ?: 0
            if (width <= 0 || height <= 0) null else ClipPlan.displayed(width, height, rotation)
        }
    } catch (e: Exception) {
        null
    }
}

/**
 * Keeps none of the metadata the original carried — its place, its capture time, the device's own entries — and dates
 * the file to no time at all rather than the moment of conversion, which for a clip just recorded is the moment it was
 * shot (MEDIA-003). The orientation is not among these: the muxer writes it from the video track itself.
 */
@OptIn(UnstableApi::class)
internal object OrientationOnly : InAppMp4Muxer.MetadataProvider {
    override fun updateMetadataEntries(metadataEntries: MutableSet<Metadata.Entry>) {
        metadataEntries.clear()
        metadataEntries.add(Mp4TimestampData(0, 0))
    }
}
