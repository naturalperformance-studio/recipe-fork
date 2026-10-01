package app.recipefork.app.card

import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.recipe.AccountId
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.fork
import java.time.Instant

/**
 * Forks the recipe [parent] of the active book at [at] and writes the fork to that same book — the active
 * account's, or the pool's when none is active (FORK-001 (7), AUTH-026). Returns the fork's identity, or null when
 * the book no longer holds the parent. Forking again always makes another fork.
 */
suspend fun forkRecipe(recipes: RecipeRepository, book: ActiveBook, parent: RecipeId, at: Instant): RecipeId? {
    val forked = recipes.get(parent) ?: return null
    val owner = (book.open.value.name as? BookName.OfAccount)?.let { AccountId(it.accountId) }
    val fork = forked.fork(at, owner)
    recipes.save(fork)
    return fork.id
}
