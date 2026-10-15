package app.recipefork.core.paste

import app.recipefork.core.recipe.RecipeBounds
import app.recipefork.core.recipe.Timer
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.time.Duration.Companion.minutes

/**
 * The timers a pasted step's text names: every duration in it ("20 минут", "1 ч 30 мин", "полчаса", "1½ hours",
 * "1 hr 15 mins", a minute or an hour with no number: "прогреть минуту", "минимум час", "по минуте", "an hour"), a range
 * read as its larger end ("35–40 минут" is 40), in whole minutes. A duration outside a timer's
 * range (1 minute to 99 h 59 min) is none; the first [RecipeBounds.MAX_TIMERS] are kept, unnamed, so that each takes its
 * name from the step. The text itself is left as it is.
 */
internal object StepTimers {
    private const val MAX_MINUTES = 99L * 60 + 59

    private const val GLYPHS = "½¼¾⅓⅔"
    private val GLYPH_VALUES = mapOf('½' to 0.5, '¼' to 0.25, '¾' to 0.75, '⅓' to 1.0 / 3, '⅔' to 2.0 / 3)

    // Every quantifier is possessive and a match starts only at a number or a number word, so the scan is linear on any text.
    private const val NUM = """(?:\d++(?:[.,]\d++|\s?+[$GLYPHS]|\s++\d++/\d++|/\d++)?|[$GLYPHS])"""
    private fun range(name: String) = """(?<${name}a>$NUM)(?:\s*+(?:[-–—]|до|to)\s*+(?<${name}b>$NUM))?"""

    /** "ч" is an hour unless a spoon follows it: "1 ч. л.", "1 ч ложку", "1 ч/л" — but "2 ч. Лук нарезать" is two hours. */
    private const val HOURS = """(?:час(?:а|ов)?|ч(?!\.?\s*+/?\s*+(?:л(?!\p{L})|лож))\.?|hours?|hrs?\.?|h)(?!\p{L})"""
    private const val MINUTES = """(?:минут[аыуе]?|мин\.?|minutes?|mins?\.?)(?!\p{L})"""
    private val DURATION = Regex(
        """(?<![\p{L}\p{N}.,/])(?:""" +
            """${range("h")}\s*+$HOURS(?:\s*+(?:и\s++|and\s++)?${range("hm")}\s*+(?:$MINUTES|m(?!\p{L})))?""" +
            """|${range("m")}\s*+$MINUTES""" +
            """|(?<half>полчаса|half\s++an\s++hour)(?!\p{L})""" +
            """|(?<oneAndHalf>полтора\s++часа|an\s++hour\s++and\s++a\s++half|one\s++and\s++a\s++half\s++hours?)(?!\p{L})""" +
            """|(?<hour>(?:an|one)\s++hour)(?!\p{L})""" +
            """|(?<minute>минуту|минутку|(?:по|около)\s++минут[еы]|(?:a|one)\s++minute)(?!\p{L})""" +
            """|(?<bareHour>час)(?!\p{L})""" +
            """)""",
        RegexOption.IGNORE_CASE,
    )

    /** What may stand between the two ends of a range written with a unit each: "45 mins - 1 hr", "40 минут – 1 час". */
    private val RANGE_JOINERS = setOf("-", "–", "—", "до", "to")

    /** A duration after one of these is how often, not how long: "помешивать каждые 5 минут". */
    private val EVERY = setOf("каждые", "каждых", "каждую", "каждый", "every")

    /**
     * A minute or an hour with no number after one of these is when, not how long: "в первую минуту", "за минуту до
     * конца".
     */
    private val WHEN = setOf("первую", "вторую", "третью", "последнюю", "первый", "последний", "за", "first", "last", "final")

    /** A minute or an hour with no number before one of these is how much longer or sooner: "на минуту меньше". */
    private val COMPARISONS = setOf(
        "меньше", "больше", "раньше", "позже", "дольше", "короче", "earlier", "later", "less", "more", "longer", "sooner",
    )

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
                gap != null && '\n' !in gap && gap.trim().lowercase() in RANGE_JOINERS -> {
                    if (previousKept && value != null) minutes[minutes.lastIndex] = maxOf(minutes.last(), value)
                    previousKept
                }
                gap != null && gap.trimStart().startsWith('(') && ')' !in gap && isRange(found) -> false
                wordBefore(text, found.range.first) in EVERY -> false
                isBare(found) && (wordBefore(text, found.range.first) in WHEN || wordAfter(text, found.range.last + 1) in COMPARISONS) -> false
                value == null -> false
                else -> true.also { minutes += value }
            }
            previous = found
            previousKept = kept
        }
        return minutes.take(RecipeBounds.MAX_TIMERS).map { Timer(it.minutes, label = null) }
    }

    /** A minute or an hour named by a word alone: "прогреть минуту", "минимум час", "an hour". */
    private fun isBare(found: MatchResult) = listOf("minute", "bareHour", "hour").any { (found.groups as MatchNamedGroupCollection)[it] != null }

    /** The word right after [at], lowercased, with only spaces between; null where there is none. */
    private fun wordAfter(text: String, at: Int): String? {
        var start = at
        while (start < text.length && text[start].isWhitespace()) start++
        var end = start
        while (end < text.length && text[end].isLetter()) end++
        return text.substring(start, end).lowercase().takeIf { it.isNotEmpty() }
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
            groups["hour"] != null || groups["bareHour"] != null -> 60.0
            groups["minute"] != null -> 1.0
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
