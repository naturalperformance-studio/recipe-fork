package app.recipefork.app.card

import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.InterfaceLanguage.ENGLISH
import app.recipefork.core.scaling.Rational
import app.recipefork.core.scaling.Rounding
import app.recipefork.core.scaling.ServingCount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The card's list while the reader fixes the amount they have of one ingredient (SCALE-002, SCALE-003, SCALE-008,
 * SCALE-016, SCALE-017, SCALE-018): which rows offer it, the factor it sets for every row, the count shown as
 * approximate, a second fixing releasing the first (T-065, departing from SCALE-006), and the releases.
 */
class FixedLinesTest {
    private fun row(
        position: Int,
        name: String,
        unit: QuantityUnit?,
        quantity: Rational?,
        divisible: Boolean? = null,
        type: UnitType = unit?.type ?: UnitType.TASTE_UNMEASURED,
    ) = Ingredient(position, null, freeTextName = name, unitType = type, unit = unit, quantity = quantity, divisible = divisible)

    /** A recipe for 4: two exact rows, a piece, a household one, one to taste, one unfinished and one off its grid. */
    private val forFour = listOf(
        row(0, "Chicken", QuantityUnit.GRAM, Rational.of(500)),
        row(1, "Rice", QuantityUnit.GRAM, Rational.of(350)),
        row(2, "Eggs", QuantityUnit.PIECE, Rational.of(3), divisible = false),
        row(3, "Salt", QuantityUnit.TEASPOON, Rational.ONE),
        row(4, "Pepper", null, null),
        row(5, "Flour", QuantityUnit.GRAM, null),
        row(6, "Sugar", QuantityUnit.TEASPOON, Rational.of(3, 10)),
    )

    private val chicken = 0
    private val rice = 1
    private val eggs = 2

    private fun list(scale: CardScale, authored: Int? = 4) = ingredientList(forFour, emptyMap(), ENGLISH, authored, scale)
    private fun quantities(list: IngredientList) = list.lines.map { it.quantity }
    private fun fixing(position: Int, amount: Long, unit: QuantityUnit, d: Long = 1) =
        CardFixing(position, Rational.of(amount, d), unit)

    private val atFour = CardScale(Rational.of(4))
    private val chicken300 = fixing(chicken, 300, QuantityUnit.GRAM)
    private val rice525 = fixing(rice, 525, QuantityUnit.GRAM)

    @Test
    fun aRowCarryingAQuantityOffersFixingInTheUnitsItsUnitReadsIn() {
        val offers = list(atFour).lines.map { it.fixing }
        val grams = listOf(QuantityUnit.GRAM, QuantityUnit.KILOGRAM, QuantityUnit.TONNE)
        assertEquals(FixOffer(grams, QuantityUnit.GRAM, null), offers[0])
        assertEquals(FixOffer(grams, QuantityUnit.GRAM, null), offers[1])
        assertEquals(FixOffer(listOf(QuantityUnit.PIECE), QuantityUnit.PIECE, false), offers[2])
        assertEquals(FixOffer(listOf(QuantityUnit.TEASPOON), QuantityUnit.TEASPOON, null), offers[3])
        // To taste, unfinished, off its grid: nothing to divide by.
        assertEquals(listOf(null, null, null), offers.subList(4, 7))
    }

    // Scenario 5, SCALE-002, SCALE-003: 300 g of the 500 g sets 3/5 for every row, and the count reads ≈2.
    @Test
    fun aFixedAmountSetsTheExactFactorForTheWholeRecipe() {
        val fixed = list(atFour.fix(chicken300))
        // Rice 350 × 3/5 = 210 g: from the exact factor, not from the 2 servings shown (which would give 175 g).
        assertEquals(listOf("300 g", "210 g", "2 pc", "⅔ tsp", null, null, "tsp"), quantities(fixed))
        assertEquals(ServingCount(Rational.of(2), approximate = true), fixed.servingCount)
        assertEquals(listOf(true, false, false, false, false, false, false), fixed.lines.map { it.fixed })
    }

    @Test
    fun theDerivedMarkersFollowAFixingOnEveryLine() {
        val fixed = list(atFour.fix(chicken300)).lines
        assertEquals(listOf(true, true, true, true, false, false, false), fixed.map { it.derived })
        assertEquals(
            listOf(Rounding.NONE, Rounding.NONE, Rounding.UP, Rounding.UP),
            fixed.take(4).map { it.rounding },
        )
    }

    // SCALE-009 under a fixing: 25 g of 500 g holds the eggs and the salt at their floors.
    @Test
    fun theProportionsWarningFollowsAFixing() {
        assertTrue(list(atFour.fix(chicken300)).holdsProportions)
        val small = list(atFour.fix(fixing(chicken, 25, QuantityUnit.GRAM)))
        assertFalse(small.holdsProportions)
        assertEquals(listOf("1 pc", "¼ tsp"), quantities(small).subList(2, 4))
    }

    // SCALE-018 (1): 0,3 kg is the same fixing as 300 g.
    @Test
    fun anAmountInAnotherUnitOfTheDimensionReadsWithoutConversion() {
        assertEquals(quantities(list(atFour.fix(chicken300))), quantities(list(atFour.fix(fixing(chicken, 3, QuantityUnit.KILOGRAM, 10)))))
    }

    // SCALE-008: 50 g of 500 g is 0.4 servings, shown as ≈⅓.
    @Test
    fun aCountBelowOneIsShownAsAFraction() {
        assertEquals(ServingCount(Rational.of(1, 3), approximate = true), list(atFour.fix(fixing(chicken, 50, QuantityUnit.GRAM))).servingCount)
    }

    // T-065: fixing rice while chicken is fixed releases chicken; rice's 525 g of 350 g sets 3/2 for every row.
    @Test
    fun aSecondFixingReleasesTheFirst() {
        val both = list(atFour.fix(chicken300).fix(rice525))
        assertEquals(listOf(false, true, false, false, false, false, false), both.lines.map { it.fixed })
        assertEquals(listOf("750 g", "525 g", "5 pc", "1½ tsp", null, null, "tsp"), quantities(both))
        assertEquals(ServingCount(Rational.of(6), approximate = true), both.servingCount)
        // Exactly the list of rice fixed alone: nothing of the chicken fixing is left.
        assertEquals(list(atFour.fix(rice525)), both)
        // The chicken line is the ordinary line of the recipe at 6: recalculated, not fixed.
        assertEquals(list(CardScale(Rational.of(6))).lines[chicken], both.lines[chicken])
    }

    // SCALE-016: a change of count releases every fixing and is shown as asked for.
    @Test
    fun changingTheCountReleasesEveryFixing() {
        val asked = list(atFour.fix(chicken300).fix(fixing(eggs, 3, QuantityUnit.PIECE)).request(Rational.of(6)))
        assertEquals(listOf("750 g", "525 g", "5 pc", "1½ tsp", null, null, "tsp"), quantities(asked))
        assertTrue(asked.lines.none { it.fixed })
        assertEquals(ServingCount(Rational.of(6), approximate = false), asked.servingCount)
    }

    // SCALE-017: releasing the fixing returns to the count in force before the first fixing of the chain.
    @Test
    fun releasingTheFixingReturnsToTheCountBeforeTheFirstOfTheChain() {
        // At 4: fix chicken, then rice, release rice → back to 4, not approximate.
        val released = list(atFour.fix(chicken300).fix(rice525).release(rice))
        assertEquals(ServingCount(Rational.of(4), approximate = false), released.servingCount)
        assertEquals(list(atFour), released)
        assertTrue(released.lines.none { it.fixed || it.derived })
        // From a requested 6, the chain also returns to 6.
        val atSix = CardScale(Rational.of(6))
        val fromSix = list(atSix.fix(chicken300).fix(fixing(eggs, 3, QuantityUnit.PIECE)).release(eggs))
        assertEquals(ServingCount(Rational.of(6), approximate = false), fromSix.servingCount)
        assertEquals(list(atSix), fromSix)
        // Releasing the row already released by the second fixing leaves the second in force.
        assertEquals(list(atFour.fix(rice525)), list(atFour.fix(chicken300).fix(rice525).release(chicken)))
    }

    @Test
    fun fixingARowAgainReplacesItsAmount() {
        val again = list(atFour.fix(chicken300).fix(fixing(chicken, 1, QuantityUnit.KILOGRAM)))
        assertEquals("1 kg", again.lines[chicken].quantity)
        assertEquals(ServingCount(Rational.of(8), approximate = true), again.servingCount)
    }

    @Test
    fun aFixingOfARowThatOffersNoneChangesNothing() {
        assertEquals(list(atFour), list(atFour.fix(fixing(4, 1, QuantityUnit.TO_TASTE))))
        assertEquals(list(atFour), list(atFour.fix(fixing(5, 100, QuantityUnit.GRAM))))
        assertEquals(list(atFour), list(atFour.fix(fixing(chicken, 300, QuantityUnit.MILLILITRE))))
    }

    @Test
    fun aRecipeWithNoServingCountScalesFromAFixingAndShowsNoCount() {
        val fixed = list(CardScale(null).fix(chicken300), authored = null)
        assertEquals("210 g", fixed.lines[1].quantity)
        assertNull(fixed.servingCount)
        assertEquals(listOf("500 g", "350 g"), quantities(list(CardScale(null), authored = null)).take(2))
    }
}
