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
import app.recipefork.app.ui.LARGE_TEXT_SCALE
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
        // Each of the bar's actions whole: an icon named for the screen reader, all of it on screen.
        listOf(CardTags.BACK, CardTags.EDIT, CardTags.MENU).forEach { tag ->
            val node = compose.onNodeWithTag(tag).fetchSemanticsNode()
            val screen = compose.onNodeWithTag(CardTags.SCREEN).fetchSemanticsNode().boundsInRoot
            assertTrue("$tag at $fontScale lies within the screen", node.boundsInRoot.left >= screen.left - 0.5f && node.boundsInRoot.right <= screen.right + 0.5f)
            assertTrue("$tag is named", AccessibilityChecks.announcedLabel(node).isNotBlank())
        }
        if (fontScale == 1f) {
            // Where they fit, one line: back at the start, edit and the ⋮ at the end.
            val (back, edit, menu) = listOf(CardTags.BACK, CardTags.EDIT, CardTags.MENU)
                .map { compose.onNodeWithTag(it).fetchSemanticsNode().boundsInRoot }
            val bar = compose.onNodeWithTag(CardTags.BAR).fetchSemanticsNode().boundsInRoot
            assertEquals(back.top, edit.top, 0.5f)
            assertEquals(back.top, menu.top, 0.5f)
            assertTrue(back.right <= edit.left && edit.right <= menu.left && bar.right - menu.right < 9f * bar.width / 360f + 1f)
        }
        val tabs = CardMode.entries.map(CardTags::tab)
        listOf(
            CardTags.BACK, CardTags.EDIT, CardTags.MENU, CardTags.PROVENANCE, CardTags.SERVINGS_FEWER, CardTags.SERVINGS_MORE,
        ).plus(tabs).forEach { compose.assertCardTarget(it) }
        // View: back, edit, the ⋮, the two tabs, the provenance, fewer, more, fork.
        AccessibilityChecks.assertTouchTargets(compose, min = CardTarget, expectAtLeast = 9)
        // The tabs' names whole: each laid out on its lines in full, nothing cut, the tab within the screen. (A tab
        // centres its name, so the name's lines are measured against the paragraph, not the text's own width.)
        val screen = compose.onNodeWithTag(CardTags.SCREEN).fetchSemanticsNode().boundsInRoot
        tabs.forEach { tab ->
            val bounds = compose.onNodeWithTag(tab).fetchSemanticsNode().boundsInRoot
            assertTrue("$tab lies within the screen", bounds.left >= screen.left - 0.5f && bounds.right <= screen.right + 0.5f)
            val layouts = compose.textLayoutsWithin(tab)
            assertTrue("$tab shows its name", layouts.isNotEmpty())
            layouts.forEach { layout ->
                val name = layout.layoutInput.text.text
                assertTrue("\"$name\" in $tab is whole at $fontScale",
                    !layout.didOverflowHeight && !layout.multiParagraph.didExceedMaxLines && layout.getLineEnd(layout.lineCount - 1) == name.length &&
                        layout.size.width <= bounds.width + 0.5f)
            }
        }
        // Fork, floating at the bottom end: a card target, its word whole, all of it on screen; at large text its icon
        // alone (v0.38), its word still read to the screen reader.
        compose.assertCardTarget(CardTags.FORK)
        if (fontScale < LARGE_TEXT_SCALE) {
            compose.assertWhole(CardTags.FORK)
        } else {
            val fork = compose.onNodeWithTag(CardTags.FORK).fetchSemanticsNode().boundsInRoot
            assertTrue("fork is its icon alone at $fontScale: $fork", kotlin.math.abs(fork.width - fork.height) < 1f)
        }
        assertTrue("fork is named", AccessibilityChecks.announcedLabel(compose.onNodeWithTag(CardTags.FORK).fetchSemanticsNode()).isNotBlank())

        // Cooking: back, the two tabs, fewer, more, the ingredient line, the timer.
        compose.chooseCardTab(CardMode.COOKING)
        listOf(CardTags.BACK, CardTags.SERVINGS_FEWER, CardTags.SERVINGS_MORE, CardTags.ingredient(0), TimerTags.start(0))
            .plus(tabs).forEach { compose.assertCardTarget(it) }
        AccessibilityChecks.assertTouchTargets(compose, min = CardTarget, expectAtLeast = 7)

        // The fixing control, opened from its amount, at first and once a fixing is held.
        compose.onNodeWithTag(CardTags.fixAmount(0), useUnmergedTree = true).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(hasTestTag(CardTags.FIX)).fetchSemanticsNodes().isNotEmpty() }
        listOf(CardTags.FIX_FIELD, FixTags.UNIT, CardTags.FIX_CANCEL, CardTags.FIX_CONFIRM).forEach { compose.assertCardTarget(it) }
        AccessibilityChecks.assertTouchTargets(compose, min = CardTarget, expectAtLeast = 11)
        // The unit drop-down open: each unit it offers is a card target too.
        compose.onNodeWithTag(FixTags.UNIT).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(hasTestTag(CardTags.fixUnit(QuantityUnit.KILOGRAM))).fetchSemanticsNodes().isNotEmpty() }
        listOf(QuantityUnit.GRAM, QuantityUnit.KILOGRAM, QuantityUnit.TONNE).forEach { compose.assertCardTarget(CardTags.fixUnit(it)) }
        AccessibilityChecks.assertTouchTargets(compose, min = CardTarget, expectAtLeast = 14)
        compose.onNodeWithTag(CardTags.fixUnit(QuantityUnit.GRAM)).performClick()
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(hasTestTag(CardTags.fixUnit(QuantityUnit.GRAM))).fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag(CardTags.FIX_FIELD).performTextReplacement("250")
        compose.onNodeWithTag(CardTags.FIX_CONFIRM).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(hasTestTag(CardTags.FIX)).fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag(CardTags.fixAmount(0), useUnmergedTree = true).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(hasTestTag(CardTags.FIX_RELEASE)).fetchSemanticsNodes().isNotEmpty() }
        compose.assertCardTarget(CardTags.FIX_RELEASE)
        AccessibilityChecks.assertTouchTargets(compose, min = CardTarget, expectAtLeast = 12)
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
