package app.recipefork.app.card

import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.media.PhotoStore
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.recipe.AccountId
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.fork
import java.time.Instant

/**
 * Forks the recipe [parent] of the active book at [at] and writes the fork to that same book — the active
 * account's, or the pool's when none is active (FORK-001 (7), AUTH-026). Returns the fork's identity, or null when
 * the book no longer holds the parent. Forking again always makes another fork.
 *
 * Every recipe written in the book is the owner's own (FORK-007); a found recipe — one the book marks, imported from a
 * file — is someone else's, and its fork carries neither its description nor its media (FORK-002, FLOW-065 (2)).
 */
suspend fun forkRecipe(recipes: RecipeRepository, book: ActiveBook, parent: RecipeId, at: Instant): RecipeId? {
    val forked = recipes.get(parent) ?: return null
    val owner = (book.open.value.name as? BookName.OfAccount)?.let { AccountId(it.accountId) }
    val fork = forked.fork(at, owner, byAuthor = !recipes.isMarked(parent))
    // The owner's fork keeps the media as its own, under new identities (FORK-007): each file is copied to its new
    // name, so the fork's photographs outlive the parent's. A found recipe's fork keeps none, so copies none.
    recipes.filesDirectory?.let { files ->
        val store = PhotoStore(files)
        val name = recipes.bookName()
        forked.media.zip(fork.media).forEach { (kept, copied) -> store.copy(name, kept.mediaId, copied.mediaId) }
    }
    recipes.save(fork)
    return fork.id
}
