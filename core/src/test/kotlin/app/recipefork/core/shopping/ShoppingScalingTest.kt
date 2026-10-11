package app.recipefork.core.shopping

import app.recipefork.core.recipe.QuantityUnit.CUP
import app.recipefork.core.recipe.QuantityUnit.GRAM
import app.recipefork.core.recipe.QuantityUnit.KILOGRAM
import app.recipefork.core.recipe.QuantityUnit.LITRE
import app.recipefork.core.recipe.QuantityUnit.MILLILITRE
import app.recipefork.core.recipe.QuantityUnit.PIECE
import app.recipefork.core.recipe.QuantityUnit.PINCH
import app.recipefork.core.recipe.QuantityUnit.TEASPOON
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.scaling.InterfaceLanguage
import app.recipefork.core.scaling.ScaledRecipe
import app.recipefork.core.scaling.Scaling
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Criterion 2: every recipe is scaled as the card scales it, the parts are added as exact fractions and rounded once on
 * the line's grid, and exact units add in their base unit and read on the magnitude ladder.
 */
class ShoppingScalingTest {

    @Test
    fun `a recipe asked for 6 servings gives its six-serving quantities`() {
        val line = list(recipe(A, row(FLOUR, r(300), GRAM), servings = 4, requested = r(6))).single()
        assertMeasured(GRAM, r(450), line)
    }

    @Test
    fun `a recipe asked for no count gives its authored quantities`() {
        assertMeasured(GRAM, r(300), list(recipe(A, row(FLOUR, r(300), GRAM), servings = 4)).single())
    }

    @Test
    fun `a recipe with no serving count is taken as authored, whatever count is asked for`() {
        val line = list(recipe(A, row(FLOUR, r(300), GRAM), servings = null, requested = r(8))).single()
        assertMeasured(GRAM, r(300), line)
    }

    @Test
    fun `each recipe is scaled by its own count`() {
        val line = list(
            recipe(A, row(FLOUR, r(300), GRAM), servings = 4, requested = r(2)),
            recipe(B, row(FLOUR, r(100), GRAM), servings = 2, requested = r(6)),
        ).single()
        assertMeasured(GRAM, r(150 + 300), line)
    }

    @Test
    fun `parts are added exactly and rounded once - three half grams are 2 g, not 3 g`() {
        // 1 g for 2 servings, at 1: ½ g each, which the card shows as 1 g.
        val half = { id: RecipeId -> recipe(id, row(SALT, r(1), GRAM), servings = 2, requested = r(1)) }
        assertMeasured(GRAM, r(2), list(half(A), half(B), half(C)).single())
    }

    @Test
    fun `two half eggs are one egg, not two`() {
        // 1 egg for 4 servings, at 2: ½ egg each, which the card rounds up to 1 on the indivisible grid.
        val half = { id: RecipeId -> recipe(id, row(EGG, r(1), PIECE), requested = r(2)) }
        assertMeasured(PIECE, r(1), list(half(A), half(B)).single())
    }

    @Test
    fun `a household sum is rounded on the kitchen fractions grid`() {
        // ⅓ tsp for 4 at 5 is 5/12 tsp, which the card shows as ½ (a tie, taken up): two such parts rounded first
        // would be 1 tsp. Added exactly they are 5/6 tsp.
        val line = list(
            recipe(A, row(SALT, r(1, 3), TEASPOON), requested = r(5)),
            recipe(B, row(SALT, r(1, 3), TEASPOON), requested = r(5)),
        ).single()
        // 5/6 lies between ¾ (0.75) and 1: nearer to ¾ by 1/12 against 1/6.
        assertMeasured(TEASPOON, r(3, 4), line)
    }

    @Test
    fun `a line from one recipe holds exactly what the card shows`() {
        val rows = listOf(
            row(42, r(7), PIECE, divisible = true),
            row(43, r(2, 3), CUP),
            row(44, r(333), GRAM),
            row(45, r(1), PINCH),
            row(46, r(3, 2), LITRE),
        )
        val counts = listOf(r(1, 4), r(1, 3), r(1, 2), r(2, 3), r(3, 4), r(1), r(3), r(4), r(7), r(10))
        for (count in counts) {
            val card = ScaledRecipe.of(4, rows.map { checkNotNull(Scaling.authored(it, null)) }, count).lines
            val shopping = ShoppingTotals.of(listOf(ShoppingRecipe(A, rows, 4, count)), emptyMap())
            rows.indices.forEach { i ->
                assertEquals(
                    amount(card[i].quantity.unit, checkNotNull(card[i].quantity.value)),
                    amountOf(shopping[i]),
                    "row $i at $count",
                )
            }
        }
    }

    @Test
    fun `300 g and 1_2 kg are one line of 1_5 kg`() {
        val line = list(recipe(A, row(FLOUR, r(300), GRAM)), recipe(B, row(FLOUR, r(6, 5), KILOGRAM))).single()
        assertMeasured(GRAM, r(1500), line)
        assertFalse(line.approximate)
        assertEquals("1.5 kg", amountOf(line).text(InterfaceLanguage.ENGLISH))
        assertEquals("1,5 кг", amountOf(line).text(InterfaceLanguage.RUSSIAN))
    }

    @Test
    fun `750 ml and half a litre are 1_25 l`() {
        val line = list(recipe(A, row(MILK, r(750), MILLILITRE)), recipe(B, row(MILK, r(1, 2), LITRE))).single()
        assertMeasured(MILLILITRE, r(1250), line)
        assertEquals("1.25 l", amountOf(line).text(InterfaceLanguage.ENGLISH))
    }

    @Test
    fun `a free-text row is scaled too`() {
        val line = list(recipe(A, free("Sourdough starter", r(100), GRAM), requested = r(8))).single()
        assertMeasured(GRAM, r(200), line)
    }
}
