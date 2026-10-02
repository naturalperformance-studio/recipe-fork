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

/** The two notification channels of timers: their ends, which are heard, and the quiet note while they run. */
object TimerChannels {
    /** A timer has ended (FLOW-013); the channel the reader may turn off alone (FLOW-013 (13)). */
    const val ENDED = "timer-ended"

    /** Timers are running: the note the running timers are kept by while the application is closed. */
    const val RUNNING = "timer-running"

    fun create(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(ENDED, context.getString(R.string.timer_channel_ended), NotificationManager.IMPORTANCE_HIGH)
                .apply { lockscreenVisibility = Notification.VISIBILITY_PUBLIC },
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

    /** Opens the application on [key]'s recipe card at its step, in the task already open if there is one. */
    fun open(context: Context, key: TimerKey): Intent = Intent(context, MainActivity::class.java)
        .setAction(Intent.ACTION_VIEW)
        // One intent per timer, so that two notifications never share a pending intent.
        .setData(uriOf(key))
        .putExtra(BOOK_KEY, key.book)
        .putExtra(RECIPE_KEY, key.recipeId)
        .putExtra(STEP_KEY, key.stepPosition)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    /** The step [intent] leads to, or null where it leads to none. */
    fun stepOf(intent: Intent?): TimerStep? {
        val extras = intent?.extras ?: return null
        val book = extras.getString(BOOK_KEY) ?: return null
        val recipe = extras.getString(RECIPE_KEY) ?: return null
        if (!extras.containsKey(STEP_KEY)) return null
        return TimerStep(book, recipe, extras.getInt(STEP_KEY))
    }

    /** Marks [intent] as followed, so that it leads nowhere again. */
    fun followed(intent: Intent) {
        intent.removeExtra(BOOK_KEY)
        intent.removeExtra(RECIPE_KEY)
        intent.removeExtra(STEP_KEY)
    }

    fun uriOf(key: TimerKey): Uri = Uri.Builder().scheme("recipefork-timer")
        .appendPath(key.book).appendPath(key.recipeId).appendPath(key.stepPosition.toString()).build()

    /** A tag naming [key] among the application's notifications. */
    fun tagOf(key: TimerKey): String = uriOf(key).toString()
}

/** The device's notifications for timers. */
class AndroidTimerNotices(private val context: Context) : TimerNotices {
    private val manager = context.getSystemService(NotificationManager::class.java)

    /**
     * Each ended timer has its own notification: the recipe's title and the timer's name, nothing of the step beyond
     * it (FLOW-013 (3), FLOW-016 (3–5)); tapping it opens the card at the step (FLOW-013 (4)). Without permission to
     * notify, nothing is shown: the timer is finished in the application all the same (FLOW-013 (5)).
     */
    override fun announce(timer: RunningTimer) {
        val notification = NotificationCompat.Builder(context, TimerChannels.ENDED)
            .setSmallIcon(R.drawable.ic_timer_notification)
            .setContentTitle(timer.recipe.ifBlank { context.getString(R.string.common_untitled_recipe) })
            .setContentText(timer.name)
            .setSubText(context.getString(R.string.timer_ended))
            .setWhen(timer.endsAt.toEpochMilli())
            .setShowWhen(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(openingIntent(context, timer.key))
            .build()
        post(TimerIntents.tagOf(timer.key), notification)
    }

    override fun withdraw(key: TimerKey) {
        manager.cancel(TimerIntents.tagOf(key), ENDED_ID)
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
                    .setContentText(next.name)
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
