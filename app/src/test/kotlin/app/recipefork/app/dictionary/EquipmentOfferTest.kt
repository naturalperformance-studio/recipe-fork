package app.recipefork.app.dictionary

import app.recipefork.core.scaling.InterfaceLanguage.ENGLISH
import app.recipefork.core.scaling.InterfaceLanguage.RUSSIAN
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * T-201: the equipment dictionary is offered by search in either language, synonyms included, as the ingredients are —
 * against the shipped snapshot, on the JVM alone.
 */
class EquipmentOfferTest {
    /** Unit tests of `:app` run with the module directory as the working directory. */
    private val shipped = DictionarySnapshot.parse(File("src/main/assets/${DictionarySnapshot.ASSET}").readText())
        .equipment.map(EquipmentEntryEntity::of)

    private fun byRu(label: String) = shipped.single { it.labelRu == label }

    @Test
    fun aWordBeginningInEitherLanguageOffersTheEntry() {
        val pan = byRu("Сковорода")
        assertTrue(EquipmentOffer.of("сков", shipped, RUSSIAN).entries.contains(pan))
        assertTrue(EquipmentOffer.of("fry", shipped, ENGLISH).entries.contains(pan))
        // Found in English, shown in the interface language.
        assertTrue(EquipmentOffer.of("blend", shipped, RUSSIAN).entries.map { it.label(RUSSIAN) }.containsAll(listOf("Блендер", "Погружной блендер")))
        assertEquals("Frying pan", pan.label(ENGLISH))
    }

    @Test
    fun aSynonymFindsItsEntryAndIsShownBesideIt() {
        val pan = byRu("Сковорода")
        val offer = EquipmentOffer.of("сковородка", shipped, RUSSIAN)
        assertEquals(pan, offer.exact)
        assertEquals(pan, offer.entries.first())
        assertNull(offer.freeText)
        val skillet = EquipmentOffer.of("skillet", shipped, ENGLISH)
        assertEquals(pan, skillet.exact)
        assertEquals("skillet", skillet.synonyms[pan.id])
        assertEquals(byRu("Мясорубка"), EquipmentOffer.of("mincer", shipped, RUSSIAN).exact)
    }

    // Scenario 5: a line whose text is an entry's name finds its entry by itself — whatever its letter case.
    @Test
    fun everyLabelTypedAsItIsFindsItsEntryExactly() {
        shipped.filterNot { it.removed }.forEach { entry ->
            listOf(entry.labelRu, entry.labelEn, entry.labelRu.lowercase(), entry.labelEn.uppercase()).forEach { typed ->
                assertEquals(typed, entry, EquipmentOffer.of(typed, shipped, RUSSIAN).exact)
            }
        }
    }

    @Test
    fun textNoEntryNamesIsOfferedAsFreeText() {
        val offer = EquipmentOffer.of("  самогонный аппарат ", shipped, RUSSIAN)
        assertEquals(emptyList<EquipmentEntryEntity>(), offer.entries)
        assertEquals("самогонный аппарат", offer.freeText)
        assertNull(offer.exact)
        assertEquals(EquipmentOffer(emptyList(), null), EquipmentOffer.of("   ", shipped, RUSSIAN))
    }

    @Test
    fun aGroupIsOfferedWithItsItemsBeforeIt() {
        val appliances = byRu("Техника")
        val offered = EquipmentOffer.of("Техника", shipped, RUSSIAN).entries
        assertEquals(appliances, offered.first())
        assertTrue(offered.containsAll(shipped.filter { it.parentId == appliances.id }))
    }

    @Test
    fun aRemovedEntryIsOfferedOnlyWhileStillHeld() {
        val kazan = EquipmentEntryEntity(10002, "Казан", "Kazan", 10000, removed = true)
        val dictionary = listOf(EquipmentEntryEntity(10000, "Посуда для плиты", "Stovetop cookware"), kazan)
        assertFalse(EquipmentOffer.of("казан", dictionary, RUSSIAN).entries.contains(kazan))
        assertNull(EquipmentOffer.of("казан", dictionary, RUSSIAN).exact)
        assertEquals(kazan, EquipmentOffer.of("казан", dictionary, RUSSIAN, stillHeld = setOf(kazan.id)).exact)
    }
}
