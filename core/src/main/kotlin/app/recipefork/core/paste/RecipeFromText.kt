package app.recipefork.core.paste

import app.recipefork.core.api.DictionariesDto
import app.recipefork.core.recipe.AccountId
import app.recipefork.core.recipe.EquipmentItem
import app.recipefork.core.recipe.IngredientGroup
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeBounds
import app.recipefork.core.recipe.RecipeLanguage
import app.recipefork.core.recipe.Step
import app.recipefork.core.search.DictionaryMatch
import java.time.Instant

/**
 * A pasted recipe text read whole into a new recipe of the owner's, `local`, as the editor's new recipe is: its title,
 * servings, description (the description lines, then the notes), ingredient rows under their groups, steps with their
 * timers, the equipment named, and its language. What does not fit the model's limits is cut to them, so the recipe
 * passes the rules of a recipe file. The text itself is not kept here: the paste screen keeps it.
 */
object RecipeFromText {
    /** The recipe [text] holds, created at [at] in [owner]'s book (the pool's when null), or null when it holds none. */
    fun read(text: String, dictionaries: DictionariesDto, at: Instant, owner: AccountId?): Recipe? {
        val layout = TextLayout.read(text) ?: return null
        val ingredientMatch = DictionaryMatch(dictionaries.ingredients)
        val reader = IngredientLineReader(ingredientMatch)
        val section = IngredientSection(layout.ingredients, reader, EquipmentMentions(dictionaries.equipment, ingredientMatch))
        val lines = section.ingredients.take(RecipeBounds.MAX_INGREDIENTS)
        val groups = lines.indices.filter { i -> lines[i].group != null && (i == 0 || lines[i].group != lines[i - 1].group) }
            .mapNotNull { i -> lines[i].group?.trim()?.takeIf { it.isNotEmpty() }?.let { IngredientGroup(fit(it, RecipeBounds.MAX_GROUP_NAME), i) } }
        val steps = layout.steps.take(RecipeBounds.MAX_STEPS).mapIndexed(::step)
        val mentions = EquipmentMentions(dictionaries.equipment, ingredientMatch)
        val equipment = ((layout.equipment + section.equipment).flatMap { mentions.of(it, inStep = false) } + steps.flatMap { mentions.of(it.text, inStep = true) })
            .distinctBy { it.id }.take(RecipeBounds.MAX_EQUIPMENT).map { EquipmentItem.entry(it.id) }
        val fallback = if (layout.language == RecipeLanguage.RU) "Рецепт" else "Recipe"
        return Recipe.create(at, owner).copy(
            title = fit(layout.title?.trim().orEmpty().ifEmpty { fallback }, RecipeBounds.MAX_TITLE),
            description = fit((layout.description + layout.notes).joinToString("\n").trim(), RecipeBounds.MAX_DESCRIPTION),
            language = layout.language,
            servings = layout.servings?.takeIf { it >= 1 },
            ingredients = lines.mapIndexed { i, line -> reader.read(line, i) },
            ingredientGroups = groups,
            steps = steps,
            equipment = equipment,
        )
    }

    /** The step [text] reads as at [position]: its words as written, within the limit, and the timers they name. */
    private fun step(position: Int, text: String): Step {
        val kept = fit(text.trim(), RecipeBounds.MAX_STEP_TEXT)
        return Step(position, kept, timers = StepTimers.read(kept))
    }

    /** [text] within [max] characters: as it is where it fits, otherwise cut at a word with an ellipsis. */
    internal fun fit(text: String, max: Int): String {
        if (text.length <= max) return text
        var cut = text.take(max - 1)
        if (cut.last().isHighSurrogate()) cut = cut.dropLast(1)
        val atWord = cut.substringBeforeLast(' ', "").trimEnd().takeIf { it.length >= max / 2 }
        return (atWord ?: cut.trimEnd()) + "…"
    }
}
