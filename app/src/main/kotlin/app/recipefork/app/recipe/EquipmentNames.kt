package app.recipefork.app.recipe

import app.recipefork.app.dictionary.DictionaryEntry
import app.recipefork.core.file.EntryLabels
import app.recipefork.core.recipe.EquipmentItem
import app.recipefork.core.recipe.RecipeLanguage
import java.util.Locale

/**
 * How an item of equipment written as text before items named entries — in a book of version 6, in a recipe file of
 * format 4 — finds its entry: its whole text, trimmed and in any case, is the label or a synonym of an entry of the
 * equipment dictionary in either language. A label is preferred to a synonym, and of two entries the lower id; a removed
 * entry, offered to nobody, is found by nothing. Text that is no entry's name finds none and stays free text.
 */
class EquipmentNames(entries: List<DictionaryEntry>) {
    private val byName: Map<String, Long> = HashMap<String, Long>().apply {
        val live = entries.filterNot { it.removed }.sortedBy { it.id }
        // Labels first, so that a synonym never takes a name a label holds.
        live.forEach { entry -> listOf(entry.labelRu, entry.labelEn).forEach { putIfAbsent(key(it), entry.id) } }
        live.forEach { entry -> (entry.synonymsRu + entry.synonymsEn).forEach { putIfAbsent(key(it), entry.id) } }
        remove("")
    }

    /** The entry whose name [text] is, or null where it is none's. */
    fun entryOf(text: String): Long? = byName[key(text)]

    private fun key(text: String) = text.trim().lowercase(Locale.ROOT)

    companion object {
        /** The labels of [entry], as a recipe file carries them. */
        fun labelsOf(entry: DictionaryEntry) = EntryLabels(entry.labelRu, entry.labelEn)

        /**
         * The text [item] reads as in [language] (L10N-001): its entry's label, from [entries] — a removed entry's too,
         * so that a recipe naming it keeps its name — followed by its qualifier; or its free text.
         */
        fun shown(item: EquipmentItem, entries: Map<Long, DictionaryEntry>, language: RecipeLanguage): String =
            item.shown(item.dictionaryEntry?.let(entries::get)?.let { if (language == RecipeLanguage.RU) it.labelRu else it.labelEn })
    }
}
