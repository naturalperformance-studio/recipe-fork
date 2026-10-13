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

    // FLOW-057 (4), DOM-010: a matched parent brings its leaves, after it (T-272: a parent before its own leaves).
    @Test
    fun theLeavesOfAMatchedParentAreOfferedAfterIt() {
        val labels = offered("кур")
        assertTrue(labels.containsAll(listOf("крылышки", "филе", "курица")))
        assertTrue(labels.indexOf("курица") < labels.indexOf("крылышки"))
        assertTrue(labels.indexOf("курица") < labels.indexOf("филе"))
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

    // FLOW-057 (10), T-272: the full order — what begins with the typed text, the shorter name first, then the rest
    // alphabetically, a parent before its own leaves — no entry twice.
    @Test
    fun theOfferReadsInOrderAndNoEntryTwice() {
        val all = dictionary + coconutMilk
        // курица and куркума begin with "кур"; the leaves курица brings do not, and follow alphabetically.
        assertEquals(listOf("курица", "куркума", "крылышки", "филе"), offered("кур", all))
        // A leaf the typed text also matches on its own stands once, after its parent.
        assertEquals(listOf("chicken", "chicken wings", "fillet"), offered("chic", all, ENGLISH))
        // The exact match heads the offer, before its own leaves (T-130).
        assertEquals(listOf("chicken", "chicken wings", "fillet"), offered("chicken", all, ENGLISH))
        val russian = offer("chick", all, RUSSIAN).entries
        assertEquals(listOf(chicken, wings, fillet), russian)
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
        assertEquals(listOf(egg, quailEgg), offer("яйц", withThem).entries)
        // A beginning still being typed is no other form: free text stays offered.
        assertEquals("молок", offer("молок").freeText)
        assertEquals(listOf(egg, quailEgg), offer("яйца", withThem).entries)
        assertEquals(listOf(egg, quailEgg), offer("eggs", withThem).entries)
        assertEquals(listOf(cucumber), offer("огурцы", withThem).entries)
        // Words in any order are offered, as before, and now also taken as the exact match.
        assertEquals(listOf(butter), offer("сливочное масло", withThem).entries)
        assertEquals(butter, offer("сливочное масло", withThem).exact)
        assertNull(offer("сливочное масло", withThem).freeText)
    }

    // T-130: a plural or another form of a label is the exact match too, offered first, before the entry's leaves.
    @Test
    fun anotherFormOfALabelIsTheExactMatchAndComesFirst() {
        val egg = IngredientEntryEntity(50, "яйцо", "egg", null)
        val quailEgg = IngredientEntryEntity(51, "яйцо перепелиное", "quail egg", 50)
        val boiledEgg = IngredientEntryEntity(52, "яйцо варёное", "boiled egg", 50)
        val onion = IngredientEntryEntity(60, "лук", "onion", null)
        val redOnion = IngredientEntryEntity(61, "лук красный", "red onion", 60)
        val leek = IngredientEntryEntity(62, "лук-порей", "leek", 60)
        val withThem = dictionary + egg + quailEgg + boiledEgg + onion + redOnion + leek
        listOf("яйца", "Яйца", "eggs").forEach { typed ->
            val result = offer(typed, withThem)
            assertEquals(typed, egg, result.exact)
            assertEquals(typed, egg, result.entries.first())
            assertNull(typed, result.freeText)
        }
        assertEquals(listOf(egg, boiledEgg, quailEgg), offer("яйца", withThem).entries)
        assertEquals(onion, offer("onions", withThem, ENGLISH).entries.first())
        assertEquals(onion, offer("onions", withThem, ENGLISH).exact)
        assertEquals(onion, offer("лука", withThem).exact)
        assertEquals("onion", offered("onions", withThem, ENGLISH).first())
        // The words of a label in another form, in any order, are exact as well: "перепелиные яйца" is "яйцо перепелиное".
        assertEquals(quailEgg, offer("перепелиные яйца", withThem).exact)
        assertEquals(quailEgg, offer("quail eggs", withThem).exact)
        // The label itself, as written, still wins over another entry in another form.
        assertEquals(egg, offer("яйцо", withThem).exact)
    }

    // T-130: the words of an entry's parents count as its own, so "сыр моцарелла" lands on моцарелла, a kind of сыр.
    @Test
    fun theWordsOfAParentNameItsLeaf() {
        val cheese = IngredientEntryEntity(70, "сыр", "cheese", null)
        val adyghe = IngredientEntryEntity(71, "адыгейский сыр", "Adyghe cheese", 70)
        val mozzarella = IngredientEntryEntity(72, "моцарелла", "mozzarella", 70)
        val withThem = dictionary + cheese + adyghe + mozzarella
        assertEquals(mozzarella, offer("сыр моцарелла", withThem).entries.first())
        assertEquals(mozzarella, offer("сыр моцарелла", withThem).exact)
        assertEquals(mozzarella, offer("mozzarella cheese", withThem, ENGLISH).entries.first())
        assertFalse(offer("сыр моцарелла", withThem).entries.contains(adyghe))
        // The parent alone still brings its leaves, after it, each once.
        assertEquals(listOf(cheese, adyghe, mozzarella), offer("сы", withThem).entries)
        assertEquals(listOf(cheese, adyghe, mozzarella), offer("сыр", withThem).entries)
    }

    // T-130: a longer word built on a label is not the label: no butter for "buttermilk", no лимон for "лимонад".
    @Test
    fun aWordBuiltOnALabelDoesNotFindIt() {
        val butter = IngredientEntryEntity(80, "масло сливочное", "butter", null)
        val water = IngredientEntryEntity(81, "вода", "water", null)
        val pepper = IngredientEntryEntity(82, "перец", "pepper", null)
        val lemon = IngredientEntryEntity(83, "лимон", "lemon", null)
        val sugar = IngredientEntryEntity(84, "сахар", "sugar", null)
        val carrot = IngredientEntryEntity(85, "морковь", "carrot", null)
        val withThem = dictionary + butter + water + pepper + lemon + sugar + carrot
        listOf("buttermilk", "watermelon", "pepperoni", "лимонад", "сахарин").forEach { typed ->
            assertEquals(typed, emptyList<IngredientEntryEntity>(), offer(typed, withThem).entries)
        }
        assertEquals(listOf(carrot), offer("морковка", withThem).entries)
        assertEquals(listOf(sugar), offer("сахарная", withThem).entries)
        assertEquals(listOf(lemon), offer("лимоны", withThem).entries)
    }

    private val potato = IngredientEntryEntity(90, "картофель", "potato", null, synonymsRu = listOf("картошка"))
    private val tomato = IngredientEntryEntity(91, "помидор", "tomato", null, synonymsRu = listOf("томат"))
    private val semolina = IngredientEntryEntity(92, "манная крупа", "semolina", null, synonymsRu = listOf("манка"))
    private val powdered = IngredientEntryEntity(
        93, "сахарная пудра", "powdered sugar", null, synonymsEn = listOf("confectioners' sugar", "icing sugar"),
    )
    private val tomatoPaste = IngredientEntryEntity(94, "томатная паста", "tomato paste", null)
    private val withSynonyms = dictionary + potato + tomato + semolina + powdered + tomatoPaste

    // T-129, FLOW-023: a synonym typed exactly, or in another form, is an exact match, shown under the entry's label with
    // the synonym beside it; choosing it names the row by the entry.
    @Test
    fun aSynonymTypedFindsItsEntryExactly() {
        listOf(
            Triple("картошка", potato, "картошка"),
            Triple("Картошку", potato, "картошка"),
            Triple("манка", semolina, "манка"),
            Triple("томат", tomato, "томат"),
            Triple("томаты", tomato, "томат"),
            Triple("icing sugar", powdered, "icing sugar"),
            Triple("confectioners' sugar", powdered, "confectioners' sugar"),
            Triple("Confectioners sugar", powdered, "confectioners' sugar"),
        ).forEach { (typed, entry, synonym) ->
            val result = offer(typed, withSynonyms)
            assertEquals(typed, entry, result.exact)
            assertEquals(typed, entry, result.entries.first())
            assertNull(typed, result.freeText)
            assertEquals(typed, synonym, result.synonyms[entry.id])
        }
        // The label shown is the interface language's, whichever language the synonym is in.
        assertEquals("сахарная пудра", offer("icing sugar", withSynonyms).entries.first().label(RUSSIAN))
        assertEquals("potato", offer("картошка", withSynonyms, ENGLISH).entries.first().label(ENGLISH))
    }

    // T-129: a synonym begun is matched by the same word-prefix rule as a label; a word built on it is not it.
    @Test
    fun aSynonymBegunOffersItsEntry() {
        assertEquals(listOf(potato), offer("карто", withSynonyms).entries)
        assertEquals(emptyMap<Long, String>(), offer("карто", withSynonyms).synonyms)
        assertEquals(listOf(potato), offer("картош", withSynonyms).entries)
        assertEquals(mapOf(potato.id to "картошка"), offer("картош", withSynonyms).synonyms)
        assertNull(offer("картош", withSynonyms).exact)
        assertEquals(listOf(powdered), offer("icing", withSynonyms, ENGLISH).entries)
        assertEquals(emptyList<IngredientEntryEntity>(), offer("манкать", withSynonyms).entries)
    }

    // T-129: an entry found by its label and a synonym alike is offered once, its label alone shown.
    @Test
    fun anEntryFoundByItsLabelAndASynonymIsOfferedOnceUnderItsLabel() {
        val sugar = IngredientEntryEntity(95, "сахар", "sugar", null)
        val result = offer("sugar", withSynonyms + sugar, ENGLISH)
        assertEquals(listOf(sugar, powdered), result.entries)
        assertEquals(1, result.entries.count { it == powdered })
        // "powdered sugar" holds "sugar" as well as its synonyms do: the label wins, and no synonym is shown.
        assertEquals(emptyMap<Long, String>(), result.synonyms)
        // "томат" is помидор's synonym and begins томатная паста's label: each offered once, the exact match first.
        val tomatoes = offer("томат", withSynonyms)
        assertEquals(listOf(tomato, tomatoPaste), tomatoes.entries)
        assertEquals(mapOf(tomato.id to "томат"), tomatoes.synonyms)
    }

    // T-129: a removed entry's synonyms find nothing.
    @Test
    fun aRemovedEntrysSynonymFindsNothing() {
        val removed = potato.copy(removed = true)
        assertEquals(emptyList<IngredientEntryEntity>(), offer("картошка", dictionary + removed).entries)
        assertEquals("картошка", offer("картошка", dictionary + removed).freeText)
    }

    // T-272: the editor's dictionary as the owner's phone holds it in part — "chick" offers chicken first, then what
    // begins with "chick" by the shorter name, chickpeas among them, then the rest; in either interface language.
    private val poultry = listOf(
        IngredientEntryEntity(200, "курица", "chicken", null),
        IngredientEntryEntity(201, "куриные бёдра без кости", "boneless chicken thighs", 200),
        IngredientEntryEntity(202, "куриные спинки", "chicken backs", 200),
        IngredientEntryEntity(203, "куриная грудка", "chicken breast", 200),
        IngredientEntryEntity(204, "куриные лапки", "chicken feet", 200),
        IngredientEntryEntity(205, "куриные крылья", "chicken wings", 200),
        IngredientEntryEntity(206, "курица свободного выгула", "free-range chicken", 200),
        IngredientEntryEntity(207, "цыплёнок", "poussin", 200),
        IngredientEntryEntity(208, "нут", "chickpeas", null, synonymsRu = listOf("бараний горох")),
        IngredientEntryEntity(209, "куркума", "turmeric", null),
    )

    @Test
    fun chickOffersChickenFirstInEnglish() {
        assertEquals(
            listOf(
                "chicken", "chickpeas", "chicken feet", "chicken backs", "chicken wings", "chicken breast",
                "boneless chicken thighs", "free-range chicken", "poussin",
            ),
            offered("chick", poultry, ENGLISH),
        )
        assertEquals("chicken", offered("Chick", poultry, ENGLISH).first())
        assertEquals("chicken", offered("chicken", poultry, ENGLISH).first())
    }

    @Test
    fun chickOffersChickenFirstInRussian() {
        // Typed in English under a Russian interface: ordered by the English names that begin with it, shown in Russian.
        assertEquals("курица", offered("chick", poultry, RUSSIAN).first())
        assertEquals(listOf("курица", "нут", "куриные лапки"), offered("chick", poultry, RUSSIAN).take(3))
        // Typed in Russian: курица (6 letters) before куркума (7), then the names that begin with "кур" by length — the
        // leaves after their parent — then the rest alphabetically.
        assertEquals(
            listOf(
                "курица", "куркума", "куриные лапки", "куриная грудка", "куриные крылья", "куриные спинки",
                "куриные бёдра без кости", "курица свободного выгула", "цыплёнок",
            ),
            offered("кур", poultry, RUSSIAN),
        )
        // Typed in Russian under an English interface: chicken first, shown in English.
        assertEquals("chicken", offered("кури", poultry, ENGLISH).first())
    }

    // T-272: an exact synonym leads as an exact label does; prefixes of a synonym count by the synonym's length.
    @Test
    fun anExactSynonymLeadsAndASynonymBegunCountsAsAName() {
        val peas = IngredientEntryEntity(210, "горох", "peas", null)
        val withPeas = poultry + peas
        assertEquals("нут", offered("бараний горох", withPeas, RUSSIAN).first())
        assertEquals(poultry.first { it.id == 208L }, offer("бараний горох", withPeas, RUSSIAN).exact)
        // "горох" is горох's label exactly — it leads — and нут's synonym holds the word: it follows.
        assertEquals(listOf("горох", "нут"), offered("горох", withPeas, RUSSIAN))
    }

    // T-272: a parent always comes before its own leaves — even one that begins with the typed text while the parent
    // does not.
    @Test
    fun aParentComesBeforeItsOwnLeaves() {
        val greenOnion = IngredientEntryEntity(300, "лук зелёный", "green onion", null)
        val tops = IngredientEntryEntity(301, "перья лука", "onion tops", 300)
        val onion = IngredientEntryEntity(302, "лук", "onion", null)
        // "onion tops" begins with "onion" and "green onion" does not, yet the leaf follows its parent.
        assertEquals(listOf("onion", "green onion", "onion tops"), offered("onion", listOf(tops, greenOnion, onion), ENGLISH))
        assertEquals(listOf("green onion", "onion tops"), offered("onio", listOf(tops, greenOnion), ENGLISH))
        // Every offer of the poultry list, each typed beginning: no leaf before its parent, no entry twice.
        listOf("c", "ch", "chi", "chick", "chicken", "к", "ку", "кур", "кури", "цып").forEach { typed ->
            val entries = offer(typed, poultry, RUSSIAN).entries
            assertEquals(typed, entries.distinct(), entries)
            entries.forEachIndexed { at, entry ->
                val parent = entries.indexOfFirst { it.id == entry.parentId }
                assertTrue("$typed: ${entry.labelEn} before its parent", parent == -1 || parent < at)
            }
        }
    }

    // T-272, scenario 6: in the dictionary the app ships, "chick" offers chicken first, and "кури" курица.
    @Test
    fun theShippedDictionaryOffersChickenFirst() {
        val shipped = DictionarySnapshot.parse(java.io.File("src/main/assets/${DictionarySnapshot.ASSET}").readText())
            .ingredients.map(IngredientEntryEntity::of)
        assertEquals("chicken", offered("chick", shipped, ENGLISH).first())
        assertEquals("курица", offered("chick", shipped, RUSSIAN).first())
        // "кур" begins курага and курдюк as much as курица — as long, the alphabet orders them; "кури" is курица's.
        assertEquals(listOf("курага", "курдюк", "курица"), offered("кур", shipped, RUSSIAN).take(3))
        assertEquals("курица", offered("кури", shipped, RUSSIAN).first())
        assertEquals("chicken", offered("кури", shipped, ENGLISH).first())
    }
}
