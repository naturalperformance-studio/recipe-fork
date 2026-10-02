package app.recipefork.core.search

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** FLOW-035 (4) with the word-prefix match of FLOW-057: what a query of the recipe book finds. */
class SearchIndexTest {
    private class Entry(val title: String, val ingredients: List<String> = emptyList())

    private fun index(vararg entries: Entry) = SearchIndex(entries.toList()) { listOf(it.title) + it.ingredients }

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

    @Test
    fun aQueryOfNoWordFindsEverythingInItsOrder() {
        val index = index(Entry("B"), Entry("A"))
        assertEquals(listOf("B", "A"), index.titles(""))
        assertEquals(listOf("B", "A"), index.titles("  ,. "))
    }

    // The book's search stays quick: 500 recipes of 10 ingredients, each named in two languages and as free text.
    // One query, the index built, takes well under the 50 ms bound on the test machine (a frame is 16 ms; the query
    // runs off the main thread, so this bounds how soon the list answers a keystroke, not the frame rate).
    @Test
    fun oneQueryOverFiveHundredRecipesOfTenIngredientsStaysUnderFiftyMilliseconds() {
        val words = listOf("курица", "рис", "свекла", "морковь", "лук", "чеснок", "масло", "соль", "перец", "мука")
        val english = listOf("chicken", "rice", "beetroot", "carrot", "onion", "garlic", "butter", "salt", "pepper", "flour")
        val entries = (0 until 500).map { i ->
            Entry(
                "Рецепт номер $i ${words[i % 10]}",
                (0 until 10).flatMap { j ->
                    val k = (i + j) % 10
                    listOf("${words[k]} отборн$j", "${english[k]} choice$j", "домашний продукт $i-$j")
                },
            )
        }
        val index = SearchIndex(entries) { listOf(it.title) + it.ingredients }
        val queries = listOf("кур", "ре но", "chick cho", "домаш прод", "zzz", "ё", "морковь отборн9")
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
    }
}
