package app.recipefork.core.recipe

import app.recipefork.core.file.RecipeFileCheck
import app.recipefork.core.scaling.Rational
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes

/** FLOW-028 over DOM-006 and DOM-012: one rule set says which part is below its minimum or over its limit. */
class RecipeBoundsTest {
    private val at = Instant.parse("2026-10-03T08:00:00Z")

    private fun row(position: Int) = Ingredient(
        position, null, freeTextName = "salt", unitType = UnitType.EXACT, unit = QuantityUnit.GRAM, quantity = Rational.of(5),
    )

    /** A recipe within every bound: at the limits exactly, because they are inclusive. */
    private val atTheLimits = Recipe.create(at, owner = null).copy(
        title = "t".repeat(120),
        language = RecipeLanguage.EN,
        servings = 4,
        description = "d".repeat(2_000),
        ingredients = (0 until 50).map { row(it).copy(note = "n".repeat(60)) },
        steps = (0 until 50).map { Step(it, "s".repeat(2_000), timer = Timer(5.minutes, "n".repeat(60))) },
    )

    @Test
    fun aRecipeAtEveryLimitIsWithinAllOfThem() {
        assertEquals(emptyMap(), RecipeBounds.all(atTheLimits))
    }

    @Test
    fun anEmptyRecipeLacksEveryPartOfTheMinimum() {
        val empty = Recipe.create(at, owner = null)
        assertEquals(
            mapOf(
                RecipePart.Title to Bound.Missing,
                RecipePart.Language to Bound.Missing,
                RecipePart.Servings to Bound.Missing,
                RecipePart.Ingredients to Bound.Missing,
                RecipePart.Steps to Bound.Missing,
            ),
            RecipeBounds.all(empty),
        )
    }

    @Test
    fun aBlankTitleIsMissing() {
        assertEquals(Bound.Missing, RecipeBounds.of(atTheLimits.copy(title = "   "), RecipePart.Title))
    }

    @Test
    fun onePastEachLimitIsOverIt() {
        val over = atTheLimits.copy(
            title = "t".repeat(121),
            description = "d".repeat(2_001),
            ingredients = atTheLimits.ingredients + row(50).copy(note = "n".repeat(61)),
            steps = atTheLimits.steps + Step(50, "s".repeat(2_001), timer = Timer(5.minutes, "n".repeat(61))),
        )
        assertEquals(
            mapOf(
                RecipePart.Title to Bound.OverLimit(120, 121),
                RecipePart.Ingredients to Bound.OverLimit(50, 51),
                RecipePart.IngredientNote(50) to Bound.OverLimit(60, 61),
                RecipePart.Steps to Bound.OverLimit(50, 51),
                RecipePart.StepText(50) to Bound.OverLimit(2_000, 2_001),
                RecipePart.TimerName(50) to Bound.OverLimit(60, 61),
                RecipePart.Description to Bound.OverLimit(2_000, 2_001),
            ),
            RecipeBounds.all(over),
        )
    }

    @Test
    fun aTimerWhoseNameFollowsItsStepHasNoNameLimit() {
        val recipe = atTheLimits.copy(steps = listOf(Step(0, "s".repeat(80), timer = Timer(5.minutes))))
        assertNull(RecipeBounds.of(recipe, RecipePart.TimerName(0)))
    }

    // DOM-023: a row with no note has no note to bound; a row that is not there neither.
    @Test
    fun aRowWithoutANoteOrNoRowHasNoNoteBound() {
        assertNull(RecipeBounds.of(atTheLimits.copy(ingredients = listOf(row(0))), RecipePart.IngredientNote(0)))
        assertNull(RecipeBounds.of(atTheLimits, RecipePart.IngredientNote(50)))
    }

    @Test
    fun aStepThatIsNotThereHasNoBound() {
        assertNull(RecipeBounds.of(atTheLimits, RecipePart.StepText(50)))
        assertNull(RecipeBounds.of(atTheLimits, RecipePart.TimerName(50)))
    }

    @Test
    fun theRecipeFileCheckReadsTheSameLimits() {
        assertEquals(
            listOf(
                RecipeBounds.MAX_INGREDIENTS, RecipeBounds.MAX_STEPS, RecipeBounds.MAX_STEP_TEXT,
                RecipeBounds.MAX_TITLE, RecipeBounds.MAX_DESCRIPTION, RecipeBounds.MAX_TIMER_NAME,
            ),
            listOf(
                RecipeFileCheck.MAX_INGREDIENTS, RecipeFileCheck.MAX_STEPS, RecipeFileCheck.MAX_STEP_TEXT,
                RecipeFileCheck.MAX_TITLE, RecipeFileCheck.MAX_DESCRIPTION, RecipeFileCheck.MAX_TIMER_NAME,
            ),
        )
        assertEquals(listOf(50, 50, 2_000, 120, 2_000, 60), listOf(
            RecipeBounds.MAX_INGREDIENTS, RecipeBounds.MAX_STEPS, RecipeBounds.MAX_STEP_TEXT,
            RecipeBounds.MAX_TITLE, RecipeBounds.MAX_DESCRIPTION, RecipeBounds.MAX_TIMER_NAME,
        ))
    }
}
