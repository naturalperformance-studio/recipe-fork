package app.recipefork.app.card

import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.Timer
import app.recipefork.core.scaling.InterfaceLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/** What the card's summary line counts, and how it rounds the timers' time together. */
class RecipeSummaryTest {
    // Under an hour to the nearest 5 min (never below 5), from an hour to the nearest 10 min; a half rounds up.
    @Test
    fun theTimersTimeIsRoundedToFiveMinutesUnderAnHourAndToTenFromAnHour() {
        val expected = mapOf(
            1L to 5, 2L to 5, 7L to 5, 8L to 10, 43L to 45, 57L to 55, 58L to 60, 59L to 60,
            60L to 60, 64L to 60, 65L to 70, 70L to 70, 94L to 90, 125L to 130, 6000L to 6000,
        )
        assertEquals(expected, expected.mapValues { (minutes, _) -> RecipeSummary.rounded(minutes) })
    }

    // Every timer of every step counts; a step that reads as nothing is not counted, nor its timers.
    @Test
    fun theSummaryCountsTheStepsTheCardShowsAndAddsUpTheirTimers() {
        val recipe = Recipe.create(Instant.parse("2026-10-09T12:00:00Z"), null).copy(
            steps = listOf(
                Step(0, "Boil", timers = listOf(Timer(10.minutes), Timer(1.hours))),
                Step(1, "Rest", timers = listOf(Timer(25.minutes))),
                Step(2, " ", timers = listOf(Timer(5.hours))),
            ),
        )
        assertEquals(RecipeSummary(0, 2, 100), RecipeSummary.of(recipe, InterfaceLanguage.ENGLISH))
    }

    @Test
    fun aRecipeWithoutTimersHasNoTimersTime() {
        val recipe = Recipe.create(Instant.parse("2026-10-09T12:00:00Z"), null).copy(steps = listOf(Step(0, "Stir")))
        assertNull(RecipeSummary.of(recipe, InterfaceLanguage.ENGLISH).minutes)
    }

    // A step that is only the author's note is listed on the card, so it is counted, and its timers with it — by the one
    // rule the card's list keeps a step by.
    @Test
    fun aStepOfOnlyANoteIsCountedWithItsTimers() {
        val recipe = Recipe.create(Instant.parse("2026-10-10T12:00:00Z"), null).copy(
            steps = listOf(
                Step(0, "Boil", timers = listOf(Timer(10.minutes))),
                Step(1, " ", timers = listOf(Timer(40.minutes)), note = "Let it rest, covered."),
                Step(2, " ", timers = listOf(Timer(5.hours))),
            ),
        )
        assertEquals(RecipeSummary(0, 2, 50), RecipeSummary.of(recipe, InterfaceLanguage.ENGLISH))
        assertEquals(listOf(true, true, false), recipe.steps.map { isListed(it, { null }, hasMedia = false) })
    }
}
