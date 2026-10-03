package app.recipefork.app.card

import android.content.Context
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.runBlocking
import android.graphics.SurfaceTexture
import android.view.Surface
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowMediaMetadataRetriever
import org.robolectric.shadows.ShadowMediaPlayer
import org.robolectric.shadows.util.DataSource
import java.io.File
import java.io.IOException

/**
 * The platform player of the card's clips (MEDIA-004): muted at volume zero, looping, never asking for audio focus;
 * a file it cannot play is told as such, never a crash; released, it holds nothing.
 */
@RunWith(AndroidJUnit4::class)
class PlatformClipPlayerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val file = File(context.filesDir, "clip.mp4").apply { writeBytes(ByteArray(64)) }
    private val source = DataSource.toDataSource(file.path)
    private val media = MediaPlayer()
    private val player = PlatformClipPlayer(media)
    private val heard = mutableListOf<String>()
    private var size: Triple<Int, Int, Int>? = null
    private val listener = object : ClipPlayer.Listener {
        override fun ready(width: Int, height: Int, rotation: Int) {
            heard += "ready"
            size = Triple(width, height, rotation)
        }

        override fun failed() {
            heard += "failed"
        }
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Test
    fun aClipPlaysMutedAndLoopingWithoutAudioFocus() {
        ShadowMediaPlayer.addMediaInfo(source, ShadowMediaPlayer.MediaInfo(4_000, 0))
        runBlocking { player.open(file, listener) }
        idle()
        assertEquals(listOf("ready"), heard)
        assertEquals(0f, shadowOf(media).leftVolume)
        assertEquals(0f, shadowOf(media).rightVolume)
        assertTrue(media.isLooping)

        player.play()
        assertEquals(ShadowMediaPlayer.State.STARTED, shadowOf(media).state)
        assertEquals(0f, shadowOf(media).leftVolume)
        assertNull(shadowOf(context.getSystemService(AudioManager::class.java)).lastAudioFocusRequest)

        player.pause()
        assertEquals(ShadowMediaPlayer.State.PAUSED, shadowOf(media).state)
        player.release()
        assertEquals(ShadowMediaPlayer.State.END, shadowOf(media).state)
        // Nothing reaches a released player.
        player.play()
        player.surface(null)
        player.release()
    }

    // Asked to play before it has read the clip, it plays once read.
    @Test
    fun aClipAskedToPlayEarlyPlaysOnceRead() {
        ShadowMediaPlayer.addMediaInfo(source, ShadowMediaPlayer.MediaInfo(4_000, 0))
        runBlocking { player.open(file, listener) }
        player.play()
        idle()
        assertEquals(ShadowMediaPlayer.State.STARTED, shadowOf(media).state)
        player.release()
    }

    @Test
    fun aFileThatCannotBeOpenedIsUnplayable() {
        ShadowMediaPlayer.addException(source, IOException("not a clip"))
        runBlocking { player.open(file, listener) }
        idle()
        assertEquals(listOf("failed"), heard)
        player.play()
        player.pause()
        player.release()
    }

    @Test
    fun aClipThatFailsWhilePlayingIsUnplayable() {
        ShadowMediaPlayer.addMediaInfo(source, ShadowMediaPlayer.MediaInfo(4_000, 0))
        runBlocking { player.open(file, listener) }
        idle()
        player.play()
        shadowOf(media).invokeErrorListener(MediaPlayer.MEDIA_ERROR_UNKNOWN, 0)
        assertEquals(listOf("ready", "failed"), heard)
        player.release()
    }

    // Read before there was a surface, a paused clip draws its frame once one arrives (animations off).
    @Test
    fun aPausedClipDrawsItsFrameWhenTheSurfaceArrives() {
        ShadowMediaPlayer.addMediaInfo(source, ShadowMediaPlayer.MediaInfo(4_000, 0))
        runBlocking { player.open(file, listener) }
        idle()
        var seeks = 0
        media.setOnSeekCompleteListener { seeks++ }
        val surface = Surface(SurfaceTexture(0))
        player.surface(surface)
        idle()
        assertEquals(1, seeks)
        // The same surface again draws nothing anew; a playing clip draws itself.
        player.surface(surface)
        idle()
        assertEquals(1, seeks)
        player.release()
        surface.release()
    }

    // MEDIA-004: the size and turn the file states are told, so that a clip shot upright is shown upright in its box.
    @Test
    fun theStoredSizeAndTurnAreTold() {
        ShadowMediaPlayer.addMediaInfo(source, ShadowMediaPlayer.MediaInfo(4_000, 0))
        ShadowMediaMetadataRetriever.addMetadata(source, MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH, "1280")
        ShadowMediaMetadataRetriever.addMetadata(source, MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT, "720")
        ShadowMediaMetadataRetriever.addMetadata(source, MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION, "90")
        runBlocking { player.open(file, listener) }
        idle()
        assertEquals(Triple(1280, 720, 90), size)
        player.release()
    }

    // A file stating no size is told at the player's own, unturned.
    @Test
    fun aFileStatingNoSizeIsToldAtThePlayersOwn() {
        ShadowMediaPlayer.addMediaInfo(source, ShadowMediaPlayer.MediaInfo(4_000, 0))
        runBlocking { player.open(file, listener) }
        idle()
        assertEquals(Triple(media.videoWidth, media.videoHeight, 0), size)
        player.release()
    }
}
