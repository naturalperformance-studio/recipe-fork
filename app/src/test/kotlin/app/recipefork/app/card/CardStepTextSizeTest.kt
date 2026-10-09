package app.recipefork.app.card

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.R
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.editor.textsOf
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.IngredientReference
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.Rational
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant

/**
 * A11Y-007, A11Y-004: a step's text on the card is set larger than the ingredient list, the rest of the card and body
 * text anywhere in the product; on a 360 dp-wide phone at font scale 2.0 it still wraps whole, inside the card, and a
 * reference in it keeps its small rounding arrow on screen and its words for the screen reader. On Cooking it is set a
 * size larger again than on View, and still fits whole at twice the font scale on a 320 dp-wide phone.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w360dp-h640dp")
class CardStepTextSizeTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val recipes = RecipeRepository(ActiveBook(BookName.Pool, book.database))
    private val environment = CardEnvironment { emptyList() }
    private val now = Instant.parse("2026-10-03T12:00:00Z")

    private val stepText = "Beat  into the flour one at a time, folding each in gently before the next, until the batter " +
        "is smooth, glossy and falls from the spoon in thick ribbons"

    /** For 4: three eggs, which do not divide, so that at 5 the reference is rounded up and carries its arrow. */
    private val recipe = Recipe.create(now, null).copy(
        title = "Batter",
        servings = 4,
        description = "A batter for pancakes.",
        ingredients = listOf(
            Ingredient(0, null, freeTextName = "Flour", unitType = UnitType.EXACT, unit = QuantityUnit.GRAM, quantity = Rational.of(200)),
            Ingredient(1, null, freeTextName = "Eggs", unitType = UnitType.PIECE, unit = QuantityUnit.PIECE, quantity = Rational.of(3), divisible = false),
        ),
        steps = listOf(Step(0, stepText, listOf(IngredientReference(1, 5)))),
    )

    @Before
    fun setUp() {
        compose.registerIdlingResource(book.idling)
        runBlocking { recipes.save(recipe) }
    }

    @After
    fun close() {
        compose.unregisterIdlingResource(book.idling)
        book.close()
    }

    private var fontScale by mutableFloatStateOf(1f)

    /** The typography of the theme the card is shown in — the app's, `MainActivity`'s `MaterialTheme` — as it read. */
    private var theme: Typography? = null

    private fun open(scale: Float) {
        fontScale = scale
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                MaterialTheme {
                    theme = MaterialTheme.typography
                    CardScreen(recipe.id, recipes, environment, onBack = {}, onEdit = {})
                }
            }
        }
        compose.awaitCard()
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(hasTestTag(CardTags.step(0))).fetchSemanticsNodes().size == 1 }
    }

    private fun layoutOf(node: SemanticsNode): TextLayoutResult? =
        node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.let { action ->
            mutableListOf<TextLayoutResult>().also { action(it) }.firstOrNull()
        }

    private fun stepLayout(): TextLayoutResult =
        layoutOf(compose.onNodeWithTag(StepTags.text(0), useUnmergedTree = true).fetchSemanticsNode())!!

    /** The size of every text laid out within the part tagged [tag]. */
    private fun sizesWithin(tag: String): List<TextUnit> =
        compose.onAllNodes(hasAnyAncestor(hasTestTag(tag)) or hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes()
            .mapNotNull { layoutOf(it)?.layoutInput?.style?.fontSize }

    @Test
    fun aStepReadsLargerThanTheListAndTheRestOfTheCard() {
        open(scale = 1f)
        val step = stepLayout().layoutInput.style.fontSize
        // A size up from the 18 sp it was set at: the theme's title style, not a size of its own.
        assertEquals(theme!!.titleLarge.fontSize, step)
        assertTrue("the step is set at $step", step.value > 18f)
        val others = mapOf(
            "the ingredient list" to sizesWithin(CardTags.INGREDIENTS),
            "the serving count" to sizesWithin(CardTags.SERVINGS),
            "the description" to sizesWithin(CardTags.DESCRIPTION),
        )
        others.forEach { (part, sizes) ->
            assertTrue("$part shows text", sizes.isNotEmpty())
            sizes.forEach { assertTrue("$part at $it is smaller than the step at $step", it.value < step.value) }
        }
        // And larger than body text anywhere else in the product: the theme's body styles.
        val typography = theme!!
        listOf(typography.bodyLarge, typography.bodyMedium, typography.bodySmall).forEach {
            assertTrue("the theme's body text at ${it.fontSize} is smaller than the step at $step", it.fontSize.value < step.value)
        }
    }

    @Test
    fun atTwiceTheFontScaleOnANarrowPhoneTheStepWrapsWholeAndKeepsItsReference() {
        open(scale = 1f)
        val single = stepLayout().let { it.getLineBottom(0) - it.getLineTop(0) }
        compose.runOnIdle { fontScale = 2f }
        compose.waitForIdle()
        val scaled = stepLayout()
        val double = scaled.getLineBottom(0) - scaled.getLineTop(0)
        // The platform scales large text less than linearly (Android 14's non-linear font scaling), so a line at 2.0
        // is not quite twice its height at 1.0; it is laid out well beyond it all the same.
        assertTrue("a line of the step grows with the font scale ($single px at 1.0, $double px at 2.0)", double / single >= 1.6f)
        compose.onNodeWithTag(CardTags.SERVINGS_MORE).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { compose.textsOf(CardTags.SERVINGS).single() == "Servings: 5" }
        compose.onNodeWithTag(CardTags.step(0)).performScrollTo()
        compose.waitForIdle()

        val layout = stepLayout()
        val text = layout.layoutInput.text
        assertEquals(theme!!.titleLarge.fontSize, layout.layoutInput.style.fontSize)
        assertTrue("it wraps over several lines", layout.lineCount > 1)
        assertFalse("nothing of it is cut off", layout.hasVisualOverflow)
        assertEquals(text.length, layout.getLineEnd(layout.lineCount - 1))
        (0 until layout.lineCount - 1).forEach { line ->
            val end = layout.getLineEnd(line)
            assertTrue("line $line ends between words: \"${text.text.substring(layout.getLineStart(line), end)}\"",
                text.text[end - 1].isWhitespace() || text.text.getOrNull(end)?.isWhitespace() == true)
        }
        val node = compose.onNodeWithTag(StepTags.text(0), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val content = compose.onNodeWithTag(CardTags.CONTENT).fetchSemanticsNode().boundsInRoot
        assertTrue("the step lies within the card: $node in $content", node.left >= content.left && node.right <= content.right)

        // The reference reads as its line, its arrow small on screen and read in words.
        val arrow = text.text.indexOf('↑')
        assertTrue("the rounded reference carries its arrow: ${text.text}", arrow >= 0 && text.text.contains("4\u00A0pc"))
        val span = text.spanStyles.singleOrNull { arrow in it.start until it.end }
        assertNotNull("the arrow is styled apart", span)
        assertTrue("the arrow is smaller than the step", span!!.item.fontSize.value < theme!!.titleLarge.fontSize.value)
        val spoken = compose.onNodeWithTag(StepTags.text(0), useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsProperties.Text].joinToString("") { it.text }
        assertTrue("read with its words: $spoken", spoken.contains(context.getString(R.string.card_quantity_rounded_up)))
        assertFalse(spoken.contains('↑'))
    }

    // Scenario 3 of v0.27: on Cooking, read from further away, a step is set a size up from View, from the theme's type
    // scale.
    @Test
    fun onCookingAStepReadsLargerThanOnView() {
        open(scale = 1f)
        val view = stepLayout().layoutInput.style.fontSize
        assertEquals(theme!!.titleLarge.fontSize, view)
        compose.chooseCardTab(CardMode.COOKING)
        val cooking = stepLayout().layoutInput.style
        assertEquals(theme!!.headlineSmall.fontSize, cooking.fontSize)
        assertEquals(theme!!.headlineSmall.lineHeight, cooking.lineHeight)
        assertTrue("on Cooking at ${cooking.fontSize}, on View at $view", cooking.fontSize.value > view.value)
    }

    @Test
    @Config(qualifiers = "en-w320dp-h640dp")
    fun onCookingAtTwiceTheFontScaleOnA320dpPhoneTheStepStillFitsWhole() {
        open(scale = 2f)
        compose.chooseCardTab(CardMode.COOKING)
        compose.onNodeWithTag(CardTags.step(0)).performScrollTo()
        compose.waitForIdle()
        val layout = stepLayout()
        assertEquals(theme!!.headlineSmall.fontSize, layout.layoutInput.style.fontSize)
        assertTrue("it wraps over several lines", layout.lineCount > 1)
        assertFalse("nothing of it is cut off", layout.hasVisualOverflow)
        compose.assertWhole(StepTags.text(0))
        val node = compose.onNodeWithTag(StepTags.text(0), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val content = compose.onNodeWithTag(CardTags.CONTENT).fetchSemanticsNode().boundsInRoot
        assertTrue("the step lies within the card: $node in $content", node.left >= content.left && node.right <= content.right)
    }
}
