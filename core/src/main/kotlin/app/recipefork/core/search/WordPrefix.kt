package app.recipefork.core.search

import java.util.Locale

/**
 * The word-prefix match of the ingredient field (FLOW-057), which the recipe book's search uses as well (FLOW-035):
 * a text matches a query when, for every word of the query, one of the text's words begins with it. Letter case is
 * ignored and `ё` is read as `е`; a word is a run of letters and digits.
 */
object WordPrefix {
    private val separators = Regex("""[^\p{L}\p{N}]+""")

    /** Letter case ignored and `ё` read as `е` (FLOW-057). */
    fun fold(text: String): String = text.lowercase(Locale.ROOT).replace('ё', 'е')

    /** The words of [text], after [fold]. */
    fun words(text: String): List<String> = fold(text).split(separators).filter { it.isNotEmpty() }

    /** Whether, for every word [sought], one of [words] begins with it; both are already [fold]ed and split. */
    fun holdsEvery(words: List<String>, sought: List<String>): Boolean =
        sought.all { word -> words.any { it.startsWith(word) } }
}

/**
 * What a query finds among [items]: those one of whose names — given by [names] — matches every word of it
 * ([WordPrefix]). The names' words are worked out once, when the index is built, so that each query only compares
 * them; a query of no word finds every item. What is found keeps the order of [items].
 */
class SearchIndex<T>(private val items: List<T>, names: (T) -> List<String>) {
    private val indexed: List<Pair<T, List<List<String>>>> = run {
        // The same name — a dictionary entry's label — stands in many recipes; its words are worked out once.
        val wordsOf = HashMap<String, List<String>>()
        items.map { item -> item to names(item).map { wordsOf.getOrPut(it) { WordPrefix.words(it) } } }
    }

    fun search(query: String): List<T> {
        val sought = WordPrefix.words(query)
        if (sought.isEmpty()) return items
        return indexed.filter { (_, names) -> names.any { WordPrefix.holdsEvery(it, sought) } }.map { it.first }
    }
}
