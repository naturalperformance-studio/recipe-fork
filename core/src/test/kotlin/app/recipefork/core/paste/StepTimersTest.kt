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
    fun aSpoonWrittenOutIsNoHour() {
        listOf("Добавить 1 ч. ложку соли", "Всыпать ½ ч. ложки соды", "1 ч ложку", "1 ч/л сахара", "1 ст. ложку масла", "1 ст/л")
            .forEach { assertEquals(emptyList(), minutes(it), it) }
        assertEquals(listOf(60L), minutes("через 1 ч лепить"))
        assertEquals(listOf(120L), minutes("Оставить на 2 ч. Лук нарезать."))
        assertEquals(listOf(120L), minutes("Убрать в холодильник на 2 ч. Летом — на 3."))
    }

    @Test
    fun aGlyphAfterASpaceIsPartOfTheNumber() {
        listOf("1 ½ hours", "Томить 1 ½ часа", "Bake 1 ½ hrs").forEach { assertEquals(listOf(90L), minutes(it), it) }
    }

    @Test
    fun aRangeWithAUnitAtEachEndIsOneTimerOfItsLargerEnd() {
        assertEquals(listOf(60L), minutes("bake for 45 mins - 1 hr"))
        assertEquals(listOf(60L), minutes("Запекать 40 минут – 1 час"))
        assertEquals(listOf(80L), minutes("1 ч – 1 ч 20 мин"))
    }

    @Test
    fun moreShapesInEnglish() {
        assertEquals(listOf(90L), minutes("Simmer 1 1/2 hours."))
        assertEquals(listOf(60L), minutes("Let it rise for an hour."))
        assertEquals(listOf(90L), minutes("Roast for an hour and a half."))
        assertEquals(listOf(90L), minutes("Cook 1h 30m."))
    }

    @Test
    fun howOftenIsNoTimer() {
        assertEquals(emptyList(), minutes("Помешивать каждые 5 минут."))
        assertEquals(emptyList(), minutes("Stir every 10 minutes."))
    }

    @Test
    fun aParenthesisRestatingTheDurationBeforeItAddsNoTimer() {
        assertEquals(listOf(21L, 10L), minutes("Варить 21 минуту (ориентир 20–22 минуты). Настоять 10 минут."))
        assertEquals(listOf(10L, 5L), minutes("Тушить 10 минут (до мягкости), потом ещё 5 минут."))
        // The owner's peach filling: an alternative, not a restatement.
        assertEquals(listOf(7L, 4L), minutes("выпаривать сок 6–7 минут в сотейнике (на сковороде — первые 4 минуты)"))
        // A parenthesis further on is a duration of its own.
        assertEquals(listOf(10L, 25L), minutes("Варить 10 минут. Затем запекать (180°C, 20–25 минут)."))
        assertEquals(listOf(10L, 3L), minutes("Bake 10 minutes. Then broil (on high, 2-3 minutes)."))
    }

    @Test
    fun aLineBreakIsNoRange() = assertEquals(listOf(10L, 5L), minutes("Rest 10 minutes\n- 5 minutes more"))

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
            "1 " + " ".repeat(20_000) + "мин", "1 час" + " ".repeat(20_000) + "x", "1 to ".repeat(10_000), "½".repeat(20_000),
            "1 1/".repeat(10_000), "1 ".repeat(10_000) + "½", "1 ч" + " ".repeat(20_000) + "л", " ".repeat(20_000) + "каждые 5 минут",
            "5 минут (".repeat(5_000), "an hour and ".repeat(5_000), "1h ".repeat(10_000) + "30m",
            "минуту ".repeat(10_000), "по ".repeat(10_000) + "минуте", "час ".repeat(10_000), "a ".repeat(10_000) + "minute",
            "первую ".repeat(10_000) + "минуту", "минуту" + " ".repeat(20_000) + "меньше", "1 минуте ".repeat(5_000))
            .forEach { text ->
                val took = measureTime { StepTimers.read(text) }
                assertTrue(took.inWholeMilliseconds < 1_000, "${text.take(10)}…: $took")
            }
    }
}
