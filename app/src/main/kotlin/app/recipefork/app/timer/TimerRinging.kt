package app.recipefork.app.timer

import android.app.ActivityManager
import android.app.AlarmManager
import android.content.ContentResolver
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import app.recipefork.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

/** What rings while an ended timer is unstopped: the alarm's sound, over and over, and a vibration that repeats. */
interface RingingSound {
    fun start()

    fun stop()
}

/**
 * When each ringing timer's time is up, whatever holds the process meanwhile: told of each ring as it starts and of each
 * that ends sooner, it calls back at the moment given — or, the process gone, has the application take it up then.
 */
interface RingTimeouts {
    fun set(key: TimerKey, at: Instant, due: () -> Unit)

    fun clear(key: TimerKey)

    object None : RingTimeouts {
        override fun set(key: TimerKey, at: Instant, due: () -> Unit) = Unit
        override fun clear(key: TimerKey) = Unit
    }
}

/**
 * The ended timers ringing like an alarm (FLOW-013): each from its end until the reader stops it — from its
 * notification, by tapping or dismissing it, or on the card — or until [cap] has passed, after which its notification
 * stays, silent. The phone rings while any of them rings, and falls silent once none is left. Its notices are told
 * when one stops ringing while its notification stays, so that the notification no longer offers to stop it. Each
 * ring's cap is kept by [timeouts] as well as by whoever calls [expire], so that it holds on every path.
 */
class TimerRinging(
    private val clock: TimerClock,
    val sound: RingingSound,
    private val notices: TimerNotices? = null,
    private val cap: Duration = CAP,
    private val timeouts: RingTimeouts = RingTimeouts.None,
) {
    private class Ring(val timer: RunningTimer, val since: Instant)

    private val rings = LinkedHashMap<TimerKey, Ring>()
    private val keys = MutableStateFlow<Set<TimerKey>>(emptySet())

    /** The timers ringing now, as they change. */
    val ringing: StateFlow<Set<TimerKey>> = keys.asStateFlow()

    val isRinging: Boolean get() = keys.value.isNotEmpty()

    /** [timer] has ended and rings from now on; the phone rings if it did not already. */
    fun ring(timer: RunningTimer) {
        val ring = Ring(timer, clock.now())
        // Its time is kept before it rings, so that nothing rings without it.
        timeouts.set(timer.key, ring.since.plus(cap.toJavaDuration())) { timeUp(timer.key, ring) }
        change { rings[timer.key] = ring }
    }

    /** [key]'s time is up, if it still rings as [ring]: it stops, and its notification stays, silent. */
    private fun timeUp(key: TimerKey, ring: Ring) {
        val over = change { if (rings[key] === ring) rings.remove(key) else null } ?: return
        notices?.quiet(over.timer)
    }

    /** The reader stopped [key]'s ringing; its notification stays, silent. Returns whether it was ringing. */
    fun stop(key: TimerKey): Boolean {
        timeouts.clear(key)
        val stopped = change { rings.remove(key) } ?: return false
        notices?.quiet(stopped.timer)
        return true
    }

    /** [key]'s notification is gone, or the timer itself: its ringing ends with it. */
    fun forget(key: TimerKey) {
        timeouts.clear(key)
        change { rings.remove(key) }
    }

    /** Stops every timer that has rung for [cap] by [now], leaving its notification in place, silent. */
    fun expire(now: Instant = clock.now()) {
        val over = change {
            val over = rings.values.filter { !now.isBefore(it.since.plus(cap.toJavaDuration())) }
            over.forEach { rings.remove(it.timer.key) }
            over
        }
        over.forEach {
            timeouts.clear(it.timer.key)
            notices?.quiet(it.timer)
        }
    }

    /** When the last of the timers ringing now falls silent by itself, or null where none rings. */
    fun silentBy(): Instant? = synchronized(rings) { rings.values.maxOfOrNull { it.since } }?.plus(cap.toJavaDuration())

    /** Runs [what] on the rings, then starts or stops the phone ringing as the rings went from none or to none. */
    private fun <T> change(what: () -> T): T = synchronized(rings) {
        val before = rings.isNotEmpty()
        val result = what()
        val after = rings.isNotEmpty()
        keys.value = rings.keys.toSet()
        if (!before && after) sound.start()
        if (before && !after) sound.stop()
        result
    }

    companion object {
        /** How long an ended timer rings unstopped before it falls silent by itself. */
        val CAP = 2.minutes
    }
}

/**
 * The device's alarm: its alarm sound — or its notification sound, or the application's own tone, the first of them
 * that plays — looping on the alarm stream, which the alarm volume governs and Do Not Disturb lets through as it lets
 * alarms, holding the audio focus as an alarm does; and a repeating vibration. The sound is prepared on [work], off the
 * main thread.
 */
class AndroidRingingSound(
    private val context: Context,
    private val work: Executor = Executors.newSingleThreadExecutor(),
) : RingingSound {
    private val vibrator = context.getSystemService(Vibrator::class.java)
    private val audio = context.getSystemService(AudioManager::class.java)
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(TimerSound.ALARM)
        .build()
    private var player: MediaPlayer? = null

    /** Bumped at each start and stop, so that a start overtaken by a stop leaves nothing playing. */
    private var turn = 0

    /** The player of the alarm sound while it plays. */
    internal fun player(): MediaPlayer? = synchronized(this) { player }

    /** Whether the alarm sound is playing now. */
    val isPlaying: Boolean get() = synchronized(this) { runCatching { player?.isPlaying == true }.getOrDefault(false) }

    override fun start() {
        val mine = synchronized(this) {
            release()
            ++turn
        }
        vibrate()
        work.execute {
            val played = TimerSound.sources(context).firstNotNullOfOrNull(::play)
            synchronized(this) {
                if (turn == mine && played != null) {
                    audio?.requestAudioFocus(focus)
                    player = played
                } else {
                    played?.let(::release)
                }
            }
        }
    }

    override fun stop() {
        synchronized(this) {
            turn++
            release()
        }
        vibrator?.cancel()
    }

    /** A player looping [uri] on the alarm stream, or null where it cannot be played. */
    private fun play(uri: Uri): MediaPlayer? {
        val player = MediaPlayer()
        return try {
            player.setAudioAttributes(TimerSound.ALARM)
            player.setDataSource(context, uri)
            player.isLooping = true
            player.setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK)
            player.prepare()
            player.start()
            player
        } catch (e: Exception) {
            // This one cannot be played — deleted, unreadable, or none at all: the next is tried.
            player.release()
            null
        }
    }

    private fun vibrate() {
        val vibrator = vibrator?.takeIf { it.hasVibrator() } ?: return
        val effect = VibrationEffect.createWaveform(TimerSound.VIBRATION, 0)
        if (Build.VERSION.SDK_INT >= 33) {
            vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(effect, TimerSound.ALARM)
        }
    }

    private fun release() {
        player?.let(::release) ?: return
        player = null
        audio?.abandonAudioFocusRequest(focus)
    }

    private fun release(player: MediaPlayer) {
        runCatching { player.stop() }
        player.release()
    }
}

/** The sound and vibration an ended timer rings with, on the alarm stream. */
object TimerSound {
    val ALARM: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    /** Buzz for 800 ms, rest for 800 ms, over and over. */
    val VIBRATION = longArrayOf(0, 800, 800)

    /** The application's own tone, which plays where the device's sounds do not. */
    fun ownTone(context: Context): Uri = Uri.Builder()
        .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
        .authority(context.packageName)
        .appendPath(R.raw.timer_alarm.toString())
        .build()

    /** What is tried in turn: the device's alarm sound, its notification sound, and the application's own tone. */
    fun sources(context: Context): List<Uri> = listOfNotNull(
        RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM),
        RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_NOTIFICATION),
        ownTone(context),
    )
}

/**
 * The ringing's time kept for the device: a delay in the process, and should the process be gone or held asleep, an
 * alarm allowed while idle at the same moment, which the application takes up (see [TimerRingReceiver]).
 */
class AndroidRingTimeouts(private val context: Context, private val scope: CoroutineScope) : RingTimeouts {
    private val waits = ConcurrentHashMap<TimerKey, Job>()

    override fun set(key: TimerKey, at: Instant, due: () -> Unit) {
        val left = java.time.Duration.between(Instant.now(), at).toMillis().coerceAtLeast(0)
        val wait = scope.launch {
            delay(left)
            waits.remove(key, coroutineContext[Job])
            due()
        }
        waits.put(key, wait)?.cancel()
        context.getSystemService(AlarmManager::class.java)
            .setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), TimerRingReceiver.expiry(context, key))
    }

    override fun clear(key: TimerKey) {
        waits.remove(key)?.cancel()
        context.getSystemService(AlarmManager::class.java).cancel(TimerRingReceiver.expiry(context, key))
    }
}

/** Whether the application is in front of the reader now. */
fun applicationIsOpen(): Boolean {
    val state = ActivityManager.RunningAppProcessInfo()
    ActivityManager.getMyMemoryState(state)
    return state.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
}
