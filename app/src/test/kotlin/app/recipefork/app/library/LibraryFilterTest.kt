package app.recipefork.app.library

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.card.CardEnvironment
import app.recipefork.app.card.IngredientEntries
import app.recipefork.app.dictionary.IngredientEntryEntity
import app.recipefork.app.dictionary.TagEntity
import app.recipefork.app.dictionary.TagSource
import app.recipefork.app.editor.EditorEnvironment
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.editor.textsOf
import app.recipefork.app.file.RecipeImport
import app.recipefork.app.recipe.BookFilters
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.Rational
import app.recipefork.core.search.BookOrder
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * FLOW-035, CAT-002, CAT-006, CAT-007: the filters under the search field of each view of the recipe book — by a
 * dictionary ingredient and by tags. The screen is tall enough for every entry of a test to be laid out at once.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "h2000dp")
class LibraryFilterTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val active = ActiveBook(BookName.Pool, book.database)
    private val recipes = RecipeRepository(active)
    private val session = LibrarySession(SavedStateHandle())
    private val now = Instant.parse("2026-10-01T12:00:00Z")

    private val chicken = IngredientEntryEntity(1, "курица", "chicken", null)
    private val wings = IngredientEntryEntity(2, "крылышки", "chicken wings", 1)
    private val thigh = IngredientEntryEntity(3, "филе бедра", "thigh fillet", 1)
    private val broth = IngredientEntryEntity(4, "бульон", "broth", null)
    /** An entry the product owner removed upstream (FLOW-001 (7), DOM-009 (5)). */
    private val goose = IngredientEntryEntity(5, "гусь", "goose", null, removed = true)
    private val italian = TagEntity(10, "итальянская", "Italian")
    private val georgian = TagEntity(11, "грузинская", "Georgian")
    private val quick = TagEntity(12, "быстро", "Quick")

    private val card = CardEnvironment(ingredients = IngredientEntries { listOf(chicken, wings, thigh, broth, goose) })
    private val editor = EditorEnvironment(
        active,
        object : TagSource {
            override suspend fun tags() = listOf(italian, georgian, quick)
        },
    ) { now }

    @Before
    fun waitForTheBook() = compose.registerIdlingResource(book.idling)

    @After
    fun close() {
        compose.unregisterIdlingResource(book.idling)
        book.close()
    }

    private fun grams(position: Int, entry: IngredientEntryEntity?, grams: Long?, qualifier: String? = null, free: String? = null) =
        if (grams == null) {
            Ingredient(position, entry?.id, qualifier, free, UnitType.TASTE_UNMEASURED, null, null, null, null)
        } else {
            Ingredient(position, entry?.id, qualifier, free, UnitType.EXACT, QuantityUnit.GRAM, Rational.of(grams), null, null)
        }

    /** An own recipe for [servings], last changed [minutesAgo] minutes before now. */
    private fun own(
        title: String,
        vararg ingredients: Ingredient,
        servings: Int? = 4,
        tags: Set<Long> = emptySet(),
        minutesAgo: Long = 0,
    ): Recipe {
        val recipe = Recipe.create(now.minusSeconds(minutesAgo * 60), owner = null)
            .copy(title = title, ingredients = ingredients.toList(), servings = servings, tags = tags)
        runBlocking { recipes.save(recipe) }
        return recipe
    }

    /** A found recipe, marked [minutesAgo] minutes before now. */
    private fun found(title: String, vararg ingredients: Ingredient, tags: Set<Long> = emptySet(), minutesAgo: Long = 0): Recipe {
        val recipe = Recipe.create(now, owner = null)
            .copy(title = title, ingredients = ingredients.toList(), servings = 2, tags = tags)
            .inState(RecipeImport.FOUND_STATE)
        runBlocking { recipes.addFound(recipe, now.minusSeconds(minutesAgo * 60)) }
        return recipe
    }

    private fun show(view: LibraryView) {
        session.choose(view)
        compose.setContent { MaterialTheme { LibraryScreen(session, recipes, card = card, editor = editor) } }
        compose.waitForIdle()
    }

    private fun choose(view: LibraryView) {
        compose.onNodeWithTag(LibraryTags.tab(view)).performClick()
        compose.waitForIdle()
    }

    private val anEntryTitle = SemanticsMatcher("an entry's title") {
        it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("library-entry-title-") == true
    }

    private fun listed(): List<String> = compose.onAllNodes(anEntryTitle, useUnmergedTree = true).fetchSemanticsNodes()
        .map { node -> node.config[SemanticsProperties.Text].joinToString("") { it.text } }

    private fun assertListed(vararg titles: String) {
        runCatching { compose.waitUntil(PATIENCE_MS) { listed() == titles.toList() } }
        assertEquals(titles.toList(), listed())
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    private fun click(tag: String) {
        compose.waitUntilExactlyOneExists(hasTestTag(tag), PATIENCE_MS)
        compose.onNodeWithTag(tag).performClick()
        compose.waitForIdle()
    }

    /** Filters by the entry offered for [typed] with the identity [entry], as the editor's ingredient field offers it. */
    private fun filterByIngredient(typed: String, entry: IngredientEntryEntity) {
        click(LibraryTags.FILTER_ADD)
        click(LibraryTags.FILTER_BY_INGREDIENT)
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.INGREDIENT_FIELD), PATIENCE_MS)
        compose.onNodeWithTag(LibraryTags.INGREDIENT_FIELD).performTextReplacement(typed)
        click(LibraryTags.ingredientChoice(entry.id))
        compose.waitUntil(PATIENCE_MS) { !exists(LibraryTags.INGREDIENT_CHOOSER) }
    }

    /** Turns each of [tags] on or off in the tag chooser. */
    private fun toggleTags(vararg tags: TagEntity) {
        click(LibraryTags.FILTER_ADD)
        click(LibraryTags.FILTER_BY_TAGS)
        tags.forEach { click(LibraryTags.tagChoice(it.id)) }
        click(LibraryTags.TAG_CHOOSER_DONE)
        compose.waitUntil(PATIENCE_MS) { !exists(LibraryTags.TAG_CHOOSER) }
    }

    private fun orderBy(order: BookOrder) {
        click(LibraryTags.ORDER)
        click(LibraryTags.order(order))
    }

    /** Waits until the chip tagged [tag] names [name], and fails naming what it shows if it never does. */
    private fun assertChip(tag: String, name: String) {
        fun shown() = if (exists(tag)) compose.textsOf(tag).joinToString("") else null
        runCatching { compose.waitUntil(PATIENCE_MS) { shown() == name } }
        assertEquals(name, shown())
    }

    // CAT-006: a filter on the parent matches it and its children; a free-text row and a qualifier match nothing; a
    // filter on a child matches only the child.
    @Test
    fun anIngredientFilterMatchesTheEntryAndItsChildrenOnly() {
        own("Крылышки в меду", grams(0, wings, 400), minutesAgo = 1)
        own("Бёдра на гриле", grams(0, thigh, 300), minutesAgo = 2)
        own("Курица целиком", grams(0, chicken, 200), minutesAgo = 3)
        own("Курица от руки", grams(0, null, 900, free = "курица"), minutesAgo = 4)
        own("Бульонный суп", grams(0, broth, 900, qualifier = "куриный"), minutesAgo = 5)
        show(LibraryView.OWN)
        filterByIngredient("кур", chicken)
        assertChip(LibraryTags.INGREDIENT_FILTER, "chicken")
        assertListed("Крылышки в меду", "Бёдра на гриле", "Курица целиком")
        // Tapping the chip removes the filter.
        click(LibraryTags.INGREDIENT_FILTER)
        assertListed("Крылышки в меду", "Бёдра на гриле", "Курица целиком", "Курица от руки", "Бульонный суп")
        filterByIngredient("wings", wings)
        assertListed("Крылышки в меду")
        assertEquals(BookFilters(ingredient = wings.id), session.filters(LibraryView.OWN).value)
    }

    // CAT-002, CAT-007: under an ingredient filter the most per serving comes first, matched lines summed; recipes of
    // no comparable quantity follow in the chosen order; the chosen order applies again once the filter is cleared.
    @Test
    fun anIngredientFilterOrdersByQuantityPerServing() {
        own("Aa 125 g a serving", grams(0, thigh, 500), servings = 4, minutesAgo = 1)
        own("Bb 150 g a serving", grams(0, chicken, 300), servings = 2, minutesAgo = 2)
        own("Cc 200 g a serving", grams(0, thigh, 500), grams(1, wings, 300), servings = 4, minutesAgo = 3)
        own("Dd to taste", grams(0, chicken, null), minutesAgo = 4)
        own("Ee no servings", grams(0, chicken, 900), servings = null, minutesAgo = 5)
        own("Ff no chicken", grams(0, broth, 900), minutesAgo = 6)
        show(LibraryView.OWN)
        orderBy(BookOrder.OLDEST)
        assertListed("Ff no chicken", "Ee no servings", "Dd to taste", "Cc 200 g a serving", "Bb 150 g a serving", "Aa 125 g a serving")
        filterByIngredient("курица", chicken)
        assertListed("Cc 200 g a serving", "Bb 150 g a serving", "Aa 125 g a serving", "Ee no servings", "Dd to taste")
        orderBy(BookOrder.TITLE)
        assertListed("Cc 200 g a serving", "Bb 150 g a serving", "Aa 125 g a serving", "Dd to taste", "Ee no servings")
        click(LibraryTags.FILTERS_CLEAR)
        assertListed("Aa 125 g a serving", "Bb 150 g a serving", "Cc 200 g a serving", "Dd to taste", "Ee no servings", "Ff no chicken")
        assertEquals(BookOrder.TITLE, session.order(LibraryView.OWN).value)
    }

    // A filter by one or more tags keeps the recipes carrying all of them.
    @Test
    fun aTagFilterKeepsTheRecipesCarryingEveryTag() {
        own("Паста", tags = setOf(italian.id), minutesAgo = 1)
        own("Пицца на скорую руку", tags = setOf(italian.id, quick.id), minutesAgo = 2)
        own("Хачапури", tags = setOf(georgian.id), minutesAgo = 3)
        own("Без тегов", minutesAgo = 4)
        show(LibraryView.OWN)
        toggleTags(italian)
        assertChip(LibraryTags.tagFilter(italian.id), "Italian")
        assertListed("Паста", "Пицца на скорую руку")
        toggleTags(quick)
        assertListed("Пицца на скорую руку")
        // Removing one tag's chip keeps the other.
        click(LibraryTags.tagFilter(italian.id))
        assertListed("Пицца на скорую руку")
        assertEquals(BookFilters(tags = setOf(quick.id)), session.filters(LibraryView.OWN).value)
        // A choice no recipe satisfies says so, and offers to clear the filters.
        toggleTags(georgian)
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.NOTHING_FOUND), PATIENCE_MS)
        assertEquals(listOf("No recipe matches the filters"), compose.textsOf(LibraryTags.NOTHING_FOUND))
        assertEquals(false, exists(LibraryTags.NOTHING_FOUND_CLEAR))
        click(LibraryTags.NOTHING_FOUND_CLEAR_FILTERS)
        assertListed("Паста", "Пицца на скорую руку", "Хачапури", "Без тегов")
    }

    // The same, in Russian: the chips and the empty result in the interface language.
    @Test
    @Config(qualifiers = "ru-h2000dp")
    fun theFiltersSpeakTheInterfaceLanguage() {
        own("Паста", grams(0, wings, 100), tags = setOf(italian.id), minutesAgo = 1)
        show(LibraryView.OWN)
        toggleTags(italian)
        filterByIngredient("крыл", wings)
        assertChip(LibraryTags.tagFilter(italian.id), "итальянская")
        assertChip(LibraryTags.INGREDIENT_FILTER, "крылышки")
        assertListed("Паста")
        assertEquals(listOf("Сбросить фильтры"), compose.textsOf(LibraryTags.FILTERS_CLEAR))
        toggleTags(georgian)
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.NOTHING_FOUND), PATIENCE_MS)
        assertEquals(listOf("Ни один рецепт не подходит под фильтры"), compose.textsOf(LibraryTags.NOTHING_FOUND))
    }

    // Filters combine with the search and the order; one control clears them all, and the search stays.
    @Test
    fun filtersCombineWithTheSearchAndOneControlClearsThemAll() {
        own("Крылышки по-итальянски", grams(0, wings, 400), tags = setOf(italian.id), minutesAgo = 1)
        own("Курица по-итальянски", grams(0, chicken, 800), tags = setOf(italian.id), minutesAgo = 2)
        own("Курица по-грузински", grams(0, chicken, 900), tags = setOf(georgian.id), minutesAgo = 3)
        own("Паста по-итальянски", tags = setOf(italian.id), minutesAgo = 4)
        show(LibraryView.OWN)
        compose.onNodeWithTag(LibraryTags.SEARCH).performTextReplacement("итал")
        assertListed("Крылышки по-итальянски", "Курица по-итальянски", "Паста по-итальянски")
        filterByIngredient("кур", chicken)
        // 200 g a serving before 100 g.
        assertListed("Курица по-итальянски", "Крылышки по-итальянски")
        toggleTags(italian)
        assertListed("Курица по-итальянски", "Крылышки по-итальянски")
        compose.onNodeWithTag(LibraryTags.SEARCH).performTextReplacement("крыл")
        assertListed("Крылышки по-итальянски")
        click(LibraryTags.FILTERS_CLEAR)
        assertEquals(BookFilters(), session.filters(LibraryView.OWN).value)
        assertEquals(false, exists(LibraryTags.INGREDIENT_FILTER))
        assertEquals(false, exists(LibraryTags.tagFilter(italian.id)))
        assertEquals(false, exists(LibraryTags.FILTERS_CLEAR))
        assertEquals("крыл", session.query(LibraryView.OWN).value)
        assertListed("Крылышки по-итальянски")
    }

    // Each view keeps its own filters for the session.
    @Test
    fun eachViewKeepsItsOwnFilters() {
        own("Паста", tags = setOf(italian.id), minutesAgo = 1)
        own("Хачапури", tags = setOf(georgian.id), minutesAgo = 2)
        found("Пицца", grams(0, chicken, 100), tags = setOf(italian.id), minutesAgo = 1)
        found("Чахохбили", grams(0, chicken, 300), tags = setOf(georgian.id), minutesAgo = 2)
        found("Щи", minutesAgo = 3)
        show(LibraryView.OWN)
        toggleTags(georgian)
        assertListed("Хачапури")
        choose(LibraryView.MARKED)
        assertEquals(false, exists(LibraryTags.tagFilter(georgian.id)))
        assertListed("Пицца", "Чахохбили", "Щи")
        filterByIngredient("кур", chicken)
        assertListed("Чахохбили", "Пицца")
        choose(LibraryView.OWN)
        assertChip(LibraryTags.tagFilter(georgian.id), "Georgian")
        assertEquals(false, exists(LibraryTags.INGREDIENT_FILTER))
        assertListed("Хачапури")
        choose(LibraryView.MARKED)
        assertChip(LibraryTags.INGREDIENT_FILTER, "chicken")
        assertListed("Чахохбили", "Пицца")
        assertEquals(BookFilters(tags = setOf(georgian.id)), session.filters(LibraryView.OWN).value)
        assertEquals(BookFilters(ingredient = chicken.id), session.filters(LibraryView.MARKED).value)
    }

    /** Opens the ingredient chooser and types [typed] into its field. */
    private fun openChooser(typed: String) {
        click(LibraryTags.FILTER_ADD)
        click(LibraryTags.FILTER_BY_INGREDIENT)
        retype(typed)
    }

    private fun retype(typed: String) {
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.INGREDIENT_FIELD), PATIENCE_MS)
        compose.onNodeWithTag(LibraryTags.INGREDIENT_FIELD).performTextReplacement(typed)
        compose.waitForIdle()
    }

    // FLOW-001 (7), DOM-009 (5): an entry removed upstream is offered by the filter only while a recipe in the view holds
    // it, and a recipe holding it is still found by its name.
    @Test
    fun aRemovedEntryIsOfferedOnlyWhileARecipeInTheViewHoldsIt() {
        own("Гусь с яблоками", grams(0, goose, 900), minutesAgo = 1)
        own("Курица целиком", grams(0, chicken, 200), minutesAgo = 2)
        found("Щи", grams(0, broth, 300), minutesAgo = 1)
        show(LibraryView.OWN)
        compose.onNodeWithTag(LibraryTags.SEARCH).performTextReplacement("goose")
        assertListed("Гусь с яблоками")
        compose.onNodeWithTag(LibraryTags.SEARCH).performTextReplacement("")
        openChooser("гус")
        click(LibraryTags.ingredientChoice(goose.id))
        compose.waitUntil(PATIENCE_MS) { !exists(LibraryTags.INGREDIENT_CHOOSER) }
        assertChip(LibraryTags.INGREDIENT_FILTER, "goose")
        assertListed("Гусь с яблоками")
        // No recipe of the found view holds it: there it is not offered, though the dictionary is read.
        choose(LibraryView.MARKED)
        assertListed("Щи")
        openChooser("бул")
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.ingredientChoice(broth.id)), PATIENCE_MS)
        retype("гус")
        compose.waitUntil(PATIENCE_MS) { !exists(LibraryTags.ingredientChoice(broth.id)) }
        assertEquals(false, exists(LibraryTags.ingredientChoice(goose.id)))
        retype("goose")
        assertEquals(false, exists(LibraryTags.ingredientChoice(goose.id)))
    }
}
