package app.recipefork.core.recipe

import app.recipefork.core.scaling.AuthoredQuantity
import app.recipefork.core.scaling.Rational
import java.time.Instant
import java.util.UUID
import kotlin.time.Duration

/** A recipe's identity: minted on the device at creation and never changed (spec/50-domain-model, `recipe_id`). */
@JvmInline
value class RecipeId(val value: String) {
    companion object {
        /** A new identity, minted on the device so that nothing waits for the server. */
        fun mint(): RecipeId = RecipeId(UUID.randomUUID().toString())
    }
}

/** An account's internal identity; never shown to anyone. */
@JvmInline
value class AccountId(val value: String)

/** The eight states of a recipe (spec/60-entity-states). A new recipe is `local`. */
enum class RecipeState(
    /** True where the owner may edit the content; false where the content is frozen. */
    val editable: Boolean,
) {
    LOCAL(true),
    PRIVATE(true),
    PENDING(false),
    REJECTED(true),
    LINK_SHARED(false),
    PUBLISHED(false),
    REPORTED_HIDDEN(false),
    REMOVED(false),
    ;

    /** `local` and `private` are drafts (FLOW-036). */
    val isDraft: Boolean get() = this == LOCAL || this == PRIVATE

    companion object {
        /** The state a recipe is created in, with or without an account. */
        val NEW = LOCAL
    }
}

/** What FLOW-005 marks: never sent, sent with every change on the server, or sent holding changes not sent. */
enum class SentStatus { NEVER, SENT, SENT_WITH_CHANGES }

/** The languages of L10N-001. */
enum class RecipeLanguage { RU, EN }

/**
 * A parent named in text only, never verified (FLOW-065): as a recipe file states it, or, for a fork of someone
 * else's recipe, as that recipe stood when it was forked. An empty [nickname] names no author.
 */
data class StatedParent(val title: String, val nickname: String)

data class Timer(
    /** From 1 minute to 99 h 59 min (FLOW-027). */
    val duration: Duration,
    /** The author's own name; null means the name follows the step (FLOW-016). */
    val label: String? = null,
)

data class IngredientReference(
    /** The position of the referenced ingredient in its recipe. */
    val ingredientPosition: Int,
    /** Where in the step's text the reference sits. */
    val textOffset: Int,
    /** The part of the ingredient the reference reads instead of its whole quantity; null for the whole. */
    val share: IngredientShare? = null,
)

data class Step(
    val position: Int,
    val text: String,
    val ingredientReferences: List<IngredientReference> = emptyList(),
    val timer: Timer? = null,
)

/**
 * An ingredient row as the author wrote it.
 *
 * A complete ingredient ([isComplete]) names an entry or its own words (DOM-009), holds a unit, holds a quantity on
 * its unit type's grid exactly when that type carries one (DOM-002, DOM-017), and, for `piece`, its divisibility.
 * A row the author is still writing may lack any of these, or hold a value off its grid, and is kept exactly as it
 * stands (FLOW-024 (6)): its [authoredQuantity] is null, and a reader shows it as written and never scales it.
 */
data class Ingredient(
    val position: Int,
    /** The dictionary entry's id; null exactly when [freeTextName] is present (DOM-009). */
    val dictionaryEntry: Long?,
    val qualifier: String? = null,
    val freeTextName: String? = null,
    val unitType: UnitType,
    /** Null for `taste_unmeasured`. */
    val unit: QuantityUnit?,
    /**
     * The value read from what the author typed, always above zero; on its grid unless the row is marked
     * (DOM-017 (6)). Null for `taste_unmeasured`, and while the field holds no number above zero.
     */
    val quantity: Rational?,
    /** Mass of one piece, `piece` only. */
    val pieceWeight: Rational? = null,
    /** `piece` only; null until the author answers. */
    val divisible: Boolean? = null,
    /**
     * What the author typed into the quantity field when it reads as no number above zero — zero, a negative
     * number, a number with separators, or no number (DOM-017 (2), DOM-019 (5)) — kept so the field gives it back.
     * Never present together with [quantity].
     */
    val enteredQuantity: String? = null,
    /**
     * How the ingredient is prepared ("finely chopped"), shown after it (DOM-023): trimmed, never blank, at most
     * [RecipeBounds.MAX_NOTE] characters on a recipe that passes the rules; null for none. It is not scaled, searched
     * or filtered by, and is no part of what the ingredient is — its [qualifier] is.
     */
    val note: String? = null,
) {
    /**
     * What scaling reads of this ingredient, or null while the row is incomplete or its quantity lies off its grid
     * (DOM-017): a reader then shows the row as written and scales the others without it.
     */
    val authoredQuantity: AuthoredQuantity?
        get() {
            if (unitType == UnitType.TASTE_UNMEASURED) return AuthoredQuantity(QuantityUnit.TO_TASTE, null)
            val unit = unit?.takeIf { it.type == unitType } ?: return null
            val quantity = quantity ?: return null
            if (unitType == UnitType.PIECE && divisible == null) return null
            if (!QuantityInput.holds(quantity, unit, divisible)) return null
            return AuthoredQuantity(unit, quantity, divisible.takeIf { unitType == UnitType.PIECE })
        }

    /** Named by an entry or by words of the author's own, never both (DOM-009), with a quantity scaling can read. */
    val isComplete: Boolean
        get() = (dictionaryEntry != null) != !freeTextName.isNullOrBlank() && authoredQuantity != null

    companion object {
        /** The note the author's [typed] text gives a row (DOM-023): trimmed, and none when blank. */
        fun noteOf(typed: String?): String? = typed?.trim()?.takeIf { it.isNotEmpty() }
    }
}

/**
 * A heading in the ingredient list (DOM-022): the rows from [firstPosition] up to the next heading belong to the group
 * [name]; rows above the first heading belong to none. A group is presentation only: ingredients keep their positions,
 * so scaling, references, filters and search read them exactly as without groups.
 *
 * On a recipe that passes the rules, [name] is 1 to [RecipeBounds.MAX_GROUP_NAME] characters, not blank, and the
 * recipe's headings stand in order of [firstPosition], at most one at a position, each from 0 to the ingredient count —
 * a heading at the count has no rows yet.
 */
data class IngredientGroup(val name: String, val firstPosition: Int)

enum class MediaKind { PHOTO, VIDEO }

enum class MediaVisibility { PRIVATE, PUBLISHED }

sealed interface MediaAttachment {
    data object Cover : MediaAttachment
    data class ToStep(val stepPosition: Int) : MediaAttachment
}

data class Media(
    val mediaId: String,
    /** Null until its recipe is sent (FLOW-004). */
    val owner: AccountId?,
    val kind: MediaKind,
    val attachment: MediaAttachment,
    val visibility: MediaVisibility,
)

/**
 * A recipe (spec/50-domain-model, "Recipe").
 *
 * [createdAt] and [contentChangedAt] are what the own-recipes view orders by (FLOW-039): a change of state
 * is not a change of content, so a frozen recipe keeps the time of its last edit.
 */
data class Recipe(
    val id: RecipeId,
    /** None while in the pool of a device (AUTH-026). */
    val owner: AccountId?,
    /** The direct parent's identity, which need not be held anywhere (FLOW-007). */
    val parent: RecipeId?,
    val sent: SentStatus,
    val statedAuthor: String?,
    val statedParent: StatedParent?,
    val title: String,
    val description: String,
    val tags: Set<Long>,
    /** Null until declared (L10N-004). */
    val language: RecipeLanguage?,
    /** Null while the field holds no value (DOM-015); otherwise 1 or more. */
    val servings: Int?,
    val ingredients: List<Ingredient>,
    val steps: List<Step>,
    val media: List<Media>,
    val state: RecipeState,
    val replacesParent: Boolean,
    val wasPublished: Boolean,
    val safetyFlag: Boolean,
    val createdAt: Instant,
    val contentChangedAt: Instant,
    /** The headings of the ingredient list, in order (DOM-022); empty for a list with no groups. */
    val ingredientGroups: List<IngredientGroup> = emptyList(),
) {
    init {
        require(servings == null || servings >= 1) { "servings must be 1 or more" }
        require(!contentChangedAt.isBefore(createdAt)) { "content cannot change before creation" }
    }

    /** The time FLOW-039 orders by under `newest`. */
    val newestKey: Instant get() = contentChangedAt

    /**
     * The content changed at [at]. Only an editable state allows it; a sent recipe now holds changes not
     * sent (FLOW-005).
     */
    fun edited(at: Instant, change: (Recipe) -> Recipe): Recipe {
        check(state.editable) { "a recipe in $state is frozen" }
        val changed = change(this)
        check(changed.id == id && changed.state == state) { "an edit changes content only" }
        return changed.copy(
            contentChangedAt = at,
            sent = if (sent == SentStatus.SENT) SentStatus.SENT_WITH_CHANGES else sent,
        )
    }

    /** A change of state, which leaves the content and its time alone. */
    fun inState(next: RecipeState): Recipe = copy(
        state = next,
        wasPublished = wasPublished || next == RecipeState.PUBLISHED,
    )

    companion object {
        /** An empty recipe in `local`, in the active book: the pool's when [owner] is null. */
        fun create(at: Instant, owner: AccountId?, parent: RecipeId? = null, id: RecipeId = RecipeId.mint()) =
            Recipe(
                id = id,
                owner = owner,
                parent = parent,
                sent = SentStatus.NEVER,
                statedAuthor = null,
                statedParent = null,
                title = "",
                description = "",
                tags = emptySet(),
                language = null,
                servings = null,
                ingredients = emptyList(),
                steps = emptyList(),
                media = emptyList(),
                state = RecipeState.NEW,
                replacesParent = false,
                wasPublished = false,
                safetyFlag = false,
                createdAt = at,
                contentChangedAt = at,
            )
    }
}

/** The own-recipes view under `newest`: the last change of content first (FLOW-039). */
val NewestOwnFirst: Comparator<Recipe> = compareByDescending<Recipe> { it.newestKey }.thenBy { it.id.value }
