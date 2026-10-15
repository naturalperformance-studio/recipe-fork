package app.recipefork.core.paste

import app.recipefork.core.recipe.RecipeBounds
import app.recipefork.core.recipe.Timer
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.time.Duration.Companion.minutes

/**
 * The timers a pasted step's text names: every duration in it ("20 минут", "1 ч 30 мин", "полчаса", "1½ hours",
 * "1 hr 15 mins", a number in words: "две минуты", "two hours"; a minute or an hour with no number: "прогреть минуту",
 * "около часа", "по минуте", "another minute", "an hour"), a range
 * read as its larger end ("35–40 минут" is 40), in whole minutes. A duration outside a timer's
 * range (1 minute to 99 h 59 min) is none; the first [RecipeBounds.MAX_TIMERS] are kept, unnamed, so that each takes its
 * name from the step. The text itself is left as it is.
 */
internal object StepTimers {
    private const val MAX_MINUTES = 99L * 60 + 59
    private val SPACES = Regex("""\s++""")

    private const val GLYPHS = "½¼¾⅓⅔"
    private val GLYPH_VALUES = mapOf('½' to 0.5, '¼' to 0.25, '¾' to 0.75, '⅓' to 1.0 / 3, '⅔' to 2.0 / 3)

    // Every quantifier is possessive and a match starts only at a number or a number word, so the scan is linear on any text.
    private const val NUM = """(?:\d++(?:[.,]\d++|\s?+[$GLYPHS]|\s++\d++/\d++|/\d++)?|[$GLYPHS])"""
    private fun range(name: String) = """(?<${name}a>$NUM)(?:\s*+(?:[-–—]|до|to)\s*+(?<${name}b>$NUM))?"""

    /** "ч" is an hour unless a spoon follows it: "1 ч. л.", "1 ч ложку", "1 ч/л" — but "2 ч. Лук нарезать" is two hours. */
    private const val HOURS = """(?:час(?:а|ов)?|ч(?!\.?\s*+/?\s*+(?:л(?!\p{L})|лож))\.?|hours?|hrs?\.?|h)(?!\p{L})"""
    private const val MINUTES = """(?:минут[аыуе]?|мин\.?|minutes?|mins?\.?)(?!\p{L})"""
    /** One minute or hour in English words: "a", "an", "one", "another", "a further", "an extra". */
    private const val ONE = """(?:an?|one|another)(?:\s++(?:further|extra|additional|more))?+"""

    /**
     * The words a minute or an hour in the genitive or the dative stands after when it says how long: "около минуты",
     * "в течение часа", "не менее часа", "по минуте" — not "ориентироваться на минуты".
     */
    private const val HOW_LONG = """(?:около|в\s++течение|до|не\s++(?:менее|более|больше|меньше|дольше)|минимум|максимум|больше|меньше|ещё|еще)"""

    /** Numbers in words before a minute or an hour word: "две минуты", "пару минут", "two hours", "a couple of minutes". */
    private val NUMBER_WORDS = mapOf(
        "одну" to 1.0, "одна" to 1.0, "один" to 1.0, "две" to 2.0, "два" to 2.0, "пару" to 2.0, "три" to 3.0, "четыре" to 4.0,
        "пять" to 5.0, "шесть" to 6.0, "семь" to 7.0, "восемь" to 8.0, "девять" to 9.0, "десять" to 10.0, "пол" to 0.5,
        "two" to 2.0, "three" to 3.0, "four" to 4.0, "five" to 5.0, "six" to 6.0, "seven" to 7.0, "eight" to 8.0, "nine" to 9.0,
        "ten" to 10.0, "a couple of" to 2.0, "couple of" to 2.0,
    )
    private val NUMBER_WORD = NUMBER_WORDS.keys.sortedByDescending { it.length }.joinToString("|") { it.replace(" ", """\s++""") }

    private val DURATION = Regex(
        """(?<![\p{L}\p{N}.,/])(?:""" +
            """${range("h")}\s*+$HOURS(?:\s*+(?:и\s++|and\s++)?${range("hm")}\s*+(?:$MINUTES|m(?!\p{L})))?""" +
            """|${range("m")}\s*+$MINUTES""" +
            """|(?<half>полчаса|half\s++an\s++hour)(?!\p{L})""" +
            """|(?<oneAndHalf>полтора\s++часа|an\s++hour\s++and\s++a\s++half|one\s++and\s++a\s++half\s++hours?)(?!\p{L})""" +
            """|(?<hour>$ONE\s++hour)(?!\p{L})""" +
            """|(?<perMinute>по\s++минуте)(?!\p{L})""" +
            """|(?<minute>$ONE\s++minute|минут(?:ку|очку|а|у)|$HOW_LONG\s++минут(?:ы|ки))(?!\p{L})""" +
            """|(?<bareHour>час|$HOW_LONG\s++часа)(?!\p{L})""" +
            """|(?<word>$NUMBER_WORD)\s++(?<wordUnit>минут[аыу]?+|час(?:а|ов)?+|minutes?+|hours?+)(?!\p{L})""" +
            """)""",
        RegexOption.IGNORE_CASE,
    )

    /** What may stand between the two ends of a range written with a unit each: "45 mins - 1 hr", "40 минут – 1 час". */
    private val RANGE_JOINERS = setOf("-", "–", "—", "до", "to")

    /** A duration after one of these is how often, not how long: "помешивать каждые 5 минут". */
    private val EVERY = setOf("каждые", "каждых", "каждую", "каждый", "каждая", "every")

    /** A minute or an hour with no number after one of these is when, not how long: "в первую минуту". */
    private val ORDINALS = setOf(
        "первую", "первой", "первый", "вторую", "второй", "третью", "третьей", "последнюю", "последней", "последний",
        "четвёртую", "четвертую", "четвёртой", "четвертой", "пятую", "пятой", "first", "second", "third", "last", "final",
    )

    /**
     * A minute or an hour with no number before one of these is how much longer or sooner, or another thing: "на минуту
     * меньше", "an hour before baking", "час пик".
     */
    /** Before these, "an hour before …" is still how long: "Chill for an hour before rolling", "let cool a minute before". */
    private val WAITING = setOf("for", "least", "cool", "rest", "stand", "sit", "chill", "marinate", "set")

    private val TENS = setOf(
        "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety",
        "двадцать", "тридцать", "сорок", "пятьдесят", "шестьдесят", "семьдесят", "восемьдесят", "девяносто",
    )

    private val COMPARISONS = setOf(
        "меньше", "больше", "раньше", "позже", "дольше", "короче", "earlier", "later", "less", "more", "longer", "sooner",
        "before", "пик",
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
                isBare(found) && notHowLong(text, found) -> false
                // "twenty-five minutes", "двадцать пять минут": the last word of a compound number is not the number.
                found.groups["word"] != null && partOfANumber(text, found.range.first) -> false
                value == null -> false
                else -> true.also { minutes += value }
            }
            previous = found
            previousKept = kept
        }
        return minutes.take(RecipeBounds.MAX_TIMERS).map { Timer(it.minutes, label = null) }
    }

    /**
     * A bare minute or hour that says when or how much more, not how long: after an ordinal, in words or in figures
     * ("в первую минуту", "с 20-й минуты"), between "за" and "до" ("за минуту до конца", but "подойдёт за час"), before a
     * comparison or "before".
     */
    private fun notHowLong(text: String, found: MatchResult): Boolean {
        val before = wordBefore(text, found.range.first)
        val after = wordAfter(text, found.range.last + 1)
        val comparison = after in COMPARISONS && !(after == "before" && before in WAITING)
        return before in ORDINALS || comparison || before == "за" && after == "до" || figureBefore(text, found.range.first)
    }

    /** A number word right after a hyphen or a tens word: the end of a number in words, not a number of its own. */
    private fun partOfANumber(text: String, at: Int): Boolean =
        text.getOrNull(at - 1) == '-' || wordBefore(text, at) in TENS

    /** The run of non-spaces right before [at], spaces between, holds a digit: "20-й", "5th". */
    private fun figureBefore(text: String, at: Int): Boolean {
        var end = at
        while (end > 0 && text[end - 1].isWhitespace()) end--
        var start = end
        while (start > 0 && !text[start - 1].isWhitespace() && end - start < 8) start--
        return (start until end).any { text[it].isDigit() }
    }

    /** A minute or an hour named by a word alone: "прогреть минуту", "минимум час", "an hour". */
    private fun isBare(found: MatchResult) = listOf("minute", "perMinute", "bareHour", "hour").any { (found.groups as MatchNamedGroupCollection)[it] != null }

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
            groups["minute"] != null || groups["perMinute"] != null -> 1.0
            groups["word"] != null -> {
                val value = NUMBER_WORDS[groups["word"]!!.value.lowercase().replace(SPACES, " ")] ?: return null
                value * if (groups["wordUnit"]!!.value.lowercase().startsWith("h") || groups["wordUnit"]!!.value.lowercase().startsWith("ч")) 60 else 1
            }
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
