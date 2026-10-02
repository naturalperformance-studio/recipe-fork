package app.recipefork.core.timer

import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.StepRun
import app.recipefork.core.recipe.StepText

/**
 * The name a timer announces itself by (FLOW-016): the name its author gave it, or else one derived from the text of
 * its own step. A step carries at most one timer (DOM-013), so the derived name is never shared within a step.
 */
object TimerName {
    /** How many characters of a step's text a derived name keeps (FLOW-016 (3)). */
    const val LENGTH = 60

    /** The mark that ends a derived name cut short. */
    const val ELLIPSIS = "…"

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
     * the space between its number and its unit included, falls before it instead.
     */
    fun derived(runs: List<StepRun>): String {
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
        val length = text.codePointCount(0, text.length)
        if (length <= LENGTH) return text.toString()
        val end = text.offsetByCodePoints(0, LENGTH)
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
        val head = text.substring(0, if (text.substring(0, kept).isBlank()) cut else kept)
        return head.trimEnd() + ELLIPSIS
    }

    /** The name [step]'s timer announces itself by, reading its text as typed; null where the step carries no timer. */
    fun of(step: Step): String? = step.timer?.let { it.label ?: derived(step.text) }

    /**
     * The name [step]'s timer announces itself by, each of its references read as [quantity] gives it (FLOW-016 (8));
     * null where the step carries no timer.
     */
    fun of(step: Step, quantity: (ingredientPosition: Int) -> String?): String? =
        step.timer?.let { it.label ?: derived(StepText.runs(step, quantity)) }

    /**
     * What a derived name is read from, apart from the scale: [step]'s typed text with a mark where each reference sits.
     * Held with a timer started under a derived name, it tells the step it was started from whatever count or fixing
     * the reader has chosen since; null where the step carries no timer or one with a given name.
     */
    fun source(step: Step): String? {
        val timer = step.timer ?: return null
        if (timer.label != null) return null
        return StepText.render(step) { REFERENCE_MARK }
    }

    /**
     * Whether a timer held as [name], started from [source] ([TimerName.source] at its start), is still [step]'s own:
     * its given name, unchanged; or a derived name read from exactly the words and references the step holds now —
     * whatever the scale it was read at, so a change of count, a fixing or an ingredient's quantity keeps it, while any
     * change to the author's words drops it. A timer held without a source was named from the step's typed text alone.
     */
    fun holds(step: Step, name: String, source: String?): Boolean {
        val timer = step.timer ?: return false
        timer.label?.let { return source == null && it == name }
        return if (source != null) source(step) == source else of(step) == name
    }

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
