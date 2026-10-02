package app.recipefork.core.scaling

import app.recipefork.core.recipe.AcceptedValues
import app.recipefork.core.recipe.QuantityProblem
import app.recipefork.core.recipe.QuantityUnit.CUBIC_METRE
import app.recipefork.core.recipe.QuantityUnit.CUP
import app.recipefork.core.recipe.QuantityUnit.GRAM
import app.recipefork.core.recipe.QuantityUnit.KILOGRAM
import app.recipefork.core.recipe.QuantityUnit.LITRE
import app.recipefork.core.recipe.QuantityUnit.MILLILITRE
import app.recipefork.core.recipe.QuantityUnit.PIECE
import app.recipefork.core.recipe.QuantityUnit.PINCH
import app.recipefork.core.recipe.QuantityUnit.TABLESPOON
import app.recipefork.core.recipe.QuantityUnit.TO_TASTE
import app.recipefork.core.recipe.QuantityUnit.TONNE
import kotlin.test.Test
import kotlin.test.assertEquals

/** SCALE-018, DOM-019, DOM-017: what the fixing control holds, and in which units. */
class FixedAmountTest {
    private fun accepted(n: Long, d: Long = 1) = FixedAmountReading.Accepted(Rational.of(n, d))
    private val notGrams = FixedAmountReading.Refused(QuantityProblem.NotAccepted(AcceptedValues.WHOLE_GRAMS))

    @Test
    fun `an exact ingredient is fixed in any unit of its dimension and no other`() {
        assertEquals(listOf(GRAM, KILOGRAM, TONNE), FixedAmount.units(GRAM))
        assertEquals(listOf(GRAM, KILOGRAM, TONNE), FixedAmount.units(KILOGRAM))
        assertEquals(listOf(MILLILITRE, LITRE, CUBIC_METRE), FixedAmount.units(LITRE))
    }

    @Test
    fun `household, piece and pinch are fixed in their own unit alone, and to taste in none`() {
        assertEquals(listOf(TABLESPOON), FixedAmount.units(TABLESPOON))
        assertEquals(listOf(CUP), FixedAmount.units(CUP))
        assertEquals(listOf(PIECE), FixedAmount.units(PIECE))
        assertEquals(listOf(PINCH), FixedAmount.units(PINCH))
        assertEquals(emptyList(), FixedAmount.units(TO_TASTE))
    }

    @Test
    fun `300 g and 0,3 kg are both held, read with a comma or a point`() {
        assertEquals(accepted(300), FixedAmount.read("300", GRAM, null))
        assertEquals(accepted(3, 10), FixedAmount.read("0,3", KILOGRAM, null))
        assertEquals(accepted(3, 10), FixedAmount.read("0.3", KILOGRAM, null))
        assertEquals(accepted(10), FixedAmount.read("10", KILOGRAM, null))
    }

    @Test
    fun `zero, a negative number, an amount off the grid and no number are refused, saying what is held`() {
        assertEquals(notGrams, FixedAmount.read("0", GRAM, null))
        assertEquals(notGrams, FixedAmount.read("-5", GRAM, null))
        assertEquals(notGrams, FixedAmount.read("109,375", GRAM, null))
        assertEquals(notGrams, FixedAmount.read("0,0005", KILOGRAM, null))
        assertEquals(notGrams, FixedAmount.read("abc", GRAM, null))
    }

    @Test
    fun `a number with separators is refused as such`() {
        val separators = FixedAmountReading.Refused(QuantityProblem.Separators)
        assertEquals(separators, FixedAmount.read("1 000", GRAM, null))
        assertEquals(separators, FixedAmount.read("1,000.5", GRAM, null))
    }

    @Test
    fun `an indivisible piece holds whole pieces, a divisible one halves`() {
        val wholePieces = FixedAmountReading.Refused(QuantityProblem.NotAccepted(AcceptedValues.WHOLE_PIECES))
        assertEquals(wholePieces, FixedAmount.read("2,5", PIECE, false))
        assertEquals(wholePieces, FixedAmount.read("½", PIECE, false))
        assertEquals(accepted(2), FixedAmount.read("2", PIECE, false))
        assertEquals(accepted(5, 2), FixedAmount.read("2½", PIECE, true))
    }

    @Test
    fun `household holds the kitchen fractions only`() {
        assertEquals(accepted(1, 3), FixedAmount.read("1/3", TABLESPOON, null))
        assertEquals(
            FixedAmountReading.Refused(QuantityProblem.NotAccepted(AcceptedValues.KITCHEN_FRACTIONS)),
            FixedAmount.read("0,3", TABLESPOON, null),
        )
    }

    @Test
    fun `nothing typed is neither an amount nor a refusal`() {
        assertEquals(FixedAmountReading.Empty, FixedAmount.read("  ", GRAM, null))
    }
}
