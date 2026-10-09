package app.recipefork.core.recipe

import app.recipefork.core.scaling.InterfaceLanguage
import app.recipefork.core.scaling.Rational
import kotlin.test.Test
import kotlin.test.assertEquals

class StepTextTest {
    private val flour = Ingredient(0, null, null, "мука", UnitType.EXACT, QuantityUnit.GRAM, Rational.of(200))
    private val salt = Ingredient(1, null, null, "соль", UnitType.TASTE_UNMEASURED, null, null)
    private val milk = Ingredient(2, null, null, "молоко", UnitType.EXACT, QuantityUnit.MILLILITRE, null)
    private val sugar = Ingredient(3, null, null, "сахар", UnitType.EXACT, QuantityUnit.KILOGRAM, Rational.of(3, 2))
    private val ingredients = listOf(flour, salt, milk, sugar)
    private val russian = ReferenceText.authored(ingredients, InterfaceLanguage.RUSSIAN)

    // Scenario 1 and FLOW-032 (1): the step's own words around the quantity, the quantity where the reference sits.
    @Test
    fun `a reference reads as its quantity at its place in the text`() {
        val step = Step(0, "Всыпьте  муки", listOf(IngredientReference(0, 8)))
        assertEquals("Всыпьте 200\u00A0г муки", StepText.render(step, russian))
        assertEquals(
            listOf(StepRun.Typed("Всыпьте "), StepRun.Reference(0, "200\u00A0г"), StepRun.Typed(" муки")),
            StepText.runs(step, russian),
        )
    }

    // FLOW-032 (6, 7): "to taste" in the interface language; a placeholder followed by the unit for no quantity yet.
    @Test
    fun `to taste and a quantity not yet entered`() {
        assertEquals("по вкусу", ReferenceText.authored(salt, InterfaceLanguage.RUSSIAN))
        assertEquals("to taste", ReferenceText.authored(salt, InterfaceLanguage.ENGLISH))
        assertEquals("${ReferenceText.PLACEHOLDER} мл", ReferenceText.authored(milk, InterfaceLanguage.RUSSIAN))
        assertEquals("${ReferenceText.PLACEHOLDER} ml", ReferenceText.authored(milk, InterfaceLanguage.ENGLISH))
    }

    // FLOW-032 (5) and SCALE-007 (2): the authored quantity, written as the ingredient list writes it.
    @Test
    fun `the authored quantity is written as the list writes it`() {
        assertEquals("1,5 кг", ReferenceText.authored(sugar, InterfaceLanguage.RUSSIAN))
        assertEquals("1.5 kg", ReferenceText.authored(sugar, InterfaceLanguage.ENGLISH))
        // Off its grid: as the quantity field writes it.
        assertEquals("2,5 г", ReferenceText.authored(flour.copy(quantity = Rational.of(5, 2)), InterfaceLanguage.RUSSIAN))
    }

    @Test
    fun `references at the start, at the end, side by side and to no ingredient`() {
        val step = Step(0, "и  в конце", listOf(IngredientReference(0, 0), IngredientReference(1, 2), IngredientReference(2, 10), IngredientReference(9, 5)))
        assertEquals("200\u00A0ги по\u00A0вкусу в конце${ReferenceText.PLACEHOLDER}\u00A0мл", StepText.render(step, russian))
        assertEquals("Mix.", StepText.render(Step(0, "Mix."), russian))
    }

    // A fork's references name its own positions, which hold its own ingredients.
    @Test
    fun `a forks references read its own ingredients`() {
        val step = Step(0, "Add  now", listOf(IngredientReference(1, 4)))
        val forkRows = listOf(flour, salt.copy(unitType = UnitType.EXACT, unit = QuantityUnit.GRAM, quantity = Rational.of(5)))
        assertEquals("Add 5\u00A0g now", StepText.render(step, ReferenceText.authored(forkRows, InterfaceLanguage.ENGLISH)))
    }
}
