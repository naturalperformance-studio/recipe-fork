package app.recipefork.app.card

import app.recipefork.app.dictionary.IngredientEntryEntity
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.scaling.InterfaceLanguage
import app.recipefork.core.scaling.Rational

/**
 * The scale [recipe]'s card opens at: at the reader's [usual] count, or at the authored one with none set
 * (FLOW-017 (1, 2)) — unless the reader left it at another this session, which it comes back at (FLOW-017 (7),
 * FLOW-012). A fixing is of a row as it stood, so a change to that row drops it.
 */
fun CardSession.openingScale(recipe: Recipe, usual: Rational?): CardScale = openingScale(recipe, heldScale(recipe.id), usual)

/** The scale [recipe]'s card opens at, as [CardSession.openingScale] says, the session holding [held] of it. */
fun openingScale(recipe: Recipe, held: HeldScale?, usual: Rational?): CardScale {
    val authored = recipe.servings?.takeIf { it >= 1 }
    return held?.restored(authored, recipe.ingredients.sortedBy { it.position }, usual) ?: CardScale.opening(authored, usual)
}

/**
 * The serving count the card shows [recipe] at [scale], with the dictionary [entries]: the count requested, or the one
 * a fixed amount implies while one is fixed; none for a recipe with no count.
 */
fun servingsShown(recipe: Recipe, scale: CardScale, entries: Map<Long, IngredientEntryEntity>, language: InterfaceLanguage): Rational? =
    ingredientList(recipe.ingredients.sortedBy { it.position }, entries, language, recipe.servings?.takeIf { it >= 1 }, scale)
        .servingCount?.value

/**
 * The serving count [recipe]'s card would open with, the session holding [held] of it, as its ⋮ would put it on the
 * shopping list (FLOW-074): that of [openingScale], as [servingsShown] shows it.
 */
fun openingServings(
    recipe: Recipe,
    held: HeldScale?,
    usual: Rational?,
    entries: Map<Long, IngredientEntryEntity>,
    language: InterfaceLanguage,
): Rational? = servingsShown(recipe, openingScale(recipe, held, usual), entries, language)
