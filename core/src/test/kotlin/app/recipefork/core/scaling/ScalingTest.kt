package app.recipefork.core.scaling

import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.QuantityUnit.CUP
import app.recipefork.core.recipe.QuantityUnit.CUBIC_METRE
import app.recipefork.core.recipe.QuantityUnit.GRAM
import app.recipefork.core.recipe.QuantityUnit.KILOGRAM
import app.recipefork.core.recipe.QuantityUnit.LITRE
import app.recipefork.core.recipe.QuantityUnit.MILLILITRE
import app.recipefork.core.recipe.QuantityUnit.PIECE
import app.recipefork.core.recipe.QuantityUnit.PINCH
import app.recipefork.core.recipe.QuantityUnit.TABLESPOON
import app.recipefork.core.recipe.QuantityUnit.TEASPOON
import app.recipefork.core.recipe.QuantityUnit.TO_TASTE
import app.recipefork.core.recipe.QuantityUnit.TONNE
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun r(n: Long, d: Long = 1) = Rational.of(n, d)
private fun servings(requested: Long, authored: Int) = ScaleFactor.forServings(r(requested), authored)

/** One recipe covering all five unit types, authored for 4 servings. */
private val recipe = listOf(
    AuthoredQuantity(GRAM, r(500)),
    AuthoredQuantity(PIECE, r(3), divisible = false),
    AuthoredQuantity(PIECE, r(3, 2), divisible = true),
    AuthoredQuantity(TABLESPOON, r(1)),
    AuthoredQuantity(TEASPOON, r(1, 3)),
    AuthoredQuantity(PINCH, r(1)),
    AuthoredQuantity(TO_TASTE, null),
    AuthoredQuantity(KILOGRAM, r(3, 2)),
    AuthoredQuantity(MILLILITRE, r(7)),
)

class Scale001ScalingByServingCountTest {
    @Test
    fun `500 g authored for 4 servings shows 750 g at 6`() {
        val line = Scaling.display(AuthoredQuantity(GRAM, r(500)), servings(6, 4))
        assertEquals(GRAM, line.unit)
        assertEquals(r(750), line.value)
        assertTrue(line.derived)
        assertEquals(Rounding.NONE, line.rounding)
    }

    @Test
    fun `a taste_unmeasured line is unchanged at any count`() {
        val toTaste = AuthoredQuantity(TO_TASTE, null)
        val atAuthored = Scaling.display(toTaste, ScaleFactor.ONE)
        for (count in listOf(1L, 2, 3, 4, 6, 13, 1000, 1_000_000)) {
            assertEquals(atAuthored, Scaling.display(toTaste, servings(count, 4)), "at $count servings")
        }
        assertEquals(atAuthored, Scaling.display(toTaste, ScaleFactor.forServings(r(1, 4), 4)))
        assertEquals(TO_TASTE, atAuthored.unit)
        assertNull(atAuthored.value)
        assertFalse(atAuthored.derived)
    }

    @Test
    fun `returning the count to the authored value restores every authored quantity, none derived`() {
        for (count in listOf(1L, 2, 6, 7, 1000)) {
            Scaling.display(recipe, servings(count, 4)) // a scaled view first
            val back = Scaling.display(recipe, servings(4, 4))
            recipe.zip(back).forEach { (authored, shown) ->
                val expected = authored.quantity?.let { q -> authored.unit.baseMultiple?.let { q * r(it) } ?: q }
                assertEquals(expected, shown.value, "$authored after $count servings")
                assertFalse(shown.derived, "$authored is not derived at the authored count")
                assertEquals(Rounding.NONE, shown.rounding)
            }
        }
    }
}

class Scale003RoundingOnlyOnDisplayTest {
    private val sevenGrams = AuthoredQuantity(GRAM, r(7))

    @Test
    fun `7 g at a factor of 0_6 shows 4 g and the stored value stays 7 g`() {
        val line = Scaling.display(sevenGrams, ScaleFactor.of(r(6, 10)))
        assertEquals(r(4), line.value)
        assertEquals(GRAM, line.unit)
        assertEquals(r(7), sevenGrams.quantity)
    }

    @Test
    fun `changing that view's factor to 1_0 shows 7 g again, not a value derived from 4 g`() {
        Scaling.display(sevenGrams, ScaleFactor.of(r(6, 10)))
        val back = Scaling.display(sevenGrams, ScaleFactor.of(r(10, 10)))
        assertEquals(r(7), back.value)
        assertFalse(back.derived)
        // Scaling the displayed 4 g back would have given 4 g; the authored value is what is read.
        assertEquals(r(7), sevenGrams.quantity)
    }

    @Test
    fun `a factor near 1 after a rounded view is still computed from the authored value`() {
        Scaling.display(sevenGrams, ScaleFactor.of(r(6, 10)))
        // From 7 g: 7 × 1.1 = 7.7 → 8 g. From the displayed 4 g it would have been 4.4 → 4 g.
        assertEquals(r(8), Scaling.display(sevenGrams, ScaleFactor.of(r(11, 10))).value)
    }
}

class Scale005NeverScaledOutTest {
    @Test
    fun `3 indivisible pieces at a factor of 0_1 show 1 piece, rounded up`() {
        val line = Scaling.display(AuthoredQuantity(PIECE, r(3), divisible = false), ScaleFactor.of(r(1, 10)))
        assertEquals(PIECE, line.unit)
        assertEquals(r(1), line.value)
        assertEquals(Rounding.UP, line.rounding)
    }

    @Test
    fun `1 teaspoon at a factor of 0_05 shows a quarter teaspoon, rounded up`() {
        val line = Scaling.display(AuthoredQuantity(TEASPOON, r(1)), ScaleFactor.of(r(5, 100)))
        assertEquals(TEASPOON, line.unit)
        assertEquals(r(1, 4), line.value)
        assertEquals(Rounding.UP, line.rounding)
    }

    @Test
    fun `no line shows zero at any factor above zero`() {
        val factors = listOf(
            r(1, 1_000_000_000_000), r(1, 1000), r(1, 17), r(1, 10), r(1, 3), r(9, 10), r(1), r(7, 3), r(1000),
        )
        for (f in factors) {
            Scaling.display(recipe, ScaleFactor.of(f)).forEachIndexed { i, line ->
                val value = line.value ?: return@forEachIndexed
                assertTrue(value.signum() > 0, "line $i shows $value at factor $f")
                val grid = RoundingGrid.of(recipe[i].unit, recipe[i].divisible)!!
                assertTrue(value >= grid.floor, "line $i shows $value, below its floor, at factor $f")
            }
        }
    }
}

class Scale010NoCeilingTest {
    @Test
    fun `1000 servings are displayed and nothing is refused`() {
        val lines = Scaling.display(recipe, servings(1000, 4))
        assertEquals(recipe.size, lines.size)
        assertEquals(r(125_000), lines[0].value) // 500 g × 250
        assertEquals(r(750), lines[1].value) // 3 pieces × 250
        assertEquals(r(375), lines[2].value) // 1½ pieces × 250
        assertEquals(r(250), lines[3].value) // 1 tablespoon × 250
        assertEquals(r(250, 3), lines[4].value) // ⅓ teaspoon × 250 = 83⅓
        assertEquals(r(375_000), lines[7].value) // 1.5 kg × 250 in grams
        assertEquals(r(1750), lines[8].value) // 7 ml × 250
        lines.filter { it.value != null }.forEach { assertTrue(it.derived) }
    }

    @Test
    fun `any requested count of a quarter or more is accepted, however large`() {
        for (n in listOf(1L, 1000, 1_000_000, Long.MAX_VALUE)) servings(n, 4)
        ScaleFactor.forServings(r(1, 4), 4)
    }

    @Test
    fun `a quantity is computed from the exact factor whatever its size`() {
        // 1000 / 3 servings from 7 g: the exact 2333⅓ g gives 2333 g; the factor rounded to 333 would give 2331 g.
        assertEquals(r(2333), Scaling.display(AuthoredQuantity(GRAM, r(7)), servings(1000, 3)).value)

        // A factor far beyond any machine integer, over an authored ⅓ teaspoon.
        val huge = BigInteger.TEN.pow(40) + BigInteger.ONE
        val factor = ScaleFactor.of(Rational.of(huge, BigInteger.valueOf(7)))
        val shown = Scaling.display(AuthoredQuantity(TEASPOON, r(1, 3)), factor).value!!
        val exact = Rational.of(huge, BigInteger.valueOf(21))
        assertTrue((shown - exact).abs() <= r(1, 12), "$shown is the nearest grid value to $exact")
        assertEquals(RoundingGrid.FRACTIONAL.nearest(exact), shown)

        // Grams at a factor of 10^30 / 3: exactly the nearest whole kilogram of the exact value.
        val gramFactor = ScaleFactor.of(Rational.of(BigInteger.TEN.pow(30), BigInteger.valueOf(3)))
        val grams = Scaling.display(AuthoredQuantity(GRAM, r(7)), gramFactor).value!!
        val exactGrams = Rational.of(BigInteger.valueOf(7) * BigInteger.TEN.pow(30), BigInteger.valueOf(3))
        assertEquals(RoundingGrid.EXACT.nearest(exactGrams), grams)
        assertTrue((grams - exactGrams).abs() <= r(500))
    }

    @Test
    fun `below a quarter serving is not a requested count`() {
        kotlin.test.assertFailsWith<IllegalArgumentException> { ScaleFactor.forServings(r(1, 5), 4) }
    }
}

class Dom017AuthoredValueIsAGridValueTest {
    /** Authored values that lie on their grid, in each unit, with the value the grid displays. */
    private val onGrid: List<Pair<AuthoredQuantity, Rational>> = listOf(
        AuthoredQuantity(GRAM, r(500)) to r(500),
        AuthoredQuantity(GRAM, r(1)) to r(1),
        AuthoredQuantity(KILOGRAM, r(3, 2)) to r(1500),
        AuthoredQuantity(KILOGRAM, r(1234, 1000)) to r(1234),
        AuthoredQuantity(TONNE, r(2)) to r(2_000_000),
        AuthoredQuantity(MILLILITRE, r(250)) to r(250),
        AuthoredQuantity(LITRE, r(1)) to r(1000),
        AuthoredQuantity(CUBIC_METRE, r(3)) to r(3_000_000),
        AuthoredQuantity(PIECE, r(3), divisible = false) to r(3),
        AuthoredQuantity(PIECE, r(1), divisible = false) to r(1),
        AuthoredQuantity(PIECE, r(1, 2), divisible = true) to r(1, 2),
        AuthoredQuantity(PIECE, r(3, 2), divisible = true) to r(3, 2),
        AuthoredQuantity(TEASPOON, r(1, 3)) to r(1, 3),
        AuthoredQuantity(TEASPOON, r(1, 4)) to r(1, 4),
        AuthoredQuantity(TABLESPOON, r(7, 4)) to r(7, 4),
        AuthoredQuantity(CUP, r(2)) to r(2),
        AuthoredQuantity(PINCH, r(2, 3)) to r(2, 3),
    )

    @Test
    fun `at the authored count a line shows the authored value, which the grid also yields`() {
        val atAuthored = ScaleFactor.forServings(r(4), 4)
        for ((ingredient, authoredInUnit) in onGrid) {
            val shown = Scaling.display(ingredient, atAuthored)
            assertEquals(authoredInUnit, shown.value, "$ingredient at the authored count")
            assertFalse(shown.derived)
            val grid = RoundingGrid.of(ingredient.unit, ingredient.divisible)!!
            assertTrue(grid.holds(authoredInUnit), "$ingredient lies on its grid")
            assertEquals(authoredInUnit, grid.nearest(authoredInUnit), "$ingredient is its own grid value")
        }
    }

    @Test
    fun `a value that DOM-018 keeps off its grid is shown as written at the authored count`() {
        val kept = AuthoredQuantity(PIECE, r(3, 2), divisible = false)
        val shown = Scaling.display(kept, ScaleFactor.ONE)
        assertEquals(r(3, 2), shown.value)
        assertFalse(shown.derived)
        assertEquals(Rounding.NONE, shown.rounding)
    }
}

class UnitsTest {
    @Test
    fun `the twelve units of DOM-016 carry the unit types its table gives them`() {
        val expected = mapOf(
            GRAM to "EXACT", KILOGRAM to "EXACT", TONNE to "EXACT",
            MILLILITRE to "EXACT", LITRE to "EXACT", CUBIC_METRE to "EXACT",
            PIECE to "PIECE",
            TEASPOON to "HOUSEHOLD", TABLESPOON to "HOUSEHOLD", CUP to "HOUSEHOLD",
            PINCH to "TASTE_MEASURED",
            TO_TASTE to "TASTE_UNMEASURED",
        )
        assertEquals(12, QuantityUnit.entries.size)
        assertEquals(expected.keys, QuantityUnit.entries.toSet())
        expected.forEach { (unit, type) -> assertEquals(type, unit.type.name, "$unit") }
    }
}
