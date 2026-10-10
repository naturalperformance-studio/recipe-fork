package app.recipefork.app.card

import app.recipefork.app.dictionary.IngredientEntryEntity
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.InterfaceLanguage
import app.recipefork.core.scaling.InterfaceLanguage.ENGLISH
import app.recipefork.core.scaling.InterfaceLanguage.RUSSIAN
import app.recipefork.core.scaling.Rational
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The card's ingredient line: named by the dictionary label in the interface language or the free-text name, its
 * quantity written by `QuantityText`; a row the author has not finished, or whose value is off its grid, is shown
 * by its name and whatever it holds, and never makes the line fail.
 */
class IngredientLineTest {
    private val entries = listOf(
        IngredientEntryEntity(1, "курица", "chicken", null),
        IngredientEntryEntity(2, "крылышки", "chicken wings", 1),
    ).associateBy { it.id }

    private fun row(
        unit: QuantityUnit?,
        quantity: Rational?,
        entry: Long? = null,
        name: String? = "Flour",
        qualifier: String? = null,
        divisible: Boolean? = null,
        type: UnitType = unit?.type ?: UnitType.TASTE_UNMEASURED,
    ) = Ingredient(0, entry, qualifier, if (entry == null) name else null, type, unit, quantity, divisible = divisible)

    private fun line(ingredient: Ingredient, language: InterfaceLanguage = ENGLISH) = ingredientLine(ingredient, entries, language)

    @Test
    fun aDictionaryEntryIsNamedByItsLabelInTheInterfaceLanguage() {
        val chicken = row(QuantityUnit.GRAM, Rational.of(500), entry = 1)
        assertEquals(IngredientLine("chicken", "500 g"), line(chicken, ENGLISH))
        assertEquals(IngredientLine("курица", "500 г"), line(chicken, RUSSIAN))
    }

    @Test
    fun aFreeTextNameIsShownAsWrittenAndAQualifierFollowsTheName() {
        assertEquals("Smoked paprika", line(row(QuantityUnit.PINCH, Rational.ONE, name = "Smoked paprika")).name)
        assertEquals("chicken wings, trimmed", line(row(QuantityUnit.GRAM, Rational.of(300), entry = 2, qualifier = " trimmed ")).name)
        assertEquals("крылышки, trimmed", line(row(QuantityUnit.GRAM, Rational.of(300), entry = 2, qualifier = "trimmed"), RUSSIAN).name)
    }

    @Test
    fun aRowThatNamesNothingTheDeviceHoldsHasNoName() {
        assertEquals(null, line(row(QuantityUnit.GRAM, Rational.of(5), entry = 99)).name)
        assertEquals(null, line(row(QuantityUnit.GRAM, Rational.of(5), name = "  ")).name)
        assertEquals(null, line(row(QuantityUnit.GRAM, Rational.of(5), name = null)).name)
    }

    @Test
    fun everyUnitTypeIsWrittenByQuantityTextInTheInterfaceLanguage() {
        val cases = listOf(
            row(QuantityUnit.KILOGRAM, Rational.of(3, 2)) to ("1.5 kg" to "1,5 кг"),
            row(QuantityUnit.LITRE, Rational.of(2)) to ("2 l" to "2 л"),
            row(QuantityUnit.GRAM, Rational.of(1_500)) to ("1.5 kg" to "1,5 кг"),
            row(QuantityUnit.PIECE, Rational.of(3), divisible = false) to ("3 pc" to "3 шт"),
            row(QuantityUnit.PIECE, Rational.of(3, 2), divisible = true) to ("1½ pc" to "1½ шт"),
            row(QuantityUnit.TEASPOON, Rational.of(1, 2)) to ("½ tsp" to "½ ч. л."),
            row(QuantityUnit.CUP, Rational.of(4, 3)) to ("1⅓ cup" to "1⅓ стакан"),
            row(QuantityUnit.PINCH, Rational.of(2)) to ("2 pinch" to "2 щепотка"),
        )
        cases.forEach { (ingredient, expected) ->
            assertEquals(expected.first, line(ingredient, ENGLISH).quantity)
            assertEquals(expected.second, line(ingredient, RUSSIAN).quantity)
        }
    }

    // T-217: "to taste" is no amount before the name — "to taste salt" — but words after it: "salt — to taste".
    @Test
    fun toTasteFollowsTheNameAndIsNoQuantity() {
        // As the domain holds it, with no unit, and with the unit named.
        listOf(row(null, null), row(QuantityUnit.TO_TASTE, null)).forEach {
            assertEquals(IngredientLine("Flour", null, toTaste = "to taste"), line(it, ENGLISH))
            assertEquals(IngredientLine("Flour", null, toTaste = "по вкусу"), line(it, RUSSIAN))
        }
        // A stray quantity on a "to taste" row is not shown as a number.
        assertEquals(IngredientLine("Flour", null, toTaste = "to taste"), line(row(QuantityUnit.TO_TASTE, Rational.of(2))))
        // At any scale it is the same words, and nothing about it is recalculated.
        val scaled = ingredientLine(row(QuantityUnit.TO_TASTE, null), entries, ENGLISH, app.recipefork.core.scaling.ScaleFactor.of(Rational.of(3)))
        assertEquals(IngredientLine("Flour", null, toTaste = "to taste"), scaled)
    }

    // T-217: a row with no amount yet reads by its name alone — "Grains of paradise", never "g Grains of paradise".
    @Test
    fun anIncompleteRowIsShownByItsNameAndWhateverItHolds() {
        // No quantity yet for a unit that carries one: no unit either, the name alone.
        assertEquals(IngredientLine("Flour", null), line(row(QuantityUnit.GRAM, null)))
        assertEquals(IngredientLine("Flour", null), line(row(QuantityUnit.TABLESPOON, null), RUSSIAN))
        assertEquals(IngredientLine("Flour", null), line(row(QuantityUnit.PIECE, null, divisible = true)))
        // No unit yet: the name alone.
        assertEquals(IngredientLine("Flour", null), line(row(null, Rational.of(2), type = UnitType.EXACT)))
        assertEquals(IngredientLine("Flour", null), line(row(null, null, type = UnitType.HOUSEHOLD)))
        // A piece whose divisibility is not stated yet.
        assertEquals(IngredientLine("Flour", "pc"), line(row(QuantityUnit.PIECE, Rational.of(2), divisible = null)))
    }

    @Test
    fun anOffGridValueIsNotWrittenAsAnotherNumber() {
        val offGrid = listOf(
            row(QuantityUnit.TEASPOON, Rational.of(3, 10)) to "tsp",
            row(QuantityUnit.CUP, Rational.of(1, 5)) to "cup",
            row(QuantityUnit.GRAM, Rational.of(1, 2)) to "g",
            row(QuantityUnit.KILOGRAM, Rational.of(1_000_000_5, 10_000)) to "kg",
            row(QuantityUnit.PIECE, Rational.of(1, 2), divisible = false) to "pc",
            row(QuantityUnit.PIECE, Rational.of(1, 3), divisible = true) to "pc",
            row(QuantityUnit.GRAM, Rational.ZERO) to "g",
            row(QuantityUnit.TEASPOON, Rational.of(-1)) to "tsp",
        )
        offGrid.forEach { (ingredient, unit) -> assertEquals(IngredientLine("Flour", unit), line(ingredient)) }
    }
}
