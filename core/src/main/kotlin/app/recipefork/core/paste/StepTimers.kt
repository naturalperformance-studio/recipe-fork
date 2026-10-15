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
    private const val NUM = """(?:\d++(?:[.,]\d++)?[$GLYPHS]?|[$GLYPHS])"""
    private fun range(name: String) = """(?<${name}a>$NUM)(?:\s*+(?:[-–—]|до|to)\s*+(?<${name}b>$NUM))?"""
    private const val HOURS = """(?:час(?:а|ов)?|ч(?!\.?\s*+л(?!\p{L}))\.?|hours?|hrs?\.?|h)(?!\p{L})"""
    private const val MINUTES = """(?:минут[аыу]?|мин\.?|minutes?|mins?\.?)(?!\p{L})"""
    private val DURATION = Regex(
        """(?<![\p{L}\p{N}.,/])(?:""" +
            """${range("h")}\s*+$HOURS(?:\s*+(?:и\s++|and\s++)?${range("hm")}\s*+$MINUTES)?""" +
            """|${range("m")}\s*+$MINUTES""" +
            """|(?<half>полчаса|half\s++an\s++hour)(?!\p{L})""" +
            """|(?<oneAndHalf>полтора\s++часа)(?!\p{L})""" +
            """)""",
        RegexOption.IGNORE_CASE,
    )

    /** The timers [text] names, in its order, at most [RecipeBounds.MAX_TIMERS]. */
    fun read(text: String): List<Timer> = DURATION.findAll(text)
        .mapNotNull { found -> minutesOf(found)?.takeIf { it in 1..MAX_MINUTES } }
        .take(RecipeBounds.MAX_TIMERS)
        .map { Timer(it.minutes, label = null) }
        .toList()

    private fun minutesOf(found: MatchResult): Long? {
        val groups = found.groups as MatchNamedGroupCollection
        fun larger(name: String): Double? {
            val a = groups["${name}a"]?.value ?: return null
            return listOfNotNull(valueOf(a), groups["${name}b"]?.value?.let(::valueOf)).maxOrNull()
        }
        val minutes = when {
            groups["half"] != null -> 30.0
            groups["oneAndHalf"] != null -> 90.0
            groups["ha"] != null -> (larger("h") ?: return null) * 60 + (larger("hm") ?: 0.0)
            else -> larger("m") ?: return null
        }
        return BigDecimal(minutes).setScale(0, RoundingMode.HALF_UP).toLong()
    }

    /** "40", "1,5", "1.5", "1½", "½"; null for a number too long to be a duration. */
    private fun valueOf(text: String): Double? {
        val glyph = GLYPH_VALUES[text.last()]
        val digits = if (glyph != null) text.dropLast(1) else text
        if (digits.length > 6) return null
        val whole = if (digits.isEmpty()) 0.0 else digits.replace(',', '.').toDouble()
        return whole + (glyph ?: 0.0)
    }
}
