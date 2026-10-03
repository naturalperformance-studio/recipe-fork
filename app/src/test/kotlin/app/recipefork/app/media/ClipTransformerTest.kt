package app.recipefork.app.media

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** What Transformer is asked to make of a clip (MEDIA-004): its picture alone, as H.264, in standard range. */
@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(UnstableApi::class)
class ClipTransformerTest {
    private val source = Uri.parse("content://media/external/video/media/7")

    // An HDR clip is tone-mapped rather than kept HDR, which would let Transformer fall back to 10-bit HEVC.
    @Test
    fun aClipIsAskedForAsH264InStandardRange() {
        val composition = ClipTransformer.composition(source, PixelSize(1920, 1080), PixelSize(1280, 720))

        assertEquals(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL, composition.hdrMode)
        assertEquals(MimeTypes.VIDEO_H264, ClipTransformer.VIDEO_MIME_TYPE)
    }

    @Test
    fun onlyThePictureOfTheOneClipIsAskedFor() {
        val composition = ClipTransformer.composition(source, PixelSize(1920, 1080), PixelSize(1280, 720))

        val sequence = composition.sequences.single()
        assertEquals(setOf(C.TRACK_TYPE_VIDEO), sequence.trackTypes)
        val item = sequence.editedMediaItems.single()
        assertEquals(source, item.mediaItem.localConfiguration!!.uri)
        assertTrue(item.removeAudio)
        assertTrue(item.effects.videoEffects.single() is Presentation)
    }

    @Test
    fun aClipWithinTheLimitIsNotScaled() {
        val item = ClipTransformer.composition(source, PixelSize(640, 360), PixelSize(640, 360)).sequences.single().editedMediaItems.single()

        assertTrue(item.effects.videoEffects.isEmpty())
    }
}
