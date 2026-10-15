package app.recipefork.core.paste

import app.recipefork.core.api.IngredientEntryDto
import app.recipefork.core.recipe.AccountId
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.QuantityUnit.CUP
import app.recipefork.core.recipe.QuantityUnit.GRAM
import app.recipefork.core.recipe.QuantityUnit.MILLILITRE
import app.recipefork.core.recipe.QuantityUnit.PIECE
import app.recipefork.core.recipe.QuantityUnit.TABLESPOON
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.Rational
import app.recipefork.core.search.DictionaryMatch
import java.io.File
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * T-284: the shapes of line the corpus showed the parser could not read, each on a line written for the test (the
 * owner's own files apart).
 */
class PasteWeakSpotsTest {
    private val shipped = IngredientLineReaderTest.shipped
    private val reader = IngredientLineReader(DictionaryMatch(shipped.ingredients))
    private val at = Instant.parse("2026-10-15T10:00:00Z")

    private fun row(text: String, reader: IngredientLineReader = this.reader): Ingredient = reader.read(IngredientLine(text, null), 0)

    private fun recipe(text: String): Recipe = assertNotNull(RecipeFromText.read(text, shipped, at, AccountId("owner")), text)

    private fun id(labelEn: String) = shipped.ingredients.single { it.labelEn == labelEn }.id

    private fun label(id: Long?) = shipped.ingredients.firstOrNull { it.id == id }?.labelEn

    /** What a row says, in the order quantity, unit, entry (its English label) or free text, qualifier, note. */
    private fun shown(row: Ingredient) =
        listOf(row.quantity, row.unit, label(row.dictionaryEntry) ?: row.freeTextName, row.qualifier, row.note)

    private fun half() = Rational.of(1, 2)

    @Test
    fun theEdaRuShapeReadsANameAndTheAmountUnderItAsOneLineAndItsServings() {
        val text = """
            Телячий суп

            Порции
            5

            Ингредиенты
            Телятина на кости
            900 г
            Перец чёрный
            6 горошин
            Морковь
            2 штуки
            Соль
            по вкусу

            Приготовление
            Шаг 1
            Сварить бульон из телятины с перцем и морковью.
        """.trimIndent()
        val layout = assertNotNull(TextLayout.read(text))
        assertEquals(listOf("Телятина на кости — 900 г", "Перец чёрный — 6 горошин", "Морковь — 2 штуки", "Соль — по вкусу"), layout.ingredients.map { it.text })
        assertEquals(5, layout.servings)
        assertTrue(layout.description.none { it == "Порции" }, "${layout.description}")
        val rows = recipe(text).ingredients
        assertEquals(listOf(Rational.of(900), GRAM), listOf(rows[0].quantity, rows[0].unit))
        assertEquals(listOf(Rational.of(6), PIECE, "black pepper", "горошина", null), shown(rows[1]))
        assertEquals(listOf(Rational.of(2), PIECE, "carrot"), shown(rows[2]).take(3))
        assertEquals(UnitType.TASTE_UNMEASURED to "salt", rows[3].unitType to label(rows[3].dictionaryEntry))
    }

    @Test
    fun aSingleNameOverAQuantityIsNoEdaRuPair() {
        // One pair alone among quantity lines is no shape of a site: each line stays as it is.
        val layout = assertNotNull(TextLayout.read("Салат\n\nИнгредиенты\nОгурцы\n2 помидора\n100 г сыра\n\nПриготовление\n1. Нарезать всё."))
        assertEquals(listOf("Огурцы", "2 помидора", "100 г сыра"), layout.ingredients.map { it.text })
    }

    @Test
    fun wordsForWholeThingsCountPiecesQualifyingTheEntry() {
        assertEquals(listOf(Rational.ONE, PIECE, "onion", "головка", null), shown(row("1 головка лука")))
        assertEquals(listOf(Rational.ONE, PIECE, "corn", "банка", null), shown(row("1 банка кукурузы")))
        assertEquals(listOf(Rational.ONE, PIECE, "baking powder", "пакетик", null), shown(row("1 пакетик разрыхлителя")))
        assertEquals(listOf(Rational.ONE, PIECE, "fresh cilantro", "пучок", null), shown(row("1 пучок кинзы")))
        assertEquals(listOf(Rational.ONE, PIECE, "raisins", "горсть", null), shown(row("горсть изюма")))
        assertEquals(listOf(Rational.ONE, PIECE, "fresh dill", "small bunch", null), shown(row("small bunch dill")))
        assertEquals(listOf(Rational.ONE, PIECE, "fresh basil", "handful", null), shown(row("a handful of basil")))
        // An entry naming the whole thing holds it: no qualifier.
        assertEquals(listOf(Rational.ONE, PIECE, "vanilla bean", null, null), shown(row("1 стручок ванили")))
        assertEquals(listOf(Rational.of(3), PIECE, "rosemary sprig", null, null), shown(row("3 веточки розмарина")))
        // A dictionary with no entry for the thing: the word qualifies.
        val mintOnly = IngredientLineReader(DictionaryMatch(listOf(IngredientEntryDto(1, "мята", "mint", divisible = true))))
        assertEquals(listOf(Rational.of(2), PIECE, 1L, "веточка"), row("2 веточки мяты", mintOnly).let { listOf(it.quantity, it.unit, it.dictionaryEntry, it.qualifier) })
        // A multicooker's cup is the model's cup, qualified.
        assertEquals(listOf(Rational.of(3), CUP, "buckwheat", "мультистакан", null), shown(row("3 мультистакана гречки")))
        assertEquals(listOf(Rational.ONE, CUP, "water", "мультистакан", null), shown(row("вода — 1 мультистакан")))
        // No entry: the word stays in the free text, for a qualifier qualifies an entry.
        assertEquals(listOf(Rational.of(2), CUP, "мультистакан чего-то", null), shown(row("2 мультистакана чего-то")).take(4))
        assertEquals(listOf(Rational.of(300), GRAM, "can неведомого", null), shown(row("300 g can неведомого")).take(4))
        assertEquals(listOf(Rational.of(2), PIECE, "garlic", "головка", null), shown(row("Чеснок — 2 головки")))
    }

    @Test
    fun aContainerWordBetweenTheQuantityAndTheNameIsNoPartOfTheName() {
        val beans = row("400g tin butter beans, rinsed")
        assertEquals(listOf(Rational.of(400), GRAM, "beans", "butter, tin", "rinsed"), shown(beans))
        // A two-word entry after the container keeps both words.
        assertEquals(listOf(Rational.ONE, PIECE, "coconut milk", "can"), shown(row("1 can coconut milk")).take(4))
    }

    @Test
    fun aQuantityInWordsBeforeTheNameIsRead() {
        assertEquals(listOf(half(), PIECE, "lemon", null, "juice"), shown(row("juice of half a lemon")))
        assertEquals(listOf(Rational.ONE, PIECE, "orange", null, "цедра"), shown(row("цедра одного апельсина")))
        assertEquals(listOf(Rational.of(2), PIECE, "lime", null, "сок"), shown(row("сок двух лаймов")))
        // A quarter piece is off the pieces' grid: half a piece, the quarter kept in the note (DOM-017).
        assertEquals(listOf(half(), PIECE, "white onion", null, "1/4"), shown(row("quarter of a white onion")))
        // A share of an amount is none.
        assertEquals(UnitType.TASTE_UNMEASURED, row("рис — примерно четверть от 300 г сухого").unitType)
    }

    @Test
    fun whatFollowsAPlusAfterTheQuantityGoesToTheNote() {
        assertEquals(listOf(Rational.of(2), TABLESPOON, "sugar", null, "+ для посыпки"), shown(row("сахар — 2 ст. л. + для посыпки")))
        assertEquals(listOf(Rational.of(40), GRAM, "flour", null, "+ щепотка на стол"), shown(row("40 г муки + щепотка на стол")))
    }

    @Test
    fun twoAmountsJoinedByAPlusAreTwoRowsWhileEachBinds() {
        fun lines(text: String) = reader.lines(IngredientLine(text, "Тесто")).map { it.text to it.group }
        assertEquals(listOf("25 г кинзы" to "Тесто", "10 г мяты" to "Тесто"), lines("25 г кинзы + 10 г мяты"))
        assertEquals(listOf("40 г сливочного масла" to "Тесто", "30 мл молока" to "Тесто"), lines("40 г сливочного масла + 30 мл молока"))
        // No entry is a yolk: one row, the rest in its note.
        assertEquals(listOf("2 яйца + 2 желтка" to "Тесто"), lines("2 яйца + 2 желтка"))
        assertEquals(listOf(Rational.of(2), PIECE, "egg", null, "+ 2 желтка"), shown(row("2 яйца + 2 желтка")))
        // Where one is: two rows.
        val withYolk = IngredientLineReader(DictionaryMatch(listOf(IngredientEntryDto(1, "яйцо", "egg"), IngredientEntryDto(2, "желток", "yolk"))))
        assertEquals(listOf("2 яйца", "2 желтка"), withYolk.lines(IngredientLine("2 яйца + 2 желтка", null)).map { it.text })
        // The parts bound first are rows, the first that does not bind stays with the one before.
        assertEquals(listOf("25 г кинзы", "10 г мяты + щепотка чего-то"), lines("25 г кинзы + 10 г мяты + щепотка чего-то").map { it.first })
        // A plus inside an alternative joins the alternative: one row.
        assertEquals(listOf("20 г сливок или 1 яйцо + 5 г воды"), lines("20 г сливок или 1 яйцо + 5 г воды").map { it.first })
        assertEquals("или 1 яйцо + 5 г воды", row("20 г сливок или 1 яйцо + 5 г воды").note)

        val rows = recipe("Соус\n\nИнгредиенты\n- 25 г кинзы + 10 г мяты\n- соль\n\nПриготовление\n1. Смешать.").ingredients
        assertEquals(listOf("fresh cilantro", "fresh mint", "salt"), rows.map { label(it.dictionaryEntry) })
        assertEquals(listOf(0, 1, 2), rows.map { it.position })
    }

    @Test
    fun aQuantityAfterACommaClauseIsRead() {
        assertEquals(listOf(Rational.of(50), GRAM, "mozzarella", null, "нарезанная"), shown(row("моцарелла, нарезанная — 50 г")))
        assertEquals(listOf(half(), PIECE, "white onion"), shown(row("белый лук — половина луковицы, около 80 г")).take(3))
        assertEquals(listOf(Rational.of(2), QuantityUnit.TEASPOON, "coffee"), shown(row("Кофе крупного помола, на глаз — 1–2 ч. л. с горкой")).take(3))
        // A name with no quantity but in a parenthesis in a unit.
        assertEquals(listOf(Rational.of(200), GRAM, "сухого"), row("варёный рис (~200 г сухого)").let { listOf(it.quantity, it.unit, it.note) })
    }

    @Test
    fun aLabelBeforeAColonIsAGroupNotPartOfTheName() {
        val glaze = reader.lines(IngredientLine("Глазурь: 100 г сахарной пудры", null)).single()
        assertEquals("100 г сахарной пудры" to "Глазурь", glaze.text to glaze.group)
        // A quantity with no name after it is no group.
        assertNull(reader.lines(IngredientLine("Яйца: 2", null)).single().group)

        val recipe = recipe("Торт\n\nИнгредиенты\n- 3 яйца\n- Пропитка: 50 мл воды + 30 г сахара\n\nПриготовление\n1. Смешать.")
        assertEquals(listOf("egg", "water", "sugar"), recipe.ingredients.map { label(it.dictionaryEntry) })
        assertEquals(listOf("Пропитка" to 1), recipe.ingredientGroups.map { it.name to it.firstPosition })
        // A colon after a quantity and a name: the rest is a note.
        assertEquals(listOf(Rational.of(90), GRAM, "соуса", null, "«Домашний»"), shown(row("~90 г соуса: «Домашний»")))
        // To taste, what a dash or a colon adds to a bound name is a note.
        assertEquals(listOf(null, null, "flour", null, "для подпыла стола"), shown(row("мука — для подпыла стола")))
        assertEquals(listOf(null, null, "fresh herbs", null, "петрушка или базилик"), shown(row("зелень: петрушка или базилик")))
    }

    @Test
    fun servingsInsideATitleAreReadAndMakesIsNoServings() {
        assertEquals(6, TextLayout.read("Борщ (кастрюля на 3 литра, порций 6)\n\nИнгредиенты\n- 1 свёкла\n- 2 л воды\n\nПриготовление\n1. Варить.")?.servings)
        assertNull(TextLayout.read("Muffins\n\nMakes 12 muffins\n\nIngredients\n- 2 cups flour\n- 2 eggs\n\nMethod\n1. Bake.")?.servings)
    }

    @Test
    fun theOwnersApplePowderListsItsEquipmentAsEquipment() {
        val text = File("src/test/resources/paste/corpus/owner/Яблочный порошок из кожуры.md").readText()
        val recipe = recipe(text)
        assertEquals(listOf("кожура яблок"), recipe.ingredients.map { it.freeTextName })
        val equipment = recipe.equipment.map { item -> shipped.equipment.single { it.id == item.dictionaryEntry }.labelEn }
        assertTrue(equipment.containsAll(listOf("Air fryer", "Coffee grinder", "Sieve", "Jar")), "$equipment")
        // An unbound line that names no equipment stays an ingredient; so does a bound one.
        val rows = recipe("Чай\n\nИнгредиенты\n- чайник\n- листья неведомые\n- вода\n\nПриготовление\n1. Заварить.").ingredients
        assertEquals(listOf("листья неведомые", null), rows.map { it.freeTextName })
    }

    @Test
    fun aBareMinuteOrHourIsATimer() {
        fun minutes(text: String) = StepTimers.read(text).map { it.duration.inWholeMinutes }
        assertEquals(listOf(1L), minutes("Влить сливки и прогреть минуту."))
        assertEquals(listOf(60L), minutes("Убрать в холод минимум час."))
        assertEquals(listOf(1L), minutes("Обжарить ломтики по минуте с каждой стороны."))
        assertEquals(listOf(1L), minutes("Взбивать около минуты."))
        assertEquals(listOf(1L), minutes("Жарить по 1 минуте с каждой стороны."))
        assertEquals(listOf(1L, 60L), minutes("Stir for a minute, then leave for an hour."))
        assertEquals(listOf(1L), minutes("Дать постоять минутку."))
        listOf(
            "Печь на минуту меньше, чем обычно.", "Помешивать каждую минуту.", "За минуту до конца добавить соль.",
            "В первую минуту не трогать.", "Add the herbs an hour later.", "Поставить тесто на час раньше.",
        ).forEach { assertEquals(emptyList(), minutes(it), it) }
    }

    // Fix round 1: each rule to its shape, on fresh lines.

    @Test
    fun aPlusBetweenTwoAmountsOfOneNameIsThatName() {
        assertEquals(listOf(Rational.ONE, CUP, "flour", null, "+ 2 tbsp"), shown(row("1 cup + 2 tbsp flour")))
        assertEquals(listOf(Rational.of(3), TABLESPOON, "sugar", null, "+ 1 ч. л."), shown(row("3 ст. л. + 1 ч. л. сахара")))
        assertEquals(listOf("3 ст. л. + 1 ч. л. сахара"), reader.lines(IngredientLine("3 ст. л. + 1 ч. л. сахара", null)).map { it.text })
    }

    @Test
    fun anAmountAloneIsReadAsTheReaderReadsAQuantity() {
        val text = "Крем\n\nИнгредиенты\nСливки\n1 ½ стакана\nСахар\nщепотка\nКрахмал\n2 1/2 ст. л.\nВанилин\n≈3 г\n\nПриготовление\nШаг 1\nСмешать всё."
        assertEquals(
            listOf("Сливки — 1 ½ стакана", "Сахар — щепотка", "Крахмал — 2 1/2 ст. л.", "Ванилин — ≈3 г"),
            assertNotNull(TextLayout.read(text)).ingredients.map { it.text },
        )
        listOf("1 ½ стакана", "щепотка", "по вкусу", "2 1/2 ст. л.", "~5 г", "3 зубчика", "полстакана")
            .forEach { assertTrue(IngredientLineReader.amountOnly(it), it) }
        listOf("2 помидора", "Сливки", "100 г сыра", "").forEach { assertTrue(!IngredientLineReader.amountOnly(it), it) }
    }

    @Test
    fun numberWordsInTheGenitiveAndSeveralWordsBeforeTheAmountAreRead() {
        assertEquals(listOf(half(), PIECE, "orange", null, "сок"), shown(row("сок половины апельсина")))
        assertEquals(listOf(half(), PIECE, "avocado", null, "мякоть"), shown(row("мякоть половины авокадо")))
        assertEquals(listOf(half(), PIECE, "lemon", null, "1/4, сок"), shown(row("сок четверти лимона")))
        assertEquals(listOf(Rational.ONE, PIECE, "orange", null, "zest and juice"), shown(row("zest and juice of 1 orange")))
        assertEquals(listOf(Rational.of(2), PIECE, "lime", null, "juice and zest"), shown(row("juice and zest of 2 limes")))
        assertEquals(listOf(Rational.ONE, PIECE, "lemon", null, "finely grated zest"), shown(row("finely grated zest of 1 lemon")))
        assertEquals(listOf(Rational.ONE, PIECE, "orange", null, "сок и цедра"), shown(row("сок и цедра 1 апельсина")))
    }

    @Test
    fun theLongestNameAfterAContainerWordIsBound() {
        assertEquals(listOf(Rational.ONE, PIECE, "beans", "butter, can"), shown(row("1 can butter beans")).take(4))
        assertEquals(listOf(Rational.of(2), PIECE, "beans", "butter, tin"), shown(row("2 tins butter beans")).take(4))
    }

    @Test
    fun aLabelBeforeAnAmountIsAGroupWhateverItSays() {
        fun labelled(text: String) = reader.lines(IngredientLine(text, null)).single().let { it.text to it.group }
        assertEquals("2 tbsp olive oil" to "For the sauce", labelled("For the sauce: 2 tbsp olive oil"))
        assertEquals("50 g icing sugar" to "For the topping", labelled("For the topping: 50 g icing sugar"))
        assertEquals("300 г муки" to "Тесто", labelled("Тесто: 300 г муки"))
        assertEquals("200 г муки" to "Мука", labelled("Мука: 200 г муки"))
        assertEquals("Соль: по вкусу" to null, labelled("Соль: по вкусу"))
    }

    @Test
    fun moreWordsForWholeThingsBeforeOrAfterTheName() {
        assertEquals(listOf(Rational.ONE, PIECE, "butter", "пачка"), shown(row("1 пачка сливочного масла")).take(4))
        assertEquals(listOf(Rational.ONE, PIECE, "butter", "пачка"), shown(row("Масло сливочное — 1 пачка")).take(4))
        assertEquals(listOf(Rational.of(2), PIECE, "quark", "пачка"), shown(row("2 пачки творога")).take(4))
        assertEquals(listOf(Rational.of(3), PIECE, "celery", "стебель"), shown(row("Сельдерей — 3 стебля")).take(4))
        assertEquals(listOf(Rational.ONE, PIECE, "quark", "упаковка"), shown(row("1 упаковка творога")).take(4))
        assertEquals(listOf(Rational.of(2), PIECE, "ломтик"), shown(row("2 ломтика ветчины")).let { listOf(it[0], it[1], it[3]) })
    }

    @Test
    fun aWordForAWholeThingThatNamesEquipmentAndNoIngredientIsEquipment() {
        val recipe = recipe("Соленья\n\nИнгредиенты\n- 1 кг огурцов\n- банка с крышкой\n- a jar with a lid\n\nПриготовление\n1. Уложить огурцы.")
        assertEquals(listOf("cucumber"), recipe.ingredients.map { label(it.dictionaryEntry) })
        assertTrue(recipe.equipment.map { item -> shipped.equipment.single { it.id == item.dictionaryEntry }.labelEn }.contains("Jar"))
        // Equipment in a parenthesis of food is no equipment line.
        val rows = recipe("Т\n\nИнгредиенты\n- 4 пирожка (лучше из аэрогриля)\n- соль\n\nПриготовление\n1. Подать.").ingredients
        assertEquals(2, rows.size)
    }

    @Test
    fun aLongListIsReadAtOnce() {
        val long = (1..20_000).joinToString("\n") { "- $it г соли + $it г сахара" }
        val took = kotlin.time.measureTime { recipe("Т\n\nИнгредиенты\n$long\n\nПриготовление\n1. Смешать.") }
        assertTrue(took.inWholeMilliseconds < 2_000, "$took")
        listOf("1 г соли" + " + 1 г соли".repeat(5_000), "Крем: " + "1 г + ".repeat(5_000) + "соли", "For " + "the ".repeat(10_000) + ": 1 g salt")
            .forEach { text ->
                val time = kotlin.time.measureTime { reader.lines(IngredientLine(text, null)) }
                assertTrue(time.inWholeMilliseconds < 2_000, "${text.take(12)}…: $time")
            }
    }

    @Test
    fun moreBareAndWordedDurationsAreTimers() {
        fun minutes(text: String) = StepTimers.read(text).map { it.duration.inWholeMinutes }
        assertEquals(listOf(60L), minutes("Тушить около часа."))
        assertEquals(listOf(60L), minutes("Охладить не менее часа."))
        assertEquals(listOf(60L), minutes("Мариновать в течение часа."))
        assertEquals(listOf(60L), minutes("Тесто подойдёт за час."))
        assertEquals(listOf(1L), minutes("Cook for another minute."))
        assertEquals(listOf(1L), minutes("Stir a further minute."))
        assertEquals(listOf(2L), minutes("Варить две минуты."))
        assertEquals(listOf(2L), minutes("Прогреть пару минут."))
        assertEquals(listOf(180L), minutes("Bake for three hours."))
        listOf(
            "Take the butter out an hour before baking.", "Час пик — не время для готовки.", "За минуту до конца добавить соль.",
            "Ориентироваться на минуты нет смысла.", "Следить с пятой минуты.",
        )
            .forEach { assertEquals(emptyList(), minutes(it), it) }
    }
}
