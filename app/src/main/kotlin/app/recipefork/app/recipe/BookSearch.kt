package app.recipefork.app.recipe

import app.recipefork.app.book.IngredientNameRow
import app.recipefork.app.dictionary.IngredientEntryEntity
import app.recipefork.core.search.BookOrder
import app.recipefork.core.search.SearchIndex
import app.recipefork.core.search.WordPrefix
import app.recipefork.core.search.inOrder
import kotlinx.coroutines.CancellationException
import java.util.Locale

/** What a view of the book is listed by: the query in its search field, its order, and the interface's collation. */
data class ListingParams(val query: String, val order: BookOrder, val locale: Locale)

/** What a view lists for [params]: the [rows] found, in order; [viewIsEmpty] when the view holds nothing at all. */
data class Listed<R>(val rows: List<R>, val viewIsEmpty: Boolean, val params: ListingParams) {
    /** Whether a query was searched: one with a word in it. A query of none lists the whole view. */
    val searched: Boolean get() = WordPrefix.words(params.query).isNotEmpty()
}

/**
 * Finds and orders the rows of one view of the book (FLOW-035 (3, 4)). A query finds a recipe whose title, or one of
 * whose ingredients' names, holds a word beginning with each of its words ([WordPrefix]); an ingredient is named by
 * its dictionary entry's label in either language with its qualifier, or by its free text.
 *
 * The search index is kept for the rows it was built from and rebuilt only when the book's rows change, so that a
 * keystroke only compares words. The dictionary is read the first time a query is searched. One listing uses it, one
 * computation at a time.
 */
class BookSearch<R>(
    private val dictionary: suspend () -> List<IngredientEntryEntity>,
    private val id: (R) -> String,
    private val title: (R) -> String,
) {
    private var labels: Map<Long, IngredientEntryEntity>? = null
    private var built: Triple<List<R>, List<IngredientNameRow>, SearchIndex<R>>? = null

    /** The [rows] of a view, given newest first, with what names their ingredients — [names] — listed for [params]. */
    suspend fun listed(rows: List<R>, names: List<IngredientNameRow>, params: ListingParams): Listed<R> {
        val found = if (WordPrefix.words(params.query).isEmpty()) rows else index(rows, names).search(params.query)
        return Listed(found.inOrder(params.order, params.locale, title), rows.isEmpty(), params)
    }

    private suspend fun index(rows: List<R>, names: List<IngredientNameRow>): SearchIndex<R> {
        built?.let { (heldRows, heldNames, index) -> if (heldRows === rows && heldNames === names) return index }
        val labels = labels()
        val byRecipe = names.groupBy { it.recipeId }
        val index = SearchIndex(rows) { row ->
            listOf(title(row)) + byRecipe[id(row)].orEmpty().flatMap { namesOf(it, labels) }
        }
        built = Triple(rows, names, index)
        return index
    }

    /** The dictionary's entries by identity; none while it cannot be read, which is tried again on the next query. */
    private suspend fun labels(): Map<Long, IngredientEntryEntity> = labels ?: try {
        dictionary().associateBy { it.id }.also { labels = it }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        emptyMap()
    }

    private fun namesOf(row: IngredientNameRow, labels: Map<Long, IngredientEntryEntity>): List<String> {
        val entry = row.dictionaryEntry?.let(labels::get)
        val qualifier = row.qualifier?.takeIf { it.isNotBlank() }
        return listOfNotNull(entry?.labelRu, entry?.labelEn, row.freeTextName)
            .map { name -> listOfNotNull(name, qualifier).joinToString(" ") }
            .ifEmpty { listOfNotNull(qualifier) }
    }
}
