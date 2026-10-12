package app.recipefork.core.shopping

import app.recipefork.core.recipe.QuantityUnit.CUP
import app.recipefork.core.recipe.QuantityUnit.GRAM
import app.recipefork.core.recipe.QuantityUnit.MILLILITRE
import app.recipefork.core.recipe.QuantityUnit.OUNCE
import app.recipefork.core.recipe.QuantityUnit.PIECE
import app.recipefork.core.recipe.QuantityUnit.TEASPOON
import app.recipefork.core.recipe.UnitSystem
import app.recipefork.core.recipe.UnitSystem.METRIC
import app.recipefork.core.recipe.UnitSystem.US
import app.recipefork.core.scaling.InterfaceLanguage.ENGLISH
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val BEEF = 8L

/** The dictionary snapshot's measures (app/src/main/assets/dictionaries.json): flour with no ml measure, a liquid milk. */
private val KITCHEN: Map<Long, EntryMeasures> = mapOf(
    FLOUR to EntryMeasures(factors = mapOf("g" to 1.0, "tsp" to 3.0, "tbsp" to 10.0, "cup" to 130.0)),
    MILK to EntryMeasures(factors = mapOf("g" to 1.0, "ml" to 1.03, "tsp" to 5.0, "tbsp" to 15.0, "cup" to 258.0)),
    BEEF to EntryMeasures(factors = mapOf("g" to 1.0)),
    ONION to EntryMeasures(divisible = false, factors = mapOf("pc" to 150.0, "g" to 1.0)),
)

private fun shownList(system: UnitSystem, vararg recipes: ShoppingRecipe) = ShoppingTotals.of(recipes.toList(), KITCHEN, system)

/** v0.35 T-250: each measured line's merged total converted once into the reader's system, its note in that system. */
class ShoppingInSystemTest {

    // A cup recipe (2 cups of flour, 260 g) and a gram recipe (100 g): one line in cups, 2 + 100/130 cups.
    private val cups = recipe(A, row(FLOUR, r(2), CUP))
    private val grams = recipe(B, row(FLOUR, r(100), GRAM))

    @Test
    fun `Metric - a cup recipe and a gram recipe read in grams, the cups noted as grams`() {
        val line = shownList(METRIC, cups, grams).single()
        // The merged total, 360/130 cups, converted once: exactly 360 g — not the line's 2¾ cups converted (357.5 g).
        assertEquals(
            ShoppingShown(amount(GRAM, r(360)), approximate = true, counted = listOf(CountedPart(amount(CUP, r(2)), amount(GRAM, r(260))))),
            line.shown,
        )
    }

    @Test
    fun `US - the same list reads in cups, the grams noted as cups`() {
        val line = shownList(US, cups, grams).single()
        assertEquals(
            ShoppingShown(amount(CUP, r(11, 4)), approximate = true, counted = listOf(CountedPart(amount(GRAM, r(100)), amount(CUP, r(3, 4))))),
            line.shown,
        )
    }

    @Test
    fun `US - a line merged into grams reads in cups, every part not in cups noted in cups`() {
        // 1 cup (130 g) and 520 g: the line is in grams, 650 g = 5 cups; a teaspoon (3 g) is less than the cups' floor.
        val line = shownList(US, recipe(A, row(FLOUR, r(1), CUP)), recipe(B, row(FLOUR, r(520), GRAM), row(FLOUR, r(1), TEASPOON))).single()
        assertMeasured(GRAM, r(653), line)
        val shown = line.shown!!
        assertEquals(amount(CUP, r(5)), shown.amount)
        assertTrue(shown.approximate)
        assertEquals(listOf(amount(GRAM, r(520)), amount(TEASPOON, r(1))), shown.counted.map { it.original })
        assertEquals("4 cup", shown.counted[0].countedText(ENGLISH))
        assertEquals("< ¼ cup", shown.counted[1].countedText(ENGLISH))
    }

    @Test
    fun `a line shown exactly is not approximate and notes nothing`() {
        // Scenario 3: 500 g of beef, nothing in cups, reads 17½ oz; 8 oz read 227 g in Metric; free text converts too.
        assertEquals(ShoppingShown(amount(OUNCE, r(35, 2)), false, emptyList()), shownList(US, recipe(A, row(BEEF, r(500), GRAM))).single().shown)
        assertEquals(ShoppingShown(amount(GRAM, r(227)), false, emptyList()), shownList(METRIC, recipe(A, row(BEEF, r(8), OUNCE))).single().shown)
        assertEquals(amount(GRAM, r(113)), shownList(METRIC, recipe(A, free("butter", r(4), OUNCE))).single().shown!!.amount)
        // Grams and ounces of one entry add exactly in grams, and read in ounces in US: 100 g + 4 oz = 213.4 g → 7½ oz.
        val both = shownList(US, recipe(A, row(BEEF, r(100), GRAM)), recipe(B, row(BEEF, r(4), OUNCE))).single().shown!!
        assertEquals(ShoppingShown(amount(OUNCE, r(15, 2)), false, emptyList()), both)
    }

    @Test
    fun `a line of one part converted through the dictionary is approximate and notes nothing, as on the card`() {
        // Scenario 2: 1 cup of milk, a liquid, reads ≈ 250 ml in Metric; 2 cups of flour ≈ 260 g, from two recipes too.
        assertEquals(ShoppingShown(amount(MILLILITRE, r(250)), true, emptyList()), shownList(METRIC, recipe(A, row(MILK, r(1), CUP))).single().shown)
        val flour = shownList(METRIC, recipe(A, row(FLOUR, r(1), CUP)), recipe(B, row(FLOUR, r(1), CUP))).single().shown
        assertEquals(ShoppingShown(amount(GRAM, r(260)), true, emptyList()), flour)
    }

    @Test
    fun `a part in the shown dimension's other unit is not noted, while a household part is`() {
        // Metric: 2 cups of flour (260 g) and 4 oz (113.4 g) merge in cups, 373.4 g; the ounces go into grams exactly.
        val flour = shownList(METRIC, recipe(A, row(FLOUR, r(2), CUP)), recipe(B, row(FLOUR, r(4), OUNCE))).single().shown
        assertEquals(ShoppingShown(amount(GRAM, r(373)), true, listOf(CountedPart(amount(CUP, r(2)), amount(GRAM, r(260))))), flour)
        // US: an onion (150 g) and 200 g merge in grams, 350 g → 12½ oz; the grams go into ounces exactly.
        val onion = shownList(US, recipe(A, row(ONION, r(1), PIECE)), recipe(B, row(ONION, r(200), GRAM))).single().shown
        assertEquals(ShoppingShown(amount(OUNCE, r(25, 2)), true, listOf(CountedPart(amount(PIECE, r(1)), amount(OUNCE, r(11, 2))))), onion)
    }

    @Test
    fun `the line itself stays as written in every system, and to-taste and as-written lines have nothing shown`() {
        val asWritten = list(cups, grams, measures = KITCHEN)
        assertEquals(asWritten, shownList(US, cups, grams).map { it.copy(shown = null) })
        assertEquals(asWritten, shownList(METRIC, cups, grams).map { it.copy(shown = null) })
        assertNull(asWritten.single().shown)
        val taste = shownList(US, recipe(A, toTaste(FLOUR)), recipe(B, row(MILK, null, GRAM))).map { it.shown }
        assertEquals(listOf(null, null), taste)
    }
}
