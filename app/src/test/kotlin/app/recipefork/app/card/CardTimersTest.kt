package app.recipefork.app.card

import app.recipefork.app.data.HeldLastCardTab
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.R
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.timer.LocalCardTimers
import app.recipefork.app.timer.TestTimers
import app.recipefork.app.timer.TimerKey
import app.recipefork.app.timer.TimerTags
import app.recipefork.app.ui.AccessibilityChecks
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.Timer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Timers on the card (FLOW-016, FLOW-013 (6, 7), FLOW-021): started on their steps, counting down there by the clock,
 * several at once, held across leaving the card and the application, cancelled, restarted only when the reader says
 * so. The clock is a [app.recipefork.app.timer.TestTimerClock] the tests move by hand.
 */
@RunWith(AndroidJUnit4::class)
class CardTimersTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val timers = TestTimers(context)
    private val recipes = RecipeRepository(ActiveBook(BookName.Pool, book.database))
    private val now = Instant.parse("2026-10-02T12:00:00Z")
    private var open by mutableStateOf<RecipeId?>(null)

    private val beansText = "Simmer the beans with the onion and the bay leaf until they are soft through and through"
    private val soup = Recipe.create(now, owner = null).copy(
        title = "Soup",
        steps = listOf(
            Step(0, "Bring the water to the boil", timer = Timer(10.minutes)),
            Step(1, "Stir"),
            Step(2, beansText, timer = Timer(1.hours + 30.minutes, "Beans")),
        ),
    )
    private val bread = Recipe.create(now, owner = null).copy(
        title = "Bread",
        steps = listOf(Step(0, "Let the dough rise", timer = Timer(45.minutes))),
    )

    private val finished get() = context.getString(R.string.timer_finished)

    @Before
    fun setUp() {
        compose.registerIdlingResource(book.idling)
        compose.registerIdlingResource(timers.idling)
        runBlocking {
            recipes.save(soup)
            recipes.save(bread)
        }
        val environment = CardEnvironment(lastTab = HeldLastCardTab(CardMode.COOKING)) { emptyList() }
        compose.setContent {
            CompositionLocalProvider(LocalCardTimers provides timers.card, LocalAwakeClock provides TestAwakeClock()) {
                MaterialTheme {
                    val id = open
                    if (id == null) {
                        Box(Modifier.testTag(AWAY))
                    } else {
                        CardScreen(id, recipes, environment, onBack = { open = null }, onEdit = {})
                    }
                }
            }
        }
    }

    @After
    fun close() {
        compose.unregisterIdlingResource(book.idling)
        compose.unregisterIdlingResource(timers.idling)
        book.close()
        timers.close()
    }

    private fun show(recipe: Recipe) {
        compose.runOnIdle { open = recipe.id }
        compose.awaitCard()
    }

    private fun leaveTheCard() {
        compose.runOnIdle { open = null }
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(hasTestTag(AWAY)).fetchSemanticsNodes().size == 1 }
    }

    private fun control(position: Int): SemanticsNodeInteraction =
        compose.onNodeWithTag(TimerTags.start(position)).performScrollTo()

    private fun start(position: Int) {
        control(position).performClick()
        compose.waitForIdle()
    }

    private fun statusOf(position: Int): String? = compose.onAllNodes(hasTestTag(TimerTags.status(position)), true)
        .fetchSemanticsNodes().singleOrNull()
        ?.config?.getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.Text) { null }
        ?.joinToString("") { it.text }

    /** Waits until [position]'s timer reads [text]. */
    private fun awaitStatus(position: Int, text: String) {
        compose.waitUntil(PATIENCE_MS) { statusOf(position) == text }
        assertEquals(text, statusOf(position))
    }

    private fun pass(time: Duration) {
        compose.runOnIdle { timers.clock.advance(time) }
        compose.waitForIdle()
    }

    private fun questionShown(): Boolean =
        compose.onAllNodes(hasTestTag(TimerTags.RESTART_QUESTION)).fetchSemanticsNodes().isNotEmpty()

    private fun answer(tag: String) {
        compose.waitUntil(PATIENCE_MS) { questionShown() }
        compose.onNodeWithTag(tag).performClick()
        compose.waitUntil(PATIENCE_MS) { !questionShown() }
    }

    private fun held() = runBlocking { timers.store.all.first() }

    // FLOW-016: a step with a timer shows a control that starts it, named by the timer's name — the one derived from
    // its step, or the one its author gave — beside its duration; a step with none shows nothing.
    @Test
    fun aStepWithATimerShowsAControlNamedByItsTimer() {
        show(soup)
        control(0).assertTextContainsAll("Bring the water to the boil", "10 min")
        control(2).assertTextContainsAll("Beans", "1 h 30 min")
        assertTrue(compose.onAllNodes(hasTestTag(TimerTags.start(1))).fetchSemanticsNodes().isEmpty())
        assertEquals(
            context.getString(R.string.timer_start_action),
            AccessibilityChecks.clickActionLabel(control(0).fetchSemanticsNode()),
        )
        AccessibilityChecks.assertTouchTargets(compose, expectAtLeast = 4)
        AccessibilityChecks.assertInteractiveElementsLabelled(compose, expectAtLeast = 4)
    }

    @Test
    fun aDerivedNameIsCutAsTheAnnouncementWouldBe() {
        val long = Recipe.create(now, owner = null).copy(steps = listOf(Step(0, beansText, timer = Timer(5.minutes))))
        runBlocking { recipes.save(long) }
        show(long)
        control(0).assertTextContainsAll("Simmer the beans with the onion and the bay leaf until they…")
    }

    // FLOW-016 (1, 9): started, a timer shows its remaining time on its step, counting down by the clock.
    @Test
    fun aStartedTimerCountsDownOnItsStepByTheClock() {
        show(soup)
        start(0)
        awaitStatus(0, "10:00")
        control(0).assertTextContainsAll("Bring the water to the boil")
        pass(1.seconds)
        awaitStatus(0, "9:59")
        pass(4.minutes)
        awaitStatus(0, "5:59")
        AccessibilityChecks.assertTouchTargets(compose, expectAtLeast = 5)
        AccessibilityChecks.assertInteractiveElementsLabelled(compose, expectAtLeast = 5)
    }

    // FLOW-016 (1, 9): two timers of one recipe run at once, each on its own step.
    @Test
    fun twoTimersOfOneRecipeRunAtOnceEachOnItsStep() {
        show(soup)
        start(0)
        start(2)
        awaitStatus(0, "10:00")
        awaitStatus(2, "1:30:00")
        pass(5.minutes)
        awaitStatus(0, "5:00")
        awaitStatus(2, "1:25:00")
    }

    // FLOW-016 (1, 10), FLOW-013 (6): timers of two recipes run at once, and each recipe, opened again, shows its
    // own timer by the clock, however long its card was not shown.
    @Test
    fun timersOfTwoRecipesKeepRunningWhileTheirCardsAreNotShown() {
        show(soup)
        start(0)
        leaveTheCard()
        pass(2.minutes)
        show(bread)
        start(0)
        awaitStatus(0, "45:00")
        leaveTheCard()
        pass(1.minutes)
        show(soup)
        awaitStatus(0, "7:00")
        show(bread)
        awaitStatus(0, "44:00")
        assertEquals(2, held().size)
    }

    // FLOW-016 (10): leaving the application and coming back, the timer has run by the clock meanwhile.
    @Test
    fun aTimerKeepsRunningWhileTheApplicationIsLeft() {
        show(soup)
        start(0)
        awaitStatus(0, "10:00")
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.runOnIdle { timers.clock.advance(3.minutes) }
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        awaitStatus(0, "7:00")
    }

    // FLOW-013 (7): a cancelled timer is gone; it never ends and nothing is left to announce it.
    @Test
    fun aCancelledTimerNeverEnds() {
        show(soup)
        start(0)
        start(2)
        awaitStatus(0, "10:00")
        pass(1.minutes)
        compose.onNodeWithTag(TimerTags.cancel(0)).performScrollTo().performClick()
        awaitStatus(0, "10 min")
        assertEquals(listOf(TimerKey(BookName.Pool, soup.id, 0)), timers.announcements.cancelled)
        assertNull(held().singleOrNull { it.key.stepPosition == 0 })
        pass(20.minutes)
        awaitStatus(0, "10 min")
        awaitStatus(2, "1:09:00")
    }

    // FLOW-021 (1, 2, 4): starting a running timer asks; confirmed, it runs again from its full duration, and the
    // other timer is untouched.
    @Test
    fun aRunningTimerStartedAgainRestartsOnlyOnConfirmation() {
        show(soup)
        start(0)
        start(2)
        pass(3.minutes)
        awaitStatus(0, "7:00")
        start(0)
        answer(TimerTags.RESTART_CONFIRM)
        awaitStatus(0, "10:00")
        awaitStatus(2, "1:27:00")
    }

    // FLOW-021 (3): declined, it keeps running with the time it had.
    @Test
    fun aDeclinedRestartKeepsTheTimeItHad() {
        show(soup)
        start(0)
        pass(3.minutes)
        val before = held().single()
        start(0)
        answer(TimerTags.RESTART_DECLINE)
        pass(1.minutes)
        awaitStatus(0, "6:00")
        assertEquals(before, held().single())
    }

    // A timer that ends while the card is open is shown as finished on its step; the other keeps running.
    @Test
    fun aTimerThatEndsWhileTheCardIsOpenIsShownFinished() {
        show(soup)
        start(0)
        start(2)
        pass(10.minutes - 1.seconds)
        awaitStatus(0, "0:01")
        pass(1.seconds)
        awaitStatus(0, finished)
        awaitStatus(2, "1:20:00")
    }

    // FLOW-021 (5): a timer that has ended starts again with no question.
    @Test
    fun anEndedTimerStartsAgainWithNoQuestion() {
        show(soup)
        start(0)
        pass(10.minutes)
        awaitStatus(0, finished)
        start(0)
        awaitStatus(0, "10:00")
        assertTrue(!questionShown())
        assertEquals(2, timers.announcements.started.size)
    }

    private companion object {
        const val AWAY = "away"
    }
}

private fun SemanticsNodeInteraction.assertTextContainsAll(vararg parts: String) {
    val node = fetchSemanticsNode()
    val text = node.config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.Text) { null }
        ?.joinToString(" ") { it.text }
        .orEmpty()
    parts.forEach { assertTrue("“$it” in “$text”", text.contains(it)) }
}
