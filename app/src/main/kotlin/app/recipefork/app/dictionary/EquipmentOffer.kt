package app.recipefork.app.dictionary

import app.recipefork.core.scaling.InterfaceLanguage

/**
 * What an equipment field offers for what the author typed: the equipment dictionary [entries] in the order they are
 * offered, each once, and — where the typed text is not exactly the name of one of them — the typed text to keep as
 * free text. Nothing is offered while nothing but spaces is typed.
 */
data class EquipmentOffer(
    val entries: List<EquipmentEntryEntity>,
    /** The typed text, without the spaces around it, when keeping it as free text is offered; null when it is not. */
    val freeText: String?,
    /** The entry whose label or synonym, in either language and in any grammatical form, is the typed text; null when none is. */
    val exact: EquipmentEntryEntity? = null,
    /** By entry id, the synonym an offered entry was found by when none of its labels found it. */
    val synonyms: Map<Long, String> = emptyMap(),
) {
    companion object {
        /**
         * The offer for [typed] from the equipment [dictionary], shown in the interface [language]: found and ordered
         * exactly as [IngredientOffer.of] finds and orders ingredients — by label or synonym in either language, a
         * group's items offered with it — and a removed entry offered only when it is one of [stillHeld].
         */
        fun of(
            typed: String,
            dictionary: List<EquipmentEntryEntity>,
            language: InterfaceLanguage,
            stillHeld: Set<Long> = emptySet(),
        ): EquipmentOffer {
            val found = search.find(typed, dictionary, language, stillHeld)
            return EquipmentOffer(found.entries, found.freeText, found.exact, found.synonyms)
        }

        private val search = DictionarySearch<EquipmentEntryEntity>()
    }
}
