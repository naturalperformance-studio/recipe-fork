package app.recipefork.core.recipe

import app.recipefork.core.scaling.Rational
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
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
        ingredients = (0 until 50).map(::row),
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
            ingredients = (0 until 51).map(::row),
            steps = atTheLimits.steps + Step(50, "s".repeat(2_001), timer = Timer(5.minutes, "n".repeat(61))),
        )
        assertEquals(
            mapOf(
                RecipePart.Title to Bound.OverLimit(120, 121),
                RecipePart.Ingredients to Bound.OverLimit(50, 51),
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

    @Test
    fun aStepThatIsNotThereHasNoBound() {
        assertNull(RecipeBounds.of(atTheLimits, RecipePart.StepText(50)))
        assertNull(RecipeBounds.of(atTheLimits, RecipePart.TimerName(50)))
    }

    @Test
    fun theRecipeFileCheckReadsTheSameLimits() {
        assertEquals(RecipeBounds.MAX_STEP_TEXT, app.recipefork.core.file.RecipeFileCheck.MAX_STEP_TEXT)
        assertEquals(RecipeBounds.MAX_TIMER_NAME, app.recipefork.core.file.RecipeFileCheck.MAX_TIMER_NAME)
        assertTrue(RecipeBounds.MAX_INGREDIENTS == app.recipefork.core.file.RecipeFileCheck.MAX_INGREDIENTS)
    }
}
