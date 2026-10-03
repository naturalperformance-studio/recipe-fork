package app.recipefork.app.dictionary

import app.recipefork.core.scaling.InterfaceLanguage
import app.recipefork.core.search.Inflection
import app.recipefork.core.search.WordPrefix
import java.text.Collator
import java.util.Locale

/** The label of this entry in the interface [language], whichever language a search matched it in (L10N-003). */
fun IngredientEntryEntity.label(language: InterfaceLanguage): String =
    if (language == InterfaceLanguage.RUSSIAN) labelRu else labelEn

/**
 * What the ingredient field offers for what the author typed (FLOW-057): the dictionary [entries] in the order they
 * are offered, each once, and — where the typed text is not exactly the label of one of them — the typed text to add
 * as free text (FLOW-022 (1)). Nothing is offered while nothing but spaces is typed.
 */
data class IngredientOffer(
    val entries: List<IngredientEntryEntity>,
    /** The typed text, without the spaces around it, when adding it as free text is offered; null when it is not. */
    val freeText: String?,
    /**
     * The entry whose label, in either language, is exactly the typed text — or, when none is, the typed text in another
     * grammatical form — offered first; null when none is.
     */
    val exact: IngredientEntryEntity? = null,
    /**
     * By entry id, the synonym an offered entry was found by when none of its labels found it: shown beside its label,
     * muted («картофель · картошка»). An entry a label found has none here.
     */
    val synonyms: Map<Long, String> = emptyMap(),
) {
    companion object {
        /**
         * The offer for [typed] from [dictionary], shown in the interface [language].
         *
         * An entry is offered when one of its labels, in either language, holds for every typed word a word that
         * begins with it, or the same word in another grammatical form ("яйца" for "яйцо"), after [fold] — the words of
         * its parent's label in that language counting as its own: "сыр моцарелла" and "mozzarella cheese" find
         * моцарелла, a kind of сыр. A synonym, in either language, counts as a label for all of it: "картошка" finds
         * картофель, "томаты" помидор (FLOW-023); an entry is offered once, whichever of its names find it. The leaves of every parent so offered are offered too, immediately before it, in the alphabetical order of their labels; a leaf matched on its own stands there as well, once.
         * The offer is in two groups — first what begins with the whole typed text in either language, then the
         * rest — each in the alphabetical order of the label shown, a parent placed by its own label.
         *
         * The exact match is the label's words, as [fold]ed, in any order: "сливочное масло" is "масло сливочное"; failing
         * that, the label's words each in another form ([Inflection.same]): "яйца" is "яйцо", "onions" "onion"; failing
         * that, with its parent's words: "сыр моцарелла" is моцарелла. A synonym is matched exactly as a label is, a label
         * before a synonym at each step: "томат" is помидор. It heads
         * the offer, before its leaves.
         *
         * An entry removed upstream is never offered, nor matched exactly (FLOW-001 (7), DOM-009 (5)), except one of
         * [stillHeld] — what the library's filter keeps offering while a recipe in its view holds it.
         */
        fun of(
            typed: String,
            dictionary: List<IngredientEntryEntity>,
            language: InterfaceLanguage,
            stillHeld: Set<Long> = emptySet(),
        ): IngredientOffer {
            val sought = fold(typed).trim()
            val typedWords = words(sought)
            if (typedWords.isEmpty()) return IngredientOffer(emptyList(), freeText = null)
            val query = WordPrefix.Sought(typedWords)
            val index = Index.of(dictionary)
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
            val byLabel = compareBy<IngredientEntryEntity, String>(collator) { it.label(language) }.thenBy { it.id }

            val matchedIds = matched.map { it.entry.id }.toSet()
            // Each offered parent heads a block of its leaves; a leaf whose parent is offered lives in that block.
            val heads = matched.filter { it.entry.parentId == null || it.entry.parentId !in matchedIds }
            val (first, rest) = heads.partition { head -> head.names.any { it.folded.startsWith(sought) } }
            val offered = (first.map { it.entry }.sortedWith(byLabel) + rest.map { it.entry }.sortedWith(byLabel)).flatMap { head ->
                leaves[head.id].orEmpty().sortedWith(byLabel) + head
            }.distinctBy { it.id }

            val soughtWords = typedWords.sorted()
            fun ownExactly(entry: IngredientEntryEntity, synonym: Boolean) =
                index.of(entry).names.any { it.isSynonym == synonym && it.sorted == soughtWords }
            fun ownInAnotherForm(entry: IngredientEntryEntity, synonym: Boolean) =
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
            return IngredientOffer(ordered, freeText = if (exact == null) typed.trim() else null, exact = exact, synonyms = synonyms)
        }

        /** Letter case ignored, `ё` read as `е` (FLOW-057) and Latin diacritics dropped. */
        fun fold(text: String): String = WordPrefix.fold(text)

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
        private class Indexed(val entry: IngredientEntryEntity, val names: List<Name>, val lineage: List<Name>)

        /**
         * The names of a dictionary's entries, worked out once for every keystroke over the same dictionary: the field
         * is given the same list until the dictionary is read anew.
         */
        private class Index(val dictionary: List<IngredientEntryEntity>) {
            val entries: List<Indexed>
            private val byId: Map<Long, Indexed>

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

            fun of(entry: IngredientEntryEntity): Indexed = byId.getValue(entry.id)

            companion object {
                @Volatile
                private var last: Index? = null

                fun of(dictionary: List<IngredientEntryEntity>): Index =
                    last?.takeIf { it.dictionary === dictionary } ?: Index(dictionary).also { last = it }
            }
        }

        /** The entry's label in one language, then its synonyms in it, each with the synonym it is or null for the label. */
        private fun IngredientEntryEntity.namesIn(russian: Boolean): List<Pair<String, String?>> =
            listOf((if (russian) labelRu else labelEn) to null) + (if (russian) synonymsRu else synonymsEn).map { it to it }

        private fun words(folded: String): List<String> = WordPrefix.words(folded)

        /** Whether [label] and [typed] hold the same words, each in one form or another ([Inflection.same]), in any order. */
        private fun sameWords(label: List<String>, typed: List<String>): Boolean {
            if (label.size != typed.size) return false
            val left = label.toMutableList()
            return typed.all { word -> left.firstOrNull { Inflection.same(word, it) }?.let(left::remove) == true }
        }
    }
}
