package app.recipefork.core.file

import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.IngredientReference
import app.recipefork.core.recipe.MediaAttachment
import app.recipefork.core.recipe.MediaKind
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.Timer
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.Rational
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * decisions/20260928-recipe-file-is-a-zip.md: an importer validates the document against the rules a sending must
 * pass — DOM-012's limits, DOM-016's units, DOM-017's quantities — and the shape of a recipe. Each rule is broken once.
 */
class RecipeFileCheckTest {
    private val valid = RecipeFileContent(
        id = RecipeId("0b6f2c1e-6a43-4d8e-9a57-3f1c2b7d9e10"),
        title = "Borscht",
        description = "Serve hot.",
        tags = setOf(3),
        language = null,
        servings = 4,
        ingredients = listOf(
            Ingredient(0, 101, qualifier = "ripe", unitType = UnitType.EXACT, unit = QuantityUnit.KILOGRAM, quantity = Rational.of(3, 2)),
            Ingredient(1, 102, unitType = UnitType.PIECE, unit = QuantityUnit.PIECE, quantity = Rational.of(5, 2), pieceWeight = Rational.of(120), divisible = true),
            Ingredient(2, null, freeTextName = "salt", unitType = UnitType.HOUSEHOLD, unit = QuantityUnit.TEASPOON, quantity = Rational.of(1, 3)),
            Ingredient(3, null, freeTextName = "pepper", unitType = UnitType.TASTE_UNMEASURED, unit = null, quantity = null),
            Ingredient(4, 104, unitType = UnitType.TASTE_MEASURED, unit = QuantityUnit.PINCH, quantity = Rational.of(2)),
        ),
        steps = listOf(
            Step(0, "Fry the beet", listOf(IngredientReference(0, 8)), Timer(12.minutes, "Beet")),
            Step(1, "Season", timer = Timer((99 * 60 + 59).minutes)),
        ),
        author = "anna",
        parent = FileParent(RecipeId("p-1"), "Soup", "boris"),
        media = listOf(
            FileMedia("m-1", MediaKind.PHOTO, MediaAttachment.Cover, "media/m-1.jpg"),
            FileMedia("m-2", MediaKind.VIDEO, MediaAttachment.ToStep(1), "media/m-2.mp4"),
        ),
    )

    private fun refused(content: RecipeFileContent) =
        assertTrue(RecipeFileCheck.problems(content).isNotEmpty(), "refused: $content")

    private fun row(position: Int, change: (Ingredient) -> Ingredient) =
        valid.copy(ingredients = valid.ingredients.map { if (it.position == position) change(it) else it })

    @Test
    fun aRecipeWithinEveryRuleHasNoProblem() = assertEquals(emptyList(), RecipeFileCheck.problems(valid))

    // DOM-012: the limits are inclusive; one over each is refused.
    @Test
    fun theLimitsOfDom012() {
        val fifty = (0 until 50).map { Ingredient(it, null, freeTextName = "i$it", unitType = UnitType.EXACT, unit = QuantityUnit.GRAM, quantity = Rational.of(1)) }
        val fiftySteps = (0 until 50).map { Step(it, "s".repeat(2_000)) }
        val atLimits = valid.copy(
            title = "t".repeat(120), description = "d".repeat(2_000), ingredients = fifty, steps = fiftySteps,
            media = emptyList(),
        )
        assertEquals(emptyList(), RecipeFileCheck.problems(atLimits))
        assertEquals(emptyList(), RecipeFileCheck.problems(valid.copy(steps = listOf(Step(0, "x", timer = Timer(5.minutes, "n".repeat(60)))), media = emptyList())))
        refused(atLimits.copy(title = "t".repeat(121)))
        refused(atLimits.copy(description = "d".repeat(2_001)))
        refused(atLimits.copy(ingredients = fifty + Ingredient(50, null, freeTextName = "x", unitType = UnitType.EXACT, unit = QuantityUnit.GRAM, quantity = Rational.of(1))))
        refused(atLimits.copy(steps = fiftySteps + Step(50, "x")))
        refused(atLimits.copy(steps = listOf(Step(0, "s".repeat(2_001)))))
        refused(valid.copy(steps = listOf(Step(0, "x", timer = Timer(5.minutes, "n".repeat(61)))), media = emptyList()))
    }

    // DOM-016: a unit is of the type its row states; a row to taste holds no unit of measure and no quantity.
    @Test
    fun theUnitsOfDom016() {
        refused(row(0) { it.copy(unit = QuantityUnit.TEASPOON) })
        refused(row(2) { it.copy(unit = null) })
        refused(row(3) { it.copy(unit = QuantityUnit.GRAM) })
        refused(row(3) { it.copy(quantity = Rational.of(1)) })
        assertEquals(emptyList(), RecipeFileCheck.problems(row(3) { it.copy(unit = QuantityUnit.TO_TASTE) }))
    }

    // DOM-017: a quantity above zero, on its unit type's grid; a row holding none, or only what was typed, is refused.
    @Test
    fun theQuantitiesOfDom017() {
        refused(row(0) { it.copy(unit = QuantityUnit.GRAM, quantity = Rational.of(15, 2)) })
        refused(row(0) { it.copy(quantity = Rational.ZERO) })
        refused(row(0) { it.copy(quantity = Rational.of(-1)) })
        refused(row(0) { it.copy(quantity = null) })
        refused(row(0) { it.copy(quantity = null, enteredQuantity = "-3") })
        refused(row(1) { it.copy(divisible = false) })
        refused(row(1) { it.copy(divisible = null) })
        refused(row(2) { it.copy(quantity = Rational.of(3, 10)) })
        refused(row(1) { it.copy(pieceWeight = Rational.ZERO) })
        refused(row(0) { it.copy(divisible = true) })
    }

    // DOM-009: an ingredient is named by an entry or by words, never both, never neither.
    @Test
    fun aRowIsNamedOnce() {
        refused(row(0) { it.copy(freeTextName = "beet") })
        refused(row(2) { it.copy(freeTextName = null) })
        refused(row(2) { it.copy(freeTextName = " ") })
        refused(row(2) { it.copy(qualifier = "fine") })
    }

    @Test
    fun theRecipesShape() {
        refused(valid.copy(ingredients = valid.ingredients.drop(1)))
        refused(valid.copy(steps = valid.steps.reversed()))
        refused(valid.copy(servings = 0))
        refused(valid.copy(id = RecipeId("../etc")))
        refused(valid.copy(id = RecipeId("")))
        refused(valid.copy(parent = FileParent(RecipeId("a/b"), null, null)))
        refused(valid.copy(author = " "))
        refused(valid.copy(author = "a".repeat(101)))
        refused(valid.copy(steps = listOf(Step(0, "Fry", listOf(IngredientReference(9, 0))), valid.steps[1])))
        refused(valid.copy(steps = listOf(Step(0, "Fry", listOf(IngredientReference(0, 4))), valid.steps[1])))
        refused(valid.copy(steps = listOf(Step(0, "Fry", listOf(IngredientReference(0, 0), IngredientReference(1, 0))), valid.steps[1])))
        refused(valid.copy(steps = listOf(Step(0, "Fry", timer = Timer(0.minutes)), valid.steps[1])))
        refused(valid.copy(steps = listOf(Step(0, "Fry", timer = Timer((100 * 60).minutes)), valid.steps[1])))
        refused(valid.copy(steps = listOf(Step(0, "Fry", timer = Timer(90.seconds)), valid.steps[1])))
    }

    // MEDIA-001 and the ADR: one photograph as cover, one item per step held, each in an entry of its own under media/.
    @Test
    fun theMediaItNames() {
        val cover = valid.media[0]
        refused(valid.copy(media = valid.media + cover.copy(mediaId = "m-3", entry = "media/m-3.jpg")))
        refused(valid.copy(media = listOf(cover.copy(kind = MediaKind.VIDEO))))
        refused(valid.copy(media = listOf(cover.copy(attachment = MediaAttachment.ToStep(7)))))
        refused(valid.copy(media = valid.media + valid.media[1].copy(mediaId = "m-3", entry = "media/m-3.mp4")))
        refused(valid.copy(media = valid.media + valid.media[1].copy(attachment = MediaAttachment.ToStep(0), entry = "media/m-3.mp4")))
        refused(valid.copy(media = valid.media + valid.media[1].copy(mediaId = "m-3", attachment = MediaAttachment.ToStep(0))))
        listOf("m-1.jpg", "/media/m-1.jpg", "media/../m-1.jpg", "media/a/m-1.jpg", "media/", "media\\m-1.jpg", "other/m-1.jpg")
            .forEach { refused(valid.copy(media = listOf(cover.copy(entry = it)))) }
    }

    @Test
    fun entryNames() {
        listOf("recipe.json", "media/m-1.jpg", "media/", "notes/readme.txt").forEach { assertTrue(RecipeFileCheck.isSafeEntryName(it), it) }
        listOf("/recipe.json", "../recipe.json", "media/../../x", "media/..", "\\x", "C:\\x", "a\\..\\b", "").forEach {
            assertFalse(RecipeFileCheck.isSafeEntryName(it), it)
        }
    }

    // FLOW-064 (5): a document from a later version is told apart from a damaged one.
    @Test
    fun aLaterFormatIsToldApart() {
        val later = RecipeFile.encode(valid).replace("\"format_version\": 1", "\"format_version\": 2")
        val thrown = runCatching { RecipeFile.decode(later) }.exceptionOrNull()
        assertTrue(thrown is LaterRecipeFile, "$thrown")
        assertEquals(2, thrown.formatVersion)
        val damaged = runCatching { RecipeFile.decode("{") }.exceptionOrNull()
        assertTrue(damaged is UnreadableRecipeFile && damaged !is LaterRecipeFile)
        val huge = RecipeFile.encode(valid).replace("\"3/2\"", "\"${"9".repeat(100_000)}/2\"")
        assertTrue(runCatching { RecipeFile.decode(huge) }.exceptionOrNull() is UnreadableRecipeFile)
    }
}
