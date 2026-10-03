package app.recipefork.core.recipe

/**
 * A part of a recipe that DOM-006's minimum or a limit of DOM-012 applies to: the editor marks it when the author leaves
 * it below or above (FLOW-028), and submission names it.
 */
sealed interface RecipePart {
    data object Title : RecipePart
    data object Language : RecipePart
    data object Servings : RecipePart
    data object Ingredients : RecipePart
    data object Steps : RecipePart
    data object Description : RecipePart

    /** The text of the step at [position]. */
    data class StepText(val position: Int) : RecipePart

    /** The name the author gave the timer of the step at [position]. */
    data class TimerName(val position: Int) : RecipePart
}

/** How a part stands against its bound: short of DOM-006's minimum, or over a limit of DOM-012. */
sealed interface Bound {
    /** The part lacks what the minimum asks of it. */
    data object Missing : Bound

    /** The part holds [count] — characters or rows — where at most [limit] is accepted. */
    data class OverLimit(val limit: Int, val count: Int) : Bound
}

/**
 * The minimum of DOM-006 and the limits of DOM-012, as one rule set: the editor's warnings (FLOW-028) and the checks at
 * submission and on a recipe file read the same numbers. The limits are inclusive.
 */
object RecipeBounds {
    const val MAX_INGREDIENTS = 50
    const val MAX_STEPS = 50
    const val MAX_STEP_TEXT = 2_000
    const val MAX_TITLE = 120
    const val MAX_DESCRIPTION = 2_000
    const val MAX_TIMER_NAME = 60

    /** How [part] of [recipe] stands against its bound; null where it is within it, or where the recipe has no such part. */
    fun of(recipe: Recipe, part: RecipePart): Bound? = when (part) {
        RecipePart.Title -> when {
            recipe.title.isBlank() -> Bound.Missing
            else -> over(MAX_TITLE, recipe.title.length)
        }
        RecipePart.Language -> if (recipe.language == null) Bound.Missing else null
        RecipePart.Servings -> if (recipe.servings == null) Bound.Missing else null
        RecipePart.Ingredients -> if (recipe.ingredients.isEmpty()) Bound.Missing else over(MAX_INGREDIENTS, recipe.ingredients.size)
        RecipePart.Steps -> if (recipe.steps.isEmpty()) Bound.Missing else over(MAX_STEPS, recipe.steps.size)
        RecipePart.Description -> over(MAX_DESCRIPTION, recipe.description.length)
        is RecipePart.StepText -> step(recipe, part.position)?.let { over(MAX_STEP_TEXT, it.text.length) }
        // A timer whose name the author never set has no name limit: its name follows its step (DOM-012).
        is RecipePart.TimerName -> step(recipe, part.position)?.timer?.label?.let { over(MAX_TIMER_NAME, it.length) }
    }

    /** Every part of [recipe] outside its bound, in the order the editor shows them; empty when it is within all. */
    fun all(recipe: Recipe): Map<RecipePart, Bound> {
        val positions = recipe.steps.indices
        val parts = listOf(
            RecipePart.Title, RecipePart.Language, RecipePart.Servings, RecipePart.Ingredients, RecipePart.Steps,
        ) + positions.flatMap { listOf(RecipePart.StepText(it), RecipePart.TimerName(it)) } + RecipePart.Description
        return parts.mapNotNull { part -> of(recipe, part)?.let { part to it } }.toMap()
    }

    private fun over(limit: Int, count: Int): Bound? = if (count > limit) Bound.OverLimit(limit, count) else null

    private fun step(recipe: Recipe, position: Int): Step? = recipe.steps.sortedBy { it.position }.getOrNull(position)
}
