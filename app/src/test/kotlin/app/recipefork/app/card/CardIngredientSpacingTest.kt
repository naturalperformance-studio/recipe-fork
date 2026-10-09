package app.recipefork.app.card

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.IngredientGroup
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.Timer
import app.recipefork.core.recipe.UnitType
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
 * A11Y-007 with more of the list in sight: each ingredient line on the card is the card's 56 dp target and no more —
 * one line of text exactly that, with no gap to the next line; a line that wraps grows by no more than its second
 * line; a group's subheading has a modest gap above it and none below; the amount and the name stay centred in the
 * line. So a cooking screen of 360 × 640 dp, with the servings changed and the running-timers bar up, shows more
 * lines under the "Ingredients" header — and every line still marks itself done with a tap, its amount still opens
 * fixing as a target of its own.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CardIngredientSpacingTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val timers = TestTimers(context)
    private val recipes = RecipeRepository(ActiveBook(BookName.Pool, book.database), context.filesDir)
    private val now = Instant.parse("2026-10-09T12:00:00Z")
    private var shown by mutableStateOf<RecipeId?>(null)

    @Before
    fun setUp() {
        listOf(book.idling, timers.idling).forEach(compose::registerIdlingResource)
        compose.setContent {
            CompositionLocalProvider(LocalCardTimers provides timers.card) {
                MaterialTheme {
                    shown?.let { CardScreen(it, recipes, CardEnvironment { emptyList() }, onBack = {}, onEdit = {}) }
                }
            }
        }
    }

    @After
    fun close() {
        listOf(book.idling, timers.idling).forEach(compose::unregisterIdlingResource)
        book.close()
        timers.close()
    }

    private fun grams(position: Int, name: String, amount: Long) =
        Ingredient(position, null, freeTextName = name, unitType = UnitType.EXACT, unit = QuantityUnit.GRAM, quantity = Rational.of(amount))

    private fun show(recipe: Recipe) {
        runBlocking { recipes.save(recipe) }
        compose.runOnIdle { shown = recipe.id }
        compose.awaitCard()
        val last = recipe.ingredients.maxOf { it.position }
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(hasTestTag(CardTags.ingredient(last))).fetchSemanticsNodes().isNotEmpty() }
    }

    /** Where the node tagged [tag] is laid out, in px, scrolled out of view or not. */
    private fun bounds(tag: String): Rect = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().let { node ->
        Rect(node.positionInRoot, Size(node.size.width.toFloat(), node.size.height.toFloat()))
    }

    private fun dp(px: Float) = px / compose.density.density

    private fun lineCount(tag: String) = compose.textLayoutsWithin(tag).single().lineCount

    /** The height of one line of the list's text (bodyLarge), in dp, as the name of the line at [position] lays it out. */
    private fun textLine(position: Int) = compose.textLayoutsWithin(IngredientTags.name(position)).single().let { dp(it.getLineBottom(0) - it.getLineTop(0)) }

    @Test
    fun linesAre56dpWithNoGapAndSubheadingsHaveAModestGapAbove() {
        val long = "Coarsely ground black pepper from the mill, fresh"
        show(
            Recipe.create(now, null).copy(
                title = "Buns",
                servings = 2,
                ingredients = listOf(
                    grams(0, "Salt", 4), grams(1, "Sugar", 20), grams(2, "Flour", 500), grams(3, "Water", 300),
                    grams(4, long, 2), grams(5, "Butter", 40),
                ),
                ingredientGroups = listOf(IngredientGroup("Dough", 2)),
                steps = listOf(Step(0, "Mix")),
            ),
        )
        // One line of text: exactly the card's target, and each line straight under the one before.
        listOf(0, 1, 2, 3, 5).forEach { position ->
            assertEquals("line $position is one line of text", 1, lineCount(IngredientTags.name(position)))
            assertEquals("line $position is 56 dp tall", CardTarget.value, dp(bounds(CardTags.ingredient(position)).height), 0.5f)
        }
        assertEquals("no gap between lines", bounds(CardTags.ingredient(0)).bottom, bounds(CardTags.ingredient(1)).top, 0.5f)
        assertEquals("no gap between lines", bounds(CardTags.ingredient(2)).bottom, bounds(CardTags.ingredient(3)).top, 0.5f)
        assertEquals("no gap between lines", bounds(CardTags.ingredient(3)).bottom, bounds(CardTags.ingredient(4)).top, 0.5f)
        assertEquals("no gap between lines", bounds(CardTags.ingredient(4)).bottom, bounds(CardTags.ingredient(5)).top, 0.5f)

        // A line that wraps onto a second line is its two lines of text and 8 dp above and below them: 64 dp.
        assertEquals("the long name wraps onto two lines", 2, lineCount(IngredientTags.name(4)))
        val wrapped = dp(bounds(CardTags.ingredient(4)).height)
        assertEquals("a two-line line is its two text lines (${textLine(4)} dp each) and 2 × 8 dp", 2 * textLine(4) + 16f, wrapped, 0.5f)
        assertEquals("a two-line line is 64 dp", 64f, wrapped, 0.5f)

        // The subheading: ≈12 dp above it, after the line before; its first line straight under it.
        val heading = bounds(IngredientTags.group(0))
        assertEquals("the gap above the subheading", 12f, dp(heading.top - bounds(CardTags.ingredient(1)).bottom), 1f)
        assertEquals("no gap below the subheading", heading.bottom, bounds(CardTags.ingredient(2)).top, 0.5f)

        // The amount and the name, centred in their line.
        listOf(0, 2, 5).forEach { position ->
            val line = bounds(CardTags.ingredient(position)).center.y
            assertEquals("the name of line $position is centred", line, bounds(IngredientTags.name(position)).center.y, 1f)
            assertEquals("the amount of line $position is centred", line, bounds(IngredientTags.quantity(position)).center.y, 1f)
        }

        // Still a target that marks the line done, the amount still a target of its own that opens fixing.
        (0..5).forEach { compose.assertCardTarget(CardTags.ingredient(it)) }
        (0..5).forEach { compose.assertCardTarget(CardTags.fixAmount(it)) }
        AccessibilityChecks.assertTouchTargets(compose, min = CardTarget, expectAtLeast = 6)
        compose.onNodeWithTag(IngredientTags.name(3), useUnmergedTree = true).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) {
            compose.onNodeWithTag(CardTags.ingredient(3)).fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription) ==
                context.getString(R.string.card_marked_done)
        }
        assertEquals("marking keeps the line's height", CardTarget.value, dp(bounds(CardTags.ingredient(3)).height), 0.5f)
        compose.onNodeWithTag(CardTags.fixAmount(2), useUnmergedTree = true).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(hasTestTag(CardTags.FIX)).fetchSemanticsNodes().isNotEmpty() }
        assertTrue("the amount opened fixing, not the marking",
            compose.onNodeWithTag(CardTags.ingredient(2)).fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription) == null)
    }

    // From T-188's review: a group that starts at the first line has the same ≈12 dp above its subheading as one further
    // down — from the status line, as from the line before — and no gap below it.
    @Test
    fun aGroupAtTheFirstLineHasTheSameGapAboveItsSubheading() {
        show(
            Recipe.create(now, null).copy(
                title = "Buns",
                servings = 2,
                ingredients = listOf(grams(0, "Flour", 500), grams(1, "Water", 300), grams(2, "Salt", 4), grams(3, "Sugar", 20)),
                ingredientGroups = listOf(IngredientGroup("Dough", 0), IngredientGroup("Glaze", 2)),
                steps = listOf(Step(0, "Mix")),
            ),
        )
        val first = bounds(IngredientTags.group(0))
        val second = bounds(IngredientTags.group(1))
        assertEquals("the gap above a subheading on the first line", 12f, dp(first.top - bounds(IngredientTags.STATUS).bottom), 1f)
        assertEquals("the gap above a subheading further down", 12f, dp(second.top - bounds(CardTags.ingredient(1)).bottom), 1f)
        assertEquals("the same gap above both", dp(first.top - bounds(IngredientTags.STATUS).bottom), dp(second.top - bounds(CardTags.ingredient(1)).bottom), 0.5f)
        assertEquals("no gap below the first subheading", first.bottom, bounds(CardTags.ingredient(0)).top, 0.5f)
    }

    // The tighter top keeps the card's parts apart: the parts 8 dp from each other, the description 16 dp from the steps.
    @Test
    fun theStepsAndTheDescriptionStillReadApart() {
        show(
            Recipe.create(now, null).copy(
                title = "Buns",
                description = "Best on the day they are baked.",
                servings = 2,
                ingredients = listOf(grams(0, "Flour", 500)),
                steps = listOf(Step(0, "Mix"), Step(1, "Bake")),
            ),
        )
        compose.onNodeWithTag(CardTags.DESCRIPTION).performScrollTo()
        val steps = bounds(CardTags.STEPS)
        val description = bounds(CardTags.DESCRIPTION)
        assertEquals("the description 16 dp under the steps", 16f, dp(description.top - steps.bottom), 0.5f)
        assertEquals("the steps 8 dp under the ingredients", 8f, dp(steps.top - bounds(IngredientTags.SURFACE).bottom), 0.5f)
        assertEquals("the servings 8 dp under the title", 8f, dp(bounds(CardTags.SERVINGS_FEWER).top - bounds(CardTags.TITLE).bottom), 0.5f)
    }

    @Test
    @Config(qualifiers = "en-w360dp-h640dp")
    fun aCookingScreenAsItOpensShowsMoreLinesInEnglish() = linesOnACookingScreen()

    @Test
    @Config(qualifiers = "ru-w360dp-h640dp")
    fun aCookingScreenAsItOpensShowsMoreLinesInRussian() = linesOnACookingScreen()

    /**
     * The card as it opens on a 360 × 640 dp screen, the servings changed and a timer running out of sight, its bar
     * up: at least [LINES_IN_SIGHT] whole lines lie between the "Ingredients" header and the bar.
     */
    private fun linesOnACookingScreen() {
        val recipe = Recipe.create(now, null).copy(
            title = "Rye bread",
            servings = 2,
            ingredients = listOf(
                grams(0, "Flour", 500), grams(1, "Water", 300), grams(2, "Salt", 10), grams(3, "Sugar", 20),
                grams(4, "Yeast", 7), grams(5, "Butter", 40), grams(6, "Milk", 100), grams(7, "Seeds", 30),
                grams(8, "Honey", 15), grams(9, "Malt", 10), grams(10, "Caraway", 5), grams(11, "Oil", 12),
            ),
            steps = listOf(Step(0, "Knead the dough", timer = Timer(10.minutes))),
        )
        // The step's timer runs; its step is far below, out of sight.
        runBlocking { timers.store.start(TimerKey(BookName.Pool, recipe.id, 0), "Knead the dough", recipe.title, 10.minutes) }
        show(recipe)
        compose.onNodeWithTag(CardTags.SERVINGS_MORE).performClick()
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(hasTestTag(IngredientTags.RECALCULATED), true).fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(hasTestTag(TimerTags.BAR)).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        val header = hasText(context.getString(R.string.card_ingredients)) and hasAnyAncestor(hasTestTag(CardTags.INGREDIENTS))
        val headerBottom = compose.onNode(header).fetchSemanticsNode().boundsInRoot.bottom
        val barTop = compose.onNodeWithTag(TimerTags.BAR).fetchSemanticsNode().boundsInRoot.top
        val inSight = (0..11).count { position ->
            bounds(CardTags.ingredient(position)).let { it.top >= headerBottom - 0.5f && it.bottom <= barTop + 0.5f }
        }
        assertTrue("$inSight lines in sight between the header and the bar", inSight >= LINES_IN_SIGHT)
    }

    private companion object {
        /** The whole lines a 360 × 640 dp card shows under its header as it opens, with the servings and the bar up. */
        const val LINES_IN_SIGHT = 6
    }
}
