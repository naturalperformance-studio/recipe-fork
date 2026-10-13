package app.recipefork.app.media

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Clips made on the device for its tests, as a phone's camera would hand them: H.264 video, optionally with an AAC
 * sound track, an orientation and the place it was shot, in an MP4 written by the platform's own muxer.
 */
object TestClips {
    private class Sample(val bytes: ByteArray, val timeUs: Long, val flags: Int)

    private class Track(val format: MediaFormat, val samples: List<Sample>)

    /**
     * Writes a clip of [width] x [height] pixels lasting [seconds] at [fps] into [file], with a sound track if [sound] —
     * running [soundPastMs] milliseconds past the picture, as a phone's camera may write it — a key frame every [keyFrames] seconds (every frame at 0), turned by [rotation] degrees for display and marked
     * as shot at [location] (latitude, longitude) if given.
     */
    fun write(
        file: File,
        width: Int,
        height: Int,
        seconds: Int,
        fps: Int = 24,
        bitrate: Int = 8_000_000,
        keyFrames: Int = 1,
        sound: Boolean = true,
        soundPastMs: Int = 0,
        rotation: Int = 0,
        location: Pair<Float, Float>? = null,
    ): File {
        file.parentFile?.mkdirs()
        val tracks = buildList {
            add(video(width, height, seconds * fps, fps, bitrate, keyFrames))
            if (sound) add(sound(seconds, soundPastMs))
        }
        val muxer = MediaMuxer(file.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        try {
            muxer.setOrientationHint(rotation)
            location?.let { (latitude, longitude) -> muxer.setLocation(latitude, longitude) }
            val ids = tracks.map { muxer.addTrack(it.format) }
            muxer.start()
            val info = MediaCodec.BufferInfo()
            tracks.zip(ids)
                .flatMap { (track, id) -> track.samples.map { id to it } }
                .sortedBy { (_, sample) -> sample.timeUs }
                .forEach { (id, sample) ->
                    info.set(0, sample.bytes.size, sample.timeUs, sample.flags)
                    muxer.writeSampleData(id, ByteBuffer.wrap(sample.bytes), info)
                }
            muxer.stop()
        } finally {
            muxer.release()
        }
        return file
    }

    /** The content address another app would hand [file] through, as a gallery or the camera does. */
    fun uri(context: Context, file: File): Uri = FileProvider.getUriForFile(context, context.packageName + ".capture", file)

    /** Where a test's clips are written: the directory the capture provider shares. */
    fun directory(context: Context) = File(context.cacheDir, "capture")

    /** A file that is no clip at all. */
    fun junk(file: File): File = file.apply { parentFile?.mkdirs(); writeBytes(Random(1).nextBytes(64_000)) }

    private fun video(width: Int, height: Int, frames: Int, fps: Int, bitrate: Int, keyFrames: Int): Track {
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, keyFrames)
        }
        // A detailed picture panning steadily, as a hand-held shot of a busy scene: every frame is new to the eye but
        // predictable from the last, so an encoder that may predict compresses it, and one told to code every frame
        // whole (an interval of none) makes a heavy original.
        val pan = 4
        val wide = width + pan * frames
        val random = Random(42)
        val block = 8
        val columns = (wide + block - 1) / block
        val levels = ByteArray(columns * ((height + block - 1) / block)).also { random.nextBytes(it) }
        val texture = ByteArray(wide * height) { i -> levels[(i / wide / block) * columns + (i % wide) / block] }
        return encode(format, frames) { index, codec, input ->
            val image = codec.getInputImage(input)!!
            val luma = image.planes[0]
            val offset = index * pan
            for (row in 0 until height) {
                val buffer = luma.buffer
                if (luma.pixelStride == 1) {
                    buffer.position(row * luma.rowStride)
                    buffer.put(texture, row * wide + offset, width)
                } else {
                    for (column in 0 until width) buffer.put(row * luma.rowStride + column * luma.pixelStride, texture[row * wide + offset + column])
                }
            }
            for (plane in image.planes.drop(1)) {
                val buffer = plane.buffer
                for (row in 0 until height / 2) {
                    for (column in 0 until width / 2) buffer.put(row * plane.rowStride + column * plane.pixelStride, 128.toByte())
                }
            }
            width * height * 3 / 2 to index * 1_000_000L / fps
        }
    }

    private fun sound(seconds: Int, pastMs: Int): Track {
        val rate = 44_100
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, rate, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 64_000)
        }
        val chunk = 1024
        val chunks = (seconds * rate + pastMs * rate / 1000) / chunk
        return encode(format, chunks) { index, codec, input ->
            val buffer = codec.getInputBuffer(input)!!
            buffer.clear()
            for (i in 0 until chunk) {
                val t = (index * chunk + i).toDouble() / rate
                buffer.putShort((sin(2 * PI * 440 * t) * 8000).toInt().toShort())
            }
            chunk * 2 to index * chunk * 1_000_000L / rate
        }
    }

    /** Encodes [count] inputs, each filled by [fill], which returns its size and time; returns what came out. */
    private fun encode(format: MediaFormat, count: Int, fill: (Int, MediaCodec, Int) -> Pair<Int, Long>): Track {
        val codec = MediaCodec.createEncoderByType(format.getString(MediaFormat.KEY_MIME)!!)
        val samples = mutableListOf<Sample>()
        var output: MediaFormat? = null
        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            val info = MediaCodec.BufferInfo()
            var queued = 0
            var ended = false
            var lastTimeUs = 0L
            while (!ended) {
                if (queued <= count) {
                    val input = codec.dequeueInputBuffer(10_000)
                    if (input >= 0) {
                        if (queued == count) {
                            codec.queueInputBuffer(input, 0, 0, lastTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        } else {
                            val (size, timeUs) = fill(queued, codec, input)
                            lastTimeUs = timeUs
                            codec.queueInputBuffer(input, 0, size, timeUs, 0)
                        }
                        queued++
                    }
                }
                val index = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> output = codec.outputFormat
                    index >= 0 -> {
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0 && info.size > 0) {
                            val buffer = codec.getOutputBuffer(index)!!
                            buffer.position(info.offset)
                            buffer.limit(info.offset + info.size)
                            val bytes = ByteArray(info.size).also { buffer.get(it) }
                            samples += Sample(bytes, info.presentationTimeUs, info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME)
                        }
                        codec.releaseOutputBuffer(index, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) ended = true
                    }
                }
            }
            codec.stop()
        } finally {
            codec.release()
        }
        return Track(checkNotNull(output) { "the encoder stated no format" }, samples)
    }
}
