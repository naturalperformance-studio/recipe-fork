package app.recipefork.core.search

import app.recipefork.core.scaling.InterfaceLanguage
import java.text.Collator
import java.util.Locale

/**
 * What a dictionary search reads of an entry, whichever dictionary holds it — ingredients or equipment: its labels and
 * synonyms in both languages, its parent (null at the top level) and whether it is [removed] upstream.
 */
interface DictionaryEntry {
    val id: Long
    val labelRu: String
    val labelEn: String
    val parentId: Long?
    val removed: Boolean
    val synonymsRu: List<String>
    val synonymsEn: List<String>
}

/**
 * The matching of names to the entries of one [dictionary], its names worked out once: the offer of a dictionary
 * field ([offer]) and the binding of a name read from a recipe's text to an entry ([bind]).
 *
 * An entry is matched by a name exactly — the exact-match cascade — when the name holds the same words as one of
 * the entry's labels, in any order; failing that, as one of its synonyms; failing those, a label's words each in
 * another form ([Inflection.same]), then a synonym's; failing all, one of its own names with its parent's after it.
 */
class DictionaryMatch<T : DictionaryEntry>(val dictionary: List<T>) {
    /**
     * What [offer] gives: the [entries] offered, in order; the [exact] match heading them, or the typed text as
     * [freeText] when there is none; and, by entry id, the synonym an entry found by none of its labels was found by.
     */
    class Found<T>(val entries: List<T>, val freeText: String?, val exact: T?, val synonyms: Map<Long, String>)

    /** What [bind] gives: the [entry] a name is bound to and the words of the name, as written, it did not use. */
    data class Bound<T>(val entry: T, val unused: List<String>)

    private val entries: List<Indexed<T>>
    private val byId: Map<Long, Indexed<T>>

    /** The names of the offerable entries by every form of every word they hold: where [bind] looks first. */
    private val byForm: Map<String, List<Pair<Indexed<T>, Name>>>

    init {
        val entities = dictionary.associateBy { it.id }
        entries = dictionary.mapIndexed { position, entry ->
            val parent = entry.parentId?.let(entities::get)
            val lineage = if (parent == null) emptyList() else listOf(true, false).flatMap { russian ->
                entry.namesIn(russian).flatMap { (own, synonym) ->
                    parent.namesIn(russian).map { (parentName, _) -> Name("$own $parentName", synonym, lineage = true) }
                }
            }
            val names = (entry.namesIn(true) + entry.namesIn(false)).map { (text, synonym) -> Name(text, synonym) }
            Indexed(entry, position, names, lineage)
        }
        byId = entries.associateBy { it.entry.id }
        val forms = HashMap<String, MutableList<Pair<Indexed<T>, Name>>>()
        entries.filter { !it.entry.removed }.forEach { indexed ->
            (indexed.names + indexed.lineage).forEach { name ->
                name.distinct.flatMapTo(HashSet()) { Inflection.forms(it) }
                    .forEach { forms.getOrPut(it) { mutableListOf() } += indexed to name }
            }
        }
        byForm = forms
    }

    private fun of(entry: T): Indexed<T> = byId.getValue(entry.id)

    /**
     * The offer for [typed], shown in the interface [language]. An entry is offered when one of its names, labels or
     * synonyms in either language, holds every typed word ([WordPrefix]), failing them one with its parent's after it;
     * a parent so offered brings its leaves. What one of its own names begins with the whole typed text comes first,
     * the shorter such name first; then the rest, alphabetically by the label shown; a parent always before its own
     * leaves. The exact match heads the offer. A removed entry is neither offered nor matched, except one of [stillHeld].
     */
    fun offer(typed: String, language: InterfaceLanguage, stillHeld: Set<Long>): Found<T> {
        val sought = fold(typed).trim()
        val typedWords = WordPrefix.words(sought)
        if (typedWords.isEmpty()) return Found(emptyList(), freeText = null, exact = null, synonyms = emptyMap())
        val query = WordPrefix.Sought(typedWords)
        val offerable = entries.filter { !it.entry.removed || it.entry.id in stillHeld }

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
        val begun = brought.associate { entry -> entry.id to of(entry).names.filter { it.folded.startsWith(sought) }.minOfOrNull { it.folded.length } }
        val sorted = brought.sortedWith(compareBy<T> { begun[it.id] == null }.thenBy { begun[it.id] ?: 0 }.then(byLabel))
        val offered = parentsFirst(sorted, offeredIds = sorted.map { it.id }.toSet())

        val soughtWords = typedWords.sorted()
        fun ownExactly(entry: T, synonym: Boolean) =
            of(entry).names.any { it.isSynonym == synonym && it.sorted == soughtWords }
        fun ownInAnotherForm(entry: T, synonym: Boolean) =
            of(entry).names.any { it.isSynonym == synonym && sameWords(it.words.words, typedWords) }
        val exact = offered.firstOrNull { ownExactly(it, synonym = false) }
            ?: offered.firstOrNull { ownExactly(it, synonym = true) }
            ?: offered.firstOrNull { ownInAnotherForm(it, synonym = false) }
            ?: offered.firstOrNull { ownInAnotherForm(it, synonym = true) }
            ?: offered.firstOrNull { entry -> of(entry).lineage.any { sameWords(it.distinct, typedWords) } }
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
     * The entry a free [name] — as a recipe's text writes it, inflected, perhaps with words around it ("cups of
     * flour", "греческого йогурта") — is bound to, or null when no entry is: the one some of whose names' words the
     * name holds every one of, each in some form, by the exact-match cascade. The entry whose name uses the most of
     * the name's words wins ("greek yogurt" is Greek yogurt, not yogurt); among those, the earlier step of the cascade,
     * then the name found earlier in the text, then the earlier entry of the dictionary. A removed entry is never bound.
     */
    fun bind(name: String): Bound<T>? {
        val written = WordPrefix.words(name).let { folded -> splitWritten(name).takeIf { it.size == folded.size } ?: folded }
        val words = written.map(::fold)
        if (words.isEmpty()) return null
        val candidates = LinkedHashSet<Pair<Indexed<T>, Name>>()
        words.forEach { word -> Inflection.forms(word).forEach { form -> byForm[form]?.let(candidates::addAll) } }
        val best = candidates.mapNotNull { (indexed, entryName) ->
            val used = usedBy(entryName.distinct, words) ?: return@mapNotNull null
            val literal = used.all { (label, typed) -> words[typed] == label }
            val step = when {
                entryName.lineage -> 4
                literal -> if (entryName.isSynonym) 1 else 0
                else -> if (entryName.isSynonym) 3 else 2
            }
            Candidate(indexed, used.map { it.second }.toSet(), step)
        }.minWithOrNull(
            compareByDescending<Candidate<T>> { it.used.size }.thenBy { it.step }.thenBy { it.used.min() }
                .thenBy { it.indexed.position },
        ) ?: return null
        return Bound(best.indexed.entry, written.filterIndexed { i, _ -> i !in best.used })
    }

    private class Candidate<T>(val indexed: Indexed<T>, val used: Set<Int>, val step: Int)

    /**
     * For every word of [label], the index of a distinct word of [words] that is it in some form — the same word
     * first — as (label word, index); null when one of the label's words is missing.
     */
    private fun usedBy(label: List<String>, words: List<String>): List<Pair<String, Int>>? {
        val taken = HashSet<Int>()
        return label.map { word ->
            val i = words.indices.firstOrNull { it !in taken && words[it] == word }
                ?: words.indices.firstOrNull { it !in taken && Inflection.same(words[it], word) }
                ?: return null
            taken += i
            word to i
        }
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
     * or, in its [lineage], it followed by a parent's name.
     */
    private class Name(text: String, val synonym: String?, val lineage: Boolean = false) {
        val folded: String = fold(text)
        val words: WordPrefix.Words = WordPrefix.Words(WordPrefix.words(text))
        val sorted: List<String> = words.words.sorted()
        val distinct: List<String> = words.words.distinct()
        val isSynonym: Boolean get() = synonym != null
    }

    /** An entry with its own [names] — labels and synonyms in both languages — and those with its parent's after them. */
    private class Indexed<T>(val entry: T, val position: Int, val names: List<Name>, val lineage: List<Name>)

    companion object {
        /** Letter case ignored, `ё` read as `е` (FLOW-057) and Latin diacritics dropped. */
        fun fold(text: String): String = WordPrefix.fold(text)

        private val separators = Regex("""[^\p{L}\p{N}]+""")

        /** The words of [text] as written, unfolded. */
        private fun splitWritten(text: String): List<String> = text.split(separators).filter { it.isNotEmpty() }

        private fun DictionaryEntry.label(language: InterfaceLanguage): String =
            if (language == InterfaceLanguage.RUSSIAN) labelRu else labelEn

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
