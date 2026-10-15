package app.recipefork.core.paste

import app.recipefork.core.recipe.RecipeBounds
import app.recipefork.core.recipe.Timer
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.time.Duration.Companion.minutes

/**
 * The timers a pasted step's text names: every duration in it ("20 минут", "1 ч 30 мин", "полчаса", "1½ hours",
 * "1 hr 15 mins"), a range read as its larger end ("35–40 минут" is 40), in whole minutes. A duration outside a timer's
 * range (1 minute to 99 h 59 min) is none; the first [RecipeBounds.MAX_TIMERS] are kept, unnamed, so that each takes its
 * name from the step. The text itself is left as it is.
 */
internal object StepTimers {
    private const val MAX_MINUTES = 99L * 60 + 59

    private const val GLYPHS = "½¼¾⅓⅔"
    private val GLYPH_VALUES = mapOf('½' to 0.5, '¼' to 0.25, '¾' to 0.75, '⅓' to 1.0 / 3, '⅔' to 2.0 / 3)

    // Every quantifier is possessive and a match starts only where a number does, so the scan is linear on any text.
    private const val NUM = """(?:\d++(?:[.,]\d++|\s?+[$GLYPHS]|\s++\d++/\d++|/\d++)?|[$GLYPHS])"""
    private fun range(name: String) = """(?<${name}a>$NUM)(?:\s*+(?:[-–—]|до|to)\s*+(?<${name}b>$NUM))?"""

    /** "ч" is an hour unless a spoon follows it: "1 ч. л.", "1 ч ложку", "1 ч/л". */
    private const val HOURS = """(?:час(?:а|ов)?|ч(?!\.?\s*+/?\s*+л)\.?|hours?|hrs?\.?|h)(?!\p{L})"""
    private const val MINUTES = """(?:минут[аыу]?|мин\.?|minutes?|mins?\.?)(?!\p{L})"""
    private val DURATION = Regex(
        """(?<![\p{L}\p{N}.,/])(?:""" +
            """${range("h")}\s*+$HOURS(?:\s*+(?:и\s++|and\s++)?${range("hm")}\s*+(?:$MINUTES|m(?!\p{L})))?""" +
            """|${range("m")}\s*+$MINUTES""" +
            """|(?<half>полчаса|half\s++an\s++hour)(?!\p{L})""" +
            """|(?<oneAndHalf>полтора\s++часа|an\s++hour\s++and\s++a\s++half|one\s++and\s++a\s++half\s++hours?)(?!\p{L})""" +
            """|(?<hour>(?:an|one)\s++hour)(?!\p{L})""" +
            """)""",
        RegexOption.IGNORE_CASE,
    )

    /** What may stand between the two ends of a range written with a unit each: "45 mins - 1 hr", "40 минут – 1 час". */
    private val RANGE_JOINERS = setOf("-", "–", "—", "до", "to")

    /** A duration after one of these is how often, not how long: "помешивать каждые 5 минут". */
    private val EVERY = setOf("каждые", "каждых", "каждую", "каждый", "every")

    /**
     * The timers [text] names, in its order, at most [RecipeBounds.MAX_TIMERS]. Two durations joined as a range are
     * one timer, its larger end; one after "каждые" / "every" is none; and so is a range in a parenthesis opened right after
     * another duration, which restates it ("21 минуту (ориентир 20–22 минуты)", not "6–7 минут (на сковороде — 4 минуты)").
     */
    fun read(text: String): List<Timer> {
        val minutes = mutableListOf<Long>()
        var previous: MatchResult? = null
        var previousKept = false
        for (found in DURATION.findAll(text)) {
            val value = minutesOf(found)?.takeIf { it in 1..MAX_MINUTES }
            val gap = previous?.let { text.substring(it.range.last + 1, found.range.first) }
            val kept = when {
                gap != null && gap.trim().lowercase() in RANGE_JOINERS -> {
                    if (previousKept && value != null) minutes[minutes.lastIndex] = maxOf(minutes.last(), value)
                    previousKept
                }
                gap != null && '(' in gap && ')' !in gap && isRange(found) -> false
                wordBefore(text, found.range.first) in EVERY -> false
                value == null -> false
                else -> true.also { minutes += value }
            }
            previous = found
            previousKept = kept
        }
        return minutes.take(RecipeBounds.MAX_TIMERS).map { Timer(it.minutes, label = null) }
    }

    private fun isRange(found: MatchResult) = listOf("hb", "hmb", "mb").any { (found.groups as MatchNamedGroupCollection)[it] != null }

    /** The word right before [at], lowercased, with only spaces between; null where there is none. */
    private fun wordBefore(text: String, at: Int): String? {
        var end = at
        while (end > 0 && text[end - 1].isWhitespace()) end--
        var start = end
        while (start > 0 && text[start - 1].isLetter()) start--
        return text.substring(start, end).lowercase().takeIf { it.isNotEmpty() }
    }

    private fun minutesOf(found: MatchResult): Long? {
        val groups = found.groups as MatchNamedGroupCollection
        fun larger(name: String): Double? {
            val a = groups["${name}a"]?.value ?: return null
            return listOfNotNull(valueOf(a), groups["${name}b"]?.value?.let(::valueOf)).maxOrNull()
        }
        val minutes = when {
            groups["half"] != null -> 30.0
            groups["oneAndHalf"] != null -> 90.0
            groups["hour"] != null -> 60.0
            groups["ha"] != null -> (larger("h") ?: return null) * 60 + (larger("hm") ?: 0.0)
            else -> larger("m") ?: return null
        }
        return BigDecimal(minutes).setScale(0, RoundingMode.HALF_UP).toLong()
    }

    /** "40", "1,5", "1.5", "1½", "1 ½", "½", "1 1/2", "1/2"; null for a number too long to be a duration, or no number. */
    private fun valueOf(text: String): Double? {
        if (text.length > 12) return null
        val glyph = GLYPH_VALUES[text.last()]
        val parts = (if (glyph != null) text.dropLast(1) else text).split(' ', '\t', '\n', '\r', '\u000B', '\u000C').filter(String::isNotEmpty)
        return parts.sumOf { part ->
            if ('/' !in part) return@sumOf part.replace(',', '.').toDouble()
            val (over, under) = part.split('/').map(String::toDouble)
            if (under == 0.0) return null else over / under
        } + (glyph ?: 0.0)
    }
}
