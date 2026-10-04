package app.recipefork.app.library

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.Mode
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.editor.textsOf
import app.recipefork.app.recipe.BookFilters
import app.recipefork.app.recipe.BookLanguage
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.ui.AppNavHost
import app.recipefork.core.recipe.Recipe
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
 * FLOW-042: an empty view of the book, and a search or filter that matches nothing, show one layout — a picture, a
 * one-line headline, at most one sentence and one action. An empty view's action is the view's own (FLOW-042 (5),
 * FLOW-064); a result narrowed to nothing offers one action that clears what narrowed it.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class EmptyStatesTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val recipes = RecipeRepository(ActiveBook(BookName.Pool, book.database))
    private val session = LibrarySession(SavedStateHandle())
    private val now = Instant.parse("2026-10-04T12:00:00Z")

    @Before
    fun waitForTheBook() = compose.registerIdlingResource(book.idling)

    @After
    fun close() {
        compose.unregisterIdlingResource(book.idling)
        book.close()
    }

    private fun own(title: String) = runBlocking { recipes.save(Recipe.create(now, owner = null).copy(title = title)) }

    private fun show(view: LibraryView, mode: Mode) {
        session.choose(view)
        compose.setContent { MaterialTheme { AppNavHost(session, recipes, mode) } }
        compose.waitForIdle()
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    private val acts = SemanticsMatcher("acts on a tap") { SemanticsActions.OnClick in it.config }

    /** The tags of what can be tapped inside the empty state tagged [state]. */
    private fun actionsIn(state: String): List<String?> =
        compose.onAllNodes(acts and hasAnyAncestor(hasTestTag(state))).fetchSemanticsNodes()
            .map { it.config.getOrNull(SemanticsProperties.TestTag) }

    private fun lineCount(tag: String): Int {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
        return layouts.single().lineCount
    }

    /**
     * The state tagged [state] shows its picture, its headline [headline] on one line and read as a heading, its
     * [sentence] or none, and one action, tagged [action] and reading [label], of at least 48 dp — or none.
     */
    private fun assertEmptyState(state: String, headline: String, sentence: String?, action: String?, label: String?) {
        val head = LibraryTags.emptyHeadline(state)
        // A typed query reaches the listing a moment after it is typed: the state is awaited with its headline.
        runCatching { compose.waitUntil(PATIENCE_MS) { exists(head) && compose.textsOf(head) == listOf(headline) } }
        compose.waitUntilExactlyOneExists(hasTestTag(state), PATIENCE_MS)
        assertTrue(exists(LibraryTags.emptyPicture(state)))
        assertEquals(listOf(headline), compose.textsOf(head))
        assertEquals(1, lineCount(head))
        assertTrue(SemanticsProperties.Heading in compose.onNodeWithTag(head).fetchSemanticsNode().config)
        if (sentence == null) {
            assertFalse(exists(LibraryTags.emptySentence(state)))
        } else {
            assertEquals(listOf(sentence), compose.textsOf(LibraryTags.emptySentence(state)))
        }
        assertEquals(listOfNotNull(action), actionsIn(state))
        if (action != null) {
            assertEquals(listOf(label), compose.textsOf(action))
            compose.onNodeWithTag(action).assertHeightIsAtLeast(48.dp)
        }
    }

    /** No text on the screen names the server (FLOW-062 (1)). */
    private fun assertNothingNamesTheServer() {
        val texts = compose.onAllNodes(SemanticsMatcher("any") { true }, useUnmergedTree = true).fetchSemanticsNodes()
            .flatMap { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } }
        assertTrue(texts.joinToString(), texts.none { it.contains("server", ignoreCase = true) || it.contains("сервер", ignoreCase = true) })
    }

    // Criterion 1: the own view of an empty book offers a new recipe, the view's own action, and only that.
    @Test
    fun anEmptyOwnViewShowsThePictureTheHeadlineASentenceAndTheNewRecipeAction() {
        show(LibraryView.OWN, Mode.Standard)
        assertEmptyState(
            LibraryTags.OWN_EMPTY, "Nothing written here yet", "Recipes you write in this book are kept here.",
            LibraryTags.WRITE, "New recipe",
        )
        // The view's floating action stands back while the empty state carries it.
        assertFalse(exists(LibraryTags.NEW))
    }

    // Criterion 1, in a build without the server: each tab — the marked view holds imports and offers the import.
    @Test
    fun inServerlessModeEachEmptyTabOffersItsOwnActionAndNamesNoServer() {
        show(LibraryView.MARKED, Mode.Serverless)
        assertEmptyState(
            LibraryTags.MARKED_EMPTY, "No marked recipes yet", "Recipes you import from files are kept here.",
            LibraryTags.IMPORT, "Import a recipe file",
        )
        assertNothingNamesTheServer()
        compose.onNodeWithTag(LibraryTags.tab(LibraryView.OWN)).performClick()
        assertEmptyState(
            LibraryTags.OWN_EMPTY, "Nothing written here yet", "Recipes you write in this book are kept here.",
            LibraryTags.WRITE, "New recipe",
        )
        assertNothingNamesTheServer()
    }

    // Criterion 1: in the standard build the marked view has no action of its own, and its empty state offers none.
    @Test
    fun inStandardModeTheEmptyMarkedViewShowsTheLayoutWithNoAction() {
        show(LibraryView.MARKED, Mode.Standard)
        assertEmptyState(LibraryTags.MARKED_EMPTY, "No marked recipes yet", "Recipes you mark are kept here.", null, null)
    }

    // Criterion 1, in Russian.
    @Test
    @Config(qualifiers = "ru")
    fun inRussianTheEmptyViewsSpeakRussian() {
        show(LibraryView.MARKED, Mode.Serverless)
        assertEmptyState(
            LibraryTags.MARKED_EMPTY, "Отмеченных рецептов пока нет", "Здесь хранятся рецепты, импортированные из файлов.",
            LibraryTags.IMPORT, "Импортировать файл рецепта",
        )
        compose.onNodeWithTag(LibraryTags.tab(LibraryView.OWN)).performClick()
        assertEmptyState(
            LibraryTags.OWN_EMPTY, "Здесь пока ничего не написано", "Здесь хранятся рецепты, которые вы пишете в этой книге.",
            LibraryTags.WRITE, "Новый рецепт",
        )
    }

    // Criterion 2: a search that finds nothing shows the layout with one action, which clears the search.
    @Test
    fun aSearchThatFindsNothingOffersOneActionThatClearsIt() {
        own("Soup")
        show(LibraryView.OWN, Mode.Standard)
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.SEARCH), PATIENCE_MS)
        compose.onNodeWithTag(LibraryTags.SEARCH).performTextReplacement("zzz")
        assertEmptyState(LibraryTags.NOTHING_FOUND, "Nothing found for “zzz”", null, LibraryTags.NOTHING_FOUND_CLEAR, "Clear the search")
        compose.onNodeWithTag(LibraryTags.NOTHING_FOUND_CLEAR).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { !exists(LibraryTags.NOTHING_FOUND) }
        assertEquals("", session.query(LibraryView.OWN).value)
    }

    // Criterion 2: filters that match nothing show the layout with one action, which clears the filters.
    @Test
    fun filtersThatMatchNothingOfferOneActionThatClearsThem() {
        own("Soup")
        session.filter(LibraryView.OWN, BookFilters(language = BookLanguage.EN))
        show(LibraryView.OWN, Mode.Standard)
        assertEmptyState(
            LibraryTags.NOTHING_FOUND, "No recipe matches the filters", null, LibraryTags.NOTHING_FOUND_CLEAR_FILTERS, "Clear filters",
        )
        compose.onNodeWithTag(LibraryTags.NOTHING_FOUND_CLEAR_FILTERS).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { !exists(LibraryTags.NOTHING_FOUND) }
        assertEquals(BookFilters(), session.filters(LibraryView.OWN).value)
    }

    // Criterion 2: a search and filters together offer still one action, which clears both.
    @Test
    fun aSearchWithFiltersOffersOneActionThatClearsBoth() {
        own("Soup")
        session.filter(LibraryView.OWN, BookFilters(language = BookLanguage.EN))
        show(LibraryView.OWN, Mode.Standard)
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.SEARCH), PATIENCE_MS)
        compose.onNodeWithTag(LibraryTags.SEARCH).performTextReplacement("zzz")
        assertEmptyState(
            LibraryTags.NOTHING_FOUND, "Nothing found for “zzz”", null, LibraryTags.NOTHING_FOUND_CLEAR_ALL, "Clear search and filters",
        )
        compose.onNodeWithTag(LibraryTags.NOTHING_FOUND_CLEAR_ALL).performScrollTo().performClick()
        compose.waitUntil(PATIENCE_MS) { !exists(LibraryTags.NOTHING_FOUND) }
        assertEquals("", session.query(LibraryView.OWN).value)
        assertEquals(BookFilters(), session.filters(LibraryView.OWN).value)
    }

    // Criterion 2, in Russian.
    @Test
    @Config(qualifiers = "ru")
    fun inRussianTheClearingOfBothSpeaksRussian() {
        own("Суп")
        session.filter(LibraryView.OWN, BookFilters(language = BookLanguage.EN))
        show(LibraryView.OWN, Mode.Standard)
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.SEARCH), PATIENCE_MS)
        compose.onNodeWithTag(LibraryTags.SEARCH).performTextReplacement("zzz")
        assertEmptyState(
            LibraryTags.NOTHING_FOUND, "По запросу «zzz» ничего не найдено", null, LibraryTags.NOTHING_FOUND_CLEAR_ALL,
            "Очистить поиск и фильтры",
        )
    }
}
