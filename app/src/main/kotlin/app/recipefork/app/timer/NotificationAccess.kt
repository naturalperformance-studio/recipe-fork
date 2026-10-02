package app.recipefork.app.timer

import android.Manifest
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

/** Whether a timer's end can be heard with the application closed or the screen locked (FLOW-013). */
enum class NotificationState {
    /** Timer notifications are shown. */
    PERMITTED,

    /** The application's notifications are not permitted: refused, never granted, or turned off. */
    OFF,

    /** The application's notifications are on, and its timer notifications alone are turned off (FLOW-013 (13)). */
    TIMERS_OFF,
}

/** What the platform says about the application's notifications, and where its settings for them are. */
interface NotificationAccess {
    fun state(): NotificationState

    /** Whether the platform has a request for the permission to notify (Android 13 and later). */
    val hasRequest: Boolean

    /** The system's settings for what [state] says is off: the application's notifications, or its timers' channel. */
    fun settings(state: NotificationState): Intent
}

/** The device's own notification settings. [sdk] is the platform's version. */
class AndroidNotificationAccess(
    private val context: Context,
    private val sdk: Int = Build.VERSION.SDK_INT,
) : NotificationAccess {
    override val hasRequest: Boolean get() = sdk >= 33

    override fun state(): NotificationState {
        val manager = context.getSystemService(NotificationManager::class.java)
        val granted = !hasRequest ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted || !manager.areNotificationsEnabled()) return NotificationState.OFF
        val channel = manager.getNotificationChannel(TimerChannels.ENDED)
        return if (channel?.importance == NotificationManager.IMPORTANCE_NONE) {
            NotificationState.TIMERS_OFF
        } else {
            NotificationState.PERMITTED
        }
    }

    override fun settings(state: NotificationState): Intent = when (state) {
        NotificationState.TIMERS_OFF -> Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_CHANNEL_ID, TimerChannels.ENDED)
        else -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
    }.putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
}

/** Opens the system's settings for what [state] says is off, or the application's details where those are missing. */
fun NotificationAccess.openSettings(context: Context, state: NotificationState) {
    try {
        context.startActivity(settings(state))
    } catch (e: ActivityNotFoundException) {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
        )
    }
}
