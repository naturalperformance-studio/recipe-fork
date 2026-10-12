package app.recipefork.app.shopping

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.core.recipe.QuantityUnit.CUP
import app.recipefork.core.recipe.QuantityUnit.GRAM
import app.recipefork.core.recipe.QuantityUnit.KILOGRAM
import app.recipefork.core.recipe.QuantityUnit.PIECE
import app.recipefork.core.recipe.QuantityUnit.PINCH
import app.recipefork.core.recipe.QuantityUnit.TABLESPOON
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.scaling.InterfaceLanguage
import app.recipefork.core.scaling.Rational
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * T-229, scenarios 1–3 of v0.32: the lines the list shows, added up by `ShoppingTotals` from each recipe at its count,
 * named in the interface language, their amounts written by the line model — "≈", "+ to taste", "to taste" — with the
 * muted note of what an approximate line counted and the recipes each line comes from.
 */
@RunWith(AndroidJUnit4::class)
class ShoppingLinesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dictionary = SHOPPING_DICTIONARY.associateBy { it.id }

    private fun lines(vararg recipes: Pair<Recipe, Rational?>, language: InterfaceLanguage = InterfaceLanguage.ENGLISH) =
        shownLines(recipes.map { (recipe, count) -> ShoppingEntry(listed(recipe, count), recipe) }, dictionary, language, context.resources)

    private fun at(recipe: Recipe, count: Rational? = recipe.servings?.let { r(it.toLong()) }) = recipe to count

    // Scenario 1: the list holds each recipe's quantities at its serving count.
    @Test
    fun eachRecipeCountsAtItsServingCount() {
        val soup = recipe("Soup", 2, row(ONION, r(1), PIECE), row(FLOUR, r(100), GRAM))
        assertEquals(listOf("onion — 1 pc", "flour — 100 g"), lines(at(soup)).map { it.text })
        assertEquals(listOf("onion — 2 pc", "flour — 200 g"), lines(at(soup, r(4))).map { it.text })
        assertEquals(listOf("onion — 1 pc", "flour — 25 g"), lines(at(soup, r(1, 2))).map { it.text })
    }

    // Scenario 2: onions of two recipes are one line with the sum; 300 g and 1.2 kg of flour read "1.5 kg"; under each
    // line the recipes it comes from.
    @Test
    fun theSameIngredientIsOneLineWithTheSum() {
        val soup = recipe("Soup", 4, row(ONION, r(2), PIECE), row(FLOUR, r(300), GRAM))
        val pie = recipe("Onion pie", 4, row(ONION, r(1), PIECE), row(FLOUR, r(12, 10), KILOGRAM))
        val shown = lines(at(soup), at(pie))
        assertEquals(
            listOf(
                ShownLine("onion — 3 pc", null, "Soup, Onion pie"),
                ShownLine("flour — 1.5 kg", null, "Soup, Onion pie"),
            ),
            shown,
        )
    }

    // Scenario 3 (amounts written as the card writes them, "2 cup"): flour in grams and in cups is one line marked "≈",
    // with what was counted; a part below the line's floor is noted as below it; a part with no measure is a line of
    // its own.
    @Test
    fun partsInOtherUnitsAreCountedApproximately() {
        val bread = recipe("Bread", 4, row(FLOUR, r(300), GRAM), row(FLOUR, r(1), PINCH))
        val cake = recipe("Cake", 4, row(FLOUR, r(2), CUP))
        assertEquals(
            listOf(
                ShownLine("flour — ≈ 560 g", "2 cup counted as ≈ 260 g", "Bread, Cake"),
                ShownLine("flour — 1 pinch", null, "Bread"),
            ),
            lines(at(bread), at(cake)),
        )
        val tea = recipe("Tea cake", 4, row(SUGAR, r(1), CUP))
        val icing = recipe("Icing", 4, row(SUGAR, r(1), TABLESPOON))
        assertEquals(listOf(ShownLine("sugar — ≈ 1 cup", "1 tbsp counted as < ¼ cup", "Tea cake, Icing")), lines(at(tea), at(icing)))
    }

    // Scenario 3: free text stays its own lines; "salt — to taste" of two recipes is one line; a measured salt line
    // gains "+ to taste". A qualifier is no part of a line's name.
    @Test
    fun freeTextAndTasteLines() {
        val soup = recipe("Soup", 4, free("bay leaf", r(2), PIECE), row(SALT, null, null), row(FLOUR, r(50), GRAM, qualifier = "sifted"))
        val stew = recipe("Stew", 4, free("bay leaf", r(1), PIECE), row(SALT, null, null))
        assertEquals(
            listOf("bay leaf — 2 pc", "salt — to taste", "flour — 50 g", "bay leaf — 1 pc"),
            lines(at(soup), at(stew)).map { it.text },
        )
        val bread = recipe("Bread", 4, row(SALT, r(5), GRAM))
        assertEquals(listOf(ShownLine("salt — 5 g + to taste", null, "Bread, Stew")), lines(at(bread), at(stew)).filter { it.text.startsWith("salt") })
    }

    @Test
    @Config(qualifiers = "ru")
    fun inRussian() {
        val bread = recipe("Хлеб", 4, row(FLOUR, r(300), GRAM), row(SALT, r(5), GRAM))
        val cake = recipe("", 4, row(FLOUR, r(2), CUP), row(SALT, null, null), free("лавровый лист", r(1), PIECE))
        assertEquals(
            listOf(
                ShownLine("мука — ≈ 560 г", "2 стакан — посчитано как ≈ 260 г", "Хлеб, Рецепт без названия"),
                ShownLine("соль — 5 г + по вкусу", null, "Хлеб, Рецепт без названия"),
                ShownLine("лавровый лист — 1 шт", null, "Рецепт без названия"),
            ),
            lines(at(bread), at(cake), language = InterfaceLanguage.RUSSIAN),
        )
    }
}
