package app.recipefork.app.library

import android.content.Context
import app.recipefork.app.ui.AppTheme
import app.recipefork.app.ui.WorkingFamily
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
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
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.ui.AppNavHost
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeLanguage
import app.recipefork.core.search.BookOrder
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
import org.robolectric.annotation.GraphicsMode
import java.time.Instant

/**
 * FLOW-035 (3), T-304: the book's order is a chip placed second in the filter row, after "All filters", reading the
 * order in force in words and opening the same menu of the same three orders; its click label names its action for
 * the screen reader. The search field keeps no icon at its end, is set wholly in Golos, and its hint stands whole on a
 * 360 dp phone.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OrderChipTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val active = ActiveBook(BookName.Pool, book.database)
    private val recipes = RecipeRepository(active)
    private val session = LibrarySession(SavedStateHandle())
    private val now = Instant.parse("2026-10-22T12:00:00Z")
    private val tags = (1L..2L).map { TagEntity(it, "тег $it", "Tag $it") }

    private val card = CardEnvironment(ingredients = IngredientEntries { emptyList() })
    private val editor = EditorEnvironment(active, object : TagSource {
        override suspend fun tags() = this@OrderChipTest.tags
    }) { now }

    @Before
    fun waitForTheBook() = compose.registerIdlingResource(book.idling)

    @After
    fun close() {
        compose.unregisterIdlingResource(book.idling)
        book.close()
    }

    /** The own view of a book of two recipes carrying tag 1, so that the row offers it after the order. */
    private fun show() {
        listOf("Borscht", "Apple pie").forEachIndexed { i, title ->
            val recipe = Recipe.create(now.minusSeconds(i * 60L), owner = null)
                .copy(title = title, language = RecipeLanguage.EN, tags = setOf(1))
            runBlocking { recipes.save(recipe) }
        }
        session.choose(LibraryView.OWN)
        compose.setContent { AppTheme { AppNavHost(session, recipes, card = card, editor = editor) } }
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.tagOffer(1)), PATIENCE_MS)
        compose.waitForIdle()
    }

    private fun bounds(tag: String): Rect = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    private fun choose(order: BookOrder) {
        compose.onNodeWithTag(LibraryTags.ORDER).performClick()
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.order(order)), PATIENCE_MS)
        compose.onNodeWithTag(LibraryTags.order(order)).performClick()
        compose.waitForIdle()
    }

    /**
     * The order chip stands second in the row, between "All filters" and the first tag offered, level with them; it
     * reads [words] for the newest, oldest and title orders as each is chosen from its menu of the three, the one in
     * force selected; its click label is [action], and that action opens the menu.
     */
    private fun assertTheChip(words: List<String>, action: String) {
        show()
        val all = bounds(LibraryTags.FILTER_ADD)
        val order = bounds(LibraryTags.ORDER)
        val offer = bounds(LibraryTags.tagOffer(1))
        assertTrue("the order $order after «All filters» $all", order.left >= all.right - 1)
        assertTrue("the order $order before the first offer $offer", order.right <= offer.left + 1)
        assertTrue("the order $order level with «All filters» $all", order.center.y in all.top..all.bottom)
        assertTrue("the order is no part of the search field", bounds(LibraryTags.SEARCH).bottom <= order.top)

        assertEquals(listOf(words[0]), compose.textsOf(LibraryTags.ORDER))
        val node = compose.onNodeWithTag(LibraryTags.ORDER).fetchSemanticsNode()
        assertEquals(action, node.config[SemanticsActions.OnClick].label)
        // The chevron is decorative: the chip is read by its words alone.
        assertTrue(node.config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }.isEmpty())

        compose.onNodeWithTag(LibraryTags.ORDER).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntilExactlyOneExists(hasTestTag(LibraryTags.order(BookOrder.TITLE)), PATIENCE_MS)
        assertEquals(3, BookOrder.entries.size)
        BookOrder.entries.forEachIndexed { i, choice ->
            assertEquals(listOf(words[i]), compose.textsOf(LibraryTags.order(choice)))
        }
        compose.onNodeWithTag(LibraryTags.order(BookOrder.NEWEST)).assertIsSelected()
        compose.onNodeWithTag(LibraryTags.order(BookOrder.OLDEST)).assertIsNotSelected()
        compose.onNodeWithTag(LibraryTags.order(BookOrder.TITLE)).performClick()
        compose.waitForIdle()
        assertEquals(listOf(words[2]), compose.textsOf(LibraryTags.ORDER))
        assertEquals(BookOrder.TITLE, session.order(LibraryView.OWN).value)

        choose(BookOrder.OLDEST)
        assertEquals(listOf(words[1]), compose.textsOf(LibraryTags.ORDER))
        assertEquals(BookOrder.OLDEST, session.order(LibraryView.OWN).value)
        choose(BookOrder.NEWEST)
        assertEquals(listOf(words[0]), compose.textsOf(LibraryTags.ORDER))
    }

    @Test
    @Config(qualifiers = "en-w360dp-h800dp")
    fun theOrderIsTheSecondChipInWords() =
        assertTheChip(listOf("Newest first", "Oldest first", "By title"), "Choose the order of the list")

    @Test
    @Config(qualifiers = "ru-w360dp-h800dp")
    fun inRussianTheOrderIsTheSecondChipInWords() =
        assertTheChip(listOf("Сначала новые", "Сначала старые", "По названию"), "Выбрать порядок списка")

    /** The layout of the search field's hint, shown once the field is focused. */
    private fun hint(): TextLayoutResult {
        compose.onNodeWithTag(LibraryTags.SEARCH).performClick()
        compose.waitUntil(PATIENCE_MS) {
            compose.onAllNodes(hasTestTag(LibraryTags.SEARCH_PLACEHOLDER), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitForIdle()
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag(LibraryTags.SEARCH_PLACEHOLDER, useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
        return layouts.single()
    }

    /**
     * The empty search field holds nothing to press at its end and reaches the 16 dp gutter of the screen's edge; its
     * hint reads [words] whole on one line in the working face, Golos, not cut short.
     */
    private fun assertTheSearchField(words: String) {
        show()
        val search = bounds(LibraryTags.SEARCH)
        val width = Density(context).density * 360
        assertEquals("the field ends 16 dp from the edge", 16f, (width - search.right) / Density(context).density, 0.5f)
        val pressable = compose.onAllNodes(hasAnyAncestor(hasTestTag(LibraryTags.SEARCH)) and hasClickAction(), useUnmergedTree = true)
            .fetchSemanticsNodes()
        assertTrue("the empty field holds $pressable", pressable.isEmpty())

        val shown = hint()
        assertEquals(words, shown.layoutInput.text.text)
        assertEquals(1, shown.lineCount)
        assertFalse("the hint is cut short", shown.isLineEllipsized(0))
        assertEquals("the hint is set in the working face", WorkingFamily, shown.layoutInput.style.fontFamily)
        assertEquals(FontWeight.Normal, shown.layoutInput.style.fontWeight)
    }

    /** The layout of the one text found by [matcher] in the unmerged tree. */
    private fun layoutOf(matcher: SemanticsMatcher): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNode(matcher, useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
        return layouts.single()
    }

    /** The whole search field is set in Golos regular: its hint, and what is typed, as the hint is. */
    @Test
    @Config(qualifiers = "ru-w360dp-h800dp")
    fun theSearchFieldIsWhollyInGolosRegular() {
        show()
        val hint = hint()
        assertEquals("the hint", WorkingFamily, hint.layoutInput.style.fontFamily)
        assertEquals(FontWeight.Normal, hint.layoutInput.style.fontWeight)

        compose.onNodeWithTag(LibraryTags.SEARCH).performTextReplacement("кускус")
        compose.waitForIdle()
        val typed = layoutOf(hasTestTag(LibraryTags.SEARCH) and hasSetTextAction())
        assertEquals("кускус", typed.layoutInput.text.text)
        assertEquals("the typed text", WorkingFamily, typed.layoutInput.style.fontFamily)
        assertEquals(FontWeight.Normal, typed.layoutInput.style.fontWeight)
    }

    @Test
    @Config(qualifiers = "en-w360dp-h800dp")
    fun theSearchHintStandsWholeAt360Dp() = assertTheSearchField("Search by title or ingredient")

    @Test
    @Config(qualifiers = "ru-w360dp-h800dp")
    fun inRussianTheSearchHintStandsWholeAt360Dp() = assertTheSearchField("Поиск по названию или ингредиенту")
}
