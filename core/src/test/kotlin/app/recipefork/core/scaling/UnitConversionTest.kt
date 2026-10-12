package app.recipefork.core.scaling

import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.QuantityUnit.CUP
import app.recipefork.core.recipe.QuantityUnit.FLUID_OUNCE
import app.recipefork.core.recipe.QuantityUnit.GRAM
import app.recipefork.core.recipe.QuantityUnit.KILOGRAM
import app.recipefork.core.recipe.QuantityUnit.LITRE
import app.recipefork.core.recipe.QuantityUnit.MILLILITRE
import app.recipefork.core.recipe.QuantityUnit.OUNCE
import app.recipefork.core.recipe.QuantityUnit.PIECE
import app.recipefork.core.recipe.QuantityUnit.PINCH
import app.recipefork.core.recipe.QuantityUnit.POUND
import app.recipefork.core.recipe.QuantityUnit.TABLESPOON
import app.recipefork.core.recipe.QuantityUnit.TEASPOON
import app.recipefork.core.recipe.QuantityUnit.TO_TASTE
import app.recipefork.core.recipe.UnitSystem
import app.recipefork.core.recipe.UnitSystem.METRIC
import app.recipefork.core.recipe.UnitSystem.US
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun q(n: Long, d: Long = 1) = Rational.of(n, d)

// The dictionary snapshot's measures of these entries (app/src/main/assets/dictionaries.json).
private val FLOUR = mapOf("g" to 1.0, "tsp" to 3.0, "tbsp" to 10.0, "cup" to 130.0)
private val BUTTER = mapOf("g" to 1.0, "tsp" to 5.0, "tbsp" to 14.0, "cup" to 227.0)
private val MILK = mapOf("g" to 1.0, "ml" to 1.03, "tsp" to 5.0, "tbsp" to 15.0, "cup" to 258.0)
private val OIL = mapOf("g" to 1.0, "ml" to 0.92, "tsp" to 5.0, "tbsp" to 14.0, "cup" to 230.0)
private val BEEF = mapOf("g" to 1.0)
private val SALT = mapOf("g" to 1.0, "tsp" to 7.0, "tbsp" to 20.0, "pinch" to 0.5)
private val WATERCRESS = mapOf("g" to 1.0, "cup" to 35.0)
private val FREE_TEXT = emptyMap<String, Double>()

private fun shown(
    unit: QuantityUnit,
    amount: Rational?,
    factors: Map<String, Double>,
    system: UnitSystem,
    factor: ScaleFactor = ScaleFactor.ONE,
    divisible: Boolean? = null,
) = UnitConversion.display(AuthoredQuantity(unit, amount, divisible), factor, factors, system)

private fun ShownQuantity.text() = (if (approximate) "≈ " else "") + QuantityText.of(quantity, InterfaceLanguage.ENGLISH)

/** v0.35 T-247: an amount shown in the reader's system. */
class UnitConversionTest {

    @Test
    fun `scenario 2 - Metric reads cups and ounces in grams and millilitres, spoons and pieces as written`() {
        assertEquals("≈ 260 g", shown(CUP, q(2), FLOUR, METRIC).text())
        assertEquals("113 g", shown(OUNCE, q(4), BUTTER, METRIC).text())
        // The dictionary's milk: 1 cup = 258 g at 1.03 g/ml, so ≈ 250 ml (the plan's "240 ml" assumed other measures).
        assertEquals("≈ 250 ml", shown(CUP, q(1), MILK, METRIC).text())
        assertEquals("1 tsp", shown(TEASPOON, q(1), SALT, METRIC).text())
        assertEquals("1½ tbsp", shown(TABLESPOON, q(3, 2), OIL, METRIC).text())
        assertEquals("1 pinch", shown(PINCH, q(1), SALT, METRIC).text())
        assertEquals("2 pc", shown(PIECE, q(2), FREE_TEXT, METRIC, divisible = false).text())
        assertEquals("to taste", shown(TO_TASTE, null, SALT, METRIC).text())
    }

    @Test
    fun `scenario 3 - US reads grams and millilitres in US units`() {
        assertEquals("≈ 2 cup", shown(GRAM, q(260), FLOUR, US).text())
        assertEquals("17½ oz", shown(GRAM, q(500), BEEF, US).text())
        assertEquals("1 cup", shown(MILLILITRE, q(250), MILK, US).text())
        assertEquals("1 tbsp", shown(MILLILITRE, q(15), OIL, US).text())
    }

    @Test
    fun `shared units, and amounts already in the chosen system, pass through as Scaling shows them`() {
        val factor = ScaleFactor.of(q(3, 2))
        listOf(
            AuthoredQuantity(TEASPOON, q(1)) to US, AuthoredQuantity(TABLESPOON, q(2)) to METRIC,
            AuthoredQuantity(PINCH, q(1)) to US, AuthoredQuantity(PIECE, q(1), divisible = true) to METRIC,
            AuthoredQuantity(TO_TASTE, null) to US, AuthoredQuantity(CUP, q(1)) to US,
            AuthoredQuantity(OUNCE, q(5)) to US, AuthoredQuantity(POUND, q(1)) to US, AuthoredQuantity(FLUID_OUNCE, q(3)) to US,
            AuthoredQuantity(GRAM, q(130)) to METRIC, AuthoredQuantity(LITRE, q(1)) to METRIC,
        ).forEach { (authored, system) ->
            listOf(ScaleFactor.ONE, factor).forEach { f ->
                val result = UnitConversion.display(authored, f, FLOUR, system)
                assertEquals(Scaling.display(authored, f), result.quantity, "$authored at $f in $system")
                assertFalse(result.approximate)
                assertEquals(Rational.ONE, result.writtenPerShown)
            }
        }
    }

    @Test
    fun `a cup with no measure, or free text, stays a cup in Metric, while exact units still convert`() {
        assertEquals("2 cup", shown(CUP, q(2), BEEF, METRIC).text())
        assertEquals("2 cup", shown(CUP, q(2), FREE_TEXT, METRIC).text())
        assertEquals("17½ oz", shown(GRAM, q(500), FREE_TEXT, US).text())
        assertEquals("113 g", shown(OUNCE, q(4), FREE_TEXT, METRIC).text())
        assertEquals("1 cup", shown(MILLILITRE, q(250), FREE_TEXT, US).text())
    }

    @Test
    fun `a liquid cup goes into millilitres by its cup and ml measures, a solid one into grams`() {
        assertEquals("≈ 250 ml", shown(CUP, q(1), OIL, METRIC).text())
        val milk = shown(CUP, q(1, 2), MILK, METRIC)
        assertEquals(MILLILITRE, milk.quantity.unit)
        assertEquals(q(125), milk.quantity.value)
        assertEquals(q(103, 25800), milk.writtenPerShown)
        assertEquals("≈ 114 g", shown(CUP, q(1, 2), BUTTER, METRIC).text())
    }

    @Test
    fun `from a quarter cup up a mass reads in cups, below it in tablespoons, below one in teaspoons`() {
        assertEquals("≈ ¼ cup", shown(GRAM, q(65, 2), FLOUR, US).text())
        assertEquals("≈ 3¼ tbsp", shown(GRAM, q(32), FLOUR, US).text())
        assertEquals("≈ 1 tbsp", shown(GRAM, q(10), FLOUR, US).text())
        assertEquals("≈ 2⅔ tsp", shown(GRAM, q(8), FLOUR, US).text())
        // No tablespoon measure: derived from the cup, 35 g / 16 per tbsp.
        assertEquals("≈ 2¼ tbsp", shown(GRAM, q(5), WATERCRESS, US).text())
        // No cup measure: spoons below 4 tbsp, ounces from there.
        assertEquals("≈ 1½ tsp", shown(GRAM, q(10), SALT, US).text())
        assertEquals("≈ 3¾ tbsp", shown(GRAM, q(75), SALT, US).text())
        assertEquals("3½ oz", shown(GRAM, q(100), SALT, US).text())
    }

    @Test
    fun `a volume reads in cups from a quarter cup by the US cup, exactly`() {
        assertEquals("¼ cup", shown(MILLILITRE, q(59_147_059_125, 1_000_000_000), FREE_TEXT, US).text())
        assertEquals("4 tbsp", shown(MILLILITRE, q(59), FREE_TEXT, US).text())
        assertEquals("1 tsp", shown(MILLILITRE, q(5), FREE_TEXT, US).text())
        assertEquals("4¼ cup", shown(LITRE, q(1), MILK, US).text())
        assertEquals(q(2_365_882_365, 10_000_000), shown(MILLILITRE, q(500), FREE_TEXT, US).writtenPerShown)
    }

    @Test
    fun `ounces and pounds across one pound, both ways`() {
        assertEquals("425 g", shown(OUNCE, q(15), BEEF, METRIC).text())
        assertEquals("454 g", shown(POUND, q(1), BEEF, METRIC).text())
        assertEquals("680 g", shown(POUND, q(3, 2), BEEF, METRIC).text())
        assertEquals("15½ oz", shown(GRAM, q(440), BEEF, US).text())
        assertEquals("1 lb", shown(GRAM, q(450), BEEF, US).text())
        assertEquals("16½ oz", shown(GRAM, q(470), BEEF, US).text())
        assertEquals("1½ lb", shown(GRAM, q(680), BEEF, US).text())
        assertEquals(q(1_000_000_000, 28_349_523_125), shown(OUNCE, q(4), BEEF, METRIC).writtenPerShown)
    }

    @Test
    fun `tiny amounts are held at the shown unit's floor, huge ones climb its ladder`() {
        val tiny = shown(OUNCE, q(1, 4), BUTTER, METRIC, ScaleFactor.of(q(1, 4)))
        assertEquals("2 g", tiny.text())
        assertEquals(Rounding.UP, tiny.quantity.rounding)
        val oz = shown(GRAM, q(1), BEEF, US)
        assertEquals("¼ oz", oz.text())
        assertTrue(oz.quantity.heldAtFloor)
        val tsp = shown(MILLILITRE, q(1), FREE_TEXT, US)
        assertEquals("¼ tsp", tsp.text())
        assertTrue(tsp.quantity.heldAtFloor)
        assertFalse(shown(GRAM, q(260), FLOUR, US).quantity.heldAtFloor)

        assertEquals("≈ 384⅔ cup", shown(KILOGRAM, q(50), FLOUR, US).text())
        assertEquals("110¼ lb", shown(KILOGRAM, q(50), BEEF, US).text())
        assertEquals("453.592 kg", shown(POUND, q(1_000), BEEF, METRIC).text())
        assertEquals("≈ 1.3 kg", shown(CUP, q(10), FLOUR, METRIC).text())
    }

    @Test
    fun `the exact scaled amount is converted, then rounded once on the shown grid`() {
        val factor = ScaleFactor.of(q(11, 10))
        // On its own grid 1.1 cups reads 1 cup (130 g); converted from the exact 1.1 cups it is 143 g.
        assertEquals(q(1), Scaling.display(AuthoredQuantity(CUP, q(1)), factor).value)
        assertEquals("≈ 143 g", shown(CUP, q(1), FLOUR, METRIC, factor).text())
        // 4.4 oz reads 4½ oz on its grid (128 g); the exact 4.4 oz is 124.74 g.
        assertEquals(q(9, 2), Scaling.display(AuthoredQuantity(OUNCE, q(4)), factor).value)
        assertEquals("125 g", shown(OUNCE, q(4), BEEF, METRIC, factor).text())
    }

    @Test
    fun `the derived mark and the rounding direction are kept for the shown value`() {
        val asWritten = shown(OUNCE, q(4), BUTTER, METRIC)
        assertFalse(asWritten.quantity.derived)
        assertEquals(Rounding.DOWN, asWritten.quantity.rounding)
        val doubled = shown(OUNCE, q(4), BUTTER, METRIC, ScaleFactor.of(q(2)))
        assertTrue(doubled.quantity.derived)
        assertEquals(q(227), doubled.quantity.value)
        assertEquals(Rounding.UP, doubled.quantity.rounding)
        val exact = shown(GRAM, q(260), FLOUR, US, ScaleFactor.of(q(1, 2)))
        assertEquals("≈ 1 cup", exact.text())
        assertEquals(Rounding.NONE, exact.quantity.rounding)
        assertTrue(exact.quantity.derived)
    }

    @Test
    fun `a step reference's part converts exactly as a line does`() {
        // A part written in g of a row now in oz reads in ounces in US, as the row does.
        assertEquals("1¾ oz", shown(GRAM, q(50), BEEF, US).text())
        assertEquals("57 g", shown(OUNCE, q(2), BEEF, METRIC).text())
    }

    @Test
    fun `a sum is converted once and otherwise rounded on its own grid`() {
        val sum = UnitConversion.convert(GRAM, q(260), FLOUR, US, derived = true)
        assertEquals("≈ 2 cup", sum.text())
        assertTrue(sum.quantity.derived)
        assertEquals(q(2), UnitConversion.convert(PIECE, q(3, 2), FREE_TEXT, US, divisible = false).quantity.value)
        assertEquals(q(1_500), UnitConversion.convert(KILOGRAM, q(3, 2), BEEF, METRIC).quantity.value)
        assertEquals(GRAM, UnitConversion.convert(KILOGRAM, q(3, 2), BEEF, METRIC).quantity.unit)
    }

    @Test
    fun `an entry's measure of a unit is exact, and none where it gives none`() {
        assertEquals(q(103, 100), measureOf(MILK, MILLILITRE))
        assertEquals(q(1_000), measureOf(BEEF, KILOGRAM))
        assertNull(measureOf(BEEF, CUP))
        assertNull(measureOf(mapOf("cup" to Double.NaN), CUP))
    }
}
