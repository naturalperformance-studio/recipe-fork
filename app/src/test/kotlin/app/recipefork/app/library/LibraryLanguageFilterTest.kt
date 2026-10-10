package app.recipefork.app.library

import app.recipefork.app.ui.AppNavHost
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
import app.recipefork.app.dictionary.TagEntity
import app.recipefork.app.dictionary.TagSource
import app.recipefork.app.editor.EditorEnvironment
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.editor.textsOf
import app.recipefork.app.file.RecipeImport
import app.recipefork.app.recipe.BookFilters
import app.recipefork.app.recipe.BookLanguage
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeLanguage
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
 * L10N-005, L10N-006 in the recipe book (FLOW-035): the filter by a recipe's declared language, and the search of a
 * recipe's description. The screen is tall enough for every entry of a test to be laid out at once.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "h2000dp")
class LibraryLanguageFilterTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val active = ActiveBook(BookName.Pool, book.database)
    private val recipes = RecipeRepository(active)
    private val session = LibrarySession(SavedStateHandle())
    private val now = Instant.parse("2026-10-01T12:00:00Z")
    private val italian = TagEntity(10, "итальянская", "Italian")

    private val card = CardEnvironment(ingredients = IngredientEntries { emptyList() })
    private val editor = EditorEnvironment(
        active,
        object : TagSource {
            override suspend fun tags() = listOf(italian)
        },
    ) { now }

    @Before
    fun waitForTheBook() = compose.registerIdlingResource(book.idling)

    @After
    fun close() {
        compose.unregisterIdlingResource(book.idling)
        book.close()
    }

    private fun own(
        title: String,
        language: RecipeLanguage?,
        description: String = "",
        tags: Set<Long> = emptySet(),
        minutesAgo: Long = 0,
    ) {
        val recipe = Recipe.create(now.minusSeconds(minutesAgo * 60), owner = null)
            .copy(title = title, language = language, description = description, tags = tags)
        runBlocking { recipes.save(recipe) }
    }

    private fun found(title: String, language: RecipeLanguage?, description: String = "", minutesAgo: Long = 0) {
        val recipe = Recipe.create(now, owner = null)
            .copy(title = title, language = language, description = description)
            .inState(RecipeImport.FOUND_STATE)
        runBlocking { recipes.addFound(recipe, now.minusSeconds(minutesAgo * 60)) }
    }

    private fun show(view: LibraryView) {
        session.choose(view)
        compose.setContent { MaterialTheme { AppNavHost(session, recipes, card = card, editor = editor) } }
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
        compose.onNodeWithTag(tag).scrolledTo().performClick()
        compose.waitForIdle()
    }

    private val languageChoice = SemanticsMatcher("a language choice") {
        it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("library-language-choice-") == true
    }

    /** Opens the sheet of every filter and returns the languages its language section offers, by their names as shown. */
    private fun openLanguages(): List<String> {
        click(LibraryTags.FILTER_ADD)
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.LANGUAGE_CHOOSER), PATIENCE_MS)
        return compose.onAllNodes(languageChoice, useUnmergedTree = false).fetchSemanticsNodes().map { node ->
            node.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty()
        }
    }

    private fun pickLanguage(language: BookLanguage) {
        click(LibraryTags.languageChoice(language))
        closeTheSheet()
    }

    private fun closeTheSheet() {
        click(LibraryTags.FILTERS_SHEET_DONE)
        compose.waitUntilDoesNotExist(hasTestTag(LibraryTags.FILTERS_SHEET), PATIENCE_MS)
    }

    private fun filterByLanguage(language: BookLanguage) {
        openLanguages()
        pickLanguage(language)
    }

    private fun assertChip(name: String) {
        fun shown() = if (exists(LibraryTags.LANGUAGE_FILTER)) compose.textsOf(LibraryTags.LANGUAGE_FILTER).joinToString("") else null
        runCatching { compose.waitUntil(PATIENCE_MS) { shown() == name } }
        assertEquals(name, shown())
    }

    // L10N-005: the sheet offers the languages present in the view, named in the interface language, and "Not given";
    // choosing one keeps the recipes written in it, shown as a chip that removes it when tapped.
    @Test
    fun theLanguageFilterKeepsTheRecipesWrittenInTheLanguageChosen() {
        own("Borscht", RecipeLanguage.EN, minutesAgo = 1)
        own("Борщ", RecipeLanguage.RU, minutesAgo = 2)
        own("Blini", null, minutesAgo = 3)
        show(LibraryView.OWN)
        assertEquals(listOf("Russian", "English", "Not given"), openLanguages())
        pickLanguage(BookLanguage.EN)
        assertChip("English")
        assertListed("Borscht")
        assertEquals(BookFilters(language = BookLanguage.EN), session.filters(LibraryView.OWN).value)
        filterByLanguage(BookLanguage.NOT_GIVEN)
        assertChip("Language not given")
        assertListed("Blini")
        click(LibraryTags.LANGUAGE_FILTER)
        assertListed("Borscht", "Борщ", "Blini")
        assertEquals(BookFilters(), session.filters(LibraryView.OWN).value)
    }

    // L10N-005: only the languages the view holds are offered, "Not given" only when a recipe declares none.
    @Test
    fun onlyTheLanguagesOfTheViewAreOffered() {
        own("Borscht", RecipeLanguage.EN, minutesAgo = 1)
        own("Beef stew", RecipeLanguage.EN, minutesAgo = 2)
        own("Blini", null, minutesAgo = 3)
        found("Щи", RecipeLanguage.RU)
        show(LibraryView.OWN)
        assertEquals(listOf("English", "Not given"), openLanguages())
    }

    // A view of fewer than two languages — one language, or none given at all — has nothing to filter by language:
    // the sheet leaves its language section out, and keeps the others.
    @Test
    fun aViewOfOneLanguageHasNoLanguageSection() {
        own("Borscht", RecipeLanguage.EN, minutesAgo = 1)
        own("Beef stew", RecipeLanguage.EN, minutesAgo = 2)
        found("Kasha", null)
        show(LibraryView.OWN)
        assertNoLanguageSection()
        closeTheSheet()
        choose(LibraryView.MARKED)
        assertNoLanguageSection()
    }

    // The same in Russian.
    @Test
    @Config(qualifiers = "ru-h2000dp")
    fun inRussianAViewOfOneLanguageHasNoLanguageSection() {
        own("Борщ", RecipeLanguage.RU)
        show(LibraryView.OWN)
        assertNoLanguageSection()
    }

    private fun assertNoLanguageSection() {
        click(LibraryTags.FILTER_ADD)
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.TAG_CHOOSER), PATIENCE_MS)
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.INGREDIENT_CHOOSER), PATIENCE_MS)
        assertEquals(false, exists(LibraryTags.LANGUAGE_CHOOSER))
        assertEquals(0, compose.onAllNodes(languageChoice).fetchSemanticsNodes().size)
    }

    // The same in Russian: the languages and the chip are named in the interface language.
    @Test
    @Config(qualifiers = "ru-h2000dp")
    fun theLanguagesAreNamedInTheInterfaceLanguage() {
        own("Borscht", RecipeLanguage.EN, minutesAgo = 1)
        own("Борщ", RecipeLanguage.RU, minutesAgo = 2)
        own("Блины", null, minutesAgo = 3)
        show(LibraryView.OWN)
        assertEquals(listOf("Русский", "Английский", "Не указан"), openLanguages())
        pickLanguage(BookLanguage.RU)
        assertChip("Русский")
        assertListed("Борщ")
        filterByLanguage(BookLanguage.NOT_GIVEN)
        assertChip("Язык не указан")
        assertListed("Блины")
    }

    // L10N-005: the filter combines with the search, the order and the other filters; "Clear filters" clears it with
    // them and leaves the search.
    @Test
    fun theLanguageFilterCombinesWithTheRestAndIsCleared() {
        own("Borscht", RecipeLanguage.EN, tags = setOf(italian.id), minutesAgo = 1)
        own("Beef stew", RecipeLanguage.EN, minutesAgo = 2)
        own("Bolognese", RecipeLanguage.EN, tags = setOf(italian.id), minutesAgo = 3)
        own("Borscht classic", RecipeLanguage.RU, tags = setOf(italian.id), minutesAgo = 4)
        show(LibraryView.OWN)
        filterByLanguage(BookLanguage.EN)
        assertListed("Borscht", "Beef stew", "Bolognese")
        click(LibraryTags.ORDER)
        click(LibraryTags.order(BookOrder.TITLE))
        assertListed("Beef stew", "Bolognese", "Borscht")
        compose.onNodeWithTag(LibraryTags.SEARCH).performTextReplacement("bo")
        assertListed("Bolognese", "Borscht")
        click(LibraryTags.FILTER_ADD)
        click(LibraryTags.TAG_FIELD)
        compose.onNodeWithTag(LibraryTags.TAG_FIELD).performTextReplacement("ital")
        click(LibraryTags.tagChoice(italian.id))
        closeTheSheet()
        compose.onNodeWithTag(LibraryTags.SEARCH).performTextReplacement("bor")
        assertListed("Borscht")
        click(LibraryTags.FILTERS_CLEAR)
        assertEquals(BookFilters(), session.filters(LibraryView.OWN).value)
        assertEquals(false, exists(LibraryTags.LANGUAGE_FILTER))
        assertEquals("bor", session.query(LibraryView.OWN).value)
        assertListed("Borscht", "Borscht classic")
    }

    // L10N-005, FLOW-035: each view keeps its own language filter for the session; the marked view filters by the
    // language of the content it holds.
    @Test
    fun eachViewKeepsItsOwnLanguageFilter() {
        own("Borscht", RecipeLanguage.EN, minutesAgo = 1)
        own("Борщ", RecipeLanguage.RU, minutesAgo = 2)
        found("Щи", RecipeLanguage.RU, minutesAgo = 1)
        found("Pancakes", RecipeLanguage.EN, minutesAgo = 2)
        found("Kasha", null, minutesAgo = 3)
        show(LibraryView.OWN)
        filterByLanguage(BookLanguage.EN)
        assertListed("Borscht")
        choose(LibraryView.MARKED)
        assertEquals(false, exists(LibraryTags.LANGUAGE_FILTER))
        assertListed("Щи", "Pancakes", "Kasha")
        filterByLanguage(BookLanguage.RU)
        assertChip("Russian")
        assertListed("Щи")
        choose(LibraryView.OWN)
        assertChip("English")
        assertListed("Borscht")
        assertEquals(BookFilters(language = BookLanguage.EN), session.filters(LibraryView.OWN).value)
        assertEquals(BookFilters(language = BookLanguage.RU), session.filters(LibraryView.MARKED).value)
    }

    // L10N-006: the search finds a word beginning in a recipe's description, case and ё folded, in both views.
    @Test
    fun theSearchFindsAWordOfTheDescription() {
        own("Пирог", RecipeLanguage.RU, description = "Бабушкин рецепт с ЁЖЕВИКОЙ из сада", minutesAgo = 1)
        own("Суп", RecipeLanguage.RU, description = "Быстрый и лёгкий", minutesAgo = 2)
        found("Torte", RecipeLanguage.EN, description = "A rich chocolate layer cake", minutesAgo = 1)
        found("Pie", RecipeLanguage.EN, minutesAgo = 2)
        show(LibraryView.OWN)
        compose.onNodeWithTag(LibraryTags.SEARCH).performTextReplacement("ежевик")
        assertListed("Пирог")
        compose.onNodeWithTag(LibraryTags.SEARCH).performTextReplacement("Легк")
        assertListed("Суп")
        choose(LibraryView.MARKED)
        compose.onNodeWithTag(LibraryTags.SEARCH).performTextReplacement("CHOC")
        assertListed("Torte")
        compose.onNodeWithTag(LibraryTags.SEARCH).performTextReplacement("colate")
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.NOTHING_FOUND), PATIENCE_MS)
    }
}
