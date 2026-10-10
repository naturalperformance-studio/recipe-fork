package app.recipefork.core.recipe

import java.time.Instant
import java.util.UUID

/**
 * A fork of this recipe, created at [at] in the active book — the pool's when [owner] is null (FORK-001 (7),
 * AUTH-026).
 *
 * The fork is a new recipe in `local`, never sent, never published and not a replacement (FORK-008 sets that mark
 * only by the author's hand), whose parent is this recipe. Title, tags, language, serving count, equipment,
 * ingredients — with their notes and groups (DOM-022, DOM-023) — and steps travel whole (FORK-001 (1–5)), so a step's references, which name ingredient positions, resolve within the
 * fork (DOM-007), and a timer keeps its duration and its own name or the lack of one (FLOW-016).
 *
 * [byAuthor] says whose fork it is. The owner's own fork carries the description, the steps' notes and media too
 * (FORK-007 (1–3)); each
 * media item becomes the fork's own, under a new identity, private and unowned until the fork is sent (DOM-005,
 * FLOW-004), attached at the same place. Anyone else's — a fork of a recipe found in a file among them — carries no
 * description, no step's note and no media (FORK-002, FLOW-065 (2)), and keeps the parent's title and stated author as its
 * [Recipe.statedParent], so that the parent can still be named once the book no longer holds it (FLOW-065 (4)) and a
 * file of the fork names it (FLOW-065 (6)).
 *
 * Nothing readers gave the parent travels (FORK-006): the fork has never been sent or published. A parent flagged
 * as dangerous to health passes its flag on (`safety_flag`, MOD-008). A file's stated author and parent describe
 * the recipe the file carried, not its fork, so neither travels.
 */
fun Recipe.fork(at: Instant, owner: AccountId?, id: RecipeId = RecipeId.mint(), byAuthor: Boolean = true): Recipe = copy(
    id = id,
    owner = owner,
    parent = this.id,
    sent = SentStatus.NEVER,
    statedAuthor = null,
    statedParent = if (byAuthor) null else StatedParent(title, statedAuthor.orEmpty()),
    description = if (byAuthor) description else "",
    steps = if (byAuthor) steps else steps.map { it.copy(note = null) },
    media = if (byAuthor) {
        media.map { it.copy(mediaId = UUID.randomUUID().toString(), owner = null, visibility = MediaVisibility.PRIVATE) }
    } else {
        emptyList()
    },
    state = RecipeState.NEW,
    replacesParent = false,
    wasPublished = false,
    safetyFlag = safetyFlag,
    createdAt = at,
    contentChangedAt = at,
)
