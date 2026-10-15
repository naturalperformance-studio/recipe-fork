package app.recipefork.core.paste

import app.recipefork.core.api.EquipmentEntryDto
import app.recipefork.core.api.IngredientEntryDto
import app.recipefork.core.search.DictionaryMatch
import app.recipefork.core.search.Inflection
import app.recipefork.core.search.WordPrefix

/**
 * The equipment a pasted text names, bound to the equipment dictionary: in a step ("на сковороде", "preheat the oven",
 * "in a large skillet") or on a line of an equipment section ("аэрогриль, пергамент"). A mention is a run of words that
 * is, as a whole, one of an entry's names in some form — the longest such run first; a heading of the dictionary
 * ("Техника") is no item.
 *
 * Precision before recall, in a step: a word that is part of an ingredient's name there ("grill seasoning", "свиную
 * лопатку") is no mention, nor is an English word that is also a verb unless an article or a preposition marks it as a
 * noun ("whisk the eggs", but "with a whisk"), nor a few words that read as something else ("весом", "таймер").
 */
internal class EquipmentMentions(
    equipment: List<EquipmentEntryDto>,
    private val ingredients: DictionaryMatch<IngredientEntryDto>,
) {
    private val match = DictionaryMatch(equipment.filter { it.parentId != null })

    /**
     * Every form of every word an entry's name holds, with the most words of a name holding it: a word with none of
     * them starts or continues no mention, and a mention starting with a word is no longer than a name holding it.
     */
    private val longest: Map<String, Int> = HashMap<String, Int>().also { longest ->
        match.dictionary.filter { !it.removed }.flatMap { listOf(it.labelRu, it.labelEn) + it.synonymsRu + it.synonymsEn }
            .map(WordPrefix::words)
            .forEach { words -> words.flatMap(Inflection::forms).forEach { longest.merge(it, words.size, ::maxOf) } }
    }

    /** The entries [text] names, in its order, each once; [inStep] applies a step's guards against false hits. */
    fun of(text: String, inStep: Boolean): List<EquipmentEntryDto> {
        val words = WORD.findAll(text).map { Word(it.range.first, it.range.last + 1, WordPrefix.fold(it.value)) }.toList()
        val reach = words.map { word -> if (word.folded in STOP) 0 else Inflection.forms(word.folded).maxOf { longest[it] ?: 0 } }
        // A text repeats its words: each run of them is bound once.
        val bindings = HashMap<String, DictionaryMatch.Bound<EquipmentEntryDto>?>()
        fun bind(run: String) = if (run in bindings) bindings[run] else match.bind(run).also { bindings[run] = it }
        val found = mutableListOf<EquipmentEntryDto>()
        var i = 0
        while (i < words.size) {
            var size = 0
            if (reach[i] > 0) {
                // The run of known words joined by spaces or hyphens only, at most as long as a name holding its first.
                var end = i + 1
                while (end < words.size && end - i < reach[i] && reach[end] > 0 && joined(text, words[end - 1], words[end])) end++
                for (to in end downTo i + 1) {
                    val bound = bind(text.substring(words[i].start, words[to - 1].end))
                    if (bound == null || bound.unused.isNotEmpty()) continue
                    if (inStep && (verbHere(text, words, i, to) || ingredientHere(text, words, i, to))) break
                    found += bound.entry
                    size = to - i
                    break
                }
            }
            i += maxOf(size, 1)
        }
        return found.distinctBy { it.id }
    }

    /** An English verb-noun ("whisk", "grill") with no article or preposition before it: the verb. */
    private fun verbHere(text: String, words: List<Word>, from: Int, to: Int): Boolean {
        if (to - from != 1 || words[from].folded !in VERBS) return false
        val before = words.getOrNull(from - 1)?.takeIf { joined(text, it, words[from]) } ?: return true
        if (before.folded in DETERMINERS || before.folded in PREPOSITIONS) return false
        val twoBefore = words.getOrNull(from - 2)?.takeIf { joined(text, it, before) }
        return twoBefore?.folded !in DETERMINERS
    }

    /** The words are part of an ingredient's name, read with a word on either side ("barbecue sauce"). */
    private fun ingredientHere(text: String, words: List<Word>, from: Int, to: Int): Boolean {
        val start = words.getOrNull(from - 1)?.start ?: words[from].start
        val end = words.getOrNull(to)?.end ?: words[to - 1].end
        val context = text.substring(start, end)
        val bound = ingredients.bind(context) ?: return false
        val unused = bound.unused.map(WordPrefix::fold).toMutableList()
        return (from until to).none { unused.remove(words[it].folded) }
    }

    private class Word(val start: Int, val end: Int, val folded: String)

    private companion object {
        val WORD = Regex("""[\p{L}\p{N}]++""")

        /** Words that would name an entry but in a recipe's text mostly mean something else, folded. */
        val STOP = setOf("весом", "веса", "весу", "весе", "таймер", "таймера", "таймером", "timer", "timers")

        val VERBS = setOf("whisk", "grill", "ladle", "sieve", "skewer", "funnel", "scale", "torch", "microwave", "barbecue", "bbq", "foil", "mill")
        val DETERMINERS = setOf("a", "an", "the", "your", "this", "that")
        val PREPOSITIONS = setOf("with", "on", "in", "into", "onto", "under", "over", "using", "from", "through")

        /** Two neighbouring words with nothing but spaces and hyphens between them. */
        fun joined(text: String, a: Word, b: Word) = (a.end until b.start).all { text[it].isWhitespace() || text[it] == '-' }
    }
}
