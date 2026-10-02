package app.recipefork.app.timer

import android.Manifest
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.R
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.ui.AccessibilityChecks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import kotlin.time.Duration.Companion.minutes

/**
 * What starting a timer says about notifications (FLOW-013 (5, 8–13)): the platform's request at the first start and
 * never before, where the platform has one; an explanation at once when they are not permitted, with a control to the
 * settings; nothing when they are; and the timer started before any of it and running whatever the answer.
 */
@RunWith(AndroidJUnit4::class)
class TimerNotificationsCheckTest : CardTimersKit() {
    private val activity get() = shadowOf(compose.activity)

    /** The permission the platform was last asked for, or null where it was asked for none. */
    private fun requested(): String? = activity.lastRequestedPermission?.requestedPermissions?.single()

    /** The reader answers the platform's request. */
    private fun answer(granted: Boolean) {
        val request = activity.lastRequestedPermission!!
        compose.runOnUiThread {
            @Suppress("DEPRECATION")
            compose.activity.onRequestPermissionsResult(
                request.requestCode,
                request.requestedPermissions,
                intArrayOf(if (granted) PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED),
            )
        }
        compose.waitForIdle()
    }

    private fun explained(): Boolean = shown(TimerTags.UNHEARD)

    private fun explanation(): String = compose.onNodeWithTag(TimerTags.UNHEARD_TEXT).fetchSemanticsNode()
        .config[SemanticsProperties.Text].joinToString("") { it.text }

    private fun awaitExplanation() = compose.waitUntil(PATIENCE_MS) { explained() }

    // FLOW-013 (8, 9, 12): on Android 13 and later the request is made at the first start, after the timer started.
    @Test
    fun theRequestIsMadeAtTheFirstStartAndNotBefore() {
        timers.access.state = NotificationState.OFF
        show(soup)
        compose.waitForIdle()
        assertNull(requested())
        start(0)
        awaitHeld { it.size == 1 }
        compose.waitUntil(PATIENCE_MS) { requested() != null }
        assertEquals(Manifest.permission.POST_NOTIFICATIONS, requested())
        // The timer had started by then, and runs while the request is open.
        assertEquals(1, timers.announcements.started.size)
        pass(1.minutes)
        awaitStatus(0, "9:00")
    }

    // FLOW-013 (9, 12): a refusal is explained at once, and the timer runs on.
    @Test
    fun aRefusalIsExplainedAtOnceAndTheTimerRunsOn() {
        timers.access.state = NotificationState.OFF
        show(soup)
        start(0)
        compose.waitUntil(PATIENCE_MS) { requested() != null }
        assertFalse(explained())
        answer(granted = false)
        awaitExplanation()
        assertEquals(context.getString(R.string.timer_unheard_off), explanation())
        AccessibilityChecks.assertTouchTargets(compose, expectAtLeast = 2)
        assertEquals(1, held().size)
        assertFalse(held().single().hasEnded(timers.clock.now()))
    }

    // FLOW-013 (10): refused as often as the platform allows, it answers at once with a refusal: the explanation.
    @Test
    fun aPlatformThatNoLongerAsksLeadsToTheExplanation() {
        timers.access.state = NotificationState.OFF
        show(soup)
        start(0)
        compose.waitUntil(PATIENCE_MS) { requested() != null }
        answer(granted = false)
        awaitExplanation()
        compose.onNodeWithTag(TimerTags.UNHEARD_DISMISS).performClick()
        compose.waitUntil(PATIENCE_MS) { !explained() }
        // The next start asks the platform again, which refuses without asking; the explanation follows.
        val first = activity.lastRequestedPermission
        start(2)
        compose.waitUntil(PATIENCE_MS) { activity.lastRequestedPermission !== first }
        answer(granted = false)
        awaitExplanation()
        assertEquals(2, held().size)
    }

    // FLOW-013 (11): granted at the request, nothing more is shown.
    @Test
    fun grantedAtTheRequestNothingMoreIsShown() {
        timers.access.state = NotificationState.OFF
        show(soup)
        start(0)
        compose.waitUntil(PATIENCE_MS) { requested() != null }
        timers.access.state = NotificationState.PERMITTED
        answer(granted = true)
        compose.waitForIdle()
        assertFalse(explained())
    }

    // FLOW-013 (10): the control opens the application's notification settings.
    @Test
    fun theControlOpensTheApplicationsNotificationSettings() {
        timers.access.state = NotificationState.OFF
        show(soup)
        start(0)
        compose.waitUntil(PATIENCE_MS) { requested() != null }
        answer(granted = false)
        awaitExplanation()
        compose.onNodeWithTag(TimerTags.UNHEARD_SETTINGS).performClick()
        compose.waitUntil(PATIENCE_MS) { !explained() }
        val opened = shadowOf(compose.activity).nextStartedActivity
        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, opened.action)
        assertEquals(context.packageName, opened.getStringExtra(Settings.EXTRA_APP_PACKAGE))
    }

    // FLOW-013 (8, 10): on Android 10 to 12 nothing is requested; with notifications off, the explanation at once.
    @Test
    fun withoutAPlatformRequestNotificationsOffAreExplainedAtOnce() {
        timers.access.hasRequest = false
        timers.access.state = NotificationState.OFF
        show(soup)
        start(0)
        awaitExplanation()
        assertNull(requested())
        assertEquals(context.getString(R.string.timer_unheard_off), explanation())
        assertEquals(1, held().size)
    }

    // FLOW-013 (8, 11): on Android 10 to 12 with notifications on, nothing is requested and nothing shown.
    @Test
    fun withoutAPlatformRequestAndNotificationsOnNothingIsShown() {
        timers.access.hasRequest = false
        show(soup)
        start(0)
        awaitHeld { it.size == 1 }
        compose.waitForIdle()
        assertTrue(timers.access.reads > 0)
        assertNull(requested())
        assertFalse(explained())
    }

    // FLOW-013 (11): permitted, starting a timer shows nothing about notifications.
    @Test
    fun permittedNothingIsShown() {
        show(soup)
        start(0)
        start(2)
        awaitHeld { it.size == 2 }
        compose.waitForIdle()
        assertTrue(timers.access.reads >= 2)
        assertNull(requested())
        assertFalse(explained())
    }

    // FLOW-013 (13): the timers' channel off alone is explained as such, and its control opens that channel.
    @Test
    fun timerNotificationsOffAloneAreExplainedAndLeadToTheirChannel() {
        timers.access.state = NotificationState.TIMERS_OFF
        show(soup)
        start(0)
        awaitExplanation()
        assertNull(requested())
        assertEquals(context.getString(R.string.timer_unheard_timers_off), explanation())
        compose.onNodeWithTag(TimerTags.UNHEARD_SETTINGS).performClick()
        compose.waitUntil(PATIENCE_MS) { !explained() }
        val opened = shadowOf(compose.activity).nextStartedActivity
        assertEquals(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS, opened.action)
        assertEquals(TimerChannels.ENDED, opened.getStringExtra(Settings.EXTRA_CHANNEL_ID))
    }

    // FLOW-013 (5): with notifications refused, the timer still ends and is shown finished in the application.
    @Test
    fun refusedTheTimerStillEndsAndShowsFinished() {
        timers.access.state = NotificationState.OFF
        show(soup)
        start(0)
        compose.waitUntil(PATIENCE_MS) { requested() != null }
        answer(granted = false)
        awaitExplanation()
        compose.onNodeWithTag(TimerTags.UNHEARD_DISMISS).performClick()
        pass(10.minutes)
        awaitStatus(0, context.getString(R.string.timer_finished))
    }
}
