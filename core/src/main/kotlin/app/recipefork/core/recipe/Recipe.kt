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

/**
 * A step of a recipe: its text, the references within it, its timers (DOM-024) — from none to
 * [RecipeBounds.MAX_TIMERS], in the order the author added them, each with its own duration and name — and the
 * author's own [note] beside the method.
 *
 * A step may hold more timers than the bound while it is read from a file; [RecipeFileCheck][app.recipefork.core.file.RecipeFileCheck]
 * refuses such a file. The editor adds a timer through [withTimer], which refuses a sixth.
 */
data class Step(
    val position: Int,
    val text: String,
    val ingredientReferences: List<IngredientReference> = emptyList(),
    val timers: List<Timer> = emptyList(),
    /**
     * The author's note on the step ("Grandma always added a pinch of sugar"): trimmed, never blank, at most
     * [RecipeBounds.MAX_STEP_NOTE] characters on a recipe that passes the rules; null for none. It is the author's
     * expression, not the method: a fork by anyone else leaves it behind, as it leaves the description (FORK-007).
     */
    val note: String? = null,
) {
    /**
     * Bridge from the one-timer step (DOM-013, retired): the step with [timer] as its only timer, or none. For code
     * written before DOM-024 — the editor, the card and the timer runtime — until they read [timers].
     */
    constructor(
        position: Int,
        text: String,
        ingredientReferences: List<IngredientReference> = emptyList(),
        timer: Timer?,
    ) : this(position, text, ingredientReferences, listOfNotNull(timer), null)

    /**
     * Bridge from the one-timer step (DOM-013, retired): the step's first timer, null for none. For code written
     * before DOM-024 — the editor, the card and the timer runtime — until they read [timers].
     */
    val timer: Timer? get() = timers.firstOrNull()

    /**
     * Bridge from the one-timer step (DOM-013, retired): this step with [timer] as its only timer, or none — every
     * other timer it held is dropped; its note stays. For code written before DOM-024 until it edits [timers].
     */
    fun copy(
        position: Int = this.position,
        text: String = this.text,
        ingredientReferences: List<IngredientReference> = this.ingredientReferences,
        timer: Timer?,
    ): Step = Step(position, text, ingredientReferences, listOfNotNull(timer), note)

    /** Whether the step holds fewer timers than [RecipeBounds.MAX_TIMERS], so that one more may be added. */
    val canAddTimer: Boolean get() = timers.size < RecipeBounds.MAX_TIMERS

    /** This step with [timer] added after its others; null, refused, where it already holds [RecipeBounds.MAX_TIMERS]. */
    fun withTimer(timer: Timer): Step? = if (canAddTimer) copy(timers = timers + timer) else null

    /** This step without its timer at [index]; the others keep their order. Unchanged where it holds none there. */
    fun withoutTimer(index: Int): Step = copy(timers = timers.filterIndexed { at, _ -> at != index })

    /** This step with the note the author [typed]: trimmed, and none when blank. */
    fun withNote(typed: String?): Step = copy(note = noteOf(typed))

    companion object {
        /** The note the author's [typed] text gives a step: trimmed, and none when blank. */
        fun noteOf(typed: String?): String? = typed?.trim()?.takeIf { it.isNotEmpty() }
    }
}

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
 * An item of the equipment a recipe is cooked with, named as an ingredient is (DOM-009): by an entry of the equipment
 * dictionary, narrowed by an optional [qualifier] shown after it ("Frying pan" + "26 cm"), or by words of the author's
 * own ([freeTextName]), never both. It reads as its [shown] text: the entry's label in the interface language and its
 * qualifier, or its free text.
 *
 * On a recipe that passes the rules the free text and the qualifier are trimmed and not blank, and the shown text is at
 * most [RecipeBounds.MAX_EQUIPMENT_NAME] characters. The editor holds an item being typed as free text as it stands.
 */
data class EquipmentItem(
    /** The equipment dictionary entry's id; null exactly when [freeTextName] is present. */
    val dictionaryEntry: Long?,
    /** What narrows the entry ("26 cm"); null for nothing, and always null for free text. */
    val qualifier: String? = null,
    /** The author's own words, for an item no entry names. */
    val freeTextName: String? = null,
) {
    /** Named by an entry or by the author's own words, exactly one of the two. */
    val isNamed: Boolean get() = (dictionaryEntry != null) != (freeTextName != null)

    /**
     * The text the item reads as: for an entry, its [label] — in the interface language, null where it is not known —
     * followed by the qualifier; otherwise its free text.
     */
    fun shown(label: String?): String =
        if (dictionaryEntry != null) listOfNotNull(label, qualifier).joinToString(" ") else freeTextName.orEmpty()

    companion object {
        /** The item [entry] names, narrowed by [qualifier]: trimmed, and none when blank. */
        fun entry(entry: Long, qualifier: String? = null) =
            EquipmentItem(entry, qualifier = qualifier?.trim()?.takeIf { it.isNotEmpty() })

        /** The item the author's own [text] names, as it stands. */
        fun free(text: String) = EquipmentItem(null, freeTextName = text)
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
    /**
     * What the recipe is cooked with ("Oven", "Frying pan" + "26 cm"), in the author's order; empty for none. Each item
     * names a dictionary entry or is free text ([EquipmentItem]). On a recipe that passes the rules each item reads as
     * at most [RecipeBounds.MAX_EQUIPMENT_NAME] characters, and there are at most [RecipeBounds.MAX_EQUIPMENT]. The
     * editor adds an item through [withEquipment], which refuses one more than that.
     */
    val equipment: List<EquipmentItem> = emptyList(),
    /**
     * The text the recipe was made from, exactly as the owner pasted or shared it, kept so that it can be read and copied
     * again; null for a recipe not made from text. It is never empty, and at most [RecipeBounds.MAX_SOURCE_TEXT]
     * characters where it was set through [sourceTextOf]. It lives on the device, in a saved book and in a recipe file
     * only: it is the owner's own paste, and nothing sends it to the server.
     */
    val sourceText: String? = null,
) {
    init {
        require(servings == null || servings >= 1) { "servings must be 1 or more" }
        require(!contentChangedAt.isBefore(createdAt)) { "content cannot change before creation" }
    }

    /** Whether the recipe holds fewer items of equipment than [RecipeBounds.MAX_EQUIPMENT], so that one more may be added. */
    val canAddEquipment: Boolean get() = equipment.size < RecipeBounds.MAX_EQUIPMENT

    /**
     * This recipe with the item the author [typed] added after its others, trimmed; unchanged where [typed] is blank, and
     * null, refused, where it already holds [RecipeBounds.MAX_EQUIPMENT].
     */
    fun withEquipment(typed: String): Recipe? {
        if (!canAddEquipment) return null
        val item = equipmentItemOf(typed) ?: return this
        return copy(equipment = equipment + item)
    }

    /** This recipe made from [text], kept as [sourceTextOf] keeps it. */
    fun withSourceText(text: String?): Recipe = copy(sourceText = sourceTextOf(text))

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
        /**
         * The source text [text] gives a recipe: as it stands, cut at [RecipeBounds.MAX_SOURCE_TEXT] characters — never
         * between the two halves of a character outside the basic plane — and none when it is empty or only blank.
         */
        fun sourceTextOf(text: String?): String? {
            if (text.isNullOrBlank()) return null
            if (text.length <= RecipeBounds.MAX_SOURCE_TEXT) return text
            val end = if (Character.isHighSurrogate(text[RecipeBounds.MAX_SOURCE_TEXT - 1])) {
                RecipeBounds.MAX_SOURCE_TEXT - 1
            } else {
                RecipeBounds.MAX_SOURCE_TEXT
            }
            return text.substring(0, end)
        }

        /** The item of equipment the author's [typed] text gives: free text, trimmed, and none when blank. */
        fun equipmentItemOf(typed: String): EquipmentItem? = typed.trim().takeIf { it.isNotEmpty() }?.let(EquipmentItem::free)

        /**
         * The equipment the author's [items] settle to, in their order: free text trimmed, a blank one dropped; an item
         * an entry names stays as it is.
         */
        fun equipmentOf(items: List<EquipmentItem>): List<EquipmentItem> =
            items.mapNotNull { item -> if (item.dictionaryEntry != null) item else equipmentItemOf(item.freeTextName.orEmpty()) }

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
