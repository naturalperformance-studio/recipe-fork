package app.recipefork.app.dictionary

import app.recipefork.core.scaling.InterfaceLanguage
import app.recipefork.core.scaling.InterfaceLanguage.ENGLISH
import app.recipefork.core.scaling.InterfaceLanguage.RUSSIAN
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** FLOW-057 (1–10), FLOW-022 (1) and L10N-003, each against a seeded test dictionary, on the JVM alone. */
class IngredientOfferTest {
    private val milk = IngredientEntryEntity(1, "молоко", "milk", null)
    private val coconutMilk = IngredientEntryEntity(2, "кокосовое молоко", "coconut milk", null)
    private val chicken = IngredientEntryEntity(10, "курица", "chicken", null)
    private val wings = IngredientEntryEntity(11, "крылышки", "chicken wings", 10)
    private val fillet = IngredientEntryEntity(12, "филе", "fillet", 10)
    private val turmeric = IngredientEntryEntity(20, "куркума", "turmeric", null)
    private val blackberry = IngredientEntryEntity(30, "ежевика", "blackberry", null)
    private val beetroot = IngredientEntryEntity(31, "свёкла", "beetroot", null)

    /** The test dictionary with no entry for coconut milk. */
    private val dictionary = listOf(milk, chicken, wings, fillet, turmeric, blackberry, beetroot)

    private fun offer(
        typed: String,
        from: List<IngredientEntryEntity> = dictionary,
        language: InterfaceLanguage = RUSSIAN,
        stillHeld: Set<Long> = emptySet(),
    ) = IngredientOffer.of(typed, from, language, stillHeld)

    private fun offered(typed: String, from: List<IngredientEntryEntity> = dictionary, language: InterfaceLanguage = RUSSIAN) =
        offer(typed, from, language).entries.map { it.label(language) }

    // FLOW-057 (1): a typed word that begins a word of the label offers the entry.
    @Test
    fun aWordBeginningOffersTheEntry() {
        assertTrue(offered("мол").contains("молоко"))
        assertTrue(offered("ку").containsAll(listOf("курица", "куркума")))
        // A word must begin with the typed text, not merely hold it.
        assertFalse(offered("локо").contains("молоко"))
    }

    // FLOW-057 (2): every typed word must begin a word of one label; otherwise only free text is offered.
    @Test
    fun aTypedWordThatBeginsNoWordOfTheLabelKeepsTheEntryOut() {
        val result = offer("кокосовое молоко")
        assertFalse(result.entries.contains(milk))
        assertEquals(emptyList<IngredientEntryEntity>(), result.entries)
        assertEquals("кокосовое молоко", result.freeText)
        // Words may come in any order, but each must begin a word of the same label.
        assertEquals(listOf(wings), offer("wings chick").entries)
        assertFalse(offer("chicken молоко").entries.contains(milk))
    }

    // FLOW-057 (3): letter case is ignored, and `ё` is `е`.
    @Test
    fun caseIsIgnoredAndYoIsYe() {
        val lower = offer("мол")
        assertEquals(lower.entries, offer("МОЛ").entries)
        assertEquals(lower.entries, offer("Мол").entries)
        assertEquals(offer("ёж").entries, offer("еж").entries)
        assertEquals(listOf(blackberry), offer("ёж").entries)
        assertEquals(listOf(beetroot), offer("свек").entries)
        assertEquals(listOf(beetroot), offer("СВЁК").entries)
    }

    // FLOW-057 (4), DOM-010: a matched parent brings its leaves, before it.
    @Test
    fun theLeavesOfAMatchedParentAreOfferedBeforeIt() {
        val labels = offered("кур")
        assertTrue(labels.containsAll(listOf("крылышки", "филе", "курица")))
        assertTrue(labels.indexOf("крылышки") < labels.indexOf("курица"))
        assertTrue(labels.indexOf("филе") < labels.indexOf("курица"))
    }

    // FLOW-057 (5), L10N-003: either language matches, and the entry is shown in the interface language.
    @Test
    fun eitherLanguageMatchesAndTheInterfaceLanguageIsShown() {
        val russian = offer("chick", language = RUSSIAN)
        assertTrue(russian.entries.contains(chicken))
        assertEquals("курица", chicken.label(RUSSIAN))
        assertTrue(offered("chick", language = RUSSIAN).contains("курица"))
        assertFalse(offered("chick", language = RUSSIAN).contains("chicken"))
        assertTrue(offered("кури", language = ENGLISH).contains("chicken"))
        assertFalse(offered("кури", language = ENGLISH).contains("курица"))
    }

    // FLOW-057 (6), FLOW-022 (1): free text is offered beside entries when the typed text is no entry's label.
    @Test
    fun freeTextStandsBesideEntriesWhenTheTextIsNotALabel() {
        val result = offer("мол")
        assertEquals(listOf(milk), result.entries)
        assertEquals("мол", result.freeText)
        assertNull(result.exact)
        // And alone where nothing is offered, never refused.
        val none = offer("черемша")
        assertEquals(emptyList<IngredientEntryEntity>(), none.entries)
        assertEquals("черемша", none.freeText)
        // Nothing typed offers nothing.
        assertEquals(IngredientOffer(emptyList(), null), offer("   "))
    }

    // FLOW-057 (7): exactly an offered entry's label typed — free text is not offered.
    @Test
    fun exactlyALabelOffersNoFreeText() {
        val result = offer("молоко")
        assertEquals(listOf(milk), result.entries)
        assertNull(result.freeText)
        assertEquals(milk, result.exact)
        // A leaf brought by its parent is offered too, and its label is exact as well.
        val leaf = offer("chicken wings")
        assertNull(leaf.freeText)
        assertEquals(wings, leaf.exact)
    }

    // FLOW-057 (8): the comparison folds case, `ё`, the spaces around the text, and takes either language.
    @Test
    fun theExactComparisonFoldsAsTheOfferDoes() {
        listOf("Молоко", " молоко ", "milk", "MILK").forEach { typed ->
            val result = offer(typed)
            assertTrue(typed, result.entries.contains(milk))
            assertNull(typed, result.freeText)
            assertEquals(typed, milk, result.exact)
        }
        assertEquals(beetroot, offer("свекла").exact)
        assertNull(offer("свекла").freeText)
        // A label written with `ё` is exact for `е` typed, and the reverse.
        assertEquals(blackberry, offer("ёжевика").exact)
    }

    // FLOW-057 (9): what begins with the typed text comes first.
    @Test
    fun whatBeginsWithTheTypedTextComesFirst() {
        val withCoconut = dictionary + coconutMilk
        assertEquals(listOf("молоко", "кокосовое молоко"), offered("мол", withCoconut))
        assertEquals(listOf("milk", "coconut milk"), offered("mil", withCoconut, ENGLISH))
        // Either language's label counts for the first group: `milk` begins with `mil` under a Russian interface.
        assertEquals(listOf("молоко", "кокосовое молоко"), offered("mil", withCoconut, RUSSIAN))
    }

    // FLOW-057 (10): the full order — leaves alphabetical before their parent, groups alphabetical — no entry twice.
    @Test
    fun theOfferReadsInOrderAndNoEntryTwice() {
        val all = dictionary + coconutMilk
        assertEquals(listOf("крылышки", "филе", "курица", "куркума"), offered("кур", all))
        // A leaf the typed text also matches on its own stands once, before its parent.
        val english = offered("chicken", all, ENGLISH)
        assertEquals(listOf("chicken wings", "fillet", "chicken"), english)
        val russian = offer("chick", all, RUSSIAN).entries
        assertEquals(listOf(wings, fillet, chicken), russian)
        assertEquals(russian.distinct(), russian)
        // A leaf whose parent is not matched is offered on its own, placed by its own label.
        assertEquals(listOf(fillet), offer("фил", all).entries)
        assertNotNull(offer("wings", all).entries.singleOrNull { it == wings })
    }

    // FLOW-001 (7), DOM-009 (5): an entry removed upstream is no longer offered, nor matched exactly when the name field
    // is left; the typed text is then offered as free text. The library's filter may keep one it still holds.
    @Test
    fun aRemovedEntryIsNeitherOfferedNorMatchedExactly() {
        val removedWings = wings.copy(removed = true)
        val removedChicken = chicken.copy(removed = true)
        val held = listOf(milk, removedChicken, removedWings, fillet)

        assertEquals(emptyList<IngredientEntryEntity>(), offer("крылышки", held).entries)
        assertNull(offer("крылышки", held).exact)
        assertEquals("крылышки", offer("крылышки", held).freeText)
        assertNull(offer("chicken", held, language = ENGLISH).exact)
        // A live leaf of a removed parent is still offered, standing on its own.
        assertEquals(listOf(fillet), offer("фил", held).entries)

        val kept = offer("крылышки", held, stillHeld = setOf(removedWings.id))
        assertEquals(listOf(removedWings), kept.entries)
        assertEquals(removedWings, kept.exact)
    }

    // T-124: diacritics folded, so "jalapeno" finds "jalapeño" and "creme fraiche" "crème fraîche", exactly as well.
    @Test
    fun diacriticsAreFolded() {
        val jalapeno = IngredientEntryEntity(40, "халапеньо", "jalapeño", null)
        val cremeFraiche = IngredientEntryEntity(41, "крем-фреш", "crème fraîche", null)
        val withThem = dictionary + jalapeno + cremeFraiche
        assertEquals(listOf(jalapeno), offer("jalapeno", withThem, ENGLISH).entries)
        assertEquals(jalapeno, offer("Jalapeno", withThem, ENGLISH).exact)
        assertEquals(listOf(cremeFraiche), offer("creme fr", withThem, ENGLISH).entries)
        assertEquals(cremeFraiche, offer("creme fraiche", withThem, ENGLISH).exact)
        assertNull(offer("creme fraiche", withThem, ENGLISH).freeText)
    }

    // T-124: a word typed in another grammatical form finds the entry, and the exact match takes the words in any order.
    @Test
    fun anotherFormOrAnotherWordOrderFindsTheEntry() {
        val egg = IngredientEntryEntity(50, "яйцо", "egg", null)
        val quailEgg = IngredientEntryEntity(51, "яйцо перепелиное", "quail egg", 50)
        val cucumber = IngredientEntryEntity(52, "огурец", "cucumber", null)
        val butter = IngredientEntryEntity(53, "масло сливочное", "butter", null)
        val withThem = dictionary + egg + quailEgg + cucumber + butter
        assertEquals(listOf(quailEgg, egg), offer("яйца", withThem).entries)
        assertEquals(listOf(quailEgg, egg), offer("eggs", withThem).entries)
        assertEquals(listOf(cucumber), offer("огурцы", withThem).entries)
        // Words in any order are offered, as before, and now also taken as the exact match.
        assertEquals(listOf(butter), offer("сливочное масло", withThem).entries)
        assertEquals(butter, offer("сливочное масло", withThem).exact)
        assertNull(offer("сливочное масло", withThem).freeText)
        // Another form is offered, never taken as exact: the author picks it.
        assertNull(offer("яйца", withThem).exact)
        assertEquals("яйца", offer("яйца", withThem).freeText)
    }
}
