package app.recipefork.app.dictionary

import app.recipefork.core.scaling.InterfaceLanguage
import app.recipefork.core.scaling.InterfaceLanguage.ENGLISH
import app.recipefork.core.scaling.InterfaceLanguage.RUSSIAN
import app.recipefork.core.search.Inflection
import app.recipefork.core.search.WordPrefix
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The shipped dictionary against ingredient lines of everyday home recipes (T-124): a corpus written for this test,
 * before the dictionary sources were read, as cooks write ingredient names in recipe lists — Russian and English.
 *
 * Each name is run through the ingredient field's real offer ([IngredientOffer]) twice — as typed, and as an author
 * normally cuts it short (its main word alone: "сливки" for "сливки 20%", "eggs" for "large eggs") — and is found
 * when, either way, one of the first [SHOWN] offered entries, or the entry the field takes on leaving it (the exact
 * match), is a sensible match: one whose label, in the interface language, shares the name's main word ([sensible]).
 * Every miss is printed.
 */
class RecipeCorpusTest {
    /** Unit tests of `:app` run with the module directory as the working directory. */
    private val dictionary = DictionarySnapshot.parse(File("src/main/assets/${DictionarySnapshot.ASSET}").readText())
        .ingredients.map(IngredientEntryEntity::of)

    @Test
    fun theRussianCorpusFindsASensibleEntryForAlmostEveryName() {
        val corpus = Corpus.read("recipe-corpus-ru.txt")
        assertTrue("${corpus.recipes} Russian recipes", corpus.recipes >= 150)
        assertCovered("Russian", corpus, RUSSIAN)
    }

    @Test
    fun theEnglishCorpusFindsASensibleEntryForAlmostEveryName() {
        val corpus = Corpus.read("recipe-corpus-en.txt")
        assertTrue("${corpus.recipes} English recipes", corpus.recipes >= 50)
        assertCovered("English", corpus, ENGLISH)
    }

    // The owner's lemon cake, every name of it.
    @Test
    fun theLemonCakeIsCoveredWhole() {
        listOf(
            "лимон", "яйца", "сахар", "растительное масло без запаха", "мацони", "мука", "разрыхлитель", "сода", "соль",
            "сахарная пудра", "лимонный сок",
        ).forEach { assertTrue(it, finds(it, RUSSIAN)) }
    }

    // The test's own yardstick, on names it must and must not count.
    @Test
    fun theYardstickIsTheMainWord() {
        assertTrue(sensible("лук репчатый", "лук репчатый"))
        assertTrue(sensible("лук", "лук-порей"))
        assertTrue(sensible("сливочное масло", "масло сливочное"))
        assertTrue(sensible("large eggs", "egg"))
        assertTrue(sensible("unsalted butter", "butter"))
        // The same word in another form shares it, as the offer reads words.
        assertTrue(sensible("яйца", "яйцо"))
        assertTrue(sensible("грецкие орехи", "орех грецкий"))
        assertTrue(sensible("морковка", "морковь"))
        assertTrue(!sensible("лук", "чеснок"))
        assertTrue(!sensible("сливочное масло", "сливки"))
        assertEquals(listOf("соль", "перец"), names("соль, перец по вкусу"))
        assertEquals(listOf("unsalted butter"), names("unsalted butter, melted"))
        assertEquals(listOf("salt", "pepper"), names("salt and pepper"))
        assertEquals("сливки", truncation("сливки 20%"))
        assertEquals("eggs", truncation("large eggs"))
        assertEquals("сливочное", truncation("сливочное масло"))
    }

    private fun assertEquals(expected: Any?, actual: Any?) = org.junit.Assert.assertEquals(expected, actual)

    private fun assertCovered(name: String, corpus: Corpus, language: InterfaceLanguage) {
        val misses = corpus.names.filterNot { finds(it, language) }
        val rate = 1.0 - misses.size.toDouble() / corpus.names.size
        println("CORPUS $name: ${corpus.recipes} recipes, ${corpus.names.size} names, found ${"%.1f".format(rate * 100)} %")
        misses.forEach { miss ->
            val offered = IngredientOffer.of(truncation(miss), dictionary, language).entries.take(SHOWN).map { it.label(language) }
            println("CORPUS $name miss: \"$miss\" (as \"${truncation(miss)}\" offers $offered)")
        }
        assertTrue("$name: ${misses.size} of ${corpus.names.size} names missed: $misses", rate >= RATE)
    }

    private fun finds(name: String, language: InterfaceLanguage): Boolean = listOf(name, truncation(name)).distinct().any { typed ->
        val offer = IngredientOffer.of(typed, dictionary, language)
        (offer.entries.take(SHOWN) + listOfNotNull(offer.exact)).any { sensible(name, it.label(language)) }
    }

    private class Corpus(val recipes: Int, val names: List<String>) {
        companion object {
            fun read(resource: String): Corpus {
                val lines = (Corpus::class.java.getResourceAsStream("/dictionary/$resource") ?: error("no $resource"))
                    .bufferedReader().readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
                val recipes = lines.count { it.startsWith("@") }
                val names = lines.filterNot { it.startsWith("@") }.flatMap { names(it.substringBefore("|")) }.distinct()
                return Corpus(recipes, names)
            }
        }
    }

    companion object {
        const val SHOWN = 5
        const val RATE = 0.95

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

        /** The folded first word of [name] that names rather than describes it, or its first word when every one describes. */
        fun mainWord(name: String): String {
            val words = WordPrefix.words(name)
            return words.firstOrNull { it !in descriptors } ?: words.first()
        }

        /** [name] as an author normally cuts it short: its main word alone, as written. */
        fun truncation(name: String): String {
            val main = mainWord(name)
            return name.trim().split(Regex("""[\s,]+""")).firstOrNull { main in WordPrefix.words(it) } ?: name.trim()
        }

        /**
         * Whether [label] shares [name]'s main word: the main word is a word-prefix of a word of the label or the other
         * way round, as the search folds them — or, as the offer reads words, is that word in another form or akin to it
         * ([Inflection]).
         */
        fun sensible(name: String, label: String): Boolean {
            val main = mainWord(name)
            val forms = Inflection.forms(main)
            return WordPrefix.words(label).any { word ->
                word.startsWith(main) || main.startsWith(word) ||
                    Inflection.forms(word).any { form -> forms.any { it == form || Inflection.akin(it, form) } }
            }
        }
    }
}
