package app.recipefork.app.timer

import android.app.ActivityManager
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

/** What rings while an ended timer is unstopped: the alarm's sound, over and over, and a vibration that repeats. */
interface RingingSound {
    fun start()

    fun stop()
}

/**
 * The ended timers ringing like an alarm (FLOW-013): each from its end until the reader stops it — from its
 * notification, by tapping or dismissing it, or on the card — or until [cap] has passed, after which its notification
 * stays, silent. The phone rings while any of them rings, and falls silent once none is left. Its notices are told
 * when one stops ringing while its notification stays, so that the notification no longer offers to stop it.
 */
class TimerRinging(
    private val clock: TimerClock,
    val sound: RingingSound,
    private val notices: TimerNotices? = null,
    private val cap: Duration = CAP,
) {
    private class Ring(val timer: RunningTimer, val since: Instant)

    private val rings = LinkedHashMap<TimerKey, Ring>()
    private val keys = MutableStateFlow<Set<TimerKey>>(emptySet())

    /** The timers ringing now, as they change. */
    val ringing: StateFlow<Set<TimerKey>> = keys.asStateFlow()

    val isRinging: Boolean get() = keys.value.isNotEmpty()

    /** [timer] has ended and rings from now on; the phone rings if it did not already. */
    fun ring(timer: RunningTimer) = change { rings[timer.key] = Ring(timer, clock.now()) }

    /** The reader stopped [key]'s ringing; its notification stays, silent. Returns whether it was ringing. */
    fun stop(key: TimerKey): Boolean {
        val stopped = change { rings.remove(key) } ?: return false
        notices?.quiet(stopped.timer)
        return true
    }

    /** [key]'s notification is gone, or the timer itself: its ringing ends with it. */
    fun forget(key: TimerKey) {
        change { rings.remove(key) }
    }

    /** Stops every timer that has rung for [cap] by [now], leaving its notification in place, silent. */
    fun expire(now: Instant = clock.now()) {
        val over = change {
            val over = rings.values.filter { !now.isBefore(it.since.plus(cap.toJavaDuration())) }
            over.forEach { rings.remove(it.timer.key) }
            over
        }
        over.forEach { notices?.quiet(it.timer) }
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
 * The device's alarm: its alarm sound (or the default notification sound where it has none) looping on the alarm
 * stream, which the alarm volume governs and Do Not Disturb lets through as it lets alarms, and a repeating vibration.
 */
class AndroidRingingSound(private val context: Context) : RingingSound {
    private val vibrator = context.getSystemService(Vibrator::class.java)
    private var player: MediaPlayer? = null

    /** The player of the alarm sound while it plays. */
    internal fun player(): MediaPlayer? = synchronized(this) { player }

    /** Whether the alarm sound is playing now. */
    val isPlaying: Boolean get() = synchronized(this) { runCatching { player?.isPlaying == true }.getOrDefault(false) }

    override fun start() = synchronized(this) {
        release()
        player = TimerSound.uri(context)?.let(::play)
        vibrate()
    }

    override fun stop() = synchronized(this) {
        release()
        vibrator?.cancel()
        Unit
    }

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
            // No sound can be played: the vibration and the notification remain.
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
        player?.let {
            runCatching { it.stop() }
            it.release()
        }
        player = null
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

    /** The device's alarm sound, or its default notification sound where it has no alarm sound; null where neither. */
    fun uri(context: Context): Uri? =
        RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_NOTIFICATION)

    /** The sound the ended timers' channel is given: the device's alarm sound as set, or its notification sound. */
    fun channelUri(context: Context): Uri =
        if (RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM) != null) {
            Settings.System.DEFAULT_ALARM_ALERT_URI
        } else {
            Settings.System.DEFAULT_NOTIFICATION_URI
        }
}

/** Whether the application is in front of the reader now. */
fun applicationIsOpen(): Boolean {
    val state = ActivityManager.RunningAppProcessInfo()
    ActivityManager.getMyMemoryState(state)
    return state.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
}
