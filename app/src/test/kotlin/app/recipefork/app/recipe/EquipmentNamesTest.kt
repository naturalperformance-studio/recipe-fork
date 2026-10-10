package app.recipefork.app.recipe

import app.recipefork.app.dictionary.DictionarySnapshot
import app.recipefork.app.dictionary.EquipmentEntryEntity
import app.recipefork.core.recipe.EquipmentItem
import app.recipefork.core.recipe.RecipeLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/**
 * Scenario 5 of v0.28: an item of equipment written as text before items named entries finds its entry by itself where
 * its whole text — trimmed, in any case — is an entry's label or synonym in either language; anything else stays free
 * text. An entry that leaves the dictionary still reads by its label in a recipe that names it.
 */
class EquipmentNamesTest {
    private val pan = EquipmentEntryEntity(10001, "Сковорода", "Frying pan", 10000, synonymsRu = listOf("сковородка"), synonymsEn = listOf("skillet"))
    private val oven = EquipmentEntryEntity(10202, "Духовка", "Oven", 10200, synonymsRu = listOf("духовой шкаф"))
    private val gone = EquipmentEntryEntity(10950, "Казан", "Kazan", 10000, removed = true)
    private val names = EquipmentNames(listOf(oven, pan, gone))

    @Test
    fun theWholeTextFindsAnEntryByItsLabelOrSynonymInEitherLanguage() {
        assertEquals(10001L, names.entryOf("Сковорода"))
        assertEquals(10001L, names.entryOf("Frying pan"))
        assertEquals(10001L, names.entryOf("сковородка"))
        assertEquals(10001L, names.entryOf("skillet"))
        assertEquals(10202L, names.entryOf("Духовой шкаф"))
        // Trimmed and in any case.
        assertEquals(10001L, names.entryOf("  FRYING PAN \t"))
        assertEquals(10202L, names.entryOf("духовка"))
    }

    @Test
    fun anythingElseFindsNothing() {
        assertNull(names.entryOf("Frying pan 26 cm"))
        assertNull(names.entryOf("Frying"))
        assertNull(names.entryOf("Frying  pan"))
        assertNull(names.entryOf(""))
        assertNull(names.entryOf("   "))
        // A removed entry is offered to nobody, and found by nothing.
        assertNull(names.entryOf("Kazan"))
        assertNull(EquipmentNames(emptyList()).entryOf("Oven"))
    }

    // A label is preferred to another entry's synonym, and of two entries the lower id.
    @Test
    fun aLabelWinsOverASynonymAndTheLowerIdOverTheHigher() {
        val tin = EquipmentEntryEntity(10100, "Форма", "Tin", synonymsEn = listOf("oven"))
        assertEquals(10202L, EquipmentNames(listOf(tin, oven)).entryOf("Oven"))
        val other = EquipmentEntryEntity(10003, "Сотейник", "Saute pan", synonymsEn = listOf("pan"))
        val another = EquipmentEntryEntity(10002, "Ковш", "Ladle pot", synonymsEn = listOf("pan"))
        assertEquals(10002L, EquipmentNames(listOf(other, another)).entryOf("pan"))
    }

    // Every label of the shipped dictionary finds its own entry.
    @Test
    fun everyShippedLabelFindsItsEntry() {
        val shipped = DictionarySnapshot.parse(File("src/main/assets/${DictionarySnapshot.ASSET}").readText()).equipment.map(EquipmentEntryEntity::of)
        val names = EquipmentNames(shipped)
        shipped.filterNot { it.removed }.forEach { entry ->
            assertEquals(entry.labelRu, entry.id, names.entryOf(entry.labelRu))
            assertEquals(entry.labelEn, entry.id, names.entryOf(entry.labelEn))
        }
    }

    // An item reads as its entry's label in the interface language and its qualifier — a removed entry's too — or as
    // its free text.
    @Test
    fun anItemReadsAsItsLabelAndQualifierEvenOnceItsEntryIsRemoved() {
        val entries = listOf(pan, gone).associateBy { it.id }
        assertEquals("Frying pan 26 cm", EquipmentNames.shown(EquipmentItem.entry(10001, "26 cm"), entries, RecipeLanguage.EN))
        assertEquals("Сковорода 26 cm", EquipmentNames.shown(EquipmentItem.entry(10001, "26 cm"), entries, RecipeLanguage.RU))
        assertEquals("Kazan 8 l", EquipmentNames.shown(EquipmentItem.entry(10950, "8 l"), entries, RecipeLanguage.EN))
        assertEquals("Казан", EquipmentNames.shown(EquipmentItem.entry(10950), entries, RecipeLanguage.RU))
        assertEquals("Grandma's pan", EquipmentNames.shown(EquipmentItem.free("Grandma's pan"), entries, RecipeLanguage.EN))
        // An entry the device does not hold at all reads as its qualifier.
        assertEquals("8 l", EquipmentNames.shown(EquipmentItem.entry(10999, "8 l"), entries, RecipeLanguage.EN))
    }
}
