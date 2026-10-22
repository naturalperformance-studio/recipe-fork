package app.recipefork.app.card

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.geometry.Offset
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.data.HeldLastCardTab
import app.recipefork.app.dictionary.IngredientEntryEntity
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.api.NutritionDto
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.Step
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

/**
 * v0.40, T-293, the card's nutrition as text grows (at 200 % on 320 dp unless a test says otherwise) (decisions-recipe-fork.md, 2026-10-22, decisions 2, 3 and 5), with
 * real text measured: the switch keeps 8 dp from the heading and from the tiles at any size, and its two or three
 * segments (v0.44, T-320) stay equal, a label that does not fit breaking rather than being cut; the tiles stand 2 × 2 at
 * 200 % on 320 dp; a row of the sheet never breaks its name inside a word to make room for its value, which stands
 * under the name where the two do not fit side by side.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w320dp-h640dp")
class CardNutritionAtLargeTextTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val recipes = RecipeRepository(ActiveBook(BookName.Pool, book.database))
    private val now = Instant.parse("2026-10-22T12:00:00Z")

    private val entries = listOf(
        IngredientEntryEntity(1, "творог", "cottage cheese", null, nutrition = NutritionDto(159.0, 16.7, 9.0, 2.0)),
        IngredientEntryEntity(7, "вустерширский", "Worcestershire", null, nutrition = NutritionDto(78.0, 0.0, 0.0, 19.5)),
    )

    private fun grams(position: Int, entry: Long?, amount: Long?, free: String? = null) =
        Ingredient(position, entry, freeTextName = free, unitType = UnitType.EXACT, unit = QuantityUnit.GRAM, quantity = amount?.let { Rational.of(it) })

    /** A counted row of one long word, and rows left out named in several words and in one long one. */
    private fun withNames(sauce: String, spice: String, weight: Int? = null) = Recipe.create(now, null).copy(
        title = "Syrniki",
        servings = 4,
        finishedWeightGrams = weight,
        ingredients = listOf(grams(0, 1, 360), grams(1, 7, 10), grams(2, null, null, free = sauce), grams(3, null, null, free = spice)),
        steps = listOf(Step(0, "Mix and fry.")),
    )

    @Before
    fun setUp() = compose.registerIdlingResource(book.idling)

    @After
    fun close() {
        compose.unregisterIdlingResource(book.idling)
        book.close()
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun open(recipe: Recipe = withNames("Sour cream", "Spice")) {
        runBlocking { recipes.save(recipe) }
        val environment = CardEnvironment(lastTab = HeldLastCardTab(CardMode.VIEW), ingredients = IngredientEntries { entries })
        compose.setContent {
            MaterialTheme {
                CardScreen(recipe.id, recipes, environment, onBack = {}, onEdit = {}, session = TextCardSession.inMemory())
            }
        }
        compose.awaitCard()
        compose.waitUntil(PATIENCE_MS) { exists(CardTags.step(0)) }
        compose.waitForIdle()
    }

    private fun openDetails() {
        compose.scrollClearOfFork(NutritionTags.DETAILS)
        compose.onNodeWithTag(NutritionTags.DETAILS).performClick()
        compose.waitUntil(PATIENCE_MS) { exists(NutritionTags.HOW) }
        compose.waitForIdle()
    }

    private fun dp(node: SemanticsNode, px: Float) = px / node.layoutInfo.density.density

    /** The gaps above and below the switch's drawing, in dp: from the heading, and to the tiles. */
    private fun switchGaps(title: String = "Nutrition, approximate"): Pair<Float, Float> {
        val heading = compose.onNode(hasText(title) and hasAnyAncestor(hasTestTag(NutritionTags.SECTION)), useUnmergedTree = true)
            .fetchSemanticsNode()
        val segment = compose.onNodeWithTag(NutritionTags.SWITCH_PER_SERVING).fetchSemanticsNode()
        val tiles = compose.onNodeWithTag(NutritionTags.TILES).fetchSemanticsNode()
        val above = segment.positionInRoot.y - (heading.positionInRoot.y + heading.size.height)
        val below = tiles.positionInRoot.y - (segment.positionInRoot.y + segment.size.height)
        return dp(segment, above) to dp(segment, below)
    }

    /** The name and the value of the sheet's row tagged [tag], as laid out. */
    private fun partsOf(tag: String): Pair<SemanticsNode, SemanticsNode> {
        val texts = compose.onAllNodes(hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true).fetchSemanticsNodes()
        assertEquals(2, texts.size)
        return texts.sortedWith(compareBy({ it.positionInRoot.y }, { it.positionInRoot.x })).let { it[0] to it[1] }
    }

    @Test
    @Config(qualifiers = "en-w360dp-h640dp")
    fun atTheUsualSizeTheSwitchIsDrawn40DpTallEightDpFromTheHeadingAndTheTiles() {
        open()
        val segment = compose.onNodeWithTag(NutritionTags.SWITCH_PER_SERVING).fetchSemanticsNode()
        assertEquals(40f, dp(segment, segment.size.height.toFloat()), 0.5f)
        val (above, below) = switchGaps()
        assertEquals(8f, above, 0.5f)
        assertEquals(8f, below, 0.5f)
        // The card's 56 dp target reaches into the gaps: a touch 7 dp above the drawing chooses the segment.
        compose.scrollClearOfFork(NutritionTags.SWITCH)
        val whole = compose.onNodeWithTag(NutritionTags.SWITCH_WHOLE)
        assertEquals(CardTarget.value, dp(segment, whole.fetchSemanticsNode().touchBoundsInRoot.height), 0.5f)
        val sevenDp = 7 * segment.layoutInfo.density.density
        whole.performTouchInput { click(Offset(centerX, -sevenDp)) }
        compose.waitForIdle()
        whole.assertIsSelected()
    }

    @Test
    @Config(fontScale = 2f)
    fun atLargeTextTheSwitchGrowsAndKeepsItsEightDpAboveAndBelow() {
        open()
        val segment = compose.onNodeWithTag(NutritionTags.SWITCH_PER_SERVING).fetchSemanticsNode()
        assertTrue("the segment is ${dp(segment, segment.size.height.toFloat())} dp", dp(segment, segment.size.height.toFloat()) > 40f + 0.5f)
        val (above, below) = switchGaps()
        assertEquals(8f, above, 0.5f)
        assertEquals(8f, below, 0.5f)
        assertNotCut(NutritionTags.SWITCH)
    }

    /**
     * Every label of the switch shows all of itself, on as many lines as it takes: one that does not fit whole breaks
     * inside its word, as its text breaks (decisions-recipe-fork.md, T-319 / T-320, decision 2, F-2); none is cut.
     */
    private fun assertNotCut(tag: String) {
        val layouts = compose.textLayoutsWithin(tag)
        assertTrue("$tag shows text", layouts.isNotEmpty())
        layouts.forEach { layout ->
            val text = layout.layoutInput.text.text
            assertTrue("\"$text\" in $tag is not cut off",
                !layout.didOverflowHeight && !layout.multiParagraph.didExceedMaxLines &&
                    layout.getLineEnd(layout.lineCount - 1) == text.length &&
                    (0 until layout.lineCount).none(layout::isLineEllipsized))
        }
    }

    /** The switch's three segments are equally wide, as decided, and as tall as one another, whichever label wraps most. */
    private fun assertThreeEqualSegments() {
        val sizes = listOf(NutritionTags.SWITCH_PER_100_G, NutritionTags.SWITCH_PER_SERVING, NutritionTags.SWITCH_WHOLE)
            .map { compose.onNodeWithTag(it).fetchSemanticsNode().size }
        assertTrue("equal segments: $sizes", sizes.maxOf { it.width } - sizes.minOf { it.width } <= 1)
        assertTrue("segments as tall: $sizes", sizes.all { it.height == sizes[0].height })
    }

    @Test
    @Config(qualifiers = "ru-w320dp-h640dp")
    fun onANarrowScreenTheThreeSegmentsStayEqualAndTheirLabelsBreakRatherThanBeCut() {
        open(withNames("Sour cream", "Spice", weight = 560))
        compose.onNodeWithTag(NutritionTags.SWITCH_PER_SERVING).assertIsSelected()
        assertThreeEqualSegments()
        assertNotCut(NutritionTags.SWITCH)
        val (above, below) = switchGaps("Пищевая ценность, примерно")
        assertEquals(8f, above, 0.5f)
        assertEquals(8f, below, 0.5f)
    }

    @Test
    @Config(qualifiers = "ru-w320dp-h640dp", fontScale = 2f)
    fun atLargeTextTheThreeSegmentsGrowStayEqualAndTheirLabelsBreakRatherThanBeCut() {
        open(withNames("Sour cream", "Spice", weight = 560))
        val segment = compose.onNodeWithTag(NutritionTags.SWITCH_PER_SERVING).fetchSemanticsNode()
        assertTrue("the segment is ${dp(segment, segment.size.height.toFloat())} dp", dp(segment, segment.size.height.toFloat()) > 40f + 0.5f)
        assertThreeEqualSegments()
        assertNotCut(NutritionTags.SWITCH)
        val (above, below) = switchGaps("Пищевая ценность, примерно")
        assertEquals(8f, above, 0.5f)
        assertEquals(8f, below, 0.5f)
        compose.scrollClearOfFork(NutritionTags.SWITCH)
        compose.onNodeWithTag(NutritionTags.SWITCH_PER_100_G).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(NutritionTags.SWITCH_PER_100_G).assertIsSelected()
        assertNotCut(NutritionTags.SWITCH)
    }

    @Test
    @Config(fontScale = 2f)
    fun theTilesStandTwoByTwo() {
        open()
        val bounds = (0..3).map { compose.onNodeWithTag(NutritionTags.tile(it), useUnmergedTree = true).fetchSemanticsNode() }
            .map { it.positionInRoot.y to it.positionInRoot.x }
        assertEquals(bounds[0].first, bounds[1].first)
        assertEquals(bounds[2].first, bounds[3].first)
        assertTrue(bounds[2].first > bounds[0].first)
        assertEquals(bounds[0].second, bounds[2].second)
    }

    private fun assertTheSheetReadsWhole() {
        openDetails()
        listOf(NutritionTags.counted(0), NutritionTags.counted(1), NutritionTags.notCounted(2), NutritionTags.notCounted(3)).forEach { tag ->
            compose.scrollClearOfFork(tag)
            // Each text whole, the name broken only between words.
            compose.assertWhole(tag)
            val (name, value) = partsOf(tag)
            val row = compose.onNodeWithTag(tag).fetchSemanticsNode()
            // The value beside the name, or under it with the row's whole width for the name.
            val beside = value.positionInRoot.y < name.positionInRoot.y + name.size.height - 0.5f
            if (beside) {
                assertTrue("$tag: the name ends before its value", name.positionInRoot.x + name.size.width <= value.positionInRoot.x + 0.5f)
            } else {
                assertTrue("$tag: the value under the name", value.positionInRoot.y >= name.positionInRoot.y + name.size.height - 0.5f)
                assertTrue("$tag: the name has the row's width", name.size.width <= row.size.width)
            }
        }
    }

    @Test
    @Config(fontScale = 2f)
    fun theSheetsRowsNeverBreakANameInsideAWordForTheirValues() {
        open(withNames("Grandma's homemade sauce", "Gingerbreadspice"))
        assertTheSheetReadsWhole()
        // A long word beside "not in the dictionary" does not fit: the reason stands under the name.
        val (name, value) = partsOf(NutritionTags.notCounted(3))
        assertTrue(value.positionInRoot.y >= name.positionInRoot.y + name.size.height - 0.5f)
    }

    @Test
    @Config(qualifiers = "ru-w320dp-h640dp", fontScale = 2f)
    fun theSheetsRowsNeverBreakANameInsideAWordForTheirValuesInRussian() {
        open(withNames("QA соус домашний", "Пряности"))
        assertTheSheetReadsWhole()
        val (name, value) = partsOf(NutritionTags.notCounted(3))
        assertTrue(value.positionInRoot.y >= name.positionInRoot.y + name.size.height - 0.5f)
    }

    @Test
    @Config(qualifiers = "en-w410dp-h800dp", fontScale = 2f)
    fun onAWiderScreenTooTheNamesBreakOnlyBetweenWords() {
        open(withNames("Grandma's homemade sauce", "Gingerbreadspice"))
        assertTheSheetReadsWhole()
    }
}
