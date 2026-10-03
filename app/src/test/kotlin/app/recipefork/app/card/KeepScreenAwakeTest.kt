package app.recipefork.app.card

import app.recipefork.app.ui.AppNavHost
import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.editor.EditorEnvironment
import app.recipefork.app.editor.EditorTags
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.editor.SAMPLE_DICTIONARIES
import app.recipefork.app.editor.TestDevice
import app.recipefork.app.library.LibrarySession
import app.recipefork.app.library.LibraryTags
import app.recipefork.app.library.LibraryView
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.recipe.Recipe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * The screen stays on while a card is shown (FLOW-014): for 15 minutes after the last touch on it, then it is left
 * to the device's own setting until the next touch, and at once when the reader leaves the card. The 15 minutes are
 * measured by [TestAwakeClock], which the tests move by hand.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class KeepScreenAwakeTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val device = TestDevice(context)
    private val active = ActiveBook(BookName.Pool, book.database)
    private val recipes = RecipeRepository(active)
    private val session = LibrarySession(SavedStateHandle())
    private val clock = TestAwakeClock()
    private val now = Instant.parse("2026-10-01T12:00:00Z")

    @Before
    fun setUp() {
        compose.registerIdlingResource(book.idling)
        compose.registerIdlingResource(device.idling)
        runBlocking { device.database.dictionaryDao().replace(SAMPLE_DICTIONARIES) }
    }

    @After
    fun close() {
        compose.unregisterIdlingResource(book.idling)
        compose.unregisterIdlingResource(device.idling)
        book.close()
        device.close()
    }

    /** Shows the library with the card of a saved recipe open, and waits until its 15 minutes have begun. */
    private fun openACard() {
        val recipe = Recipe.create(now, owner = null).copy(title = "Borscht")
        runBlocking { recipes.save(recipe) }
        session.choose(LibraryView.OWN)
        session.openCard(recipe.id.value)
        val editor = EditorEnvironment(active, device.catalog(context)) { now }
        val card = CardEnvironment { device.database.dictionaryDao().ingredients() }
        compose.setContent {
            CompositionLocalProvider(LocalAwakeClock provides clock) {
                MaterialTheme { AppNavHost(session, recipes, editor = editor, card = card) }
            }
        }
        compose.awaitCard()
        awaitAFreshPeriod()
    }

    /** Waits until the 15 minutes run from the present moment: the last opening or touch has been counted. */
    private fun awaitAFreshPeriod() {
        compose.waitForIdle()
        compose.waitUntil(PATIENCE_MS) { clock.periodRunsFromNow() }
    }

    /** Moves the clock and lets the card see that it moved. */
    private fun pass(time: Duration) {
        compose.runOnIdle { clock.advance(time) }
        compose.waitForIdle()
    }

    private fun touchTheCard() {
        compose.onNodeWithTag(CardTags.TITLE).performTouchInput { down(center); up() }
        awaitAFreshPeriod()
    }

    /** Whether any view of the window asks for the screen to stay on: what the window reads to keep it so. */
    private fun screenKeptOn(): Boolean = compose.runOnIdle { anyKeepsScreenOn(compose.activity.window.decorView) }

    private fun anyKeepsScreenOn(view: View): Boolean =
        view.keepScreenOn || (view is ViewGroup && (0 until view.childCount).any { anyKeepsScreenOn(view.getChildAt(it)) })

    private fun assertKeptOn() = assertTrue("the screen is kept on", screenKeptOn())

    private fun assertLeftToTheDevice() = assertFalse("the screen is left to the device's setting", screenKeptOn())

    // FLOW-014 (1): a card open and untouched for a minute keeps the screen on.
    @Test
    fun aCardUntouchedForAMinuteKeepsTheScreenOn() {
        openACard()
        assertKeptOn()
        pass(1.minutes)
        assertKeptOn()
    }

    // FLOW-014 (2): after 15 minutes without a touch, the screen is left to the device's own setting.
    @Test
    fun aCardUntouchedFor15MinutesLeavesTheScreenToTheDevice() {
        openACard()
        pass(15.minutes - 1.seconds)
        assertKeptOn()
        pass(1.seconds)
        assertLeftToTheDevice()
    }

    // FLOW-014 (3): once left to sleep, a touch on the card keeps the screen on for another 15 minutes.
    @Test
    fun aTouchAfterTheScreenWasLeftKeepsItOnForAnother15Minutes() {
        openACard()
        pass(15.minutes)
        assertLeftToTheDevice()
        touchTheCard()
        assertKeptOn()
        pass(15.minutes - 1.seconds)
        assertKeptOn()
        pass(1.seconds)
        assertLeftToTheDevice()
    }

    // FLOW-014 (2, 3): the 15 minutes count from the last touch, not from the opening.
    @Test
    fun aTouchBeforeThe15MinutesAreUpCountsThemAgainFromTheTouch() {
        openACard()
        pass(10.minutes)
        touchTheCard()
        pass(14.minutes)
        assertKeptOn()
        pass(1.minutes)
        assertLeftToTheDevice()
    }

    // FLOW-014 (4): back from the card to the book leaves the screen to the device at once.
    @Test
    fun leavingTheCardForTheBookLeavesTheScreenToTheDeviceAtOnce() {
        openACard()
        assertKeptOn()
        compose.onNodeWithTag(CardTags.BACK).performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.OWN_LIST), PATIENCE_MS)
        assertLeftToTheDevice()
    }

    // FLOW-014 (4): the edit action leaves the screen to the device at once; leaving the editor shows the card,
    // which keeps it on again.
    @Test
    fun leavingTheCardForTheEditorLeavesTheScreenToTheDeviceAtOnce() {
        openACard()
        assertKeptOn()
        compose.onNodeWithTag(CardTags.EDIT).performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(EditorTags.FORM), PATIENCE_MS)
        assertLeftToTheDevice()
        compose.onNodeWithTag(EditorTags.BACK).performClick()
        compose.awaitCard()
        awaitAFreshPeriod()
        assertKeptOn()
    }

    // Coming back to the application while the card is shown counts as a touch.
    @Test
    fun comingBackToTheApplicationKeepsTheScreenOnForAnother15Minutes() {
        openACard()
        pass(15.minutes)
        assertLeftToTheDevice()
        compose.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        awaitAFreshPeriod()
        assertKeptOn()
    }
}

/** A clock that stands still until a test moves it, and tells which waits on it are running. */
class TestAwakeClock : AwakeClock {
    private val now = MutableStateFlow(Duration.ZERO)
    private val waits = mutableListOf<Duration>()

    override suspend fun wait(duration: Duration) {
        val until = now.value + duration
        synchronized(waits) { waits += until }
        try {
            now.first { it >= until }
        } finally {
            synchronized(waits) { waits -= until }
        }
    }

    fun advance(by: Duration) {
        now.value += by
    }

    /** True when exactly one wait is running and it ends [AWAKE_WITHOUT_A_TOUCH] from the present moment. */
    fun periodRunsFromNow(): Boolean = synchronized(waits) { waits.toList() } == listOf(now.value + AWAKE_WITHOUT_A_TOUCH)
}
