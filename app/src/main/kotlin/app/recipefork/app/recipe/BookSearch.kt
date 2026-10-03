package app.recipefork.app.recipe

import app.recipefork.app.book.IngredientLineRow
import app.recipefork.app.book.RecipeTagEntity
import app.recipefork.app.dictionary.IngredientEntryEntity
import app.recipefork.core.recipe.RecipeLanguage
import app.recipefork.core.scaling.MeasuredLine
import app.recipefork.core.scaling.QuantityPerServing
import app.recipefork.core.scaling.byQuantityPerServing
import app.recipefork.core.search.BookOrder
import app.recipefork.core.search.SearchIndex
import app.recipefork.core.search.WordPrefix
import app.recipefork.core.search.ingredientFamily
import app.recipefork.core.search.inOrder
import kotlinx.coroutines.CancellationException
import java.io.Serializable
import java.util.Locale

/** What a recipe of the book declares it is written in (L10N-004) as the language filter tells recipes apart (L10N-005). */
enum class BookLanguage {
    RU,
    EN,

    /** No language declared. */
    NOT_GIVEN,
    ;

    companion object {
        fun of(language: RecipeLanguage?): BookLanguage = when (language) {
            RecipeLanguage.RU -> RU
            RecipeLanguage.EN -> EN
            null -> NOT_GIVEN
        }
    }
}

/**
 * What narrows a view of the book beside its query (FLOW-035): a dictionary [ingredient] entry, which keeps the recipes
 * holding it or an entry below it (CAT-006) and orders them by how much of it a serving holds (CAT-002), [tags], all
 * of which a recipe must carry to be kept, and the [language] a recipe is written in (L10N-005).
 */
data class BookFilters(
    val ingredient: Long? = null,
    val tags: Set<Long> = emptySet(),
    val language: BookLanguage? = null,
) : Serializable {
    /** Whether any filter is on. */
    val any: Boolean get() = ingredient != null || tags.isNotEmpty() || language != null

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
        get() = listOf(query, order.name, filters.ingredient, filters.tags.sorted(), filters.language, locale.toLanguageTag()).toString()
}

/**
 * What a view lists for [params]: the [rows] found, in order; [viewIsEmpty] when the view holds nothing at all; the
 * [languages] its recipes are written in, whatever narrows it, in the order of [BookLanguage]; the dictionary entries
 * its recipes' ingredients [hold], whatever narrows it — what keeps an entry removed upstream offered by the filter;
 * the tags each recipe of the view [carries], by the recipe's identity, which its entry shows.
 */
data class Listed<R>(
    val rows: List<R>,
    val viewIsEmpty: Boolean,
    val params: ListingParams,
    val languages: List<BookLanguage> = emptyList(),
    val hold: Set<Long> = emptySet(),
    val carries: Map<String, List<Long>> = emptyMap(),
) {
    /** Whether a query was searched: one with a word in it. A query of none lists the whole view. */
    val searched: Boolean get() = WordPrefix.words(params.query).isNotEmpty()

    /** Whether anything narrowed the view: a query searched, or a filter on. */
    val narrowed: Boolean get() = searched || params.filters.any
}

/**
 * Finds, filters and orders the rows of one view of the book (FLOW-035). A query finds a recipe in which each of its
 * words begins a word of the title, of the description or of any of its ingredients' names, wherever each stands
 * ([WordPrefix]); an
 * ingredient is named by its dictionary entry's label or any of its synonyms, in either language, with its qualifier,
 * or by its free text: "картошка" finds a recipe holding картофель.
 * A filter by tags keeps the recipes carrying every one of them; a filter by an ingredient entry keeps the recipes
 * holding it or an entry below it (CAT-006), the most of it per serving first (CAT-002, CAT-007), in the order chosen
 * for the view among recipes of equal or no comparable quantity. A filter by language keeps the recipes declaring it, or
 * declaring none for [BookLanguage.NOT_GIVEN] (L10N-005).
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
    private val language: (R) -> RecipeLanguage? = { null },
    private val description: (R) -> String = { "" },
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
        filters.language?.let { chosen -> found = found.filter { BookLanguage.of(language(it)) == chosen } }
        val carried = tags.groupBy({ it.recipeId }, { it.tagId })
        if (filters.tags.isNotEmpty()) {
            found = found.filter { row -> carried[id(row)].orEmpty().containsAll(filters.tags) }
        }
        found = found.inOrder(params.order, params.locale, title)
        filters.ingredient?.let { entry -> found = byIngredient(found, lines, entry) }
        val languages = rows.mapTo(HashSet()) { BookLanguage.of(language(it)) }
        val inView = rows.mapTo(HashSet()) { id(it) }
        val hold = lines.filter { it.recipeId in inView }.mapNotNullTo(HashSet()) { it.dictionaryEntry }
        return Listed(found, rows.isEmpty(), params, BookLanguage.entries.filter { it in languages }, hold, carried)
    }

    /**
     * Of [rows], in their order, those holding [entry] or an entry below it, the most of it per serving first: each
     * matched line through its own entry's comparison factors (CAT-007, CAT-009), which order and are never shown
     * (DOM-011).
     */
    private suspend fun byIngredient(rows: List<R>, lines: List<IngredientLineRow>, entry: Long): List<R> {
        val entries = entries().orEmpty()
        val family = ingredientFamily(entry, entries.mapValues { it.value.parentId })
        val matched = lines.filter { it.dictionaryEntry in family }.groupBy { it.recipeId }
        return rows.filter { id(it) in matched }.byQuantityPerServing { row ->
            val held = matched.getValue(id(row))
            val measured = held.map { line ->
                MeasuredLine(line.unitType, line.unit, line.quantity, entries[line.dictionaryEntry]?.factors.orEmpty())
            }
            QuantityPerServing.of(measured, held.first().servings)
        }
    }

    private suspend fun index(rows: List<R>, lines: List<IngredientLineRow>): SearchIndex<R> {
        built?.let { (heldRows, heldLines, index) -> if (heldRows === rows && heldLines === lines) return index }
        val entries = entries()
        val byRecipe = lines.groupBy { it.recipeId }
        val index = SearchIndex(rows) { row ->
            listOf(title(row), description(row)) + byRecipe[id(row)].orEmpty().flatMap { namesOf(it, entries.orEmpty()) }
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
        val synonyms = entry?.let { it.synonymsRu + it.synonymsEn }.orEmpty()
        return (listOfNotNull(entry?.labelRu, entry?.labelEn) + synonyms + listOfNotNull(row.freeTextName))
            .map { name -> listOfNotNull(name, qualifier).joinToString(" ") }
            .ifEmpty { listOfNotNull(qualifier) }
    }
}
