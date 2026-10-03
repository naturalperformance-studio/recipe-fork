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
) {
    companion object {
        /**
         * The offer for [typed] from [dictionary], shown in the interface [language].
         *
         * An entry is offered when one of its labels, in either language, holds for every typed word a word that
         * begins with it, or the same word in another grammatical form ("яйца" for "яйцо"), after [fold] — the words of
         * its parent's label in that language counting as its own: "сыр моцарелла" and "mozzarella cheese" find
         * моцарелла, a kind of сыр. The leaves of every parent so offered are offered too, immediately before it, in the alphabetical order of their labels; a leaf matched on its own stands there as well, once.
         * The offer is in two groups — first what begins with the whole typed text in either language, then the
         * rest — each in the alphabetical order of the label shown, a parent placed by its own label.
         *
         * The exact match is the label's words, as [fold]ed, in any order: "сливочное масло" is "масло сливочное"; failing
         * that, the label's words each in another form ([Inflection.same]): "яйца" is "яйцо", "onions" "onion"; failing
         * that, with its parent's words: "сыр моцарелла" is моцарелла. It heads
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
            val offerable = dictionary.filter { !it.removed || it.id in stillHeld }

            val byId = dictionary.associateBy { it.id }
            val matched = offerable.filter { entry ->
                entry.labels().any { holdsEveryWord(it, typedWords) } ||
                    entry.parentId != null && entry.lineage(byId).any { holdsEveryWord(it, typedWords) }
            }
            val leaves = offerable.filter { it.parentId != null }.groupBy { it.parentId }
            val collator = Collator.getInstance(if (language == InterfaceLanguage.RUSSIAN) Locale("ru") else Locale.ENGLISH)
            val byLabel = compareBy<IngredientEntryEntity, String>(collator) { it.label(language) }.thenBy { it.id }

            val matchedIds = matched.map { it.id }.toSet()
            // Each offered parent heads a block of its leaves; a leaf whose parent is offered lives in that block.
            val heads = matched.filter { it.parentId == null || it.parentId !in matchedIds }
            val (first, rest) = heads.partition { head -> head.labels().any { fold(it).startsWith(sought) } }
            val offered = (first.sortedWith(byLabel) + rest.sortedWith(byLabel)).flatMap { head ->
                leaves[head.id].orEmpty().sortedWith(byLabel) + head
            }.distinctBy { it.id }

            val soughtWords = typedWords.sorted()
            val exact = offered.firstOrNull { entry -> entry.labels().any { WordPrefix.Words.of(it).words.sorted() == soughtWords } }
                ?: offered.firstOrNull { entry -> entry.labels().any { sameWords(WordPrefix.Words.of(it).words, typedWords) } }
                ?: offered.firstOrNull { entry ->
                    entry.parentId != null && entry.lineage(byId).any { sameWords(WordPrefix.Words.of(it).words.distinct(), typedWords) }
                }
            // The exact match heads the offer, before its own leaves: what was typed is what is offered first.
            val ordered = if (exact == null) offered else listOf(exact) + (offered - exact)
            return IngredientOffer(ordered, freeText = if (exact == null) typed.trim() else null, exact = exact)
        }

        /** Letter case ignored, `ё` read as `е` (FLOW-057) and Latin diacritics dropped. */
        fun fold(text: String): String = WordPrefix.fold(text)

        private fun IngredientEntryEntity.labels() = listOf(labelRu, labelEn)

        /** Each label of this entry followed by its parent's label in the same language: "моцарелла сыр". */
        private fun IngredientEntryEntity.lineage(byId: Map<Long, IngredientEntryEntity>): List<String> {
            val parent = parentId?.let(byId::get) ?: return emptyList()
            return listOf("$labelRu ${parent.labelRu}", "$labelEn ${parent.labelEn}")
        }

        private fun words(folded: String): List<String> = WordPrefix.words(folded)

        /** Whether [label] and [typed] hold the same words, each in one form or another ([Inflection.same]), in any order. */
        private fun sameWords(label: List<String>, typed: List<String>): Boolean {
            if (label.size != typed.size) return false
            val left = label.toMutableList()
            return typed.all { word -> left.firstOrNull { Inflection.same(word, it) }?.let(left::remove) == true }
        }

        private fun holdsEveryWord(label: String, typedWords: List<String>): Boolean =
            WordPrefix.holdsEvery(WordPrefix.Words.of(label), typedWords)
    }
}
