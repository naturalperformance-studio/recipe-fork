package app.recipefork.app.library

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
import app.recipefork.app.dictionary.TagEntity
import app.recipefork.app.dictionary.TagSource
import app.recipefork.app.editor.EditorEnvironment
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.ui.AppNavHost
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeLanguage
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * FLOW-035: in the book, the search field and the order share the first line of the header, and the row of filter chips
 * stands under them about 12 dp apart — never touching the field, at the usual font size and at twice it.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class FilterRowGapTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val active = ActiveBook(BookName.Pool, book.database)
    private val recipes = RecipeRepository(active)
    private val session = LibrarySession(SavedStateHandle())
    private val now = Instant.parse("2026-10-09T12:00:00Z")
    private val tags = (1L..3L).map { TagEntity(it, "тег $it", "Tag $it") }

    private val card = CardEnvironment(ingredients = IngredientEntries { emptyList() })
    private val editor = EditorEnvironment(active, object : TagSource {
        override suspend fun tags() = this@FilterRowGapTest.tags
    }) { now }

    @Before
    fun waitForTheBook() = compose.registerIdlingResource(book.idling)

    @After
    fun close() {
        compose.unregisterIdlingResource(book.idling)
        book.close()
    }

    @Test
    @Config(qualifiers = "en-w360dp-h800dp")
    fun theChipRowStandsTwelveDpUnderTheSearchLine() = assertTheHeaderLines()

    @Test
    @Config(qualifiers = "en-w360dp-h800dp", fontScale = 2f)
    fun theChipRowStandsTwelveDpUnderTheSearchLineAtTwiceTheFont() = assertTheHeaderLines()

    @Test
    @Config(qualifiers = "ru-w360dp-h800dp", fontScale = 2f)
    fun theChipRowStandsTwelveDpUnderTheSearchLineInRussianAtTwiceTheFont() = assertTheHeaderLines()

    private fun assertTheHeaderLines() {
        listOf("Borscht" to RecipeLanguage.EN, "Борщ" to RecipeLanguage.RU).forEachIndexed { i, (title, language) ->
            val recipe = Recipe.create(now.minusSeconds(i * 60L), owner = null)
                .copy(title = title, language = language, tags = setOf(1, 2, 3))
            runBlocking { recipes.save(recipe) }
        }
        session.choose(LibraryView.OWN)
        compose.setContent { MaterialTheme { AppNavHost(session, recipes, card = card, editor = editor) } }
        // The row is full: "All filters", the languages and the tags offered.
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.tagOffer(1)), PATIENCE_MS)
        compose.waitForIdle()

        val px = Density(context).density
        val search = bounds(LibraryTags.SEARCH)
        val order = bounds(LibraryTags.ORDER)
        val firstChip = bounds(LibraryTags.FILTER_ADD)
        val row = bounds(LibraryTags.FILTERS)

        // Search and order share the first line: the order at the field's end, level with it.
        assertTrue("order $order beside search $search", order.left >= search.right - 1)
        assertTrue("order $order level with search $search", order.top < search.bottom && order.bottom > search.top)
        assertTrue("order $order within the line of search $search", order.center.y in search.top..search.bottom)
        // The chip row lies wholly below the first line.
        assertTrue("row $row under the order $order", row.top >= order.bottom)
        assertTrue("row $row under the search $search", row.top >= search.bottom)
        // About 12 dp between the field and the first chip; they never overlap or touch.
        val gap = (firstChip.top - search.bottom) / px
        assertTrue("gap is $gap dp", gap in 11.5f..12.5f)
        assertTrue("first chip $firstChip overlaps the field $search", !firstChip.overlaps(search))
    }

    private fun bounds(tag: String): Rect = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
}
