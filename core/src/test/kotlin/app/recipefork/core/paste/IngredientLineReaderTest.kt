package app.recipefork.core.paste

import app.recipefork.core.api.DictionariesDto
import app.recipefork.core.api.IngredientEntryDto
import app.recipefork.core.file.RecipeFileCheck
import app.recipefork.core.file.RecipeFileContent
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.QuantityUnit.CUP
import app.recipefork.core.recipe.QuantityUnit.GRAM
import app.recipefork.core.recipe.QuantityUnit.PIECE
import app.recipefork.core.recipe.QuantityUnit.PINCH
import app.recipefork.core.recipe.QuantityUnit.TABLESPOON
import app.recipefork.core.recipe.QuantityUnit.TEASPOON
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.Rational
import app.recipefork.core.search.DictionaryMatch
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * T-277: one pasted ingredient line read as one ingredient row — its quantity, its unit, its name bound to the shipped
 * dictionary, its qualifier and its note — every row a valid row of the model.
 */
class IngredientLineReaderTest {
    private val reader = IngredientLineReader(DictionaryMatch(shipped.ingredients))

    private fun read(text: String, reader: IngredientLineReader = this.reader): Ingredient =
        reader.read(IngredientLine(text, group = null), 0).also(::assertValid)

    private fun id(labelEn: String) = shipped.ingredients.single { it.labelEn == labelEn }.id

    /** [text] reads as [quantity] of [unit]. */
    private fun assertAmount(text: String, quantity: Rational, unit: QuantityUnit) {
        val row = read(text)
        assertEquals(quantity to unit, row.quantity to row.unit, text)
        assertEquals(unit.type, row.unitType, text)
    }

    private fun half() = Rational.of(1, 2)

    @Test
    fun quantitiesAreReadInEveryShape() {
        assertAmount("2 г соли", Rational.of(2), GRAM)
        assertAmount("1,5 кг муки", Rational.of(3, 2), QuantityUnit.KILOGRAM)
        assertAmount("1.5 кг муки", Rational.of(3, 2), QuantityUnit.KILOGRAM)
        assertAmount("1/2 стакана молока", half(), CUP)
        assertAmount("1 1/2 cups flour", Rational.of(3, 2), CUP)
        assertAmount("½ ч. л. соли", half(), TEASPOON)
        assertAmount("1½ ст. л. сахара", Rational.of(3, 2), TABLESPOON)
        assertAmount("1 ½ ст. л. сахара", Rational.of(3, 2), TABLESPOON)
    }

    @Test
    fun aRangeReadsAsItsLargerEnd() {
        assertAmount("3–5 г мёда", Rational.of(5), GRAM)
        assertAmount("3-5 г мёда", Rational.of(5), GRAM)
        assertAmount("от 3 до 5 г мёда", Rational.of(5), GRAM)
        assertAmount("2 to 3 cups flour", Rational.of(3), CUP)
    }

    @Test
    fun smallNumbersAreReadFromWords() {
        assertAmount("пол стакана молока", half(), CUP)
        assertAmount("полстакана молока", half(), CUP)
        assertAmount("половина луковицы", half(), PIECE)
        assertAmount("один огурец", Rational.ONE, PIECE)
        assertAmount("полторы ст. л. сахара", Rational.of(3, 2), TABLESPOON)
        assertAmount("half a cup milk", half(), CUP)
        assertAmount("a pinch of salt", Rational.ONE, PINCH)
        assertEquals(id("egg"), read("an egg").dictionaryEntry)
        assertAmount("an egg", Rational.ONE, PIECE)
        assertEquals(id("salt"), read("a pinch of salt").dictionaryEntry)
    }

    @Test
    fun unitWordsAreReadInBothLanguagesAbbreviatedAndInflected() {
        val units = mapOf(
            GRAM to listOf("г", "г.", "гр", "гр.", "грамм", "грамма", "граммов", "g", "grams"),
            QuantityUnit.KILOGRAM to listOf("кг", "килограмма", "kg"),
            QuantityUnit.MILLILITRE to listOf("мл", "ml"),
            QuantityUnit.LITRE to listOf("л", "литра", "l"),
            TABLESPOON to listOf("ст. л.", "ст.л.", "ст л", "столовые ложки", "столовых ложки", "tbsp", "tablespoons"),
            TEASPOON to listOf("ч. л.", "ч.л.", "чайная ложка", "чайные ложки", "tsp", "teaspoon"),
            CUP to listOf("стакан", "стакана", "стаканов", "cup", "cups"),
            PINCH to listOf("щепотка", "щепотки", "pinch"),
            PIECE to listOf("шт", "шт.", "штуки", "штук", "pcs"),
            QuantityUnit.OUNCE to listOf("oz", "ounces"),
            QuantityUnit.POUND to listOf("lb", "pounds"),
            QuantityUnit.FLUID_OUNCE to listOf("fl oz", "fl. oz"),
        )
        units.forEach { (unit, words) -> words.forEach { word -> assertAmount("2 $word сахара", Rational.of(2), unit) } }
        assertEquals(id("sugar"), read("2 ст. л. сахара").dictionaryEntry)
    }

    @Test
    fun toTasteHoldsNoQuantity() {
        listOf("соль по вкусу", "Salt, to taste", "Соль — по вкусу", "по вкусу соль").forEach { text ->
            val row = read(text)
            assertEquals(UnitType.TASTE_UNMEASURED, row.unitType, text)
            assertEquals(id("salt"), row.dictionaryEntry, text)
            assertNull(row.note, text)
            assertNull(row.qualifier, text)
        }
        assertNull(read("salt to taste").qualifier)
    }

    @Test
    fun whatMayStandForTheIngredientGoesToTheNoteNotTheQualifier() {
        val salt = read("1 ч. л. соли или по вкусу")
        assertEquals(listOf(id("salt"), null, "или по вкусу", Rational.ONE, TEASPOON), listOf(salt.dictionaryEntry, salt.qualifier, salt.note, salt.quantity, salt.unit))
        val pepper = read("salt and pepper to taste")
        assertEquals(listOf(id("salt"), null, "and pepper", UnitType.TASTE_UNMEASURED), listOf(pepper.dictionaryEntry, pepper.qualifier, pepper.note, pepper.unitType))
    }

    @Test
    fun aHyphenatedHalfIsRead() {
        val milk = read("пол-литра молока")
        assertEquals(Triple(id("milk"), half(), QuantityUnit.LITRE), Triple(milk.dictionaryEntry, milk.quantity, milk.unit))
        val lemon = read("пол-лимона")
        assertEquals(Triple(id("lemon"), half(), PIECE), Triple(lemon.dictionaryEntry, lemon.quantity, lemon.unit))
        val salt = read("пол-ложки соли")
        assertEquals(Triple(id("salt"), half(), TABLESPOON), Triple(salt.dictionaryEntry, salt.quantity, salt.unit))
        val onion = read("пол-луковицы")
        assertEquals(Triple(id("onion"), half(), PIECE), Triple(onion.dictionaryEntry, onion.quantity, onion.unit))
    }

    @Test
    fun anyUnicodeSpaceSeparatesAsASpaceDoes() {
        listOf("2\u00A0г соли", "2\u2009г соли", "2\u202Fг\tсоли").forEach { text ->
            val row = read(text)
            assertEquals(listOf(id("salt"), null, Rational.of(2), GRAM), listOf(row.dictionaryEntry, row.qualifier, row.quantity, row.unit), text)
        }
    }

    @Test
    fun milkIsMilkInTheGenitive() {
        listOf("1 л молока", "200 мл молока", "пол стакана молока").forEach { assertEquals(id("milk"), read(it).dictionaryEntry, it) }
        assertEquals(id("milt"), read("200 г молоки").dictionaryEntry)
    }

    @Test
    fun aLongRunOfDashesIsReadAtOnce() {
        listOf("-".repeat(20_000), "Соль " + "—".repeat(20_000) + " 5 г", "a" + "-".repeat(20_000) + "b", "a" + " ".repeat(20_000) + "b", "соль" + " -".repeat(10_000) + "по вкусу")
            .plus(
                // T-284: approximators, dashes and colons, labels, a word before an amount, alternatives, pluses, clauses.
                listOf(
                    "~".repeat(20_000) + "1 г соли", "примерно ".repeat(5_000) + "5 г", "соль " + "~ ".repeat(10_000) + "5 г",
                    "a — ".repeat(10_000) + "1 г", "a: ".repeat(10_000) + "1 г соли", "Крем: " + "1 ".repeat(10_000) + "г",
                    "Крем" + "-".repeat(20_000) + ": 1 г соли", "juice of " + "1 ".repeat(10_000) + "lime", "сок " + "1".repeat(20_000) + " лайма",
                    "соль " + "или ".repeat(10_000), "1 г соли " + "+ ".repeat(10_000), "1 г соли" + " + 1 г соли".repeat(2_000),
                    "Ааааа. Б".repeat(5_000), "a→".repeat(10_000), "соль, " + "1 ".repeat(10_000) + "x", "соль (" + "1 г ".repeat(5_000) + ")",
                    "1 " + "small ".repeat(10_000) + "bunch", "четверть " + "от ".repeat(10_000) + "1", "400g " + "can ".repeat(10_000) + "beans",
                    "мука" + " — 1".repeat(10_000),
                    // Fix round 1: several words before an amount, a plus between two amounts, a long word.
                    "zest " + "and juice ".repeat(5_000) + "of 1 lemon", "сок " + "а".repeat(20_000) + " 1 лимона",
                    "сок, ".repeat(10_000) + "1 лимона", "1 cup" + " + 2 tbsp".repeat(5_000) + " flour", "1 can " + "butter ".repeat(5_000) + "beans",
                ),
            )
            .forEach { text ->
                val took = kotlin.time.measureTime { read(text) }
                assertTrue(took.inWholeMilliseconds < 1_000, "${text.take(10)}…: $took")
            }
    }

    @Test
    fun aNoteThatIsOnlyAConjunctionIsDropped() {
        // Cheese is no part of the macaroni the row is bound to: "and" alone was left as its note.
        assertNull(read("200 g macaroni and cheese").note)
        assertNull(read("соль, и").note)
        assertEquals("и перец", read("соль и перец").note)
    }

    @Test
    fun aCloveIsItsEntryWhereItHoldsTheQuantityOtherwisePiecesQualified() {
        // The shipped dictionary has a clove entry, indivisible: whole cloves are it.
        val whole = read("2 зубчика чеснока")
        assertEquals(listOf(id("garlic clove"), Rational.of(2), PIECE, false, null), listOf(whole.dictionaryEntry, whole.quantity, whole.unit, whole.divisible, whole.qualifier))
        // Half a clove is not: garlic, by the half, qualified by the clove.
        val half = read("½ зубчика чеснока")
        assertEquals(listOf(id("garlic"), half(), PIECE, true, "зубчик"), listOf(half.dictionaryEntry, half.quantity, half.unit, half.divisible, half.qualifier))
        // A dictionary with no clove entry: always garlic, qualified.
        val garlicOnly = IngredientLineReader(DictionaryMatch(listOf(IngredientEntryDto(1, "чеснок", "garlic", divisible = true))))
        val ru = read("3 зубчика чеснока", garlicOnly)
        assertEquals(listOf(1L, Rational.of(3), PIECE, "зубчик"), listOf(ru.dictionaryEntry, ru.quantity, ru.unit, ru.qualifier))
        val en = read("2 cloves garlic", garlicOnly)
        assertEquals(listOf(1L, Rational.of(2), PIECE, "clove"), listOf(en.dictionaryEntry, en.quantity, en.unit, en.qualifier))
    }

    @Test
    fun whatFollowsTheNameAndSaysWhatItIsQualifiesIt() {
        val fillet = read("500 г куриного филе без кожи")
        assertEquals(id("chicken breast fillet") to "без кожи", fillet.dictionaryEntry to fillet.qualifier)
        val yogurt = read("200 г йогурта 9%")
        assertEquals(id("yogurt") to "9%", yogurt.dictionaryEntry to yogurt.qualifier)
        assertNull(yogurt.note)
    }

    @Test
    fun preparationGoesToTheNote() {
        val butter = read("100 г сливочного масла комнатной температуры")
        assertEquals(Triple(id("butter"), null, "комнатной температуры"), Triple(butter.dictionaryEntry, butter.qualifier, butter.note))
        val walnuts = read("1 cup walnuts, finely chopped")
        assertEquals(id("walnuts") to "finely chopped", walnuts.dictionaryEntry to walnuts.note)
        val garlic = read("½ зубчика чеснока, натереть")
        assertEquals("натереть", garlic.note)
        val flour = read("2 cups sifted flour")
        assertEquals(Triple(id("flour"), null, "sifted"), Triple(flour.dictionaryEntry, flour.qualifier, flour.note))
    }

    @Test
    fun anUnboundNameIsFreeTextWithoutItsPreparation() {
        val row = read("15 г мелко рубленного маринованного огурчика")
        assertEquals(Triple(null, "маринованного огурчика", "мелко рубленного"), Triple(row.dictionaryEntry, row.freeTextName, row.note))
        assertEquals(Rational.of(15) to GRAM, row.quantity to row.unit)
    }

    @Test
    fun aParenthesisGoesToTheNoteOrIsDroppedWhenTooLong() {
        assertEquals("натереть (или 1 ч. л. сушёного)", read("½ зубчика чеснока (или 1 ч. л. сушёного), натереть").note)
        assertEquals("или мацони", read("100 г греческого йогурта (или мацони)").note)
        val long = "или мацони, откинутого на марлю на ночь в холодильнике, — см. заметки внизу"
        assertNull(read("100 г греческого йогурта ($long)").note)
        assertEquals("натереть", read("½ зубчика чеснока ($long), натереть").note)
    }

    @Test
    fun aNoteIsCutToItsBound() {
        val row = read("2 cups flour, sifted twice through a fine sieve over a large bowl and then set aside")
        val note = row.note!!
        assertTrue(note.length <= 60 && note.startsWith("sifted twice through") && note.endsWith("…"), note)
    }

    @Test
    fun aQuantityAfterTheNameReadsAsOneBeforeIt() {
        val quark = read("Творог — 500 г")
        assertEquals(Triple(id("quark"), Rational.of(500), GRAM), Triple(quark.dictionaryEntry, quark.quantity, quark.unit))
        val butter = read("Сливочное масло 100 г")
        assertEquals(Triple(id("butter"), Rational.of(100), GRAM), Triple(butter.dictionaryEntry, butter.quantity, butter.unit))
        val salt = read("Соль щепотка")
        assertEquals(Triple(id("salt"), Rational.ONE, PINCH), Triple(salt.dictionaryEntry, salt.quantity, salt.unit))
        val eggs = read("Яйца: 2")
        assertEquals(Triple(id("egg"), Rational.of(2), PIECE), Triple(eggs.dictionaryEntry, eggs.quantity, eggs.unit))
        val toTaste = read("Соль — по вкусу")
        assertEquals(id("salt") to UnitType.TASTE_UNMEASURED, toTaste.dictionaryEntry to toTaste.unitType)
    }

    @Test
    fun aShortInflectedWordBinds() {
        val eggs = read("5 яиц")
        assertEquals(listOf(id("egg"), Rational.of(5), PIECE, false), listOf(eggs.dictionaryEntry, eggs.quantity, eggs.unit, eggs.divisible))
    }

    @Test
    fun aQuantityOffItsGridSnapsToIt() {
        assertAmount("⅛ ч. л. соли", Rational.ONE, PINCH)
        assertAmount("⅛ tsp salt", Rational.ONE, PINCH)
        assertAmount("2,5 г соли", Rational.of(3), GRAM)
        assertAmount("1,3 ст. л. сахара", Rational.of(4, 3), TABLESPOON)
        assertAmount("0,0015 кг соли", Rational.of(2, 1000), QuantityUnit.KILOGRAM)
        // An egg is indivisible: one and a half is two.
        assertAmount("1,5 яйца", Rational.of(2), PIECE)
        // An onion is divisible: the half holds.
        assertAmount("1.5 onions", Rational.of(3, 2), PIECE)
    }

    @Test
    fun aQuantityThatCannotBeReadIsKeptAsTyped() {
        listOf("1/0 г соли" to "1/0", "0 г соли" to "0", "1.2.3 г соли" to "1.2.3").forEach { (text, typed) ->
            val row = read(text)
            assertEquals(listOf(id("salt"), null, typed, GRAM), listOf(row.dictionaryEntry, row.quantity, row.enteredQuantity, row.unit), text)
        }
        val free = read("2..5 штуки чего-то")
        assertEquals(listOf(null, "чего-то", "2..5", PIECE), listOf(free.dictionaryEntry, free.freeTextName, free.enteredQuantity, free.unit))
    }

    @Test
    fun aNameOfNothingButPreparationOrAParenthesisKeepsTheLine() {
        val chopped = read("2 cups chopped")
        assertEquals(listOf(null, "2 cups chopped", Rational.of(2), CUP), listOf(chopped.dictionaryEntry, chopped.freeTextName, chopped.quantity, chopped.unit))
        val paren = read("(неведомое)")
        assertEquals(listOf(null, "(неведомое)", "неведомое"), listOf(paren.dictionaryEntry, paren.freeTextName, paren.note))
        val blank = read(" ")
        assertEquals(listOf(null, null), listOf(blank.dictionaryEntry, blank.freeTextName))
    }

    @Test
    fun aLineWithNoQuantityIsToTasteAndKeepsItsName() {
        val row = read("Пыльца неведомая")
        assertEquals(listOf(null, "Пыльца неведомая", UnitType.TASTE_UNMEASURED), listOf(row.dictionaryEntry, row.freeTextName, row.unitType))
    }

    @Test
    fun divisibilityOfPiecesComesFromTheEntry() {
        assertEquals(false, read("2 яйца").divisible)
        assertEquals(true, read("2 onions").divisible)
        // An entry setting none, and free text: the author answers.
        assertNull(read("2 грецких ореха").divisible)
        assertNull(read("2 штуки чего-то").divisible)
    }

    @Test
    fun englishReadsTheSameWay() {
        val flour = read("2 cups flour")
        assertEquals(Triple(id("flour"), Rational.of(2), CUP), Triple(flour.dictionaryEntry, flour.quantity, flour.unit))
        val butter = read("1 tbsp butter")
        assertEquals(Triple(id("butter"), Rational.ONE, TABLESPOON), Triple(butter.dictionaryEntry, butter.quantity, butter.unit))
        val milk = read("8 fl oz milk")
        assertEquals(Triple(id("milk"), Rational.of(8), QuantityUnit.FLUID_OUNCE), Triple(milk.dictionaryEntry, milk.quantity, milk.unit))
    }

    @Test
    fun theOwnersBurgerSauceReadsAsNineRows() {
        val text = File("src/test/resources/paste/layout/burger-sauce-yogurt.md").readText()
        val lines = TextLayout.read(text)!!.ingredients
        val rows = lines.mapIndexed { i, line -> reader.read(line, i) }
        rows.forEach(::assertValid)
        fun shown(row: Ingredient) = listOf(row.dictionaryEntry ?: row.freeTextName, row.qualifier, row.quantity, row.unit)
        assertEquals(
            listOf(
                listOf(id("Greek yogurt"), null, Rational.of(100), GRAM),
                listOf(id("tomato paste"), null, Rational.of(20), GRAM),
                listOf(id("mustard"), null, Rational.of(5), GRAM),
                listOf("маринованного огурчика", null, Rational.of(15), GRAM),
                listOf(id("garlic"), "зубчик", half(), PIECE),
                listOf(id("paprika"), null, Rational.ONE, GRAM),
                listOf(id("honey"), null, Rational.of(5), GRAM),
                listOf(id("starch"), null, Rational.of(3), GRAM),
                listOf(id("salt"), null, Rational.ONE, PINCH),
            ),
            rows.map(::shown),
        )
        assertEquals("или мацони, откинутого на марлю — см. заметки", rows[0].note)
        assertEquals("натереть", rows[4].note)
    }

    /** [row] is a row a recipe file may hold: it breaks none of the model's invariants. */
    private fun assertValid(row: Ingredient) {
        val content = RecipeFileContent(
            id = RecipeId("r"), title = "T", description = "", tags = emptySet(), language = null, servings = 1,
            ingredients = listOf(row.copy(position = 0)), steps = listOf(Step(0, "Mix")), author = null, parent = null,
        )
        assertEquals(emptyList(), RecipeFileCheck.problems(content), "$row")
    }

    companion object {
        val shipped: DictionariesDto = Json { ignoreUnknownKeys = true }
            .decodeFromString(DictionariesDto.serializer(), File("../app/src/main/assets/dictionaries.json").readText())
    }
}
