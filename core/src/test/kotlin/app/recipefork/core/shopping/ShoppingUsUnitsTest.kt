package app.recipefork.core.shopping

import app.recipefork.core.recipe.QuantityUnit.CUP
import app.recipefork.core.recipe.QuantityUnit.FLUID_OUNCE
import app.recipefork.core.recipe.QuantityUnit.GRAM
import app.recipefork.core.recipe.QuantityUnit.KILOGRAM
import app.recipefork.core.recipe.QuantityUnit.MILLILITRE
import app.recipefork.core.recipe.QuantityUnit.OUNCE
import app.recipefork.core.recipe.QuantityUnit.POUND
import app.recipefork.core.scaling.InterfaceLanguage.ENGLISH
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** v0.35 T-246: the shopping list adds US units as any exact unit — exactly, oz and g of one entry into one line. */
class ShoppingUsUnitsTest {

    @Test
    fun `ounces and pounds of an entry add exactly on their own ladder`() {
        val line = list(recipe(A, row(FLOUR, r(12), OUNCE)), recipe(B, row(FLOUR, r(1, 2), POUND))).single()
        assertMeasured(OUNCE, r(20), line)
        assertFalse(line.approximate)
        assertEquals("1¼ lb", amountOf(line).text(ENGLISH))
    }

    @Test
    fun `scaled oz rows add after scaling - 4 oz for 4 asked for 8 and 1 lb make 1½ lb`() {
        val line = list(recipe(A, row(SUGAR, r(4), OUNCE), requested = r(8)), recipe(B, row(SUGAR, r(1), POUND))).single()
        assertMeasured(OUNCE, r(24), line)
    }

    @Test
    fun `grams and ounces of one entry add up exactly in grams, as one exact line`() {
        // 100 g + 4 oz = 100 + 113.39809 g → 213 g; 1 kg + 1 lb = 1453.59237 g → 1.454 kg.
        val small = list(recipe(A, row(SUGAR, r(100), GRAM)), recipe(B, row(SUGAR, r(4), OUNCE))).single()
        assertMeasured(GRAM, r(213), small)
        assertFalse(small.approximate)
        assertTrue(small.counted.isEmpty())
        val large = list(recipe(A, row(SUGAR, r(1), KILOGRAM)), recipe(B, row(SUGAR, r(1), POUND))).single()
        assertMeasured(GRAM, r(1_454), large)
        assertEquals("1.454 kg", amountOf(large).text(ENGLISH))
    }

    @Test
    fun `free-text ounces stand alone, and fluid ounces with millilitres add up exactly too`() {
        val milk = list(recipe(A, row(MILK, r(8), FLUID_OUNCE)), recipe(B, row(MILK, r(100), MILLILITRE))).single()
        // 8 fl oz = 236.588 ml; with 100 ml, 336.588 → 337 ml.
        assertMeasured(MILLILITRE, r(337), milk)
        assertFalse(milk.approximate)
        val own = list(recipe(A, free("butter", r(4), OUNCE))).single()
        assertMeasured(OUNCE, r(4), own)
    }

    @Test
    fun `ounces merge with cups by the entry's grams per gram, as grams do`() {
        // 1 cup of flour weighs 130 g; 8 oz weigh 226.8 g, the larger part: the line is in oz, 8 + 130/28.35 = 12.59 → 12½.
        val line = list(recipe(A, row(FLOUR, r(8), OUNCE)), recipe(B, row(FLOUR, r(1), CUP))).single()
        assertMeasured(OUNCE, r(25, 2), line)
        assertTrue(line.approximate)
        assertEquals("4½ oz", line.counted.single().countedAs.text(ENGLISH))
    }
}
