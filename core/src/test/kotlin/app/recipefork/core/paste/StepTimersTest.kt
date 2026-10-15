package app.recipefork.core.paste

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.measureTime

/** T-278: the durations a pasted step names, read as its timers in whole minutes. */
class StepTimersTest {
    private fun minutes(text: String) = StepTimers.read(text).map { it.duration.inWholeMinutes }

    @Test fun minutesInRussian() = assertEquals(listOf(20L), minutes("Тушить 20 минут под крышкой."))

    @Test fun hoursAndMinutesAbbreviated() = assertEquals(listOf(90L), minutes("Запекать 1 ч 30 мин."))

    @Test fun hoursAndMinutesInWords() = assertEquals(listOf(90L, 135L), minutes("1 час 30 минут, потом 2 часа 15 минут"))

    @Test fun halfAnHourInOneWord() = assertEquals(listOf(30L), minutes("Дать постоять полчаса."))

    @Test fun anHourAndAHalfInWords() = assertEquals(listOf(90L, 30L), minutes("Варить полтора часа, потом half an hour"))

    @Test fun aRangeIsItsLargerEnd() = assertEquals(listOf(40L), minutes("Варить 35–40 минут."))

    @Test fun aRangeAfterThrough() = assertEquals(listOf(15L), minutes("Через 10-15 минут проверить."))

    @Test fun aRangeInWords() = assertEquals(listOf(25L, 15L), minutes("Bake 20 to 25 minutes, then от 10 до 15 мин"))

    @Test fun minutesInEnglish() = assertEquals(listOf(25L), minutes("Bake for 25 min."))

    @Test fun oneHour() = assertEquals(listOf(60L), minutes("Chill for 1 hour."))

    @Test fun aFractionOfAnHour() = assertEquals(listOf(90L, 90L), minutes("Simmer 1½ hours; заморозить на 1,5 часа"))

    @Test fun hoursAndMinutesAbbreviatedInEnglish() = assertEquals(listOf(75L), minutes("Roast 1 hr 15 mins."))

    @Test
    fun whatIsNoDurationIsNoTimer() {
        // A teaspoon, an ordinal minute, seconds, degrees and a number on its own.
        assertEquals(emptyList(), minutes("Добавить 1 ч. л. соли и 2 ч л сахара."))
        assertEquals(emptyList(), minutes("С 20-й минуты проверять. На 25-й развернуть."))
        assertEquals(emptyList(), minutes("Взбивать 30 секунд при 180 °C, 4 части."))
    }

    @Test
    fun aTimerIsWithinOneMinuteAndNinetyNineHoursFiftyNine() {
        assertEquals(emptyList(), minutes("0 минут, 100 часов"))
        assertEquals(listOf(1L, 5999L), minutes("1 минута, 99 ч 59 мин"))
    }

    @Test
    fun aStepKeepsItsFirstFiveTimersEachUnnamed() {
        val timers = StepTimers.read("1 мин, 2 мин, 3 мин, 4 мин, 5 мин, 6 мин")
        assertEquals((1L..5L).toList(), timers.map { it.duration.inWholeMinutes })
        assertTrue(timers.all { it.label == null })
    }

    @Test
    fun aLongTextIsReadAtOnce() {
        listOf("1".repeat(20_000) + " минут", "1 ".repeat(10_000), "1-".repeat(10_000), "1 ч ".repeat(10_000), "1,".repeat(10_000),
            "1 " + " ".repeat(20_000) + "мин", "1 час" + " ".repeat(20_000) + "x", "1 to ".repeat(10_000), "½".repeat(20_000))
            .forEach { text ->
                val took = measureTime { StepTimers.read(text) }
                assertTrue(took.inWholeMilliseconds < 1_000, "${text.take(10)}…: $took")
            }
    }
}
