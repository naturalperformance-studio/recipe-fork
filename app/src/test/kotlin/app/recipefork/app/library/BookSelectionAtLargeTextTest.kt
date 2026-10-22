package app.recipefork.app.library

import android.content.Context
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.book.ActiveBook
import app.recipefork.app.book.BookName
import app.recipefork.app.book.TestBook
import app.recipefork.app.editor.PATIENCE_MS
import app.recipefork.app.recipe.RecipeRepository
import app.recipefork.app.ui.AppNavHost
import app.recipefork.app.ui.AppTheme
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
import org.robolectric.annotation.GraphicsMode
import java.time.Instant

/**
 * The selection's bar at twice the font scale on a 320 dp phone (T-261), in English and Russian: the count of chosen
 * entries stands whole, between ✕ and "Select all", overlapping neither, and "Select all" on one line; the five actions
 * at the bottom (v0.42, T-305) share the narrow width, each at least 56 dp high, their words whole, never cut; ✕ and
 * "Select all" are at least 48 dp, and an entry's mark of being chosen is a 26 dp picture over its cover.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BookSelectionAtLargeTextTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val book = TestBook(context)
    private val recipes = RecipeRepository(ActiveBook(BookName.Pool, book.database))
    private val session = LibrarySession(SavedStateHandle())
    private val now = Instant.parse("2026-10-13T12:00:00Z")

    @Before
    fun waitForTheBook() = compose.registerIdlingResource(book.idling)

    @After
    fun close() {
        compose.unregisterIdlingResource(book.idling)
        book.close()
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun bounds(tag: String): Rect = compose.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot().let {
        Rect(it.left.value, it.top.value, it.right.value, it.bottom.value)
    }

    private fun layoutOf(tag: String): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
        return layouts.single()
    }

    private fun assertAtLeast48(tag: String) {
        val box = bounds(tag)
        assertTrue("$tag is ${box.width} × ${box.height} dp", box.width >= 47.5f && box.height >= 47.5f)
    }

    private fun assertTheBar(expected: String) {
        val recipesMade = (1..12).map { n ->
            Recipe.create(now.minusSeconds(n * 60L), owner = null).copy(title = "Dish $n").also { runBlocking { recipes.save(it) } }
        }
        session.choose(LibraryView.OWN)
        compose.setContent { AppTheme { AppNavHost(session, recipes) } }
        val first = recipesMade.first().id.value
        compose.waitUntil(PATIENCE_MS) {
            try {
                compose.onNodeWithTag(LibraryTags.OWN_LIST).performScrollToNode(hasTestTag(LibraryTags.entry(first)))
                true
            } catch (e: AssertionError) {
                false
            }
        }
        compose.onNodeWithTag(LibraryTags.entry(first)).performTouchInput { longClick() }
        compose.waitUntil(PATIENCE_MS) { exists(LibraryTags.SELECT_ALL) && exists(LibraryTags.SELECTION_DELETE) }
        compose.onNodeWithTag(LibraryTags.SELECT_ALL).performClick()
        compose.waitUntil(PATIENCE_MS) { session.selection.value?.count == 12 }
        compose.waitForIdle()

        val layout = layoutOf(LibraryTags.SELECTION_COUNT)
        assertEquals(expected, layout.layoutInput.text.text.replace('\u00A0', ' '))
        assertEquals("the count on one line", 1, layout.lineCount)
        assertFalse("the count cut short", layout.didOverflowWidth || layout.didOverflowHeight)
        val count = bounds(LibraryTags.SELECTION_COUNT)
        val close = bounds(LibraryTags.SELECTION_CLOSE)
        val all = bounds(LibraryTags.SELECT_ALL)
        val bar = bounds(LibraryTags.SELECTION_BAR)
        assertTrue("the count ($count) after ✕ ($close)", count.left >= close.right - 0.5f)
        assertTrue("the count ($count) before Select all ($all)", count.right <= all.left + 0.5f)
        assertTrue("the count ($count) inside the bar ($bar)", count.top >= bar.top - 0.5f && count.bottom <= bar.bottom + 0.5f)
        assertTrue("Select all ($all) inside the bar ($bar)", all.right <= bar.right + 0.5f && all.bottom <= bar.bottom + 0.5f)
        val allWords = textLayoutWithin(LibraryTags.SELECT_ALL)
        assertEquals("Select all on one line", 1, allWords.lineCount)
        assertFalse("Select all cut short", allWords.didOverflowWidth || allWords.didOverflowHeight)
        assertAtLeast48(LibraryTags.SELECTION_CLOSE)
        assertAtLeast48(LibraryTags.SELECT_ALL)

        // The five actions at the bottom share the narrow width, side by side within the window, none of their words cut.
        val actions = listOf(
            LibraryTags.SELECTION_ADD_TO_COLLECTION, LibraryTags.SELECTION_ADD_TO_SHOPPING, LibraryTags.SELECTION_PRINT,
            LibraryTags.SELECTION_SHARE_PDF, LibraryTags.SELECTION_DELETE,
        )
        val boxes = actions.map { bounds(it) }
        boxes.zipWithNext().forEach { (a, b) -> assertTrue("$a before $b", a.right <= b.left + 0.5f) }
        assertTrue("within the window", boxes.first().left >= -0.5f && boxes.last().right <= bar.right + 0.5f)
        actions.zip(boxes).forEach { (tag, box) ->
            assertTrue("$tag is $box, at least 56 dp high", box.height >= 55.5f)
            assertTrue("$tag is $box, at least 48 dp wide", box.width >= 47.5f)
            val words = textLayoutWithin(tag)
            assertFalse("$tag's words cut short", words.didOverflowWidth || words.didOverflowHeight)
            val drawn = textBoundsWithin(tag)
            assertTrue("$tag's words ($drawn) inside it ($box)", drawn.left >= box.left - 0.5f && drawn.right <= box.right + 0.5f && drawn.bottom <= box.bottom + 0.5f)
        }

        // The mark of being chosen is a picture over the cover (T-301), 26 dp; the row is what is pressed.
        val mark = bounds(LibraryTags.selectionMark(first))
        assertEquals(26f, mark.width, 0.5f)
        assertTrue(48.dp.value <= bounds(LibraryTags.entry(first)).height)
    }

    private fun textWithin(tag: String) = compose.onNode(
        SemanticsMatcher.keyIsDefined(SemanticsProperties.Text) and hasAnyAncestor(hasTestTag(tag)),
        useUnmergedTree = true,
    )

    /** The layout of the words inside the node tagged [tag]. */
    private fun textLayoutWithin(tag: String): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        textWithin(tag).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
        return layouts.single()
    }

    private fun textBoundsWithin(tag: String): Rect = textWithin(tag).getUnclippedBoundsInRoot().let {
        Rect(it.left.value, it.top.value, it.right.value, it.bottom.value)
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp", fontScale = 2f)
    fun theBarHoldsTheCountAtTwiceTheFontScale() = assertTheBar("12 selected")

    @Test
    @Config(qualifiers = "ru-w320dp-h640dp", fontScale = 2f)
    fun theBarHoldsTheCountAtTwiceTheFontScaleInRussian() = assertTheBar("Выбрано 12")
}
