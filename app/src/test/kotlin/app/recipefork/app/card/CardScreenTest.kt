package app.recipefork.app.card

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.activity.ComponentActivity
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.editor.SAMPLE_DICTIONARIES
import app.recipefork.app.editor.TestDevice
import app.recipefork.app.editor.descriptionOf
import app.recipefork.app.editor.textsOf
import app.recipefork.app.library.LibraryTags
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.ui.AccessibilityChecks
import app.recipefork.app.ui.LocalizationChecks
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeId
import app.recipefork.core.recipe.RecipeState
import app.recipefork.core.recipe.SentStatus
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.Rational
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * The recipe card as the reader sees it (FLOW-029, FLOW-019, the card intro of `cooking`). Every text is asserted
 * as it reads in the language in force — English, and Russian under `@Config(qualifiers = "ru")`.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class CardScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val device = TestDevice(context)
    private val recipes = RecipeRepository(ActiveBook(BookName.Pool, book.database))
    private val environment = CardEnvironment { device.database.dictionaryDao().ingredients() }
    private val now = Instant.parse("2026-10-01T12:00:00Z")
    private var backs = 0
    private var edits = 0

    /** What the reader reads on the card, in one language. */
    private class Words(
        val back: String,
        val backAction: String,
        val edit: String,
        val editAction: String,
        val servings: String,
        val ingredients: String,
        val steps: String,
        val unnamed: String,
        val missing: String,
        val missingHeadline: String,
        val missingBack: String,
        /** The sample recipe's lines, in order: name, then quantity. */
        val lines: List<List<String>>,
    )

    private val english = Words(
        back = "Back",
        backAction = "Return to My recipe book",
        edit = "Edit",
        editAction = "Open this recipe in the editor",
        servings = "Servings: 4",
        ingredients = "Ingredients",
        steps = "Steps",
        unnamed = "Unnamed ingredient",
        missing = "This recipe is no longer in this recipe book.",
        missingHeadline = "Recipe not found",
        missingBack = "Back to the book",
        lines = listOf(
            listOf("chicken", "1.5 kg"),
            listOf("Smoked paprika", "½ tsp"),
            listOf("chicken wings, trimmed", "6 pc"),
            listOf("Salt — to taste"),
            listOf("Water", "750 ml"),
        ),
    )

    private val russian = Words(
        back = "Назад",
        backAction = "Вернуться в «Мою книгу рецептов»",
        edit = "Изменить",
        editAction = "Открыть этот рецепт в редакторе",
        servings = "Порций: 4",
        ingredients = "Ингредиенты",
        steps = "Шаги",
        unnamed = "Ингредиент без названия",
        missing = "Этого рецепта больше нет в этой книге рецептов.",
        missingHeadline = "Рецепт не найден",
        missingBack = "Назад к книге",
        lines = listOf(
            listOf("курица", "1,5 кг"),
            listOf("Smoked paprika", "½ ч. л."),
            listOf("крылышки, trimmed", "6 шт"),
            listOf("Salt — по вкусу"),
            listOf("Water", "750 мл"),
        ),
    )

    private val stepTexts = listOf("Heat the oven.", "Rub the chicken with paprika and salt.", "Roast for an hour.")

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

    /** A full recipe, its ingredients and steps held out of order so that the card has to put them in order. */
    private fun fullRecipe(state: RecipeState = RecipeState.LOCAL): Recipe = Recipe.create(now, null).copy(
        title = "Roast chicken",
        description = "A weeknight dinner.",
        servings = 4,
        sent = if (state == RecipeState.LOCAL) SentStatus.NEVER else SentStatus.SENT,
        ingredients = listOf(
            Ingredient(3, null, freeTextName = "Salt", unitType = UnitType.TASTE_UNMEASURED, unit = null, quantity = null),
            Ingredient(0, 1, unitType = UnitType.EXACT, unit = QuantityUnit.KILOGRAM, quantity = Rational.of(3, 2)),
            Ingredient(4, null, freeTextName = "Water", unitType = UnitType.EXACT, unit = QuantityUnit.MILLILITRE, quantity = Rational.of(750)),
            Ingredient(1, null, freeTextName = "Smoked paprika", unitType = UnitType.HOUSEHOLD, unit = QuantityUnit.TEASPOON, quantity = Rational.of(1, 2)),
            Ingredient(2, 2, qualifier = "trimmed", unitType = UnitType.PIECE, unit = QuantityUnit.PIECE, quantity = Rational.of(6), divisible = false),
        ),
        steps = listOf(Step(2, stepTexts[2]), Step(0, stepTexts[0]), Step(1, stepTexts[1])),
    ).inState(state)

    private fun save(recipe: Recipe): Recipe = recipe.also { runBlocking { recipes.save(it) } }

    private var shown by mutableStateOf<RecipeId?>(null)
    private var composed = false

    /** Opens the card on [id]: a test composes once, and each later call opens another card in its place. */
    private fun show(id: RecipeId) {
        if (!composed) {
            composed = true
            compose.setContent {
                MaterialTheme {
                    shown?.let { key(it) { CardScreen(it, recipes, environment, onBack = { backs++ }, onEdit = { edits++ }) } }
                }
            }
        }
        compose.runOnIdle { shown = id }
        compose.waitForIdle()
        compose.awaitCard()
    }

    /** The author's own words and what `:core` writes, which are not interface text of a resource. */
    private fun content(recipe: Recipe, w: Words) = buildSet {
        add(recipe.title)
        add(recipe.description)
        addAll(stepTexts)
        addAll(listOf("1.", "2.", "3."))
        w.lines.forEach { addAll(it) }
    }

    /** Where the node tagged [tag] starts, scrolled out of view or not. */
    private fun top(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().positionInRoot.y

    // The card intro, FLOW-029 (1), in English.
    @Test
    fun theCardShowsTheWholeRecipeInOrderOnOneScrollingScreen() = wholeRecipe(english)

    // The same, in Russian.
    @Test
    @Config(qualifiers = "ru")
    fun theCardShowsTheWholeRecipeInOrderOnOneScrollingScreenInRussian() = wholeRecipe(russian)

    private fun wholeRecipe(w: Words) {
        val recipe = save(fullRecipe())
        show(recipe.id)
        assertEquals(listOf("Roast chicken"), compose.textsOf(CardTags.TITLE))
        compose.onNodeWithTag(CardTags.TITLE).assertHeading()
        assertEquals(listOf(w.servings), compose.textsOf(CardTags.SERVINGS))
        // The ingredients in their order, each by its dictionary label in the interface language or its free-text
        // name, with its quantity as `QuantityText` writes it; "to taste" follows the name after a dash (T-217).
        (0..4).forEach { assertEquals(w.lines[it], compose.textsOf(CardTags.ingredient(it))) }
        (0 until 4).forEach { assertTrue(top(CardTags.ingredient(it)) < top(CardTags.ingredient(it + 1))) }
        // The steps, numbered, in their order.
        (0..2).forEach { assertEquals(listOf("${it + 1}.", stepTexts[it]), compose.textsOf(CardTags.step(it))) }
        (0 until 2).forEach { assertTrue(top(CardTags.step(it)) < top(CardTags.step(it + 1))) }
        assertEquals(listOf("A weeknight dinner."), compose.textsOf(CardTags.DESCRIPTION))
        // Headings name the two lists.
        val headings = compose.onAllNodes(isHeading() and hasAnyAncestor(hasTestTag(CardTags.CONTENT)))
            .fetchSemanticsNodes().map { it.config[SemanticsProperties.Text].joinToString("") { t -> t.text } }
        assertEquals(listOf("Roast chicken", w.ingredients, w.steps), headings)
        // Title, description, serving count, ingredients, steps, one under the other on one scrolling screen.
        val order = listOf(CardTags.TITLE, CardTags.DESCRIPTION, CardTags.SERVINGS, CardTags.INGREDIENTS, CardTags.STEPS)
        order.zipWithNext().forEach { (above, below) -> assertTrue("$above above $below", top(above) < top(below)) }
        assertEquals(1, compose.onAllNodes(hasScrollAction() and hasTestTag(CardTags.CONTENT)).fetchSemanticsNodes().size)
        // Every text on it is this language's own, or the recipe's.
        LocalizationChecks.assertScreenTextFromResources(compose, context, content(recipe, w))
        LocalizationChecks.assertScreenTextWrittenInItsLanguage(compose, context, content(recipe, w))
    }

    // FLOW-019 (1–3, 5): a recipe holding only a title reads as a recipe, with no heading or placeholder for the rest.
    @Test
    fun aRecipeHoldingOnlyATitleShowsNoHeadingOrPlaceholderForWhatIsAbsent() {
        val recipe = save(Recipe.create(now, null).copy(title = "Toast"))
        show(recipe.id)
        // Nothing but the title in the recipe's content: no serving count, no ingredient or step heading, no
        // description block, no media area and no provenance area (it has no parent).
        val inContent = compose.onAllNodes(hasAnyAncestor(hasTestTag(CardTags.CONTENT)), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .flatMap { it.config.getOrElse(SemanticsProperties.Text) { emptyList() }.map { t -> t.text } }
        assertEquals(listOf("Toast"), inContent)
        listOf(CardTags.SERVINGS, CardTags.INGREDIENTS, CardTags.STEPS, CardTags.DESCRIPTION).forEach {
            compose.onNodeWithTag(it).assertDoesNotExist()
        }
        // Blank steps and a blank description are absent too.
        val blank = save(Recipe.create(now, null).copy(title = "Tea", description = "  ", steps = listOf(Step(0, " "))))
        show(blank.id)
        compose.onNodeWithTag(CardTags.STEPS).assertDoesNotExist()
        compose.onNodeWithTag(CardTags.DESCRIPTION).assertDoesNotExist()
        compose.onNodeWithTag(CardTags.TITLE).assertExists()
    }

    // An incomplete or off-grid row is shown by its name and whatever it holds — a row with no amount yet by its name
    // alone, no unit (T-217) — and the card never fails for it.
    @Test
    fun incompleteAndOffGridRowsAreShownByNameAndWhatTheyHold() {
        val recipe = save(
            Recipe.create(now, null).copy(
                title = "Unfinished",
                ingredients = listOf(
                    Ingredient(0, null, freeTextName = "Flour", unitType = UnitType.EXACT, unit = QuantityUnit.GRAM, quantity = null),
                    Ingredient(1, null, freeTextName = "Sugar", unitType = UnitType.HOUSEHOLD, unit = QuantityUnit.TEASPOON, quantity = Rational.of(3, 10)),
                    Ingredient(2, null, freeTextName = "Eggs", unitType = UnitType.PIECE, unit = QuantityUnit.PIECE, quantity = Rational.of(2)),
                    Ingredient(3, null, freeTextName = "", unitType = UnitType.EXACT, unit = null, quantity = null),
                    Ingredient(4, 99, unitType = UnitType.EXACT, unit = QuantityUnit.GRAM, quantity = Rational.of(100)),
                    Ingredient(5, null, freeTextName = "Butter", unitType = UnitType.EXACT, unit = QuantityUnit.GRAM, quantity = Rational.of(50)),
                ),
            ),
        )
        show(recipe.id)
        assertEquals(listOf("Flour"), compose.textsOf(CardTags.ingredient(0)))
        assertEquals(listOf("Sugar", "tsp"), compose.textsOf(CardTags.ingredient(1)))
        assertEquals(listOf("Eggs", "pc"), compose.textsOf(CardTags.ingredient(2)))
        assertEquals(listOf(english.unnamed), compose.textsOf(CardTags.ingredient(3)))
        assertEquals(listOf(english.unnamed, "100 g"), compose.textsOf(CardTags.ingredient(4)))
        // A complete row beside them is shown as written.
        assertEquals(listOf("Butter", "50 g"), compose.textsOf(CardTags.ingredient(5)))
    }

    // FLOW-029 (2, 3): an editable recipe's bar holds back and edit; nothing inside the recipe edits it or takes text —
    // the serving count's two actions are the only thing in it that acts, and they change only how it is read.
    @Test
    fun anEditableRecipeOffersEditInTheBarAndNothingInItsContent() {
        RecipeState.entries.filter { it.editable }.forEach { state ->
            val recipe = save(fullRecipe(state))
            show(recipe.id)
            // Cooking: the lines mark; no edit, no fork, no ⋮ while nothing is marked.
            compose.chooseCardTab(CardMode.COOKING)
            assertEquals("$state", listOf(CardTags.BACK) + TABS + SERVINGS_ACTIONS + MARKED_PARTS, interactiveTags())
            assertNothingInTheContentActs()
            // View: edit and the ⋮ in the bar, fork floating; nothing in the content acts but the servings.
            compose.chooseCardTab(CardMode.VIEW)
            assertEquals("$state", listOf(CardTags.BACK, CardTags.EDIT, CardTags.MENU) + TABS + SERVINGS_ACTIONS, interactiveTags() - CardTags.FORK)
            assertTrue("$state offers fork, floating", compose.onAllNodes(hasTestTag(CardTags.FORK)).fetchSemanticsNodes().size == 1)
            compose.onAllNodes(hasAnyAncestor(hasTestTag(CardTags.BAR)) and hasTestTag(CardTags.EDIT)).fetchSemanticsNodes()
                .let { assertEquals("$state", 1, it.size) }
            assertEquals("$state", 0, compose.onAllNodes(hasSetTextAction(), useUnmergedTree = true).fetchSemanticsNodes().size)
            assertNothingInTheContentActs()
            val before = edits
            compose.onNodeWithTag(CardTags.EDIT).performClick()
            assertEquals("$state", before + 1, edits)
        }
    }

    // FLOW-029 (4): a recipe no longer editable carries no edit action and no way into the editor.
    @Test
    fun aFrozenRecipeOffersNoEditAction() {
        RecipeState.entries.filter { !it.editable }.forEach { state ->
            val recipe = save(fullRecipe(state))
            show(recipe.id)
            compose.chooseCardTab(CardMode.VIEW)
            compose.onNodeWithTag(CardTags.EDIT).assertDoesNotExist()
            assertEquals("$state", listOf(CardTags.BACK) + TABS + SERVINGS_ACTIONS, interactiveTags() - CardTags.FORK)
            assertTrue("$state offers fork, floating", compose.onAllNodes(hasTestTag(CardTags.FORK)).fetchSemanticsNodes().size == 1)
            assertNothingInTheContentActs()
            compose.chooseCardTab(CardMode.COOKING)
            compose.onNodeWithTag(CardTags.EDIT).assertDoesNotExist()
            assertEquals("$state", listOf(CardTags.BACK) + TABS + SERVINGS_ACTIONS + MARKED_PARTS, interactiveTags())
        }
    }

    // The bar's back action. The system back is the navigation host's: it leaves the card for the book
    // (OwnEntryOpensCardTest, RecipeDestinationsTest).
    @Test
    fun theBackActionLeavesTheCard() {
        val recipe = save(fullRecipe())
        show(recipe.id)
        compose.onNodeWithTag(CardTags.BACK).performClick()
        assertEquals(1, backs)
    }

    // A recipe the book no longer holds: the app's empty state — a picture, a headline, the message — and a way back
    // to the book beside the bar's back, never a crash.
    @Test
    fun aRecipeTheBookDoesNotHoldShowsAMessageAndTheBackAction() = missing(english)

    @Test
    @Config(qualifiers = "ru")
    fun aRecipeTheBookDoesNotHoldShowsAMessageAndTheBackActionInRussian() = missing(russian)

    private fun missing(w: Words) {
        show(RecipeId("not-in-this-book"))
        assertEquals(listOf(w.missingHeadline), compose.textsOf(LibraryTags.emptyHeadline(CardTags.MISSING)))
        assertEquals(listOf(w.missing), compose.textsOf(LibraryTags.emptySentence(CardTags.MISSING)))
        compose.onNodeWithTag(LibraryTags.emptyPicture(CardTags.MISSING), useUnmergedTree = true).assertExists()
        assertEquals(listOf(w.missingBack), compose.textsOf(CardTags.MISSING_BACK))
        assertEquals(listOf(CardTags.BACK, CardTags.MISSING_BACK), interactiveTags())
        compose.assertCardTarget(CardTags.MISSING_BACK)
        compose.onNodeWithTag(CardTags.CONTENT).assertDoesNotExist()
        compose.onNodeWithTag(CardTags.BACK).performClick()
        assertEquals(1, backs)
        compose.onNodeWithTag(CardTags.MISSING_BACK).performClick()
        assertEquals(2, backs)
        LocalizationChecks.assertScreenTextFromResources(compose, context)
        LocalizationChecks.assertScreenTextWrittenInItsLanguage(compose, context)
    }

    // The card follows the book: a write to the recipe while it is open is shown, and a recipe gone says so.
    @Test
    fun theCardShowsTheRecipeAsTheBookHoldsItNow() {
        val recipe = save(fullRecipe())
        show(recipe.id)
        save(recipe.copy(title = "Roast chicken with lemon"))
        compose.waitUntil(PATIENCE) { compose.textsOf(CardTags.TITLE) == listOf("Roast chicken with lemon") }
    }

    // A11Y-003 (1), A11Y-005 (1) on the card, in English and in Russian.
    @Test
    fun theBarsActionsAreLargeEnoughAndAnnounceWhatTheyDo() = announced(english)

    @Test
    @Config(qualifiers = "ru")
    fun theBarsActionsAreLargeEnoughAndAnnounceWhatTheyDoInRussian() = announced(russian)

    private fun announced(w: Words) {
        var id by mutableStateOf(save(fullRecipe()).id)
        compose.setContent {
            MaterialTheme { key(id) { CardScreen(id, recipes, environment, onBack = {}, onEdit = {}) } }
        }
        compose.awaitCard()
        AccessibilityChecks.assertTouchTargets(compose, expectAtLeast = 5)
        AccessibilityChecks.assertInteractiveElementsLabelled(compose, expectAtLeast = 5)
        assertEquals(listOf(w.back), compose.descriptionOf(CardTags.BACK))
        assertEquals(listOf(w.edit), compose.descriptionOf(CardTags.EDIT))
        assertEquals(w.backAction, AccessibilityChecks.clickActionLabel(compose.onNodeWithTag(CardTags.BACK).fetchSemanticsNode()))
        assertEquals(w.editAction, AccessibilityChecks.clickActionLabel(compose.onNodeWithTag(CardTags.EDIT).fetchSemanticsNode()))
        id = save(fullRecipe(RecipeState.PUBLISHED)).id
        compose.awaitCard()
        compose.waitUntil(PATIENCE) { compose.onAllNodes(hasTestTag(CardTags.EDIT)).fetchSemanticsNodes().isEmpty() }
        AccessibilityChecks.assertTouchTargets(compose, expectAtLeast = 4)
        AccessibilityChecks.assertInteractiveElementsLabelled(compose, expectAtLeast = 4)
    }

    /** The test tags of every element on screen the reader can act on, in the order laid out. */
    private fun interactiveTags(): List<String?> =
        compose.onAllNodes(AccessibilityChecks.isInteractive).fetchSemanticsNodes()
            .sortedWith(compareBy({ it.positionInRoot.y }, { it.positionInRoot.x }))
            .map { it.config.getOrElseNullable(SemanticsProperties.TestTag) { null } }

    private fun assertNothingInTheContentActs() {
        val acting = compose.onAllNodes(
            hasAnyAncestor(hasTestTag(CardTags.CONTENT)) and AccessibilityChecks.isInteractive,
            useUnmergedTree = true,
        ).fetchSemanticsNodes()
            .filter { SemanticsActions.OnClick in it.config || SemanticsActions.SetText in it.config }
            .filter { it.config.getOrElseNullable(SemanticsProperties.TestTag) { null } !in SERVINGS_ACTIONS + MARKED_PARTS + FIXING_AMOUNTS }
        assertEquals(emptyList<Any>(), acting.map { it.config })
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertHeading() =
        assertTrue(SemanticsProperties.Heading in fetchSemanticsNode().config)

    private companion object {
        const val PATIENCE = 60_000L
        val SERVINGS_ACTIONS = listOf(CardTags.SERVINGS_FEWER, CardTags.SERVINGS_MORE)

        /** The card's two tabs, under its bar. */
        val TABS = listOf(CardTags.tab(CardMode.VIEW), CardTags.tab(CardMode.COOKING))

        /** Every ingredient line and every step is marked done with a tap (FLOW-068). */
        val MARKED_PARTS = (0..4).map(CardTags::ingredient) + (0..2).map(CardTags::step)

        /** The amounts of the rows that carry a quantity open their fixing control (SCALE-018); salt, to taste, does not. */
        val FIXING_AMOUNTS = listOf(0, 1, 2, 4).map(CardTags::fixAmount)
    }
}
