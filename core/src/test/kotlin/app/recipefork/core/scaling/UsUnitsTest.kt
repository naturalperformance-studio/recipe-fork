package app.recipefork.core.scaling

import app.recipefork.core.recipe.AcceptedValues
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.QuantityInput
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.QuantityUnit.CUBIC_METRE
import app.recipefork.core.recipe.QuantityUnit.FLUID_OUNCE
import app.recipefork.core.recipe.QuantityUnit.GRAM
import app.recipefork.core.recipe.QuantityUnit.KILOGRAM
import app.recipefork.core.recipe.QuantityUnit.LITRE
import app.recipefork.core.recipe.QuantityUnit.MILLILITRE
import app.recipefork.core.recipe.QuantityUnit.OUNCE
import app.recipefork.core.recipe.QuantityUnit.POUND
import app.recipefork.core.recipe.QuantityUnit.TONNE
import app.recipefork.core.recipe.UnitSystem
import app.recipefork.core.recipe.comparisonFactor
import app.recipefork.core.scaling.InterfaceLanguage.ENGLISH
import app.recipefork.core.scaling.InterfaceLanguage.RUSSIAN
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun q(n: Long, d: Long = 1) = Rational.of(n, d)

private fun text(unit: QuantityUnit, value: Rational, language: InterfaceLanguage = ENGLISH) =
    QuantityText.of(DisplayedQuantity(unit, value, derived = true, rounding = Rounding.NONE), language)

/** v0.35 T-246: the three US units are exact, with exact ratios to their base units. */
class UsUnitRatiosTest {
    @Test
    fun `an ounce is 28,349523125 g, a pound 16 oz, a fluid ounce 29,5735295625 ml - exactly`() {
        assertEquals(q(28_349_523_125, 1_000_000_000), OUNCE.baseMultiple)
        assertEquals(q(45_359_237, 100_000), POUND.baseMultiple)
        assertEquals(q(16), POUND.baseMultiple!! / OUNCE.baseMultiple!!)
        assertEquals(q(295_735_295_625, 10_000_000_000), FLUID_OUNCE.baseMultiple)
        listOf(OUNCE, POUND, FLUID_OUNCE).forEach { assertEquals(UnitSystem.US, it.system, "$it") }
        assertEquals(listOf(GRAM, GRAM, MILLILITRE), listOf(OUNCE, POUND, FLUID_OUNCE).map { it.baseUnit })
    }

    @Test
    fun `each unit is computed in the smallest unit of its own system and dimension`() {
        assertEquals(
            listOf(GRAM, GRAM, GRAM, MILLILITRE, MILLILITRE, MILLILITRE, OUNCE, OUNCE, FLUID_OUNCE),
            listOf(GRAM, KILOGRAM, TONNE, MILLILITRE, LITRE, CUBIC_METRE, OUNCE, POUND, FLUID_OUNCE).map { it.lineUnit },
        )
        assertEquals(q(1_000), KILOGRAM.lineMultiple)
        assertEquals(q(1_000_000), CUBIC_METRE.lineMultiple)
        assertEquals(q(16), POUND.lineMultiple)
        assertEquals(Rational.ONE, OUNCE.lineMultiple)
        assertNull(QuantityUnit.CUP.lineUnit)
        assertNull(QuantityUnit.CUP.baseMultiple)
    }

    @Test
    fun `an entry's grams per gram give its grams per ounce and per pound, for ordering`() {
        val factors = mapOf("g" to 2.0, "ml" to 1.0)
        assertEquals(56.69904625, comparisonFactor(factors, OUNCE)!!, 1e-9)
        assertEquals(907.18474, comparisonFactor(factors, POUND)!!, 1e-9)
        assertEquals(29.5735295625, comparisonFactor(factors, FLUID_OUNCE)!!, 1e-9)
    }
}

/** v0.35 T-246: the kitchen grids of oz (¼ below 4 oz, ½ up to 2 lb, ¼ lb from 2 lb) and fl oz (½). */
class UsUnitGridsTest {
    @Test
    fun `oz and lb share the ounce grid, fl oz has its own, metric keeps the exact one`() {
        assertEquals(RoundingGrid.OUNCES, RoundingGrid.of(OUNCE))
        assertEquals(RoundingGrid.OUNCES, RoundingGrid.of(POUND))
        assertEquals(RoundingGrid.FLUID_OUNCES, RoundingGrid.of(FLUID_OUNCE))
        listOf(GRAM, KILOGRAM, TONNE, MILLILITRE, LITRE, CUBIC_METRE).forEach { assertEquals(RoundingGrid.EXACT, RoundingGrid.of(it)) }
        assertEquals(q(1, 4), RoundingGrid.OUNCES.floor)
        assertEquals(q(1, 2), RoundingGrid.FLUID_OUNCES.floor)
    }

    @Test
    fun `the ounce grid holds quarters below 4 oz, halves up to 2 lb, quarter pounds from 2 lb`() {
        val grid = RoundingGrid.OUNCES
        listOf(q(1, 4), q(3, 4), q(15, 4), q(4), q(9, 2), q(31, 2), q(16), q(33, 2), q(18), q(35, 2), q(63, 2), q(32), q(36), q(400))
            .forEach { assertTrue(grid.holds(it), "holds $it") }
        listOf(q(1, 8), q(1, 3), q(17, 4), q(65, 4), q(33), q(34), q(35)).forEach { assertFalse(grid.holds(it), "does not hold $it") }
    }

    @Test
    fun `a computed amount goes to the nearest value, the larger at a tie, never below ¼ oz`() {
        val grid = RoundingGrid.OUNCES
        assertEquals(q(1, 4), grid.nearest(q(1, 100)))
        assertEquals(q(4), grid.nearest(q(39, 10)))
        assertEquals(q(4), grid.nearest(q(42, 10)))
        assertEquals(q(9, 2), grid.nearest(q(17, 4)))
        assertEquals(q(16), grid.nearest(q(159, 10)))
        // 500 g = 17.637 oz → 17½ oz, not a quarter pound away.
        assertEquals(q(35, 2), grid.nearest(Rational.of(500) / OUNCE.baseMultiple!!))
        assertEquals(q(18), grid.nearest(q(179, 10)))
        assertEquals(q(32), grid.nearest(q(319, 10)))
        assertEquals(q(32), grid.nearest(q(33)))
        assertEquals(q(36), grid.nearest(q(34))) // a tie between 32 and 36 goes up
    }

    @Test
    fun `fluid ounces go to the nearest half, never below ½ fl oz`() {
        val grid = RoundingGrid.FLUID_OUNCES
        assertEquals(q(1, 2), grid.nearest(q(1, 10)))
        assertEquals(q(3, 2), grid.nearest(q(5, 4)))
        assertEquals(q(8), grid.nearest(q(81, 10)))
        assertTrue(grid.holds(q(33, 2)))
        assertFalse(grid.holds(q(1, 4)))
    }

    @Test
    fun `the quantity field holds what the grid holds, in the unit typed`() {
        assertTrue(QuantityInput.holds(q(5, 4), POUND, null))
        assertTrue(QuantityInput.holds(q(3, 4), POUND, null)) // 12 oz
        assertFalse(QuantityInput.holds(q(11, 10), POUND, null)) // 17.6 oz
        assertTrue(QuantityInput.holds(q(14), OUNCE, null))
        assertTrue(QuantityInput.holds(q(18), OUNCE, null)) // an 18 oz jar, as the author writes it
        assertTrue(QuantityInput.holds(q(3, 2), POUND, null))
        assertFalse(QuantityInput.holds(q(11, 5), POUND, null)) // 2.2 lb = 35.2 oz, off the quarter pounds
        assertFalse(QuantityInput.holds(q(33), OUNCE, null))
        assertTrue(QuantityInput.holds(q(3, 2), FLUID_OUNCE, null))
        assertEquals(AcceptedValues.OUNCES, QuantityInput.accepts(POUND, null))
        assertEquals(AcceptedValues.FLUID_OUNCES, QuantityInput.accepts(FLUID_OUNCE, null))
        assertEquals(AcceptedValues.WHOLE_GRAMS, QuantityInput.accepts(KILOGRAM, null))
    }
}

/** v0.35 T-246: the US magnitude ladder — oz, then lb from 16 oz; fl oz alone — and the metric one unchanged. */
class UsUnitLadderTest {
    @Test
    fun `ounces below 16 oz, pounds from 16 oz, each as a kitchen fraction`() {
        assertEquals("¼ oz", text(OUNCE, q(1, 4)))
        assertEquals("3¾ oz", text(OUNCE, q(15, 4)))
        assertEquals("4 oz", text(OUNCE, q(4)))
        assertEquals("15½ oz", text(OUNCE, q(31, 2)))
        assertEquals("1 lb", text(OUNCE, q(16)))
        assertEquals("16½ oz", text(OUNCE, q(33, 2)))
        assertEquals("17½ oz", text(OUNCE, q(35, 2)))
        assertEquals("18 oz", text(OUNCE, q(18)))
        assertEquals("1¼ lb", text(OUNCE, q(20)))
        assertEquals("1½ lb", text(OUNCE, q(24)))
        assertEquals("31½ oz", text(OUNCE, q(63, 2)))
        assertEquals("2 lb", text(OUNCE, q(32)))
        assertEquals("2¼ lb", text(OUNCE, q(36)))
        assertEquals("2½ lb", text(OUNCE, q(40)))
        assertEquals("1,000 lb", text(OUNCE, q(16_000)))
        assertEquals("1${RUSSIAN.groupSeparator}000 фн", text(OUNCE, q(16_000), RUSSIAN))
    }

    @Test
    fun `a pound or an ounce given in pounds is written on the same ladder`() {
        assertEquals("1½ lb", text(POUND, q(3, 2)))
        assertEquals("12 oz", text(POUND, q(3, 4)))
    }

    @Test
    fun `fluid ounces have no rung above them`() {
        assertEquals("½ fl oz", text(FLUID_OUNCE, q(1, 2)))
        assertEquals("8 fl oz", text(FLUID_OUNCE, q(8)))
        assertEquals("64½ fl oz", text(FLUID_OUNCE, q(129, 2)))
        assertEquals("1½ жидк. унц.", text(FLUID_OUNCE, q(3, 2), RUSSIAN))
    }

    @Test
    fun `russian labels`() {
        assertEquals("4 унц.", text(OUNCE, q(4), RUSSIAN))
        assertEquals("1¼ фн", text(OUNCE, q(20), RUSSIAN))
        assertEquals("18 унц.", text(OUNCE, q(18), RUSSIAN))
    }

    @Test
    fun `the metric ladder never turns to ounces`() {
        assertEquals("28 g", text(GRAM, q(28)))
        assertEquals("454 g", text(GRAM, q(454)))
        assertEquals("1.5 kg", text(GRAM, q(1_500)))
        assertEquals("1.5 t", text(GRAM, q(1_500_000)))
        assertEquals("240 ml", text(MILLILITRE, q(240)))
        assertEquals("2 l", text(MILLILITRE, q(2_000)))
    }
}

/** v0.35 T-246: a recipe written in US units scales, rounds and fixes as any exact unit does, staying in its system. */
class UsUnitScalingTest {
    private val butter = AuthoredQuantity(OUNCE, q(4))
    private val beef = AuthoredQuantity(POUND, q(3, 2))
    private val milk = AuthoredQuantity(FLUID_OUNCE, q(8))

    private fun shown(authored: AuthoredQuantity, factor: Rational) = Scaling.display(authored, ScaleFactor.of(factor))

    @Test
    fun `at its authored count an amount reads as written, in the line unit of its ladder`() {
        assertEquals(DisplayedQuantity(OUNCE, q(24), derived = false, rounding = Rounding.NONE), shown(beef, Rational.ONE))
        assertEquals("1½ lb", QuantityText.of(shown(beef, Rational.ONE), ENGLISH))
        assertEquals("4 oz", QuantityText.of(shown(butter, Rational.ONE), ENGLISH))
    }

    @Test
    fun `scaled, an ounce amount stays in oz and lb`() {
        assertEquals("8 oz", QuantityText.of(shown(butter, q(2)), ENGLISH))
        assertEquals("1¼ lb", QuantityText.of(shown(butter, q(5)), ENGLISH))
        assertEquals("2¼ lb", QuantityText.of(shown(beef, q(3, 2)), ENGLISH)) // 36 oz
        assertEquals("27 oz", QuantityText.of(shown(butter, q(27, 4)), ENGLISH))
        // An author's 18 oz scaled by 1 stays 18 oz; by 2, 36 oz is 2¼ lb.
        assertEquals("18 oz", QuantityText.of(shown(AuthoredQuantity(OUNCE, q(18)), Rational.ONE), ENGLISH))
        assertEquals("2¼ lb", QuantityText.of(shown(AuthoredQuantity(OUNCE, q(18)), q(2)), ENGLISH))
        assertEquals("12 oz", QuantityText.of(shown(beef, q(1, 2)), ENGLISH))
        assertEquals("4 fl oz", QuantityText.of(shown(milk, q(1, 2)), ENGLISH))
    }

    @Test
    fun `rounding follows the ounce grid and says which way it went`() {
        // 4 oz × 1/3 = 1⅓ oz → 1¼ oz, rounded down.
        val third = shown(butter, q(1, 3))
        assertEquals(OUNCE, third.unit)
        assertEquals(q(5, 4), third.value)
        assertEquals(Rounding.DOWN, third.rounding)
        // 4 oz × 1/32 = ⅛ oz, below the floor: ¼ oz, held at it.
        val tiny = shown(butter, q(1, 32))
        assertEquals(q(1, 4), tiny.value)
        assertTrue(tiny.heldAtFloor)
        assertEquals(Rounding.UP, tiny.rounding)
    }

    @Test
    fun `a row in oz or lb is read for scaling where its grid holds it`() {
        fun row(unit: QuantityUnit, quantity: Rational) =
            Ingredient(0, null, freeTextName = "x", unitType = unit.type, unit = unit, quantity = quantity)
        assertEquals(AuthoredQuantity(POUND, q(5, 4)), Scaling.authored(row(POUND, q(5, 4)), null))
        assertEquals(AuthoredQuantity(OUNCE, q(29, 2)), Scaling.authored(row(OUNCE, q(29, 2)), null))
        assertEquals(AuthoredQuantity(OUNCE, q(18)), Scaling.authored(row(OUNCE, q(18)), null))
        assertNull(Scaling.authored(row(OUNCE, q(33)), null))
        assertNull(Scaling.authored(row(POUND, q(11, 5)), null))
    }

    @Test
    fun `a recipe in oz is fixed in oz or lb, its own ladder alone`() {
        assertEquals(listOf(OUNCE, POUND), FixedAmount.units(OUNCE))
        assertEquals(listOf(OUNCE, POUND), FixedAmount.units(POUND))
        assertEquals(listOf(FLUID_OUNCE), FixedAmount.units(FLUID_OUNCE))
        assertEquals(listOf(GRAM, KILOGRAM, TONNE), FixedAmount.units(GRAM))
        assertEquals(listOf(MILLILITRE, LITRE, CUBIC_METRE), FixedAmount.units(LITRE))

        val recipe = ScaledRecipe.of(4, listOf(beef, butter, milk))
        // 1½ lb of beef fixed at 2¼ lb: the factor is 3/2, butter 6 oz, milk 12 fl oz.
        val fixed = recipe.fix(0, q(9, 4), POUND)
        assertEquals(ScaleFactor.of(q(3, 2)), fixed.factor)
        assertEquals(listOf(q(36), q(6), q(12)), fixed.lines.map { it.quantity.value })
        assertEquals(ScaleFactor.of(q(3, 2)), recipe.fix(1, q(6), OUNCE).factor)
        assertFailsWith<IllegalArgumentException> { recipe.fix(1, q(170), GRAM) }
        assertEquals(FixedAmountReading.Accepted(q(3, 2)), FixedAmount.read("1.5", POUND, null))
    }
}
