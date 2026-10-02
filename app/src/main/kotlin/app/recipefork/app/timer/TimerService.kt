package app.recipefork.app.timer

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.recipefork.app.RecipeForkApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.Instant
import kotlin.time.Duration.Companion.minutes

/**
 * How a timer is made to announce itself with the application closed or the screen locked (FLOW-013 (1, 2)): while
 * any timer awaits its announcement, a foreground service keeps the process, and a partial wake lock keeps the clock
 * read, until the last of them has been announced and has stopped ringing. It needs no access the reader must grant in
 * the settings. An inexact alarm allowed while idle stands behind it at each timer's end, should the service be taken
 * away.
 */
class DeviceTimerAnnouncements(
    private val context: Context,
    private val announcer: TimerAnnouncer,
) : TimerAnnouncements {
    override suspend fun started(timer: RunningTimer) {
        // The notice of the timer it replaces, if it had ended, goes with it.
        announcer.withdraw(timer.key)
        TimerService.start(context)
        TimerBackstop.set(context, timer)
    }

    override suspend fun cancelled(key: TimerKey) {
        announcer.withdraw(key)
        TimerBackstop.clear(context, key)
    }
}

/** The foreground service that holds the running timers, and the ringing of ended ones, while the application is closed. */
class TimerService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watching: Job? = null
    private var latestStart = 0
    private lateinit var wakeLock: PowerManager.WakeLock

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
            .apply { setReferenceCounted(false) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStart = startId
        try {
            ServiceCompat.startForeground(this, AndroidTimerNotices.RUNNING_ID, AndroidTimerNotices.running(this, emptyList()), TYPE)
        } catch (e: RuntimeException) {
            // Not allowed to come forward now: the alarms at the timers' ends announce them instead.
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (watching?.isActive != true) watching = scope.launch { watch() }
        return START_STICKY
    }

    private suspend fun watch() {
        val container = (application as RecipeForkApplication).container
        val store = container.timers
        val ringing = container.timerRinging
        val manager = getSystemService(NotificationManager::class.java)
        while (true) {
            container.timerAnnouncer.watch(store) { running ->
                // Awake until the last running timer has ended, and a little after, to announce it; and until the
                // last ringing one falls silent by itself.
                val last = listOfNotNull(running.maxOfOrNull { it.endsAt }, ringing.silentBy()).maxOrNull()
                if (last != null) {
                    val left = java.time.Duration.between(Instant.now(), last).toMillis().coerceAtLeast(0)
                    wakeLock.acquire(left + WAKE_MARGIN.inWholeMilliseconds)
                }
                try {
                    manager.notify(AndroidTimerNotices.RUNNING_ID, AndroidTimerNotices.running(this, running))
                } catch (e: SecurityException) {
                    // Without permission to notify the note is not shown; the service holds the timers all the same.
                }
            }
            // A start that arrived while the store was last read is watched before the service ends.
            val seen = latestStart
            if (store.unannounced().isNotEmpty() || ringing.isRinging || latestStart != seen) continue
            break
        }
        if (wakeLock.isHeld) wakeLock.release()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf(latestStart)
    }

    override fun onDestroy() {
        scope.cancel()
        if (wakeLock.isHeld) wakeLock.release()
        super.onDestroy()
    }

    companion object {
        private const val WAKE_LOCK_TAG = "recipefork:timers"
        private val WAKE_MARGIN = 1.minutes
        private val TYPE =
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0

        /** Brings the service up to hold the timers; where the platform does not allow it now, the alarms stand in. */
        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, TimerService::class.java))
            } catch (e: RuntimeException) {
                // Started from the background where the platform forbids it: the alarms at the timers' ends remain.
            }
        }
    }
}

/**
 * An inexact alarm, allowed while the device is idle, at each timer's end: should the service be gone, it announces
 * whatever has ended, late at worst. It needs no permission.
 */
object TimerBackstop {
    fun set(context: Context, timer: RunningTimer) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timer.endsAt.toEpochMilli(), pending(context, timer.key))
    }

    fun clear(context: Context, key: TimerKey) {
        context.getSystemService(AlarmManager::class.java).cancel(pending(context, key))
    }

    private fun pending(context: Context, key: TimerKey): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, TimerEndReceiver::class.java).setData(TimerIntents.uriOf(key)),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

/** A timer's end reached by its alarm: whatever has ended is announced. */
class TimerEndReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val application = context.applicationContext as RecipeForkApplication
        val result = goAsync()
        application.scope.launch {
            try {
                application.container.timerAnnouncer.announceEnded(application.container.timers)
                // The service holds the ringing until it is stopped; where it may not start now, it rings meanwhile.
                if (application.container.timerRinging.isRinging) TimerService.start(context)
            } finally {
                result.finish()
            }
        }
    }
}

/**
 * The reader stopped an ended timer's ringing from its notification: by its Stop action, which leaves the
 * notification in place, silent; or by dismissing it.
 */
class TimerRingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val key = TimerIntents.keyOf(intent.data) ?: return
        val ringing = (context.applicationContext as RecipeForkApplication).container.timerRinging
        when (intent.action) {
            STOP -> ringing.stop(key)
            DISMISSED -> ringing.forget(key)
        }
    }

    companion object {
        const val STOP = "app.recipefork.timer.STOP"
        const val DISMISSED = "app.recipefork.timer.DISMISSED"

        /** What [action] on [key]'s notification sends; one per timer and action. */
        fun intent(context: Context, action: String, key: TimerKey): PendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, TimerRingReceiver::class.java).setAction(action).setData(TimerIntents.uriOf(key)),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}

/**
 * The device restarted or the application was updated: the service and the alarms were lost with it, so held timers
 * are taken up again, and those that ended meanwhile are announced.
 */
class TimerRestartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in RESTARTS) return
        val application = context.applicationContext as RecipeForkApplication
        val result = goAsync()
        application.scope.launch {
            try {
                val waiting = application.container.timers.unannounced()
                if (waiting.isNotEmpty()) {
                    waiting.forEach { TimerBackstop.set(context, it) }
                    TimerService.start(context)
                }
            } finally {
                result.finish()
            }
        }
    }

    private companion object {
        val RESTARTS = setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)
    }
}
