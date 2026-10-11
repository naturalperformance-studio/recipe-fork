package app.recipefork.core.shopping

import app.recipefork.core.recipe.QuantityUnit.CUP
import app.recipefork.core.recipe.QuantityUnit.GRAM
import app.recipefork.core.recipe.QuantityUnit.KILOGRAM
import app.recipefork.core.recipe.QuantityUnit.MILLILITRE
import app.recipefork.core.recipe.QuantityUnit.PIECE
import app.recipefork.core.recipe.QuantityUnit.PINCH
import app.recipefork.core.recipe.QuantityUnit.TABLESPOON
import app.recipefork.core.recipe.QuantityUnit.TEASPOON
import app.recipefork.core.scaling.InterfaceLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Criterion 3: one entry in one unit adds exactly; in several units it merges, by the entry's grams per unit, into one
 * approximate line in the unit of its largest part, naming each part it counted; a unit with no measure stays apart.
 */
class ShoppingMergeTest {

    @Test
    fun `one household unit adds exactly`() {
        val line = list(recipe(A, row(SALT, r(1, 2), TEASPOON)), recipe(B, row(SALT, r(3, 4), TEASPOON))).single()
        assertMeasured(TEASPOON, r(5, 4), line)
        assertFalse(line.approximate)
        assertTrue(line.counted.isEmpty())
    }

    @Test
    fun `household units of an entry with no measures stay apart, each exact`() {
        val lines = list(recipe(A, row(42, r(1), TEASPOON)), recipe(B, row(42, r(1), TABLESPOON)))
        assertEquals(2, lines.size)
        assertMeasured(TEASPOON, r(1), lines[0])
        assertMeasured(TABLESPOON, r(1), lines[1])
        lines.forEach { assertFalse(it.approximate) }
    }

    @Test
    fun `300 g and 2 cups of flour are one approximate line of 560 g, naming the cups it counted`() {
        val line = list(recipe(A, row(FLOUR, r(300), GRAM)), recipe(B, row(FLOUR, r(2), CUP))).single()
        assertMeasured(GRAM, r(560), line)
        assertTrue(line.approximate)
        assertEquals(listOf(CountedPart(amount(CUP, r(2)), amount(GRAM, r(260)))), line.counted)
        assertEquals("2 cup", line.counted.single().original.text(InterfaceLanguage.ENGLISH))
        assertEquals("260 g", line.counted.single().countedAs.text(InterfaceLanguage.ENGLISH))
        assertEquals(listOf(A, B), line.recipes)
    }

    @Test
    fun `the line is in the unit of its largest part - 100 g and 2 cups are about 2¾ cups`() {
        // 2 cups weigh 260 g, more than 100 g; 100 g is 10/13 of a cup, which the kitchen grid reads as ¾.
        val line = list(recipe(A, row(FLOUR, r(100), GRAM)), recipe(B, row(FLOUR, r(2), CUP))).single()
        assertMeasured(CUP, r(11, 4), line)
        assertTrue(line.approximate)
        assertEquals(listOf(CountedPart(amount(GRAM, r(100)), amount(CUP, r(3, 4)))), line.counted)
    }

    @Test
    fun `a part is counted as its grams over the grams of the line's unit`() {
        // 500 ml of flour weigh 300 g at 0.6 g per ml, more than 100 g; 100 g is 100 / 0.6 ≈ 166.7 ml.
        val line = list(recipe(A, row(FLOUR, r(500), MILLILITRE)), recipe(B, row(FLOUR, r(100), GRAM))).single()
        assertMeasured(MILLILITRE, r(667), line)
        assertEquals(listOf(CountedPart(amount(GRAM, r(100)), amount(MILLILITRE, r(167)))), line.counted)
    }

    @Test
    fun `a kilogram is counted through its grams`() {
        val line = list(recipe(A, row(FLOUR, r(1), KILOGRAM)), recipe(B, row(FLOUR, r(1), CUP))).single()
        assertMeasured(GRAM, r(1130), line)
        assertEquals("1.13 kg", amountOf(line).text(InterfaceLanguage.ENGLISH))
        assertEquals(listOf(CountedPart(amount(CUP, r(1)), amount(GRAM, r(130)))), line.counted)
    }

    @Test
    fun `rows in one unit are one part, added before it is counted`() {
        val line = list(
            recipe(A, row(FLOUR, r(1), CUP), row(FLOUR, r(300), GRAM)),
            recipe(B, row(FLOUR, r(1), CUP)),
        ).single()
        assertMeasured(GRAM, r(560), line)
        assertEquals(listOf(CountedPart(amount(CUP, r(2)), amount(GRAM, r(260)))), line.counted)
        assertEquals(listOf(A, B), line.recipes)
    }

    @Test
    fun `a merged line is rounded once, from the exact parts`() {
        // 201 g for 4 servings at 1 is 50¼ g; ⅓ cup at 129.9 g a cup is 43.3 g. Exactly 93.55 g → 94 g; rounded part
        // by part it would read 50 g + 43 g = 93 g.
        val measures = mapOf(50L to EntryMeasures(factors = mapOf("g" to 1.0, "cup" to 129.9)))
        val line = list(
            recipe(A, row(50, r(201), GRAM), requested = r(1)),
            recipe(B, row(50, r(1, 3), CUP)),
            measures = measures,
        ).single()
        assertMeasured(GRAM, r(94), line)
        assertEquals(listOf(CountedPart(amount(CUP, r(1, 3)), amount(GRAM, r(43)))), line.counted)
    }

    @Test
    fun `a unit the entry has no measure for stays a line of its own, exact`() {
        val lines = list(
            recipe(A, row(FLOUR, r(300), GRAM), row(FLOUR, r(1), PINCH)),
            recipe(B, row(FLOUR, r(2), CUP), row(FLOUR, r(2), PINCH)),
        )
        assertEquals(2, lines.size)
        assertMeasured(GRAM, r(560), lines[0])
        assertTrue(lines[0].approximate)
        assertMeasured(PINCH, r(3), lines[1])
        assertFalse(lines[1].approximate)
        assertTrue(lines[1].counted.isEmpty())
        assertEquals(listOf(A, B), lines[1].recipes)
    }

    @Test
    fun `without a measure for one of two units nothing merges`() {
        val noCup = mapOf(FLOUR to EntryMeasures(factors = mapOf("g" to 1.0)))
        val lines = list(recipe(A, row(FLOUR, r(300), GRAM)), recipe(B, row(FLOUR, r(2), CUP)), measures = noCup)
        assertEquals(2, lines.size)
        assertMeasured(GRAM, r(300), lines[0])
        assertMeasured(CUP, r(2), lines[1])
        lines.forEach { assertFalse(it.approximate) }
    }

    @Test
    fun `at a tie the unit that came first leads`() {
        // 260 g and 2 cups weigh the same.
        val gramsFirst = list(recipe(A, row(FLOUR, r(260), GRAM)), recipe(B, row(FLOUR, r(2), CUP))).single()
        assertMeasured(GRAM, r(520), gramsFirst)
        val cupsFirst = list(recipe(B, row(FLOUR, r(2), CUP)), recipe(A, row(FLOUR, r(260), GRAM))).single()
        assertMeasured(CUP, r(4), cupsFirst)
    }

    @Test
    fun `a piece line is rounded on the entry's piece grid`() {
        // 2 onions weigh 300 g, more than 100 g; 100 g is ⅔ of an onion, and onions are indivisible: 2⅔ → 3.
        val line = list(recipe(A, row(ONION, r(2), PIECE)), recipe(B, row(ONION, r(100), GRAM))).single()
        assertMeasured(PIECE, r(3), line)
        assertEquals(listOf(CountedPart(amount(GRAM, r(100)), amount(PIECE, r(1)))), line.counted)
    }
}
