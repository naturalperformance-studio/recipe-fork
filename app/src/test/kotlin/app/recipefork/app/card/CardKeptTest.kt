package app.recipefork.app.card

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.editor.textsOf
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.timer.LocalCardTimers
import app.recipefork.app.timer.TestTimers
import app.recipefork.app.timer.TimerTags
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.Timer
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.Rational
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
import kotlin.time.Duration.Companion.minutes

/**
 * The card as it comes back from the end of the process while away (FLOW-012), the composition gone and only what
 * the session saved left — here a map standing for the application's saved state: the same count and fixing, the same
 * line at the top whatever the text scale did meanwhile, and a running timer at the time left by the clock.
 */
@RunWith(AndroidJUnit4::class)
class CardKeptTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val timers = TestTimers(context)
    private val recipes = RecipeRepository(ActiveBook(BookName.Pool, book.database))
    private val environment = CardEnvironment { emptyList() }
    private val now = Instant.parse("2026-10-03T12:00:00Z")

    /** What the session saved, which outlives the composition as saved state outlives the process. */
    private val saved = HashMap<String, String>()
    private val session = TextCardSession(saved::get) { key, value -> if (value == null) saved.remove(key) else saved[key] = value }
    private var fontScale by mutableFloatStateOf(1f)

    private val stew = Recipe.create(now, null).copy(
        title = "Stew",
        servings = 4,
        ingredients = listOf(
            Ingredient(0, null, freeTextName = "Beef", unitType = UnitType.EXACT, unit = QuantityUnit.GRAM, quantity = Rational.of(800)),
        ) + (1..15).map {
            Ingredient(it, null, freeTextName = "Spice $it", unitType = UnitType.HOUSEHOLD, unit = QuantityUnit.TEASPOON, quantity = Rational.ONE)
        },
        steps = listOf(Step(0, "Brown the beef", timer = Timer(10.minutes))) +
            (1..10).map { Step(it, "Step $it: stir the pot slowly and look at it for a while before going on") },
    )

    @Before
    fun setUp() {
        compose.registerIdlingResource(book.idling)
        compose.registerIdlingResource(timers.idling)
        runBlocking { recipes.save(stew) }
    }

    @After
    fun close() {
        compose.unregisterIdlingResource(book.idling)
        compose.unregisterIdlingResource(timers.idling)
        book.close()
        timers.close()
    }

    private fun show(): StateRestorationTester {
        val tester = StateRestorationTester(compose)
        tester.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalCardTimers provides timers.card,
                LocalAwakeClock provides TestAwakeClock(),
                LocalDensity provides Density(density.density, fontScale),
            ) {
                MaterialTheme { CardScreen(stew.id, recipes, environment, onBack = {}, onEdit = {}, session = session) }
            }
        }
        awaitLines()
        return tester
    }

    private fun awaitLines() {
        compose.awaitCard()
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(hasTestTag(CardTags.ingredient(0))).fetchSemanticsNodes().size == 1 }
    }

    private fun servings() = compose.textsOf(CardTags.SERVINGS).single()
    private fun fixed(position: Int) = "fixed" in compose.textsOf(CardTags.ingredient(position))

    private fun timerStatus(): String? = compose.onAllNodes(hasTestTag(TimerTags.status(0)), true)
        .fetchSemanticsNodes().singleOrNull()
        ?.config?.getOrElseNullable(SemanticsProperties.Text) { null }
        ?.joinToString("") { it.text }

    private fun scrollBy(pixels: Float) {
        compose.onNodeWithTag(CardTags.CONTENT).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, pixels) }
        compose.waitForIdle()
    }

    // Scenario 3, FLOW-012 (1, 2, 3, 5): count, fixing, the line at the top under a larger text, the timer by the clock.
    @Test
    fun theEndOfTheProcessWhileAwayReturnsToTheSameCard() {
        val tester = show()
        compose.onNodeWithTag(CardTags.SERVINGS_MORE).performScrollTo().performClick()
        compose.onNodeWithTag(CardTags.ingredient(0)).performScrollTo().performClick()
        compose.onNodeWithTag(CardTags.FIX_FIELD).performTextReplacement("600")
        compose.onNodeWithTag(CardTags.FIX_CONFIRM).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { fixed(0) }
        compose.onNodeWithTag(TimerTags.start(0)).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { timerStatus() == "10:00" }
        val count = servings()
        scrollBy(1200f)
        val top = compose.firstAtTheTop()

        compose.runOnIdle {
            timers.clock.advance(3.minutes)
            fontScale = 2f
        }
        tester.emulateSavedInstanceStateRestore()
        awaitLines()

        assertEquals(count, servings())
        assertTrue(fixed(0))
        compose.waitUntil(PATIENCE_MS) { compose.firstAtTheTop() == top }
        assertEquals(top, compose.firstAtTheTop())
        compose.waitUntil(PATIENCE_MS) { timerStatus() == "7:00" }
    }

    // The reader's own scroll after the return is theirs: the card no longer holds them at the restored line.
    @Test
    fun aScrollAfterTheReturnIsKept() {
        val tester = show()
        scrollBy(1500f)
        tester.emulateSavedInstanceStateRestore()
        awaitLines()
        val restored = compose.firstAtTheTop()
        scrollBy(-400f)
        val moved = compose.firstAtTheTop()
        assertTrue("moved from $restored", moved != restored)
        tester.emulateSavedInstanceStateRestore()
        awaitLines()
        compose.waitUntil(PATIENCE_MS) { compose.firstAtTheTop() == moved }
    }

    // A card never scrolled holds no place, and comes back at its top.
    @Test
    fun aCardAtItsTopComesBackAtItsTop() {
        val tester = show()
        tester.emulateSavedInstanceStateRestore()
        awaitLines()
        assertNull(session.heldAnchor(stew.id))
        assertEquals(CardTags.ingredient(0), compose.firstAtTheTop())
    }
}
