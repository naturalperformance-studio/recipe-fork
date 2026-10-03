package app.recipefork.app.recipe

import app.recipefork.app.book.IngredientLineRow
import app.recipefork.app.book.RecipeTagEntity
import app.recipefork.app.dictionary.IngredientEntryEntity
import app.recipefork.core.scaling.MeasuredLine
import app.recipefork.core.scaling.QuantityPerServing
import app.recipefork.core.scaling.mostPerServingFirst
import app.recipefork.core.search.BookOrder
import app.recipefork.core.search.SearchIndex
import app.recipefork.core.search.WordPrefix
import app.recipefork.core.search.ingredientFamily
import app.recipefork.core.search.inOrder
import kotlinx.coroutines.CancellationException
import java.io.Serializable
import java.util.Locale

/**
 * What narrows a view of the book beside its query (FLOW-035): a dictionary [ingredient] entry, which keeps the recipes
 * holding it or an entry below it (CAT-006) and orders them by how much of it a serving holds (CAT-002), and [tags], all
 * of which a recipe must carry to be kept.
 */
data class BookFilters(val ingredient: Long? = null, val tags: Set<Long> = emptySet()) : Serializable {
    /** Whether any filter is on. */
    val any: Boolean get() = ingredient != null || tags.isNotEmpty()

    fun toggled(tag: Long): BookFilters = copy(tags = if (tag in tags) tags - tag else tags + tag)

    private companion object {
        private const val serialVersionUID = 1L
    }
}

/** What a view of the book is listed by: the query in its search field, its filters, its order, and the interface's collation. */
data class ListingParams(
    val query: String,
    val order: BookOrder,
    val locale: Locale,
    val filters: BookFilters = BookFilters(),
) {
    /** These params as a text that is the same in every process, telling one listing from another across process death. */
    val key: String
        get() = listOf(query, order.name, filters.ingredient, filters.tags.sorted(), locale.toLanguageTag()).toString()
}

/** What a view lists for [params]: the [rows] found, in order; [viewIsEmpty] when the view holds nothing at all. */
data class Listed<R>(val rows: List<R>, val viewIsEmpty: Boolean, val params: ListingParams) {
    /** Whether a query was searched: one with a word in it. A query of none lists the whole view. */
    val searched: Boolean get() = WordPrefix.words(params.query).isNotEmpty()

    /** Whether anything narrowed the view: a query searched, or a filter on. */
    val narrowed: Boolean get() = searched || params.filters.any
}

/**
 * Finds, filters and orders the rows of one view of the book (FLOW-035). A query finds a recipe in which each of its
 * words begins a word of the title or of any of its ingredients' names, wherever each stands ([WordPrefix]); an
 * ingredient is named by its dictionary entry's label in either language with its qualifier, or by its free text.
 * A filter by tags keeps the recipes carrying every one of them; a filter by an ingredient entry keeps the recipes
 * holding it or an entry below it (CAT-006), the most of it per serving first (CAT-002, CAT-007), in the order chosen
 * for the view among recipes of equal or no comparable quantity.
 *
 * The search index is kept for the rows it was built from and rebuilt only when the book's rows change, so that a
 * keystroke only compares words. The dictionary is read the first time a query or a filter needs it; while it cannot
 * be read nothing built from it is kept, so that the next listing reads it again. One listing uses it, one computation
 * at a time.
 */
class BookSearch<R>(
    private val dictionary: suspend () -> List<IngredientEntryEntity>,
    private val id: (R) -> String,
    private val title: (R) -> String,
) {
    private var entries: Map<Long, IngredientEntryEntity>? = null
    private var built: Triple<List<R>, List<IngredientLineRow>, SearchIndex<R>>? = null

    /** The [rows] of a view, given newest first, with its recipes' ingredient [lines] and [tags], listed for [params]. */
    suspend fun listed(
        rows: List<R>,
        lines: List<IngredientLineRow>,
        tags: List<RecipeTagEntity>,
        params: ListingParams,
    ): Listed<R> {
        var found = if (WordPrefix.words(params.query).isEmpty()) rows else index(rows, lines).search(params.query)
        val filters = params.filters
        if (filters.tags.isNotEmpty()) {
            val carried = tags.groupBy({ it.recipeId }, { it.tagId })
            found = found.filter { row -> carried[id(row)].orEmpty().containsAll(filters.tags) }
        }
        found = found.inOrder(params.order, params.locale, title)
        filters.ingredient?.let { entry -> found = byIngredient(found, lines, entry) }
        return Listed(found, rows.isEmpty(), params)
    }

    /** Of [rows], in their order, those holding [entry] or an entry below it, the most of it per serving first. */
    private suspend fun byIngredient(rows: List<R>, lines: List<IngredientLineRow>, entry: Long): List<R> {
        val family = ingredientFamily(entry, entries().orEmpty().mapValues { it.value.parentId })
        val matched = lines.filter { it.dictionaryEntry in family }.groupBy { it.recipeId }
        return rows.filter { id(it) in matched }.mostPerServingFirst { row ->
            val held = matched.getValue(id(row))
            QuantityPerServing.of(held.map { MeasuredLine(it.unitType, it.unit, it.quantity) }, held.first().servings)
        }
    }

    private suspend fun index(rows: List<R>, lines: List<IngredientLineRow>): SearchIndex<R> {
        built?.let { (heldRows, heldLines, index) -> if (heldRows === rows && heldLines === lines) return index }
        val entries = entries()
        val byRecipe = lines.groupBy { it.recipeId }
        val index = SearchIndex(rows) { row ->
            listOf(title(row)) + byRecipe[id(row)].orEmpty().flatMap { namesOf(it, entries.orEmpty()) }
        }
        // An index built without the dictionary names no entry: it is not kept, and the next query builds it anew.
        built = if (entries != null) Triple(rows, lines, index) else null
        return index
    }

    /** The dictionary's entries by identity; null while it cannot be read, which is tried again the next time. */
    private suspend fun entries(): Map<Long, IngredientEntryEntity>? = entries ?: try {
        dictionary().associateBy { it.id }.also { entries = it }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private fun namesOf(row: IngredientLineRow, entries: Map<Long, IngredientEntryEntity>): List<String> {
        val entry = row.dictionaryEntry?.let(entries::get)
        val qualifier = row.qualifier?.takeIf { it.isNotBlank() }
        return listOfNotNull(entry?.labelRu, entry?.labelEn, row.freeTextName)
            .map { name -> listOfNotNull(name, qualifier).joinToString(" ") }
            .ifEmpty { listOfNotNull(qualifier) }
    }
}
