package app.recipefork.app.card

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.recipefork.app.EnglishInterface
import app.recipefork.app.MainActivity
import app.recipefork.app.RecipeForkApplication
import app.recipefork.app.library.LibraryTags
import app.recipefork.app.settings.SettingsTags
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.IngredientReference
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.UnitSystem
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.Rational
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * v0.35 on a real device, in the whole application: a recipe's card follows the units setting (scenarios 1–3). Written
 * in cups and ounces with entries of the dictionary the application ships, beside a free-text line in grams and a step
 * naming it, it reads in grams and millilitres under Metric — "≈" where a cup went through the entry's measure — and
 * as written, the grams in ounces, under US; the open card turns as the setting changes, and the setting taken in
 * Settings → Units is the one the card shows next. The device's own setting is given back after.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class UnitsOnDeviceTest {
    /** The test reads the application's words in English, whatever the device speaks. */
    @get:Rule
    val english = EnglishInterface()

    @get:Rule
    val compose = createEmptyComposeRule()

    private val application = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as RecipeForkApplication
    private val container = application.container
    private var before: UnitSystem? = null
    private var recipe: Recipe? = null
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() {
        // The emulator's region is the United States, so the device starts on US: the test starts on Metric itself.
        before = container.unitSystem.system.value
        container.unitSystem.set(UnitSystem.METRIC)
    }

    @After
    fun tearDown() = runBlocking {
        scenario?.close()
        recipe?.let { container.recipes.delete(it.id) }
        before?.let(container.unitSystem::set)
        Unit
    }

    private fun row(position: Int, entry: Long, amount: Long, unit: QuantityUnit) =
        Ingredient(position, entry, unitType = UnitType.EXACT, unit = unit, quantity = Rational.of(amount))

    /** Scenario 2's flour, butter and milk, by the shipped dictionary's entries, and beets as free text; for 4. */
    private fun scones(): Recipe = Recipe.create(Instant.now().minusSeconds(3600), null, null, RecipeId.mint()).copy(
        // A title of its own each run, so that nothing an earlier run left in the book is taken for it.
        title = "Units scones ${System.currentTimeMillis() % 100_000}",
        servings = 4,
        ingredients = listOf(
            row(0, FLOUR, 2, QuantityUnit.CUP),
            row(1, BUTTER, 4, QuantityUnit.OUNCE),
            row(2, MILK, 1, QuantityUnit.CUP),
            Ingredient(3, null, freeTextName = "Beets", unitType = UnitType.EXACT, unit = QuantityUnit.GRAM, quantity = Rational.of(500)),
        ),
        steps = listOf(Step(0, "Brown .", listOf(IngredientReference(3, 6)))),
    )

    /** The texts each of [nodes] shows, merged as a reader hears it, the spaces that never break read as plain ones. */
    private fun texts(nodes: SemanticsNodeInteractionCollection): List<List<String>> = nodes.fetchSemanticsNodes().map { node ->
        node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text.replace('\u00A0', ' ').replace('\u202F', ' ') }
    }

    /** The amounts the card's lines show, each line once whichever of its tabs is laid out; null while any line is missing. */
    private fun amounts(): List<String>? = (0..3).map { position ->
        texts(compose.onAllNodes(hasTestTag(CardTags.ingredient(position)))).map { it.getOrNull(1) }.distinct().singleOrNull() ?: return null
    }

    /** The step's text as the card shows it, its reference read in: once for each of its tabs laid out. */
    private fun steps(): List<String> = texts(compose.onAllNodes(hasTestTag(StepTags.text(0)), useUnmergedTree = true)).map { it.joinToString("") }

    /** Waits until the card shows [amounts] and its step begins with [step] (a rounded reference carries its arrow after). */
    private fun awaitCard(amounts: List<String>, step: String) {
        try {
            compose.waitUntil(PATIENCE_MS) { amounts() == amounts && steps().let { shown -> shown.isNotEmpty() && shown.all { it.startsWith(step) } } }
        } catch (e: Throwable) {
            throw AssertionError("the card shows ${amounts()} and the steps ${steps()}, not $amounts and a step beginning “$step”", e)
        }
    }

    private fun clickText(text: String) {
        compose.waitUntil(PATIENCE_MS) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText(text)[0].performClick()
    }

    /** From the book, the card of [written], on its View tab. */
    private fun openCard(written: Recipe) {
        clickText(WRITTEN_HERE)
        clickText(written.title)
        compose.waitUntilAtLeastOneExists(hasTestTag(CardTags.tab(CardMode.VIEW)), PATIENCE_MS)
        compose.onNodeWithTag(CardTags.tab(CardMode.VIEW)).performClick()
    }

    @Test
    fun theCardReadsInTheChosenSystemAndTurnsAsTheSettingChanges() {
        // The shipped dictionary's measures the expected amounts are reckoned by (grams per cup; grams per ml of milk).
        val entries = runBlocking { container.dictionaries.ingredients() }.associateBy { it.id }
        assertEquals(mapOf(FLOUR to 130.0, BUTTER to 227.0, MILK to 258.0), listOf(FLOUR, BUTTER, MILK).associateWith { entries.getValue(it).factors["cup"] })
        assertEquals(1.03, entries.getValue(MILK).factors["ml"])

        val written = scones().also { recipe = it }
        runBlocking { container.recipes.save(written) }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        openCard(written)

        // Metric (scenario 2): the cups through the entries' measures, "≈"; the ounces exactly; the grams as written.
        awaitCard(METRIC_AMOUNTS, "Brown 500 g")
        // The open card turns as the setting changes (scenario 1): under US the cups and ounces read as written and the
        // grams in ounces (scenario 3), the step's reference with its line.
        container.unitSystem.set(UnitSystem.US)
        awaitCard(US_AMOUNTS, "Brown 17½ oz")
        container.unitSystem.set(UnitSystem.METRIC)
        awaitCard(METRIC_AMOUNTS, "Brown 500 g")

        // Through Settings → Units, as a reader switches it: the card opened next reads in the system taken.
        for ((system, amounts, step) in listOf(Triple(UnitSystem.US, US_AMOUNTS, "Brown 17½ oz"), Triple(UnitSystem.METRIC, METRIC_AMOUNTS, "Brown 500 g"))) {
            compose.onNodeWithTag(CardTags.BACK).performClick()
            compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.SETTINGS), PATIENCE_MS)
            compose.onNodeWithTag(LibraryTags.SETTINGS).performClick()
            compose.waitUntilExactlyOneExists(hasTestTag(SettingsTags.UNITS), PATIENCE_MS)
            compose.onNodeWithTag(SettingsTags.UNITS).performScrollTo().performClick()
            compose.waitUntilExactlyOneExists(hasTestTag(SettingsTags.units(system)), PATIENCE_MS)
            compose.onNodeWithTag(SettingsTags.units(system)).performClick()
            compose.waitUntilDoesNotExist(hasTestTag(SettingsTags.UNITS_DIALOG), PATIENCE_MS)
            assertEquals(system, container.unitSystem.system.value)
            compose.onNodeWithTag(SettingsTags.BACK).performClick()
            compose.waitUntilDoesNotExist(hasTestTag(SettingsTags.UNITS), PATIENCE_MS)
            openCard(written)
            awaitCard(amounts, step)
        }
    }

    private companion object {
        const val PATIENCE_MS = 30_000L
        const val WRITTEN_HERE = "My recipes"
        /** The shipped dictionary's flour, butter and milk. */
        const val FLOUR = 5000L
        const val BUTTER = 4600L
        const val MILK = 4450L
        val METRIC_AMOUNTS = listOf("≈ 260 g", "113 g", "≈ 250 ml", "500 g")
        val US_AMOUNTS = listOf("2 cup", "4 oz", "1 cup", "17½ oz")
    }
}
