package app.recipefork.app.card

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.data.HeldLastCardTab
import app.recipefork.app.dictionary.IngredientEntryEntity
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.editor.textsOf
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.api.NutritionDto
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeState
import app.recipefork.core.recipe.SentStatus
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.Rational
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * v0.44, T-320 — per 100 g of the finished dish on the card (scenarios 2–4; decisions-recipe-fork.md, 2026-10-22,
 * T-319 / T-320, decisions 2 and 3):
 *
 * - The switch is «100 г | Порция | Всё блюдо» with a weight and a serving count, «Порция | Всё блюдо» without a weight,
 *   «100 г | Всё блюдо» with a weight and no count, and none without either; it opens on «Порция», else «Всё блюдо».
 * - «100 г» shows the tiles per 100 g of the finished dish, the same at any serving count, grouped by digits, and said
 *   as «На 100 граммов готового блюда: …» with plain numbers.
 * - The sheet names the weight per 100 g rests on at the counter's servings; without a weight, on a recipe the reader
 *   may edit, where to give it, and on one they may not, nothing.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "en-w360dp-h640dp")
class CardNutritionPer100gTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val recipes = RecipeRepository(ActiveBook(BookName.Pool, book.database))
    private val now = Instant.parse("2026-10-22T12:00:00Z")

    // Per 100 g. 360 g of cottage cheese and 60 g of flour make 790.8 kcal, 66.12 g protein, 33 g fat, 52.8 g
    // carbohydrates. Weighing 560 g, 100 g of the dish hold 141.2 kcal, 11.8 g, 5.9 g, 9.4 g.
    private val entries = listOf(
        IngredientEntryEntity(1, "творог", "cottage cheese", null, nutrition = NutritionDto(159.0, 16.7, 9.0, 2.0)),
        IngredientEntryEntity(2, "мука", "flour", null, nutrition = NutritionDto(364.0, 10.0, 1.0, 76.0)),
    )

    private fun grams(position: Int, entry: Long, amount: Long) =
        Ingredient(position, entry, unitType = UnitType.EXACT, unit = QuantityUnit.GRAM, quantity = Rational.of(amount))

    private fun cheesecakes(servings: Int? = 2, weight: Int? = 560) = Recipe.create(now, null).copy(
        title = "Syrniki",
        servings = servings,
        finishedWeightGrams = weight,
        ingredients = listOf(grams(0, 1, 360), grams(1, 2, 60)),
        steps = listOf(Step(0, "Mix and fry.")),
    )

    /** [recipe] as one the reader may not edit: published. */
    private fun frozen(recipe: Recipe) = recipe.copy(sent = SentStatus.SENT).inState(RecipeState.PUBLISHED)

    @Before
    fun setUp() = compose.registerIdlingResource(book.idling)

    @After
    fun close() {
        compose.unregisterIdlingResource(book.idling)
        book.close()
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun open(recipe: Recipe = cheesecakes()) {
        runBlocking { recipes.save(recipe) }
        val environment = CardEnvironment(lastTab = HeldLastCardTab(CardMode.VIEW), ingredients = IngredientEntries { entries })
        compose.setContent {
            MaterialTheme {
                CardScreen(recipe.id, recipes, environment, onBack = {}, onEdit = {}, session = TextCardSession.inMemory())
            }
        }
        compose.awaitCard()
        compose.waitUntil(PATIENCE_MS) { exists(CardTags.step(0)) }
        compose.waitForIdle()
    }

    private fun spoken() = compose.onNodeWithTag(NutritionTags.TILES).fetchSemanticsNode().config[SemanticsProperties.ContentDescription].single()
    private fun shown() = compose.onNodeWithTag(NutritionTags.TILES).fetchSemanticsNode().config[ShownText].replace('\u00A0', ' ').split(TILES_SHOWN_APART)

    private fun choose(tag: String) {
        compose.scrollClearOfFork(tag)
        compose.onNodeWithTag(tag).performClick()
        compose.waitForIdle()
    }

    private fun more(times: Int) = repeat(times) {
        compose.onNodeWithTag(CardTags.SERVINGS_MORE).performScrollTo().performClick()
        compose.waitForIdle()
    }

    private fun openDetails() {
        compose.scrollClearOfFork(NutritionTags.DETAILS)
        compose.onNodeWithTag(NutritionTags.DETAILS).performClick()
        compose.waitUntil(PATIENCE_MS) { exists(NutritionTags.HOW) }
        compose.waitForIdle()
    }

    /** Fixes the cottage cheese at 720 g, twice the authored 360 g — the dish for about 4 — on Cooking, and comes back. */
    private fun fixTheCheese() {
        compose.chooseCardTab(CardMode.COOKING)
        compose.onNodeWithTag(CardTags.fixAmount(0), useUnmergedTree = true).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { exists(CardTags.FIX) }
        compose.onNodeWithTag(CardTags.FIX_FIELD).performTextReplacement("720")
        compose.waitForIdle()
        compose.onNodeWithTag(CardTags.FIX_CONFIRM).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { !exists(CardTags.FIX) }
        compose.chooseCardTab(CardMode.VIEW)
    }

    /** The switch's segments that stand, start to end, by their tags. */
    private fun segments(): List<String> =
        listOf(NutritionTags.SWITCH_PER_100_G, NutritionTags.SWITCH_PER_SERVING, NutritionTags.SWITCH_WHOLE).filter(::exists)
            .sortedBy { compose.onNodeWithTag(it).fetchSemanticsNode().positionInRoot.x }

    private fun text(tag: String) = compose.textsOf(tag).single().replace(' ', ' ')

    @Test
    fun withAWeightAndAServingCountTheSwitchIs100GServingWholeDishOpeningOnServing() {
        open()
        assertEquals(listOf(NutritionTags.SWITCH_PER_100_G, NutritionTags.SWITCH_PER_SERVING, NutritionTags.SWITCH_WHOLE), segments())
        assertEquals("100 g", text(NutritionTags.SWITCH_PER_100_G))
        assertEquals("Serving", text(NutritionTags.SWITCH_PER_SERVING))
        assertEquals("Whole dish", text(NutritionTags.SWITCH_WHOLE))
        compose.onNodeWithTag(NutritionTags.SWITCH_PER_SERVING).assertIsSelected()
        compose.onNodeWithTag(NutritionTags.SWITCH_PER_100_G).assertIsNotSelected()
        compose.onNodeWithTag(NutritionTags.SWITCH_WHOLE).assertIsNotSelected()
        // 790.8 / 2 = 395.4 kcal, 33.06 g, 16.5 g, 26.4 g.
        assertEquals(listOf("400 kcal", "33 g protein", "17 g fat", "26 g carbs"), shown())
        assertEquals("Per serving: about 400 kilocalories, protein 33 grams, fat 17 grams, carbs 26 grams", spoken())
    }

    @Test
    fun withoutAWeightTheSwitchIsServingWholeDish() {
        open(cheesecakes(weight = null))
        assertEquals(listOf(NutritionTags.SWITCH_PER_SERVING, NutritionTags.SWITCH_WHOLE), segments())
        compose.onNodeWithTag(NutritionTags.SWITCH_PER_SERVING).assertIsSelected()
        assertFalse(exists(NutritionTags.SWITCH_PER_100_G))
    }

    @Test
    fun withAWeightAndNoServingCountTheSwitchIs100GWholeDishOpeningOnWholeDish() {
        open(cheesecakes(servings = null))
        assertEquals(listOf(NutritionTags.SWITCH_PER_100_G, NutritionTags.SWITCH_WHOLE), segments())
        assertFalse(exists(NutritionTags.WHOLE_ONLY))
        compose.onNodeWithTag(NutritionTags.SWITCH_WHOLE).assertIsSelected()
        assertEquals(listOf("790 kcal", "66 g protein", "33 g fat", "53 g carbs"), shown())
        assertEquals("Whole dish: about 790 kilocalories, protein 66 grams, fat 33 grams, carbs 53 grams", spoken())
        choose(NutritionTags.SWITCH_PER_100_G)
        assertEquals(listOf("140 kcal", "12 g protein", "6 g fat", "9 g carbs"), shown())
    }

    @Test
    fun withNeitherThereIsNoSwitchButTheWholeDishAlone() {
        open(cheesecakes(servings = null, weight = null))
        assertFalse(exists(NutritionTags.SWITCH))
        assertEquals(listOf("Whole dish"), compose.textsOf(NutritionTags.WHOLE_ONLY))
    }

    @Test
    fun theThreeSegmentsAreRadiosEachWithTheCardsTarget() {
        open()
        compose.scrollClearOfFork(NutritionTags.SWITCH)
        val widths = segments().map { tag ->
            val node = compose.onNodeWithTag(tag).fetchSemanticsNode()
            assertEquals(Role.RadioButton, node.config.getOrNull(SemanticsProperties.Role))
            val density = node.layoutInfo.density.density
            assertTrue("$tag is ${node.touchBoundsInRoot.height / density} dp to touch", node.touchBoundsInRoot.height / density + 0.5f >= CardTarget.value)
            node.size.width
        }
        assertTrue("equal segments: $widths", widths.max() - widths.min() <= 1)
    }

    @Test
    fun at100GTheTilesShowPer100GOfTheFinishedDishTheSameAtAnyServingCount() {
        open()
        choose(NutritionTags.SWITCH_PER_100_G)
        compose.onNodeWithTag(NutritionTags.SWITCH_PER_100_G).assertIsSelected()
        assertEquals(listOf("140 kcal", "12 g protein", "6 g fat", "9 g carbs"), shown())
        assertEquals("Per 100 grams of the finished dish: about 140 kilocalories, protein 12 grams, fat 6 grams, carbs 9 grams", spoken())
        more(2)
        assertEquals(listOf("Servings: 4"), compose.textsOf(CardTags.SERVINGS))
        assertEquals(listOf("140 kcal", "12 g protein", "6 g fat", "9 g carbs"), shown())
        fixTheCheese()
        compose.onNodeWithTag(NutritionTags.SWITCH_PER_100_G).assertIsSelected()
        assertEquals(listOf("140 kcal", "12 g protein", "6 g fat", "9 g carbs"), shown())
        choose(NutritionTags.SWITCH_WHOLE)
        assertEquals(listOf("1,580 kcal", "132 g protein", "66 g fat", "106 g carbs"), shown())
    }

    @Test
    fun at100GTheTilesGroupDigitsAndTheScreenReaderSaysPlainNumbers() {
        // Weighing 10 g: 7 908 kcal, 661.2 g, 330 g, 528 g in 100 g.
        open(cheesecakes(weight = 10))
        choose(NutritionTags.SWITCH_PER_100_G)
        assertEquals(listOf("7,910 kcal", "661 g protein", "330 g fat", "528 g carbs"), shown())
        assertEquals("Per 100 grams of the finished dish: about 7910 kilocalories, protein 661 grams, fat 330 grams, carbs 528 grams", spoken())
    }

    @Test
    fun theChoiceOf100GIsKeptWhileTheCardStaysOpen() {
        open()
        choose(NutritionTags.SWITCH_PER_100_G)
        compose.chooseCardTab(CardMode.COOKING)
        compose.chooseCardTab(CardMode.VIEW)
        compose.onNodeWithTag(NutritionTags.SWITCH_PER_100_G).assertIsSelected()
        assertEquals("140 kcal", shown().first())
    }

    @Test
    fun aWeightRemovedWhileOn100GTakesTheSwitchBackToServing() {
        val recipe = cheesecakes()
        open(recipe)
        choose(NutritionTags.SWITCH_PER_100_G)
        runBlocking { recipes.save(recipe.copy(finishedWeightGrams = null)) }
        compose.waitUntil(PATIENCE_MS) { !exists(NutritionTags.SWITCH_PER_100_G) }
        compose.waitForIdle()
        compose.onNodeWithTag(NutritionTags.SWITCH_PER_SERVING).assertIsSelected()
        assertEquals("400 kcal", shown().first())
        // Given again, the weight brings the segment back, the switch staying where it opens.
        runBlocking { recipes.save(recipe) }
        compose.waitUntil(PATIENCE_MS) { exists(NutritionTags.SWITCH_PER_100_G) }
        compose.waitForIdle()
        compose.onNodeWithTag(NutritionTags.SWITCH_PER_SERVING).assertIsSelected()
    }

    @Test
    fun detailsNamesTheWeightAtTheCountersServingsAfterHowItIsCounted() {
        open()
        more(2)
        openDetails()
        assertEquals("Per 100 g is by the finished weight the recipe gives: 1,120 g for 4 servings.", text(NutritionTags.WEIGHT))
        assertEquals(listOf(NutritionTags.HOW, NutritionTags.WEIGHT), compose.tagsTopToBottom(setOf(NutritionTags.HOW, NutritionTags.WEIGHT)))
        assertFalse(exists(NutritionTags.NO_WEIGHT))
    }

    @Test
    fun detailsNamesTheWeightOfAFixedAmountForItsApproximateServings() {
        open()
        fixTheCheese()
        openDetails()
        assertEquals("Per 100 g is by the finished weight the recipe gives: 1,120 g for ≈4 servings.", text(NutritionTags.WEIGHT))
    }

    @Test
    fun detailsNamesTheWeightAloneWithNoServingCount() {
        open(cheesecakes(servings = null))
        openDetails()
        assertEquals("Per 100 g is by the finished weight the recipe gives: 560 g.", text(NutritionTags.WEIGHT))
    }

    @Test
    fun withoutAWeightDetailsSaysWhereToGiveItOnARecipeTheReaderMayEdit() {
        open(cheesecakes(weight = null))
        openDetails()
        assertEquals("To see per 100 g, give the finished dish weight in the recipe.", text(NutritionTags.NO_WEIGHT))
        assertFalse(exists(NutritionTags.WEIGHT))
        assertEquals(listOf(NutritionTags.HOW, NutritionTags.NO_WEIGHT), compose.tagsTopToBottom(setOf(NutritionTags.HOW, NutritionTags.NO_WEIGHT)))
    }

    @Test
    fun withoutAWeightDetailsSaysNothingOfItOnARecipeTheReaderMayNotEdit() {
        open(frozen(cheesecakes(weight = null)))
        openDetails()
        assertFalse(exists(NutritionTags.NO_WEIGHT))
        assertFalse(exists(NutritionTags.WEIGHT))
    }

    @Test
    fun aRecipeTheReaderMayNotEditStillNamesItsWeight() {
        open(frozen(cheesecakes()))
        assertTrue(exists(NutritionTags.SWITCH_PER_100_G))
        openDetails()
        assertEquals("Per 100 g is by the finished weight the recipe gives: 560 g for 2 servings.", text(NutritionTags.WEIGHT))
    }

    @Test
    @Config(qualifiers = "ru-w360dp-h640dp")
    fun inRussianEverythingIsSaidAsDecided() {
        open()
        assertEquals("100 г", text(NutritionTags.SWITCH_PER_100_G))
        assertEquals("Порция", text(NutritionTags.SWITCH_PER_SERVING))
        assertEquals("Всё блюдо", text(NutritionTags.SWITCH_WHOLE))
        compose.onNodeWithTag(NutritionTags.SWITCH_PER_SERVING).assertIsSelected()
        assertEquals("На порцию: примерно 400 килокалорий, белки 33 грамма, жиры 17 граммов, углеводы 26 граммов", spoken())
        choose(NutritionTags.SWITCH_PER_100_G)
        assertEquals(listOf("140 ккал", "12 г белки", "6 г жиры", "9 г углеводы"), shown())
        assertEquals("На 100 граммов готового блюда: примерно 140 килокалорий, белки 12 граммов, жиры 6 граммов, углеводы 9 граммов", spoken())
        more(2)
        openDetails()
        assertEquals("На 100 г — по весу готового блюда из рецепта: 1 120 г на 4 порции.", text(NutritionTags.WEIGHT))
    }

    @Test
    @Config(qualifiers = "ru-w360dp-h640dp")
    fun inRussianAFixedAmountAndNoCountAreSaidAsDecided() {
        open()
        fixTheCheese()
        openDetails()
        assertEquals("На 100 г — по весу готового блюда из рецепта: 1 120 г на ≈4 порции.", text(NutritionTags.WEIGHT))
    }

    @Test
    @Config(qualifiers = "ru-w360dp-h640dp")
    fun inRussianWithNoCountTheWeightStandsAlone() {
        open(cheesecakes(servings = null))
        openDetails()
        assertEquals("На 100 г — по весу готового блюда из рецепта: 560 г.", text(NutritionTags.WEIGHT))
    }

    @Test
    @Config(qualifiers = "ru-w360dp-h640dp")
    fun inRussianWithoutAWeightDetailsSaysWhereToGiveIt() {
        open(cheesecakes(weight = null))
        openDetails()
        assertEquals("Чтобы видеть «на 100 г», укажите в рецепте вес готового блюда.", text(NutritionTags.NO_WEIGHT))
    }
}
