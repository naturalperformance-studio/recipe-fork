package app.recipefork.app.card

import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.InterfaceLanguage
import app.recipefork.core.scaling.InterfaceLanguage.ENGLISH
import app.recipefork.core.scaling.InterfaceLanguage.RUSSIAN
import app.recipefork.core.scaling.Rational
import app.recipefork.core.scaling.RequestedServings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * The card's ingredient list at a requested serving count: every row that can be scaled through one `ScaledRecipe`
 * from the exact factor (SCALE-001, SCALE-003), never to zero (SCALE-005); a row that cannot be scaled shown
 * unscaled beside them.
 */
class IngredientLinesTest {
    private fun row(
        name: String,
        unit: QuantityUnit?,
        quantity: Rational?,
        divisible: Boolean? = null,
        type: UnitType = unit?.type ?: UnitType.TASTE_UNMEASURED,
    ) = Ingredient(0, null, freeTextName = name, unitType = type, unit = unit, quantity = quantity, divisible = divisible)

    /** A recipe for 4. */
    private val forFour = listOf(
        row("Chicken", QuantityUnit.GRAM, Rational.of(500)),
        row("Eggs", QuantityUnit.PIECE, Rational.of(3), divisible = false),
        row("Salt", QuantityUnit.TEASPOON, Rational.ONE),
        row("Pepper", null, null),
        row("Lemons", QuantityUnit.PIECE, Rational.of(3, 2), divisible = true),
        row("Flour", QuantityUnit.KILOGRAM, Rational.of(3, 2)),
    )

    private fun quantities(
        requested: Rational?,
        ingredients: List<Ingredient> = forFour,
        authored: Int? = 4,
        language: InterfaceLanguage = ENGLISH,
    ) = ingredientLines(ingredients, emptyMap(), language, authored, requested).map { it.quantity }

    @Test
    fun atTheAuthoredCountEveryQuantityIsAsAuthored() {
        assertEquals(listOf("500 g", "3 pc", "1 tsp", "to taste", "1½ pc", "1.5 kg"), quantities(Rational.of(4)))
        assertEquals(listOf("500 g", "3 pc", "1 tsp", "to taste", "1½ pc", "1.5 kg"), quantities(null))
    }

    @Test
    fun sixServingsOfARecipeForFourShows750gAndTheRestByTheSameFactor() {
        assertEquals(listOf("750 g", "5 pc", "1½ tsp", "to taste", "2½ pc", "2.25 kg"), quantities(Rational.of(6)))
        assertEquals(listOf("750 г", "5 шт", "1½ ч. л.", "по вкусу", "2½ шт", "2,25 кг"), quantities(Rational.of(6), language = RUSSIAN))
    }

    @Test
    fun oneServingShowsOneEggAndAQuarterTeaspoon() {
        assertEquals(listOf("125 g", "1 pc", "¼ tsp", "to taste", "½ pc", "375 g"), quantities(Rational.ONE))
    }

    @Test
    fun everyQuantityIsComputedFromTheExactFactorOfTheRequestedCount() {
        // ⅔ of a serving of a recipe for 4 is a factor of exactly 1/6: 500 g → 83⅓ g → 83 g; 1.5 kg → 250 g.
        // A factor rounded to two places (0.17) would show 85 g and 255 g.
        assertEquals(listOf("83 g", "1 pc", "¼ tsp", "to taste", "½ pc", "250 g"), quantities(Rational.of(2, 3)))
        // 7 of 4 is 1.75: 500 g → 875 g, 1.5 kg → 2.625 kg; 3 eggs → 5.25 → 5.
        assertEquals(listOf("875 g", "5 pc", "1¾ tsp", "to taste", "2½ pc", "2.625 kg"), quantities(Rational.of(7)))
    }

    @Test
    fun atNoCountDoesALineShowZero() {
        val counts = generateSequence(RequestedServings.MIN) { RequestedServings.increased(it) }.take(25).toList()
        counts.forEach { count ->
            quantities(count).filterNotNull().forEach { text ->
                assertFalse("$count: $text", text.startsWith("0"))
            }
        }
        // At ¼ of a serving every line is at its floor or above, never zero (SCALE-005).
        assertEquals(listOf("31 g", "1 pc", "¼ tsp", "to taste", "½ pc", "94 g"), quantities(Rational.of(1, 4)))
    }

    @Test
    fun returningToTheAuthoredCountShowsTheAuthoredValuesExactly() {
        val authored = quantities(Rational.of(4))
        var count = Rational.of(4)
        repeat(4) { count = RequestedServings.decreased(count) }
        assertEquals(Rational.of(3, 4), count)
        repeat(4) { count = RequestedServings.increased(count) }
        assertEquals(authored, quantities(count))
    }

    @Test
    fun aRecipeWithNoServingCountShowsItsQuantitiesAsAuthored() {
        assertEquals(listOf("500 g", "3 pc", "1 tsp", "to taste", "1½ pc", "1.5 kg"), quantities(null, authored = null))
        assertEquals(listOf("500 g", "3 pc", "1 tsp", "to taste", "1½ pc", "1.5 kg"), quantities(Rational.of(6), authored = null))
    }

    @Test
    fun anIncompleteOrOffGridRowIsShownUnscaledAndTheOthersStillScale() {
        val mixed = listOf(
            row("Chicken", QuantityUnit.GRAM, Rational.of(500)),
            row("Flour", QuantityUnit.GRAM, null),
            row("Sugar", QuantityUnit.TEASPOON, Rational.of(3, 10)),
            row("Eggs", QuantityUnit.PIECE, Rational.of(2), divisible = null),
            row("Oil", null, Rational.of(2), type = UnitType.EXACT),
            row("Butter", QuantityUnit.GRAM, Rational.of(1, 2)),
            row("Eggs", QuantityUnit.PIECE, Rational.of(3), divisible = false),
            row("Yeast", QuantityUnit.GRAM, Rational.of(15, 2)),
        )
        assertEquals(listOf("750 g", "g", "tsp", "pc", null, "g", "5 pc", "g"), quantities(Rational.of(6), mixed))
        assertEquals(listOf("125 g", "g", "tsp", "pc", null, "g", "1 pc", "g"), quantities(Rational.ONE, mixed))
        assertEquals(
            listOf("Chicken", "Flour", "Sugar", "Eggs", "Oil", "Butter", "Eggs", "Yeast"),
            ingredientLines(mixed, emptyMap(), ENGLISH, 4, Rational.ONE).map { it.name },
        )
    }

    @Test
    fun theCardDecidesWhatCanBeScaledInOnePlace() {
        assertEquals(null, scalableQuantity(row("Flour", QuantityUnit.GRAM, null), emptyMap()))
        assertEquals(null, scalableQuantity(row("Sugar", QuantityUnit.TEASPOON, Rational.of(3, 10)), emptyMap()))
        assertEquals(null, scalableQuantity(row("Eggs", QuantityUnit.PIECE, Rational.of(2), divisible = null), emptyMap()))
        assertEquals(null, scalableQuantity(row("Eggs", QuantityUnit.PIECE, Rational.of(1, 2), divisible = false), emptyMap()))
        assertEquals(null, scalableQuantity(row("Butter", QuantityUnit.GRAM, Rational.ZERO), emptyMap()))
        assertEquals(null, scalableQuantity(row("Yeast", QuantityUnit.GRAM, Rational.of(15, 2)), emptyMap()))
        assertEquals(null, scalableQuantity(row("Oil", null, Rational.of(2), type = UnitType.EXACT), emptyMap()))
        assertEquals(QuantityUnit.TO_TASTE, scalableQuantity(row("Pepper", null, null), emptyMap())?.unit)
        assertEquals(Rational.of(500), scalableQuantity(row("Chicken", QuantityUnit.GRAM, Rational.of(500)), emptyMap())?.quantity)
    }
}
