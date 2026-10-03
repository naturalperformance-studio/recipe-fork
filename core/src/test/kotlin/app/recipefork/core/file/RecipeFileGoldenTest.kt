package app.recipefork.core.file

import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.IngredientReference
import app.recipefork.core.recipe.IngredientShare
import app.recipefork.core.recipe.MediaAttachment
import app.recipefork.core.recipe.MediaKind
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.RecipeLanguage
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.Timer
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.Rational
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes

/**
 * The recipe file's contract, pinned: a fixed recipe is written as exactly the `recipe.json` checked in beside this
 * test, and that document reads back as the recipe. A change of the format changes this file, on purpose or not at all.
 */
class RecipeFileGoldenTest {
    private val golden = RecipeFileContent(
        id = RecipeId("0b6f2c1e-6a43-4d8e-9a57-3f1c2b7d9e10"),
        title = "Борщ «домашний»",
        description = "Serve with sour cream.\nKeeps for two days.",
        tags = setOf(12, 3),
        language = RecipeLanguage.RU,
        servings = 4,
        ingredients = listOf(
            Ingredient(0, 101, qualifier = "ripe", unitType = UnitType.EXACT, unit = QuantityUnit.KILOGRAM, quantity = Rational.of(3, 2)),
            Ingredient(1, 102, unitType = UnitType.PIECE, unit = QuantityUnit.PIECE, quantity = Rational.of(5, 2), pieceWeight = Rational.of(120), divisible = true),
            Ingredient(2, 103, unitType = UnitType.PIECE, unit = QuantityUnit.PIECE, quantity = Rational.of(2), divisible = false),
            Ingredient(3, null, freeTextName = "smoked salt", unitType = UnitType.HOUSEHOLD, unit = QuantityUnit.TABLESPOON, quantity = Rational.of(1, 2)),
            Ingredient(4, 104, unitType = UnitType.TASTE_MEASURED, unit = QuantityUnit.PINCH, quantity = Rational.of(2)),
            Ingredient(5, 105, qualifier = "black", unitType = UnitType.TASTE_UNMEASURED, unit = null, quantity = null),
            Ingredient(6, null, freeTextName = "cream", unitType = UnitType.EXACT, unit = QuantityUnit.CUBIC_METRE, quantity = null, enteredQuantity = "-3"),
        ),
        steps = listOf(
            Step(0, "Fry the beet until soft", listOf(IngredientReference(0, 8)), Timer(12.minutes, "Beet")),
            Step(
                1, "Simmer with the salt and onions",
                listOf(IngredientReference(3, 14, IngredientShare(Rational.of(1, 4), QuantityUnit.TABLESPOON)), IngredientReference(1, 0)),
                Timer(95.minutes),
            ),
            Step(2, "Season and serve"),
        ),
        author = "anna",
        parent = FileParent(RecipeId("7d0e5c4a-2b1f-4e3d-8c6a-1f2e3d4c5b6a"), "Борщ", "boris"),
        media = listOf(
            FileMedia("m-1", MediaKind.PHOTO, MediaAttachment.Cover, "media/m-1.jpg"),
            FileMedia("m-2", MediaKind.VIDEO, MediaAttachment.ToStep(1), "media/m-2.mp4"),
        ),
    )

    private val expected = checkNotNull(javaClass.getResource("/recipe-file/golden-recipe.json")).readText()

    @Test
    fun aFixedRecipeIsWrittenAsTheCheckedInDocument() = assertEquals(expected.trimEnd(), RecipeFile.encode(golden))

    @Test
    fun theCheckedInDocumentReadsBackAsTheRecipe() = assertEquals(golden, RecipeFile.decode(expected))
}
