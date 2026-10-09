package app.recipefork.app.card

import app.recipefork.app.data.HeldLastCardTab
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.editor.textsOf
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.Step
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
import kotlin.math.abs

/**
 * Scenario 5 of cooking with busy hands: each step on the card starts with its number in a filled circle — decorative
 * to the screen reader, which reads the step's number once — its text is set in the theme's title style, a size up from
 * the body text it was, and the steps stand clearly apart from one another.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w360dp-h640dp")
class CardStepLayoutTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val recipes = RecipeRepository(ActiveBook(BookName.Pool, book.database))
    private val environment = CardEnvironment(lastTab = HeldLastCardTab(CardMode.COOKING)) { emptyList() }

    private val recipe = Recipe.create(Instant.parse("2026-10-04T12:00:00Z"), null).copy(
        title = "Rice",
        steps = listOf(Step(0, "Rinse the rice."), Step(1, "Simmer it covered for twenty minutes."), Step(2, "Rest.")),
    )

    private var typography: Typography? = null
    private var colors: ColorScheme? = null

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

    private fun open(fontScale: Float = 1f) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                MaterialTheme {
                    typography = MaterialTheme.typography
                    colors = MaterialTheme.colorScheme
                    CardScreen(recipe.id, recipes, environment, onBack = {}, onEdit = {})
                }
            }
        }
        compose.awaitCard()
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(hasTestTag(CardTags.step(2))).fetchSemanticsNodes().size == 1 }
    }

    private fun bounds(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    @Test
    fun eachStepStartsWithItsNumberInAFilledCircleTheScreenReaderReadsOnce() {
        open()
        (0..2).forEach { position ->
            val number = "${position + 1}"
            // Read as one line, the number said once: the step's own words follow it.
            val spoken = compose.textsOf(CardTags.step(position))
            assertEquals(listOf("$number.", recipe.steps[position].text), spoken)
            // The circle itself is decorative: it carries nothing for the screen reader.
            val circle = compose.onNodeWithTag(StepRowTags.number(position), useUnmergedTree = true).fetchSemanticsNode()
            assertTrue(circle.config.getOrNull(SemanticsProperties.Text).isNullOrEmpty())
            assertTrue(circle.config.getOrNull(SemanticsProperties.ContentDescription).isNullOrEmpty())
            // A circle, at the line's start, before the text.
            val box = circle.boundsInRoot
            assertEquals("the circle is round: $box", box.width, box.height, 1f)
            assertTrue(box.width >= with(compose.density) { StepNumberSize.toPx() } - 0.5f)
            assertEquals(bounds(CardTags.step(position)).left, box.left, 0.5f)
            assertTrue("the text follows the circle", bounds(StepTags.text(position)).left >= box.right)
        }
        // Filled in the theme's primary colour: inside the circle, above its number.
        val image = compose.onNodeWithTag(StepRowTags.number(0), useUnmergedTree = true).captureToImage().toPixelMap()
        val inside = image[image.width / 2, image.height / 8]
        val primary = colors!!.primary
        assertTrue("the circle is filled with the primary colour: $inside",
            abs(inside.red - primary.red) < 0.02f && abs(inside.green - primary.green) < 0.02f && abs(inside.blue - primary.blue) < 0.02f)
    }

    @Test
    fun aStepIsSetInTheThemesTitleStyleAndStandsApartFromTheNext() {
        open()
        val layout = compose.onNodeWithTag(StepTags.text(0), useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult].action!!.let { action ->
                mutableListOf<TextLayoutResult>().also { action(it) }.single()
            }
        val style = layout.layoutInput.style
        val title = typography!!.titleLarge
        assertEquals(title.fontSize, style.fontSize)
        assertEquals(title.lineHeight, style.lineHeight)
        assertEquals(title.fontWeight, style.fontWeight)
        val apart = with(compose.density) { 24.dp.toPx() }
        (0 until 2).forEach {
            val gap = bounds(CardTags.step(it + 1)).top - bounds(CardTags.step(it)).bottom
            assertTrue("step ${it + 1} stands $gap px apart from the next", gap >= apart - 0.5f)
        }
    }

    @Test
    fun atTwiceTheFontScaleTheNumberStaysInItsCircle() {
        open(fontScale = 2f)
        val circle = bounds(StepRowTags.number(0))
        assertEquals("the circle stays round: $circle", circle.width, circle.height, 1f)
        compose.assertWhole(CardTags.step(0))
    }
}
