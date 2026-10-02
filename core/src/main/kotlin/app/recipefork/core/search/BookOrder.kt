package app.recipefork.core.search

import java.text.Collator
import java.util.Locale

/** The orders a view of the recipe book offers (FLOW-035, FLOW-039). */
enum class BookOrder {
    /** The view's own order: for the own recipes the last change of content first, for the marked the last marked. */
    NEWEST,

    /** The reverse of [NEWEST]. */
    OLDEST,

    /** Alphabetical by title in the interface language's collation, `ё` read as `е`; a recipe with no title last. */
    TITLE,
}

/**
 * This list, given newest first, in [order]: titles — given by [title] — are compared in the collation of [locale].
 * Recipes whose titles compare equal keep their newest-first order.
 */
fun <T> List<T>.inOrder(order: BookOrder, locale: Locale, title: (T) -> String): List<T> = when (order) {
    BookOrder.NEWEST -> this
    BookOrder.OLDEST -> asReversed().toList()
    BookOrder.TITLE -> {
        val collator = Collator.getInstance(locale)
        val (untitled, titled) = partition { title(it).isBlank() }
        titled.map { it to collator.getCollationKey(WordPrefix.fold(title(it).trim())) }
            .sortedWith { a, b -> a.second.compareTo(b.second) }
            .map { it.first } + untitled
    }
}
