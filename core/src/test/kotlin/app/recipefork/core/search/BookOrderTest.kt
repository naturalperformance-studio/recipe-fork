package app.recipefork.core.search

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

/** FLOW-035 (3): the orders of a view of the recipe book. */
class BookOrderTest {
    private val newestFirst = listOf("ёлка", "Яблоко", "елка", "", "Арбуз", "ель", "  ", "банан")

    @Test
    fun newestIsTheViewsOwnOrderAndOldestItsReverse() {
        assertEquals(newestFirst, newestFirst.inOrder(BookOrder.NEWEST, Locale.ENGLISH) { it })
        assertEquals(newestFirst.reversed(), newestFirst.inOrder(BookOrder.OLDEST, Locale.ENGLISH) { it })
    }

    @Test
    fun byTitleIsAlphabeticalInTheInterfaceLanguageWithYoAsYe() {
        // "ёлка" and "елка" compare equal, so they keep their newest-first order; untitled recipes come last.
        assertEquals(
            listOf("Арбуз", "банан", "ёлка", "елка", "ель", "Яблоко", "", "  "),
            newestFirst.inOrder(BookOrder.TITLE, Locale.forLanguageTag("ru")) { it },
        )
        assertEquals(
            listOf("apple", "Banana", "cherry"),
            listOf("cherry", "Banana", "apple").inOrder(BookOrder.TITLE, Locale.ENGLISH) { it },
        )
    }
}
