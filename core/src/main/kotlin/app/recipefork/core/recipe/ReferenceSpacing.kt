package app.recipefork.core.recipe

/**
 * The spaces around an ingredient reference in a step's text (FLOW-031): a reference put against a word is set apart
 * from it by a space, and a reference taken out leaves single spacing behind. The spaces are ordinary typed text of the
 * step. A neighbouring reference reads as a word: every quantity starts and ends with a letter, a digit or a placeholder.
 */
object ReferenceSpacing {
    /** What a reference stands against with no space between: the end of a phrase or of a bracket. */
    const val CLOSING = ",.:;!?)]}»…"

    /** What a reference follows with no space between: the start of a bracket. */
    const val OPENING = "([{«"

    /** Whether a reference after [left] — null at the start of the text — is set apart from it by a space. */
    fun spaceBefore(left: Char?): Boolean = left != null && !left.isWhitespace() && left !in OPENING

    /** Whether a reference before [right] — null at the end of the text — is set apart from it by a space. */
    fun spaceAfter(right: Char?): Boolean = right != null && !right.isWhitespace() && right !in CLOSING

    /** A reference put into a step: the step with it, the [reference] as it sits there, and whether a space follows it. */
    data class Insertion(val step: Step, val reference: IngredientReference, val spaceAfter: Boolean)

    /**
     * A reference to the ingredient at [ingredientPosition] put at [offset] of [step]'s text, a space typed on each side
     * where it stands against a word. Where another reference already sits at [offset], [afterExisting] says on which
     * side of it the new one goes; that side is against a word, so the two always end up at two offsets. A [share]
     * makes it a reference to that part of the ingredient; none, to its whole.
     */
    fun insert(step: Step, ingredientPosition: Int, offset: Int, afterExisting: Boolean, share: IngredientShare? = null): Insertion {
        val text = step.text
        val at = offset.coerceIn(0, text.length)
        val there = step.ingredientReferences.any { it.textOffset == at }
        val before = if (there && afterExisting) true else spaceBefore(text.getOrNull(at - 1))
        val after = if (there && !afterExisting) true else spaceAfter(text.getOrNull(at))
        val added = (if (before) 1 else 0) + (if (after) 1 else 0)
        val spaced = text.substring(0, at) + (if (before) " " else "") + (if (after) " " else "") + text.substring(at)
        val new = IngredientReference(ingredientPosition, at + if (before) 1 else 0, share)
        val moved = step.ingredientReferences.map {
            if (it.textOffset > at || (it.textOffset == at && !afterExisting)) it.copy(textOffset = it.textOffset + added) else it
        }
        return Insertion(step.copy(text = spaced, ingredientReferences = (moved + new).sortedBy { it.textOffset }), new, after)
    }

    /** A step closed up after a reference was taken out: the step, and which character of its old text went, if any. */
    data class Closing(val step: Step, val removedAt: Int?)

    /**
     * [step] closed up where a reference was taken out of it, at [at] of its text: of two spaces meeting there one goes;
     * a space left at the start or end of the text, or of a line, goes; a space left before punctuation ([CLOSING])
     * goes. Next to another reference nothing goes. Only spaces are taken, never a line break.
     */
    fun closeUp(step: Step, at: Int): Closing {
        val text = step.text
        if (at !in 0..text.length || step.ingredientReferences.any { it.textOffset == at }) return Closing(step, null)
        val left = text.getOrNull(at - 1)
        val right = text.getOrNull(at)
        fun edge(c: Char?) = c == null || c == '\n'
        val removed = when {
            left == ' ' && right == ' ' -> at
            edge(left) && right == ' ' -> at
            left == ' ' && (edge(right) || right!! in CLOSING) -> at - 1
            else -> return Closing(step, null)
        }
        val references = step.ingredientReferences.map { if (it.textOffset > removed) it.copy(textOffset = it.textOffset - 1) else it }
        return Closing(step.copy(text = text.removeRange(removed, removed + 1), ingredientReferences = references), removed)
    }

    /**
     * [step] with every reference to the ingredient at [ingredientPosition] taken out, closed up where each stood; the
     * references to other ingredients stay where they are in the sentence.
     */
    fun withoutReferencesTo(step: Step, ingredientPosition: Int): Step {
        val gone = step.ingredientReferences.filter { it.ingredientPosition == ingredientPosition }.map { it.textOffset }
        var result = step.copy(ingredientReferences = step.ingredientReferences.filter { it.ingredientPosition != ingredientPosition })
        // From the end, so that a space taken out never moves an offset still to be closed up.
        gone.sortedDescending().forEach { result = closeUp(result, it).step }
        return result
    }
}
