package app.recipefork.app.card

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.editor.SAMPLE_DICTIONARIES
import app.recipefork.app.editor.TestDevice
import app.recipefork.app.editor.textsOf
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.ui.AccessibilityChecks
import app.recipefork.app.ui.Haptic
import app.recipefork.app.ui.LocalHaptics
import app.recipefork.app.ui.RecordingHaptics
import app.recipefork.app.ui.LocalizationChecks
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.Rational
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * The serving count control on the card (SCALE-001, SCALE-003, SCALE-005, SCALE-010, SCALE-011, FLOW-017 (2, 5)):
 * − and + walk the requested count, and every quantity is recomputed from it.
 */
@RunWith(AndroidJUnit4::class)
class CardServingsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val device = TestDevice(context)
    private val recipes = RecipeRepository(ActiveBook(BookName.Pool, book.database))
    private val environment = CardEnvironment { device.database.dictionaryDao().ingredients() }
    private val now = Instant.parse("2026-10-01T12:00:00Z")
    private val haptics = RecordingHaptics()

    @Before
    fun setUp() {
        compose.registerIdlingResource(book.idling)
        compose.registerIdlingResource(device.idling)
        runBlocking { device.database.dictionaryDao().replace(SAMPLE_DICTIONARIES) }
    }

    @After
    fun close() {
        compose.unregisterIdlingResource(book.idling)
        compose.unregisterIdlingResource(device.idling)
        book.close()
        device.close()
    }

    /** A recipe for 4, with a row left unfinished and one off its grid among those that scale. */
    private fun forFour(servings: Int? = 4): Recipe = Recipe.create(now, null).copy(
        title = "Omelette",
        servings = servings,
        ingredients = listOf(
            Ingredient(0, null, freeTextName = "Chicken", unitType = UnitType.EXACT, unit = QuantityUnit.GRAM, quantity = Rational.of(500)),
            Ingredient(1, null, freeTextName = "Eggs", unitType = UnitType.PIECE, unit = QuantityUnit.PIECE, quantity = Rational.of(3), divisible = false),
            Ingredient(2, null, freeTextName = "Salt", unitType = UnitType.HOUSEHOLD, unit = QuantityUnit.TEASPOON, quantity = Rational.ONE),
            Ingredient(3, null, freeTextName = "Flour", unitType = UnitType.EXACT, unit = QuantityUnit.GRAM, quantity = null),
            Ingredient(4, null, freeTextName = "Sugar", unitType = UnitType.HOUSEHOLD, unit = QuantityUnit.TEASPOON, quantity = Rational.of(3, 10)),
        ),
    )

    private fun save(recipe: Recipe): Recipe = recipe.also { runBlocking { recipes.save(it) } }

    private var shown by mutableStateOf<RecipeId?>(null)
    private var opened by mutableStateOf(0)
    private var composed = false

    /** Opens the card on [id]; every call opens it anew, as leaving and coming back does. */
    private fun show(id: RecipeId) {
        if (!composed) {
            composed = true
            compose.setContent {
                CompositionLocalProvider(LocalHaptics provides haptics) {
                    MaterialTheme {
                        shown?.let { key(it, opened) { CardScreen(it, recipes, environment, onBack = {}, onEdit = {}) } }
                    }
                }
            }
        }
        compose.runOnIdle { shown = id; opened++ }
        compose.waitForIdle()
        compose.awaitCard()
    }

    private fun servings() = compose.textsOf(CardTags.SERVINGS).single()
    private fun quantities() = (0..4).map { compose.textsOf(CardTags.ingredient(it)).getOrNull(1) }
    private fun more(times: Int = 1) = repeat(times) { compose.onNodeWithTag(CardTags.SERVINGS_MORE).performClick() }
    private fun fewer(times: Int = 1) = repeat(times) { compose.onNodeWithTag(CardTags.SERVINGS_FEWER).performClick() }

    private val authored = listOf("500 g", "3 pc", "1 tsp", "g", "tsp")

    // FLOW-017 (2): the card opens at the authored count, every quantity as authored.
    @Test
    fun theCardOpensAtTheAuthoredCountWithEveryQuantityAsAuthored() {
        show(save(forFour()).id)
        assertEquals("Servings: 4", servings())
        assertEquals(authored, quantities())
    }

    // Scenario 4, SCALE-001, SCALE-003: 4 → 6, then 4 → 1.
    @Test
    fun changingTheCountRecomputesEveryQuantity() {
        show(save(forFour()).id)
        more(2)
        assertEquals("Servings: 6", servings())
        // The unfinished and the off-grid rows stay as they are; the others scale.
        assertEquals(listOf("750 g", "5 pc", "1½ tsp", "g", "tsp"), quantities())
        fewer(5)
        assertEquals("Servings: 1", servings())
        assertEquals(listOf("125 g", "1 pc", "¼ tsp", "g", "tsp"), quantities())
    }

    // SCALE-011 (1–4), SCALE-010, SCALE-005: the walk below one, the floor at ¼, the count as requested, no zero.
    @Test
    fun theControlWalksTheFractionsBelowOneAndStopsAtAQuarter() {
        show(save(forFour()).id)
        fewer(3)
        val walk = mutableListOf(servings())
        repeat(5) {
            fewer()
            walk += servings()
            quantities().filterNotNull().forEach { assertFalse("${walk.last()}: $it", it.startsWith("0")) }
        }
        assertEquals(listOf("Servings: 1", "Servings: ¾", "Servings: ⅔", "Servings: ½", "Servings: ⅓", "Servings: ¼"), walk)
        // ¼ of a serving is the exact factor 1/16: 500 g → 31.25 g → 31 g; eggs and salt at their floors.
        assertEquals(listOf("31 g", "1 pc", "¼ tsp", "g", "tsp"), quantities())
        compose.onNodeWithTag(CardTags.SERVINGS_FEWER).assertIsNotEnabled()
        fewer()
        assertEquals("Servings: ¼", servings())
        more()
        assertEquals("Servings: ⅓", servings())
        // ⅓ of a serving is the exact factor 1/12: 500 g → 41⅔ g → 42 g.
        assertEquals(listOf("42 g", "1 pc", "¼ tsp", "g", "tsp"), quantities())
        compose.onNodeWithTag(CardTags.SERVINGS_FEWER).assertIsEnabled()
        // Never marked approximate, in any form.
        assertFalse(servings().contains("≈") || servings().contains("~"))
    }

    // Each step of the count, − or +, gives one light tick; a − refused at ¼ gives none.
    @Test
    fun eachStepOfTheCountTicksAndARefusedOneDoesNot() {
        show(save(forFour()).id)
        assertEquals(emptyList<Haptic>(), haptics.all)
        more()
        assertEquals(listOf(Haptic.TICK), haptics.all)
        fewer()
        assertEquals(List(2) { Haptic.TICK }, haptics.all)
        fewer(8)
        assertEquals("Servings: ¼", servings())
        assertEquals(List(10) { Haptic.TICK }, haptics.all)
        fewer()
        assertEquals("Servings: ¼", servings())
        assertEquals(List(10) { Haptic.TICK }, haptics.all)
    }

    // Each half has one click: exactly one OnClick action in the half, carrying its label, and a touch and the screen
    // reader's click are that same action — one step, one tick each — and the − at ¼ is disabled.
    @Test
    fun aTouchAndTheScreenReadersClickAreTheSameOneAction() {
        show(save(forFour()).id)
        listOf(
            CardTags.SERVINGS_FEWER to "Recalculate the recipe for fewer servings",
            CardTags.SERVINGS_MORE to "Recalculate the recipe for more servings",
        ).forEach { (tag, label) ->
            val clicks = compose.onAllNodes(
                (hasTestTag(tag) or hasAnyAncestor(hasTestTag(tag))) and SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick),
                useUnmergedTree = true,
            ).fetchSemanticsNodes()
            assertEquals("$tag's click actions", 1, clicks.size)
            val click = clicks.single().config[SemanticsActions.OnClick]
            assertEquals(label, click.label)
            assertNotNull("$tag's click acts", click.action)
            // The one action is the button's own click: a click set in the half's semantics would merge into the same
            // key and replace it with a lambda of this application's.
            assertTrue(
                "$tag's click is the button's own, not a semantics click",
                click.action!!.javaClass.name.startsWith("androidx.compose.foundation."),
            )
        }
        compose.onNodeWithTag(CardTags.SERVINGS_MORE).performTouchInput { click() }
        assertEquals("Servings: 5", servings())
        compose.onNodeWithTag(CardTags.SERVINGS_MORE).performSemanticsAction(SemanticsActions.OnClick)
        assertEquals("Servings: 6", servings())
        compose.onNodeWithTag(CardTags.SERVINGS_FEWER).performTouchInput { click() }
        assertEquals("Servings: 5", servings())
        compose.onNodeWithTag(CardTags.SERVINGS_FEWER).performSemanticsAction(SemanticsActions.OnClick)
        assertEquals("Servings: 4", servings())
        assertEquals(List(4) { Haptic.TICK }, haptics.all)
        fewer(8)
        assertEquals("Servings: ¼", servings())
        // Disabled, which the screen reader announces and acts on no further; a touch does nothing.
        compose.onNodeWithTag(CardTags.SERVINGS_FEWER).assertIsNotEnabled().performTouchInput { click() }
        assertEquals("Servings: ¼", servings())
        assertEquals(List(12) { Haptic.TICK }, haptics.all)
    }

    // SCALE-010: no ceiling.
    @Test
    fun thereIsNoCeiling() {
        show(save(forFour()).id)
        more(40)
        assertEquals("Servings: 44", servings())
        assertEquals(listOf("5.5 kg", "33 pc", "11 tsp", "g", "tsp"), quantities())
        compose.onNodeWithTag(CardTags.SERVINGS_MORE).assertIsEnabled()
    }

    // FLOW-017 (5): back at the authored count, the authored values exactly.
    @Test
    fun returningToTheAuthoredCountShowsTheAuthoredValues() {
        show(save(forFour()).id)
        fewer(6)
        more(6)
        assertEquals("Servings: 4", servings())
        assertEquals(authored, quantities())
    }

    // A recipe with no serving count: no control, and its quantities as authored.
    @Test
    fun aRecipeWithNoServingCountShowsNoControl() {
        show(save(forFour(servings = null)).id)
        compose.onNodeWithTag(CardTags.SERVINGS).assertDoesNotExist()
        compose.onNodeWithTag(CardTags.SERVINGS_FEWER).assertDoesNotExist()
        compose.onNodeWithTag(CardTags.SERVINGS_MORE).assertDoesNotExist()
        assertEquals(authored, quantities())
    }

    // Each time the card is opened, it starts at the authored count.
    @Test
    fun theCardOpensAtTheAuthoredCountEachTime() {
        val recipe = save(forFour())
        show(recipe.id)
        more(2)
        assertEquals("Servings: 6", servings())
        show(recipe.id)
        assertEquals("Servings: 4", servings())
        assertEquals(authored, quantities())
    }

    // A11Y-003 (1), A11Y-005 (1): both actions are the card's 56 dp and say what they do, in English and Russian.
    @Test
    fun bothActionsAreLargeEnoughAndSayWhatTheyDo() = announced(
        "Fewer servings", "Recalculate the recipe for fewer servings",
        "More servings", "Recalculate the recipe for more servings", "Servings: ¾",
    )

    @Test
    @Config(qualifiers = "ru")
    fun bothActionsAreLargeEnoughAndSayWhatTheyDoInRussian() = announced(
        "Меньше порций", "Пересчитать рецепт на меньшее число порций",
        "Больше порций", "Пересчитать рецепт на большее число порций", "Порций: ¾",
    )

    private fun announced(fewerName: String, fewerAction: String, moreName: String, moreAction: String, threeQuarters: String) {
        val recipe = save(forFour())
        show(recipe.id)
        listOf(CardTags.SERVINGS_FEWER to (fewerName to fewerAction), CardTags.SERVINGS_MORE to (moreName to moreAction))
            .forEach { (tag, words) ->
                val node = compose.onNodeWithTag(tag).fetchSemanticsNode()
                val density = node.layoutInfo.density.density
                assertEquals(56f, node.size.width / density, 0.5f)
                assertEquals(56f, node.size.height / density, 0.5f)
                assertEquals(listOf(words.first), node.config[SemanticsProperties.ContentDescription])
                assertEquals(words.second, AccessibilityChecks.clickActionLabel(node))
            }
        AccessibilityChecks.assertTouchTargets(compose, expectAtLeast = 4)
        AccessibilityChecks.assertInteractiveElementsLabelled(compose, expectAtLeast = 4)
        fewer(4)
        assertEquals(threeQuarters, servings())
        val content = setOf(recipe.title) + recipe.ingredients.mapNotNull { it.freeTextName } +
            quantities().filterNotNull()
        LocalizationChecks.assertScreenTextFromResources(compose, context, content)
        LocalizationChecks.assertScreenTextWrittenInItsLanguage(compose, context, content)
    }
}
