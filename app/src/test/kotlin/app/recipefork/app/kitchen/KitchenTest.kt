package app.recipefork.app.kitchen

import app.recipefork.app.dictionary.EquipmentEntryEntity
import app.recipefork.core.scaling.InterfaceLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T-204: what the kitchen lacks — the one rule the card's marks and the book's filter read — and what My kitchen lists.
 */
class KitchenTest {
    // Lacking: nothing while the kitchen is empty; never what does not count; otherwise whatever is not ticked.
    @Test
    fun anEmptyKitchenLacksNothing() {
        val kitchen = Kitchen(emptySet(), KITCHEN_DICTIONARY)
        assertTrue(kitchen.isEmpty)
        KITCHEN_DICTIONARY.forEach { assertFalse("${it.labelEn}", kitchen.lacks(it.id)) }
    }

    @Test
    fun aKitchenLacksWhatCountsAndIsNotTicked() {
        val kitchen = Kitchen(setOf(FRYING_PAN), KITCHEN_DICTIONARY)
        assertFalse(kitchen.lacks(FRYING_PAN))
        assertTrue(kitchen.lacks(SAUCEPAN))
        assertTrue(kitchen.lacks(SCALE))
    }

    @Test
    fun whatDoesNotCountIsNeverLacking() {
        val kitchen = Kitchen(setOf(FRYING_PAN), KITCHEN_DICTIONARY)
        // Consumables, their group, a removed entry, an id the dictionary does not hold.
        listOf(PARCHMENT, FOIL, Kitchen.CONSUMABLES, REMOVED_POT, 99_999L).forEach { assertFalse("$it", kitchen.lacks(it)) }
        listOf(PARCHMENT, Kitchen.CONSUMABLES, REMOVED_POT).forEach { id ->
            assertFalse("$id", Kitchen.counts(KITCHEN_DICTIONARY.single { it.id == id }))
        }
        assertTrue(Kitchen.counts(KITCHEN_DICTIONARY.single { it.id == FRYING_PAN }))
    }

    // T-317: known to have — never while the kitchen is empty, never what does not count; otherwise what it does not lack.
    @Test
    fun aKitchenHasOnlyWhatCountsAndIsTicked() {
        val empty = Kitchen(setOf(PARCHMENT), KITCHEN_DICTIONARY)
        KITCHEN_DICTIONARY.forEach { assertFalse("${it.labelEn}", empty.has(it.id)) }
        val kitchen = Kitchen(setOf(FRYING_PAN), KITCHEN_DICTIONARY)
        assertTrue(kitchen.has(FRYING_PAN))
        assertTrue("a group is had through any of its items", kitchen.has(STOVETOP))
        listOf(SAUCEPAN, SCALE, MEASURING).forEach { assertFalse("$it", kitchen.has(it)) }
        listOf(PARCHMENT, FOIL, Kitchen.CONSUMABLES, REMOVED_POT, 99_999L).forEach { assertFalse("$it", kitchen.has(it)) }
    }

    @Test
    fun aGroupIsHadWhenAnyOfItsItemsIs() {
        val kitchen = Kitchen(setOf(FRYING_PAN), KITCHEN_DICTIONARY)
        assertFalse(kitchen.lacks(STOVETOP))
        assertTrue(kitchen.lacks(MEASURING))
    }

    // T-205: a removed entry has no say at all — ticked, it does not make its group count as had.
    @Test
    fun aRemovedTickedItemDoesNotMakeItsGroupHad() {
        val kitchen = Kitchen(setOf(REMOVED_POT, SCALE), KITCHEN_DICTIONARY)
        assertFalse(kitchen.isEmpty)
        assertTrue("the pasta pot was removed: the stovetop group is not had", kitchen.lacks(STOVETOP))
        assertFalse(kitchen.lacks(MEASURING))
        assertFalse("a removed entry is never lacking", kitchen.lacks(REMOVED_POT))
    }

    // Only what counts is counted and makes the kitchen not empty: a ticked consumable or an entry since removed does not.
    @Test
    fun onlyWhatCountsIsCounted() {
        val stale = Kitchen(setOf(REMOVED_POT, PARCHMENT, 99_999L), KITCHEN_DICTIONARY)
        assertEquals(0, stale.count)
        assertTrue(stale.isEmpty)
        assertFalse(stale.lacks(SAUCEPAN))
        val two = Kitchen(setOf(REMOVED_POT, FRYING_PAN, SCALE), KITCHEN_DICTIONARY)
        assertEquals(2, two.count)
    }

    // My kitchen lists the groups in the dictionary's order, their items by label; neither consumables nor removed entries.
    @Test
    fun theListingLeavesOutConsumablesAndRemovedEntries() {
        val english = Kitchen.groups(KITCHEN_DICTIONARY, InterfaceLanguage.ENGLISH)
        assertEquals(listOf(STOVETOP, MEASURING), english.map { it.group.id })
        assertEquals(listOf("Frying pan", "Saucepan"), english[0].items.map { it.labelEn })
        val russian = Kitchen.groups(KITCHEN_DICTIONARY, InterfaceLanguage.RUSSIAN)
        assertEquals(listOf("Кастрюля", "Сковорода"), russian[0].items.map { it.labelRu })
    }
}

const val STOVETOP = 10000L
const val FRYING_PAN = 10001L
const val SAUCEPAN = 10006L
const val REMOVED_POT = 10022L
const val MEASURING = 10500L
const val SCALE = 10501L
const val PARCHMENT = 10119L
const val FOIL = 10120L

/** A small equipment dictionary: two groups that count, a removed item, and the consumables. */
val KITCHEN_DICTIONARY = listOf(
    EquipmentEntryEntity(STOVETOP, "Посуда для плиты", "Stovetop cookware"),
    EquipmentEntryEntity(FRYING_PAN, "Сковорода", "Frying pan", STOVETOP, synonymsRu = listOf("сковородка"), synonymsEn = listOf("skillet")),
    EquipmentEntryEntity(SAUCEPAN, "Кастрюля", "Saucepan", STOVETOP, synonymsEn = listOf("pot")),
    EquipmentEntryEntity(REMOVED_POT, "Кастрюля для пасты", "Pasta pot", STOVETOP, removed = true),
    EquipmentEntryEntity(MEASURING, "Мерная посуда", "Measuring tools"),
    EquipmentEntryEntity(SCALE, "Кухонные весы", "Kitchen scale", MEASURING, synonymsRu = listOf("весы")),
    EquipmentEntryEntity(Kitchen.CONSUMABLES, "Расходники", "Consumables"),
    EquipmentEntryEntity(PARCHMENT, "Пергамент", "Parchment paper", Kitchen.CONSUMABLES),
    EquipmentEntryEntity(FOIL, "Фольга", "Aluminium foil", Kitchen.CONSUMABLES),
)
