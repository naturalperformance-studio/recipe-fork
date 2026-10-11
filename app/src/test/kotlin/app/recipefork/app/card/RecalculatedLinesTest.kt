package app.recipefork.app.card

import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.InterfaceLanguage.ENGLISH
import app.recipefork.core.scaling.Rational
import app.recipefork.core.scaling.Rounding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the card's list says about how its quantities were computed: recalculated — once, for the whole list — at any
 * scale other than the authored one (SCALE-004 (1), SCALE-001), which way rounding moved each line, the arrow it
 * carries (SCALE-004 (2–5)), rounded up at the floor (SCALE-005), and whether the list still holds the authored
 * proportions (SCALE-009 (1–3)).
 */
class RecalculatedLinesTest {
    private fun row(
        name: String,
        unit: QuantityUnit?,
        quantity: Rational?,
        divisible: Boolean? = null,
        type: UnitType = unit?.type ?: UnitType.TASTE_UNMEASURED,
    ) = Ingredient(0, null, freeTextName = name, unitType = type, unit = unit, quantity = quantity, divisible = divisible)

    /** A recipe for 4, with a row to taste and an unfinished one. */
    private val forFour = listOf(
        row("Chicken", QuantityUnit.GRAM, Rational.of(500)),
        row("Eggs", QuantityUnit.PIECE, Rational.of(3), divisible = false),
        row("Salt", QuantityUnit.TEASPOON, Rational.ONE),
        row("Pepper", null, null),
        row("Flour", QuantityUnit.GRAM, null),
    )

    private fun list(requested: Long, d: Long = 1, ingredients: List<Ingredient> = forFour, authored: Int = 4) =
        ingredientList(ingredients, emptyMap(), ENGLISH, authored, Rational.of(requested, d))

    private data class Marking(val quantity: String?, val derived: Boolean, val rounding: Rounding)

    private fun markings(list: IngredientList) = list.lines.map { Marking(it.quantity, it.derived, it.rounding) }

    @Test
    fun atTheAuthoredCountNothingIsMarked() {
        assertFalse(list(4).recalculated)
        assertFalse(ingredientList(forFour, emptyMap(), ENGLISH, 4, null).recalculated)
        list(4).lines.forEach { assertFalse("${it.name}", it.derived); assertEquals(Rounding.NONE, it.rounding) }
        ingredientList(forFour, emptyMap(), ENGLISH, 4, null).lines.forEach { assertFalse("${it.name}", it.derived) }
    }

    @Test
    fun atAnyOtherCountEveryQuantityIsMarkedAndARoundedOneSaysWhichWay() {
        assertTrue(list(6).recalculated)
        assertTrue(list(3).recalculated)
        assertTrue(list(1, 4).recalculated)
        // 6 servings: 750 g exact, 4.5 eggs → 5 (the exact value was lower), 1½ tsp exact.
        assertEquals(
            listOf(
                Marking("750 g", true, Rounding.NONE),
                Marking("5 pc", true, Rounding.UP),
                Marking("1½ tsp", true, Rounding.NONE),
                Marking(null, false, Rounding.NONE),
                Marking(null, false, Rounding.NONE),
            ),
            markings(list(6)),
        )
        // 3 servings: 2.25 eggs → 2 (the exact value was higher).
        assertEquals(Marking("2 pc", true, Rounding.DOWN), markings(list(3))[1])
    }

    @Test
    fun aQuantityRoundedBackOntoItsAuthoredValueIsStillMarked() {
        // SCALE-004 (3): 1 egg for 4, read for 2, computes ½, shows 1 egg — derived, the exact value was lower.
        val egg = listOf(row("Egg", QuantityUnit.PIECE, Rational.ONE, divisible = false))
        assertEquals(listOf(Marking("1 pc", true, Rounding.UP)), markings(list(2, ingredients = egg)))
        // SCALE-004 (5): 400 g for 4, read for 8, is exact and marked with no direction; read for 4, nothing marked.
        val flour = listOf(row("Flour", QuantityUnit.GRAM, Rational.of(400)))
        assertEquals(listOf(Marking("800 g", true, Rounding.NONE)), markings(list(8, ingredients = flour)))
        assertEquals(listOf(Marking("400 g", false, Rounding.NONE)), markings(list(4, ingredients = flour)))
        assertTrue(list(8, ingredients = flour).recalculated)
        assertFalse(list(4, ingredients = flour).recalculated)
    }

    @Test
    fun aFixedAmountRecalculatesTheListAndMarksItsRoundedLines() {
        // SCALE-002: 300 g of the 500 g chicken scales the whole list by 3/5 — 1.8 eggs → 2, ⅗ tsp → ⅔ tsp.
        val fixed = ingredientList(forFour, emptyMap(), ENGLISH, 4, CardScale(null).fix(CardFixing(0, Rational.of(300), QuantityUnit.GRAM)))
        assertTrue(fixed.recalculated)
        assertTrue(fixed.lines[0].fixed)
        assertEquals(Marking("2 pc", true, Rounding.UP), markings(fixed)[1])
        assertEquals(Marking("⅔ tsp", true, Rounding.UP), markings(fixed)[2])
        // A recipe without a serving count is recalculated by a fixing all the same.
        val uncounted = ingredientList(forFour, emptyMap(), ENGLISH, null, CardScale(null).fix(CardFixing(0, Rational.of(300), QuantityUnit.GRAM)))
        assertTrue(uncounted.recalculated)
    }

    @Test
    fun aQuantityHeldAtItsFloorReadsAsRoundedUp() {
        // ¼ of a serving: 3 eggs → 0.1875 → 1, 1 tsp → 1/16 → ¼ tsp.
        val quarter = markings(list(1, 4))
        assertEquals(Marking("1 pc", true, Rounding.UP), quarter[1])
        assertEquals(Marking("¼ tsp", true, Rounding.UP), quarter[2])
    }

    @Test
    fun oneLineAtItsFloorKeepsTheProportionsAndTwoDoNot() {
        // 1 serving: only the eggs (0.75) are held at the floor; the salt is exactly ¼ tsp.
        assertTrue(list(1).holdsProportions)
        // ½ serving: eggs 0.375 → 1 and salt ⅛ → ¼ tsp, both held.
        assertFalse(list(1, 2).holdsProportions)
        assertTrue(list(4).holdsProportions)
        assertTrue(list(6).holdsProportions)
    }

    @Test
    fun aRecipeWithNoServingCountHoldsItsProportionsAndMarksNothing() {
        val unscaled = ingredientList(forFour, emptyMap(), ENGLISH, null, null)
        assertTrue(unscaled.holdsProportions)
        assertFalse(unscaled.recalculated)
        unscaled.lines.forEach { assertFalse("${it.name}", it.derived) }
    }
}
