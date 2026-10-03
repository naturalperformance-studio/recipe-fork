package app.recipefork.core.recipe

import app.recipefork.core.api.DictionariesDto
import app.recipefork.core.api.IngredientEntryDto
import app.recipefork.core.scaling.Rational
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * decisions/20260920-divisibility-belongs-to-the-ingredient.md: the entry decides divisibility when it says; DOM-011 and
 * CAT-009: a factor per unit code, an exact unit through its base multiple; and the encoding of an entry that sets none
 * of the new fields is what it was before them.
 */
class EntryDivisibilityTest {
    private val chicken = IngredientEntryDto(1, "курица", "chicken", divisible = true, factors = mapOf("g" to 1.0, "pc" to 1500.0))
    private val wings = IngredientEntryDto(2, "крылышки", "chicken wings", 1, divisible = false, factors = mapOf("g" to 1.0, "pc" to 80.0))
    private val meat = IngredientEntryDto(3, "мясо", "meat", factors = mapOf("g" to 1.0))

    private fun piece(entry: Long?, divisible: Boolean?, freeText: String? = null) = Ingredient(
        position = 0,
        dictionaryEntry = entry,
        freeTextName = freeText,
        unitType = UnitType.PIECE,
        unit = QuantityUnit.PIECE,
        quantity = Rational.of(3, 2),
        divisible = divisible,
    )

    @Test
    fun theEntrysFlagWinsOverWhatTheRowHolds() {
        assertEquals(false, effectiveDivisible(piece(2, null), wings))
        assertEquals(false, effectiveDivisible(piece(2, true), wings))
        assertEquals(true, effectiveDivisible(piece(1, null), chicken))
        assertEquals(true, effectiveDivisible(piece(1, false), chicken))
    }

    @Test
    fun anEntryThatSaysNothingLeavesTheRowsOwnValue() {
        assertNull(effectiveDivisible(piece(3, null), meat))
        assertEquals(false, effectiveDivisible(piece(3, false), meat))
        assertEquals(true, effectiveDivisible(piece(3, true), meat))
        // An entry not held (no dictionary yet) leaves it to the row as well.
        assertEquals(true, effectiveDivisible(piece(3, true), null as IngredientEntryDto?))
    }

    @Test
    fun freeTextIsAlwaysTheAuthorsAnswer() {
        assertNull(effectiveDivisible(piece(null, null, "яйцо"), null as IngredientEntryDto?))
        assertEquals(false, effectiveDivisible(piece(null, false, "яйцо"), false))
        assertEquals(true, effectiveDivisible(piece(null, true, "яйцо"), false))
        // Another row's entry is not read.
        assertEquals(true, effectiveDivisible(piece(1, true), wings))
    }

    @Test
    fun aFactorIsReadByUnitCodeAndAnExactUnitThroughItsBaseMultiple() {
        assertEquals(1500.0, comparisonFactor(chicken.factors, QuantityUnit.PIECE))
        assertEquals(1.0, comparisonFactor(chicken.factors, QuantityUnit.GRAM))
        assertEquals(1000.0, comparisonFactor(chicken.factors, QuantityUnit.KILOGRAM))
        assertEquals(1_000_000.0, comparisonFactor(chicken.factors, QuantityUnit.TONNE))
        assertNull(comparisonFactor(chicken.factors, QuantityUnit.MILLILITRE))
        assertNull(comparisonFactor(chicken.factors, QuantityUnit.LITRE))
        assertNull(comparisonFactor(chicken.factors, QuantityUnit.TABLESPOON))
        assertNull(comparisonFactor(chicken.factors, QuantityUnit.TO_TASTE))
        val volume = mapOf("ml" to 2.0, "tsp" to 5.0, "tbsp" to 15.0, "cup" to 250.0, "pinch" to 0.5)
        assertEquals(2000.0, comparisonFactor(volume, QuantityUnit.LITRE))
        assertEquals(2_000_000.0, comparisonFactor(volume, QuantityUnit.CUBIC_METRE))
        assertEquals(5.0, comparisonFactor(volume, QuantityUnit.TEASPOON))
        assertEquals(15.0, comparisonFactor(volume, QuantityUnit.TABLESPOON))
        assertEquals(250.0, comparisonFactor(volume, QuantityUnit.CUP))
        assertEquals(0.5, comparisonFactor(volume, QuantityUnit.PINCH))
    }

    @Test
    fun factorsNameKnownUnitsWithNumbersAboveZero() {
        assertTrue(IngredientEntryDto.validFactors(chicken.factors))
        assertTrue(IngredientEntryDto.validFactors(emptyMap()))
        assertFalse(IngredientEntryDto.validFactors(mapOf("kg" to 1.0)))
        assertFalse(IngredientEntryDto.validFactors(mapOf("g" to 0.0)))
        assertFalse(IngredientEntryDto.validFactors(mapOf("g" to -1.0)))
        assertFalse(IngredientEntryDto.validFactors(mapOf("g" to Double.NaN)))
        assertFalse(IngredientEntryDto.validFactors(mapOf("g" to Double.POSITIVE_INFINITY)))
    }

    @Test
    fun anEntrySettingNoneOfTheNewFieldsEncodesAsBefore() {
        val plain = DictionariesDto("", listOf(IngredientEntryDto(4, "фарш", "mince"), IngredientEntryDto(2, "крылышки", "chicken wings", 1)), emptyList())
        assertEquals(
            """{"version":"","ingredients":[{"id":4,"labelRu":"фарш","labelEn":"mince"},""" +
                """{"id":2,"labelRu":"крылышки","labelEn":"chicken wings","parentId":1}],"tags":[]}""",
            Json.encodeToString(DictionariesDto.serializer(), plain),
        )
        val removed = IngredientEntryDto(5, "говядина", "beef", divisible = false, factors = mapOf("g" to 1.0), removed = true)
        val encoded = Json.encodeToString(IngredientEntryDto.serializer(), removed)
        assertEquals(
            """{"id":5,"labelRu":"говядина","labelEn":"beef","divisible":false,"factors":{"g":1.0},"removed":true}""",
            encoded,
        )
        assertEquals(removed, Json.decodeFromString(IngredientEntryDto.serializer(), encoded))
    }
}
