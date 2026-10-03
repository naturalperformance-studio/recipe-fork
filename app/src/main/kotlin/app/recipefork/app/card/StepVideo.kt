package app.recipefork.app.card

import android.content.Context
import android.graphics.Matrix
import android.graphics.Rect
import android.media.MediaMetadataRetriever
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.provider.Settings
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.recipefork.app.R
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext

/**
 * Plays one clip, always without sound (MEDIA-004): it never raises its volume above zero and never asks the system
 * for audio focus, so whatever else is playing on the phone goes on undisturbed. A clip plays round and round once
 * started. The card's own player is [PlatformClipPlayer]; the tests provide one of their own through [LocalClipPlayers].
 */
internal interface ClipPlayer {
    /**
     * Starts reading [file], opening it off the main thread; [listener] hears once whether it can be shown, and later
     * if it stops being playable.
     */
    suspend fun open(file: File, listener: Listener)

    /** Where the picture is drawn; null while there is nowhere to draw it. */
    fun surface(surface: Surface?)

    /** Plays, at once if read, else as soon as it is. */
    fun play()

    /** Stays on the frame it is at. */
    fun pause()

    /** Lets go of everything it holds; nothing is called on it afterwards. */
    fun release()

    interface Listener {
        /**
         * The clip can be shown; its first frame is drawn. Its pictures are [width] by [height] pixels as stored (zero
         * where it states none), turned by [rotation] degrees for display — a phone held upright stores a landscape
         * picture marked as turned by 90.
         */
        fun ready(width: Int, height: Int, rotation: Int)

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
    private var hasSurface = false
    private var stored: StoredSize? = null

    override suspend fun open(file: File, listener: ClipPlayer.Listener) {
        try {
            player.setVolume(0f, 0f)
            player.isLooping = true
            player.setOnPreparedListener {
                if (released) return@setOnPreparedListener
                prepared = true
                // The stored size and turn, as the file states them; the player's own size may or may not have the
                // turn applied already, so it stands in only where the file states none.
                val stated = stored
                if (stated != null) listener.ready(stated.width, stated.height, stated.rotation) else listener.ready(it.videoWidth, it.videoHeight, 0)
                // A paused clip still shows its first frame, not a blank.
                if (playing) it.start() else it.seekTo(0)
            }
            player.setOnErrorListener { _, _, _ ->
                prepared = false
                if (!released) listener.failed()
                true
            }
            // Reading the file is the disc's work, not the main thread's; the player is let go of only once it is done.
            withContext(Dispatchers.IO) {
                stored = storedSize(file)
                player.setDataSource(file.path)
            }
            if (released) return
            player.prepareAsync()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A file the framework cannot open is one it cannot play, never a crash.
            listener.failed()
        }
    }

    override fun surface(surface: Surface?) {
        if (released) return
        try {
            player.setSurface(surface)
            val arrived = surface != null && !hasSurface
            hasSurface = surface != null
            // Read before there was anywhere to draw, a paused clip draws its frame now rather than staying blank.
            if (arrived && prepared && !player.isPlaying) player.seekTo(player.currentPosition)
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

/** A clip's pictures as stored, and the turn they are shown at. */
internal data class StoredSize(val width: Int, val height: Int, val rotation: Int)

/** The size and turn [file] states for its pictures; null where it states no size, or cannot be read. */
internal fun storedSize(file: File): StoredSize? = try {
    MediaMetadataRetriever().use { retriever ->
        retriever.setDataSource(file.path)
        fun read(key: Int) = retriever.extractMetadata(key)?.toIntOrNull()
        val width = read(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
        val height = read(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
        if (width == null || height == null || width <= 0 || height <= 0) {
            null
        } else {
            StoredSize(width, height, read(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION) ?: 0)
        }
    }
} catch (e: Exception) {
    null
}

/**
 * Where a clip is drawn in its box. The box fills the step's width as a photograph does: a landscape or square clip is
 * shown whole at its own proportions, a taller one cropped to a square about its centre (PhotoImage's rule). The
 * picture is scaled to cover the box, centred, and whatever falls outside the box is clipped away — never drawn over
 * what is around it.
 */
internal object ClipFrame {
    /** The proportions of the box until a clip's size is known: the usual landscape clip's. */
    const val UNKNOWN_RATIO = 16f / 9f

    /** The size a clip stored at [width] by [height] and turned by [rotation] degrees is seen at. */
    fun shown(width: Int, height: Int, rotation: Int): IntSize =
        if (((rotation % 360) + 360) % 180 == 0) IntSize(width, height) else IntSize(height, width)

    /** The box's width over its height for a clip seen at [shown]: its own if it is landscape or square, else 1. */
    fun boxRatio(shown: IntSize?): Float =
        if (shown == null || shown.width <= 0 || shown.height <= 0) UNKNOWN_RATIO else maxOf(shown.width.toFloat() / shown.height, 1f)

    /**
     * Where the picture of a clip seen at [shown] is drawn in a box of [boxWidth] by [boxHeight]: [left, top, right,
     * bottom], covering the box, centred on it, its proportions kept.
     */
    fun content(boxWidth: Float, boxHeight: Float, shown: IntSize): FloatArray {
        val scale = maxOf(boxWidth / shown.width, boxHeight / shown.height)
        val width = shown.width * scale
        val height = shown.height * scale
        val left = (boxWidth - width) / 2f
        val top = (boxHeight - height) / 2f
        return floatArrayOf(left, top, left + width, top + height)
    }

    /**
     * The transform a TextureView of [boxWidth] by [boxHeight] applies to a picture it otherwise stretches over its
     * whole size (the turn already applied by the player), so that it is drawn at [content]: scaled about the centre.
     */
    fun transform(boxWidth: Float, boxHeight: Float, shown: IntSize): Matrix {
        val (left, top, right, bottom) = content(boxWidth, boxHeight, shown)
        return Matrix().apply { setScale((right - left) / boxWidth, (bottom - top) / boxHeight, boxWidth / 2f, boxHeight / 2f) }
    }
}

/** Whether the system's animations are off — the animator duration scale set to zero. */
internal fun animationsOff(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

/**
 * A step's clip on the card: muted and looping while it is on screen and the card is in front, paused once the card
 * leaves (MEDIA-004). It holds a player only while on screen — one is made as it comes into view and let go of as it
 * leaves, or as the card goes — so a recipe of many clips holds no more players than the screen shows. Its box follows
 * [ClipFrame] and nothing of the picture is drawn outside it. A round 56 dp control in its corner — or a tap on the clip
 * — pauses and plays it again (A11Y-007, WCAG 2.2.2), and that choice holds across its leaving and coming back;
 * nothing offers sound. With the system's animations off it does not start by itself but shows its first frame, with
 * the control offering to play. [description] names it for the screen reader as the author's video (A11Y-001); a clip
 * that cannot be played shows [unavailable] instead, and is tried again when it next comes into view.
 *
 * The editor shows a step's clip by this same box, player and control, with [autoplay] off — it never starts by
 * itself there, only when the author plays it — and [poster], the clip's first frame read beforehand, drawn under the
 * picture and setting the box's proportions until the player has read the clip's own; [overlay] is drawn over the clip.
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
    autoplay: Boolean = true,
    poster: ImageBitmap? = null,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    val players = LocalClipPlayers.current
    val context = LocalContext.current
    // The clip's size, once a player has read it, kept while it is away so that its space does not jump.
    var size by remember(file) { mutableStateOf<IntSize?>(null) }
    var failed by remember(file) { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }
    var player by remember(file) { mutableStateOf<ClipPlayer?>(null) }
    var ready by remember(file) { mutableStateOf<ClipPlayer?>(null) }
    // The state as this composition saw it, not as it may stand once the effect runs.
    val inView = visible
    LaunchedEffect(file, players, inView) {
        if (!inView) return@LaunchedEffect
        val made = players.create()
        failed = false
        player = made
        try {
            made.open(
                file,
                object : ClipPlayer.Listener {
                    override fun ready(width: Int, height: Int, rotation: Int) {
                        if (player !== made) return
                        size = ClipFrame.shown(width, height, rotation)
                        ready = made
                    }

                    override fun failed() {
                        if (player === made) failed = true
                    }
                },
            )
            awaitCancellation()
        } finally {
            made.release()
            if (player === made) player = null
            if (ready === made) ready = null
        }
    }
    // The reader's own choice, kept across a turn of the phone; it starts by itself only with animations on, and only
    // where it is to start by itself at all.
    var wanted by rememberSaveable(file.path) { mutableStateOf(autoplay && !animationsOff(context)) }
    var resumed by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        resumed = true
        onPauseOrDispose { resumed = false }
    }
    val current = player
    val playing = wanted && visible && resumed && current != null && ready === current && !failed
    LaunchedEffect(current, playing) {
        // A player already let go of, as its clip left the screen, is asked nothing more.
        if (current == null || player !== current) return@LaunchedEffect
        if (playing) current.play() else current.pause()
    }

    Column(
        modifier.fillMaxWidth().onGloballyPositioned { coordinates ->
            // Clipped by the card's scrolling: nothing of it left in view is off screen.
            val bounds = coordinates.boundsInWindow()
            visible = bounds.width > 0f && bounds.height > 0f
        },
    ) {
        if (failed) {
            unavailable()
            return@Column
        }
        val shown = size ?: poster?.let { IntSize(it.width, it.height) }
        val toggle = { wanted = !wanted }
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(ClipFrame.boxRatio(shown))
                // The picture is scaled to cover the box; nothing of it is drawn outside.
                .clipToBounds()
                .semantics {
                    contentDescription = description
                    role = Role.Image
                }
                .testTag(videoTag),
        ) {
            if (poster != null) Image(poster, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            if (current != null) {
                AndroidView(
                    factory = { ClipView(it) },
                    update = {
                        it.player = current
                        it.videoSize = shown
                    },
                    modifier = Modifier.fillMaxSize().clipToBounds(),
                )
            }
            // A tap anywhere on the clip pauses or plays it, as the control does; the picture's view never sees it.
            Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { toggle() } })
            ClipControl(
                playing = wanted,
                action = if (wanted) pauseAction else playAction,
                onClick = toggle,
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).testTag(controlTag),
            )
            overlay()
        }
    }
}

/** The scrim the clip's control sits on: dark enough that its white icon reads on any frame (≥ 3:1 over white). */
internal val ClipControlScrim = Color.Black.copy(alpha = 0.6f)

/** The icon of the clip's control. */
internal val ClipControlIcon = Color.White

/**
 * The control that pauses and plays a clip (A11Y-007, WCAG 2.2.2): a round 56 dp button over the clip's corner, a
 * pause or play icon on [ClipControlScrim], named by [action].
 */
@Composable
private fun ClipControl(playing: Boolean, action: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(CardTarget)
            .clip(CircleShape)
            .background(ClipControlScrim)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = action },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(if (playing) R.drawable.ic_video_pause else R.drawable.ic_video_play),
            contentDescription = null,
            tint = ClipControlIcon,
            modifier = Modifier.size(32.dp),
        )
    }
}

/**
 * The surface a clip is drawn on: its picture placed as [ClipFrame] says, and clipped to its own bounds so that a
 * picture scaled beyond them is never drawn over what is around it.
 */
private class ClipView(context: Context) : TextureView(context), TextureView.SurfaceTextureListener {
    private var surface: Surface? = null

    /** The player drawing here; a new one is given the surface at once, if there is one. */
    var player: ClipPlayer? = null
        set(value) {
            if (value === field) return
            field = value
            surface?.let { value?.surface(it) }
        }

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
        if (width <= 0 || height <= 0) return
        clipBounds = Rect(0, 0, width, height)
        val video = videoSize ?: return
        if (video.width <= 0 || video.height <= 0) return
        setTransform(ClipFrame.transform(width.toFloat(), height.toFloat(), video))
    }

    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
        surface = Surface(texture).also { player?.surface(it) }
    }

    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) = Unit

    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
        player?.surface(null)
        surface?.release()
        surface = null
        return true
    }

    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
}
