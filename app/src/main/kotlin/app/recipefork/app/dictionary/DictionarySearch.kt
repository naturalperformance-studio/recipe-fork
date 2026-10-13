package app.recipefork.app.dictionary

import app.recipefork.core.scaling.InterfaceLanguage
import app.recipefork.core.search.Inflection
import app.recipefork.core.search.WordPrefix
import java.text.Collator
import java.util.Locale

/** The label of this entry in the interface [language], whichever language a search matched it in (L10N-003). */
fun DictionaryEntry.label(language: InterfaceLanguage): String =
    if (language == InterfaceLanguage.RUSSIAN) labelRu else labelEn

/**
 * The search a dictionary field runs over the entries of one dictionary — ingredients ([IngredientOffer]) or equipment
 * ([EquipmentOffer]) — as [IngredientOffer.of] describes it. One search is held per dictionary, so that the names of
 * its entries are worked out once for every keystroke over the same list.
 */
internal class DictionarySearch<T : DictionaryEntry> {
    /** What [find] gives: as [IngredientOffer] holds it. */
    class Found<T>(val entries: List<T>, val freeText: String?, val exact: T?, val synonyms: Map<Long, String>)

    @Volatile
    private var last: Index<T>? = null

    private fun index(dictionary: List<T>): Index<T> =
        last?.takeIf { it.dictionary === dictionary } ?: Index(dictionary).also { last = it }

    fun find(typed: String, dictionary: List<T>, language: InterfaceLanguage, stillHeld: Set<Long>): Found<T> {
        val sought = fold(typed).trim()
        val typedWords = WordPrefix.words(sought)
        if (typedWords.isEmpty()) return Found(emptyList(), freeText = null, exact = null, synonyms = emptyMap())
        val query = WordPrefix.Sought(typedWords)
        val index = index(dictionary)
        val offerable = index.entries.filter { !it.entry.removed || it.entry.id in stillHeld }

        // What each entry is matched by: its own names, failing them those with its parent's after them.
        val matchedBy = HashMap<Long, List<Name>>()
        val matched = offerable.mapNotNull { indexed ->
            val names = indexed.names.filter { WordPrefix.holdsEvery(it.words, query) }
                .ifEmpty { indexed.lineage.filter { WordPrefix.holdsEvery(it.words, query) } }
            if (names.isEmpty()) null else indexed.also { matchedBy[it.entry.id] = names }
        }
        val leaves = offerable.filter { it.entry.parentId != null }.groupBy({ it.entry.parentId }, { it.entry })
        val collator = Collator.getInstance(if (language == InterfaceLanguage.RUSSIAN) Locale("ru") else Locale.ENGLISH)
        val byLabel = compareBy<T, String>(collator) { it.label(language) }.thenBy { it.id }

        // A matched parent brings its leaves; each entry is offered once, whichever way it came.
        val brought = matched.flatMap { listOf(it.entry) + if (it.entry.parentId == null) leaves[it.entry.id].orEmpty() else emptyList() }
            .distinctBy { it.id }
        // What begins with the whole typed text, by one of its own names, comes first, the shorter such name first; then
        // the rest, alphabetically by the label shown.
        val begun = brought.associate { entry -> entry.id to index.of(entry).names.filter { it.folded.startsWith(sought) }.minOfOrNull { it.folded.length } }
        val sorted = brought.sortedWith(compareBy<T> { begun[it.id] == null }.thenBy { begun[it.id] ?: 0 }.then(byLabel))
        val offered = parentsFirst(sorted, offeredIds = sorted.map { it.id }.toSet())

        val soughtWords = typedWords.sorted()
        fun ownExactly(entry: T, synonym: Boolean) =
            index.of(entry).names.any { it.isSynonym == synonym && it.sorted == soughtWords }
        fun ownInAnotherForm(entry: T, synonym: Boolean) =
            index.of(entry).names.any { it.isSynonym == synonym && sameWords(it.words.words, typedWords) }
        val exact = offered.firstOrNull { ownExactly(it, synonym = false) }
            ?: offered.firstOrNull { ownExactly(it, synonym = true) }
            ?: offered.firstOrNull { ownInAnotherForm(it, synonym = false) }
            ?: offered.firstOrNull { ownInAnotherForm(it, synonym = true) }
            ?: offered.firstOrNull { entry -> index.of(entry).lineage.any { sameWords(it.words.words.distinct(), typedWords) } }
        // The exact match heads the offer, before its own leaves: what was typed is what is offered first.
        val ordered = if (exact == null) offered else listOf(exact) + (offered - exact)
        // An entry found by one of its labels shows the label alone (FLOW-023); one found by a synonym only, the
        // synonym beside it — the one typed in some form, when several are found.
        val synonyms = buildMap {
            matchedBy.forEach { (id, names) ->
                if (names.any { !it.isSynonym }) return@forEach
                val shown = names.firstOrNull { sameWords(it.words.words, typedWords) } ?: names.first()
                put(id, shown.synonym!!)
            }
        }
        return Found(ordered, freeText = if (exact == null) typed.trim() else null, exact = exact, synonyms = synonyms)
    }

    /**
     * [sorted] with every leaf whose parent is offered too moved, where it came before that parent, to just after it —
     * the leaves so moved in the order they had — so that a parent is always offered before its own leaves.
     */
    private fun parentsFirst(sorted: List<T>, offeredIds: Set<Long>): List<T> {
        val placed = HashSet<Long>()
        val waiting = HashMap<Long, MutableList<T>>()
        val ordered = ArrayList<T>(sorted.size)
        fun place(entry: T) {
            ordered += entry
            placed += entry.id
            waiting.remove(entry.id)?.forEach(::place)
        }
        for (entry in sorted) {
            val parent = entry.parentId
            if (parent != null && parent in offeredIds && parent !in placed) waiting.getOrPut(parent) { mutableListOf() } += entry
            else place(entry)
        }
        return ordered
    }

    /**
     * A text an entry is found by, worked out once: one of its labels, or one of its [synonym]s — the synonym itself,
     * or it followed by a parent's name.
     */
    private class Name(text: String, val synonym: String?) {
        val folded: String = fold(text)
        val words: WordPrefix.Words = WordPrefix.Words(WordPrefix.words(text))
        val sorted: List<String> = words.words.sorted()
        val isSynonym: Boolean get() = synonym != null
    }

    /** An entry with its own [names] — labels and synonyms in both languages — and those with its parent's after them. */
    private class Indexed<T>(val entry: T, val names: List<Name>, val lineage: List<Name>)

    /**
     * The names of a dictionary's entries, worked out once for every keystroke over the same dictionary: the field
     * is given the same list until the dictionary is read anew.
     */
    private class Index<T : DictionaryEntry>(val dictionary: List<T>) {
        val entries: List<Indexed<T>>
        private val byId: Map<Long, Indexed<T>>

        init {
            val entities = dictionary.associateBy { it.id }
            entries = dictionary.map { entry ->
                val parent = entry.parentId?.let(entities::get)
                val lineage = if (parent == null) emptyList() else listOf(true, false).flatMap { russian ->
                    entry.namesIn(russian).flatMap { (own, synonym) ->
                        parent.namesIn(russian).map { (parentName, _) -> Name("$own $parentName", synonym) }
                    }
                }
                val names = (entry.namesIn(true) + entry.namesIn(false)).map { (text, synonym) -> Name(text, synonym) }
                Indexed(entry, names, lineage)
            }
            byId = entries.associateBy { it.entry.id }
        }

        fun of(entry: T): Indexed<T> = byId.getValue(entry.id)
    }

    companion object {
        /** Letter case ignored, `ё` read as `е` (FLOW-057) and Latin diacritics dropped. */
        fun fold(text: String): String = WordPrefix.fold(text)

        /** The entry's label in one language, then its synonyms in it, each with the synonym it is or null for the label. */
        private fun DictionaryEntry.namesIn(russian: Boolean): List<Pair<String, String?>> =
            listOf((if (russian) labelRu else labelEn) to null) + (if (russian) synonymsRu else synonymsEn).map { it to it }

        /** Whether [label] and [typed] hold the same words, each in one form or another ([Inflection.same]), in any order. */
        private fun sameWords(label: List<String>, typed: List<String>): Boolean {
            if (label.size != typed.size) return false
            val left = label.toMutableList()
            return typed.all { word -> left.firstOrNull { Inflection.same(word, it) }?.let(left::remove) == true }
        }
    }
}
