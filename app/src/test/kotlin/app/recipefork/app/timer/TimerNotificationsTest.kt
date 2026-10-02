package app.recipefork.app.timer

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.MainActivity
import app.recipefork.app.R
import app.recipefork.app.book.BookName
import app.recipefork.core.recipe.RecipeId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.time.Instant
import kotlin.time.Duration.Companion.minutes

/**
 * The device's side of announcing: the notification of a timer's end (FLOW-013 (3, 4), FLOW-016 (2–5)), and what the
 * platform says about notifications being permitted (FLOW-013 (8, 10, 13)).
 */
@RunWith(AndroidJUnit4::class)
class TimerNotificationsTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val notices = AndroidTimerNotices(context)
    private val beansText = "Simmer the beans with the onion and the bay leaf until they are soft through and through"

    private fun timer(position: Int, name: String, recipe: String = "Soup") = RunningTimer(
        TimerKey(BookName.Pool, RecipeId("soup"), position),
        name,
        recipe,
        30.minutes,
        Instant.parse("2026-10-02T12:30:00Z"),
    )

    private fun shown(): List<Notification> = shadowOf(manager).allNotifications

    private val Notification.title get() = extras.getCharSequence(NotificationCompat.EXTRA_TITLE)?.toString()
    private val Notification.text get() = extras.getCharSequence(NotificationCompat.EXTRA_TEXT)?.toString()

    // FLOW-013 (3), FLOW-016 (5): the recipe's title and the given name, and no part of the step's text.
    @Test
    fun theNotificationNamesTheRecipeAndTheTimer() {
        notices.announce(timer(2, "Beans"))
        val notification = shown().single()
        assertEquals("Soup", notification.title)
        assertEquals("Beans", notification.text)
        assertEquals(TimerChannels.ENDED, notification.channelId)
        val everything = notification.extras.keySet().joinToString(" ") { notification.extras.get(it).toString() }
        assertFalse(everything.contains("Simmer"))
    }

    // FLOW-016 (3, 4): a derived name, and nothing of the step's text beyond it.
    @Test
    fun aDerivedNameIsAnnouncedAsItWasCut() {
        val derived = app.recipefork.core.timer.TimerName.derived(beansText)
        notices.announce(timer(2, derived))
        val notification = shown().single()
        assertEquals("Simmer the beans with the onion and the bay leaf until they…", notification.text)
        val everything = notification.extras.keySet().joinToString(" ") { notification.extras.get(it).toString() }
        assertFalse(everything.contains("soft through"))
    }

    @Test
    fun anUntitledRecipeIsNamedAsTheBookNamesIt() {
        notices.announce(timer(0, "Water", recipe = ""))
        assertEquals(context.getString(R.string.common_untitled_recipe), shown().single().title)
    }

    // FLOW-016 (2): two timers ending are two notifications; withdrawing one leaves the other.
    @Test
    fun twoTimersAreTwoNotifications() {
        notices.announce(timer(0, "Water"))
        notices.announce(timer(2, "Beans"))
        assertEquals(2, shown().size)
        assertEquals(setOf("Water", "Beans"), shown().map { it.text }.toSet())
        notices.withdraw(timer(0, "Water").key)
        assertEquals(listOf("Beans"), shown().map { it.text })
    }

    // FLOW-013 (4): tapping it opens the application on that recipe's card, at the timer's step.
    @Test
    fun theNotificationOpensTheCardAtTheStep() {
        notices.announce(timer(2, "Beans"))
        val intent = shadowOf(shown().single().contentIntent).savedIntent
        assertEquals(MainActivity::class.java.name, intent.component?.className)
        assertEquals(TimerStep(BookName.Pool.fileName, "soup", 2), TimerIntents.stepOf(intent))
        assertTrue(shadowOf(shown().single().contentIntent).isActivityIntent)
    }

    // FLOW-013 (8): Android 10 to 12 have no request for the permission; Android 13 and later have one.
    @Test
    fun onlyAndroid13AndLaterHaveARequest() {
        assertFalse(AndroidNotificationAccess(context, sdk = 29).hasRequest)
        assertFalse(AndroidNotificationAccess(context, sdk = 32).hasRequest)
        assertTrue(AndroidNotificationAccess(context, sdk = 33).hasRequest)
        assertTrue(AndroidNotificationAccess(context, sdk = 37).hasRequest)
    }

    // FLOW-013 (8, 10): from Android 13 the permission decides; before it, the application's switch alone.
    @Test
    fun notificationsArePermittedByThePermissionOrTheSwitch() {
        TimerChannels.create(context)
        assertEquals(NotificationState.OFF, AndroidNotificationAccess(context, sdk = 36).state())
        assertEquals(NotificationState.PERMITTED, AndroidNotificationAccess(context, sdk = 29).state())
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertEquals(NotificationState.PERMITTED, AndroidNotificationAccess(context, sdk = 36).state())
        shadowOf(manager).setNotificationsEnabled(false)
        assertEquals(NotificationState.OFF, AndroidNotificationAccess(context, sdk = 36).state())
        assertEquals(NotificationState.OFF, AndroidNotificationAccess(context, sdk = 29).state())
    }

    // FLOW-013 (13): the timers' channel turned off alone counts as not permitted, and is told apart.
    @Test
    fun theTimersChannelOffAloneIsTimersOff() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        TimerChannels.create(context)
        manager.createNotificationChannel(
            NotificationChannel(TimerChannels.ENDED, "Timer ended", NotificationManager.IMPORTANCE_NONE),
        )
        assertEquals(NotificationState.TIMERS_OFF, AndroidNotificationAccess(context, sdk = 36).state())
        assertEquals(NotificationState.TIMERS_OFF, AndroidNotificationAccess(context, sdk = 29).state())
    }

    // FLOW-013 (10, 13): the control leads to the application's notification settings, or to its timers' channel.
    @Test
    fun theSettingsAreTheApplicationsOrItsTimersChannel() {
        val access = AndroidNotificationAccess(context)
        val app = access.settings(NotificationState.OFF)
        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, app.action)
        assertEquals(context.packageName, app.getStringExtra(Settings.EXTRA_APP_PACKAGE))
        val channel = access.settings(NotificationState.TIMERS_OFF)
        assertEquals(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS, channel.action)
        assertEquals(context.packageName, channel.getStringExtra(Settings.EXTRA_APP_PACKAGE))
        assertEquals(TimerChannels.ENDED, channel.getStringExtra(Settings.EXTRA_CHANNEL_ID))
    }

    @Test
    fun theTimersEndIsHeardAndTheRunningNoteIsQuiet() {
        TimerChannels.create(context)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, manager.getNotificationChannel(TimerChannels.ENDED).importance)
        assertEquals(NotificationManager.IMPORTANCE_LOW, manager.getNotificationChannel(TimerChannels.RUNNING).importance)
    }
}
