package app.recipefork.app.dictionary

import app.recipefork.core.scaling.InterfaceLanguage
import app.recipefork.core.search.DictionaryMatch
import app.recipefork.core.search.WordPrefix

/** The label of this entry in the interface [language], whichever language a search matched it in (L10N-003). */
fun DictionaryEntry.label(language: InterfaceLanguage): String =
    if (language == InterfaceLanguage.RUSSIAN) labelRu else labelEn

/**
 * The search a dictionary field runs over the entries of one dictionary — ingredients ([IngredientOffer]) or equipment
 * ([EquipmentOffer]) — as [IngredientOffer.of] describes it and [DictionaryMatch.offer] finds it. One search is held per
 * dictionary, so that the names of its entries are worked out once for every keystroke over the same list.
 */
internal class DictionarySearch<T : DictionaryEntry> {
    @Volatile
    private var last: DictionaryMatch<T>? = null

    private fun match(dictionary: List<T>): DictionaryMatch<T> =
        last?.takeIf { it.dictionary === dictionary } ?: DictionaryMatch(dictionary).also { last = it }

    fun find(typed: String, dictionary: List<T>, language: InterfaceLanguage, stillHeld: Set<Long>): DictionaryMatch.Found<T> {
        // Nothing typed finds nothing, without working out the dictionary's names.
        if (WordPrefix.words(typed).isEmpty()) return DictionaryMatch.Found(emptyList(), null, null, emptyMap())
        return match(dictionary).offer(typed, language, stillHeld)
    }

    companion object {
        /** Letter case ignored, `ё` read as `е` (FLOW-057) and Latin diacritics dropped. */
        fun fold(text: String): String = DictionaryMatch.fold(text)
    }
}
