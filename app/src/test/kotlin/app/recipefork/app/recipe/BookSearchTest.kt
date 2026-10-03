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

/** FLOW-035, CAT-002, CAT-006, CAT-007, CAT-009: what a view lists under a query, its filters and its order. */
class BookSearchTest {
    private val chicken = IngredientEntryEntity(1, "курица", "chicken", null, factors = mapOf("g" to 1.0, "pc" to 1500.0))
    private val wings = IngredientEntryEntity(2, "крылышки", "chicken wings", 1, factors = mapOf("g" to 1.0, "pc" to 80.0))
    private val thigh = IngredientEntryEntity(3, "филе бедра", "thigh fillet", 1, factors = mapOf("g" to 1.0))
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

    // CAT-007, CAT-009: lines in grams and in pieces are each converted by their own entry's factors and summed; a line
    // in a unit its entry has no factor for contributes nothing; a recipe of nothing comparable follows every other.
    @Test
    fun anIngredientFilterComparesUnitsThroughTheEntriesFactors() = runBlocking {
        val rows = listOf("A 8 wings", "B 500 g", "taste", "C 1 carcass", "thigh pieces", "mixed")
        val lines = listOf(
            line("A 8 wings", wings, 8, unit = QuantityUnit.PIECE),
            line("B 500 g", chicken, 500),
            line("taste", chicken, null),
            line("C 1 carcass", chicken, 1, unit = QuantityUnit.PIECE),
            line("thigh pieces", thigh, 3, unit = QuantityUnit.PIECE),
            // 320 g of chicken and 8 wings for 4 servings: 80 + 160 = 240; the thigh pieces contribute nothing.
            line("mixed", chicken, 320),
            line("mixed", wings, 8, unit = QuantityUnit.PIECE),
            line("mixed", thigh, 5, unit = QuantityUnit.PIECE),
        )
        val search = search()
        val filtered = BookFilters(ingredient = 1)
        assertEquals(
            listOf("C 1 carcass", "mixed", "A 8 wings", "B 500 g", "taste", "thigh pieces"),
            search.listed(rows, lines, emptyList(), params(filters = filtered)).rows,
        )
        assertEquals(
            listOf("C 1 carcass", "mixed", "A 8 wings", "B 500 g", "thigh pieces", "taste"),
            search.listed(rows, lines, emptyList(), params(filters = filtered, order = BookOrder.OLDEST)).rows,
        )
        // The filter still matches as before — a leaf counts only its own lines, 160 each here, equal ones in the chosen
        // order — and combines with the query.
        assertEquals(listOf("A 8 wings", "mixed"), search.listed(rows, lines, emptyList(), params(filters = BookFilters(2))).rows)
        assertEquals(listOf("mixed", "A 8 wings"), search.listed(rows, lines, emptyList(), params("wings", filtered)).rows)
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

    // T-130: the book's search reads words as the ingredient field does — no compound found by the word it is built
    // on, "-ves" plurals and other forms found.
    @Test
    fun aQueryFindsNoWordBuiltOnIt() = runBlocking {
        val butter = IngredientEntryEntity(10, "масло сливочное", "butter", null)
        val lemon = IngredientEntryEntity(11, "лимон", "lemon", null)
        val pepper = IngredientEntryEntity(12, "перец", "pepper", null)
        val sugar = IngredientEntryEntity(13, "сахар", "sugar", null)
        val bayLeaf = IngredientEntryEntity(14, "лавровый лист", "bay leaf", null)
        val onion = IngredientEntryEntity(15, "лук", "onion", null)
        val carrot = IngredientEntryEntity(16, "морковь", "carrot", null)
        val search = BookSearch<String>({ listOf(butter, lemon, pepper, sugar, bayLeaf, onion, carrot) }, { it }, { it })
        val rows = listOf("A", "B", "C", "D", "E", "F", "G", "H")
        val lines = listOf(
            line("A", butter, 100),
            line("B", lemon, 100),
            line("C", pepper, null),
            line("D", sugar, 100),
            line("E", null, 500, free = "buttermilk"),
            line("F", null, 500, free = "лимонад"),
            line("G", null, 100, free = "pepperoni"),
            line("H", bayLeaf, null),
            line("H", onion, 100),
            line("H", carrot, 100),
        )
        suspend fun found(query: String) = search.listed(rows, lines, emptyList(), params(query)).rows
        assertEquals(listOf("E"), found("buttermilk"))
        assertEquals(listOf("F"), found("лимонад"))
        assertEquals(listOf("G"), found("pepperoni"))
        assertEquals(emptyList<String>(), found("сахарин"))
        assertEquals(emptyList<String>(), found("watermelon"))
        // The words it is built on still find what holds them.
        assertEquals(listOf("A", "E"), found("butter"))
        assertEquals(listOf("B", "F"), found("лимон"))
        assertEquals(listOf("D"), found("сахара"))
        // Another form of a word finds it: a "-ves" plural, a Russian case.
        assertEquals(listOf("H"), found("bay leaves"))
        assertEquals(listOf("H"), found("лука моркови"))
        assertEquals(listOf("B"), found("lemons"))
    }

    // T-129, FLOW-035: a query finds a recipe by a synonym of its ingredient's entry, in either language and form; the
    // ingredient filter is unaffected by synonyms.
    @Test
    fun aQueryFindsARecipeByASynonymOfItsIngredient() = runBlocking {
        val potato = IngredientEntryEntity(10, "картофель", "potato", null, synonymsRu = listOf("картошка"), synonymsEn = listOf("spud"))
        val search = BookSearch<String>({ dictionary + potato }, { it }, { it })
        val rows = listOf("Пюре", "Бульон", "Драники")
        val lines = listOf(
            line("Пюре", potato, 500),
            line("Бульон", broth, 900),
            line("Драники", null, 400, free = "картошка тёртая"),
        )
        suspend fun found(query: String) = search.listed(rows, lines, emptyList(), params(query)).rows
        assertEquals(listOf("Пюре", "Драники"), found("картошка"))
        assertEquals(listOf("Пюре", "Драники"), found("картошку"))
        assertEquals(listOf("Пюре"), found("spud"))
        assertEquals(listOf("Пюре"), found("картофель"))
        assertEquals(listOf("Пюре"), search.listed(rows, lines, emptyList(), params(filters = BookFilters(potato.id))).rows)
        assertEquals(listOf("Пюре"), search.listed(rows, lines, emptyList(), params("картошка", BookFilters(potato.id))).rows)
    }
}

