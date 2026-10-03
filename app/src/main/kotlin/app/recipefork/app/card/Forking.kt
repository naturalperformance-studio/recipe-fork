package app.recipefork.app.card

import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.media.PhotoStore
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.recipe.AccountId
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.fork
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.time.Instant

/**
 * Forks the recipe [parent] of the active book at [at] and writes the fork to that same book — the active
 * account's, or the pool's when none is active (FORK-001 (7), AUTH-026). Returns the fork's identity, or null when
 * the book no longer holds the parent. This always makes another fork; [forkUnlessHeld] first offers one held.
 *
 * Every recipe written in the book is the owner's own (FORK-007); a found recipe — one the book marks, imported from a
 * file — is someone else's, and its fork carries neither its description nor its media (FORK-002, FLOW-065 (2)).
 */
suspend fun forkRecipe(
    recipes: RecipeRepository,
    book: ActiveBook,
    parent: RecipeId,
    at: Instant,
    store: PhotoStore? = recipes.filesDirectory?.let { PhotoStore(it) },
): RecipeId? {
    val forked = recipes.get(parent) ?: return null
    val owner = (book.open.value.name as? BookName.OfAccount)?.let { AccountId(it.accountId) }
    val fork = forked.fork(at, owner, byAuthor = !recipes.isMarked(parent))
    if (store == null) {
        recipes.save(fork)
        return fork.id
    }
    // The owner's fork keeps the media as its own, under new identities (FORK-007): each file is copied to its new
    // name, so the fork's photographs outlive the parent's. A found recipe's fork keeps none, so copies none. An item
    // whose copy fails is left out of the fork, so the fork never names a file it does not hold; and if the fork is
    // not written, the copies made for it are deleted.
    val name = recipes.bookName()
    val copied = forked.media.zip(fork.media).filter { (kept, copy) -> store.copy(name, kept.mediaId, copy.mediaId) }
        .map { it.second }
    try {
        recipes.save(fork.copy(media = copied))
    } catch (e: Throwable) {
        withContext(NonCancellable) { copied.forEach { store.delete(name, it.mediaId) } }
        throw e
    }
    return fork.id
}

/** What asking to fork a recipe came to (FLOW-015). */
sealed interface ForkAsked {
    /** The book holds [fork], a fork of the recipe nothing has changed since it was made; no fork was made. */
    data class Held(val fork: RecipeId) : ForkAsked

    /** The fork [fork] was made. */
    data class Made(val fork: RecipeId) : ForkAsked
}

/**
 * Forks [parent] as [forkRecipe] does, unless the book holds a fork of it that nothing has changed since it was made
 * — then that fork is returned and none is made, so that the person is told it exists and can open it or ask for
 * another (FLOW-015). Whether that fork was ever sent does not matter. Null when the book no longer holds the parent.
 */
suspend fun forkUnlessHeld(
    recipes: RecipeRepository,
    book: ActiveBook,
    parent: RecipeId,
    at: Instant,
    store: PhotoStore? = recipes.filesDirectory?.let { PhotoStore(it) },
): ForkAsked? {
    recipes.unchangedForkOf(parent)?.let { return ForkAsked.Held(it) }
    return forkRecipe(recipes, book, parent, at, store)?.let(ForkAsked::Made)
}
