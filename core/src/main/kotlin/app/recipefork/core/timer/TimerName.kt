package app.recipefork.core.timer

import app.recipefork.core.recipe.Step

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
    fun derived(stepText: String): String {
        val text = stepText.trim().replace(WHITESPACE, " ")
        val length = text.codePointCount(0, text.length)
        if (length <= LENGTH) return text
        val end = text.offsetByCodePoints(0, LENGTH)
        // The cut stays where the next character is no part of a word (a space, a comma); otherwise back to the last space.
        val head = if (!Character.isLetterOrDigit(text.codePointAt(end))) {
            text.substring(0, end)
        } else {
            val space = text.lastIndexOf(' ', end - 1)
            if (space > 0) text.substring(0, space) else text.substring(0, end)
        }
        return head.trimEnd() + ELLIPSIS
    }

    /** The name [step]'s timer announces itself by; null where the step carries no timer. */
    fun of(step: Step): String? = step.timer?.let { it.label ?: derived(step.text) }

    private val WHITESPACE = Regex("\\s+")
}
