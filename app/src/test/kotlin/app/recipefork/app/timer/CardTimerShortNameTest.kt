package app.recipefork.app.timer

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.card.CardMode
import app.recipefork.app.card.CardTags
import app.recipefork.app.card.ViewTimerTags
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.Timer
import app.recipefork.core.timer.TimerName
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.minutes

/**
 * v0.38, T-270: a timer with no name of its own is called on the screens by its step's first words — cut at a word to
 * about 24 characters, with "…" — on View, on Cooking and in the bar of running timers, in English and Russian; one
 * of a step with several keeps its number; a step shorter than the cut reads whole; a named timer keeps its name. The
 * notification keeps the whole derived name of FLOW-016.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "en-w411dp-h891dp")
class CardTimerShortNameTest : CardTimersKit() {
    /** A step's text, its short name, and how the screens write a number and the minutes. */
    private class Words(val step: String, val short: String, val knead: String, val numbered: String, val minutes: String, val stir: String)

    private val english = Words(
        step = "Bake until the crust is golden brown and the loaf sounds hollow when tapped",
        short = "Bake until the crust is…",
        knead = "Knead the dough",
        numbered = "%s · timer %d",
        minutes = "min",
        stir = "Keep stirring slowly over a low heat, scraping the bottom of the pan so that nothing catches or burns",
    )

    private val russian = Words(
        step = "Выпекайте до золотистой корочки около сорока минут при двухстах градусах",
        short = "Выпекайте до золотистой…",
        knead = "Замесите тесто",
        numbered = "%s · таймер %d",
        minutes = "мин",
        stir = "Помешивайте на слабом огне, соскребая со дна кастрюли, чтобы ничего не пригорело и не прилипло",
    )

    /** Its first step long, its second shorter than the cut, its third with two timers, then enough to scroll. */
    private fun bread(words: Words) = Recipe.create(now, owner = null).copy(
        title = "Bread",
        steps = listOf(
            Step(0, words.step, timer = Timer(40.minutes)),
            Step(1, words.knead, timer = Timer(10.minutes)),
            Step(2, words.step, timers = listOf(Timer(20.minutes), Timer(5.minutes, "Steam"))),
        ) + (3..25).map { Step(it, words.stir) },
    ).also { runBlocking { recipes.save(it) } }

    /** Whether the text tagged [tag] is there, read apart from the line it is merged into. */
    private fun there(tag: String) = compose.onAllNodes(hasTestTag(tag), true).fetchSemanticsNodes().isNotEmpty()

    private fun texts(tag: String): List<String> = compose.onAllNodes(hasTestTag(tag), true).fetchSemanticsNodes().single()
        .config.getOrElseNullable(SemanticsProperties.Text) { null }.orEmpty().map { it.text }

    private fun mergedTexts(tag: String): List<String> = compose.onNodeWithTag(tag).fetchSemanticsNode()
        .config.getOrElseNullable(SemanticsProperties.Text) { null }.orEmpty().map { it.text }

    /** The name on the control of the timer at [index] of the step at [position], scrolled into sight. */
    private fun name(position: Int, index: Int = 0): String {
        control(position, index)
        return mergedTexts(TimerTags.start(position, index)).first()
    }

    private fun onCooking(words: Words) {
        val recipe = bread(words)
        show(recipe)
        val numbered = words.numbered.format(words.short, 1)
        assertEquals(words.short, name(0))
        assertEquals(words.knead, name(1))
        assertEquals(numbered, name(2, 0))
        assertEquals("Steam", name(2, 1))

        // Started, it reads the same; its notification keeps the whole name the step derives (FLOW-016).
        start(0)
        awaitHeld { it.size == 1 }
        assertEquals(TimerName.derived(words.step), held().single().name)
        assertTrue(held().single().name.length > words.short.length)
        awaitStatus(0, "40:00")
        assertEquals(words.short, mergedTexts(TimerTags.start(0)).first())
    }

    @Test
    fun onCookingAnUnnamedTimerIsCalledByItsStepsFirstWords() = onCooking(english)

    @Test
    @Config(qualifiers = "ru-w411dp-h891dp")
    fun onCookingAnUnnamedTimerIsCalledByItsStepsFirstWordsInRussian() = onCooking(russian)

    private fun onView(words: Words) {
        bread(words).also { show(it) }
        compose.onNodeWithTag(CardTags.tab(CardMode.VIEW)).performClick()
        compose.waitUntil(PATIENCE_MS) { shown(ViewTimerTags.line(0)) }
        assertEquals(listOf("${words.short} · 40 ${words.minutes}"), texts(ViewTimerTags.line(0)))
        assertEquals(listOf("${words.knead} · 10 ${words.minutes}"), texts(ViewTimerTags.line(1)))
        assertEquals(listOf("${words.numbered.format(words.short, 1)} · 20 ${words.minutes}"), texts(ViewTimerTags.line(2, 0)))
        assertEquals(listOf("Steam · 5 ${words.minutes}"), texts(ViewTimerTags.line(2, 1)))
    }

    @Test
    fun onViewAnUnnamedTimerIsCalledByItsStepsFirstWords() = onView(english)

    @Test
    @Config(qualifiers = "ru-w411dp-h891dp")
    fun onViewAnUnnamedTimerIsCalledByItsStepsFirstWordsInRussian() = onView(russian)

    private fun inTheBar(words: Words) {
        bread(words).also { show(it) }
        start(0)
        awaitHeld { it.size == 1 }
        compose.onNodeWithTag(CardTags.step(25)).performScrollTo()
        compose.waitUntil(PATIENCE_MS) { there(TimerTags.barName(0)) }
        assertEquals(listOf(words.short), texts(TimerTags.barName(0)))

        // Two running gather into one line, led by the soonest: the step's first of two, by its number.
        start(2, 0)
        awaitHeld { it.size == 2 }
        compose.onNodeWithTag(CardTags.step(25)).performScrollTo()
        compose.waitUntil(PATIENCE_MS) { there(TimerTags.BAR_RUNNING_NAME) }
        assertEquals(listOf(words.numbered.format(words.short, 1)), texts(TimerTags.BAR_RUNNING_NAME))
        compose.onNodeWithTag(TimerTags.BAR_RUNNING).performClick()
        compose.waitUntil(PATIENCE_MS) { there(TimerTags.barName(0)) && there(TimerTags.barName(2, 0)) }
        assertEquals(listOf(words.short), texts(TimerTags.barName(0)))
        assertEquals(listOf(words.numbered.format(words.short, 1)), texts(TimerTags.barName(2, 0)))
    }

    @Test
    fun inTheBarAnUnnamedTimerIsCalledByItsStepsFirstWords() = inTheBar(english)

    @Test
    @Config(qualifiers = "ru-w411dp-h891dp")
    fun inTheBarAnUnnamedTimerIsCalledByItsStepsFirstWordsInRussian() = inTheBar(russian)

    // A name the author gave is the author's: the bar shows it whole, however long.
    @Test
    fun aNamedTimerInTheBarKeepsItsWholeName() {
        val named = "Bread in the oven on the lowest shelf"
        val recipe = Recipe.create(now, owner = null).copy(
            title = "Loaf",
            steps = listOf(Step(0, english.knead, timer = Timer(30.minutes, named))) + (1..25).map { Step(it, english.stir) },
        ).also { runBlocking { recipes.save(it) } }
        show(recipe)
        start(0)
        awaitHeld { it.size == 1 }
        compose.onNodeWithTag(CardTags.step(25)).performScrollTo()
        compose.waitUntil(PATIENCE_MS) { there(TimerTags.barName(0)) }
        assertEquals(listOf(named), texts(TimerTags.barName(0)))
    }
}
