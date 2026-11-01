package app.recipefork.app.card

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.data.HeldLastCardTab
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.editor.textsOf
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.timer.LocalCardTimers
import app.recipefork.app.timer.TestTimers
import app.recipefork.app.timer.TimerTags
import app.recipefork.app.ui.AppTheme
import app.recipefork.app.ui.ReadingFamily
import app.recipefork.app.ui.WorkingFamily
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.IngredientReference
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.Timer
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.Rational
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import kotlin.math.abs
import kotlin.time.Duration.Companion.minutes

/**
 * T-314, the steps as a page of a cookbook on View: a step's number stands in the margin — the reading face at 30 sp in
 * a 34 dp column — and its text beside it at 16 sp; a reference in the text is set in the working face and follows the
 * servings; each timer is a duration label ("Sauce · 15 min"), several side by side. The screen reader reads the number
 * once. At twice the font scale on a 320 dp phone nothing is cut. Cooking keeps its look: the circle, the headline-sized
 * text, references in the text's own face, the timer controls.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w360dp-h640dp")
class CardStepPageTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val timers = TestTimers(context)
    private val recipes = RecipeRepository(ActiveBook(BookName.Pool, book.database))
    private val now = Instant.parse("2026-10-29T12:00:00Z")

    private val fry = "Melt  of butter and fry the onion until soft."
    private val simmer = "Add the peppers and the tomatoes and simmer the sauce uncovered until it thickens."
    private val note = "Tinned tomatoes in winter."

    /** For 4: 40 g of butter, referred to in the first step; twelve steps, so that a number takes two figures. */
    private val recipe = Recipe.create(now, null).copy(
        title = "Shakshuka",
        servings = 4,
        ingredients = listOf(
            Ingredient(0, null, freeTextName = "Butter", unitType = UnitType.EXACT, unit = QuantityUnit.GRAM, quantity = Rational.of(40)),
        ),
        steps = listOf(
            Step(0, fry, listOf(IngredientReference(0, 5))),
            Step(1, simmer, timers = listOf(Timer(15.minutes, "Sauce"), Timer(6.minutes, "Eggs")), note = note),
        ) + (2..11).map { Step(it, "Stir and taste.") },
    )

    private var theme: Typography? = null
    private var scheme: ColorScheme? = null

    @Before
    fun setUp() {
        compose.registerIdlingResource(book.idling)
        compose.registerIdlingResource(timers.idling)
        runBlocking { recipes.save(recipe) }
    }

    @After
    fun close() {
        compose.unregisterIdlingResource(book.idling)
        compose.unregisterIdlingResource(timers.idling)
        book.close()
        timers.close()
    }

    private fun open(mode: CardMode = CardMode.VIEW, fontScale: Float = 1f) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale),
                LocalCardTimers provides timers.card,
                LocalAwakeClock provides TestAwakeClock(),
            ) {
                AppTheme {
                    theme = MaterialTheme.typography
                    scheme = MaterialTheme.colorScheme
                    CardScreen(recipe.id, recipes, CardEnvironment(lastTab = HeldLastCardTab(mode)) { emptyList() }, onBack = {}, onEdit = {})
                }
            }
        }
        compose.awaitCard()
        compose.waitUntil(PATIENCE_MS) { exists(CardTags.step(11)) }
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun bounds(tag: String): Rect =
        compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().let { Rect(it.positionInRoot, it.size.toSize()) }

    private fun layoutOf(tag: String): TextLayoutResult = mutableListOf<TextLayoutResult>().also {
        compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!(it)
    }.single()

    private fun px(dp: Dp) = with(compose.density) { dp.toPx() }

    private fun assertNear(message: String, expected: Float, actual: Float) =
        assertTrue("$message: expected $expected px, was $actual px", abs(expected - actual) <= 1.5f)

    /** The span of the step's text at [position] that covers the reference, and the words it covers. */
    private fun referenceOf(position: Int, reference: String): AnnotatedString.Range<androidx.compose.ui.text.SpanStyle>? {
        val text = layoutOf(StepTags.text(position)).layoutInput.text
        val start = text.text.indexOf(reference)
        assertTrue("\"$reference\" reads in \"${text.text}\"", start >= 0)
        return text.spanStyles.singleOrNull { it.start == start && it.end == start + reference.length }
    }

    private fun scroll(tag: String) {
        compose.onNodeWithTag(tag, useUnmergedTree = true).performScrollTo()
        compose.waitForIdle()
    }

    @Test
    fun theNumberStandsInTheMarginAndTheTextBesideIt() {
        open()
        listOf(0, 1, 11).forEach { position ->
            scroll(CardTags.step(position))
            val number = layoutOf(StepRowTags.number(position)).layoutInput.style
            assertEquals("step $position's number in the reading face", ReadingFamily, number.fontFamily)
            assertEquals(30.sp, number.fontSize)
            assertEquals("its number muted", scheme!!.onSurfaceVariant, number.color)
            val text = layoutOf(StepTags.text(position)).layoutInput.style
            assertEquals("step $position's text in the reading face", ReadingFamily, text.fontFamily)
            assertEquals(16.sp, text.fontSize)
            assertEquals(theme!!.bodyLarge.fontSize, text.fontSize)
            // The margin is 34 dp — the widest number, "12", still fits it at this scale — and the text 8 dp beyond it.
            val row = bounds(CardTags.step(position))
            val margin = bounds(StepRowTags.number(position))
            assertNear("step $position's number at the row's start", row.left, margin.left)
            assertNear("step $position's margin 34 dp wide", px(VIEW_MARGIN), margin.width)
            assertNear("step $position's text beside the margin", row.left + px(VIEW_MARGIN + VIEW_MARGIN_GAP), bounds(StepTags.text(position)).left)
            assertTrue("step $position's number fits its margin", layoutOf(StepRowTags.number(position)).size.width <= margin.width + 0.5f)
            assertFalse(layoutOf(StepRowTags.number(position)).hasVisualOverflow)
        }
        // No circle, no line: what Cooking draws is not on View.
        assertEquals(false, compose.onNodeWithTag(StepRowTags.number(0), useUnmergedTree = true).fetchSemanticsNode().config[ShowsCheck])
    }

    @Test
    fun theNumberIsReadOnce() {
        open()
        assertEquals(listOf("1.", fry.replace("  ", " 40 g ")), compose.textsOf(CardTags.step(0)))
        // The reader's note stands after the step's labels, in the margin, and is read there (T-315).
        assertEquals(listOf("2.", simmer), compose.textsOf(CardTags.step(1)))
        assertEquals(listOf(note), compose.textsOf(StepRowTags.note(1)))
        assertEquals(listOf("12.", "Stir and taste."), compose.textsOf(CardTags.step(11)))
        // The number in the margin says nothing of its own.
        val number = compose.onNodeWithTag(StepRowTags.number(0), useUnmergedTree = true).fetchSemanticsNode()
        assertNull(number.config.getOrNull(SemanticsProperties.Text))
        val said = compose.onAllNodes(
            hasAnyAncestor(hasTestTag(CardTags.STEPS)) and SemanticsMatcher("says 1") { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == "1" } == true
            },
        ).fetchSemanticsNodes()
        assertTrue("no node reads the bare number: $said", said.isEmpty())
    }

    @Test
    fun aReferenceIsSetInTheWorkingFaceAndFollowsTheServings() {
        open()
        val before = referenceOf(0, "40 g")
        assertTrue("the reference is styled apart", before != null)
        assertEquals(WorkingFamily, before!!.item.fontFamily)
        assertEquals(FontWeight.Medium, before.item.fontWeight)
        assertEquals("tnum", before.item.fontFeatureSettings)
        assertEquals(VIEW_REFERENCE_SIZE, before.item.fontSize)
        // The rest of the text stays in the reading face.
        assertEquals(1, layoutOf(StepTags.text(0)).layoutInput.text.spanStyles.size)

        compose.onNodeWithTag(CardTags.SERVINGS_MORE).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { compose.textsOf(CardTags.SERVINGS).single() == "Servings: 5" }
        scroll(CardTags.step(0))
        val after = referenceOf(0, "50 g")
        assertTrue("the reference follows the servings, still in the working face", after != null)
        assertEquals(WorkingFamily, after!!.item.fontFamily)
    }

    @Test
    fun timersAreDurationLabelsSideBySide() {
        open()
        scroll(CardTags.step(1))
        assertEquals(listOf("Sauce · 15 min"), compose.textsOf(ViewTimerTags.line(1, 0)))
        assertEquals(listOf("Eggs · 6 min"), compose.textsOf(ViewTimerTags.line(1, 1)))
        val words = layoutOf(ViewTimerTags.line(1, 0)).layoutInput.style
        assertEquals("a label's words in the working face", WorkingFamily, words.fontFamily)
        assertEquals("tnum", words.fontFeatureSettings)
        assertEquals(scheme!!.onSurfaceVariant, words.color)
        // In the text's column, under the text, the two labels on one line; the reader's note after them (T-315).
        val first = bounds(ViewTimerTags.label(1, 0))
        val second = bounds(ViewTimerTags.label(1, 1))
        val text = bounds(StepTags.text(1))
        assertNear("the first label under the text's start", text.left, first.left)
        assertTrue("the labels under the text", first.top >= text.bottom)
        assertTrue("the note under the labels", bounds(StepRowTags.margin(1)).top >= maxOf(first.bottom, second.bottom))
        assertNear("the second label on the first's line", first.top, second.top)
        assertNear("the second label 6 dp after the first", first.right + px(VIEW_LABELS_GAP), second.left)
        assertTrue("a label is as wide as its words, both within the column", second.right <= text.right + 0.5f)
        // Nothing on them starts a timer.
        assertFalse(exists(TimerTags.start(1)))
        listOf(ViewTimerTags.label(1, 0), ViewTimerTags.line(1, 0)).forEach {
            assertNull("$it answers no click", compose.onNodeWithTag(it, useUnmergedTree = true).fetchSemanticsNode().config.getOrNull(SemanticsActions.OnClick))
        }
    }

    @Test
    @Config(qualifiers = "en-w320dp-h640dp")
    fun atTwiceTheFontScaleOnA320dpPhoneNothingIsCut() {
        open(fontScale = 2f)
        val content = bounds(CardTags.CONTENT)
        listOf(0, 1, 9, 11).forEach { position ->
            scroll(CardTags.step(position))
            compose.assertWhole(CardTags.step(position))
            // The number, grown with the scale, keeps a margin wide enough for it: the text starts after it.
            val number = layoutOf(StepRowTags.number(position))
            val margin = bounds(StepRowTags.number(position))
            assertFalse("step $position's number is not cut", number.hasVisualOverflow)
            assertTrue("step $position's number fits its margin", number.size.width <= margin.width + 0.5f)
            assertTrue("step $position's text after its number", bounds(StepTags.text(position)).left >= margin.right)
            val step = bounds(CardTags.step(position))
            assertTrue("step $position lies within the card: $step in $content", step.left >= content.left - 0.5f && step.right <= content.right + 0.5f)
        }
        // One margin for all: every step's text starts at the same line.
        assertNear("the steps' texts start alike", bounds(StepTags.text(0)).left, bounds(StepTags.text(11)).left)
        listOf(0, 1).forEach { index ->
            scroll(ViewTimerTags.label(1, index))
            compose.assertWhole(ViewTimerTags.label(1, index))
            val label = bounds(ViewTimerTags.label(1, index))
            assertTrue("label $index lies within the card: $label in $content", label.right <= content.right + 0.5f)
        }
        scroll(StepRowTags.note(1))
        compose.assertWhole(StepRowTags.note(1))
    }

    @Test
    fun cookingKeepsItsLook() {
        open(mode = CardMode.COOKING)
        val text = layoutOf(StepTags.text(0)).layoutInput
        assertEquals(theme!!.headlineSmall.fontSize, text.style.fontSize)
        assertTrue("no reference styled apart on Cooking: ${text.text.spanStyles}", text.text.spanStyles.isEmpty())
        // The number in its circle, drawn, the text after the circle's gap.
        assertFalse("the circle carries no text layout", compose.onNodeWithTag(StepRowTags.number(0), useUnmergedTree = true).fetchSemanticsNode()
            .config.contains(SemanticsActions.GetTextLayoutResult))
        assertNear("the text after the circle", bounds(CardTags.step(0)).left + px(StepIndent), bounds(StepTags.text(0)).left)
        scroll(CardTags.step(1))
        assertTrue("the timer offers Start", exists(TimerTags.start(1)))
        assertFalse("no duration label on Cooking", exists(ViewTimerTags.label(1, 0)))
    }
}
