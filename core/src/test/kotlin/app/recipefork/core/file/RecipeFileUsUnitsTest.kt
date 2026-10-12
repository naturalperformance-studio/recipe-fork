package app.recipefork.core.file

import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.IngredientReference
import app.recipefork.core.recipe.IngredientShare
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.Rational
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** v0.35 T-246: the recipe file writes and reads the US units; a file holding them passes the import's check. */
class RecipeFileUsUnitsTest {
    private val recipe = Recipe.create(Instant.parse("2026-10-12T12:00:00Z"), owner = null).copy(
        title = "Pound cake",
        servings = 8,
        ingredients = listOf(
            Ingredient(0, null, freeTextName = "butter", unitType = UnitType.EXACT, unit = QuantityUnit.POUND, quantity = Rational.of(1)),
            Ingredient(1, null, freeTextName = "sugar", unitType = UnitType.EXACT, unit = QuantityUnit.OUNCE, quantity = Rational.of(29, 2)),
            Ingredient(2, null, freeTextName = "milk", unitType = UnitType.EXACT, unit = QuantityUnit.FLUID_OUNCE, quantity = Rational.of(3, 2)),
        ),
        steps = listOf(
            Step(0, "Cream the butter with the sugar", listOf(IngredientReference(0, 10, IngredientShare(Rational.of(1, 2), QuantityUnit.POUND)), IngredientReference(1, 20))),
            Step(1, "Add the milk", listOf(IngredientReference(2, 8, IngredientShare(Rational.of(1, 2), QuantityUnit.FLUID_OUNCE)))),
        ),
    )

    @Test
    fun `ounces, pounds and fluid ounces are written by name and read back`() {
        val content = RecipeFileContent.of(recipe, parentInBook = null, ownerNickname = null)
        val written = RecipeFile.encode(content)
        assertEquals(content, RecipeFile.decode(written))
        val document = Json.parseToJsonElement(written).jsonObject["recipe"]!!.jsonObject
        assertEquals(
            listOf("pound", "ounce", "fluid_ounce"),
            document["ingredients"]!!.jsonArray.map { it.jsonObject["unit"]!!.jsonPrimitive.content },
        )
        assertEquals(emptyList(), RecipeFileCheck.problems(content))
    }

    @Test
    fun `a part off its US grid is refused by the check, as any part off its grid is`() {
        val off = recipe.copy(
            steps = listOf(Step(0, "Cream the butter", listOf(IngredientReference(0, 10, IngredientShare(Rational.of(1, 8), QuantityUnit.OUNCE))))),
        )
        assertEquals(1, RecipeFileCheck.problems(RecipeFileContent.of(off, null, null)).size)
    }

    private fun versionOf(written: String) = Json.parseToJsonElement(written).jsonObject["format_version"]!!.jsonPrimitive.int

    private val metric = recipe.copy(
        ingredients = recipe.ingredients.map { it.copy(unit = if (it.unit == QuantityUnit.FLUID_OUNCE) QuantityUnit.MILLILITRE else QuantityUnit.GRAM, quantity = Rational.of(250)) },
        steps = listOf(Step(0, "Cream the butter", listOf(IngredientReference(0, 10, IngredientShare(Rational.of(100), QuantityUnit.GRAM))))),
    )

    // FLOW-064: a document holding a US unit is format 7, which an application reading at most 6 refuses as later.
    @Test
    fun `a document holding a US unit, in a row or only in a part, is written as format 7`() {
        assertEquals(7, RecipeFile.FORMAT_VERSION)
        assertEquals(7, versionOf(RecipeFile.encode(RecipeFileContent.of(recipe, null, null))))
        val onlyAPart = metric.copy(
            steps = listOf(Step(0, "Cream the butter", listOf(IngredientReference(0, 10, IngredientShare(Rational.of(4), QuantityUnit.OUNCE))))),
        )
        val written = RecipeFile.encode(RecipeFileContent.of(onlyAPart, null, null))
        assertEquals(7, versionOf(written))
        assertEquals(RecipeFileContent.of(onlyAPart, null, null), RecipeFile.decode(written))
    }

    // A metric document stays format 6, so an application of v0.34 still opens it; 6 reads as before, 8 is later.
    @Test
    fun `a document in metric units alone is written as format 6, and reads back`() {
        val content = RecipeFileContent.of(metric, null, null)
        val written = RecipeFile.encode(content)
        assertEquals(6, versionOf(written))
        assertEquals(RecipeFile.LAST_FORMAT_WITHOUT_US_UNITS, versionOf(written))
        assertEquals(content, RecipeFile.decode(written))
        val later = RecipeFile.encode(RecipeFileContent.of(recipe, null, null)).replace("\"format_version\": 7", "\"format_version\": 8")
        assertEquals(8, assertFailsWith<LaterRecipeFile> { RecipeFile.decode(later) }.formatVersion)
    }
}
