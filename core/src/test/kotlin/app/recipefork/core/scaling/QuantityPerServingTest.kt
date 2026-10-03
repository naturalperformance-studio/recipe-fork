package app.recipefork.core.scaling

import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.UnitType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** CAT-002, CAT-007, CAT-009: how much of a filtered ingredient one serving holds, compared through the entries' factors. */
class QuantityPerServingTest {
    private val chicken = mapOf("g" to 1.0, "pc" to 1500.0)
    private val wings = mapOf("g" to 1.0, "pc" to 80.0)
    private val thigh = mapOf("g" to 1.0)

    private fun line(quantity: Long, unit: QuantityUnit, factors: Map<String, Double>) =
        MeasuredLine(unit.type, unit, Rational.of(quantity), factors)

    private fun perServing(servings: Int?, vararg lines: MeasuredLine) = QuantityPerServing.of(lines.toList(), servings)

    // CAT-009 (1): 8 pieces of wings for 4 servings at 80 a piece is 160.
    @Test
    fun piecesAreComparedThroughTheirFactor() {
        assertEquals(160.0, perServing(4, line(8, QuantityUnit.PIECE, wings)))
    }

    // CAT-009 (2): 500 g of thigh for 4 servings at 1 a gram is 125, so two wings a serving come above it.
    @Test
    fun gramsAreComparedThroughTheirFactor() {
        assertEquals(125.0, perServing(4, line(500, QuantityUnit.GRAM, thigh)))
        val amounts = mapOf("500 g thigh" to perServing(4, line(500, QuantityUnit.GRAM, thigh)), "8 wings" to perServing(4, line(8, QuantityUnit.PIECE, wings)))
        assertEquals(listOf("8 wings", "500 g thigh"), amounts.keys.toList().byQuantityPerServing { amounts.getValue(it) })
    }

    // CAT-009 (3): a whole carcass at 1500 a piece comes before 5 g of thigh.
    @Test
    fun aWholeCarcassComesBeforeAFewGrams() {
        val amounts = mapOf("5 g thigh" to perServing(4, line(5, QuantityUnit.GRAM, thigh)), "1 carcass" to perServing(4, line(1, QuantityUnit.PIECE, chicken)))
        assertEquals(listOf("1 carcass", "5 g thigh"), amounts.keys.toList().byQuantityPerServing { amounts.getValue(it) })
    }

    // Scenario 3: for 4 servings, C (1 piece of chicken) before A (8 wings) before B (500 g of chicken); to taste last.
    @Test
    fun theScenarioOrdersTheCarcassTheWingsThenTheGrams() {
        val amounts = linkedMapOf(
            "to taste" to perServing(4, MeasuredLine(UnitType.TASTE_UNMEASURED, QuantityUnit.TO_TASTE, null, chicken)),
            "A" to perServing(4, line(8, QuantityUnit.PIECE, wings)),
            "B" to perServing(4, line(500, QuantityUnit.GRAM, chicken)),
            "C" to perServing(4, line(1, QuantityUnit.PIECE, chicken)),
        )
        assertEquals(listOf("C", "A", "B", "to taste"), amounts.keys.toList().byQuantityPerServing { amounts.getValue(it) })
    }

    // CAT-007 (1): 500 g of thigh and 300 g of wings for 4 servings are 200 on the family's scale.
    @Test
    fun matchedLinesAreSummed() {
        assertEquals(200.0, perServing(4, line(500, QuantityUnit.GRAM, thigh), line(300, QuantityUnit.GRAM, wings)))
        // CAT-007 (2): one line is that line.
        assertEquals(125.0, perServing(4, line(500, QuantityUnit.GRAM, thigh)))
    }

    // CAT-007 (3): grams and pieces are each converted by their own entry's factors and summed; a kilogram through the gram.
    @Test
    fun linesInDifferentUnitsAreSummedThroughTheirFactors() {
        assertEquals(240.0, perServing(4, line(320, QuantityUnit.GRAM, chicken), line(8, QuantityUnit.PIECE, wings)))
        assertEquals(375.0, perServing(4, line(1, QuantityUnit.KILOGRAM, thigh), line(500, QuantityUnit.GRAM, chicken)))
    }

    // CAT-007 (4), CAT-009 (4): a line in a unit its entry has no factor for contributes nothing; the rest still counts.
    @Test
    fun aLineWithoutAFactorContributesNothing() {
        assertEquals(125.0, perServing(4, line(500, QuantityUnit.GRAM, thigh), line(3, QuantityUnit.PIECE, thigh)))
        assertEquals(125.0, perServing(4, line(500, QuantityUnit.GRAM, thigh), line(2, QuantityUnit.TABLESPOON, chicken)))
        assertEquals(125.0, perServing(4, line(500, QuantityUnit.GRAM, thigh), line(900, QuantityUnit.GRAM, emptyMap())))
        assertNull(perServing(4, line(3, QuantityUnit.PIECE, thigh)))
    }

    // CAT-007 (5): `to taste` carries no quantity and contributes nothing; nor does a line without a quantity.
    @Test
    fun aLineWithNoQuantityContributesNothing() {
        val lines = arrayOf(
            MeasuredLine(UnitType.TASTE_UNMEASURED, null, null, chicken),
            MeasuredLine(UnitType.TASTE_UNMEASURED, QuantityUnit.TO_TASTE, null, chicken),
            MeasuredLine(UnitType.EXACT, QuantityUnit.GRAM, null, chicken),
        )
        assertNull(perServing(2, *lines))
        assertEquals(750.0, perServing(2, *lines, line(1, QuantityUnit.PIECE, chicken)))
    }

    @Test
    fun aRecipeOfNoServingCountHasNoQuantityPerServing() {
        assertNull(perServing(null, line(500, QuantityUnit.GRAM, thigh)))
    }

    // CAT-002: 150 g a serving before 125 g; CAT-009 (5): the incomparable after every comparable recipe, in the chosen
    // order, whichever way the quantities run; equal quantities keep the chosen order.
    @Test
    fun theIncomparableComeLastInBothOrders() {
        val amounts = linkedMapOf(
            "no quantity" to null,
            "125" to 125.0,
            "no factor" to null,
            "150" to 150.0,
            "also 125" to 125.0,
        )
        val items = amounts.keys.toList()
        assertEquals(listOf("150", "125", "also 125", "no quantity", "no factor"), items.byQuantityPerServing { amounts.getValue(it) })
        assertEquals(
            listOf("125", "also 125", "150", "no quantity", "no factor"),
            items.byQuantityPerServing(mostFirst = false) { amounts.getValue(it) },
        )
        assertEquals(listOf("a", "b"), listOf("a", "b").byQuantityPerServing { null })
    }
}
