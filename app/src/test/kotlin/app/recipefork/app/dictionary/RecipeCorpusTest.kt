package app.recipefork.app.dictionary

import app.recipefork.core.scaling.InterfaceLanguage
import app.recipefork.core.scaling.InterfaceLanguage.ENGLISH
import app.recipefork.core.scaling.InterfaceLanguage.RUSSIAN
import app.recipefork.core.search.WordPrefix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The shipped dictionary against ingredient lines of everyday home recipes (T-124): a corpus written for this test,
 * before the dictionary sources were read, as cooks write ingredient names in recipe lists — Russian and English.
 *
 * Every name of the corpus names the entry the cook means (T-130): its id, or ids where either is meant, or a gap where
 * the dictionary holds no such entry. A name is run through the ingredient field's real offer ([IngredientOffer])
 * as written, and as an author normally cuts it short — its main word alone: "сливки" for "сливки 20%", "eggs" for
 * "large eggs" — and is found only when, either way, the entry meant is among the first [SHOWN] offered; a gap is never
 * found. The rate is over the corpus's distinct names, each with the entry it means. Every miss, every gap and every
 * wrong landing — the entry meant found, but another offered first — is printed.
 */
class RecipeCorpusTest {
    /** Unit tests of `:app` run with the module directory as the working directory. */
    private val dictionary = DictionarySnapshot.parse(File("src/main/assets/${DictionarySnapshot.ASSET}").readText())
        .ingredients.map(IngredientEntryEntity::of)
    private val byId = dictionary.associateBy { it.id }

    private val russian by lazy { Corpus.read("recipe-corpus-ru.txt") }
    private val english by lazy { Corpus.read("recipe-corpus-en.txt") }

    @Test
    fun everyNameOfTheCorpusMeansALiveEntryOrAGap() {
        assertTrue("${russian.recipes} Russian recipes", russian.recipes >= 150)
        assertTrue("${english.recipes} English recipes", english.recipes >= 50)
        (russian.names + english.names).forEach { name ->
            name.intended.forEach { id ->
                val entry = byId[id]
                assertTrue("\"${name.text}\" means $id, which the dictionary does not hold", entry != null && !entry.removed)
            }
        }
    }

    // The honest rate is printed with every miss; it may only rise.
    @Test
    fun theRussianCorpusIsMeasuredAgainstTheEntryMeant() {
        val rate = measure("Russian", russian, RUSSIAN)
        assertTrue("Russian: ${percent(rate)} fell below ${percent(FLOOR_RU)}", rate >= FLOOR_RU)
    }

    @Test
    fun theEnglishCorpusIsMeasuredAgainstTheEntryMeant() {
        val rate = measure("English", english, ENGLISH)
        assertTrue("English: ${percent(rate)} fell below ${percent(FLOOR_EN)}", rate >= FLOOR_EN)
    }

    @Test
    fun theRussianCorpusFindsTheEntryMeantForAlmostEveryName() {
        val rate = measure("Russian", russian, RUSSIAN)
        assertTrue("Russian: ${percent(rate)}", rate >= RATE)
    }

    @Test
    fun theEnglishCorpusFindsTheEntryMeantForAlmostEveryName() {
        val rate = measure("English", english, ENGLISH)
        assertTrue("English: ${percent(rate)}", rate >= RATE)
    }

    // The owner's lemon cake, every name of it, finds the entry meant among the first offered.
    @Test
    fun theLemonCakeIsCoveredWhole() {
        val cake = russian.names.filter { it.recipe == "Лимонный кекс" }
        assertTrue("the lemon cake is in the corpus", cake.size >= 10)
        cake.forEach { assertTrue(it.text, found(it, RUSSIAN)) }
    }

    // Scenarios 3 and 5 of T-130: what is typed lands on the entry meant, the plain entry before its kinds.
    @Test
    fun theWordAsWrittenLandsOnTheEntryMeant() {
        mapOf(
            "сыр моцарелла" to 4735L, "сыр чеддер" to 4749L, "яйца" to 4400L, "яйцо" to 4400L, "лука" to 1100L,
            "сахара" to 6100L, "моркови" to 1030L, "огурцы" to 1320L,
        ).forEach { (typed, id) -> assertEquals(typed, id, IngredientOffer.of(typed, dictionary, RUSSIAN).entries.firstOrNull()?.id) }
        mapOf(
            "eggs" to 4400L, "onions" to 1100L, "bay leaves" to 7251L, "lemons" to 2000L, "carrots" to 1030L,
            "mozzarella cheese" to 4735L, "cheddar cheese" to 4749L, "potatoes" to 1000L,
        ).forEach { (typed, id) -> assertEquals(typed, id, IngredientOffer.of(typed, dictionary, ENGLISH).entries.firstOrNull()?.id) }
    }

    // T-131: the word a cook writes lands on the entry meant — the plain entry, not a look-alike or a kind of it.
    @Test
    fun theWordACookWritesLandsOnThePlainEntry() {
        mapOf(
            "куриные крылья" to 2L, "овсянка" to 5350L, "морковка" to 1030L, "грецкий орех" to 2501L, "сгущёнка" to 4461L,
            "перец" to 7100L, "масло" to 4600L, "базилик" to 1706L, "укроп" to 1701L, "петрушка" to 1702L, "кинза" to 1705L,
            "мята" to 1710L, "паста" to 5400L, "огуречный рассол" to 8426L, "говяжьи ноги" to 3131L,
        ).forEach { (typed, id) -> assertEquals(typed, id, IngredientOffer.of(typed, dictionary, RUSSIAN).entries.firstOrNull()?.id) }
        mapOf(
            "roma tomatoes" to 1302L, "swiss cheese" to 4750L, "chicken broth" to 8601L, "beef broth" to 8602L,
            "pepper" to 7100L, "basil" to 1706L, "cilantro" to 1705L, "rosemary" to 1720L, "sage" to 1722L, "thyme" to 1721L,
            "dill" to 1701L, "green onions" to 1107L, "italian sausage" to 3833L,
        ).forEach { (typed, id) -> assertEquals(typed, id, IngredientOffer.of(typed, dictionary, ENGLISH).entries.firstOrNull()?.id) }
    }

    // Scenario 6 of T-131: пармиджано is one entry, the Parmesan; the duplicate is removed and offered no more.
    @Test
    fun parmigianoIsOneEntryTheParmesan() {
        assertTrue("4792 is removed", byId.getValue(4792).removed)
        listOf("пармиджано" to RUSSIAN, "пармиджано реджано" to RUSSIAN, "parmigiano" to ENGLISH, "Parmigiano Reggiano" to ENGLISH)
            .forEach { (typed, language) ->
                val offer = IngredientOffer.of(typed, dictionary, language)
                assertEquals(typed, listOf(4732L), offer.entries.map { it.id })
            }
        assertEquals(4732L, IngredientOffer.of("пармиджано реджано", dictionary, RUSSIAN).exact?.id)
    }

    // The test's own reading of the corpus.
    @Test
    fun theCorpusIsReadNameByName() {
        assertEquals(listOf("соль", "перец"), names("соль, перец по вкусу"))
        assertEquals(listOf("unsalted butter"), names("unsalted butter, melted"))
        assertEquals(listOf("salt", "pepper"), names("salt and pepper"))
        assertEquals(listOf("сливки 20%"), names("сливки 20%"))
        assertEquals("сливки", truncation("сливки 20%"))
        assertEquals("eggs", truncation("large eggs"))
        assertEquals("сливочное", truncation("сливочное масло"))
        assertEquals("сыр", truncation("сыр моцарелла"))
        val salt = Corpus.parse(listOf("@ Борщ", "соль, перец по вкусу | salt, black pepper | 7000, 7100/7101", "вода | water | gap"))
        assertEquals(1, salt.recipes)
        assertEquals(
            listOf(Name("соль", setOf(7000), "Борщ"), Name("перец", setOf(7100, 7101), "Борщ"), Name("вода", emptySet(), "Борщ")),
            salt.names,
        )
    }

    private fun found(name: Name, language: InterfaceLanguage): Boolean = shown(name, language).any { it.id in name.intended }

    /** The first offered for [name] as written or, where that misses the entry meant, as cut short ([truncation]). */
    private fun shown(name: Name, language: InterfaceLanguage): List<IngredientEntryEntity> {
        val written = IngredientOffer.of(name.text, dictionary, language).entries.take(SHOWN)
        if (written.any { it.id in name.intended }) return written
        val cut = truncation(name.text)
        return if (cut == name.text) written else IngredientOffer.of(cut, dictionary, language).entries.take(SHOWN)
    }

    /** The rate of [corpus]'s distinct names found, printed with every miss, gap and wrong landing. */
    private fun measure(title: String, corpus: Corpus, language: InterfaceLanguage): Double {
        val names = corpus.names.distinctBy { it.text to it.intended }
        val label = { id: Long -> "${byId[id]?.label(language)} #$id" }
        var found = 0
        val report = mutableListOf<String>()
        names.forEach { name ->
            val offered = shown(name, language)
            val shown = offered.map { label(it.id) }
            val at = offered.indexOfFirst { it.id in name.intended }
            when {
                name.intended.isEmpty() -> report += "CORPUS $title gap: \"${name.text}\" offers $shown"
                at < 0 -> report += "CORPUS $title miss: \"${name.text}\" means ${name.intended.map(label)}, offers $shown"
                else -> {
                    found++
                    if (at > 0) report += "CORPUS $title wrong landing: \"${name.text}\" means ${label(offered[at].id)}, lands on ${shown.first()}"
                }
            }
        }
        val rate = found.toDouble() / names.size
        println("CORPUS $title: ${corpus.recipes} recipes, ${names.size} names, found ${percent(rate)}")
        report.sorted().forEach(::println)
        return rate
    }

    private fun percent(rate: Double) = "%.1f %%".format(rate * 100)

    /** One name of the corpus, in its [recipe], and the ids of the entries meant by it; none for a gap. */
    data class Name(val text: String, val intended: Set<Long>, val recipe: String)

    private class Corpus(val recipes: Int, val names: List<Name>) {
        companion object {
            fun read(resource: String): Corpus = parse(
                (Corpus::class.java.getResourceAsStream("/dictionary/$resource") ?: error("no $resource")).bufferedReader().readLines(),
            )

            fun parse(lines: List<String>): Corpus {
                var recipes = 0
                var recipe = ""
                val names = mutableListOf<Name>()
                lines.map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.forEach { line ->
                    if (line.startsWith("@")) {
                        recipes++
                        recipe = line.removePrefix("@").trim()
                        return@forEach
                    }
                    val columns = line.split("|").map { it.trim() }
                    require(columns.size == 3) { "no intended ids: $line" }
                    val texts = names(columns[0])
                    val ids = columns[2].split(",").map { it.trim() }
                    require(texts.size == ids.size) { "${texts.size} names, ${ids.size} ids: $line" }
                    texts.zip(ids).forEach { (text, id) ->
                        val intended = if (id == "gap") emptySet() else id.split("/").map { it.trim().toLong() }.toSet()
                        names += Name(text, intended, recipe)
                    }
                }
                return Corpus(recipes, names)
            }
        }
    }

    companion object {
        const val SHOWN = 5
        const val RATE = 0.95

        /** The honest rates measured when T-130 named the entries meant: a floor the rates must not fall below. */
        const val FLOOR_RU = 0.93
        const val FLOOR_EN = 0.93

        /** Qualifiers a cook writes after a name, not part of it. */
        private val tails = Regex("""\s+(по вкусу|для\s.*|to taste|for\s.*|optional)$""")
        private val preparation = setOf("melted", "softened", "divided", "chopped", "diced", "minced", "optional")

        /** The names of one ingredient line: "соль, перец по вкусу" is salt and pepper. */
        fun names(line: String): List<String> = line.split(Regex(""",|;|\s+and\s+|\s+и\s+"""))
            .map { it.replace(Regex("""\(.*?\)"""), "").trim().replace(tails, "").trim() }
            .filter { it.isNotEmpty() && it !in preparation }

        /** Words that describe a name rather than name it: the main word is the first word that is not one of these. */
        private val descriptors = setOf(
            "large", "small", "medium", "fresh", "freshly", "ripe", "cold", "warm", "melted", "softened", "unsalted",
            "salted", "extra", "virgin", "light", "chopped", "minced", "shredded", "grated", "diced", "crushed", "cooked",
            "canned", "frozen", "dried", "boneless", "skinless", "hard", "boiled", "sharp", "plain", "old", "fashioned",
            "ground", "whole", "roasted",
            "свежий", "свежая", "свежее", "свежие", "крупный", "крупная", "крупные", "мелкий", "мелкая", "мелкие",
            "замороженный", "замороженная", "замороженные", "молотый", "молотая", "отварной", "отварная", "вареный",
            "вареная", "консервированный", "консервированная", "консервированные",
        )

        /** [name] as an author normally cuts it short: its first word that names rather than describes it, as written. */
        fun truncation(name: String): String {
            val words = WordPrefix.words(name)
            val main = words.firstOrNull { it !in descriptors } ?: words.first()
            return name.trim().split(Regex("""[\s,]+""")).firstOrNull { main in WordPrefix.words(it) } ?: name.trim()
        }
    }
}
