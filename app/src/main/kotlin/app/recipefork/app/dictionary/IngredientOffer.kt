package app.recipefork.app.dictionary

import app.recipefork.core.scaling.InterfaceLanguage
import app.recipefork.core.search.Inflection

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
         * картофель, "томаты" помидор (FLOW-023); an entry is offered once, whichever of its names find it. The leaves of every
         * parent so offered are offered too.
         * The offer is in two groups — first what one of its own names, a label or a synonym in either language, begins
         * with the whole typed text, the shorter such name first ("chick" offers chicken before chicken wings); then the
         * rest, in the alphabetical order of the label shown — and a parent always comes before its own leaves: a leaf
         * that would come before its parent comes just after it instead.
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
            val found = search.find(typed, dictionary, language, stillHeld)
            return IngredientOffer(found.entries, found.freeText, found.exact, found.synonyms)
        }

        private val search = DictionarySearch<IngredientEntryEntity>()

        /** Letter case ignored, `ё` read as `е` (FLOW-057) and Latin diacritics dropped. */
        fun fold(text: String): String = DictionarySearch.fold(text)
    }
}
