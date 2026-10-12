package app.recipefork.core.recipe

import java.time.Instant
import java.util.UUID

/** A collection's identity: minted on the device as it is made, or carried by the collection file it came in; never changed. */
@JvmInline
value class CollectionId(val value: String) {
    companion object {
        fun mint(): CollectionId = CollectionId(UUID.randomUUID().toString())
    }
}

/** Where a collection came from, which decides what the reader may change of it. */
enum class CollectionOrigin(
    /** Whether the reader may rename it, reorder it, and add or remove its recipes; an imported one is frozen. */
    val editable: Boolean,
) {
    /** Made by the reader in this book. */
    OWN(true),

    /** Arrived in a collection file: it may be opened, shopped, shared onward and deleted, and nothing more. */
    IMPORTED(false),
}

/**
 * A named collection of the book's recipes, own and found (v0.36): its [recipes] in the reader's order. A recipe is
 * in a collection at most once; deleting a collection never deletes a recipe, and a recipe deleted from the book
 * leaves every collection. [statedAuthor] is the author's nickname an imported collection's file states, never
 * verified; an own collection has none.
 */
data class RecipeCollection(
    val id: CollectionId,
    val name: String,
    val origin: CollectionOrigin,
    val statedAuthor: String?,
    val createdAt: Instant,
    val recipes: List<RecipeId>,
) {
    val editable: Boolean get() = origin.editable

    companion object {
        /** The longest name a collection takes, in characters. */
        const val MAX_NAME = 60

        /** [text] as a collection's name: trimmed; null when nothing is left or it is longer than [MAX_NAME]. */
        fun nameOf(text: String): String? = text.trim().takeIf { it.isNotEmpty() && it.length <= MAX_NAME }
    }
}

/** How a collection's order of recipes changes; each keeps every recipe once. */
object CollectionOrder {
    /**
     * [current] in the order [asked]: the recipes of [asked] that [current] holds, in that order, then those it holds
     * that [asked] leaves out, in their order. A recipe [current] does not hold is not added.
     */
    fun reordered(current: List<RecipeId>, asked: List<RecipeId>): List<RecipeId> {
        val held = current.toSet()
        val first = asked.filter { it in held }.distinct()
        return first + current.filterNot { it in first.toSet() }
    }

    /** [current] with each recipe of [more] it does not hold yet put after it, in the order of [more]. */
    fun added(current: List<RecipeId>, more: List<RecipeId>): List<RecipeId> = (current + more).distinct()

    /**
     * An imported collection as its file [file] has it, over what the book holds of it ([current]): the file's
     * recipes in the file's order, then those the book's copy holds that the file does not list, in their order.
     */
    fun imported(current: List<RecipeId>, file: List<RecipeId>): List<RecipeId> = (file + current).distinct()
}
