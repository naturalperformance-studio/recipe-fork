package app.recipefork.app.perf

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.card.CardEnvironment
import app.recipefork.app.card.CardScreen
import app.recipefork.app.card.CardTags
import app.recipefork.app.dictionary.DictionarySnapshot
import app.recipefork.app.dictionary.IngredientEntryEntity
import app.recipefork.app.dictionary.IngredientOffer
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.editor.TestDevice
import app.recipefork.app.editor.textsOf
import app.recipefork.app.recipe.ListingParams
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.api.DictionariesDto
import app.recipefork.core.api.IngredientEntryDto
import app.recipefork.core.api.TagDto
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.InterfaceLanguage
import app.recipefork.core.scaling.Rational
import app.recipefork.core.search.BookOrder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.util.Locale

/**
 * QUAL-002 / QUAL-003 on the JVM, at the caps: a 5 000-entry ingredient dictionary and recipes of DOM-012's 50
 * ingredients and 50 steps. Each figure is the median of [RUNS] runs after warm-up runs (QUAL-001: a first-launch
 * cost is not ordinary behaviour), printed and held to the `typical` threshold. A JVM under Robolectric is no
 * reference device; the figures guard against a regression of the order of the thresholds, not for the release report.
 */
@RunWith(AndroidJUnit4::class)
class CapsBenchTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val device = TestDevice(context)
    private val recipes = RecipeRepository(ActiveBook(BookName.Pool, book.database))
    private val dao = device.database.dictionaryDao()
    private val environment = CardEnvironment { dao.ingredients() }
    private val now = Instant.parse("2026-10-01T12:00:00Z")

    @Before
    fun setUp() {
        compose.registerIdlingResource(book.idling)
        compose.registerIdlingResource(device.idling)
        runBlocking { dao.replace(DICTIONARY) }
    }

    @After
    fun close() {
        compose.unregisterIdlingResource(book.idling)
        compose.unregisterIdlingResource(device.idling)
        book.close()
        device.close()
    }

    // QUAL-002: open a held recipe with no connection, 1 s; recalculate it to another serving count, 200 ms.
    @Test
    fun aRecipeAtTheCapsOpensAndRecalculatesWithinTheThresholds() {
        val held = (0 until WARM_UP + RUNS).map { capsRecipe(it) }
        runBlocking { held.forEach { recipes.save(it) } }
        val shown = showCards()
        assertWithin("card open (50 ingredients, 50 steps, 5 000-entry dictionary)", opens(held, shown), OPEN_MS)

        val recalculations = (0 until WARM_UP + RUNS).map { run ->
            val servings = compose.textsOf(CardTags.SERVINGS).single()
            val first = compose.textsOf(CardTags.ingredient(0))
            timed {
                compose.onNodeWithTag(if (run % 2 == 0) CardTags.SERVINGS_MORE else CardTags.SERVINGS_FEWER).performClick()
                compose.waitUntil(PATIENCE_MS) {
                    compose.textsOf(CardTags.SERVINGS).single() != servings && compose.textsOf(CardTags.ingredient(0)) != first
                }
            }
        }.drop(WARM_UP)
        assertWithin("recalculation (servings change, 50 ingredients)", recalculations, RECALCULATION_MS)
    }

    // QUAL-002 with the dictionary the application ships (T-124): a card of 50 of its entries opens within 1 s.
    @Test
    fun aRecipeOfTheShippedDictionaryOpensWithinTheThreshold() {
        // The bench's own dictionary is held removed beside it, as a device holds what is no longer shipped.
        runBlocking { dao.replace(SHIPPED) }
        val ids = SHIPPED.ingredients.filterNot { it.removed }.map { it.id }
        assertEquals(ids.size, runBlocking { dao.ingredients() }.count { !it.removed })
        val held = (0 until WARM_UP + RUNS).map { capsRecipe(it, ids) }
        runBlocking { held.forEach { recipes.save(it) } }
        assertWithin("card open (50 ingredients, 50 steps, shipped dictionary)", opens(held, showCards()), OPEN_MS)
    }

    private fun showCards(): MutableState<RecipeId?> {
        val shown = mutableStateOf<RecipeId?>(null)
        compose.setContent {
            MaterialTheme {
                shown.value?.let { id -> key(id) { CardScreen(id, recipes, environment, onBack = {}, onEdit = {}) } }
            }
        }
        return shown
    }

    /** How long each of [held] takes to open in full, after the warm-up runs. */
    private fun opens(held: List<Recipe>, shown: MutableState<RecipeId?>): List<Long> = held.map { recipe ->
        shown.value = null
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodes(hasTestTag(CardTags.SCREEN)).fetchSemanticsNodes().isEmpty() }
        timed {
            shown.value = recipe.id
            compose.waitUntil(PATIENCE_MS) { shown(CardTags.ingredient(CAP - 1)) && shown(CardTags.step(CAP - 1)) }
        }
    }.drop(WARM_UP)

    // QUAL-002: show the results of a search, 2 s — over a book that holds more than a screen of recipes at the caps.
    @Test
    fun aSearchOfABookAtTheCapsListsWithinTheThreshold() = runBlocking {
        recipes.save(capsRecipe(0))
        (1 until BOOK).forEach { recipes.save(capsRecipe(it)) }
        val params = MutableStateFlow(ListingParams("", BookOrder.NEWEST, Locale.ENGLISH))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val listings = recipes.ownListed(params) { dao.ingredients() }.shareIn(scope, SharingStarted.Eagerly, replay = 1)
            listings.first { it.params == params.value && it.rows.size == BOOK }
            val queries = listOf("karo", "Caps 1", "mile", "ro mi", "tu", "Soup", "va ni", "pe", "za bu", "gi ka", "le", "ka ro mi")
            val found = mutableMapOf<String, Int>()
            val searches = queries.map { query ->
                val asked = ListingParams(query, BookOrder.NEWEST, Locale.ENGLISH)
                timed {
                    params.value = asked
                    found[query] = runBlocking { listings.first { it.params == asked } }.rows.size
                }
            }
            // A selection, not the whole book: "Caps 1" is recipes 1, 10–19 and 100–199.
            assertEquals(111, found.getValue("Caps 1"))
            println("BENCH book search, first query (builds the index over $BOOK recipes): ${searches.first()} ms")
            assertWithin("book search ($BOOK recipes at the caps)", searches.drop(1), SEARCH_MS)
        } finally {
            scope.cancel()
        }
    }

    // FLOW-057: the ingredient field's offer, worked out on every keystroke over the whole dictionary.
    @Test
    fun theIngredientOfferPerKeystrokeIsWithinItsCeiling() = runBlocking {
        val dictionary = dao.ingredients()
        assertEquals(ENTRIES, dictionary.size)
        val typed = listOf("кароми", "розату", "chicken", "ka ro", "миле", "tusova", "пени", "zabu", "гика", "le mi")
            .flatMap { word -> (1..word.length).map { word.take(it) } }
        repeat(2) { typed.forEach { IngredientOffer.of(it, dictionary, InterfaceLanguage.RUSSIAN) } }
        val offers = typed.map { text -> timed { IngredientOffer.of(text, dictionary, InterfaceLanguage.RUSSIAN) } }
        println("BENCH ingredient offer per keystroke, slowest: ${offers.max()} ms")
        assertWithin("ingredient offer per keystroke (5 000 entries)", offers, OFFER_MS)
    }

    // FLOW-057 with the dictionary the application ships (T-124): what cooks type, keystroke by keystroke, within 50 ms.
    @Test
    fun theIngredientOfferOverTheShippedDictionaryPerKeystrokeIsWithinItsCeiling() {
        // As a device that never held another dictionary holds it.
        val dictionary = SHIPPED.ingredients.map(IngredientEntryEntity::of)
        val words = listOf("лук репчатый", "сливочное масло", "яйца", "мука пшеничная", "солёные огурцы", "chicken breast", "eggs", "all-purpose flour", "creme fraiche", "к")
        val typed = words.flatMap { word -> (1..word.length).map { word.take(it) } }
        val languages = listOf(InterfaceLanguage.RUSSIAN, InterfaceLanguage.ENGLISH)
        repeat(2) { typed.forEach { text -> languages.forEach { IngredientOffer.of(text, dictionary, it) } } }
        val offers = typed.flatMap { text -> languages.map { language -> timed { IngredientOffer.of(text, dictionary, language) } } }
        println("BENCH ingredient offer per keystroke over the shipped dictionary (${dictionary.size} entries), slowest: ${offers.max()} ms")
        assertWithin("ingredient offer per keystroke (shipped dictionary)", offers, OFFER_MS)
    }

    // The one cost the shipped dictionary has at its size: parsed and written into the device's copy, once.
    @Test
    fun theDictionaryAtItsSizeIsParsedAndSeededWithinItsCeiling() = runBlocking {
        val text = Json.encodeToString(DictionariesDto.serializer(), DICTIONARY)
        val seeds = (0 until WARM_UP + RUNS).map {
            timed { runBlocking { dao.replace(DictionarySnapshot.parse(text)) } }
        }.drop(WARM_UP)
        assertWithin("snapshot parse and seed (5 000 entries)", seeds, SEED_MS)
    }

    private fun shown(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().size == 1

    private inline fun timed(work: () -> Unit): Long {
        val start = System.nanoTime()
        work()
        return (System.nanoTime() - start) / 1_000_000
    }

    private fun assertWithin(operation: String, figures: List<Long>, thresholdMs: Long) {
        val median = figures.sorted()[figures.size / 2]
        println("BENCH $operation: median $median ms of ${figures.size} runs (threshold $thresholdMs ms), all: $figures")
        assertTrue("$operation: median $median ms over $thresholdMs ms ($figures)", median <= thresholdMs)
    }

    /** A recipe at DOM-012's caps: 50 ingredients of the dictionary in three kinds of unit, 50 steps of some length. */
    private fun capsRecipe(n: Int, ids: List<Long>? = null): Recipe = Recipe.create(now, null).copy(
        title = "Caps $n ${word(n * 13 + 5, EN)} soup",
        servings = 4,
        ingredients = (0 until CAP).map { i ->
            val entry = if (ids == null) ((n * CAP + i) * 97L % ENTRIES) + 1 else ids[(n * CAP + i) * 97 % ids.size]
            when (i % 3) {
                0 -> Ingredient(i, entry, unitType = UnitType.EXACT, unit = QuantityUnit.GRAM, quantity = Rational.of(100L + i * 10))
                1 -> Ingredient(i, entry, unitType = UnitType.PIECE, unit = QuantityUnit.PIECE, quantity = Rational.of(3), divisible = i % 2 == 0)
                else -> Ingredient(i, entry, unitType = UnitType.HOUSEHOLD, unit = QuantityUnit.TEASPOON, quantity = Rational.ONE)
            }
        },
        steps = (0 until CAP).map { j ->
            Step(j, "Step ${j + 1}: " + (0 until 40).joinToString(" ") { word(n * 1000 + j * 40 + it, EN) } + ".")
        },
    )

    private companion object {
        const val CAP = 50
        const val ENTRIES = 5_000
        const val BOOK = 200
        const val WARM_UP = 3
        const val RUNS = 11

        const val OPEN_MS = 1_000L
        const val RECALCULATION_MS = 200L
        const val SEARCH_MS = 2_000L
        const val OFFER_MS = 50L
        const val SEED_MS = 2_000L

        val RU = listOf("ка", "ро", "ми", "ле", "ту", "со", "ва", "ни", "пе", "за", "бу", "ги")
        val EN = listOf("ka", "ro", "mi", "le", "tu", "so", "va", "ni", "pe", "za", "bu", "gi")

        /** A made-up word of three syllables, the same for the same [n]. */
        fun word(n: Int, syllables: List<String>): String {
            var rest = n
            return (0 until 3).joinToString("") { syllables[rest % syllables.size].also { rest /= syllables.size } }
        }

        /** The dictionary the application ships; unit tests of `:app` run with the module directory as the working directory. */
        val SHIPPED: DictionariesDto by lazy { DictionarySnapshot.parse(File("src/main/assets/${DictionarySnapshot.ASSET}").readText()) }

        /** 5 000 entries of two words each, in tens: a parent and the nine leaves under it; and 100 tags. */
        val DICTIONARY = DictionariesDto(
            version = "bench-5000",
            ingredients = (1..ENTRIES.toLong()).map { id ->
                val n = id.toInt()
                IngredientEntryDto(
                    id = id,
                    labelRu = "${word(n, RU)} ${word(n * 7 + 3, RU)}",
                    labelEn = "${word(n, EN)} ${word(n * 7 + 3, EN)}",
                    parentId = if (id % 10 == 1L) null else id - (id - 1) % 10,
                )
            },
            tags = (1..100L).map { TagDto(id = 10_000 + it, labelRu = word(it.toInt(), RU), labelEn = word(it.toInt(), EN)) },
        )
    }
}
