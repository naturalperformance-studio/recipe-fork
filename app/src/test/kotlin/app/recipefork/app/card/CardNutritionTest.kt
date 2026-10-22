package app.recipefork.app.card

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.geometry.Offset
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
 * v0.40, T-293 — nutrition on the recipe card (scenarios 1–3, 5–7; decisions-recipe-fork.md, 2026-10-22):
 *
 * - On View, between the ingredients and the steps, «Пищевая ценность, примерно» with the «Порция | Всё блюдо» switch
 *   opening on one serving (v0.44, T-320, relabelled), four tiles and "Details"; not on Cooking.
 * - One serving stays as the servings change; the whole dish follows them and a fixed amount.
 * - The rows left out are named on the card, and with their reasons in the sheet, free text and an entry the
 *   dictionary does not hold both as «нет в словаре»; the sheet gives each counted row's kcal for the whole dish and how
 *   it is counted.
 * - No section where nothing can be counted; the whole dish alone where the recipe has no serving count.
 * - The tiles are one phrase to the screen reader, in either language.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "en-w360dp-h640dp")
class CardNutritionTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val recipes = RecipeRepository(ActiveBook(BookName.Pool, book.database))
    private val now = Instant.parse("2026-10-22T12:00:00Z")

    // Per 100 g. 360 g of cottage cheese give 572.4 kcal, 60.12 g protein, 32.4 g fat, 7.2 g carbohydrates; 60 g of
    // flour 218.4 kcal, 6 g, 0.6 g, 45.6 g. The dish: 790.8 kcal, 66.12 g, 33 g, 52.8 g.
    private val entries = listOf(
        IngredientEntryEntity(1, "творог", "cottage cheese", null, nutrition = NutritionDto(159.0, 16.7, 9.0, 2.0)),
        IngredientEntryEntity(2, "мука", "flour", null, nutrition = NutritionDto(364.0, 10.0, 1.0, 76.0)),
        IngredientEntryEntity(3, "соль", "salt", null, nutrition = NutritionDto(0.0, 0.0, 0.0, 0.0)),
        IngredientEntryEntity(4, "сахар", "sugar", null, nutrition = NutritionDto(387.0, 0.0, 0.0, 100.0)),
        IngredientEntryEntity(5, "яйцо", "egg", null, factors = mapOf("pc" to 55.0), nutrition = NutritionDto(143.0, 12.6, 9.5, 0.7)),
        IngredientEntryEntity(6, "ваниль", "vanilla", null),
    )

    private fun grams(position: Int, entry: Long?, amount: Long?, free: String? = null) =
        Ingredient(position, entry, freeTextName = free, unitType = UnitType.EXACT, unit = QuantityUnit.GRAM, quantity = amount?.let { Rational.of(it) })

    private val counted = listOf(grams(0, 1, 360), grams(1, 2, 60))

    /** Every reason a row is left out, in turn: to taste, free text, no quantity, no measure, no data, no entry. */
    private val leftOut = listOf(
        Ingredient(2, 3, unitType = UnitType.TASTE_UNMEASURED, unit = null, quantity = null),
        grams(3, null, null, free = "Sour cream"),
        grams(4, 4, null),
        Ingredient(5, 5, unitType = UnitType.HOUSEHOLD, unit = QuantityUnit.TABLESPOON, quantity = Rational.ONE),
        grams(6, 6, 5),
        grams(7, 99, 10),
    )

    private fun cheesecakes(servings: Int? = 4, ingredients: List<Ingredient> = counted + leftOut) = Recipe.create(now, null).copy(
        title = "Syrniki",
        servings = servings,
        ingredients = ingredients,
        steps = listOf(Step(0, "Mix and fry.")),
    )

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
    private fun shown() = compose.onNodeWithTag(NutritionTags.TILES).fetchSemanticsNode().config[ShownText].split(TILES_SHOWN_APART)

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

    /**
     * Fixes the cottage cheese at 720 g, twice the authored 360 g — the dish for about 8 — on Cooking, where amounts are
     * fixed, and comes back to View.
     */
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

    /** The tags of [tags] in the order they stand in the content, top to bottom, wherever it is scrolled. */
    private fun inOrder(tags: Set<String>) = tags.sortedBy { top(it) }

    private fun top(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().positionInRoot.y
    private fun bottom(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().let { it.positionInRoot.y + it.size.height }

    private fun rowsOf(tags: List<String>) = tags.map { compose.textsOf(it) }

    @Test
    fun onViewTheSectionStandsBetweenTheIngredientsAndTheStepsWithItsHeadingSwitchTilesAndDetails() {
        open()
        assertTrue(bottom(CardTags.INGREDIENTS) <= top(NutritionTags.SECTION) && bottom(NutritionTags.SECTION) <= top(CardTags.STEPS))
        val heading = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading) and hasText("Nutrition, approximate") and
            hasAnyAncestor(hasTestTag(NutritionTags.SECTION))
        compose.onNode(heading, useUnmergedTree = true).assertExists()
        assertEquals(
            listOf(NutritionTags.SWITCH, NutritionTags.TILES, NutritionTags.LEFT_OUT, NutritionTags.DETAILS),
            inOrder(setOf(NutritionTags.SWITCH, NutritionTags.TILES, NutritionTags.LEFT_OUT, NutritionTags.DETAILS)),
        )
        assertEquals(listOf("Serving"), compose.textsOf(NutritionTags.SWITCH_PER_SERVING))
        assertEquals(listOf("Whole dish"), compose.textsOf(NutritionTags.SWITCH_WHOLE))
        assertEquals(listOf("Details"), compose.textsOf(NutritionTags.DETAILS))
    }

    @Test
    fun cookingShowsNoNutrition() {
        open()
        assertTrue(exists(NutritionTags.SECTION))
        compose.chooseCardTab(CardMode.COOKING)
        assertFalse(exists(NutritionTags.SECTION))
        assertFalse(exists(NutritionTags.TILES))
    }

    @Test
    fun itOpensOnOneServingWithTheFourValuesInOrder() {
        open()
        compose.onNodeWithTag(NutritionTags.SWITCH_PER_SERVING).assertIsSelected()
        compose.onNodeWithTag(NutritionTags.SWITCH_WHOLE).assertIsNotSelected()
        // 790.8 / 4 = 197.7 kcal, 16.53 g, 8.25 g, 13.2 g.
        assertEquals(listOf("200 kcal", "17 g protein", "8 g fat", "13 g carbs"), shown())
        assertEquals("Per serving: about 200 kilocalories, protein 17 grams, fat 8 grams, carbs 13 grams", spoken())
    }

    @Test
    fun theWholeDishFollowsTheServingsWhileOneServingStaysTheSame() {
        open()
        choose(NutritionTags.SWITCH_WHOLE)
        compose.onNodeWithTag(NutritionTags.SWITCH_WHOLE).assertIsSelected()
        assertEquals(listOf("790 kcal", "66 g protein", "33 g fat", "53 g carbs"), shown())
        assertEquals("Whole dish, 4 servings: about 790 kilocalories, protein 66 grams, fat 33 grams, carbs 53 grams", spoken())
        more(4)
        assertEquals(listOf("Servings: 8"), compose.textsOf(CardTags.SERVINGS))
        assertEquals(listOf("1,580 kcal", "132 g protein", "66 g fat", "106 g carbs"), shown())
        assertEquals("Whole dish, 8 servings: about 1580 kilocalories, protein 132 grams, fat 66 grams, carbs 106 grams", spoken())
        choose(NutritionTags.SWITCH_PER_SERVING)
        assertEquals(listOf("200 kcal", "17 g protein", "8 g fat", "13 g carbs"), shown())
    }

    @Test
    fun aFixedAmountMovesTheWholeDishAndIsSaidAsApproximate() {
        open()
        choose(NutritionTags.SWITCH_WHOLE)
        fixTheCheese()
        assertEquals(listOf("1,580 kcal", "132 g protein", "66 g fat", "106 g carbs"), shown())
        assertEquals("Whole dish, about 8 servings: about 1580 kilocalories, protein 132 grams, fat 66 grams, carbs 106 grams", spoken())
        openDetails()
        assertEquals(listOf("By ingredient, whole dish (≈8 servings)"), compose.textsOf(NutritionTags.COUNTED))
        assertEquals(listOf(listOf("cottage cheese", "1,140 kcal"), listOf("flour", "440 kcal")), rowsOf(listOf(NutritionTags.counted(0), NutritionTags.counted(1))))
    }

    @Test
    fun theChoiceIsKeptWhileTheCardStaysOpen() {
        open()
        choose(NutritionTags.SWITCH_WHOLE)
        compose.chooseCardTab(CardMode.COOKING)
        compose.chooseCardTab(CardMode.VIEW)
        compose.onNodeWithTag(NutritionTags.SWITCH_WHOLE).assertIsSelected()
        assertTrue(shown().first() == "790 kcal")
    }

    @Test
    fun theRowsLeftOutAreNamedOnTheCardAndTheTotalsCountTheRest() {
        open()
        assertEquals(
            listOf("Not counted: salt, Sour cream, sugar, egg, vanilla, Unnamed ingredient"),
            compose.textsOf(NutritionTags.LEFT_OUT),
        )
        assertEquals("200 kcal", shown().first())
    }

    @Test
    fun detailsGivesEachCountedRowsKcalForTheWholeDishTheRowsLeftOutWithTheirReasonsAndHowItIsCounted() {
        open()
        openDetails()
        val title = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading) and hasTestTag(NutritionTags.SHEET_TITLE)
        compose.onNode(title, useUnmergedTree = true).assertExists()
        assertEquals(listOf("Nutrition, approximate"), compose.textsOf(NutritionTags.SHEET_TITLE))
        assertEquals(listOf("By ingredient, whole dish (4 servings)"), compose.textsOf(NutritionTags.COUNTED))
        // Each rounded on its own: 572.4 and 218.4.
        assertEquals(listOf(listOf("cottage cheese", "570 kcal"), listOf("flour", "220 kcal")), rowsOf((0..1).map(NutritionTags::counted)))
        assertEquals(listOf("Not counted"), compose.textsOf(NutritionTags.NOT_COUNTED))
        assertEquals(
            listOf(
                listOf("salt", "to taste"),
                listOf("Sour cream", "not in the dictionary"),
                listOf("sugar", "no amount"),
                listOf("egg", "no measure"),
                listOf("vanilla", "no data"),
                listOf("Unnamed ingredient", "not in the dictionary"),
            ),
            rowsOf((2..7).map(NutritionTags::notCounted)),
        )
        assertEquals(
            listOf("Counted from raw ingredients: cooking losses are ignored, piece weights are averages."),
            compose.textsOf(NutritionTags.HOW),
        )
        // Top to bottom: title, counted, not counted, how; no totals table.
        assertEquals(
            listOf(NutritionTags.SHEET_TITLE, NutritionTags.COUNTED, NutritionTags.NOT_COUNTED, NutritionTags.HOW),
            compose.tagsTopToBottom(setOf(NutritionTags.SHEET_TITLE, NutritionTags.COUNTED, NutritionTags.NOT_COUNTED, NutritionTags.HOW)),
        )
        // The totals are the card's, behind its switch, not the sheet's.
        assertFalse(compose.onAllNodes(hasText("200 kcal") and hasAnyAncestor(hasTestTag(NutritionTags.SHEET)), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun aRecipeWhoseEveryRowCountsShowsNoRowsLeftOut() {
        open(cheesecakes(ingredients = counted))
        assertFalse(exists(NutritionTags.LEFT_OUT))
        openDetails()
        assertTrue(exists(NutritionTags.counted(0)))
        assertFalse(exists(NutritionTags.NOT_COUNTED))
    }

    @Test
    fun aRecipeWhereNoRowCanBeCountedShowsNoSection() {
        open(cheesecakes(ingredients = leftOut))
        assertFalse(exists(NutritionTags.SECTION))
        assertFalse(compose.onAllNodes(hasText("Nutrition, approximate"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        assertTrue(bottom(CardTags.INGREDIENTS) <= top(CardTags.STEPS))
    }

    @Test
    fun aRecipeWithNoServingCountShowsTheWholeDishAloneWithNoSwitch() {
        open(cheesecakes(servings = null))
        assertFalse(exists(NutritionTags.SWITCH))
        assertEquals(listOf("Whole dish"), compose.textsOf(NutritionTags.WHOLE_ONLY))
        assertEquals(listOf("790 kcal", "66 g protein", "33 g fat", "53 g carbs"), shown())
        assertEquals("Whole dish: about 790 kilocalories, protein 66 grams, fat 33 grams, carbs 53 grams", spoken())
        openDetails()
        assertEquals(listOf("By ingredient, whole dish"), compose.textsOf(NutritionTags.COUNTED))
    }

    @Test
    fun theScreenReaderFindsRadioSegmentsOneTilesNodeAndADetailsButton() {
        open()
        compose.scrollClearOfFork(NutritionTags.SWITCH)
        listOf(NutritionTags.SWITCH_PER_SERVING, NutritionTags.SWITCH_WHOLE).forEach { tag ->
            val node = compose.onNodeWithTag(tag).fetchSemanticsNode()
            assertEquals(Role.RadioButton, node.config.getOrNull(SemanticsProperties.Role))
            // Drawn 40 dp tall within the card's target.
            val density = node.layoutInfo.density.density
            assertTrue("$tag is ${node.touchBoundsInRoot.height / density} dp to touch", node.touchBoundsInRoot.height / density + 0.5f >= CardTarget.value)
        }
        val tiles = compose.onNodeWithTag(NutritionTags.TILES).fetchSemanticsNode()
        assertFalse(tiles.config.contains(SemanticsActions.OnClick))
        assertTrue(tiles.config.getOrNull(SemanticsProperties.LiveRegion) == null)
        // The tiles are no focus stops of their own and carry no text apart from the phrase.
        (0..3).forEach { i ->
            val tile = compose.onNodeWithTag(NutritionTags.tile(i), useUnmergedTree = true).fetchSemanticsNode()
            assertFalse(tile.config.contains(SemanticsActions.OnClick))
            assertTrue(tile.config.getOrNull(SemanticsProperties.Text).isNullOrEmpty())
        }
        val details = compose.onNodeWithTag(NutritionTags.DETAILS).fetchSemanticsNode()
        assertEquals(Role.Button, details.config.getOrNull(SemanticsProperties.Role))
        assertEquals(listOf("Details"), details.config[SemanticsProperties.Text].map { it.text })
        compose.assertCardTarget(NutritionTags.DETAILS)
    }

    @Test
    fun aSegmentAnswersATouchWithinTheCardsTargetAboveItsDrawing() {
        open()
        compose.scrollClearOfFork(NutritionTags.SWITCH)
        val segment = compose.onNodeWithTag(NutritionTags.SWITCH_WHOLE).fetchSemanticsNode()
        val above = (CardTarget.value - 2) / 2 * segment.layoutInfo.density.density - segment.size.height / 2f
        compose.onNodeWithTag(NutritionTags.SWITCH_WHOLE).performTouchInput { click(Offset(centerX, -above)) }
        compose.waitForIdle()
        compose.onNodeWithTag(NutritionTags.SWITCH_WHOLE).assertIsSelected()
    }

    @Test
    fun theSheetsRowsReadAsOneItemEach() {
        open()
        openDetails()
        val row = compose.onNodeWithTag(NutritionTags.counted(0)).fetchSemanticsNode()
        assertTrue(row.config.isMergingSemanticsOfDescendants)
        assertEquals(listOf("cottage cheese", "570 kcal"), row.config[SemanticsProperties.Text].map { it.text })
        assertTrue(compose.onNodeWithTag(NutritionTags.notCounted(2)).fetchSemanticsNode().config.isMergingSemanticsOfDescendants)
    }

    @Test
    fun theFourTilesStandInOneRowWhenTheyFit() {
        open()
        val tops = (0..3).map { compose.onNodeWithTag(NutritionTags.tile(it), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top }
        assertTrue("$tops", tops.all { it == tops[0] })
    }

    @Test
    @Config(qualifiers = "ru-w360dp-h640dp")
    fun inRussianEverythingIsSaidAsDecided() {
        open()
        compose.onNode(hasText("Пищевая ценность, примерно") and hasAnyAncestor(hasTestTag(NutritionTags.SECTION)), useUnmergedTree = true).assertExists()
        assertEquals(listOf("Порция"), compose.textsOf(NutritionTags.SWITCH_PER_SERVING))
        assertEquals(listOf("Всё блюдо"), compose.textsOf(NutritionTags.SWITCH_WHOLE))
        assertEquals(listOf("200 ккал", "17 г белки", "8 г жиры", "13 г углеводы"), shown())
        assertEquals("На порцию: примерно 200 килокалорий, белки 17 граммов, жиры 8 граммов, углеводы 13 граммов", spoken())
        assertEquals(listOf("Не учтены: соль, Sour cream, сахар, яйцо, ваниль, Ингредиент без названия"), compose.textsOf(NutritionTags.LEFT_OUT))
        assertEquals(listOf("Подробнее"), compose.textsOf(NutritionTags.DETAILS))
        choose(NutritionTags.SWITCH_WHOLE)
        assertEquals("Всё блюдо, 4 порции: примерно 790 килокалорий, белки 66 граммов, жиры 33 грамма, углеводы 53 грамма", spoken())
        openDetails()
        assertEquals(listOf("Пищевая ценность, примерно"), compose.textsOf(NutritionTags.SHEET_TITLE))
        assertEquals(listOf("По ингредиентам, на всё блюдо (4 порции)"), compose.textsOf(NutritionTags.COUNTED))
        assertEquals(listOf(listOf("творог", "570 ккал"), listOf("мука", "220 ккал")), rowsOf((0..1).map(NutritionTags::counted)))
        assertEquals(listOf("Не учтены"), compose.textsOf(NutritionTags.NOT_COUNTED))
        assertEquals(
            listOf("по вкусу", "нет в словаре", "без количества", "нет меры", "нет данных", "нет в словаре"),
            rowsOf((2..7).map(NutritionTags::notCounted)).map { it[1] },
        )
        assertEquals(
            listOf("Посчитано по сырым продуктам: потери при готовке не учтены, вес штук — средний."),
            compose.textsOf(NutritionTags.HOW),
        )
    }

    @Test
    @Config(qualifiers = "ru-w360dp-h640dp")
    fun inRussianAFixedAmountIsSaidAsApproximate() {
        open()
        choose(NutritionTags.SWITCH_WHOLE)
        fixTheCheese()
        assertEquals("Всё блюдо, примерно 8 порций: примерно 1580 килокалорий, белки 132 грамма, жиры 66 граммов, углеводы 106 граммов", spoken())
        openDetails()
        assertEquals(listOf("По ингредиентам, на всё блюдо (≈8 порций)"), compose.textsOf(NutritionTags.COUNTED))
    }

    @Test
    @Config(qualifiers = "ru-w360dp-h640dp")
    fun inRussianARecipeWithNoCountIsTheWholeDishAlone() {
        open(cheesecakes(servings = null))
        assertEquals(listOf("Всё блюдо"), compose.textsOf(NutritionTags.WHOLE_ONLY))
        assertEquals("Всё блюдо: примерно 790 килокалорий, белки 66 граммов, жиры 33 грамма, углеводы 53 грамма", spoken())
    }
}
