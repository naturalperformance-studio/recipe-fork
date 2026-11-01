package app.recipefork.app.card

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.data.HeldLastCardTab
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.ui.AccessibilityChecks
import app.recipefork.core.recipe.EquipmentItem
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.Rational
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import kotlin.math.abs

/**
 * v0.45, T-323 (decisions 2026-10-15 item 9; redesign-1 § 4.4 frames 1–2): on Cooking every ingredient line, item of
 * equipment and step starts with a round done mark — an empty circle until marked, then a filled circle with a tick,
 * its text struck through and muted: three signs, never colour alone. The whole row stays the target, at least the
 * card's 56 dp; a marked step folds to one struck line with its tick and unfolds when tapped; the screen reader reads
 * the state in words; a tap on a quantity still opens the fixing under its line.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w360dp-h640dp")
class CardDoneMarkCircleTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val recipes = RecipeRepository(ActiveBook(BookName.Pool, book.database))
    private val scheme = lightColorScheme()
    private val now = Instant.parse("2026-11-01T12:00:00Z")

    private val long = "stir the pot slowly with a wooden spoon, scraping the bottom so that nothing catches or burns."

    /** Two items, two lines and three steps: marking one of each leaves its list unfolded. */
    private val pilaf = Recipe.create(now, null).copy(
        title = "Pilaf",
        servings = 4,
        equipment = listOf("Cauldron", "Skimmer").map(EquipmentItem::free),
        ingredients = listOf(
            Ingredient(0, null, freeTextName = "Chicken", unitType = UnitType.EXACT, unit = QuantityUnit.GRAM, quantity = Rational.of(500)),
            Ingredient(1, null, freeTextName = "Rice", unitType = UnitType.EXACT, unit = QuantityUnit.GRAM, quantity = Rational.of(350)),
        ),
        steps = listOf(Step(0, "Brown the chicken: $long"), Step(1, "Add the rice: $long"), Step(2, "Serve.")),
    )

    /** A part of the card that carries a mark: its row's tag and its mark's. */
    private data class Part(val row: String, val mark: String)

    private val item = Part(CardTags.equipment(0), EquipmentTags.mark(0))
    private val line = Part(CardTags.ingredient(0), IngredientTags.mark(0))
    // Not the current step, which stands on its own container.
    private val step = Part(CardTags.step(1), StepRowTags.number(1))
    private val parts = listOf(item, line, step)

    @Before
    fun setUp() {
        compose.registerIdlingResource(book.idling)
        runBlocking { recipes.save(pilaf) }
        val environment = CardEnvironment(
            marks = DoneMarks.inMemory(), lastTab = HeldLastCardTab(CardMode.COOKING), ingredients = IngredientEntries { emptyList() },
        )
        compose.setContent {
            MaterialTheme(colorScheme = scheme) { CardScreen(pilaf.id, recipes, environment, onBack = {}, onEdit = {}) }
        }
        compose.awaitCard()
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(hasTestTag(CardTags.step(2))).fetchSemanticsNodes().size == 1 }
        compose.waitForIdle()
    }

    @After
    fun close() {
        compose.unregisterIdlingResource(book.idling)
        book.close()
    }

    private fun tap(tag: String) {
        compose.onNodeWithTag(tag).performScrollTo().performClick()
        compose.waitForIdle()
    }

    private fun unmerged(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()

    private fun showsCheck(part: Part) = unmerged(part.mark).config[ShowsCheck]

    private fun state(part: Part) = compose.onNodeWithTag(part.row).fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription)

    /** The texts within [part]'s row drawn struck through, and whether each is in the muted colour. */
    private fun struck(part: Part): List<Pair<String, Boolean>> = compose.textLayoutsWithin(part.row)
        .filter { it.layoutInput.style.textDecoration == TextDecoration.LineThrough }
        .map { it.layoutInput.text.text to (it.layoutInput.style.color == scheme.onSurfaceVariant) }

    private fun same(a: Color, b: Color) = abs(a.red - b.red) < 3f / 255 && abs(a.green - b.green) < 3f / 255 && abs(a.blue - b.blue) < 3f / 255

    private fun px(dp: Dp) = with(compose.density) { dp.toPx() }

    /** The colour drawn in [part]'s mark at [x], [y] from its top-left corner. */
    private fun colourIn(part: Part, x: Dp, y: Dp): Color {
        compose.onNodeWithTag(part.row).performScrollTo()
        compose.waitForIdle()
        val pixels = compose.onNodeWithTag(part.mark, useUnmergedTree = true).captureToImage().toPixelMap()
        return pixels[px(x).toInt().coerceAtMost(pixels.width - 1), px(y).toInt().coerceAtMost(pixels.height - 1)]
    }

    /** Inside the circle, clear of the tick: the primary fill once ticked, what lies under it while empty. */
    private fun fill(part: Part) = colourIn(part, DONE_MARK_SIZE / 2, DONE_MARK_SIZE / 5)

    /** On the circle's ring, at its top. */
    private fun ring(part: Part) = colourIn(part, DONE_MARK_SIZE / 2, DONE_MARK_RING / 2)

    /** On the tick's corner: the redesign's check (5, 12.5)–(9.5, 17)–(19, 7.5) of 24, 18 dp wide in the circle's middle. */
    private fun tick(part: Part) = colourIn(part, 5.dp + 18.dp * (9.5f / 24), 5.dp + 18.dp * (17f / 24))

    @Test
    fun anUnmarkedRowShowsAnEmptyCircleAndAMarkedOneATickItsTextStruckAndMuted() {
        parts.forEach { part ->
            assertFalse("${part.row}: no tick", showsCheck(part))
            assertTrue("${part.row}: the empty circle's ring is muted ink", same(scheme.onSurfaceVariant, ring(part)))
            assertFalse("${part.row}: the empty circle is not filled", same(scheme.primary, fill(part)))
            assertEquals("${part.row}: nothing struck", emptyList<Pair<String, Boolean>>(), struck(part))
        }
        parts.forEach { tap(it.row) }
        parts.forEach { part ->
            assertTrue("${part.row}: ticked", showsCheck(part))
            assertTrue("${part.row}: the circle is filled with the primary colour", same(scheme.primary, fill(part)))
            assertTrue("${part.row}: the tick is drawn on it", same(scheme.onPrimary, tick(part)))
            val struck = struck(part)
            assertTrue("${part.row}: its text is struck through", struck.isNotEmpty())
            assertTrue("${part.row}: and muted: $struck", struck.all { it.second })
        }
        assertEquals(listOf("Chicken", "500 g"), struck(line).map { it.first })
        assertEquals(listOf("Cauldron"), struck(item).map { it.first })
        // Unmarked again, each is as it was.
        parts.forEach { tap(it.row) }
        parts.forEach { part ->
            assertFalse("${part.row}: no tick again", showsCheck(part))
            assertFalse("${part.row}: not filled again", same(scheme.primary, fill(part)))
            assertEquals("${part.row}: nothing struck again", emptyList<Pair<String, Boolean>>(), struck(part))
        }
    }

    @Test
    fun theCurrentStepsEmptyCircleIsInItsContainersInk() {
        val current = Part(CardTags.step(0), StepRowTags.number(0))
        assertEquals("Current step, 1 of 3", state(current))
        assertFalse(showsCheck(current))
        assertTrue("the ring on the current step", same(scheme.onPrimaryContainer, ring(current)))
    }

    @Test
    fun theWholeRowIsTheTargetAtLeastTheCardsAndItsCircleMarksItToo() {
        parts.forEach { part ->
            compose.onNodeWithTag(part.row).performScrollTo()
            compose.waitForIdle()
            compose.assertCardTarget(part.row)
            val row = compose.onNodeWithTag(part.row).fetchSemanticsNode()
            assertEquals("${part.row} is a check box", ToggleableState.Off, row.config[SemanticsProperties.ToggleableState])
            val mark = unmerged(part.mark).boundsInRoot
            assertTrue("${part.row}: its mark lies within the row", row.boundsInRoot.contains(mark.center))
            assertTrue("${part.row}: the mark stands at the row's start", mark.left - row.boundsInRoot.left < px(1.dp))
            // A tap on the mark marks the row, one on the row's far end unmarks it.
            val at = mark.center - row.boundsInRoot.topLeft
            compose.onNodeWithTag(part.row).performTouchInput { click(at) }
            compose.waitForIdle()
            assertTrue("${part.row}: marked from its circle", showsCheck(part))
            compose.onNodeWithTag(part.row).performTouchInput { click(Offset(width - 2f, height / 2f)) }
            compose.waitForIdle()
            assertFalse("${part.row}: unmarked from its far end", showsCheck(part))
        }
        AccessibilityChecks.assertTouchTargets(compose, min = CardTarget, expectAtLeast = 7)
    }

    private fun stepLayout(position: Int): TextLayoutResult = compose.textLayoutsWithin(StepTags.text(position)).single()

    @Test
    fun aMarkedStepFoldsToOneStruckLineWithItsTickAndATapUnfoldsIt() {
        val first = Part(CardTags.step(0), StepRowTags.number(0))
        val whole = stepLayout(0)
        assertTrue("the step runs over several lines", whole.lineCount > 1)
        // The current step: its caption numbers it, its text does not (T-324).
        assertTrue("its text starts with its words", whole.layoutInput.text.text.startsWith("Brown the chicken"))

        tap(first.row)
        val folded = stepLayout(0)
        assertEquals("folded to one line", 1, folded.lineCount)
        assertTrue("its first line, ellipsised", folded.isLineEllipsized(0))
        assertTrue("still numbered", folded.layoutInput.text.text.startsWith("1. "))
        assertEquals(TextDecoration.LineThrough, folded.layoutInput.style.textDecoration)
        assertEquals(scheme.onSurfaceVariant, folded.layoutInput.style.color)
        assertTrue("with its tick", showsCheck(first))
        compose.assertCardTarget(first.row)

        tap(first.row)
        val unfolded = stepLayout(0)
        assertEquals("unfolded", whole.lineCount, unfolded.lineCount)
        assertTrue("no longer struck", unfolded.layoutInput.style.textDecoration != TextDecoration.LineThrough)
        assertFalse("its circle empty again", showsCheck(first))
    }

    @Test
    fun theScreenReaderReadsTheMarkInWordsAndTheCircleSaysNothing() {
        parts.forEach { part ->
            assertEquals("${part.row}", "Not done", state(part))
            assertEquals("Mark as done", AccessibilityChecks.clickActionLabel(compose.onNodeWithTag(part.row).fetchSemanticsNode()))
            val mark = unmerged(part.mark).config
            assertTrue("${part.mark} has no words", mark.getOrNull(SemanticsProperties.Text).isNullOrEmpty())
            assertTrue("${part.mark} has no description", mark.getOrNull(SemanticsProperties.ContentDescription).isNullOrEmpty())
        }
        parts.forEach { tap(it.row) }
        parts.forEach { part ->
            assertEquals("${part.row}", "Done", state(part))
            assertEquals("Mark as not done", AccessibilityChecks.clickActionLabel(compose.onNodeWithTag(part.row).fetchSemanticsNode()))
        }
        // The step's number is said once, by its line, though its text now draws it too.
        assertEquals(listOf("2.", pilaf.steps[1].text), compose.onNodeWithTag(step.row).fetchSemanticsNode().config[SemanticsProperties.Text].map { it.text })
    }

    @Test
    fun aTapOnTheQuantityStillOpensTheFixingUnderTheLineAndMarksNothing() {
        compose.onNodeWithTag(CardTags.fixAmount(0), useUnmergedTree = true).performScrollTo().performClick()
        compose.waitForIdle()
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(hasTestTag(CardTags.FIX)).fetchSemanticsNodes().isNotEmpty() }
        val control = compose.onNodeWithTag(CardTags.FIX).fetchSemanticsNode().boundsInRoot
        val row = compose.onNodeWithTag(line.row).fetchSemanticsNode().boundsInRoot
        assertTrue("the fixing opens under the line: $control under $row", control.top >= row.bottom - 0.5f)
        assertFalse("the line is not marked", showsCheck(line))
        assertEquals("Not done", state(line))
    }
}
