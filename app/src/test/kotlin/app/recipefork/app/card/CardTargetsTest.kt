package app.recipefork.app.card

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.timer.LocalCardTimers
import app.recipefork.app.timer.TestTimers
import app.recipefork.app.timer.TimerTags
import app.recipefork.app.ui.AccessibilityChecks
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.Timer
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.recipe.fork
import app.recipefork.core.scaling.Rational
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import kotlin.time.Duration.Companion.minutes

/**
 * A11Y-003 (2), A11Y-007: every control of the cooking card is at least 56 by 56 dp — the bar's back, fork and edit,
 * the provenance link, the serving count's − and +, a timer's start, an ingredient line that opens fixing and every
 * control of the fixing (the amount, each unit, Recalculate, Cancel, Release) — and the held-fork offer's two buttons.
 * Each is measured by name, and then every interactive element on the screen is measured against the same figure.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CardTargetsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val timers = TestTimers(context)
    private val recipes = RecipeRepository(ActiveBook(BookName.Pool, book.database))
    private val environment = CardEnvironment { emptyList() }
    private val now = Instant.parse("2026-10-03T12:00:00Z")

    @Before
    fun setUp() {
        compose.registerIdlingResource(book.idling)
        compose.registerIdlingResource(timers.idling)
    }

    @After
    fun close() {
        compose.unregisterIdlingResource(book.idling)
        compose.unregisterIdlingResource(timers.idling)
        book.close()
        timers.close()
    }

    /** An editable fork of a recipe the book holds: provenance, servings, a row that fixes in g or kg, a timed step. */
    private fun fork(): Recipe {
        val parent = Recipe.create(now, null).copy(title = "Bread", servings = 2)
        val fork = parent.fork(now, owner = null).copy(
            title = "Rye bread",
            servings = 2,
            ingredients = listOf(
                Ingredient(0, null, freeTextName = "Flour", unitType = UnitType.EXACT, unit = QuantityUnit.GRAM, quantity = Rational.of(500)),
            ),
            steps = listOf(Step(0, "Knead the dough", timer = Timer(10.minutes))),
        )
        runBlocking { recipes.save(parent); recipes.save(fork) }
        return fork
    }

    @Test
    fun everyControlOfTheCardIsAtLeast56dp() = sweep(fontScale = 1f)

    // A11Y-004 with A11Y-003 (2): at twice the font scale on a narrow phone the bar wraps rather than squeezing an action.
    @Test
    @Config(qualifiers = "ru-w320dp-h640dp")
    fun everyControlIsAtLeast56dpAndWholeInRussianAt320dpAndTwiceTheFontScale() = sweep(fontScale = 2f)

    @Test
    @Config(qualifiers = "ru-w360dp-h640dp")
    fun everyControlIsAtLeast56dpAndWholeInRussianAt360dpAndTwiceTheFontScale() = sweep(fontScale = 2f)

    @Test
    @Config(qualifiers = "en-w320dp-h640dp")
    fun everyControlIsAtLeast56dpAndWholeInEnglishAt320dpAndTwiceTheFontScale() = sweep(fontScale = 2f)

    private fun sweep(fontScale: Float) {
        val recipe = fork()
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalCardTimers provides timers.card, LocalDensity provides Density(density.density, fontScale)) {
                MaterialTheme { CardScreen(recipe.id, recipes, environment, onBack = {}, onEdit = {}) }
            }
        }
        compose.awaitCard()
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(hasTestTag(CardTags.PROVENANCE)).fetchSemanticsNodes().size == 1 }
        // Each of the bar's actions whole: its label on one line, all of it on screen.
        listOf(CardTags.BACK, CardTags.FORK, CardTags.EDIT).forEach { tag ->
            compose.assertWhole(tag)
            compose.textLayoutsWithin(tag).forEach { assertEquals("$tag at $fontScale", 1, it.lineCount) }
        }
        if (fontScale == 1f) {
            // Where they fit, one line: back at the start, fork then edit at the end.
            val (back, fork, edit) = listOf(CardTags.BACK, CardTags.FORK, CardTags.EDIT)
                .map { compose.onNodeWithTag(it).fetchSemanticsNode().boundsInRoot }
            val bar = compose.onNodeWithTag(CardTags.BAR).fetchSemanticsNode().boundsInRoot
            assertEquals(back.top, fork.top, 0.5f)
            assertEquals(back.top, edit.top, 0.5f)
            assertTrue(back.right <= fork.left && fork.right <= edit.left && bar.right - edit.right < 9f * bar.width / 360f + 1f)
        }
        listOf(
            CardTags.BACK, CardTags.FORK, CardTags.EDIT, CardTags.PROVENANCE,
            CardTags.SERVINGS_FEWER, CardTags.SERVINGS_MORE, CardTags.ingredient(0), TimerTags.start(0),
        ).forEach { compose.assertCardTarget(it) }
        // Back, fork, edit, the provenance, fewer, more, the ingredient line, the timer.
        AccessibilityChecks.assertTouchTargets(compose, min = CardTarget, expectAtLeast = 8)

        // The fixing control, opened from its line, at first and once a fixing is held.
        compose.onNodeWithTag(CardTags.ingredient(0)).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(hasTestTag(CardTags.FIX)).fetchSemanticsNodes().isNotEmpty() }
        listOf(
            CardTags.FIX_FIELD, CardTags.fixUnit(QuantityUnit.GRAM), CardTags.fixUnit(QuantityUnit.KILOGRAM),
            CardTags.FIX_CANCEL, CardTags.FIX_CONFIRM,
        ).forEach { compose.assertCardTarget(it) }
        AccessibilityChecks.assertTouchTargets(compose, min = CardTarget, expectAtLeast = 13)
        compose.onNodeWithTag(CardTags.FIX_FIELD).performTextReplacement("250")
        compose.onNodeWithTag(CardTags.FIX_CONFIRM).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(hasTestTag(CardTags.FIX)).fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag(CardTags.ingredient(0)).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(hasTestTag(CardTags.FIX_RELEASE)).fetchSemanticsNodes().isNotEmpty() }
        compose.assertCardTarget(CardTags.FIX_RELEASE)
        AccessibilityChecks.assertTouchTargets(compose, min = CardTarget, expectAtLeast = 14)
    }

    @Test
    fun theHeldForkOffersButtonsAreAtLeast56dp() {
        compose.setContent { MaterialTheme { HeldForkDialog(onOpen = {}, onAnother = {}, onDismiss = {}) } }
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(hasTestTag(CardTags.FORK_HELD)).fetchSemanticsNodes().isNotEmpty() }
        compose.assertCardTarget(CardTags.FORK_HELD_OPEN)
        compose.assertCardTarget(CardTags.FORK_HELD_ANOTHER)
        AccessibilityChecks.assertTouchTargets(compose, min = CardTarget, expectAtLeast = 2)
    }
}
