package app.recipefork.core.search

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** FLOW-035 (4) with the word-prefix match of FLOW-057: what a query of the recipe book finds. */
class SearchIndexTest {
    private class Entry(val title: String, val ingredients: List<String> = emptyList(), val description: String = "")

    private fun index(vararg entries: Entry) =
        SearchIndex(entries.toList()) { listOf(it.title, it.description) + it.ingredients }

    private fun SearchIndex<Entry>.titles(query: String) = search(query).map { it.title }

    @Test
    fun aQueryFindsATitleOrAnIngredientNameWithAWordBeginningWithIt() {
        val index = index(
            Entry("Курица с рисом"),
            Entry("Плов", listOf("Курица бедро", "Рис")),
            Entry("Борщ", listOf("Свёкла")),
            // "кур" inside a word is no beginning of one.
            Entry("Окурок"),
        )
        assertEquals(listOf("Курица с рисом", "Плов"), index.titles("кур"))
    }

    @Test
    fun caseAndYoAreIgnoredOnBothSides() {
        val index = index(Entry("Ёжики"), Entry("Борщ", listOf("свекла")), Entry("CHICKEN pie"))
        assertEquals(listOf("Ёжики"), index.titles("ежик"))
        assertEquals(listOf("Ёжики"), index.titles("ЁЖ"))
        assertEquals(listOf("Борщ"), index.titles("Свёк"))
        assertEquals(listOf("CHICKEN pie"), index.titles("chick"))
    }

    @Test
    fun everyWordOfTheQueryMustBeginAWordOfTheRecipeWhereverItStands() {
        val index = index(
            Entry("Курица с рисом"),
            // "кур" and "рис" are both here, in two different ingredients.
            Entry("Плов", listOf("Курица", "Рис")),
            Entry("Курица запечённая"),
            Entry("Рисовая каша", listOf("Молоко")),
        )
        // Within one name, as before.
        assertEquals(listOf("Курица с рисом"), index.titles("кур с"))
        // Across ingredients, and across the title and an ingredient.
        assertEquals(listOf("Курица с рисом", "Плов"), index.titles("рис  кур"))
        assertEquals(listOf("Курица с рисом", "Плов"), index.titles("кур, рис"))
        assertEquals(listOf("Плов"), index.titles("плов кур"))
        // A word found nowhere in the recipe keeps it out.
        assertEquals(emptyList(), index.titles("плов молоко"))
    }

    // L10N-006: a word beginning in the description finds the recipe, case and ё folded, alone or with the title's.
    @Test
    fun aQueryFindsAWordBeginningInTheDescription() {
        val index = index(
            Entry("Пирог", description = "Бабушкин рецепт с ЁЖЕВИКОЙ"),
            Entry("Cake", description = "Rich, dark chocolate"),
            Entry("Суп"),
        )
        assertEquals(listOf("Пирог"), index.titles("ежев"))
        assertEquals(listOf("Пирог"), index.titles("пир бабушк"))
        assertEquals(listOf("Cake"), index.titles("CHOCO"))
        assertEquals(emptyList(), index.titles("ocolate"))
    }

    @Test
    fun aQueryOfNoWordFindsEverythingInItsOrder() {
        val index = index(Entry("B"), Entry("A"))
        assertEquals(listOf("B", "A"), index.titles(""))
        assertEquals(listOf("B", "A"), index.titles("  ,. "))
    }

    // The book's search stays quick: 500 recipes of 10 ingredients, each named in two languages and as free text, each
    // with a description of about 500 characters (L10N-006).
    // One query, the index built, takes well under the 50 ms bound on the test machine (a frame is 16 ms; the query
    // runs off the main thread, so this bounds how soon the list answers a keystroke, not the frame rate).
    @Test
    fun oneQueryOverFiveHundredRecipesOfTenIngredientsStaysUnderFiftyMilliseconds() {
        val words = listOf("курица", "рис", "свекла", "морковь", "лук", "чеснок", "масло", "соль", "перец", "мука")
        val english = listOf("chicken", "rice", "beetroot", "carrot", "onion", "garlic", "butter", "salt", "pepper", "flour")
        val prose = listOf(
            "Бабушкин", "рецепт", "который", "готовили", "по", "воскресеньям", "летом", "на", "даче", "с", "ёжевикой",
            "и", "свежей", "зеленью", "a", "family", "favourite", "simmered", "slowly", "until", "tender", "served", "warm",
        )
        val entries = (0 until 500).map { i ->
            val description = generateSequence(i) { it + 7 }.map { prose[it % prose.size] + (it % 13) }
                .runningFold("") { text, word -> if (text.isEmpty()) word else "$text $word" }
                .first { it.length >= 500 }
            Entry(
                "Рецепт номер $i ${words[i % 10]}",
                (0 until 10).flatMap { j ->
                    val k = (i + j) % 10
                    listOf("${words[k]} отборн$j", "${english[k]} choice$j", "домашний продукт $i-$j")
                },
                description,
            )
        }
        assertTrue(entries.all { it.description.length in 500..520 })
        val index = SearchIndex(entries) { listOf(it.title, it.description) + it.ingredients }
        val queries = listOf("кур", "ре но", "chick cho", "домаш прод", "zzz", "ё", "морковь отборн9", "ежевик simmer", "воскр12")
        // Warmed up first, so that the bound is on the query, not on the JIT.
        repeat(20) { queries.forEach { index.search(it) } }
        val times = (0 until 21).map { n ->
            val start = System.nanoTime()
            index.search(queries[n % queries.size])
            (System.nanoTime() - start) / 1_000_000.0
        }.sorted()
        val median = times[times.size / 2]
        assertTrue(median < 50.0, "one query took $median ms (all: $times)")
        assertTrue(index.search("кур").size == 500)
        assertTrue(index.search("ежевик").isNotEmpty())
    }

    // T-124: the book's search reads words as the ingredient field does — diacritics folded, other forms found.
    @Test
    fun aQueryFindsAWordWithoutItsDiacriticsAndInAnotherForm() {
        val index = index(Entry("Crème brûlée"), Entry("Борщ", listOf("Свёкла", "Яйцо")), Entry("Пирог с огурцами"))
        assertEquals(listOf("Crème brûlée"), index.titles("creme brulee"))
        assertEquals(listOf("Борщ"), index.titles("яйца"))
        assertEquals(listOf("Пирог с огурцами"), index.titles("огурец"))
    }
}
