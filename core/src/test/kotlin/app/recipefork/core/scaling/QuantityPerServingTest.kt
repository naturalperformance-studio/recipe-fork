package app.recipefork.core.scaling

import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.UnitType
import kotlin.test.Test
import kotlin.test.assertEquals

/** CAT-002, CAT-007: how much of a filtered ingredient one serving holds, compared within one measure. */
class QuantityPerServingTest {
    private fun line(quantity: Long, unit: QuantityUnit) = MeasuredLine(unit.type, unit, Rational.of(quantity))

    @Test
    fun aQuantityIsTakenForOneServing() {
        assertEquals(mapOf(QuantityUnit.GRAM to Rational.of(125)), QuantityPerServing.of(listOf(line(500, QuantityUnit.GRAM)), 4))
        assertEquals(mapOf(QuantityUnit.GRAM to Rational.of(150)), QuantityPerServing.of(listOf(line(300, QuantityUnit.GRAM)), 2))
    }

    // CAT-007: several matched lines are summed; a kilogram is a thousand grams, a litre a thousand millilitres.
    @Test
    fun severalLinesOfOneMeasureAreSummed() {
        assertEquals(
            mapOf(QuantityUnit.GRAM to Rational.of(200)),
            QuantityPerServing.of(listOf(line(500, QuantityUnit.GRAM), line(300, QuantityUnit.GRAM)), 4),
        )
        assertEquals(
            mapOf(QuantityUnit.GRAM to Rational.of(375), QuantityUnit.MILLILITRE to Rational.of(250)),
            QuantityPerServing.of(listOf(line(1, QuantityUnit.KILOGRAM), line(500, QuantityUnit.GRAM), line(1, QuantityUnit.LITRE)), 4),
        )
    }

    // CAT-007: `to taste` carries no quantity and contributes nothing; nor does a line without a quantity.
    @Test
    fun aLineWithNoQuantityContributesNothing() {
        val lines = listOf(
            MeasuredLine(UnitType.TASTE_UNMEASURED, null, null),
            MeasuredLine(UnitType.TASTE_UNMEASURED, QuantityUnit.TO_TASTE, null),
            MeasuredLine(UnitType.EXACT, QuantityUnit.GRAM, null),
            line(2, QuantityUnit.PIECE),
        )
        assertEquals(mapOf(QuantityUnit.PIECE to Rational.of(1)), QuantityPerServing.of(lines, 2))
    }

    @Test
    fun aRecipeOfNoServingCountHasNoQuantityPerServing() {
        assertEquals(emptyMap(), QuantityPerServing.of(listOf(line(500, QuantityUnit.GRAM)), null))
    }

    // CAT-002: the second recipe first, because 150 g per serving exceeds 125 g; recipes of no comparable quantity last,
    // in the order chosen for them; equal quantities keep that order.
    @Test
    fun theMostPerServingComesFirstAndTheIncomparableLast() {
        val amounts = mapOf(
            "no quantity" to emptyMap(),
            "125 g" to mapOf(QuantityUnit.GRAM to Rational.of(125)),
            "in pieces" to mapOf(QuantityUnit.PIECE to Rational.of(9)),
            "150 g" to mapOf(QuantityUnit.GRAM to Rational.of(150)),
            "also 125 g" to mapOf(QuantityUnit.GRAM to Rational.of(125)),
        )
        assertEquals(
            listOf("150 g", "125 g", "also 125 g", "no quantity", "in pieces"),
            amounts.keys.toList().mostPerServingFirst { amounts.getValue(it) },
        )
    }

    // Without comparison factors the measure compared is the one most of the recipes hold, a mass first on a tie.
    @Test
    fun theMeasureComparedIsTheOneMostRecipesHold() {
        val amounts = mapOf(
            "100 g" to mapOf(QuantityUnit.GRAM to Rational.of(100)),
            "1 piece" to mapOf(QuantityUnit.PIECE to Rational.of(1)),
            "3 pieces" to mapOf(QuantityUnit.PIECE to Rational.of(3)),
        )
        assertEquals(listOf("3 pieces", "1 piece", "100 g"), amounts.keys.toList().mostPerServingFirst { amounts.getValue(it) })
        val tie = mapOf(
            "1 piece" to mapOf(QuantityUnit.PIECE to Rational.of(1)),
            "100 g" to mapOf(QuantityUnit.GRAM to Rational.of(100)),
        )
        assertEquals(listOf("100 g", "1 piece"), tie.keys.toList().mostPerServingFirst { tie.getValue(it) })
        assertEquals(listOf("a", "b"), listOf("a", "b").mostPerServingFirst { emptyMap() })
    }
}
