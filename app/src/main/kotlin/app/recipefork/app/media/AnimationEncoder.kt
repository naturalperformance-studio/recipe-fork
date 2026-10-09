package app.recipefork.app.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.MediaFormatUtil
import androidx.media3.common.util.UnstableApi
import androidx.media3.muxer.BufferInfo
import androidx.media3.muxer.Muxer
import androidx.media3.transformer.InAppMp4Muxer
import app.recipefork.core.recipe.VideoLimits
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import kotlin.coroutines.coroutineContext

/**
 * Turns an animated GIF or WebP into a step's clip (scenario 3, MEDIA-004): each frame decoded by the platform and
 * composed onto the animation's canvas as its container says, laid on white where it is transparent, brought to the
 * limits' longer edge when larger, and encoded as H.264 at [ClipPlan.bitrate] — one picture per frame, held for the
 * frame's own delay — into an MP4 with no audio track, written by the same muxer as [ClipTransformer]'s, which keeps
 * no metadata (MEDIA-003).
 *
 * Media3 Transformer reads only the first frame of an image, and takes timed pictures with no way to hold back an
 * animation decoded faster than it encodes; the platform's encoder, fed a frame whenever it has room, has neither
 * limit.
 */
@OptIn(UnstableApi::class)
class AnimationEncoder(private val images: AnimatedImages) : ClipConverter {
    override suspend fun convert(source: Uri, target: File) = withContext(Dispatchers.Default) {
        val animation = images.read(source) ?: throw ClipConversionFailed("The image is no animation")
        val size = ClipPlan.converted(PixelSize(animation.width, animation.height), VideoLimits.MAX_LONGER_EDGE)
        try {
            encode(animation, size, target)
        } catch (e: CancellationException) {
            throw e
        } catch (e: ClipConversionFailed) {
            throw e
        } catch (e: Exception) {
            throw ClipConversionFailed("The animation could not be encoded", e)
        }
    }

    private suspend fun encode(animation: AnimatedImage, size: PixelSize, target: File) {
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, size.width, size.height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            setInteger(MediaFormat.KEY_BIT_RATE, ClipPlan.bitrate(size))
            setInteger(MediaFormat.KEY_FRAME_RATE, NOMINAL_FRAME_RATE)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            setInteger(MediaFormat.KEY_COLOR_STANDARD, MediaFormat.COLOR_STANDARD_BT709)
            setInteger(MediaFormat.KEY_COLOR_RANGE, MediaFormat.COLOR_RANGE_LIMITED)
            setInteger(MediaFormat.KEY_COLOR_TRANSFER, MediaFormat.COLOR_TRANSFER_SDR_VIDEO)
        }
        val frames = animation.frames
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        var muxer: Muxer? = null
        try {
            val composer = Composer(animation, size)
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            // Dated to no time, like a converted clip; its last frame held for its own delay before the clip ends.
            val output = InAppMp4Muxer.Factory(OrientationOnly)
                .setVideoDurationUs(animation.duration.inWholeMicroseconds)
                .create(target.path)
            muxer = output
            val info = MediaCodec.BufferInfo()
            var track = -1
            var queued = 0
            var timeUs = 0L
            var ended = false
            while (!ended) {
                coroutineContext.ensureActive()
                if (queued <= frames.size) {
                    val input = codec.dequeueInputBuffer(WAIT_US)
                    if (input >= 0) {
                        if (queued == frames.size) {
                            codec.queueInputBuffer(input, 0, 0, timeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        } else {
                            val picture = composer.compose(frames[queued])
                            val image = codec.getInputImage(input) ?: throw ClipConversionFailed("The encoder took no picture")
                            Yuv.write(picture, image)
                            codec.queueInputBuffer(input, 0, size.width * size.height * 3 / 2, timeUs, 0)
                            timeUs += frames[queued].delay.inWholeMicroseconds
                        }
                        queued++
                    }
                }
                val index = codec.dequeueOutputBuffer(info, WAIT_US)
                when {
                    index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED ->
                        track = output.addTrack(MediaFormatUtil.createFormatFromMediaFormat(codec.outputFormat))
                    index >= 0 -> {
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0 && info.size > 0) {
                            check(track >= 0) { "the encoder stated no format" }
                            val encoded = codec.getOutputBuffer(index)!!
                            encoded.position(info.offset)
                            encoded.limit(info.offset + info.size)
                            // The muxer may hold a sample until it writes its chunk; the encoder's buffer is reused.
                            val sample = ByteBuffer.allocateDirect(info.size).put(encoded).apply { flip() }
                            output.writeSampleData(track, sample, BufferInfo(info.presentationTimeUs, info.size, info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME))
                        }
                        codec.releaseOutputBuffer(index, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) ended = true
                    }
                }
            }
            codec.stop()
            muxer = null
            output.close()
        } finally {
            codec.release()
            // Left unfinished, the file is the caller's to delete; closing it is only letting go of it.
            muxer?.let { runCatching { it.close() } }
        }
    }

    private companion object {
        /** How long the encoder is waited on for a buffer, in microseconds, before the loop looks again. */
        const val WAIT_US = 10_000L

        /**
         * The frame rate the encoder is told to expect. An animation's frames come at their own pace; this only
         * guides the encoder's rate control.
         */
        const val NOMINAL_FRAME_RATE = 15
    }
}

/** Draws an animation's frames one after another onto its canvas, and hands out each result at the clip's size. */
private class Composer(animation: AnimatedImage, private val size: PixelSize) {
    private val canvasBitmap = Bitmap.createBitmap(animation.width, animation.height, Bitmap.Config.ARGB_8888)
    private val canvas = Canvas(canvasBitmap)
    private val picture = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
    private val pictureCanvas = Canvas(picture)
    private val replacing = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC) }
    private val clearing = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
    private val scaling = Paint(Paint.FILTER_BITMAP_FLAG)
    private val whole = Rect(0, 0, size.width, size.height)
    private val decoding = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }

    /** The frame last drawn, and the canvas as it was before it if it is to be restored. */
    private var last: AnimationFrame? = null
    private var beforeLast: Bitmap? = null

    /** The picture once [frame] is drawn: its predecessor disposed of as it says, [frame] drawn over or in its place. */
    fun compose(frame: AnimationFrame): Bitmap {
        last?.let { previous ->
            when (previous.disposal) {
                Disposal.KEEP -> Unit
                Disposal.BACKGROUND -> canvas.drawRect(
                    previous.left.toFloat(),
                    previous.top.toFloat(),
                    (previous.left + previous.width).toFloat(),
                    (previous.top + previous.height).toFloat(),
                    clearing,
                )
                Disposal.PREVIOUS -> beforeLast?.let { canvas.drawBitmap(it, 0f, 0f, replacing) }
            }
        }
        beforeLast?.recycle()
        beforeLast = if (frame.disposal == Disposal.PREVIOUS) canvasBitmap.copy(Bitmap.Config.ARGB_8888, false) else null
        val bytes = frame.still()
        val still = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decoding)
            ?: throw ClipConversionFailed("A frame of the animation could not be decoded")
        canvas.drawBitmap(still, frame.left.toFloat(), frame.top.toFloat(), if (frame.blends) null else replacing)
        still.recycle()
        last = frame
        // A clip has no transparency: what the animation leaves clear is shown on white, as a page shows it.
        pictureCanvas.drawColor(Color.WHITE)
        pictureCanvas.drawBitmap(canvasBitmap, null, whole, scaling)
        return picture
    }
}

/** Writes a picture into an encoder's YUV 4:2:0 input as BT.709 limited range, whatever the planes' layout. */
private object Yuv {
    fun write(picture: Bitmap, image: Image) {
        val width = picture.width
        val height = picture.height
        val argb = IntArray(width * height)
        picture.getPixels(argb, 0, width, 0, 0, width, height)
        val (yPlane, uPlane, vPlane) = image.planes
        val luma = yPlane.buffer
        val row = ByteArray(width)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val c = argb[y * width + x]
                row[x] = luma((c shr 16) and 0xff, (c shr 8) and 0xff, c and 0xff).toByte()
            }
            if (yPlane.pixelStride == 1) {
                luma.position(y * yPlane.rowStride)
                luma.put(row)
            } else {
                for (x in 0 until width) luma.put(y * yPlane.rowStride + x * yPlane.pixelStride, row[x])
            }
        }
        val u = uPlane.buffer
        val v = vPlane.buffer
        for (y in 0 until height / 2) {
            for (x in 0 until width / 2) {
                var r = 0
                var g = 0
                var b = 0
                for (i in 0..1) {
                    for (j in 0..1) {
                        val c = argb[(2 * y + i) * width + 2 * x + j]
                        r += (c shr 16) and 0xff
                        g += (c shr 8) and 0xff
                        b += c and 0xff
                    }
                }
                r /= 4
                g /= 4
                b /= 4
                u.put(y * uPlane.rowStride + x * uPlane.pixelStride, (((-26 * r - 87 * g + 112 * b + 128) shr 8) + 128).toByte())
                v.put(y * vPlane.rowStride + x * vPlane.pixelStride, (((112 * r - 102 * g - 10 * b + 128) shr 8) + 128).toByte())
            }
        }
    }

    private fun luma(r: Int, g: Int, b: Int): Int = ((47 * r + 157 * g + 16 * b + 128) shr 8) + 16
}
