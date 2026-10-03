package app.recipefork.app.card

import android.content.Context
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.provider.Settings
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleResumeEffect
import java.io.File

/**
 * Plays one clip, always without sound (MEDIA-004): it never raises its volume above zero and never asks the system
 * for audio focus, so whatever else is playing on the phone goes on undisturbed. A clip plays round and round once
 * started. The card's own player is [PlatformClipPlayer]; the tests provide one of their own through [LocalClipPlayers].
 */
internal interface ClipPlayer {
    /** Starts reading [file]; [listener] hears once whether it can be shown, and later if it stops being playable. */
    fun open(file: File, listener: Listener)

    /** Where the picture is drawn; null while there is nowhere to draw it. */
    fun surface(surface: Surface?)

    /** Plays, at once if read, else as soon as it is. */
    fun play()

    /** Stays on the frame it is at. */
    fun pause()

    /** Lets go of everything it holds; nothing is called on it afterwards. */
    fun release()

    interface Listener {
        /** The clip can be shown, at [width] by [height] pixels (zero where it states none); its first frame is drawn. */
        fun ready(width: Int, height: Int)

        /** The clip cannot be played. */
        fun failed()
    }
}

/** Makes a player for each clip the card shows. */
internal fun interface ClipPlayers {
    fun create(): ClipPlayer
}

/** The players the card's clips are played by: the platform's own, unless a test provides others. */
internal val LocalClipPlayers = staticCompositionLocalOf { ClipPlayers { PlatformClipPlayer() } }

/** A clip played by the platform's [MediaPlayer]: muted, looping, never taking audio focus (MEDIA-004). */
internal class PlatformClipPlayer(private val player: MediaPlayer = MediaPlayer()) : ClipPlayer {
    private var prepared = false
    private var playing = false
    private var released = false

    override fun open(file: File, listener: ClipPlayer.Listener) {
        try {
            player.setVolume(0f, 0f)
            player.isLooping = true
            player.setOnPreparedListener {
                if (released) return@setOnPreparedListener
                prepared = true
                listener.ready(it.videoWidth, it.videoHeight)
                // A paused clip still shows its first frame, not a blank.
                if (playing) it.start() else it.seekTo(0)
            }
            player.setOnErrorListener { _, _, _ ->
                prepared = false
                if (!released) listener.failed()
                true
            }
            player.setDataSource(file.path)
            player.prepareAsync()
        } catch (e: Exception) {
            // A file the framework cannot open is one it cannot play, never a crash.
            listener.failed()
        }
    }

    override fun surface(surface: Surface?) {
        if (released) return
        try {
            player.setSurface(surface)
        } catch (e: IllegalStateException) {
            // A player that failed has no picture to draw.
        }
    }

    override fun play() {
        playing = true
        if (prepared && !released) runCatching { if (!player.isPlaying) player.start() }
    }

    override fun pause() {
        playing = false
        if (prepared && !released) runCatching { if (player.isPlaying) player.pause() }
    }

    override fun release() {
        if (released) return
        released = true
        player.release()
    }
}

/** Whether the system's animations are off — the animator duration scale set to zero. */
internal fun animationsOff(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

/**
 * A step's clip on the card: muted and looping while it is on screen and the card is in front, paused once scrolled
 * off or once the card leaves, and let go of when the card is gone (MEDIA-004). A 56 dp control under it pauses and
 * plays it again (A11Y-007, WCAG 2.2.2); nothing offers sound. With the system's animations off it does not start by
 * itself but shows its first frame, with the control offering to play. [description] names it for the screen reader
 * as the author's video (A11Y-001); a clip that cannot be played shows [unavailable] instead.
 */
@Composable
internal fun StepVideo(
    file: File,
    description: String,
    playAction: String,
    pauseAction: String,
    unavailable: @Composable () -> Unit,
    videoTag: String,
    controlTag: String,
    modifier: Modifier = Modifier,
) {
    val players = LocalClipPlayers.current
    val context = LocalContext.current
    var size by remember(file) { mutableStateOf<IntSize?>(null) }
    var failed by remember(file) { mutableStateOf(false) }
    val player = remember(file, players) { players.create() }
    DisposableEffect(player) {
        player.open(
            file,
            object : ClipPlayer.Listener {
                override fun ready(width: Int, height: Int) {
                    size = IntSize(width, height)
                }

                override fun failed() {
                    failed = true
                }
            },
        )
        onDispose { player.release() }
    }
    // The reader's own choice, kept across a turn of the phone; it starts by itself only with animations on.
    var wanted by rememberSaveable(file.path) { mutableStateOf(!animationsOff(context)) }
    var visible by remember { mutableStateOf(false) }
    var resumed by remember { mutableStateOf(false) }
    LifecycleResumeEffect(player) {
        resumed = true
        onPauseOrDispose { resumed = false }
    }
    val playing = wanted && visible && resumed && size != null && !failed
    LaunchedEffect(player, playing) { if (playing) player.play() else player.pause() }

    if (failed) {
        unavailable()
        return
    }
    val shown = size
    val ratio = if (shown == null || shown.width <= 0 || shown.height <= 0) 16f / 9f else maxOf(shown.width.toFloat() / shown.height, 1f)
    Column(modifier.fillMaxWidth()) {
        AndroidView(
            factory = { ClipView(it, player) },
            update = { it.videoSize = shown },
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(ratio)
                .onGloballyPositioned { coordinates ->
                    // Clipped by the card's scrolling: nothing of it left in view is off screen.
                    val bounds = coordinates.boundsInWindow()
                    visible = bounds.width > 0f && bounds.height > 0f
                }
                .semantics {
                    contentDescription = description
                    role = Role.Image
                }
                .testTag(videoTag),
        )
        val action = if (wanted) pauseAction else playAction
        OutlinedButton(
            onClick = { wanted = !wanted },
            modifier = Modifier
                .padding(top = 8.dp)
                .heightIn(min = CardTarget)
                .widthIn(min = CardTarget)
                .testTag(controlTag),
        ) {
            Text(action)
        }
    }
}

/**
 * The surface a clip is drawn on: filling its width, a landscape clip shown whole and a taller one cropped to a square
 * about its centre, as a photograph is.
 */
private class ClipView(context: Context, private val player: ClipPlayer) : TextureView(context), TextureView.SurfaceTextureListener {
    private var surface: Surface? = null

    var videoSize: IntSize? = null
        set(value) {
            field = value
            crop()
        }

    init {
        surfaceTextureListener = this
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        crop()
    }

    private fun crop() {
        val video = videoSize ?: return
        if (video.width <= 0 || video.height <= 0 || width <= 0 || height <= 0) return
        val scale = maxOf(width.toFloat() / video.width, height.toFloat() / video.height)
        val matrix = Matrix()
        matrix.setScale(video.width * scale / width, video.height * scale / height, width / 2f, height / 2f)
        setTransform(matrix)
    }

    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
        surface = Surface(texture).also { player.surface(it) }
    }

    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) = Unit

    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
        player.surface(null)
        surface?.release()
        surface = null
        return true
    }

    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
}
