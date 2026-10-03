package app.recipefork.app.editor

import app.recipefork.app.dictionary.DictionarySnapshot
import app.recipefork.app.dictionary.TagEntity
import app.recipefork.app.dictionary.TagOffer
import app.recipefork.core.scaling.InterfaceLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * What the tag search offers (DOM-008) from the tag dictionary the application ships: hundreds of tags, searched by
 * the beginnings of their words as the app's search folds them, never listed whole.
 */
class TagOfferTest {
    private val shipped: List<TagEntity> =
        DictionarySnapshot.parse(File("src/main/assets/${DictionarySnapshot.ASSET}").readText()).tags
            .map { TagEntity(it.id, it.labelRu, it.labelEn) }

    private fun offered(typed: String, language: InterfaceLanguage = InterfaceLanguage.RUSSIAN, excluded: Set<Long> = emptySet()) =
        TagOffer.of(typed, shipped, language, excluded).map { if (language == InterfaceLanguage.RUSSIAN) it.labelRu else it.labelEn }

    @Test
    fun theShippedDictionaryIsLargeEnoughToNeedSearching() = assertTrue(shipped.size > 100)

    @Test
    fun nothingIsOfferedUntilAWordIsTyped() {
        assertEquals(emptyList<String>(), offered(""))
        assertEquals(emptyList<String>(), offered("   "))
        assertEquals(emptyList<String>(), offered(" - "))
    }

    // Scenario 4: a Russian cuisine is an adjective, found by its beginning; so are a method, a course, a meal, a diet.
    @Test
    fun theBeginningOfARussianWordFindsItsTag() {
        assertEquals("итальянская", offered("итал").first())
        assertEquals(listOf("аэрогриль"), offered("аэро"))
        assertTrue("завтрак" in offered("завт"))
        assertTrue("вегетарианское" in offered("вегет"))
        assertEquals("выпечка", offered("выпеч").first())
    }

    @Test
    fun caseIsIgnored() {
        assertEquals(offered("итал"), offered("ИТАЛ"))
        assertEquals(listOf("Italian"), offered("ITAL", InterfaceLanguage.ENGLISH))
    }

    // Both interface languages: each finds the tag by either of its labels and names it in its own.
    @Test
    fun bothInterfaceLanguagesFindTagsAndNameThemInTheirOwn() {
        assertEquals(listOf("air fryer"), offered("air", InterfaceLanguage.ENGLISH))
        assertEquals(listOf("air fryer"), offered("аэро", InterfaceLanguage.ENGLISH))
        assertEquals(listOf("аэрогриль"), offered("air"))
        assertEquals(listOf("Italian"), offered("ital", InterfaceLanguage.ENGLISH))
    }

    // ё is read as е both ways: typed and in the label.
    @Test
    fun yoIsReadAsYe() {
        assertEquals(listOf("слоёное тесто"), offered("слоен"))
        assertEquals(listOf("слоёное тесто"), offered("слоён"))
        assertEquals(offered("выпеч"), offered("выпёч"))
    }

    // Only the beginning of a word: the middle of one finds nothing.
    @Test
    fun theMiddleOfAWordFindsNothing() {
        assertEquals(emptyList<String>(), offered("льянск"))
        assertTrue(offered("тесто").contains("слоёное тесто"))
    }

    @Test
    fun atMostEightAreOffered() {
        assertEquals(TagOffer.LIMIT, 8)
        assertEquals(8, offered("с").size)
        assertEquals(8, offered("s", InterfaceLanguage.ENGLISH).size)
    }

    // A label that begins with the text comes before one where a later word does.
    @Test
    fun aLabelThatBeginsWithTheTextComesFirst() {
        val baking = offered("выпеч")
        assertEquals("без выпечки", baking.last())
        assertTrue(baking.take(baking.size - 1).all { it.startsWith("выпечка") })
    }

    @Test
    fun anExcludedTagIsNotOffered() {
        assertEquals(emptyList<String>(), offered("итал", excluded = setOf(10L)))
        val withoutBaking = offered("выпеч", excluded = setOf(shipped.single { it.labelRu == "выпечка" }.id))
        assertTrue("выпечка" !in withoutBaking)
        assertTrue("без выпечки" in withoutBaking)
    }
}
