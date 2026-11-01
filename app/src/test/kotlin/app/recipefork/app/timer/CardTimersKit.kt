package app.recipefork.app.timer

import app.recipefork.app.card.CardMode
import app.recipefork.app.data.HeldLastCardTab
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import app.recipefork.app.R
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.card.CardEnvironment
import app.recipefork.app.data.HeldUsualServings
import app.recipefork.app.card.CardScreen
import app.recipefork.app.card.awaitCard
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.ui.LocalHaptics
import app.recipefork.app.ui.RecordingHaptics
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.Timer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/** A card over a book and timers held in memory, run by a clock the test moves, as `CardTimersTest` sets it up. */
abstract class CardTimersKit {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    protected val context: Context = ApplicationProvider.getApplicationContext()
    protected val book = TestBook(context)
    protected val timers = TestTimers(context)
    protected val recipes = RecipeRepository(ActiveBook(BookName.Pool, book.database))

    /** The reader's usual serving count the cards open at; never set unless a test sets it. */
    protected val usual = HeldUsualServings()

    /** The touch feedback the card gives, recorded. */
    protected val haptics = RecordingHaptics()
    private var open by mutableStateOf<RecipeId?>(null)

    /** The theme the card is shown in. */
    @Composable
    protected open fun Theme(content: @Composable () -> Unit) = MaterialTheme(content = content)

    /** The font scale the card is shown at. */
    protected open val fontScale: Float = 1f

    protected val now: Instant = Instant.parse("2026-10-02T12:00:00Z")
    protected val beansText = "Simmer the beans with the onion and the bay leaf until they are soft through and through"
    protected val soup = Recipe.create(now, owner = null).copy(
        title = "Soup",
        steps = listOf(
            Step(0, "Bring the water to the boil", timer = Timer(10.minutes)),
            Step(1, "Stir"),
            Step(2, beansText, timer = Timer(1.hours + 30.minutes, "Beans")),
        ),
    )

    @Before
    fun setUpTheCard() {
        compose.registerIdlingResource(book.idling)
        compose.registerIdlingResource(timers.idling)
        runBlocking { recipes.save(soup) }
        val environment = CardEnvironment(usual, lastTab = HeldLastCardTab(CardMode.COOKING)) { emptyList() }
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalCardTimers provides timers.card,
                LocalDensity provides Density(density.density, fontScale),
                LocalHaptics provides haptics,
            ) {
                Theme {
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
    fun closeTheCard() {
        compose.unregisterIdlingResource(book.idling)
        compose.unregisterIdlingResource(timers.idling)
        book.close()
        timers.close()
    }

    protected fun show(recipe: Recipe) {
        compose.runOnIdle { open = recipe.id }
        compose.awaitCard()
    }

    protected fun leaveTheCard() {
        compose.runOnUiThread { open = null }
    }

    protected fun control(position: Int, index: Int = 0): SemanticsNodeInteraction =
        compose.onNodeWithTag(TimerTags.start(position, index)).performScrollTo()

    protected fun start(position: Int, index: Int = 0) {
        control(position, index).performClick()
        compose.waitForIdle()
    }

    protected fun statusOf(position: Int, index: Int = 0): String? = compose.onAllNodes(hasTestTag(TimerTags.status(position, index)), true)
        .fetchSemanticsNodes().singleOrNull()
        ?.config?.getOrElseNullable(SemanticsProperties.Text) { null }
        ?.joinToString("") { it.text }

    protected fun awaitStatus(position: Int, text: String) = awaitStatus(position, 0, text)

    protected fun awaitStatus(position: Int, index: Int, text: String) {
        compose.waitUntil(PATIENCE_MS) { statusOf(position, index) == text }
        assertEquals(text, statusOf(position, index))
    }

    protected fun pass(time: Duration) {
        compose.runOnIdle { timers.clock.advance(time) }
        compose.waitForIdle()
    }

    protected fun shown(tag: String): Boolean = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    protected fun held(): List<RunningTimer> = runBlocking { timers.store.all.first() }

    /** Waits, letting the main thread and the timers' database work run, until what is held meets [condition]. */
    protected fun awaitHeld(condition: (List<RunningTimer>) -> Boolean) {
        val deadline = System.currentTimeMillis() + PATIENCE_MS
        while (true) {
            compose.waitForIdle()
            val now = held()
            if (condition(now)) return
            check(System.currentTimeMillis() < deadline) { "held timers never met the condition: $now" }
            Thread.sleep(10)
        }
    }

    /** [name] as a running timer reads it under its countdown, on its step and in the bar: «<name> · running». */
    protected fun runningName(name: String): String = context.getString(R.string.timer_running_name, name)

    protected fun key(position: Int) = TimerKey(BookName.Pool, soup.id, position)

    protected companion object {
        const val AWAY = "away"
    }
}
