package app.recipefork.core.recipe

import app.recipefork.core.scaling.InterfaceLanguage
import app.recipefork.core.scaling.Rational
import app.recipefork.core.scaling.ScaledRecipe
import app.recipefork.core.scaling.Scaling
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * DOM-022, DOM-023: groups and notes are presentation only — a recipe holding them scales, fixes an amount and reads
 * its step references exactly as the same recipe without them.
 */
class IngredientGroupsAndNotesTest {
    private val now = Instant.parse("2026-10-09T12:00:00Z")

    private val plain = Recipe.create(now, owner = null).copy(
        servings = 4,
        ingredients = listOf(
            Ingredient(0, 101, unitType = UnitType.EXACT, unit = QuantityUnit.GRAM, quantity = Rational.of(500)),
            Ingredient(1, null, freeTextName = "butter", unitType = UnitType.EXACT, unit = QuantityUnit.GRAM, quantity = Rational.of(200)),
            Ingredient(2, 102, unitType = UnitType.PIECE, unit = QuantityUnit.PIECE, quantity = Rational.of(3), divisible = false),
            Ingredient(3, 103, unitType = UnitType.HOUSEHOLD, unit = QuantityUnit.TABLESPOON, quantity = Rational.of(2)),
            Ingredient(4, 104, unitType = UnitType.TASTE_UNMEASURED, unit = null, quantity = null),
        ),
        steps = listOf(
            Step(0, "Rub  into ", listOf(IngredientReference(1, 4), IngredientReference(0, 10))),
            Step(1, "Beat  and glaze with ", listOf(IngredientReference(2, 5, IngredientShare(Rational.of(2), QuantityUnit.PIECE)), IngredientReference(3, 21))),
        ),
    )

    private val grouped = plain.copy(
        ingredients = plain.ingredients.map { it.copy(note = "note of ${it.position}") },
        ingredientGroups = listOf(IngredientGroup("Dough", 0), IngredientGroup("Glaze", 2)),
    )

    private fun scaled(recipe: Recipe, servings: Long) =
        ScaledRecipe.of(recipe.servings!!, recipe.ingredients.mapNotNull { Scaling.authored(it, null) }, Rational.of(servings))

    private fun steps(recipe: Recipe) = recipe.steps.map { step ->
        StepText.runs(step, ReferenceText.authored(recipe.ingredients, InterfaceLanguage.ENGLISH))
    }

    @Test
    fun aRecipeWithGroupsAndNotesScalesAsWithout() {
        listOf(1L, 4L, 6L, 10L).forEach { servings ->
            assertEquals(scaled(plain, servings).lines, scaled(grouped, servings).lines, "at $servings")
        }
        assertEquals(
            scaled(plain, 4).fix(0, Rational.of(300), QuantityUnit.GRAM).lines,
            scaled(grouped, 4).fix(0, Rational.of(300), QuantityUnit.GRAM).lines,
        )
        assertEquals(plain.ingredients.map { it.authoredQuantity }, grouped.ingredients.map { it.authoredQuantity })
        assertEquals(plain.ingredients.map { it.isComplete }, grouped.ingredients.map { it.isComplete })
    }

    @Test
    fun aRecipeWithGroupsAndNotesReadsItsReferencesAsWithout() {
        assertEquals(steps(plain), steps(grouped))
        assertEquals("Rub 200 g into 500 g", StepText.render(grouped.steps[0], ReferenceText.authored(grouped.ingredients, InterfaceLanguage.ENGLISH)))
    }

    // DOM-023: what the author types is the note trimmed; blank is none.
    @Test
    fun aNoteIsTheTypedTextTrimmedAndBlankIsNone() {
        assertEquals("finely chopped", Ingredient.noteOf("  finely chopped "))
        assertEquals("room temperature", Ingredient.noteOf("room temperature"))
        assertNull(Ingredient.noteOf("   "))
        assertNull(Ingredient.noteOf(""))
        assertNull(Ingredient.noteOf(null))
    }

    // DOM-022: a recipe written before groups existed has none; an edit keeps them.
    @Test
    fun aNewRecipeHasNoGroupsAndAnEditKeepsThem() {
        assertEquals(emptyList(), Recipe.create(now, owner = null).ingredientGroups)
        assertEquals(grouped.ingredientGroups, grouped.edited(now) { it.copy(title = "Pie") }.ingredientGroups)
    }
}
