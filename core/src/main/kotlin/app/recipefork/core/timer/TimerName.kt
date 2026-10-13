package app.recipefork.core.timer

import app.recipefork.core.recipe.ReferenceQuantity
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.StepRun
import app.recipefork.core.recipe.StepText

/**
 * The name a timer announces itself by (FLOW-016): the name its author gave it, or else one derived from the text of
 * its own step. A step carries up to five timers (DOM-024), so the derived name may be shared within a step: an unnamed
 * timer of a step with several is told apart by its [number], which the screen shows with the name in its own words.
 * A step with one timer reads as it always did, with no number.
 */
object TimerName {
    /** How many characters of a step's text a derived name keeps (FLOW-016 (3)). */
    const val LENGTH = 60

    /** The mark that ends a derived name cut short. */
    const val ELLIPSIS = "…"

    /** How many characters of a derived name a screen shows ([short]): the notification keeps [LENGTH]. */
    const val SHORT_LENGTH = 24

    /**
     * The name derived from [stepText]: its whole text where it is [LENGTH] characters or fewer, with nothing marking
     * it (FLOW-016 (4)); otherwise its first [LENGTH] characters, cut back to the last space within them where the cut
     * would split a word, then an ellipsis (FLOW-016 (3)). A single word longer than [LENGTH] is cut at [LENGTH].
     *
     * A name is one line, so line breaks and runs of spaces read as one space, and the text is trimmed. Characters are
     * counted as the reader sees them: a character outside the basic plane counts once and is never split.
     */
    fun derived(stepText: String): String = derived(listOf(StepRun.Typed(stepText)))

    /**
     * The name derived from a step as it reads — its [runs], each reference already read as the screen shows it
     * (FLOW-016 (8)) — cut as [derived] cuts plain text. A reference is one unit: a cut that would fall inside one,
     * the space between its number and its unit included, falls before it instead. Its spaces stay as the runs give
     * them — no-break spaces, from [StepText.runs] — so it never wraps inside where the name is shown.
     */
    fun derived(runs: List<StepRun>): String = reading(runs).name

    /**
     * How a step's derived name reads (FLOW-016, GUIDE-006): the [Reading.name] as [derived] gives it, whether it was
     * cut, whether a reference stands within the step's first [LENGTH] characters — which a reader's larger count can
     * lengthen past the cut — and whether one stands within the name as shown.
     */
    fun reading(runs: List<StepRun>): Reading = reading(runs, LENGTH)

    /**
     * A derived [name] as a screen shows it, where a line or a button has no room for a notification's [LENGTH]
     * characters: cut as [derived] cuts, to [SHORT_LENGTH] characters — at a word, with an ellipsis, a character
     * outside the basic plane counting once. A reference is one unit here too: in a name each reference holds its
     * spaces as no-break spaces ([StepText.unbreakable]), so whatever such spaces join is never cut inside. A name of
     * [SHORT_LENGTH] characters or fewer is shown whole. Only a derived name is shortened: a given one is the author's.
     */
    fun short(name: String): String = reading(unitsOf(name), SHORT_LENGTH).name

    /** [name] as runs: each group of characters joined by no-break spaces a unit of its own, the rest typed text. */
    private fun unitsOf(name: String): List<StepRun> {
        val runs = ArrayList<StepRun>()
        var at = 0
        for (unit in JOINED.findAll(name)) {
            if (unit.range.first > at) runs += StepRun.Typed(name.substring(at, unit.range.first))
            runs += StepRun.Reference(-1, unit.value)
            at = unit.range.last + 1
        }
        if (at < name.length) runs += StepRun.Typed(name.substring(at))
        return runs
    }

    /** Characters joined by no-break spaces, as a reference reads within a name. */
    private val JOINED = Regex("[^\\s\u00A0]+(?:\u00A0+[^\\s\u00A0]+)+")

    /** [reading], cut at [length] characters. */
    private fun reading(runs: List<StepRun>, length: Int): Reading {
        // The text on one line, with, for every character, the run of the reference it belongs to, or -1.
        val text = StringBuilder()
        val owners = ArrayList<Int>()
        var space = false
        runs.forEachIndexed { index, run ->
            val owner = if (run is StepRun.Reference) index else -1
            for (c in run.text) {
                if (c in WHITESPACE) {
                    space = text.isNotEmpty()
                    continue
                }
                if (space) {
                    // A space between two characters of one reference is part of it.
                    text.append(' ')
                    owners += if (owners.last() == owner) owner else -1
                    space = false
                }
                text.append(c)
                owners += owner
            }
        }
        // Trimmed of every kind of space, as the plain text always was.
        while (text.isNotEmpty() && text.last().isWhitespace()) {
            text.setLength(text.length - 1)
            owners.removeAt(owners.lastIndex)
        }
        val lead = text.indexOfFirst { !it.isWhitespace() }.let { if (it < 0) text.length else it }
        text.delete(0, lead)
        repeat(lead) { owners.removeAt(0) }
        val count = text.codePointCount(0, text.length)
        if (count <= length) {
            val referenced = owners.any { it != -1 }
            return Reading(text.toString(), cut = false, referenceInOpening = referenced, referenceShown = referenced)
        }
        val end = text.offsetByCodePoints(0, length)
        // The cut stays where the next character is no part of a word (a space, a comma); otherwise back to the last space.
        val cut = if (!isPartOfAWord(Character.codePointAt(text, end))) {
            end
        } else {
            val space = text.lastIndexOf(" ", end - 1)
            if (space > 0) space else end
        }
        // Never inside a reference: back to where it starts.
        var kept = cut
        if (kept in 1 until owners.size && owners[kept] != -1 && owners[kept - 1] == owners[kept]) {
            val owner = owners[kept]
            while (kept > 0 && owners[kept - 1] == owner) kept--
        }
        val head = text.substring(0, if (text.substring(0, kept).isBlank()) cut else kept).trimEnd()
        return Reading(
            head + ELLIPSIS,
            cut = true,
            referenceInOpening = (0 until end).any { owners[it] != -1 },
            referenceShown = head.indices.any { owners[it] != -1 },
        )
    }

    /**
     * A derived name and what decides whether the editor shows it (GUIDE-006): [name] is what the notification carries,
     * [cut] whether the step was longer than [LENGTH] characters, [referenceInOpening] whether a reference stands within
     * the first [LENGTH], [referenceShown] whether one stands within [name].
     */
    data class Reading(val name: String, val cut: Boolean, val referenceInOpening: Boolean, val referenceShown: Boolean) {
        /** Whether the notification is cut here, or may be cut at a reader's larger serving count. */
        val mayBeCut: Boolean get() = cut || referenceInOpening
    }

    /**
     * The name the timer at [index] of [step] announces itself by, reading the step's text as typed: its given name, or
     * else the one derived from the step's text; null where the step carries no timer there.
     */
    fun of(step: Step, index: Int): String? = step.timers.getOrNull(index)?.let { it.label ?: derived(step.text) }

    /**
     * The name the timer at [index] of [step] announces itself by, each of the step's references — a part as its
     * part — read as [quantity] gives it, as the card shows it (FLOW-016 (8)); null where the step carries no timer
     * there.
     */
    fun of(step: Step, index: Int, quantity: ReferenceQuantity): String? =
        step.timers.getOrNull(index)?.let { it.label ?: derived(StepText.runs(step, quantity)) }

    /**
     * What tells the timer at [index] of [step] apart from the step's other timers (DOM-024): its place among them,
     * from 1, where it has no given name and the step carries more than one timer — its name, derived from the step's
     * text, is then shared. Null where the author named it, where it is the step's only timer, or where the step
     * carries no timer there. How the number is shown is the screen's.
     */
    fun number(step: Step, index: Int): Int? {
        val timer = step.timers.getOrNull(index) ?: return null
        return if (timer.label == null && step.timers.size > 1) index + 1 else null
    }

    /**
     * What a derived name is read from, apart from the scale: [step]'s typed text with a mark where each reference sits.
     * Held with a timer started under a derived name, it tells the step it was started from whatever count or fixing
     * the reader has chosen since; null where the step carries no timer at [index] or one with a given name there.
     */
    fun source(step: Step, index: Int): String? {
        val timer = step.timers.getOrNull(index) ?: return null
        if (timer.label != null) return null
        // A part is part of what the name reads; the scale it is read at is not. Read as spliced, not as displayed, so
        // a source held from before is the same source still.
        return StepText.spliced(step) { reference -> REFERENCE_MARK + reference.share?.let { "${it.amount} ${it.unit}" }.orEmpty() }
            .joinToString("") { it.text }
    }

    /**
     * Whether a timer held as [name], started from [source] ([TimerName.source] at its start) as the timer at [index]
     * of its step, is still [step]'s timer there: its given name, unchanged; or a derived name read from exactly the
     * words and references the step holds now — whatever the scale it was read at, so a change of count, a fixing or
     * an ingredient's quantity keeps it, while any change to the author's words drops it. A timer held without a source
     * was named from the step's typed text alone.
     */
    fun holds(step: Step, index: Int, name: String, source: String?): Boolean {
        val timer = step.timers.getOrNull(index) ?: return false
        timer.label?.let { return source == null && it == name }
        return if (source != null) source(step, index) == source else of(step, index) == name
    }

    /** Bridge for code written when a step held one timer: [of] for the step's first timer. */
    fun of(step: Step): String? = of(step, 0)

    /** Bridge for code written when a step held one timer: [of] for the step's first timer. */
    fun of(step: Step, quantity: ReferenceQuantity): String? = of(step, 0, quantity)

    /** Bridge for code written when a step held one timer: [source] for the step's first timer. */
    fun source(step: Step): String? = source(step, 0)

    /** Bridge for code written when a step held one timer: [holds] for the step's first timer. */
    fun holds(step: Step, name: String, source: String?): Boolean = holds(step, 0, name, source)

    /**
     * Whether [codePoint] belongs to the word it stands in: a letter or digit, a hyphen or an apostrophe inside a
     * word ("кое-где", "don't"), or a mark that combines with the letter before it.
     */
    private fun isPartOfAWord(codePoint: Int): Boolean =
        Character.isLetterOrDigit(codePoint) ||
            codePoint == '-'.code || codePoint == '\''.code || codePoint == '\u2019'.code ||
            Character.getType(codePoint) == Character.NON_SPACING_MARK.toInt()

    /** What `\s` matches: the characters that read as one space. */
    private const val WHITESPACE = " \t\n\u000B\u000C\r"

    /** Where a reference sits in a [source]: a character no typed text holds. */
    private const val REFERENCE_MARK = "\uFFFC"
}
