package app.recipefork.app.library

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
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
import app.recipefork.app.recipe.BookLanguage
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.ui.AppNavHost
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeLanguage
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
 * FLOW-035, L10N-005: the row of quick filter chips under the book's search field — the filters in force, selected, and
 * one-tap offers of the view's languages and its most used tags — and the one sheet of every filter "All filters" opens.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "en-w360dp-h2000dp")
class QuickFiltersTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val active = ActiveBook(BookName.Pool, book.database)
    private val recipes = RecipeRepository(active)
    private val session = LibrarySession(SavedStateHandle())
    private val now = Instant.parse("2026-10-04T12:00:00Z")
    private val tags = (1L..7L).map { TagEntity(it, "тег $it", "Tag $it") }
    private val chicken = IngredientEntryEntity(1, "курица", "chicken", null)

    private val card = CardEnvironment(ingredients = IngredientEntries { listOf(chicken) })
    private val editor = EditorEnvironment(active, object : TagSource {
        override suspend fun tags() = this@QuickFiltersTest.tags
    }) { now }

    @Before
    fun waitForTheBook() = compose.registerIdlingResource(book.idling)

    @After
    fun close() {
        compose.unregisterIdlingResource(book.idling)
        book.close()
    }

    private fun own(title: String, language: RecipeLanguage?, tags: Set<Long> = emptySet(), minutesAgo: Long = 0) {
        val recipe = Recipe.create(now.minusSeconds(minutesAgo * 60), owner = null).copy(title = title, language = language, tags = tags)
        runBlocking { recipes.save(recipe) }
    }

    private fun found(title: String, tags: Set<Long>) {
        val recipe = Recipe.create(now, owner = null).copy(title = title, tags = tags).inState(RecipeImport.FOUND_STATE)
        runBlocking { recipes.addFound(recipe, now) }
    }

    private fun show() {
        session.choose(LibraryView.OWN)
        compose.setContent { MaterialTheme { AppNavHost(session, recipes, card = card, editor = editor) } }
        compose.waitForIdle()
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    private fun click(tag: String) {
        compose.waitUntilExactlyOneExists(hasTestTag(tag), PATIENCE_MS)
        compose.onNodeWithTag(tag).scrolledTo().performClick()
        compose.waitForIdle()
    }

    private val anOffer = SemanticsMatcher("a quick filter offered") {
        val tag = it.config.getOrNull(SemanticsProperties.TestTag).orEmpty()
        tag.startsWith("library-tag-offer-") || tag.startsWith("library-language-offer-")
    }

    /** The quick offers of the row, by their tags, in their order. */
    private fun offers(): List<String> = compose.onAllNodes(anOffer).fetchSemanticsNodes()
        .map { it.config[SemanticsProperties.TestTag] }

    /** A book of three languages whose tags 1 to 6 are carried 6, 5, 4, 3, 2 and 1 times, and tag 7 never. */
    private fun aBookOfLanguagesAndTags() {
        own("Borscht", RecipeLanguage.EN, tags = setOf(1, 2, 3, 4, 5, 6), minutesAgo = 1)
        own("Борщ", RecipeLanguage.RU, tags = setOf(1, 2, 3, 4, 5), minutesAgo = 2)
        own("Blini", null, tags = setOf(1, 2, 3, 4), minutesAgo = 3)
        own("Pie", RecipeLanguage.EN, tags = setOf(1, 2, 3), minutesAgo = 4)
        own("Stew", RecipeLanguage.EN, tags = setOf(1, 2), minutesAgo = 5)
        own("Soup", RecipeLanguage.EN, tags = setOf(1), minutesAgo = 6)
    }

    // The row offers the view's languages and its five most used tags, the most used first; one tap applies an offer,
    // which then stands selected among the filters in force, and a tap on it removes it.
    @Test
    fun theRowOffersLanguagesAndTheMostUsedTagsAndOneTapApplies() {
        aBookOfLanguagesAndTags()
        show()
        val languages = listOf(BookLanguage.RU, BookLanguage.EN, BookLanguage.NOT_GIVEN).map { LibraryTags.languageOffer(it) }
        compose.waitUntil(PATIENCE_MS) { offers() == languages + (1L..5L).map { LibraryTags.tagOffer(it) } }
        assertEquals(listOf("Tag 1"), compose.textsOf(LibraryTags.tagOffer(1)))
        compose.onNodeWithTag(LibraryTags.tagOffer(1)).assertIsNotSelected()
        // The row scrolls sideways, and every chip in it has 48 dp of touch height.
        val row = compose.onNodeWithTag(LibraryTags.FILTERS).fetchSemanticsNode()
        assertTrue(SemanticsActions.ScrollBy in row.config)
        assertTrue(row.config.getOrNull(SemanticsProperties.HorizontalScrollAxisRange) != null)
        val minimum = with(Density(context)) { 48.dp.toPx() } - 1
        (offers() + LibraryTags.FILTER_ADD).forEach { tag ->
            val size = compose.onNodeWithTag(tag).fetchSemanticsNode().size
            assertTrue("$tag is $size", size.height >= minimum && size.width >= minimum)
        }
        // A tag offered: one tap filters by it.
        click(LibraryTags.tagOffer(3))
        compose.waitUntil(PATIENCE_MS) { session.filters(LibraryView.OWN).value == BookFilters(tags = setOf(3)) }
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.tagFilter(3)), PATIENCE_MS)
        compose.onNodeWithTag(LibraryTags.tagFilter(3)).assertIsSelected()
        // The next most used tag takes its place among the offers.
        compose.waitUntil(PATIENCE_MS) { offers() == languages + listOf(1L, 2L, 4L, 5L, 6L).map { LibraryTags.tagOffer(it) } }
        // A language offered: one tap filters by it, and the others stay offered.
        click(LibraryTags.languageOffer(BookLanguage.EN))
        compose.waitUntil(PATIENCE_MS) {
            session.filters(LibraryView.OWN).value == BookFilters(tags = setOf(3), language = BookLanguage.EN)
        }
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.LANGUAGE_FILTER), PATIENCE_MS)
        compose.onNodeWithTag(LibraryTags.LANGUAGE_FILTER).assertIsSelected()
        assertEquals(listOf("English"), compose.textsOf(LibraryTags.LANGUAGE_FILTER))
        assertEquals(false, exists(LibraryTags.languageOffer(BookLanguage.EN)))
        // A filter in force: a tap removes it.
        click(LibraryTags.tagFilter(3))
        compose.waitUntil(PATIENCE_MS) { session.filters(LibraryView.OWN).value == BookFilters(language = BookLanguage.EN) }
        // Clear filters clears what is left.
        click(LibraryTags.FILTERS_CLEAR)
        compose.waitUntil(PATIENCE_MS) { session.filters(LibraryView.OWN).value == BookFilters() }
        compose.waitUntil(PATIENCE_MS) { !exists(LibraryTags.FILTERS_CLEAR) && !exists(LibraryTags.LANGUAGE_FILTER) }
    }

    // A view of one language offers no language: filtering by it would keep every recipe.
    @Test
    fun aViewOfOneLanguageOffersNoLanguage() {
        own("Borscht", RecipeLanguage.EN, tags = setOf(1), minutesAgo = 1)
        own("Pie", RecipeLanguage.EN, minutesAgo = 2)
        show()
        compose.waitUntil(PATIENCE_MS) { offers() == listOf(LibraryTags.tagOffer(1)) }
    }

    // "All filters" opens one bottom sheet with a section for the ingredient, the tags and the language; a choice
    // applies at once, and Done closes the sheet.
    @Test
    fun allFiltersOpensOneSheetWithEverySection() {
        aBookOfLanguagesAndTags()
        show()
        assertEquals(listOf("All filters"), compose.textsOf(LibraryTags.FILTER_ADD))
        click(LibraryTags.FILTER_ADD)
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.FILTERS_SHEET), PATIENCE_MS)
        listOf(LibraryTags.INGREDIENT_CHOOSER, LibraryTags.TAG_CHOOSER, LibraryTags.LANGUAGE_CHOOSER).forEach { section ->
            compose.onNode(hasTestTag(section) and hasAnyAncestor(hasTestTag(LibraryTags.FILTERS_SHEET))).assertExists()
        }
        click(LibraryTags.languageChoice(BookLanguage.RU))
        compose.waitUntil(PATIENCE_MS) { session.filters(LibraryView.OWN).value == BookFilters(language = BookLanguage.RU) }
        compose.onNodeWithTag(LibraryTags.languageChoice(BookLanguage.RU)).assertIsSelected()
        // Picking the language chosen again removes the filter; the sheet stays open for the next choice.
        click(LibraryTags.languageChoice(BookLanguage.RU))
        compose.waitUntil(PATIENCE_MS) { session.filters(LibraryView.OWN).value == BookFilters() }
        click(LibraryTags.languageChoice(BookLanguage.NOT_GIVEN))
        click(LibraryTags.FILTERS_SHEET_DONE)
        compose.waitUntilDoesNotExist(hasTestTag(LibraryTags.FILTERS_SHEET), PATIENCE_MS)
        assertEquals(listOf("Language not given"), compose.textsOf(LibraryTags.LANGUAGE_FILTER))
        assertEquals(BookFilters(language = BookLanguage.NOT_GIVEN), session.filters(LibraryView.OWN).value)
    }

    // Each view offers the tags its own recipes carry, and none the other view's recipes carry.
    @Test
    fun eachViewOffersOnlyTheTagsOfItsOwnRecipes() {
        own("Borscht", null, tags = setOf(1, 2), minutesAgo = 1)
        own("Pie", null, tags = setOf(1), minutesAgo = 2)
        found("Shchi", tags = setOf(5, 6))
        found("Pilaf", tags = setOf(6))
        show()
        compose.waitUntil(PATIENCE_MS) { offers() == listOf(LibraryTags.tagOffer(1), LibraryTags.tagOffer(2)) }
        click(LibraryTags.tab(LibraryView.MARKED))
        compose.waitUntil(PATIENCE_MS) { offers() == listOf(LibraryTags.tagOffer(6), LibraryTags.tagOffer(5)) }
        click(LibraryTags.tab(LibraryView.OWN))
        compose.waitUntil(PATIENCE_MS) { offers() == listOf(LibraryTags.tagOffer(1), LibraryTags.tagOffer(2)) }
    }

    // The most used tags are counted over the view's recipes, each tag once a recipe; ties go to the lower identity.
    @Test
    fun theMostUsedTagsAreCountedOncePerRecipe() {
        val carries = mapOf("a" to listOf(3L, 3L, 1L), "b" to listOf(1L, 2L), "c" to listOf(2L, 9L))
        assertEquals(listOf(1L, 2L, 3L, 9L), mostUsedTags(carries))
        assertEquals(listOf(2L, 3L, 9L), mostUsedTags(carries, excluded = setOf(1L)))
    }
}
