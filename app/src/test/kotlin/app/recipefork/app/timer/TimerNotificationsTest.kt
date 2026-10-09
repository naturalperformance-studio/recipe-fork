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

    private fun timer(position: Int, name: String, recipe: String = "Soup", index: Int = 0, number: Int? = null) = RunningTimer(
        TimerKey(BookName.Pool, RecipeId("soup"), position, index),
        name,
        recipe,
        30.minutes,
        Instant.parse("2026-10-02T12:30:00Z"),
        number = number,
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

    // DOM-024: two timers of one step are two notifications, each with its own tag and intents; the unnamed ones are
    // told apart by their numbers; withdrawing one leaves the other.
    @Test
    fun twoTimersOfOneStepAreTwoNotifications() {
        val first = timer(2, "Simmer", index = 0, number = 1)
        val second = timer(2, "Simmer", index = 1, number = 2)
        notices.announce(first)
        notices.announce(second)
        val both = shadowOf(manager).activeNotifications.toList()
        assertEquals(2, both.size)
        assertEquals(setOf(TimerIntents.tagOf(first.key), TimerIntents.tagOf(second.key)), both.map { it.tag }.toSet())
        assertEquals(setOf("Simmer · timer 1", "Simmer · timer 2"), shown().map { it.text }.toSet())
        val second2 = shown().single { it.text == "Simmer · timer 2" }
        val opened = shadowOf(second2.contentIntent).savedIntent
        assertEquals(second.key, TimerIntents.timerOf(opened))
        assertEquals(second.key, TimerIntents.keyOf(opened.data))
        assertEquals(second.key, TimerIntents.keyOf(shadowOf(second2.actions.single().actionIntent).savedIntent.data))
        assertEquals(second.key, TimerIntents.keyOf(shadowOf(second2.deleteIntent).savedIntent.data))
        val first2 = shown().single { it.text == "Simmer · timer 1" }
        assertFalse(first2.contentIntent == second2.contentIntent)
        assertFalse(first2.actions.single().actionIntent == second2.actions.single().actionIntent)
        assertFalse(first2.deleteIntent == second2.deleteIntent)
        notices.withdraw(first.key)
        assertEquals(listOf("Simmer · timer 2"), shown().map { it.text })
    }

    // A timer's URI carries its place among its step's timers; the first is named as a step's only timer always was,
    // so that what was set for it before a step held several is still its own.
    @Test
    fun theUriCarriesTheTimersIndex() {
        val first = TimerKey(BookName.Pool, RecipeId("soup"), 2)
        val third = TimerKey(BookName.Pool, RecipeId("soup"), 2, 2)
        assertEquals(listOf("book-pool.db", "soup", "2"), TimerIntents.uriOf(first).pathSegments)
        assertEquals(listOf("book-pool.db", "soup", "2", "2"), TimerIntents.uriOf(third).pathSegments)
        assertEquals(first, TimerIntents.keyOf(TimerIntents.uriOf(first)))
        assertEquals(third, TimerIntents.keyOf(TimerIntents.uriOf(third)))
        assertFalse(TimerIntents.tagOf(first) == TimerIntents.tagOf(third))
    }

    // A named timer, or a step's only one, is announced by its name alone.
    @Test
    fun aTimerWithNoNumberIsAnnouncedByItsNameAlone() {
        notices.announce(timer(2, "Beans", index = 1))
        assertEquals("Beans", shown().single().text)
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

    // The ended channel pops up and shows on the lock screen, and is itself silent: the application rings alone, so
    // nothing of the channel echoes its ringing or outlives its cap.
    @Test
    fun theEndedChannelPopsUpAndIsSilent() {
        TimerChannels.create(context)
        val channel = manager.getNotificationChannel(TimerChannels.ENDED)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)
        assertEquals(Notification.VISIBILITY_PUBLIC, channel.lockscreenVisibility)
        assertEquals(null, channel.sound)
        assertFalse(channel.shouldVibrate())
    }

    // A channel's sound cannot change: the old channels of ended timers are gone, so installs get the silent one.
    @Test
    fun theOldEndedChannelsAreDeleted() {
        for (old in listOf("timer-ended", "timer-ended-alarm")) {
            manager.createNotificationChannel(NotificationChannel(old, "Timer ended", NotificationManager.IMPORTANCE_HIGH))
        }
        TimerChannels.create(context)
        assertEquals(null, manager.getNotificationChannel("timer-ended"))
        assertEquals(null, manager.getNotificationChannel("timer-ended-alarm"))
        assertTrue(manager.getNotificationChannel(TimerChannels.ENDED) != null)
        assertFalse(TimerChannels.ENDED in listOf("timer-ended", "timer-ended-alarm"))
    }

    // The notification of an ended timer is an alarm, offers to stop its ringing, and tells when it is dismissed.
    @Test
    fun theNotificationOffersToStopTheRinging() {
        notices.announce(timer(2, "Beans"))
        val notification = shown().single()
        assertEquals(NotificationCompat.CATEGORY_ALARM, notification.category)
        val stop = notification.actions.single()
        assertEquals("Stop", stop.title.toString())
        val sent = shadowOf(stop.actionIntent).savedIntent
        assertTrue(shadowOf(stop.actionIntent).isBroadcastIntent)
        assertEquals(TimerRingReceiver.STOP, sent.action)
        assertEquals(timer(2, "Beans").key, TimerIntents.keyOf(sent.data))
        val dismissed = shadowOf(notification.deleteIntent).savedIntent
        assertEquals(TimerRingReceiver.DISMISSED, dismissed.action)
        assertEquals(timer(2, "Beans").key, TimerIntents.keyOf(dismissed.data))
    }

    // Stopped or fallen silent, the notification stays in place, silent, and no longer offers to stop.
    @Test
    fun aQuietedNotificationStaysSilent() {
        notices.announce(timer(0, "Water"))
        notices.announce(timer(2, "Beans"))
        notices.quiet(timer(2, "Beans"))
        val beans = shown().single { it.text == "Beans" }
        assertTrue(beans.actions.isNullOrEmpty())
        assertTrue(beans.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
        assertEquals(TimerChannels.ENDED, beans.channelId)
        assertEquals(1, shown().single { it.text == "Water" }.actions.size)
        // One already gone is not posted again.
        notices.withdraw(timer(0, "Water").key)
        notices.quiet(timer(0, "Water"))
        assertEquals(listOf("Beans"), shown().map { it.text })
    }

    // Whether the ended timer's notification is shown, which decides whether it rings with the application closed.
    @Test
    fun anAnnouncementSaysWhetherItIsShown() {
        TimerChannels.create(context)
        assertFalse(AndroidTimerNotices(context, AndroidNotificationAccess(context, sdk = 36)).announce(timer(0, "Water")))
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertTrue(AndroidTimerNotices(context, AndroidNotificationAccess(context, sdk = 36)).announce(timer(0, "Water")))
    }
}
