package app.recipefork.app.timer

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import app.recipefork.app.library.LibraryView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.MainActivity
import app.recipefork.app.RecipeForkApplication
import app.recipefork.app.book.BookName
import app.recipefork.app.card.CardMode
import app.recipefork.app.card.CardTags
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.library.LibraryTags
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.Timer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import java.time.Instant
import kotlin.time.Duration.Companion.minutes

/**
 * The application itself: a timer's notification opens its recipe's card scrolled to the timer's step (FLOW-013 (4)),
 * from a closed application and from an open one; and the platform's request for notifications is made at the first
 * timer started, never at launch (FLOW-013 (8)).
 */
@RunWith(AndroidJUnit4::class)
class TimerNotificationOpensCardTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val application = ApplicationProvider.getApplicationContext<RecipeForkApplication>()
    private val container = application.container
    private val manager = application.getSystemService(NotificationManager::class.java)
    private val now = Instant.parse("2026-10-02T12:00:00Z")
    private val far = 25

    /** A long recipe whose step [far] carries a timer, far below the top of the card. */
    private val stew = Recipe.create(now, owner = null).copy(
        title = "Stew",
        steps = (0 until 30).map { position ->
            Step(
                position,
                "Step $position: brown the meat in batches, then return it all to the pot with the onions and stock",
                timer = if (position == far) Timer(20.minutes, "Stew") else null,
            )
        },
    )
    private val key = TimerKey(BookName.Pool, stew.id, far)
    private var opened: ActivityController<MainActivity>? = null

    @After
    fun close() {
        opened?.pause()?.stop()?.destroy()
        runBlocking { container.timers.cancel(key) }
    }

    /** The intent of the notification announcing the stew's timer, as tapping it sends it. */
    private fun notificationIntent(): Intent {
        runBlocking { container.recipes.save(stew) }
        AndroidTimerNotices(application).announce(RunningTimer(key, "Stew", "Stew", 20.minutes, now))
        val notification = shadowOf(manager).allNotifications.single()
        return shadowOf(notification.contentIntent).savedIntent
    }

    private fun launch(intent: Intent? = null): ActivityController<MainActivity> =
        (if (intent == null) Robolectric.buildActivity(MainActivity::class.java) else Robolectric.buildActivity(MainActivity::class.java, intent))
            .setup().also { opened = it; compose.waitForIdle() }

    /**
     * Waits for [condition], letting the main thread run between looks: the device's own databases have no idling
     * resource, so their results reach the main thread only while it is let run.
     */
    private fun awaitLooping(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + PATIENCE_MS
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "the condition was never met" }
            compose.waitForIdle()
            Thread.sleep(10)
        }
    }

    private fun awaitTag(tag: String) =
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }

    private fun assertTheCardShowsTheStep() {
        awaitTag(CardTags.step(far))
        compose.waitUntil(PATIENCE_MS) {
            runCatching { compose.onNodeWithTag(CardTags.step(far)).assertIsDisplayed() }.isSuccess
        }
        compose.onNodeWithTag(CardTags.step(far)).assertIsDisplayed()
        compose.onNodeWithTag(TimerTags.start(far)).assertIsDisplayed()
        // On the Cooking tab, where the timer runs, whichever tab was chosen last.
        compose.onNodeWithTag(CardTags.tab(CardMode.COOKING)).assertIsSelected()
        // The card was brought down to it: its top is out of view.
        compose.onNodeWithTag(CardTags.step(0)).assertIsNotDisplayed()
    }

    // FLOW-013 (4): the application closed, tapping the notification opens it on the card at the timer's step.
    @Test
    fun tappingTheNotificationOpensTheCardAtTheStep() {
        container.lastCardTab.keep(CardMode.VIEW)
        val intent = notificationIntent()
        launch(intent)
        assertTheCardShowsTheStep()
        // Followed once: the intent leads nowhere again.
        assertNull(TimerIntents.stepOf(opened!!.get().intent))
    }

    // FLOW-013 (4): the application open on the book, the notification brings the card at the step.
    @Test
    fun tappingTheNotificationWithTheApplicationOpenShowsTheCardAtTheStep() {
        val intent = notificationIntent()
        launch()
        awaitTag(LibraryTags.TITLE)
        opened!!.newIntent(intent)
        compose.waitForIdle()
        assertTheCardShowsTheStep()
    }

    // FLOW-013 (4) over a held place: a card left scrolled elsewhere is brought to the timer's step all the same.
    @Test
    fun theNotificationsStepOutranksThePlaceTheCardWasLeftAt() {
        val intent = notificationIntent()
        launch()
        compose.onNodeWithTag(LibraryTags.tab(LibraryView.OWN)).performClick()
        awaitTag(LibraryTags.entry(stew.id.value))
        compose.onNodeWithTag(LibraryTags.entry(stew.id.value)).performClick()
        // The card opens on View, the tab last chosen, and is left scrolled down.
        awaitTag(CardTags.step(0))
        compose.onNodeWithTag(CardTags.tab(CardMode.VIEW)).assertIsSelected()
        compose.onNodeWithTag(CardTags.CONTENT).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 600f) }
        compose.waitForIdle()
        compose.onNodeWithTag(CardTags.BACK).performClick()
        awaitTag(LibraryTags.TITLE)
        opened!!.newIntent(intent)
        compose.waitForIdle()
        assertTheCardShowsTheStep()
    }

    // FLOW-013 (8): nothing is requested at launch; the first timer started makes the platform's request.
    @Test
    fun theRequestIsMadeAtTheFirstTimerAndNotAtLaunch() {
        launch()
        awaitTag(LibraryTags.TITLE)
        assertNull(shadowOf(opened!!.get()).lastRequestedPermission)
        opened!!.newIntent(notificationIntent())
        compose.waitForIdle()
        assertTheCardShowsTheStep()
        assertNull(shadowOf(opened!!.get()).lastRequestedPermission)
        compose.onNodeWithTag(TimerTags.start(far)).performClick()
        awaitLooping { shadowOf(opened!!.get()).lastRequestedPermission != null }
        assertEquals(
            listOf(Manifest.permission.POST_NOTIFICATIONS),
            shadowOf(opened!!.get()).lastRequestedPermission.requestedPermissions.toList(),
        )
        assertEquals("Stew", runBlocking { container.timers.get(key) }?.name)
    }
}
