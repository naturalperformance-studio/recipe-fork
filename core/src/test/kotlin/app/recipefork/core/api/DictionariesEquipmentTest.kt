package app.recipefork.core.api

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * T-201: the dictionaries carry the equipment, and what was written without it — a snapshot, a server answer — still
 * reads, as dictionaries with no equipment, under the version it had.
 */
class DictionariesEquipmentTest {
    private val json = Json { ignoreUnknownKeys = true }

    private val withoutEquipment = DictionariesDto(
        "",
        listOf(IngredientEntryDto(1, "курица", "chicken")),
        listOf(TagDto(10, "итальянская", "Italian")),
    )

    @Test
    fun anAnswerWithoutEquipmentReadsAsNone() {
        val text = """{"version":"v","ingredients":[{"id":1,"labelRu":"курица","labelEn":"chicken"}],"tags":[]}"""
        val read = json.decodeFromString(DictionariesDto.serializer(), text)
        assertEquals(emptyList(), read.equipment)
        assertEquals(listOf(IngredientEntryDto(1, "курица", "chicken")), read.ingredients)
    }

    @Test
    fun noEquipmentIsNotEncodedSoTheDigestStaysWhatItWas() {
        val encoded = Json.encodeToString(DictionariesDto.serializer(), withoutEquipment)
        assertFalse("equipment" in encoded)
        // The digest as it was computed before the field existed: of exactly these three keys.
        val before = """{"version":"","ingredients":[{"id":1,"labelRu":"курица","labelEn":"chicken"}],""" +
            """"tags":[{"id":10,"labelRu":"итальянская","labelEn":"Italian"}]}"""
        assertEquals(before, encoded)
    }

    @Test
    fun equipmentIsCarriedAndCountsInTheVersion() {
        val pan = EquipmentEntryDto(10001, "Сковорода", "Frying pan", 10000, synonymsRu = listOf("сковородка"))
        val with = withoutEquipment.copy(equipment = listOf(EquipmentEntryDto(10000, "Посуда для плиты", "Stovetop cookware"), pan))
        val encoded = Json.encodeToString(DictionariesDto.serializer(), with)
        assertTrue("\"equipment\"" in encoded)
        assertEquals(with, json.decodeFromString(DictionariesDto.serializer(), encoded))
        assertTrue(with.digest() != withoutEquipment.digest())
        assertTrue(with.digest() != with.copy(equipment = listOf(pan.copy(labelEn = "Skillet"))).digest())
        // An item's defaults are omitted, as an ingredient's are.
        assertEquals(
            """{"id":10000,"labelRu":"Посуда для плиты","labelEn":"Stovetop cookware"}""",
            Json.encodeToString(EquipmentEntryDto.serializer(), with.equipment.first()),
        )
    }
}
