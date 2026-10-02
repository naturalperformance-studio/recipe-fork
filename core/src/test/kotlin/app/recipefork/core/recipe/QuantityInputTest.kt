package app.recipefork.core.recipe

import app.recipefork.core.recipe.QuantityUnit.CUP
import app.recipefork.core.recipe.QuantityUnit.GRAM
import app.recipefork.core.recipe.QuantityUnit.KILOGRAM
import app.recipefork.core.recipe.QuantityUnit.LITRE
import app.recipefork.core.recipe.QuantityUnit.MILLILITRE
import app.recipefork.core.recipe.QuantityUnit.PIECE
import app.recipefork.core.recipe.QuantityUnit.PINCH
import app.recipefork.core.recipe.QuantityUnit.TABLESPOON
import app.recipefork.core.recipe.QuantityUnit.TEASPOON
import app.recipefork.core.recipe.QuantityUnit.TONNE
import app.recipefork.core.recipe.QuantityUnit.TO_TASTE
import app.recipefork.core.scaling.AuthoredQuantity
import app.recipefork.core.scaling.InterfaceLanguage
import app.recipefork.core.scaling.InterfaceLanguage.ENGLISH
import app.recipefork.core.scaling.InterfaceLanguage.RUSSIAN
import app.recipefork.core.scaling.Rational
import app.recipefork.core.scaling.UnitLabels
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun r(n: Long, d: Long = 1) = Rational.of(n, d)

private const val DIVISIBLE = true
private const val INDIVISIBLE = false

/** The value [typed] reads as, or null when it reads as no number. */
private fun valueOf(typed: String): Rational? = (QuantityInput.read(typed) as? QuantityReading.Value)?.value

/** True when the quantity field holds [typed] in [unit]: it reads as a number on the unit's grid (DOM-017). */
private fun held(typed: String, unit: QuantityUnit, divisible: Boolean? = null): Boolean =
    valueOf(typed)?.let { QuantityInput.holds(it, unit, divisible) } == true

/** What the field shows once it has read [typed]: the value as it writes it, then the unit's label (L10N-009). */
private fun shown(typed: String, unit: QuantityUnit, language: InterfaceLanguage): String =
    "${QuantityInput.write(valueOf(typed)!!, unit, language)} ${UnitLabels.of(unit, language)}"

private fun row(unit: QuantityUnit, divisible: Boolean? = null) =
    Ingredient(0, null, freeTextName = "x", unitType = unit.type, unit = unit, quantity = null, divisible = divisible)

class Dom019DecimalMarkTest {
    // DOM-019 (1)
    @Test
    fun `in Russian 1,5 кг and 1_5 кг are both read as 1_5 kg and shown as 1,5 кг`() {
        listOf("1,5", "1.5").forEach { typed ->
            assertEquals(r(3, 2), valueOf(typed), typed)
            assertEquals("1,5 кг", shown(typed, KILOGRAM, RUSSIAN), typed)
        }
    }

    // DOM-019 (2)
    @Test
    fun `in English 1_5 kg and 1,5 kg are both read as 1_5 kg and shown as 1_5 kg`() {
        listOf("1.5", "1,5").forEach { typed ->
            assertEquals(r(3, 2), valueOf(typed), typed)
            assertEquals("1.5 kg", shown(typed, KILOGRAM, ENGLISH), typed)
        }
    }

    // DOM-019 (3)
    @Test
    fun `in English 1,500 kg is read as 1_5 kg and not as 1500 kg`() {
        assertEquals(r(3, 2), valueOf("1,500"))
        assertEquals("1.5 kg", shown("1,500", KILOGRAM, ENGLISH))
        assertTrue(held("1,500", KILOGRAM))
    }

    // DOM-019 (4)
    @Test
    fun `1,2345 кг and 1_2345 kg are read as 1_2345 kg and not held, being no whole number of grams`() {
        listOf("1,2345", "1.2345").forEach { typed ->
            assertEquals(r(12345, 10000), valueOf(typed), typed)
            assertFalse(held(typed, KILOGRAM), typed)
        }
    }

    // DOM-019 (5)
    @Test
    fun `a space, two decimal marks, or a comma and a point are separators the field does not hold`() {
        listOf("1 500", "1.500,5", "1,5,0", "1 500", "1 500", "1'500", "1.5.0").forEach { typed ->
            assertEquals(QuantityReading.Separators, QuantityInput.read(typed), typed)
            val problem = QuantityInput.problem(row(GRAM).withTypedQuantity(typed))
            assertEquals(QuantityProblem.Separators, problem, typed)
        }
    }

    @Test
    fun `text that is no number is read as none, and spaces around a number are ignored`() {
        listOf("abc", "500 г", "-", "1/0", "½½", "1,5½").forEach { typed ->
            assertEquals(QuantityReading.NotANumber, QuantityInput.read(typed), typed)
        }
        assertEquals(QuantityReading.Empty, QuantityInput.read("  "))
        assertEquals(r(500), valueOf(" 500 "))
        assertEquals(r(-1), valueOf("-1"))
        assertEquals(r(1, 3), valueOf("1/3"))
        assertEquals(r(4, 3), valueOf("1⅓"))
    }
}

class Dom017QuantityOnTheGridTest {
    // DOM-017 (1)
    @Test
    fun `500 г, 1,5 кг, ⅓ ч_ л_, 1½ шт of a divisible piece and 3 шт of an indivisible one are held`() {
        assertTrue(held("500", GRAM))
        assertTrue(held("1,5", KILOGRAM))
        assertTrue(held("⅓", TEASPOON))
        assertTrue(held("1½", PIECE, DIVISIBLE))
        assertTrue(held("3", PIECE, INDIVISIBLE))
        // 3 pieces lie on the divisible grid as well.
        assertTrue(held("3", PIECE, DIVISIBLE))
    }

    // DOM-017 (2)
    @Test
    fun `0, a negative number, 7,5 г, 1,2345 кг, 0,3 ч_ л_ and, of an indivisible piece, 2,5 шт and ½ шт are not held`() {
        listOf(GRAM, KILOGRAM, TEASPOON, PINCH).forEach { unit ->
            assertFalse(held("0", unit), "0 $unit")
            assertFalse(held("-1", unit), "-1 $unit")
        }
        listOf(DIVISIBLE, INDIVISIBLE).forEach { divisible ->
            assertFalse(held("0", PIECE, divisible), "0 pieces, divisible $divisible")
            assertFalse(held("-2", PIECE, divisible), "-2 pieces, divisible $divisible")
        }
        assertFalse(held("7,5", GRAM))
        assertFalse(held("1,2345", KILOGRAM))
        assertFalse(held("0,3", TEASPOON))
        assertFalse(held("2,5", PIECE, INDIVISIBLE))
        assertFalse(held("½", PIECE, INDIVISIBLE))
    }

    // DOM-017 (3)
    @Test
    fun `a fraction grid value typed with a decimal mark is held and written as the grid writes it`() {
        assertTrue(held("2,5", PIECE, DIVISIBLE))
        assertEquals("2½ шт", shown("2,5", PIECE, RUSSIAN))
        assertTrue(held("0,5", TEASPOON))
        assertEquals("½ ч. л.", shown("0,5", TEASPOON, RUSSIAN))
        assertTrue(held("0,25", TEASPOON))
        assertEquals("¼ ч. л.", shown("0,25", TEASPOON, RUSSIAN))
        // ⅓ and ⅔ have no decimal form on the grid, and the error names the fractions the field accepts.
        listOf("0,33", "0,66").forEach { typed ->
            assertFalse(held(typed, TEASPOON), typed)
            val problem = QuantityInput.problem(row(TEASPOON).withTypedQuantity(typed))
            assertEquals(QuantityProblem.NotAccepted(AcceptedValues.KITCHEN_FRACTIONS), problem, typed)
        }
    }

    // DOM-017 (4)
    @Test
    fun `the floor of each grid is held`() {
        assertTrue(held("1", GRAM))
        assertTrue(held("¼", TEASPOON))
        assertTrue(held("½", PIECE, DIVISIBLE))
        assertTrue(held("1", PIECE, INDIVISIBLE))
        assertTrue(held("1", PIECE, DIVISIBLE))
        // Below each floor is not.
        assertFalse(held("0,5", GRAM))
        assertFalse(held("0,2", TEASPOON))
        assertFalse(held("0,25", PIECE, DIVISIBLE))
        assertFalse(held("0,5", PIECE, INDIVISIBLE))
    }

    // DOM-017 (5)
    @Test
    fun `a number however large that lies on the grid is held`() {
        assertTrue(held("999999", GRAM))
        assertTrue(held("123456789000", GRAM))
        assertTrue(held("1000000000", PIECE, INDIVISIBLE))
        assertTrue(held("1000000000,5", PIECE, DIVISIBLE))
        assertTrue(held("123456789⅔", CUP))
        assertTrue(held("7,5", TONNE))
        // From 1000 kg the grid holds whole kilograms only.
        assertFalse(held("1000,5", KILOGRAM))
        assertTrue(held("1000,5", LITRE) == false && held("999,5", LITRE))
    }

    @Test
    fun `a piece whose divisibility is not yet given is held to the divisible grid`() {
        assertTrue(held("½", PIECE, null))
        assertTrue(held("2", PIECE, null))
        assertFalse(held("¼", PIECE, null))
        assertEquals(AcceptedValues.HALVES, QuantityInput.accepts(PIECE, null))
    }

    @Test
    fun `the values each unit accepts are named by its unit type and dimension`() {
        assertEquals(AcceptedValues.WHOLE_GRAMS, QuantityInput.accepts(KILOGRAM, null))
        assertEquals(AcceptedValues.WHOLE_MILLILITRES, QuantityInput.accepts(MILLILITRE, null))
        assertEquals(AcceptedValues.WHOLE_PIECES, QuantityInput.accepts(PIECE, INDIVISIBLE))
        assertEquals(AcceptedValues.HALVES, QuantityInput.accepts(PIECE, DIVISIBLE))
        assertEquals(AcceptedValues.KITCHEN_FRACTIONS, QuantityInput.accepts(TABLESPOON, null))
        assertEquals(AcceptedValues.KITCHEN_FRACTIONS, QuantityInput.accepts(PINCH, null))
        assertNull(QuantityInput.accepts(TO_TASTE, null))
    }

    // DOM-017 (6)
    @Test
    fun `changing the unit or the divisibility so the value leaves the grid keeps the value and marks the row`() {
        val halfTablespoon = row(TABLESPOON).withTypedQuantity("½")
        assertNull(QuantityInput.problem(halfTablespoon))
        val inGrams = halfTablespoon.withUnit(GRAM)
        assertEquals(r(1, 2), inGrams.quantity)
        assertEquals(QuantityProblem.NotAccepted(AcceptedValues.WHOLE_GRAMS), QuantityInput.problem(inGrams))
        // Back to a unit whose grid holds it: the mark is gone.
        assertNull(QuantityInput.problem(inGrams.withUnit(TEASPOON)))

        val oneAndAHalf = row(PIECE, DIVISIBLE).withTypedQuantity("1½")
        assertNull(QuantityInput.problem(oneAndAHalf))
        val indivisible = oneAndAHalf.copy(divisible = INDIVISIBLE)
        assertEquals(r(3, 2), indivisible.quantity)
        assertEquals(QuantityProblem.NotAccepted(AcceptedValues.WHOLE_PIECES), QuantityInput.problem(indivisible))
    }

    @Test
    fun `what the field does not hold is kept as typed or as its value, and marked`() {
        val zero = row(GRAM).withTypedQuantity("0")
        assertEquals("0", zero.enteredQuantity)
        assertNull(zero.quantity)
        assertEquals(QuantityProblem.NotAccepted(AcceptedValues.WHOLE_GRAMS), QuantityInput.problem(zero))
        assertEquals("0", QuantityInput.fieldText(zero, RUSSIAN))

        val offGrid = row(GRAM).withTypedQuantity("7,5")
        assertEquals(r(15, 2), offGrid.quantity)
        assertNull(offGrid.enteredQuantity)
        assertEquals("7,5", QuantityInput.fieldText(offGrid, RUSSIAN))

        val words = row(GRAM).withTypedQuantity("a little")
        assertEquals("a little", words.enteredQuantity)
        assertEquals(QuantityProblem.NotAccepted(AcceptedValues.WHOLE_GRAMS), QuantityInput.problem(words))

        val cleared = words.withTypedQuantity("")
        assertNull(cleared.enteredQuantity)
        assertNull(cleared.quantity)
        assertNull(QuantityInput.problem(cleared))
    }
}

class QuantityWrittenBackTest {
    @Test
    fun `every value the field writes reads back as the same value`() {
        val values = listOf(r(500), r(3, 2), r(1, 3), r(4, 3), r(1, 2), r(15, 2), r(12345, 10000), r(1, 7), r(22, 7), r(1_000_000))
        QuantityUnit.entries.filter { it.type.carriesQuantity }.forEach { unit ->
            InterfaceLanguage.entries.forEach { language ->
                values.forEach { value ->
                    val written = QuantityInput.write(value, unit, language)
                    assertEquals(QuantityReading.Value(value), QuantityInput.read(written), "$value $unit $language: $written")
                }
            }
        }
    }

    @Test
    fun `a value is written without grouping, as a decimal in exact units and in glyphs on a fraction grid`() {
        assertEquals("1500", QuantityInput.write(r(1500), GRAM, RUSSIAN))
        assertEquals("1,25", QuantityInput.write(r(5, 4), KILOGRAM, RUSSIAN))
        assertEquals("1.25", QuantityInput.write(r(5, 4), KILOGRAM, ENGLISH))
        assertEquals("1¼", QuantityInput.write(r(5, 4), CUP, ENGLISH))
        assertEquals("0,3", QuantityInput.write(r(3, 10), TEASPOON, RUSSIAN))
        assertEquals("⅓", QuantityInput.write(r(1, 3), GRAM, RUSSIAN))
        assertEquals("1/7", QuantityInput.write(r(1, 7), GRAM, RUSSIAN))
    }
}

class Dom016UnitTypeFollowsTheUnitTest {
    // DOM-016 (2), DOM-002 (1), FLOW-023 (8, 9)
    @Test
    fun `the unit type follows from the unit, and to taste holds no unit and no quantity`() {
        val start = row(GRAM).withTypedQuantity("500")
        assertEquals(UnitType.HOUSEHOLD, start.withUnit(CUP).unitType)
        assertEquals(UnitType.TASTE_MEASURED, start.withUnit(PINCH).unitType)
        assertEquals(UnitType.PIECE, start.withUnit(PIECE).unitType)
        QuantityUnit.entries.forEach { unit -> assertEquals(unit.type, start.withUnit(unit).unitType, "$unit") }

        val toTaste = start.withUnit(TO_TASTE)
        assertEquals(UnitType.TASTE_UNMEASURED, toTaste.unitType)
        assertNull(toTaste.unit)
        assertNull(toTaste.quantity)
        assertNull(toTaste.enteredQuantity)
        assertNull(QuantityInput.problem(toTaste))
        assertEquals(AuthoredQuantity(TO_TASTE, null), toTaste.authoredQuantity)

        // Another unit afterwards starts the quantity empty.
        val back = toTaste.withUnit(GRAM)
        assertEquals(GRAM, back.unit)
        assertNull(back.quantity)
        assertNull(back.enteredQuantity)
    }

    @Test
    fun `divisibility belongs to a piece only`() {
        val piece = row(PIECE, DIVISIBLE).withTypedQuantity("2")
        assertEquals(true, piece.divisible)
        assertNull(piece.withUnit(GRAM).divisible)
        assertNull(piece.withUnit(GRAM).withUnit(PIECE).divisible)
    }
}

class IncompleteRowTest {
    @Test
    fun `a row scaling can read has an authored quantity, and an incomplete or off-grid one has none`() {
        assertEquals(AuthoredQuantity(GRAM, r(500)), row(GRAM).withTypedQuantity("500").authoredQuantity)
        assertEquals(AuthoredQuantity(PIECE, r(3, 2), true), row(PIECE, DIVISIBLE).withTypedQuantity("1,5").authoredQuantity)
        assertTrue(row(GRAM).withTypedQuantity("500").isComplete)

        assertNull(row(GRAM).authoredQuantity, "no quantity")
        assertNull(row(GRAM).withTypedQuantity("7,5").authoredQuantity, "off its grid")
        assertNull(row(GRAM).withTypedQuantity("1 500").authoredQuantity, "separators")
        assertNull(row(PIECE).withTypedQuantity("2").authoredQuantity, "divisibility not given")
        assertNull(row(PIECE, INDIVISIBLE).withTypedQuantity("½").authoredQuantity, "off the indivisible grid")
        assertFalse(row(GRAM).withTypedQuantity("500").copy(freeTextName = " ").isComplete, "no name")
        assertFalse(row(GRAM).withTypedQuantity("500").copy(dictionaryEntry = 1).isComplete, "named twice")
        assertTrue(row(GRAM).withTypedQuantity("500").copy(dictionaryEntry = 1, freeTextName = null).isComplete)
    }
}

class Dom015ServingCountTest {
    // DOM-015 (1, 2)
    @Test
    fun `the serving count field holds a whole number of one or more and nothing else`() {
        listOf("1" to 1, "4" to 4, "1000" to 1000, " 12 " to 12).forEach { (typed, count) ->
            assertEquals(ServingCountReading.Count(count), ServingCountInput.read(typed), typed)
        }
        listOf("0", "-1", "2.5", "2,5", "½", "1/2", "four", "1 000", "99999999999").forEach { typed ->
            assertEquals(ServingCountReading.Refused, ServingCountInput.read(typed), typed)
        }
    }

    // DOM-015 (3): an empty field holds no value, and no zero stands in for it.
    @Test
    fun `an empty field holds no value`() {
        assertEquals(ServingCountReading.Empty, ServingCountInput.read(""))
        assertEquals(ServingCountReading.Empty, ServingCountInput.read("  "))
    }
}
