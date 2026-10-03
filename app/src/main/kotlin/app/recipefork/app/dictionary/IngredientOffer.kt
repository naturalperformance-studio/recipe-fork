package app.recipefork.app.dictionary

import app.recipefork.core.scaling.InterfaceLanguage
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
    /** The first offered entry whose label, in either language, is exactly the typed text; null when none is. */
    val exact: IngredientEntryEntity? = null,
) {
    companion object {
        /**
         * The offer for [typed] from [dictionary], shown in the interface [language].
         *
         * An entry is offered when one of its labels, in either language, holds for every typed word a word that
         * begins with it, after [fold]. The leaves of every parent so offered are offered too, immediately before
         * it, in the alphabetical order of their labels; a leaf matched on its own stands there as well, once.
         * The offer is in two groups — first what begins with the whole typed text in either language, then the
         * rest — each in the alphabetical order of the label shown, a parent placed by its own label.
         */
        fun of(typed: String, dictionary: List<IngredientEntryEntity>, language: InterfaceLanguage): IngredientOffer {
            val sought = fold(typed).trim()
            val typedWords = words(sought)
            if (typedWords.isEmpty()) return IngredientOffer(emptyList(), freeText = null)

            val matched = dictionary.filter { entry -> entry.labels().any { holdsEveryWord(it, typedWords) } }
            val leaves = dictionary.filter { it.parentId != null }.groupBy { it.parentId }
            val collator = Collator.getInstance(if (language == InterfaceLanguage.RUSSIAN) Locale("ru") else Locale.ENGLISH)
            val byLabel = compareBy<IngredientEntryEntity, String>(collator) { it.label(language) }.thenBy { it.id }

            val matchedIds = matched.map { it.id }.toSet()
            // Each offered parent heads a block of its leaves; a leaf whose parent is offered lives in that block.
            val heads = matched.filter { it.parentId == null || it.parentId !in matchedIds }
            val (first, rest) = heads.partition { head -> head.labels().any { fold(it).startsWith(sought) } }
            val offered = (first.sortedWith(byLabel) + rest.sortedWith(byLabel)).flatMap { head ->
                leaves[head.id].orEmpty().sortedWith(byLabel) + head
            }.distinctBy { it.id }

            val exact = offered.firstOrNull { entry -> entry.labels().any { fold(it).trim() == sought } }
            return IngredientOffer(offered, freeText = if (exact == null) typed.trim() else null, exact = exact)
        }

        /** Letter case ignored and `ё` read as `е` (FLOW-057). */
        fun fold(text: String): String = WordPrefix.fold(text)

        private fun IngredientEntryEntity.labels() = listOf(labelRu, labelEn)

        private fun words(folded: String): List<String> = WordPrefix.words(folded)

        private fun holdsEveryWord(label: String, typedWords: List<String>): Boolean =
            WordPrefix.holdsEvery(WordPrefix.words(label), typedWords)
    }
}
