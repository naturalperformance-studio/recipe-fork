package app.recipefork.app.card

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
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
    private val listener = object : ClipPlayer.Listener {
        override fun ready(width: Int, height: Int) {
            heard += "ready"
        }

        override fun failed() {
            heard += "failed"
        }
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Test
    fun aClipPlaysMutedAndLoopingWithoutAudioFocus() {
        ShadowMediaPlayer.addMediaInfo(source, ShadowMediaPlayer.MediaInfo(4_000, 0))
        player.open(file, listener)
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
        player.open(file, listener)
        player.play()
        idle()
        assertEquals(ShadowMediaPlayer.State.STARTED, shadowOf(media).state)
        player.release()
    }

    @Test
    fun aFileThatCannotBeOpenedIsUnplayable() {
        ShadowMediaPlayer.addException(source, IOException("not a clip"))
        player.open(file, listener)
        idle()
        assertEquals(listOf("failed"), heard)
        player.play()
        player.pause()
        player.release()
    }

    @Test
    fun aClipThatFailsWhilePlayingIsUnplayable() {
        ShadowMediaPlayer.addMediaInfo(source, ShadowMediaPlayer.MediaInfo(4_000, 0))
        player.open(file, listener)
        idle()
        player.play()
        shadowOf(media).invokeErrorListener(MediaPlayer.MEDIA_ERROR_UNKNOWN, 0)
        assertEquals(listOf("ready", "failed"), heard)
        player.release()
    }
}
