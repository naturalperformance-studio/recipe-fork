package app.recipefork.core.search

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** L10N-007 and FLOW-057 as T-124 widened them: diacritics folded, and a word found in its other grammatical forms. */
class WordPrefixTest {
    private fun holds(text: String, query: String) = WordPrefix.holdsEvery(WordPrefix.Words(WordPrefix.words(text)), WordPrefix.words(query))

    @Test
    fun foldingDropsLatinDiacriticsAndKeepsCyrillic() {
        assertEquals("jalapeno", WordPrefix.fold("Jalapeño"))
        assertEquals("creme fraiche", WordPrefix.fold("crème fraîche"))
        assertEquals("gruyere comte epoisses", WordPrefix.fold("Gruyère Comté Époisses"))
        assertEquals("facon garcon muller", WordPrefix.fold("façon garçon Müller"))
        assertEquals("strasse aeble", WordPrefix.fold("Straße Æble"))
        // `й` is a letter of its own, not `и` with a mark; `ё` is `е`.
        assertEquals("чай йогурт еж", WordPrefix.fold("Чай Йогурт Ёж"))
    }

    @Test
    fun aQueryWithoutDiacriticsFindsTheWordWithThemAndTheReverse() {
        assertTrue(holds("jalapeño", "jalapeno"))
        assertTrue(holds("jalapeño", "jalap"))
        assertTrue(holds("crème fraîche", "creme fraiche"))
        assertTrue(holds("creme fraiche", "crème"))
        assertTrue(holds("Gruyère", "gruyere"))
    }

    @Test
    fun aWordIsFoundInItsOtherForms() {
        // Russian endings, both ways.
        assertTrue(holds("яйцо", "яйца"))
        assertTrue(holds("яйца куриные", "яйцо"))
        assertTrue(holds("орех грецкий", "грецкие орехи"))
        assertTrue(holds("баклажан", "баклажаны"))
        // A fleeting vowel.
        assertTrue(holds("огурец солёный", "солёные огурцы"))
        assertTrue(holds("кабачок", "кабачки"))
        assertTrue(holds("белок", "белки"))
        // A stem of five letters or more found at the beginning of the typed word.
        assertTrue(holds("редис", "редиска"))
        assertTrue(holds("морковь", "морковка"))
        // English plurals.
        assertTrue(holds("egg", "eggs"))
        assertTrue(holds("cherry", "cherries"))
        assertTrue(holds("potato", "potatoes"))
        assertTrue(holds("peach", "peaches"))
        assertTrue(holds("lemon", "lemons"))
    }

    @Test
    fun anotherFormIsNoLooserThanThat() {
        // A short beginning stays a beginning: "кури" begins no word of "куркума", nor is it a form of it.
        assertFalse(holds("куркума", "кури"))
        assertFalse(holds("молоко", "локо"))
        // A typed stem never finds a longer word it merely begins.
        assertFalse(holds("pickled ginger", "pickles"))
        assertFalse(holds("сливки", "сливочное"))
        assertFalse(holds("лук", "чеснок"))
        // Every typed word must still be found.
        assertFalse(holds("молоко", "кокосовое молоко"))
    }

    @Test
    fun theFormsOfAWord() {
        assertEquals(setOf("яйц"), Inflection.forms("яйца"))
        assertEquals(setOf("огурец", "огурц"), Inflection.forms("огурец"))
        assertEquals(setOf("egg"), Inflection.forms("eggs"))
        // A word under four letters is its own only form.
        assertEquals(setOf("лук"), Inflection.forms("лук"))
        assertEquals(setOf("egg"), Inflection.forms("egg"))
    }
}
