package app.recipefork.app.timer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import app.recipefork.app.MainActivity
import app.recipefork.app.R

/** The two notification channels of timers: their ends, which pop up, and the quiet note while they run. */
object TimerChannels {
    /**
     * A timer has ended (FLOW-013): its notification pops up on the screen and the lock screen; the channel the reader
     * may turn off alone (FLOW-013 (13)). The channel itself is silent: the ringing is the application's own, which
     * alone can ring on until stopped and fall silent at its cap, so nothing of the channel plays over it or after it.
     */
    const val ENDED = "timer-ended-silent"

    /**
     * The channels ended timers were once announced on: with the default notification sound, then with the alarm's
     * sound. A channel's sound cannot change once created, so each change came with a new one; the old are deleted.
     */
    val RETIRED = listOf("timer-ended", "timer-ended-alarm")

    /** Timers are running: the note the running timers are kept by while the application is closed. */
    const val RUNNING = "timer-running"

    fun create(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        RETIRED.forEach(manager::deleteNotificationChannel)
        manager.createNotificationChannel(
            NotificationChannel(ENDED, context.getString(R.string.timer_channel_ended), NotificationManager.IMPORTANCE_HIGH)
                .apply {
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    setSound(null, null)
                    enableVibration(false)
                },
        )
        manager.createNotificationChannel(
            NotificationChannel(RUNNING, context.getString(R.string.timer_channel_running), NotificationManager.IMPORTANCE_LOW)
                .apply { setShowBadge(false) },
        )
    }
}

/** The step a timer's notification leads to, carried by the intent that opens the application (FLOW-013 (4)). */
data class TimerStep(val book: String, val recipeId: String, val stepPosition: Int)

object TimerIntents {
    private const val BOOK_KEY = "app.recipefork.timer.book"
    private const val RECIPE_KEY = "app.recipefork.timer.recipe"
    private const val STEP_KEY = "app.recipefork.timer.step"
    private const val INDEX_KEY = "app.recipefork.timer.index"

    /** Opens the application on [key]'s recipe card at its step, in the task already open if there is one. */
    fun open(context: Context, key: TimerKey): Intent = Intent(context, MainActivity::class.java)
        .setAction(Intent.ACTION_VIEW)
        // One intent per timer, so that two notifications never share a pending intent.
        .setData(uriOf(key))
        .putExtra(BOOK_KEY, key.book)
        .putExtra(RECIPE_KEY, key.recipeId)
        .putExtra(STEP_KEY, key.stepPosition)
        .putExtra(INDEX_KEY, key.index)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    /** The step [intent] leads to, or null where it leads to none. */
    fun stepOf(intent: Intent?): TimerStep? {
        val extras = intent?.extras ?: return null
        val book = extras.getString(BOOK_KEY) ?: return null
        val recipe = extras.getString(RECIPE_KEY) ?: return null
        if (!extras.containsKey(STEP_KEY)) return null
        return TimerStep(book, recipe, extras.getInt(STEP_KEY))
    }

    /**
     * The timer whose notification [intent] came from, or null where it came from none. One opened by a notification
     * posted before a step held several timers names none among them: it was the step's only one, at index 0.
     */
    fun timerOf(intent: Intent?): TimerKey? {
        val step = stepOf(intent) ?: return null
        return TimerKey(step.book, step.recipeId, step.stepPosition, intent?.extras?.getInt(INDEX_KEY, 0) ?: 0)
    }

    /** Marks [intent] as followed, so that it leads nowhere again. */
    fun followed(intent: Intent) {
        intent.removeExtra(BOOK_KEY)
        intent.removeExtra(RECIPE_KEY)
        intent.removeExtra(STEP_KEY)
        intent.removeExtra(INDEX_KEY)
    }

    /** The timer [uri] names, as [uriOf] made it; null where it names none. */
    fun keyOf(uri: Uri?): TimerKey? {
        val path = uri?.takeIf { it.scheme == SCHEME }?.pathSegments ?: return null
        if (path.size != 3 && path.size != 4) return null
        val index = if (path.size == 4) path[3].toIntOrNull()?.takeIf { it > 0 } ?: return null else 0
        return TimerKey(path[0], path[1], path[2].toIntOrNull() ?: return null, index)
    }

    private const val SCHEME = "recipefork-timer"

    /**
     * The URI that names [key] — its book, recipe, step and, from the second timer of a step on, its place among the
     * step's timers — so that each timer has intents, alarms and a notification of its own. The first timer of a step
     * is named as the step's only timer was before a step held several, so that what was set for it then is still its.
     */
    fun uriOf(key: TimerKey): Uri = Uri.Builder().scheme(SCHEME)
        .appendPath(key.book).appendPath(key.recipeId).appendPath(key.stepPosition.toString())
        .apply { if (key.index != 0) appendPath(key.index.toString()) }
        .build()

    /** A tag naming [key] among the application's notifications. */
    fun tagOf(key: TimerKey): String = uriOf(key).toString()
}

/** The device's notifications for timers. */
class AndroidTimerNotices(
    private val context: Context,
    private val access: NotificationAccess = AndroidNotificationAccess(context),
) : TimerNotices {
    private val manager = context.getSystemService(NotificationManager::class.java)

    /**
     * Each ended timer has its own notification: the recipe's title and the timer's name, nothing of the step beyond
     * it (FLOW-013 (3), FLOW-016 (3–5)); tapping it opens the card at the step (FLOW-013 (4)), and it offers to stop
     * the ringing. Without permission to notify, nothing is shown: the timer is finished in the application all the
     * same (FLOW-013 (5)). Returns whether it is shown.
     */
    override fun announce(timer: RunningTimer): Boolean {
        post(TimerIntents.tagOf(timer.key), ended(timer, ringing = true))
        return access.state() == NotificationState.PERMITTED
    }

    /** The notification of [timer], if it is still shown, no longer rings nor offers to stop: it stays, silent. */
    override fun quiet(timer: RunningTimer) {
        val tag = TimerIntents.tagOf(timer.key)
        if (manager.activeNotifications.none { it.tag == tag && it.id == ENDED_ID }) return
        post(tag, ended(timer, ringing = false))
    }

    override fun withdraw(key: TimerKey) {
        manager.cancel(TimerIntents.tagOf(key), ENDED_ID)
    }

    private fun ended(timer: RunningTimer, ringing: Boolean): Notification {
        val builder = NotificationCompat.Builder(context, TimerChannels.ENDED)
            .setSmallIcon(R.drawable.ic_timer_notification)
            .setContentTitle(timer.recipe.ifBlank { context.getString(R.string.common_untitled_recipe) })
            .setContentText(shownName(context, timer))
            .setSubText(context.getString(R.string.timer_ended))
            .setWhen(timer.endsAt.toEpochMilli())
            .setShowWhen(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(openingIntent(context, timer.key))
            .setDeleteIntent(TimerRingReceiver.intent(context, TimerRingReceiver.DISMISSED, timer.key))
        if (ringing) {
            builder.addAction(
                0,
                context.getString(R.string.timer_stop),
                TimerRingReceiver.intent(context, TimerRingReceiver.STOP, timer.key),
            )
        } else {
            // Posted again in place: it does not pop up again.
            builder.setOnlyAlertOnce(true)
        }
        return builder.build()
    }

    private fun post(tag: String, notification: Notification) {
        try {
            manager.notify(tag, ENDED_ID, notification)
        } catch (e: SecurityException) {
            // Notifications are not permitted: the timer is finished in the application alone.
        }
    }

    companion object {
        const val ENDED_ID = 1
        const val RUNNING_ID = 2

        /** [timer]'s name as its notification shows it: with its number where it shares its name within its step. */
        fun shownName(context: Context, timer: RunningTimer): String =
            timer.number?.let { context.getString(R.string.timer_numbered, timer.name, it) } ?: timer.name

        fun openingIntent(context: Context, key: TimerKey): PendingIntent = PendingIntent.getActivity(
            context,
            0,
            TimerIntents.open(context, key),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        /** The quiet note shown while timers run: the next to end, counting down, and the card it belongs to. */
        fun running(context: Context, running: List<RunningTimer>): Notification {
            val next = running.minByOrNull { it.endsAt }
            val builder = NotificationCompat.Builder(context, TimerChannels.RUNNING)
                .setSmallIcon(R.drawable.ic_timer_notification)
                .setSubText(context.getString(R.string.timer_running))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            if (next != null) {
                builder
                    .setContentTitle(next.recipe.ifBlank { context.getString(R.string.common_untitled_recipe) })
                    .setContentText(shownName(context, next))
                    .setWhen(next.endsAt.toEpochMilli())
                    .setShowWhen(true)
                    .setUsesChronometer(true)
                    .setChronometerCountDown(true)
                    .setContentIntent(openingIntent(context, next.key))
            } else {
                builder.setContentTitle(context.getString(R.string.timer_running))
            }
            return builder.build()
        }
    }
}
