package app.recipefork.app.library

import android.content.Context
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
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
 * entries stands whole on one line, between ✕ and ⋮, overlapping neither; ✕, ⋮, the bar's menu items and an entry's mark
 * of being chosen are each at least 48 dp.
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
        compose.waitUntil(PATIENCE_MS) { exists(LibraryTags.SELECTION_MENU) }
        compose.onNodeWithTag(LibraryTags.SELECTION_MENU).performClick()
        compose.waitUntil(PATIENCE_MS) { exists(LibraryTags.SELECT_ALL) }
        assertAtLeast48(LibraryTags.SELECT_ALL)
        assertAtLeast48(LibraryTags.SELECTION_DELETE)
        compose.onNodeWithTag(LibraryTags.SELECT_ALL).performClick()
        compose.waitUntil(PATIENCE_MS) { session.selection.value?.count == 12 && !exists(LibraryTags.SELECT_ALL) }
        compose.waitForIdle()

        val layout = layoutOf(LibraryTags.SELECTION_COUNT)
        assertEquals(expected, layout.layoutInput.text.text.replace(' ', ' '))
        assertEquals("the count on one line", 1, layout.lineCount)
        val drawn = layout.getLineRight(0) - layout.getLineLeft(0)
        assertTrue("the count, $drawn wide, cut short in ${layout.layoutInput.constraints.maxWidth}", drawn <= layout.layoutInput.constraints.maxWidth + 0.5f)
        assertFalse("the count cut short below", layout.didOverflowHeight)
        val count = bounds(LibraryTags.SELECTION_COUNT)
        val close = bounds(LibraryTags.SELECTION_CLOSE)
        val menu = bounds(LibraryTags.SELECTION_MENU)
        val bar = bounds(LibraryTags.SELECTION_BAR)
        assertTrue("the count ($count) after ✕ ($close)", count.left >= close.right - 0.5f)
        assertTrue("the count ($count) before ⋮ ($menu)", count.right <= menu.left + 0.5f)
        assertTrue("the count ($count) inside the bar ($bar)", count.top >= bar.top - 0.5f && count.bottom <= bar.bottom + 0.5f)
        assertAtLeast48(LibraryTags.SELECTION_CLOSE)
        assertAtLeast48(LibraryTags.SELECTION_MENU)
        assertAtLeast48(LibraryTags.selectionMark(first))
        assertTrue(48.dp.value <= bounds(LibraryTags.entry(first)).height)
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp", fontScale = 2f)
    fun theBarHoldsTheCountAtTwiceTheFontScale() = assertTheBar("12 selected")

    @Test
    @Config(qualifiers = "ru-w320dp-h640dp", fontScale = 2f)
    fun theBarHoldsTheCountAtTwiceTheFontScaleInRussian() = assertTheBar("Выбрано 12")
}
