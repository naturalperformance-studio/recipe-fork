package app.recipefork.core.recipe

import app.recipefork.core.scaling.InterfaceLanguage
import app.recipefork.core.scaling.Rational
import app.recipefork.core.scaling.Rounding
import app.recipefork.core.scaling.ScaleFactor
import app.recipefork.core.timer.TimerName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/** A step's reference to a part of an ingredient: how the part is entered, checked, and read at a scale. */
class IngredientShareTest {
    private val garlic = Ingredient(0, null, null, "чеснок", UnitType.PIECE, QuantityUnit.PIECE, Rational.of(4), divisible = false)
    private val flour = Ingredient(1, null, null, "мука", UnitType.EXACT, QuantityUnit.KILOGRAM, Rational.of(3, 2))
    private val oil = Ingredient(2, null, null, "масло", UnitType.HOUSEHOLD, QuantityUnit.TABLESPOON, Rational.of(3))
    private val salt = Ingredient(3, null, null, "соль", UnitType.TASTE_UNMEASURED, null, null)
    private val milk = Ingredient(4, null, null, "молоко", UnitType.EXACT, QuantityUnit.MILLILITRE, null)
    private val ingredients = listOf(garlic, flour, oil, salt, milk)

    private fun part(amount: Rational, unit: QuantityUnit) = ShareReading.Accepted(IngredientShare(amount, unit))

    // A "to taste" row, or a row with no quantity, offers no part; every row holding a quantity does.
    @Test
    fun `a part is offered only for a row holding a quantity`() {
        assertTrue(IngredientShares.offered(garlic))
        assertTrue(IngredientShares.offered(flour))
        assertTrue(IngredientShares.offered(oil))
        assertFalse(IngredientShares.offered(salt))
        assertFalse(IngredientShares.offered(milk))
        assertFalse(IngredientShares.offered(oil.copy(quantity = null, enteredQuantity = "0")))
    }

    // DOM-019: a comma or a point is the decimal mark, no grouping; DOM-017: only its unit type's grid; above zero.
    @Test
    fun `a part is read like the quantity field, in the row's own unit`() {
        assertEquals(part(Rational.of(2), QuantityUnit.PIECE), IngredientShares.read("2", garlic))
        assertEquals(part(Rational.of(1, 2), QuantityUnit.KILOGRAM), IngredientShares.read("0,5", flour))
        assertEquals(part(Rational.of(1, 2), QuantityUnit.KILOGRAM), IngredientShares.read("0.5", flour))
        assertEquals(part(Rational.of(3, 2), QuantityUnit.TABLESPOON), IngredientShares.read("1,5", oil))
        assertEquals(part(Rational.of(3, 2), QuantityUnit.TABLESPOON), IngredientShares.read("1½", oil))
        assertEquals(part(Rational.of(1, 3), QuantityUnit.TABLESPOON), IngredientShares.read("1/3", oil))
        assertEquals(ShareReading.Empty, IngredientShares.read("  ", garlic))
        assertEquals(ShareReading.Refused(QuantityProblem.Separators), IngredientShares.read("1 000", flour))
        assertEquals(ShareReading.Refused(QuantityProblem.Separators), IngredientShares.read("1,0.5", flour))
        val wholePieces = ShareReading.Refused(QuantityProblem.NotAccepted(AcceptedValues.WHOLE_PIECES))
        assertEquals(wholePieces, IngredientShares.read("0", garlic))
        assertEquals(wholePieces, IngredientShares.read("-1", garlic))
        assertEquals(wholePieces, IngredientShares.read("1,5", garlic))
        assertEquals(wholePieces, IngredientShares.read("два", garlic))
        assertEquals(ShareReading.Refused(QuantityProblem.NotAccepted(AcceptedValues.WHOLE_GRAMS)), IngredientShares.read("0,0005", flour))
        assertEquals(ShareReading.Refused(QuantityProblem.NotAccepted(AcceptedValues.KITCHEN_FRACTIONS)), IngredientShares.read("0,2", oil))
        // A divisible piece holds halves.
        assertEquals(part(Rational.of(1, 2), QuantityUnit.PIECE), IngredientShares.read("0,5", garlic.copy(divisible = true)))
    }

    // A part never exceeds the ingredient's quantity; the whole itself is a part it may take.
    @Test
    fun `a part is no more than the whole`() {
        assertEquals(part(Rational.of(4), QuantityUnit.PIECE), IngredientShares.read("4", garlic))
        assertEquals(ShareReading.MoreThanWhole, IngredientShares.read("5", garlic))
        assertEquals(ShareReading.MoreThanWhole, IngredientShares.read("1,501", flour))
        assertEquals(part(Rational.of(3, 2), QuantityUnit.KILOGRAM), IngredientShares.read("1,5", flour))
    }

    // Parts are independent: two parts adding up past the whole are each accepted.
    @Test
    fun `parts are not checked against each other`() {
        val step = Step(
            0, "В маринад  и в соус ",
            listOf(IngredientReference(0, 10, IngredientShare(Rational.of(3), QuantityUnit.PIECE)), IngredientReference(0, 20, IngredientShare(Rational.of(3), QuantityUnit.PIECE))),
        )
        assertEquals(part(Rational.of(3), QuantityUnit.PIECE), IngredientShares.read("3", garlic))
        assertEquals("В маринад 3 шт и в соус 3 шт", StepText.render(step, ReferenceText.authored(ingredients, InterfaceLanguage.RUSSIAN)))
    }

    // A part larger than a whole changed since is told; one in grams of a row now in kilograms is compared as grams.
    @Test
    fun `a part larger than the whole as it stands is told`() {
        val two = IngredientShare(Rational.of(2), QuantityUnit.PIECE)
        assertFalse(IngredientShares.exceeds(two, garlic))
        assertTrue(IngredientShares.exceeds(two, garlic.copy(quantity = Rational.of(1))))
        assertFalse(IngredientShares.exceeds(two, garlic.copy(quantity = null)))
        val grams = IngredientShare(Rational.of(600), QuantityUnit.GRAM)
        assertFalse(IngredientShares.exceeds(grams, flour))
        assertTrue(IngredientShares.exceeds(grams, flour.copy(quantity = Rational.of(1, 2))))
    }

    // A part stays a part while it reads in the row's unit; any other unit makes it whole.
    @Test
    fun `a part fits its own unit and the exact units of its dimension`() {
        val grams = IngredientShare(Rational.of(600), QuantityUnit.GRAM)
        assertTrue(IngredientShares.fits(grams, QuantityUnit.KILOGRAM))
        assertTrue(IngredientShares.fits(grams, QuantityUnit.GRAM))
        assertFalse(IngredientShares.fits(grams, QuantityUnit.LITRE))
        assertFalse(IngredientShares.fits(grams, QuantityUnit.PIECE))
        assertFalse(IngredientShares.fits(grams, null))
        assertFalse(IngredientShares.fits(IngredientShare(Rational.ONE, QuantityUnit.TEASPOON), QuantityUnit.TABLESPOON))
        val step = Step(0, "Добавьте ", listOf(IngredientReference(0, 9, IngredientShare(Rational.of(2), QuantityUnit.PIECE))))
        assertEquals(
            listOf(step.copy(ingredientReferences = listOf(IngredientReference(0, 9)))),
            IngredientShares.madeWhole(listOf(step), 0),
        )
        assertNull(IngredientShares.madeWhole(listOf(step), 1))
    }

    // The editor reads the authored part, written as the list writes a quantity.
    @Test
    fun `a part reads at the authored scale`() {
        val russian = ReferenceText.authored(ingredients, InterfaceLanguage.RUSSIAN)
        val step = Step(
            0, "Чеснок:  в маринад,  в соус;  муки",
            listOf(
                IngredientReference(0, 8, IngredientShare(Rational.of(2), QuantityUnit.PIECE)),
                IngredientReference(0, 20, null),
                IngredientReference(1, 29, IngredientShare(Rational.of(600), QuantityUnit.GRAM)),
            ),
        )
        assertEquals("Чеснок: 2 шт в маринад, 4 шт в соус; 600 г муки", StepText.render(step, russian))
        assertEquals(
            StepRun.Reference(0, "2 шт", IngredientShare(Rational.of(2), QuantityUnit.PIECE)),
            StepText.runs(step, russian)[1],
        )
        assertEquals("1½ tbsp", ReferenceText.authored(oil, IngredientShare(Rational.of(3, 2), QuantityUnit.TABLESPOON), InterfaceLanguage.ENGLISH))
        assertEquals("1,2 кг", ReferenceText.authored(flour, IngredientShare(Rational.of(6, 5), QuantityUnit.KILOGRAM), InterfaceLanguage.RUSSIAN))
        // A part that no longer fits the row's unit reads as the whole.
        assertEquals("1,5 кг", ReferenceText.authored(flour, IngredientShare(Rational.of(2), QuantityUnit.PIECE), InterfaceLanguage.RUSSIAN))
    }

    // Scenario 3: at double servings a part of 2 of 4 cloves reads 4, through the same grid as the whole; a part is
    // rounded on its own, so it may carry a mark the whole does not.
    @Test
    fun `a part is scaled by the factor and rounded on its own grid`() {
        val two = IngredientShare(Rational.of(2), QuantityUnit.PIECE)
        val doubled = IngredientShares.display(two, garlic, ScaleFactor.of(Rational.of(2)))!!
        assertEquals(Rational.of(4), doubled.value)
        assertTrue(doubled.derived)
        assertEquals(Rounding.NONE, doubled.rounding)
        val threeQuarters = ScaleFactor.of(Rational.of(3, 4))
        val part = IngredientShares.display(IngredientShare(Rational.of(1), QuantityUnit.PIECE), garlic, threeQuarters)!!
        assertEquals(Rational.of(1), part.value)
        assertEquals(Rounding.UP, part.rounding)
        assertNull(IngredientShares.display(two, garlic.copy(divisible = null), ScaleFactor.ONE))
    }

    // FLOW-016 (8): a derived timer name reads a part as the screen shows it; what it was read from tells a part apart.
    @Test
    fun `a timer named from its step reads a part`() {
        val step = Step(0, "Обжарьте  до аромата", listOf(IngredientReference(0, 9, IngredientShare(Rational.of(2), QuantityUnit.PIECE))), Timer(2.minutes))
        assertEquals("Обжарьте 2 шт до аромата", TimerName.of(step, ReferenceText.authored(ingredients, InterfaceLanguage.RUSSIAN)))
        val whole = step.copy(ingredientReferences = listOf(IngredientReference(0, 9)))
        assertEquals("Обжарьте 4 шт до аромата", TimerName.of(whole, ReferenceText.authored(ingredients, InterfaceLanguage.RUSSIAN)))
        assertFalse(TimerName.source(step) == TimerName.source(whole))
    }
}
