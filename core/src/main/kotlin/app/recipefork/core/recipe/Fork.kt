package app.recipefork.core.recipe

import java.time.Instant
import java.util.UUID

/**
 * The owner's own fork of this recipe, created at [at] in the active book — the pool's when [owner] is null
 * (FORK-001 (7), AUTH-026).
 *
 * The fork is a new recipe in `local`, never sent, never published and not a replacement (FORK-008 sets that mark
 * only by the author's hand), whose parent is this recipe. Everything else is the parent's content as it stands:
 * title, tags, language, serving count, ingredients and steps travel whole (FORK-001 (1–5)), so a step's
 * references, which name ingredient positions, resolve within the fork (DOM-007), and a timer keeps its duration
 * and its own name or the lack of one (FLOW-016). Every recipe of the book is the owner's own, so the description
 * and media travel too (FORK-007 (1–3)); each media item becomes the fork's own, under a new identity, private and
 * unowned until the fork is sent (DOM-005, FLOW-004), attached at the same place.
 *
 * Nothing readers gave the parent travels (FORK-006): the fork has never been sent or published. A parent flagged
 * as dangerous to health passes its flag on (`safety_flag`, MOD-008). A file's stated author and parent describe
 * the recipe the file carried, not its fork, so neither travels.
 */
fun Recipe.fork(at: Instant, owner: AccountId?, id: RecipeId = RecipeId.mint()): Recipe = copy(
    id = id,
    owner = owner,
    parent = this.id,
    sent = SentStatus.NEVER,
    statedAuthor = null,
    statedParent = null,
    media = media.map { it.copy(mediaId = UUID.randomUUID().toString(), owner = null, visibility = MediaVisibility.PRIVATE) },
    state = RecipeState.NEW,
    replacesParent = false,
    wasPublished = false,
    safetyFlag = safetyFlag,
    createdAt = at,
    contentChangedAt = at,
)
