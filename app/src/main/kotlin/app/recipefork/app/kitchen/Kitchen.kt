package app.recipefork.app.kitchen

import app.recipefork.app.dictionary.EquipmentEntryEntity
import app.recipefork.app.dictionary.label
import app.recipefork.core.scaling.InterfaceLanguage
import java.text.Collator
import java.util.Locale

/** A group of the equipment dictionary as My kitchen lists it: the group, and its items in the order they are listed. */
data class KitchenGroup(val group: EquipmentEntryEntity, val items: List<EquipmentEntryEntity>)

/**
 * The reader's kitchen: the [held] ids of the equipment it has, read against the equipment [dictionary]. What the card
 * marks and the book filters by asks [lacks].
 *
 * An entry counts for the kitchen when it is in the dictionary, not removed, and not a consumable — parchment, foil
 * and the like stand in the group [CONSUMABLES], used up rather than had. A group counts too: the kitchen has it when
 * it has any item of it.
 */
class Kitchen(val held: Set<Long>, dictionary: List<EquipmentEntryEntity>) {
    private val byId = dictionary.associateBy { it.id }
    /** Each group's items that count: an item since removed — ticked or not — has no say in whether its group is had. */
    private val itemsOf = dictionary.filter { it.parentId != null && counts(it) }.groupBy { it.parentId }

    /** How many items the kitchen has that count — those My kitchen lists ticked. */
    val count: Int = held.count { id -> byId[id]?.let { it.parentId != null && counts(it) } == true }

    /** No item that counts is ticked: the reader has not said what the kitchen has, so it lacks nothing. */
    val isEmpty: Boolean get() = count == 0

    /**
     * Whether the kitchen lacks the entry [entryId]: never while it [isEmpty], never for an entry that does not count,
     * and otherwise when it is not ticked — a group when none of its items that count is.
     */
    fun lacks(entryId: Long): Boolean {
        if (isEmpty) return false
        val entry = byId[entryId]?.takeIf(::counts) ?: return false
        return if (entry.parentId == null) itemsOf[entry.id].orEmpty().none { it.id in held } else entry.id !in held
    }

    companion object {
        /** The group «Расходники» / "Consumables" of the equipment dictionary; its id is permanent. */
        const val CONSUMABLES: Long = 10700L

        /** Whether [entry] counts for a kitchen: not removed, and neither the consumables nor one of them. */
        fun counts(entry: EquipmentEntryEntity): Boolean =
            !entry.removed && entry.id != CONSUMABLES && entry.parentId != CONSUMABLES

        /**
         * The groups My kitchen lists from [dictionary], in the dictionary's order, each with the items that count in
         * it by their labels in the interface [language]; a group with no such item is not listed.
         */
        fun groups(dictionary: List<EquipmentEntryEntity>, language: InterfaceLanguage): List<KitchenGroup> {
            val collator = Collator.getInstance(if (language == InterfaceLanguage.RUSSIAN) Locale.forLanguageTag("ru") else Locale.ENGLISH)
            val byLabel = compareBy<EquipmentEntryEntity, String>(collator) { it.label(language) }.thenBy { it.id }
            val items = dictionary.filter { it.parentId != null && counts(it) }.groupBy { it.parentId }
            return dictionary.filter { it.parentId == null && counts(it) }.sortedBy { it.id }.mapNotNull { group ->
                items[group.id]?.sortedWith(byLabel)?.let { KitchenGroup(group, it) }
            }
        }
    }
}
