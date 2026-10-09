package app.recipefork.app.card

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.BookName
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.timer.LocalCardTimers
import app.recipefork.app.timer.TestTimers
import app.recipefork.app.timer.TimerTags
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.IngredientGroup
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.Timer
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.InterfaceLanguage
import app.recipefork.core.scaling.Rational
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
import java.time.Instant
import kotlin.math.abs
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * The card's lists in their two presentations ([CardMode]). VIEW: a line is as tall as its text with [VIEW_LINE_PADDING]
 * around it, a step as tall as its number and text, the steps [SECTION_GAP] and [VIEW_STEP_SPACING] apart; a done mark is shown (struck)
 * and read as done, but no line or step carries a click action and a tap changes nothing; no amount offers fixing; a
 * timer reads as its name and duration with no Start, and as its remaining time while it runs; group subheadings and
 * notes stand as in cooking. COOKING is today's card: 56 dp lines that a tap marks, the amount's own target, a Start
 * control on each timer. In English and in Russian.
 */
@RunWith(AndroidJUnit4::class)
class CardModeListsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val timers = TestTimers(context)
    private var mode by mutableStateOf(CardMode.VIEW)

    @Before
    fun setUp() = compose.registerIdlingResource(timers.idling)

    @After
    fun close() {
        compose.unregisterIdlingResource(timers.idling)
        timers.close()
    }

    /** The words of one language the lists are read in. */
    private class Words(
        val language: InterfaceLanguage,
        val names: List<String>,
        val note: String,
        val group: String,
        val steps: List<String>,
        val oven: String,
        val beans: String,
        val done: String,
        val ovenLine: String,
        val beansLine: String,
        val ovenRunning: String,
    )

    private val english = Words(
        InterfaceLanguage.ENGLISH,
        names = listOf("Chicken", "Rice", "Pepper"),
        note = "diced",
        group = "For the sauce",
        steps = listOf("Fry the chicken.", "Add the rice.", "Bake."),
        oven = "Oven",
        beans = "Beans",
        done = "Done",
        ovenLine = "Oven · 40 min",
        beansLine = "Beans · 1 h 30 min",
        ovenRunning = "Oven · 39:00",
    )

    private val russian = Words(
        InterfaceLanguage.RUSSIAN,
        names = listOf("Курица", "Рис", "Перец"),
        note = "кубиками",
        group = "Для соуса",
        steps = listOf("Обжарьте курицу.", "Добавьте рис.", "Запекайте."),
        oven = "Духовка",
        beans = "Фасоль",
        done = "Готово",
        ovenLine = "Духовка · 40 мин",
        beansLine = "Фасоль · 1 ч 30 мин",
        ovenRunning = "Духовка · 39:00",
    )

    private val now = Instant.parse("2026-10-09T12:00:00Z")

    private fun recipeIn(words: Words) = Recipe.create(now, null).copy(
        title = "Pilaf",
        servings = 4,
        ingredients = listOf(
            Ingredient(0, null, freeTextName = words.names[0], unitType = UnitType.EXACT, unit = QuantityUnit.GRAM, quantity = Rational.of(500), note = words.note),
            Ingredient(1, null, freeTextName = words.names[1], unitType = UnitType.EXACT, unit = QuantityUnit.GRAM, quantity = Rational.of(350)),
            Ingredient(2, null, freeTextName = words.names[2], unitType = UnitType.TASTE_UNMEASURED, unit = null, quantity = null),
        ),
        steps = listOf(
            Step(0, words.steps[0], timer = Timer(40.minutes, words.oven)),
            Step(1, words.steps[1]),
            Step(2, words.steps[2], timer = Timer(1.hours + 30.minutes, words.beans)),
        ),
    )

    private lateinit var marks: CardMarks

    /** Shows both lists of [words]' recipe in [start], the first ingredient and the first step marked done. */
    private fun show(words: Words, start: CardMode) {
        val recipe = recipeIn(words)
        val offer = FixOffer(listOf(QuantityUnit.GRAM), QuantityUnit.GRAM, divisible = null)
        val list = IngredientList(
            listOf(
                IngredientLine(words.names[0], "500 g", fixing = offer, note = words.note),
                IngredientLine(words.names[1], "350 g", fixing = offer),
                IngredientLine(words.names[2], null),
            ),
            holdsProportions = true,
        )
        marks = CardMarks(DoneMarks.inMemory(), BookName.Pool, recipe)
        marks.toggle(MarkedPart.INGREDIENT, 0)
        marks.toggle(MarkedPart.STEP, 0)
        mode = start
        compose.setContent {
            CompositionLocalProvider(LocalCardTimers provides timers.card) {
                MaterialTheme {
                    Box(Modifier.width(360.dp).padding(horizontal = 16.dp)) {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            IngredientsList(
                                recipe.ingredients, list, emptyList(), words.language, onFix = {}, onRelease = {},
                                marks = marks, groups = listOf(IngredientGroup(words.group, 1)), mode = mode,
                            )
                            StepsList(recipe.steps, recipe.id, recipe.title, marks = marks, mode = mode)
                        }
                    }
                }
            }
        }
        compose.waitUntil(PATIENCE_MS) { exists(CardTags.step(2)) }
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun node(tag: String): SemanticsNode = compose.onNodeWithTag(tag).fetchSemanticsNode()

    private fun unmerged(tag: String): SemanticsNode = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()

    private fun px(dp: Dp) = with(compose.density) { dp.toPx() }

    private fun text(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString("") { it.text }

    private fun clickable(node: SemanticsNode) = node.config.getOrNull(SemanticsActions.OnClick) != null

    private fun assertNear(message: String, expected: Float, actual: Float) =
        assertTrue("$message: expected $expected px, was $actual px", abs(expected - actual) <= 1.5f)

    private val lines = listOf(0, 1, 2).map(CardTags::ingredient)
    private val steps = listOf(0, 1, 2).map(CardTags::step)

    // VIEW: heights from node bounds.

    @Test
    @Config(qualifiers = "en-w360dp-h640dp")
    fun viewLinesAreAsTallAsTheirTextInEnglish() = viewHeights(english)

    @Test
    @Config(qualifiers = "ru-w360dp-h640dp")
    fun viewLinesAreAsTallAsTheirTextInRussian() = viewHeights(russian)

    private fun viewHeights(words: Words) {
        show(words, CardMode.VIEW)
        lines.forEachIndexed { position, tag ->
            val line = node(tag).boundsInRoot
            val name = unmerged(IngredientTags.name(position)).boundsInRoot
            assertNear("$tag is its text and ${VIEW_LINE_PADDING} above and below", name.height + 2 * px(VIEW_LINE_PADDING), line.height)
            assertTrue("$tag is shorter than the cooking target (${line.height} px)", line.height < px(CardTarget) - 0.5f)
        }
        // The lines stand one against the next; their padding is the gap.
        assertNear("the second line follows the first", node(lines[0]).boundsInRoot.bottom, unmerged(IngredientTags.group(0)).boundsInRoot.top - px(12.dp))
        assertNear("the third line follows the second", node(lines[1]).boundsInRoot.bottom, node(lines[2]).boundsInRoot.top)
        steps.forEachIndexed { position, tag ->
            val row = node(tag).boundsInRoot
            val texts = unmerged(StepTags.text(position)).boundsInRoot
            val number = unmerged(StepRowTags.number(position)).boundsInRoot
            assertNear("$tag is as tall as its number and text", maxOf(texts.height, number.height), row.height)
            assertTrue("$tag is shorter than the cooking target (${row.height} px)", row.height < px(CardTarget) - 0.5f)
        }
        // A step follows the one before — its text, or its timer — a modest gap below it.
        assertNear("the second step stands under the first's timer", unmerged(ViewTimerTags.line(0)).boundsInRoot.bottom + px(SECTION_GAP + VIEW_STEP_SPACING), node(steps[1]).boundsInRoot.top)
        assertNear("the third step stands under the second", node(steps[1]).boundsInRoot.bottom + px(SECTION_GAP + VIEW_STEP_SPACING), node(steps[2]).boundsInRoot.top)
        // Groups and notes stand as in cooking.
        val group = unmerged(IngredientTags.group(0))
        assertEquals(words.group, text(group))
        assertTrue("the group's subheading is a heading", group.config.getOrNull(SemanticsProperties.Heading) != null)
        assertEquals("${words.names[0]} — ${words.note}", text(unmerged(IngredientTags.name(0))))
    }

    // VIEW: marks shown, nothing to click.

    @Test
    @Config(qualifiers = "en-w360dp-h640dp")
    fun viewLinesCarryNoClickInEnglish() = viewNoClick(english)

    @Test
    @Config(qualifiers = "ru-w360dp-h640dp")
    fun viewLinesCarryNoClickInRussian() = viewNoClick(russian)

    private fun viewNoClick(words: Words) {
        show(words, CardMode.VIEW)
        val anyClick = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick), useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue("nothing in the lists answers a click: ${anyClick.map { it.config }}", anyClick.isEmpty())
        (lines + steps).forEach { tag ->
            val line = node(tag)
            assertFalse("$tag carries no click action", clickable(line))
            assertNull("$tag carries no custom action", line.config.getOrNull(SemanticsActions.CustomActions))
        }
        // Marked ones read as done and are struck; the others are not.
        listOf(lines[0], steps[0]).forEach { assertEquals("$it reads as done", words.done, node(it).config.getOrNull(SemanticsProperties.StateDescription)) }
        listOf(lines[1], steps[1]).forEach { assertNull("$it is not done", node(it).config.getOrNull(SemanticsProperties.StateDescription)) }
        assertEquals(TextDecoration.LineThrough, compose.textLayoutsWithin(IngredientTags.name(0)).single().layoutInput.style.textDecoration)
        assertEquals(TextDecoration.LineThrough, compose.textLayoutsWithin(StepTags.text(0)).single().layoutInput.style.textDecoration)
        // No amount offers fixing.
        listOf(0, 1).forEach { assertFalse("the amount of line $it offers no fixing", exists(CardTags.fixAmount(it))) }
        // A tap on a marked and on an unmarked line, on the amount and on a step changes nothing.
        listOf(lines[0], lines[1], steps[0], steps[1]).forEach { compose.onNodeWithTag(it).performScrollTo().performClick() }
        compose.waitForIdle()
        assertTrue(marks.done(MarkedPart.INGREDIENT, 0))
        assertFalse(marks.done(MarkedPart.INGREDIENT, 1))
        assertTrue(marks.done(MarkedPart.STEP, 0))
        assertFalse(marks.done(MarkedPart.STEP, 1))
        assertFalse("no fixing control opened", exists(CardTags.FIX_FIELD))
    }

    // VIEW: timers.

    @Test
    @Config(qualifiers = "en-w360dp-h640dp")
    fun viewTimersShowTheirDurationInEnglish() = viewTimers(english)

    @Test
    @Config(qualifiers = "ru-w360dp-h640dp")
    fun viewTimersShowTheirDurationInRussian() = viewTimers(russian)

    private fun viewTimers(words: Words) {
        show(words, CardMode.VIEW)
        assertEquals(words.ovenLine, text(unmerged(ViewTimerTags.line(0))))
        assertEquals(words.beansLine, text(unmerged(ViewTimerTags.line(2))))
        listOf(0, 2).forEach { position ->
            assertFalse("step $position offers no Start", exists(TimerTags.start(position)))
            assertFalse("step $position offers no Cancel", exists(TimerTags.cancel(position)))
        }
    }

    @Test
    @Config(qualifiers = "en-w360dp-h640dp")
    fun aRunningTimerShowsItsRemainingTimeInViewInEnglish() = runningInView(english)

    @Test
    @Config(qualifiers = "ru-w360dp-h640dp")
    fun aRunningTimerShowsItsRemainingTimeInViewInRussian() = runningInView(russian)

    private fun runningInView(words: Words) {
        show(words, CardMode.COOKING)
        compose.onNodeWithTag(TimerTags.start(0)).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { exists(TimerTags.cancel(0)) }
        compose.runOnIdle { mode = CardMode.VIEW }
        compose.runOnIdle { timers.clock.advance(1.minutes) }
        compose.waitUntil(PATIENCE_MS) { exists(ViewTimerTags.line(0)) && text(unmerged(ViewTimerTags.line(0))) == words.ovenRunning }
        assertEquals(words.ovenRunning, text(unmerged(ViewTimerTags.line(0))))
        assertFalse("a running timer offers no Start in view", exists(TimerTags.start(0)))
        assertEquals("the timer not started shows its duration", words.beansLine, text(unmerged(ViewTimerTags.line(2))))
    }

    // COOKING: today's card.

    @Test
    @Config(qualifiers = "en-w360dp-h640dp")
    fun cookingIsTodaysCardInEnglish() = cooking(english)

    @Test
    @Config(qualifiers = "ru-w360dp-h640dp")
    fun cookingIsTodaysCardInRussian() = cooking(russian)

    private fun cooking(words: Words) {
        show(words, CardMode.COOKING)
        (lines + steps).forEach { tag ->
            val height = node(tag).boundsInRoot.height
            assertTrue("$tag is the card's target tall ($height px)", height + 0.5f >= px(CardTarget))
            assertTrue("$tag answers a click", clickable(node(tag)))
        }
        assertEquals(words.done, node(lines[0]).config.getOrNull(SemanticsProperties.StateDescription))
        listOf(0, 1).forEach { compose.assertCardTarget(CardTags.fixAmount(it)) }
        listOf(0, 2).forEach { assertTrue("step $it offers Start", exists(TimerTags.start(it))) }
        assertFalse("no view timer line in cooking", exists(ViewTimerTags.line(0)))
        // A tap marks, the amount opens its fixing control.
        compose.onNodeWithTag(lines[1]).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { marks.done(MarkedPart.INGREDIENT, 1) }
        compose.onNodeWithTag(CardTags.fixAmount(1), useUnmergedTree = true).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { exists(CardTags.FIX_FIELD) }
        // Switching to view closes it.
        compose.runOnIdle { mode = CardMode.VIEW }
        compose.waitUntil(PATIENCE_MS) { !exists(CardTags.FIX_FIELD) }
    }
}
