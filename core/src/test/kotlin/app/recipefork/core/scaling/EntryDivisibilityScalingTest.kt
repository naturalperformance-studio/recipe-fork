package app.recipefork.core.scaling

import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.IngredientShare
import app.recipefork.core.recipe.IngredientShares
import app.recipefork.core.recipe.QuantityUnit.GRAM
import app.recipefork.core.recipe.QuantityUnit.PIECE
import app.recipefork.core.recipe.UnitType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun r(n: Long, d: Long = 1) = Rational.of(n, d)

private const val WINGS = 2L

private fun wings(quantity: Rational, written: Boolean?) =
    Ingredient(0, WINGS, unitType = UnitType.PIECE, unit = PIECE, quantity = quantity, divisible = written)

private fun shown(row: Ingredient, entry: Boolean?, requested: Long, authored: Int = 4): DisplayedQuantity =
    Scaling.display(checkNotNull(Scaling.authored(row, entry)), ScaleFactor.forServings(r(requested), authored))

/** A `piece` row is scaled by its effective divisibility: the entry's flag where it sets one, the row's otherwise. */
class EntryDivisibilityScalingTest {

    // Scenario 4: 8 wings for 4, at 3 servings, stay whole pieces — 6.
    @Test
    fun wingsForFourAtThreeStayWhole() {
        val at3 = shown(wings(r(8), written = true), entry = false, requested = 3)
        assertEquals(r(6), at3.value)
        assertTrue(at3.derived)
    }

    // 7 wings for 4 at 2 servings is 3½ exactly: an indivisible entry takes it to 4, whatever the row stored.
    @Test
    fun theEntrysFlagDecidesTheGrid() {
        assertEquals(r(4), shown(wings(r(7), written = true), entry = false, requested = 2).value)
        assertEquals(Rounding.UP, shown(wings(r(7), written = true), entry = false, requested = 2).rounding)
        assertEquals(r(7, 2), shown(wings(r(7), written = false), entry = true, requested = 2).value)
        // An entry setting no flag leaves the row's own answer.
        assertEquals(r(7, 2), shown(wings(r(7), written = true), entry = null, requested = 2).value)
        assertEquals(r(4), shown(wings(r(7), written = false), entry = null, requested = 2).value)
        // A row with no answer of its own is read by the entry's.
        assertEquals(r(4), shown(wings(r(7), written = null), entry = false, requested = 2).value)
        assertNull(Scaling.authored(wings(r(7), written = null), null))
    }

    @Test
    fun aFreeTextRowIsNeverReadByAnEntry() {
        val own = Ingredient(0, null, freeTextName = "Wings", unitType = UnitType.PIECE, unit = PIECE, quantity = r(7), divisible = true)
        assertEquals(true, Scaling.authored(own, false)?.divisible)
    }

    // DOM-018 (1): 1½ written while the entry was divisible, now marked indivisible: shown as written, not derived.
    @Test
    fun aValueWrittenBeforeTheCorrectionIsShownAsWrittenAtTheAuthoredCount() {
        val atAuthored = shown(wings(r(3, 2), written = true), entry = false, requested = 4)
        assertEquals(r(3, 2), atAuthored.value)
        assertFalse(atAuthored.derived)
        assertEquals(Rounding.NONE, atAuthored.rounding)
    }

    // DOM-018 (2): at any other count it is computed from the written 1½ and rounded on the new, whole grid.
    @Test
    fun atAnotherCountItIsComputedFromTheWrittenValueOnTheNewGrid() {
        val row = wings(r(3, 2), written = true)
        assertEquals(r(3), shown(row, entry = false, requested = 8).value)
        val at3 = shown(row, entry = false, requested = 3) // 1⅛
        assertEquals(r(1), at3.value)
        assertEquals(Rounding.DOWN, at3.rounding)
        assertTrue(at3.derived)
        val at6 = shown(row, entry = false, requested = 6) // 2¼
        assertEquals(r(2), at6.value)
    }

    // A value off the grid it was written on is still never scaled from.
    @Test
    fun aValueOffEveryGridIsNotRead() {
        assertNull(Scaling.authored(wings(r(3, 2), written = false), false))
        assertNull(Scaling.authored(wings(r(5, 4), written = true), false))
        assertNull(Scaling.authored(Ingredient(0, null, freeTextName = "Yeast", unitType = UnitType.EXACT, unit = GRAM, quantity = r(5, 2)), null))
    }

    // SCALE-005: an indivisible entry holds its floor of one whole piece.
    @Test
    fun anIndivisibleEntryIsHeldAtOnePiece() {
        val at1 = shown(wings(r(1), written = true), entry = false, requested = 1)
        assertEquals(r(1), at1.value)
        assertTrue(at1.heldAtFloor)
        assertEquals(Rounding.UP, at1.rounding)
    }

    // A step's part of a row is rounded on the grid the card reads the row with.
    @Test
    fun aPartIsRoundedOnTheEffectiveGrid() {
        val row = wings(r(4), written = true)
        val share = IngredientShare(r(1), PIECE)
        val factor = ScaleFactor.forServings(r(2), 4)
        assertEquals(r(1, 2), IngredientShares.display(share, row, factor)?.value)
        assertEquals(r(1), IngredientShares.display(share, row, factor, divisible = false)?.value)
    }
}
