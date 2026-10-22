package app.recipefork.app.card

import app.recipefork.app.dictionary.IngredientEntryEntity
import app.recipefork.core.api.NutritionDto
import app.recipefork.core.nutrition.LeftOutReason
import app.recipefork.core.nutrition.LeftOutRow
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.Rational
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigInteger

/**
 * v0.40, T-294: the card hands the device's entries to the count with their mark — a child known to have no values of
 * its own is left out as «нет данных», not counted by its parent's values; its unmarked sibling still takes them.
 */
class CardNutritionMarkTest {
    private val entries = listOf(
        IngredientEntryEntity(1, "кукуруза", "corn", null, nutrition = NutritionDto(86.0, 3.27, 1.35, 18.7)),
        IngredientEntryEntity(2, "мини-кукуруза", "baby corn", 1, noNutrition = true),
        IngredientEntryEntity(3, "кукуруза замороженная", "frozen corn", 1),
    ).associateBy { it.id }

    private fun grams(position: Int, entry: Long) =
        Ingredient(position, entry, unitType = UnitType.EXACT, unit = QuantityUnit.GRAM, quantity = Rational.of(100))

    @Test
    fun aMarkedChildIsLeftOutAsNoDataAndItsSiblingTakesTheParentsValues() {
        val dish = dishNutrition(listOf(grams(0, 2), grams(1, 3)), entries, IngredientList(emptyList(), holdsProportions = true))
        assertEquals(listOf(LeftOutRow(0, LeftOutReason.NO_DATA)), dish?.leftOut)
        assertEquals(BigInteger.valueOf(90), dish?.whole?.kcal)
    }
}
