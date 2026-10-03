package app.recipefork.app.recipe

import app.recipefork.app.book.IngredientLineRow
import app.recipefork.app.book.RecipeTagEntity
import app.recipefork.app.dictionary.IngredientEntryEntity
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.RecipeLanguage
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.Rational
import app.recipefork.core.search.BookOrder
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/** FLOW-035, CAT-002, CAT-006, CAT-007: what a view lists under a query, its filters and its order. */
class BookSearchTest {
    private val chicken = IngredientEntryEntity(1, "курица", "chicken", null)
    private val wings = IngredientEntryEntity(2, "крылышки", "chicken wings", 1)
    private val thigh = IngredientEntryEntity(3, "филе бедра", "thigh fillet", 1)
    private val broth = IngredientEntryEntity(4, "бульон", "broth", null)
    private val dictionary = listOf(chicken, wings, thigh, broth)

    private fun search(read: suspend () -> List<IngredientEntryEntity> = { dictionary }) = BookSearch<String>(read, { it }, { it })

    private fun params(query: String = "", filters: BookFilters = BookFilters(), order: BookOrder = BookOrder.NEWEST) =
        ListingParams(query, order, Locale.ENGLISH, filters)

    private fun line(
        recipe: String,
        entry: IngredientEntryEntity?,
        grams: Long?,
        servings: Int? = 4,
        qualifier: String? = null,
        free: String? = null,
        unit: QuantityUnit = QuantityUnit.GRAM,
    ) = IngredientLineRow(
        recipe, entry?.id, qualifier, free, if (grams == null) UnitType.TASTE_UNMEASURED else unit.type,
        if (grams == null) null else unit, grams?.let { Rational.of(it) }, servings,
    )

    // CAT-006: the parent matches itself and its children; a child only itself; free text and a qualifier never.
    @Test
    fun anIngredientFilterMatchesTheEntryAndTheEntriesBelowIt() = runBlocking {
        val rows = listOf("wings", "thigh", "chicken", "free text", "broth")
        val lines = listOf(
            line("wings", wings, 400),
            line("thigh", thigh, 300),
            line("chicken", chicken, 200),
            line("free text", null, 900, free = "курица"),
            line("broth", broth, 900, qualifier = "куриный"),
        )
        val search = search()
        assertEquals(listOf("wings", "thigh", "chicken"), search.listed(rows, lines, emptyList(), params(filters = BookFilters(1))).rows)
        assertEquals(listOf("wings"), search.listed(rows, lines, emptyList(), params(filters = BookFilters(2))).rows)
    }

    // CAT-002, CAT-007: the most per serving first, matched lines summed; the incomparable last, in the chosen order.
    @Test
    fun anIngredientFilterOrdersByQuantityPerServing() = runBlocking {
        val rows = listOf("Aa 125 g", "Bb 150 g", "Cc 200 g", "Dd to taste", "Ee no servings")
        val lines = listOf(
            line("Aa 125 g", thigh, 500, servings = 4),
            line("Bb 150 g", chicken, 300, servings = 2),
            line("Cc 200 g", thigh, 500, servings = 4),
            line("Cc 200 g", wings, 300, servings = 4),
            line("Dd to taste", chicken, null),
            line("Ee no servings", chicken, 900, servings = null),
        )
        val search = search()
        val filtered = BookFilters(ingredient = 1)
        assertEquals(
            listOf("Cc 200 g", "Bb 150 g", "Aa 125 g", "Dd to taste", "Ee no servings"),
            search.listed(rows, lines, emptyList(), params(filters = filtered)).rows,
        )
        assertEquals(
            listOf("Cc 200 g", "Bb 150 g", "Aa 125 g", "Ee no servings", "Dd to taste"),
            search.listed(rows, lines, emptyList(), params(filters = filtered, order = BookOrder.OLDEST)).rows,
        )
        // Without the filter, the chosen order applies again.
        assertEquals(rows.reversed(), search.listed(rows, lines, emptyList(), params(order = BookOrder.OLDEST)).rows)
    }

    // A filter by tags keeps the recipes carrying every one of them, and combines with the query.
    @Test
    fun aTagFilterKeepsTheRecipesCarryingEveryTag() = runBlocking {
        val rows = listOf("Pasta", "Pizza", "Khachapuri", "Plain")
        val tags = listOf(RecipeTagEntity("Pasta", 10), RecipeTagEntity("Pizza", 10), RecipeTagEntity("Pizza", 12), RecipeTagEntity("Khachapuri", 11))
        val search = search()
        assertEquals(listOf("Pasta", "Pizza"), search.listed(rows, emptyList(), tags, params(filters = BookFilters(tags = setOf(10)))).rows)
        assertEquals(listOf("Pizza"), search.listed(rows, emptyList(), tags, params(filters = BookFilters(tags = setOf(10, 12)))).rows)
        assertEquals(listOf("Pasta"), search.listed(rows, emptyList(), tags, params("pas", BookFilters(tags = setOf(10)))).rows)
        assertEquals(emptyList<String>(), search.listed(rows, emptyList(), tags, params(filters = BookFilters(tags = setOf(10, 11)))).rows)
    }

    // An index built while the dictionary could not be read is not kept: the next query reads it again and finds by it.
    @Test
    fun anIndexBuiltWithoutTheDictionaryIsNotKept() = runBlocking {
        var reads = 0
        val search = search {
            reads++
            if (reads == 1) error("the dictionary cannot be read yet") else dictionary
        }
        val rows = listOf("Soup")
        val lines = listOf(line("Soup", wings, 300))
        assertEquals(emptyList<String>(), search.listed(rows, lines, emptyList(), params("wings")).rows)
        assertEquals(listOf("Soup"), search.listed(rows, lines, emptyList(), params("wings")).rows)
        assertEquals(listOf("Soup"), search.listed(rows, lines, emptyList(), params("крыл")).rows)
        // Once read, the dictionary and the index are kept.
        assertEquals(2, reads)
    }

    private class Row(val title: String, val language: RecipeLanguage? = null, val description: String = "")

    private fun rowSearch() = BookSearch<Row>({ dictionary }, { it.title }, { it.title }, { it.language }, { it.description })

    private fun BookSearch<Row>.titles(rows: List<Row>, params: ListingParams) =
        runBlocking { listed(rows, emptyList(), emptyList(), params).rows.map { it.title } }

    // L10N-005: a filter by language keeps the recipes declaring it, or declaring none for "not given"; it combines with
    // the query, the order and the other filters; the view's languages are offered whatever narrows it.
    @Test
    fun aLanguageFilterKeepsTheRecipesWrittenInIt() = runBlocking {
        val rows = listOf(
            Row("Borscht", RecipeLanguage.EN),
            Row("Борщ", RecipeLanguage.RU),
            Row("Beef stew", RecipeLanguage.EN),
            Row("Blini"),
        )
        val tags = listOf(RecipeTagEntity("Beef stew", 10), RecipeTagEntity("Борщ", 10))
        val search = rowSearch()
        fun listed(params: ListingParams) = runBlocking { search.listed(rows, emptyList(), tags, params) }
        assertEquals(listOf("Borscht", "Beef stew"), search.titles(rows, params(filters = BookFilters(language = BookLanguage.EN))))
        assertEquals(listOf("Борщ"), search.titles(rows, params(filters = BookFilters(language = BookLanguage.RU))))
        assertEquals(listOf("Blini"), search.titles(rows, params(filters = BookFilters(language = BookLanguage.NOT_GIVEN))))
        // With the query, the order and a tag filter.
        assertEquals(listOf("Borscht"), search.titles(rows, params("bor", BookFilters(language = BookLanguage.EN))))
        assertEquals(
            listOf("Beef stew", "Borscht"),
            search.titles(rows, params(filters = BookFilters(language = BookLanguage.EN), order = BookOrder.TITLE)),
        )
        assertEquals(
            listOf("Beef stew"),
            listed(params(filters = BookFilters(tags = setOf(10), language = BookLanguage.EN))).rows.map { it.title },
        )
        // The languages present in the view, in a fixed order, whatever the filter keeps.
        val all = listOf(BookLanguage.RU, BookLanguage.EN, BookLanguage.NOT_GIVEN)
        assertEquals(all, listed(params()).languages)
        assertEquals(all, listed(params("zzz", BookFilters(language = BookLanguage.RU))).languages)
        assertEquals(listOf(BookLanguage.EN), runBlocking { search.listed(rows.take(1), emptyList(), emptyList(), params()) }.languages)
        assertEquals(true, BookFilters(language = BookLanguage.NOT_GIVEN).any)
    }

    // L10N-006: a query finds a word beginning in the description, with case and ё/е folded, as in the title.
    @Test
    fun aQueryFindsAWordBeginningInTheDescription() = runBlocking {
        val rows = listOf(
            Row("Пирог", description = "Бабушкин рецепт с ЁЖЕВИКОЙ из сада"),
            Row("Torte", description = "A rich chocolate layer cake"),
            Row("Суп", description = "Лёгкий и быстрый"),
        )
        val search = rowSearch()
        assertEquals(listOf("Пирог"), search.titles(rows, params("ежевик")))
        assertEquals(listOf("Пирог"), search.titles(rows, params("пирог бабушк")))
        assertEquals(listOf("Torte"), search.titles(rows, params("CHOC")))
        assertEquals(listOf("Суп"), search.titles(rows, params("легк")))
        // A word inside another word is no beginning of one.
        assertEquals(emptyList<String>(), search.titles(rows, params("colate")))
    }
}
